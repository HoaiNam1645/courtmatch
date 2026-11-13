import { expect, test } from '@playwright/test'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  nextMondayIso,
  runSql,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.12 owner views booking requests', () => {
  let venue
  let soonCode
  let laterCode
  let monday

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  async function book(page, token, time, note) {
    const response = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { courtId: venue.courts[0].id, startTime: `${monday}T${time}:00+07:00`, durationMinutes: 60, note },
    })
    expect(response.status(), await response.text()).toBe(201)
    return (await response.json()).bookingCode
  }

  test('lists pending requests with the soonest deadline first and highlights the urgent one', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Yêu Cầu')
    makeVenueBookable(venue.id)
    monday = nextMondayIso()

    const player = await loginByApi(page, 'an.nguyen@example.com')
    laterCode = await book(page, player.token, '09:00', 'Mang theo vợt dự phòng')
    soonCode = await book(page, player.token, '12:00', null)
    runSql(`UPDATE bookings SET owner_response_expires_at = now() + interval '25 minutes' WHERE booking_code = '${laterCode}'`)
    runSql(`UPDATE bookings SET owner_response_expires_at = now() + interval '4 minutes' WHERE booking_code = '${soonCode}'`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Yêu cầu đặt sân' }).click()
    await expect(page).toHaveURL('/owner/booking-requests')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)

    const cards = page.getByTestId('request-list').locator('article')
    await expect(cards).toHaveCount(2)
    await expect(cards.first()).toHaveAttribute('data-testid', `booking-request-${soonCode}`)
    await expect(cards.first()).toHaveAttribute('data-urgent', 'true')
    await expect(cards.first().getByTestId('request-deadline')).toContainText(/Còn [34] phút/)
    await expect(cards.last()).toHaveAttribute('data-urgent', 'false')
    await expect(cards.last().getByTestId('request-deadline')).toContainText(/Còn 2[45] phút/)

    const later = page.getByTestId(`booking-request-${laterCode}`)
    await expect(later).toContainText('E2E Sân Yêu Cầu')
    await expect(later).toContainText('Sân A')
    await expect(later).toContainText('Nguyen Van An')
    await expect(later).toContainText('0901000001')
    await expect(later).toContainText('Mang theo vợt dự phòng')
    await expect(later).toContainText('120.000đ')
    await expect(later.getByTestId('request-status')).toHaveText('Chờ chủ sân duyệt')

    // Chỉ xem: không có nút duyệt / từ chối ở bước này.
    await expect(page.getByRole('button', { name: /Chấp nhận|Từ chối|Xác nhận/ })).toHaveCount(0)
    expect(Number(await page.getByTestId('expiring-count').textContent())).toBeGreaterThanOrEqual(1)
    assertNoErrors()
  })

  test('filters by status and play date, with a distinct empty state', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/booking-requests')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await expect(page.getByTestId('request-list').locator('article')).toHaveCount(2)

    await page.getByLabel('Lọc theo trạng thái').selectOption('awaiting_payment')
    await expect(page.getByRole('heading', { name: 'Không có yêu cầu phù hợp bộ lọc' })).toBeVisible()

    await page.getByLabel('Lọc theo trạng thái').selectOption('all')
    await page.getByLabel('Lọc theo ngày chơi').fill(monday)
    await expect(page.getByTestId('request-list').locator('article')).toHaveCount(2)
    await page.getByLabel('Lọc theo ngày chơi').fill(runSql(`SELECT ('${monday}'::date + 2)::text`))
    await expect(page.getByRole('heading', { name: 'Không có yêu cầu phù hợp bộ lọc' })).toBeVisible()
    assertNoErrors()
  })

  test('shows an error state with retry when the API fails', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    // React StrictMode gọi API hai lần ở môi trường dev nên giữ lỗi cho tới khi bấm "Thử lại".
    let failing = true
    await page.route('**/api/v1/owner/booking-requests?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/booking-requests')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('request-summary')).toBeVisible()
  })

  test('requests page fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/booking-requests')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await expect(page.getByTestId('request-list')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

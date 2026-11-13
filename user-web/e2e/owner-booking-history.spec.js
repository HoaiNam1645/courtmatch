import { expect, test } from '@playwright/test'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  nextMondayIso,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.15 owner views booking history', () => {
  let venue
  let monday
  let codes = {}

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('lists bookings of every status and filters by status group, search and venue', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Lịch Sử')
    makeVenueBookable(venue.id)
    monday = nextMondayIso()
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const bookings = []
    for (const time of ['09:00', '12:00', '15:00']) {
      const response = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
        headers: { Authorization: `Bearer ${player.token}` },
        data: { courtId: venue.courts[0].id, startTime: `${monday}T${time}:00+07:00`, durationMinutes: 60, note: 'E2E' },
      })
      expect(response.status(), await response.text()).toBe(201)
      bookings.push(await response.json())
    }
    const [pending, accepted, rejected] = bookings
    codes = { pending: pending.bookingCode, accepted: accepted.bookingCode, rejected: rejected.bookingCode }
    const auth = { Authorization: `Bearer ${owner.token}` }
    expect((await page.request.post(`${API_BASE_URL}/api/v1/owner/booking-requests/${accepted.id}/accept`, { headers: auth })).status()).toBe(200)
    expect((await page.request.post(`${API_BASE_URL}/api/v1/owner/booking-requests/${rejected.id}/reject`, { headers: auth, data: { reasonCode: 'maintenance', note: 'Thay lưới' } })).status()).toBe(200)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Lịch sử đặt sân' }).click()
    await expect(page).toHaveURL('/owner/bookings')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)

    const list = page.getByTestId('history-list')
    await expect(list.locator('li')).toHaveCount(3)
    await expect(page.getByTestId(`history-${codes.accepted}`).getByTestId('history-status')).toHaveText('Chờ thanh toán')
    await expect(page.getByTestId(`history-${codes.accepted}`).getByTestId('history-payment')).toHaveText('Chờ thanh toán')
    await expect(page.getByTestId(`history-${codes.pending}`).getByTestId('history-payment')).toHaveCount(0)

    await page.getByRole('tab', { name: 'Đang xử lý' }).click()
    await expect(list.locator('li')).toHaveCount(2)
    await page.getByRole('tab', { name: 'Đã đóng' }).click()
    await expect(list.locator('li')).toHaveCount(1)
    await expect(page.getByTestId(`history-${codes.rejected}`).getByTestId('history-status')).toHaveText('Đã từ chối')
    await page.getByRole('tab', { name: 'Tranh chấp' }).click()
    await expect(page.getByRole('heading', { name: 'Không có booking phù hợp' })).toBeVisible()

    await page.getByRole('tab', { name: 'Tất cả' }).click()
    await page.getByLabel('Tìm booking').fill(codes.pending.toLowerCase())
    await expect(list.locator('li')).toHaveCount(1)
    await expect(page.getByTestId(`history-${codes.pending}`)).toBeVisible()
    await page.getByLabel('Tìm booking').fill('nguyen van an')
    await expect(list.locator('li')).toHaveCount(3)
    await page.getByLabel('Xóa tìm kiếm').click()
    assertNoErrors()
  })

  test('opens the detail drawer with payment info and the status timeline', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/bookings')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await page.getByRole('button', { name: `Xem chi tiết ${codes.accepted}` }).click()

    const drawer = page.getByTestId('booking-drawer')
    await expect(drawer.getByTestId('drawer-status')).toHaveText('Chờ thanh toán')
    await expect(drawer.getByTestId('drawer-payment')).toContainText('Chuyển khoản vào tài khoản chủ sân')
    await expect(drawer.getByTestId('drawer-payment')).toContainText('120.000đ')
    const steps = drawer.getByTestId('drawer-timeline').locator('li')
    await expect(steps).toHaveCount(2)
    await expect(steps.first()).toContainText('Chờ chủ sân duyệt')
    await expect(steps.first()).toContainText('Nguyen Van An')
    await expect(steps.last()).toContainText('Chờ thanh toán')
    await expect(steps.last()).toContainText('Nguyen Huu Thang')
    await page.keyboard.press('Escape')
    await expect(drawer).toHaveCount(0)

    await page.getByRole('button', { name: `Xem chi tiết ${codes.rejected}` }).click()
    await expect(page.getByTestId('booking-drawer')).toContainText('Lý do từ chối: San dang bao tri hoac sua chua: Thay lưới')
    await expect(page.getByTestId('booking-drawer')).toContainText('Chưa có thanh toán')
    assertNoErrors()
  })

  test('filters by play date and rejects an inverted date range', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/bookings')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await page.getByLabel('Chơi từ ngày').fill(monday)
    await page.getByLabel('Chơi đến ngày').fill(monday)
    await expect(page.getByTestId('history-list').locator('li')).toHaveCount(3)
    await page.getByLabel('Chơi từ ngày').fill('2030-01-01')
    await expect(page.getByRole('alert')).toContainText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu')
    assertNoErrors()
  })

  test('shows an error state with retry when the API fails', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    let failing = true
    await page.route('**/api/v1/owner/bookings?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/bookings')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByRole('tab', { name: 'Tất cả' })).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('history page and drawer fit a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/bookings')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await expect(page.getByTestId('history-list')).toBeVisible()
    let overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await page.getByRole('button', { name: `Xem chi tiết ${codes.accepted}` }).click()
    await expect(page.getByTestId('booking-drawer')).toBeVisible()
    const box = await page.getByTestId('booking-drawer').boundingBox()
    expect(box.x + box.width).toBeLessThanOrEqual(375)
    overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

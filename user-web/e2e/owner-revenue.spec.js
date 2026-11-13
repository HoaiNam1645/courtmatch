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
const VN_TODAY = "(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"

test.describe.serial('2.3.17 owner views revenue statistics', () => {
  let venue
  let emptyVenue
  let today
  let threeDaysAgo

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('shows gross revenue per day, per venue and against the previous period', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Doanh Thu')
    emptyVenue = await createE2eVenue(page, owner.token, 'E2E Sân Chưa Có Thu')
    makeVenueBookable(venue.id)
    const monday = nextMondayIso()
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const ownerAuth = { Authorization: `Bearer ${owner.token}` }
    const playerAuth = { Authorization: `Bearer ${player.token}` }

    // Ba booking đã thu tiền (hôm nay, 3 ngày trước, 10 ngày trước) và một booking chưa thu.
    const daysAgo = [0, 3, 10, null]
    for (const [index, time] of ['07:00', '09:00', '12:00', '15:00'].entries()) {
      const booking = await (await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
        headers: playerAuth, data: { courtId: venue.courts[0].id, startTime: `${monday}T${time}:00+07:00`, durationMinutes: 60, note: 'E2E' },
      })).json()
      expect((await page.request.post(`${API_BASE_URL}/api/v1/owner/booking-requests/${booking.id}/accept`, { headers: ownerAuth })).status()).toBe(200)
      if (daysAgo[index] === null) continue
      const payment = await (await page.request.get(`${API_BASE_URL}/api/v1/payments/by-booking/${booking.id}`, { headers: playerAuth })).json()
      expect((await page.request.post(`${API_BASE_URL}/api/v1/payments/${payment.id}/report-paid`, { headers: playerAuth, data: { method: 'cash' } })).status()).toBe(200)
      expect((await page.request.post(`${API_BASE_URL}/api/v1/payments/${payment.id}/confirm-received`, { headers: ownerAuth })).status()).toBe(200)
      runSql(`UPDATE payments SET confirmed_at = (${VN_TODAY} - ${daysAgo[index]} + time '12:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' WHERE id = '${payment.id}'`)
    }
    today = runSql(`SELECT ${VN_TODAY}::text`)
    threeDaysAgo = runSql(`SELECT (${VN_TODAY} - 3)::text`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Tài chính' }).click()
    await expect(page).toHaveURL('/owner/finance')
    await expect(page.getByRole('tab', { name: 'Doanh thu' })).toHaveAttribute('aria-selected', 'true')
    await page.getByLabel('Lọc doanh thu theo sân').selectOption(venue.id)

    // 30 ngày: cả ba booking, kỳ trước chưa có doanh thu.
    await expect(page.getByTestId('revenue-total')).toHaveText('360.000đ')
    await expect(page.getByTestId('revenue-bookings')).toHaveText('3')
    await expect(page.getByTestId('revenue-average')).toHaveText('120.000đ')
    await expect(page.getByTestId('revenue-change')).toHaveText('Kỳ trước chưa có doanh thu')
    await expect(page.getByTestId(`bar-${today}`)).toHaveAttribute('data-value', '120000')
    await expect(page.getByTestId(`bar-${threeDaysAgo}`)).toHaveAttribute('data-value', '120000')
    await expect(page.getByTestId('revenue-chart').locator('[data-testid^="bar-"]')).toHaveCount(30)
    await expect(page.getByTestId('revenue-venue-E2E Sân Doanh Thu')).toContainText('360.000đ')

    // 7 ngày: hôm nay + 3 ngày trước; 7 ngày trước đó có booking 10 ngày trước => +100%.
    await page.getByRole('button', { name: '7 ngày' }).click()
    await expect(page.getByTestId('revenue-total')).toHaveText('240.000đ')
    await expect(page.getByTestId('revenue-change')).toContainText('Tăng 100% so với kỳ trước')
    await expect(page.getByTestId('revenue-chart').locator('[data-testid^="bar-"]')).toHaveCount(7)

    await page.getByRole('button', { name: 'Tùy chọn' }).click()
    await page.getByLabel('Doanh thu từ ngày').fill(today)
    await page.getByLabel('Doanh thu đến ngày').fill(today)
    await expect(page.getByTestId('revenue-total')).toHaveText('120.000đ')
    await page.getByLabel('Doanh thu từ ngày').fill('2031-01-10')
    await expect(page.getByRole('alert')).toContainText('ngày kết thúc phải sau hoặc bằng ngày bắt đầu')
    assertNoErrors()
  })

  test('shows an empty state for a venue without revenue', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=revenue')
    await page.getByLabel('Lọc doanh thu theo sân').selectOption(emptyVenue.id)
    await expect(page.getByRole('heading', { name: 'Chưa có doanh thu trong kỳ này' })).toBeVisible()
    assertNoErrors()
  })

  test('shows an error state with retry when the API fails', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    let failing = true
    await page.route('**/api/v1/owner/revenue**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/finance')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('revenue-total').or(page.getByRole('heading', { name: 'Chưa có doanh thu trong kỳ này' }))).toBeVisible()
  })

  test('revenue page fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance')
    await page.getByLabel('Lọc doanh thu theo sân').selectOption(venue.id)
    await expect(page.getByTestId('revenue-chart')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

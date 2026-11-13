import { expect, test } from '@playwright/test'
import {
  createAcceptedBookings,
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  runSql,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

/** Số tiền hiển thị theo định dạng vi-VN. */
const vnd = (value) => `${value.toLocaleString('vi-VN')}đ`

const emptyDebt = {
  asOf: new Date().toISOString(), totalOutstanding: 0, outstandingCount: 0, overdueAmount: 0, overdueCount: 0,
  upcomingAmount: 0, upcomingCount: 0, suspendedAmount: 0, suspendedCount: 0, settledAmount: 0, byVenue: [],
}

test.describe.serial('2.3.20 owner views the total commission debt', () => {
  let venue
  let baseline

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('aggregates the debt from per-booking commissions', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    // Mốc ban đầu của chủ sân (dữ liệu seed hoặc dữ liệu khác trong DB local).
    baseline = await (await page.request.get(`${API_BASE_URL}/api/v1/owner/commission-debt`, { headers: { Authorization: `Bearer ${owner.token}` } })).json()

    venue = await createE2eVenue(page, owner.token, 'E2E Sân Công Nợ')
    makeVenueBookable(venue.id)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00', '10:00', '12:00', '14:00'] })
    const plan = [
      { amount: 12000, status: 'due', dueDays: -3 },     // quá hạn
      { amount: 25000, status: 'due', dueDays: 5 },      // chưa đến hạn
      { amount: 8000, status: 'pending', dueDays: 2 },   // chưa đến hạn
      { amount: 12000, status: 'suspended', dueDays: -4 }, // tạm hoãn
      { amount: 6000, status: 'settled', dueDays: -9 },  // đã đối soát
    ]
    bookings.forEach((booking, index) => {
      const { amount, status, dueDays } = plan[index]
      runSql(`
        UPDATE bookings SET status = 'completed', completed_at = now() WHERE id = '${booking.id}';
        INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
        SELECT b.id, v.owner_id, b.total_amount, 'fixed', ${amount}, ${amount}, '${status}', now() - interval '10 days', now() + interval '${dueDays} days'
        FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    })

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Tài chính' }).click()
    await page.getByRole('tab', { name: 'Công nợ' }).click()
    await expect(page).toHaveURL('/owner/finance?tab=debt')

    const outstanding = Number(baseline.totalOutstanding) + 45000
    await expect(page.getByTestId('debt-total')).toHaveText(vnd(outstanding))
    await expect(page.getByTestId('debt-total-count')).toHaveText(`${baseline.outstandingCount + 3} booking chưa đối soát`)
    await expect(page.getByTestId('debt-overdue')).toHaveText(vnd(Number(baseline.overdueAmount) + 12000))
    await expect(page.getByTestId('debt-upcoming')).toHaveText(vnd(Number(baseline.upcomingAmount) + 33000))
    await expect(page.getByTestId('debt-suspended')).toHaveText(vnd(Number(baseline.suspendedAmount) + 12000))
    await expect(page.getByTestId('debt-settled')).toHaveText(vnd(Number(baseline.settledAmount) + 6000))
    await expect(page.getByTestId('debt-overdue-warning')).toContainText('đã quá hạn thanh toán')

    const venueRow = page.getByTestId('debt-venue-E2E Sân Công Nợ')
    await expect(venueRow).toContainText('45.000đ')
    await expect(venueRow).toContainText('quá hạn 12.000đ')
    await expect(venueRow).toContainText('3 booking')
    assertNoErrors()
  })

  test('shows no overdue warning and the next due date when nothing is late', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.route('**/api/v1/owner/commission-debt', (route) => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ ...emptyDebt, totalOutstanding: 30000, outstandingCount: 2, upcomingAmount: 30000, upcomingCount: 2, nextDueAt: new Date(Date.now() + 86400000).toISOString(), settledAmount: 5000, byVenue: [{ venueId: venue.id, venueName: 'Sân A', outstanding: 30000, overdue: 0, bookings: 2 }] }),
    }))
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-total')).toHaveText('30.000đ')
    await expect(page.getByTestId('debt-overdue-warning')).toHaveCount(0)
    await expect(page.getByTestId('debt-next-due')).toBeVisible()
    await expect(page.getByTestId('debt-venue-Sân A')).not.toContainText('quá hạn')
    assertNoErrors()
  })

  test('says so when the owner has no debt at all, and when only paid history exists', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.route('**/api/v1/owner/commission-debt', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(emptyDebt) }))
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByRole('heading', { name: 'Bạn chưa có công nợ hoa hồng' })).toBeVisible()

    await page.unroute('**/api/v1/owner/commission-debt')
    await page.route('**/api/v1/owner/commission-debt', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...emptyDebt, settledAmount: 9000 }) }))
    await page.reload()
    await expect(page.getByTestId('debt-total')).toHaveText('0đ')
    await expect(page.getByText('Bạn không còn khoản hoa hồng nào phải thanh toán.')).toBeVisible()
    await expect(page.getByTestId('debt-by-venue-empty')).toBeVisible()
  })

  test('shows an error state with retry when the API fails', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    let failing = true
    await page.route('**/api/v1/owner/commission-debt', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('debt-total')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('debt tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-summary')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

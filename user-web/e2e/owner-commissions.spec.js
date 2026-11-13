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

test.describe.serial('2.3.18 owner views the platform fee of each booking', () => {
  let venue
  let codes = {}

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('lists fee snapshots with policy, amount, status and overdue flag', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Hoa Hồng')
    makeVenueBookable(venue.id)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const [recent, overdue, suspended, estimated] = await createAcceptedBookings(page, {
      ownerToken: owner.token, playerToken: player.token, venue, times: ['07:00', '09:00', '12:00', '15:00'],
    })
    codes = { recent: recent.bookingCode, overdue: overdue.bookingCode, suspended: suspended.bookingCode, estimated: estimated.bookingCode }

    const insert = (booking, type, value, amount, status, calculatedDays, dueDays) => runSql(`
      UPDATE bookings SET status = 'completed', completed_at = now() WHERE id = '${booking.id}';
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
      SELECT b.id, v.owner_id, b.total_amount, '${type}', ${value}, ${amount}, '${status}', now() - interval '${calculatedDays} days', now() + interval '${dueDays} days'
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    insert(recent, 'percentage', 10, 12000, 'due', 0, 7)
    insert(overdue, 'fixed', 25000, 25000, 'due', 10, -3)
    insert(suspended, 'percentage', 10, 12000, 'suspended', 1, 6)
    runSql(`UPDATE bookings SET status = 'confirmed' WHERE id = '${estimated.id}'`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance')
    await page.getByRole('tab', { name: 'Hoa hồng' }).click()
    await expect(page).toHaveURL('/owner/finance?tab=commissions')
    await page.getByLabel('Lọc hoa hồng theo sân').selectOption(venue.id)

    await expect(page.getByTestId('active-policy')).toContainText('10%')
    const list = page.getByTestId('commission-list')
    await expect(list.locator('li')).toHaveCount(3)
    await expect(page.getByTestId('commission-total')).toHaveText('49.000đ')
    const recentCard = page.getByTestId(`commission-${codes.recent}`)
    await expect(recentCard.getByTestId('commission-amount')).toHaveText('12.000đ')
    await expect(recentCard.getByTestId('commission-status')).toHaveText('Cần thanh toán')
    await expect(recentCard).toContainText('doanh thu gộp 120.000đ')
    await expect(recentCard).toContainText('Phí 10%')
    await expect(recentCard.getByTestId('commission-overdue')).toHaveCount(0)
    const overdueCard = page.getByTestId(`commission-${codes.overdue}`)
    await expect(overdueCard).toContainText('Phí 25.000đ/booking')
    await expect(overdueCard.getByTestId('commission-overdue')).toHaveText('Quá hạn')

    await page.getByTestId('commission-tab-due').click()
    await expect(list.locator('li')).toHaveCount(2)
    await page.getByTestId('commission-tab-suspended').click()
    await expect(list.locator('li')).toHaveCount(1)
    await expect(page.getByTestId(`commission-${codes.suspended}`).getByTestId('commission-status')).toHaveText('Tạm hoãn do tranh chấp')
    await page.getByTestId('commission-tab-settled').click()
    await expect(page.getByRole('heading', { name: 'Chưa có hoa hồng nào' })).toBeVisible()
    assertNoErrors()
  })

  test('shows the fee snapshot, or an estimate, in the booking detail', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/bookings')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await page.getByRole('button', { name: `Xem chi tiết ${codes.recent}` }).click()
    const fee = page.getByTestId('drawer-fee')
    await expect(fee.getByTestId('drawer-fee-amount')).toHaveText('12.000đ')
    await expect(fee).toContainText('Cần thanh toán')
    await expect(fee.getByTestId('drawer-fee-estimate')).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.getByRole('button', { name: `Xem chi tiết ${codes.estimated}` }).click()
    await expect(page.getByTestId('drawer-fee').getByTestId('drawer-fee-estimate')).toContainText('Ước tính')
    await expect(page.getByTestId('drawer-fee-amount')).toHaveText('12.000đ')
    assertNoErrors()
  })

  test('shows an error state with retry when the API fails', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    let failing = true
    await page.route('**/api/v1/owner/commissions?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/finance?tab=commissions')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('commission-note')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('commission tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=commissions')
    await page.getByLabel('Lọc hoa hồng theo sân').selectOption(venue.id)
    await expect(page.getByTestId('commission-list')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

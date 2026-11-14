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

test.describe.serial('2.3.22 owner views the reconciliation history of a booking', () => {
  let venue
  let bookings

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('shows recorded, settlement and settled events in order on the booking drawer', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Đối Soát')
    makeVenueBookable(venue.id)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00', '10:00'] })
    bookings.forEach((booking, index) => {
      const status = ['settled', 'due', 'suspended'][index]
      runSql(`
        UPDATE bookings SET status = 'completed', completed_at = now() WHERE id = '${booking.id}';
        INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at, settled_at)
        SELECT b.id, v.owner_id, b.total_amount, 'fixed', 11000, 11000, '${status}', now() - interval '10 days',
               now() + interval '${index === 1 ? -2 : 3} days', ${index === 0 ? "now() - interval '1 day'" : 'NULL'}
        FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    })
    runSql(`
      WITH s AS (
        INSERT INTO commission_settlements (owner_id, period_from, period_to, commission_total, amount_due, amount_paid, status, note, created_at)
        SELECT v.owner_id, current_date - 30, current_date - 5, 11000, 11000, 11000, 'paid', 'E2E', now() - interval '5 days'
        FROM venues v WHERE v.id = '${venue.id}' RETURNING id)
      UPDATE booking_commissions SET settlement_id = (SELECT id FROM s) WHERE booking_id = '${bookings[0].id}'`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/bookings')
    await page.getByLabel('Tìm booking').fill(bookings[0].bookingCode)
    await page.getByRole('button', { name: `Xem chi tiết ${bookings[0].bookingCode}` }).click()
    const history = page.getByTestId('reconciliation-history')
    await expect(history).toBeVisible()
    const events = history.getByTestId('reconciliation-events').getByRole('listitem')
    await expect(events).toHaveCount(3)
    await expect(events.nth(0)).toContainText('Ghi nhận hoa hồng cho booking')
    await expect(events.nth(1)).toContainText('Đưa vào kỳ đối soát')
    await expect(events.nth(2)).toContainText('Đã đối soát xong')
    await expect(history.getByTestId('reconciliation-settlement')).toContainText('Kỳ đối soát')
    assertNoErrors()
  })

  test('shows overdue and suspended states', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await page.getByLabel('Lọc công nợ theo sân').selectOption(venue.id)
    await page.getByTestId(`debt-booking-${bookings[1].bookingCode}`).getByTestId('debt-booking-history').click()
    await expect(page.getByTestId('reconciliation-overdue')).toContainText('Đã quá hạn thanh toán')
    await expect(page.getByTestId('reconciliation-recorded')).toBeVisible()
    await page.getByRole('button', { name: 'Đóng' }).click()

    await page.goto('/owner/bookings')
    await page.getByLabel('Tìm booking').fill(bookings[2].bookingCode)
    await page.getByRole('button', { name: `Xem chi tiết ${bookings[2].bookingCode}` }).click()
    await expect(page.getByTestId('reconciliation-suspended')).toContainText('Tạm hoãn do tranh chấp')
  })

  test('shows an error state with retry when the API fails', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    let failing = true
    await page.route('**/reconciliation', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/bookings')
    await page.getByLabel('Tìm booking').fill(bookings[0].bookingCode)
    await page.getByRole('button', { name: `Xem chi tiết ${bookings[0].bookingCode}` }).click()
    await expect(page.getByTestId('reconciliation-history').getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByTestId('reconciliation-history').getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('reconciliation-events')).toBeVisible()
  })

  test('reconciliation history fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/bookings')
    await page.getByLabel('Tìm booking').fill(bookings[0].bookingCode)
    await page.getByRole('button', { name: `Xem chi tiết ${bookings[0].bookingCode}` }).click()
    await expect(page.getByTestId('reconciliation-events')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

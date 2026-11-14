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

const vnd = (value) => `${value.toLocaleString('vi-VN')}đ`

test.describe.serial('2.3.21 owner sees the bookings that make up the commission debt', () => {
  let venue
  let bookings

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('lists only unsettled commissions, oldest due date first, filtered by bucket and venue', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Chi Tiết Nợ')
    makeVenueBookable(venue.id)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00', '10:00', '12:00', '14:00'] })
    const plan = [
      { amount: 12000, status: 'due', dueDays: 4 },
      { amount: 20000, status: 'due', dueDays: -6 },
      { amount: 8000, status: 'adjusted', dueDays: -1 },
      { amount: 9000, status: 'suspended', dueDays: -3 },
      { amount: 7000, status: 'settled', dueDays: -9 },
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
    await page.goto('/owner/finance?tab=debt')
    await page.getByLabel('Lọc công nợ theo sân').selectOption(venue.id)

    const items = page.getByTestId('debt-bookings').getByRole('listitem')
    await expect(items).toHaveCount(3)
    await expect(page.getByTestId('debt-list-total')).toHaveText(vnd(40000))
    // Hạn sớm nhất trước: -6 ngày, -1 ngày, +4 ngày.
    await expect(items.nth(0)).toContainText(bookings[1].bookingCode)
    await expect(items.nth(1)).toContainText(bookings[2].bookingCode)
    await expect(items.nth(2)).toContainText(bookings[0].bookingCode)
    await expect(items.nth(0).getByTestId('debt-booking-amount')).toHaveText('20.000đ')
    await expect(items.nth(0).getByTestId('debt-booking-overdue')).toBeVisible()
    await expect(items.nth(2).getByTestId('debt-booking-overdue')).toHaveCount(0)
    await expect(page.getByTestId(`debt-booking-${bookings[3].bookingCode}`)).toHaveCount(0)
    await expect(page.getByTestId(`debt-booking-${bookings[4].bookingCode}`)).toHaveCount(0)

    await page.getByTestId('debt-bucket-overdue').click()
    await expect(items).toHaveCount(2)
    await expect(page.getByTestId('debt-list-total')).toHaveText(vnd(28000))
    await page.getByTestId('debt-bucket-upcoming').click()
    await expect(items).toHaveCount(1)
    await expect(items.first()).toContainText(bookings[0].bookingCode)
    assertNoErrors()
  })

  test('shows an empty state for a venue with no debt, and an error state with retry', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    runSql(`UPDATE booking_commissions SET status = 'settled' WHERE booking_id IN (SELECT id FROM bookings WHERE venue_id = '${venue.id}')`)
    await page.goto('/owner/finance?tab=debt')
    await page.getByLabel('Lọc công nợ theo sân').selectOption(venue.id)
    await expect(page.getByRole('heading', { name: 'Không có booking nào cấu thành công nợ' })).toBeVisible()

    let failing = true
    await page.route('**/api/v1/owner/commission-debt/bookings**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.getByTestId('debt-bucket-overdue').click()
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByRole('heading', { name: 'Không có booking nào cấu thành công nợ' })).toBeVisible()
  })

  test('debt booking list fits a mobile screen', async ({ browser }) => {
    runSql(`UPDATE booking_commissions SET status = 'due' WHERE booking_id IN (SELECT id FROM bookings WHERE venue_id = '${venue.id}')`)
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await page.getByLabel('Lọc công nợ theo sân').selectOption(venue.id)
    await expect(page.getByTestId('debt-bookings')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

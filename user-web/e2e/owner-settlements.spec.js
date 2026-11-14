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

test.describe.serial('2.3.23 owner views the commission settlement periods', () => {
  let venue
  let bookings

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('lists settlement periods newest first with the amounts and opens one', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Kỳ Đối Soát')
    makeVenueBookable(venue.id)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00'] })
    bookings.forEach((booking) => {
      runSql(`
        UPDATE bookings SET status = 'completed', completed_at = now() WHERE id = '${booking.id}';
        INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
        SELECT b.id, v.owner_id, b.total_amount, 'fixed', 15000, 15000, 'due', now() - interval '3 days', now() + interval '3 days'
        FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    })
    // Hai kỳ ở quá khứ xa để không đụng kỳ thật: kỳ cũ đã trả đủ, kỳ mới trả một phần.
    runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, commission_total, amount_due, amount_paid, status, note, created_at)
      SELECT v.owner_id, date '2001-01-01', date '2001-01-31', 9000, 9000, 9000, 'paid', 'E2E', now() FROM venues v WHERE v.id = '${venue.id}';
      INSERT INTO commission_settlements (owner_id, period_from, period_to, opening_outstanding, commission_total, amount_due, amount_paid, status, note, created_at)
      SELECT v.owner_id, date '2001-02-01', date '2001-02-28', 5000, 30000, 35000, 12000, 'partial', 'E2E ghi chú kỳ hai', now() FROM venues v WHERE v.id = '${venue.id}';
      UPDATE booking_commissions SET settlement_id = (SELECT id FROM commission_settlements WHERE note = 'E2E ghi chú kỳ hai')
      WHERE booking_id IN ('${bookings[0].id}', '${bookings[1].id}')`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Tài chính' }).click()
    await page.getByRole('tab', { name: 'Đối soát' }).click()
    await expect(page).toHaveURL('/owner/finance?tab=settlements')

    await page.getByTestId('settlement-tab-partial').click()
    const partial = page.getByTestId('settlement-2001-02-01')
    await expect(partial).toBeVisible()
    await expect(partial.getByTestId('settlement-status')).toHaveText('Đã trả một phần')
    await expect(partial.getByTestId('settlement-remaining')).toHaveText('Còn 23.000đ')
    await expect(partial).toContainText('Kỳ 01/02/2001 – 28/02/2001')
    await expect(partial).toContainText('Phải trả 35.000đ · đã trả 12.000đ · 2 booking')

    await page.getByTestId('settlement-tab-all').click()
    await expect(page.getByTestId('settlement-2001-01-01')).toBeVisible()
    const list = page.getByTestId('settlement-list').getByRole('listitem')
    const texts = await list.allTextContents()
    expect(texts.findIndex((text) => text.includes('01/02/2001'))).toBeLessThan(texts.findIndex((text) => text.includes('01/01/2001')))

    await page.getByTestId('settlement-tab-partial').click()
    await page.getByRole('button', { name: 'Xem chi tiết kỳ 01/02/2001' }).click()
    const drawer = page.getByTestId('settlement-drawer')
    await expect(drawer.getByTestId('settlement-drawer-status')).toHaveText('Đã trả một phần')
    await expect(drawer.getByTestId('settlement-drawer-due')).toHaveText('35.000đ')
    await expect(drawer.getByTestId('settlement-drawer-paid')).toHaveText('12.000đ')
    await expect(drawer.getByTestId('settlement-drawer-remaining')).toHaveText('23.000đ')
    await expect(drawer).toContainText('E2E ghi chú kỳ hai')
    await expect(drawer.getByTestId('settlement-drawer-bookings').getByRole('listitem')).toHaveCount(2)
    await expect(drawer.getByTestId(`settlement-booking-${bookings[0].bookingCode}`)).toContainText('15.000đ')
    await page.keyboard.press('Escape')
    await expect(drawer).toHaveCount(0)

    const token = (await loginByApi(page, SEED_OWNER_EMAIL)).token
    const bad = await page.request.get(`${API_BASE_URL}/api/v1/owner/settlements/00000000-0000-0000-0000-000000000000`, { headers: { Authorization: `Bearer ${token}` } })
    expect(bad.status()).toBe(404)
    assertNoErrors()
  })

  test('shows an empty state per status and an error state with retry', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=settlements')
    await page.getByTestId('settlement-tab-disputed').click()
    await expect(page.getByRole('heading', { name: 'Chưa có kỳ đối soát nào' })).toBeVisible()

    let failing = true
    await page.route('**/api/v1/owner/settlements?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.getByTestId('settlement-tab-open').click()
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('settlement tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=settlements')
    await page.getByTestId('settlement-tab-partial').click()
    await page.getByRole('button', { name: 'Xem chi tiết kỳ 01/02/2001' }).click()
    await expect(page.getByTestId('settlement-drawer-bookings')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

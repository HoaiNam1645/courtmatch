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

/** Người chơi báo đã trả; chủ sân có thể xác nhận (confirmed) hoặc bỏ qua. */
async function reportPaid(page, playerToken, ownerToken, booking, { confirm }) {
  const payment = await (await page.request.get(`${API_BASE_URL}/api/v1/payments/by-booking/${booking.id}`, {
    headers: { Authorization: `Bearer ${playerToken}` },
  })).json()
  const reported = await page.request.post(`${API_BASE_URL}/api/v1/payments/${payment.id}/report-paid`, {
    headers: { Authorization: `Bearer ${playerToken}` }, data: { method: 'cash' },
  })
  expect(reported.status(), await reported.text()).toBe(200)
  if (confirm) {
    const confirmed = await page.request.post(`${API_BASE_URL}/api/v1/payments/${payment.id}/confirm-received`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
    })
    expect(confirmed.status(), await confirmed.text()).toBe(200)
  }
}

/** Đưa booking về quá khứ: giờ chơi đã xong và hạn thanh toán đã qua. */
function moveToPast(bookingId, hoursAgo) {
  runSql(`
    UPDATE booking_items SET start_time = now() - interval '${hoursAgo + 1} hours', end_time = now() - interval '${hoursAgo} hours'
      WHERE booking_id = '${bookingId}';
    UPDATE bookings SET payment_expires_at = CASE WHEN payment_expires_at IS NULL THEN NULL ELSE now() - interval '30 minutes' END
      WHERE id = '${bookingId}';
    UPDATE payments SET expired_at = now() - interval '30 minutes' WHERE booking_id = '${bookingId}' AND status IN ('awaiting_payment', 'payer_reported', 'rejected')`)
}

test.describe.serial('Booking đã chơi xong tự hoàn thành, hoa hồng vẫn được ghi nhận', () => {
  let venue
  let owner
  let reported
  let unreported
  let confirmedBooking

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('dựng ba booking đã chơi xong: đã báo thanh toán nhưng chưa xác nhận, chưa trả và đã xác nhận', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Tự Hoàn Thành')
    makeVenueBookable(venue.id)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    ;[reported, unreported, confirmedBooking] = await createAcceptedBookings(page, {
      ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00', '10:00'],
    })
    await reportPaid(page, player.token, owner.token, reported, { confirm: false })
    await reportPaid(page, player.token, owner.token, confirmedBooking, { confirm: true })
    moveToPast(reported.id, 6)
    moveToPast(unreported.id, 4)
    moveToPast(confirmedBooking.id, 2)

    // Playwright chạy backend với job hết hạn tắt; logic job được test ở backend (BookingAutoCompleteTest).
    // Ở đây dựng trạng thái sau khi job chạy để kiểm tra giao diện và API xác nhận muộn.
    for (const booking of [reported, confirmedBooking]) {
      runSql(`
        UPDATE bookings SET status = 'completed', completed_at = now(), payment_expires_at = NULL WHERE id = '${booking.id}';
        UPDATE booking_items SET status = 'completed' WHERE booking_id = '${booking.id}';
        INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
        SELECT b.id, v.owner_id, b.total_amount, 'percentage', 10, round(b.total_amount * 0.10, 2), 'due', now(), now() + interval '7 days'
        FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    }
    runSql(`
      UPDATE bookings SET status = 'expired' WHERE id = '${unreported.id}';
      UPDATE booking_items SET status = 'expired' WHERE booking_id = '${unreported.id}'`)
    assertNoErrors()
  })

  test('chủ sân thấy nhãn "Chưa xác nhận thu tiền" chỉ ở booking tự hoàn thành, kèm ghi chú trong chi tiết', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/bookings')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)

    const row = (booking) => page.getByTestId(`history-${booking.bookingCode}`)
    await expect(row(reported).getByTestId('history-status')).toHaveText('Đã hoàn thành')
    await expect(row(reported).getByTestId('history-unconfirmed')).toHaveText('Chưa xác nhận thu tiền')
    await expect(row(confirmedBooking).getByTestId('history-status')).toHaveText('Đã hoàn thành')
    await expect(row(confirmedBooking).getByTestId('history-unconfirmed')).toHaveCount(0)
    await expect(row(unreported).getByTestId('history-status')).toHaveText('Đã hết hạn')
    await expect(row(unreported).getByTestId('history-unconfirmed')).toHaveCount(0)

    await row(reported).getByRole('button', { name: `Xem chi tiết ${reported.bookingCode}` }).click()
    await expect(page.getByTestId('drawer-unconfirmed')).toContainText('chưa xác nhận thu tiền')
    await page.keyboard.press('Escape')
    await row(confirmedBooking).getByRole('button', { name: `Xem chi tiết ${confirmedBooking.bookingCode}` }).click()
    await expect(page.getByTestId('drawer-payment')).toBeVisible()
    await expect(page.getByTestId('drawer-unconfirmed')).toHaveCount(0)
    assertNoErrors()
  })

  test('hoa hồng của booking tự hoàn thành nằm trong công nợ, booking hết hạn thì không', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await page.getByLabel('Lọc công nợ theo sân').selectOption(venue.id)
    await expect(page.getByTestId('debt-bookings').getByRole('listitem')).toHaveCount(2)
    await expect(page.getByTestId(`debt-booking-${reported.bookingCode}`)).toBeVisible()
    await expect(page.getByTestId(`debt-booking-${confirmedBooking.bookingCode}`)).toBeVisible()
    await expect(page.getByTestId(`debt-booking-${unreported.bookingCode}`)).toHaveCount(0)
    assertNoErrors()
  })

  test('chủ sân xác nhận thu tiền muộn: thanh toán thành confirmed, booking vẫn hoàn thành và hoa hồng không đổi', async ({ page }) => {
    const owner2 = await loginByApi(page, SEED_OWNER_EMAIL)
    const before = runSql(`SELECT commission_amount FROM booking_commissions WHERE booking_id = '${reported.id}'`)
    // Mở lại hạn thanh toán để chủ sân còn xác nhận được (hạn cũ đã qua nên API từ chối, đúng 2.3.27).
    const payment = runSql(`SELECT id FROM payments WHERE booking_id = '${reported.id}'`).trim().split('\n').at(-1).trim()
    runSql(`UPDATE payments SET expired_at = now() + interval '1 hour' WHERE booking_id = '${reported.id}'`)
    const confirmed = await page.request.post(`${API_BASE_URL}/api/v1/owner/payments/${payment}/confirm`, {
      headers: { Authorization: `Bearer ${owner2.token}` },
    })
    expect(confirmed.status(), await confirmed.text()).toBe(200)
    expect((await confirmed.json()).status).toBe('confirmed')

    const after = runSql(`SELECT b.status, c.commission_amount FROM bookings b JOIN booking_commissions c ON c.booking_id = b.id WHERE b.id = '${reported.id}'`)
    expect(after).toContain('completed')
    expect(after).toContain(before.trim().split('\n').at(-1).trim())

    await page.goto('/owner/bookings')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await expect(page.getByTestId(`history-${reported.bookingCode}`).getByTestId('history-unconfirmed')).toHaveCount(0)
  })
})

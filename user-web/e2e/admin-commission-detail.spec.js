import { expect, test } from '@playwright/test'
import {
  collectPayments,
  createAcceptedBookings,
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  runSql,
  SEED_ADMIN_EMAIL,
  SEED_OWNER_EMAIL,
  shot,
  watchRuntimeErrors,
} from './helpers'

const vnd = (value) => `${Number(value).toLocaleString('vi-VN')}đ`
const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.44 admin views the reconciliation detail of a booking', () => {
  let bookings
  let commissionIds
  let startedAt

  test.beforeAll(() => {
    deleteE2eVenues()
    startedAt = runSql('SELECT now()')
  })
  test.afterAll(() => {
    deleteE2eVenues()
    runSql(`DELETE FROM audit_logs WHERE entity_type = 'booking_commission' AND created_at >= '${startedAt}'`)
  })

  test('builds four bookings: plain, voucher over commission, auto-completed unconfirmed, and one with refund + dispute + history', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Chi Tiết Đối Soát')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00', '10:00', '12:00'] })
    await collectPayments(page, { ownerToken: owner.token, playerToken: player.token, bookings: [bookings[0], bookings[1], bookings[3]] })
    // bookings[2]: người chơi báo đã trả nhưng chủ sân chưa xác nhận.
    const payment = await (await page.request.get(`${API_BASE_URL}/api/v1/payments/by-booking/${bookings[2].id}`, { headers: { Authorization: `Bearer ${player.token}` } })).json()
    expect((await page.request.post(`${API_BASE_URL}/api/v1/payments/${payment.id}/report-paid`, { headers: { Authorization: `Bearer ${player.token}` }, data: { method: 'cash' } })).status()).toBe(200)

    const settlementId = runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, status, amount_due, amount_paid, note)
      SELECT v.owner_id, '2020-04-01', '2020-04-07', 'partial', 50000, 20000, 'E2E kỳ chi tiết' FROM venues v WHERE v.id = '${venue.id}' RETURNING id`).split('\n')[0]
    const insert = (booking, commission, voucher, owed, status, settlement) => runSql(`
      UPDATE bookings SET status = 'completed', completed_at = now(), discount_amount = ${voucher} WHERE id = '${booking.id}';
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, voucher_discount, courtly_owed, status, calculated_at, due_at, settlement_id)
      SELECT b.id, v.owner_id, b.total_amount, 'percentage', 10, ${commission}, ${voucher}, ${owed}, '${status}', now(), now() + interval '4 days', ${settlement ? `'${settlement}'` : 'NULL'}
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    const total = bookings[0].totalAmount
    insert(bookings[0], total / 10, 0, 0, 'due', settlementId)
    insert(bookings[1], 0, total * 0.4, total * 0.4 - total / 10, 'due', null)
    insert(bookings[2], total / 10, 0, 0, 'due', null)
    insert(bookings[3], total / 10, 0, 0, 'adjusted', null)
    commissionIds = bookings.map((booking) => runSql(`SELECT id FROM booking_commissions WHERE booking_id = '${booking.id}'`).split('\n')[0])

    const paymentId = runSql(`SELECT id FROM payments WHERE booking_id = '${bookings[3].id}'`).split('\n')[0]
    runSql(`
      INSERT INTO refunds (payment_id, booking_id, amount, status, reason) VALUES ('${paymentId}', '${bookings[3].id}', 50000, 'confirmed', 'Sân ngưng hoạt động');
      INSERT INTO booking_disputes (booking_id, payment_id, reporter_id, category, description, status)
        SELECT '${bookings[3].id}', '${paymentId}', b.user_id, 'payment', 'Chưa nhận được sân', 'reviewing' FROM bookings b WHERE b.id = '${bookings[3].id}';
      INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, old_data, new_data, created_at)
        SELECT id, 'commission.adjust', 'booking_commission', '${commissionIds[3]}', '{"commissionAmount": 18000}'::jsonb, '{"commissionAmount": 12000}'::jsonb, now() - interval '2 hours'
        FROM users WHERE email = '${SEED_ADMIN_EMAIL}'`)
  })

  async function openDetail(page, booking) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-transactions')
    await page.getByLabel('Tìm mã booking').fill(booking.bookingCode)
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(1)
    await page.getByRole('button', { name: `Xem chi tiết ${booking.bookingCode}` }).click()
    await expect(page.getByTestId('commission-drawer')).toBeVisible()
  }

  test('plain booking: committed commission, payment, settlement period; no voucher, refund, dispute or history', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openDetail(page, bookings[0])
    const drawer = page.getByTestId('commission-drawer')
    await expect(drawer.getByTestId('drawer-code')).toHaveText(bookings[0].bookingCode)
    await expect(drawer.getByTestId('drawer-status')).toHaveText('Cần thanh toán')
    await expect(drawer.getByTestId('drawer-gross')).toHaveText(vnd(bookings[0].totalAmount))
    await expect(drawer.getByTestId('drawer-amount')).toHaveText(vnd(bookings[0].totalAmount / 10))
    await expect(drawer.getByTestId('drawer-voucher')).toHaveCount(0)
    await expect(drawer.getByTestId('drawer-owed')).toHaveCount(0)
    await expect(drawer.getByTestId('drawer-payment')).toContainText('Đã thu tiền')
    await expect(drawer.getByTestId('drawer-unconfirmed')).toHaveCount(0)
    await expect(drawer.getByTestId('drawer-settlement')).toContainText('01/04/2020 – 07/04/2020')
    await expect(drawer.getByTestId('drawer-refunds-empty')).toBeVisible()
    await expect(drawer.getByTestId('drawer-disputes-empty')).toBeVisible()
    await expect(drawer.getByTestId('drawer-history-empty')).toBeVisible()
    await shot(page, '2344-detail-desktop')
    assertNoErrors()
  })

  test('voucher booking: original price, voucher, payable, full commission offset by Courtly and the amount Courtly still owes', async ({ page }) => {
    await openDetail(page, bookings[1])
    const drawer = page.getByTestId('commission-drawer')
    const total = bookings[1].totalAmount
    await expect(drawer.getByTestId('drawer-booking-voucher')).toHaveText(`− ${vnd(total * 0.4)}`)
    await expect(drawer.getByTestId('drawer-payable')).toHaveText(vnd(total * 0.6))
    await expect(drawer.getByTestId('drawer-base')).toHaveText(vnd(total / 10))
    await expect(drawer.getByTestId('drawer-voucher')).toHaveText(`− ${vnd(total * 0.4)}`)
    await expect(drawer.getByTestId('drawer-amount')).toHaveText(vnd(0))
    await expect(drawer.getByTestId('drawer-owed')).toHaveText(vnd(total * 0.3))
    await expect(drawer.getByTestId('drawer-settlement')).toContainText('Chưa gán kỳ đối soát')
    await shot(page, '2344-detail-voucher')
  })

  test('auto-completed booking without owner confirmation is flagged', async ({ page }) => {
    await openDetail(page, bookings[2])
    const drawer = page.getByTestId('commission-drawer')
    await expect(drawer.getByTestId('drawer-unconfirmed')).toHaveText('Chưa xác nhận thu tiền')
    await expect(drawer.getByTestId('drawer-payment')).toContainText('Chờ chủ sân xác nhận')
  })

  test('refund, dispute and adjustment history are listed; Escape closes the drawer', async ({ page }) => {
    await openDetail(page, bookings[3])
    const drawer = page.getByTestId('commission-drawer')
    await expect(drawer.getByTestId('drawer-status')).toHaveText('Đã điều chỉnh')
    await expect(drawer.getByTestId('drawer-refund')).toContainText(vnd(50000))
    await expect(drawer.getByTestId('drawer-refund')).toContainText('Đã xác nhận')
    await expect(drawer.getByTestId('drawer-dispute')).toContainText('Đang xem xét')
    await expect(drawer.getByTestId('drawer-history-item')).toHaveCount(1)
    await expect(drawer.getByTestId('drawer-history-item')).toContainText('Điều chỉnh hoa hồng')
    await expect(drawer.getByTestId('drawer-history-item')).toContainText('18000 → 12000')
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('commission-drawer')).toHaveCount(0)
  })

  test('detail fits a phone and a missing commission shows an error with retry', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await openDetail(page, bookings[1])
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
    expect(overflow).toBe(false)
    await shot(page, '2344-detail-mobile')
    await page.getByRole('button', { name: 'Đóng' }).click()
    await expect(page.getByTestId('commission-drawer')).toHaveCount(0)

    // Booking bị xóa sau khi danh sách đã tải: chi tiết báo lỗi chứ không treo.
    await page.route('**/api/v1/admin/finance/commission-transactions/*', (route) => route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Không tìm thấy giao dịch đối soát.' }) }))
    await page.getByRole('button', { name: `Xem chi tiết ${bookings[1].bookingCode}` }).click()
    await expect(page.getByTestId('commission-drawer')).toContainText('Không tìm thấy giao dịch đối soát')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
  })
})

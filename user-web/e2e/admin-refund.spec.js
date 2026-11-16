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

test.describe.serial('2.3.52 admin records a direct refund and the commission follows', () => {
  let startedAt
  let bookings
  let total

  test.beforeAll(() => {
    deleteE2eVenues()
    startedAt = runSql('SELECT now()')
  })
  test.afterAll(() => {
    deleteE2eVenues()
    runSql('UPDATE court_owner_profiles SET commission_credit = 0')
    runSql(`DELETE FROM audit_logs WHERE created_at >= '${startedAt}' AND (entity_type IN ('booking_commission', 'commission_settlement', 'refunds') OR action LIKE 'refund.%')`)
  })

  const commission = (booking) => runSql(`SELECT commission_amount || '|' || status FROM booking_commissions WHERE booking_id = '${booking.id}'`)
  const paymentStatus = (booking) => runSql(`SELECT status FROM payments WHERE booking_id = '${booking.id}'`)

  test('builds three paid bookings with commissions (due, settled) and one unpaid booking', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Hoàn Tiền')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '07:00', '08:00'] })
    await collectPayments(page, { ownerToken: owner.token, playerToken: player.token, bookings: bookings.slice(0, 2) })
    total = bookings[0].totalAmount
    const insert = (booking, amount, state, dueDays) => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
      SELECT b.id, v.owner_id, ${total}, 'percentage', 10, ${amount}, '${state}', now() - interval '20 days', now() + interval '${dueDays} days'
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    insert(bookings[0], total / 10, 'due', 3)
    insert(bookings[1], total / 10, 'settled', -4)
    insert(bookings[2], total / 10, 'due', 3)
  })

  async function openDetail(page, booking) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-transactions')
    await page.getByLabel('Tìm mã booking').fill(booking.bookingCode)
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(1)
    await page.getByRole('button', { name: `Xem chi tiết ${booking.bookingCode}` }).click()
    await expect(page.getByTestId('commission-drawer')).toBeVisible()
  }

  test('a booking whose payment is not confirmed has no refund button', async ({ page }) => {
    await openDetail(page, bookings[2])
    await expect(page.getByTestId('drawer-payment')).not.toContainText('Đã thu tiền')
    await expect(page.getByTestId('drawer-record-refund')).toHaveCount(0)
  })

  test('the dialog shows what can still be refunded, validates and previews the new commission', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openDetail(page, bookings[0])
    await page.getByTestId('drawer-record-refund').click()
    const dialog = page.getByTestId('refund-dialog')
    await expect(dialog.getByTestId('refund-paid')).toHaveText(vnd(total))
    await expect(dialog.getByTestId('refund-before')).toHaveText(vnd(0))
    await expect(dialog.getByTestId('refund-refundable')).toHaveText(vnd(total))

    await dialog.getByTestId('refund-submit').click()
    await expect(dialog).toContainText('Nhập số tiền hoàn là số nguyên đồng')
    await expect(dialog).toContainText('Nhập lý do hoàn tiền')
    await dialog.getByTestId('refund-amount').fill(String(total + 1))
    await dialog.getByTestId('refund-reason').fill('ngan')
    await dialog.getByTestId('refund-submit').click()
    await expect(dialog).toContainText('phần còn có thể hoàn')
    await expect(dialog).toContainText('ít nhất 5 ký tự')
    await dialog.getByTestId('refund-amount').fill(String(total / 2))
    await expect(dialog.getByTestId('refund-preview')).toContainText(`khoảng ${vnd(total / 20)}`)
    await dialog.getByTestId('refund-amount').fill(String(total))
    await expect(dialog.getByTestId('refund-preview')).toContainText('được miễn')
    await shot(page, '2352-refund-dialog')
    expect(commission(bookings[0])).toBe(`${total / 10}.00|due`)
    assertNoErrors()
  })

  test('a partial refund lowers the commission in proportion and is listed with the payment status and history', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openDetail(page, bookings[0])
    await page.getByTestId('drawer-record-refund').click()
    const dialog = page.getByTestId('refund-dialog')
    await dialog.getByTestId('refund-amount').fill(String(total / 2))
    await dialog.getByTestId('refund-reason').fill('Sân ngưng hoạt động giữa chừng')
    await dialog.getByTestId('refund-submit').click()
    await expect(page.getByTestId('refund-dialog')).toHaveCount(0)

    const drawer = page.getByTestId('commission-drawer')
    await expect(drawer.getByTestId('drawer-notice')).toContainText(`Đã ghi nhận hoàn ${vnd(total / 2)}`)
    await expect(drawer.getByTestId('drawer-amount')).toHaveText(vnd(total / 20))
    await expect(drawer.getByTestId('drawer-status')).toHaveText('Đã điều chỉnh')
    await expect(drawer.getByTestId('drawer-payment')).toContainText('Đã hoàn một phần')
    await expect(drawer.getByTestId('drawer-refund')).toHaveCount(1)
    await expect(drawer.getByTestId('drawer-refund')).toContainText(vnd(total / 2))
    await expect(drawer.getByTestId('drawer-refund')).toContainText('Đã xác nhận')
    await expect(drawer.getByTestId('drawer-history-item').first()).toContainText('Điều chỉnh theo hoàn tiền')
    await expect(drawer.getByTestId('drawer-history-reason').first()).toContainText('Sân ngưng hoạt động giữa chừng')
    await shot(page, '2352-after-refund')
    expect(commission(bookings[0])).toBe(`${total / 20}.00|adjusted`)
    expect(paymentStatus(bookings[0])).toBe('partially_refunded')
    assertNoErrors()
  })

  test('refunding the rest waives the commission, marks the payment refunded and removes the refund button', async ({ page }) => {
    await openDetail(page, bookings[0])
    await page.getByTestId('drawer-record-refund').click()
    const dialog = page.getByTestId('refund-dialog')
    await expect(dialog.getByTestId('refund-before')).toHaveText(vnd(total / 2))
    await expect(dialog.getByTestId('refund-refundable')).toHaveText(vnd(total / 2))
    await dialog.getByTestId('refund-amount').fill(String(total / 2))
    await dialog.getByTestId('refund-reason').fill('Hoàn nốt phần còn lại')
    await dialog.getByTestId('refund-submit').click()
    const drawer = page.getByTestId('commission-drawer')
    await expect(drawer.getByTestId('drawer-status')).toHaveText('Được miễn')
    await expect(drawer.getByTestId('drawer-amount')).toHaveText(vnd(0))
    await expect(drawer.getByTestId('drawer-payment')).toContainText('Đã hoàn tiền')
    await expect(drawer.getByTestId('drawer-refund')).toHaveCount(2)
    await expect(drawer.getByTestId('drawer-record-refund')).toHaveCount(0)
    expect(commission(bookings[0])).toBe('0.00|waived')
    expect(paymentStatus(bookings[0])).toBe('refunded')
  })

  test('refunding an already settled commission keeps it settled and gives the owner a prepaid credit', async ({ page }) => {
    await openDetail(page, bookings[1])
    await expect(page.getByTestId('drawer-status')).toHaveText('Đã đối soát')
    await page.getByTestId('drawer-record-refund').click()
    const dialog = page.getByTestId('refund-dialog')
    await dialog.getByTestId('refund-amount').fill(String(total / 2))
    await expect(dialog.getByTestId('refund-preview')).toContainText('số dư trả trước')
    await dialog.getByTestId('refund-reason').fill('Hoàn một nửa sau khi đã trả hoa hồng')
    await dialog.getByTestId('refund-submit').click()
    const drawer = page.getByTestId('commission-drawer')
    await expect(drawer.getByTestId('drawer-amount')).toHaveText(vnd(total / 20))
    await expect(drawer.getByTestId('drawer-status')).toHaveText('Đã đối soát')
    expect(runSql(`SELECT commission_credit FROM court_owner_profiles WHERE user_id = (SELECT owner_id FROM venues WHERE name = 'E2E Sân Hoàn Tiền')`)).toBe(`${total / 20}.00`)
  })

  test('the owner sees the refund on the net income screen and the credit on the debt screen', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-credit')).toContainText(vnd(total / 20))
  })

  test('the refund dialog fits a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await openDetail(page, bookings[1])
    await page.getByTestId('drawer-record-refund').click()
    await expect(page.getByTestId('refund-dialog')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2352-refund-mobile')
  })
})

import { expect, test } from '@playwright/test'
import {
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

test.describe.serial('2.3.51 admin adjusts the commission debt with a reason and an audit trail', () => {
  let startedAt
  let ownerId
  let bookings
  let settlement

  test.beforeAll(() => {
    deleteE2eVenues()
    startedAt = runSql('SELECT now()')
  })
  test.afterAll(() => {
    deleteE2eVenues()
    runSql('UPDATE court_owner_profiles SET commission_credit = 0')
    runSql(`DELETE FROM audit_logs WHERE created_at >= '${startedAt}' AND (entity_type IN ('booking_commission', 'commission_settlement') OR action LIKE 'settlement.%')`)
  })

  const amount = (booking) => runSql(`SELECT commission_amount FROM booking_commissions WHERE booking_id = '${booking.id}'`)
  const status = (booking) => runSql(`SELECT status FROM booking_commissions WHERE booking_id = '${booking.id}'`)

  test('builds a period (18.000 + 12.000, 20.000 already paid), a loose commission and a settled one', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Điều Chỉnh Công Nợ')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '07:00', '08:00', '09:00'] })
    ownerId = runSql(`SELECT owner_id FROM venues WHERE id = '${venue.id}'`).split('\n')[0]
    settlement = runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, status, commission_total, amount_due, amount_paid, note)
      VALUES ('${ownerId}', '2025-01-01', '2025-01-07', 'partial', 30000, 30000, 20000, 'E2E kỳ điều chỉnh') RETURNING id`).split('\n')[0]
    const insert = (booking, amountValue, state, settlementId, dueDays) => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at, settlement_id, voucher_discount)
      SELECT b.id, v.owner_id, 180000, 'percentage', 10, ${amountValue}, '${state}', now() - interval '20 days', now() + interval '${dueDays} days', ${settlementId ? `'${settlementId}'` : 'NULL'}, 0
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    insert(bookings[0], 18000, 'due', settlement, 1)
    insert(bookings[1], 12000, 'due', settlement, 5)
    insert(bookings[2], 8000, 'due', null, 2)
    insert(bookings[3], 5000, 'settled', null, 2)
  })

  async function openDetail(page, booking) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-transactions')
    await page.getByLabel('Tìm mã booking').fill(booking.bookingCode)
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(1)
    await page.getByRole('button', { name: `Xem chi tiết ${booking.bookingCode}` }).click()
    await expect(page.getByTestId('commission-drawer')).toBeVisible()
  }

  test('a settled commission cannot be adjusted: no adjust button', async ({ page }) => {
    await openDetail(page, bookings[3])
    await expect(page.getByTestId('drawer-status')).toHaveText('Đã đối soát')
    await expect(page.getByTestId('drawer-adjust')).toHaveCount(0)
  })

  test('the dialog validates the amount and the reason and previews the effect on the period', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openDetail(page, bookings[0])
    await page.getByTestId('drawer-adjust').click()
    const dialog = page.getByTestId('adjust-dialog')
    await expect(dialog.getByTestId('adjust-current')).toHaveText(vnd(18000))
    await expect(dialog).toContainText('01/01/2025 – 07/01/2025')

    await dialog.getByTestId('adjust-submit').click()
    await expect(dialog).toContainText('Số tiền mới phải khác số hiện tại.')
    await expect(dialog).toContainText('Nhập lý do điều chỉnh')
    await dialog.getByTestId('adjust-amount').fill('12.5')
    await dialog.getByTestId('adjust-submit').click()
    await expect(dialog).toContainText('Nhập số tiền là số nguyên đồng')
    await dialog.getByTestId('adjust-amount').fill('10000')
    await expect(dialog.getByTestId('adjust-delta')).toContainText('Giảm 8.000đ')
    // Kỳ phải thu 30.000, đã thu 20.000; giảm 8.000 còn phải trả 2.000.
    await expect(dialog.getByTestId('adjust-delta')).toContainText('còn 2.000đ phải trả')
    await dialog.getByTestId('adjust-amount').fill('20000')
    await expect(dialog.getByTestId('adjust-delta')).toContainText('Tăng 2.000đ')
    await dialog.getByTestId('adjust-reason').fill('ngan')
    await dialog.getByTestId('adjust-submit').click()
    await expect(dialog).toContainText('Nhập lý do điều chỉnh (ít nhất 5 ký tự)')
    expect(amount(bookings[0])).toBe('18000.00')
    await shot(page, '2351-adjust-dialog')
    assertNoErrors()
  })

  test('reducing a commission inside the period updates the period, the history shows the reason and who did it', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openDetail(page, bookings[0])
    await page.getByTestId('drawer-adjust').click()
    const dialog = page.getByTestId('adjust-dialog')
    await dialog.getByTestId('adjust-amount').fill('10000')
    await dialog.getByTestId('adjust-reason').fill('Khách khiếu nại, giảm hoa hồng theo thỏa thuận')
    await dialog.getByTestId('adjust-submit').click()
    await expect(page.getByTestId('adjust-dialog')).toHaveCount(0)

    const drawer = page.getByTestId('commission-drawer')
    await expect(drawer.getByTestId('drawer-notice')).toContainText('từ 18.000đ thành 10.000đ')
    // Kỳ đã thu 20.000 nên hoa hồng cũ nhất, sau khi giảm còn 10.000, đã được phủ đủ và chuyển sang đối soát.
    await expect(drawer.getByTestId('drawer-status')).toHaveText('Đã đối soát')
    await expect(drawer.getByTestId('drawer-amount')).toHaveText(vnd(10000))
    await expect(drawer.getByTestId('drawer-adjust')).toHaveCount(0)
    await expect(drawer.getByTestId('drawer-settlement')).toContainText('phải thu 22.000đ')
    await expect(drawer.getByTestId('drawer-history-item')).toHaveCount(1)
    await expect(drawer.getByTestId('drawer-history-item')).toContainText('Điều chỉnh hoa hồng')
    await expect(drawer.getByTestId('drawer-history-item')).toContainText('Quan Tri Courtly')
    await expect(drawer.getByTestId('drawer-history-change')).toContainText('Hoa hồng: 18.000đ → 10.000đ')
    await expect(drawer.getByTestId('drawer-history-change')).toContainText('Trạng thái: Cần thanh toán → Đã đối soát')
    await expect(drawer.getByTestId('drawer-history-reason')).toContainText('Khách khiếu nại, giảm hoa hồng theo thỏa thuận')
    await shot(page, '2351-after-adjust')

    expect(runSql(`SELECT amount_due || '|' || adjustment_total || '|' || status FROM commission_settlements WHERE id = '${settlement}'`)).toBe('22000.00|-8000.00|partial')
    expect(amount(bookings[0])).toBe('10000.00')
    expect(status(bookings[0])).toBe('settled')
    assertNoErrors()
  })

  test('the period drawer shows the new amount and the adjusted booking', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/settlements')
    await page.getByLabel('Kỳ từ ngày').fill('2025-01-01')
    await page.getByLabel('Kỳ đến ngày').fill('2025-01-31')
    await page.getByTestId('settlement-list').getByRole('listitem').first().getByTestId('settlement-detail').click()
    const drawer = page.getByTestId('settlement-drawer')
    await expect(drawer.getByTestId('sd-due')).toHaveText(vnd(22000))
    await expect(drawer.getByTestId('sd-adjustment')).toHaveText(vnd(-8000))
    await expect(drawer.getByTestId('sd-remaining')).toHaveText(vnd(2000))
    await expect(drawer.getByTestId('sd-history-item').first()).toContainText('Điều chỉnh công nợ')

    // Điều chỉnh tiếp từ chi tiết booking: kỳ và danh sách tự làm mới.
    await drawer.getByTestId(`sd-booking-${bookings[1].bookingCode}`).getByRole('button', { name: 'Xem đối soát booking' }).click()
    await page.getByTestId('drawer-adjust').click()
    await page.getByTestId('adjust-amount').fill('11000')
    await page.getByTestId('adjust-reason').fill('Giảm theo thỏa thuận lần hai')
    await page.getByTestId('adjust-submit').click()
    await expect(page.getByTestId('drawer-notice')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('commission-drawer')).toHaveCount(0)
    // Ngăn kỳ phía sau tự làm mới: phải thu 22.000 - 1.000 = 21.000, còn lại 1.000.
    await expect(drawer.getByTestId('sd-due')).toHaveText(vnd(21000))
    await expect(drawer.getByTestId('sd-adjustment')).toHaveText(vnd(-9000))
    await expect(drawer.getByTestId('sd-remaining')).toHaveText(vnd(1000))
  })

  test('an adjustment that would drop the amount due below what was already paid is refused with a message and nothing changes', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/adjustments'] })
    await openDetail(page, bookings[1])
    await page.getByTestId('drawer-adjust').click()
    const dialog = page.getByTestId('adjust-dialog')
    // Kỳ phải thu 21.000, đã thu 20.000: bỏ hoàn toàn 11.000 sẽ còn 10.000 < 20.000.
    await dialog.getByTestId('adjust-amount').fill('0')
    await dialog.getByTestId('adjust-reason').fill('Miễn toàn bộ hoa hồng booking này')
    await dialog.getByTestId('adjust-submit').click()
    await expect(dialog.getByRole('alert')).toContainText('nho hon so da thu')
    await expect(dialog).toBeVisible()
    await page.keyboard.press('Escape')
    expect(amount(bookings[1])).toBe('11000.00')
    assertNoErrors()
  })

  test('a commission outside any period can be adjusted too and shows no period', async ({ page }) => {
    await openDetail(page, bookings[2])
    await expect(page.getByTestId('drawer-settlement')).toContainText('Chưa gán kỳ đối soát')
    await page.getByTestId('drawer-adjust').click()
    await page.getByTestId('adjust-amount').fill('9500')
    await page.getByTestId('adjust-reason').fill('Bổ sung phí phát sinh')
    await expect(page.getByTestId('adjust-delta')).toContainText('Tăng 1.500đ')
    await page.getByTestId('adjust-submit').click()
    await expect(page.getByTestId('drawer-notice')).toContainText('từ 8.000đ thành 9.500đ')
    await expect(page.getByTestId('drawer-history-reason')).toContainText('Bổ sung phí phát sinh')
    expect(status(bookings[2])).toBe('adjusted')
  })

  test('the adjusted debt shows up on the owner debt screen', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    // Công nợ tính trực tiếp từ hoa hồng từng booking chưa đối soát: 11.000 + 9.500 (booking đầu đã đối soát).
    await expect(page.getByTestId('debt-total')).toHaveText(vnd(11000 + 9500))
  })

  test('the dialog fits a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await openDetail(page, bookings[2])
    await page.getByTestId('drawer-adjust').click()
    await expect(page.getByTestId('adjust-dialog')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2351-adjust-mobile')
  })
})

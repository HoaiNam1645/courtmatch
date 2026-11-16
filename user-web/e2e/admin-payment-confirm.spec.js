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
const HOURS = ['06:00', '07:00', '08:00', '09:00', '10:00', '11:00', '12:00', '13:00']

test.describe.serial('2.3.47 admin confirms an owner commission payment', () => {
  let startedAt
  let ownerId
  let bookings
  let settlements

  test.beforeAll(() => {
    deleteE2eVenues()
    startedAt = runSql('SELECT now()')
  })
  test.afterAll(() => {
    deleteE2eVenues()
    runSql(`UPDATE court_owner_profiles SET commission_credit = 0`)
    runSql(`DELETE FROM audit_logs WHERE created_at >= '${startedAt}' AND (entity_type IN ('booking_commission', 'commission_settlement') OR action LIKE 'settlement.payment.%')`)
  })

  const status = (booking) => runSql(`SELECT status FROM booking_commissions WHERE booking_id = '${booking.id}'`)
  const reportStatus = (ref) => runSql(`SELECT status FROM commission_payment_reports WHERE reference_code = '${ref}'`)

  test('builds a period with a pending report, a general payment, an oversized report and a Courtly-owed commission', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Xác Nhận Thanh Toán')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: HOURS })
    ownerId = runSql(`SELECT owner_id FROM venues WHERE id = '${venue.id}'`).split('\n')[0]
    settlements = ['2024-09-01', '2024-09-08'].map((from, index) => runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, status, commission_total, amount_due, note)
      VALUES ('${ownerId}', '${from}', '2024-09-${index === 0 ? '07' : '14'}', 'pending_review', ${index === 0 ? 30000 : 20000}, ${index === 0 ? 30000 : 20000}, 'E2E kỳ xác nhận')
      RETURNING id`).split('\n')[0])
    const insert = (booking, amount, status, settlement, dueDays, extra = '') => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at, settlement_id ${extra ? ', voucher_discount, courtly_owed' : ''})
      SELECT b.id, v.owner_id, 180000, 'percentage', 10, ${amount}, '${status}', now() - interval '20 days', now() + interval '${dueDays} days', ${settlement ? `'${settlement}'` : 'NULL'} ${extra}
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    // Kỳ 1: hai hoa hồng 18.000 + 12.000 = 30.000. Kỳ 2: một hoa hồng 20.000.
    insert(bookings[0], 18000, 'due', settlements[0], -2)
    insert(bookings[1], 12000, 'adjusted', settlements[0], 3)
    insert(bookings[2], 20000, 'due', settlements[1], 1)
    // Ba hoa hồng chưa thuộc kỳ nào (thanh toán chung): 10.000, 15.000, 8.000.
    insert(bookings[3], 10000, 'due', null, -1)
    insert(bookings[4], 15000, 'due', null, 2)
    insert(bookings[5], 8000, 'pending', null, 6)
    // Hoa hồng đã đối soát nhưng voucher vượt hoa hồng: Courtly còn nợ chủ sân 4.000.
    insert(bookings[6], 0, 'settled', null, -5, ', 22000, 4000')
    const report = (ref, settlement, amount) => runSql(`
      INSERT INTO commission_payment_reports (owner_id, settlement_id, amount, paid_on, reference_code, status, note)
      VALUES ('${ownerId}', ${settlement ? `'${settlement}'` : 'NULL'}, ${amount}, current_date, '${ref}', 'pending_review', 'E2E ghi chú chủ sân')`)
    report('E2E-CONF-1', settlements[0], 30000)
    report('E2E-CONF-2', null, 12000)
    report('E2E-CONF-3', null, 99999)
    report('E2E-CONF-4', settlements[1], 20000)
  })

  async function open(page) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-payments')
    await page.getByLabel('Thanh toán từ ngày').fill(new Date().toISOString().slice(0, 10))
  }

  test('opens from the finance group; pending reports are shown first with owner, amount, target and proof of payment', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Thanh toán công nợ' }).click()
    await expect(page).toHaveURL('/admin/commission-payments')
    await expect(page.getByRole('heading', { name: 'Thanh toán công nợ' })).toBeVisible()
    await expect(page.getByTestId('payment-tab-pending_review')).toHaveAttribute('aria-selected', 'true')
    await page.getByLabel('Tìm thanh toán').fill('E2E-CONF')
    await expect(page.getByTestId('payment-list').getByRole('listitem')).toHaveCount(4)
    await expect(page.getByTestId('payment-tab-count-pending_review')).toHaveText('4')
    await expect(page.getByTestId('payment-total-amount')).toHaveText(vnd(30000 + 12000 + 99999 + 20000))
    const one = page.getByTestId('payment-E2E-CONF-1')
    await expect(one.getByTestId('payment-owner')).toContainText('Nguyen Huu Thang')
    await expect(one.getByTestId('payment-amount')).toHaveText(vnd(30000))
    await expect(one.getByTestId('payment-target')).toContainText('01/09/2024 – 07/09/2024')
    await expect(page.getByTestId('payment-E2E-CONF-2').getByTestId('payment-target')).toContainText('Công nợ chung')
    await shot(page, '2347-payments-desktop')
    assertNoErrors()
  })

  test('confirming the full amount of a period: dialog shows what is confirmed, period becomes paid, commissions are settled', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page)
    await page.getByLabel('Tìm thanh toán').fill('E2E-CONF-1')
    await page.getByRole('button', { name: 'Xác nhận E2E-CONF-1' }).click()
    const dialog = page.getByTestId('payment-dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.getByTestId('payment-dialog-amount')).toHaveText(vnd(30000))
    await expect(dialog).toContainText('E2E-CONF-1')
    await expect(dialog).toContainText('01/09/2024 – 07/09/2024')
    await expect(dialog).toContainText('E2E ghi chú chủ sân')
    await dialog.getByLabel('Ghi chú xác nhận').fill('Đã đối chiếu sao kê')
    await shot(page, '2347-confirm-dialog')
    await dialog.getByTestId('payment-dialog-confirm').click()
    await expect(page.getByTestId('payment-dialog')).toHaveCount(0)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã xác nhận 30.000đ')
    await expect(page.getByTestId('admin-notice')).toContainText('E2E-CONF-1')

    expect(reportStatus('E2E-CONF-1')).toBe('confirmed')
    expect(runSql(`SELECT status || '|' || amount_paid FROM commission_settlements WHERE id = '${settlements[0]}'`)).toBe('paid|30000.00')
    expect(status(bookings[0])).toBe('settled')
    expect(status(bookings[1])).toBe('settled')
    expect(status(bookings[2])).toBe('due')
    // Báo cáo đã xác nhận rời khỏi tab chờ và hiện ở tab đã xác nhận cùng người xử lý.
    await expect(page.getByTestId('payment-E2E-CONF-1')).toHaveCount(0)
    await page.getByTestId('payment-tab-confirmed').click()
    await expect(page.getByTestId('payment-E2E-CONF-1').getByTestId('payment-reviewed')).toContainText('Đã đối chiếu sao kê')
    assertNoErrors()
  })

  test('a general payment settles the oldest commissions outside any period and keeps the rest as credit; Courtly-owed is offset', async ({ page }) => {
    await open(page)
    await page.getByLabel('Tìm thanh toán').fill('E2E-CONF-2')
    await page.getByRole('button', { name: 'Xác nhận E2E-CONF-2' }).click()
    await page.getByTestId('payment-dialog-confirm').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã xác nhận 12.000đ')
    await expect(page.getByTestId('admin-notice')).toContainText('Đã bù trừ 4.000đ')

    // 12.000 + 4.000 bù trừ = 16.000: đóng hoa hồng 10.000 (cũ nhất), dư 6.000 chưa đủ 15.000.
    expect(status(bookings[3])).toBe('settled')
    expect(status(bookings[4])).toBe('due')
    expect(status(bookings[5])).toBe('pending')
    expect(runSql(`SELECT commission_credit FROM court_owner_profiles WHERE user_id = '${ownerId}'`)).toBe('6000.00')
    expect(runSql(`SELECT courtly_owed_cleared_at IS NOT NULL FROM booking_commissions WHERE booking_id = '${bookings[6].id}'`)).toBe('t')
    // Hoa hồng thuộc kỳ khác không bị chạm vào.
    expect(status(bookings[2])).toBe('due')
    await page.getByTestId('payment-tab-confirmed').click()
    await expect(page.getByTestId('payment-E2E-CONF-2').getByTestId('payment-reviewed')).toContainText('bù trừ 4.000đ')
  })

  test('the owner sees the prepaid credit and pays the rest from the debt screen', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-credit')).toContainText('6.000đ')
    // Hoa hồng chưa thuộc kỳ còn 15.000 + 8.000, trừ số dư 6.000 = 17.000 được gửi thanh toán chung.
    await page.getByTestId('pay-debt').click()
    await expect(page.getByText('Tối đa 17.000đ')).toBeVisible()
  })

  test('a report larger than the remaining debt cannot be confirmed: the server message is shown and the report stays pending', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/admin/finance/commission-payments'] })
    await open(page)
    await page.getByLabel('Tìm thanh toán').fill('E2E-CONF-3')
    await page.getByRole('button', { name: 'Xác nhận E2E-CONF-3' }).click()
    await page.getByTestId('payment-dialog-confirm').click()
    await expect(page.getByTestId('payment-dialog').getByRole('alert')).toContainText('vuot qua so con no')
    await expect(page.getByTestId('payment-dialog')).toBeVisible()
    expect(reportStatus('E2E-CONF-3')).toBe('pending_review')
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('payment-dialog')).toHaveCount(0)
    assertNoErrors()
  })

  test('from the period detail drawer an admin can confirm its pending report too', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/settlements')
    await page.getByLabel('Kỳ từ ngày').fill('2024-09-08')
    await page.getByLabel('Kỳ đến ngày').fill('2024-09-14')
    await page.getByTestId('settlement-list').getByRole('listitem').first().getByTestId('settlement-detail').click()
    const drawer = page.getByTestId('settlement-drawer')
    await expect(drawer.getByTestId('sd-pending-reports')).toBeVisible()
    await drawer.getByTestId('sd-report-confirm').click()
    await page.getByTestId('payment-dialog-confirm').click()
    await expect(drawer.getByTestId('sd-notice')).toContainText('Đã xác nhận 20.000đ')
    await expect(drawer.getByTestId('sd-status')).toHaveText('Đã thanh toán')
    await expect(drawer.getByTestId('sd-remaining')).toHaveText(vnd(0))
    await expect(drawer.getByTestId('sd-report-confirm')).toHaveCount(0)
    await expect(drawer.getByTestId('sd-history-item').first()).toContainText('Xác nhận thanh toán công nợ')
    expect(status(bookings[2])).toBe('settled')
    assertNoErrors()
  })

  test('the pending tab empties and the page fits a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await open(page)
    await page.getByLabel('Tìm thanh toán').fill('E2E-CONF')
    await expect(page.getByTestId('payment-list').getByRole('listitem')).toHaveCount(1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2347-payments-mobile')
    await page.getByRole('button', { name: 'Xác nhận E2E-CONF-3' }).click()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2347-dialog-mobile')
  })
})

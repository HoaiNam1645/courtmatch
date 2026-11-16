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

test.describe.serial('2.3.49 admin records a commission payment made outside the system', () => {
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
    runSql('UPDATE court_owner_profiles SET commission_credit = 0')
    runSql(`DELETE FROM audit_logs WHERE created_at >= '${startedAt}' AND (entity_type IN ('booking_commission', 'commission_settlement', 'commission_payment_reports') OR action LIKE 'settlement.payment.%')`)
  })

  const status = (booking) => runSql(`SELECT status FROM booking_commissions WHERE booking_id = '${booking.id}'`)
  const reportCount = () => Number(runSql(`SELECT count(*) FROM commission_payment_reports WHERE reference_code LIKE 'E2E-REC%'`))

  test('builds two periods, general debt of 25.000đ and a Courtly-owed commission', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Ghi Nhận Thanh Toán')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '07:00', '08:00', '09:00', '10:00'] })
    ownerId = runSql(`SELECT owner_id FROM venues WHERE id = '${venue.id}'`).split('\n')[0]
    settlements = ['2024-11-01', '2024-11-08'].map((from, index) => runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, status, commission_total, amount_due, note)
      VALUES ('${ownerId}', '${from}', '2024-11-${index === 0 ? '07' : '14'}', 'open', ${index === 0 ? 30000 : 20000}, ${index === 0 ? 30000 : 20000}, 'E2E kỳ ghi nhận') RETURNING id`).split('\n')[0])
    const insert = (booking, amount, status, settlement, dueDays, extra = '') => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at, settlement_id ${extra ? ', voucher_discount, courtly_owed' : ''})
      SELECT b.id, v.owner_id, 180000, 'percentage', 10, ${amount}, '${status}', now() - interval '20 days', now() + interval '${dueDays} days', ${settlement ? `'${settlement}'` : 'NULL'} ${extra}
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    insert(bookings[0], 30000, 'due', settlements[0], 3)
    insert(bookings[1], 20000, 'due', settlements[1], 3)
    insert(bookings[2], 10000, 'due', null, -1)
    insert(bookings[3], 15000, 'due', null, 4)
    insert(bookings[4], 0, 'settled', null, -5, ', 22000, 4000')
  })

  async function openDialog(page) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-payments')
    await page.getByTestId('payment-record').click()
    const dialog = page.getByTestId('record-dialog')
    await expect(dialog).toBeVisible()
    return dialog
  }

  test('the form validates every field before sending anything', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const dialog = await openDialog(page)
    await dialog.getByTestId('record-submit').click()
    await expect(dialog).toContainText('Chọn chủ sân.')
    await expect(dialog).toContainText('Nhập số tiền là số nguyên đồng')
    await expect(dialog).toContainText('Mã tham chiếu từ 3 đến 100 ký tự.')

    await dialog.getByTestId('record-owner').selectOption(ownerId)
    await dialog.getByTestId('record-amount').fill('12.5')
    await dialog.getByTestId('record-reference').fill('ab')
    await dialog.getByTestId('record-paid-on').fill('2999-01-01')
    await dialog.getByTestId('record-submit').click()
    await expect(dialog).toContainText('Nhập số tiền là số nguyên đồng')
    await expect(dialog).toContainText('Ngày thanh toán không được sau hôm nay.')
    await expect(dialog).toContainText('Mã tham chiếu từ 3 đến 100 ký tự.')
    await dialog.getByTestId('record-paid-on').fill('2020-01-01')
    await dialog.getByTestId('record-submit').click()
    await expect(dialog).toContainText('Chỉ ghi nhận thanh toán trong 365 ngày gần đây.')
    expect(reportCount()).toBe(0)
    await shot(page, '2349-record-errors')
    assertNoErrors()
  })

  test('choosing the owner lists only periods that still owe money', async ({ page }) => {
    const dialog = await openDialog(page)
    await dialog.getByTestId('record-owner').selectOption(ownerId)
    const options = dialog.getByTestId('record-settlement').locator('option')
    await expect(options).toHaveCount(3)
    await expect(options.nth(0)).toContainText('Công nợ chung')
    // Kỳ mới nhất xếp trước.
    await expect(options.nth(1)).toContainText('08/11/2024 – 14/11/2024')
    await expect(options.nth(1)).toContainText('còn 20.000đ')
    await expect(options.nth(2)).toContainText('01/11/2024 – 07/11/2024')
    await expect(options.nth(2)).toContainText('còn 30.000đ')
  })

  test('recording the full amount of a period settles it and shows "Admin ghi nhận" with the proof saved on the period', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const dialog = await openDialog(page)
    await dialog.getByTestId('record-owner').selectOption(ownerId)
    await expect(dialog.getByTestId('record-settlement').locator('option')).toHaveCount(3)
    await dialog.getByTestId('record-settlement').selectOption(settlements[0])
    await dialog.getByTestId('record-amount').fill('30000')
    await dialog.getByTestId('record-reference').fill('E2E-REC-1')
    await dialog.getByTestId('record-proof').fill('https://example.com/bien-nhan-1.png')
    await dialog.getByLabel('Ghi chú giao dịch').fill('Thu tiền mặt tại văn phòng')
    await shot(page, '2349-record-dialog')
    await dialog.getByTestId('record-submit').click()
    await expect(page.getByTestId('record-dialog')).toHaveCount(0)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã ghi nhận 30.000đ')
    await expect(page.getByTestId('admin-notice')).toContainText('E2E-REC-1')

    expect(runSql(`SELECT status || '|' || amount_paid || '|' || payment_reference || '|' || proof_url FROM commission_settlements WHERE id = '${settlements[0]}'`))
      .toBe('paid|30000.00|E2E-REC-1|https://example.com/bien-nhan-1.png')
    expect(status(bookings[0])).toBe('settled')
    await page.getByTestId('payment-tab-confirmed').click()
    await page.getByLabel('Tìm thanh toán').fill('E2E-REC-1')
    const row = page.getByTestId('payment-E2E-REC-1')
    await expect(row.getByTestId('payment-status')).toHaveText('Đã xác nhận')
    await expect(row.getByTestId('payment-recorded')).toHaveText('Admin ghi nhận')
    await expect(row).toContainText('Thu tiền mặt tại văn phòng')
    await expect(row.getByTestId('payment-target')).toContainText('01/11/2024 – 07/11/2024')
    assertNoErrors()
  })

  test('server-side refusals are shown in the dialog and nothing is created: amount over the debt, reused reference', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/admin/finance/commission-payments'] })
    const dialog = await openDialog(page)
    await dialog.getByTestId('record-owner').selectOption(ownerId)
    await dialog.getByTestId('record-amount').fill('25001')
    await dialog.getByTestId('record-reference').fill('E2E-REC-2')
    await dialog.getByTestId('record-submit').click()
    await expect(dialog.getByRole('alert')).toContainText('vuot qua so con no')
    await expect(dialog).toBeVisible()
    expect(reportCount()).toBe(1)

    await dialog.getByTestId('record-amount').fill('1000')
    await dialog.getByTestId('record-reference').fill('e2e-rec-1')
    await dialog.getByTestId('record-submit').click()
    await expect(dialog).toContainText('Ma tham chieu da duoc su dung')
    expect(reportCount()).toBe(1)
    await page.keyboard.press('Escape')
    assertNoErrors()
  })

  test('a general payment settles the oldest commission outside any period, offsets what Courtly owes and keeps the rest as credit', async ({ page }) => {
    const dialog = await openDialog(page)
    await dialog.getByTestId('record-owner').selectOption(ownerId)
    await dialog.getByTestId('record-amount').fill('12000')
    await dialog.getByTestId('record-reference').fill('E2E-REC-3')
    await dialog.getByTestId('record-submit').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã ghi nhận 12.000đ')
    await expect(page.getByTestId('admin-notice')).toContainText('Đã bù trừ 4.000đ')
    // 12.000 + 4.000 bù trừ = 16.000: đóng hoa hồng cũ nhất 10.000, dư 6.000.
    expect(status(bookings[2])).toBe('settled')
    expect(status(bookings[3])).toBe('due')
    expect(runSql(`SELECT commission_credit FROM court_owner_profiles WHERE user_id = '${ownerId}'`)).toBe('6000.00')
    expect(status(bookings[1])).toBe('due')
  })

  test('from a period drawer the owner and period are fixed and the remaining amount is pre-filled', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/settlements')
    await page.getByLabel('Kỳ từ ngày').fill('2024-11-08')
    await page.getByLabel('Kỳ đến ngày').fill('2024-11-14')
    await page.getByTestId('settlement-list').getByRole('listitem').first().getByTestId('settlement-detail').click()
    const drawer = page.getByTestId('settlement-drawer')
    await drawer.getByTestId('sd-record').click()
    const dialog = page.getByTestId('record-dialog')
    await expect(dialog.getByTestId('record-target')).toContainText('08/11/2024 – 14/11/2024')
    await expect(dialog.getByTestId('record-target')).toContainText('còn 20.000đ')
    await expect(dialog.getByTestId('record-owner')).toBeDisabled()
    await expect(dialog.getByTestId('record-amount')).toHaveValue('20000')
    await dialog.getByTestId('record-reference').fill('E2E-REC-4')
    await dialog.getByTestId('record-submit').click()
    await expect(drawer.getByTestId('sd-notice')).toContainText('Đã ghi nhận 20.000đ')
    await expect(drawer.getByTestId('sd-status')).toHaveText('Đã thanh toán')
    await expect(drawer.getByTestId('sd-record')).toHaveCount(0)
    await expect(drawer.getByTestId('sd-report')).toContainText('E2E-REC-4')
    await expect(drawer.getByTestId('sd-history-item').first()).toContainText('Xác nhận thanh toán công nợ')
    assertNoErrors()
  })

  test('the dialog fits a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await openDialog(page)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2349-record-mobile')
  })
})

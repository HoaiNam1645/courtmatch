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

const API_BASE_URL = 'http://127.0.0.1:8080'
const vnd = (value) => `${Number(value).toLocaleString('vi-VN')}đ`

test.describe.serial('2.3.48 admin rejects an owner commission payment report', () => {
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
    runSql(`DELETE FROM audit_logs WHERE created_at >= '${startedAt}' AND (entity_type IN ('commission_settlement', 'commission_payment_reports') OR action LIKE 'settlement.payment.%')`)
  })

  const reportStatus = (ref) => runSql(`SELECT status FROM commission_payment_reports WHERE reference_code = '${ref}' ORDER BY created_at DESC LIMIT 1`)

  test('builds two periods with pending reports and one general report', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Từ Chối Thanh Toán')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '07:00', '08:00', '09:00'] })
    ownerId = runSql(`SELECT owner_id FROM venues WHERE id = '${venue.id}'`).split('\n')[0]
    settlements = ['2024-10-01', '2024-10-08'].map((from, index) => runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, status, commission_total, amount_due, note)
      VALUES ('${ownerId}', '${from}', '2024-10-${index === 0 ? '07' : '14'}', 'pending_review', 20000, 20000, 'E2E kỳ từ chối') RETURNING id`).split('\n')[0])
    const insert = (booking, amount, settlement, dueDays) => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at, settlement_id)
      SELECT b.id, v.owner_id, 180000, 'percentage', 10, ${amount}, 'due', now() - interval '20 days', now() + interval '${dueDays} days', ${settlement ? `'${settlement}'` : 'NULL'}
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    insert(bookings[0], 20000, settlements[0], 5)
    insert(bookings[1], 20000, settlements[1], 5)
    insert(bookings[2], 9000, null, 5)
    const report = (ref, settlement, amount) => runSql(`
      INSERT INTO commission_payment_reports (owner_id, settlement_id, amount, paid_on, reference_code, status)
      VALUES ('${ownerId}', ${settlement ? `'${settlement}'` : 'NULL'}, ${amount}, current_date, '${ref}', 'pending_review')`)
    report('E2E-REJ-1', settlements[0], 20000)
    report('E2E-REJ-2', null, 9000)
    report('E2E-REJ-4', settlements[1], 8000)
    report('E2E-REJ-5', settlements[1], 6000)
  })

  async function open(page, term) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-payments')
    await page.getByLabel('Tìm thanh toán').fill(term)
  }

  test('rejecting needs a reason: empty and too short are blocked, quick reasons fill the field', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page, 'E2E-REJ-1')
    await page.getByRole('button', { name: 'Từ chối E2E-REJ-1' }).click()
    const dialog = page.getByTestId('payment-dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('Công nợ không thay đổi')
    await dialog.getByTestId('payment-dialog-reject').click()
    await expect(dialog).toContainText('Nhập lý do từ chối')
    await dialog.getByTestId('reject-reason').fill('ngan')
    await dialog.getByTestId('payment-dialog-reject').click()
    await expect(dialog).toContainText('Nhập lý do từ chối')
    expect(reportStatus('E2E-REJ-1')).toBe('pending_review')

    await dialog.getByRole('button', { name: 'Không thấy giao dịch trên sao kê' }).click()
    await expect(dialog.getByTestId('reject-reason')).toHaveValue('Không thấy giao dịch trên sao kê')
    await expect(dialog).not.toContainText('Nhập lý do từ chối')
    await shot(page, '2348-reject-dialog')
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('payment-dialog')).toHaveCount(0)
    assertNoErrors()
  })

  test('rejecting a period report keeps the debt, restores the period to open and shows the reason in the rejected tab', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page, 'E2E-REJ-1')
    await page.getByRole('button', { name: 'Từ chối E2E-REJ-1' }).click()
    const dialog = page.getByTestId('payment-dialog')
    await dialog.getByTestId('reject-reason').fill('Bằng chứng mờ, thiếu mã giao dịch')
    await dialog.getByTestId('payment-dialog-reject').click()
    await expect(page.getByTestId('payment-dialog')).toHaveCount(0)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã từ chối báo cáo E2E-REJ-1')
    await expect(page.getByTestId('admin-notice')).toContainText('Công nợ không thay đổi')

    expect(reportStatus('E2E-REJ-1')).toBe('rejected')
    expect(runSql(`SELECT status || '|' || amount_paid FROM commission_settlements WHERE id = '${settlements[0]}'`)).toBe('open|0.00')
    expect(runSql(`SELECT status FROM booking_commissions WHERE booking_id = '${bookings[0].id}'`)).toBe('due')
    await expect(page.getByTestId('payment-E2E-REJ-1')).toHaveCount(0)
    await page.getByTestId('payment-tab-rejected').click()
    const row = page.getByTestId('payment-E2E-REJ-1')
    await expect(row.getByTestId('payment-status')).toHaveText('Bị từ chối')
    await expect(row.getByTestId('payment-reviewed')).toContainText('Bằng chứng mờ, thiếu mã giao dịch')
    await expect(row.getByRole('button', { name: 'Xác nhận E2E-REJ-1' })).toHaveCount(0)
    await expect(row.getByRole('button', { name: 'Từ chối E2E-REJ-1' })).toHaveCount(0)
    assertNoErrors()
  })

  test('the owner sees the reason and can resubmit with the same reference code', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=payment-history')
    const rejected = page.getByTestId('payment-history-E2E-REJ-1')
    await expect(rejected.getByTestId('payment-history-status')).toHaveText('Bị từ chối')
    await expect(rejected.getByTestId('payment-history-reason')).toContainText('Bằng chứng mờ, thiếu mã giao dịch')

    const resubmit = await page.request.post(`${API_BASE_URL}/api/v1/owner/commission-payments`, {
      headers: { Authorization: `Bearer ${owner.token}` },
      data: { settlementId: settlements[0], amount: 20000, paidOn: new Date().toISOString().slice(0, 10), referenceCode: 'E2E-REJ-1', proofUrl: 'https://example.com/bang-chung.png' },
    })
    expect(resubmit.status(), await resubmit.text()).toBe(201)
    expect(runSql(`SELECT status FROM commission_settlements WHERE id = '${settlements[0]}'`)).toBe('pending_review')

    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-payments')
    await page.getByLabel('Tìm thanh toán').fill('E2E-REJ-1')
    await expect(page.getByTestId('payment-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('payment-status')).toHaveText('Chờ Courtly xác nhận')
  })

  test('a general (no period) report can be rejected too and touches no period', async ({ page }) => {
    await open(page, 'E2E-REJ-2')
    await page.getByRole('button', { name: 'Từ chối E2E-REJ-2' }).click()
    await page.getByTestId('reject-reason').fill('Sai số tiền chuyển khoản')
    await page.getByTestId('payment-dialog-reject').click()
    await expect(page.getByTestId('admin-notice')).toContainText('E2E-REJ-2')
    expect(reportStatus('E2E-REJ-2')).toBe('rejected')
    expect(runSql(`SELECT commission_credit FROM court_owner_profiles WHERE user_id = '${ownerId}'`)).toBe('0.00')
    expect(runSql(`SELECT status FROM booking_commissions WHERE booking_id = '${bookings[2].id}'`)).toBe('due')
  })

  test('from the period drawer: rejecting one of two pending reports leaves the period waiting, rejecting the second reopens it', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/settlements')
    await page.getByLabel('Kỳ từ ngày').fill('2024-10-08')
    await page.getByLabel('Kỳ đến ngày').fill('2024-10-14')
    await page.getByTestId('settlement-list').getByRole('listitem').first().getByTestId('settlement-detail').click()
    const drawer = page.getByTestId('settlement-drawer')
    await expect(drawer.getByTestId('sd-report')).toHaveCount(2)

    await drawer.getByTestId('sd-report').filter({ hasText: 'E2E-REJ-4' }).getByTestId('sd-report-reject').click()
    await page.getByTestId('reject-reason').fill('Mã tham chiếu không khớp')
    await page.getByTestId('payment-dialog-reject').click()
    await expect(drawer.getByTestId('sd-notice')).toContainText('Đã từ chối báo cáo E2E-REJ-4')
    await expect(drawer.getByTestId('sd-status')).toHaveText('Chờ Courtly xác nhận')
    await expect(drawer.getByTestId('sd-pending-reports')).toHaveText('1 báo cáo chờ xác nhận')

    await drawer.getByTestId('sd-report').filter({ hasText: 'E2E-REJ-5' }).getByTestId('sd-report-reject').click()
    await page.getByTestId('reject-reason').fill('Chưa nhận được tiền')
    await page.getByTestId('payment-dialog-reject').click()
    await expect(drawer.getByTestId('sd-status')).toHaveText('Đang mở')
    await expect(drawer.getByTestId('sd-remaining')).toHaveText(vnd(20000))
    await expect(drawer.getByTestId('sd-report').filter({ hasText: 'E2E-REJ-4' })).toContainText('Mã tham chiếu không khớp')
    await expect(drawer.getByTestId('sd-history-item').first()).toContainText('Từ chối báo cáo thanh toán')
    await shot(page, '2348-drawer-after-reject')
    assertNoErrors()
  })

  test('the reject dialog fits a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await open(page, 'E2E-REJ-1')
    await page.getByRole('button', { name: 'Từ chối E2E-REJ-1' }).click()
    await expect(page.getByTestId('payment-dialog')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2348-reject-mobile')
  })
})

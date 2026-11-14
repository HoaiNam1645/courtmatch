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

test.describe.serial('2.3.24 owner submits a commission payment to Courtly', () => {
  let venue
  let bookings
  let settlementId
  let baselineOutstanding

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('submits a general payment from the debt tab and the debt is not reduced yet', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Thanh Toán Nợ')
    makeVenueBookable(venue.id)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00'] })
    bookings.forEach((booking) => {
      runSql(`
        UPDATE bookings SET status = 'completed', completed_at = now() WHERE id = '${booking.id}';
        INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
        SELECT b.id, v.owner_id, b.total_amount, 'fixed', 10000, 10000, 'due', now() - interval '3 days', now() + interval '3 days'
        FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    })
    baselineOutstanding = Number(runSql(`SELECT coalesce(sum(commission_amount), 0) FROM booking_commissions WHERE owner_id = (SELECT owner_id FROM venues WHERE id = '${venue.id}') AND status IN ('pending','due','adjusted')`))

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await page.getByTestId('pay-debt').click()
    const dialog = page.getByTestId('commission-payment-dialog')

    // Kiểm tra phía client trước khi gọi API.
    await dialog.getByLabel('Số tiền đã thanh toán').fill('')
    await dialog.getByRole('button', { name: 'Gửi thông tin thanh toán' }).click()
    await expect(dialog.getByText('Vui lòng nhập số tiền lớn hơn 0')).toBeVisible()
    await expect(dialog.getByText('Mã tham chiếu từ 3 đến 100 ký tự')).toBeVisible()
    await dialog.getByLabel('Số tiền đã thanh toán').fill(String(baselineOutstanding + 1))
    await dialog.getByLabel('Mã tham chiếu giao dịch').fill('E2E-FT-1')
    await dialog.getByLabel('Đường dẫn ảnh chứng từ').fill('javascript:alert(1)')
    await dialog.getByRole('button', { name: 'Gửi thông tin thanh toán' }).click()
    await expect(dialog.getByText(/^Tối đa /).last()).toBeVisible()
    await expect(dialog.getByText('Đường dẫn phải bắt đầu bằng http:// hoặc https://')).toBeVisible()

    await dialog.getByLabel('Số tiền đã thanh toán').fill('15000')
    await dialog.getByLabel('Đường dẫn ảnh chứng từ').fill('https://example.com/e2e-bill.png')
    await dialog.getByLabel('Ghi chú thanh toán').fill('Chuyển khoản đợt 1')
    const created = page.waitForResponse((response) => response.url().endsWith('/owner/commission-payments') && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: 'Gửi thông tin thanh toán' }).click()
    expect((await created).status()).toBe(201)
    await expect(dialog).toHaveCount(0)
    await expect(page.getByTestId('payment-notice')).toContainText('E2E-FT-1')

    // Công nợ chưa giảm cho tới khi Courtly xác nhận.
    await expect(page.getByTestId('debt-total')).toHaveText(`${(Number(runSql(`SELECT coalesce(sum(commission_amount), 0) FROM booking_commissions WHERE owner_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}') AND status IN ('pending','due','adjusted')`))).toLocaleString('vi-VN')}đ`)
    expect(runSql("SELECT status || '|' || amount::int || '|' || coalesce(proof_url, '') FROM commission_payment_reports WHERE reference_code = 'E2E-FT-1'"))
      .toBe('pending_review|15000|https://example.com/e2e-bill.png')
    assertNoErrors()
  })

  test('shows the server error when the reference code is reused', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await page.getByTestId('pay-debt').click()
    const dialog = page.getByTestId('commission-payment-dialog')
    await dialog.getByLabel('Số tiền đã thanh toán').fill('1000')
    await dialog.getByLabel('Mã tham chiếu giao dịch').fill('e2e-ft-1')
    await dialog.getByRole('button', { name: 'Gửi thông tin thanh toán' }).click()
    await expect(dialog.getByRole('alert')).toContainText('Ma tham chieu nay da duoc dung')
    await expect(dialog.getByText('Ma tham chieu da duoc su dung')).toBeVisible()
    await dialog.getByRole('button', { name: 'Hủy' }).click()
    await expect(dialog).toHaveCount(0)
  })

  test('pays a settlement from its detail drawer', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, commission_total, amount_due, amount_paid, status, note)
      SELECT v.owner_id, date '2002-03-01', date '2002-03-31', 40000, 40000, 10000, 'overdue', 'E2E kỳ thanh toán' FROM venues v WHERE v.id = '${venue.id}'`)
    settlementId = runSql("SELECT id FROM commission_settlements WHERE note = 'E2E kỳ thanh toán'")
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=settlements')
    await page.getByTestId('settlement-tab-overdue').click()
    await page.getByRole('button', { name: 'Xem chi tiết kỳ 01/03/2002' }).click()
    await page.getByTestId('pay-settlement').click()
    const dialog = page.getByTestId('commission-payment-dialog')
    await expect(dialog).toContainText('Kỳ 01/03/2002 – 31/03/2002')
    await expect(dialog.getByLabel('Số tiền đã thanh toán')).toHaveValue('30000')
    await dialog.getByLabel('Mã tham chiếu giao dịch').fill('E2E-FT-2')
    await dialog.getByRole('button', { name: 'Gửi thông tin thanh toán' }).click()
    await expect(page.getByTestId('payment-notice')).toContainText('E2E-FT-2')
    await expect(page.getByTestId('settlement-drawer-status')).toHaveText('Chờ Courtly xác nhận')
    await expect(page.getByTestId('pay-settlement')).toHaveCount(0)
    expect(runSql(`SELECT status FROM commission_settlements WHERE id = '${settlementId}'`)).toBe('pending_review')
    assertNoErrors()
  })

  test('hides the pay button when there is no debt, and surfaces a server failure', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.route('**/api/v1/owner/commission-debt', (route) => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ asOf: new Date().toISOString(), totalOutstanding: 0, outstandingCount: 0, overdueAmount: 0, overdueCount: 0, upcomingAmount: 0, upcomingCount: 0, suspendedAmount: 0, suspendedCount: 0, settledAmount: 5000, byVenue: [] }),
    }))
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-total')).toHaveText('0đ')
    await expect(page.getByTestId('pay-debt')).toHaveCount(0)
    await page.unroute('**/api/v1/owner/commission-debt')

    await page.route('**/api/v1/owner/commission-payments', (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' }))
    await page.reload()
    await page.getByTestId('pay-debt').click()
    const dialog = page.getByTestId('commission-payment-dialog')
    await dialog.getByLabel('Số tiền đã thanh toán').fill('1000')
    await dialog.getByLabel('Mã tham chiếu giao dịch').fill('E2E-FT-3')
    await dialog.getByRole('button', { name: 'Gửi thông tin thanh toán' }).click()
    await expect(dialog.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    await expect(dialog).toBeVisible()
  })

  test('payment dialog fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await page.getByTestId('pay-debt').click()
    await expect(page.getByTestId('commission-payment-dialog')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

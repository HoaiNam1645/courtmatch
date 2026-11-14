import { expect, test } from '@playwright/test'
import { deleteE2eVenues, loginByApi, runSql, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const ownerId = () => runSql(`SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}'`)

test.describe.serial('2.3.25 owner views the commission payment status', () => {
  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('summarises pending, confirmed and rejected submissions and the unpaid periods', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const baseline = await (await page.request.get(`${API_BASE_URL}/api/v1/owner/commission-payments/status`, { headers: { Authorization: `Bearer ${owner.token}` } })).json()
    runSql(`
      INSERT INTO commission_settlements (id, owner_id, period_from, period_to, commission_total, amount_due, amount_paid, status, note)
      VALUES ('11111111-1111-4111-8111-111111111111', '${ownerId()}', date '2003-04-01', date '2003-04-30', 40000, 40000, 10000, 'partial', 'E2E kỳ 2003');
      INSERT INTO commission_payment_reports (owner_id, settlement_id, amount, paid_on, reference_code, status, review_note, created_at) VALUES
        ('${ownerId()}', '11111111-1111-4111-8111-111111111111', 6000, current_date - 3, 'E2E-S1', 'pending_review', NULL, now() - interval '3 hours'),
        ('${ownerId()}', NULL, 20000, current_date - 9, 'E2E-S2', 'confirmed', NULL, now() - interval '2 hours'),
        ('${ownerId()}', NULL, 4000, current_date - 1, 'E2E-S3', 'rejected', 'Không thấy giao dịch trên sao kê', now() - interval '1 hour')`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    const card = page.getByTestId('payment-status')
    await expect(card.getByTestId('status-pending')).toHaveText(`${(Number(baseline.pendingAmount) + 6000).toLocaleString('vi-VN')}đ`)
    await expect(card.getByTestId('status-confirmed')).toHaveText(`${(Number(baseline.confirmedAmount) + 20000).toLocaleString('vi-VN')}đ`)
    await expect(card.getByTestId('status-rejected')).toHaveText(`${baseline.rejectedCount + 1} lần`)
    await expect(card.getByTestId('status-last-badge')).toHaveText('Bị từ chối')
    await expect(card.getByTestId('status-last')).toContainText('E2E-S3')
    await expect(card.getByTestId('status-last-reason')).toContainText('Không thấy giao dịch trên sao kê')
    const period = card.getByTestId('status-settlement-2003-04-01')
    await expect(period).toContainText('còn 30.000đ · chờ xác nhận 6.000đ')
    await expect(period).toContainText('Đã trả một phần')
    assertNoErrors()
  })

  test('shows the latest submission as pending right after the owner submits a payment', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    runSql(`DELETE FROM commission_payment_reports WHERE reference_code LIKE 'E2E-S%'`)
    await page.goto('/owner/finance?tab=settlements')
    await page.getByTestId('settlement-tab-partial').click()
    await page.getByRole('button', { name: 'Xem chi tiết kỳ 01/04/2003' }).click()
    await page.getByTestId('pay-settlement').click()
    const dialog = page.getByTestId('commission-payment-dialog')
    await dialog.getByLabel('Số tiền đã thanh toán').fill('5000')
    await dialog.getByLabel('Mã tham chiếu giao dịch').fill('E2E-S9')
    await dialog.getByRole('button', { name: 'Gửi thông tin thanh toán' }).click()
    await expect(page.getByTestId('payment-notice')).toBeVisible()
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('status-last-badge')).toHaveText('Chờ Courtly xác nhận')
    await expect(page.getByTestId('status-last')).toContainText('E2E-S9')
  })

  test('shows the empty message when nothing was ever submitted, and an error state with retry', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.route('**/api/v1/owner/commission-payments/status', (route) => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ totalOutstanding: 0, pendingAmount: 0, pendingCount: 0, confirmedAmount: 0, confirmedCount: 0, rejectedCount: 0, lastPayment: null, unpaidSettlements: [] }),
    }))
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('status-none')).toBeVisible()
    await expect(page.getByTestId('status-settlements')).toHaveCount(0)
    await page.unroute('**/api/v1/owner/commission-payments/status')

    let failing = true
    await page.route('**/api/v1/owner/commission-payments/status', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.reload()
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('payment-status')).toBeVisible()
  })

  test('status card fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('payment-status')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

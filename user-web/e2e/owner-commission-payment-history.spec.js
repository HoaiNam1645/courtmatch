import { expect, test } from '@playwright/test'
import { deleteE2eVenues, loginByApi, runSql, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const ownerId = () => runSql(`SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}'`)

test.describe.serial('2.3.26 owner views the commission payment history', () => {
  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('lists submissions newest first and filters by status and payment date', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    runSql(`
      INSERT INTO commission_settlements (id, owner_id, period_from, period_to, commission_total, amount_due, amount_paid, status, note)
      VALUES ('22222222-2222-4222-8222-222222222222', '${ownerId()}', date '2004-05-01', date '2004-05-31', 9000, 9000, 0, 'open', 'E2E kỳ 2004');
      INSERT INTO commission_payment_reports (owner_id, settlement_id, amount, paid_on, reference_code, status, review_note, reviewed_at, proof_url, note, created_at) VALUES
        ('${ownerId()}', NULL, 5000, date '2004-06-01', 'E2E-H1', 'confirmed', NULL, now() - interval '1 day', NULL, NULL, now() - interval '3 days'),
        ('${ownerId()}', '22222222-2222-4222-8222-222222222222', 9000, date '2004-06-05', 'E2E-H2', 'rejected', 'Sai số tiền', now() - interval '1 hour', 'https://example.com/h2.png', 'Đợt hai', now() - interval '2 days'),
        ('${ownerId()}', NULL, 1000, date '2004-06-09', 'E2E-H3', 'pending_review', NULL, NULL, NULL, NULL, now() - interval '1 day')`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Tài chính' }).click()
    await page.getByRole('tab', { name: 'Lịch sử thanh toán' }).click()
    await expect(page).toHaveURL('/owner/finance?tab=payment-history')

    // Lọc theo khoảng ngày để chỉ còn 3 dòng của test.
    await page.getByLabel('Thanh toán từ ngày').fill('2004-06-01')
    await page.getByLabel('Thanh toán đến ngày').fill('2004-06-30')
    const items = page.getByTestId('payment-history').getByRole('listitem')
    await expect(items).toHaveCount(3)
    await expect(page.getByTestId('payment-history-total')).toHaveText('15.000đ')
    await expect(items.nth(0)).toContainText('E2E-H3')
    await expect(items.nth(1)).toContainText('E2E-H2')
    await expect(items.nth(2)).toContainText('E2E-H1')

    const rejected = page.getByTestId('payment-history-E2E-H2')
    await expect(rejected.getByTestId('payment-history-status')).toHaveText('Bị từ chối')
    await expect(rejected.getByTestId('payment-history-amount')).toHaveText('9.000đ')
    await expect(rejected).toContainText('kỳ 01/05/2004 – 31/05/2004')
    await expect(rejected.getByTestId('payment-history-reason')).toContainText('Sai số tiền')
    await expect(rejected).toContainText('Ghi chú: Đợt hai')
    await expect(rejected.getByRole('link', { name: 'Xem chứng từ' })).toHaveAttribute('href', 'https://example.com/h2.png')
    await expect(page.getByTestId('payment-history-E2E-H3')).toContainText('vào công nợ chung')

    await page.getByTestId('payment-history-tab-confirmed').click()
    await expect(items).toHaveCount(1)
    await expect(page.getByTestId('payment-history-total')).toHaveText('5.000đ')
    await page.getByTestId('payment-history-tab-pending_review').click()
    await expect(items).toHaveCount(1)
    await expect(items.first()).toContainText('E2E-H3')

    await page.getByLabel('Thanh toán từ ngày').fill('2004-06-06')
    await page.getByTestId('payment-history-tab-all').click()
    await expect(items).toHaveCount(1)
    assertNoErrors()
  })

  test('rejects an inverted date range without calling the API, and shows empty and error states', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=payment-history')
    let calls = 0
    page.on('request', (request) => { if (request.url().includes('/owner/commission-payments?')) calls += 1 })
    await page.getByLabel('Thanh toán từ ngày').fill('2004-06-20')
    await page.getByLabel('Thanh toán đến ngày').fill('2004-06-10')
    await expect(page.getByRole('alert')).toContainText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.')
    const before = calls
    await page.waitForTimeout(300)
    expect(calls).toBe(before)

    await page.getByLabel('Thanh toán từ ngày').fill('1999-01-01')
    await page.getByLabel('Thanh toán đến ngày').fill('1999-12-31')
    await expect(page.getByRole('heading', { name: 'Chưa có lần thanh toán nào' })).toBeVisible()

    let failing = true
    await page.route('**/api/v1/owner/commission-payments?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.getByTestId('payment-history-tab-rejected').click()
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('payment history fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=payment-history')
    await page.getByLabel('Thanh toán từ ngày').fill('2004-06-01')
    await page.getByLabel('Thanh toán đến ngày').fill('2004-06-30')
    await expect(page.getByTestId('payment-history-E2E-H2')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

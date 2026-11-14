import { expect, test } from '@playwright/test'
import { loginByApi, runSql, SEED_ADMIN_EMAIL, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const ownerRow = () => runSql(`SELECT verification_status || '|' || coalesce(verification_note, '') FROM court_owner_profiles WHERE user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`)

test.describe.serial('2.3.32 admin manages the single owner account', () => {
  let original

  test.beforeAll(() => {
    original = runSql(`SELECT verification_status FROM court_owner_profiles WHERE user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`)
  })
  test.afterAll(() => {
    runSql(`UPDATE court_owner_profiles SET verification_status = '${original}', verification_note = NULL, verified_by = NULL WHERE user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`)
    runSql("DELETE FROM audit_logs WHERE action = 'owner.verification.review' AND created_at > now() - interval '1 hour'")
  })

  test('shows the owner account, business profile and overview', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Chủ sân' }).click()
    await expect(page).toHaveURL('/admin/owner')
    await expect(page.getByTestId('owner-account-status')).toHaveText('Đang hoạt động')
    await expect(page.getByTestId('owner-verification')).toBeVisible()
    const venues = Number(runSql(`SELECT count(*) FROM venues WHERE owner_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}') AND approval_status = 'approved'`))
    await expect(page.getByTestId('overview-approved')).toHaveText(String(venues))
    await expect(page.getByTestId('overview-bookings')).toBeVisible()
    await expect(page.getByTestId('overview-commission')).toContainText('đ')
    assertNoErrors()
  })

  test('rejects the verification with a reason, then verifies again', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/owner')
    runSql(`UPDATE court_owner_profiles SET verification_status = 'verified', verification_note = NULL WHERE user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`)
    await page.reload()
    await expect(page.getByTestId('owner-verification')).toHaveText('Đã xác minh')
    await expect(page.getByTestId('owner-review-verified')).toHaveCount(0)

    await page.getByTestId('owner-review-rejected').click()
    await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
    await expect(page.getByText('Vui lòng nhập lý do (ít nhất 3 ký tự)')).toBeVisible()
    await page.getByLabel('Lý do xác minh').fill('Thiếu giấy phép kinh doanh')
    const reviewed = page.waitForResponse((response) => response.url().endsWith('/verification') && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
    expect((await reviewed).status()).toBe(200)
    await expect(page.getByTestId('owner-verification')).toHaveText('Bị từ chối')
    await expect(page.getByTestId('owner-verification-note')).toContainText('Thiếu giấy phép kinh doanh')
    await expect(page.getByTestId('owner-verified-by')).toHaveText('Quan Tri Courtly')
    await expect(page.getByTestId('admin-notice')).toContainText('Bị từ chối')
    expect(ownerRow()).toBe('rejected|Thiếu giấy phép kinh doanh')

    await page.getByTestId('owner-review-verified').click()
    await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
    await expect(page.getByTestId('owner-verification')).toHaveText('Đã xác minh')
    await expect(page.getByTestId('owner-verification-note')).toHaveCount(0)
    expect(ownerRow()).toBe('verified|')
    assertNoErrors()
  })

  test('shows a conflict when the status changed elsewhere, the empty state, and an error state', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/owner')
    await page.getByTestId('owner-review-pending').click()
    await page.getByLabel('Lý do xác minh').fill('Cần bổ sung hồ sơ')
    runSql(`UPDATE court_owner_profiles SET verification_status = 'pending' WHERE user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`)
    await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('Ho so da o trang thai nay')

    await page.route('**/api/v1/admin/owner', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ owner: null, profile: null, overview: null }) }))
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Hệ thống chưa có tài khoản chủ sân' })).toBeVisible()
    await page.unroute('**/api/v1/admin/owner')

    let failing = true
    await page.route('**/api/v1/admin/owner', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.reload()
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('owner-account-status')).toBeVisible()

    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const mobile = await context.newPage()
    await loginByApi(mobile, SEED_ADMIN_EMAIL)
    await mobile.goto('/admin/owner')
    await expect(mobile.getByTestId('overview-pending')).toBeVisible()
    const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })

  test('the owner cannot open the admin owner page', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/admin/owner')
    await expect(page.getByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeVisible()
  })
})

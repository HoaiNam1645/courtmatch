import { expect, test } from '@playwright/test'
import { deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const seedOwnerId = () => runSql(`SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}'`)

/** Hệ thống chỉ có một chủ sân: tạm gỡ vai trò của chủ sân seed để thử luồng cấp tài khoản, rồi trả lại. */
test.describe.serial('2.3.33 admin creates, locks and resets the owner account', () => {
  let seedId

  test.beforeAll(() => {
    deleteE2eUsers()
    seedId = seedOwnerId()
    runSql(`DELETE FROM user_roles WHERE user_id = '${seedId}' AND role_id = (SELECT id FROM roles WHERE code = 'owner')`)
  })
  test.afterAll(() => {
    deleteE2eUsers()
    runSql(`INSERT INTO user_roles (user_id, role_id) SELECT '${seedId}', id FROM roles WHERE code = 'owner' ON CONFLICT DO NOTHING`)
    runSql("DELETE FROM audit_logs WHERE action IN ('owner.account.create', 'owner.credentials.reset') AND created_at > now() - interval '1 hour'")
  })

  test('creates the owner account, shows the temporary password once and forces a password change', async ({ page }) => {
    // Lần gửi đầu cố ý trùng email nên 409 là kết quả mong đợi.
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/admin/owner/account'] })
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/owner')
    await expect(page.getByRole('heading', { name: 'Hệ thống chưa có tài khoản chủ sân' })).toBeVisible()

    const form = page.getByTestId('owner-create-form')
    await form.getByRole('button', { name: 'Cấp tài khoản chủ sân' }).click()
    await expect(form.getByText('Vui lòng nhập họ tên (ít nhất 2 ký tự)')).toBeVisible()
    await expect(form.getByText('Email không đúng định dạng')).toBeVisible()
    await form.getByLabel('Họ tên chủ sân').fill('E2E Chủ Sân Mới')
    await form.getByLabel('Email chủ sân').fill('an.nguyen@example.com')
    await form.getByLabel('Số điện thoại chủ sân').fill('0912345678')
    await form.getByRole('button', { name: 'Cấp tài khoản chủ sân' }).click()
    await expect(form.getByText('Email da ton tai')).toBeVisible()

    await form.getByLabel('Email chủ sân').fill('e2e-chusan@example.com')
    const created = page.waitForResponse((response) => response.url().endsWith('/owner/account') && response.request().method() === 'POST')
    await form.getByRole('button', { name: 'Cấp tài khoản chủ sân' }).click()
    const response = await created
    expect(response.status()).toBe(201)
    expect(response.headers()['cache-control']).toContain('no-store')

    const box = page.getByTestId('temporary-password-box')
    const password = await page.getByTestId('temporary-password').innerText()
    expect(password).toHaveLength(12)
    await expect(box).toContainText('e2e-chusan@example.com')
    await expect(page.getByTestId('owner-name')).toHaveText('E2E Chủ Sân Mới')
    await expect(page.getByTestId('owner-verification')).toHaveText('Chờ xác minh')
    await expect(page.getByTestId('owner-temp-flag')).toBeVisible()

    const login = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: 'e2e-chusan@example.com', password } })
    expect(login.status()).toBe(200)
    const body = await login.json()
    expect(body.user.passwordChangeRequired).toBe(true)
    const owned = await page.request.get(`${API_BASE_URL}/api/v1/owner/profile`, { headers: { Authorization: `Bearer ${body.accessToken}` } })
    expect(owned.status()).toBe(403)

    await page.getByTestId('temporary-password-dismiss').click()
    await expect(box).toHaveCount(0)
    await page.reload()
    await expect(page.getByTestId('temporary-password-box')).toHaveCount(0)
    assertNoErrors()
  })

  test('resets the credentials: the old password stops working and a new temporary one appears once', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/owner')
    await page.getByTestId('owner-reset-open').click()
    await expect(page.getByTestId('owner-reset-confirm')).toContainText('Mật khẩu cũ và phiên đăng nhập hiện tại sẽ hết hiệu lực')
    await page.getByTestId('owner-reset-confirm-button').click()
    const password = await page.getByTestId('temporary-password').innerText()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã đặt lại mật khẩu tạm')

    const old = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: 'e2e-chusan@example.com', password: 'Courtly@123' } })
    expect(old.status()).toBe(401)
    const fresh = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: 'e2e-chusan@example.com', password } })
    expect(fresh.status()).toBe(200)
    expect(runSql("SELECT count(*) FROM audit_logs WHERE action = 'owner.credentials.reset' AND created_at > now() - interval '5 minutes'")).toBe('1')
    assertNoErrors()
  })

  test('locks the owner with a reason and reactivates; the owner is blocked while locked', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/owner')
    await page.getByTestId('owner-status-banned').click()
    await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
    await expect(page.getByText('Vui lòng nhập lý do (ít nhất 3 ký tự)')).toBeVisible()
    await page.getByLabel('Lý do đổi trạng thái chủ sân').fill('Nợ hoa hồng quá hạn')
    await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
    await expect(page.getByTestId('owner-account-status')).toHaveText('Bị khóa')
    await expect(page.getByTestId('owner-last-change')).toContainText('Lý do: Nợ hoa hồng quá hạn')
    const blocked = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: 'e2e-chusan@example.com', password: 'Courtly@123' } })
    expect([401, 403]).toContain(blocked.status())
    expect(runSql("SELECT status FROM users WHERE email = 'e2e-chusan@example.com'")).toBe('banned')

    await page.getByTestId('owner-status-active').click()
    await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
    await expect(page.getByTestId('owner-account-status')).toHaveText('Đang hoạt động')
    assertNoErrors()
  })

  test('shows the server error when creation conflicts, and fits a mobile screen', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.route('**/api/v1/admin/owner/account/reset-credentials', (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' }))
    await page.goto('/admin/owner')
    await page.getByTestId('owner-reset-open').click()
    await page.getByTestId('owner-reset-confirm-button').click()
    await expect(page.getByRole('alert').filter({ hasText: 'Hệ thống đang gặp sự cố' })).toBeVisible()
    await expect(page.getByTestId('temporary-password-box')).toHaveCount(0)

    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const mobile = await context.newPage()
    await loginByApi(mobile, SEED_ADMIN_EMAIL)
    await mobile.goto('/admin/owner')
    await expect(mobile.getByTestId('owner-account-actions')).toBeVisible()
    const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

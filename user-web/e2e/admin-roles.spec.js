import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.29 admin assigns roles', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    createE2eUser({ tag: 'vaitro', name: 'E2E Vai Trò', role: 'player' })
    createE2eUser({ tag: 'vaitro2', name: 'E2E Vai Trò Hai', role: 'player' })
  })
  test.afterAll(deleteE2eUsers)

  async function openUser(page, tag, name) {
    await page.goto('/admin/users')
    await page.getByLabel('Tìm tài khoản').fill(`e2e-${tag}@`)
    await page.getByRole('button', { name: `Xem chi tiết ${name}`, exact: true }).click()
    return page.getByTestId('user-drawer')
  }

  test('assigns and removes a role, the new role appears in the next login token', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    const drawer = await openUser(page, 'vaitro', 'E2E Vai Trò')
    const roles = drawer.getByTestId('user-roles')
    await expect(roles.getByTestId('role-chip-player')).toBeVisible()
    await expect(roles.getByRole('button', { name: 'Gỡ vai trò Người chơi' })).toBeDisabled()

    await roles.getByLabel('Chọn vai trò để gán').selectOption('staff')
    const assigned = page.waitForResponse((response) => response.url().endsWith('/roles') && response.request().method() === 'POST')
    await roles.getByTestId('role-assign').click()
    expect((await assigned).status()).toBe(200)
    await expect(roles.getByTestId('role-chip-staff')).toBeVisible()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã gán vai trò Nhân viên')

    const login = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: 'e2e-vaitro@example.com', password: 'Courtly@123' } })
    expect((await login.json()).user.roles).toEqual(['player', 'staff'])
    expect(runSql("SELECT count(*) FROM audit_logs WHERE action = 'user.role.assign' AND entity_id = (SELECT id FROM users WHERE email = 'e2e-vaitro@example.com')")).toBe('1')

    await roles.getByRole('button', { name: 'Gỡ vai trò Nhân viên' }).click()
    await expect(roles.getByTestId('role-chip-staff')).toHaveCount(0)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã gỡ vai trò Nhân viên')
    assertNoErrors()
  })

  test('shows the server message when only one owner is allowed', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    const drawer = await openUser(page, 'vaitro2', 'E2E Vai Trò Hai')
    const roles = drawer.getByTestId('user-roles')
    await roles.getByLabel('Chọn vai trò để gán').selectOption('owner')
    await roles.getByTestId('role-assign').click()
    await expect(roles.getByRole('alert')).toContainText('He thong chi co mot chu san')
    await expect(roles.getByTestId('role-chip-owner')).toHaveCount(0)
  })

  test('an admin cannot remove their own admin role, and the roles list fails gracefully', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.route('**/api/v1/admin/roles', (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' }))
    await page.goto('/admin/users')
    await page.getByLabel('Tìm tài khoản').fill(SEED_ADMIN_EMAIL)
    await page.getByRole('button', { name: 'Xem chi tiết Quan Tri Courtly' }).click()
    await expect(page.getByTestId('user-roles')).toContainText('Không tải được danh mục vai trò.')
    await page.unroute('**/api/v1/admin/roles')

    const token = (await loginByApi(page, SEED_ADMIN_EMAIL)).token
    const adminId = runSql(`SELECT id FROM users WHERE email = '${SEED_ADMIN_EMAIL}'`)
    const response = await page.request.delete(`${API_BASE_URL}/api/v1/admin/users/${adminId}/roles/admin`, { headers: { Authorization: `Bearer ${token}` } })
    expect(response.status()).toBe(409)
  })

  test('role section fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_ADMIN_EMAIL)
    const drawer = await openUser(page, 'vaitro', 'E2E Vai Trò')
    await expect(drawer.getByTestId('user-roles')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

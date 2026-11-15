import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.28 admin manages user accounts', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    createE2eUser({ tag: 'quanly', name: 'E2E Nguyễn Quản Lý', role: 'player' })
    createE2eUser({ tag: 'owner', name: 'E2E Chủ Thử', role: 'owner' })
    createE2eUser({ tag: 'pending', name: 'E2E Chưa Xác Minh', role: 'player', status: 'pending' })
  })
  test.afterAll(deleteE2eUsers)

  test('lists accounts, searches without diacritics and filters by role and status', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Tài khoản' }).click()
    await expect(page).toHaveURL('/admin/users')

    await page.getByLabel('Tìm tài khoản').fill('nguyen quan ly')
    await expect(page.getByTestId('user-list').getByRole('listitem')).toHaveCount(1)
    const row = page.getByTestId('user-e2e-quanly@example.com')
    await expect(row).toContainText('E2E Nguyễn Quản Lý')
    await expect(row).toContainText('Người chơi')
    await expect(row.getByTestId('user-status')).toHaveText('Đang hoạt động')

    await page.getByLabel('Tìm tài khoản').fill('e2e-')
    await page.getByLabel('Lọc theo vai trò').selectOption('owner')
    await expect(page.getByTestId('user-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('user-e2e-owner@example.com')).toBeVisible()
    await page.getByLabel('Lọc theo vai trò').selectOption('all')
    await page.getByTestId('user-tab-pending').click()
    await expect(page.getByTestId('user-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('user-e2e-pending@example.com').getByTestId('user-status')).toHaveText('Chưa xác minh')

    await page.getByTestId('user-tab-all').click()
    await page.getByLabel('Tìm tài khoản').fill('khong-ton-tai-xyz')
    await expect(page.getByRole('heading', { name: 'Không tìm thấy tài khoản nào' })).toBeVisible()
    assertNoErrors()
  })

  test('bans an account with a reason, the user is blocked, then reactivates', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const player = await loginByApi(page, 'e2e-quanly@example.com')
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/users')
    await page.getByLabel('Tìm tài khoản').fill('e2e-quanly')
    await page.getByRole('button', { name: 'Xem chi tiết E2E Nguyễn Quản Lý' }).click()
    const drawer = page.getByTestId('user-drawer')
    await expect(drawer.getByTestId('user-drawer-status')).toHaveText('Đang hoạt động')
    await expect(drawer).toContainText('e2e-quanly@example.com')

    await drawer.getByTestId('user-action-banned').click()
    await drawer.getByRole('button', { name: 'Xác nhận: Khóa tài khoản' }).click()
    await expect(drawer.getByText('Vui lòng nhập lý do (ít nhất 3 ký tự)')).toBeVisible()
    await drawer.getByLabel('Lý do đổi trạng thái').fill('Gian lận đặt sân')
    const changed = page.waitForResponse((response) => response.url().endsWith('/status') && response.request().method() === 'POST')
    await drawer.getByRole('button', { name: 'Xác nhận: Khóa tài khoản' }).click()
    expect((await changed).status()).toBe(200)
    await expect(drawer.getByTestId('user-drawer-status')).toHaveText('Bị khóa')
    await expect(drawer.getByTestId('user-drawer-last-change')).toContainText('Lý do: Gian lận đặt sân')
    await expect(page.getByTestId('admin-notice')).toContainText('Bị khóa')

    // Người dùng bị chặn: token cũ không dùng được và đăng nhập mới bị từ chối.
    const me = await page.request.get(`${API_BASE_URL}/api/v1/auth/me`, { headers: { Authorization: `Bearer ${player.token}` } })
    expect(me.status()).toBe(403)
    const login = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: 'e2e-quanly@example.com', password: 'Courtly@123' } })
    expect(login.status()).toBe(403)
    expect(runSql("SELECT count(*) FROM audit_logs WHERE action = 'user.status.change' AND entity_id = (SELECT id FROM users WHERE email = 'e2e-quanly@example.com')")).toBe('1')

    await drawer.getByTestId('user-action-active').click()
    await drawer.getByRole('button', { name: 'Xác nhận: Kích hoạt lại' }).click()
    await expect(drawer.getByTestId('user-drawer-status')).toHaveText('Đang hoạt động')
    const again = await page.request.get(`${API_BASE_URL}/api/v1/auth/me`, { headers: { Authorization: `Bearer ${player.token}` } })
    expect(again.status()).toBe(200)
    assertNoErrors()
  })

  test('hides unavailable actions: a pending account cannot be activated, the admin cannot lock themself', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/users')
    await page.getByLabel('Tìm tài khoản').fill('e2e-pending')
    await page.getByRole('button', { name: 'Xem chi tiết E2E Chưa Xác Minh' }).click()
    const drawer = page.getByTestId('user-drawer')
    await expect(drawer.getByTestId('user-action-banned')).toBeVisible()
    await expect(drawer.getByTestId('user-action-active')).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.getByLabel('Tìm tài khoản').fill(SEED_ADMIN_EMAIL)
    await page.getByRole('button', { name: 'Xem chi tiết Quan Tri Courtly' }).click()
    await expect(page.getByTestId('user-drawer-self')).toBeVisible()
    await expect(page.getByTestId('user-action-banned')).toHaveCount(0)
  })

  test('shows a server conflict when the account changed elsewhere, and an error state with retry', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/users')
    await page.getByLabel('Tìm tài khoản').fill('e2e-owner')
    // Chờ tìm kiếm (trễ 300ms) áp dụng xong, nếu không yêu cầu muộn sẽ đè lên trạng thái lỗi ở dưới.
    await expect(page.getByTestId('user-list').getByRole('listitem')).toHaveCount(1)
    await page.getByRole('button', { name: 'Xem chi tiết E2E Chủ Thử' }).click()
    const drawer = page.getByTestId('user-drawer')
    await drawer.getByTestId('user-action-inactive').click()
    await drawer.getByLabel('Lý do đổi trạng thái').fill('Tạm ngừng hợp tác')
    runSql("UPDATE users SET status = 'inactive' WHERE email = 'e2e-owner@example.com'")
    await drawer.getByRole('button', { name: 'Xác nhận: Tạm ngừng' }).click()
    await expect(drawer.getByRole('alert')).toContainText('Tai khoan da o trang thai nay')
    await page.keyboard.press('Escape')

    let failing = true
    await page.route('**/api/v1/admin/users?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.getByTestId('user-tab-banned').click()
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('only administrators can open the admin area; users page fits a mobile screen', async ({ page, browser }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeVisible()

    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const mobile = await context.newPage()
    await loginByApi(mobile, SEED_ADMIN_EMAIL)
    await mobile.goto('/admin/users')
    await mobile.getByLabel('Tìm tài khoản').fill('e2e-')
    await expect(mobile.getByTestId('user-list')).toBeVisible()
    const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

import { expect, test } from '@playwright/test'
import { loginByApi, runSql, SEED_ADMIN_EMAIL, watchRuntimeErrors } from './helpers'

const staffHas = (code) => runSql(`SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id WHERE r.code = 'staff' AND p.code = '${code}'`) === '1'

test.describe.serial('2.3.30 admin manages role permissions', () => {
  let originalStaffHadStats

  test.beforeAll(() => { originalStaffHadStats = staffHas('stats.system.view') })
  test.afterAll(() => {
    // Trả quyền của vai trò nhân viên về như lúc bắt đầu.
    if (staffHas('stats.system.view') !== originalStaffHadStats) {
      runSql(originalStaffHadStats
        ? "INSERT INTO role_permissions SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'staff' AND p.code = 'stats.system.view'"
        : "DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE code = 'staff') AND permission_id = (SELECT id FROM permissions WHERE code = 'stats.system.view')")
    }
  })

  test('shows the matrix and locks the core admin permissions', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Vai trò & quyền' }).click()
    await expect(page).toHaveURL('/admin/permissions')
    const matrix = page.getByTestId('permission-matrix')
    await expect(matrix.getByTestId('permission-row-venue.approve')).toBeVisible()
    await expect(matrix.getByLabel('Quản trị viên: Quan ly tai khoan nguoi dung')).toBeDisabled()
    await expect(matrix.getByLabel('Quản trị viên: Quan ly tai khoan nguoi dung')).toBeChecked()
    await expect(matrix.getByLabel('Quản trị viên: Xac minh tai khoan chu san')).toBeEnabled()
    await expect(page.getByTestId('permission-changes')).toHaveCount(0)
    assertNoErrors()
  })

  test('toggles a permission, confirms the change and persists it', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/permissions')
    const box = page.getByLabel('Nhân viên: Xem thong ke he thong')
    const before = await box.isChecked()
    await box.click()
    await expect(page.getByTestId('permission-changes')).toContainText('1 vai trò có thay đổi chưa lưu')
    await page.getByTestId('permission-save').click()
    const confirm = page.getByTestId('permission-confirm')
    await expect(confirm).toContainText('Nhân viên')
    await expect(confirm).toContainText(before ? 'bỏ: Xem thong ke he thong' : 'thêm: Xem thong ke he thong')
    const saved = page.waitForResponse((response) => response.url().includes('/permissions/roles/staff') && response.request().method() === 'PUT')
    await page.getByTestId('permission-confirm-save').click()
    expect((await saved).status()).toBe(200)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã lưu quyền của 1 vai trò')
    await expect(page.getByTestId('permission-changes')).toHaveCount(0)
    expect(staffHas('stats.system.view')).toBe(!before)
    expect(runSql("SELECT count(*) FROM audit_logs WHERE action = 'role.permissions.update' AND created_at > now() - interval '1 minute'")).toBe('1')

    await page.reload()
    await expect(page.getByLabel('Nhân viên: Xem thong ke he thong')).toBeChecked({ checked: !before })
    assertNoErrors()
  })

  test('undo discards unsaved edits, and a server failure keeps the draft with an error', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/permissions')
    const box = page.getByLabel('Người chơi: Xem lich su thanh toan')
    const before = await box.isChecked()
    await box.click()
    await expect(page.getByTestId('permission-changes')).toBeVisible()
    await page.getByRole('button', { name: 'Hoàn tác' }).click()
    await expect(page.getByTestId('permission-changes')).toHaveCount(0)
    await expect(box).toBeChecked({ checked: before })

    await page.route('**/permissions/roles/player', (route) => route.fulfill({ status: 409, contentType: 'application/json', body: '{"code":"PERMISSION_ASSIGNMENT_INVALID","message":"Khong the go quyen nay.","fieldErrors":{"permissions":"Khong the go quyen nay"}}' }))
    await box.click()
    await page.getByTestId('permission-save').click()
    await page.getByTestId('permission-confirm-save').click()
    await expect(page.getByRole('alert').first()).toContainText('Khong the go quyen nay')
    await expect(page.getByTestId('permission-changes')).toBeVisible()
    expect(runSql("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id WHERE r.code = 'player' AND p.code = 'payment.view'")).toBe(before ? '1' : '0')
  })

  test('shows an error state with retry and fits a mobile screen', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    let failing = true
    await page.route('**/api/v1/admin/permissions', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/admin/permissions')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('permission-matrix')).toBeVisible()

    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const mobile = await context.newPage()
    await loginByApi(mobile, SEED_ADMIN_EMAIL)
    await mobile.goto('/admin/permissions')
    await expect(mobile.getByTestId('permission-matrix')).toBeVisible()
    const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

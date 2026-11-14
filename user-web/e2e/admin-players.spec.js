import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.31 admin manages player accounts', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    createE2eUser({ tag: 'choi1', name: 'E2E Người Chơi Một', role: 'player' })
    createE2eUser({ tag: 'choi2', name: 'E2E Người Chơi Hai', role: 'player' })
    createE2eUser({ tag: 'chuthu', name: 'E2E Chủ Không Phải Người Chơi', role: 'owner' })
    runSql(`
      INSERT INTO player_profiles (user_id, skill_level, skill_score)
      SELECT id, 'advanced', 4.5 FROM users WHERE email = 'e2e-choi1@example.com';
      INSERT INTO reports (reporter_id, target_type, target_id, reason, description)
      SELECT (SELECT id FROM users WHERE email = 'an.nguyen@example.com'), 'user', u.id, 'abusive', 'E2E'
      FROM users u WHERE u.email = 'e2e-choi1@example.com'`)
  })
  test.afterAll(() => {
    runSql("DELETE FROM reports WHERE description = 'E2E'")
    deleteE2eUsers()
  })

  test('lists only players, searches and filters by skill and registration date', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Người chơi' }).click()
    await expect(page).toHaveURL('/admin/players')

    await page.getByLabel('Tìm người chơi').fill('e2e')
    await expect(page.getByTestId('player-e2e-choi1@example.com')).toBeVisible()
    await expect(page.getByTestId('player-e2e-choi2@example.com')).toBeVisible()
    await expect(page.getByTestId('player-e2e-chuthu@example.com')).toHaveCount(0)
    await page.getByLabel('Lọc theo trình độ').selectOption('advanced')
    await expect(page.getByTestId('player-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('player-e2e-choi1@example.com')).toContainText('Khá')
    await page.getByLabel('Lọc theo trình độ').selectOption('all')

    await page.getByLabel('Đăng ký từ ngày').fill('2999-01-01')
    await expect(page.getByRole('heading', { name: 'Không tìm thấy người chơi nào' })).toBeVisible()
    await page.getByLabel('Đăng ký đến ngày').fill('2998-01-01')
    await expect(page.getByRole('alert')).toContainText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.')
    assertNoErrors()
  })

  test('opens a player, shows stats and reports, locks with a reason and unlocks', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const player = await loginByApi(page, 'e2e-choi1@example.com')
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/players')
    await page.getByLabel('Tìm người chơi').fill('e2e-choi1')
    await page.getByRole('button', { name: 'Xem chi tiết E2E Người Chơi Một' }).click()
    const drawer = page.getByTestId('player-drawer')
    await expect(drawer).toContainText('Khá · 4.5 điểm')
    await expect(drawer.getByTestId('player-report-count')).toHaveText('1')
    await expect(drawer.getByTestId('player-booking-count')).toHaveText('0')
    await expect(drawer.getByTestId('player-no-bookings')).toBeVisible()
    await expect(drawer.getByTestId('player-unlock')).toHaveCount(0)

    await drawer.getByTestId('player-lock').click()
    await drawer.getByRole('button', { name: 'Xác nhận khóa' }).click()
    await expect(drawer.getByText('Vui lòng nhập lý do (ít nhất 3 ký tự)')).toBeVisible()
    await drawer.getByLabel('Lý do khóa').fill('Bị báo cáo nhiều lần')
    const locked = page.waitForResponse((response) => response.url().endsWith('/lock') && response.request().method() === 'POST')
    await drawer.getByRole('button', { name: 'Xác nhận khóa' }).click()
    expect((await locked).status()).toBe(200)
    await expect(drawer.getByTestId('player-drawer-status')).toHaveText('Bị khóa')
    await expect(drawer.getByTestId('player-drawer-last-change')).toContainText('Lý do: Bị báo cáo nhiều lần')
    await expect(page.getByTestId('admin-notice')).toContainText('Đã khóa tài khoản E2E Người Chơi Một')
    const me = await page.request.get(`${API_BASE_URL}/api/v1/auth/me`, { headers: { Authorization: `Bearer ${player.token}` } })
    expect(me.status()).toBe(403)

    await drawer.getByTestId('player-unlock').click()
    await expect(drawer.getByTestId('player-drawer-status')).toHaveText('Đang hoạt động')
    await expect(page.getByTestId('admin-notice')).toContainText('Đã mở khóa tài khoản')
    await expect(drawer.getByTestId('player-lock')).toBeVisible()
    assertNoErrors()
  })

  test('shows the conflict when the status changed elsewhere, an error state, and fits a mobile screen', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/players')
    await page.getByLabel('Tìm người chơi').fill('e2e-choi2')
    await page.getByRole('button', { name: 'Xem chi tiết E2E Người Chơi Hai' }).click()
    const drawer = page.getByTestId('player-drawer')
    await drawer.getByTestId('player-lock').click()
    await drawer.getByLabel('Lý do khóa').fill('Gian lận')
    runSql("UPDATE users SET status = 'banned' WHERE email = 'e2e-choi2@example.com'")
    await drawer.getByRole('button', { name: 'Xác nhận khóa' }).click()
    await expect(drawer.getByRole('alert')).toContainText('Tai khoan da o trang thai nay')
    await page.keyboard.press('Escape')

    let failing = true
    await page.route('**/api/v1/admin/players?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.getByLabel('Lọc theo trạng thái').selectOption('banned')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)

    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const mobile = await context.newPage()
    await loginByApi(mobile, SEED_ADMIN_EMAIL)
    await mobile.goto('/admin/players')
    await mobile.getByLabel('Tìm người chơi').fill('e2e')
    await expect(mobile.getByTestId('player-list')).toBeVisible()
    const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })

  test('owners cannot open the admin players page', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/admin/players')
    await expect(page.getByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeVisible()
  })
})

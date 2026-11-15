import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const TAG = 'E2E-REPORT'

function addReport({ reporter, target, reason, status = 'pending', description = TAG, minutesAgo = 5 }) {
  const targetSql = target ? `(SELECT id FROM users WHERE email = '${target}')` : 'gen_random_uuid()'
  return runSql(`
    INSERT INTO reports (reporter_id, target_type, target_id, reason, description, status, created_at)
    VALUES ((SELECT id FROM users WHERE email = '${reporter}'), 'user', ${targetSql}, '${reason}', '${description}', '${status}', now() - interval '${minutesAgo} minutes')
    RETURNING id`).split('\n')[0]
}

test.describe.serial('2.3.36 admin handles reported users', () => {
  let ids = {}

  test.beforeAll(() => {
    runSql(`DELETE FROM reports WHERE description LIKE '${TAG}%'`)
    deleteE2eUsers()
    createE2eUser({ tag: 'bc-nguoi-bao', name: 'E2E Người Báo Cáo', role: 'player' })
    createE2eUser({ tag: 'bc-vi-pham-1', name: 'E2E Bị Báo Cáo Một', role: 'player' })
    createE2eUser({ tag: 'bc-vi-pham-2', name: 'E2E Bị Báo Cáo Hai', role: 'player' })
    createE2eUser({ tag: 'bc-vi-pham-3', name: 'E2E Bị Báo Cáo Ba', role: 'player' })
    const reporter = 'e2e-bc-nguoi-bao@example.com'
    ids = {
      spam: addReport({ reporter, target: 'e2e-bc-vi-pham-1@example.com', reason: 'Spam tin nhắn', description: `${TAG} gửi tin nhắn quảng cáo liên tục`, minutesAgo: 10 }),
      cheat: addReport({ reporter, target: 'e2e-bc-vi-pham-2@example.com', reason: 'Gian lận kết quả', minutesAgo: 20 }),
      spam2: addReport({ reporter, target: 'e2e-bc-vi-pham-1@example.com', reason: 'Quấy rối', minutesAgo: 30 }),
      ban: addReport({ reporter, target: 'e2e-bc-vi-pham-3@example.com', reason: 'Lừa đảo', minutesAgo: 40 }),
      closed: addReport({ reporter, target: 'e2e-bc-vi-pham-2@example.com', reason: 'Spam tin nhắn', status: 'resolved', minutesAgo: 600 }),
      orphan: addReport({ reporter, target: null, reason: 'Giả mạo', minutesAgo: 50 }),
    }
  })
  test.afterAll(() => {
    runSql(`DELETE FROM audit_logs WHERE entity_type = 'reports' AND entity_id IN (SELECT id FROM reports WHERE description LIKE '${TAG}%')`)
    runSql(`DELETE FROM reports WHERE description LIKE '${TAG}%'`)
    deleteE2eUsers()
  })

  test('opens from the sidebar, shows status tabs with counts and the newest open reports first', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Báo cáo người dùng' }).click()
    await expect(page).toHaveURL('/admin/user-reports')
    await expect(page.getByRole('heading', { name: 'Người dùng bị báo cáo' })).toBeVisible()

    await page.getByLabel('Tìm báo cáo').fill('e2e')
    const list = page.getByTestId('report-list')
    await expect(list.getByRole('listitem')).toHaveCount(5)
    // Số đếm theo tab là toàn hệ thống (kể cả báo cáo có sẵn), nên so với số liệu trong database.
    const count = (status) => runSql(`SELECT count(*) FROM reports WHERE target_type = 'user' AND status = '${status}'`)
    await expect(page.getByTestId('report-tab-pending')).toHaveText(new RegExp(`Chờ xử lý${count('pending')}$`))
    await expect(page.getByTestId('report-tab-resolved')).toHaveText(new RegExp(`Đã xử lý${count('resolved')}$`))
    await expect(page.getByTestId('report-tab-reviewing')).toHaveText(new RegExp(`Đang xử lý${count('reviewing')}$`))
    // Mới nhất trước: spam (10 phút) rồi cheat (20 phút).
    await expect(list.getByRole('listitem').nth(0)).toHaveAttribute('data-testid', `report-${ids.spam}`)
    await expect(list.getByRole('listitem').nth(1)).toHaveAttribute('data-testid', `report-${ids.cheat}`)
    await expect(page.getByTestId(`report-${ids.orphan}`)).toContainText('Tài khoản không còn tồn tại')
    await shot(page, '2336-list-desktop')

    await page.getByTestId('report-tab-resolved').click()
    await expect(list.getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId(`report-${ids.closed}`).getByTestId('report-status')).toHaveText('Đã xử lý')
    await page.getByTestId('report-tab-all').click()
    await expect(list.getByRole('listitem')).toHaveCount(6)
    assertNoErrors()
  })

  test('filters by reason and by keyword (accent-insensitive), shows empty and clears the search', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/user-reports')
    await page.getByTestId('report-tab-all').click()
    await page.getByLabel('Tìm báo cáo').fill('e2e')
    const list = page.getByTestId('report-list')
    await expect(list.getByRole('listitem')).toHaveCount(6)

    await page.getByLabel('Lọc theo lý do').selectOption('Spam tin nhắn')
    await expect(list.getByRole('listitem')).toHaveCount(2)
    await page.getByLabel('Lọc theo lý do').selectOption('all')
    await page.getByLabel('Tìm báo cáo').fill('bi bao cao hai')
    await expect(list.getByRole('listitem')).toHaveCount(2)
    await page.getByLabel('Tìm báo cáo').fill('e2e-bc-nguoi-bao@example.com')
    await expect(list.getByRole('listitem')).toHaveCount(6)
    await page.getByLabel('Tìm báo cáo').fill('e2e-bc-vi-pham-3@example.com')
    await expect(list.getByRole('listitem')).toHaveCount(1)
    await page.getByLabel('Tìm báo cáo').fill('khong-co-ket-qua-nay')
    await expect(page.getByText('Không có báo cáo nào')).toBeVisible()
    await page.getByRole('button', { name: 'Xóa tìm kiếm' }).click()
    await expect(page.getByLabel('Tìm báo cáo')).toHaveValue('')
  })

  test('detail shows reporter, reported account, total received and the other reports about the same account', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/user-reports')
    await page.getByLabel('Tìm báo cáo').fill('e2e')
    await page.getByTestId(`report-${ids.spam}`).getByRole('button', { name: /Xem báo cáo về/ }).click()

    const drawer = page.getByTestId('report-drawer')
    await expect(drawer.getByTestId('report-drawer-status')).toHaveText('Chờ xử lý')
    await expect(drawer.getByTestId('report-drawer-reason')).toHaveText('Spam tin nhắn')
    await expect(drawer.getByTestId('report-drawer-description')).toContainText('quảng cáo liên tục')
    await expect(drawer.getByTestId('report-drawer-target')).toContainText('E2E Bị Báo Cáo Một')
    await expect(drawer.getByTestId('report-target-status')).toContainText('Đang hoạt động')
    await expect(drawer.getByTestId('report-total-received')).toHaveText('Đã nhận 2 báo cáo')
    await expect(drawer.getByTestId('report-related')).toContainText('Quấy rối')
    await expect(drawer.getByTestId('report-start')).toBeVisible()
    await expect(drawer.getByTestId('report-resolve-open')).toHaveCount(0)
    await shot(page, '2336-detail-pending')
    await page.keyboard.press('Escape')
    await expect(drawer).toHaveCount(0)
    assertNoErrors()
  })

  test('takes a report, validates the conclusion form, then rejects it with a note and cannot be changed again', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/user-reports')
    await page.getByLabel('Tìm báo cáo').fill('e2e')
    await page.getByTestId(`report-${ids.cheat}`).getByRole('button', { name: /Xem báo cáo về/ }).click()
    const drawer = page.getByTestId('report-drawer')
    await drawer.getByTestId('report-start').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã nhận xử lý báo cáo')
    await expect(drawer.getByTestId('report-drawer-status')).toHaveText('Đang xử lý')
    await expect(drawer.getByTestId('report-start')).toHaveCount(0)

    await drawer.getByTestId('report-reject-open').click()
    await drawer.getByTestId('report-conclude-submit').click()
    await expect(drawer.getByText('Vui lòng nhập ghi chú (ít nhất 3 ký tự)')).toBeVisible()
    await drawer.getByTestId('report-note').fill('ab')
    await drawer.getByTestId('report-conclude-submit').click()
    await expect(drawer.getByText('Vui lòng nhập ghi chú (ít nhất 3 ký tự)')).toBeVisible()
    await shot(page, '2336-reject-form')
    await drawer.getByTestId('report-note').fill('Không có bằng chứng cho thấy gian lận')
    await drawer.getByTestId('report-conclude-submit').click()

    await expect(page.getByTestId('admin-notice')).toContainText('Đã bác bỏ báo cáo')
    await expect(drawer.getByTestId('report-drawer-status')).toHaveText('Đã bác bỏ')
    await expect(drawer.getByTestId('report-handling-note')).toHaveText('Không có bằng chứng cho thấy gian lận')
    await expect(drawer.getByTestId('report-handling-action')).toHaveCount(0)
    await expect(drawer.getByTestId('report-resolve-open')).toHaveCount(0)
    await expect(drawer.getByTestId('report-reject-open')).toHaveCount(0)
    await shot(page, '2336-detail-rejected')
    expect(runSql(`SELECT status || '|' || coalesce(handling_action, 'none') FROM reports WHERE id = '${ids.cheat}'`)).toBe('rejected|none')
    assertNoErrors()
  })

  test('resolves with a warning: account stays active, action and note are recorded', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/user-reports')
    await page.getByLabel('Tìm báo cáo').fill('e2e')
    await page.getByTestId(`report-${ids.spam2}`).getByRole('button', { name: /Xem báo cáo về/ }).click()
    const drawer = page.getByTestId('report-drawer')
    await drawer.getByTestId('report-start').click()
    await drawer.getByTestId('report-resolve-open').click()
    await drawer.getByTestId('report-conclude-submit').click()
    await expect(drawer.getByText('Vui lòng chọn hướng xử lý')).toBeVisible()
    await shot(page, '2336-resolve-form')
    await drawer.getByTestId('report-action-warning').check()
    await drawer.getByTestId('report-note').fill('Đã nhắc nhở người dùng không quấy rối')
    await drawer.getByTestId('report-conclude-submit').click()

    await expect(drawer.getByTestId('report-drawer-status')).toHaveText('Đã xử lý')
    await expect(drawer.getByTestId('report-handling-action')).toHaveText('Hướng xử lý: Cảnh cáo')
    await expect(drawer.getByTestId('report-target-status')).toContainText('Đang hoạt động')
    expect(runSql("SELECT status FROM users WHERE email = 'e2e-bc-vi-pham-1@example.com'")).toBe('active')
    assertNoErrors()
  })

  test('resolves by banning the account: it is locked, can no longer log in and the report records the action', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/user-reports')
    await page.getByLabel('Tìm báo cáo').fill('e2e')
    await page.getByTestId(`report-${ids.ban}`).getByRole('button', { name: /Xem báo cáo về/ }).click()
    const drawer = page.getByTestId('report-drawer')
    await drawer.getByTestId('report-start').click()
    await drawer.getByTestId('report-resolve-open').click()
    await drawer.getByTestId('report-action-account_banned').check()
    await drawer.getByTestId('report-note').fill('Lừa đảo nhiều người chơi')
    await drawer.getByTestId('report-conclude-submit').click()

    await expect(drawer.getByTestId('report-drawer-status')).toHaveText('Đã xử lý')
    await expect(drawer.getByTestId('report-handling-action')).toHaveText('Hướng xử lý: Khóa tài khoản')
    await expect(drawer.getByTestId('report-target-status')).toContainText('Bị khóa')
    expect(runSql("SELECT status FROM users WHERE email = 'e2e-bc-vi-pham-3@example.com'")).toBe('banned')
    const login = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: 'e2e-bc-vi-pham-3@example.com', password: 'Courtly@123' } })
    expect(login.ok()).toBeFalsy()
    expect(runSql(`SELECT count(*) FROM audit_logs WHERE entity_type = 'users' AND action = 'user.status.change' AND new_data->>'reason' LIKE 'Xu ly bao cao%'`)).toBe('1')

    // Báo cáo khác về tài khoản đã khóa: không còn chọn được khóa / ngừng.
    await page.keyboard.press('Escape')
    await page.getByTestId('report-tab-all').click()
    await expect(page.getByTestId(`report-${ids.ban}`).getByTestId('report-target-name')).toContainText('Đã khóa')
    assertNoErrors()
  })

  test('disables account actions for a missing account but still lets the admin conclude with a warning', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/user-reports')
    await page.getByLabel('Tìm báo cáo').fill('e2e')
    await page.getByTestId(`report-${ids.orphan}`).getByRole('button', { name: /Xem báo cáo về/ }).click()
    const drawer = page.getByTestId('report-drawer')
    await expect(drawer.getByTestId('report-target-missing')).toBeVisible()
    await drawer.getByTestId('report-start').click()
    await drawer.getByTestId('report-resolve-open').click()
    await expect(drawer.getByTestId('report-action-account_banned')).toBeDisabled()
    await expect(drawer.getByTestId('report-action-account_inactive')).toBeDisabled()
    await expect(drawer.getByText('Tài khoản không còn tồn tại.').first()).toBeVisible()
    await drawer.getByTestId('report-action-other').check()
    await drawer.getByTestId('report-note').fill('Tài khoản đã bị xóa, ghi nhận để tham khảo')
    await drawer.getByTestId('report-conclude-submit').click()
    await expect(drawer.getByTestId('report-drawer-status')).toHaveText('Đã xử lý')
    assertNoErrors()
  })

  test('shows an error state with retry, and a conflict message when the report was concluded elsewhere', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    let fail = true
    await page.route('**/api/v1/admin/user-reports?**', async (route) => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) })
      else await route.continue()
    })
    await page.goto('/admin/user-reports')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
    fail = false
    await page.getByRole('button', { name: /Thử lại/ }).click()
    await expect(page.getByTestId('report-tab-all')).toBeVisible()

    runSql(`UPDATE reports SET status = 'pending', handling_action = NULL, handling_note = NULL, handled_at = NULL, handled_by = NULL WHERE id = '${ids.closed}'`)
    await page.getByTestId('report-tab-all').click()
    await page.getByLabel('Tìm báo cáo').fill('e2e')
    await page.getByTestId(`report-${ids.closed}`).getByRole('button', { name: /Xem báo cáo về/ }).click()
    const drawer = page.getByTestId('report-drawer')
    await expect(drawer.getByTestId('report-start')).toBeVisible()
    runSql(`UPDATE reports SET status = 'reviewing' WHERE id = '${ids.closed}'`)
    await drawer.getByTestId('report-start').click()
    await expect(drawer.getByText('Chi nhan xu ly duoc bao cao dang cho xu ly.')).toBeVisible()
    await expect(drawer.getByTestId('report-resolve-open')).toBeVisible()
  })

  test('list and drawer fit a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/user-reports')
    await page.getByTestId('report-tab-all').click()
    await page.getByLabel('Tìm báo cáo').fill('e2e')
    await expect(page.getByTestId('report-list')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(page, '2336-list-mobile')
    await page.getByTestId(`report-${ids.spam}`).getByRole('button', { name: /Xem báo cáo về/ }).click()
    await expect(page.getByTestId('report-drawer-status')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    expect(await page.getByTestId('report-drawer').evaluate((el) => el.scrollWidth <= el.clientWidth)).toBeTruthy()
    await shot(page, '2336-detail-mobile')
    assertNoErrors()
    await context.close()
  })

  test('players and owners cannot open the page', async ({ page }) => {
    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto('/admin/user-reports')
    await expect(page.getByRole('heading', { name: 'Người dùng bị báo cáo' })).toHaveCount(0)
  })
})

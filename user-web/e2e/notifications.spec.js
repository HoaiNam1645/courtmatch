import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'

const PLAYER = 'e2e-notif-player@example.com'
const EMPTY = 'e2e-notif-empty@example.com'

function insertNotification(email, { type, title, content = 'Nội dung E2E', route = '/profile', minutesAgo, read = false }) {
  runSql(`
    INSERT INTO notifications (user_id, type, title, content, data_json, read_at, created_at)
    SELECT id, '${type}', '${title}', '${content}', '{"route": "${route}"}'::jsonb, ${read ? 'now()' : 'NULL'}, now() - interval '${minutesAgo} minutes'
    FROM users WHERE email = '${email}'`)
}

test.describe.serial('2.3.61 - 2.3.62 notifications history, bell and preferences', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    createE2eUser({ tag: 'notif-player', name: 'E2E Người Nhận Thông Báo' })
    createE2eUser({ tag: 'notif-empty', name: 'E2E Chưa Có Thông Báo' })
    // 25 thông báo: 15 xác nhận đặt sân (5 đã đọc), 6 thanh toán, 4 nhắc nhở; mới nhất là "E2E Thanh toán 0".
    for (let i = 0; i < 15; i += 1) insertNotification(PLAYER, { type: 'booking_confirmed', title: `E2E Đặt sân ${i}`, minutesAgo: 100 + i, read: i % 3 === 0 })
    for (let i = 0; i < 6; i += 1) insertNotification(PLAYER, { type: 'payment_status', title: `E2E Thanh toán ${i}`, minutesAgo: 1 + i })
    for (let i = 0; i < 4; i += 1) insertNotification(PLAYER, { type: 'booking_reminder', title: `E2E Nhắc ${i}`, minutesAgo: 50 + i })
    insertNotification(SEED_OWNER_EMAIL, { type: 'payment_status', title: 'E2E Thông báo cho chủ sân', route: '/owner/payments', minutesAgo: 0 })
    insertNotification(SEED_ADMIN_EMAIL, { type: 'payment_status', title: 'E2E Thông báo cho quản trị', route: '/admin/commission-payments', minutesAgo: 0 })
  })
  test.afterAll(() => {
    runSql("DELETE FROM notifications WHERE title LIKE 'E2E %'")
    runSql(`DELETE FROM notification_preferences WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'e2e-notif-%')`)
    deleteE2eUsers()
  })

  const unreadInDb = (email) => Number(runSql(`SELECT count(*) FROM notifications WHERE read_at IS NULL AND user_id = (SELECT id FROM users WHERE email = '${email}')`))

  test('the bell shows the unread count and the six newest notifications, newest first', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, PLAYER)
    await page.goto('/profile')
    // 25 thông báo, 5 đã đọc.
    await expect(page.getByTestId('bell-count')).toHaveText('20')
    await page.getByTestId('bell').click()
    const items = page.getByTestId('bell-list').getByRole('listitem')
    await expect(items).toHaveCount(6)
    await expect(items.first()).toContainText('E2E Thanh toán 0')
    await expect(items.nth(5)).toContainText('E2E Thanh toán 5')
    await expect(page.getByTestId('bell-panel')).toContainText('Thanh toán')
    await shot(page, '2361-bell-panel')
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('bell-panel')).toHaveCount(0)
    assertNoErrors()
  })

  test('clicking a notification in the bell marks it read and opens its screen', async ({ page }) => {
    await loginByApi(page, PLAYER)
    await page.goto('/')
    await page.getByTestId('bell').click()
    await page.getByTestId('bell-list').getByRole('listitem').first().getByRole('button').click()
    await expect(page).toHaveURL(/\/profile/)
    expect(unreadInDb(PLAYER)).toBe(19)
    await expect(page.getByTestId('bell-count')).toHaveText('19')
  })

  test('the history page lists 20 per page with pagination, type filter, unread-only filter and clear', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, PLAYER)
    await page.goto('/notifications')
    await expect(page.getByRole('heading', { name: 'Thông báo' })).toBeVisible()
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(20)
    await expect(page.getByTestId('notif-total')).toContainText('25 thông báo')
    await expect(page.getByTestId('notif-unread-total')).toHaveText('19')
    await expect(page.getByTestId('notif-title').first()).toHaveText('E2E Thanh toán 0')
    await page.getByRole('button', { name: 'Trang sau' }).click()
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(5)
    await expect(page.getByText('Trang 2/2')).toBeVisible()

    await page.getByLabel('Lọc theo loại').selectOption('booking_reminder')
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(4)
    await expect(page.getByTestId('notif-type').first()).toHaveText('Nhắc đặt sân')
    await page.getByLabel('Lọc theo loại').selectOption('booking_confirmed')
    await page.getByTestId('notif-unread-only').check()
    // 15 xác nhận, 5 đã đọc (i chia hết cho 3) nên 10 chưa đọc.
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(10)
    await expect(page.getByTestId('notif-total')).toContainText('10 thông báo')
    await page.getByTestId('notif-clear').click()
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(20)
    await shot(page, '2361-history-desktop')
    assertNoErrors()
  })

  test('opening an item from the history marks it read and navigates; mark-all-read by type only touches that type', async ({ page }) => {
    await loginByApi(page, PLAYER)
    await page.goto('/notifications')
    await page.getByLabel('Lọc theo loại').selectOption('booking_reminder')
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(4)
    await page.getByTestId('notif-read-all').click()
    await expect(page.getByTestId('notif-notice')).toContainText('Đã đánh dấu 4 thông báo')
    await expect(page.getByTestId('notif-unread')).toHaveCount(0)
    expect(unreadInDb(PLAYER)).toBe(15)
    // Bấm lại khi loại này đã đọc hết: báo không có gì thay đổi, không lỗi.
    await page.getByTestId('notif-read-all').click()
    await expect(page.getByTestId('notif-notice')).toHaveText('Không có thông báo chưa đọc.')
    expect(unreadInDb(PLAYER)).toBe(15)

    await page.getByTestId('notif-clear').click()
    // Mục đầu tiên đã được đọc ở bước trước: chọn mục chưa đọc đầu tiên.
    const first = page.getByTestId('notif-list').getByRole('listitem').filter({ has: page.getByTestId('notif-unread') }).first()
    await expect(first.getByTestId('notif-unread')).toBeVisible()
    await first.getByRole('button').click()
    await expect(page).toHaveURL(/\/profile/)
    expect(unreadInDb(PLAYER)).toBe(14)
  })

  test('mark everything read clears the badge', async ({ page }) => {
    await loginByApi(page, PLAYER)
    await page.goto('/notifications')
    await page.getByTestId('notif-read-all').click()
    await expect(page.getByTestId('notif-notice')).toContainText('Đã đánh dấu 14 thông báo')
    expect(unreadInDb(PLAYER)).toBe(0)
    await page.goto('/profile')
    await expect(page.getByTestId('bell-count')).toHaveCount(0)
    await page.getByTestId('bell').click()
    await expect(page.getByTestId('bell-read-all')).toBeDisabled()
  })

  test('a user with no notifications sees an empty state in the page and in the bell', async ({ page }) => {
    await loginByApi(page, EMPTY)
    await page.goto('/notifications')
    await expect(page.getByText('Bạn chưa có thông báo nào')).toBeVisible()
    await page.getByTestId('bell').click()
    await expect(page.getByTestId('bell-empty')).toHaveText('Bạn chưa có thông báo nào.')
  })

  test('preferences: every type is listed, the mandatory one is locked, changes are saved and persist after a reload', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, PLAYER)
    await page.goto('/notifications?tab=settings')
    await expect(page.getByTestId('notif-tab-settings')).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByTestId('pref-list').getByRole('listitem')).toHaveCount(17)
    const restriction = page.getByTestId('pref-debt_restriction')
    await expect(restriction.getByTestId('pref-mandatory')).toBeVisible()
    await expect(restriction.getByTestId('pref-in-app')).toBeDisabled()
    await expect(restriction.getByTestId('pref-email')).toBeEnabled()
    await expect(page.getByTestId('pref-booking_confirmed').getByTestId('pref-push')).toHaveText('Chưa hỗ trợ')
    await expect(page.getByTestId('pref-save')).toBeDisabled()

    const confirmed = page.getByTestId('pref-booking_confirmed')
    await confirmed.getByTestId('pref-in-app').uncheck()
    await page.getByTestId('pref-payment_status').getByTestId('pref-email').uncheck()
    await expect(page.getByTestId('pref-save')).toBeEnabled()
    await shot(page, '2362-preferences')
    await page.getByTestId('pref-save').click()
    await expect(page.getByTestId('pref-notice')).toHaveText('Đã lưu tùy chọn nhận thông báo.')
    expect(runSql(`SELECT in_app_enabled || '|' || email_enabled FROM notification_preferences WHERE type = 'booking_confirmed' AND user_id = (SELECT id FROM users WHERE email = '${PLAYER}')`)).toBe('false|true')
    expect(runSql(`SELECT in_app_enabled || '|' || email_enabled FROM notification_preferences WHERE type = 'payment_status' AND user_id = (SELECT id FROM users WHERE email = '${PLAYER}')`)).toBe('true|false')

    await page.reload()
    await expect(page.getByTestId('pref-booking_confirmed').getByTestId('pref-in-app')).not.toBeChecked()
    await expect(page.getByTestId('pref-payment_status').getByTestId('pref-email')).not.toBeChecked()
    await expect(page.getByTestId('pref-booking_cancelled').getByTestId('pref-in-app')).toBeChecked()
    assertNoErrors()
  })

  test('undo discards unsaved changes; switching tabs keeps the URL in sync', async ({ page }) => {
    await loginByApi(page, PLAYER)
    await page.goto('/notifications?tab=settings')
    await page.getByTestId('pref-booking_cancelled').getByTestId('pref-email').uncheck()
    await expect(page.getByTestId('pref-reset')).toBeVisible()
    await page.getByTestId('pref-reset').click()
    await expect(page.getByTestId('pref-booking_cancelled').getByTestId('pref-email')).toBeChecked()
    await expect(page.getByTestId('pref-save')).toBeDisabled()
    await page.getByTestId('notif-tab-history').click()
    await expect(page).toHaveURL('/notifications')
    await page.getByTestId('notif-tab-settings').click()
    await expect(page).toHaveURL('/notifications?tab=settings')
  })

  test('the owner and the admin get the bell and the page inside their own layouts', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await expect(page.getByTestId('bell-count')).toBeVisible()
    await page.getByTestId('bell').click()
    // Các spec khác cũng tạo thông báo cho chủ sân seed, nên chọn đúng mục theo nội dung thay vì mục đầu tiên.
    await page.getByTestId('bell-list').getByRole('listitem').filter({ hasText: 'E2E Thông báo cho chủ sân' }).getByRole('button').click()
    await expect(page).toHaveURL('/owner/payments')
    await page.goto('/notifications')
    await expect(page.getByRole('navigation', { name: 'Điều hướng quản lý sân' })).toBeVisible()
    await expect(page.getByTestId('notif-list')).toContainText('E2E Thông báo cho chủ sân')

    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await expect(page.getByTestId('bell')).toBeVisible()
    await page.goto('/notifications')
    await expect(page.getByRole('navigation', { name: 'Điều hướng quản trị' })).toBeVisible()
    await expect(page.getByTestId('notif-list')).toContainText('E2E Thông báo cho quản trị')
  })

  test('a signed-out visitor is sent to the login page', async ({ page }) => {
    await page.goto('/notifications')
    await expect(page).toHaveURL(/\/login/)
  })

  test('the bell shows an error message when notifications cannot be loaded', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/notifications'] })
    await loginByApi(page, PLAYER)
    await page.route('**/api/v1/notifications?**', (route) => route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) }))
    await page.goto('/profile')
    await page.getByTestId('bell').click()
    await expect(page.getByTestId('bell-empty')).toHaveText('Không tải được thông báo.')
    assertNoErrors()
  })

  test('the bell panel, the history and the preferences fit a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, PLAYER)
    insertNotification(PLAYER, { type: 'debt_restriction', title: 'E2E Thông báo điện thoại', minutesAgo: 0 })
    await page.goto('/profile')
    await page.getByTestId('bell').click()
    const box = await page.getByTestId('bell-panel').boundingBox()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(375)
    await shot(page, '2361-bell-mobile')
    await page.keyboard.press('Escape')
    for (const url of ['/notifications', '/notifications?tab=settings']) {
      await page.goto(url)
      await expect(page.getByTestId('notif-tab-history')).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    }
    await shot(page, '2362-preferences-mobile')
  })
})

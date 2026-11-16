import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const PLAYER = 'e2e-remind-player@example.com'

// Job nhắc nhở chạy nền và tắt trong môi trường E2E; logic gửi và chống trùng được kiểm thử ở BookingReminderTest và MatchReminderTest.
// Ở đây kiểm tra thông báo nhắc nhở hiển thị đúng ở chuông, trang lịch sử, bộ lọc và tùy chọn.
test.describe.serial('2.3.59 - 2.3.60 reminder notifications show up for the user', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    createE2eUser({ tag: 'remind-player', name: 'E2E Người Được Nhắc' })
    const insert = (email, type, title, content, route, minutes) => runSql(`
      INSERT INTO notifications (user_id, type, title, content, data_json, created_at)
      SELECT id, '${type}', '${title}', '${content}', '{"route": "${route}"}'::jsonb, now() - interval '${minutes} minutes' FROM users WHERE email = '${email}'`)
    insert(PLAYER, 'booking_reminder', 'E2E Nhắc lịch chơi sắp tới', 'Bạn có lịch chơi tại Sân A lúc 18:00 (còn khoảng 2 giờ).', '/bookings', 5)
    insert(PLAYER, 'match_reminder', 'E2E Nhắc xác nhận kết quả trận đấu', 'Kết quả trận đấu sẽ tự động được xác nhận sau khoảng 41 giờ.', '/matches', 3)
    insert(SEED_OWNER_EMAIL, 'booking_reminder', 'E2E Booking sắp diễn ra', 'Một người chơi đã đặt sân A lúc 18:00 (còn khoảng 2 giờ).', '/owner/bookings', 2)
  })
  test.afterAll(() => {
    runSql("DELETE FROM notifications WHERE title LIKE 'E2E %'")
    deleteE2eUsers()
  })

  test('reminders appear in the bell with their own labels and open the right screens', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, PLAYER)
    await page.goto('/profile')
    await expect(page.getByTestId('bell-count')).toHaveText('2')
    await page.getByTestId('bell').click()
    const items = page.getByTestId('bell-list').getByRole('listitem')
    await expect(items.first()).toContainText('Nhắc trận đấu')
    await expect(items.first()).toContainText('tự động được xác nhận')
    await expect(items.nth(1)).toContainText('Nhắc đặt sân')
    await shot(page, '2359-bell-reminders')
    await items.nth(1).getByRole('button').click()
    await expect(page).toHaveURL('/bookings')
    assertNoErrors()
  })

  test('the history filters by the two reminder types', async ({ page }) => {
    await loginByApi(page, PLAYER)
    await page.goto('/notifications')
    await page.getByLabel('Lọc theo loại').selectOption('booking_reminder')
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('notif-type')).toHaveText('Nhắc đặt sân')
    await page.getByLabel('Lọc theo loại').selectOption('match_reminder')
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('notif-type')).toHaveText('Nhắc trận đấu')
    await page.getByTestId('notif-list').getByRole('listitem').first().getByRole('button').click()
    await expect(page).toHaveURL('/matches')
  })

  test('the owner gets the booking reminder in the owner layout', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/notifications')
    await expect(page.getByTestId('notif-list')).toContainText('E2E Booking sắp diễn ra')
    await expect(page.getByRole('navigation', { name: 'Điều hướng quản lý sân' })).toBeVisible()
  })

  test('reminders can be switched off per type in the preferences', async ({ page }) => {
    await loginByApi(page, PLAYER)
    await page.goto('/notifications?tab=settings')
    for (const type of ['booking_reminder', 'match_reminder']) {
      const row = page.getByTestId(`pref-${type}`)
      await expect(row.getByTestId('pref-in-app')).toBeChecked()
      await row.getByTestId('pref-in-app').uncheck()
    }
    await page.getByTestId('pref-save').click()
    await expect(page.getByTestId('pref-notice')).toBeVisible()
    expect(runSql(`SELECT count(*) FROM notification_preferences WHERE in_app_enabled = false AND type IN ('booking_reminder', 'match_reminder') AND user_id = (SELECT id FROM users WHERE email = '${PLAYER}')`)).toBe('2')
    // Dọn tùy chọn để người dùng E2E bị xóa sạch.
    runSql(`DELETE FROM notification_preferences WHERE user_id = (SELECT id FROM users WHERE email = '${PLAYER}')`)
  })
})

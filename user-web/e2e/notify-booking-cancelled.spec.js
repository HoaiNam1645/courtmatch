import { expect, test } from '@playwright/test'
import {
  createE2eUser, createE2eVenue, deleteE2eUsers, deleteE2eVenues, loginByApi, makeVenueBookable, nextMondayIso, runSql, SEED_OWNER_EMAIL,
  shot, watchRuntimeErrors,
} from './helpers'

const API = 'http://127.0.0.1:8080'
const BOOKER = 'e2e-cancel-booker@example.com'

// Việc hết hạn do job nền xử lý (tắt trong môi trường E2E) nên được kiểm thử ở BookingCancelledNotificationTest.
test.describe.serial('2.3.54 cancellations notify the owner', () => {
  let startedAt
  let venue
  let owner
  let booker
  let baselineUnread
  const bookings = []

  test.beforeAll(() => {
    deleteE2eVenues()
    deleteE2eUsers()
    startedAt = runSql('SELECT now()')
    createE2eUser({ tag: 'cancel-booker', name: 'E2E Người Hủy Sân' })
  })
  test.afterAll(() => {
    runSql(`DELETE FROM notifications WHERE created_at >= '${startedAt}' AND user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`)
    deleteE2eVenues()
    deleteE2eUsers()
  })

  async function book(page, time) {
    const response = await page.request.post(`${API}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${booker.token}` },
      data: { courtId: venue.courts[0].id, startTime: `${nextMondayIso()}T${time}:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    expect(response.status(), await response.text()).toBe(201)
    return response.json()
  }

  const ownerPost = (page, path) => page.request.post(`${API}${path}`, { headers: { Authorization: `Bearer ${owner.token}` } })
  const unreadOf = (email) => Number(runSql(`SELECT count(*) FROM notifications WHERE read_at IS NULL AND user_id = (SELECT id FROM users WHERE email = '${email}')`))
  const countFor = (email, where = '') => Number(runSql(`SELECT count(*) FROM notifications WHERE created_at >= '${startedAt}' AND user_id = (SELECT id FROM users WHERE email = '${email}') ${where}`))

  test('builds a venue and three bookings (pending, pending, awaiting payment)', async ({ page }) => {
    owner = await loginByApi(page, SEED_OWNER_EMAIL)
    booker = await loginByApi(page, BOOKER)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Thông Báo Hủy')
    makeVenueBookable(venue.id)
    for (const time of ['06:00', '07:00', '08:00']) bookings.push(await book(page, time))
    expect((await ownerPost(page, `/api/v1/owner/booking-requests/${bookings[2].id}/accept`)).status()).toBe(200)
    expect(countFor(SEED_OWNER_EMAIL)).toBe(0)
    // Chủ sân seed đã có sẵn thông báo chưa đọc từ dữ liệu mẫu: so sánh theo mức tăng.
    baselineUnread = unreadOf(SEED_OWNER_EMAIL)
  })

  test('when the player cancels, the owner is notified with the player, booking, time and reason and the bell opens the history', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const cancel = await page.request.post(`${API}/api/v1/bookings/${bookings[0].id}/cancel`, { headers: { Authorization: `Bearer ${booker.token}` }, data: { reason: 'Đổi lịch công tác' } })
    expect(cancel.status(), await cancel.text()).toBe(200)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await expect(page.getByTestId('bell-count')).toHaveText(String(baselineUnread + 1))
    await page.getByTestId('bell').click()
    const item = page.getByTestId('bell-list').getByRole('listitem').first()
    await expect(item).toContainText('Hủy đặt sân')
    await expect(item).toContainText('Người chơi đã hủy đặt sân')
    await expect(item).toContainText('E2E Người Hủy Sân')
    await expect(item).toContainText(bookings[0].bookingCode)
    await expect(item).toContainText('Đổi lịch công tác')
    await shot(page, '2354-owner-bell')
    await item.getByRole('button').click()
    await expect(page).toHaveURL('/owner/bookings')
    expect(countFor(BOOKER, "AND type = 'booking_cancelled'")).toBe(0)
    assertNoErrors()
  })

  test('cancelling a booking that already awaits payment also notifies the owner; a repeated cancel does not', async ({ page }) => {
    expect((await page.request.post(`${API}/api/v1/bookings/${bookings[2].id}/cancel`, { headers: { Authorization: `Bearer ${booker.token}` }, data: { reason: 'Không kịp thanh toán' } })).status()).toBe(200)
    expect((await page.request.post(`${API}/api/v1/bookings/${bookings[2].id}/cancel`, { headers: { Authorization: `Bearer ${booker.token}` }, data: { reason: 'Hủy lần hai' } })).status()).toBe(409)
    expect(countFor(SEED_OWNER_EMAIL)).toBe(2)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/notifications')
    await expect(page.getByTestId('notif-list').getByRole('listitem').first().getByTestId('notif-content')).toContainText('Không kịp thanh toán')
  })

  test('the owner bell panel fits a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByTestId('bell').click()
    const box = await page.getByTestId('bell-panel').boundingBox()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(375)
    await shot(page, '2354-owner-bell-mobile')
  })
})

import { expect, test } from '@playwright/test'
import {
  createE2eUser, createE2eVenue, deleteE2eUsers, deleteE2eVenues, loginByApi, makeVenueBookable, nextMondayIso, runSql, SEED_OWNER_EMAIL,
  shot, watchRuntimeErrors,
} from './helpers'

const API = 'http://127.0.0.1:8080'
const BOOKER = 'e2e-notify-booker@example.com'

test.describe.serial('2.3.53 the player is notified when the owner accepts, rejects and confirms payment', () => {
  let startedAt
  let venue
  let owner
  let booker
  const bookings = []

  test.beforeAll(() => {
    deleteE2eVenues()
    deleteE2eUsers()
    startedAt = runSql('SELECT now()')
    createE2eUser({ tag: 'notify-booker', name: 'E2E Người Đặt Sân' })
  })
  test.afterAll(() => {
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

  const ownerPost = (page, path, data) => page.request.post(`${API}${path}`, { headers: { Authorization: `Bearer ${owner.token}` }, data })

  test('builds a venue and three requests from a fresh player', async ({ page }) => {
    owner = await loginByApi(page, SEED_OWNER_EMAIL)
    booker = await loginByApi(page, BOOKER)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Thông Báo Đặt Sân')
    makeVenueBookable(venue.id)
    for (const time of ['06:00', '07:00', '08:00']) bookings.push(await book(page, time))
    // Gửi yêu cầu không báo cho chính người chơi.
    expect(Number(runSql(`SELECT count(*) FROM notifications WHERE user_id = (SELECT id FROM users WHERE email = '${BOOKER}')`))).toBe(0)
  })

  test('accepting: the bell shows one unread notification that opens the booking and marks it read', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const accepted = await ownerPost(page, `/api/v1/owner/booking-requests/${bookings[0].id}/accept`)
    expect(accepted.status()).toBe(200)

    await loginByApi(page, BOOKER)
    await page.goto('/profile')
    await expect(page.getByTestId('bell-count')).toHaveText('1')
    await page.getByTestId('bell').click()
    const item = page.getByTestId('bell-list').getByRole('listitem').first()
    await expect(item).toContainText('Xác nhận đặt sân')
    await expect(item).toContainText('Chủ sân đã chấp nhận đặt sân')
    await expect(item).toContainText(bookings[0].bookingCode)
    await expect(item).toContainText('E2E Sân Thông Báo Đặt Sân')
    await expect(item).toContainText('30 phút')
    await shot(page, '2353-bell-accepted')
    await item.getByRole('button').click()
    await expect(page).toHaveURL(`/bookings/${bookings[0].id}`)
    await expect(page.getByText(bookings[0].bookingCode).first()).toBeVisible()
    expect(Number(runSql(`SELECT count(*) FROM notifications WHERE read_at IS NULL AND user_id = (SELECT id FROM users WHERE email = '${BOOKER}')`))).toBe(0)
    assertNoErrors()
  })

  test('rejecting: the player is told why', async ({ page }) => {
    const rejected = await ownerPost(page, `/api/v1/owner/booking-requests/${bookings[1].id}/reject`, { reasonCode: 'other', note: 'Sân đang sửa mái che' })
    expect(rejected.status()).toBe(200)
    await loginByApi(page, BOOKER)
    await page.goto('/notifications')
    const rejectedItem = page.getByTestId('notif-list').getByRole('listitem').first()
    await expect(rejectedItem.getByTestId('notif-title')).toHaveText('Chủ sân đã từ chối đặt sân')
    await expect(rejectedItem.getByTestId('notif-content')).toContainText(bookings[1].bookingCode)
    await expect(rejectedItem.getByTestId('notif-content')).toContainText('Sân đang sửa mái che')
    await expect(rejectedItem.getByTestId('notif-unread')).toBeVisible()
    await shot(page, '2353-history-rejected')
    await rejectedItem.getByRole('button').click()
    await expect(page).toHaveURL(`/bookings/${bookings[1].id}`)
  })

  test('confirming payment: a second notification arrives and an owner action that fails sends nothing', async ({ page }) => {
    const payment = await (await page.request.get(`${API}/api/v1/payments/by-booking/${bookings[0].id}`, { headers: { Authorization: `Bearer ${booker.token}` } })).json()
    expect((await page.request.post(`${API}/api/v1/payments/${payment.id}/report-paid`, { headers: { Authorization: `Bearer ${booker.token}` }, data: { method: 'cash' } })).status()).toBe(200)
    // Báo đã thanh toán chưa sinh thông báo xác nhận cho người chơi.
    expect(Number(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_confirmed' AND user_id = (SELECT id FROM users WHERE email = '${BOOKER}')`))).toBe(2)
    expect((await ownerPost(page, `/api/v1/payments/${payment.id}/confirm-received`)).status()).toBe(200)
    expect((await ownerPost(page, `/api/v1/payments/${payment.id}/confirm-received`)).status()).toBe(409)
    expect((await ownerPost(page, `/api/v1/owner/booking-requests/${bookings[0].id}/accept`)).status()).toBe(409)

    await loginByApi(page, BOOKER)
    await page.goto('/profile')
    await expect(page.getByTestId('bell-count')).toHaveText('1')
    await page.getByTestId('bell').click()
    await expect(page.getByTestId('bell-list').getByRole('listitem').first()).toContainText('Chủ sân đã xác nhận thu tiền')
    await expect(page.getByTestId('bell-list').getByRole('listitem').first()).toContainText('Hẹn gặp bạn tại sân')
    expect(Number(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_confirmed' AND user_id = (SELECT id FROM users WHERE email = '${BOOKER}')`))).toBe(3)
  })

  test('the owner who performed the actions gets no booking-confirmed notification of their own, and a player who turned the type off gets none', async ({ page }) => {
    expect(Number(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_confirmed' AND created_at >= '${startedAt}' AND user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`))).toBe(0)
    await loginByApi(page, BOOKER)
    await page.goto('/notifications?tab=settings')
    await page.getByTestId('pref-booking_confirmed').getByTestId('pref-in-app').uncheck()
    await page.getByTestId('pref-save').click()
    await expect(page.getByTestId('pref-notice')).toBeVisible()
    const extra = await book(page, '09:00')
    expect((await ownerPost(page, `/api/v1/owner/booking-requests/${extra.id}/accept`)).status()).toBe(200)
    expect(Number(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_confirmed' AND user_id = (SELECT id FROM users WHERE email = '${BOOKER}')`))).toBe(3)
  })
})

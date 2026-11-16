import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'

const API = 'http://127.0.0.1:8080'
const CREATOR = 'e2e-mn-creator@example.com'
const OPPONENT = 'e2e-mn-opponent@example.com'

test.describe.serial('2.3.58 match notifications', () => {
  let creator
  let opponent
  let matchId

  test.beforeAll(() => {
    deleteE2eUsers()
    createE2eUser({ tag: 'mn-creator', name: 'E2E Người Ghi Trận' })
    createE2eUser({ tag: 'mn-opponent', name: 'E2E Đối Thủ' })
  })
  test.afterAll(() => {
    runSql(`DELETE FROM matches WHERE created_by IN (SELECT id FROM users WHERE email LIKE 'e2e-mn-%')`)
    deleteE2eUsers()
  })

  const authed = (user) => ({ Authorization: `Bearer ${user.token}` })
  const countOf = (email) => Number(runSql(`SELECT count(*) FROM notifications WHERE type = 'match_scheduled' AND user_id = (SELECT id FROM users WHERE email = '${email}')`))

  test('recording a match notifies the opponent, who opens it from the bell; the creator is not notified', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    creator = await loginByApi(page, CREATOR)
    opponent = await loginByApi(page, OPPONENT)
    const response = await page.request.post(`${API}/api/v1/matches`, {
      headers: authed(creator),
      data: { bookingId: null, venueId: null, matchType: 'single', scheduledAt: new Date(Date.now() - 3 * 3600000).toISOString(), team1UserIds: [creator.user.id], team2UserIds: [opponent.user.id] },
    })
    expect(response.status(), await response.text()).toBe(201)
    matchId = (await response.json()).id
    expect(countOf(CREATOR)).toBe(0)

    await loginByApi(page, OPPONENT)
    await page.goto('/profile')
    await expect(page.getByTestId('bell-count')).toHaveText('1')
    await page.getByTestId('bell').click()
    const item = page.getByTestId('bell-list').getByRole('listitem').first()
    await expect(item).toContainText('Trận đấu')
    await expect(item).toContainText('Bạn được ghi vào một trận đấu')
    await expect(item).toContainText('E2E Người Ghi Trận')
    await expect(item).toContainText('trận đơn')
    await shot(page, '2358-bell-created')
    await item.getByRole('button').click()
    await expect(page).toHaveURL(`/matches/${matchId}`)
    assertNoErrors()
  })

  test('entering the score asks the opponent to confirm; confirming sends nothing more', async ({ page }) => {
    const result = await page.request.put(`${API}/api/v1/matches/${matchId}/result`, {
      headers: authed(creator),
      data: { games: [{ gameNo: 1, team1Score: 21, team2Score: 18 }, { gameNo: 2, team1Score: 19, team2Score: 21 }, { gameNo: 3, team1Score: 21, team2Score: 16 }] },
    })
    expect(result.status(), await result.text()).toBe(200)
    expect(countOf(OPPONENT)).toBe(2)

    await loginByApi(page, OPPONENT)
    await page.goto('/notifications')
    const first = page.getByTestId('notif-list').getByRole('listitem').first()
    await expect(first.getByTestId('notif-title')).toHaveText('Kết quả trận đấu cần bạn xác nhận')
    await expect(first.getByTestId('notif-content')).toContainText('báo sai')
    await shot(page, '2358-history-result')
    await first.getByRole('button').click()
    await expect(page).toHaveURL(`/matches/${matchId}`)

    expect((await page.request.post(`${API}/api/v1/matches/${matchId}/confirm`, { headers: authed(opponent) })).status()).toBe(200)
    expect(countOf(OPPONENT)).toBe(2)
    expect(countOf(CREATOR)).toBe(0)
  })

  test('cancelling a match tells the others; a user who is not the creator cannot cancel and nothing is sent', async ({ page }) => {
    const second = await page.request.post(`${API}/api/v1/matches`, {
      headers: authed(creator),
      data: { bookingId: null, venueId: null, matchType: 'single', scheduledAt: new Date(Date.now() - 2 * 3600000).toISOString(), team1UserIds: [creator.user.id], team2UserIds: [opponent.user.id] },
    })
    const secondId = (await second.json()).id
    expect(countOf(OPPONENT)).toBe(3)
    expect((await page.request.delete(`${API}/api/v1/matches/${secondId}`, { headers: authed(opponent) })).status()).toBe(403)
    expect(countOf(OPPONENT)).toBe(3)
    expect((await page.request.delete(`${API}/api/v1/matches/${secondId}`, { headers: authed(creator) })).status()).toBe(204)
    expect(countOf(OPPONENT)).toBe(4)

    await loginByApi(page, OPPONENT)
    await page.goto('/notifications')
    const item = page.getByTestId('notif-list').getByRole('listitem').first()
    await expect(item.getByTestId('notif-title')).toHaveText('Trận đấu đã bị hủy')
    await item.getByRole('button').click()
    await expect(page).toHaveURL('/matches')
  })

  test('the type can be filtered in the history', async ({ page }) => {
    await loginByApi(page, OPPONENT)
    await page.goto('/notifications')
    await page.getByLabel('Lọc theo loại').selectOption('match_scheduled')
    await expect(page.getByTestId('notif-list').getByRole('listitem')).toHaveCount(4)
    await expect(page.getByTestId('notif-type').first()).toHaveText('Trận đấu')
    await page.getByLabel('Lọc theo loại').selectOption('payment_status')
    await expect(page.getByText('Không có thông báo phù hợp')).toBeVisible()
  })
})

import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'

const API = 'http://127.0.0.1:8080'
const SENDER = 'e2e-pe-sender@example.com'
const RECEIVER = 'e2e-pe-receiver@example.com'

test.describe.serial('Partner request expiry (60 minutes without a reply)', () => {
  let sender
  let receiver

  test.beforeAll(() => {
    deleteE2eUsers()
    createE2eUser({ tag: 'pe-sender', name: 'E2E Người Gửi Hết Hạn' })
    createE2eUser({ tag: 'pe-receiver', name: 'E2E Người Nhận Hết Hạn' })
  })
  test.afterAll(() => {
    runSql(`DELETE FROM partner_requests WHERE requester_id IN (SELECT id FROM users WHERE email LIKE 'e2e-pe-%')`)
    deleteE2eUsers()
  })

  const authed = (user) => ({ Authorization: `Bearer ${user.token}` })
  const send = async (page, message) => {
    const response = await page.request.post(`${API}/api/v1/partner-requests`, { headers: authed(sender), data: { receiverId: receiver.user.id, message } })
    expect(response.status(), await response.text()).toBe(201)
    return response.json()
  }
  const age = (id, minutes) => runSql(`UPDATE partner_requests SET created_at = now() - interval '${minutes} minutes' WHERE id = '${id}'`)
  const statusOf = (id) => runSql(`SELECT status FROM partner_requests WHERE id = '${id}'`)

  test('a fresh request shows the remaining time and the response buttons', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    sender = await loginByApi(page, SENDER)
    receiver = await loginByApi(page, RECEIVER)
    const request = await send(page, 'Mình đánh đôi tối nay nhé')
    expect(request.expiresAt).toBeTruthy()

    await loginByApi(page, RECEIVER)
    await page.goto('/partners/requests')
    await expect(page.getByText('Mình đánh đôi tối nay nhé')).toBeVisible()
    await expect(page.getByText(/Đang chờ bạn phản hồi · còn \d+ phút/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Chấp nhận' })).toBeVisible()
    await shot(page, 'partner-expiry-fresh')
    assertNoErrors()
    runSql(`DELETE FROM partner_requests WHERE id = '${request.id}'`)
  })

  test('an overdue request is expired: no buttons for the receiver, the sender is notified and can resend', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/partner-requests/'] })
    const request = await send(page, 'Lời mời đã quá hạn')
    age(request.id, 75)

    await loginByApi(page, RECEIVER)
    await page.goto('/partners/requests')
    await page.getByRole('button', { name: 'Tất cả yêu cầu' }).click()
    const card = page.locator('article', { hasText: 'Lời mời đã quá hạn' })
    await expect(card).toContainText('Đã hết hạn')
    await expect(card.getByRole('button', { name: 'Chấp nhận' })).toHaveCount(0)
    await expect(card.getByRole('button', { name: 'Từ chối' })).toHaveCount(0)
    await shot(page, 'partner-expiry-receiver')
    expect(statusOf(request.id)).toBe('expired')

    // Chấp nhận qua API sau hạn bị từ chối, không tạo kết nối.
    const accept = await page.request.post(`${API}/api/v1/partner-requests/${request.id}/accept`, { headers: authed(receiver) })
    expect(accept.status()).toBe(409)
    expect(runSql(`SELECT count(*) FROM player_connections WHERE user_id = '${sender.user.id}'`)).toBe('0')

    await loginByApi(page, SENDER)
    await page.goto('/profile')
    await expect(page.getByTestId('bell-count')).toHaveText('1')
    await page.getByTestId('bell').click()
    const item = page.getByTestId('bell-list').getByRole('listitem').first()
    await expect(item).toContainText('Lời mời ghép cặp đã hết hạn')
    await expect(item).toContainText('E2E Người Nhận Hết Hạn')
    await shot(page, 'partner-expiry-bell')
    await item.getByRole('button').click()
    await expect(page).toHaveURL('/partners')

    const again = await send(page, 'Gửi lại sau khi hết hạn')
    expect(statusOf(again.id)).toBe('pending')
    assertNoErrors()
  })

  test('the sent list marks the old request expired on a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SENDER)
    await page.goto('/partners/requests')
    await page.getByRole('button', { name: 'Yêu cầu đã gửi' }).click()
    await page.getByRole('button', { name: 'Tất cả yêu cầu' }).click()
    await expect(page.locator('article', { hasText: 'Lời mời đã quá hạn' })).toContainText('Đã hết hạn')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, 'partner-expiry-sent-phone')
  })
})

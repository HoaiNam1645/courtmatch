import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'

const API = 'http://127.0.0.1:8080'
const SENDER = 'e2e-pn-sender@example.com'
const RECEIVER = 'e2e-pn-receiver@example.com'

test.describe.serial('2.3.56 - 2.3.57 partner request notifications', () => {
  let sender
  let receiver
  let requests = []

  test.beforeAll(() => {
    deleteE2eUsers()
    createE2eUser({ tag: 'pn-sender', name: 'E2E Người Gửi Lời Mời' })
    createE2eUser({ tag: 'pn-receiver', name: 'E2E Người Nhận Lời Mời' })
  })
  test.afterAll(() => {
    runSql(`DELETE FROM partner_requests WHERE requester_id IN (SELECT id FROM users WHERE email LIKE 'e2e-pn-%')`)
    deleteE2eUsers()
  })

  const authed = (user) => ({ Authorization: `Bearer ${user.token}` })
  const countOf = (email, type) => Number(runSql(`SELECT count(*) FROM notifications WHERE type = '${type}' AND user_id = (SELECT id FROM users WHERE email = '${email}')`))

  async function sendRequest(page, message) {
    const response = await page.request.post(`${API}/api/v1/partner-requests`, { headers: authed(sender), data: { receiverId: receiver.user.id, message } })
    expect(response.status(), await response.text()).toBe(201)
    return response.json()
  }

  test('sets up two players', async ({ page }) => {
    sender = await loginByApi(page, SENDER)
    receiver = await loginByApi(page, RECEIVER)
    expect(countOf(RECEIVER, 'partner_request')).toBe(0)
  })

  test('2.3.56: a request notifies the receiver, the bell opens the invitations page, the sender gets nothing', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    requests.push(await sendRequest(page, 'Thứ bảy này mình đánh đôi nhé'))
    expect(countOf(SENDER, 'partner_request')).toBe(0)

    await loginByApi(page, RECEIVER)
    await page.goto('/profile')
    await expect(page.getByTestId('bell-count')).toHaveText('1')
    await page.getByTestId('bell').click()
    const item = page.getByTestId('bell-list').getByRole('listitem').first()
    await expect(item).toContainText('Ghép cặp')
    await expect(item).toContainText('Lời mời ghép cặp mới')
    await expect(item).toContainText('E2E Người Gửi Lời Mời')
    await expect(item).toContainText('Thứ bảy này mình đánh đôi nhé')
    await shot(page, '2356-bell-request')
    await item.getByRole('button').click()
    await expect(page).toHaveURL('/partners/requests')
    await expect(page.getByText('Thứ bảy này mình đánh đôi nhé').first()).toBeVisible()
    assertNoErrors()
  })

  test('2.3.57: accepting notifies the sender with a hint to book together; the link opens the partners list', async ({ page }) => {
    expect((await page.request.post(`${API}/api/v1/partner-requests/${requests[0].id}/accept`, { headers: authed(receiver) })).status()).toBe(200)
    expect(countOf(RECEIVER, 'partner_request_response')).toBe(0)

    await loginByApi(page, SENDER)
    await page.goto('/profile')
    await expect(page.getByTestId('bell-count')).toHaveText('1')
    await page.getByTestId('bell').click()
    const item = page.getByTestId('bell-list').getByRole('listitem').first()
    await expect(item).toContainText('Phản hồi ghép cặp')
    await expect(item).toContainText('Lời mời ghép cặp được chấp nhận')
    await expect(item).toContainText('E2E Người Nhận Lời Mời')
    await expect(item).toContainText('đặt sân')
    await shot(page, '2357-bell-accepted')
    await item.getByRole('button').click()
    await expect(page).toHaveURL('/partners/connections')
    await expect(page.getByText('E2E Người Nhận Lời Mời').first()).toBeVisible()
  })

  test('2.3.57: a rejected request tells the sender; after a rejection a new request can be sent and notifies again', async ({ page }) => {
    // Hai người đã là đối tác nên dùng cặp khác: hủy kết nối rồi gửi lại không có API công khai, nên dùng người nhận thứ ba.
    createE2eUser({ tag: 'pn-third', name: 'E2E Người Thứ Ba' })
    const third = await loginByApi(page, 'e2e-pn-third@example.com')
    const response = await page.request.post(`${API}/api/v1/partner-requests`, { headers: authed(sender), data: { receiverId: third.user.id, message: 'Thử ghép với bạn' } })
    expect(response.status(), await response.text()).toBe(201)
    const created = await response.json()
    expect(countOf('e2e-pn-third@example.com', 'partner_request')).toBe(1)
    expect((await page.request.post(`${API}/api/v1/partner-requests/${created.id}/reject`, { headers: authed(third) })).status()).toBe(200)

    await loginByApi(page, SENDER)
    await page.goto('/notifications')
    const item = page.getByTestId('notif-list').getByRole('listitem').first()
    await expect(item.getByTestId('notif-title')).toHaveText('Lời mời ghép cặp bị từ chối')
    await expect(item.getByTestId('notif-content')).toContainText('E2E Người Thứ Ba')
    await item.getByRole('button').click()
    await expect(page).toHaveURL('/partners')

    // Gửi lại sau khi bị từ chối: người nhận lại được báo.
    expect((await page.request.post(`${API}/api/v1/partner-requests`, { headers: authed(sender), data: { receiverId: third.user.id, message: 'Thử lại lần hai' } })).status()).toBe(201)
    expect(countOf('e2e-pn-third@example.com', 'partner_request')).toBe(2)
  })

  test('failed actions and cancellations send nothing; the receiver can turn the type off', async ({ page }) => {
    const before = countOf(RECEIVER, 'partner_request') + countOf(SENDER, 'partner_request_response')
    // Lời mời trùng với người đã là đối tác bị từ chối.
    const duplicate = await page.request.post(`${API}/api/v1/partner-requests`, { headers: authed(sender), data: { receiverId: receiver.user.id, message: 'Đã là đối tác' } })
    expect(duplicate.status()).toBe(409)
    expect(countOf(RECEIVER, 'partner_request') + countOf(SENDER, 'partner_request_response')).toBe(before)

    await loginByApi(page, 'e2e-pn-third@example.com')
    await page.goto('/notifications?tab=settings')
    await page.getByTestId('pref-partner_request').getByTestId('pref-in-app').uncheck()
    await page.getByTestId('pref-save').click()
    await expect(page.getByTestId('pref-notice')).toBeVisible()
    const fourth = createE2eUser({ tag: 'pn-fourth', name: 'E2E Người Thứ Tư' })
    expect(fourth).toBeTruthy()
    // Người thứ ba tắt loại này: một lời mời mới từ người khác tới họ không để lại thông báo.
    const other = await loginByApi(page, 'e2e-pn-fourth@example.com')
    const third = runSql(`SELECT id FROM users WHERE email = 'e2e-pn-third@example.com'`)
    const response = await page.request.post(`${API}/api/v1/partner-requests`, { headers: authed(other), data: { receiverId: third, message: 'Lời mời khi đã tắt thông báo' } })
    expect(response.status(), await response.text()).toBe(201)
    expect(countOf('e2e-pn-third@example.com', 'partner_request')).toBe(2)
  })
})

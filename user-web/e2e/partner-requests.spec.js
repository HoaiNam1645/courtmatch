import { expect, test } from '@playwright/test'
import { loginByApi, resetPartnerE2eData, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

async function loginPayload(page, email) {
  const response = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, {
    data: { emailOrPhone: email, password: 'Courtly@123' },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json()
}

async function createFixture(page, senderEmail, receiverEmail, message) {
  const [sender, receiver] = await Promise.all([
    loginPayload(page, senderEmail), loginPayload(page, receiverEmail),
  ])
  const response = await page.request.post(`${API_BASE_URL}/api/v1/partner-requests`, {
    headers: { Authorization: `Bearer ${sender.accessToken}` },
    data: { receiverId: receiver.user.id, message },
  })
  expect(response.status(), await response.text()).toBe(201)
  return { request: await response.json(), sender, receiver }
}

async function respondByApi(page, token, requestId, action) {
  const response = await page.request.post(`${API_BASE_URL}/api/v1/partner-requests/${requestId}/${action}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json()
}

test.describe('partner requests', () => {
  test.beforeEach(() => resetPartnerE2eData())

  test('2.2.32 lists and filters received partner requests', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    const message = 'E2E: Lời mời dành cho danh sách đã nhận.'
    await createFixture(page, 'dung.pham@example.com', 'chau.le@example.com', message)
    await loginByApi(page, 'chau.le@example.com')
    const pendingResponse = page.waitForResponse((response) => response.request().method() === 'GET' && response.url().includes('/received?status=pending'))
    await page.goto('/partners/requests')
    expect((await pendingResponse).ok()).toBeTruthy()
    await expect(page.getByRole('heading', { name: 'Quản lý lời mời ghép cặp' })).toBeVisible()
    await expect(page.getByText(message)).toBeVisible()
    await expect(page.getByText('Đang chờ', { exact: true })).toBeVisible()
    const allResponse = page.waitForResponse((response) => response.request().method() === 'GET' && response.url().includes('/received?status=all'))
    await page.getByRole('button', { name: 'Tất cả yêu cầu' }).click()
    expect((await allResponse).ok()).toBeTruthy()
    await expect(page.getByText(message)).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('2.2.33 accepts a pending request and refreshes its status', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    const message = 'E2E: Chấp nhận yêu cầu ghép cặp.'
    await createFixture(page, 'dung.pham@example.com', 'chau.le@example.com', message)
    await loginByApi(page, 'chau.le@example.com')
    await page.goto('/partners/requests')
    const card = page.locator('article').filter({ hasText: message })
    const responsePromise = page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/accept'))
    await card.getByRole('button', { name: 'Chấp nhận' }).click()
    const response = await responsePromise
    expect(response.status(), await response.text()).toBe(200)
    expect((await response.json()).status).toBe('accepted')
    await expect(page.getByText('Không có yêu cầu nào đang chờ')).toBeVisible()
    await page.getByRole('button', { name: 'Tất cả yêu cầu' }).click()
    await expect(page.locator('article').filter({ hasText: message }).getByText('Đã chấp nhận', { exact: true })).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('2.2.34 rejects a pending request and stores the response state', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    const message = 'E2E: Từ chối yêu cầu ghép cặp.'
    await createFixture(page, 'binh.tran@example.com', 'dat.hoang@example.com', message)
    await loginByApi(page, 'dat.hoang@example.com')
    await page.goto('/partners/requests')
    const card = page.locator('article').filter({ hasText: message })
    const responsePromise = page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/reject'))
    await card.getByRole('button', { name: 'Từ chối' }).click()
    const response = await responsePromise
    expect(response.status(), await response.text()).toBe(200)
    const payload = await response.json()
    expect(payload.status).toBe('rejected')
    expect(payload.respondedAt).toBeTruthy()
    await expect(page.getByText('Không có yêu cầu nào đang chờ')).toBeVisible()
    await page.getByRole('button', { name: 'Tất cả yêu cầu' }).click()
    await expect(page.locator('article').filter({ hasText: message }).getByText('Đã từ chối', { exact: true })).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('2.2.35 lets only the sender cancel a pending request', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    const message = 'E2E: Hủy yêu cầu đã gửi.'
    await createFixture(page, 'khanh.dang@example.com', 'lam.bui@example.com', message)
    await loginByApi(page, 'khanh.dang@example.com')
    await page.goto('/partners/requests')
    await page.getByRole('button', { name: 'Yêu cầu đã gửi' }).click()
    const card = page.locator('article').filter({ hasText: message })
    const responsePromise = page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/cancel'))
    await card.getByRole('button', { name: 'Hủy yêu cầu' }).click()
    const response = await responsePromise
    expect(response.status(), await response.text()).toBe(200)
    expect((await response.json()).status).toBe('cancelled')
    await expect(page.getByText('Không có yêu cầu nào đang chờ')).toBeVisible()
    await page.getByRole('button', { name: 'Tất cả yêu cầu' }).click()
    await expect(page.locator('article').filter({ hasText: message }).getByText('Đã hủy', { exact: true })).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('2.2.36 lists only active confirmed partner connections', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    const accepted = await createFixture(page, 'dung.pham@example.com', 'khanh.dang@example.com', 'E2E: Kết nối được xác nhận.')
    await respondByApi(page, accepted.receiver.accessToken, accepted.request.id, 'accept')
    await createFixture(page, 'lam.bui@example.com', 'khanh.dang@example.com', 'E2E: Yêu cầu vẫn đang chờ.')
    const rejected = await createFixture(page, 'binh.tran@example.com', 'khanh.dang@example.com', 'E2E: Yêu cầu đã từ chối.')
    await respondByApi(page, rejected.receiver.accessToken, rejected.request.id, 'reject')
    await loginByApi(page, 'khanh.dang@example.com')
    const responsePromise = page.waitForResponse((response) => response.request().method() === 'GET' && response.url().includes('/partners/connections?'))
    await page.goto('/partners/connections')
    const response = await responsePromise
    expect(response.ok(), await response.text()).toBeTruthy()
    const payload = await response.json()
    const names = payload.content.map((item) => item.fullName)
    expect(names).toContain('Pham Thu Dung')
    expect(names).not.toContain('Bui Thanh Lam')
    expect(names).not.toContain('Tran Thi Binh')
    await expect(page.getByRole('heading', { name: 'Đối tác của bạn' })).toBeVisible()
    await expect(page.getByText('Pham Thu Dung')).toBeVisible()
    await expect(page.getByText('Bui Thanh Lam')).not.toBeVisible()
    await expect(page.getByText('Tran Thi Binh')).not.toBeVisible()
    assertNoRuntimeErrors()
  })
})

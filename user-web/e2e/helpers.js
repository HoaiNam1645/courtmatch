import { expect } from '@playwright/test'

const API_BASE_URL = 'http://127.0.0.1:8080'
const DEFAULT_PASSWORD = 'Courtly@123'

export function watchRuntimeErrors(page, { ignoredUrls = [] } = {}) {
  const errors = []
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() === 'error') {
      const source = message.location().url
      if (ignoredUrls.some((url) => source.includes(url))) return
      errors.push(`console: ${message.text()}${source ? ` (${source})` : ''}`)
    }
  })
  return () => expect(errors, errors.join('\n')).toEqual([])
}

export async function loginByApi(page, emailOrPhone, password = DEFAULT_PASSWORD) {
  const response = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, {
    data: { emailOrPhone, password },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  const payload = await response.json()
  const user = {
    id: payload.user.id,
    name: payload.user.fullName,
    email: payload.user.email ?? '',
    phone: payload.user.phone ?? '',
    avatarUrl: payload.user.avatarUrl ?? null,
    status: payload.user.status,
    roles: payload.user.roles ?? [],
  }
  await page.addInitScript(({ token, cachedUser }) => {
    window.localStorage.setItem('courtly_access_token', token)
    window.localStorage.setItem('courtly_cached_user', JSON.stringify(cachedUser))
  }, { token: payload.accessToken, cachedUser: user })
  return { token: payload.accessToken, user }
}

export async function loginThroughUi(page, identity, { method = 'email', password = DEFAULT_PASSWORD } = {}) {
  await page.goto('/login')
  if (method === 'email') {
    await page.getByRole('button', { name: 'Email', exact: true }).click()
    await page.locator('[name="courtly_login_email"]').fill(identity)
  } else {
    await page.locator('[name="courtly_login_phone"]').fill(identity)
  }
  await page.locator('[name="courtly_login_password"]').fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('link', { name: /Nguyen Van An|Tran Thi Binh/ })).toBeVisible()
}

import { expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'

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
    passwordChangeRequired: Boolean(payload.user.passwordChangeRequired),
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

/** Chạy SQL trên database local; chỉ dùng để dựng và dọn dữ liệu E2E. */
export function runSql(sql) {
  return execFileSync('psql', ['-h', 'localhost', '-U', 'courtly', '-d', 'courtly', '-v', 'ON_ERROR_STOP=1', '-At', '-c', sql], {
    env: { ...process.env, PGPASSWORD: 'courtly' },
    encoding: 'utf8',
  }).trim()
}

/** Chủ sân mẫu duy nhất của dữ liệu seed. */
export const SEED_OWNER_EMAIL = 'thang.owner@example.com'

/**
 * Tài khoản chủ sân tạm cho E2E (2.3.1), dùng mật khẩu mẫu và bị bắt đổi mật khẩu.
 * Mượn hash mật khẩu của chủ sân seed để không phải tự băm.
 */
export function createTemporaryOwner(email) {
  deleteTemporaryOwner(email)
  runSql(`
    INSERT INTO users (id, full_name, email, password_hash, status, email_verified_at, password_change_required)
    SELECT gen_random_uuid(), 'E2E Chu San Tam', '${email}', password_hash, 'active', now(), true
    FROM users WHERE email = '${SEED_OWNER_EMAIL}';
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r WHERE u.email = '${email}' AND r.code = 'owner';
    INSERT INTO court_owner_profiles (user_id, business_name, verification_status)
    SELECT id, 'E2E Ho kinh doanh', 'pending' FROM users WHERE email = '${email}';
  `)
}

export function deleteTemporaryOwner(email) {
  runSql(`
    DELETE FROM audit_logs WHERE actor_id IN (SELECT id FROM users WHERE email = '${email}');
    DELETE FROM users WHERE email = '${email}';
  `)
}

export function resetPartnerE2eData() {
  const sql = `
    DELETE FROM player_connections
    WHERE source_request_id IN (
      SELECT id FROM partner_requests WHERE message LIKE 'E2E:%'
    );
    DELETE FROM partner_requests WHERE message LIKE 'E2E:%';
  `
  execFileSync('psql', ['-h', 'localhost', '-U', 'courtly', '-d', 'courtly', '-v', 'ON_ERROR_STOP=1', '-c', sql], {
    env: { ...process.env, PGPASSWORD: 'courtly' },
    stdio: 'ignore',
  })
}

import { expect, test } from '@playwright/test'
import {
  createTemporaryOwner,
  deleteTemporaryOwner,
  loginByApi,
  runSql,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const TEMP_OWNER_EMAIL = 'e2e.owner.account@example.com'
const TEMP_PASSWORD = 'Courtly@123'
const NEW_PASSWORD = 'SanMoi@2026'

async function loginThroughForm(page, email, password) {
  await page.goto('/login')
  await page.locator('[name="courtly_login_email"]').fill(email)
  await page.locator('[name="courtly_login_password"]').fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
}

test.describe.serial('2.3.1 owner login with an admin-issued account', () => {
  test.beforeAll(() => createTemporaryOwner(TEMP_OWNER_EMAIL))
  test.afterAll(() => deleteTemporaryOwner(TEMP_OWNER_EMAIL))

  test('temporary password forces a password change before the owner area', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/owner/account/password'] })
    await loginThroughForm(page, TEMP_OWNER_EMAIL, TEMP_PASSWORD)

    await expect(page).toHaveURL('/owner/change-password')
    await expect(page.getByRole('heading', { name: 'Đổi mật khẩu' })).toBeVisible()
    await expect(page.getByText('Bạn cần đổi mật khẩu tạm để tiếp tục.')).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Điều hướng quản lý sân' })).toHaveCount(0)

    // Không vào thẳng trang quản lý được khi chưa đổi mật khẩu.
    await page.goto('/owner')
    await expect(page).toHaveURL('/owner/change-password')

    // Validate phía client giống backend.
    await page.getByRole('button', { name: 'Lưu mật khẩu mới' }).click()
    await expect(page.getByText('Vui lòng nhập mật khẩu hiện tại')).toBeVisible()
    await page.locator('[name="currentPassword"]').fill('SaiMatKhau1')
    await page.locator('[name="newPassword"]').fill('khongcoso')
    await page.locator('[name="confirmPassword"]').fill('khongcoso')
    await page.getByRole('button', { name: 'Lưu mật khẩu mới' }).click()
    await expect(page.getByText('Mật khẩu phải có ít nhất một chữ cái và một chữ số')).toBeVisible()

    // Sai mật khẩu hiện tại: lỗi từ backend hiện ngay dưới ô nhập.
    await page.locator('[name="newPassword"]').fill(NEW_PASSWORD)
    await page.locator('[name="confirmPassword"]').fill(NEW_PASSWORD)
    const rejected = page.waitForResponse((response) => response.url().endsWith('/api/v1/owner/account/password'))
    await page.getByRole('button', { name: 'Lưu mật khẩu mới' }).click()
    expect((await rejected).status()).toBe(400)
    await expect(page.getByText('Mat khau hien tai khong dung', { exact: true })).toBeVisible()

    await page.locator('[name="currentPassword"]').fill(TEMP_PASSWORD)
    const accepted = page.waitForResponse((response) => response.url().endsWith('/api/v1/owner/account/password'))
    await page.getByRole('button', { name: 'Lưu mật khẩu mới' }).click()
    expect((await accepted).status()).toBe(200)

    await expect(page).toHaveURL('/owner')
    await expect(page.getByRole('heading', { name: 'Xin chào, E2E Chu San Tam' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Điều hướng quản lý sân' })).toBeVisible()
    assertNoErrors()
  })

  test('the new password logs straight into the owner area', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginThroughForm(page, TEMP_OWNER_EMAIL, NEW_PASSWORD)
    await expect(page).toHaveURL('/owner')
    await expect(page.getByTestId('owner-name')).toHaveText('E2E Chu San Tam')

    await page.getByRole('button', { name: 'Đăng xuất' }).click()
    await expect(page).toHaveURL('/')
    assertNoErrors()
  })

  test('a locked owner account cannot sign in', async ({ page }) => {
    runSql(`UPDATE users SET status = 'banned' WHERE email = '${TEMP_OWNER_EMAIL}'`)
    await loginThroughForm(page, TEMP_OWNER_EMAIL, NEW_PASSWORD)
    await expect(page.getByRole('alert')).toHaveText('Tai khoan dang bi khoa.')
    await expect(page).toHaveURL('/login')
  })
})

test('the seeded owner is sent to the owner area after login', async ({ page }) => {
  const assertNoErrors = watchRuntimeErrors(page)
  await loginThroughForm(page, SEED_OWNER_EMAIL, TEMP_PASSWORD)
  await expect(page).toHaveURL('/owner')
  await expect(page.getByRole('heading', { name: 'Xin chào, Nguyen Huu Thang' })).toBeVisible()
  assertNoErrors()
})

test('players and guests cannot use the owner area', async ({ page }) => {
  const assertNoErrors = watchRuntimeErrors(page)
  await page.goto('/owner')
  await expect(page.getByRole('heading', { name: 'Đăng nhập để quản lý sân' })).toBeVisible()

  await loginByApi(page, 'an.nguyen@example.com')
  await page.goto('/owner')
  await expect(page.getByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeVisible()
  assertNoErrors()
})

test('owner area fits a mobile screen without horizontal scrolling', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
  const page = await context.newPage()
  await loginByApi(page, SEED_OWNER_EMAIL)
  for (const path of ['/owner', '/owner/change-password']) {
    await page.goto(path)
    await expect(page.locator('h1')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, path).toBeLessThanOrEqual(0)
  }
  await context.close()
})

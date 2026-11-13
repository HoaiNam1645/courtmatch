import { expect, test } from '@playwright/test'
import { loginByApi, runSql, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.2 owner profile', () => {
  let original
  let startedAt

  test.beforeAll(() => {
    startedAt = runSql('SELECT now()')
    const row = runSql(`
      SELECT json_build_object('fullName', u.full_name, 'phone', u.phone, 'businessName', p.business_name,
        'taxCode', p.tax_code, 'bankName', p.bank_name, 'bankAccountNo', p.bank_account_no,
        'bankAccountName', p.bank_account_name)
      FROM users u JOIN court_owner_profiles p ON p.user_id = u.id WHERE u.email = '${SEED_OWNER_EMAIL}'`)
    original = JSON.parse(row)
  })

  test.afterAll(() => {
    const quote = (value) => (value == null ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`)
    runSql(`
      UPDATE users SET full_name = ${quote(original.fullName)}, phone = ${quote(original.phone)}
      WHERE email = '${SEED_OWNER_EMAIL}';
      UPDATE court_owner_profiles SET business_name = ${quote(original.businessName)}, tax_code = ${quote(original.taxCode)},
        bank_name = ${quote(original.bankName)}, bank_account_no = ${quote(original.bankAccountNo)},
        bank_account_name = ${quote(original.bankAccountName)}
      WHERE user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}');
      DELETE FROM audit_logs WHERE action = 'owner.profile.update' AND created_at >= '${startedAt}';
    `)
  })

  test('shows the owner profile with read-only email and verification status', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Hồ sơ' }).click()

    await expect(page).toHaveURL('/owner/profile')
    await expect(page.getByRole('heading', { name: 'Hồ sơ chủ sân' })).toBeVisible()
    await expect(page.getByTestId('verification-status')).toHaveText('Đã xác minh')
    await expect(page.locator('[name="email"]')).toBeDisabled()
    await expect(page.locator('[name="email"]')).toHaveValue(SEED_OWNER_EMAIL)
    await expect(page.locator('[name="fullName"]')).toHaveValue(original.fullName)
    await expect(page.locator('[name="bankAccountNo"]')).toHaveValue(original.bankAccountNo)
    assertNoErrors()
  })

  test('validates fields on the client and shows server field errors', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/owner/profile'] })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/profile')

    await page.locator('[name="bankAccountNo"]').fill('12AB')
    await page.locator('[name="phone"]').fill('0123456789')
    await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
    await expect(page.getByText('Số tài khoản gồm 6 đến 20 chữ số')).toBeVisible()
    await expect(page.getByText('Số di động Việt Nam gồm 10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09')).toBeVisible()

    // Số điện thoại của người chơi seed: backend trả 409 kèm lỗi tại ô số điện thoại.
    await page.locator('[name="bankAccountNo"]').fill(original.bankAccountNo)
    await page.locator('[name="taxCode"]').fill('0401359111')
    await page.locator('[name="phone"]').fill('0901000001')
    const conflict = page.waitForResponse((response) => response.url().endsWith('/api/v1/owner/profile') && response.request().method() === 'PUT')
    await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
    expect((await conflict).status()).toBe(409)
    await expect(page.getByText('So dien thoai da ton tai')).toBeVisible()
    await expect(page.locator('[name="phone"]')).toHaveValue('0901000001')
    assertNoErrors()
  })

  test('saves the profile, reloads it from the API and updates the header', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/profile')

    await page.locator('[name="fullName"]').fill('Nguyen Huu Thang E2E')
    await page.locator('[name="phone"]').fill('+84 935 222 333')
    await page.locator('[name="taxCode"]').fill('0401359111-001')
    await page.locator('[name="bankName"]').fill('Vietcombank')
    await page.locator('[name="bankAccountNo"]').fill('0041 0000 9999')
    await page.locator('[name="bankAccountName"]').fill('nguyen huu thang')
    const saved = page.waitForResponse((response) => response.url().endsWith('/api/v1/owner/profile') && response.request().method() === 'PUT')
    await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
    expect((await saved).status()).toBe(200)

    await expect(page.getByText('Đã lưu hồ sơ chủ sân.')).toBeVisible()
    await expect(page.locator('[name="phone"]')).toHaveValue('0935222333')
    await expect(page.locator('[name="bankAccountNo"]')).toHaveValue('004100009999')
    await expect(page.locator('[name="bankAccountName"]')).toHaveValue('NGUYEN HUU THANG')
    await expect(page.getByTestId('owner-name')).toHaveText('Nguyen Huu Thang E2E')

    const apiResponse = await page.request.get(`${API_BASE_URL}/api/v1/owner/profile`, { headers: { Authorization: `Bearer ${token}` } })
    const profile = await apiResponse.json()
    expect(profile.bankName).toBe('Vietcombank')
    expect(profile.identityNumber).toBeUndefined()

    await page.reload()
    await expect(page.locator('[name="bankName"]')).toHaveValue('Vietcombank')
    assertNoErrors()
  })

  test('profile page fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/profile')
    await expect(page.getByRole('button', { name: 'Lưu hồ sơ' })).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

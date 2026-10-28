import { expect, test } from '@playwright/test'
import { loginThroughUi, watchRuntimeErrors } from './helpers'

test.describe.serial('public pages and authentication', () => {
  test('loads real venues, searches, opens a venue and renders the map page', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Gần bạn và còn lịch đẹp' })).toBeVisible()
    const firstVenue = page.getByRole('button', { name: /Badminton House Ngũ Hành Sơn/ }).first()
    await expect(firstVenue).toBeVisible()

    await page.getByPlaceholder('Tìm tên sân, quận hoặc địa chỉ').fill('khong-co-san-e2e-987654')
    await expect(page.getByText('Không tìm thấy sân phù hợp.')).toBeVisible()
    await page.getByRole('button', { name: 'Xóa từ khóa và bộ lọc' }).click()
    await expect(firstVenue).toBeVisible()

    await firstVenue.click()
    await expect(page.locator('#booking')).toBeVisible()
    await page.goto('/map')
    await expect(page.getByPlaceholder('Tìm kiếm sân quanh đây.').first()).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('validates login, rejects bad credentials, logs in by email and logs out', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/auth/login'] })
    await page.goto('/login')
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
    await expect(page.getByText('Vui lòng nhập số điện thoại')).toBeVisible()
    await expect(page.getByText('Vui lòng nhập mật khẩu')).toBeVisible()

    await page.getByRole('button', { name: 'Email', exact: true }).click()
    await page.locator('[name="courtly_login_email"]').fill('an.nguyen@example.com')
    await page.locator('[name="courtly_login_password"]').fill('sai-mat-khau')
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('khong dung')

    await page.locator('[name="courtly_login_password"]').fill('Courtly@123')
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('link', { name: /Nguyen Van An/ })).toBeVisible()
    await page.getByRole('button', { name: 'Đăng xuất' }).click()
    await expect(page.getByRole('link', { name: 'Đăng nhập' })).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('logs in by phone and validates registration and recovery forms', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await loginThroughUi(page, '0901000001', { method: 'phone' })

    await page.goto('/register')
    await page.getByRole('button', { name: 'Đăng ký', exact: true }).click()
    await expect(page.getByText('Họ và tên từ 2 đến 150 ký tự')).toBeVisible()
    await expect(page.getByText('Vui lòng nhập email')).toBeVisible()
    await expect(page.getByText('Mật khẩu từ 8 đến 72 ký tự')).toBeVisible()

    await page.goto('/forgot-password')
    await page.getByRole('button', { name: /Gửi mã xác minh/ }).click()
    await expect(page.getByText('Vui lòng nhập email đã đăng ký')).toBeVisible()
    assertNoRuntimeErrors()
  })
})

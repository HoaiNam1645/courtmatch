import { expect, test } from '@playwright/test'
import { loginByApi, watchRuntimeErrors } from './helpers'

test.describe.serial('profile and booking flows', () => {
  test.beforeEach(async ({ page }) => {
    await loginByApi(page, 'an.nguyen@example.com')
  })

  test('loads profile, validates edit form and persists a profile update', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await page.goto('/profile')
    await expect(page.getByRole('heading', { name: 'Nguyen Van An' })).toBeVisible()
    await page.getByRole('link', { name: /Chỉnh sửa hồ sơ/ }).click()
    const nameInput = page.getByLabel('Họ và tên (*)')
    const originalName = await nameInput.inputValue()
    await nameInput.fill('A')
    await page.getByRole('button', { name: /Lưu hồ sơ/ }).click()
    await expect(page.getByText('Họ và tên từ 2 đến 150 ký tự')).toBeVisible()

    await nameInput.fill(originalName)
    const bio = page.getByLabel('Giới thiệu bản thân')
    await bio.fill(`Playwright kiểm tra lúc ${new Date().toISOString()}`)
    await page.getByRole('button', { name: /Lưu hồ sơ/ }).click()
    await expect(page).toHaveURL('/profile')
    await expect(page.getByText(/Playwright kiểm tra lúc/)).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('validates and saves player preferences', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await page.goto('/profile/preferences')
    await expect(page.getByRole('heading', { name: 'Thiết lập người chơi' })).toBeVisible()
    await page.getByRole('button', { name: /Nâng cao/ }).click()
    await page.getByRole('button', { name: /Cân bằng/ }).click()
    await page.getByRole('button', { name: /Đánh đơn/ }).click()
    await page.getByRole('button', { name: 'Lưu thiết lập' }).click()
    await expect(page.getByText('Đã lưu thay đổi')).toBeVisible()

    await page.reload()
    await expect(page.getByRole('button', { name: /Nâng cao/ })).toHaveClass(/border-emerald-500/)
    await expect(page.getByRole('button', { name: /Cân bằng/ })).toHaveClass(/border-emerald-500/)
    assertNoRuntimeErrors()
  })

  test('renders booking tabs, search states, booking detail and payment access protection', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await page.goto('/bookings')
    await expect(page.getByRole('heading', { name: 'Booking của tôi' })).toBeVisible()
    await page.getByRole('button', { name: 'Đã hoàn thành' }).click()
    await expect(page.getByText('CT-20260924-0001')).toBeVisible()
    await page.getByPlaceholder('Tìm theo mã booking hoặc tên sân').fill('CT-20260924-0001')
    await page.getByRole('link', { name: /Xem chi tiết/ }).click()
    await expect(page.getByText('CT-20260924-0001')).toBeVisible()
    assertNoRuntimeErrors()

    await page.goto('/payments/278a06e1-b359-3f4e-9689-6ecb27a9f37c')
    await expect(page.getByText('Không tìm thấy giao dịch')).toBeVisible()
  })
})

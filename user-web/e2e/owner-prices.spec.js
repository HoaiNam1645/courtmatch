import { expect, test } from '@playwright/test'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  nextMondayIso,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.5 owner updates court prices', () => {
  let venue

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('splits the day into normal and peak prices and the quote follows', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/price-rules'] })
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Bảng Giá')
    makeVenueBookable(venue.id)
    const courtA = venue.courts[0]

    await page.goto(`/owner/venues/${venue.id}`)
    await page.getByRole('tab', { name: 'Bảng giá' }).click()
    const prices = page.getByTestId('court-prices-A')
    await expect(prices.getByText('Mọi ngày · 05:00–23:00')).toBeVisible()
    await expect(prices.getByText('120.000đ/giờ')).toBeVisible()

    // Thêm giờ cao điểm khi khung cả ngày còn hiệu lực: backend báo chồng lấn.
    await prices.getByRole('button', { name: 'Thêm khung giá' }).click()
    const form = prices.getByTestId('price-rule-form')
    await form.getByLabel('Từ giờ').fill('18:00')
    await form.getByLabel('Đến giờ').fill('17:00')
    await form.getByRole('button', { name: 'Lưu quy tắc giá' }).click()
    await expect(form.getByText('Giờ kết thúc phải sau giờ bắt đầu')).toBeVisible()
    await expect(form.getByText('Vui lòng nhập giá mỗi giờ')).toBeVisible()

    await form.getByLabel('Từ giờ').fill('17:00')
    await form.getByLabel('Đến giờ').fill('23:00')
    await form.getByLabel('Giá mỗi giờ').fill('150000')
    const overlap = page.waitForResponse((response) => response.url().endsWith(`/courts/${courtA.id}/price-rules`) && response.request().method() === 'POST')
    await form.getByRole('button', { name: 'Lưu quy tắc giá' }).click()
    expect((await overlap).status()).toBe(409)
    await expect(form.getByRole('alert')).toContainText('chong lan voi quy tac Moi ngay 05:00-23:00')
    await form.getByRole('button', { name: 'Hủy' }).click()

    // Rút khung cả ngày về 05:00–17:00 rồi thêm giờ cao điểm.
    await prices.getByRole('button', { name: 'Sửa giá Mọi ngày 05:00' }).click()
    const editForm = prices.getByTestId('price-rule-form')
    await editForm.getByLabel('Đến giờ').fill('17:00')
    await editForm.getByRole('button', { name: 'Lưu quy tắc giá' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã cập nhật quy tắc giá.')

    await page.getByTestId('court-prices-A').getByRole('button', { name: 'Thêm khung giá' }).click()
    const peakForm = page.getByTestId('price-rule-form')
    await peakForm.getByLabel('Từ giờ').fill('17:00')
    await peakForm.getByLabel('Đến giờ').fill('23:00')
    await peakForm.getByLabel('Giá mỗi giờ').fill('150000')
    await peakForm.getByRole('button', { name: 'Lưu quy tắc giá' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã thêm quy tắc giá.')
    await expect(page.getByTestId('court-prices-A').getByText('Mọi ngày · 17:00–23:00')).toBeVisible()
    await expect(page.getByTestId('court-prices-A').getByText('150.000đ/giờ')).toBeVisible()

    // 16:30–18:00: 30 phút giá thường + 60 phút giờ cao điểm.
    const quote = await page.request.post(`${API_BASE_URL}/api/v1/bookings/quote`, {
      data: { courtId: courtA.id, startTime: `${nextMondayIso()}T16:30:00+07:00`, durationMinutes: 90 },
    })
    expect(Number((await quote.json()).totalAmount)).toBe(210000)
    assertNoErrors()
  })

  test('stops applying a price rule and keeps it as history', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=prices`)
    page.once('dialog', (dialog) => dialog.accept())
    const prices = page.getByTestId('court-prices-A')
    const peak = prices.locator('li').filter({ hasText: 'Mọi ngày · 17:00–23:00' })
    await peak.getByRole('button', { name: 'Ngừng' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã ngừng áp dụng quy tắc giá.')
    await expect(page.getByTestId('court-prices-A').getByText('Đã ngừng áp dụng (1)')).toBeVisible()
    await expect(page.getByTestId('court-prices-A').locator('li').filter({ hasText: 'Mọi ngày · 17:00–23:00' }).getByRole('button', { name: 'Ngừng' })).toHaveCount(0)
    assertNoErrors()
  })

  test('price tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=prices`)
    await page.getByTestId('court-prices-A').getByRole('button', { name: 'Thêm khung giá' }).click()
    await expect(page.getByTestId('price-rule-form')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

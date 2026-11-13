import { expect, test } from '@playwright/test'
import path from 'node:path'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const fixture = (name) => path.resolve(import.meta.dirname, 'fixtures', name)

async function imageSources(page) {
  return page.locator('[data-testid^="venue-image-"] img').evaluateAll((items) => items.map((item) => item.getAttribute('src')))
}

test.describe.serial('2.3.6 owner uploads venue images', () => {
  let venue

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('uploads several images; the first one becomes the cover', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Hình Ảnh')

    await page.goto(`/owner/venues/${venue.id}`)
    await page.getByRole('tab', { name: 'Hình ảnh' }).click()
    await expect(page.getByRole('heading', { name: 'Sân chưa có ảnh' })).toBeVisible()

    // File không phải ảnh bị chặn ngay ở trình duyệt.
    await page.getByLabel('Chọn ảnh sân').setInputFiles({ name: 'ghi-chu.png', mimeType: 'text/plain', buffer: Buffer.from('khong phai anh') })
    await expect(page.getByRole('alert')).toContainText('chỉ nhận JPEG, PNG, WebP và tối đa 5MB')

    await page.getByLabel('Chọn ảnh sân').setInputFiles([fixture('court-1.png'), fixture('court-2.png'), fixture('court-3.jpg')])
    await expect(page.getByRole('status')).toHaveText('Đã tải lên 3 ảnh.')
    await expect(page.getByTestId('image-count')).toHaveText('3/20 ảnh')
    await expect(page.getByTestId('venue-image-0').getByTestId('cover-badge')).toBeVisible()
    await expect(page.getByTestId('cover-badge')).toHaveCount(1)

    // Ảnh được backend phục vụ thật, trình duyệt hiển thị được.
    const firstImage = page.getByTestId('venue-image-0').locator('img')
    await expect.poll(() => firstImage.evaluate((img) => img.complete && img.naturalWidth)).toBe(160)
    expect(await firstImage.getAttribute('src')).toContain(`/api/v1/media/venues/${venue.id}/`)
    assertNoErrors()
  })

  test('changes cover, order and caption, then deletes the cover image', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=images`)
    await expect(page.getByTestId('image-count')).toHaveText('3/20 ảnh')
    const [first, second, third] = await imageSources(page)

    await page.getByTestId('venue-image-2').getByRole('button', { name: 'Đặt làm ảnh bìa' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã đổi ảnh bìa.')
    await expect(page.getByTestId('venue-image-2').getByTestId('cover-badge')).toBeVisible()
    await expect(page.getByTestId('cover-badge')).toHaveCount(1)

    await page.getByRole('button', { name: 'Đưa ảnh 3 lên trước' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã cập nhật thứ tự ảnh.')
    await expect.poll(() => imageSources(page)).toEqual([first, third, second])

    await page.getByLabel('Chú thích ảnh 1').fill('Mặt tiền sân')
    await page.getByTestId('venue-image-0').getByRole('button', { name: 'Lưu' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã lưu chú thích ảnh.')
    await expect(page.getByLabel('Chú thích ảnh 1')).toHaveValue('Mặt tiền sân')

    page.once('dialog', (dialog) => dialog.accept())
    await page.getByRole('button', { name: 'Xóa ảnh 2' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã xóa ảnh.')
    await expect(page.getByTestId('image-count')).toHaveText('2/20 ảnh')
    await expect.poll(() => imageSources(page)).toEqual([first, second])
    // Ảnh bìa vừa xóa: ảnh đứng đầu còn lại thành ảnh bìa.
    await expect(page.getByTestId('venue-image-0').getByTestId('cover-badge')).toBeVisible()

    const deletedFile = await page.request.get(third.replace('http://localhost:8080', API_BASE_URL))
    expect(deletedFile.status()).toBe(404)
    assertNoErrors()
  })

  test('players see the same images and order on the public venue page', async ({ page }) => {
    makeVenueBookable(venue.id)
    const response = await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.slug}`)
    const detail = await response.json()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=images`)
    await expect(page.getByTestId('image-count')).toHaveText('2/20 ảnh')
    expect(detail.images.map((image) => image.imageUrl)).toEqual(await imageSources(page))
    expect(detail.images[0].cover).toBe(true)
    expect(detail.images[0].caption).toBe('Mặt tiền sân')
  })

  test('image tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=images`)
    await expect(page.getByTestId('image-count')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

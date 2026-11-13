import { expect, test } from '@playwright/test'
import { deleteE2eVenues, loginByApi, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const VENUE_NAME = 'E2E Sân Thử Nghiệm Sơn Trà'

test.describe.serial('2.3.3 owner adds a new venue', () => {
  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('lists the owner venues with their approval status', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Sân của tôi' }).click()

    await expect(page).toHaveURL('/owner/venues')
    const seeded = page.getByTestId('owner-venue-san-cau-long-hai-chau')
    await expect(seeded).toBeVisible()
    await expect(seeded.getByTestId('approval-status')).toHaveText('Đã duyệt')
    await expect(seeded.getByText('3 sân con')).toBeVisible()
    assertNoErrors()
  })

  test('validates and submits a new venue that waits for admin approval', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/venues')
    await page.getByRole('link', { name: 'Thêm sân mới' }).first().click()
    await expect(page).toHaveURL('/owner/venues/new')
    await expect(page.getByRole('heading', { name: 'Thêm sân cầu lông mới' })).toBeVisible()

    // Quận/huyện lấy từ API, không khai báo cứng.
    await expect(page.locator('select[name="district"] option')).toHaveCount(8)

    await page.getByRole('button', { name: 'Gửi sân chờ duyệt' }).click()
    await expect(page.getByText('Vui lòng nhập tên sân', { exact: true })).toBeVisible()
    await expect(page.getByText('Vui lòng nhập địa chỉ', { exact: true })).toBeVisible()
    await expect(page.getByText('Vui lòng chọn quận/huyện', { exact: true })).toBeVisible()
    await expect(page.getByText('Vui lòng chọn vị trí sân trên bản đồ')).toBeVisible()
    await expect(page.getByText('Vui lòng nhập số điện thoại của sân')).toBeVisible()

    await page.locator('[name="name"]').fill(VENUE_NAME)
    await page.locator('textarea[name="description"]').fill('Sân E2E kiểm thử thêm sân mới.')
    await page.locator('[name="address"]').fill('99 Võ Nguyên Giáp')
    await page.locator('[name="ward"]').fill('Phước Mỹ')
    await page.locator('select[name="district"]').selectOption('Sơn Trà')
    await page.locator('[name="phone"]').fill('0236 3777 666')

    const map = page.locator('.leaflet-container')
    await expect(map).toBeVisible()
    await map.click()
    await expect(page.getByTestId('venue-coordinates')).toContainText('Đã ghim: 16.')

    await page.getByLabel('Bãi xe').check()
    await page.getByLabel('Giá Bãi xe').fill('0')
    await page.getByLabel('Ghi chú Bãi xe').fill('Miễn phí xe máy')

    await page.getByRole('button', { name: 'Thêm sân con' }).click()
    await page.getByLabel('Tên sân con 2').fill('Sân VIP')
    await page.getByLabel('Mã sân con 2').fill('a')
    await page.getByLabel('Loại sân 2').selectOption('vip')
    await page.getByLabel('Mặt sân 2').selectOption('wood')
    await page.getByRole('button', { name: 'Gửi sân chờ duyệt' }).click()
    await expect(page.getByTestId('court-row-1').getByText('Mã sân con đã tồn tại trong sân này')).toBeVisible()

    await page.getByLabel('Mã sân con 2').fill('VIP')
    const created = page.waitForResponse((response) => response.url().endsWith('/api/v1/owner/venues') && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Gửi sân chờ duyệt' }).click()
    const response = await created
    expect(response.status()).toBe(201)
    const venue = await response.json()
    expect(venue.approvalStatus).toBe('pending')
    expect(venue.phone).toBe('02363777666')
    expect(venue.courts.map((court) => court.courtCode)).toEqual(['A', 'VIP'])

    await expect(page).toHaveURL('/owner/venues')
    await expect(page.getByRole('status')).toContainText(`Đã gửi sân “${VENUE_NAME}”`)
    const card = page.getByTestId(`owner-venue-${venue.slug}`)
    await expect(card.getByTestId('approval-status')).toHaveText('Chờ duyệt')
    await expect(card.getByText('2 sân con')).toBeVisible()

    // Người chơi chưa thấy sân chưa duyệt.
    const publicResponse = await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.slug}`)
    expect(publicResponse.status()).toBe(404)
    const ownerList = await page.request.get(`${API_BASE_URL}/api/v1/owner/venues?size=100`, { headers: { Authorization: `Bearer ${token}` } })
    expect((await ownerList.json()).content.some((item) => item.id === venue.id)).toBeTruthy()
    assertNoErrors()
  })

  test('create venue form fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    for (const path of ['/owner/venues', '/owner/venues/new']) {
      await page.goto(path)
      await expect(page.locator('h1')).toBeVisible()
      await page.waitForTimeout(500)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      expect(overflow, path).toBeLessThanOrEqual(0)
    }
    await context.close()
  })
})

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

test.describe.serial('2.3.4 owner updates venue information', () => {
  let venue

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('edits general information and amenities of a venue', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Cập Nhật')

    await page.goto('/owner/venues')
    await page.getByRole('link', { name: 'Quản lý E2E Sân Cập Nhật' }).click()
    await expect(page).toHaveURL(`/owner/venues/${venue.id}`)
    await expect(page.getByRole('heading', { name: 'E2E Sân Cập Nhật', level: 1 })).toBeVisible()
    await expect(page.getByRole('tab', { name: 'Thông tin' })).toHaveAttribute('aria-selected', 'true')
    await expect(page.locator('[name="name"]')).toHaveValue('E2E Sân Cập Nhật')
    await expect(page.getByTestId('venue-coordinates')).toContainText('16.060900, 108.223700')

    await page.locator('[name="address"]').fill('')
    await page.getByRole('button', { name: 'Lưu thông tin sân' }).click()
    await expect(page.getByText('Vui lòng nhập địa chỉ', { exact: true })).toBeVisible()

    await page.locator('[name="name"]').fill('E2E Sân Cập Nhật Mới')
    await page.locator('[name="address"]').fill('25 Bạch Đằng')
    await page.getByLabel('Nước uống').check()
    await page.getByLabel('Giá Nước uống').fill('10000')
    const saved = page.waitForResponse((response) => response.url().endsWith(`/api/v1/owner/venues/${venue.id}`) && response.request().method() === 'PUT')
    await page.getByRole('button', { name: 'Lưu thông tin sân' }).click()
    expect((await saved).status()).toBe(200)

    await expect(page.getByRole('status')).toHaveText('Đã lưu thông tin sân.')
    await expect(page.getByRole('heading', { name: 'E2E Sân Cập Nhật Mới', level: 1 })).toBeVisible()
    await expect(page.getByLabel('Giá Nước uống')).toHaveValue('10000')
    assertNoErrors()
  })

  test('adds a court, rejects a duplicate code and puts a court into maintenance', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: [`/api/v1/owner/venues/${venue.id}/courts`] })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}`)
    await page.getByRole('tab', { name: 'Sân con' }).click()
    await expect(page).toHaveURL(`/owner/venues/${venue.id}?tab=courts`)

    await page.getByRole('button', { name: 'Thêm sân con' }).click()
    await page.getByLabel('Tên sân con mới').fill('Sân trùng')
    await page.getByLabel('Mã sân con mới').fill('a')
    const duplicate = page.waitForResponse((response) => response.url().endsWith(`/api/v1/owner/venues/${venue.id}/courts`))
    await page.getByRole('button', { name: 'Thêm sân con' }).click()
    expect((await duplicate).status()).toBe(409)
    await expect(page.getByText('Ma san con da ton tai trong san nay', { exact: true })).toBeVisible()

    await page.getByLabel('Tên sân con mới').fill('Sân B')
    await page.getByLabel('Mã sân con mới').fill('b')
    await page.getByLabel('Loại sân mới').selectOption('vip')
    await page.getByRole('button', { name: 'Thêm sân con' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã thêm sân con B.')

    const editorB = page.getByTestId('court-editor-B')
    await expect(editorB).toBeVisible()
    await editorB.getByLabel('Trạng thái sân con B').selectOption('maintenance')
    await editorB.getByRole('button', { name: 'Lưu sân con' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã lưu sân con B.')
    await expect(page.getByTestId('court-editor-B').getByTestId('court-status')).toHaveText('Bảo trì')
    assertNoErrors()
  })

  test('blocks stopping a court that still has an upcoming booking', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/courts/'] })
    makeVenueBookable(venue.id)
    const courtA = venue.courts.find((court) => court.courtCode === 'A')

    const player = await loginByApi(page, 'an.nguyen@example.com')
    const booking = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${player.token}` },
      data: { courtId: courtA.id, startTime: `${nextMondayIso()}T09:00:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    expect(booking.status(), await booking.text()).toBe(201)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=courts`)
    const editorA = page.getByTestId('court-editor-A')
    await editorA.getByLabel('Trạng thái sân con A').selectOption('inactive')
    const rejected = page.waitForResponse((response) => response.url().endsWith(`/courts/${courtA.id}`))
    await editorA.getByRole('button', { name: 'Lưu sân con' }).click()
    expect((await rejected).status()).toBe(409)
    await expect(editorA.getByRole('alert')).toContainText('khung gio da duoc dat chua choi')
    await expect(editorA.getByTestId('court-status')).toHaveText('Hoạt động')
    assertNoErrors()
  })

  test('venue management tabs fit a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    for (const tab of ['info', 'courts']) {
      await page.goto(`/owner/venues/${venue.id}?tab=${tab}`)
      await expect(page.locator('h1')).toBeVisible()
      await page.waitForTimeout(500)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      expect(overflow, tab).toBeLessThanOrEqual(0)
    }
    await context.close()
  })
})

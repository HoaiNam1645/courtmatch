import { expect, test } from '@playwright/test'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  nextMondayIso,
  runSql,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.7 owner manages operating hours', () => {
  let venue

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('declares the weekly hours of a new venue', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Giờ Hoạt Động')

    await page.goto(`/owner/venues/${venue.id}`)
    await page.getByRole('tab', { name: 'Giờ hoạt động' }).click()
    await expect(page.getByTestId('hours-day-1').getByText('Chưa khai báo')).toBeVisible()
    await expect(page.getByLabel('Nghỉ Thứ hai')).toBeChecked()

    await page.getByLabel('Nghỉ Thứ hai').uncheck()
    await page.getByLabel('Giờ mở cửa Thứ hai').fill('06:00')
    await page.getByLabel('Giờ đóng cửa Thứ hai').fill('22:00')
    await page.getByRole('button', { name: 'Áp dụng giờ Thứ hai cho cả tuần' }).click()
    await expect(page.getByLabel('Giờ mở cửa Chủ nhật')).toHaveValue('06:00')
    await page.getByLabel('Nghỉ Chủ nhật').check()
    await page.getByLabel('Giờ đóng cửa Thứ ba').fill('05:00')
    await page.getByRole('button', { name: 'Lưu giờ hoạt động' }).click()
    await expect(page.getByTestId('hours-day-2').getByText('Giờ đóng cửa phải sau giờ mở cửa')).toBeVisible()

    await page.getByLabel('Giờ đóng cửa Thứ ba').fill('22:00')
    const saved = page.waitForResponse((response) => response.url().endsWith(`/venues/${venue.id}/operating-hours`) && response.request().method() === 'PUT')
    await page.getByRole('button', { name: 'Lưu giờ hoạt động' }).click()
    expect((await saved).status()).toBe(200)
    await expect(page.getByRole('status')).toHaveText('Đã lưu giờ hoạt động.')
    await expect(page.getByText('Chưa khai báo')).toHaveCount(0)
    await expect(page.getByLabel('Nghỉ Chủ nhật')).toBeChecked()
    await expect(page.getByLabel('Giờ mở cửa Thứ bảy')).toHaveValue('06:00')

    // Lịch trống của người chơi đọc giờ mới.
    makeVenueBookable(venue.id)
    const sunday = runSql(`SELECT ('${nextMondayIso()}'::date + 6)::text`)
    const availability = await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.id}/availability?date=${sunday}`)
    expect((await availability.json()).closed).toBe(true)
    assertNoErrors()
  })

  test('warns about upcoming bookings outside the new hours without cancelling them', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const created = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${player.token}` },
      data: { courtId: venue.courts[0].id, startTime: `${nextMondayIso()}T06:30:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    expect(created.status(), await created.text()).toBe(201)
    const booking = await created.json()

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=hours`)
    await expect(page.getByTestId('hours-conflicts')).toHaveCount(0)
    await page.getByLabel('Giờ mở cửa Thứ hai').fill('08:00')
    await page.getByRole('button', { name: 'Lưu giờ hoạt động' }).click()

    const warning = page.getByTestId('hours-conflicts')
    await expect(warning).toContainText('1 booking chưa chơi nằm ngoài giờ hoạt động hiện tại')
    await expect(warning).toContainText(booking.bookingCode)

    const detail = await page.request.get(`${API_BASE_URL}/api/v1/bookings/${booking.id}`, { headers: { Authorization: `Bearer ${player.token}` } })
    expect((await detail.json()).status).toBe('pending_owner_confirmation')
    assertNoErrors()
  })

  test('hours tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=hours`)
    await expect(page.getByTestId('hours-day-7')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

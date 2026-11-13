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

test.describe.serial('2.3.10 owner blocks court time slots', () => {
  let venue
  let monday

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('blocks a time range on several courts and the slots disappear for players', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/blocks'] })
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Khóa Giờ', [
      { name: 'Sân A', courtCode: 'A', courtType: 'standard', indoor: true },
      { name: 'Sân B', courtCode: 'B', courtType: 'standard', indoor: true },
    ])
    makeVenueBookable(venue.id)
    monday = nextMondayIso()

    await page.goto(`/owner/venues/${venue.id}?tab=blocks`)
    await expect(page.getByRole('heading', { name: 'Không có khung giờ nào đang bị khóa' })).toBeVisible()

    await page.getByRole('button', { name: 'Khóa khung giờ' }).click()
    await expect(page.getByText('Vui lòng nhập giờ bắt đầu')).toBeVisible()
    await expect(page.getByText('Vui lòng nhập lý do khóa')).toBeVisible()

    await page.getByLabel('Sân B').check()
    await page.getByLabel('Ngày khóa').fill(monday)
    await page.getByLabel('Khóa từ giờ').fill('11:00')
    await page.getByLabel('Khóa đến giờ').fill('10:00')
    await page.getByLabel('Lý do khóa', { exact: true }).fill('Bảo trì lưới')
    await page.getByRole('button', { name: 'Khóa khung giờ' }).click()
    await expect(page.getByText('Giờ kết thúc phải sau giờ bắt đầu')).toBeVisible()

    await page.getByLabel('Khóa từ giờ').fill('10:00')
    await page.getByLabel('Khóa đến giờ').fill('12:00')
    const created = page.waitForResponse((response) => response.url().endsWith(`/venues/${venue.id}/blocks`) && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Khóa khung giờ' }).click()
    expect((await created).status()).toBe(201)
    await expect(page.getByRole('status')).toHaveText('Đã khóa 2 sân con.')
    await expect(page.getByTestId('block-list').locator('li')).toHaveCount(2)
    await expect(page.getByTestId('block-list')).toContainText('Bảo trì lưới')

    const availability = await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.id}/availability?date=${monday}`)
    const courts = (await availability.json()).courts
    for (const court of courts) {
      expect(court.slots.find((slot) => slot.startTime === '10:00').status).toBe('blocked')
      expect(court.slots.find((slot) => slot.startTime === '11:00').status).toBe('blocked')
      expect(court.slots.find((slot) => slot.startTime === '12:00').status).toBe('available')
    }
    assertNoErrors()
  })

  test('rejects blocking a slot that already has a booking', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/blocks'] })
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const booking = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${player.token}` },
      data: { courtId: venue.courts[0].id, startTime: `${monday}T15:00:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    const bookingCode = (await booking.json()).bookingCode

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=blocks`)
    await page.getByLabel('Sân A', { exact: true }).check()
    await page.getByLabel('Ngày khóa').fill(monday)
    await page.getByLabel('Khóa từ giờ').fill('14:30')
    await page.getByLabel('Khóa đến giờ').fill('15:30')
    await page.getByLabel('Lý do khóa', { exact: true }).fill('Sự kiện')
    const rejected = page.waitForResponse((response) => response.url().endsWith(`/venues/${venue.id}/blocks`) && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Khóa khung giờ' }).click()
    expect((await rejected).status()).toBe(409)
    await expect(page.getByRole('alert')).toContainText(bookingCode)
    assertNoErrors()
  })

  test('blocks a free slot straight from the schedule', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=schedule`)
    await page.getByLabel('Chọn ngày').fill(monday)
    await page.getByTestId('slot-A-18:00').click()
    await page.getByLabel('Lý do khóa ô này').fill('Dọn sân')
    await page.getByRole('button', { name: 'Khóa khung giờ này' }).click()
    await expect(page.getByTestId('slot-A-18:00')).toHaveAttribute('data-status', 'blocked')
    await page.getByTestId('slot-A-18:00').click()
    await expect(page.getByTestId('slot-detail')).toContainText('Dọn sân')
    assertNoErrors()
  })

  test('block tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=blocks`)
    await expect(page.getByTestId('block-list')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

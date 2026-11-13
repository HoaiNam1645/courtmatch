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

test.describe.serial('2.3.9 owner configures venue time slots', () => {
  let venue

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('switches the slot step to 30 minutes and players see the finer slots', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Khung Giờ')
    makeVenueBookable(venue.id)

    await page.goto(`/owner/venues/${venue.id}?tab=slots`)
    await expect(page.getByLabel('60 phút (1 giờ)')).toBeChecked()
    await expect(page.getByTestId('slot-preview-1')).toContainText('05:00–23:00')
    await expect(page.getByTestId('slot-preview-1')).toContainText('18 khung')
    await expect(page.getByRole('button', { name: 'Lưu bước khung giờ' })).toBeDisabled()

    await page.getByLabel('30 phút').check()
    await page.getByRole('button', { name: 'Lưu bước khung giờ' }).click()
    await expect(page.getByRole('status')).toHaveText('Đã đổi bước khung giờ sang 30 phút.')
    await expect(page.getByTestId('slot-preview-1')).toContainText('36 khung')

    const availability = await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.id}/availability?date=${nextMondayIso()}&durationMinutes=60`)
    const slots = (await availability.json()).courts[0].slots
    expect(slots[0].startTime).toBe('05:00')
    expect(slots[1].startTime).toBe('05:30')

    await page.goto(`/owner/venues/${venue.id}?tab=schedule`)
    await page.getByLabel('Chọn ngày').fill(nextMondayIso())
    await expect(page.getByTestId('slot-A-05:30')).toBeVisible()
    assertNoErrors()
  })

  test('warns about slots without a price after the price list is cut short', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/price-rules'] })
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    const prices = await page.request.get(`${API_BASE_URL}/api/v1/owner/venues/${venue.id}/price-rules`, { headers: { Authorization: `Bearer ${token}` } })
    const court = (await prices.json()).courts[0]
    const rule = court.rules[0]
    const shortened = await page.request.put(`${API_BASE_URL}/api/v1/owner/venues/${venue.id}/courts/${court.courtId}/price-rules/${rule.id}`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { startTime: '05:00', endTime: '21:00', pricePerHour: 120000 },
    })
    expect(shortened.status(), await shortened.text()).toBe(200)

    await page.goto(`/owner/venues/${venue.id}?tab=slots`)
    await expect(page.getByTestId('unpriced-warning')).toContainText('sân A thiếu 4 khung')
    assertNoErrors()
  })

  test('slot tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=slots`)
    await expect(page.getByTestId('slot-preview')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

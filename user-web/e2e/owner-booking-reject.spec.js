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

test.describe.serial('2.3.14 owner rejects a booking request', () => {
  let venue
  let monday
  let player

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  async function book(page, time) {
    const response = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${player.token}` },
      data: { courtId: venue.courts[0].id, startTime: `${monday}T${time}:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    expect(response.status(), await response.text()).toBe(201)
    return response.json()
  }

  async function openRequests(page) {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/booking-requests')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
  }

  test('requires a reason from the catalog, then rejects and frees the slot', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Từ Chối')
    makeVenueBookable(venue.id)
    monday = nextMondayIso()
    player = await loginByApi(page, 'an.nguyen@example.com')
    const booking = await book(page, '09:00')

    await openRequests(page)
    const card = page.getByTestId(`booking-request-${booking.bookingCode}`)
    await card.getByRole('button', { name: 'Từ chối' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toContainText(`Từ chối yêu cầu ${booking.bookingCode}`)
    // Danh mục lý do lấy từ API.
    await expect(dialog.getByLabel('Lý do từ chối').locator('option')).toHaveCount(6)

    await dialog.getByRole('button', { name: 'Từ chối yêu cầu' }).click()
    await expect(dialog.getByText('Vui lòng chọn lý do từ chối')).toBeVisible()

    await dialog.getByLabel('Lý do từ chối').selectOption('other')
    await dialog.getByRole('button', { name: 'Từ chối yêu cầu' }).click()
    await expect(dialog.getByText(/Vui lòng nhập ghi chú/)).toBeVisible()

    await dialog.getByLabel('Lý do từ chối').selectOption('maintenance')
    await dialog.getByLabel('Ghi chú từ chối').fill('Thay lưới mới')
    const rejected = page.waitForResponse((response) => response.url().endsWith(`/booking-requests/${booking.id}/reject`))
    await dialog.getByRole('button', { name: 'Từ chối yêu cầu' }).click()
    expect((await rejected).status()).toBe(200)
    await expect(dialog).toHaveCount(0)
    await expect(page.getByRole('status')).toContainText(`Đã từ chối yêu cầu ${booking.bookingCode}`)
    await expect(page.getByTestId(`booking-request-${booking.bookingCode}`)).toHaveCount(0)

    await page.getByLabel('Lọc theo trạng thái').selectOption('rejected')
    await expect(page.getByTestId(`booking-request-${booking.bookingCode}`).getByTestId('rejection-reason'))
      .toContainText('San dang bao tri hoac sua chua: Thay lưới mới')

    // Khung giờ được giải phóng cho người khác.
    const availability = await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.id}/availability?date=${monday}`)
    expect((await availability.json()).courts[0].slots.find((slot) => slot.startTime === '09:00').status).toBe('available')
    const detail = await page.request.get(`${API_BASE_URL}/api/v1/bookings/${booking.id}`, { headers: { Authorization: `Bearer ${player.token}` } })
    expect((await detail.json()).status).toBe('rejected')
    assertNoErrors()
  })

  test('closing the dialog leaves the request untouched; Escape closes it', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const booking = await book(page, '12:00')
    await openRequests(page)
    const card = page.getByTestId(`booking-request-${booking.bookingCode}`)
    await card.getByRole('button', { name: 'Từ chối' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await card.getByRole('button', { name: 'Từ chối' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Hủy' }).click()
    await expect(card.getByTestId('request-status')).toHaveText('Chờ chủ sân duyệt')
    assertNoErrors()
  })

  test('shows the server error when the request was already handled elsewhere', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/reject'] })
    const booking = await book(page, '15:00')
    await openRequests(page)
    const card = page.getByTestId(`booking-request-${booking.bookingCode}`)
    await expect(card).toBeVisible()
    await page.request.post(`${API_BASE_URL}/api/v1/bookings/${booking.id}/cancel`, { headers: { Authorization: `Bearer ${player.token}` }, data: { reason: 'Đổi lịch' } })

    await card.getByRole('button', { name: 'Từ chối' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('Lý do từ chối').selectOption('slot_unavailable')
    await dialog.getByRole('button', { name: 'Từ chối yêu cầu' }).click()
    await expect(dialog.getByRole('alert')).toContainText('khong con cho chu san duyet')
    await expect(dialog).toBeVisible()
    assertNoErrors()
  })

  test('reject dialog fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    const booking = await book(page, '18:00')
    await openRequests(page)
    await page.getByTestId(`booking-request-${booking.bookingCode}`).getByRole('button', { name: 'Từ chối' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    const box = await page.getByRole('dialog').boundingBox()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(375)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

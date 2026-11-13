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

test.describe.serial('2.3.13 owner accepts a booking request', () => {
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

  test('accepts a request: it leaves the pending list and waits for the player to pay', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Xác Nhận')
    makeVenueBookable(venue.id)
    monday = nextMondayIso()
    player = await loginByApi(page, 'an.nguyen@example.com')
    const booking = await book(page, '09:00')

    await openRequests(page)
    const card = page.getByTestId(`booking-request-${booking.bookingCode}`)
    // Huỷ hộp xác nhận thì không đổi gì.
    page.once('dialog', (dialog) => dialog.dismiss())
    await card.getByRole('button', { name: 'Chấp nhận' }).click()
    await expect(card.getByTestId('request-status')).toHaveText('Chờ chủ sân duyệt')

    page.once('dialog', (dialog) => dialog.accept())
    const accepted = page.waitForResponse((response) => response.url().endsWith(`/booking-requests/${booking.id}/accept`))
    await card.getByRole('button', { name: 'Chấp nhận' }).click()
    expect((await accepted).status()).toBe(200)
    await expect(page.getByRole('status')).toContainText(`Đã chấp nhận yêu cầu ${booking.bookingCode}`)
    await expect(page.getByTestId(`booking-request-${booking.bookingCode}`)).toHaveCount(0)

    await page.getByLabel('Lọc theo trạng thái').selectOption('awaiting_payment')
    const waiting = page.getByTestId(`booking-request-${booking.bookingCode}`)
    await expect(waiting.getByTestId('request-status')).toHaveText('Chờ thanh toán')
    await expect(waiting.getByTestId('payment-deadline')).toContainText('Nguyen Huu Thang')
    await expect(waiting.getByRole('button', { name: 'Chấp nhận' })).toHaveCount(0)

    const detail = await page.request.get(`${API_BASE_URL}/api/v1/bookings/${booking.id}`, { headers: { Authorization: `Bearer ${player.token}` } })
    expect((await detail.json()).status).toBe('awaiting_payment')
    const payment = await page.request.get(`${API_BASE_URL}/api/v1/payments/by-booking/${booking.id}`, { headers: { Authorization: `Bearer ${player.token}` } })
    expect(payment.status()).toBe(200)
    assertNoErrors()
  })

  test('an expired request cannot be accepted from the UI', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const booking = await book(page, '12:00')
    runSql(`UPDATE bookings SET owner_response_expires_at = now() - interval '1 minute' WHERE id = '${booking.id}'`)
    await openRequests(page)
    const card = page.getByTestId(`booking-request-${booking.bookingCode}`)
    await expect(card.getByTestId('request-deadline')).toHaveText('Đã quá hạn phản hồi')
    await expect(card.getByRole('button', { name: 'Chấp nhận' })).toHaveCount(0)
    assertNoErrors()
  })

  test('shows the server error and refreshes when the request was already handled elsewhere', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/accept'] })
    const booking = await book(page, '15:00')
    await openRequests(page)
    const card = page.getByTestId(`booking-request-${booking.bookingCode}`)
    await expect(card).toBeVisible()
    // Người chơi huỷ ở nơi khác trong lúc chủ sân đang xem danh sách.
    const cancelled = await page.request.post(`${API_BASE_URL}/api/v1/bookings/${booking.id}/cancel`, {
      headers: { Authorization: `Bearer ${player.token}` }, data: { reason: 'Đổi lịch' },
    })
    expect(cancelled.status()).toBe(200)

    page.once('dialog', (dialog) => dialog.accept())
    const conflict = page.waitForResponse((response) => response.url().endsWith(`/booking-requests/${booking.id}/accept`))
    await card.getByRole('button', { name: 'Chấp nhận' }).click()
    expect((await conflict).status()).toBe(409)
    await expect(page.getByRole('alert')).toContainText('khong con cho chu san duyet')
    await expect(page.getByTestId(`booking-request-${booking.bookingCode}`)).toHaveCount(0)
    assertNoErrors()
  })

  test('accept controls fit a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    const booking = await book(page, '18:00')
    await openRequests(page)
    await expect(page.getByTestId(`booking-request-${booking.bookingCode}`).getByRole('button', { name: 'Chấp nhận' })).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

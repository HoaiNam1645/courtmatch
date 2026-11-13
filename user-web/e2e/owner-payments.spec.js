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

test.describe.serial('2.3.16 owner follows direct payments', () => {
  let venue
  let codes = {}

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('tracks payments through every status with counts, filters and the receiving account', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Thanh Toán')
    makeVenueBookable(venue.id)
    const monday = nextMondayIso()
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const ownerAuth = { Authorization: `Bearer ${owner.token}` }
    const playerAuth = { Authorization: `Bearer ${player.token}` }

    const made = []
    for (const time of ['07:00', '09:00', '12:00', '15:00']) {
      const booking = await (await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
        headers: playerAuth, data: { courtId: venue.courts[0].id, startTime: `${monday}T${time}:00+07:00`, durationMinutes: 60, note: 'E2E' },
      })).json()
      expect((await page.request.post(`${API_BASE_URL}/api/v1/owner/booking-requests/${booking.id}/accept`, { headers: ownerAuth })).status()).toBe(200)
      const payment = await (await page.request.get(`${API_BASE_URL}/api/v1/payments/by-booking/${booking.id}`, { headers: playerAuth })).json()
      made.push({ booking, paymentId: payment.id })
    }
    const [confirmed, rejected, reported, waiting] = made
    codes = { confirmed: confirmed.booking.bookingCode, rejected: rejected.booking.bookingCode, reported: reported.booking.bookingCode, waiting: waiting.booking.bookingCode }
    for (const item of [confirmed, rejected, reported]) {
      expect((await page.request.post(`${API_BASE_URL}/api/v1/payments/${item.paymentId}/report-paid`, {
        headers: playerAuth, data: { method: 'owner_bank_transfer', proofUrl: item === confirmed ? 'https://example.com/chuyen-khoan.png' : null },
      })).status()).toBe(200)
    }
    expect((await page.request.post(`${API_BASE_URL}/api/v1/payments/${confirmed.paymentId}/confirm-received`, { headers: ownerAuth })).status()).toBe(200)
    expect((await page.request.post(`${API_BASE_URL}/api/v1/payments/${rejected.paymentId}/reject-report`, { headers: ownerAuth, data: { reason: 'Chưa thấy tiền về' } })).status()).toBe(200)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Thanh toán' }).click()
    await expect(page).toHaveURL('/owner/payments')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)

    const list = page.getByTestId('payment-list')
    await expect(list.locator('li')).toHaveCount(4)
    await expect(page.getByTestId('payment-tab-confirmed')).toContainText('1')
    await expect(page.getByTestId('payment-tab-rejected')).toContainText('1')
    await expect(page.getByTestId('payment-tab-payer_reported')).toContainText('1')
    await expect(page.getByTestId('payment-tab-awaiting_payment')).toContainText('1')

    const confirmedCard = page.getByTestId(`payment-${codes.confirmed}`)
    await expect(confirmedCard.getByTestId('payment-status')).toHaveText('Đã thu tiền')
    await expect(confirmedCard).toContainText('120.000đ')
    await expect(confirmedCard).toContainText('MBBank')
    await expect(confirmedCard).toContainText('0359111001')
    await expect(confirmedCard).toContainText('Chủ sân xác nhận thu')
    await expect(confirmedCard.getByTestId('payment-proof')).toHaveAttribute('href', 'https://example.com/chuyen-khoan.png')
    await expect(confirmedCard.getByTestId('payment-proof')).toHaveAttribute('rel', /noopener/)

    await page.getByTestId('payment-tab-rejected').click()
    await expect(list.locator('li')).toHaveCount(1)
    await expect(page.getByTestId(`payment-${codes.rejected}`)).toContainText('Chưa nhận được tiền: Chưa thấy tiền về')
    await page.getByTestId('payment-tab-payer_reported').click()
    await expect(page.getByTestId(`payment-${codes.reported}`).getByTestId('payment-status')).toHaveText('Chờ chủ sân xác nhận')
    await page.getByTestId('payment-tab-awaiting_payment').click()
    await expect(page.getByTestId(`payment-${codes.waiting}`)).toContainText('Hạn thanh toán')
    await page.getByTestId('payment-tab-expired').click()
    await expect(page.getByRole('heading', { name: 'Không có thanh toán nào' })).toBeVisible()
    await expect(page.getByText('không có số dư')).toBeVisible()
    assertNoErrors()
  })

  test('never links an unsafe proof URL', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    runSql(`UPDATE payments SET proof_url = 'javascript:alert(1)'
            WHERE booking_id = (SELECT id FROM bookings WHERE booking_code = '${codes.reported}')`)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/payments')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await page.getByTestId('payment-tab-payer_reported').click()
    await expect(page.getByTestId(`payment-${codes.reported}`)).toBeVisible()
    await expect(page.getByTestId('payment-proof')).toHaveCount(0)
    assertNoErrors()
  })

  test('shows an error state with retry when the API fails', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    let failing = true
    await page.route('**/api/v1/owner/payments?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/payments')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('payment-tab-all')).toBeVisible()
  })

  test('payments page fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/payments')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await expect(page.getByTestId('payment-list')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

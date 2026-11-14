import { expect, test } from '@playwright/test'
import {
  createAcceptedBookings,
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  runSql,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.27 owner confirms or rejects a direct payment', () => {
  let venue
  let bookings
  let ownerToken
  let playerToken

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  async function playerReports(page, booking) {
    const payment = await (await page.request.get(`${API_BASE_URL}/api/v1/payments/by-booking/${booking.id}`, { headers: { Authorization: `Bearer ${playerToken}` } })).json()
    const response = await page.request.post(`${API_BASE_URL}/api/v1/payments/${payment.id}/report-paid`, {
      headers: { Authorization: `Bearer ${playerToken}` },
      data: { method: 'owner_bank_transfer', proofUrl: 'https://example.com/e2e-proof.png' },
    })
    expect(response.status(), await response.text()).toBe(200)
    return payment.id
  }

  test('confirms a reported payment from the detail drawer and the booking becomes confirmed', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    ownerToken = owner.token
    playerToken = (await loginByApi(page, 'an.nguyen@example.com')).token
    venue = await createE2eVenue(page, ownerToken, 'E2E Sân Xác Nhận Thu')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken, playerToken, venue, times: ['06:00', '08:00', '10:00'] })
    await playerReports(page, bookings[0])
    await playerReports(page, bookings[1])

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/payments')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await page.getByRole('button', { name: `Xem chi tiết thanh toán ${bookings[2].bookingCode}` }).click()
    const waiting = page.getByTestId('payment-drawer')
    await expect(waiting.getByTestId('payment-drawer-status')).toHaveText('Chờ thanh toán')
    await expect(waiting.getByTestId('payment-confirm')).toHaveCount(0)
    await expect(waiting.getByTestId('payment-reject-open')).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.getByRole('button', { name: `Xem chi tiết thanh toán ${bookings[0].bookingCode}` }).click()
    const drawer = page.getByTestId('payment-drawer')
    await expect(drawer.getByTestId('payment-drawer-status')).toHaveText('Chờ chủ sân xác nhận')
    await expect(drawer).toContainText('E2E Sân Xác Nhận Thu')
    await expect(drawer.getByTestId('payment-drawer-amount')).toHaveText('120.000đ')
    await expect(drawer.getByTestId('payment-drawer-proof')).toHaveAttribute('href', 'https://example.com/e2e-proof.png')
    await expect(drawer.getByTestId('payment-drawer-timeline').getByRole('listitem')).toHaveCount(2)

    const confirmed = page.waitForResponse((response) => response.url().endsWith('/confirm') && response.request().method() === 'POST')
    await drawer.getByTestId('payment-confirm').click()
    expect((await confirmed).status()).toBe(200)
    await expect(drawer.getByTestId('payment-drawer-status')).toHaveText('Đã thu tiền')
    await expect(drawer.getByTestId('payment-drawer-booking-status')).toHaveText('Đã xác nhận')
    await expect(drawer.getByTestId('payment-confirm')).toHaveCount(0)
    await expect(drawer.getByTestId('payment-drawer-timeline').getByRole('listitem')).toHaveCount(3)
    await expect(page.getByTestId('payment-notice')).toContainText(`Đã xác nhận thu tiền ${bookings[0].bookingCode}`)
    expect(runSql(`SELECT status FROM bookings WHERE id = '${bookings[0].id}'`)).toBe('confirmed')
    expect(runSql(`SELECT count(*) FROM audit_logs WHERE action = 'payment.confirm' AND entity_id = (SELECT id FROM payments WHERE booking_id = '${bookings[0].id}')`)).toBe('1')
    assertNoErrors()
  })

  test('rejects a reported payment with a reason, validating the reason first', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/payments')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await page.getByRole('button', { name: `Xem chi tiết thanh toán ${bookings[1].bookingCode}` }).click()
    const drawer = page.getByTestId('payment-drawer')
    await expect(drawer).toContainText(bookings[1].bookingCode)
    await drawer.getByTestId('payment-reject-open').click()
    await drawer.getByRole('button', { name: 'Từ chối báo thanh toán' }).click()
    await expect(drawer.getByText('Vui lòng nhập lý do (ít nhất 3 ký tự)')).toBeVisible()
    await drawer.getByLabel('Lý do từ chối thanh toán').fill('ab')
    await drawer.getByRole('button', { name: 'Từ chối báo thanh toán' }).click()
    await expect(drawer.getByText('Vui lòng nhập lý do (ít nhất 3 ký tự)')).toBeVisible()

    await drawer.getByLabel('Lý do từ chối thanh toán').fill('Chưa thấy tiền về tài khoản')
    const rejected = page.waitForResponse((response) => response.url().endsWith('/reject') && response.request().method() === 'POST')
    await drawer.getByRole('button', { name: 'Từ chối báo thanh toán' }).click()
    expect((await rejected).status()).toBe(200)
    await expect(drawer.getByTestId('payment-drawer-status')).toHaveText('Chưa nhận được tiền')
    await expect(drawer.getByTestId('payment-drawer-reason')).toContainText('Chưa thấy tiền về tài khoản')
    await expect(drawer.getByTestId('payment-reject-form')).toHaveCount(0)
    await expect(page.getByTestId('payment-notice')).toContainText(`Đã từ chối báo thanh toán của ${bookings[1].bookingCode}`)

    // Người chơi thấy lý do và báo lại được.
    const payment = await (await page.request.get(`${API_BASE_URL}/api/v1/payments/by-booking/${bookings[1].id}`, { headers: { Authorization: `Bearer ${playerToken}` } })).json()
    expect(payment.rejectionReason).toBe('Chưa thấy tiền về tài khoản')
    assertNoErrors()
  })

  test('shows the server conflict when the payment was already handled elsewhere', async ({ page }) => {
    await playerReports(page, bookings[1])
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/payments')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await page.getByRole('button', { name: `Xem chi tiết thanh toán ${bookings[1].bookingCode}` }).click()
    const drawer = page.getByTestId('payment-drawer')
    await expect(drawer.getByTestId('payment-confirm')).toBeVisible()
    // Xử lý ở nơi khác (API) trước khi bấm.
    const paymentId = runSql(`SELECT id FROM payments WHERE booking_id = '${bookings[1].id}'`)
    const handled = await page.request.post(`${API_BASE_URL}/api/v1/owner/payments/${paymentId}/confirm`, { headers: { Authorization: `Bearer ${ownerToken}` } })
    expect(handled.status()).toBe(200)
    await drawer.getByTestId('payment-confirm').click()
    await expect(drawer.getByRole('alert')).toContainText('Chi xac nhan duoc khi nguoi choi da bao thanh toan')
    await expect(drawer.getByTestId('payment-drawer-status')).toHaveText('Đã thu tiền')
  })

  test('payment drawer fits a mobile screen and shows an error state with retry', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    let failing = true
    await page.route('**/api/v1/owner/payments/*', (route) => (failing && route.request().method() === 'GET'
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/payments')
    await page.getByLabel('Lọc theo sân').selectOption(venue.id)
    await page.getByRole('button', { name: `Xem chi tiết thanh toán ${bookings[2].bookingCode}` }).click()
    await expect(page.getByTestId('payment-drawer').getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByTestId('payment-drawer').getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('payment-drawer-timeline')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

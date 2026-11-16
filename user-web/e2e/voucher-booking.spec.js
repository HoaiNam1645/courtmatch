import { expect, test } from '@playwright/test'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  nextMondayIso,
  runSql,
  SEED_OWNER_EMAIL,
  shot,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const PLAYER = 'an.nguyen@example.com'
const vnd = (value) => `${Number(value).toLocaleString('vi-VN')}đ`

function cleanup() {
  deleteE2eVenues()
  runSql("DELETE FROM voucher_redemptions WHERE voucher_id IN (SELECT id FROM vouchers WHERE code LIKE 'E2E-%')")
  runSql("DELETE FROM audit_logs WHERE entity_type = 'vouchers' AND entity_id IN (SELECT id FROM vouchers WHERE code LIKE 'E2E-%')")
  runSql("DELETE FROM vouchers WHERE code LIKE 'E2E-%'")
}

test.describe.serial('B4 player uses a voucher when booking', () => {
  let venue
  let price
  let startAt
  let endAt
  let discount
  let bookingId

  test.beforeAll(cleanup)
  test.afterAll(cleanup)

  /** Đặt lựa chọn sân vào sessionStorage như trang chi tiết sân, rồi mở trang xác nhận đặt sân. */
  async function openCheckout(page, days = 0) {
    await loginByApi(page, PLAYER)
    await page.goto('/')
    const shift = (iso) => new Date(new Date(iso).getTime() + days * 86400000).toISOString()
    await page.evaluate((pending) => window.sessionStorage.setItem('courtly_pending_booking', JSON.stringify(pending)), {
      venueId: venue.id, venueName: venue.name, courtId: venue.courts[0].id, courtName: venue.courts[0].name,
      date: shift(`${nextMondayIso()}T12:00:00Z`).slice(0, 10), startTime: '09:00', endTime: '10:00', startAt: shift(startAt), endAt: shift(endAt), durationMinutes: 60, price,
    })
    await page.goto('/booking/checkout')
    await expect(page.getByTestId('voucher-picker')).toBeVisible()
  }

  test('builds a bookable venue and four vouchers: usable, too demanding, locked and expired', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Voucher')
    makeVenueBookable(venue.id)
    startAt = new Date(`${nextMondayIso()}T09:00:00+07:00`).toISOString()
    endAt = new Date(`${nextMondayIso()}T10:00:00+07:00`).toISOString()
    const quote = await (await page.request.post(`${API_BASE_URL}/api/v1/bookings/quote`, { data: { courtId: venue.courts[0].id, startTime: startAt, durationMinutes: 60 } })).json()
    price = Number(quote.totalAmount)
    expect(price).toBeGreaterThan(0)
    discount = Math.min(Math.round(price * 0.2), 15000)
    const insert = (code, name, extra = '', until = "now() + interval '30 days'", status = 'active') => runSql(`
      INSERT INTO vouchers (code, name, discount_percent, max_discount_amount, valid_from, valid_until, status, ${extra ? extra.split('=')[0] : 'min_elo'})
      VALUES ('${code}', '${name}', 20, 15000, now() - interval '1 day', ${until}, '${status}', ${extra ? extra.split('=')[1] : 'NULL'})`)
    insert('E2E-GIAM20', 'E2E Giảm 20%')
    insert('E2E-KHO', 'E2E Voucher khó', 'min_elo=4999')
    insert('E2E-KHOA', 'E2E Voucher khóa', '', "now() + interval '30 days'", 'locked')
    insert('E2E-HETHAN', 'E2E Voucher hết hạn', '', "now() - interval '1 hour'")
  })

  test('lists only vouchers issued to players; a demanding one is greyed out with what is missing', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openCheckout(page)
    const list = page.getByTestId('voucher-list')
    await expect(list.getByTestId('voucher-E2E-GIAM20')).toContainText('Giảm 20% (tối đa 15.000đ)')
    await expect(list.getByTestId('voucher-E2E-GIAM20').getByTestId('voucher-use')).toBeVisible()
    await expect(list.getByTestId('voucher-E2E-KHO').getByTestId('voucher-unavailable')).toHaveText('Chưa đủ điều kiện')
    await expect(list.getByTestId('voucher-E2E-KHO').getByTestId('voucher-unmet')).toContainText('Cần Elo 4.999 (bạn có')
    await expect(list.getByTestId('voucher-E2E-KHO').getByTestId('voucher-use')).toHaveCount(0)
    await expect(list.getByTestId('voucher-E2E-KHOA')).toHaveCount(0)
    await expect(list.getByTestId('voucher-E2E-HETHAN')).toHaveCount(0)
    await shot(page, 'b4-picker')
    assertNoErrors()
  })

  test('applying a voucher shows the discount and the new total; removing it restores the original price', async ({ page }) => {
    await openCheckout(page)
    await expect(page.getByText('Tổng thanh toán').locator('..').getByText(vnd(price))).toBeVisible()
    await page.getByTestId('voucher-E2E-GIAM20').getByTestId('voucher-use').click()
    await expect(page.getByTestId('voucher-applied')).toContainText('E2E-GIAM20')
    await expect(page.getByTestId('voucher-applied-discount')).toContainText(`Giảm ${vnd(discount)}`)
    await expect(page.getByTestId('voucher-applied-discount')).toContainText('Courtly chịu')
    await expect(page.getByTestId('checkout-discount')).toContainText(`-${vnd(discount)}`)
    await expect(page.getByText('Tổng thanh toán').locator('..').getByText(vnd(price - discount))).toBeVisible()
    await shot(page, 'b4-applied')
    await page.getByTestId('voucher-remove').click()
    await expect(page.getByTestId('voucher-applied')).toHaveCount(0)
    await expect(page.getByTestId('checkout-discount')).toHaveCount(0)
    await expect(page.getByText('Tổng thanh toán').locator('..').getByText(vnd(price))).toBeVisible()
  })

  test('typing a code: lower case works; a wrong, locked, expired or too-demanding code shows a clear message and changes nothing', async ({ page }) => {
    await openCheckout(page)
    for (const [code, message] of [['KHONG-CO-MA', 'Ma voucher khong ton tai'], ['E2E-KHOA', 'Voucher khong con hieu luc'], ['E2E-HETHAN', 'Voucher khong con hieu luc'], ['E2E-KHO', 'chua du dieu kien']]) {
      await page.getByTestId('voucher-code').fill(code)
      await page.getByTestId('voucher-apply').click()
      await expect(page.getByRole('alert').filter({ hasText: message })).toBeVisible()
      await expect(page.getByTestId('voucher-applied')).toHaveCount(0)
    }
    await expect(page.getByTestId('voucher-apply')).toBeEnabled()
    await page.getByTestId('voucher-code').fill('e2e-giam20')
    await page.getByTestId('voucher-code').press('Enter')
    await expect(page.getByTestId('voucher-applied')).toContainText('E2E-GIAM20')
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('placing the booking charges the discounted price while the venue price stays the original', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openCheckout(page)
    await page.getByTestId('voucher-code').fill('E2E-GIAM20')
    await page.getByTestId('voucher-apply').click()
    await expect(page.getByTestId('voucher-applied')).toBeVisible()
    await page.locator('input[type="checkbox"][required]').check()
    await page.getByRole('button', { name: /Gửi yêu cầu|Đặt sân|Xác nhận/ }).last().click()
    await page.waitForURL(/\/bookings\/[0-9a-f-]{36}$/)
    bookingId = page.url().split('/').pop()

    await expect(page.getByTestId('detail-discount')).toContainText(`-${vnd(discount)}`)
    await expect(page.getByTestId('detail-payable')).toHaveText(vnd(price - discount))
    await expect(page.getByText('Courtly chịu phần giảm của voucher')).toBeVisible()
    expect(runSql(`SELECT total_amount::numeric(12,0) || '|' || discount_amount::numeric(12,0) FROM bookings WHERE id = '${bookingId}'`)).toBe(`${price}|${discount}`)
    expect(runSql(`SELECT status FROM voucher_redemptions WHERE booking_id = '${bookingId}'`)).toBe('applied')
    await shot(page, 'b4-booking-detail')
    assertNoErrors()
  })

  test('the owner accepts and the payment asks for the discounted amount only', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const accepted = await page.request.post(`${API_BASE_URL}/api/v1/owner/booking-requests/${bookingId}/accept`, { headers: { Authorization: `Bearer ${owner.token}` } })
    expect(accepted.status()).toBe(200)
    const player = await loginByApi(page, PLAYER)
    const payment = await (await page.request.get(`${API_BASE_URL}/api/v1/payments/by-booking/${bookingId}`, { headers: { Authorization: `Bearer ${player.token}` } })).json()
    expect(Number(payment.amount)).toBe(price - discount)
    await page.goto(`/payments/${payment.id}`)
    await expect(page.getByText(vnd(price - discount)).first()).toBeVisible()
  })

  test('a used voucher cannot be used twice, and it comes back when the booking is cancelled', async ({ page }) => {
    // Khung giờ khác (hôm sau) vì khung giờ đã đặt đang bị giữ.
    await openCheckout(page, 1)
    await expect(page.getByTestId('voucher-E2E-GIAM20').getByTestId('voucher-unavailable')).toHaveText('Bạn đã dùng')
    await page.getByTestId('voucher-code').fill('E2E-GIAM20')
    await page.getByTestId('voucher-apply').click()
    await expect(page.getByRole('alert').filter({ hasText: 'da dung voucher nay' })).toBeVisible()

    const player = await loginByApi(page, PLAYER)
    const cancel = await page.request.post(`${API_BASE_URL}/api/v1/bookings/${bookingId}/cancel`, { headers: { Authorization: `Bearer ${player.token}` }, data: { reason: 'E2E đổi lịch' } })
    expect(cancel.status(), await cancel.text()).toBe(200)
    expect(runSql(`SELECT status FROM voucher_redemptions WHERE booking_id = '${bookingId}'`)).toBe('released')
    await openCheckout(page)
    await expect(page.getByTestId('voucher-E2E-GIAM20').getByTestId('voucher-use')).toBeVisible()
  })

  test('the voucher section fits a mobile screen and a failed vouchers request offers a retry', async ({ page, browser }) => {
    await loginByApi(page, PLAYER)
    let fail = true
    await page.route('**/api/v1/vouchers', async (route) => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) })
      else await route.continue()
    })
    await page.goto('/')
    await page.evaluate((pending) => window.sessionStorage.setItem('courtly_pending_booking', JSON.stringify(pending)), {
      venueId: venue.id, venueName: venue.name, courtId: venue.courts[0].id, courtName: venue.courts[0].name,
      date: nextMondayIso(), startTime: '09:00', endTime: '10:00', startAt, endAt, durationMinutes: 60, price,
    })
    await page.goto('/booking/checkout')
    await expect(page.getByTestId('voucher-picker').getByRole('alert')).toContainText('Lỗi máy chủ')
    fail = false
    await page.getByTestId('voucher-picker').getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByTestId('voucher-list')).toBeVisible()

    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const mobile = await context.newPage()
    await openCheckout(mobile)
    await expect(mobile.getByTestId('voucher-E2E-GIAM20')).toBeVisible()
    expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(mobile, 'b4-mobile')
    await context.close()
  })
})

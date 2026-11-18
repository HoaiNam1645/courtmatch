import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'
import { addPayment, bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

let venue
let unpaid
let full
let custom
let plain
let invalid
let phone

function cleanup() {
  cleanupDisputes('E2EBR')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EBR%'`)
  deleteE2eUsers()
}

const DESCRIPTION = 'Bị thu thêm tiền nước ngoài giá niêm yết của đơn'

test.describe.serial('2.3.87 player marks a refund request when reporting a booking', () => {
  test.beforeAll(() => {
    cleanup()
    venue = pickVenue()
    createE2eUser({ tag: 'br-a', name: 'E2E Người Yêu Cầu Hoàn' })
    unpaid = bookingWithStatus(venue, 'br-a', 'E2EBR001', 'completed', 1)
    full = bookingWithStatus(venue, 'br-a', 'E2EBR002', 'completed', 1)
    custom = bookingWithStatus(venue, 'br-a', 'E2EBR003', 'completed', 2)
    plain = bookingWithStatus(venue, 'br-a', 'E2EBR004', 'completed', 2)
    invalid = bookingWithStatus(venue, 'br-a', 'E2EBR005', 'completed', 3)
    phone = bookingWithStatus(venue, 'br-a', 'E2EBR006', 'confirmed', 3)
    for (const id of [full, custom, plain, invalid, phone]) addPayment(id)
    addPayment(unpaid, 'awaiting_payment')
  })
  test.afterAll(cleanup)

  async function fill(page, bookingId, category = 'extra_charge') {
    await page.goto(`/bookings/${bookingId}/bao-cao`)
    await page.getByTestId(`dispute-category-${category}`).check()
    await page.getByTestId('dispute-description').fill(DESCRIPTION)
  }

  test('the refund option appears only when the owner has confirmed the payment', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('br-a'))
    await page.goto(`/bookings/${unpaid}/bao-cao`)
    await expect(page.getByTestId('dispute-form')).toBeVisible()
    await expect(page.getByTestId('dispute-refund')).toHaveCount(0)
    await page.goto(`/bookings/${full}/bao-cao`)
    await expect(page.getByTestId('dispute-refund-check')).not.toBeChecked()
    await expect(page.getByTestId('dispute-refund-amount')).toHaveCount(0)
    await page.getByTestId('dispute-refund-check').check()
    await expect(page.getByTestId('dispute-refund-amount')).toBeVisible()
    await expect(page.getByTestId('dispute-refund')).toContainText('180.000')
    await shot(page, '2387-refund-option')
    assertNoErrors()
  })

  test('ticking without an amount requests the full amount paid', async ({ page }) => {
    await loginByApi(page, email('br-a'))
    await fill(page, full)
    await page.getByTestId('dispute-refund-check').check()
    await page.getByTestId('dispute-submit').click()
    await expect(page.getByTestId('dispute-refund-done')).toContainText('180.000')
    expect(runSql(`SELECT refund_requested || '|' || requested_refund_amount::int FROM booking_disputes WHERE booking_id = '${full}'`)).toBe('true|180000')
  })

  test('a custom amount is validated against 1.000đ and the amount paid, then saved', async ({ page }) => {
    await loginByApi(page, email('br-a'))
    await fill(page, invalid)
    await page.getByTestId('dispute-refund-check').check()
    for (const value of ['500', '999', '180001', '0']) {
      await page.getByTestId('dispute-refund-amount').fill(value)
      await page.getByTestId('dispute-submit').click()
      await expect(page.getByTestId('dispute-refund')).toContainText('Số tiền từ')
    }
    expect(runSql(`SELECT count(*) FROM booking_disputes WHERE booking_id = '${invalid}'`)).toBe('0')

    await fill(page, custom)
    await page.getByTestId('dispute-refund-check').check()
    await page.getByTestId('dispute-refund-amount').fill('60000')
    await page.getByTestId('dispute-submit').click()
    await expect(page.getByTestId('dispute-refund-done')).toContainText('60.000')
    expect(runSql(`SELECT requested_refund_amount::int FROM booking_disputes WHERE booking_id = '${custom}'`)).toBe('60000')
    // Chỉ ghi nhận yêu cầu: chưa tạo bản ghi hoàn tiền, trạng thái đơn không đổi.
    expect(runSql(`SELECT count(*) FROM refunds WHERE booking_id = '${custom}'`)).toBe('0')
    expect(runSql(`SELECT status FROM bookings WHERE id = '${custom}'`)).toBe('completed')
  })

  test('without the tick the report is only a complaint and saves no amount', async ({ page }) => {
    await loginByApi(page, email('br-a'))
    await fill(page, plain)
    await page.getByTestId('dispute-submit').click()
    await expect(page.getByTestId('dispute-done')).toBeVisible()
    await expect(page.getByTestId('dispute-refund-done')).toHaveCount(0)
    expect(runSql(`SELECT refund_requested || '|' || coalesce(requested_refund_amount::text, 'null') FROM booking_disputes WHERE booking_id = '${plain}'`)).toBe('false|null')
  })

  test('the refund field fits a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, email('br-a'))
    await page.goto(`/bookings/${phone}/bao-cao`)
    await page.getByTestId('dispute-refund-check').check()
    const box = await page.getByTestId('dispute-refund').boundingBox()
    expect(box.x + box.width).toBeLessThanOrEqual(376)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  })
})

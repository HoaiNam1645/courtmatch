import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'
import { bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

let venue
let ok
let pending
let late
let withDispute
let toExpire
let phone

function cleanup() {
  cleanupDisputes('E2EBD')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EBD%'`)
  deleteE2eUsers()
}

test.describe.serial('2.3.85 - 2.3.86 player reports a problem with a booking', () => {
  test.beforeAll(() => {
    cleanup()
    venue = pickVenue()
    createE2eUser({ tag: 'bd-a', name: 'E2E Người Báo Cáo Đơn' })
    ok = bookingWithStatus(venue, 'bd-a', 'E2EBD001', 'completed', 1)
    pending = bookingWithStatus(venue, 'bd-a', 'E2EBD002', 'pending_owner_confirmation', 1)
    late = bookingWithStatus(venue, 'bd-a', 'E2EBD003', 'completed', 9)
    withDispute = bookingWithStatus(venue, 'bd-a', 'E2EBD004', 'confirmed', 2)
    toExpire = bookingWithStatus(venue, 'bd-a', 'E2EBD005', 'completed', 6)
    phone = bookingWithStatus(venue, 'bd-a', 'E2EBD006', 'confirmed', 3)
  })
  test.afterAll(cleanup)

  test('the booking detail offers the report only for a reportable booking', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('bd-a'))
    await page.goto(`/bookings/${ok}`)
    await expect(page.getByTestId('detail-report')).toContainText('Gặp vấn đề với đơn này?')
    await expect(page.getByTestId('detail-report-open')).toHaveAttribute('href', `/bookings/${ok}/bao-cao`)
    await page.goto(`/bookings/${pending}`)
    await expect(page.getByTestId('detail-report')).toHaveCount(0)
    await page.goto(`/bookings/${late}`)
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()
    await expect(page.getByTestId('detail-report')).toHaveCount(0)
    assertNoErrors()
  })

  test('the form lists the five categories with hints, validates every rule and posts the report', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('bd-a'))
    await page.goto(`/bookings/${ok}`)
    await page.getByTestId('detail-report-open').click()
    await expect(page).toHaveURL(`/bookings/${ok}/bao-cao`)
    await expect(page.getByTestId('dispute-booking')).toContainText('E2EBD001')
    const form = page.getByTestId('dispute-form')
    await expect(form.locator('input[type="radio"]')).toHaveCount(5)
    await shot(page, '2385-dispute-form')

    await page.getByTestId('dispute-submit').click()
    await expect(form).toContainText('Vui lòng chọn loại báo cáo')
    await page.getByTestId('dispute-category-venue_closed').check()
    await page.getByTestId('dispute-description').fill('ngắn')
    await page.getByTestId('dispute-submit').click()
    await expect(form).toContainText('ít nhất 10 ký tự')
    await page.getByTestId('dispute-category-other').check()
    await expect(page.getByTestId('dispute-counter')).toContainText('tối thiểu 20')
    await page.getByTestId('dispute-description').fill('đủ mười ký tự')
    await page.getByTestId('dispute-submit').click()
    await expect(form).toContainText('ít nhất 20 ký tự')
    await page.getByTestId('dispute-description').fill('x'.repeat(2001))
    await expect(page.getByTestId('dispute-counter')).toContainText('2001/2000')
    await page.getByTestId('dispute-submit').click()
    await expect(form).toContainText('tối đa 2000 ký tự')

    // Đổi loại không làm mất nội dung đã gõ.
    await page.getByTestId('dispute-category-venue_closed').check()
    await page.getByTestId('dispute-description').fill('  Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng  ')
    await page.getByTestId('dispute-category-extra_charge').check()
    await expect(page.getByTestId('dispute-description')).toHaveValue('  Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng  ')
    await page.getByTestId('dispute-category-venue_closed').check()
    await page.getByTestId('dispute-submit').click()

    await expect(page.getByTestId('dispute-done')).toContainText('Đã gửi báo cáo')
    await expect(page.getByTestId('dispute-done')).toContainText('Sân đóng cửa')
    await expect(page.getByTestId('dispute-deadline')).toContainText('Chủ sân cần giải trình trước')
    expect(runSql(`SELECT category || '|' || status || '|' || description FROM booking_disputes WHERE booking_id = '${ok}'`))
      .toBe('venue_closed|awaiting_owner_response|Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng')
    const hours = Number(runSql(`SELECT round(extract(epoch FROM (owner_response_deadline - created_at)) / 3600) FROM booking_disputes WHERE booking_id = '${ok}'`))
    expect(hours).toBe(48)
    // Chủ sân của sân nhận thông báo kèm mã đơn và loại báo cáo.
    expect(runSql(`SELECT content FROM notifications WHERE type = 'booking_dispute_opened' AND data_json ->> 'bookingId' = '${ok}'`)).toContain('E2EBD001')
    assertNoErrors()

    await page.getByTestId('dispute-back').click()
    await expect(page).toHaveURL(`/bookings/${ok}`)
    await expect(page.getByTestId('detail-dispute-status')).toContainText('Chờ chủ sân giải trình')
    await expect(page.getByTestId('detail-report')).toHaveCount(0)
    await page.goto(`/bookings/${ok}/bao-cao`)
    await expect(page.getByTestId('dispute-unavailable')).toContainText('đã có báo cáo đang xử lý')
  })

  test('an unreportable booking shows why instead of the form', async ({ page }) => {
    await loginByApi(page, email('bd-a'))
    for (const id of [pending, late]) {
      await page.goto(`/bookings/${id}/bao-cao`)
      await expect(page.getByTestId('dispute-unavailable')).toContainText('Không thể báo cáo đơn này')
      await expect(page.getByTestId('dispute-form')).toHaveCount(0)
    }
  })

  test('a window that closes while the form is open is reported inside the form and nothing is saved', async ({ page }) => {
    await loginByApi(page, email('bd-a'))
    await page.goto(`/bookings/${toExpire}/bao-cao`)
    await page.getByTestId('dispute-category-extra_charge').check()
    await page.getByTestId('dispute-description').fill('Bị thu thêm 50.000đ tiền nước ngoài giá niêm yết')
    runSql(`UPDATE booking_items SET start_time = now() - interval '9 days 90 minutes', end_time = now() - interval '9 days' WHERE booking_id = '${toExpire}'`)
    await page.getByTestId('dispute-submit').click()
    await expect(page.getByTestId('dispute-form')).toContainText('7 ngay')
    await expect(page.getByTestId('dispute-form')).toBeVisible()
    expect(runSql(`SELECT count(*) FROM booking_disputes WHERE booking_id = '${toExpire}'`)).toBe('0')
  })

  test('a confirmed booking can be reported too, and the form fits a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, email('bd-a'))
    await page.goto(`/bookings/${phone}/bao-cao`)
    await expect(page.getByTestId('dispute-form')).toBeVisible()
    const box = await page.getByTestId('dispute-form').boundingBox()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(376)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await page.getByTestId('dispute-category-owner_cancelled').check()
    await page.getByTestId('dispute-description').fill('Chủ sân nhắn tin báo huỷ lịch sát giờ chơi')
    await page.getByTestId('dispute-submit').click()
    await expect(page.getByTestId('dispute-done')).toBeVisible()
    await shot(page, '2385-dispute-done-phone')
  })
})

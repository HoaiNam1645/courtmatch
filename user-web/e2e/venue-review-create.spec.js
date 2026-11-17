import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'

const email = (tag) => `e2e-${tag}@example.com`
let venue
let court
let snapshot
let counter = 0

/** Đơn đã hoàn thành cách đây `daysAgo` ngày, tạo bằng SQL (không đi qua luồng thanh toán). */
function completedBooking(tag, code, daysAgo) {
  const shift = (counter += 1) * 3 // mỗi đơn lệch 3 giờ để không đụng ràng buộc chống trùng giờ của sân
  runSql(`
    WITH b AS (
      INSERT INTO bookings (booking_code, user_id, venue_id, status, total_amount, price_locked_at, completed_at)
      SELECT '${code}', u.id, '${venue.id}', 'completed', 180000, now(), now() - interval '${daysAgo} days'
      FROM users u WHERE u.email = '${email(tag)}' RETURNING id)
    INSERT INTO booking_items (booking_id, court_id, start_time, end_time, price, status)
    SELECT b.id, '${court}', now() - interval '${daysAgo} days ${shift + 2} hours', now() - interval '${daysAgo} days ${shift} hours 30 minutes', 180000, 'confirmed' FROM b;`)
  return runSql(`SELECT id FROM bookings WHERE booking_code = '${code}'`)
}

function cleanup() {
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2ERV%'`)
  deleteE2eUsers()
  if (snapshot) runSql(`UPDATE venues SET average_rating = ${snapshot.rating}, review_count = ${snapshot.count} WHERE id = '${venue.id}'`)
}

test.describe.serial('2.3.77 player writes a venue review after playing', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2ERV%'`)
    const row = runSql(`SELECT v.id || '|' || v.slug || '|' || v.name || '|' || v.average_rating || '|' || v.review_count || '|' || (SELECT c.id FROM courts c WHERE c.venue_id = v.id LIMIT 1)
      FROM venues v WHERE v.approval_status = 'approved' AND v.status = 'active' AND EXISTS (SELECT 1 FROM courts c WHERE c.venue_id = v.id) ORDER BY v.created_at LIMIT 1`)
    const [id, slug, name, rating, count, courtId] = row.split('|')
    venue = { id, slug, name }
    court = courtId
    snapshot = { rating, count }
    createE2eUser({ tag: 'rv-a', name: 'E2E Người Đánh Giá' })
    createE2eUser({ tag: 'rv-b', name: 'E2E Không Có Đơn' })
  })
  test.afterAll(cleanup)

  test('bookings page offers a review only for completed, un-reviewed, in-window bookings', async ({ page }) => {
    const fresh = completedBooking('rv-a', 'E2ERV001', 1)
    const old = completedBooking('rv-a', 'E2ERV002', 9)
    const done = completedBooking('rv-a', 'E2ERV003', 2)
    runSql(`INSERT INTO venue_reviews (venue_id, user_id, booking_id, rating, comment)
      SELECT '${venue.id}', u.id, '${done}', 4, 'E2E đã đánh giá' FROM users u WHERE u.email = '${email('rv-a')}'`)
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('rv-a'))
    await page.goto('/bookings')
    await page.getByRole('button', { name: 'Đã hoàn thành' }).click()
    await expect(page.getByTestId(`review-open-${fresh}`)).toBeVisible()
    await expect(page.getByTestId(`review-open-${old}`)).toHaveCount(0)
    await expect(page.getByTestId(`review-open-${done}`)).toHaveCount(0)
    await expect(page.getByTestId(`review-done-${done}`)).toContainText('4/5')
    await shot(page, '2377-bookings')
    assertNoErrors()
  })

  test('validates the form, then posts the review and the venue rating is recomputed', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('rv-a'))
    await page.goto('/bookings')
    await page.getByRole('button', { name: 'Đã hoàn thành' }).click()
    const fresh = runSql(`SELECT id FROM bookings WHERE booking_code = 'E2ERV001'`)
    await page.getByTestId(`review-open-${fresh}`).click()
    const dialog = page.getByTestId('review-dialog')
    await expect(dialog).toBeVisible()

    await page.getByTestId('review-submit').click()
    await expect(dialog).toContainText('Vui lòng chọn số sao')
    await page.getByTestId('review-star-5').click()
    await expect(page.getByTestId('review-star-5')).toHaveAttribute('aria-checked', 'true')
    await expect(page.getByTestId('review-rating-label')).toHaveText('Rất tốt')
    await page.getByTestId('review-comment').fill('x'.repeat(1001))
    await expect(page.getByTestId('review-counter')).toHaveText('1001/1000')
    await page.getByTestId('review-submit').click()
    await expect(dialog).toContainText('Nhận xét tối đa 1000 ký tự')
    await page.getByTestId('review-comment').fill('  Sân đẹp, chủ sân thân thiện  ')
    await shot(page, '2377-dialog')
    await page.getByTestId('review-submit').click()

    await expect(dialog).toHaveCount(0)
    await expect(page.getByTestId('booking-notice')).toContainText('đã được đăng')
    await expect(page.getByTestId(`review-done-${fresh}`)).toContainText('5/5')
    await expect(page.getByTestId(`review-open-${fresh}`)).toHaveCount(0)

    expect(runSql(`SELECT comment FROM venue_reviews WHERE booking_id = '${fresh}'`)).toBe('Sân đẹp, chủ sân thân thiện')
    const stored = runSql(`SELECT average_rating || '|' || review_count FROM venues WHERE id = '${venue.id}'`).split('|')
    const expected = runSql(`SELECT round(avg(rating), 2) || '|' || count(*) FROM venue_reviews WHERE venue_id = '${venue.id}' AND status = 'visible'`).split('|')
    expect(Number(stored[0])).toBeCloseTo(Number(expected[0]), 2)
    expect(stored[1]).toBe(expected[1])
    assertNoErrors()
  })

  test('a deadline that passes while the dialog is open is reported inside the dialog', async ({ page }) => {
    const late = completedBooking('rv-a', 'E2ERV004', 6)
    await loginByApi(page, email('rv-a'))
    await page.goto('/bookings')
    await page.getByRole('button', { name: 'Đã hoàn thành' }).click()
    await page.getByTestId(`review-open-${late}`).click()
    runSql(`UPDATE bookings SET completed_at = now() - interval '8 days' WHERE id = '${late}'`)
    await page.getByTestId('review-star-3').click()
    await page.getByTestId('review-submit').click()
    await expect(page.getByTestId('review-dialog')).toContainText('7 ngay')
    await expect(page.getByTestId('review-dialog')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('review-dialog')).toHaveCount(0)
    expect(runSql(`SELECT count(*) FROM venue_reviews WHERE booking_id = '${late}'`)).toBe('0')
  })

  test('venue page shows the write button only to a player who can review there, and the new review appears', async ({ page, browser }) => {
    const fresh = completedBooking('rv-a', 'E2ERV005', 1)
    const assertNoErrors = watchRuntimeErrors(page)
    // Người không có đơn nào tại sân: mở trong ngữ cảnh trình duyệt riêng để không lẫn phiên đăng nhập.
    const other = await (await browser.newContext()).newPage()
    await loginByApi(other, email('rv-b'))
    await other.goto(`/san/${venue.slug}`)
    await expect(other.locator('#danh-gia')).toBeVisible()
    await expect(other.getByTestId('review-write')).toHaveCount(0)
    await other.context().close()

    await loginByApi(page, email('rv-a'))
    await page.goto(`/san/${venue.slug}#danh-gia`)
    await expect(page.getByTestId('review-write')).toBeVisible()
    await page.getByTestId('review-write').click()
    await page.getByTestId('review-star-4').click()
    await page.getByTestId('review-comment').fill('E2E trải nghiệm tốt ở trang chi tiết')
    await page.getByTestId('review-submit').click()
    await expect(page.getByTestId('review-notice')).toContainText('đã được đăng')
    await expect(page.locator('[data-testid^="venue-review-"]', { hasText: 'E2E trải nghiệm tốt ở trang chi tiết' })).toBeVisible()
    await expect(page.getByTestId('review-write')).toHaveCount(0)
    expect(runSql(`SELECT rating FROM venue_reviews WHERE booking_id = '${fresh}'`)).toBe('4')
    await shot(page, '2377-venue-page')
    assertNoErrors()
  })

  test('fits a phone screen', async ({ page }) => {
    completedBooking('rv-a', 'E2ERV006', 1)
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, email('rv-a'))
    await page.goto('/bookings')
    await page.getByRole('button', { name: 'Đã hoàn thành' }).click()
    await page.locator('[data-testid^="review-open-"]').first().click()
    await expect(page.getByTestId('review-dialog')).toBeVisible()
    const box = await page.getByTestId('review-dialog').boundingBox()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(376)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await shot(page, '2377-dialog-phone')
  })
})

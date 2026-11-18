import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { cleanupReviews, completedBooking, email, insertReview, pickVenue } from './reviewHelpers'

let venue
let reviewA // báo cáo thành công
let reviewB // bị ẩn theo báo cáo
let reviewC // được giữ nguyên
let reviewD // báo cáo bị trùng khi hộp thoại đang mở
let bookingA

const resolve = (id, status, action) => runSql(`UPDATE reports SET status = '${status}', handling_action = '${action}', handled_at = now() WHERE target_id = '${id}'`)

async function openOwnerReviews(page, filter = null) {
  await loginByApi(page, SEED_OWNER_EMAIL)
  await page.goto('/owner/reviews')
  await expect(page.getByTestId('or-list')).toBeVisible()
  if (filter) await page.getByTestId(`or-filter-${filter}`).click()
}

test.describe.serial('2.3.83 owner reports an inappropriate venue review', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    for (const tag of ['rr-a', 'rr-b', 'rr-c', 'rr-d']) createE2eUser({ tag, name: `E2E Người Viết ${tag}` })
    bookingA = completedBooking(venue, 'rr-a', 'E2ERR001', 1)
    reviewA = insertReview(venue, 'rr-a', bookingA, 1, 'E2E lời lẽ xúc phạm')
    reviewB = insertReview(venue, 'rr-b', null, 1, 'E2E sẽ bị ẩn')
    reviewC = insertReview(venue, 'rr-c', null, 2, 'E2E sẽ được giữ')
    reviewD = insertReview(venue, 'rr-d', null, 2, 'E2E báo cáo trùng')
  })
  test.afterAll(() => cleanupReviews(venue, 'E2ERR'))

  test('the dialog lists the five reasons, validates, then files the report while the review stays public', async ({ page, browser }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openOwnerReviews(page)
    await page.getByTestId(`report-open-${reviewA}`).click()
    const dialog = page.getByTestId('report-dialog')
    await expect(dialog.locator('input[type="radio"]')).toHaveCount(5)
    await expect(dialog).toContainText('lời lẽ xúc phạm')
    await shot(page, '2383-report-dialog')

    await page.getByTestId('report-submit').click()
    await expect(dialog).toContainText('Vui lòng chọn lý do báo cáo')
    await page.getByTestId('report-reason-other').check()
    await page.getByTestId('report-description').fill('ngắn')
    await page.getByTestId('report-submit').click()
    await expect(dialog).toContainText('ít nhất 10 ký tự')
    await page.getByTestId('report-description').fill('x'.repeat(1001))
    await page.getByTestId('report-submit').click()
    await expect(dialog).toContainText('tối đa 1000 ký tự')

    await page.getByTestId('report-reason-offensive').check()
    await page.getByTestId('report-description').fill('Dùng lời lẽ thô tục với nhân viên')
    await page.getByTestId('report-submit').click()
    await expect(dialog).toHaveCount(0)
    await expect(page.getByTestId('or-notice')).toContainText('vẫn hiển thị cho tới khi quản trị viên kết luận')
    await expect(page.getByTestId(`owner-report-${reviewA}`)).toHaveAttribute('data-tone', 'pending')
    await expect(page.getByTestId(`owner-report-${reviewA}`)).toContainText('Xúc phạm, lăng mạ')
    await expect(page.getByTestId(`report-open-${reviewA}`)).toHaveCount(0)
    expect(runSql(`SELECT reason || '|' || status || '|' || description FROM reports WHERE target_id = '${reviewA}'`)).toBe('offensive|pending|Dùng lời lẽ thô tục với nhân viên')
    expect(runSql(`SELECT status FROM venue_reviews WHERE id = '${reviewA}'`)).toBe('visible')

    const visitor = await (await browser.newContext()).newPage()
    await visitor.goto(`/san/${venue.slug}`)
    for (let i = 0; i < 4 && (await visitor.getByTestId(`venue-review-${reviewA}`).count()) === 0; i += 1) {
      if (await visitor.getByTestId('review-more').count()) await visitor.getByTestId('review-more').click()
      await visitor.waitForTimeout(400)
    }
    await expect(visitor.getByTestId(`venue-review-${reviewA}`)).toContainText('E2E lời lẽ xúc phạm')
    await visitor.context().close()
    assertNoErrors()
  })

  test('the "Đã báo cáo" filter shows the outcome: pending, hidden by the admin, or kept', async ({ page }) => {
    runSql(`INSERT INTO reports (reporter_id, target_type, target_id, reason, status) SELECT id, 'venue_review', '${reviewB}', 'spam', 'pending' FROM users WHERE email = '${SEED_OWNER_EMAIL}'`)
    runSql(`INSERT INTO reports (reporter_id, target_type, target_id, reason, status) SELECT id, 'venue_review', '${reviewC}', 'defamation', 'pending' FROM users WHERE email = '${SEED_OWNER_EMAIL}'`)
    resolve(reviewB, 'resolved', 'review_hidden')
    runSql(`UPDATE venue_reviews SET status = 'hidden' WHERE id = '${reviewB}'`)
    resolve(reviewC, 'rejected', 'review_kept')
    await openOwnerReviews(page, 'reported')
    await expect(page.getByTestId(`owner-review-${reviewA}`)).toBeVisible()
    await expect(page.getByTestId(`owner-report-${reviewB}`)).toHaveAttribute('data-tone', 'hidden')
    await expect(page.getByTestId(`owner-report-${reviewB}`)).toContainText('Đã ẩn đánh giá theo báo cáo của bạn')
    await expect(page.getByTestId(`owner-hidden-${reviewB}`)).toBeVisible()
    await expect(page.getByTestId(`reply-form-${reviewB}`)).toHaveCount(0)
    await expect(page.getByTestId(`owner-report-${reviewC}`)).toHaveAttribute('data-tone', 'kept')
    await expect(page.getByTestId(`owner-report-${reviewC}`)).toContainText('giữ nguyên')
    await expect(page.getByTestId(`owner-review-${reviewD}`)).toHaveCount(0)
    await shot(page, '2383-reported-filter')
    // Bộ lọc "Tất cả" chỉ còn đánh giá hiển thị: đánh giá đã ẩn không lẫn vào.
    await page.getByTestId('or-filter-all').click()
    await expect(page.getByTestId(`owner-review-${reviewB}`)).toHaveCount(0)
    await expect(page.getByTestId(`owner-review-${reviewC}`)).toBeVisible()
  })

  test('a report filed elsewhere while the dialog is open is rejected inside the app with the server message', async ({ page }) => {
    await openOwnerReviews(page)
    await page.getByTestId(`report-open-${reviewD}`).click()
    runSql(`INSERT INTO reports (reporter_id, target_type, target_id, reason, status) SELECT id, 'venue_review', '${reviewD}', 'spam', 'pending' FROM users WHERE email = '${SEED_OWNER_EMAIL}'`)
    await page.getByTestId('report-reason-spam').check()
    await page.getByTestId('report-submit').click()
    await expect(page.getByTestId('report-dialog')).toHaveCount(0)
    await expect(page.getByTestId('or-notice')).toContainText('da bao cao')
    expect(runSql(`SELECT count(*) FROM reports WHERE target_id = '${reviewD}'`)).toBe('1')
    await expect(page.getByTestId(`owner-report-${reviewD}`)).toHaveAttribute('data-tone', 'pending')
  })

  test('the author cannot edit or delete a review under report, and gets the buttons back once it is concluded', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('rr-a'))
    await page.goto('/bookings')
    await page.getByRole('button', { name: 'Đã hoàn thành' }).click()
    await expect(page.getByTestId(`review-locked-${bookingA}`)).toContainText('đang được xem xét')
    await expect(page.getByTestId(`review-edit-${bookingA}`)).toHaveCount(0)
    await expect(page.getByTestId(`review-delete-${bookingA}`)).toHaveCount(0)

    await page.goto(`/san/${venue.slug}`)
    const mine = page.getByTestId(`venue-review-${reviewA}`)
    for (let i = 0; i < 4 && (await mine.count()) === 0; i += 1) {
      if (await page.getByTestId('review-more').count()) await page.getByTestId('review-more').click()
      await page.waitForTimeout(400)
    }
    await expect(page.getByTestId(`venue-review-locked-${reviewA}`)).toBeVisible()
    await expect(page.getByTestId(`venue-review-edit-${reviewA}`)).toHaveCount(0)

    resolve(reviewA, 'rejected', 'review_kept')
    await page.goto('/bookings')
    await page.getByRole('button', { name: 'Đã hoàn thành' }).click()
    await expect(page.getByTestId(`review-edit-${bookingA}`)).toBeVisible()
    await expect(page.getByTestId(`review-delete-${bookingA}`)).toBeVisible()
    assertNoErrors()
  })

  test('the report dialog fits a phone screen', async ({ page }) => {
    runSql(`DELETE FROM reports WHERE target_id = '${reviewA}'`)
    await page.setViewportSize({ width: 375, height: 800 })
    await openOwnerReviews(page)
    await page.getByTestId(`report-open-${reviewA}`).click()
    const box = await page.getByTestId('report-dialog').boundingBox()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(376)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  })
})

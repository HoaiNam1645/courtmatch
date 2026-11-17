import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'
import { cleanupReviews, completedBooking, email, insertReview, pickVenue } from './reviewHelpers'

let venue
let editId
let deleteId
let reportedId
let hiddenId
let editReview

async function openCompleted(page) {
  await loginByApi(page, email('rvx-a'))
  await page.goto('/bookings')
  await page.getByRole('button', { name: 'Đã hoàn thành' }).click()
}

test.describe.serial('2.3.78 - 2.3.79 player edits and deletes own venue review', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2ERX%'`)
    venue = pickVenue()
    createE2eUser({ tag: 'rvx-a', name: 'E2E Sửa Xóa' })
    editId = completedBooking(venue, 'rvx-a', 'E2ERX001', 1)
    deleteId = completedBooking(venue, 'rvx-a', 'E2ERX002', 2)
    reportedId = completedBooking(venue, 'rvx-a', 'E2ERX003', 3)
    hiddenId = completedBooking(venue, 'rvx-a', 'E2ERX004', 4)
    editReview = insertReview(venue, 'rvx-a', editId, 5, 'Nhận xét gốc')
    insertReview(venue, 'rvx-a', deleteId, 3, 'Sẽ bị xóa')
    insertReview(venue, 'rvx-a', reportedId, 2, 'Đang bị báo cáo', 'reported')
    insertReview(venue, 'rvx-a', hiddenId, 1, 'Đã bị ẩn', 'hidden')
  })
  test.afterAll(() => cleanupReviews(venue, 'E2ERX'))

  test('edit opens pre-filled, saves the new rating and comment and recomputes the venue rating', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openCompleted(page)
    await page.getByTestId(`review-edit-${editId}`).click()
    const dialog = page.getByTestId('review-dialog')
    await expect(dialog).toContainText('Sửa đánh giá')
    await expect(page.getByTestId('review-star-5')).toHaveAttribute('aria-checked', 'true')
    await expect(page.getByTestId('review-comment')).toHaveValue('Nhận xét gốc')
    await expect(page.getByTestId('review-submit')).toHaveText('Lưu thay đổi')

    await page.getByTestId('review-star-2').click()
    await page.getByTestId('review-comment').fill('  Lần này kém hơn  ')
    await shot(page, '2378-edit-dialog')
    await page.getByTestId('review-submit').click()
    await expect(dialog).toHaveCount(0)
    await expect(page.getByTestId('booking-notice')).toContainText('Đã cập nhật đánh giá')
    await expect(page.getByTestId(`review-done-${editId}`)).toContainText('2/5')
    expect(runSql(`SELECT rating || '|' || comment FROM venue_reviews WHERE id = '${editReview}'`)).toBe('2|Lần này kém hơn')
    const stored = runSql(`SELECT average_rating FROM venues WHERE id = '${venue.id}'`)
    const expected = runSql(`SELECT round(avg(rating), 2) FROM venue_reviews WHERE venue_id = '${venue.id}' AND status = 'visible'`)
    expect(Number(stored)).toBeCloseTo(Number(expected), 2)
    assertNoErrors()
  })

  test('a review that is reported or hidden shows why it cannot be changed, with no edit or delete button', async ({ page }) => {
    await openCompleted(page)
    await expect(page.getByTestId(`review-locked-${reportedId}`)).toContainText('đang bị báo cáo')
    await expect(page.getByTestId(`review-locked-${hiddenId}`)).toContainText('bị quản trị viên ẩn')
    for (const id of [reportedId, hiddenId]) {
      await expect(page.getByTestId(`review-edit-${id}`)).toHaveCount(0)
      await expect(page.getByTestId(`review-delete-${id}`)).toHaveCount(0)
    }
  })

  test('a review that becomes reported while the dialog is open is rejected inside the dialog', async ({ page }) => {
    await openCompleted(page)
    await page.getByTestId(`review-edit-${editId}`).click()
    runSql(`UPDATE venue_reviews SET status = 'reported' WHERE id = '${editReview}'`)
    await page.getByTestId('review-star-1').click()
    await page.getByTestId('review-submit').click()
    await expect(page.getByTestId('review-dialog')).toContainText('bi bao cao')
    expect(runSql(`SELECT rating FROM venue_reviews WHERE id = '${editReview}'`)).toBe('2')
    await page.keyboard.press('Escape')
    runSql(`UPDATE venue_reviews SET status = 'visible' WHERE id = '${editReview}'`)
  })

  test('delete asks for confirmation, keeps the review when cancelled and removes it when confirmed', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openCompleted(page)
    await page.getByTestId(`review-delete-${deleteId}`).click()
    await expect(page.getByTestId('review-delete-dialog')).toContainText('Xóa đánh giá?')
    await shot(page, '2379-delete-dialog')
    await page.getByRole('button', { name: 'Giữ lại' }).click()
    await expect(page.getByTestId('review-delete-dialog')).toHaveCount(0)
    expect(runSql(`SELECT count(*) FROM venue_reviews WHERE booking_id = '${deleteId}'`)).toBe('1')

    await page.getByTestId(`review-delete-${deleteId}`).click()
    await page.getByTestId('review-delete-confirm').click()
    await expect(page.getByTestId('booking-notice')).toContainText('Đã xóa đánh giá')
    expect(runSql(`SELECT count(*) FROM venue_reviews WHERE booking_id = '${deleteId}'`)).toBe('0')
    // Xóa xong thì viết lại được cho cùng đơn.
    await expect(page.getByTestId(`review-open-${deleteId}`)).toBeVisible()
    const stored = runSql(`SELECT review_count FROM venues WHERE id = '${venue.id}'`)
    expect(stored).toBe(runSql(`SELECT count(*) FROM venue_reviews WHERE venue_id = '${venue.id}' AND status = 'visible'`))
    assertNoErrors()
  })

  test('fits a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await openCompleted(page)
    await page.getByTestId(`review-edit-${editId}`).click()
    const box = await page.getByTestId('review-dialog').boundingBox()
    expect(box.x + box.width).toBeLessThanOrEqual(376)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  })
})

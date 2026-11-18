import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { cleanupReviews, email, insertReview, pickVenue } from './reviewHelpers'

const TAG = 'E2E-VRR'
let venue
let hideId
let keepId
let reportHide
let reportKeep

const num = (sql) => Number(runSql(sql))
const aggregate = () => runSql(`SELECT coalesce(round(avg(rating), 2), 0) || '|' || count(*) FROM venue_reviews WHERE venue_id = '${venue.id}' AND status = 'visible'`).split('|')
const stored = () => runSql(`SELECT average_rating || '|' || review_count FROM venues WHERE id = '${venue.id}'`).split('|')

function addReport(reviewId, reason, description) {
  return runSql(`INSERT INTO reports (reporter_id, target_type, target_id, reason, description, status)
    SELECT id, 'venue_review', '${reviewId}', '${reason}', '${description}', 'pending' FROM users WHERE email = '${SEED_OWNER_EMAIL}' RETURNING id`).split('\n')[0]
}

async function openVenueReports(page) {
  await loginByApi(page, SEED_ADMIN_EMAIL)
  await page.goto('/admin')
  await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Báo cáo đánh giá' }).click()
  await page.getByTestId('review-kind-venue').click()
  await expect(page).toHaveURL(/kind=venue/)
  await expect(page.getByRole('heading', { name: 'Báo cáo đánh giá sân' })).toBeVisible()
  await expect(page.getByTestId('review-report-list')).toBeVisible()
}

async function conclude(page, openTestId, note) {
  await page.getByTestId(openTestId).click()
  await page.getByTestId('review-note').fill(note)
  await page.getByTestId('review-conclude-submit').click()
}

test.describe.serial('2.3.84 admin hides, keeps or restores reported venue reviews', () => {
  test.beforeAll(() => {
    runSql(`DELETE FROM reports WHERE description LIKE '${TAG}%'`)
    deleteE2eUsers()
    venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    for (const tag of ['vrr-a', 'vrr-b', 'vrr-c']) createE2eUser({ tag, name: `E2E Người Viết ${tag}` })
    hideId = insertReview(venue, 'vrr-a', null, 1, 'E2E lời lẽ xúc phạm chủ sân')
    keepId = insertReview(venue, 'vrr-b', null, 2, 'E2E nhận xét thẳng thắn')
    insertReview(venue, 'vrr-c', null, 5, 'E2E đánh giá tốt')
    runSql(`INSERT INTO venue_review_replies (review_id, owner_id, content) SELECT '${hideId}', owner_id, 'E2E chủ sân đã phản hồi' FROM venues WHERE id = '${venue.id}'`)
    const [average, count] = aggregate()
    runSql(`UPDATE venues SET average_rating = ${average}, review_count = ${count} WHERE id = '${venue.id}'`)
    reportHide = addReport(hideId, 'offensive', `${TAG} xúc phạm`)
    reportKeep = addReport(keepId, 'defamation', `${TAG} vu khống`)
  })
  test.afterAll(() => {
    runSql(`DELETE FROM reports WHERE description LIKE '${TAG}%'`)
    cleanupReviews(venue, 'E2EVRR')
  })

  test('lists reports of venue reviews with the reason label, content and counters, and filters them', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openVenueReports(page)
    await expect(page.getByTestId(`review-report-${reportHide}`)).toContainText('Xúc phạm, lăng mạ')
    await expect(page.getByTestId(`review-report-${reportHide}`)).toContainText('E2E lời lẽ xúc phạm chủ sân')
    await expect(page.getByTestId(`review-report-${reportHide}`)).toContainText(venue.name)
    await expect(page.getByTestId(`review-report-${reportKeep}`)).toContainText('Vu khống, sai sự thật')
    await expect(page.getByTestId('review-tab-pending')).toContainText(String(num(`SELECT count(*) FROM reports WHERE target_type = 'venue_review' AND status = 'pending'`)))

    await page.getByLabel('Lọc theo lý do').selectOption('defamation')
    await expect(page.getByTestId(`review-report-${reportKeep}`)).toBeVisible()
    await expect(page.getByTestId(`review-report-${reportHide}`)).toHaveCount(0)
    await page.getByLabel('Lọc theo lý do').selectOption('all')
    await page.getByLabel('Tìm báo cáo đánh giá').fill('xúc phạm chủ sân')
    await expect(page.getByTestId(`review-report-${reportHide}`)).toBeVisible()
    await expect(page.getByTestId(`review-report-${reportKeep}`)).toHaveCount(0)
    await page.getByLabel('Tìm báo cáo đánh giá').fill('')
    await shot(page, '2384-venue-reports')
    assertNoErrors()
  })

  test('hides a review: validates the note, hides it, recomputes the venue rating and tells the author; then restores it', async ({ page, browser }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openVenueReports(page)
    await page.getByTestId(`review-report-${reportHide}`).getByRole('button').click()
    const drawer = page.getByTestId('review-drawer')
    await expect(drawer).toContainText('E2E lời lẽ xúc phạm chủ sân')
    await expect(page.getByTestId('review-drawer-reply')).toContainText('E2E chủ sân đã phản hồi')
    await expect(page.getByTestId('review-drawer-reason')).toHaveText('Xúc phạm, lăng mạ')
    await expect(page.getByTestId('review-drawer-description')).toContainText(`${TAG} xúc phạm`)
    await page.getByTestId('review-start').click()
    await expect(page.getByTestId('review-drawer-status')).toContainText('Đang xử lý')

    await page.getByTestId('review-hide-open').click()
    await expect(page.getByTestId('review-hide-form')).toContainText('Người viết được thông báo')
    await page.getByTestId('review-note').fill('ab')
    await page.getByTestId('review-conclude-submit').click()
    await expect(page.getByTestId('review-hide-form')).toContainText('ít nhất 3 ký tự')
    await page.getByTestId('review-note').fill('Lời lẽ xúc phạm rõ ràng, vi phạm quy định')
    await shot(page, '2384-hide-form')
    await page.getByTestId('review-conclude-submit').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã ẩn đánh giá khỏi trang sân')
    await expect(page.getByTestId('review-handling-action')).toHaveText('Đã gỡ đánh giá')
    await expect(page.getByTestId('review-current-status')).toContainText('Đã gỡ')

    expect(runSql(`SELECT status FROM venue_reviews WHERE id = '${hideId}'`)).toBe('hidden')
    expect(runSql(`SELECT status || '|' || handling_action FROM reports WHERE id = '${reportHide}'`)).toBe('resolved|review_hidden')
    expect(stored()).toEqual(aggregate())
    expect(runSql(`SELECT count(*) FROM notifications WHERE type = 'venue_review_hidden' AND user_id = (SELECT id FROM users WHERE email = '${email('vrr-a')}')`)).toBe('1')
    expect(runSql(`SELECT count(*) FROM audit_logs WHERE action = 'review.hide' AND entity_id = '${hideId}'`)).toBe('1')

    // Trang sân công khai không còn đánh giá đó.
    const visitor = await (await browser.newContext()).newPage()
    await visitor.goto(`/san/${venue.slug}`)
    await expect(visitor.getByTestId('review-average')).toBeVisible()
    for (let i = 0; i < 4 && (await visitor.getByTestId('review-more').count()); i += 1) {
      await visitor.getByTestId('review-more').click()
      await visitor.waitForTimeout(400)
    }
    await expect(visitor.getByTestId(`venue-review-${hideId}`)).toHaveCount(0)
    await visitor.context().close()

    // Người viết nhận thông báo.
    await loginByApi(page, email('vrr-a'))
    await page.goto('/notifications')
    await expect(page.getByTestId('notif-list')).toContainText('Đánh giá của bạn đã bị ẩn')

    // Khôi phục.
    await openVenueReports(page)
    await page.getByTestId('review-tab-resolved').click()
    await page.getByTestId(`review-report-${reportHide}`).getByRole('button').click()
    await expect(page.getByTestId('review-restore-open')).toBeVisible()
    await conclude(page, 'review-restore-open', 'Xem lại thấy không vi phạm')
    await expect(page.getByTestId('admin-notice')).toContainText('hiển thị lại ở trang sân')
    await expect(page.getByTestId('review-restore-open')).toHaveCount(0)
    expect(runSql(`SELECT status FROM venue_reviews WHERE id = '${hideId}'`)).toBe('visible')
    expect(stored()).toEqual(aggregate())
    assertNoErrors()
  })

  test('keeping a review rejects the report and leaves the review and the venue rating untouched', async ({ page }) => {
    const before = stored()
    await openVenueReports(page)
    await page.getByTestId(`review-report-${reportKeep}`).getByRole('button').click()
    await page.getByTestId('review-start').click()
    await expect(page.getByTestId('review-keep-open')).toBeVisible()
    await conclude(page, 'review-keep-open', 'Nhận xét thẳng thắn, không vi phạm')
    await expect(page.getByTestId('admin-notice')).toContainText('Đã giữ nguyên đánh giá')
    await expect(page.getByTestId('review-handling-action')).toHaveText('Giữ nguyên đánh giá')
    expect(runSql(`SELECT status || '|' || handling_action FROM reports WHERE id = '${reportKeep}'`)).toBe('rejected|review_kept')
    expect(runSql(`SELECT status FROM venue_reviews WHERE id = '${keepId}'`)).toBe('visible')
    expect(stored()).toEqual(before)
    expect(runSql(`SELECT count(*) FROM notifications WHERE type = 'venue_review_hidden' AND user_id = (SELECT id FROM users WHERE email = '${email('vrr-b')}')`)).toBe('0')
  })

  test('the partner tab still works and the drawer fits a phone screen', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await expect(page.getByRole('heading', { name: 'Báo cáo đánh giá đối tác' })).toBeVisible()
    await expect(page.getByTestId('review-kind-partner')).toHaveAttribute('aria-pressed', 'true')
    await page.setViewportSize({ width: 375, height: 800 })
    await page.goto('/admin/review-reports?kind=venue')
    await expect(page.getByTestId('review-report-list').or(page.getByText('Không có báo cáo nào'))).toBeVisible()
    await page.getByTestId('review-tab-rejected').click()
    await page.getByTestId(`review-report-${reportKeep}`).getByRole('button').click()
    const box = await page.getByTestId('review-drawer').boundingBox()
    expect(box.x + box.width).toBeLessThanOrEqual(376)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  })
})

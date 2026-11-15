import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const TAG = 'E2E-RVREPORT'
const email = (tag) => `e2e-${tag}@example.com`
const userId = (tag) => `(SELECT id FROM users WHERE email = '${email(tag)}')`

function addReview({ by, about, rating, comment, status = 'visible' }) {
  return runSql(`
    INSERT INTO partner_reviews (reviewer_id, reviewed_user_id, rating, comment, status)
    VALUES (${userId(by)}, ${userId(about)}, ${rating}, '${comment}', '${status}') RETURNING id`).split('\n')[0]
}

function addReport({ reviewId, reason, status = 'pending', minutesAgo = 5, description = TAG }) {
  const target = reviewId ?? 'gen_random_uuid()'
  const targetSql = reviewId ? `'${reviewId}'` : target
  return runSql(`
    INSERT INTO reports (reporter_id, target_type, target_id, reason, description, status, created_at)
    VALUES (${userId('rv-bao-cao')}, 'partner_review', ${targetSql}, '${reason}', '${description}', '${status}', now() - interval '${minutesAgo} minutes')
    RETURNING id`).split('\n')[0]
}

async function publicReviews(page, viewer, playerEmail) {
  const { token } = await loginByApi(page, viewer)
  const id = runSql(`SELECT id FROM users WHERE email = '${playerEmail}'`)
  const response = await page.request.get(`${API_BASE_URL}/api/v1/players/${id}/partner-reviews`, { headers: { Authorization: `Bearer ${token}` } })
  expect(response.ok()).toBeTruthy()
  return response.json()
}

test.describe.serial('2.3.37 admin handles reported partner reviews', () => {
  let ids = {}

  test.beforeAll(() => {
    runSql(`DELETE FROM reports WHERE description LIKE '${TAG}%'`)
    deleteE2eUsers()
    createE2eUser({ tag: 'rv-bao-cao', name: 'E2E Người Báo Đánh Giá', role: 'player' })
    createE2eUser({ tag: 'rv-nguoi-cham', name: 'E2E Người Chấm Điểm', role: 'player' })
    createE2eUser({ tag: 'rv-duoc-cham', name: 'E2E Người Được Chấm', role: 'player' })
    createE2eUser({ tag: 'rv-khac', name: 'E2E Người Chấm Khác', role: 'player' })
    const bad = addReview({ by: 'rv-nguoi-cham', about: 'rv-duoc-cham', rating: 1, comment: 'Chơi tệ và thái độ xấu' })
    const good = addReview({ by: 'rv-khac', about: 'rv-duoc-cham', rating: 5, comment: 'Chơi rất hay và vui vẻ' })
    const fine = addReview({ by: 'rv-nguoi-cham', about: 'rv-khac', rating: 4, comment: 'Chơi tốt nhưng hay trễ giờ', status: 'reported' })
    ids = {
      bad, good, fine,
      hide: addReport({ reviewId: bad, reason: 'Nội dung xúc phạm', minutesAgo: 10, description: `${TAG} đánh giá có lời lẽ xúc phạm` }),
      dup: addReport({ reviewId: bad, reason: 'Spam', minutesAgo: 30 }),
      keep: addReport({ reviewId: fine, reason: 'Sai sự thật', minutesAgo: 20 }),
      closed: addReport({ reviewId: good, reason: 'Spam', status: 'rejected', minutesAgo: 600 }),
      orphan: addReport({ reviewId: null, reason: 'Giả mạo', minutesAgo: 50 }),
    }
  })
  test.afterAll(() => {
    runSql(`DELETE FROM audit_logs WHERE (entity_type = 'reports' AND entity_id IN (SELECT id FROM reports WHERE description LIKE '${TAG}%'))
      OR (entity_type = 'partner_reviews' AND entity_id IN (SELECT id FROM partner_reviews WHERE comment LIKE '%' AND reviewer_id IN (SELECT id FROM users WHERE email LIKE 'e2e-rv-%')))`)
    runSql(`DELETE FROM reports WHERE description LIKE '${TAG}%'`)
    runSql("DELETE FROM partner_reviews WHERE reviewer_id IN (SELECT id FROM users WHERE email LIKE 'e2e-rv-%@example.com')")
    deleteE2eUsers()
  })

  test('opens from the sidebar; tabs count, newest open first, review content and status are shown', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Báo cáo đánh giá' }).click()
    await expect(page).toHaveURL('/admin/review-reports')
    await expect(page.getByRole('heading', { name: 'Báo cáo đánh giá đối tác' })).toBeVisible()

    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    const list = page.getByTestId('review-report-list')
    await expect(list.getByRole('listitem')).toHaveCount(4)
    const count = (status) => runSql(`SELECT count(*) FROM reports WHERE target_type = 'partner_review' AND status = '${status}'`)
    await expect(page.getByTestId('review-tab-pending')).toHaveText(new RegExp(`Chờ xử lý${count('pending')}$`))
    await expect(list.getByRole('listitem').nth(0)).toHaveAttribute('data-testid', `review-report-${ids.hide}`)
    await expect(list.getByRole('listitem').nth(1)).toHaveAttribute('data-testid', `review-report-${ids.keep}`)
    await expect(page.getByTestId(`review-report-${ids.hide}`).getByTestId('review-report-comment')).toHaveText('Chơi tệ và thái độ xấu')
    await expect(page.getByTestId(`review-report-${ids.hide}`).getByRole('img', { name: '1 trên 5 sao' })).toBeVisible()
    await expect(page.getByTestId(`review-report-${ids.keep}`).getByTestId('review-report-review-status')).toHaveText('Đang bị báo cáo')
    await shot(page, '2337-list-desktop')

    await page.getByTestId('review-tab-all').click()
    await expect(list.getByRole('listitem')).toHaveCount(5)
    await expect(page.getByTestId(`review-report-${ids.orphan}`)).toContainText('Đánh giá không còn tồn tại')
    assertNoErrors()
  })

  test('filters by review status, reason and keyword (including review text)', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await page.getByTestId('review-tab-all').click()
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    const list = page.getByTestId('review-report-list')
    await expect(list.getByRole('listitem')).toHaveCount(5)

    await page.getByLabel('Lọc theo trạng thái đánh giá').selectOption('reported')
    await expect(list.getByRole('listitem')).toHaveCount(1)
    await page.getByLabel('Lọc theo trạng thái đánh giá').selectOption('all')
    await page.getByLabel('Lọc theo lý do').selectOption('Spam')
    await expect(list.getByRole('listitem')).toHaveCount(2)
    await page.getByLabel('Lọc theo lý do').selectOption('all')
    await page.getByLabel('Tìm báo cáo đánh giá').fill('trễ giờ')
    await expect(list.getByRole('listitem')).toHaveCount(1)
    await page.getByLabel('Tìm báo cáo đánh giá').fill('nguoi duoc cham')
    await expect(list.getByRole('listitem')).toHaveCount(3)
    await page.getByLabel('Tìm báo cáo đánh giá').fill('khong-co-ket-qua')
    await expect(page.getByText('Không có báo cáo nào')).toBeVisible()
  })

  test('detail shows the review, the people (names only), other reports and a start button', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    await page.getByTestId(`review-report-${ids.hide}`).getByRole('button', { name: /Xem báo cáo đánh giá/ }).click()
    const drawer = page.getByTestId('review-drawer')
    await expect(drawer.getByTestId('review-drawer-status')).toHaveText('Chờ xử lý')
    await expect(drawer.getByTestId('review-drawer-description')).toContainText('lời lẽ xúc phạm')
    await expect(drawer.getByTestId('review-comment')).toHaveText('Chơi tệ và thái độ xấu')
    await expect(drawer.getByTestId('review-current-status')).toHaveText('Đang hiển thị')
    await expect(drawer.getByTestId('review-drawer-review')).toContainText('E2E Người Chấm Điểm đánh giá E2E Người Được Chấm')
    await expect(drawer).not.toContainText('@example.com')
    await expect(drawer.getByTestId('review-related')).toContainText('Spam')
    await expect(drawer.getByTestId('review-start')).toBeVisible()
    await expect(drawer.getByTestId('review-hide-open')).toHaveCount(0)
    await shot(page, '2337-detail-pending')
    assertNoErrors()
  })

  test('hides the review: validation, conclusion shown, the review disappears from the public profile and average', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const before = await publicReviews(page, email('rv-bao-cao'), email('rv-duoc-cham'))
    expect(before.totalReviews).toBe(2)
    expect(Number(before.averageRating)).toBe(3)

    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    await page.getByTestId(`review-report-${ids.hide}`).getByRole('button', { name: /Xem báo cáo đánh giá/ }).click()
    const drawer = page.getByTestId('review-drawer')
    await drawer.getByTestId('review-start').click()
    await expect(drawer.getByTestId('review-drawer-status')).toHaveText('Đang xử lý')
    await drawer.getByTestId('review-hide-open').click()
    await drawer.getByTestId('review-conclude-submit').click()
    await expect(drawer.getByText('Vui lòng nhập ghi chú (ít nhất 3 ký tự)')).toBeVisible()
    await shot(page, '2337-hide-form')
    await drawer.getByTestId('review-note').fill('Lời lẽ xúc phạm vi phạm quy tắc cộng đồng')
    await drawer.getByTestId('review-conclude-submit').click()

    await expect(page.getByTestId('admin-notice')).toContainText('Đã gỡ đánh giá')
    await expect(drawer.getByTestId('review-drawer-status')).toHaveText('Đã xử lý')
    await expect(drawer.getByTestId('review-current-status')).toHaveText('Đã gỡ')
    await expect(drawer.getByTestId('review-handling-action')).toHaveText('Đã gỡ đánh giá')
    await expect(drawer.getByTestId('review-handling-note')).toHaveText('Lời lẽ xúc phạm vi phạm quy tắc cộng đồng')
    await expect(drawer.getByTestId('review-hide-open')).toHaveCount(0)
    await expect(drawer.getByTestId('review-restore-open')).toBeVisible()
    await shot(page, '2337-detail-hidden')

    const after = await publicReviews(page, email('rv-bao-cao'), email('rv-duoc-cham'))
    expect(after.totalReviews).toBe(1)
    expect(Number(after.averageRating)).toBe(5)
    assertNoErrors()
  })

  test('a second report about an already hidden review can still be concluded without hiding twice', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    await page.getByTestId(`review-report-${ids.dup}`).getByRole('button', { name: /Xem báo cáo đánh giá/ }).click()
    const drawer = page.getByTestId('review-drawer')
    await expect(drawer.getByTestId('review-current-status')).toHaveText('Đã gỡ')
    await drawer.getByTestId('review-start').click()
    await drawer.getByTestId('review-hide-open').click()
    await drawer.getByTestId('review-note').fill('Trùng với báo cáo đã xử lý')
    await drawer.getByTestId('review-conclude-submit').click()
    await expect(drawer.getByTestId('review-drawer-status')).toHaveText('Đã xử lý')
    expect(runSql(`SELECT count(*) FROM audit_logs WHERE entity_type = 'partner_reviews' AND entity_id = '${ids.bad}' AND action = 'review.hide'`)).toBe('1')
  })

  test('restores the hidden review: it shows again on the public profile and cannot be restored twice', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await page.getByTestId('review-tab-resolved').click()
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    await page.getByTestId(`review-report-${ids.hide}`).getByRole('button', { name: /Xem báo cáo đánh giá/ }).click()
    const drawer = page.getByTestId('review-drawer')
    await drawer.getByTestId('review-restore-open').click()
    await drawer.getByTestId('review-note').fill('Xem lại thấy đánh giá hợp lệ')
    await drawer.getByTestId('review-conclude-submit').click()

    await expect(page.getByTestId('admin-notice')).toContainText('Đã khôi phục đánh giá')
    await expect(drawer.getByTestId('review-current-status')).toHaveText('Đang hiển thị')
    await expect(drawer.getByTestId('review-drawer-status')).toHaveText('Đã xử lý')
    await expect(drawer.getByTestId('review-restore-open')).toHaveCount(0)
    const after = await publicReviews(page, email('rv-bao-cao'), email('rv-duoc-cham'))
    expect(after.totalReviews).toBe(2)
    assertNoErrors()
  })

  test('keeps a reported review: report is dismissed and the review is visible again', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    await page.getByTestId(`review-report-${ids.keep}`).getByRole('button', { name: /Xem báo cáo đánh giá/ }).click()
    const drawer = page.getByTestId('review-drawer')
    await expect(drawer.getByTestId('review-current-status')).toHaveText('Đang bị báo cáo')
    await drawer.getByTestId('review-start').click()
    await drawer.getByTestId('review-keep-open').click()
    await drawer.getByTestId('review-note').fill('Đánh giá phản ánh đúng trải nghiệm')
    await drawer.getByTestId('review-conclude-submit').click()
    await expect(drawer.getByTestId('review-drawer-status')).toHaveText('Đã bác bỏ')
    await expect(drawer.getByTestId('review-current-status')).toHaveText('Đang hiển thị')
    await expect(drawer.getByTestId('review-handling-action')).toHaveText('Giữ nguyên đánh giá')
    await expect(drawer.getByTestId('review-keep-open')).toHaveCount(0)
    expect(runSql(`SELECT status FROM partner_reviews WHERE id = '${ids.fine}'`)).toBe('visible')
    assertNoErrors()
  })

  test('a report whose review no longer exists can only be dismissed', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    await page.getByTestId(`review-report-${ids.orphan}`).getByRole('button', { name: /Xem báo cáo đánh giá/ }).click()
    const drawer = page.getByTestId('review-drawer')
    await expect(drawer.getByTestId('review-missing')).toBeVisible()
    await drawer.getByTestId('review-start').click()
    await expect(drawer.getByTestId('review-hide-open')).toHaveCount(0)
    await drawer.getByTestId('review-keep-open').click()
    await drawer.getByTestId('review-note').fill('Đánh giá đã bị xóa từ trước')
    await drawer.getByTestId('review-conclude-submit').click()
    await expect(drawer.getByTestId('review-drawer-status')).toHaveText('Đã bác bỏ')
  })

  test('shows an error state with retry and a conflict when the report changed elsewhere', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    let fail = true
    await page.route('**/api/v1/admin/review-reports?**', async (route) => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) })
      else await route.continue()
    })
    await page.goto('/admin/review-reports')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
    fail = false
    await page.getByRole('button', { name: /Thử lại/ }).click()
    await expect(page.getByTestId('review-tab-all')).toBeVisible()

    runSql(`UPDATE reports SET status = 'pending', handling_action = NULL, handling_note = NULL, handled_at = NULL WHERE id = '${ids.closed}'`)
    await page.getByTestId('review-tab-all').click()
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    await page.getByTestId(`review-report-${ids.closed}`).getByRole('button', { name: /Xem báo cáo đánh giá/ }).click()
    const drawer = page.getByTestId('review-drawer')
    await expect(drawer.getByTestId('review-start')).toBeVisible()
    runSql(`UPDATE reports SET status = 'reviewing' WHERE id = '${ids.closed}'`)
    await drawer.getByTestId('review-start').click()
    await expect(drawer.getByText('Chi nhan xu ly duoc bao cao dang cho xu ly.')).toBeVisible()
    await expect(drawer.getByTestId('review-keep-open')).toBeVisible()
  })

  test('list and drawer fit a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/review-reports')
    await page.getByTestId('review-tab-all').click()
    await page.getByLabel('Tìm báo cáo đánh giá').fill('e2e')
    await expect(page.getByTestId('review-report-list')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(page, '2337-list-mobile')
    await page.getByTestId(`review-report-${ids.hide}`).getByRole('button', { name: /Xem báo cáo đánh giá/ }).click()
    await expect(page.getByTestId('review-drawer-status')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    expect(await page.getByTestId('review-drawer').evaluate((el) => el.scrollWidth <= el.clientWidth)).toBeTruthy()
    await shot(page, '2337-detail-mobile')
    assertNoErrors()
    await context.close()
  })

  test('players cannot open the page', async ({ page }) => {
    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto('/admin/review-reports')
    await expect(page.getByRole('heading', { name: 'Báo cáo đánh giá đối tác' })).toHaveCount(0)
  })
})

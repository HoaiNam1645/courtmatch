import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'
import { cleanupReviews, email, insertReview, pickVenue } from './reviewHelpers'

const RATINGS = [5, 5, 5, 4, 4, 3, 2, 2]
let venue
let hiddenId

const visible = (extra = '') => `FROM venue_reviews WHERE venue_id = '${venue.id}' AND status = 'visible' ${extra}`
const num = (sql) => Number(runSql(sql))
const open = async (page) => {
  await page.goto(`/san/${venue.slug}#danh-gia`)
  await expect(page.getByTestId('review-average')).toBeVisible()
}

test.describe.serial('2.3.80 anyone can read a venue reviews list', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    venue = pickVenue()
    RATINGS.forEach((rating, index) => {
      createE2eUser({ tag: `rl-${index}`, name: `E2E Người Xem ${index}` })
      insertReview(venue, `rl-${index}`, null, rating, `E2E nhận xét ${index}`)
    })
    createE2eUser({ tag: 'rl-hidden', name: 'E2E Bị Ẩn' })
    hiddenId = insertReview(venue, 'rl-hidden', null, 1, 'E2E ẨN KHÔNG ĐƯỢC HIỆN', 'hidden')
    runSql(`UPDATE venues SET average_rating = (SELECT round(avg(rating), 2) ${visible()}), review_count = (SELECT count(*) ${visible()}) WHERE id = '${venue.id}'`)
  })
  test.afterAll(() => cleanupReviews(venue, 'E2ERL'))

  test('shows the exact average, total and per-star distribution of visible reviews only', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page)
    const total = num(`SELECT count(*) ${visible()}`)
    await expect(page.getByTestId('review-total')).toHaveText(`${total} đánh giá`)
    const average = Number(runSql(`SELECT round(avg(rating), 2) ${visible()}`))
    await expect(page.getByTestId('review-average')).toHaveText(average.toLocaleString('vi-VN', { minimumFractionDigits: 1, maximumFractionDigits: 2 }))
    for (const star of [1, 2, 3, 4, 5]) {
      await expect(page.getByTestId(`review-filter-${star}`)).toHaveAttribute('data-count', String(num(`SELECT count(*) ${visible(`AND rating = ${star}`)}`)))
    }
    await expect(page.getByText('E2E ẨN KHÔNG ĐƯỢC HIỆN')).toHaveCount(0)
    await expect(page.getByTestId(`venue-review-${hiddenId}`)).toHaveCount(0)
    await page.locator('#danh-gia').scrollIntoViewIfNeeded()
    await shot(page, '2380-reviews-desktop')
    assertNoErrors()
  })

  test('pages through the list with "Xem thêm" until every visible review is shown', async ({ page }) => {
    await open(page)
    const total = num(`SELECT count(*) ${visible()}`)
    const cards = page.locator('[data-testid^="venue-review-"][data-rating]')
    await expect(cards).toHaveCount(Math.min(6, total))
    while ((await cards.count()) < total) {
      const before = await cards.count()
      await page.getByTestId('review-more').click()
      await expect(cards).not.toHaveCount(before)
    }
    await expect(cards).toHaveCount(total)
    await expect(page.getByTestId('review-more')).toHaveCount(0)
  })

  test('clicking a star row filters the list, clicking again or "Xem tất cả" clears it', async ({ page }) => {
    await open(page)
    const five = num(`SELECT count(*) ${visible('AND rating = 5')}`)
    await page.getByTestId('review-filter-5').click()
    await expect(page.getByTestId('review-filter-5')).toHaveAttribute('aria-pressed', 'true')
    const cards = page.locator('[data-testid^="venue-review-"][data-rating]')
    await expect(cards).toHaveCount(Math.min(6, five))
    expect(await cards.evaluateAll((items) => items.every((item) => item.getAttribute('data-rating') === '5'))).toBe(true)
    await page.getByTestId('review-filter-clear').click()
    await expect(page.getByTestId('review-filter-5')).toHaveAttribute('aria-pressed', 'false')
    await page.getByTestId('review-filter-2').click()
    await expect(cards.first()).toHaveAttribute('data-rating', '2')
    await page.getByTestId('review-filter-2').click()
    await expect(page.getByTestId('review-filter-2')).toHaveAttribute('aria-pressed', 'false')
  })

  test('a star level without reviews says so', async ({ page }) => {
    const empty = [1, 2, 3, 4, 5].find((star) => num(`SELECT count(*) ${visible(`AND rating = ${star}`)}`) === 0)
    test.skip(!empty, 'mọi mức sao đều đã có đánh giá thật')
    await open(page)
    await page.getByTestId(`review-filter-${empty}`).click()
    await expect(page.getByTestId('review-empty')).toHaveText(`Chưa có đánh giá ${empty} sao nào.`)
  })

  test('the signed-in author sees their review marked, can edit it (label "Đã chỉnh sửa") and delete it', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('rl-7'))
    await open(page)
    const mine = page.locator('[data-testid^="venue-review-"][data-mine="true"]')
    await expect(mine).toHaveCount(1)
    await expect(mine).toContainText('Của bạn')
    await expect(mine).toContainText('E2E nhận xét 7')
    const id = (await mine.getAttribute('data-testid')).replace('venue-review-', '')
    await expect(page.getByTestId(`review-edited-${id}`)).toHaveCount(0)

    await page.getByTestId(`venue-review-edit-${id}`).click()
    await page.getByTestId('review-star-3').click()
    await page.getByTestId('review-comment').fill('E2E đã sửa nhận xét')
    await page.getByTestId('review-submit').click()
    await expect(page.getByTestId('review-notice')).toContainText('Đã cập nhật')
    await expect(page.getByTestId(`review-edited-${id}`)).toBeVisible()
    await expect(page.getByTestId(`venue-review-${id}`)).toContainText('E2E đã sửa nhận xét')
    expect(runSql(`SELECT rating FROM venue_reviews WHERE id = '${id}'`)).toBe('3')

    const totalBefore = num(`SELECT count(*) ${visible()}`)
    await page.getByTestId(`venue-review-delete-${id}`).click()
    await page.getByTestId('review-delete-confirm').click()
    await expect(page.getByTestId('review-notice')).toContainText('Đã xóa')
    await expect(page.getByTestId(`venue-review-${id}`)).toHaveCount(0)
    await expect(page.getByTestId('review-total')).toHaveText(`${totalBefore - 1} đánh giá`)
    expect(runSql(`SELECT count(*) FROM venue_reviews WHERE id = '${id}'`)).toBe('0')
    assertNoErrors()
  })

  test('an anonymous visitor sees no marked review and no edit buttons; layout fits a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await open(page)
    await expect(page.locator('[data-mine="true"]')).toHaveCount(0)
    await expect(page.locator('[data-testid^="venue-review-edit-"]')).toHaveCount(0)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await page.locator('#danh-gia').scrollIntoViewIfNeeded()
    await shot(page, '2380-reviews-phone')
  })
})

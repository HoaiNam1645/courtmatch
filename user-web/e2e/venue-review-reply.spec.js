import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { cleanupReviews, email, insertReview, pickVenue } from './reviewHelpers'

let venue
let openId
let otherId
let answeredId
let hiddenId

const num = (sql) => Number(runSql(sql))
const ownerUnanswered = () => num(`SELECT count(*) FROM venue_reviews r JOIN venues v ON v.id = r.venue_id JOIN users o ON o.id = v.owner_id
  WHERE o.email = '${SEED_OWNER_EMAIL}' AND r.status = 'visible' AND NOT EXISTS (SELECT 1 FROM venue_review_replies x WHERE x.review_id = r.id)`)

async function openOwnerReviews(page) {
  await loginByApi(page, SEED_OWNER_EMAIL)
  await page.goto('/owner')
  await page.getByRole('navigation', { name: 'Điều hướng quản lý sân' }).getByRole('link', { name: 'Đánh giá của khách' }).click()
  await expect(page).toHaveURL('/owner/reviews')
  await expect(page.getByTestId('or-list')).toBeVisible()
}

test.describe.serial('2.3.81 owner reads venue reviews and replies publicly', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    for (const tag of ['rp-a', 'rp-b', 'rp-c', 'rp-d']) createE2eUser({ tag, name: `E2E Khách ${tag}` })
    // Mới nhất trước: viết lần lượt để các đánh giá E2E nằm đầu danh sách.
    answeredId = insertReview(venue, 'rp-c', null, 4, 'E2E đánh giá đã trả lời')
    runSql(`INSERT INTO venue_review_replies (review_id, owner_id, content) SELECT '${answeredId}', owner_id, 'E2E cảm ơn bạn' FROM venues WHERE id = '${venue.id}'`)
    hiddenId = insertReview(venue, 'rp-d', null, 1, 'E2E đánh giá bị ẩn', 'hidden')
    otherId = insertReview(venue, 'rp-b', null, 3, 'E2E đánh giá chưa trả lời B')
    openId = insertReview(venue, 'rp-a', null, 5, 'E2E đánh giá chưa trả lời A')
  })
  test.afterAll(() => cleanupReviews(venue, 'E2ERP'))

  test('lists visible reviews with the unanswered counter and filters by answered state, venue and stars', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openOwnerReviews(page)
    await expect(page.getByTestId('or-filter-unanswered')).toHaveText(`Chưa trả lời (${ownerUnanswered()})`)
    await expect(page.getByTestId(`owner-review-${openId}`)).toHaveAttribute('data-answered', 'false')
    await expect(page.getByTestId(`owner-review-${answeredId}`)).toHaveAttribute('data-answered', 'true')
    await expect(page.getByTestId(`owner-reply-${answeredId}`)).toContainText('E2E cảm ơn bạn')
    await expect(page.getByTestId(`owner-review-${hiddenId}`)).toHaveCount(0)

    await page.getByTestId('or-filter-unanswered').click()
    await expect(page.getByTestId(`owner-review-${openId}`)).toBeVisible()
    await expect(page.getByTestId(`owner-review-${answeredId}`)).toHaveCount(0)
    await page.getByTestId('or-filter-answered').click()
    await expect(page.getByTestId(`owner-review-${answeredId}`)).toBeVisible()
    await expect(page.getByTestId(`owner-review-${openId}`)).toHaveCount(0)
    await page.getByTestId('or-filter-all').click()
    await page.getByTestId('or-rating').selectOption('5')
    await expect(page.getByTestId(`owner-review-${answeredId}`)).toHaveCount(0) // đánh giá 4 sao biến mất khi lọc 5 sao
    const cards = page.locator('[data-testid^="owner-review-"][data-rating]')
    expect(await cards.evaluateAll((items) => items.length > 0 && items.every((item) => item.getAttribute('data-rating') === '5'))).toBe(true)
    await page.getByTestId('or-rating').selectOption('')
    await page.getByTestId('or-venue').selectOption(venue.id)
    await expect(page.getByTestId(`owner-review-${openId}`)).toBeVisible()
    await shot(page, '2381-owner-reviews')
    assertNoErrors()
  })

  test('validates the reply, then posts it: shown on the venue page, reviewer notified, counter drops', async ({ page, browser }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openOwnerReviews(page)
    const before = ownerUnanswered()
    await page.getByTestId(`reply-submit-${openId}`).click()
    await expect(page.getByTestId(`reply-form-${openId}`)).toContainText('Vui lòng nhập nội dung trả lời')
    await page.getByTestId(`reply-text-${openId}`).fill('x'.repeat(1001))
    await page.getByTestId(`reply-submit-${openId}`).click()
    await expect(page.getByTestId(`reply-form-${openId}`)).toContainText('tối đa 1000 ký tự')

    await page.getByTestId(`reply-text-${openId}`).fill('  Cảm ơn bạn đã ủng hộ sân!  ')
    await page.getByTestId(`reply-submit-${openId}`).click()
    await expect(page.getByTestId('or-notice')).toContainText('Đã đăng câu trả lời')
    await expect(page.getByTestId(`owner-reply-${openId}`)).toContainText('Cảm ơn bạn đã ủng hộ sân!')
    await expect(page.getByTestId(`reply-form-${openId}`)).toHaveCount(0)
    await expect(page.getByTestId('or-filter-unanswered')).toHaveText(`Chưa trả lời (${before - 1})`)
    expect(runSql(`SELECT content FROM venue_review_replies WHERE review_id = '${openId}'`)).toBe('Cảm ơn bạn đã ủng hộ sân!')

    // Khách ẩn danh thấy câu trả lời ngay dưới đánh giá.
    const visitor = await (await browser.newContext()).newPage()
    await visitor.goto(`/san/${venue.slug}`)
    for (let i = 0; i < 4 && (await visitor.getByTestId(`review-reply-${openId}`).count()) === 0; i += 1) {
      if (await visitor.getByTestId('review-more').count()) await visitor.getByTestId('review-more').click()
      await visitor.waitForTimeout(400)
    }
    await expect(visitor.getByTestId(`review-reply-${openId}`)).toContainText('Cảm ơn bạn đã ủng hộ sân!')
    await expect(visitor.getByTestId(`review-reply-${openId}`)).toContainText('Phản hồi từ chủ sân')
    await visitor.context().close()

    // Người viết đánh giá nhận thông báo dẫn tới mục đánh giá của sân.
    expect(runSql(`SELECT data_json ->> 'route' FROM notifications WHERE user_id = (SELECT id FROM users WHERE email = '${email('rp-a')}') AND type = 'venue_review_replied'`))
      .toBe(`/san/${venue.slug}#danh-gia`)
    await loginByApi(page, email('rp-a'))
    await page.goto('/notifications')
    await expect(page.getByTestId('notif-list')).toContainText('Chủ sân đã trả lời đánh giá của bạn')
    assertNoErrors()
  })

  test('a review answered elsewhere while the form is open is refreshed with the server message', async ({ page }) => {
    await openOwnerReviews(page)
    await page.getByTestId(`reply-text-${otherId}`).fill('Trả lời muộn')
    runSql(`INSERT INTO venue_review_replies (review_id, owner_id, content) SELECT '${otherId}', owner_id, 'Đã có người trả lời trước' FROM venues WHERE id = '${venue.id}'`)
    await page.getByTestId(`reply-submit-${otherId}`).click()
    await expect(page.getByTestId('or-notice')).toContainText('da co cau tra loi')
    await expect(page.getByTestId(`owner-reply-${otherId}`)).toContainText('Đã có người trả lời trước')
    expect(runSql(`SELECT count(*) FROM venue_review_replies WHERE review_id = '${otherId}'`)).toBe('1')
  })

  test('a player cannot use the owner page and the layout fits a phone', async ({ page }) => {
    await loginByApi(page, email('rp-a'))
    await page.goto('/owner/reviews')
    await expect(page.getByTestId('or-list')).toHaveCount(0)
    await page.setViewportSize({ width: 375, height: 800 })
    await openOwnerReviews(page)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await shot(page, '2381-owner-reviews-phone')
  })
})

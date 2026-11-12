import { expect, test } from '@playwright/test'
import { loginByApi, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.2.43 - 2.2.45 partner reviews', () => {
  let matchPath = ''
  let creatorId = ''
  let opponentId = ''

  test('2.2.43 rates another match participant from 1 to 5 stars', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const creator = await loginByApi(page, 'an.nguyen@example.com')
    const opponent = await loginByApi(page, 'binh.tran@example.com')
    creatorId = creator.user.id
    opponentId = opponent.user.id

    const matchId = await createCompletedMatch(page, creator, opponent)
    matchPath = `/matches/${matchId}`
    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto(matchPath)

    await expect(page.getByRole('heading', { name: 'Chia sẻ trải nghiệm sau trận' })).toBeVisible()
    await page.getByRole('button', { name: '5 sao' }).click()
    const reviewResponse = page.waitForResponse((response) => response.url().endsWith('/api/v1/partner-reviews') && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Gửi đánh giá' }).click()
    expect((await reviewResponse).status()).toBe(201)
    await expect(page.getByText('Đã gửi đánh giá cho Tran Thi Binh.')).toBeVisible()
    await expect(page.getByText('Bạn đã đánh giá tất cả người chơi khác trong trận này.')).toBeVisible()
    assertNoErrors()
  })

  test('2.2.44 submits an optional written comment with the star rating', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'binh.tran@example.com')
    await page.goto(matchPath)

    await page.getByRole('button', { name: '4 sao' }).click()
    await page.getByLabel('Nhận xét (không bắt buộc)').fill('Đối tác thân thiện, đúng giờ và chơi rất đẹp.')
    const reviewResponse = page.waitForResponse((response) => response.url().endsWith('/api/v1/partner-reviews') && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Gửi đánh giá' }).click()
    expect((await reviewResponse).status()).toBe(201)
    await expect(page.getByText('Đã gửi đánh giá cho Nguyen Van An.')).toBeVisible()
    assertNoErrors()
  })

  test('2.2.45 displays public reviews, average score and total count', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, 'an.nguyen@example.com')
    const apiResponse = await page.request.get(
      `${API_BASE_URL}/api/v1/players/${creatorId}/partner-reviews?page=0&size=10`,
      { headers: { Authorization: `Bearer ${token}` } },
    )
    expect(apiResponse.ok()).toBeTruthy()
    const payload = await apiResponse.json()
    const submittedReview = payload.content.find((review) => review.matchId === matchPath.split('/').at(-1))
    expect(submittedReview.comment).toBe('Đối tác thân thiện, đúng giờ và chơi rất đẹp.')
    expect(submittedReview.reviewer.email).toBeUndefined()
    expect(submittedReview.reviewer.phone).toBeUndefined()

    await page.goto(`/partners/${creatorId}/reviews`)

    await expect(page.getByRole('heading', { name: 'Nguyen Van An' })).toBeVisible()
    await expect(page.getByTestId('average-rating')).toHaveText(Number(payload.averageRating).toFixed(1))
    await expect(page.getByTestId('total-reviews')).toHaveText(String(payload.totalReviews))
    const reviewCard = page.getByTestId(`partner-review-${submittedReview.id}`)
    await expect(reviewCard.getByText('Đối tác thân thiện, đúng giờ và chơi rất đẹp.')).toBeVisible()
    await expect(reviewCard.getByRole('heading', { name: 'Tran Thi Binh' })).toBeVisible()

    const opponentResponse = await page.request.get(
      `${API_BASE_URL}/api/v1/players/${opponentId}/partner-reviews?page=0&size=10`,
      { headers: { Authorization: `Bearer ${token}` } },
    )
    expect(opponentResponse.ok()).toBeTruthy()
    const opponentPayload = await opponentResponse.json()
    expect(opponentPayload.content.some((review) => review.matchId === matchPath.split('/').at(-1) && review.rating === 5)).toBeTruthy()
    assertNoErrors()
  })

  test('partner review pages remain usable on mobile Chrome', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto(`/partners/${creatorId}/reviews`)
    await expect(page.getByRole('heading', { name: 'Nguyen Van An' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    assertNoErrors()
    await context.close()
  })
})

async function createCompletedMatch(page, creator, opponent) {
  const createResponse = await page.request.post(`${API_BASE_URL}/api/v1/matches`, {
    headers: { Authorization: `Bearer ${creator.token}` },
    data: {
      bookingId: null,
      venueId: null,
      matchType: 'single',
      scheduledAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
      team1UserIds: [creator.user.id],
      team2UserIds: [opponent.user.id],
    },
  })
  expect(createResponse.status(), await createResponse.text()).toBe(201)
  const match = await createResponse.json()

  const resultResponse = await page.request.put(`${API_BASE_URL}/api/v1/matches/${match.id}/result`, {
    headers: { Authorization: `Bearer ${creator.token}` },
    data: { games: [
      { gameNo: 1, team1Score: 21, team2Score: 17 },
      { gameNo: 2, team1Score: 21, team2Score: 19 },
    ] },
  })
  expect(resultResponse.ok(), await resultResponse.text()).toBeTruthy()

  const confirmResponse = await page.request.post(`${API_BASE_URL}/api/v1/matches/${match.id}/confirm`, {
    headers: { Authorization: `Bearer ${opponent.token}` },
  })
  expect(confirmResponse.ok(), await confirmResponse.text()).toBeTruthy()
  return match.id
}

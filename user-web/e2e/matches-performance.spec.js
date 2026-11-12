import { expect, test } from '@playwright/test'
import { loginByApi, watchRuntimeErrors } from './helpers'

test.describe.serial('2.2.37 - 2.2.42 match management', () => {
  let matchPath = ''

  test('2.2.37 creates a match without a result', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto('/matches/new')
    await expect(page.getByRole('heading', { name: 'Tạo trận đấu' })).toBeVisible()
    await page.getByRole('button', { name: 'Nhập thủ công' }).click()
    await page.getByRole('button', { name: 'Đánh đơn' }).click()

    const playedAt = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const localDateTime = new Date(playedAt.getTime() - playedAt.getTimezoneOffset() * 60_000)
      .toISOString().slice(0, 16)
    await page.locator('input[type="datetime-local"]').fill(localDateTime)
    const opponentTeam = page.getByText('Đội đối phương', { exact: true }).locator('..')
    await opponentTeam.locator('select').first().selectOption({ label: 'Tran Thi Binh' })
    await page.getByRole('button', { name: 'Tạo trận đấu', exact: true }).click()

    await expect(page).toHaveURL(/\/matches\/[0-9a-f-]{36}$/)
    matchPath = new URL(page.url()).pathname
    await expect(page.getByText('Chờ ghi kết quả').first()).toBeVisible()
    await expect(page.getByText('Trận đấu đã được tạo và đang chờ người tạo ghi kết quả.')).toBeVisible()
    assertNoErrors()
  })

  test('2.2.38 records a valid badminton result', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto(matchPath)
    await expect(page.getByRole('heading', { name: 'Nhập tỷ số từng set' })).toBeVisible()
    await page.getByRole('button', { name: 'Lưu kết quả trận đấu' }).click()
    await expect(page.getByText('Đã ghi kết quả; đang chờ đối thủ xác nhận.')).toBeVisible()
    await expect(page.getByText('21—18')).toBeVisible()
    await expect(page.getByText('21—16')).toBeVisible()
    assertNoErrors()
  })

  test('2.2.39 confirms the match and updates player ratings', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, 'binh.tran@example.com')
    await page.goto(matchPath)
    await page.getByRole('button', { name: 'Kết quả chính xác' }).click()
    await expect(page.getByText('Bạn đã xác nhận kết quả.')).toBeVisible()
    await expect(page.getByText('Đã xác nhận').first()).toBeVisible()

    const response = await page.request.get(
      'http://127.0.0.1:8080/api/v1/players/me/rating-history?days=90&page=0&size=100',
      { headers: { Authorization: `Bearer ${token}` } },
    )
    expect(response.ok()).toBeTruthy()
    const ratingHistory = await response.json()
    expect(ratingHistory.content.some((item) => item.matchId === matchPath.split('/').at(-1))).toBeTruthy()
    assertNoErrors()
  })

  test('2.2.40 exposes updated performance statistics', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'binh.tran@example.com')
    await page.goto('/performance')
    await expect(page.getByRole('heading', { name: 'Phong độ của Tran Thi Binh' })).toBeVisible()
    await expect(page.getByRole('img', { name: 'Biểu đồ thay đổi điểm kỹ năng' })).toBeVisible()
    await expect(page.getByText('Trận hợp lệ được tính')).toBeVisible()
    assertNoErrors()
  })

  test('2.2.41 displays the recorded result to participants', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'binh.tran@example.com')
    await page.goto(matchPath)
    await expect(page.getByRole('heading', { name: 'Kết quả trận đấu' })).toBeVisible()
    await expect(page.getByText('Tran Thi Binh', { exact: true }).nth(1)).toBeVisible()
    await expect(page.getByText('21—18')).toBeVisible()
    await expect(page.getByText('Đã xác nhận').first()).toBeVisible()
    assertNoErrors()
  })

  test('2.2.42 lists the completed match in history', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'binh.tran@example.com')
    await page.goto('/matches')
    await page.getByRole('button', { name: 'Đã xác nhận' }).click()
    await expect(page.locator(`a[href="${matchPath}"]`)).toBeVisible()
    await expect(page.getByText('2.2.42 · Lịch sử trận đấu')).toBeVisible()
    assertNoErrors()
  })

  test('match pages remain usable on mobile Chrome', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'binh.tran@example.com')
    await page.goto(matchPath)
    await expect(page.getByRole('heading', { name: 'Kết quả trận đấu' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    assertNoErrors()
    await context.close()
  })
})

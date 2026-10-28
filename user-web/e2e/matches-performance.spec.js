import { expect, test } from '@playwright/test'
import { loginByApi, watchRuntimeErrors } from './helpers'

test.describe.serial('match data and player performance', () => {
  test('creates a match in the UI, confirms as the opponent and updates performance views', async ({ browser }) => {
    const creatorContext = await browser.newContext()
    const creatorPage = await creatorContext.newPage()
    const assertCreatorNoErrors = watchRuntimeErrors(creatorPage)
    await loginByApi(creatorPage, 'an.nguyen@example.com')

    await creatorPage.goto('/matches')
    await expect(creatorPage.getByRole('heading', { name: 'Mỗi trận đấu là một bước tiến bộ.' })).toBeVisible()
    await expect(creatorPage.getByText('Hành trình trên sân')).toBeVisible()
    await creatorPage.getByRole('link', { name: /Ghi kết quả trận đấu/ }).first().click()
    await creatorPage.getByRole('button', { name: 'Nhập thủ công' }).click()
    await creatorPage.getByRole('button', { name: 'Đánh đơn' }).click()

    const past = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const localDateTime = new Date(past.getTime() - past.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
    await creatorPage.locator('input[type="datetime-local"]').fill(localDateTime)
    const opponentTeam = creatorPage.getByText('Đội đối phương', { exact: true }).locator('..')
    await opponentTeam.getByRole('combobox').selectOption({ label: 'Tran Thi Binh' })
    await creatorPage.getByLabel('Điểm đội 1 set 1').fill('21')
    await creatorPage.getByLabel('Điểm đội 2 set 1').fill('18')
    await creatorPage.getByLabel('Điểm đội 1 set 2').fill('19')
    await creatorPage.getByLabel('Điểm đội 2 set 2').fill('21')
    await creatorPage.getByLabel('Điểm đội 1 set 3').fill('21')
    await creatorPage.getByLabel('Điểm đội 2 set 3').fill('16')
    await creatorPage.getByRole('button', { name: /Gửi kết quả xác nhận/ }).click()
    await expect(creatorPage).toHaveURL(/\/matches\/[0-9a-f-]{36}$/)
    const matchUrl = creatorPage.url()
    await expect(creatorPage.getByText('Chờ xác nhận')).toBeVisible()
    assertCreatorNoErrors()

    const opponentContext = await browser.newContext()
    const opponentPage = await opponentContext.newPage()
    const assertOpponentNoErrors = watchRuntimeErrors(opponentPage)
    await loginByApi(opponentPage, 'binh.tran@example.com')
    await opponentPage.goto(matchUrl)
    await expect(opponentPage.getByRole('button', { name: 'Kết quả chính xác' })).toBeVisible()
    await opponentPage.getByRole('button', { name: 'Kết quả chính xác' }).click()
    await expect(opponentPage.getByText('Bạn đã xác nhận kết quả.')).toBeVisible()
    await expect(opponentPage.getByText('Đã xác nhận').first()).toBeVisible()

    await opponentPage.goto('/performance')
    await expect(opponentPage.getByRole('heading', { name: 'Phong độ của Tran Thi Binh' })).toBeVisible()
    await expect(opponentPage.getByRole('img', { name: 'Biểu đồ thay đổi điểm kỹ năng' })).toBeVisible()
    await opponentPage.goto('/matches')
    await opponentPage.getByRole('button', { name: 'Đã xác nhận' }).click()
    await expect(opponentPage.locator(`a[href="${new URL(matchUrl).pathname}"]`)).toBeVisible()
    assertOpponentNoErrors()

    await creatorContext.close()
    await opponentContext.close()
  })

  test('protects match detail from non-participants and renders performance on mobile Chrome', async ({ browser }) => {
    const ownerContext = await browser.newContext()
    const ownerPage = await ownerContext.newPage()
    const { token } = await loginByApi(ownerPage, 'an.nguyen@example.com')
    const response = await ownerPage.request.get('http://127.0.0.1:8080/api/v1/matches?page=0&size=1', {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(response.ok()).toBeTruthy()
    const history = await response.json()
    const matchId = history.content[0].id

    const outsiderContext = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const outsiderPage = await outsiderContext.newPage()
    const assertNoRuntimeErrors = watchRuntimeErrors(outsiderPage, { ignoredUrls: ['/api/v1/matches/'] })
    await loginByApi(outsiderPage, 'admin@courtly.vn')
    await outsiderPage.goto(`/matches/${matchId}`)
    await expect(outsiderPage.getByText('Không tìm thấy trận đấu')).toBeVisible()
    assertNoRuntimeErrors()
    await outsiderContext.close()

    const mobileContext = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const mobilePage = await mobileContext.newPage()
    const assertMobileNoErrors = watchRuntimeErrors(mobilePage)
    await loginByApi(mobilePage, 'binh.tran@example.com')
    await mobilePage.goto('/performance')
    await expect(mobilePage.getByRole('heading', { name: 'Phong độ của Tran Thi Binh' })).toBeVisible()
    await expect(mobilePage.getByRole('img', { name: 'Biểu đồ thay đổi điểm kỹ năng' })).toBeVisible()
    expect(await mobilePage.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    assertMobileNoErrors()
    await ownerContext.close()
    await mobileContext.close()
  })
})

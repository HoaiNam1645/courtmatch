import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'

const email = (tag) => `e2e-${tag}@example.com`
const PLAYERS = [
  { tag: 'dth-me', name: 'E2E Lịch Sử Chủ', elo: 1300 },
  { tag: 'dth-b', name: 'E2E Lịch Sử Bình', elo: 1200 },
  { tag: 'dth-c', name: 'E2E Lịch Sử Cường', elo: 1100 },
  { tag: 'dth-d', name: 'E2E Lịch Sử Dũng', elo: 1000 },
  { tag: 'dth-other', name: 'E2E Lịch Sử Khác', elo: 1000 },
]

test.describe.serial('2.3.76 player views own double team history', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    for (const p of PLAYERS) {
      createE2eUser({ tag: p.tag, name: p.name })
      runSql(`
        INSERT INTO player_profiles (user_id, gender, playing_style)
        SELECT id, 'male', 'balanced' FROM users WHERE email = '${email(p.tag)}'
        ON CONFLICT (user_id) DO UPDATE SET gender = 'male', playing_style = 'balanced';
        INSERT INTO player_statistics (user_id, rating_score, total_matches)
        SELECT id, ${p.elo}, 30 FROM users WHERE email = '${email(p.tag)}'
        ON CONFLICT (user_id) DO UPDATE SET rating_score = ${p.elo}, total_matches = 30;`)
    }
    runSql(`
      INSERT INTO player_connections (user_id, partner_id, status)
      SELECT me.id, other.id, 'active' FROM users me, users other
      WHERE me.email = '${email('dth-me')}' AND other.email IN ('${email('dth-b')}', '${email('dth-c')}', '${email('dth-d')}')`)
  })
  test.afterAll(deleteE2eUsers)

  async function divide(page) {
    await page.goto('/partners/double-teams')
    for (const tag of ['dth-b', 'dth-c', 'dth-d']) {
      await page.getByTestId(`dt-player-${runSql(`SELECT id FROM users WHERE email = '${email(tag)}'`)}`).click()
    }
    await page.getByTestId('dt-submit').click()
    await expect(page).toHaveURL(/\/partners\/double-teams\/[0-9a-f-]{36}$/)
    return page.url().split('/').pop()
  }

  test('shows an empty state before the first division', async ({ page }) => {
    await loginByApi(page, email('dth-me'))
    await page.goto('/partners/double-teams')
    await page.getByTestId('open-double-team-history').click()
    await expect(page).toHaveURL('/partners/double-teams/history')
    await expect(page.getByTestId('dth-empty')).toContainText('Bạn chưa chia đội lần nào')
  })

  test('lists divisions newest first, opens one again and keeps other players out', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('dth-me'))
    const first = await divide(page)
    runSql(`UPDATE double_team_suggestions SET created_at = created_at - interval '1 hour' WHERE batch_id = '${first}'`)
    const second = await divide(page)

    await page.goto('/partners/double-teams/history')
    await expect(page.getByTestId('dth-pager')).toHaveText('Trang 1/1 · 2 lần chia')
    const items = page.locator('[data-testid^="dth-item-"]')
    await expect(items).toHaveCount(2)
    await expect(items.nth(0)).toHaveAttribute('data-testid', `dth-item-${second}`)
    await expect(items.nth(1)).toHaveAttribute('data-testid', `dth-item-${first}`)
    await expect(items.nth(0).getByTestId('dth-players')).toContainText('E2E Lịch Sử Chủ')
    await expect(items.nth(0)).toContainText('Đôi nam')
    await expect(items.nth(0)).toContainText('đội 1 thắng')
    await shot(page, '2376-player-history')

    await items.nth(1).click()
    await expect(page).toHaveURL(`/partners/double-teams/${first}`)
    await expect(page.getByTestId('dt-options')).toBeVisible()

    await loginByApi(page, email('dth-other'))
    await page.goto('/partners/double-teams/history')
    await expect(page.getByTestId('dth-empty')).toBeVisible()
    assertNoErrors()
  })

  test('fits a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, email('dth-me'))
    await page.goto('/partners/double-teams/history')
    await expect(page.getByTestId('dth-list')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await shot(page, '2376-player-history-phone')
  })
})

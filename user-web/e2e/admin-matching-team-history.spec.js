import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const DAY = '2020-03-10'
const newUser = (options) => createE2eUser(options).split('\n')[0]
let u = {}
let older
let newer
const OLDER = '55555555-5555-4555-8555-555555555551'
const NEWER = '55555555-5555-4555-8555-555555555552'

async function openHistory(page, from = DAY, to = DAY) {
  await loginByApi(page, SEED_ADMIN_EMAIL)
  await page.goto('/admin/matching?tab=team-history')
  await page.getByTestId('mt-preset-custom').click()
  await page.getByLabel('Từ ngày').fill(from)
  await page.getByLabel('Đến ngày').fill(to)
}

test.describe.serial('2.3.76 admin views double team history', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    u = {
      w: newUser({ tag: 'tht-w', name: 'E2E Chia Đội W' }),
      x: newUser({ tag: 'tht-x', name: 'E2E Chia Đội X' }),
      y: newUser({ tag: 'tht-y', name: 'E2E Chia Đội Y' }),
      z: newUser({ tag: 'tht-z', name: 'E2E Chia Đội Z' }),
    }
    const names = { [u.w]: 'E2E Chia Đội W', [u.x]: 'E2E Chia Đội X', [u.y]: 'E2E Chia Đội Y', [u.z]: 'E2E Chia Đội Z' }
    const ids = (...list) => `["${list.join('","')}"]`
    const players = JSON.stringify(Object.entries(names).map(([id, fullName]) => ({ id, fullName, strength: 1000, provisional: false })))
    const pairs = JSON.stringify([{ playerIds: [u.w, u.x], totalScore: 77 }])
    const splits = [[[u.w, u.x], [u.y, u.z]], [[u.w, u.y], [u.x, u.z]], [[u.w, u.z], [u.x, u.y]]]
    const labels = ['Rất cân bằng', 'Cân bằng', 'Chênh lệch']
    const gaps = [10, 40, 80]
    const row = (batch, type, rank, at, requester) => {
      const [t1, t2] = splits[rank - 1]
      const option = JSON.stringify({
        rank, recommended: rank === 1, team1: { playerIds: t1, strength: 1050, compatibility: 70 }, team2: { playerIds: t2, strength: 1040, compatibility: 60 },
        balanceScore: gaps[rank - 1], balanceLabel: labels[rank - 1], team1WinProbability: rank === 1 ? 64 : 50, reason: `Lý do phương án ${rank}`,
      })
      const detail = JSON.stringify({ players: JSON.parse(players), pairs: JSON.parse(pairs), option: JSON.parse(option) }).replace(/'/g, "''")
      return `INSERT INTO double_team_suggestions (model_id, requested_by, batch_id, match_type, rank_no, recommended, scoring_source,
        team1_players, team2_players, team1_strength, team2_strength, balance_score, detail_json, created_at)
        SELECT (SELECT id FROM ai_models WHERE model_type = 'double_team_ranker' LIMIT 1), '${requester}', '${batch}', '${type}', ${rank}, ${rank === 1}, 'baseline',
        '${ids(...t1)}'::jsonb, '${ids(...t2)}'::jsonb, 1050, 1040, ${gaps[rank - 1]}, '${detail}'::jsonb, '${at}';`
    }
    older = [1, 2, 3].map((rank) => row(OLDER, 'mens', rank, `${DAY} 08:00+07`, u.w)).join('\n')
    newer = [1, 2, 3].map((rank) => row(NEWER, 'mixed', rank, `${DAY} 09:00+07`, u.x)).join('\n')
    runSql(`
      ${older}
      ${newer}
      WITH m AS (INSERT INTO matches (match_type, scheduled_at, status, created_by) VALUES ('double', '2020-03-12 18:00+07', 'completed', '${u.w}') RETURNING id),
      r AS (INSERT INTO match_results (match_id, winning_team) SELECT id, 1 FROM m)
      INSERT INTO match_players (match_id, user_id, team_no)
      SELECT m.id, p.id, p.team FROM m, (VALUES ('${u.y}'::uuid, 1), ('${u.z}'::uuid, 1), ('${u.w}'::uuid, 2), ('${u.x}'::uuid, 2)) AS p(id, team);
    `)
  })
  test.afterAll(() => {
    runSql(`DELETE FROM matches WHERE created_by IN (SELECT id FROM users WHERE email LIKE 'e2e-%@example.com')`)
    deleteE2eUsers()
  })

  test('lists divisions with the recommended option and the real-match reconciliation', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openHistory(page)
    await expect(page.getByTestId('th-pager')).toHaveText('Trang 1/1 · 2 lần chia')
    const rows = page.locator('[data-testid^="th-row-"]')
    await expect(rows.nth(0)).toHaveAttribute('data-testid', `th-row-${NEWER}`)
    await expect(rows.nth(1)).toHaveAttribute('data-testid', `th-row-${OLDER}`)
    await expect(rows.nth(0)).toContainText('Đôi nam nữ')
    await expect(rows.nth(0)).toContainText('Rất cân bằng')
    await expect(rows.nth(0)).toContainText('3 phương án')
    await expect(rows.nth(0)).toContainText('E2E Chia Đội Z')
    // Cả hai lần chia đều có một phương án trùng cách chia của trận thật (y,z thắng) -> phương án 1 khuyến nghị sai.
    await expect(rows.nth(1)).toContainText('Đã chơi phương án 1 (khuyến nghị) · Dự đoán sai')
    await shot(page, '2376-admin-team-history')
    assertNoErrors()
  })

  test('filters by type, reconciliation state and keyword', async ({ page }) => {
    await openHistory(page)
    await page.getByTestId('th-type').selectOption('mixed')
    await expect(page.locator('[data-testid^="th-row-"]')).toHaveCount(1)
    await expect(page.getByTestId(`th-row-${NEWER}`)).toBeVisible()
    await page.getByTestId('th-type').selectOption('all')
    await page.getByTestId('th-played').selectOption('unplayed')
    await expect(page.getByText('Không có lần chia đội nào')).toBeVisible()
    await page.getByTestId('th-played').selectOption('played')
    await expect(page.locator('[data-testid^="th-row-"]')).toHaveCount(2)
    await page.getByLabel('Tìm người chơi').fill('e2e-tht-x')
    await expect(page.locator('[data-testid^="th-row-"]')).toHaveCount(2) // x nằm trong cả hai lần chia
    await page.getByLabel('Tìm người chơi').fill('khong-ton-tai-xyz')
    await expect(page.getByText('Không có lần chia đội nào')).toBeVisible()
  })

  test('opens the detail with every option, player strengths and pair compatibility', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openHistory(page)
    await page.getByTestId(`th-row-${OLDER}`).click()
    await expect(page.getByTestId('th-drawer')).toBeVisible()
    await expect(page.getByTestId('th-options').locator('li')).toHaveCount(3)
    await expect(page.getByTestId('th-option-1')).toContainText('khuyến nghị')
    await expect(page.getByTestId('th-option-1')).toContainText('E2E Chia Đội W + E2E Chia Đội X')
    await expect(page.getByTestId('th-option-3')).toContainText('Chênh lệch · chênh 80')
    await expect(page.getByTestId('th-option-2')).toContainText('Lý do phương án 2')
    await expect(page.getByTestId('th-players').locator('li')).toHaveCount(4)
    await expect(page.getByTestId('th-pairs')).toContainText('77')
    await expect(page.getByTestId('th-reconciliation')).toContainText('Dự đoán sai')
    await shot(page, '2376-admin-team-drawer')
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('th-drawer')).toHaveCount(0)
    assertNoErrors()
  })

  test('is empty for a quiet range and fits a phone screen', async ({ page }) => {
    await openHistory(page, '2019-01-01', '2019-01-02')
    await expect(page.getByText('Không có lần chia đội nào')).toBeVisible()
    await page.setViewportSize({ width: 375, height: 800 })
    await openHistory(page)
    await expect(page.getByTestId('th-pager')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  })
})

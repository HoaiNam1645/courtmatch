import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const DAY = '2020-03-10'
const newUser = (options) => createE2eUser(options).split('\n')[0]
let u = {}

function cleanup() {
  runSql(`DELETE FROM matches WHERE created_by IN (SELECT id FROM users WHERE email LIKE 'e2e-%@example.com')`)
  deleteE2eUsers()
}

async function openAccuracy(page, from = DAY, to = DAY) {
  await loginByApi(page, SEED_ADMIN_EMAIL)
  await page.goto('/admin/matching')
  await page.getByTestId('mt-preset-custom').click()
  await page.getByLabel('Từ ngày').fill(from)
  await page.getByLabel('Đến ngày').fill(to)
}

test.describe.serial('2.3.71 admin evaluates double team accuracy', () => {
  test.beforeAll(() => {
    cleanup()
    u = {
      w: newUser({ tag: 'tacc-w', name: 'E2E Đội W' }),
      x: newUser({ tag: 'tacc-x', name: 'E2E Đội X' }),
      y: newUser({ tag: 'tacc-y', name: 'E2E Đội Y' }),
      z: newUser({ tag: 'tacc-z', name: 'E2E Đội Z' }),
    }
    const ids = (...list) => `'["${list.join('","')}"]'`
    const option = (batch, rank, recommended, p, gap, t1, t2) => `INSERT INTO double_team_suggestions
      (model_id, requested_by, batch_id, match_type, rank_no, recommended, scoring_source, team1_players, team2_players,
       team1_strength, team2_strength, balance_score, detail_json, created_at)
      SELECT (SELECT id FROM ai_models WHERE model_type = 'double_team_ranker' LIMIT 1), '${u.w}', '${batch}', 'mens', ${rank}, ${recommended}, 'baseline',
       ${t1}::jsonb, ${t2}::jsonb, 1000, 1000, ${gap}, '{"option":{"team1WinProbability":${p}}}'::jsonb, '${DAY} 09:00+07';`
    const batch = '11111111-1111-4111-8111-111111111111'
    runSql(`
      ${option(batch, 1, true, 64, 100, ids(u.w, u.x), ids(u.y, u.z))}
      ${option(batch, 2, false, 50, 0, ids(u.w, u.y), ids(u.x, u.z))}
      ${option(batch, 3, false, 40, 60, ids(u.w, u.z), ids(u.x, u.y))}
      WITH m AS (INSERT INTO matches (match_type, scheduled_at, status, created_by) VALUES ('double', '2020-03-12 18:00+07', 'completed', '${u.w}') RETURNING id),
      r AS (INSERT INTO match_results (match_id, winning_team) SELECT id, 2 FROM m),
      g AS (INSERT INTO match_games (match_id, game_no, team1_score, team2_score) SELECT id, 1, 15, 21 FROM m)
      INSERT INTO match_players (match_id, user_id, team_no)
      SELECT m.id, p.id, p.team FROM m, (VALUES ('${u.y}'::uuid, 1), ('${u.z}'::uuid, 1), ('${u.w}'::uuid, 2), ('${u.x}'::uuid, 2)) AS p(id, team);
    `)
  })
  test.afterAll(cleanup)

  test('reconciles the proposal with the real match and shows accuracy, Brier and the comparison', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openAccuracy(page)
    await expect(page.getByTestId('ta-batches')).toHaveText('1')
    await expect(page.getByTestId('ta-reconciled')).toHaveText('1')
    await expect(page.getByTestId('ta-accuracy')).toHaveText('100%')
    await expect(page.getByTestId('ta-brier')).toHaveText('0,13')
    await expect(page.getByTestId('ta-insufficient')).toContainText('Chưa đủ dữ liệu (1/30 mẫu)')
    await expect(page.getByTestId('ta-recommended')).toHaveAttribute('data-reconciled', '1')
    await expect(page.getByTestId('ta-recommended-accuracy')).toHaveText('100%')
    await expect(page.getByTestId('ta-others')).toHaveAttribute('data-reconciled', '0')
    await expect(page.getByTestId('ta-others-accuracy')).toHaveText('—')
    await expect(page.getByTestId('ta-samples').locator('[data-correct="true"]')).toHaveCount(1)
    await expect(page.getByTestId('ta-samples')).toContainText('đội 1 thắng 64%')
    await page.getByTestId('team-accuracy').scrollIntoViewIfNeeded()
    await page.getByTestId('ta-samples').scrollIntoViewIfNeeded()
    await shot(page, '2371-team-accuracy-desktop')
    assertNoErrors()
  })

  test('says nothing can be evaluated when no match used the proposed split', async ({ page }) => {
    runSql(`UPDATE matches SET status = 'scheduled' WHERE created_by = '${u.w}'`)
    try {
      await openAccuracy(page)
      await expect(page.getByTestId('ta-batches')).toHaveText('1')
      await expect(page.getByTestId('ta-reconciled')).toHaveText('0')
      await expect(page.getByTestId('ta-accuracy')).toHaveText('—')
      await expect(page.getByTestId('ta-empty')).toContainText('Chưa có trận nào')
    } finally {
      runSql(`UPDATE matches SET status = 'completed' WHERE created_by = '${u.w}'`)
    }
  })

  test('is empty for a range without proposals and fits a phone screen', async ({ page }) => {
    await openAccuracy(page, '2019-01-01', '2019-01-02')
    await expect(page.getByTestId('ta-batches')).toHaveText('0')
    await expect(page.getByTestId('ta-brier')).toHaveText('—')
    await page.setViewportSize({ width: 375, height: 800 })
    await openAccuracy(page)
    await expect(page.getByTestId('ta-reconciled')).toHaveText('1')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await page.getByTestId('team-accuracy').scrollIntoViewIfNeeded()
    await shot(page, '2371-team-accuracy-phone')
  })
})

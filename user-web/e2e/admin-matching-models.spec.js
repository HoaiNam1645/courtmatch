import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const DAY = '2020-03-10'
const MODEL_A = '22222222-2222-4222-8222-222222222221'
const MODEL_B = '22222222-2222-4222-8222-222222222222'
const MODEL_T = '22222222-2222-4222-8222-222222222223'
const newUser = (options) => createE2eUser(options).split('\n')[0]
let u = {}

function cleanup() {
  deleteE2eUsers()
  runSql(`DELETE FROM ai_models WHERE model_version LIKE 'e2e-model-%'`)
}

async function openModels(page, from = DAY, to = DAY) {
  await loginByApi(page, SEED_ADMIN_EMAIL)
  await page.goto('/admin/matching')
  await page.getByTestId('mt-tab-models').click()
  await expect(page).toHaveURL(/tab=models/)
  await page.getByTestId('mt-preset-custom').click()
  await page.getByLabel('Từ ngày').fill(from)
  await page.getByLabel('Đến ngày').fill(to)
}

test.describe.serial('2.3.72 + 2.3.73 admin analyses AI models and service performance', () => {
  test.beforeAll(() => {
    cleanup()
    u = {
      a: newUser({ tag: 'mdl-a', name: 'E2E Model A' }),
      b: newUser({ tag: 'mdl-b', name: 'E2E Model B' }),
      c: newUser({ tag: 'mdl-c', name: 'E2E Model C' }),
      d: newUser({ tag: 'mdl-d', name: 'E2E Model D' }),
      e: newUser({ tag: 'mdl-e', name: 'E2E Model E' }),
    }
    const suggest = (model, to, total, skill, loc, time, style) => `INSERT INTO partner_suggestions
      (model_id, user_id, suggested_user_id, total_score, skill_score, location_score, time_score, style_score, scoring_source, created_at)
      VALUES ('${model}', '${u.a}', '${to}', ${total}, ${skill}, ${loc}, ${time}, ${style}, 'baseline', '${DAY} 09:00+07');`
    const pairs = '[{"timeScore":80,"locationScore":70},{"locationScore":70},{"timeScore":80},{},{"timeScore":80,"locationScore":70},{"timeScore":80,"locationScore":70}]'
    const teamRow = (batch, rank, gap) => `INSERT INTO double_team_suggestions
      (model_id, requested_by, batch_id, match_type, rank_no, recommended, scoring_source, team1_players, team2_players, balance_score, detail_json, created_at)
      VALUES ('${MODEL_T}', '${u.a}', '${batch}', 'mens', ${rank}, ${rank === 1}, 'baseline', '[]', '[]', ${gap}, '{"pairs":${pairs}}', '${DAY} 09:00+07');`
    runSql(`
      INSERT INTO ai_models (id, model_type, model_version, status) VALUES
        ('${MODEL_A}', 'baseline_ranker', 'e2e-model-a', 'ready'),
        ('${MODEL_B}', 'baseline_ranker', 'e2e-model-b', 'ready'),
        ('${MODEL_T}', 'double_team_ranker', 'e2e-model-t', 'ready');
      ${suggest(MODEL_A, u.b, 90, 100, 80, 90, 100)}
      ${suggest(MODEL_A, u.c, 50, 60, 40, 'NULL', 100)}
      ${suggest(MODEL_A, u.d, 10, 20, 10, 'NULL', 50)}
      ${suggest(MODEL_B, u.e, 70, 70, 70, 70, 70)}
      INSERT INTO partner_requests (requester_id, receiver_id, status, created_at, responded_at)
        VALUES ('${u.a}', '${u.b}', 'accepted', '2020-03-11 09:00+07', '2020-03-11 10:00+07');
      ${teamRow('33333333-3333-4333-8333-333333333331', 1, 10)}
      ${teamRow('33333333-3333-4333-8333-333333333331', 2, 90)}
      ${teamRow('33333333-3333-4333-8333-333333333332', 1, 20)}
      ${teamRow('33333333-3333-4333-8333-333333333332', 2, 90)}
      INSERT INTO matching_runs (run_type, user_id, scoring_source, result_count, duration_ms, status, created_at)
        SELECT 'partner_search', '${u.a}', 'baseline', CASE WHEN i <= 18 THEN 5 ELSE 0 END,
               CASE WHEN i <= 18 THEN i * 100 ELSE 50 END, CASE WHEN i <= 18 THEN 'success' ELSE 'failed' END, '${DAY} 09:00+07'
        FROM generate_series(1, 20) AS i;
    `)
  })
  test.afterAll(cleanup)

  test('compares models side by side with exact scoring numbers', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openModels(page)
    await expect(page.getByTestId('model-analysis')).toBeVisible()
    await expect(page.getByTestId('ma-model-e2e-model-a')).toBeVisible()
    await expect(page.getByTestId('ma-model-e2e-model-b')).toBeVisible()
    await expect(page.getByTestId('ma-scored-e2e-model-a')).toHaveText('3')
    await expect(page.getByTestId('ma-avg-total-e2e-model-a')).toHaveText('50')
    await expect(page.getByTestId('ma-avg-skill-e2e-model-a')).toHaveText('60')
    await expect(page.getByTestId('ma-avg-location-e2e-model-a')).toHaveText('43,3')
    await expect(page.getByTestId('ma-avg-time-e2e-model-a')).toHaveText('90')
    await expect(page.getByTestId('ma-missing-time-e2e-model-a')).toHaveText('66,7%')
    await expect(page.getByTestId('ma-acceptance-e2e-model-a')).toHaveText('100% (1/1)')
    await expect(page.getByTestId('ma-scored-e2e-model-b')).toHaveText('1')
    await expect(page.getByTestId('ma-acceptance-e2e-model-b')).toHaveText('— (0/0)')
    await expect(page.getByTestId('ma-batches-e2e-model-t')).toHaveText('2')
    await expect(page.getByTestId('ma-avg-gap-e2e-model-t')).toHaveText('15')
    await expect(page.getByTestId('ma-team-missing-time-e2e-model-t')).toHaveText('33,3%')
    await expect(page.getByTestId('ma-scored-e2e-model-t')).toHaveText('—')
    const bucket = page.getByTestId('ma-distribution').first().locator('[data-label="80-100"]')
    await expect(bucket).toHaveAttribute('data-count', '1')
    await shot(page, '2372-models-desktop')
    assertNoErrors()
  })

  test('source filter hides models that have no scores from that source', async ({ page }) => {
    await openModels(page)
    await expect(page.getByTestId('ma-model-e2e-model-a')).toBeVisible()
    await page.getByTestId('mt-source').selectOption('ai')
    await expect(page.getByTestId('ma-model-e2e-model-a')).toHaveCount(0)
    await page.getByTestId('mt-source').selectOption('')
    await expect(page.getByTestId('ma-model-e2e-model-a')).toBeVisible()
  })

  test('shows processing times, failure rate, daily chart and threshold alerts', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openModels(page)
    await expect(page.getByTestId('matching-performance')).toBeVisible()
    await expect(page.getByTestId('mp-partner-runs')).toHaveText('20')
    await expect(page.getByTestId('mp-partner-failure')).toHaveText('10%')
    await expect(page.getByTestId('mp-partner-p50')).toHaveText('850 ms')
    await expect(page.getByTestId('mp-partner-p95')).toHaveText('1.705 ms')
    await expect(page.getByTestId('mp-partner-max')).toHaveText('1.800 ms')
    await expect(page.getByTestId('mp-partner-baseline')).toHaveText('100%')
    await expect(page.getByTestId('mp-team-runs')).toHaveText('0')
    await expect(page.getByTestId('mp-team-p50')).toHaveText('—')
    await expect(page.getByTestId(`mp-bar-${DAY}`)).toHaveAttribute('data-value', '20')
    await expect(page.getByTestId(`mp-bar-${DAY}`)).toHaveAttribute('data-failed', '2')
    await expect(page.getByTestId('mp-alert-high_failure_rate')).toHaveAttribute('data-severity', 'warning')
    await expect(page.getByTestId('mp-alert-slow_response')).toBeVisible()
    await expect(page.getByTestId('mp-alert-no_ai_service')).toHaveAttribute('data-severity', 'info')
    await expect(page.getByTestId('mp-ai-status')).toContainText('Chưa có AI service')
    await expect(page.getByTestId('mp-model-e2e-model-a')).toContainText('Chưa có chỉ số huấn luyện')
    await page.getByTestId('matching-performance').scrollIntoViewIfNeeded()
    await shot(page, '2373-performance-desktop')
    assertNoErrors()
  })

  test('shows a clean state for a quiet range and fits a phone screen', async ({ page }) => {
    await openModels(page, '2019-01-01', '2019-01-02')
    await expect(page.getByTestId('mp-partner-runs')).toHaveText('0')
    await expect(page.getByTestId('mp-partner-p95')).toHaveText('—')
    await expect(page.getByTestId('mp-partner-failure')).toHaveText('—')
    await expect(page.getByTestId('mp-alert-high_failure_rate')).toHaveCount(0)
    await page.setViewportSize({ width: 375, height: 800 })
    await openModels(page)
    await expect(page.getByTestId('ma-scored-e2e-model-a')).toHaveText('3')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    const card = await page.getByTestId('model-analysis').evaluate((el) => el.closest('section').getBoundingClientRect().right)
    expect(card).toBeLessThanOrEqual(376)
    await shot(page, '2372-models-phone')
  })
})

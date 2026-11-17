import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const newUser = (options) => createE2eUser(options).split('\n')[0]
const OUTCOMES = ['accepted', 'rejected', null, 'expired', 'pending', 'cancelled']
let a
let targets = []
let detailId

async function openHistory(page, from = '2020-03-10', to = '2020-03-10') {
  await loginByApi(page, SEED_ADMIN_EMAIL)
  await page.goto('/admin/matching?tab=partner-history')
  await page.getByTestId('mt-preset-custom').click()
  await page.getByLabel('Từ ngày').fill(from)
  await page.getByLabel('Đến ngày').fill(to)
}

test.describe.serial('2.3.75 admin views partner matching history', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    a = newUser({ tag: 'hst-a', name: 'E2E Lịch Sử A' })
    targets = OUTCOMES.map((_, index) => newUser({ tag: `hst-t${index}`, name: `E2E Lịch Sử T${index}` }))
    const extra = newUser({ tag: 'hst-x', name: 'E2E Lịch Sử X' })
    const statements = []
    OUTCOMES.forEach((outcome, index) => {
      const hour = String(8 + index).padStart(2, '0')
      statements.push(`INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, rank_no, scoring_source, created_at)
        VALUES ('${a}', '${targets[index]}', ${90 - index * 10}, ${index + 1}, 'baseline', '2020-03-10 ${hour}:00+07');`)
      if (outcome) {
        statements.push(`INSERT INTO partner_requests (requester_id, receiver_id, status, created_at, responded_at)
          VALUES ('${a}', '${targets[index]}', '${outcome}', '2020-03-11 09:00+07', ${['accepted', 'rejected'].includes(outcome) ? "'2020-03-11 10:00+07'" : 'NULL'});`)
      }
    })
    statements.push(`INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, rank_no, scoring_source, created_at)
      SELECT '${a}', '${extra}', 40, 1, 'baseline', '2020-03-10 14:00+07'::timestamptz + (i || ' minutes')::interval FROM generate_series(0, 19) AS i;`)
    runSql(statements.join('\n'))
    detailId = runSql(`
      WITH run AS (INSERT INTO matching_runs (run_type, user_id, scoring_source, result_count, duration_ms, status, params_json, created_at)
        VALUES ('partner_search', '${a}', 'baseline', 7, 123, 'success', '{"durationMinutes": 90}', '2020-03-10 07:00+07') RETURNING id)
      INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, skill_score, location_score, time_score, style_score, reason_json, search_id, rank_no, scoring_source, created_at)
      SELECT '${a}', '${extra}', 71.5, 90, 60, NULL, 50, '{"ratingGap": 20, "provisional": false}'::jsonb, run.id, 3, 'baseline', '2020-03-10 07:00+07' FROM run
      RETURNING id`).split('\n')[0]
  })
  test.afterAll(deleteE2eUsers)

  test('lists suggestions newest first with their outcome and paginates', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openHistory(page)
    await expect(page.getByTestId('ph-pager')).toHaveText('Trang 1/2 · 27 gợi ý')
    await expect(page.locator('[data-testid^="ph-row-"]')).toHaveCount(20)
    await page.getByRole('button', { name: 'Trang sau' }).click()
    await expect(page.getByTestId('ph-pager')).toHaveText('Trang 2/2 · 27 gợi ý')
    const rows = page.locator('[data-testid^="ph-row-"]')
    await expect(rows).toHaveCount(7)
    // Trang 2: sáu gợi ý có kết quả (13h → 08h) rồi tới gợi ý lúc 07h.
    const outcomes = await rows.evaluateAll((items) => items.map((item) => item.getAttribute('data-outcome')))
    expect(outcomes).toEqual(['cancelled', 'pending', 'expired', 'none', 'rejected', 'accepted', 'none'])
    await shot(page, '2375-history-page2')
    assertNoErrors()
  })

  test('filters by outcome and by keyword, and resets to the first page', async ({ page }) => {
    await openHistory(page)
    await page.getByRole('button', { name: 'Trang sau' }).click()
    await page.getByTestId('ph-outcome').selectOption('accepted')
    await expect(page.getByTestId('ph-pager')).toHaveText('Trang 1/1 · 1 gợi ý')
    await expect(page.locator('[data-testid^="ph-row-"]')).toHaveCount(1)
    await expect(page.locator('[data-testid^="ph-row-"]').first()).toHaveAttribute('data-outcome', 'accepted')
    await page.getByTestId('ph-outcome').selectOption('expired')
    await expect(page.locator('[data-testid^="ph-row-"]').first()).toHaveAttribute('data-outcome', 'expired')
    await page.getByTestId('ph-outcome').selectOption('all')
    await page.getByLabel('Tìm người chơi').fill('e2e-hst-t3')
    await expect(page.locator('[data-testid^="ph-row-"]')).toHaveCount(1)
    await expect(page.locator('[data-testid^="ph-row-"]').first()).toContainText('E2E Lịch Sử T3')
    await page.getByLabel('Tìm người chơi').fill('khong-ton-tai-xyz')
    await expect(page.getByText('Không có gợi ý nào')).toBeVisible()
  })

  test('opens a detail drawer with component scores, reason and the originating search', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openHistory(page)
    await page.getByRole('button', { name: 'Trang sau' }).click()
    await page.getByTestId(`ph-row-${detailId}`).click()
    const drawer = page.getByTestId('ph-drawer')
    await expect(drawer).toBeVisible()
    await expect(page.getByTestId('ph-score-total')).toHaveText('71,5')
    await expect(page.getByTestId('ph-score-skill')).toHaveText('90')
    await expect(page.getByTestId('ph-score-location')).toHaveText('60')
    await expect(page.getByTestId('ph-score-time')).toHaveText('Thiếu dữ liệu')
    await expect(page.getByTestId('ph-score-style')).toHaveText('50')
    await expect(page.getByTestId('ph-reason')).toContainText('Chênh điểm trình độ')
    await expect(page.getByTestId('ph-request')).toContainText('Chưa có yêu cầu')
    await expect(page.getByTestId('ph-search')).toContainText('7 kết quả · xử lý 123 ms · 90 phút')
    await shot(page, '2375-history-drawer')
    await page.keyboard.press('Escape')
    await expect(drawer).toHaveCount(0)

    await page.getByTestId('ph-outcome').selectOption('accepted')
    await page.locator('[data-testid^="ph-row-"]').first().click()
    await expect(page.getByTestId('ph-request')).toContainText('phản hồi lúc')
    assertNoErrors()
  })

  test('shows an empty state for a quiet range and fits a phone screen', async ({ page }) => {
    await openHistory(page, '2019-01-01', '2019-01-02')
    await expect(page.getByText('Không có gợi ý nào')).toBeVisible()
    await page.setViewportSize({ width: 375, height: 800 })
    await openHistory(page)
    await expect(page.getByTestId('ph-pager')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await shot(page, '2375-history-phone')
  })
})

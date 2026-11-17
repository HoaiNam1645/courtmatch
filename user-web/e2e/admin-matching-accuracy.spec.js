import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const DAY = '2020-03-10'
const emailOf = (tag) => `e2e-${tag}@example.com`
let ids = {}
const newUser = (options) => createE2eUser(options).split('\n')[0]

function cleanup() {
  runSql(`DELETE FROM matches WHERE created_by IN (SELECT id FROM users WHERE email LIKE 'e2e-%@example.com')`)
  deleteE2eUsers()
}

async function openAccuracy(page, from = DAY, to = DAY) {
  await loginByApi(page, SEED_ADMIN_EMAIL)
  await page.goto('/admin')
  await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Ghép cặp' }).click()
  await expect(page).toHaveURL(/\/admin\/matching/)
  await expect(page.getByRole('heading', { name: 'Phân tích ghép cặp' })).toBeVisible()
  await page.getByTestId('mt-tab-accuracy').click()
  await setRange(page, from, to)
}

async function setRange(page, from, to) {
  await page.getByTestId('mt-preset-custom').click()
  await page.getByLabel('Từ ngày').fill(from)
  await page.getByLabel('Đến ngày').fill(to)
}

test.describe.serial('2.3.70 admin evaluates partner matching accuracy', () => {
  test.beforeAll(() => {
    cleanup()
    ids = {
      a: newUser({ tag: 'acc-a', name: 'E2E Chính Xác A' }),
      b: newUser({ tag: 'acc-b', name: 'E2E Chính Xác B' }),
      c: newUser({ tag: 'acc-c', name: 'E2E Chính Xác C' }),
      d: newUser({ tag: 'acc-d', name: 'E2E Chính Xác D' }),
    }
    const suggest = (to, rank, score) => `INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, rank_no, scoring_source, created_at)
      VALUES ('${ids.a}', '${to}', ${score}, ${rank}, 'baseline', '${DAY} 09:00+07');`
    runSql(`
      ${suggest(ids.b, 1, 90)}
      ${suggest(ids.c, 2, 55)}
      ${suggest(ids.d, 3, 20)}
      INSERT INTO partner_requests (requester_id, receiver_id, status, created_at, responded_at)
      VALUES ('${ids.a}', '${ids.b}', 'accepted', '2020-03-11 09:00+07', '2020-03-11 10:00+07'),
             ('${ids.a}', '${ids.c}', 'rejected', '2020-03-11 09:00+07', '2020-03-11 10:00+07');
      WITH m AS (INSERT INTO matches (match_type, scheduled_at, status, created_by) VALUES ('single', '2020-03-15 18:00+07', 'completed', '${ids.a}') RETURNING id)
      INSERT INTO match_players (match_id, user_id, team_no)
      SELECT m.id, u.id, u.team FROM m, (VALUES ('${ids.a}'::uuid, 1), ('${ids.b}'::uuid, 2)) AS u(id, team);
    `)
  })
  test.afterAll(cleanup)

  test('shows outcome rates of suggestions and flags the small sample', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openAccuracy(page)
    await expect(page.getByTestId('pa-suggestions')).toHaveText('3')
    await expect(page.getByTestId('pa-request-rate')).toHaveText('66,7%')
    await expect(page.getByTestId('pa-acceptance-rate')).toHaveText('50%')
    await expect(page.getByTestId('pa-played-rate')).toHaveText('100%')
    await expect(page.getByTestId('pa-insufficient')).toContainText('Chưa đủ dữ liệu (3/30 mẫu)')
    await expect(page.getByTestId('pa-breakdown')).toContainText('Từ chối: 1')

    await expect(page.getByTestId('pa-rank-top1')).toHaveAttribute('data-suggestions', '1')
    await expect(page.getByTestId('pa-rank-top1-rate')).toHaveText('100%')
    await expect(page.getByTestId('pa-rank-top3')).toHaveAttribute('data-suggestions', '3')
    await expect(page.getByTestId('pa-rank-top3-rate')).toHaveText('50%')
    await expect(page.getByTestId('pa-score-0-40')).toHaveAttribute('data-suggestions', '1')
    await expect(page.getByTestId('pa-score-0-40-rate')).toHaveText('—')
    await expect(page.getByTestId('pa-score-70-100')).toHaveAttribute('data-accepted', '1')
    await shot(page, '2370-accuracy-desktop')
    assertNoErrors()
  })

  test('shows dashes instead of 0% when there is nothing to measure', async ({ page }) => {
    await openAccuracy(page, '2019-01-01', '2019-01-02')
    await expect(page.getByTestId('pa-suggestions')).toHaveText('0')
    await expect(page.getByTestId('pa-request-rate')).toHaveText('—')
    await expect(page.getByTestId('pa-acceptance-rate')).toHaveText('—')
    await expect(page.getByTestId('pa-played-rate')).toHaveText('—')
  })

  test('source filter narrows the measurement and a bad range is rejected', async ({ page }) => {
    await openAccuracy(page)
    await expect(page.getByTestId('pa-suggestions')).toHaveText('3')
    await page.getByTestId('mt-source').selectOption('ai')
    await expect(page.getByTestId('pa-suggestions')).toHaveText('0')
    await page.getByTestId('mt-source').selectOption('baseline')
    await expect(page.getByTestId('pa-suggestions')).toHaveText('3')

    await page.getByLabel('Từ ngày').fill('2020-03-12')
    await expect(page.getByTestId('mt-range-error')).toBeVisible()
    await expect(page.getByTestId('partner-accuracy')).toHaveCount(0)
  })

  test('is blocked for players and works on a phone screen', async ({ page }) => {
    await loginByApi(page, emailOf('acc-a'))
    await page.goto('/admin/matching')
    await expect(page.getByText('Bạn không có quyền truy cập')).toBeVisible()

    await page.setViewportSize({ width: 375, height: 800 })
    await openAccuracy(page)
    await expect(page.getByTestId('pa-suggestions')).toHaveText('3')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await shot(page, '2370-accuracy-phone')
  })
})

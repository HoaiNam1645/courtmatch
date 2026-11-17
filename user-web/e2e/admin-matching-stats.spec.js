import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const newUser = (options) => createE2eUser(options).split('\n')[0]
let u = {}

async function openStats(page, from = '2020-03-09', to = '2020-03-12') {
  await loginByApi(page, SEED_ADMIN_EMAIL)
  await page.goto('/admin/matching')
  await expect(page.getByTestId('matching-stats')).toBeVisible()
  await page.getByTestId('mt-preset-custom').click()
  await page.getByLabel('Từ ngày').fill(from)
  await page.getByLabel('Đến ngày').fill(to)
}

test.describe.serial('2.3.74 admin views matching performance statistics', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    u = {
      a: newUser({ tag: 'mst-a', name: 'E2E Thống Kê A' }),
      b: newUser({ tag: 'mst-b', name: 'E2E Thống Kê B' }),
      c: newUser({ tag: 'mst-c', name: 'E2E Thống Kê C' }),
      d: newUser({ tag: 'mst-d', name: 'E2E Thống Kê D' }),
    }
    const run = (type, status, at) => `INSERT INTO matching_runs (run_type, user_id, status, created_at) VALUES ('${type}', '${u.a}', '${status}', '${at}');`
    const request = (from, to, status, at, responded) => `INSERT INTO partner_requests (requester_id, receiver_id, status, created_at, responded_at)
      VALUES ('${from}', '${to}', '${status}', '${at}', ${responded ? `'${responded}'` : 'NULL'});`
    runSql(`
      ${run('partner_search', 'success', '2020-03-09 09:00+07')}
      ${run('partner_search', 'success', '2020-03-09 10:00+07')}
      ${run('partner_search', 'failed', '2020-03-09 11:00+07')}
      ${run('partner_search', 'success', '2020-03-10 09:00+07')}
      ${run('double_team', 'success', '2020-03-10 09:00+07')}
      INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, created_at) VALUES
        ('${u.a}', '${u.b}', 50, '2020-03-09 09:00+07'), ('${u.a}', '${u.c}', 50, '2020-03-09 09:00+07'), ('${u.a}', '${u.d}', 50, '2020-03-09 09:00+07');
      ${request(u.a, u.b, 'accepted', '2020-03-09 12:00+07', '2020-03-09 12:30+07')}
      ${request(u.a, u.c, 'rejected', '2020-03-09 12:00+07', '2020-03-09 13:30+07')}
      ${request(u.a, u.d, 'expired', '2020-03-10 09:00+07', null)}
      ${request(u.b, u.c, 'cancelled', '2020-03-10 09:00+07', null)}
      ${request(u.a, u.d, 'pending', '2020-03-12 09:00+07', null)}
      INSERT INTO player_connections (user_id, partner_id, created_at) VALUES
        ('${u.a}', '${u.b}', '2020-03-12 10:00+07'), ('${u.b}', '${u.a}', '2020-03-12 10:00+07');
    `)
  })
  test.afterAll(deleteE2eUsers)

  test('shows exact totals, breakdown and per-day bars', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openStats(page)
    await expect(page.getByTestId('ms-searches')).toHaveText('3')
    await expect(page.getByTestId('ms-requests')).toHaveText('5')
    await expect(page.getByTestId('ms-requests').locator('xpath=following-sibling::p')).toContainText('1 chấp nhận · 1 từ chối · 1 hết hạn · 1 hủy')
    await expect(page.getByTestId('ms-connections')).toHaveText('1')
    await expect(page.getByTestId('ms-team-runs')).toHaveText('1')
    await expect(page.getByTestId('ms-response')).toHaveText('1 giờ')
    const bars = page.locator('[data-testid^="ms-bar-"]')
    await expect(bars).toHaveCount(4)
    await expect(page.getByTestId('ms-bar-2020-03-09')).toHaveAttribute('data-value', '2')
    await expect(page.getByTestId('ms-bar-2020-03-10')).toHaveAttribute('data-value', '2')
    await expect(page.getByTestId('ms-bar-2020-03-11')).toHaveAttribute('data-value', '0')
    await expect(page.getByTestId('ms-bar-2020-03-12')).toHaveAttribute('data-value', '1')
    await shot(page, '2374-stats-desktop')
    assertNoErrors()
  })

  test('metric, week and month views regroup the same data', async ({ page }) => {
    await openStats(page)
    await page.getByLabel('Chỉ số').selectOption('accepted')
    await expect(page.getByTestId('ms-total')).toContainText('Yêu cầu được chấp nhận: 1')
    await expect(page.getByTestId('ms-bar-2020-03-09')).toHaveAttribute('data-value', '1')
    await page.getByLabel('Chỉ số').selectOption('requests')
    await page.getByTestId('ms-granularity-week').click()
    await expect(page.getByTestId('ms-granularity-week')).toHaveAttribute('aria-pressed', 'true')
    await expect(page.locator('[data-testid^="ms-bar-"]')).toHaveCount(1)
    await expect(page.getByTestId('ms-bar-2020-03-09')).toHaveAttribute('data-value', '5')
    await page.getByTestId('ms-granularity-month').click()
    await expect(page.getByTestId('ms-bar-2020-03-01')).toHaveAttribute('data-value', '5')
  })

  test('an empty range shows zeros, a dash for response time and an empty message', async ({ page }) => {
    await openStats(page, '2019-01-01', '2019-01-03')
    await expect(page.getByTestId('ms-searches')).toHaveText('0')
    await expect(page.getByTestId('ms-requests')).toHaveText('0')
    await expect(page.getByTestId('ms-response')).toHaveText('—')
    await expect(page.getByTestId('ms-empty')).toBeVisible()
    await expect(page.locator('[data-testid^="ms-bar-"]')).toHaveCount(3)
  })

  test('fits a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await openStats(page)
    await expect(page.getByTestId('ms-searches')).toHaveText('3')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await page.getByTestId('ms-chart').scrollIntoViewIfNeeded()
    await shot(page, '2374-stats-phone')
  })
})

import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const num = (sql) => Number(runSql(sql))
const vi = (value) => value.toLocaleString('vi-VN')

test.describe.serial('2.3.38 admin views system statistics', () => {
  test.beforeAll(deleteE2eUsers)
  test.afterAll(deleteE2eUsers)

  test('shows overview numbers that match the database, with every status listed', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Thống kê' }).click()
    await expect(page).toHaveURL('/admin/stats')
    await expect(page.getByRole('heading', { name: 'Thống kê hệ thống' })).toBeVisible()

    await expect(page.getByTestId('kpi-users')).toHaveText(vi(num('SELECT count(*) FROM users')))
    await expect(page.getByTestId('kpi-venues')).toHaveText(vi(num('SELECT count(*) FROM venues')))
    await expect(page.getByTestId('kpi-bookings')).toHaveText(vi(num('SELECT count(*) FROM bookings')))
    await expect(page.getByTestId('kpi-matches')).toHaveText(vi(num('SELECT count(*) FROM matches')))
    await expect(page.getByTestId('kpi-reports')).toHaveText(String(num("SELECT count(*) FROM reports WHERE status IN ('pending','reviewing')")))

    const role = (code) => num(`SELECT count(DISTINCT ur.user_id) FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.code = '${code}'`)
    await expect(page.getByTestId('dist-role-player')).toHaveAttribute('data-value', String(role('player')))
    await expect(page.getByTestId('dist-role-admin')).toHaveAttribute('data-value', String(role('admin')))
    for (const status of ['active', 'inactive', 'banned', 'pending']) {
      await expect(page.getByTestId(`dist-user-status-${status}`)).toHaveAttribute('data-value', String(num(`SELECT count(*) FROM users WHERE status = '${status}'`)))
    }
    for (const status of ['pending_owner_confirmation', 'awaiting_payment', 'confirmed', 'completed', 'rejected', 'cancelled', 'expired', 'disputed', 'refunded']) {
      await expect(page.getByTestId(`dist-booking-${status}`)).toHaveAttribute('data-value', String(num(`SELECT count(*) FROM bookings WHERE status = '${status}'`)))
    }
    for (const status of ['scheduled', 'completed', 'disputed', 'cancelled']) {
      await expect(page.getByTestId(`dist-match-${status}`)).toHaveAttribute('data-value', String(num(`SELECT count(*) FROM matches WHERE status = '${status}'`)))
    }
    await expect(page.getByTestId('dist-report-partner_review')).toBeVisible()
    await shot(page, '2338-stats-desktop')
    assertNoErrors()
  })

  test('new accounts raise the totals and today\'s trend exactly', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/stats')
    await expect(page.getByTestId('kpi-users')).toBeVisible()
    const before = num('SELECT count(*) FROM users')
    const todayBefore = num("SELECT count(*) FROM users WHERE (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date")
    createE2eUser({ tag: 'tk-1', name: 'E2E Thống Kê Một', role: 'player' })
    createE2eUser({ tag: 'tk-2', name: 'E2E Thống Kê Hai', role: 'player' })
    await page.reload()
    await expect(page.getByTestId('kpi-users')).toHaveText(vi(before + 2))
    await expect(page.getByTestId('stats-total')).toContainText(`Tài khoản mới: ${vi(num("SELECT count(*) FROM users WHERE created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') - interval '29 days') AT TIME ZONE 'Asia/Ho_Chi_Minh'"))}`)
    const today = runSql("SELECT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date")
    await expect(page.getByTestId(`bar-${today}`)).toHaveAttribute('data-value', String(todayBefore + 2))
    assertNoErrors()
  })

  test('range presets, granularity and metric change the chart and totals', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/stats')
    const bars = page.locator('[data-testid^="bar-"]')
    await expect(bars).toHaveCount(30)
    await page.getByTestId('stats-preset-7d').click()
    await expect(bars).toHaveCount(7)
    await page.getByTestId('stats-preset-year').click()
    await expect(page.getByTestId('stats-granularity-month')).toHaveAttribute('aria-pressed', 'true')
    await expect(bars).toHaveCount(13)
    await page.getByTestId('stats-granularity-day').click()
    await expect(bars).toHaveCount(365)
    await page.getByTestId('stats-preset-30d').click()
    await expect(bars).toHaveCount(30)
    await page.getByLabel('Chỉ số').selectOption('newMatches')
    await expect(page.getByTestId('stats-total')).toContainText('Trận đấu mới')
    await page.getByLabel('Chỉ số').selectOption('newBookings')
    await expect(page.getByTestId('stats-total')).toContainText('Booking mới')
    await page.getByTestId('stats-preset-month').click()
    const today = runSql("SELECT extract(day FROM (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'))::int")
    await expect(bars).toHaveCount(Number(today))
    await shot(page, '2338-trend-month')
  })

  test('custom range: validation errors, an empty range shows zeros instead of failing', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/stats')
    await page.getByTestId('stats-preset-custom').click()
    await page.getByLabel('Từ ngày').fill('2020-02-01')
    await page.getByLabel('Đến ngày').fill('2020-01-01')
    await expect(page.getByTestId('stats-range-error')).toContainText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu')
    await page.getByLabel('Từ ngày').fill('2018-01-01')
    await page.getByLabel('Đến ngày').fill('2020-01-01')
    await expect(page.getByTestId('stats-range-error')).toContainText('tối đa 366 ngày')
    await page.getByLabel('Từ ngày').fill('')
    await expect(page.getByTestId('stats-range-error')).toContainText('Chọn đủ ngày')

    await page.getByLabel('Từ ngày').fill('1999-01-01')
    await page.getByLabel('Đến ngày').fill('1999-01-10')
    await expect(page.getByTestId('stats-range-error')).toHaveCount(0)
    await expect(page.locator('[data-testid^="bar-"]')).toHaveCount(10)
    await expect(page.getByTestId('stats-empty')).toContainText('Không có tài khoản mới trong khoảng này')
    await expect(page.getByTestId('stats-total')).toContainText('Tài khoản mới: 0')
    await expect(page.getByTestId('kpi-users')).toBeVisible()
    assertNoErrors()
  })

  test('shows an error state with retry, and recovers', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    let fail = true
    await page.route('**/api/v1/admin/stats/system**', async (route) => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) })
      else await route.continue()
    })
    await page.goto('/admin/stats')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
    fail = false
    await page.getByRole('button', { name: /Thử lại/ }).click()
    await expect(page.getByTestId('kpi-users')).toBeVisible()
  })

  test('the page fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/stats')
    await expect(page.getByTestId('kpi-users')).toBeVisible()
    await expect(page.getByTestId('stats-chart')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(page, '2338-stats-mobile')
    assertNoErrors()
    await context.close()
  })

  test('players cannot open the page', async ({ page }) => {
    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto('/admin/stats')
    await expect(page.getByRole('heading', { name: 'Thống kê hệ thống' })).toHaveCount(0)
  })
})

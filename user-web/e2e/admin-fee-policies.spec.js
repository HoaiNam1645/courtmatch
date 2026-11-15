import { expect, test } from '@playwright/test'
import { loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const vnd = (value) => `${Number(value).toLocaleString('vi-VN')}đ`

test.describe.serial('2.3.41 admin views platform fee configuration', () => {
  let activeId
  let restrictionsBackup

  test.beforeAll(() => {
    runSql("DELETE FROM platform_fee_configs WHERE name LIKE 'E2E %'")
    activeId = runSql("SELECT id FROM platform_fee_configs WHERE status = 'active'")
    restrictionsBackup = runSql('SELECT level, enabled, coalesce(min_overdue_amount::text, \'NULL\'), coalesce(min_overdue_days::text, \'NULL\') FROM debt_restriction_levels ORDER BY level')
  })
  test.afterAll(() => {
    runSql("DELETE FROM platform_fee_configs WHERE name LIKE 'E2E %'")
    runSql(`UPDATE platform_fee_configs SET status = 'active', effective_to = NULL WHERE id = '${activeId}'`)
    for (const line of restrictionsBackup.split('\n')) {
      const [level, enabled, amount, days] = line.split('|')
      runSql(`UPDATE debt_restriction_levels SET enabled = ${enabled === 't'}, min_overdue_amount = ${amount === 'NULL' ? 'NULL' : amount}, min_overdue_days = ${days === 'NULL' ? 'NULL' : days} WHERE level = ${level}`)
    }
  })

  test('opens from the finance group and shows the active policy with its usage', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Phí nền tảng' }).click()
    await expect(page).toHaveURL('/admin/fees')
    await expect(page.getByRole('heading', { name: 'Phí nền tảng', exact: true })).toBeVisible()

    const row = runSql(`SELECT name, fee_type, fee_value FROM platform_fee_configs WHERE id = '${activeId}'`).split('|')
    await expect(page.getByTestId('fee-active-name')).toHaveText(row[0])
    await expect(page.getByTestId('fee-active-value')).toHaveText(row[1] === 'percentage' ? `${Number(row[2])}%` : `${vnd(row[2])} / booking`)
    const used = runSql(`SELECT count(*) || '|' || coalesce(sum(commission_amount), 0) FROM booking_commissions WHERE fee_config_id = '${activeId}'`).split('|')
    await expect(page.getByTestId('fee-active-count')).toHaveText(Number(used[0]).toLocaleString('vi-VN'))
    await expect(page.getByTestId('fee-active-total')).toHaveText(vnd(used[1]))
    await shot(page, '23241-fees-desktop')
    assertNoErrors()
  })

  test('lists past policies newest first with their period, creator and usage; a fixed fee is shown in dong per booking', async ({ page }) => {
    runSql(`
      INSERT INTO platform_fee_configs (name, fee_type, fee_value, effective_from, effective_to, status, created_by)
      VALUES ('E2E Phí cũ nhất', 'percentage', 5, now() - interval '60 days', now() - interval '40 days', 'inactive', (SELECT id FROM users WHERE email = '${SEED_ADMIN_EMAIL}')),
             ('E2E Phí cố định', 'fixed', 15000, now() - interval '40 days', now() - interval '10 days', 'inactive', (SELECT id FROM users WHERE email = '${SEED_ADMIN_EMAIL}'))`)
    const oldId = runSql("SELECT id FROM platform_fee_configs WHERE name = 'E2E Phí cố định'")
    const bookingId = runSql('SELECT id FROM bookings LIMIT 1')
    expect(bookingId).not.toBe('')
    runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, fee_config_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
      SELECT b.id, v.owner_id, '${oldId}', 100000, 'fixed', 15000, 15000, 'due', now(), now() + interval '7 days'
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE NOT EXISTS (SELECT 1 FROM booking_commissions c WHERE c.booking_id = b.id) LIMIT 2`)

    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/fees')
    const history = page.getByTestId('fee-history')
    const items = history.getByRole('listitem')
    await expect(items.first()).toContainText('E2E Phí cố định')
    await expect(page.getByTestId(`fee-policy-${oldId}`)).toContainText('15.000đ / booking')
    await expect(page.getByTestId(`fee-policy-${oldId}`).getByTestId('fee-policy-usage')).toContainText('2 hoa hồng')
    await expect(page.getByTestId(`fee-policy-${oldId}`).getByTestId('fee-policy-usage')).toContainText('30.000đ')
    const oldest = items.filter({ hasText: 'E2E Phí cũ nhất' })
    await expect(oldest).toContainText('5%')
    await expect(oldest.getByTestId('fee-policy-usage')).toHaveText('Chưa ghi nhận hoa hồng nào')
    await expect(oldest).toContainText('Quan Tri Courtly')
    // Chính sách đang áp dụng không lặp lại trong lịch sử.
    await expect(history.getByTestId(`fee-policy-${activeId}`)).toHaveCount(0)
    await shot(page, '23241-fees-history')
    runSql(`DELETE FROM booking_commissions WHERE fee_config_id = '${oldId}'`)
  })

  test('shows the debt restriction levels with their thresholds and whether they are on', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    runSql('UPDATE debt_restriction_levels SET enabled = false, min_overdue_amount = 2000000, min_overdue_days = 14 WHERE level = 1')
    runSql('UPDATE debt_restriction_levels SET enabled = true, min_overdue_amount = 5000000, min_overdue_days = NULL WHERE level = 2')
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/fees')
    await expect(page.getByTestId('restriction-1-state')).toHaveText('Đang tắt')
    await expect(page.getByTestId('restriction-1-threshold')).toHaveText('Áp dụng khi công nợ quá hạn từ 2.000.000đ hoặc quá hạn từ 14 ngày.')
    await expect(page.getByTestId('restriction-2-state')).toHaveText('Đang bật')
    await expect(page.getByTestId('restriction-2-threshold')).toHaveText('Áp dụng khi công nợ quá hạn từ 5.000.000đ.')
    await expect(page.getByTestId('restriction-all-off')).toHaveCount(0)

    runSql('UPDATE debt_restriction_levels SET enabled = false WHERE level = 2')
    await page.reload()
    await expect(page.getByTestId('restriction-all-off')).toBeVisible()
    assertNoErrors()
  })

  test('warns clearly when no policy is active', async ({ page }) => {
    runSql(`UPDATE platform_fee_configs SET status = 'inactive', effective_to = now() WHERE id = '${activeId}'`)
    try {
      await loginByApi(page, SEED_ADMIN_EMAIL)
      await page.goto('/admin/fees')
      await expect(page.getByTestId('fee-no-active')).toContainText('Chưa có chính sách phí đang áp dụng')
      await expect(page.getByTestId('fee-active')).toHaveCount(0)
      await shot(page, '23241-fees-no-active')
    } finally {
      runSql(`UPDATE platform_fee_configs SET status = 'active', effective_to = NULL WHERE id = '${activeId}'`)
    }
  })

  test('shows an error state with retry, and fits a mobile screen', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    let fail = true
    await page.route('**/api/v1/admin/fee-policies', async (route) => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) })
      else await route.continue()
    })
    await page.goto('/admin/fees')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
    fail = false
    await page.getByRole('button', { name: /Thử lại/ }).click()
    await expect(page.getByTestId('fee-active')).toBeVisible()

    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const mobile = await context.newPage()
    await loginByApi(mobile, SEED_ADMIN_EMAIL)
    await mobile.goto('/admin/fees')
    await expect(mobile.getByTestId('fee-active')).toBeVisible()
    expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(mobile, '23241-fees-mobile')
    await context.close()
  })

  test('players cannot open the page', async ({ page }) => {
    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto('/admin/fees')
    await expect(page.getByRole('heading', { name: 'Phí nền tảng', exact: true })).toHaveCount(0)
  })
})

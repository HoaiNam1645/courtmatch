import { expect, test } from '@playwright/test'
import { deleteE2eFeePolicies, loginByApi, runSql, SEED_ADMIN_EMAIL, SEED_FEE_POLICY_ID, shot, watchRuntimeErrors } from './helpers'

test.describe.serial('2.3.40 admin updates platform fee policies and debt restriction thresholds', () => {
  let originalActiveId
  let originalName
  let originalType
  let originalValue
  let oldId
  let restrictionsBackup
  let commissionBookingId

  test.beforeAll(() => {
    // Lần chạy trước bị ngắt giữa chừng có thể để chính sách seed mang tên "E2E ...": trả về tên gốc.
    runSql(`UPDATE platform_fee_configs SET name = 'Chinh sach hoa hong', fee_type = 'percentage', fee_value = 10, status = 'active', effective_to = NULL WHERE id = '${SEED_FEE_POLICY_ID}'`)
    runSql(`UPDATE platform_fee_configs SET status = 'inactive', effective_to = now() WHERE status = 'active' AND id <> '${SEED_FEE_POLICY_ID}'`)
    deleteE2eFeePolicies()
    ;[originalActiveId, originalName, originalType, originalValue] = runSql("SELECT id || '|' || name || '|' || fee_type || '|' || fee_value::numeric(12,2) FROM platform_fee_configs WHERE status = 'active'").split('|')
    runSql(`DELETE FROM booking_commissions WHERE fee_config_id = '${originalActiveId}' AND gross_amount = 100000 AND commission_amount = 10000`)
    restrictionsBackup = runSql("SELECT level || '|' || enabled || '|' || coalesce(min_overdue_amount::text, 'NULL') || '|' || coalesce(min_overdue_days::text, 'NULL') FROM debt_restriction_levels ORDER BY level")
    oldId = runSql(`
      INSERT INTO platform_fee_configs (name, fee_type, fee_value, effective_from, effective_to, status, created_by)
      VALUES ('E2E Phí cũ', 'fixed', 15000, now() - interval '30 days', now() - interval '10 days', 'inactive', (SELECT id FROM users WHERE email = '${SEED_ADMIN_EMAIL}'))
      RETURNING id`).split('\n')[0]
    runSql('UPDATE debt_restriction_levels SET enabled = false, min_overdue_amount = 2000000, min_overdue_days = 14, updated_by = NULL WHERE level = 1')
    runSql('UPDATE debt_restriction_levels SET enabled = false, min_overdue_amount = 5000000, min_overdue_days = 30, updated_by = NULL WHERE level = 2')
  })
  test.afterAll(() => {
    runSql("UPDATE platform_fee_configs SET status = 'inactive', effective_to = now() WHERE status = 'active' AND id <> '" + originalActiveId + "'")
    runSql(`UPDATE platform_fee_configs SET name = '${originalName}', fee_type = '${originalType}', fee_value = ${originalValue}, status = 'active', effective_to = NULL WHERE id = '${originalActiveId}'`)
    deleteE2eFeePolicies()
    if (commissionBookingId) runSql(`DELETE FROM booking_commissions WHERE booking_id = '${commissionBookingId}'`)
    for (const line of restrictionsBackup.split('\n')) {
      const [level, enabled, amount, days] = line.split('|')
      runSql(`UPDATE debt_restriction_levels SET enabled = ${enabled === 't' || enabled === 'true'}, min_overdue_amount = ${amount === 'NULL' ? 'NULL' : amount}, min_overdue_days = ${days === 'NULL' ? 'NULL' : days}, updated_by = NULL WHERE level = ${level}`)
    }
  })

  const activeRow = () => runSql("SELECT id || '|' || name || '|' || fee_type || '|' || fee_value::numeric(12,2) FROM platform_fee_configs WHERE status = 'active'")
  const activeCount = () => runSql("SELECT count(*) FROM platform_fee_configs WHERE status = 'active'")

  async function open(page, viewport) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/fees')
    await expect(page.getByTestId(viewport === 'noactive' ? 'fee-no-active' : 'fee-active')).toBeVisible()
  }

  test('edits the active policy: the form is prefilled, save is disabled until something changes, snapshots stay put', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    commissionBookingId = runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, fee_config_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
      SELECT b.id, v.owner_id, '${originalActiveId}', 100000, '${originalType}', ${originalValue}, 10000, 'due', now(), now() + interval '7 days'
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE NOT EXISTS (SELECT 1 FROM booking_commissions c WHERE c.booking_id = b.id) LIMIT 1
      RETURNING booking_id`).split('\n')[0]
    await open(page)
    await page.getByTestId('fee-edit-active').click()
    const dialog = page.getByTestId('fee-dialog')
    await expect(dialog.getByRole('heading', { name: 'Sửa chính sách phí' })).toBeVisible()
    await expect(dialog.getByTestId('fee-name')).toHaveValue(originalName)
    await expect(dialog.getByTestId(`fee-type-${originalType}`)).toBeChecked()
    await expect(dialog.getByTestId('fee-submit')).toBeDisabled()
    await expect(dialog.getByTestId('fee-preview')).toContainText('Thay đổi áp dụng cho hoa hồng ghi nhận từ lúc lưu')

    await dialog.getByTestId('fee-name').fill('E2E Phí đã sửa')
    await dialog.getByTestId('fee-value').fill('15')
    await expect(dialog.getByTestId('fee-submit')).toBeEnabled()
    await shot(page, '23240-edit-dialog')
    await dialog.getByTestId('fee-submit').click()

    await expect(page.getByTestId('admin-notice')).toContainText('Đã cập nhật chính sách "E2E Phí đã sửa"')
    await expect(page.getByTestId('fee-active-name')).toHaveText('E2E Phí đã sửa')
    await expect(page.getByTestId('fee-active-value')).toHaveText('15%')
    expect(activeRow()).toBe(`${originalActiveId}|E2E Phí đã sửa|percentage|15.00`)
    expect(activeCount()).toBe('1')
    expect(runSql(`SELECT fee_type || '|' || fee_value::numeric(12,2) || '|' || commission_amount::numeric(12,2) FROM booking_commissions WHERE booking_id = '${commissionBookingId}'`)).toBe(`${originalType}|${Number(originalValue).toFixed(2)}|10000.00`)
    expect(runSql("SELECT count(*) FROM audit_logs WHERE action = 'fee.update' AND created_at > now() - interval '1 minute'")).not.toBe('0')
    assertNoErrors()
  })

  test('shows validation on the edit form and a server conflict when nothing changed there', async ({ page }) => {
    await open(page)
    await page.getByTestId('fee-edit-active').click()
    const dialog = page.getByTestId('fee-dialog')
    await dialog.getByTestId('fee-value').fill('150')
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByText('Phí phần trăm không được vượt quá 100')).toBeVisible()
    await dialog.getByTestId('fee-name').fill('')
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByText('Tên chính sách tối thiểu 2 ký tự')).toBeVisible()
    await dialog.getByTestId('fee-name').fill('E2E Phí lỗi')
    await dialog.getByTestId('fee-value').fill('20')
    await page.route('**/api/v1/admin/fee-policies/*', async (route) => {
      if (route.request().method() !== 'PUT') return route.continue()
      return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 'FEE_POLICY_INVALID', message: 'Khong co thay doi nao de luu.' }) })
    })
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByRole('alert')).toContainText('Khong co thay doi nao de luu')
    expect(activeRow()).toContain('E2E Phí đã sửa')
  })

  test('editing an old policy says it only matters once reactivated, and does not touch the active one', async ({ page }) => {
    await open(page)
    await page.getByTestId(`fee-policy-${oldId}`).getByTestId('fee-edit-old').click()
    const dialog = page.getByTestId('fee-dialog')
    await expect(dialog.getByTestId('fee-preview')).toContainText('chỉ có hiệu lực khi bạn kích hoạt lại')
    await expect(dialog.getByTestId('fee-type-fixed')).toBeChecked()
    await dialog.getByTestId('fee-name').fill('E2E Phí cũ đổi tên')
    await dialog.getByTestId('fee-value').fill('18000')
    await dialog.getByTestId('fee-submit').click()
    await expect(page.getByTestId(`fee-policy-${oldId}`)).toContainText('E2E Phí cũ đổi tên')
    await expect(page.getByTestId(`fee-policy-${oldId}`)).toContainText('18.000đ / booking')
    expect(activeRow()).toContain(originalActiveId)
  })

  test('reactivating shows what will be replaced; cancelling changes nothing, confirming swaps the active policy', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page)
    await page.getByTestId(`fee-policy-${oldId}`).getByTestId('fee-activate').click()
    const dialog = page.getByTestId('activate-dialog')
    await expect(dialog.getByTestId('activate-summary')).toContainText('E2E Phí cũ đổi tên')
    await expect(dialog.getByTestId('activate-summary')).toContainText('18.000đ / booking')
    await expect(dialog.getByTestId('activate-summary')).toContainText('E2E Phí đã sửa')
    await expect(dialog.getByTestId('activate-summary')).toContainText('sẽ ngừng áp dụng')
    await shot(page, '23240-activate-dialog')
    await dialog.getByRole('button', { name: 'Hủy' }).click()
    expect(activeRow()).toContain(originalActiveId)

    await page.getByTestId(`fee-policy-${oldId}`).getByTestId('fee-activate').click()
    await page.getByTestId('activate-confirm').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã kích hoạt lại chính sách "E2E Phí cũ đổi tên"')
    await expect(page.getByTestId('fee-active-name')).toHaveText('E2E Phí cũ đổi tên')
    await expect(page.getByTestId('fee-active-value')).toHaveText('18.000đ / booking')
    await expect(page.getByTestId(`fee-policy-${originalActiveId}`)).toContainText('→')
    await expect(page.getByTestId(`fee-policy-${oldId}`)).toHaveCount(0)
    expect(activeRow()).toContain(oldId)
    expect(activeCount()).toBe('1')
    expect(runSql(`SELECT effective_to IS NULL FROM platform_fee_configs WHERE id = '${oldId}'`)).toBe('t')
    assertNoErrors()
  })

  test('shows the server message when reactivation fails', async ({ page }) => {
    await open(page)
    await page.route('**/api/v1/admin/fee-policies/*/activate', (route) => route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 'FEE_POLICY_INVALID', message: 'Chinh sach phi vua duoc quan tri vien khac thay doi, vui long tai lai.' }) }))
    await page.getByTestId(`fee-policy-${originalActiveId}`).getByTestId('fee-activate').click()
    await page.getByTestId('activate-confirm').click()
    await expect(page.getByTestId('activate-dialog').getByRole('alert')).toContainText('vua duoc quan tri vien khac thay doi')
    expect(activeRow()).toContain(oldId)
  })

  test('restriction thresholds: prefilled, validated on screen, saved, and shown on the cards', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page)
    await page.getByTestId('restriction-edit').click()
    const dialog = page.getByTestId('restriction-dialog')
    await expect(dialog.getByTestId('restriction-amount-1')).toHaveValue('2000000')
    await expect(dialog.getByTestId('restriction-days-2')).toHaveValue('30')
    await expect(dialog.getByTestId('restriction-enabled-1')).not.toBeChecked()
    await expect(dialog.getByTestId('restriction-submit')).toBeDisabled()

    await dialog.getByTestId('restriction-enabled-1').check()
    await dialog.getByTestId('restriction-amount-1').fill('')
    await dialog.getByTestId('restriction-days-1').fill('')
    await dialog.getByTestId('restriction-submit').click()
    await expect(dialog.getByText('Mức đang bật phải có ít nhất một ngưỡng')).toBeVisible()
    await dialog.getByTestId('restriction-days-1').fill('0')
    await dialog.getByTestId('restriction-submit').click()
    await expect(dialog.getByText('Ngưỡng số ngày là số nguyên từ 1 đến 3650')).toBeVisible()
    await dialog.getByTestId('restriction-days-1').fill('7,5')
    await dialog.getByTestId('restriction-submit').click()
    await expect(dialog.getByText('Ngưỡng số ngày là số nguyên từ 1 đến 3650')).toBeVisible()
    await dialog.getByTestId('restriction-days-1').fill('20')
    await dialog.getByTestId('restriction-amount-1').fill('-5')
    await dialog.getByTestId('restriction-submit').click()
    await expect(dialog.getByText('Ngưỡng số tiền phải lớn hơn 0')).toBeVisible()

    await dialog.getByTestId('restriction-amount-1').fill('1.000.000')
    await dialog.getByTestId('restriction-enabled-2').check()
    await dialog.getByTestId('restriction-days-2').fill('10')
    await dialog.getByTestId('restriction-submit').click()
    await expect(dialog.getByText('Ngưỡng số ngày của mức 2 phải từ ngưỡng mức 1 trở lên')).toBeVisible()
    await shot(page, '23240-restriction-dialog')
    await dialog.getByTestId('restriction-days-2').fill('30')
    await dialog.getByTestId('restriction-amount-2').fill('3000000')
    await dialog.getByTestId('restriction-submit').click()

    await expect(page.getByTestId('admin-notice')).toContainText('Đã cập nhật ngưỡng hạn chế công nợ quá hạn')
    await expect(page.getByTestId('restriction-1-state')).toHaveText('Đang bật')
    await expect(page.getByTestId('restriction-1-threshold')).toHaveText('Áp dụng khi công nợ quá hạn từ 1.000.000đ hoặc quá hạn từ 20 ngày.')
    await expect(page.getByTestId('restriction-2-state')).toHaveText('Đang bật')
    await expect(page.getByTestId('restriction-2-threshold')).toHaveText('Áp dụng khi công nợ quá hạn từ 3.000.000đ hoặc quá hạn từ 30 ngày.')
    expect(runSql('SELECT enabled || min_overdue_amount::text || min_overdue_days FROM debt_restriction_levels WHERE level = 1')).toBe('true1000000.0020')
    expect(runSql('SELECT (updated_by IS NOT NULL)::text FROM debt_restriction_levels WHERE level = 1')).toBe('true')
    await expect(page.getByTestId('restriction-all-off')).toHaveCount(0)
    assertNoErrors()
  })

  test('a threshold can use only the amount or only the days; turning both levels off shows the notice', async ({ page }) => {
    await open(page)
    await page.getByTestId('restriction-edit').click()
    const dialog = page.getByTestId('restriction-dialog')
    await dialog.getByTestId('restriction-days-1').fill('')
    await dialog.getByTestId('restriction-amount-2').fill('')
    await dialog.getByTestId('restriction-submit').click()
    await expect(page.getByTestId('restriction-1-threshold')).toHaveText('Áp dụng khi công nợ quá hạn từ 1.000.000đ.')
    await expect(page.getByTestId('restriction-2-threshold')).toHaveText('Áp dụng khi quá hạn từ 30 ngày.')

    await page.getByTestId('restriction-edit').click()
    await page.getByTestId('restriction-enabled-1').uncheck()
    await page.getByTestId('restriction-enabled-2').uncheck()
    await page.getByTestId('restriction-submit').click()
    await expect(page.getByTestId('restriction-all-off')).toBeVisible()
    // Tắt mức vẫn giữ ngưỡng đã nhập.
    await expect(page.getByTestId('restriction-1-threshold')).toHaveText('Áp dụng khi công nợ quá hạn từ 1.000.000đ.')
  })

  test('shows the server error when saving thresholds fails; the dialogs fit a mobile screen', async ({ page, browser }) => {
    await open(page)
    await page.getByTestId('restriction-edit').click()
    const dialog = page.getByTestId('restriction-dialog')
    await dialog.getByTestId('restriction-enabled-1').check()
    await page.route('**/api/v1/admin/fee-policies/debt-restrictions', (route) => route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ code: 'VALIDATION_FAILED', message: 'Nguong han che khong hop le.', fieldErrors: { 'level2.minOverdueDays': 'Loi tu may chu ve nguong ngay' } }) }))
    await dialog.getByTestId('restriction-submit').click()
    await expect(dialog.getByText('Loi tu may chu ve nguong ngay')).toBeVisible()
    await expect(dialog.getByRole('alert')).toContainText('Nguong han che khong hop le')

    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const mobile = await context.newPage()
    await open(mobile)
    await mobile.getByTestId('restriction-edit').click()
    await expect(mobile.getByTestId('restriction-dialog')).toBeVisible()
    expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(mobile, '23240-restriction-mobile')
    await mobile.keyboard.press('Escape')
    await mobile.getByTestId('fee-edit-active').click()
    await expect(mobile.getByTestId('fee-dialog')).toBeVisible()
    expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await context.close()
  })
})

import { expect, test } from '@playwright/test'
import { loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

test.describe.serial('2.3.39 admin creates a platform fee policy', () => {
  let originalActiveId
  let commissionBookingId

  test.beforeAll(() => {
    runSql("DELETE FROM platform_fee_configs WHERE name LIKE 'E2E %'")
    originalActiveId = runSql("SELECT id FROM platform_fee_configs WHERE status = 'active'")
    // Dọn hoa hồng thử còn sót nếu lần chạy trước bị ngắt giữa chừng.
    runSql(`DELETE FROM booking_commissions WHERE fee_config_id = '${originalActiveId}' AND gross_amount = 100000 AND commission_amount = 10000`)
  })
  test.afterAll(() => {
    runSql("DELETE FROM platform_fee_configs WHERE name LIKE 'E2E %'")
    runSql("UPDATE platform_fee_configs SET status = 'inactive' WHERE status = 'active'")
    runSql(`UPDATE platform_fee_configs SET status = 'active', effective_to = NULL WHERE id = '${originalActiveId}'`)
    if (commissionBookingId) runSql(`DELETE FROM booking_commissions WHERE booking_id = '${commissionBookingId}'`)
  })

  const activeValue = () => runSql("SELECT name || '|' || fee_type || '|' || fee_value::numeric(12,2) FROM platform_fee_configs WHERE status = 'active'")
  const activeCount = () => runSql("SELECT count(*) FROM platform_fee_configs WHERE status = 'active'")

  async function openDialog(page) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/fees')
    await page.getByTestId('fee-create-open').click()
    return page.getByTestId('fee-dialog')
  }

  test('validates the form on the screen: required fields, ranges, decimals, and the unit follows the fee type', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const dialog = await openDialog(page)
    await expect(dialog.getByTestId('fee-preview')).toContainText('Nhập tên và giá trị hợp lệ')
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByText('Tên chính sách tối thiểu 2 ký tự')).toBeVisible()
    await expect(dialog.getByText('Vui lòng nhập giá trị phí')).toBeVisible()

    await dialog.getByTestId('fee-name').fill('E2E Phí thử')
    await dialog.getByTestId('fee-value').fill('101')
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByText('Phí phần trăm không được vượt quá 100')).toBeVisible()
    await dialog.getByTestId('fee-value').fill('-5')
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByText('Giá trị phí phải là số không âm')).toBeVisible()
    await dialog.getByTestId('fee-value').fill('abc')
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByText('Giá trị phí phải là số không âm')).toBeVisible()
    await dialog.getByTestId('fee-value').fill('7.123')
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByText('Giá trị phí tối đa 2 chữ số thập phân')).toBeVisible()

    await dialog.getByTestId('fee-type-fixed').check()
    await expect(dialog.getByText('Số tiền mỗi booking (đồng)')).toBeVisible()
    await dialog.getByTestId('fee-value').fill('10000001')
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByText('Phí cố định tối đa 10.000.000đ mỗi booking')).toBeVisible()
    await dialog.getByTestId('fee-value').fill('15000')
    await expect(dialog.getByTestId('fee-preview')).toContainText('15.000đ / booking')
    await expect(dialog.getByTestId('fee-preview')).toContainText('Hoa hồng đã ghi nhận giữ nguyên mức phí cũ')
    await shot(page, '23239-dialog')
    expect(activeCount()).toBe('1')
    expect(activeValue()).not.toContain('E2E')
    assertNoErrors()
  })

  test('closing the dialog (button, Escape, backdrop) changes nothing', async ({ page }) => {
    const before = activeValue()
    const dialog = await openDialog(page)
    await dialog.getByTestId('fee-name').fill('E2E Không lưu')
    await dialog.getByRole('button', { name: 'Hủy' }).click()
    await expect(page.getByTestId('fee-dialog')).toHaveCount(0)
    await page.getByTestId('fee-create-open').click()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('fee-dialog')).toHaveCount(0)
    await page.getByTestId('fee-create-open').click()
    await page.mouse.click(5, 5)
    await expect(page.getByTestId('fee-dialog')).toHaveCount(0)
    expect(activeValue()).toBe(before)
  })

  test('creates a percentage policy: it becomes active at once and the old one moves to history with an end time', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    // Một hoa hồng đã ghi nhận theo chính sách cũ phải giữ nguyên sau khi đổi chính sách.
    commissionBookingId = runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, fee_config_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
      SELECT b.id, v.owner_id, '${originalActiveId}', 100000, 'percentage', 10, 10000, 'due', now(), now() + interval '7 days'
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE NOT EXISTS (SELECT 1 FROM booking_commissions c WHERE c.booking_id = b.id) LIMIT 1
      RETURNING booking_id`).split('\n')[0]
    const dialog = await openDialog(page)
    await dialog.getByTestId('fee-name').fill('E2E Phí 12,5%')
    await dialog.getByTestId('fee-value').fill('12,5')
    await expect(dialog.getByTestId('fee-preview')).toContainText('12,5%')
    await dialog.getByTestId('fee-submit').click()

    await expect(page.getByTestId('fee-dialog')).toHaveCount(0)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã áp dụng chính sách "E2E Phí 12,5%"')
    await expect(page.getByTestId('fee-active-name')).toHaveText('E2E Phí 12,5%')
    await expect(page.getByTestId('fee-active-value')).toHaveText('12,5%')
    await expect(page.getByTestId('fee-active-count')).toHaveText('0')
    const oldCard = page.getByTestId(`fee-policy-${originalActiveId}`)
    await expect(oldCard).toBeVisible()
    await expect(oldCard).toContainText('→')
    await expect(oldCard.getByTestId('fee-policy-usage')).toContainText('1 hoa hồng')
    await shot(page, '23239-after-create')

    expect(activeCount()).toBe('1')
    expect(activeValue()).toBe('E2E Phí 12,5%|percentage|12.50')
    expect(runSql(`SELECT status || '|' || (effective_to IS NOT NULL) FROM platform_fee_configs WHERE id = '${originalActiveId}'`)).toBe('inactive|true')
    expect(runSql(`SELECT fee_type || '|' || fee_value::numeric(12,2) || '|' || commission_amount::numeric(12,2) FROM booking_commissions WHERE booking_id = '${commissionBookingId}'`)).toBe('percentage|10.00|10000.00')
    assertNoErrors()
  })

  test('creates a fixed fee, which is shown in dong per booking, and the previous custom policy goes to history', async ({ page }) => {
    const dialog = await openDialog(page)
    await expect(dialog.getByTestId('fee-preview')).toContainText('Nhập tên và giá trị hợp lệ')
    await dialog.getByTestId('fee-name').fill('E2E Phí cố định')
    await dialog.getByTestId('fee-type-fixed').check()
    await dialog.getByTestId('fee-value').fill('20000')
    await expect(dialog.getByTestId('fee-preview')).toContainText('thay cho "E2E Phí 12,5%" (12,5%)')
    await dialog.getByTestId('fee-submit').click()
    await expect(page.getByTestId('fee-active-value')).toHaveText('20.000đ / booking')
    await expect(page.getByTestId('fee-history').getByRole('listitem').first()).toContainText('E2E Phí 12,5%')
    expect(activeCount()).toBe('1')
  })

  test('shows server-side validation and conflict messages, and blocks a double submit', async ({ page }) => {
    const dialog = await openDialog(page)
    await dialog.getByTestId('fee-name').fill('E2E Lỗi máy chủ')
    await dialog.getByTestId('fee-value').fill('5')
    let posts = 0
    let mode = 'invalid'
    await page.route('**/api/v1/admin/fee-policies', async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      posts += 1
      await new Promise((resolve) => setTimeout(resolve, 400))
      if (mode === 'invalid') return route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ code: 'VALIDATION_FAILED', message: 'Thong tin chinh sach phi khong hop le.', fieldErrors: { feeValue: 'Gia tri phi khong hop le tu may chu' } }) })
      return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 'FEE_POLICY_INVALID', message: 'Chinh sach phi vua duoc quan tri vien khac thay doi, vui long tai lai.' }) })
    })
    await dialog.getByTestId('fee-submit').dblclick()
    await expect(dialog.getByText('Gia tri phi khong hop le tu may chu')).toBeVisible()
    expect(posts).toBe(1)
    mode = 'conflict'
    await dialog.getByTestId('fee-submit').click()
    await expect(dialog.getByRole('alert')).toContainText('vua duoc quan tri vien khac thay doi')
    await expect(page.getByTestId('fee-dialog')).toBeVisible()
    expect(runSql("SELECT count(*) FROM platform_fee_configs WHERE name = 'E2E Lỗi máy chủ'")).toBe('0')
  })

  test('the dialog fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    const assertNoErrors = watchRuntimeErrors(page)
    const dialog = await openDialog(page)
    await dialog.getByTestId('fee-name').fill('E2E Mobile')
    await expect(dialog.getByTestId('fee-submit')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(page, '23239-dialog-mobile')
    assertNoErrors()
    await context.close()
  })
})

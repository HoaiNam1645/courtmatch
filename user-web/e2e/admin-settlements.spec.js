import { expect, test } from '@playwright/test'
import {
  createAcceptedBookings,
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  runSql,
  SEED_ADMIN_EMAIL,
  SEED_OWNER_EMAIL,
  shot,
  watchRuntimeErrors,
} from './helpers'

const vnd = (value) => `${Number(value).toLocaleString('vi-VN')}đ`
const HOURS = ['06:00', '07:00', '08:00', '09:00', '10:00', '11:00', '12:00', '13:00']

test.describe.serial('2.3.45 - 2.3.46 admin settlement periods', () => {
  let startedAt
  let ownerId
  let bookings
  let preSettlements

  test.beforeAll(() => {
    deleteE2eVenues()
    startedAt = runSql('SELECT now()')
  })
  test.afterAll(() => {
    deleteE2eVenues()
    runSql(`DELETE FROM audit_logs WHERE entity_type = 'commission_settlement' AND created_at >= '${startedAt}'`)
  })

  test('builds commissions of every status plus two existing settlements (one with a pending payment report)', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Kỳ Công Nợ')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: HOURS })
    ownerId = runSql(`SELECT owner_id FROM venues WHERE id = '${venue.id}'`).split('\n')[0]
    // Hai kỳ có sẵn trong tháng 6/2024 (lọc theo khoảng này để không lẫn dữ liệu khác).
    preSettlements = ['pending_review', 'paid'].map((status, index) => runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, status, commission_total, amount_due, amount_paid, note)
      VALUES ('${ownerId}', '2024-06-${index === 0 ? '01' : '08'}', '2024-06-${index === 0 ? '07' : '14'}', '${status}', 30000, 30000, ${status === 'paid' ? 30000 : 0}, 'E2E kỳ có sẵn')
      RETURNING id`).split('\n')[0])
    const insert = (booking, amount, status, at, dueExpr, settlement = null) => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at, settlement_id)
      SELECT b.id, v.owner_id, 180000, 'percentage', 10, ${amount}, '${status}', timestamp '${at}' AT TIME ZONE 'Asia/Ho_Chi_Minh', ${dueExpr}, ${settlement ? `'${settlement}'` : 'NULL'}
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    insert(bookings[0], 18000, 'due', '2024-06-03 09:00', "now() - interval '2 days'", preSettlements[0])
    insert(bookings[1], 12000, 'adjusted', '2024-06-04 09:00', "now() + interval '2 days'", preSettlements[0])
    insert(bookings[2], 30000, 'settled', '2024-06-10 09:00', "now() - interval '9 days'", preSettlements[1])
    runSql(`INSERT INTO commission_payment_reports (owner_id, settlement_id, amount, paid_on, reference_code, status)
            VALUES ('${ownerId}', '${preSettlements[0]}', 30000, '2024-06-20', 'E2E-REF-1', 'pending_review')`)
    // Hoa hồng chưa vào kỳ nào: ba khoản gom được, hai khoản không, một khoản nằm ngoài khoảng ngày.
    insert(bookings[3], 10000, 'due', '2025-02-10 09:00', "now() + interval '3 days'")
    insert(bookings[4], 20000, 'pending', '2025-02-11 09:00', "now() + interval '3 days'")
    insert(bookings[5], 5000, 'adjusted', '2025-02-12 09:00', "now() + interval '3 days'")
    insert(bookings[6], 7000, 'suspended', '2025-02-11 10:00', "now() + interval '3 days'")
    insert(bookings[7], 9000, 'due', '2025-03-05 09:00', "now() + interval '3 days'")
  })

  async function open(page) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/settlements')
    await page.getByLabel('Kỳ từ ngày').fill('2024-06-01')
    await page.getByLabel('Kỳ đến ngày').fill('2024-06-30')
  }

  test('opens from the finance group; rows show owner, period, amounts, status; the pending review period comes first', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Kỳ công nợ' }).click()
    await expect(page).toHaveURL('/admin/settlements')
    await expect(page.getByRole('heading', { name: 'Kỳ công nợ hoa hồng' })).toBeVisible()
    await page.getByLabel('Kỳ từ ngày').fill('2024-06-01')
    await page.getByLabel('Kỳ đến ngày').fill('2024-06-30')

    const list = page.getByTestId('settlement-list').getByRole('listitem')
    await expect(list).toHaveCount(2)
    const first = list.first()
    await expect(first.getByTestId('settlement-status')).toHaveText('Chờ Courtly xác nhận')
    await expect(first.getByTestId('settlement-owner')).toContainText('Nguyen Huu Thang')
    await expect(first.getByTestId('settlement-period')).toContainText('01/06/2024 – 07/06/2024')
    await expect(first.getByTestId('settlement-period')).toContainText('2 booking')
    await expect(first.getByTestId('settlement-due')).toContainText(vnd(30000))
    await expect(first.getByTestId('settlement-remaining')).toContainText(vnd(30000))
    await expect(first.getByTestId('settlement-reports')).toHaveText('1 báo cáo chờ xác nhận')
    await expect(first.getByTestId('settlement-overdue')).toBeVisible()
    const paid = list.nth(1)
    await expect(paid.getByTestId('settlement-status')).toHaveText('Đã thanh toán')
    await expect(paid.getByTestId('settlement-remaining')).toContainText(vnd(0))
    await expect(paid.getByTestId('settlement-overdue')).toHaveCount(0)

    await expect(page.getByTestId('settlement-total-count')).toHaveText('2')
    await expect(page.getByTestId('settlement-total-due')).toHaveText(vnd(60000))
    await expect(page.getByTestId('settlement-total-paid')).toHaveText(vnd(30000))
    await expect(page.getByTestId('settlement-total-remaining')).toHaveText(vnd(30000))
    await shot(page, '2345-settlements-desktop')
    assertNoErrors()
  })

  test('status tabs show counts and filter; overdue-only, owner and search filters work; clear resets', async ({ page }) => {
    await open(page)
    await expect(page.getByTestId('settlement-tab-count-pending_review')).toHaveText('1')
    await expect(page.getByTestId('settlement-tab-count-paid')).toHaveText('1')
    await page.getByTestId('settlement-tab-paid').click()
    await expect(page.getByTestId('settlement-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('settlement-status')).toHaveText('Đã thanh toán')
    // Đếm theo trạng thái vẫn giữ nguyên khi đang lọc một trạng thái.
    await expect(page.getByTestId('settlement-tab-count-pending_review')).toHaveText('1')
    await page.getByTestId('settlement-tab-all').click()

    await page.getByTestId('settlement-overdue-only').check()
    await expect(page.getByTestId('settlement-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('settlement-status')).toHaveText('Chờ Courtly xác nhận')
    await page.getByTestId('settlement-overdue-only').uncheck()

    await page.getByLabel('Tìm chủ sân').fill('khong-ton-tai-xyz')
    await expect(page.getByText('Không có kỳ công nợ nào')).toBeVisible()
    await page.getByTestId('settlement-clear').click()
    await page.getByLabel('Kỳ từ ngày').fill('2024-06-01')
    await page.getByLabel('Kỳ đến ngày').fill('2024-06-30')
    await page.getByLabel('Tìm chủ sân').fill('thang.owner')
    await expect(page.getByTestId('settlement-list').getByRole('listitem')).toHaveCount(2)

    await page.getByLabel('Kỳ đến ngày').fill('2024-05-01')
    await expect(page.getByTestId('settlement-range-error')).toBeVisible()
  })

  test('detail drawer: amounts, owner status, pending payment report and the bookings that make up the period', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page)
    await page.getByTestId('settlement-list').getByRole('listitem').first().getByTestId('settlement-detail').click()
    const drawer = page.getByTestId('settlement-drawer')
    await expect(drawer).toBeVisible()
    await expect(drawer.getByTestId('sd-status')).toHaveText('Chờ Courtly xác nhận')
    await expect(drawer.getByTestId('sd-period')).toContainText('01/06/2024 – 07/06/2024')
    await expect(drawer.getByTestId('sd-commission')).toHaveText(vnd(30000))
    await expect(drawer.getByTestId('sd-due')).toHaveText(vnd(30000))
    await expect(drawer.getByTestId('sd-paid')).toHaveText(vnd(0))
    await expect(drawer.getByTestId('sd-remaining')).toHaveText(vnd(30000))
    await expect(drawer.getByTestId('sd-adjustment')).toHaveCount(0)
    await expect(drawer.getByTestId('sd-restriction')).toHaveText('Không bị hạn chế')
    await expect(drawer.getByTestId('sd-owed')).toHaveText(vnd(0))
    await expect(drawer.getByTestId('sd-pending-reports')).toBeVisible()
    await expect(drawer.getByTestId('sd-report')).toContainText('E2E-REF-1')
    await expect(drawer.getByTestId('sd-report')).toContainText('Chờ Courtly xác nhận')
    await expect(drawer.getByTestId(`sd-booking-${bookings[0].bookingCode}`)).toContainText(vnd(18000))
    await expect(drawer.getByTestId(`sd-booking-${bookings[0].bookingCode}`)).toContainText('Quá hạn')
    await expect(drawer.getByTestId(`sd-booking-${bookings[1].bookingCode}`)).toContainText(vnd(12000))
    await expect(drawer.getByTestId(`sd-booking-${bookings[2].bookingCode}`)).toHaveCount(0)
    await expect(drawer.getByTestId('sd-history-empty')).toBeVisible()
    await shot(page, '2346-settlement-drawer')

    // Từ booking cấu thành mở chi tiết đối soát của booking (2.3.44).
    await drawer.getByTestId(`sd-booking-${bookings[0].bookingCode}`).getByRole('button', { name: 'Xem đối soát booking' }).click()
    await expect(page.getByTestId('commission-drawer')).toBeVisible()
    await expect(page.getByTestId('drawer-code')).toHaveText(bookings[0].bookingCode)
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('commission-drawer')).toHaveCount(0)
    await expect(drawer).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('settlement-drawer')).toHaveCount(0)
    assertNoErrors()
  })

  test('closing a period: validation errors are shown, then only eligible commissions are gathered', async ({ page }) => {
    // Hai lần chốt kỳ không có gì để gom cố ý trả 409; trình duyệt ghi lỗi tải tài nguyên cho các phản hồi đó.
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/admin/finance/settlements'] })
    await open(page)
    await page.getByTestId('settlement-create').click()
    const dialog = page.getByTestId('settlement-dialog')
    await expect(dialog).toBeVisible()

    await dialog.getByTestId('settlement-form-submit').click()
    await expect(dialog).toContainText('Chọn chủ sân.')
    await dialog.getByTestId('settlement-form-owner').selectOption(ownerId)
    await dialog.getByTestId('settlement-form-from').fill('2025-02-12')
    await dialog.getByTestId('settlement-form-to').fill('2025-02-10')
    await dialog.getByTestId('settlement-form-submit').click()
    await expect(dialog).toContainText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.')

    // Khoảng không có hoa hồng nào: lỗi từ máy chủ, hộp thoại vẫn mở để sửa.
    await dialog.getByTestId('settlement-form-from').fill('2025-01-01')
    await dialog.getByTestId('settlement-form-to').fill('2025-01-31')
    await dialog.getByTestId('settlement-form-submit').click()
    await expect(dialog.getByRole('alert')).toContainText('Không có hoa hồng chưa đối soát nào')
    await expect(dialog).toBeVisible()

    await dialog.getByTestId('settlement-form-from').fill('2025-02-10')
    await dialog.getByTestId('settlement-form-to').fill('2025-02-12')
    await dialog.getByLabel('Ghi chú kỳ').fill('E2E chốt kỳ')
    await shot(page, '2345-settlement-dialog')
    await dialog.getByTestId('settlement-form-submit').click()
    await expect(page.getByTestId('settlement-dialog')).toHaveCount(0)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã chốt kỳ 10/02/2025 – 12/02/2025')
    await expect(page.getByTestId('admin-notice')).toContainText('3 booking')

    await page.getByLabel('Kỳ từ ngày').fill('2025-02-01')
    await page.getByLabel('Kỳ đến ngày').fill('2025-02-28')
    const row = page.getByTestId('settlement-list').getByRole('listitem').first()
    await expect(row.getByTestId('settlement-status')).toHaveText('Đang mở')
    await expect(row.getByTestId('settlement-due')).toContainText(vnd(35000))
    await expect(row.getByTestId('settlement-period')).toContainText('3 booking')

    const gathered = Number(runSql(`SELECT count(*) FROM booking_commissions WHERE settlement_id = (SELECT id FROM commission_settlements WHERE period_from = '2025-02-10' AND owner_id = '${ownerId}')`))
    expect(gathered).toBe(3)
    // Hoa hồng tạm hoãn và hoa hồng ngoài khoảng ngày không bị gom.
    for (const index of [6, 7]) {
      expect(runSql(`SELECT settlement_id IS NULL FROM booking_commissions WHERE booking_id = '${bookings[index].id}'`)).toBe('t')
    }

    // Chốt lại đúng khoảng đó: không còn gì để gom.
    await page.getByTestId('settlement-create').click()
    await page.getByTestId('settlement-form-owner').selectOption(ownerId)
    await page.getByTestId('settlement-form-from').fill('2025-02-10')
    await page.getByTestId('settlement-form-to').fill('2025-02-12')
    await page.getByTestId('settlement-form-submit').click()
    await expect(page.getByTestId('settlement-dialog').getByRole('alert')).toContainText('Không có hoa hồng chưa đối soát nào')
    await page.keyboard.press('Escape')
    assertNoErrors()
  })

  test('the new period shows its history entry and bookings in the detail drawer', async ({ page }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/settlements')
    await page.getByLabel('Kỳ từ ngày').fill('2025-02-01')
    await page.getByLabel('Kỳ đến ngày').fill('2025-02-28')
    await page.getByTestId('settlement-list').getByRole('listitem').first().getByTestId('settlement-detail').click()
    const drawer = page.getByTestId('settlement-drawer')
    await expect(drawer.getByTestId('sd-commission')).toHaveText(vnd(35000))
    await expect(drawer.getByTestId('sd-history-item')).toHaveCount(1)
    await expect(drawer.getByTestId('sd-history-item')).toContainText('Chốt kỳ công nợ')
    await expect(drawer.getByTestId('sd-history-item')).toContainText('Quan Tri Courtly')
    for (const index of [3, 4, 5]) {
      await expect(drawer.getByTestId(`sd-booking-${bookings[index].bookingCode}`)).toBeVisible()
    }
    await expect(drawer.getByTestId('sd-reports-empty')).toBeVisible()
  })

  test('list and detail fit a phone; a failing detail request shows an error with retry', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await open(page)
    await expect(page.getByTestId('settlement-list').getByRole('listitem')).toHaveCount(2)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2345-settlements-mobile')
    await page.getByTestId('settlement-list').getByRole('listitem').first().getByTestId('settlement-detail').click()
    await expect(page.getByTestId('settlement-drawer')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2346-settlement-drawer-mobile')
    await page.getByRole('button', { name: 'Đóng' }).click()

    await page.route('**/api/v1/admin/finance/settlements/*', (route) => route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Không tìm thấy kỳ đối soát.' }) }))
    await page.getByTestId('settlement-list').getByRole('listitem').first().getByTestId('settlement-detail').click()
    await expect(page.getByTestId('settlement-drawer')).toContainText('Không tìm thấy kỳ đối soát')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
  })
})

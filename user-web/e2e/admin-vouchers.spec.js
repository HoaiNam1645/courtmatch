import { expect, test } from '@playwright/test'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  nextMondayIso,
  runSql,
  SEED_ADMIN_EMAIL,
  SEED_OWNER_EMAIL,
  shot,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const vnd = (value) => `${Number(value).toLocaleString('vi-VN')}đ`

function cleanup() {
  deleteE2eVenues()
  runSql("DELETE FROM voucher_redemptions WHERE voucher_id IN (SELECT id FROM vouchers WHERE code LIKE 'E2E-%')")
  runSql("DELETE FROM audit_logs WHERE entity_type = 'vouchers' AND entity_id IN (SELECT id FROM vouchers WHERE code LIKE 'E2E-%')")
  runSql("DELETE FROM vouchers WHERE code LIKE 'E2E-%'")
}

const local = (days) => {
  const date = new Date(Date.now() + days * 86400000)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

test.describe.serial('B4 admin manages vouchers', () => {
  test.beforeAll(cleanup)
  test.afterAll(cleanup)

  async function openPage(page) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/vouchers')
    await expect(page.getByRole('heading', { name: 'Voucher giảm giá' })).toBeVisible()
  }

  async function fillForm(dialog, { code, name, percent, cap = '', from, until, limit = '', elo = '', matches = '', weeks = '' }) {
    if (code !== undefined) await dialog.getByTestId('voucher-form-code').fill(code)
    await dialog.getByTestId('voucher-form-name').fill(name)
    await dialog.getByTestId('voucher-form-percent').fill(percent)
    await dialog.getByTestId('voucher-form-cap').fill(cap)
    if (from) await dialog.getByTestId('voucher-form-from').fill(from)
    if (until) await dialog.getByTestId('voucher-form-until').fill(until)
    await dialog.getByTestId('voucher-form-limit').fill(limit)
    await dialog.getByTestId('voucher-form-elo').fill(elo)
    await dialog.getByTestId('voucher-form-matches').fill(matches)
    await dialog.getByTestId('voucher-form-weeks').fill(weeks)
  }

  test('opens from the finance group and creating validates every field on the screen', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Voucher' }).click()
    await expect(page).toHaveURL('/admin/vouchers')
    await page.getByLabel('Tìm voucher').fill('E2E-')
    await expect(page.getByText('Không có voucher phù hợp').or(page.getByText('Chưa có voucher nào')).first()).toBeVisible()
    await page.getByTestId('voucher-create-open').click()
    const dialog = page.getByTestId('voucher-dialog')
    await dialog.getByTestId('voucher-form-submit').click()
    await expect(dialog.getByText('Mã từ 3 đến 40 ký tự')).toBeVisible()
    await expect(dialog.getByText('Tên voucher từ 2 đến 150 ký tự')).toBeVisible()
    await expect(dialog.getByText('Phần trăm giảm lớn hơn 0 và tối đa 100')).toBeVisible()

    await fillForm(dialog, { code: 'E2E-LOI', name: 'E2E Lỗi', percent: '101', cap: '0', until: local(-1), limit: '0', elo: '-5', matches: '0', weeks: '521' })
    await dialog.getByTestId('voucher-form-submit').click()
    await expect(dialog.getByText('Phần trăm giảm lớn hơn 0 và tối đa 100')).toBeVisible()
    await expect(dialog.getByText('Trần giảm lớn hơn 0')).toBeVisible()
    await expect(dialog.getByText('Hết hạn phải sau thời điểm bắt đầu')).toBeVisible()
    await expect(dialog.getByText('Tổng số lượt là số nguyên từ 1 đến 100.000')).toBeVisible()
    await expect(dialog.getByText('Elo tối thiểu từ 0 đến 5000')).toBeVisible()
    await expect(dialog.getByText('Số trận tối thiểu là số nguyên từ 1')).toBeVisible()
    await expect(dialog.getByText('Số tuần tối thiểu là số nguyên từ 1 đến 520')).toBeVisible()
    await dialog.getByTestId('voucher-form-percent').fill('12.345')
    await dialog.getByTestId('voucher-form-submit').click()
    await expect(dialog.getByText('Phần trăm giảm tối đa 2 chữ số thập phân')).toBeVisible()
    await shot(page, 'b4-admin-dialog-errors')
    expect(runSql("SELECT count(*) FROM vouchers WHERE code LIKE 'E2E-%'")).toBe('0')
    assertNoErrors()
  })

  test('creates vouchers: the code is upper-cased, optional limits stay empty, and the list shows the result', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openPage(page)
    await page.getByTestId('voucher-create-open').click()
    const dialog = page.getByTestId('voucher-dialog')
    await fillForm(dialog, { code: 'e2e-giam10', name: 'E2E Giảm 10%', percent: '10', cap: '30000', limit: '5', elo: '1100', matches: '2', weeks: '1' })
    await dialog.getByTestId('voucher-form-submit').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã tạo voucher E2E-GIAM10')
    const row = page.getByTestId('voucher-E2E-GIAM10')
    await expect(row.getByTestId('voucher-state')).toHaveText('Đang phát hành')
    await expect(row.getByTestId('voucher-discount')).toHaveText(`Giảm 10% (tối đa ${vnd(30000)})`)
    await expect(row).toContainText('Elo ≥ 1.100')
    await expect(row).toContainText('≥ 2 trận có đặt sân')
    await expect(row).toContainText('1 tuần hoạt động liên tiếp')
    await expect(row.getByTestId('voucher-usage')).toContainText('Đã dùng 0/5 lượt')
    await expect(row.getByTestId('voucher-usage')).toContainText(vnd(0))

    await page.getByTestId('voucher-create-open').click()
    await fillForm(page.getByTestId('voucher-dialog'), { code: 'E2E-TATCA', name: 'E2E Cho mọi người', percent: '7.5' })
    await page.getByTestId('voucher-form-submit').click()
    const open = page.getByTestId('voucher-E2E-TATCA')
    await expect(open.getByTestId('voucher-discount')).toHaveText('Giảm 7,5%')
    await expect(open).toContainText('Không yêu cầu điều kiện')
    await expect(open.getByTestId('voucher-usage')).toContainText('Đã dùng 0 lượt')
    expect(runSql("SELECT discount_percent::text || '|' || coalesce(total_limit::text, 'null') FROM vouchers WHERE code = 'E2E-TATCA'")).toBe('7.50|null')
    assertNoErrors()
  })

  test('a duplicate code is refused by the server with a message on the form', async ({ page }) => {
    await openPage(page)
    await page.getByTestId('voucher-create-open').click()
    const dialog = page.getByTestId('voucher-dialog')
    await fillForm(dialog, { code: 'E2E-GIAM10', name: 'E2E Trùng mã', percent: '5' })
    await dialog.getByTestId('voucher-form-submit').click()
    await expect(dialog.getByRole('alert')).toContainText('Ma voucher da ton tai')
    await expect(dialog.getByText('Ma voucher da duoc dung')).toBeVisible()
    await expect(page.getByTestId('voucher-dialog')).toBeVisible()
    expect(runSql("SELECT count(*) FROM vouchers WHERE code = 'E2E-GIAM10'")).toBe('1')
  })

  test('tabs count the states; scheduled and expired vouchers are listed; search narrows the list', async ({ page }) => {
    await openPage(page)
    await page.getByTestId('voucher-create-open').click()
    let dialog = page.getByTestId('voucher-dialog')
    await fillForm(dialog, { code: 'E2E-SAPTOI', name: 'E2E Sắp bắt đầu', percent: '5', from: local(5), until: local(20) })
    await dialog.getByTestId('voucher-form-submit').click()
    await expect(page.getByTestId('admin-notice')).toContainText('E2E-SAPTOI')
    // Voucher hết hạn: tạo bằng SQL vì form chỉ cho ngày hợp lệ ở tương lai gần.
    runSql("INSERT INTO vouchers (code, name, discount_percent, valid_from, valid_until) VALUES ('E2E-CU', 'E2E Hết hạn', 5, now() - interval '10 days', now() - interval '1 day')")
    await page.reload()
    await page.getByLabel('Tìm voucher').fill('E2E-')
    await expect(page.getByTestId('voucher-tab-all')).toContainText('4')
    await expect(page.getByTestId('voucher-tab-active')).toContainText('2')
    await expect(page.getByTestId('voucher-tab-scheduled')).toContainText('1')
    await expect(page.getByTestId('voucher-tab-expired')).toContainText('1')
    await expect(page.getByTestId('voucher-tab-locked')).toContainText('0')
    await page.getByTestId('voucher-tab-scheduled').click()
    await expect(page.getByTestId('voucher-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('voucher-E2E-SAPTOI').getByTestId('voucher-state')).toHaveText('Sắp bắt đầu')
    await page.getByTestId('voucher-tab-expired').click()
    await expect(page.getByTestId('voucher-E2E-CU').getByTestId('voucher-state')).toHaveText('Hết hạn')
    await page.getByTestId('voucher-tab-locked').click()
    await expect(page.getByText('Không có voucher phù hợp')).toBeVisible()
    await page.getByTestId('voucher-tab-all').click()
    await page.getByLabel('Tìm voucher').fill('giam10')
    await expect(page.getByTestId('voucher-list').getByRole('listitem')).toHaveCount(1)
    await page.getByLabel('Tìm voucher').fill('Cho mọi người')
    await expect(page.getByTestId('voucher-E2E-TATCA')).toBeVisible()
    await page.getByLabel('Tìm voucher').fill('khong-co-gi-ca')
    await expect(page.getByText('Không có voucher phù hợp')).toBeVisible()
  })

  test('locking stops players from seeing or using it; unlocking brings it back', async ({ page }) => {
    await openPage(page)
    await page.getByLabel('Tìm voucher').fill('E2E-TATCA')
    await page.getByTestId('voucher-E2E-TATCA').getByTestId('voucher-lock').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã khóa voucher E2E-TATCA')
    await expect(page.getByTestId('voucher-E2E-TATCA').getByTestId('voucher-state')).toHaveText('Đã khóa')
    await expect(page.getByTestId('voucher-E2E-TATCA').getByTestId('voucher-lock')).toContainText('Mở khóa')
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const codes = async () => (await (await page.request.get(`${API_BASE_URL}/api/v1/vouchers`, { headers: { Authorization: `Bearer ${player.token}` } })).json()).map((item) => item.code)
    expect(await codes()).not.toContain('E2E-TATCA')

    await openPage(page)
    await page.getByLabel('Tìm voucher').fill('E2E-TATCA')
    await page.getByTestId('voucher-E2E-TATCA').getByTestId('voucher-lock').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã mở khóa voucher E2E-TATCA')
    await expect(page.getByTestId('voucher-E2E-TATCA').getByTestId('voucher-state')).toHaveText('Đang phát hành')
    const again = await loginByApi(page, 'an.nguyen@example.com')
    const list = await (await page.request.get(`${API_BASE_URL}/api/v1/vouchers`, { headers: { Authorization: `Bearer ${again.token}` } })).json()
    expect(list.map((item) => item.code)).toContain('E2E-TATCA')
  })

  test('usage shows in the drawer with the cost Courtly bears; cancelled bookings give the voucher back; the discount can no longer be edited', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Voucher Admin')
    makeVenueBookable(venue.id)
    runSql("UPDATE vouchers SET min_elo = NULL, min_booking_matches = NULL, min_active_weeks = NULL WHERE code = 'E2E-GIAM10'")
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const startAt = new Date(`${nextMondayIso()}T09:00:00+07:00`).toISOString()
    const booked = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, { headers: { Authorization: `Bearer ${player.token}` }, data: { courtId: venue.courts[0].id, startTime: startAt, durationMinutes: 60, voucherCode: 'E2E-GIAM10' } })
    expect(booked.status(), await booked.text()).toBe(201)
    const booking = await booked.json()
    const cost = Number(booking.discountAmount)
    expect(cost).toBeGreaterThan(0)

    await openPage(page)
    await page.getByLabel('Tìm voucher').fill('E2E-GIAM10')
    await expect(page.getByTestId('voucher-E2E-GIAM10').getByTestId('voucher-usage')).toContainText(`Đã dùng 1/5 lượt · Courtly chịu ${vnd(cost)}`)
    await expect(page.getByTestId('voucher-total-cost')).toHaveText(vnd(cost))
    await page.getByTestId('voucher-E2E-GIAM10').getByTestId('voucher-view').click()
    const drawer = page.getByTestId('voucher-drawer')
    await expect(drawer.getByTestId('voucher-drawer-used')).toHaveText('1 / 5')
    await expect(drawer.getByTestId('voucher-drawer-cost')).toHaveText(vnd(cost))
    await expect(drawer.getByTestId('voucher-drawer-redemptions')).toContainText(booking.bookingCode)
    await expect(drawer.getByTestId('voucher-redemption-status')).toHaveText('Đang dùng')
    await expect(drawer.getByTestId('voucher-drawer-conditions')).toContainText('Không yêu cầu điều kiện')
    await shot(page, 'b4-admin-drawer')
    await page.keyboard.press('Escape')

    // Hủy booking: lượt dùng được trả lại, chi phí Courtly chịu về 0.
    const cancelled = await page.request.post(`${API_BASE_URL}/api/v1/bookings/${booking.id}/cancel`, { headers: { Authorization: `Bearer ${player.token}` }, data: { reason: 'E2E hủy' } })
    expect(cancelled.status()).toBe(200)
    await page.reload()
    await page.getByLabel('Tìm voucher').fill('E2E-GIAM10')
    await expect(page.getByTestId('voucher-E2E-GIAM10').getByTestId('voucher-usage')).toContainText(`Đã dùng 0/5 lượt · Courtly chịu ${vnd(0)}`)
    await page.getByTestId('voucher-E2E-GIAM10').getByTestId('voucher-view').click()
    await expect(page.getByTestId('voucher-drawer').getByTestId('voucher-redemption-status')).toHaveText('Đã trả lại')
    await page.keyboard.press('Escape')

    // Đã từng có lượt dùng (kể cả đã trả lại): không đổi được mức giảm, vẫn sửa được tên.
    await page.getByTestId('voucher-E2E-GIAM10').getByTestId('voucher-edit').click()
    const dialog = page.getByTestId('voucher-dialog')
    await expect(dialog.getByTestId('voucher-form-code')).toBeDisabled()
    await expect(dialog.getByTestId('voucher-form-percent')).toBeDisabled()
    await expect(dialog.getByTestId('voucher-form-cap')).toBeDisabled()
    await expect(dialog.getByText('đã có lượt dùng nên không đổi được mức giảm')).toBeVisible()
    await expect(dialog.getByTestId('voucher-form-submit')).toBeEnabled()
    await dialog.getByTestId('voucher-form-name').fill('E2E Giảm 10% đổi tên')
    await dialog.getByTestId('voucher-form-limit').fill('50')
    await dialog.getByTestId('voucher-form-submit').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã cập nhật voucher E2E-GIAM10')
    await expect(page.getByTestId('voucher-E2E-GIAM10')).toContainText('E2E Giảm 10% đổi tên')
    await expect(page.getByTestId('voucher-E2E-GIAM10').getByTestId('voucher-usage')).toContainText('0/50')
    assertNoErrors()
  })

  test('an unused voucher can still have its discount edited', async ({ page }) => {
    await openPage(page)
    await page.getByLabel('Tìm voucher').fill('E2E-TATCA')
    await page.getByTestId('voucher-E2E-TATCA').getByTestId('voucher-edit').click()
    const dialog = page.getByTestId('voucher-dialog')
    await expect(dialog.getByTestId('voucher-form-percent')).toBeEnabled()
    await dialog.getByTestId('voucher-form-percent').fill('15')
    await dialog.getByTestId('voucher-form-cap').fill('25000')
    await dialog.getByTestId('voucher-form-submit').click()
    await expect(page.getByTestId('voucher-E2E-TATCA').getByTestId('voucher-discount')).toHaveText(`Giảm 15% (tối đa ${vnd(25000)})`)
    expect(runSql("SELECT discount_percent::text FROM vouchers WHERE code = 'E2E-TATCA'")).toBe('15.00')
  })

  test('shows an error state with retry, fits a mobile screen, and players cannot open the page', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    let fail = true
    await page.route('**/api/v1/admin/vouchers?**', async (route) => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) })
      else await route.continue()
    })
    await page.goto('/admin/vouchers')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
    fail = false
    await page.getByRole('button', { name: /Thử lại/ }).click()
    await expect(page.getByTestId('voucher-totals')).toBeVisible()

    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const mobile = await context.newPage()
    await openPage(mobile)
    await mobile.getByLabel('Tìm voucher').fill('E2E-')
    await expect(mobile.getByTestId('voucher-list')).toBeVisible()
    expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(mobile, 'b4-admin-mobile')
    await mobile.getByTestId('voucher-create-open').click()
    await expect(mobile.getByTestId('voucher-dialog')).toBeVisible()
    expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await context.close()

    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto('/admin/vouchers')
    await expect(page.getByRole('heading', { name: 'Voucher giảm giá' })).toHaveCount(0)
  })
})

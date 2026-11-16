import { expect, test } from '@playwright/test'
import {
  createAcceptedBookings,
  createE2eVenue,
  createTemporaryOwner,
  deleteE2eVenues,
  deleteTemporaryOwner,
  loginByApi,
  makeVenueBookable,
  runSql,
  SEED_ADMIN_EMAIL,
  SEED_OWNER_EMAIL,
  shot,
  watchRuntimeErrors,
} from './helpers'

const vnd = (value) => `${Number(value).toLocaleString('vi-VN')}đ`
const EMPTY_OWNER = 'e2e.history.owner@example.com'

test.describe.serial('2.3.50 admin views the commission payment history of an owner', () => {
  let startedAt
  let ownerId
  let emptyOwnerId

  test.beforeAll(() => {
    deleteE2eVenues()
    startedAt = runSql('SELECT now()')
  })
  test.afterAll(() => {
    deleteE2eVenues()
    deleteTemporaryOwner(EMPTY_OWNER)
    runSql('UPDATE court_owner_profiles SET commission_credit = 0, restriction_level = 0, restriction_changed_at = NULL')
    runSql(`DELETE FROM audit_logs WHERE created_at >= '${startedAt}' AND action LIKE 'owner.restriction%'`)
  })

  test('builds a history: partial period, two confirmed (one recorded by admin with an offset), one rejected, one pending', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Lịch Sử Thanh Toán')
    makeVenueBookable(venue.id)
    const bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '07:00', '08:00'] })
    ownerId = runSql(`SELECT owner_id FROM venues WHERE id = '${venue.id}'`).split('\n')[0]
    const settlement = runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, status, commission_total, amount_due, amount_paid, note)
      VALUES ('${ownerId}', '2024-12-01', '2024-12-07', 'partial', 30000, 30000, 10000, 'E2E kỳ lịch sử') RETURNING id`).split('\n')[0]
    const insert = (booking, amount, status, settlementId, extra = '') => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at, settlement_id ${extra ? ', voucher_discount, courtly_owed' : ''})
      SELECT b.id, v.owner_id, 180000, 'percentage', 10, ${amount}, '${status}', now() - interval '20 days', now() + interval '3 days', ${settlementId ? `'${settlementId}'` : 'NULL'} ${extra}
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    insert(bookings[0], 30000, 'due', settlement)
    insert(bookings[1], 8000, 'due', null)
    insert(bookings[2], 0, 'settled', null, ', 22000, 4000')
    runSql(`UPDATE court_owner_profiles SET commission_credit = 2500, restriction_level = 1 WHERE user_id = '${ownerId}'`)
    const admin = runSql(`SELECT id FROM users WHERE email = '${SEED_ADMIN_EMAIL}'`)
    const report = (ref, settlementId, amount, status, paidDays, extra) => runSql(`
      INSERT INTO commission_payment_reports (owner_id, settlement_id, amount, paid_on, reference_code, status ${extra ? ', ' + extra.columns : ''})
      VALUES ('${ownerId}', ${settlementId ? `'${settlementId}'` : 'NULL'}, ${amount}, current_date - ${paidDays}, '${ref}', '${status}' ${extra ? ', ' + extra.values : ''})`)
    report('E2E-HIS-1', settlement, 10000, 'confirmed', 10, { columns: 'reviewed_by, reviewed_at, courtly_offset_amount', values: `'${admin}', now(), 1000` })
    report('E2E-HIS-2', null, 5000, 'confirmed', 3, { columns: 'reviewed_by, reviewed_at, recorded_by, note', values: `'${admin}', now(), '${admin}', 'Thu tiền mặt'` })
    report('E2E-HIS-3', null, 9000, 'rejected', 2, { columns: 'reviewed_by, reviewed_at, review_note', values: `'${admin}', now(), 'Sai số tiền chuyển khoản'` })
    report('E2E-HIS-4', settlement, 7000, 'pending_review', 0)
    createTemporaryOwner(EMPTY_OWNER)
    emptyOwnerId = runSql(`SELECT id FROM users WHERE email = '${EMPTY_OWNER}'`)
  })

  async function open(page, url = '/admin/commission-payments') {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto(url)
  }

  test('without choosing an owner there is no summary; choosing one shows the whole history and an owner summary', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page)
    await expect(page.getByTestId('owner-summary')).toHaveCount(0)
    await page.getByLabel('Lọc theo chủ sân').selectOption(ownerId)
    // Chọn chủ sân thì tự chuyển sang xem mọi trạng thái để thấy cả lịch sử.
    await expect(page.getByTestId('payment-tab-all')).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByTestId('payment-list').getByRole('listitem')).toHaveCount(4)

    const summary = page.getByTestId('owner-summary')
    await expect(summary.getByTestId('owner-summary-name')).toContainText('Nguyen Huu Thang')
    await expect(summary.getByTestId('owner-summary-confirmed')).toHaveText(vnd(15000))
    await expect(summary.getByTestId('owner-summary-pending')).toHaveText(vnd(7000))
    await expect(summary.getByTestId('owner-summary-outstanding')).toHaveText(vnd(38000))
    await expect(summary.getByTestId('owner-summary-credit')).toHaveText(vnd(2500))
    await expect(summary.getByTestId('owner-summary-owed')).toHaveText(vnd(4000))
    await expect(summary.getByTestId('owner-summary-restriction')).toHaveText('Mức 1')
    await expect(page.getByTestId('payment-total-count')).toHaveText('4')
    await expect(page.getByTestId('payment-total-amount')).toHaveText(vnd(31000))
    await shot(page, '2350-history-desktop')
    assertNoErrors()
  })

  test('each entry shows its status, target, who handled it, the admin-recorded badge, the Courtly offset and the rejection reason; pending first', async ({ page }) => {
    await open(page, `/admin/commission-payments?ownerId=${ownerId}`)
    const rows = page.getByTestId('payment-list').getByRole('listitem')
    await expect(rows).toHaveCount(4)
    await expect(rows.first().getByTestId('payment-status')).toHaveText('Chờ Courtly xác nhận')

    const one = page.getByTestId('payment-E2E-HIS-1')
    await expect(one.getByTestId('payment-status')).toHaveText('Đã xác nhận')
    await expect(one.getByTestId('payment-target')).toContainText('01/12/2024 – 07/12/2024')
    await expect(one.getByTestId('payment-reviewed')).toContainText('Quan Tri Courtly')
    await expect(one.getByTestId('payment-reviewed')).toContainText('bù trừ 1.000đ')
    await expect(one.getByTestId('payment-recorded')).toHaveCount(0)

    const two = page.getByTestId('payment-E2E-HIS-2')
    await expect(two.getByTestId('payment-recorded')).toHaveText('Admin ghi nhận')
    await expect(two.getByTestId('payment-target')).toContainText('Công nợ chung')
    await expect(two).toContainText('Thu tiền mặt')

    const three = page.getByTestId('payment-E2E-HIS-3')
    await expect(three.getByTestId('payment-status')).toHaveText('Bị từ chối')
    await expect(three.getByTestId('payment-reviewed')).toContainText('Sai số tiền chuyển khoản')
  })

  test('tabs, date range and search narrow the history while the owner summary stays the same', async ({ page }) => {
    await open(page, `/admin/commission-payments?ownerId=${ownerId}`)
    await expect(page.getByTestId('payment-tab-count-confirmed')).toHaveText('2')
    await expect(page.getByTestId('payment-tab-count-rejected')).toHaveText('1')
    await page.getByTestId('payment-tab-confirmed').click()
    await expect(page.getByTestId('payment-list').getByRole('listitem')).toHaveCount(2)
    await expect(page.getByTestId('payment-total-amount')).toHaveText(vnd(15000))
    await expect(page.getByTestId('owner-summary-confirmed')).toHaveText(vnd(15000))

    const fiveDaysAgo = new Date(Date.now() - 5 * 86400000).toISOString().slice(0, 10)
    await page.getByLabel('Thanh toán từ ngày').fill(fiveDaysAgo)
    await expect(page.getByTestId('payment-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('payment-E2E-HIS-2')).toBeVisible()
    await expect(page.getByTestId('owner-summary-pending')).toHaveText(vnd(7000))

    await page.getByLabel('Thanh toán từ ngày').fill('')
    await page.getByTestId('payment-tab-all').click()
    await page.getByLabel('Tìm thanh toán').fill('HIS-3')
    await expect(page.getByTestId('payment-list').getByRole('listitem')).toHaveCount(1)
    await page.getByLabel('Thanh toán từ ngày').fill('2030-01-02')
    await page.getByLabel('Thanh toán đến ngày').fill('2030-01-01')
    await expect(page.getByTestId('payment-range-error')).toBeVisible()
  })

  test('an owner with no payments shows an empty history and a zero summary', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await open(page, `/admin/commission-payments?ownerId=${emptyOwnerId}`)
    await expect(page.getByTestId('owner-summary-name')).toContainText('E2E Chu San Tam')
    await expect(page.getByTestId('owner-summary-confirmed')).toHaveText(vnd(0))
    await expect(page.getByTestId('owner-summary-outstanding')).toHaveText(vnd(0))
    await expect(page.getByTestId('owner-summary-restriction')).toHaveText('Không')
    await expect(page.getByText('Không có thanh toán nào')).toBeVisible()
    assertNoErrors()
  })

  test('clearing the owner returns to the pending queue; an unknown owner in the link shows an error with retry', async ({ page }) => {
    await open(page, `/admin/commission-payments?ownerId=${ownerId}`)
    await page.getByLabel('Lọc theo chủ sân').selectOption('')
    await expect(page.getByTestId('owner-summary')).toHaveCount(0)
    await expect(page.getByTestId('payment-tab-pending_review')).toHaveAttribute('aria-selected', 'true')

    await open(page, '/admin/commission-payments?ownerId=00000000-0000-0000-0000-000000000000')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
  })

  test('the history and summary fit a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await open(page, `/admin/commission-payments?ownerId=${ownerId}`)
    await expect(page.getByTestId('owner-summary')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2350-history-mobile')
  })
})

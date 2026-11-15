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
const HOURS = ['06:00', '07:00', '08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00']

test.describe.serial('2.3.43 admin views reconciliation transactions', () => {
  let venueA
  let venueB
  let codes = {}

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('builds two venues with commissions of every status, one settlement period and past dates', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    venueA = await createE2eVenue(page, owner.token, 'E2E Sân Đối Soát A')
    venueB = await createE2eVenue(page, owner.token, 'E2E Sân Đối Soát B')
    makeVenueBookable(venueA.id)
    makeVenueBookable(venueB.id)
    const a = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue: venueA, times: HOURS })
    const b = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue: venueB, times: HOURS })
    const ownerId = runSql(`SELECT owner_id FROM venues WHERE id = '${venueA.id}'`)
    const settlementId = runSql(`
      INSERT INTO commission_settlements (owner_id, period_from, period_to, status, note)
      VALUES ('${ownerId}', '2020-03-01', '2020-03-31', 'pending_review', 'E2E kỳ đối soát') RETURNING id`).split('\n')[0]
    const insert = (booking, gross, amount, status, at, dueExpr, settlement = null) => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at, settlement_id, settled_at)
      SELECT b.id, v.owner_id, ${gross}, 'percentage', 10, ${amount}, '${status}', timestamp '${at}' AT TIME ZONE 'Asia/Ho_Chi_Minh', ${dueExpr},
             ${settlement ? `'${settlement}'` : 'NULL'}, ${status === 'settled' ? `timestamp '${at}' AT TIME ZONE 'Asia/Ho_Chi_Minh' + interval '2 days'` : 'NULL'}
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    const past = "now() - interval '3 days'"
    const future = "now() + interval '3 days'"
    insert(a[0], 100000, 10000, 'settled', '2020-03-10 09:00', past, settlementId)
    insert(a[1], 200000, 20000, 'due', '2020-03-11 09:00', past, settlementId)
    insert(a[2], 150000, 15000, 'adjusted', '2020-03-12 09:00', future)
    insert(b[0], 300000, 30000, 'pending', '2020-03-13 09:00', future)
    insert(b[1], 400000, 40000, 'suspended', '2020-03-14 09:00', past)
    insert(b[2], 500000, 50000, 'waived', '2020-03-15 09:00', past)
    // Thêm 18 giao dịch tháng 8/2020 để có nhiều trang.
    ;[...a.slice(3), ...b.slice(3)].forEach((booking, index) => insert(booking, 100000, 1000, 'due', `2020-08-${String(1 + index).padStart(2, '0')} 10:00`, future))
    codes = { settled: a[0].bookingCode, due: a[1].bookingCode, adjusted: a[2].bookingCode, pending: b[0].bookingCode, suspended: b[1].bookingCode, waived: b[2].bookingCode }
  })

  async function open(page, params = {}) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-transactions')
    await page.getByLabel('Ghi nhận từ ngày').fill(params.from ?? '2020-03-01')
    await page.getByLabel('Ghi nhận đến ngày').fill(params.to ?? '2020-03-31')
  }

  test('opens from the finance group; each row shows booking, venue, owner, amounts, status, due date and settlement period', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Giao dịch đối soát' }).click()
    await expect(page).toHaveURL('/admin/commission-transactions')
    await expect(page.getByRole('heading', { name: 'Giao dịch đối soát' })).toBeVisible()
    await page.getByLabel('Ghi nhận từ ngày').fill('2020-03-01')
    await page.getByLabel('Ghi nhận đến ngày').fill('2020-03-31')

    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(6)
    await expect(page.getByTestId('tx-total-count')).toHaveText('6')
    await expect(page.getByTestId('tx-total-gross')).toHaveText(vnd(1650000))
    await expect(page.getByTestId('tx-total-commission')).toHaveText(vnd(165000))

    const settled = page.getByTestId(`tx-${codes.settled}`)
    await expect(settled.getByTestId('tx-status')).toHaveText('Đã đối soát')
    await expect(settled.getByTestId('tx-commission')).toHaveText(vnd(10000))
    await expect(settled).toContainText('E2E Sân Đối Soát A')
    await expect(settled).toContainText('Chủ sân')
    await expect(settled.getByTestId('tx-settlement')).toContainText('01/03/2020 – 31/03/2020')
    await expect(settled.getByTestId('tx-settlement')).toContainText('Chờ Courtly xác nhận')
    await expect(settled.getByTestId('tx-settled')).toBeVisible()
    await expect(settled.getByTestId('tx-due')).toContainText('Hạn thanh toán')
    await expect(page.getByTestId(`tx-${codes.pending}`).getByTestId('tx-settlement')).toHaveText('Chưa gán kỳ đối soát')
    await expect(page.getByTestId(`tx-${codes.pending}`).getByTestId('tx-settled')).toHaveCount(0)
    await shot(page, '23243-transactions-desktop')
    assertNoErrors()
  })

  test('overdue amounts are highlighted, and only unpaid ones past their due date', async ({ page }) => {
    await open(page)
    await expect(page.getByTestId(`tx-${codes.due}`).getByTestId('tx-overdue')).toHaveText('Quá hạn')
    for (const key of ['settled', 'adjusted', 'pending', 'suspended', 'waived']) {
      await expect(page.getByTestId(`tx-${codes[key]}`).getByTestId('tx-overdue')).toHaveCount(0)
    }
    await page.getByTestId('tx-overdue-only').check()
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('tx-total-commission')).toHaveText(vnd(20000))
    await page.getByTestId('tx-overdue-only').uncheck()
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(6)
  })

  test('filters by status, venue and settlement period; totals follow the filter; the filters can be cleared', async ({ page }) => {
    await open(page)
    await page.getByLabel('Lọc theo trạng thái').selectOption('suspended')
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('tx-total-commission')).toHaveText(vnd(40000))
    await page.getByLabel('Lọc theo trạng thái').selectOption('all')

    await page.getByLabel('Lọc theo sân').selectOption(venueB.id)
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(3)
    await expect(page.getByTestId('tx-total-commission')).toHaveText(vnd(120000))
    await page.getByLabel('Lọc theo sân').selectOption('')

    await page.getByLabel('Lọc theo kỳ đối soát').selectOption(runSql("SELECT id FROM commission_settlements WHERE note = 'E2E kỳ đối soát'"))
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(2)
    await expect(page.getByTestId('tx-total-commission')).toHaveText(vnd(30000))
    await shot(page, '23243-transactions-settlement')

    await page.getByTestId('tx-clear').click()
    await expect(page.getByLabel('Ghi nhận từ ngày')).toHaveValue('')
    await expect(page.getByLabel('Lọc theo kỳ đối soát')).toHaveValue('')
  })

  test('searches by booking code (any case, partial) and shows the empty state', async ({ page }) => {
    await open(page)
    await page.getByLabel('Tìm mã booking').fill(codes.waived.toLowerCase())
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId(`tx-${codes.waived}`).getByTestId('tx-status')).toHaveText('Được miễn')
    await page.getByLabel('Tìm mã booking').fill('KHONG-CO-MA-NAY')
    await expect(page.getByText('Không có giao dịch nào')).toBeVisible()
    await expect(page.getByTestId('tx-list')).toHaveCount(0)
    await page.getByRole('button', { name: 'Xóa tìm kiếm' }).click()
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(6)
  })

  test('paginates a long list and keeps the filtered totals across pages', async ({ page }) => {
    await open(page, { from: '2020-08-01', to: '2020-08-31' })
    await expect(page.getByTestId('tx-total-count')).toHaveText('18')
    await expect(page.getByTestId('tx-total-commission')).toHaveText(vnd(18000))
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(18)
    // 20 dòng một trang: mở rộng khoảng ngày để có 24 giao dịch và sang trang hai.
    await page.getByLabel('Ghi nhận từ ngày').fill('2020-03-01')
    await page.getByLabel('Ghi nhận đến ngày').fill('2020-08-31')
    await expect(page.getByTestId('tx-total-count')).toHaveText('24')
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(20)
    await expect(page.getByText('Trang 1/2 · 24 giao dịch')).toBeVisible()
    await page.getByRole('button', { name: 'Trang sau' }).click()
    await expect(page.getByText('Trang 2/2 · 24 giao dịch')).toBeVisible()
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(4)
    await expect(page.getByTestId('tx-total-count')).toHaveText('24')
    await page.getByRole('button', { name: 'Trang trước' }).click()
    await expect(page.getByText('Trang 1/2 · 24 giao dịch')).toBeVisible()
    // Đổi bộ lọc quay về trang đầu.
    await page.getByRole('button', { name: 'Trang sau' }).click()
    await page.getByLabel('Lọc theo trạng thái').selectOption('settled')
    await expect(page.getByTestId('tx-list').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByRole('button', { name: 'Trang sau' })).toHaveCount(0)
  })

  test('rejects an inverted date range on screen, shows an error state with retry, fits a mobile screen, blocks players', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/commission-transactions')
    await page.getByLabel('Ghi nhận từ ngày').fill('2020-05-01')
    await page.getByLabel('Ghi nhận đến ngày').fill('2020-04-01')
    await expect(page.getByTestId('tx-range-error')).toBeVisible()

    let fail = true
    await page.route('**/api/v1/admin/finance/commission-transactions**', async (route) => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) })
      else await route.continue()
    })
    await page.getByLabel('Ghi nhận đến ngày').fill('2020-06-01')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
    fail = false
    await page.getByRole('button', { name: /Thử lại/ }).click()
    // Khoảng ngày này không có giao dịch nào nên sau khi thử lại phải hiện trạng thái rỗng, không phải lỗi.
    await expect(page.getByText('Không có giao dịch nào')).toBeVisible()
    await expect(page.getByRole('button', { name: /Thử lại/ })).toHaveCount(0)

    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const mobile = await context.newPage()
    await open(mobile)
    await expect(mobile.getByTestId('tx-list').getByRole('listitem')).toHaveCount(6)
    expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(mobile, '23243-transactions-mobile')
    await context.close()

    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto('/admin/commission-transactions')
    await expect(page.getByRole('heading', { name: 'Giao dịch đối soát' })).toHaveCount(0)
  })
})

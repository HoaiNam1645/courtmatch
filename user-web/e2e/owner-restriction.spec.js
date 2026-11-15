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

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('B2 owner restriction by overdue commission debt', () => {
  let venue
  let bookings
  let thresholdsBackup
  let startedAt

  test.beforeAll(() => {
    deleteE2eVenues()
    startedAt = runSql('SELECT now()')
    thresholdsBackup = runSql("SELECT level || '|' || enabled || '|' || coalesce(min_overdue_amount::text, 'NULL') || '|' || coalesce(min_overdue_days::text, 'NULL') FROM debt_restriction_levels ORDER BY level")
  })
  test.afterAll(() => {
    deleteE2eVenues()
    for (const line of thresholdsBackup.split('\n')) {
      const [level, enabled, amount, days] = line.split('|')
      runSql(`UPDATE debt_restriction_levels SET enabled = ${enabled === 't' || enabled === 'true'}, min_overdue_amount = ${amount === 'NULL' ? 'NULL' : amount}, min_overdue_days = ${days === 'NULL' ? 'NULL' : days}, updated_by = NULL WHERE level = ${level}`)
    }
    runSql("UPDATE court_owner_profiles SET restriction_level = 0, restriction_changed_at = NULL")
    runSql(`DELETE FROM audit_logs WHERE action = 'owner.restriction.change' AND created_at >= '${startedAt}'`)
  })

  /** Admin đặt ngưỡng qua giao diện; backend áp dụng ngay cho mọi chủ sân. */
  async function setThresholds(page, { l1, l2 }) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/fees')
    await page.getByTestId('restriction-edit').click()
    const dialog = page.getByTestId('restriction-dialog')
    for (const [level, config] of [[1, l1], [2, l2]]) {
      const box = dialog.getByTestId(`restriction-enabled-${level}`)
      if (config.on) await box.check()
      else await box.uncheck()
      await dialog.getByTestId(`restriction-amount-${level}`).fill(config.amount ?? '')
      await dialog.getByTestId(`restriction-days-${level}`).fill(config.days ?? '')
    }
    await dialog.getByTestId('restriction-submit').click()
    await expect(page.getByTestId('admin-notice')).toContainText('Đã cập nhật ngưỡng')
  }

  const storedLevel = () => Number(runSql(`SELECT restriction_level FROM court_owner_profiles WHERE user_id = (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`))

  test('builds a venue whose owner has 4 overdue commissions (day 20 overdue, 40.000đ in total)', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Bị Hạn Chế')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00', '10:00', '12:00'] })
    bookings.forEach((booking, index) => runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
      SELECT b.id, v.owner_id, 100000, 'percentage', 10, 10000, 'due', now() - interval '30 days', now() - interval '${index === 0 ? 20 : 5} days'
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`))
    runSql("UPDATE debt_restriction_levels SET updated_by = NULL")
  })

  test('with every level off, an overdue owner is not restricted and sees no restriction banner', async ({ page }) => {
    await setThresholds(page, { l1: { on: false, amount: '30000', days: '14' }, l2: { on: false, amount: '60000', days: '40' } })
    expect(storedLevel()).toBe(0)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-overdue-warning')).toBeVisible()
    await expect(page.getByTestId('debt-restriction')).toHaveCount(0)
    await expect(page.getByTestId('debt-restriction-warning')).toHaveCount(0)
  })

  test('before reaching the threshold the owner gets a warning naming the first enabled level', async ({ page }) => {
    await setThresholds(page, { l1: { on: true, amount: '90000', days: '' }, l2: { on: false, amount: '', days: '' } })
    expect(storedLevel()).toBe(0)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-restriction-warning')).toContainText('công nợ quá hạn từ 90.000đ')
    await expect(page.getByTestId('debt-restriction-warning')).toContainText('chặn nhận booking mới')
    await expect(page.getByTestId('debt-restriction')).toHaveCount(0)
  })

  test('level 1 (amount reached): banner explains the reason; players cannot book but still see the venue; accepted bookings keep working', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/api/v1/bookings'] })
    await setThresholds(page, { l1: { on: true, amount: '30000', days: '' }, l2: { on: false, amount: '', days: '' } })
    expect(storedLevel()).toBe(1)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-restriction-title')).toHaveText('Sân của bạn đang bị chặn nhận booking mới')
    await expect(page.getByTestId('debt-restriction-reason')).toContainText('công nợ quá hạn 40.000đ đã đạt ngưỡng 30.000đ')
    await expect(page.getByTestId('debt-restriction')).toContainText('booking đã nhận trước đó vẫn được xử lý')
    await shot(page, 'b2-level1-owner')

    const player = await loginByApi(page, 'an.nguyen@example.com')
    const blocked = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${player.token}` },
      data: { courtId: venue.courts[0].id, startTime: '2099-01-05T03:00:00Z', durationMinutes: 60, note: 'E2E' },
    })
    expect(blocked.status()).toBe(409)
    expect((await blocked.json()).code).toBe('OWNER_RESTRICTED')
    const detail = await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.id}`)
    expect(detail.status()).toBe(200)
    const found = await (await page.request.get(`${API_BASE_URL}/api/v1/venues?q=${encodeURIComponent('E2E Sân Bị Hạn Chế')}`)).json()
    expect(found.totalElements).toBe(1)
    assertNoErrors()
  })

  test('level 2 (days reached): the venue disappears for players while the owner still manages it', async ({ page }) => {
    await setThresholds(page, { l1: { on: true, amount: '30000', days: '' }, l2: { on: true, amount: '', days: '20' } })
    expect(storedLevel()).toBe(2)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-restriction-title')).toHaveText('Sân của bạn đang bị tạm ẩn khỏi người chơi')
    await expect(page.getByTestId('debt-restriction-reason')).toContainText('đã quá hạn 20 ngày, đạt ngưỡng 20 ngày')
    await shot(page, 'b2-level2-owner')
    const ownerVenue = await page.request.get(`${API_BASE_URL}/api/v1/owner/venues/${venue.id}`, { headers: { Authorization: `Bearer ${(await loginByApi(page, SEED_OWNER_EMAIL)).token}` } })
    expect(ownerVenue.status()).toBe(200)

    expect((await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.id}`)).status()).toBe(404)
    const found = await (await page.request.get(`${API_BASE_URL}/api/v1/venues?q=${encodeURIComponent('E2E Sân Bị Hạn Chế')}`)).json()
    expect(found.totalElements).toBe(0)
    await page.goto(`/venues/${venue.slug}`)
    await expect(page.getByRole('heading', { name: 'E2E Sân Bị Hạn Chế' })).toHaveCount(0)
    await page.goto('/venues')
    await page.getByPlaceholder(/Tìm/).first().fill('E2E Sân Bị Hạn Chế')
    await expect(page.getByText('E2E Sân Bị Hạn Chế')).toHaveCount(0)
  })

  test('paying the debt lifts the restriction as soon as the thresholds are recomputed; the venue is public again', async ({ page }) => {
    runSql(`UPDATE booking_commissions SET status = 'settled', settled_at = now() WHERE booking_id IN (SELECT id FROM bookings WHERE venue_id = '${venue.id}')`)
    // Job tính lại tắt khi chạy E2E: lưu lại ngưỡng để backend tính lại ngay.
    await setThresholds(page, { l1: { on: true, amount: '30001', days: '' }, l2: { on: true, amount: '', days: '21' } })
    expect(storedLevel()).toBe(0)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-restriction')).toHaveCount(0)
    expect((await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.id}`)).status()).toBe(200)
    expect(runSql("SELECT count(*) FROM audit_logs WHERE action = 'owner.restriction.change' AND created_at >= '" + startedAt + "'")).toBe('3')
  })

  test('the owner banner fits a mobile screen', async ({ browser }) => {
    runSql(`UPDATE booking_commissions SET status = 'due', settled_at = NULL WHERE booking_id IN (SELECT id FROM bookings WHERE venue_id = '${venue.id}')`)
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const adminPage = await context.newPage()
    await setThresholds(adminPage, { l1: { on: true, amount: '30000', days: '' }, l2: { on: false, amount: '', days: '' } })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-restriction')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(page, 'b2-mobile')
    await context.close()
  })
})

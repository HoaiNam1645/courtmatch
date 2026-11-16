import { expect, test } from '@playwright/test'
import {
  createE2eUser, createE2eVenue, deleteE2eUsers, deleteE2eVenues, loginByApi, makeVenueBookable, nextMondayIso, runSql, SEED_ADMIN_EMAIL,
  SEED_OWNER_EMAIL, shot, watchRuntimeErrors,
} from './helpers'

const API = 'http://127.0.0.1:8080'
const PAYER = 'e2e-pay-notify@example.com'

test.describe.serial('2.3.55 payment status notifications reach the owner, the player and the owner on admin decisions', () => {
  let startedAt
  let venue
  let owner
  let payer
  let admin
  let ownerId
  const bookings = []

  test.beforeAll(() => {
    deleteE2eVenues()
    deleteE2eUsers()
    startedAt = runSql('SELECT now()')
    createE2eUser({ tag: 'pay-notify', name: 'E2E Người Báo Thanh Toán' })
  })
  test.afterAll(() => {
    runSql(`DELETE FROM notifications WHERE created_at >= '${startedAt}' AND user_id IN (SELECT id FROM users WHERE email = '${SEED_OWNER_EMAIL}')`)
    runSql(`DELETE FROM audit_logs WHERE created_at >= '${startedAt}' AND (action LIKE 'settlement.payment.%' OR entity_type = 'commission_settlement')`)
    deleteE2eVenues()
    deleteE2eUsers()
  })

  const unreadOf = (email) => Number(runSql(`SELECT count(*) FROM notifications WHERE read_at IS NULL AND user_id = (SELECT id FROM users WHERE email = '${email}')`))
  const countFor = (email, where = '') => Number(runSql(`SELECT count(*) FROM notifications WHERE created_at >= '${startedAt}' AND user_id = (SELECT id FROM users WHERE email = '${email}') ${where}`))
  const authed = (token) => ({ Authorization: `Bearer ${token}` })

  async function book(page, time) {
    const response = await page.request.post(`${API}/api/v1/bookings`, {
      headers: authed(payer.token), data: { courtId: venue.courts[0].id, startTime: `${nextMondayIso()}T${time}:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    expect(response.status(), await response.text()).toBe(201)
    const booking = await response.json()
    expect((await page.request.post(`${API}/api/v1/owner/booking-requests/${booking.id}/accept`, { headers: authed(owner.token) })).status()).toBe(200)
    return booking
  }

  test('builds an accepted booking and a pending commission payment report', async ({ page }) => {
    owner = await loginByApi(page, SEED_OWNER_EMAIL)
    payer = await loginByApi(page, PAYER)
    admin = await loginByApi(page, SEED_ADMIN_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Thông Báo Thanh Toán')
    makeVenueBookable(venue.id)
    bookings.push(await book(page, '06:00'), await book(page, '07:00'))
    ownerId = runSql(`SELECT owner_id FROM venues WHERE id = '${venue.id}'`).split('\n')[0]
  })

  test('the player reports a payment: the owner’s bell shows it and opens the payments screen', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const baseline = unreadOf(SEED_OWNER_EMAIL)
    const payment = await (await page.request.get(`${API}/api/v1/payments/by-booking/${bookings[0].id}`, { headers: authed(payer.token) })).json()
    expect((await page.request.post(`${API}/api/v1/payments/${payment.id}/report-paid`, { headers: authed(payer.token), data: { method: 'cash' } })).status()).toBe(200)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await expect(page.getByTestId('bell-count')).toHaveText(String(baseline + 1))
    await page.getByTestId('bell').click()
    const item = page.getByTestId('bell-list').getByRole('listitem').first()
    await expect(item).toContainText('Thanh toán')
    await expect(item).toContainText('Người chơi báo đã thanh toán')
    await expect(item).toContainText('E2E Người Báo Thanh Toán')
    await expect(item).toContainText('120.000đ')
    await expect(item).toContainText(bookings[0].bookingCode)
    await shot(page, '2355-owner-bell-reported')
    await item.getByRole('button').click()
    await expect(page).toHaveURL('/owner/payments')
    assertNoErrors()
  })

  test('the owner rejects the report: the player is told why and the screen opens the payment', async ({ page }) => {
    const payment = await (await page.request.get(`${API}/api/v1/payments/by-booking/${bookings[0].id}`, { headers: authed(payer.token) })).json()
    expect((await page.request.post(`${API}/api/v1/owner/payments/${payment.id}/reject`, { headers: authed(owner.token), data: { reason: 'Chưa thấy tiền trong tài khoản' } })).status()).toBe(200)
    await loginByApi(page, PAYER)
    await page.goto('/profile')
    await page.getByTestId('bell').click()
    const item = page.getByTestId('bell-list').getByRole('listitem').first()
    await expect(item).toContainText('Chủ sân chưa nhận được tiền')
    await expect(item).toContainText('Chưa thấy tiền trong tài khoản')
    await item.getByRole('button').click()
    await expect(page).toHaveURL(`/payments/${payment.id}`)
  })

  test('commission payments: Courtly’s confirmation and rejection reach the owner with the reference and the reason', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    runSql(`
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
      SELECT b.id, v.owner_id, 120000, 'percentage', 10, 12000, 'due', now() - interval '10 days', now() + interval '3 days'
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${bookings[1].id}'`)
    runSql(`INSERT INTO commission_payment_reports (owner_id, amount, paid_on, reference_code, status) VALUES ('${ownerId}', 12000, current_date, 'E2E-NOTI-OK', 'pending_review'), ('${ownerId}', 5000, current_date, 'E2E-NOTI-NO', 'pending_review')`)
    const reports = Object.fromEntries(runSql(`SELECT reference_code || '|' || id FROM commission_payment_reports WHERE reference_code LIKE 'E2E-NOTI-%'`).split('\n').map((line) => line.split('|')))
    expect((await page.request.post(`${API}/api/v1/admin/finance/commission-payments/${reports['E2E-NOTI-OK']}/confirm`, { headers: authed(admin.token), data: {} })).status()).toBe(200)
    expect((await page.request.post(`${API}/api/v1/admin/finance/commission-payments/${reports['E2E-NOTI-NO']}/reject`, { headers: authed(admin.token), data: { reason: 'Bằng chứng mờ' } })).status()).toBe(200)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/notifications')
    const list = page.getByTestId('notif-list')
    await expect(list).toContainText('Courtly đã xác nhận thanh toán công nợ')
    await expect(list).toContainText('E2E-NOTI-OK')
    await expect(list).toContainText('vào công nợ chung')
    await expect(list).toContainText('Courtly từ chối báo cáo thanh toán công nợ')
    await expect(list).toContainText('Bằng chứng mờ')
    await shot(page, '2355-owner-history')
    await page.getByTestId('notif-list').getByRole('listitem').filter({ hasText: 'E2E-NOTI-NO' }).getByRole('button').click()
    await expect(page).toHaveURL('/owner/finance?tab=payment-history')
    assertNoErrors()
  })

  test('failed actions notify nobody: confirming the same report again', async ({ page }) => {
    const before = countFor(SEED_OWNER_EMAIL)
    const id = runSql(`SELECT id FROM commission_payment_reports WHERE reference_code = 'E2E-NOTI-OK'`)
    expect((await page.request.post(`${API}/api/v1/admin/finance/commission-payments/${id}/confirm`, { headers: authed(admin.token), data: {} })).status()).toBe(409)
    expect(countFor(SEED_OWNER_EMAIL)).toBe(before)
    expect(countFor(SEED_ADMIN_EMAIL)).toBe(0)
  })

  test('a restriction notice shows in the owner’s history as mandatory and can be switched off by email only', async ({ page }) => {
    runSql(`INSERT INTO notifications (user_id, type, title, content, data_json, created_at)
            VALUES ('${ownerId}', 'debt_restriction', 'E2E Bị hạn chế nhận booking mới do công nợ', 'Công nợ hoa hồng quá hạn 20.000đ.', '{"route": "/owner/finance?tab=debt"}'::jsonb, now())`)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/notifications')
    const item = page.getByTestId('notif-list').getByRole('listitem').first()
    await expect(item.getByTestId('notif-type')).toHaveText('Công nợ')
    await item.getByRole('button').click()
    await expect(page).toHaveURL('/owner/finance?tab=debt')
    await page.goto('/notifications?tab=settings')
    const restriction = page.getByTestId('pref-debt_restriction')
    await expect(restriction.getByTestId('pref-mandatory')).toBeVisible()
    await expect(restriction.getByTestId('pref-in-app')).toBeDisabled()
    await expect(restriction.getByTestId('pref-email')).toBeEnabled()
  })

  test('the owner notifications page fits a phone', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/notifications')
    await expect(page.getByTestId('notif-list')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
    await shot(page, '2355-owner-history-mobile')
  })
})

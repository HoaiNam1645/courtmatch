import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const ADMIN = 'admin@courtly.vn'
const ids = {}
let venue

function cleanup() {
  runSql(`DELETE FROM booking_commissions WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE 'E2EAS%')`)
  runSql(`DELETE FROM notifications WHERE type IN ('venue_suspended') AND data_json ->> 'venueId' IN (SELECT id::text FROM venues WHERE name LIKE 'E2E AS%')`)
  runSql(`DELETE FROM audit_logs WHERE entity_type IN ('booking_disputes', 'venues') AND (new_data ->> 'note' LIKE 'E2E AS%' OR entity_id IN (SELECT d.id FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.booking_code LIKE 'E2EAS%'))`)
  cleanupDisputes('E2EAS')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EAS%'`)
  deleteE2eUsers()
  if (venue) runSql(`UPDATE venues SET status = 'active', suspension_reason = NULL, suspended_at = NULL WHERE id = '${venue.id}'`)
}

test.describe.serial('2.3.103-2.3.104 and 2.3.107 system-resolved reports, venue stats and suspension', () => {
  test.beforeAll(() => {
    venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    cleanup()
    createE2eUser({ tag: 'as-a', name: 'E2E Người Báo Cáo Hệ Thống' })
    ;['auto', 'lost', 'phone'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'as-a', `E2EAS00${index + 1}`, 'completed', index + 1)
    })
  })
  test.afterAll(cleanup)

  test('a report the system resolved is labelled "Hệ thống tự xử lý" for the player, the owner and the admin', async ({ page, browser }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const login = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: email('as-a'), password: 'Courtly@123' } })
    const token = (await login.json()).accessToken
    const created = await page.request.post(`${API}/api/v1/bookings/${ids.auto}/disputes`, {
      headers: { Authorization: `Bearer ${token}` }, data: { category: 'venue_closed', description: 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng' },
    })
    ids.dAuto = (await created.json()).id
    runSql(`UPDATE booking_disputes SET status = 'resolved', resolution = 'owner_no_response', resolution_note = 'Chủ sân không giải trình trong 48 giờ, hệ thống tự xử lý.', handled_at = now() + interval '1 hour', handled_by = NULL WHERE id = '${ids.dAuto}'`)

    await loginByApi(page, email('as-a'))
    await page.goto(`/bao-cao/${ids.dAuto}`)
    await expect(page.getByTestId('dd-entry-1')).toContainText('Hệ thống tự xử lý')
    await expect(page.getByTestId('dd-entry-1')).toContainText('Chủ sân không phản hồi')

    const owner = await browser.newContext()
    const ownerPage = await owner.newPage()
    await loginByApi(ownerPage, SEED_OWNER_EMAIL)
    await ownerPage.goto(`/owner/disputes/${ids.dAuto}`)
    await expect(ownerPage.getByTestId('odd-entry-1')).toContainText('Hệ thống tự xử lý')
    await owner.close()

    const admin = await browser.newContext()
    const adminPage = await admin.newPage()
    await loginByApi(adminPage, ADMIN)
    await adminPage.goto(`/admin/disputes/${ids.dAuto}`)
    await expect(adminPage.getByTestId('add-admin-entry')).toContainText('Hệ thống')
    await expect(adminPage.getByTestId('add-resolution')).toHaveText('Chủ sân không phản hồi')
    await admin.close()
    assertNoErrors()
  })

  test('venue stats tab counts reports per venue, flags a venue close to the threshold and links to its reports', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const login = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: email('as-a'), password: 'Courtly@123' } })
    const token = (await login.json()).accessToken
    const created = await page.request.post(`${API}/api/v1/bookings/${ids.lost}/disputes`, {
      headers: { Authorization: `Bearer ${token}` }, data: { category: 'other', description: 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng' },
    })
    ids.dLost = (await created.json()).id
    runSql(`UPDATE booking_disputes SET status = 'resolved', resolution = 'refund_half', handled_at = now() - interval '1 day' WHERE id = '${ids.dLost}'`)
    const total = Number(runSql(`SELECT count(*) FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.venue_id = '${venue.id}'`))
    const lost = Number(runSql(`SELECT count(*) FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.venue_id = '${venue.id}' AND d.status = 'resolved' AND d.resolution IN ('refund_full', 'refund_half', 'owner_no_response')`))
    const lostWindow = Number(runSql(`SELECT count(*) FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.venue_id = '${venue.id}' AND d.status = 'resolved' AND d.resolution IN ('refund_full', 'refund_half', 'owner_no_response') AND d.handled_at > now() - interval '90 days'`))

    await loginByApi(page, ADMIN)
    await page.goto('/admin/disputes')
    await page.getByTestId('ad-view-venues').click()
    await page.getByLabel('Tìm sân hoặc chủ sân').fill(venue.name.slice(0, 6))
    const row = page.getByTestId(`vs-${venue.id}`)
    await expect(row).toBeVisible()
    await expect(row.getByTestId('vs-total')).toHaveText(String(total))
    await expect(row.getByTestId('vs-lost')).toHaveText(String(lost))
    if (lostWindow >= 2) await expect(row.getByTestId('vs-near')).toBeVisible()
    await shot(page, '2303-venue-stats')
    await page.getByTestId('vs-from').fill('2026-02-01')
    await page.getByTestId('vs-to').fill('2026-01-01')
    await expect(page.getByText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.')).toBeVisible()
    await page.getByTestId('vs-from').fill('')
    await page.getByTestId('vs-to').fill('')
    await row.getByTestId('vs-open').click()
    await expect(page.getByTestId('ad-venue-filter')).toContainText(venue.name)
    await expect(page.getByTestId(`ad-${ids.dLost}`)).toBeVisible()
    assertNoErrors()
  })

  test('a suspended venue shows the reason to its owner and the admin can switch it back on with a note', async ({ page, browser }) => {
    runSql(`UPDATE venues SET status = 'inactive', suspension_reason = 'Bị xử thua 3 báo cáo đơn đặt sân trong 90 ngày gần nhất (ngưỡng 3 lần).', suspended_at = now() WHERE id = '${venue.id}'`)
    const owner = await browser.newContext()
    const ownerPage = await owner.newPage()
    await loginByApi(ownerPage, SEED_OWNER_EMAIL)
    await ownerPage.goto(`/owner/venues/${venue.id}`)
    await expect(ownerPage.getByTestId('venue-suspension-reason')).toContainText('xử thua 3 báo cáo')
    await shot(ownerPage, '2304-owner-venue-suspended')
    await ownerPage.goto('/owner/venues')
    await expect(ownerPage.getByTestId('venue-suspension').first()).toContainText('tạm ngưng')
    await owner.close()

    await loginByApi(page, ADMIN)
    await page.goto('/admin/disputes?tab=venues')
    await page.getByLabel('Tìm sân hoặc chủ sân').fill(venue.name.slice(0, 6))
    const row = page.getByTestId(`vs-${venue.id}`)
    await expect(row.getByTestId('vs-suspended')).toBeVisible()
    await row.getByTestId('vs-reactivate').click()
    await page.getByTestId('vs-dialog-confirm').click()
    await expect(page.getByTestId('vs-dialog')).toContainText('Ghi chú từ 5 đến 1000 ký tự')
    await page.getByTestId('vs-note').fill('E2E AS đã làm việc với chủ sân')
    await page.getByTestId('vs-dialog-confirm').click()
    await expect(page.getByTestId('venue-stats-notice')).toContainText('Đã bật lại sân')
    expect(runSql(`SELECT status || '|' || coalesce(suspension_reason, 'null') FROM venues WHERE id = '${venue.id}'`)).toBe('active|null')
    await expect(page.getByTestId(`vs-${venue.id}`).getByTestId('vs-suspended')).toHaveCount(0)
  })

  test('phone: the venue stats cards fit the screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, ADMIN)
    await page.goto('/admin/disputes?tab=venues')
    await page.getByLabel('Tìm sân hoặc chủ sân').fill(venue.name.slice(0, 6))
    await expect(page.getByTestId(`vs-${venue.id}`)).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, '2303-venue-stats-phone')
  })
})

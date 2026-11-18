import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { addPayment, bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const ADMIN = 'admin@courtly.vn'
const ids = {}

function cleanup() {
  runSql(`DELETE FROM refunds WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE 'E2ENL%')`)
  runSql(`DELETE FROM notifications WHERE data_json ->> 'bookingId' IN (SELECT id::text FROM bookings WHERE booking_code LIKE 'E2ENL%')`)
  runSql(`DELETE FROM audit_logs WHERE entity_type = 'booking_disputes' AND entity_id IN (SELECT d.id FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.booking_code LIKE 'E2ENL%')`)
  cleanupDisputes('E2ENL')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2ENL%'`)
  deleteE2eUsers()
}

async function token(page, loginEmail) {
  const response = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: loginEmail, password: 'Courtly@123' } })
  return (await response.json()).accessToken
}

test.describe.serial('2.3.111 dispute notifications open the right screen for each recipient', () => {
  test.beforeAll(() => {
    cleanup()
    const venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    createE2eUser({ tag: 'nl-a', name: 'E2E Người Nhận Thông Báo' })
    ids.booking = bookingWithStatus(venue, 'nl-a', 'E2ENL001', 'completed', 1)
    addPayment(ids.booking, 'confirmed')
  })
  test.afterAll(cleanup)

  test('owner, player and admin each open their own detail screen from the bell', async ({ page, browser }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const playerToken = await token(page, email('nl-a'))
    const created = await page.request.post(`${API}/api/v1/bookings/${ids.booking}/disputes`, {
      headers: { Authorization: `Bearer ${playerToken}` },
      data: { category: 'extra_charge', description: 'Chủ sân thu thêm tiền thuê vợt ngoài thỏa thuận', refundRequested: true, refundAmount: 90000 },
    })
    ids.dispute = (await created.json()).id
    runSql(`UPDATE booking_disputes SET status = 'reviewing', owner_response = 'E2E giải trình', owner_responded_at = now() WHERE id = '${ids.dispute}'`)
    const adminToken = await token(page, ADMIN)
    const decided = await page.request.post(`${API}/api/v1/admin/booking-disputes/${ids.dispute}/refund-full`, { headers: { Authorization: `Bearer ${adminToken}` }, data: { note: 'Camera cho thấy sân đóng cửa' } })
    expect(decided.status()).toBe(200)
    const refund = runSql(`SELECT id FROM refunds WHERE dispute_id = '${ids.dispute}'`)
    const ownerToken = await token(page, SEED_OWNER_EMAIL)
    const reported = await page.request.post(`${API}/api/v1/owner/refunds/${refund}/report-paid`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      multipart: { file: { name: 'ck.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64') } },
    })
    expect(reported.status(), await reported.text()).toBe(200)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/')
    await page.getByTestId('bell').click()
    await page.getByTestId('bell-list').getByRole('listitem').filter({ hasText: 'Bạn cần hoàn tiền cho người chơi' }).first().getByRole('button').click()
    await expect(page).toHaveURL(new RegExp(`/owner/disputes/${ids.dispute}$`))
    await expect(page.getByTestId('odd-detail')).toBeVisible()

    const player = await browser.newContext()
    const playerPage = await player.newPage()
    await loginByApi(playerPage, email('nl-a'))
    await playerPage.goto('/')
    await playerPage.getByTestId('bell').click()
    await playerPage.getByTestId('bell-list').getByRole('listitem').filter({ hasText: 'Chủ sân báo đã hoàn tiền cho bạn' }).first().getByRole('button').click()
    await expect(playerPage).toHaveURL(new RegExp(`/bao-cao/${ids.dispute}$`))
    await expect(playerPage.getByTestId('refund-panel')).toBeVisible()
    await shot(playerPage, '2311-player-notification')
    await player.close()

    const admin = await browser.newContext()
    const adminPage = await admin.newPage()
    await loginByApi(adminPage, ADMIN)
    await adminPage.goto('/admin')
    await adminPage.getByTestId('bell').click()
    await adminPage.getByTestId('bell-list').getByRole('listitem').filter({ hasText: 'Chủ sân báo đã hoàn tiền' }).first().getByRole('button').click()
    await expect(adminPage).toHaveURL(new RegExp(`/admin/disputes/${ids.dispute}$`))
    await expect(adminPage.getByTestId('add-detail')).toBeVisible()
    await admin.close()
    assertNoErrors()
  })

  test('phone: the bell opens the dispute from a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner')
    await page.getByTestId('bell').click()
    await page.getByTestId('bell-list').getByRole('listitem').filter({ hasText: 'Bạn cần hoàn tiền cho người chơi' }).first().getByRole('button').click()
    await expect(page.getByTestId('odd-detail')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
})

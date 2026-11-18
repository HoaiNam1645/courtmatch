import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { addPayment, bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const ADMIN = 'admin@courtly.vn'
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
const NOTE = 'Camera cho thấy sân đóng cửa đúng như người chơi báo'
const ids = {}
let playerToken
let adminToken

function cleanup() {
  runSql(`DELETE FROM booking_commissions WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE 'E2ERF%')`)
  runSql(`DELETE FROM refunds WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE 'E2ERF%')`)
  runSql(`DELETE FROM notifications WHERE type IN ('booking_dispute_resolved', 'booking_refund_updated', 'commission_restored') AND data_json ->> 'bookingId' IN (SELECT id::text FROM bookings WHERE booking_code LIKE 'E2ERF%')`)
  runSql(`DELETE FROM audit_logs WHERE entity_type IN ('booking_disputes', 'refunds', 'booking_commission') AND (entity_id IN (SELECT d.id FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.booking_code LIKE 'E2ERF%') OR new_data ->> 'bookingId' IN (SELECT id::text FROM bookings WHERE booking_code LIKE 'E2ERF%'))`)
  cleanupDisputes('E2ERF')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2ERF%'`)
  deleteE2eUsers()
}

async function login(page, loginEmail) {
  const response = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: loginEmail, password: 'Courtly@123' } })
  return (await response.json()).accessToken
}

/** Báo cáo yêu cầu hoàn `requested`, chủ sân đã giải trình, admin đã quyết định hoàn toàn phần; trả về {dispute, refund, booking}. */
async function decidedRefund(page, bookingId, requested) {
  runSql(`INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, due_at)
    SELECT b.id, v.owner_id, 180000, 'fixed', 20000, 20000, 'due', now() + interval '7 days' FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${bookingId}'`)
  const created = await page.request.post(`${API}/api/v1/bookings/${bookingId}/disputes`, {
    headers: { Authorization: `Bearer ${playerToken}` },
    data: { category: 'extra_charge', description: 'Chủ sân thu thêm tiền thuê vợt ngoài thỏa thuận', refundRequested: true, refundAmount: requested },
  })
  expect(created.status(), await created.text()).toBe(201)
  const dispute = (await created.json()).id
  runSql(`UPDATE booking_disputes SET status = 'reviewing', owner_response = 'E2E giải trình', owner_responded_at = now() WHERE id = '${dispute}'`)
  const decided = await page.request.post(`${API}/api/v1/admin/booking-disputes/${dispute}/refund-full`, { headers: { Authorization: `Bearer ${adminToken}` }, data: { note: NOTE } })
  expect(decided.status(), await decided.text()).toBe(200)
  return { dispute, refund: runSql(`SELECT id FROM refunds WHERE dispute_id = '${dispute}'`), booking: bookingId }
}

test.describe.serial('2.3.108-2.3.109 direct refund tracking and commission adjustment', () => {
  test.beforeAll(() => {
    cleanup()
    const venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    createE2eUser({ tag: 'rf-a', name: 'E2E Người Nhận Hoàn Tiền' })
    ;['one', 'two', 'three', 'phone'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'rf-a', `E2ERF00${index + 1}`, 'completed', index + 1)
      addPayment(ids[key], 'confirmed')
    })
  })
  test.afterAll(cleanup)

  test('owner reports the refund with a receipt, the player confirms and the commission follows', async ({ page, browser }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    playerToken = await login(page, email('rf-a'))
    adminToken = await login(page, ADMIN)
    ids.r1 = await decidedRefund(page, ids.one, 120000)
    expect(runSql(`SELECT status FROM booking_commissions WHERE booking_id = '${ids.one}'`)).toBe('suspended')

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/disputes/${ids.r1.dispute}`)
    await expect(page.getByTestId('refund-panel')).toHaveAttribute('data-status', 'awaiting_owner_refund')
    await expect(page.getByTestId('refund-amount')).toContainText('120.000')
    await page.getByTestId('refund-report').click()
    await page.getByTestId('refund-dialog-confirm').click()
    await expect(page.getByTestId('refund-dialog')).toContainText('Hãy chọn ảnh chứng từ')
    await page.getByTestId('refund-file').setInputFiles({ name: 'ck.png', mimeType: 'image/png', buffer: PNG })
    await page.getByTestId('refund-text').fill('Đã chuyển khoản lúc 10h')
    await shot(page, '2308-owner-report-refund')
    await page.getByTestId('refund-dialog-confirm').click()
    await expect(page.getByTestId('refund-status')).toHaveText('Chủ sân báo đã hoàn')
    await expect(page.getByTestId('refund-proof')).toBeVisible()
    await expect(page.getByTestId('refund-report')).toHaveCount(0)

    const player = await browser.newContext()
    const playerPage = await player.newPage()
    await loginByApi(playerPage, email('rf-a'))
    await playerPage.goto(`/bao-cao/${ids.r1.dispute}`)
    await expect(playerPage.getByTestId('refund-owner-note')).toContainText('Đã chuyển khoản lúc 10h')
    await expect(playerPage.getByTestId('refund-proof')).toBeVisible()
    await shot(playerPage, '2308-player-refund')
    await playerPage.getByTestId('refund-confirm').click()
    await playerPage.getByTestId('refund-dialog-confirm').click()
    await expect(playerPage.getByTestId('refund-status')).toHaveText('Đã hoàn tất')
    await expect(playerPage.getByTestId('refund-confirm')).toHaveCount(0)
    await player.close()

    expect(runSql(`SELECT status || '|' || commission_amount FROM booking_commissions WHERE booking_id = '${ids.one}'`)).toBe('adjusted|6667.00')
    expect(runSql(`SELECT status FROM payments WHERE booking_id = '${ids.one}'`)).toBe('partially_refunded')
    assertNoErrors()
  })

  test('a complaint sends the refund back to the owner, who reports again; the admin can confirm instead of the player', async ({ page, browser }) => {
    ids.r2 = await decidedRefund(page, ids.two, 180000)
    await page.request.post(`${API}/api/v1/owner/refunds/${ids.r2.refund}/report-paid`, {
      headers: { Authorization: `Bearer ${await login(page, SEED_OWNER_EMAIL)}` }, multipart: { file: { name: 'ck.png', mimeType: 'image/png', buffer: PNG } },
    })
    const player = await browser.newContext()
    const playerPage = await player.newPage()
    await loginByApi(playerPage, email('rf-a'))
    await playerPage.goto(`/bao-cao/${ids.r2.dispute}`)
    await playerPage.getByTestId('refund-complain').click()
    await playerPage.getByTestId('refund-dialog-confirm').click()
    await expect(playerPage.getByTestId('refund-dialog')).toContainText('Lý do từ 5 đến 1000 ký tự')
    await playerPage.getByTestId('refund-text').fill('Tôi chưa thấy tiền về tài khoản')
    await playerPage.getByTestId('refund-dialog-confirm').click()
    await expect(playerPage.getByTestId('refund-status')).toHaveText('Chờ chủ sân hoàn tiền')
    await expect(playerPage.getByTestId('refund-confirm')).toHaveCount(0)
    await player.close()

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/disputes/${ids.r2.dispute}`)
    await expect(page.getByTestId('refund-complaint')).toContainText('chưa thấy tiền về')
    await expect(page.getByTestId('refund-report')).toBeVisible()

    const admin = await browser.newContext()
    const adminPage = await admin.newPage()
    await loginByApi(adminPage, ADMIN)
    await adminPage.goto('/admin/disputes')
    await adminPage.getByTestId('ad-tab-refunding').click()
    await expect(adminPage.getByTestId(`ad-${ids.r2.dispute}`)).toContainText('Chờ chủ sân hoàn tiền')
    await adminPage.getByTestId(`ad-${ids.r2.dispute}`).click()
    await adminPage.getByTestId('refund-admin-confirm').click()
    await adminPage.getByTestId('refund-dialog-confirm').click()
    await expect(adminPage.getByTestId('refund-dialog')).toContainText('Ghi chú từ 5 đến 1000 ký tự')
    await adminPage.getByTestId('refund-text').fill('Đã đối chiếu sao kê ngân hàng')
    await adminPage.getByTestId('refund-dialog-confirm').click()
    await expect(adminPage.getByTestId('refund-status')).toHaveText('Đã hoàn tất')
    await shot(adminPage, '2308-admin-refund-confirmed')
    await admin.close()
    expect(runSql(`SELECT status || '|' || commission_amount FROM booking_commissions WHERE booking_id = '${ids.two}'`)).toBe('waived|0.00')
    expect(runSql(`SELECT status FROM payments WHERE booking_id = '${ids.two}'`)).toBe('refunded')
  })

  test('phone: the refund panel and its dialog fit the screen', async ({ page }) => {
    ids.r3 = await decidedRefund(page, ids.three, 90000)
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/disputes/${ids.r3.dispute}`)
    await expect(page.getByTestId('refund-panel')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.getByTestId('refund-report').click()
    await expect(page.getByTestId('refund-dialog')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, '2308-refund-phone')
  })
})

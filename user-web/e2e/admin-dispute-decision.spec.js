import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { addPayment, bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const ADMIN = 'admin@courtly.vn'
const NOTE = 'Camera cho thấy sân đóng cửa đúng như người chơi báo'
const ids = {}

function cleanup() {
  runSql(`DELETE FROM booking_commissions WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE 'E2EDC%')`)
  runSql(`DELETE FROM refunds WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE 'E2EDC%')`)
  runSql(`DELETE FROM notifications WHERE type IN ('booking_dispute_resolved', 'commission_restored') AND data_json ->> 'disputeId' IN (SELECT d.id::text FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.booking_code LIKE 'E2EDC%')`)
  runSql(`DELETE FROM audit_logs WHERE entity_type = 'booking_disputes' AND entity_id IN (SELECT d.id FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.booking_code LIKE 'E2EDC%')`)
  cleanupDisputes('E2EDC')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EDC%'`)
  deleteE2eUsers()
}

async function reviewingDispute(page, playerToken, bookingId, body) {
  const response = await page.request.post(`${API}/api/v1/bookings/${bookingId}/disputes`, {
    headers: { Authorization: `Bearer ${playerToken}` },
    data: { category: 'extra_charge', description: 'Chủ sân thu thêm tiền thuê vợt ngoài thỏa thuận', ...body },
  })
  expect(response.status(), await response.text()).toBe(201)
  const id = (await response.json()).id
  runSql(`UPDATE booking_disputes SET status = 'reviewing', owner_response = 'E2E giải trình', owner_responded_at = now() WHERE id = '${id}'`)
  return id
}

test.describe.serial('2.3.100-2.3.101 admin decides a refund', () => {
  let playerToken
  test.beforeAll(() => {
    cleanup()
    const venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    createE2eUser({ tag: 'dc-a', name: 'E2E Người Chơi Quyết Định' })
    ;['full', 'half', 'none', 'phone', 'reject'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'dc-a', `E2EDC00${index + 1}`, 'completed', index + 1)
      if (key !== 'none') addPayment(ids[key], 'confirmed')
    })
  })
  test.afterAll(cleanup)

  test('full refund: confirmation shows the amount, note is required, result reaches the timeline, the refund and the player', async ({ page, browser }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const login = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: email('dc-a'), password: 'Courtly@123' } })
    playerToken = (await login.json()).accessToken
    ids.dFull = await reviewingDispute(page, playerToken, ids.full, { refundRequested: true, refundAmount: 125000 })

    await loginByApi(page, ADMIN)
    await page.goto(`/admin/disputes/${ids.dFull}`)
    await expect(page.getByTestId('add-refund-full')).toContainText('125.000')
    await expect(page.getByTestId('add-refund-half')).toContainText('62.000')
    await page.getByTestId('add-refund-full').click()
    await expect(page.getByTestId('add-confirm-amount')).toContainText('125.000')
    await shot(page, '2300-admin-refund-confirm')
    await page.getByTestId('add-confirm-confirm').click()
    await expect(page.getByTestId('add-confirm')).toContainText('Ghi chú quyết định từ 5 đến 1000 ký tự')
    expect(runSql(`SELECT status FROM booking_disputes WHERE id = '${ids.dFull}'`)).toBe('reviewing')
    await page.getByTestId('add-note').fill(NOTE)
    await page.getByTestId('add-confirm-confirm').click()

    await expect(page.getByTestId('add-notice')).toContainText('hoàn tiền toàn phần')
    await expect(page.getByTestId('add-status')).toHaveText('Đã giải quyết')
    await expect(page.getByTestId('add-resolution')).toHaveText('Hoàn tiền toàn phần')
    await expect(page.getByTestId('add-refund-full')).toHaveCount(0)
    await expect(page.getByTestId('add-admin-entry')).toContainText(NOTE)
    await expect(page.getByTestId('add-refunded')).toContainText('125.000')
    expect(runSql(`SELECT amount || '|' || status FROM refunds WHERE dispute_id = '${ids.dFull}'`)).toBe('125000.00|awaiting_owner_refund')
    expect(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_dispute_resolved' AND data_json ->> 'disputeId' = '${ids.dFull}'`)).toBe('2')
    await shot(page, '2300-admin-refund-done')

    const player = await browser.newContext()
    const playerPage = await player.newPage()
    await loginByApi(playerPage, email('dc-a'))
    await playerPage.goto(`/bao-cao/${ids.dFull}`)
    await expect(playerPage.getByTestId('dd-status')).toBeVisible()
    await expect(playerPage.getByTestId('dd-entry-2')).toContainText(NOTE)
    await player.close()
    assertNoErrors()
  })

  test('half refund shows how the amount is calculated', async ({ page }) => {
    ids.dHalf = await reviewingDispute(page, playerToken, ids.half, { refundRequested: true, refundAmount: 125000 })
    await loginByApi(page, ADMIN)
    await page.goto(`/admin/disputes/${ids.dHalf}`)
    await page.getByTestId('add-refund-half').click()
    await expect(page.getByTestId('add-confirm')).toContainText('50% của 125.000')
    await expect(page.getByTestId('add-confirm')).toContainText('làm tròn xuống')
    await expect(page.getByTestId('add-confirm-amount')).toContainText('62.000')
    await page.getByTestId('add-note').fill(NOTE)
    await page.getByTestId('add-confirm-confirm').click()
    await expect(page.getByTestId('add-resolution')).toHaveText('Hoàn tiền một nửa')
    expect(runSql(`SELECT amount FROM refunds WHERE dispute_id = '${ids.dHalf}'`)).toBe('62000.00')
  })

  test('reject: the commission held for the dispute returns to the debt and both sides are told', async ({ page }) => {
    ids.dReject = await reviewingDispute(page, playerToken, ids.reject, { refundRequested: true, refundAmount: 70000 })
    runSql(`INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, status_before, suspended_at, suspended_dispute_id, due_at)
      SELECT b.id, v.owner_id, 180000, 'fixed', 20000, 20000, 'suspended', 'due', now(), '${ids.dReject}', now() + interval '7 days' FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${ids.reject}'`)
    await loginByApi(page, ADMIN)
    await page.goto(`/admin/disputes/${ids.dReject}`)
    await page.getByTestId('add-reject').click()
    await page.getByTestId('add-confirm-confirm').click()
    await expect(page.getByTestId('add-confirm')).toContainText('Ghi chú quyết định từ 5 đến 1000 ký tự')
    await page.getByTestId('add-note').fill('Không đủ bằng chứng cho thấy bị thu thêm tiền')
    await page.getByTestId('add-confirm-confirm').click()
    await expect(page.getByTestId('add-notice')).toContainText('Đã bác bỏ báo cáo')
    await expect(page.getByTestId('add-status')).toHaveText('Đã bác bỏ')
    await expect(page.getByTestId('add-admin-entry')).toContainText('Không đủ bằng chứng')
    expect(runSql(`SELECT status FROM booking_commissions WHERE booking_id = '${ids.reject}'`)).toBe('due')
    expect(runSql(`SELECT count(*) FROM refunds WHERE dispute_id = '${ids.dReject}'`)).toBe('0')
    expect(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_dispute_resolved' AND data_json ->> 'disputeId' = '${ids.dReject}'`)).toBe('2')
    await shot(page, '2302-admin-reject-done')
  })

  test('a report without a refund request has no refund decision; phone layout fits', async ({ page }) => {
    ids.dNone = await reviewingDispute(page, playerToken, ids.none, {})
    await loginByApi(page, ADMIN)
    await page.goto(`/admin/disputes/${ids.dNone}`)
    await expect(page.getByTestId('add-no-refund')).toContainText('không yêu cầu hoàn tiền')
    await expect(page.getByTestId('add-reject')).toContainText('Kết luận không hoàn tiền')
    await expect(page.getByTestId('add-refund-full')).toHaveCount(0)

    ids.dPhone = await reviewingDispute(page, playerToken, ids.phone, { refundRequested: true, refundAmount: 90000 })
    await page.setViewportSize({ width: 375, height: 800 })
    await page.goto(`/admin/disputes/${ids.dPhone}`)
    await expect(page.getByTestId('add-refund-full')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.getByTestId('add-refund-half').click()
    await expect(page.getByTestId('add-confirm')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, '2300-admin-refund-phone')
  })
})

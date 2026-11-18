import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'
import { bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
const DESCRIPTION = 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng'
const SUPPLEMENT = 'Tôi có thêm tin nhắn chứng minh chủ sân đã hẹn mở cổng lúc 18h'

let venue
let token
const ids = {}

function cleanup() {
  cleanupDisputes('E2ESP')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2ESP%'`)
  deleteE2eUsers()
}

async function createDispute(page, bookingId) {
  const response = await page.request.post(`${API}/api/v1/bookings/${bookingId}/disputes`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { category: 'venue_closed', description: DESCRIPTION },
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()).id
}

const count = (id) => runSql(`SELECT coalesce(jsonb_array_length(reporter_updates_json), 0) FROM booking_disputes WHERE id = '${id}'`)

test.describe.serial('2.3.91 player supplements an open report', () => {
  test.beforeAll(() => {
    cleanup()
    venue = pickVenue()
    createE2eUser({ tag: 'sp-a', name: 'E2E Người Bổ Sung' })
    ;['waiting', 'reviewing', 'closed', 'full', 'broken', 'phone'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'sp-a', `E2ESP00${index + 1}`, 'completed', index + 1)
    })
  })
  test.afterAll(cleanup)

  test('validates the text, then adds a supplement with an image that shows in the conversation and tells the owner', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    ;({ token } = await loginByApi(page, email('sp-a')))
    ids.d1 = await createDispute(page, ids.waiting)
    await page.goto(`/bao-cao/${ids.d1}`)
    await expect(page.getByTestId('supplement-form')).toContainText('Còn 10 lần bổ sung')
    await shot(page, '2391-supplement-form')

    await page.getByTestId('supplement-submit').click()
    await expect(page.getByTestId('supplement-form')).toContainText('ít nhất 5 ký tự')
    await page.getByTestId('supplement-content').fill('ngắn')
    await page.getByTestId('supplement-submit').click()
    await expect(page.getByTestId('supplement-form')).toContainText('ít nhất 5 ký tự')
    await page.getByTestId('supplement-content').fill('x'.repeat(2001))
    await expect(page.getByTestId('supplement-counter')).toContainText('2001/2000')
    await page.getByTestId('supplement-submit').click()
    await expect(page.getByTestId('supplement-form')).toContainText('tối đa 2000 ký tự')
    expect(count(ids.d1)).toBe('0')

    await page.getByTestId('supplement-content').fill(`  ${SUPPLEMENT}  `)
    await page.getByTestId('supplement-form').getByTestId('evidence-input').setInputFiles([{ name: 'tin-nhan.png', mimeType: 'image/png', buffer: PNG }])
    await page.getByTestId('supplement-submit').click()
    await expect(page.getByTestId('dd-entry-1')).toContainText(SUPPLEMENT)
    await expect(page.getByTestId('dd-entry-1')).toHaveAttribute('data-type', 'supplement')
    await expect(page.getByTestId('dd-entry-1')).toHaveAttribute('data-actor', 'you')
    await expect(page.getByTestId('dd-evidences-1').locator('img')).toHaveCount(1)
    await expect(page.getByTestId('supplement-form')).toContainText('Còn 9 lần bổ sung')
    await expect(page.getByTestId('supplement-content')).toHaveValue('')
    expect(count(ids.d1)).toBe('1')
    expect(runSql(`SELECT reporter_updates_json -> 0 ->> 'content' FROM booking_disputes WHERE id = '${ids.d1}'`)).toBe(SUPPLEMENT)
    expect(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_dispute_updated' AND data_json ->> 'disputeId' = '${ids.d1}'`)).toBe('1')
    assertNoErrors()
  })

  test('still possible while the admin is reviewing, without notifying the owner again; hidden once closed', async ({ page }) => {
    await loginByApi(page, email('sp-a'))
    ids.d2 = await createDispute(page, ids.reviewing)
    runSql(`UPDATE booking_disputes SET status = 'reviewing', owner_response = 'E2E chủ sân đã giải trình', owner_responded_at = now() + interval '1 hour' WHERE id = '${ids.d2}'`)
    await page.goto(`/bao-cao/${ids.d2}`)
    await expect(page.getByTestId('supplement-form')).toBeVisible()
    await expect(page.getByTestId('evidence-manager')).toHaveCount(0)
    await page.getByTestId('supplement-content').fill(SUPPLEMENT)
    await page.getByTestId('supplement-submit').click()
    await expect(page.getByTestId('dd-timeline').locator('[data-type="supplement"]')).toHaveCount(1)
    expect(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_dispute_updated' AND data_json ->> 'disputeId' = '${ids.d2}'`)).toBe('0')

    ids.d3 = await createDispute(page, ids.closed)
    runSql(`UPDATE booking_disputes SET status = 'resolved', resolution = 'refund_full', resolution_note = 'E2E xong', handled_at = now() + interval '1 hour' WHERE id = '${ids.d3}'`)
    await page.goto(`/bao-cao/${ids.d3}`)
    await expect(page.getByTestId('dd-timeline')).toBeVisible()
    await expect(page.getByTestId('supplement-form')).toHaveCount(0)
    await expect(page.getByTestId('dd-no-supplement')).toHaveCount(0)
  })

  test('after ten supplements the form is replaced by a notice', async ({ page }) => {
    await loginByApi(page, email('sp-a'))
    ids.d4 = await createDispute(page, ids.full)
    const updates = Array.from({ length: 10 }, (_, index) => ({ id: `u${index}`, content: `Bổ sung số ${index + 1}`, createdAt: new Date(Date.now() + index * 1000).toISOString() }))
    runSql(`UPDATE booking_disputes SET reporter_updates_json = '${JSON.stringify(updates)}'::jsonb WHERE id = '${ids.d4}'`)
    await page.goto(`/bao-cao/${ids.d4}`)
    await expect(page.getByTestId('dd-no-supplement')).toContainText('hết số lần bổ sung')
    await expect(page.getByTestId('supplement-form')).toHaveCount(0)
    await expect(page.getByTestId('dd-timeline').locator('[data-type="supplement"]')).toHaveCount(10)
  })

  test('an image the server rejects keeps the text (sent once) and offers a retry or skipping the images', async ({ page }) => {
    await loginByApi(page, email('sp-a'))
    ids.d5 = await createDispute(page, ids.broken)
    await page.goto(`/bao-cao/${ids.d5}`)
    await page.getByTestId('supplement-content').fill(SUPPLEMENT)
    await page.getByTestId('supplement-form').getByTestId('evidence-input').setInputFiles([{ name: 'tot.png', mimeType: 'image/png', buffer: PNG }, { name: 'gia-mao.png', mimeType: 'image/png', buffer: Buffer.from('khong phai anh') }])
    await page.getByTestId('supplement-submit').click()
    await expect(page.getByTestId('supplement-pending')).toContainText('1 ảnh chưa tải được')
    await expect(page.getByTestId('supplement-pending')).toContainText('gia-mao.png')
    expect(count(ids.d5)).toBe('1')
    expect(runSql(`SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = '${ids.d5}'`)).toBe('1')
    await page.getByTestId('supplement-submit').click()
    await expect(page.getByTestId('supplement-pending')).toContainText('gia-mao.png')
    expect(count(ids.d5)).toBe('1') // thử tải lại không gửi trùng nội dung
    await page.getByTestId('supplement-skip').click()
    await expect(page.getByTestId('supplement-pending')).toHaveCount(0)
    await expect(page.getByTestId('dd-timeline').locator('[data-type="supplement"]')).toHaveCount(1)
    expect(count(ids.d5)).toBe('1')
  })

  test('the form fits a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, email('sp-a'))
    ids.d6 = await createDispute(page, ids.phone)
    await page.goto(`/bao-cao/${ids.d6}`)
    await page.getByTestId('supplement-form').getByTestId('evidence-input').setInputFiles([{ name: 'a.png', mimeType: 'image/png', buffer: PNG }])
    const box = await page.getByTestId('supplement-form').boundingBox()
    expect(box.x + box.width).toBeLessThanOrEqual(376)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  })
})

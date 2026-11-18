import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
const image = (name) => ({ name, mimeType: 'image/png', buffer: PNG })
const DESCRIPTION = 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng'

let venue
let limits
let sent
let broken
let phone

function cleanup() {
  cleanupDisputes('E2EEV')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EEV%'`)
  deleteE2eUsers()
}

async function open(page, bookingId) {
  await page.goto(`/bookings/${bookingId}/bao-cao`)
  await page.getByTestId('dispute-category-venue_closed').check()
  await page.getByTestId('dispute-description').fill(DESCRIPTION)
}

test.describe.serial('2.3.88 player attaches evidence images to a booking report', () => {
  test.beforeAll(() => {
    cleanup()
    venue = pickVenue()
    createE2eUser({ tag: 'ev-a', name: 'E2E Người Gửi Ảnh' })
    createE2eUser({ tag: 'ev-b', name: 'E2E Người Lạ' })
    limits = bookingWithStatus(venue, 'ev-a', 'E2EEV001', 'completed', 1)
    sent = bookingWithStatus(venue, 'ev-a', 'E2EEV002', 'completed', 1)
    broken = bookingWithStatus(venue, 'ev-a', 'E2EEV003', 'completed', 2)
    phone = bookingWithStatus(venue, 'ev-a', 'E2EEV004', 'confirmed', 2)
  })
  test.afterAll(cleanup)

  test('previews, removes and validates the chosen files before sending', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('ev-a'))
    await open(page, limits)
    const input = page.getByTestId('evidence-input')
    await input.setInputFiles([image('a.png'), image('b.png')])
    await expect(page.getByTestId('evidence-previews').locator('li')).toHaveCount(2)
    await page.getByTestId('evidence-remove-0').click()
    await expect(page.getByTestId('evidence-previews').locator('li')).toHaveCount(1)

    await input.setInputFiles([{ name: 'ghi-chu.txt', mimeType: 'text/plain', buffer: Buffer.from('xin chao') }])
    await expect(page.getByTestId('evidence-problems')).toContainText('ghi-chu.txt: Chỉ nhận ảnh JPEG, PNG hoặc WebP')
    await input.setInputFiles([{ name: 'qua-lon.png', mimeType: 'image/png', buffer: Buffer.alloc(5 * 1024 * 1024 + 1, 1) }])
    await expect(page.getByTestId('evidence-problems')).toContainText('qua-lon.png: Ảnh vượt quá 5MB')
    await expect(page.getByTestId('evidence-previews').locator('li')).toHaveCount(1)

    await input.setInputFiles([image('c.png'), image('d.png'), image('e.png'), image('f.png'), image('g.png')])
    await expect(page.getByTestId('evidence-previews').locator('li')).toHaveCount(5)
    await expect(page.getByTestId('evidence-problems')).toContainText('chỉ đính kèm tối đa 5 ảnh')
    await expect(page.getByTestId('evidence-add')).toHaveCount(0)
    await shot(page, '2388-evidence-picker')
    assertNoErrors()
  })

  test('sends the report and uploads every image; the files are private to the parties', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, email('ev-a'))
    await open(page, sent)
    await page.getByTestId('evidence-input').setInputFiles([image('truoc-cong.png'), image('tin-nhan.png')])
    await page.getByTestId('dispute-submit').click()
    await expect(page.getByTestId('dispute-uploaded')).toContainText('Đã đính kèm 2 ảnh bằng chứng')
    expect(runSql(`SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE booking_id = '${sent}'`)).toBe('2')
    expect(runSql(`SELECT reporter_evidences_json -> 0 ->> 'originalName' FROM booking_disputes WHERE booking_id = '${sent}'`)).toBe('truoc-cong.png')

    const disputeId = runSql(`SELECT id FROM booking_disputes WHERE booking_id = '${sent}'`)
    const evidenceId = runSql(`SELECT reporter_evidences_json -> 0 ->> 'id' FROM booking_disputes WHERE booking_id = '${sent}'`)
    const url = `${API}/api/v1/booking-disputes/${disputeId}/evidences/${evidenceId}/file`
    const own = await page.request.get(url, { headers: { Authorization: `Bearer ${token}` } })
    expect(own.status()).toBe(200)
    expect(own.headers()['content-type']).toBe('image/png')
    expect((await page.request.get(url)).status()).toBe(401)
    const stranger = await loginByApi(page, email('ev-b'))
    expect((await page.request.get(url, { headers: { Authorization: `Bearer ${stranger.token}` } })).status()).toBe(404)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const ownerView = await page.request.get(url, { headers: { Authorization: `Bearer ${owner.token}` } })
    // Chủ sân của sân được chọn ngẫu nhiên theo dữ liệu: chỉ chủ sân đúng của sân mới xem được.
    const venueOwner = runSql(`SELECT u.email FROM venues v JOIN users u ON u.id = v.owner_id WHERE v.id = '${venue.id}'`)
    expect(ownerView.status()).toBe(venueOwner === SEED_OWNER_EMAIL ? 200 : 404)
    assertNoErrors()
  })

  test('an image the server rejects leaves the report in place and can be retried from the same screen', async ({ page }) => {
    await loginByApi(page, email('ev-a'))
    await open(page, broken)
    await page.getByTestId('evidence-input').setInputFiles([image('tot.png'), { name: 'gia-mao.png', mimeType: 'image/png', buffer: Buffer.from('khong phai anh that') }])
    await page.getByTestId('dispute-submit').click()
    await expect(page.getByTestId('dispute-done')).toBeVisible()
    await expect(page.getByTestId('dispute-upload-failed')).toContainText('1 ảnh chưa tải được')
    await expect(page.getByTestId('dispute-upload-failed')).toContainText('gia-mao.png')
    await expect(page.getByTestId('dispute-upload-failed')).toContainText('Chi nhan anh JPEG, PNG hoac WebP')
    expect(runSql(`SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE booking_id = '${broken}'`)).toBe('1')
    await page.getByTestId('dispute-upload-retry').click()
    await expect(page.getByTestId('dispute-upload-failed')).toContainText('gia-mao.png')
    expect(runSql(`SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE booking_id = '${broken}'`)).toBe('1')
  })

  test('the picker fits a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, email('ev-a'))
    await open(page, phone)
    await page.getByTestId('evidence-input').setInputFiles([image('a.png'), image('b.png'), image('c.png')])
    const box = await page.getByTestId('evidence-picker').boundingBox()
    expect(box.x + box.width).toBeLessThanOrEqual(376)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
    await shot(page, '2388-evidence-phone')
  })
})

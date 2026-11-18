import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'
import { addPayment, bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
const DESCRIPTION = 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng'

let venue
let token
const ids = {}

function cleanup() {
  cleanupDisputes('E2EDV')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EDV%'`)
  deleteE2eUsers()
}

async function createDispute(page, bookingId, category, extra = {}) {
  const response = await page.request.post(`${API}/api/v1/bookings/${bookingId}/disputes`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { category, description: DESCRIPTION, ...extra },
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()).id
}

async function uploadImage(page, disputeId, name = 'anh.png') {
  const response = await page.request.post(`${API}/api/v1/booking-disputes/${disputeId}/evidences`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: { file: { name, mimeType: 'image/png', buffer: PNG } },
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()).id
}

test.describe.serial('2.3.89 - 2.3.90 player lists and opens own booking reports', () => {
  test.beforeAll(() => {
    cleanup()
    venue = pickVenue()
    createE2eUser({ tag: 'dv-a', name: 'E2E Người Xem Báo Cáo' })
    createE2eUser({ tag: 'dv-b', name: 'E2E Người Lạ Báo Cáo' })
    ;['waiting', 'reviewing', 'resolved', 'rejected', 'phone'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'dv-a', `E2EDV00${index + 1}`, 'completed', index + 1)
      addPayment(ids[key])
    })
  })
  test.afterAll(cleanup)

  test('seeds reports through the real API, then the list shows each state', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    ;({ token } = await loginByApi(page, email('dv-a')))
    ids.d1 = await createDispute(page, ids.waiting, 'venue_closed', { refundRequested: true, refundAmount: 90000 })
    await uploadImage(page, ids.d1, 'cong.png')
    await uploadImage(page, ids.d1, 'tin-nhan.png')
    ids.d2 = await createDispute(page, ids.reviewing, 'extra_charge')
    ids.d3 = await createDispute(page, ids.resolved, 'venue_mismatch', { refundRequested: true })
    ids.d4 = await createDispute(page, ids.rejected, 'other')
    // Trạng thái do chủ sân / quản trị viên tạo ra (các chức năng 2.3.93+ chưa có): đặt bằng SQL.
    runSql(`UPDATE booking_disputes SET status = 'reviewing', owner_response = 'E2E chủ sân giải trình: chỉ thu tiền thuê vợt', owner_responded_at = now() + interval '1 hour' WHERE id = '${ids.d2}'`)
    runSql(`UPDATE booking_disputes SET status = 'resolved', resolution = 'refund_half', resolution_note = 'E2E hoàn 50% theo thỏa thuận', handled_at = now() + interval '2 hours' WHERE id = '${ids.d3}'`)
    runSql(`UPDATE booking_disputes SET status = 'rejected', resolution = 'rejected' WHERE id = '${ids.d4}'`)

    await page.goto('/profile')
    await page.getByTestId('profile-disputes').click()
    await expect(page).toHaveURL('/bao-cao')
    await expect(page.getByTestId('disputes-pager')).toContainText('4 báo cáo')
    const waiting = page.getByTestId(`dispute-${ids.d1}`)
    await expect(waiting).toHaveAttribute('data-status', 'awaiting_owner_response')
    await expect(waiting).toContainText('Chờ chủ sân giải trình')
    await expect(waiting).toContainText('Sân đóng cửa')
    await expect(waiting).toContainText('Yêu cầu hoàn 90.000')
    await expect(waiting).toContainText('2 ảnh')
    await expect(waiting.getByTestId('dispute-deadline')).toContainText('Chủ sân còn')
    await expect(page.getByTestId(`dispute-${ids.d2}`)).toContainText('Quản trị viên đang xem xét')
    await expect(page.getByTestId(`dispute-${ids.d2}`).getByTestId('dispute-deadline')).toHaveCount(0)
    await expect(page.getByTestId(`dispute-${ids.d3}`)).toContainText('Hoàn tiền một nửa')
    await expect(page.getByTestId(`dispute-${ids.d4}`)).toContainText('Bác bỏ yêu cầu hoàn tiền')
    await shot(page, '2389-disputes-list')
    assertNoErrors()
  })

  test('tabs split active from closed reports and empty tabs say so', async ({ page }) => {
    await loginByApi(page, email('dv-a'))
    await page.goto('/bao-cao')
    await page.getByTestId('disputes-tab-active').click()
    await expect(page.getByTestId(`dispute-${ids.d1}`)).toBeVisible()
    await expect(page.getByTestId(`dispute-${ids.d2}`)).toBeVisible()
    await expect(page.getByTestId(`dispute-${ids.d3}`)).toHaveCount(0)
    await page.getByTestId('disputes-tab-closed').click()
    await expect(page.getByTestId(`dispute-${ids.d3}`)).toBeVisible()
    await expect(page.getByTestId(`dispute-${ids.d4}`)).toBeVisible()
    await expect(page.getByTestId(`dispute-${ids.d1}`)).toHaveCount(0)
    await expect(page.getByTestId('disputes-pager')).toContainText('2 báo cáo')

    // Người chưa có báo cáo nào thấy trạng thái trống.
    const other = await (await page.context().browser().newContext()).newPage()
    await loginByApi(other, email('dv-b'))
    await other.goto('/bao-cao')
    await expect(other.getByTestId('disputes-empty')).toContainText('Bạn chưa gửi báo cáo nào')
    await other.context().close()
  })

  test('the detail shows the booking, refund, deadline and the whole conversation in time order', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('dv-a'))
    await page.goto('/bao-cao')
    await page.getByTestId(`dispute-${ids.d1}`).click()
    await expect(page).toHaveURL(`/bao-cao/${ids.d1}`)
    await expect(page.getByTestId('dd-status')).toHaveText('Chờ chủ sân giải trình')
    await expect(page.getByTestId('dd-venue')).toHaveText(venue.name)
    await expect(page.getByTestId('dd-booking')).toContainText('E2EDV001')
    await expect(page.getByTestId('dd-refund')).toContainText('90.000')
    await expect(page.getByTestId('dd-deadline')).toContainText('còn')
    await expect(page.getByTestId('dd-entry-0')).toHaveAttribute('data-type', 'created')
    await expect(page.getByTestId('dd-entry-0')).toContainText(DESCRIPTION)
    await expect(page.getByTestId('dd-evidences-0').locator('img')).toHaveCount(2)
    await shot(page, '2390-dispute-detail')

    // Báo cáo đã có giải trình và quyết định: cả bốn bước hiện đúng thứ tự, đúng người gửi.
    runSql(`UPDATE booking_disputes SET reporter_updates_json = jsonb_build_array(jsonb_build_object('id', 's1', 'content', 'E2E bổ sung: có tin nhắn chứng minh', 'createdAt', to_char((now() + interval '30 minutes') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'))),
      owner_response = 'E2E chủ sân giải trình', owner_responded_at = now() + interval '1 hour', status = 'resolved', resolution = 'refund_full', resolution_note = 'E2E hoàn toàn phần', handled_at = now() + interval '2 hours' WHERE id = '${ids.d1}'`)
    await page.reload()
    const entries = page.locator('[data-testid^="dd-entry-"]')
    await expect(entries).toHaveCount(4)
    expect(await entries.evaluateAll((items) => items.map((item) => item.getAttribute('data-type')))).toEqual(['created', 'supplement', 'owner_response', 'resolution'])
    expect(await entries.evaluateAll((items) => items.map((item) => item.getAttribute('data-actor')))).toEqual(['you', 'you', 'owner', 'admin'])
    await expect(page.getByTestId('dd-entry-1')).toContainText('E2E bổ sung: có tin nhắn chứng minh')
    await expect(page.getByTestId('dd-entry-2')).toContainText('Chủ sân giải trình')
    await expect(page.getByTestId('dd-label-3')).toHaveText('Hoàn tiền toàn phần')
    await expect(page.getByTestId('dd-entry-3')).toContainText('E2E hoàn toàn phần')
    await expect(page.getByTestId('evidence-manager')).toHaveCount(0)
    assertNoErrors()
  })

  test('an image opens in a larger view and closes again', async ({ page }) => {
    await loginByApi(page, email('dv-a'))
    await page.goto(`/bao-cao/${ids.d1}`)
    await page.getByTestId('dd-evidences-0').getByRole('button').first().click()
    await expect(page.getByTestId('dd-lightbox')).toBeVisible()
    await page.getByTestId('dd-lightbox').getByRole('button', { name: 'Đóng' }).click()
    await expect(page.getByTestId('dd-lightbox')).toHaveCount(0)
  })

  test('while the owner has not answered, images can be added and removed from the detail', async ({ page }) => {
    await loginByApi(page, email('dv-a'))
    ids.d5 = await createDispute(page, ids.phone, 'owner_cancelled')
    await uploadImage(page, ids.d5)
    await page.goto(`/bao-cao/${ids.d5}`)
    await expect(page.getByTestId('evidence-manager-list').locator('li')).toHaveCount(1)
    await page.getByTestId('evidence-input').setInputFiles([{ name: 'them.png', mimeType: 'image/png', buffer: PNG }])
    await page.getByTestId('evidence-upload').click()
    await expect(page.getByTestId('evidence-manager-list').locator('li')).toHaveCount(2)
    expect(runSql(`SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = '${ids.d5}'`)).toBe('2')
    const first = runSql(`SELECT reporter_evidences_json -> 0 ->> 'id' FROM booking_disputes WHERE id = '${ids.d5}'`)
    await page.getByTestId(`evidence-delete-${first}`).click()
    await expect(page.getByTestId('evidence-manager-list').locator('li')).toHaveCount(1)
    expect(runSql(`SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = '${ids.d5}'`)).toBe('1')
    // Chủ sân vừa giải trình trong lúc đang mở trang: xóa ảnh bị từ chối và báo lỗi.
    runSql(`UPDATE booking_disputes SET status = 'reviewing' WHERE id = '${ids.d5}'`)
    const remaining = runSql(`SELECT reporter_evidences_json -> 0 ->> 'id' FROM booking_disputes WHERE id = '${ids.d5}'`)
    await page.getByTestId(`evidence-delete-${remaining}`).click()
    await expect(page.getByTestId('evidence-manager-errors')).toContainText('cho chu san giai trinh')
    expect(runSql(`SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = '${ids.d5}'`)).toBe('1')
  })

  test("another player cannot open someone else's report, and the screens fit a phone", async ({ page }) => {
    await loginByApi(page, email('dv-b'))
    await page.goto(`/bao-cao/${ids.d1}`)
    await expect(page.getByText('Không tìm thấy báo cáo')).toBeVisible()
    await loginByApi(page, email('dv-a'))
    await page.setViewportSize({ width: 375, height: 800 })
    for (const path of ['/bao-cao', `/bao-cao/${ids.d1}`]) {
      await page.goto(path)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      expect(overflow).toBeLessThanOrEqual(1)
    }
    await shot(page, '2390-dispute-detail-phone')
  })
})

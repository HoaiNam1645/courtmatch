import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'
import { bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const DESCRIPTION = 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng'
let venue
let token
const ids = {}

function cleanup() {
  cleanupDisputes('E2ECN')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2ECN%'`)
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

test.describe.serial('2.3.92 player cancels a report the owner has not answered', () => {
  test.beforeAll(() => {
    cleanup()
    venue = pickVenue()
    createE2eUser({ tag: 'cn-a', name: 'E2E Người Huỷ Báo Cáo' })
    ;['cancel', 'answered', 'phone'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'cn-a', `E2ECN00${index + 1}`, 'completed', index + 1)
    })
  })
  test.afterAll(cleanup)

  test('asks for confirmation, keeps the report when declined and cancels it when confirmed', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    ;({ token } = await loginByApi(page, email('cn-a')))
    ids.d1 = await createDispute(page, ids.cancel)
    await page.goto(`/bao-cao/${ids.d1}`)
    await page.getByTestId('dd-cancel').click()
    await expect(page.getByTestId('dd-cancel-dialog')).toContainText('kể cả yêu cầu hoàn tiền')
    await shot(page, '2392-cancel-dialog')
    await page.getByRole('button', { name: 'Giữ báo cáo' }).click()
    await expect(page.getByTestId('dd-cancel-dialog')).toHaveCount(0)
    expect(runSql(`SELECT status FROM booking_disputes WHERE id = '${ids.d1}'`)).toBe('awaiting_owner_response')

    await page.getByTestId('dd-cancel').click()
    await page.getByTestId('dd-cancel-dialog-confirm').click()
    await expect(page.getByTestId('dd-notice')).toContainText('Đã huỷ báo cáo')
    await expect(page.getByTestId('dd-status')).toHaveText('Đã hủy')
    await expect(page.getByTestId('dd-cancel')).toHaveCount(0)
    await expect(page.getByTestId('supplement-form')).toHaveCount(0)
    await expect(page.getByTestId('dd-entry-0')).toContainText(DESCRIPTION)
    expect(runSql(`SELECT status FROM booking_disputes WHERE id = '${ids.d1}'`)).toBe('cancelled')
    expect(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_dispute_cancelled' AND data_json ->> 'disputeId' = '${ids.d1}'`)).toBe('1')
    assertNoErrors()

    // Báo cáo đã huỷ nằm ở tab "Đã đóng" và đơn báo cáo lại được.
    await page.goto('/bao-cao')
    await page.getByTestId('disputes-tab-closed').click()
    await expect(page.getByTestId(`dispute-${ids.d1}`)).toHaveAttribute('data-status', 'cancelled')
    await page.goto(`/bookings/${ids.cancel}`)
    await expect(page.getByTestId('detail-report-open')).toBeVisible()
  })

  test('once the owner has answered there is no cancel button, and a late answer is rejected inside the dialog', async ({ page }) => {
    await loginByApi(page, email('cn-a'))
    ids.d2 = await createDispute(page, ids.answered)
    await page.goto(`/bao-cao/${ids.d2}`)
    await page.getByTestId('dd-cancel').click()
    // Chủ sân giải trình ngay khi hộp xác nhận đang mở: server từ chối, báo cáo giữ nguyên.
    runSql(`UPDATE booking_disputes SET status = 'reviewing', owner_response = 'E2E chủ sân giải trình', owner_responded_at = now() WHERE id = '${ids.d2}'`)
    await page.getByTestId('dd-cancel-dialog-confirm').click()
    await expect(page.getByTestId('dd-cancel-dialog')).toContainText('chu san chua giai trinh')
    expect(runSql(`SELECT status FROM booking_disputes WHERE id = '${ids.d2}'`)).toBe('reviewing')
    await page.getByRole('button', { name: 'Giữ báo cáo' }).click()
    await page.reload()
    await expect(page.getByTestId('dd-status')).toContainText('xem xét')
    await expect(page.getByTestId('dd-cancel')).toHaveCount(0)
  })

  test('the confirmation fits a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, email('cn-a'))
    ids.d3 = await createDispute(page, ids.phone)
    await page.goto(`/bao-cao/${ids.d3}`)
    await page.getByTestId('dd-cancel').click()
    const box = await page.getByTestId('dd-cancel-dialog').boundingBox()
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(376)
  })
})

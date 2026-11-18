import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { addPayment, bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
const ADMIN = 'admin@courtly.vn'
const DESCRIPTION = 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng'
const RESPONSE = 'Sân mở cửa đúng giờ, camera ghi lại cảnh khách đến muộn 40 phút'
const ids = {}

function cleanup() {
  runSql(`DELETE FROM refunds WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE 'E2EAD%')`)
  cleanupDisputes('E2EAD')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EAD%'`)
  deleteE2eUsers()
}

async function token(page, loginEmail, password = 'Courtly@123') {
  const response = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: loginEmail, password } })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json()).accessToken
}

async function createDispute(page, playerToken, bookingId, body) {
  const response = await page.request.post(`${API}/api/v1/bookings/${bookingId}/disputes`, {
    headers: { Authorization: `Bearer ${playerToken}` },
    data: { category: 'venue_closed', description: DESCRIPTION, ...body },
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()).id
}

test.describe.serial('2.3.98-2.3.99 admin views reports about bookings', () => {
  test.beforeAll(() => {
    cleanup()
    const venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    createE2eUser({ tag: 'ad-a', name: 'E2E Người Báo Cáo Admin' })
    ;['wait', 'answered', 'phone'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'ad-a', `E2EAD00${index + 1}`, 'completed', index + 1)
    })
    addPayment(ids.answered, 'confirmed')
  })
  test.afterAll(cleanup)

  test('lists the answered report first, filters and searches, and shows counters', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const playerToken = await token(page, email('ad-a'))
    ids.dWait = await createDispute(page, playerToken, ids.wait, {})
    ids.dAnswered = await createDispute(page, playerToken, ids.answered, { refundRequested: true, refundAmount: 125000 })
    ids.dPhone = await createDispute(page, playerToken, ids.phone, { category: 'extra_charge' })

    const ownerToken = await token(page, SEED_OWNER_EMAIL)
    for (const name of ['a.png', 'b.png']) {
      const upload = await page.request.post(`${API}/api/v1/owner/booking-disputes/${ids.dAnswered}/evidences`, {
        headers: { Authorization: `Bearer ${ownerToken}` }, multipart: { file: { name, mimeType: 'image/png', buffer: PNG } },
      })
      expect(upload.status(), await upload.text()).toBe(201)
    }
    const upload = await page.request.post(`${API}/api/v1/booking-disputes/${ids.dAnswered}/evidences`, {
      headers: { Authorization: `Bearer ${playerToken}` }, multipart: { file: { name: 'p.png', mimeType: 'image/png', buffer: PNG } },
    })
    expect(upload.status()).toBe(201)
    const response = await page.request.post(`${API}/api/v1/owner/booking-disputes/${ids.dAnswered}/response`, {
      headers: { Authorization: `Bearer ${ownerToken}` }, data: { content: RESPONSE },
    })
    expect(response.status(), await response.text()).toBe(200)

    await loginByApi(page, ADMIN)
    await page.goto('/admin/disputes')
    await expect(page.getByTestId(`ad-${ids.dAnswered}`)).toHaveAttribute('data-status', 'reviewing')
    await expect(page.getByTestId(`ad-${ids.dWait}`)).toHaveCount(0)
    const item = page.getByTestId(`ad-${ids.dAnswered}`)
    await expect(item.getByTestId('ad-refund-badge')).toContainText('125.000')
    await expect(item.getByTestId('ad-images')).toContainText('1 ảnh người chơi · 2 ảnh chủ sân')
    await expect(item.getByTestId('ad-waiting')).toContainText('đã chờ')
    await expect(item.getByTestId('ad-prior')).toContainText('báo cáo trước đó')
    await expect(page.getByTestId('ad-count-pending')).not.toHaveText('0')
    await shot(page, '2398-admin-disputes')

    await page.getByTestId('ad-tab-awaiting').click()
    await expect(page.getByTestId(`ad-${ids.dWait}`)).toBeVisible()
    await expect(page.getByTestId(`ad-${ids.dAnswered}`)).toHaveCount(0)
    await page.getByTestId('ad-tab-all').click()
    await page.getByLabel('Tìm báo cáo').fill('E2EAD001')
    await expect(page.getByTestId('ad-list').getByRole('listitem')).toHaveCount(1)
    await page.getByLabel('Tìm báo cáo').fill('')
    await page.getByTestId('ad-category').selectOption('extra_charge')
    await expect(page.getByTestId(`ad-${ids.dPhone}`)).toBeVisible()
    await expect(page.getByTestId(`ad-${ids.dWait}`)).toHaveCount(0)
    await page.getByTestId('ad-category').selectOption('all')
    await page.getByTestId('ad-refund').selectOption('yes')
    await expect(page.getByTestId(`ad-${ids.dAnswered}`)).toBeVisible()
    await expect(page.getByTestId(`ad-${ids.dWait}`)).toHaveCount(0)
    await page.getByTestId('ad-refund').selectOption('all')
    await page.getByTestId('ad-from').fill('2026-02-01')
    await page.getByTestId('ad-to').fill('2026-01-01')
    await expect(page.getByTestId('ad-range-error')).toBeVisible()
    assertNoErrors()
  })

  test('detail puts both sides next to each other with payment, commission and history', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, ADMIN)
    await page.goto('/admin/disputes')
    await page.getByTestId(`ad-${ids.dAnswered}`).click()
    await expect(page.getByTestId('add-detail')).toBeVisible()
    await expect(page.getByTestId('add-status')).toHaveText('Chờ xử lý')
    await expect(page.getByTestId('add-player')).toContainText('E2E Người Báo Cáo Admin')
    await expect(page.getByTestId('add-refund')).toContainText('125.000')
    await expect(page.getByTestId('add-player-entry-0')).toContainText(DESCRIPTION)
    await expect(page.getByTestId('add-player-images-0').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId('add-owner-entry-0')).toContainText(RESPONSE)
    await expect(page.getByTestId('add-owner-images-0').getByRole('listitem')).toHaveCount(2)
    await expect(page.getByTestId('add-paid')).toContainText('180.000')
    await expect(page.getByTestId('add-refunded')).toContainText('0')
    await expect(page.getByTestId('add-history')).toContainText('báo cáo')
    await page.getByTestId('add-owner-images-0').getByRole('button').first().click()
    await expect(page.getByTestId('add-lightbox')).toBeVisible()
    await shot(page, '2399-admin-dispute-detail')
    assertNoErrors()
  })

  test('a report still waiting for the owner shows that, and the phone layout does not scroll sideways', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, ADMIN)
    await page.goto(`/admin/disputes/${ids.dWait}`)
    await expect(page.getByTestId('add-owner-empty')).toContainText('chưa giải trình')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.goto('/admin/disputes')
    await expect(page.getByTestId('ad-list')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, '2398-admin-disputes-phone')
  })
})

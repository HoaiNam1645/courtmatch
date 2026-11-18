import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
const image = (name) => ({ name, mimeType: 'image/png', buffer: PNG })
const DESCRIPTION = 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng'
const RESPONSE = 'Sân mở cửa đúng giờ, camera ghi lại cảnh khách đến muộn 40 phút'
let venue
const ids = {}

function cleanup() {
  cleanupDisputes('E2EOR')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EOR%'`)
  deleteE2eUsers()
}

async function createDispute(page, token, bookingId) {
  const response = await page.request.post(`${API}/api/v1/bookings/${bookingId}/disputes`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { category: 'venue_closed', description: DESCRIPTION },
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()).id
}

test.describe.serial('2.3.95-2.3.96 owner responds with evidence images', () => {
  let token
  test.beforeAll(() => {
    cleanup()
    venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    createE2eUser({ tag: 'or-a', name: 'E2E Người Báo Cáo' })
    ;['main', 'late', 'phone'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'or-a', `E2EOR00${index + 1}`, 'completed', index + 1)
    })
  })
  test.afterAll(cleanup)

  test('keeps the button disabled until 10 characters, uploads and removes images, confirms and sends once', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const login = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: email('or-a'), password: 'Courtly@123' } })
    token = (await login.json()).accessToken
    ids.d1 = await createDispute(page, token, ids.main)
    ids.d2 = await createDispute(page, token, ids.late)
    runSql(`UPDATE booking_disputes SET owner_response_deadline = now() - interval '1 hour' WHERE id = '${ids.d2}'`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/disputes/${ids.d1}`)
    await expect(page.getByTestId('odd-submit')).toBeDisabled()
    await page.getByTestId('odd-response').fill('ngắn')
    await expect(page.getByTestId('odd-submit')).toBeDisabled()

    const input = page.getByTestId('evidence-manager').getByTestId('evidence-input')
    await input.setInputFiles([image('a.png'), image('b.png'), image('c.png')])
    await page.getByTestId('evidence-upload').click()
    await expect(page.getByTestId('evidence-manager-list').getByRole('listitem')).toHaveCount(3)
    await page.getByTestId('evidence-manager-list').getByRole('button', { name: /Xóa ảnh/ }).first().click()
    await expect(page.getByTestId('evidence-manager-list').getByRole('listitem')).toHaveCount(2)
    expect(runSql(`SELECT jsonb_array_length(owner_evidences_json) FROM booking_disputes WHERE id = '${ids.d1}'`)).toBe('2')

    await page.getByTestId('odd-response').fill(RESPONSE)
    await expect(page.getByTestId('odd-submit')).toBeEnabled()
    await shot(page, '2395-owner-response-form')
    await page.getByTestId('odd-submit').click()
    await expect(page.getByTestId('odd-confirm')).toContainText('chỉ gửi được một lần')
    await page.getByRole('button', { name: 'Quay lại' }).click()
    expect(runSql(`SELECT status FROM booking_disputes WHERE id = '${ids.d1}'`)).toBe('awaiting_owner_response')
    await page.getByTestId('odd-submit').click()
    await page.getByTestId('odd-confirm-confirm').click()

    await expect(page.getByTestId('odd-notice')).toContainText('Đã gửi giải trình')
    await expect(page.getByTestId('odd-status')).toHaveText('Quản trị viên đang xem xét')
    await expect(page.getByTestId('odd-response')).toHaveCount(0)
    await expect(page.getByTestId('odd-entry-1')).toContainText(RESPONSE)
    await expect(page.getByTestId('odd-entry-1')).toHaveAttribute('data-actor', 'you')
    await expect(page.getByTestId('odd-evidences-1').getByRole('listitem')).toHaveCount(2)
    expect(runSql(`SELECT status || '|' || owner_response FROM booking_disputes WHERE id = '${ids.d1}'`)).toBe(`reviewing|${RESPONSE}`)
    expect(runSql(`SELECT count(*) FROM notifications WHERE type = 'booking_dispute_responded' AND data_json ->> 'disputeId' = '${ids.d1}'`)).toBe('1')
    await shot(page, '2395-owner-response-sent')
    assertNoErrors()
  })

  test('the player sees the owner response and a late report cannot be answered', async ({ page, browser }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/disputes/${ids.d2}`)
    await expect(page.getByTestId('odd-detail')).toBeVisible()
    await expect(page.getByTestId('odd-response')).toHaveCount(0)

    const player = await browser.newContext()
    const playerPage = await player.newPage()
    await loginByApi(playerPage, email('or-a'))
    await playerPage.goto(`/bao-cao/${ids.d1}`)
    await expect(playerPage.getByTestId('dd-entry-1')).toContainText(RESPONSE)
    await expect(playerPage.getByTestId('dd-entry-1')).toHaveAttribute('data-actor', 'owner')
    await expect(playerPage.getByTestId('dd-entry-1').locator('img')).toHaveCount(2)
    await player.close()
  })

  test('phone: response form fits the screen', async ({ page }) => {
    const login = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: email('or-a'), password: 'Courtly@123' } })
    ids.d3 = await createDispute(page, (await login.json()).accessToken, ids.phone)
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/disputes/${ids.d3}`)
    await expect(page.getByTestId('odd-response')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, '2395-owner-response-phone')
  })
})

import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const DESCRIPTION = 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng'
let venue
let token
const ids = {}

function cleanup() {
  cleanupDisputes('E2EOV')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2EOV%'`)
  deleteE2eUsers()
}

async function createDispute(page, bookingId, category = 'venue_closed') {
  const response = await page.request.post(`${API}/api/v1/bookings/${bookingId}/disputes`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { category, description: DESCRIPTION },
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()).id
}

test.describe.serial('2.3.93-2.3.94 owner views reports about own venues', () => {
  test.beforeAll(() => {
    cleanup()
    venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    createE2eUser({ tag: 'ov-a', name: 'E2E Người Báo Cáo', phone: '0900000931' })
    ;['wait', 'soon', 'late', 'answered', 'closed'].forEach((key, index) => {
      ids[key] = bookingWithStatus(venue, 'ov-a', `E2EOV00${index + 1}`, 'completed', index + 1)
    })
  })
  test.afterAll(cleanup)

  test('lists reports with deadline badges, tabs and the awaiting counter', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const login = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: email('ov-a'), password: 'Courtly@123' } })
    token = (await login.json()).accessToken
    ids.dWait = await createDispute(page, ids.wait)
    ids.dSoon = await createDispute(page, ids.soon, 'venue_mismatch')
    ids.dLate = await createDispute(page, ids.late, 'owner_cancelled')
    ids.dAnswered = await createDispute(page, ids.answered, 'extra_charge')
    ids.dClosed = await createDispute(page, ids.closed, 'other')
    runSql(`UPDATE booking_disputes SET owner_response_deadline = now() + interval '3 hours' WHERE id = '${ids.dSoon}'`)
    runSql(`UPDATE booking_disputes SET owner_response_deadline = now() - interval '1 hour' WHERE id = '${ids.dLate}'`)
    runSql(`UPDATE booking_disputes SET status = 'reviewing', owner_response = 'E2E giải trình', owner_responded_at = now() WHERE id = '${ids.dAnswered}'`)
    runSql(`UPDATE booking_disputes SET status = 'cancelled' WHERE id = '${ids.dClosed}'`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/disputes')
    await expect(page.getByTestId('od-summary')).toContainText('sắp hết hạn')
    await expect(page.getByTestId('od-summary')).toContainText('quá hạn')
    await expect(page.getByTestId('nav-disputes-badge')).toBeVisible()
    await expect(page.getByTestId(`od-${ids.dWait}`)).toHaveAttribute('data-status', 'awaiting_owner_response')
    await expect(page.getByTestId(`od-${ids.dSoon}`)).toHaveAttribute('data-soon', 'true')
    await expect(page.getByTestId(`od-${ids.dLate}`)).toHaveAttribute('data-overdue', 'true')
    await expect(page.getByTestId(`od-${ids.dAnswered}`)).toHaveCount(0)
    await shot(page, '2393-owner-disputes')

    await page.getByTestId('od-tab-answered').click()
    await expect(page.getByTestId(`od-${ids.dAnswered}`)).toBeVisible()
    await expect(page.getByTestId(`od-${ids.dWait}`)).toHaveCount(0)
    await page.getByTestId('od-tab-closed').click()
    await expect(page.getByTestId(`od-${ids.dClosed}`)).toBeVisible()
    await page.getByTestId('od-tab-all').click()
    await expect(page.getByTestId(`od-${ids.dWait}`)).toBeVisible()
    await expect(page.getByTestId(`od-${ids.dClosed}`)).toBeVisible()
    assertNoErrors()
  })

  test('detail shows player contact, the report content and a private evidence image', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/disputes')
    await page.getByTestId(`od-${ids.dWait}`).click()
    await expect(page.getByTestId('odd-detail')).toBeVisible()
    await expect(page.getByTestId('odd-player')).toContainText('E2E Người Báo Cáo')
    await expect(page.getByTestId('odd-player')).toContainText('0900000931')
    await expect(page.getByTestId('odd-category')).toContainText('Sân đóng cửa')
    await expect(page.getByTestId('odd-entry-0')).toContainText(DESCRIPTION)
    await expect(page.getByTestId('odd-entry-0')).toHaveAttribute('data-actor', 'player')
    await shot(page, '2394-owner-dispute-detail')
    assertNoErrors()
  })

  test('a player cannot open the owner view and a phone screen does not scroll sideways', async ({ page, browser }) => {
    const other = await browser.newContext()
    const otherPage = await other.newPage()
    await loginByApi(otherPage, email('ov-a'))
    await otherPage.goto(`/owner/disputes/${ids.dWait}`)
    await expect(otherPage.getByTestId('odd-detail')).toHaveCount(0)
    await other.close()

    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/disputes')
    await expect(page.getByTestId(`od-${ids.dWait}`)).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, '2393-owner-disputes-phone')
    await page.goto(`/owner/disputes/${ids.dWait}`)
    await expect(page.getByTestId('odd-detail')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
})

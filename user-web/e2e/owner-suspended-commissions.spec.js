import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, SEED_OWNER_EMAIL, shot, watchRuntimeErrors } from './helpers'
import { bookingWithStatus, cleanupDisputes, email, pickVenue } from './reviewHelpers'

const API = 'http://127.0.0.1:8080'
const ids = {}

function cleanup() {
  runSql(`DELETE FROM booking_commissions WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE 'E2ESC%')`)
  cleanupDisputes('E2ESC')
  runSql(`DELETE FROM bookings WHERE booking_code LIKE 'E2ESC%'`)
  deleteE2eUsers()
}

test.describe.serial('2.3.97 owner sees commissions held because of a dispute', () => {
  test.beforeAll(() => {
    cleanup()
    const venue = pickVenue({ ownerEmail: SEED_OWNER_EMAIL })
    createE2eUser({ tag: 'sc-a', name: 'E2E Người Báo Cáo' })
    ids.held = bookingWithStatus(venue, 'sc-a', 'E2ESC001', 'completed', 1)
    ids.free = bookingWithStatus(venue, 'sc-a', 'E2ESC002', 'completed', 2)
  })
  test.afterAll(cleanup)

  test('empty state first, then the held commission with its total and a link to the report', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=commissions')
    await expect(page.getByTestId('suspended-card')).toBeVisible()
    await expect(page.getByTestId('suspended-card')).not.toContainText('E2ESC001')

    const login = await page.request.post(`${API}/api/v1/auth/login`, { data: { emailOrPhone: email('sc-a'), password: 'Courtly@123' } })
    const token = (await login.json()).accessToken
    const created = await page.request.post(`${API}/api/v1/bookings/${ids.held}/disputes`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { category: 'venue_closed', description: 'Sân đóng cửa khi tôi đến lúc 18h, không ai mở cổng' },
    })
    expect(created.status()).toBe(201)
    ids.dispute = (await created.json()).id
    runSql(`INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, due_at)
      SELECT b.id, v.owner_id, 180000, 'fixed', 20000, 20000, 'suspended', now() + interval '7 days' FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${ids.held}'`)
    runSql(`INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, due_at)
      SELECT b.id, v.owner_id, 180000, 'fixed', 15000, 15000, 'due', now() + interval '7 days' FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${ids.free}'`)

    await page.reload()
    await expect(page.getByTestId('suspended-E2ESC001')).toContainText('20.000')
    await expect(page.getByTestId('suspended-E2ESC002')).toHaveCount(0)
    await shot(page, '2397-suspended-card')
    await page.getByTestId('suspended-E2ESC001').getByRole('link', { name: 'Xem báo cáo' }).click()
    await expect(page.getByTestId('odd-detail')).toBeVisible()
    await expect(page.getByTestId('suspended-list')).toContainText('E2ESC001')
    await expect(page.getByTestId('suspended-total')).toContainText('20.000')
    assertNoErrors()
  })

  test('phone: the card fits the screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=commissions')
    await expect(page.getByTestId('suspended-E2ESC001')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
})

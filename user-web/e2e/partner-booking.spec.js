import { expect, test } from '@playwright/test'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  nextMondayIso,
  resetPartnerE2eData,
  runSql,
  SEED_OWNER_EMAIL,
  shot,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'
const PLAYER = 'binh.tran@example.com'
const PARTNER = 'chau.le@example.com'
const MESSAGE = 'E2E: ket noi de dat san cung nhau'

function cleanup() {
  deleteE2eVenues()
  resetPartnerE2eData()
  runSql("DELETE FROM matches WHERE booking_id IN (SELECT id FROM bookings WHERE partner_user_id IS NOT NULL AND venue_id NOT IN (SELECT id FROM venues))")
}

test.describe.serial('B5 from partner matching to booking and match', () => {
  let venue
  let partnerName
  let partnerId
  let price
  let startAt
  let endAt
  let bookingId

  test.beforeAll(cleanup)
  test.afterAll(() => {
    runSql(`DELETE FROM matches WHERE booking_id IN (SELECT id FROM bookings WHERE venue_id IN (SELECT id FROM venues WHERE name LIKE 'E2E %'))`)
    cleanup()
  })

  async function placeCheckout(page, days = 0) {
    await page.goto('/')
    const shift = (iso) => new Date(new Date(iso).getTime() + days * 86400000).toISOString()
    await page.evaluate((pending) => window.sessionStorage.setItem('courtly_pending_booking', JSON.stringify(pending)), {
      venueId: venue.id, venueName: venue.name, courtId: venue.courts[0].id, courtName: venue.courts[0].name,
      date: shift(`${nextMondayIso()}T12:00:00Z`).slice(0, 10), startTime: '09:00', endTime: '10:00', startAt: shift(startAt), endAt: shift(endAt), durationMinutes: 60, price,
    })
  }

  test('builds a venue and a confirmed partner connection', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Ghép Cặp')
    makeVenueBookable(venue.id)
    startAt = new Date(`${nextMondayIso()}T09:00:00+07:00`).toISOString()
    endAt = new Date(`${nextMondayIso()}T10:00:00+07:00`).toISOString()
    const quote = await (await page.request.post(`${API_BASE_URL}/api/v1/bookings/quote`, { data: { courtId: venue.courts[0].id, startTime: startAt, durationMinutes: 60 } })).json()
    price = Number(quote.totalAmount)

    const sender = await (await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: PLAYER, password: 'Courtly@123' } })).json()
    const receiver = await (await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { emailOrPhone: PARTNER, password: 'Courtly@123' } })).json()
    partnerName = receiver.user.fullName ?? receiver.user.name
    partnerId = receiver.user.id
    const request = await page.request.post(`${API_BASE_URL}/api/v1/partner-requests`, { headers: { Authorization: `Bearer ${sender.accessToken}` }, data: { receiverId: receiver.user.id, message: MESSAGE } })
    expect(request.status(), await request.text()).toBe(201)
    const accepted = await page.request.post(`${API_BASE_URL}/api/v1/partner-requests/${(await request.json()).id}/accept`, { headers: { Authorization: `Bearer ${receiver.accessToken}` } })
    expect(accepted.ok(), await accepted.text()).toBeTruthy()
    expect(partnerName).toBeTruthy()
  })

  test('a partner connection has a "book together" button that carries the partner to the venue list and venue page', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, PLAYER)
    await page.goto('/partners/connections')
    await page.getByTestId(`book-with-${partnerId}`).click()
    await expect(page).toHaveURL(/\/#venues$/)
    await expect(page.getByTestId('pending-partner-name')).toHaveText(partnerName)
    await page.goto(`/san/${venue.id}`)
    await expect(page.getByTestId('pending-partner')).toContainText(`chơi cùng ${partnerName}`)
    await shot(page, 'b5-venue-banner')

    await page.getByTestId('pending-partner-remove').click()
    await expect(page.getByTestId('pending-partner')).toHaveCount(0)
    await page.reload()
    await expect(page.getByTestId('pending-partner')).toHaveCount(0)
    assertNoErrors()
  })

  test('partner search results also offer it', async ({ page }) => {
    await loginByApi(page, PLAYER)
    await page.goto('/partners')
    const searched = page.waitForResponse((response) => response.request().method() === 'GET' && response.url().includes('/api/v1/partners/search?'))
    await page.getByRole('button', { name: 'Tìm đối tác phù hợp' }).click()
    const payload = await (await searched).json()
    expect(payload.content.length).toBeGreaterThan(0)
    const first = payload.content[0]
    await page.getByTestId(`book-with-${first.userId}`).click()
    await expect(page).toHaveURL(/\/#venues$/)
    await expect(page.getByTestId('pending-partner-name')).toHaveText(first.fullName)
    await page.getByTestId('pending-partner-remove').click()
  })

  test('booking without a chosen partner has no partner; booking with one keeps it on the booking', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    // Không chọn người chơi cùng.
    await loginByApi(page, PLAYER)
    await placeCheckout(page, 1)
    await page.goto('/booking/checkout')
    await expect(page.getByTestId('pending-partner')).toHaveCount(0)
    await page.locator('input[type="checkbox"][required]').check()
    await page.getByRole('button', { name: /Gửi yêu cầu|Đặt sân|Xác nhận/ }).last().click()
    await page.waitForURL(/\/bookings\/[0-9a-f-]{36}$/)
    await expect(page.getByTestId('detail-partner')).toHaveCount(0)

    // Chọn người chơi cùng từ danh sách kết nối.
    await page.goto('/partners/connections')
    await page.getByTestId(`book-with-${partnerId}`).click()
    await placeCheckout(page, 0)
    await page.goto('/booking/checkout')
    await expect(page.getByTestId('pending-partner')).toContainText(partnerName)
    await shot(page, 'b5-checkout-banner')
    await page.locator('input[type="checkbox"][required]').check()
    await page.getByRole('button', { name: /Gửi yêu cầu|Đặt sân|Xác nhận/ }).last().click()
    await page.waitForURL(/\/bookings\/[0-9a-f-]{36}$/)
    bookingId = page.url().split('/').pop()
    await expect(page.getByTestId('detail-partner')).toContainText(partnerName)
    await expect(page.getByTestId('detail-partner')).toContainText('Sau khi chơi xong')
    await expect(page.getByTestId('detail-record-match')).toHaveCount(0)
    expect(runSql(`SELECT partner_user_id FROM bookings WHERE id = '${bookingId}'`)).toBe(partnerId)
    // Đặt xong thì bỏ ghi nhớ để lần đặt sau không tự gắn người chơi cùng.
    await page.goto('/')
    await expect(page.getByTestId('pending-partner')).toHaveCount(0)
    assertNoErrors()
  })

  test('after the match is played, the booking offers to record the result with the partner preselected, and the match links to the booking', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    // Chơi xong: đưa booking về quá khứ và hoàn thành như job sau giờ chơi.
    runSql(`
      UPDATE booking_items SET start_time = now() - interval '3 hours', end_time = now() - interval '90 minutes', status = 'completed' WHERE booking_id = '${bookingId}';
      UPDATE bookings SET status = 'completed', completed_at = now() - interval '1 hour' WHERE id = '${bookingId}'`)
    await loginByApi(page, PLAYER)
    await page.goto(`/bookings/${bookingId}`)
    await expect(page.getByTestId('detail-record-match')).toContainText(`Ghi kết quả trận với ${partnerName}`)
    await shot(page, 'b5-record-link')
    await page.getByTestId('detail-record-match').click()
    await expect(page).toHaveURL(new RegExp(`/matches/new\\?booking=${bookingId}`))
    await expect(page.getByRole('heading', { name: 'Tạo trận đấu' })).toBeVisible()

    // Đối thủ được chọn sẵn là người chơi cùng.
    await page.getByRole('button', { name: 'Đánh đơn' }).click()
    const opponent = page.getByText('Đội đối phương', { exact: true }).locator('..').locator('select').first()
    await expect(opponent.locator('option:checked')).toHaveText(new RegExp(partnerName))
    await page.getByRole('button', { name: 'Tạo trận đấu', exact: true }).click()
    await expect(page).toHaveURL(/\/matches\/[0-9a-f-]{36}$/)
    const matchId = page.url().split('/').pop()
    expect(runSql(`SELECT booking_id || '|' || data_source FROM matches WHERE id = '${matchId}'`)).toBe(`${bookingId}|booking`)
    expect(runSql(`SELECT count(*) FROM match_players WHERE match_id = '${matchId}' AND user_id = '${partnerId}'`)).toBe('1')
    assertNoErrors()
  })

  test('the partner banner fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    await loginByApi(page, PLAYER)
    await page.goto('/partners/connections')
    await page.getByTestId(`book-with-${partnerId}`).click()
    await expect(page.getByTestId('pending-partner')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(page, 'b5-mobile')
    await page.getByTestId('pending-partner-remove').click()
    await context.close()
  })
})

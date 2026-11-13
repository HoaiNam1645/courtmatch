import { expect, test } from '@playwright/test'
import {
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  nextMondayIso,
  runSql,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

function slotStatus(page, venueId, monday) {
  return page.request
    .get(`${API_BASE_URL}/api/v1/venues/${venueId}/availability?date=${monday}`)
    .then((response) => response.json())
    .then((data) => Object.fromEntries(data.courts[0].slots.map((slot) => [slot.startTime, slot.status])))
}

test.describe.serial('2.3.11 owner unblocks court time slots', () => {
  let venue
  let monday

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('unlocks a range from the block list and players can book it again', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Mở Khóa')
    makeVenueBookable(venue.id)
    monday = nextMondayIso()
    runSql(`
      INSERT INTO court_blocks (id, court_id, start_time, end_time, reason, status)
      VALUES (gen_random_uuid(), '${venue.courts[0].id}', '${monday}T10:00:00+07', '${monday}T12:00:00+07', 'Bảo trì sàn', 'active')`)
    expect((await slotStatus(page, venue.id, monday))['10:00']).toBe('blocked')

    await page.goto(`/owner/venues/${venue.id}?tab=blocks`)
    await expect(page.getByTestId('block-list').locator('li')).toHaveCount(1)

    // Hủy hộp xác nhận thì không mở khóa.
    page.once('dialog', (dialog) => dialog.dismiss())
    await page.getByRole('button', { name: /^Mở khóa Sân A/ }).click()
    await expect(page.getByTestId('block-list').locator('li')).toHaveCount(1)

    page.once('dialog', (dialog) => dialog.accept())
    const unlocked = page.waitForResponse((response) => response.url().includes('/unlock'))
    await page.getByRole('button', { name: /^Mở khóa Sân A/ }).click()
    expect((await unlocked).status()).toBe(200)
    await expect(page.getByRole('status')).toHaveText('Đã mở khóa Sân A.')
    await expect(page.getByRole('heading', { name: 'Không có khung giờ nào đang bị khóa' })).toBeVisible()

    const status = await slotStatus(page, venue.id, monday)
    expect(status['10:00']).toBe('available')
    expect(status['11:00']).toBe('available')

    const player = await loginByApi(page, 'an.nguyen@example.com')
    const booking = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${player.token}` },
      data: { courtId: venue.courts[0].id, startTime: `${monday}T10:00:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    expect(booking.status(), await booking.text()).toBe(201)
    assertNoErrors()
  })

  test('unlocks from a blocked slot in the schedule', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    runSql(`
      INSERT INTO court_blocks (id, court_id, start_time, end_time, reason, status)
      VALUES (gen_random_uuid(), '${venue.courts[0].id}', '${monday}T14:00:00+07', '${monday}T16:00:00+07', 'Dọn sân', 'active')`)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=schedule`)
    await page.getByLabel('Chọn ngày').fill(monday)
    await page.getByTestId('slot-A-15:00').click()
    await expect(page.getByTestId('slot-detail')).toContainText('Dọn sân')
    page.once('dialog', (dialog) => dialog.accept())
    await page.getByRole('button', { name: 'Mở khóa khoảng này' }).click()
    await expect(page.getByTestId('slot-A-14:00')).toHaveAttribute('data-status', 'available')
    await expect(page.getByTestId('slot-A-15:00')).toHaveAttribute('data-status', 'available')
    assertNoErrors()
  })

  test('shows an error and refreshes when the block was already unlocked elsewhere', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: ['/unlock'] })
    runSql(`
      INSERT INTO court_blocks (id, court_id, start_time, end_time, reason, status)
      VALUES (gen_random_uuid(), '${venue.courts[0].id}', '${monday}T18:00:00+07', '${monday}T19:00:00+07', 'Mở ở tab khác', 'active')`)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=blocks`)
    await expect(page.getByTestId('block-list').locator('li')).toHaveCount(1)
    runSql(`UPDATE court_blocks SET status = 'cancelled' WHERE reason = 'Mở ở tab khác'`)

    page.once('dialog', (dialog) => dialog.accept())
    const conflict = page.waitForResponse((response) => response.url().includes('/unlock'))
    await page.getByRole('button', { name: /^Mở khóa Sân A/ }).click()
    expect((await conflict).status()).toBe(409)
    await expect(page.getByRole('alert')).toContainText('da duoc mo truoc do')
    await expect(page.getByRole('heading', { name: 'Không có khung giờ nào đang bị khóa' })).toBeVisible()
    assertNoErrors()
  })

  test('unlock controls fit a mobile screen', async ({ browser }) => {
    runSql(`
      INSERT INTO court_blocks (id, court_id, start_time, end_time, reason, status)
      VALUES (gen_random_uuid(), '${venue.courts[0].id}', '${monday}T20:00:00+07', '${monday}T21:00:00+07', 'Kiểm tra mobile', 'active')`)
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=blocks`)
    await expect(page.getByRole('button', { name: /^Mở khóa Sân A/ })).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

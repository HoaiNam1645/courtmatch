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

test.describe.serial('2.3.8 owner manages the venue schedule', () => {
  let venue
  let monday

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('shows free, pending, confirmed and blocked slots by court for a day', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const { token } = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, token, 'E2E Sân Lịch', [
      { name: 'Sân A', courtCode: 'A', courtType: 'standard', indoor: true },
      { name: 'Sân B', courtCode: 'B', courtType: 'standard', indoor: true },
    ])
    makeVenueBookable(venue.id)
    monday = nextMondayIso()
    const [courtA, courtB] = venue.courts

    const player = await loginByApi(page, 'an.nguyen@example.com')
    const booking = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${player.token}` },
      data: { courtId: courtA.id, startTime: `${monday}T09:00:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    expect(booking.status(), await booking.text()).toBe(201)
    const created = await booking.json()
    runSql(`
      INSERT INTO court_blocks (id, court_id, start_time, end_time, reason, status)
      VALUES (gen_random_uuid(), '${courtB.id}', '${monday}T14:00:00+07', '${monday}T15:00:00+07', 'Bảo trì lưới', 'active')`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=schedule`)
    await page.getByLabel('Chọn ngày').fill(monday)

    await expect(page.getByTestId('schedule-grid')).toBeVisible()
    const pending = page.getByTestId('slot-A-09:00')
    await expect(pending).toHaveAttribute('data-status', 'pending')
    await expect(page.getByTestId('slot-A-10:00')).toHaveAttribute('data-status', 'available')
    await expect(page.getByTestId('slot-B-14:00')).toHaveAttribute('data-status', 'blocked')

    await pending.click()
    const detail = page.getByTestId('slot-detail')
    await expect(detail).toContainText(created.bookingCode)
    await expect(detail).toContainText('Nguyen Van An')
    await page.getByTestId('slot-B-14:00').click()
    await expect(detail).toContainText('Bảo trì lưới')
    await page.getByTestId('slot-A-10:00').click()
    await expect(detail).toContainText('120.000đ')
    assertNoErrors()
  })

  test('switches to the weekly view per court and moves between weeks', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=schedule`)
    await page.getByLabel('Chọn ngày').fill(monday)
    await page.getByRole('button', { name: 'Theo tuần' }).click()
    await expect(page.getByRole('columnheader', { name: /Thứ hai/ })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /Chủ nhật/ })).toBeVisible()
    await expect(page.getByTestId(`slot-${monday}-09:00`)).toHaveAttribute('data-status', 'pending')

    await page.getByLabel('Sân con', { exact: true }).selectOption({ label: 'Sân B' })
    await expect(page.getByTestId(`slot-${monday}-09:00`)).toHaveAttribute('data-status', 'available')
    await expect(page.getByTestId(`slot-${monday}-14:00`)).toHaveAttribute('data-status', 'blocked')

    await page.getByRole('button', { name: 'Tuần sau' }).click()
    await expect(page.getByLabel('Chọn ngày')).not.toHaveValue(monday)
    await expect(page.getByTestId(`slot-${monday}-14:00`)).toHaveCount(0)
    assertNoErrors()
  })

  test('schedule fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}?tab=schedule`)
    await expect(page.getByTestId('schedule-grid')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

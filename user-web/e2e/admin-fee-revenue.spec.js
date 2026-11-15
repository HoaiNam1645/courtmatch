import { expect, test } from '@playwright/test'
import {
  createAcceptedBookings,
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  runSql,
  SEED_ADMIN_EMAIL,
  SEED_OWNER_EMAIL,
  shot,
  watchRuntimeErrors,
} from './helpers'

const vnd = (value) => `${Number(value).toLocaleString('vi-VN')}đ`
const RANGE = { from: '2020-03-01', to: '2020-03-31' }

test.describe.serial('2.3.42 admin views platform fee revenue', () => {
  let venues

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('builds two venues with commissions of every status at fixed past dates', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const a = await createE2eVenue(page, owner.token, 'E2E Sân Doanh Thu A')
    const b = await createE2eVenue(page, owner.token, 'E2E Sân Doanh Thu B')
    makeVenueBookable(a.id)
    makeVenueBookable(b.id)
    const bookingsA = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue: a, times: ['06:00', '08:00', '10:00'] })
    const bookingsB = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue: b, times: ['06:00', '08:00', '10:00'] })
    const plan = [
      [bookingsA[0], 100000, 10000, 'settled', '2020-03-10 09:00'],
      [bookingsA[1], 200000, 20000, 'due', '2020-03-10 20:00'],
      [bookingsA[2], 300000, 30000, 'pending', '2020-03-11 09:00'],
      [bookingsB[0], 150000, 15000, 'adjusted', '2020-03-11 10:00'],
      [bookingsB[1], 400000, 40000, 'suspended', '2020-03-11 11:00'],
      [bookingsB[2], 500000, 50000, 'waived', '2020-03-12 11:00'],
    ]
    for (const [booking, gross, amount, status, at] of plan) {
      runSql(`
        INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
        SELECT b.id, v.owner_id, ${gross}, 'percentage', 10, ${amount}, '${status}', timestamp '${at}' AT TIME ZONE 'Asia/Ho_Chi_Minh', timestamp '${at}' AT TIME ZONE 'Asia/Ho_Chi_Minh' + interval '7 days'
        FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    }
    venues = { a, b }
  })

  async function openCustom(page, from = RANGE.from, to = RANGE.to) {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/fee-revenue')
    await page.getByTestId('rev-preset-custom').click()
    await page.getByLabel('Từ ngày').fill(from)
    await page.getByLabel('Đến ngày').fill(to)
  }

  test('opens from the finance group and shows totals by status; suspended and waived are listed apart', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Doanh thu phí' }).click()
    await expect(page).toHaveURL('/admin/fee-revenue')
    await expect(page.getByRole('heading', { name: 'Doanh thu phí nền tảng' })).toBeVisible()
    await expect(page.getByTestId('rev-recorded')).toBeVisible()

    await page.getByTestId('rev-preset-custom').click()
    await page.getByLabel('Từ ngày').fill(RANGE.from)
    await page.getByLabel('Đến ngày').fill(RANGE.to)
    await expect(page.getByTestId('rev-recorded')).toHaveText(vnd(75000))
    await expect(page.getByTestId('rev-collected')).toHaveText(vnd(10000))
    await expect(page.getByTestId('rev-outstanding')).toHaveText(vnd(65000))
    await expect(page.getByTestId('rev-rate')).toHaveText('10%')
    await expect(page.getByTestId('rev-recorded').locator('xpath=following-sibling::p')).toContainText('4 booking')
    await expect(page.getByTestId('rev-recorded').locator('xpath=following-sibling::p')).toContainText(vnd(750000))
    await expect(page.getByTestId('rev-suspended')).toContainText(`1 hoa hồng · ${vnd(40000)}`)
    await expect(page.getByTestId('rev-waived')).toContainText(`1 hoa hồng · ${vnd(50000)}`)
    await shot(page, '23242-revenue-desktop')
    assertNoErrors()
  })

  test('the chart has one bar per day with the recorded fee, and zero for days without data', async ({ page }) => {
    await openCustom(page, '2020-03-09', '2020-03-13')
    const bars = page.locator('[data-testid^="bar-"]')
    await expect(bars).toHaveCount(5)
    await expect(page.getByTestId('bar-2020-03-09')).toHaveAttribute('data-value', '0')
    await expect(page.getByTestId('bar-2020-03-10')).toHaveAttribute('data-value', '30000')
    await expect(page.getByTestId('bar-2020-03-11')).toHaveAttribute('data-value', '45000')
    await expect(page.getByTestId('bar-2020-03-12')).toHaveAttribute('data-value', '0')
    await expect(page.getByTestId('rev-recorded')).toHaveText(vnd(75000))
    await page.getByTestId('rev-granularity-month').click()
    await expect(bars).toHaveCount(1)
    await expect(page.getByTestId('bar-2020-03-01')).toHaveAttribute('data-value', '75000')
  })

  test('by-venue list is sorted by fee and the venue filter narrows everything to one venue', async ({ page }) => {
    await openCustom(page)
    const list = page.getByTestId('rev-venues')
    const rowA = list.getByTestId('rev-venue-E2E Sân Doanh Thu A')
    const rowB = list.getByTestId('rev-venue-E2E Sân Doanh Thu B')
    await expect(rowA.getByTestId('rev-venue-recorded')).toHaveText(vnd(60000))
    await expect(rowA).toContainText('3 booking')
    await expect(rowA.getByTestId('rev-venue-collected')).toHaveText(vnd(10000))
    await expect(rowA.getByTestId('rev-venue-outstanding')).toHaveText(vnd(50000))
    await expect(rowB.getByTestId('rev-venue-recorded')).toHaveText(vnd(15000))
    const names = await list.getByRole('listitem').allInnerTexts()
    expect(names.findIndex((text) => text.includes('Sân Doanh Thu A'))).toBeLessThan(names.findIndex((text) => text.includes('Sân Doanh Thu B')))

    await page.getByLabel('Lọc theo sân').selectOption(venues.b.id)
    await expect(page.getByTestId('rev-recorded')).toHaveText(vnd(15000))
    await expect(page.getByTestId('rev-suspended')).toContainText(vnd(40000))
    await expect(list.getByRole('listitem')).toHaveCount(1)
    await page.getByLabel('Lọc theo sân').selectOption(venues.a.id)
    await expect(page.getByTestId('rev-recorded')).toHaveText(vnd(60000))
    await expect(page.getByTestId('rev-excluded')).toHaveCount(0)
    await shot(page, '23242-revenue-venue')
  })

  test('custom range validation, an empty range and a month view', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await openCustom(page, '2020-02-01', '2020-01-01')
    await expect(page.getByTestId('rev-range-error')).toContainText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu')
    await page.getByLabel('Từ ngày').fill('2018-01-01')
    await page.getByLabel('Đến ngày').fill('2020-01-01')
    await expect(page.getByTestId('rev-range-error')).toContainText('tối đa 366 ngày')
    await expect(page.getByTestId('rev-recorded')).toHaveCount(0)

    await page.getByLabel('Từ ngày').fill('1999-01-01')
    await page.getByLabel('Đến ngày').fill('1999-01-10')
    await expect(page.getByTestId('rev-range-error')).toHaveCount(0)
    await expect(page.getByTestId('rev-recorded')).toHaveText(vnd(0))
    await expect(page.getByTestId('rev-rate')).toHaveText('—')
    await expect(page.getByTestId('rev-empty')).toBeVisible()
    await expect(page.getByText('Chưa có sân nào')).toBeVisible()
    await expect(page.getByTestId('rev-excluded')).toHaveCount(0)
    await expect(page.locator('[data-testid^="bar-"]')).toHaveCount(10)
    assertNoErrors()
  })

  test('shows an error state with retry, fits a mobile screen, and players cannot open the page', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    let fail = true
    await page.route('**/api/v1/admin/finance/fee-revenue**', async (route) => {
      if (fail) await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Lỗi máy chủ' }) })
      else await route.continue()
    })
    await page.goto('/admin/fee-revenue')
    await expect(page.getByRole('button', { name: /Thử lại/ })).toBeVisible()
    fail = false
    await page.getByRole('button', { name: /Thử lại/ }).click()
    await expect(page.getByTestId('rev-recorded')).toBeVisible()

    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const mobile = await context.newPage()
    await openCustom(mobile)
    await expect(mobile.getByTestId('rev-recorded')).toHaveText(vnd(75000))
    expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    await shot(mobile, '23242-revenue-mobile')
    await context.close()

    await loginByApi(page, 'an.nguyen@example.com')
    await page.goto('/admin/fee-revenue')
    await expect(page.getByRole('heading', { name: 'Doanh thu phí nền tảng' })).toHaveCount(0)
  })
})

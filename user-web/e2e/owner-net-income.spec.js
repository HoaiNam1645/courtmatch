import { expect, test } from '@playwright/test'
import {
  collectPayments,
  createAcceptedBookings,
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  runSql,
  SEED_OWNER_EMAIL,
  watchRuntimeErrors,
} from './helpers'

test.describe.serial('2.3.19 owner views the net amount received per booking', () => {
  let venue
  let codes = {}

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('shows gross - commission - refunds per booking and for the whole list', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Thực Nhận')
    makeVenueBookable(venue.id)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['07:00', '09:00'] })
    await collectPayments(page, { ownerToken: owner.token, playerToken: player.token, bookings })
    const [recorded, estimated] = bookings
    codes = { recorded: recorded.bookingCode, estimated: estimated.bookingCode }

    // Booking 1: đã ghi nhận hoa hồng 12.000đ, đã hoàn 20.000đ và còn 5.000đ hoàn chưa xác nhận.
    runSql(`
      UPDATE bookings SET status = 'completed', completed_at = now() WHERE id = '${recorded.id}';
      INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, calculated_at, due_at)
      SELECT b.id, v.owner_id, b.total_amount, 'percentage', 10, 12000, 'due', now(), now() + interval '7 days'
      FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${recorded.id}';
      INSERT INTO refunds (id, payment_id, booking_id, amount, status)
      SELECT gen_random_uuid(), p.id, p.booking_id, 20000, 'confirmed' FROM payments p WHERE p.booking_id = '${recorded.id}';
      INSERT INTO refunds (id, payment_id, booking_id, amount, status)
      SELECT gen_random_uuid(), p.id, p.booking_id, 5000, 'approved' FROM payments p WHERE p.booking_id = '${recorded.id}'`)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance')
    await page.getByRole('tab', { name: 'Thực nhận' }).click()
    await expect(page).toHaveURL('/owner/finance?tab=net')
    await page.getByLabel('Lọc thực nhận theo sân').selectOption(venue.id)

    await expect(page.getByTestId('net-list').locator('li')).toHaveCount(2)
    await expect(page.getByTestId('net-total-gross')).toHaveText('240.000đ')
    await expect(page.getByTestId('net-total-commission')).toHaveText('24.000đ')
    await expect(page.getByTestId('net-total-refunded')).toHaveText('20.000đ')
    await expect(page.getByTestId('net-total-net')).toHaveText('196.000đ')
    await expect(page.getByTestId('net-pending-refund')).toContainText('5.000đ')

    const first = page.getByTestId(`net-${codes.recorded}`)
    await expect(first.getByTestId('net-gross')).toHaveText('120.000đ')
    await expect(first.getByTestId('net-commission')).toHaveText('12.000đ')
    await expect(first.getByTestId('net-refunded')).toHaveText('20.000đ')
    await expect(first.getByTestId('net-amount')).toHaveText('88.000đ')
    await expect(first).not.toContainText('ước tính')

    const second = page.getByTestId(`net-${codes.estimated}`)
    await expect(second.getByTestId('net-amount')).toHaveText('108.000đ')
    await expect(second).toContainText('ước tính')
    assertNoErrors()
  })

  test('filters by collection date and rejects an inverted range', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=net')
    await page.getByLabel('Lọc thực nhận theo sân').selectOption(venue.id)
    await expect(page.getByTestId('net-list').locator('li')).toHaveCount(2)

    // Chọn "từ ngày" quá xa khi chưa có "đến ngày" (mặc định hôm nay): báo ngay, không gọi API.
    await page.getByLabel('Thu tiền từ ngày').fill('2020-01-01')
    await expect(page.getByRole('alert')).toContainText('Chỉ xem tối đa 366 ngày')
    await page.getByLabel('Thu tiền đến ngày').fill('2020-01-31')
    await expect(page.getByRole('heading', { name: 'Chưa có booking nào đã thu tiền trong kỳ này' })).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await page.getByLabel('Thu tiền từ ngày').fill('2031-01-31')
    await expect(page.getByRole('alert')).toContainText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu')
    assertNoErrors()
  })

  test('shows an error state with retry when the API fails', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    let failing = true
    await page.route('**/api/v1/owner/net-income?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/owner/finance?tab=net')
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByLabel('Lọc thực nhận theo sân')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('net income tab fits a mobile screen', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const page = await context.newPage()
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=net')
    await page.getByLabel('Lọc thực nhận theo sân').selectOption(venue.id)
    await expect(page.getByTestId('net-list')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

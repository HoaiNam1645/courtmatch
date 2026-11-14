import { expect, test } from '@playwright/test'
import { createE2eVenue, deleteE2eVenues, loginByApi, makeVenueBookable, runSql, SEED_ADMIN_EMAIL, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.35 admin rejects venues', () => {
  let venue
  let busyVenue

  test.beforeAll(deleteE2eVenues)
  test.afterAll(() => {
    runSql("DELETE FROM audit_logs WHERE action IN ('venue.reject', 'venue.resubmit', 'venue.approve') AND created_at > now() - interval '1 hour' AND (new_data ->> 'venueName') LIKE 'E2E %'")
    deleteE2eVenues()
  })

  test('rejects a pending venue with a catalogue reason and the owner sees it', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    venue = await createE2eVenue(page, owner.token, 'E2E Sân Bị Từ Chối')
    makeVenueBookable(venue.id)
    runSql(`UPDATE venues SET status = 'pending', approval_status = 'pending' WHERE id = '${venue.id}'`)

    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/venues')
    await page.getByLabel('Tìm sân').fill('E2E Sân Bị Từ Chối')
    await page.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Bị Từ Chối' }).click()
    const drawer = page.getByTestId('venue-drawer')
    await drawer.getByTestId('venue-reject-open').click()
    const form = drawer.getByTestId('venue-reject-form')
    await form.getByRole('button', { name: 'Từ chối sân' }).click()
    await expect(form.getByText('Vui lòng chọn lý do từ chối')).toBeVisible()
    await form.getByLabel('Lý do từ chối sân').selectOption('other')
    await form.getByRole('button', { name: 'Từ chối sân' }).click()
    await expect(form.getByText('Vui lòng nhập ghi chú (ít nhất 3 ký tự) khi chọn lý do khác')).toBeVisible()
    await form.getByLabel('Lý do từ chối sân').selectOption('invalid_location')
    await form.getByLabel('Ghi chú từ chối sân').fill('Tọa độ lệch so với địa chỉ')
    const rejected = page.waitForResponse((response) => response.url().endsWith('/reject') && response.request().method() === 'POST')
    await form.getByRole('button', { name: 'Từ chối sân' }).click()
    expect((await rejected).status()).toBe(200)
    await expect(drawer.getByTestId('venue-drawer-status')).toHaveText('Đã từ chối')
    await expect(drawer.getByTestId('venue-rejection')).toContainText('Lý do: Dia chi hoac toa do khong chinh xac: Tọa độ lệch so với địa chỉ')
    await expect(drawer.getByTestId('venue-approve')).toHaveCount(0)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã từ chối sân E2E Sân Bị Từ Chối')
    expect(runSql(`SELECT status || '|' || approval_status || '|' || rejection_code FROM venues WHERE id = '${venue.id}'`)).toBe('rejected|rejected|invalid_location')
    expect((await page.request.get(`${API_BASE_URL}/api/v1/venues/${venue.id}`)).status()).toBe(404)

    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}`)
    await expect(page.getByTestId('venue-rejection-reason')).toContainText('Tọa độ lệch so với địa chỉ')
    assertNoErrors()
  })

  test('the owner resubmits the venue and the admin approves it', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto(`/owner/venues/${venue.id}`)
    const resubmitted = page.waitForResponse((response) => response.url().endsWith('/resubmit') && response.request().method() === 'POST')
    await page.getByTestId('venue-resubmit').click()
    expect((await resubmitted).status()).toBe(200)
    await expect(page.getByRole('status')).toContainText('Đã gửi sân để quản trị viên duyệt lại.')
    await expect(page.getByTestId('venue-rejection')).toHaveCount(0)
    expect(runSql(`SELECT approval_status FROM venues WHERE id = '${venue.id}'`)).toBe('pending')

    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/venues')
    await page.getByLabel('Tìm sân').fill('E2E Sân Bị Từ Chối')
    await page.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Bị Từ Chối' }).click()
    await page.getByTestId('venue-approve').click()
    await expect(page.getByTestId('venue-drawer-status')).toHaveText('Đã duyệt')
    await expect(page.getByTestId('venue-rejection')).toHaveCount(0)
    assertNoErrors()
  })

  test('refuses to reject a venue with open bookings and shows the server message', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    busyVenue = await createE2eVenue(page, owner.token, 'E2E Sân Có Booking')
    makeVenueBookable(busyVenue.id)
    const booking = await page.request.post(`${API_BASE_URL}/api/v1/bookings`, {
      headers: { Authorization: `Bearer ${player.token}` },
      data: { courtId: busyVenue.courts[0].id, startTime: `${runSql("SELECT (date_trunc('week', now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + 7)::text")}T09:00:00+07:00`, durationMinutes: 60, note: 'E2E' },
    })
    expect(booking.status(), await booking.text()).toBe(201)
    runSql(`UPDATE venues SET status = 'pending', approval_status = 'pending' WHERE id = '${busyVenue.id}'`)

    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/venues')
    await page.getByLabel('Tìm sân').fill('E2E Sân Có Booking')
    await page.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Có Booking' }).click()
    const drawer = page.getByTestId('venue-drawer')
    await drawer.getByTestId('venue-reject-open').click()
    await drawer.getByLabel('Lý do từ chối sân').selectOption('incomplete_info')
    await drawer.getByRole('button', { name: 'Từ chối sân' }).click()
    await expect(drawer.getByRole('alert')).toContainText('booking dang xu ly')
    expect(runSql(`SELECT approval_status FROM venues WHERE id = '${busyVenue.id}'`)).toBe('pending')
    await drawer.getByRole('button', { name: 'Hủy' }).click()
    await expect(drawer.getByTestId('venue-approve')).toBeVisible()
  })

  test('shows an error state when the reasons fail to load, and fits a mobile screen', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    let failing = true
    await page.route('**/api/v1/admin/venues/reject-reasons', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.goto('/admin/venues')
    await page.getByLabel('Tìm sân').fill('E2E Sân Có Booking')
    await page.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Có Booking' }).click()
    await page.getByTestId('venue-reject-open').click()
    await expect(page.getByTestId('venue-reject-form').getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByTestId('venue-reject-form').getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByLabel('Lý do từ chối sân')).toBeVisible()

    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const mobile = await context.newPage()
    await loginByApi(mobile, SEED_ADMIN_EMAIL)
    await mobile.goto('/admin/venues')
    await mobile.getByLabel('Tìm sân').fill('E2E Sân Có Booking')
    await mobile.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Có Booking' }).click()
    await mobile.getByTestId('venue-reject-open').click()
    await expect(mobile.getByTestId('venue-reject-form')).toBeVisible()
    const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })
})

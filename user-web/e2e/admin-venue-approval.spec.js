import { expect, test } from '@playwright/test'
import { createE2eVenue, deleteE2eVenues, loginByApi, makeVenueBookable, runSql, SEED_ADMIN_EMAIL, SEED_OWNER_EMAIL, watchRuntimeErrors } from './helpers'

const API_BASE_URL = 'http://127.0.0.1:8080'

test.describe.serial('2.3.34 admin approves venues', () => {
  let ready
  let incomplete

  test.beforeAll(deleteE2eVenues)
  test.afterAll(() => {
    runSql("DELETE FROM audit_logs WHERE action = 'venue.approve' AND created_at > now() - interval '1 hour' AND (new_data ->> 'venueName') LIKE 'E2E %'")
    deleteE2eVenues()
  })

  async function setup(page) {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    ready = await createE2eVenue(page, owner.token, 'E2E Sân Chờ Duyệt Đủ')
    incomplete = await createE2eVenue(page, owner.token, 'E2E Sân Chờ Duyệt Thiếu')
    makeVenueBookable(ready.id)
    // Sân thứ nhất đủ điều kiện nhưng vẫn chờ duyệt; sân thứ hai chưa có giờ và giá.
    runSql(`UPDATE venues SET status = 'pending', approval_status = 'pending' WHERE id = '${ready.id}'`)
  }

  test('lists pending venues, opens one and shows its checklist', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await setup(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin')
    await page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Duyệt sân' }).click()
    await expect(page).toHaveURL('/admin/venues')

    await page.getByLabel('Tìm sân').fill('E2E Sân Chờ Duyệt')
    const list = page.getByTestId('venue-list').getByRole('listitem')
    await expect(list).toHaveCount(2)
    await expect(page.getByTestId('venue-E2E Sân Chờ Duyệt Đủ')).toContainText('Chủ sân')
    await expect(page.getByTestId('venue-E2E Sân Chờ Duyệt Đủ').getByTestId('venue-approval')).toHaveText('Chờ duyệt')

    await page.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Chờ Duyệt Đủ' }).click()
    const drawer = page.getByTestId('venue-drawer')
    await expect(drawer.getByTestId('venue-check-location')).toBeVisible()
    for (const code of ['location', 'active_court', 'operating_hours', 'prices']) {
      await expect(drawer.getByTestId(`venue-check-${code}`)).toHaveClass(/text-emerald-700/)
    }
    await expect(drawer.getByTestId('venue-hours').getByRole('listitem')).toHaveCount(7)
    await expect(drawer.getByTestId('venue-courts')).toContainText('120.000đ')
    await expect(drawer.getByTestId('venue-approve')).toBeEnabled()
    await page.keyboard.press('Escape')

    await page.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Chờ Duyệt Thiếu' }).click()
    await expect(drawer.getByTestId('venue-check-prices')).toHaveClass(/text-red-700/)
    await expect(drawer.getByTestId('venue-check-operating_hours')).toHaveClass(/text-red-700/)
    await expect(drawer.getByTestId('venue-approve')).toBeDisabled()
    await expect(drawer.getByTestId('venue-cannot-approve')).toBeVisible()
    assertNoErrors()
  })

  test('approves a venue: it becomes visible to players', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page, { ignoredUrls: [`/api/v1/venues/${ready?.id}`] })
    const before = await page.request.get(`${API_BASE_URL}/api/v1/venues/${ready.id}`)
    expect(before.status()).toBe(404)

    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/venues')
    await page.getByLabel('Tìm sân').fill('E2E Sân Chờ Duyệt Đủ')
    await page.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Chờ Duyệt Đủ' }).click()
    const drawer = page.getByTestId('venue-drawer')
    const approved = page.waitForResponse((response) => response.url().endsWith('/approve') && response.request().method() === 'POST')
    await drawer.getByTestId('venue-approve').click()
    expect((await approved).status()).toBe(200)
    await expect(drawer.getByTestId('venue-drawer-status')).toHaveText('Đã duyệt')
    await expect(drawer.getByTestId('venue-approved-by')).toContainText('Quan Tri Courtly')
    await expect(drawer.getByTestId('venue-approve')).toHaveCount(0)
    await expect(page.getByTestId('admin-notice')).toContainText('Đã duyệt sân E2E Sân Chờ Duyệt Đủ')

    const after = await page.request.get(`${API_BASE_URL}/api/v1/venues/${ready.id}`)
    expect(after.status()).toBe(200)
    expect(runSql(`SELECT status || '|' || approval_status FROM venues WHERE id = '${ready.id}'`)).toBe('active|approved')
    assertNoErrors()
  })

  test('shows the server conflict when the venue was handled elsewhere, the empty tab and an error state', async ({ page, browser }) => {
    await loginByApi(page, SEED_ADMIN_EMAIL)
    makeVenueBookable(incomplete.id)
    runSql(`UPDATE venues SET status = 'pending', approval_status = 'pending' WHERE id = '${incomplete.id}'`)
    await page.goto('/admin/venues')
    await page.getByLabel('Tìm sân').fill('E2E Sân Chờ Duyệt Thiếu')
    await page.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Chờ Duyệt Thiếu' }).click()
    const drawer = page.getByTestId('venue-drawer')
    await expect(drawer.getByTestId('venue-approve')).toBeEnabled()
    runSql(`UPDATE venues SET status = 'rejected', approval_status = 'rejected' WHERE id = '${incomplete.id}'`)
    await drawer.getByTestId('venue-approve').click()
    await expect(drawer.getByRole('alert')).toContainText('Chi duyet duoc san dang cho duyet')
    await page.keyboard.press('Escape')

    await page.getByLabel('Tìm sân').fill('khong-co-san-nay')
    await expect(page.getByRole('heading', { name: 'Không có sân nào' })).toBeVisible()
    await page.getByLabel('Tìm sân').fill('')
    let failing = true
    await page.route('**/api/v1/admin/venues?**', (route) => (failing
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Hệ thống đang gặp sự cố"}' })
      : route.continue()))
    await page.getByTestId('venue-tab-approved').click()
    await expect(page.getByRole('alert')).toContainText('Hệ thống đang gặp sự cố')
    failing = false
    await page.getByRole('button', { name: 'Thử lại' }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)

    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true })
    const mobile = await context.newPage()
    await loginByApi(mobile, SEED_ADMIN_EMAIL)
    await mobile.goto('/admin/venues')
    await mobile.getByTestId('venue-tab-all').click()
    await mobile.getByLabel('Tìm sân').fill('E2E Sân')
    await mobile.getByRole('button', { name: 'Xem chi tiết sân E2E Sân Chờ Duyệt Đủ' }).click()
    await expect(mobile.getByTestId('venue-checks')).toBeVisible()
    const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    await context.close()
  })

  test('owners cannot open the admin venue page', async ({ page }) => {
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/admin/venues')
    await expect(page.getByRole('heading', { name: 'Bạn không có quyền truy cập' })).toBeVisible()
  })
})

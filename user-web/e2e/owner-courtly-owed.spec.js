import { expect, test } from '@playwright/test'
import {
  createAcceptedBookings,
  createE2eVenue,
  deleteE2eVenues,
  loginByApi,
  makeVenueBookable,
  runSql,
  SEED_OWNER_EMAIL,
  shot,
  watchRuntimeErrors,
} from './helpers'

test.describe.serial('Khoản Courtly còn nợ chủ sân ở màn công nợ', () => {
  let bookings

  test.beforeAll(deleteE2eVenues)
  test.afterAll(deleteE2eVenues)

  test('dựng 4 booking: voucher vượt hoa hồng, đã bù trừ, được miễn và không voucher', async ({ page }) => {
    const owner = await loginByApi(page, SEED_OWNER_EMAIL)
    const player = await loginByApi(page, 'an.nguyen@example.com')
    const venue = await createE2eVenue(page, owner.token, 'E2E Sân Courtly Nợ')
    makeVenueBookable(venue.id)
    bookings = await createAcceptedBookings(page, { ownerToken: owner.token, playerToken: player.token, venue, times: ['06:00', '08:00', '10:00', '12:00'] })
    const rows = [
      { status: 'due', discount: 58000, owed: 40000, cleared: false },
      { status: 'due', discount: 30000, owed: 12000, cleared: true },
      { status: 'waived', discount: 20000, owed: 8000, cleared: false },
      { status: 'due', discount: 0, owed: 0, cleared: false },
    ]
    bookings.forEach((booking, index) => {
      const row = rows[index]
      runSql(`
        INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, voucher_discount, courtly_owed, courtly_owed_cleared_at, status, calculated_at, due_at)
        SELECT b.id, v.owner_id, 180000, 'percentage', 10, 18000, ${row.discount}, ${row.owed}, ${row.cleared ? 'now()' : 'NULL'}, '${row.status}', now(), now() + interval '5 days'
        FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${booking.id}'`)
    })
  })

  test('chủ sân thấy khoản Courtly còn nợ tách riêng, không cộng vào công nợ hoa hồng', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')

    const card = page.getByTestId('courtly-owed')
    await expect(card).toBeVisible()
    await expect(card.getByRole('heading', { name: 'Courtly còn nợ bạn' })).toBeVisible()
    await expect(page.getByTestId('courtly-owed-total')).toContainText('40.000')
    await expect(page.getByTestId('courtly-owed-items').getByRole('listitem')).toHaveCount(1)
    await expect(page.getByTestId(`courtly-owed-${bookings[0].bookingCode}`)).toContainText('Voucher giảm')
    for (const other of bookings.slice(1)) {
      await expect(page.getByTestId(`courtly-owed-${other.bookingCode}`)).toHaveCount(0)
    }
    // Công nợ chỉ gồm ba booking còn phải trả (18.000 x 3 = 54.000), không trừ khoản Courtly nợ; booking được miễn không tính.
    await expect(page.getByTestId('debt-total')).toContainText('54.000')
    await card.scrollIntoViewIfNeeded()
    await shot(page, 'owner-courtly-owed-desktop')
    assertNoErrors()
  })

  test('hiển thị gọn trên điện thoại, không tràn ngang', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('courtly-owed')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
    expect(overflow).toBe(false)
    await page.getByTestId('courtly-owed').scrollIntoViewIfNeeded()
    await shot(page, 'owner-courtly-owed-mobile')
  })

  test('khoản đã được bù trừ thì thẻ biến mất', async ({ page }) => {
    runSql(`UPDATE booking_commissions SET courtly_owed_cleared_at = now() WHERE booking_id = '${bookings[0].id}'`)
    await loginByApi(page, SEED_OWNER_EMAIL)
    await page.goto('/owner/finance?tab=debt')
    await expect(page.getByTestId('debt-total')).toBeVisible()
    await expect(page.getByTestId('courtly-owed')).toHaveCount(0)
  })
})

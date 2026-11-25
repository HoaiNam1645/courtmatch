import { expect, test } from '@playwright/test'
import { loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'

const PLAYER = 'an.nguyen@example.com'
const PAGES = ['/', '/profile', '/partners', '/bookings', '/matches', '/notifications', '/bao-cao']

/** Thanh dưới phải dính đáy khung nhìn và không che nội dung cuối trang. */
async function expectBottomNavDocked(page) {
  const viewport = page.viewportSize()
  const nav = page.getByTestId('bottom-nav')
  await expect(nav).toBeVisible()
  const box = await nav.boundingBox()
  expect(Math.round(box.y + box.height)).toBe(viewport.height)
  const spacer = await page.getByTestId('bottom-nav-spacer').boundingBox()
  expect(spacer.height).toBeGreaterThanOrEqual(box.height - 1)
}

test.describe('Thanh điều hướng chính', () => {
  test('điện thoại: thanh dưới dính đáy ở mọi trang, cuộn hết trang không che nội dung, không cuộn ngang', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await page.setViewportSize({ width: 390, height: 844 })
    await loginByApi(page, PLAYER)
    for (const path of PAGES) {
      await page.goto(path)
      await expect(page.getByTestId('bottom-nav')).toBeVisible()
      await expect(page.getByTestId('desktop-nav')).toBeHidden()
      await expectBottomNavDocked(page)
      // Cuộn tới cuối: phần tử cuối cùng của trang (trước khoảng đệm) phải nằm trên thanh dưới.
      await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
      await page.waitForTimeout(150)
      await expectBottomNavDocked(page)
      // Moi phan tu noi dung dang hien (khong phai thanh duoi, khoang dem hay phan tu co dinh) phai ket thuc phia tren thanh duoi.
      const covered = await page.evaluate(() => {
        const nav = document.querySelector('[data-testid="bottom-nav"]')
        const top = nav.getBoundingClientRect().top
        const offenders = []
        for (const el of document.body.querySelectorAll('*')) {
          if (nav.contains(el) || el.closest('[data-testid="bottom-nav-spacer"]')) continue
          const style = getComputedStyle(el)
          if (style.position === 'fixed' || style.visibility === 'hidden' || style.display === 'none' || el.closest('.leaflet-container')) continue
          // Chi xet noi dung nhin thay duoc: phan tu co chu truc tiep, anh, nut, o nhap, bieu tuong (bo khung bao ngoai).
          const ownText = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim() !== '')
          if (!ownText && !['IMG', 'BUTTON', 'INPUT', 'svg', 'SELECT', 'TEXTAREA'].includes(el.tagName)) continue
          const r = el.getBoundingClientRect()
          if (r.height > 0 && r.width > 0 && r.bottom > top + 1 && r.top < window.innerHeight) offenders.push(`${el.tagName}.${String(el.className).slice(0, 40)}`)
        }
        return offenders.slice(0, 3)
      })
      expect(covered, path).toEqual([])
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), path).toBe(true)
    }
    await page.goto('/profile')
    await expect(page.getByTestId('bottom-nav').getByRole('link', { name: 'Tài khoản' })).toHaveAttribute('aria-current', 'page')
    await shot(page, 'nav-phone-profile')
    assertNoErrors()
  })

  test('điện thoại: "Khám phá" mở danh sách sân ở trang chủ (trước đây trỏ tới /explore không tồn tại)', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginByApi(page, PLAYER)
    await page.goto('/profile')
    await page.getByTestId('bottom-nav').getByRole('link', { name: 'Khám phá' }).click()
    await expect(page).toHaveURL(/\/#venues$/)
    await expect(page.locator('#venues')).toBeInViewport()
    await expect(page.getByTestId('bottom-nav').getByRole('link', { name: 'Khám phá' })).toHaveAttribute('aria-current', 'page')
    const slug = runSql(`SELECT slug FROM venues WHERE status = 'active' AND approval_status = 'approved' ORDER BY created_at LIMIT 1`)
    await page.goto(`/san/${slug}`)
    await expect(page.getByTestId('bottom-nav').getByRole('link', { name: 'Khám phá' })).toHaveAttribute('aria-current', 'page')
  })

  test('máy tính: không còn thanh dưới kiểu điện thoại, các mục nằm trên thanh trên và đánh dấu đúng mục đang mở', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await page.setViewportSize({ width: 1440, height: 900 })
    await loginByApi(page, PLAYER)
    for (const [path, label] of [['/', 'Trang chủ'], ['/partners', 'Đối tác'], ['/profile', 'Tài khoản'], ['/bookings', 'Tài khoản']]) {
      await page.goto(path)
      await expect(page.getByTestId('bottom-nav')).toBeHidden()
      await expect(page.getByTestId('bottom-nav-spacer')).toBeHidden()
      const nav = page.getByTestId('desktop-nav')
      await expect(nav).toBeVisible()
      await expect(nav.getByRole('link')).toHaveCount(5)
      await expect(nav.getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page')
    }
    await page.goto('/profile')
    await page.getByTestId('desktop-nav').getByRole('link', { name: 'Đối tác' }).click()
    await expect(page).toHaveURL(/\/partners$/)
    await shot(page, 'nav-desktop-partners')
    assertNoErrors()
  })

  test('bản đồ toàn màn hình: giữ thanh dưới ở mọi kích thước (điện thoại và máy tính) và bản đồ không bị thanh che', async ({ page }) => {
    await loginByApi(page, PLAYER)
    for (const size of [{ width: 390, height: 844 }, { width: 1440, height: 900 }]) {
      await page.setViewportSize(size)
      await page.goto('/map')
      const nav = page.getByTestId('bottom-nav')
      await expect(nav).toBeVisible()
      const box = await nav.boundingBox()
      expect(Math.round(box.y + box.height)).toBe(size.height)
      const map = await page.locator('.leaflet-container').boundingBox()
      expect(map.y + map.height).toBeLessThanOrEqual(box.y + 1)
      await expect(nav.getByRole('link', { name: 'Bản đồ' })).toHaveAttribute('aria-current', 'page')
    }
  })

  test('máy tính bảng (900px): vẫn dùng thanh dưới, thanh trên không chen các mục', async ({ page }) => {
    await page.setViewportSize({ width: 900, height: 1100 })
    await loginByApi(page, PLAYER)
    await page.goto('/profile')
    await expect(page.getByTestId('desktop-nav')).toBeHidden()
    await expectBottomNavDocked(page)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })

  test('khách chưa đăng nhập: thanh dưới vẫn có và "Tài khoản" dẫn tới đăng nhập', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/')
    await expectBottomNavDocked(page)
    await expect(page.getByTestId('bottom-nav').getByRole('link', { name: 'Tài khoản' })).toHaveAttribute('href', '/login')
  })
})

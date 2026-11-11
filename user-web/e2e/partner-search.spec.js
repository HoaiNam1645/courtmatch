import { expect, test } from '@playwright/test'
import { loginByApi, resetPartnerE2eData, watchRuntimeErrors } from './helpers'

async function openPartnerSearch(page, email = 'binh.tran@example.com') {
  await loginByApi(page, email)
  await page.goto('/partners')
  await expect(page.getByRole('heading', { name: 'Tìm người vừa tầm, đúng nơi, đúng lúc.' })).toBeVisible()
}

async function searchFromUi(page) {
  const responsePromise = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && response.url().includes('/api/v1/partners/search?')
  ))
  await page.getByRole('button', { name: 'Tìm đối tác phù hợp' }).click()
  const response = await responsePromise
  expect(response.ok(), await response.text()).toBeTruthy()
  const payload = await response.json()
  await expect(page.getByText(`${payload.totalElements} người vượt qua ba bộ lọc`)).toBeVisible()
  return { payload, url: new URL(response.url()) }
}

function timeToMinutes(value) {
  const [hour, minute] = value.slice(0, 5).split(':').map(Number)
  return hour * 60 + minute
}

function expectedSkillSimilarity(ratingGap) {
  const expectedWin = 1 / (1 + (10 ** (ratingGap / 400)))
  return Number((100 * (1 - Math.abs((2 * expectedWin) - 1))).toFixed(2))
}

test.describe('partner search', () => {
  test('2.2.20 searches partners from the UI through the real backend', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    await expect(page.getByLabel('Khu vực chơi')).toContainText('Gan nha')
    const { payload } = await searchFromUi(page)
    expect(payload.content.length).toBeGreaterThan(0)
    await expect(page.getByText(payload.content[0].fullName).first()).toBeVisible()
    await expect(page.getByText('Tương đồng').first()).toBeVisible()
    await expect(page.getByText(/Cách khoảng/).first()).toBeVisible()
    await expect(page.getByText(/Rảnh/).first()).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('2.2.21 sends the pinned location and only renders candidates inside its radius', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    const location = page.getByLabel('Khu vực chơi')
    const selectedLocationId = await location.inputValue()
    const selectedLabel = await location.locator('option:checked').textContent()
    const radiusKm = Number(selectedLabel.match(/bán kính ([\d.]+) km/)?.[1])
    const { payload, url } = await searchFromUi(page)

    expect(url.searchParams.get('preferredLocationId')).toBe(selectedLocationId)
    expect(radiusKm).toBeGreaterThan(0)
    expect(payload.content.length).toBeGreaterThan(0)
    for (const candidate of payload.content) {
      expect(Number(candidate.distanceKm)).toBeLessThanOrEqual(radiusKm)
      await expect(page.getByText(`Cách khoảng ${Number(candidate.distanceKm).toFixed(1)} km`)).toBeVisible()
    }
    assertNoRuntimeErrors()
  })

  test('2.2.22 only renders candidates available for the complete requested interval', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    await page.getByRole('button', { name: '90 phút' }).click()
    const { payload, url } = await searchFromUi(page)
    const scheduledAt = url.searchParams.get('scheduledAt')
    const startTime = scheduledAt.match(/T(\d{2}:\d{2})/)?.[1]
    const duration = Number(url.searchParams.get('durationMinutes'))
    const requestedStart = timeToMinutes(startTime)
    const requestedEnd = requestedStart + duration

    expect(payload.content.length).toBeGreaterThan(0)
    for (const candidate of payload.content) {
      expect(timeToMinutes(candidate.availableFrom)).toBeLessThanOrEqual(requestedStart)
      expect(timeToMinutes(candidate.availableUntil)).toBeGreaterThanOrEqual(requestedEnd)
      await expect(page.getByText(`Rảnh ${candidate.availableFrom.slice(0, 5)}–${candidate.availableUntil.slice(0, 5)}`)).toBeVisible()
    }
    assertNoRuntimeErrors()
  })

  test('2.2.23 applies the selected measured-Elo difference', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    await page.getByRole('button', { name: '± 200' }).click()
    const { payload, url } = await searchFromUi(page)
    const maximumGap = Number(url.searchParams.get('maxRatingDifference'))

    expect(maximumGap).toBe(200)
    expect(payload.content.length).toBeGreaterThan(0)
    for (const candidate of payload.content) {
      expect(Number(candidate.ratingGap)).toBeLessThanOrEqual(maximumGap)
      await expect(page.getByText(Number(candidate.ratingGap).toFixed(0), { exact: true }).first()).toBeVisible()
    }
    assertNoRuntimeErrors()
  })

  test('2.2.24 calculates and displays Elo skill similarity consistently', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    const { payload } = await searchFromUi(page)
    expect(payload.content.length).toBeGreaterThan(0)
    for (const candidate of payload.content) {
      const similarity = Number(candidate.skillSimilarity)
      expect(similarity).toBe(expectedSkillSimilarity(Number(candidate.ratingGap)))
      expect(similarity).toBeGreaterThanOrEqual(0)
      expect(similarity).toBeLessThanOrEqual(100)
      await expect(page.getByText(`${similarity}%`, { exact: true }).first()).toBeVisible()
    }
    assertNoRuntimeErrors()
  })

  test('2.2.25 returns and displays backend location compatibility', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    const selectedLabel = await page.getByLabel('Khu vực chơi').locator('option:checked').textContent()
    const radiusKm = Number(selectedLabel.match(/bán kính ([\d.]+) km/)?.[1])
    const { payload } = await searchFromUi(page)
    const candidate = payload.content[0]
    const expected = Number(Math.max(0, 100 * (1 - (Number(candidate.distanceKm) / radiusKm))).toFixed(2))

    // distanceKm duoc API lam tron 2 chu so, con diem duoc tinh tu khoang cach goc.
    expect(Math.abs(Number(candidate.locationCompatibility) - expected)).toBeLessThanOrEqual(0.1)
    const card = page.locator('article').first()
    await expect(card.getByText('Vị trí', { exact: true })).toBeVisible()
    await expect(card.getByText(`${Number(candidate.locationCompatibility).toFixed(0)}%`, { exact: true }).first()).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('2.2.26 returns and displays backend time compatibility', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    const { payload } = await searchFromUi(page)
    const candidate = payload.content[0]
    const score = Number(candidate.timeCompatibility)

    expect(score).toBeGreaterThanOrEqual(0)
    expect(score).toBeLessThanOrEqual(100)
    const card = page.locator('article').first()
    await expect(card.getByText('Thời gian', { exact: true })).toBeVisible()
    await expect(card.getByText(`${score.toFixed(0)}%`, { exact: true }).first()).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('2.2.27 returns and displays backend playing-style compatibility', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    const { payload } = await searchFromUi(page)
    const candidate = payload.content[0]
    const score = Number(candidate.styleCompatibility)

    expect([50, 65, 80, 100]).toContain(score)
    const card = page.locator('article').first()
    await expect(card.getByText('Phong cách', { exact: true })).toBeVisible()
    await expect(card.getByText(`${score.toFixed(0)}%`, { exact: true }).first()).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('2.2.31 sends a partner request from the search result', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    resetPartnerE2eData()
    await openPartnerSearch(page, 'binh.tran@example.com')

    await page.getByRole('button', { name: '± 300' }).click()
    await searchFromUi(page)
    const firstCard = page.locator('article').first()
    const candidateName = await firstCard.getByRole('heading').textContent()
    await firstCard.getByRole('button', { name: 'Gửi yêu cầu ghép cặp' }).click()

    const dialog = page.getByRole('dialog', { name: `Mời ${candidateName} cùng chơi` })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('textbox', { name: 'Lời nhắn' }).fill('E2E: Tối thứ Năm mình đánh đôi, bạn tham gia nhé?')
    const responsePromise = page.waitForResponse((response) => (
      response.request().method() === 'POST'
        && response.url().endsWith('/api/v1/partner-requests')
    ))
    await dialog.getByRole('button', { name: 'Gửi lời mời' }).click()
    const response = await responsePromise
    expect(response.status(), await response.text()).toBe(201)
    const payload = await response.json()
    expect(payload.status).toBe('pending')
    expect(payload.message).toBe('E2E: Tối thứ Năm mình đánh đôi, bạn tham gia nhé?')

    await expect(dialog.getByText('Đã gửi lời mời')).toBeVisible()
    await dialog.getByRole('button', { name: 'Hoàn tất' }).click()
    await expect(firstCard.getByRole('button', { name: 'Đã gửi lời mời' })).toBeDisabled()
    assertNoRuntimeErrors()
  })

  test('rejects a search time that is not in the future', async ({ page }) => {
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)

    const today = new Date()
    const localToday = [
      today.getFullYear(),
      String(today.getMonth() + 1).padStart(2, '0'),
      String(today.getDate()).padStart(2, '0'),
    ].join('-')
    await page.getByLabel('Ngày chơi').fill(localToday)
    await page.getByLabel('Giờ bắt đầu').fill('00:00')
    await page.getByRole('button', { name: 'Tìm đối tác phù hợp' }).click()
    await expect(page.getByText('Thời gian chơi phải ở tương lai.')).toBeVisible()
    assertNoRuntimeErrors()
  })

  test('renders the partner flow without horizontal overflow on mobile Chrome', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    const assertNoRuntimeErrors = watchRuntimeErrors(page)
    await openPartnerSearch(page)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    assertNoRuntimeErrors()
    await context.close()
  })
})

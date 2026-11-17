import { expect, test } from '@playwright/test'
import { createE2eUser, deleteE2eUsers, loginByApi, runSql, shot, watchRuntimeErrors } from './helpers'

const API = 'http://127.0.0.1:8080'
const email = (tag) => `e2e-${tag}@example.com`

const PLAYERS = [
  { tag: 'dt-me', name: 'E2E Minh Chủ', gender: 'male', elo: 1300, matches: 30 },
  { tag: 'dt-b', name: 'E2E Bình', gender: 'male', elo: 1200, matches: 30 },
  { tag: 'dt-c', name: 'E2E Cường', gender: 'male', elo: 1100, matches: 30 },
  { tag: 'dt-d', name: 'E2E Dũng', gender: 'male', elo: 1400, matches: 2 },
  { tag: 'dt-e', name: 'E2E Em Nam', gender: 'male', elo: 1000, matches: 30 },
  { tag: 'dt-f', name: 'E2E Phương', gender: 'female', elo: 1150, matches: 30 },
  { tag: 'dt-g', name: 'E2E Giang', gender: 'female', elo: 1050, matches: 30 },
]

test.describe.serial('2.3.63 - 2.3.69 double team suggestion', () => {
  test.beforeAll(() => {
    deleteE2eUsers()
    for (const p of PLAYERS) {
      createE2eUser({ tag: p.tag, name: p.name })
      runSql(`
        INSERT INTO player_profiles (user_id, gender, playing_style)
        SELECT id, '${p.gender}', 'balanced' FROM users WHERE email = '${email(p.tag)}'
        ON CONFLICT (user_id) DO UPDATE SET gender = EXCLUDED.gender, playing_style = 'balanced';
        INSERT INTO player_statistics (user_id, rating_score, total_matches)
        SELECT id, ${p.elo}, ${p.matches} FROM users WHERE email = '${email(p.tag)}'
        ON CONFLICT (user_id) DO UPDATE SET rating_score = EXCLUDED.rating_score, total_matches = EXCLUDED.total_matches;`)
    }
    runSql(`
      INSERT INTO player_connections (user_id, partner_id, status)
      SELECT me.id, other.id, 'active' FROM users me, users other
      WHERE me.email = '${email('dt-me')}' AND other.email LIKE 'e2e-dt-%@example.com' AND other.id <> me.id`)
  })
  test.afterAll(() => deleteE2eUsers())

  const idOf = (tag) => runSql(`SELECT id FROM users WHERE email = '${email(tag)}'`)
  const card = (page, tag) => page.getByTestId(`dt-player-${idOf(tag)}`)

  test('opens from the partners list, rules per type disable unsuitable players and cap the selection at three', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('dt-me'))
    await page.goto('/partners/connections')
    await page.getByTestId('open-double-teams').click()
    await expect(page).toHaveURL('/partners/double-teams')

    await expect(card(page, 'dt-b')).toBeEnabled()
    await expect(card(page, 'dt-f')).toBeDisabled()
    await expect(card(page, 'dt-f')).toContainText('không phù hợp loại đôi này')
    await expect(page.getByTestId('dt-submit')).toBeDisabled()

    await card(page, 'dt-b').click()
    await card(page, 'dt-c').click()
    await expect(page.getByTestId('dt-counter')).toContainText('2/3')
    await expect(page.getByTestId('dt-submit')).toBeDisabled()
    await card(page, 'dt-d').click()
    await expect(page.getByTestId('dt-counter')).toContainText('3/3')
    await expect(card(page, 'dt-e')).toBeDisabled()
    await expect(page.getByTestId('dt-submit')).toBeEnabled()
    await shot(page, 'dt-select')

    // Bỏ một người thì chọn lại được người khác.
    await card(page, 'dt-d').click()
    await expect(card(page, 'dt-e')).toBeEnabled()

    // Đổi loại đôi: nam -> nữ bỏ hết nam đã chọn, nữ được mở khóa.
    await page.getByTestId('dt-type-womens').click()
    await expect(page.getByTestId('dt-counter')).toContainText('0/3')
    await expect(card(page, 'dt-f')).toBeEnabled()
    await expect(card(page, 'dt-b')).toBeDisabled()
    assertNoErrors()
  })

  test('mixed doubles with the wrong gender mix shows the backend reason in Vietnamese and nothing is saved', async ({ page }) => {
    await loginByApi(page, email('dt-me'))
    await page.goto('/partners/double-teams')
    await page.getByTestId('dt-type-mixed').click()
    await card(page, 'dt-b').click()
    await card(page, 'dt-c').click()
    await card(page, 'dt-f').click()
    await page.getByTestId('dt-submit').click()
    await expect(page.getByTestId('dt-problems')).toContainText('Đôi nam nữ cần đúng 2 nam và 2 nữ')
    await shot(page, 'dt-mixed-error')
    expect(runSql(`SELECT count(*) FROM double_team_suggestions WHERE requested_by = '${idOf('dt-me')}'`)).toBe('0')
  })

  test('men doubles: the result shows three ordered options, one recommendation, strengths with a provisional badge and six pairs', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, email('dt-me'))
    await page.goto('/partners/double-teams')
    await card(page, 'dt-b').click()
    await card(page, 'dt-c').click()
    await card(page, 'dt-d').click()
    await page.getByTestId('dt-submit').click()
    await expect(page).toHaveURL(/\/partners\/double-teams\/[0-9a-f-]{36}$/)

    await expect(page.getByTestId('dt-options').locator('[data-testid^="dt-option-"]')).toHaveCount(3)
    await expect(page.getByTestId('dt-recommended')).toHaveCount(1)
    await expect(page.getByTestId('dt-option-1')).toContainText('Đề xuất tối ưu')
    await expect(page.getByTestId('dt-option-1').getByTestId('dt-balance-label')).toContainText('lệch')
    await expect(page.getByTestId('dt-source')).toContainText('baseline')

    // Mức lệch tăng dần theo thứ tự phương án.
    const gaps = []
    for (const rank of [1, 2, 3]) {
      const text = await page.getByTestId(`dt-option-${rank}`).getByTestId('dt-balance-label').innerText()
      gaps.push(Number(text.match(/lệch ([\d.]+) điểm/)[1]))
    }
    expect(gaps).toEqual([...gaps].sort((a, b) => a - b))

    // Dũng chỉ có 2 trận: Elo 1400 bị kéo về 1160, hiện "Tạm tính".
    const dung = page.getByTestId(`dt-strength-${idOf('dt-d')}`)
    await expect(dung).toContainText('Tạm tính')
    await expect(dung).toContainText('Elo 1400')
    await expect(dung).toContainText('1160')
    await expect(page.getByTestId(`dt-strength-${idOf('dt-b')}`)).not.toContainText('Tạm tính')
    await expect(page.getByTestId('dt-strengths').locator('li')).toHaveCount(4)
    await expect(page.getByTestId('dt-pairs').locator('li')).toHaveCount(6)
    await shot(page, 'dt-result')

    expect(runSql(`SELECT count(*) FROM double_team_suggestions WHERE requested_by = '${idOf('dt-me')}' AND match_type = 'mens'`)).toBe('3')
    assertNoErrors()
  })

  test('the saved suggestion can be reopened after a reload; another player cannot open it', async ({ page }) => {
    const batchId = runSql(`SELECT batch_id FROM double_team_suggestions WHERE requested_by = '${idOf('dt-me')}' LIMIT 1`)
    await loginByApi(page, email('dt-me'))
    await page.goto(`/partners/double-teams/${batchId}`)
    await expect(page.getByTestId('dt-options').locator('[data-testid^="dt-option-"]')).toHaveCount(3)
    await page.reload()
    await expect(page.getByTestId('dt-option-1')).toContainText('Đề xuất tối ưu')

    await loginByApi(page, email('dt-b'))
    await page.goto(`/partners/double-teams/${batchId}`)
    await expect(page.getByText('Không tìm thấy đề xuất chia đội này.')).toBeVisible()
  })

  test('mixed doubles with two men and two women gives two options with one man and one woman on each team', async ({ page }) => {
    await loginByApi(page, email('dt-me'))
    await page.goto('/partners/double-teams')
    await page.getByTestId('dt-type-mixed').click()
    await card(page, 'dt-b').click()
    await card(page, 'dt-f').click()
    await card(page, 'dt-g').click()
    await page.getByTestId('dt-submit').click()
    await expect(page.getByTestId('dt-options').locator('[data-testid^="dt-option-"]')).toHaveCount(2)
    for (const rank of [1, 2]) {
      const option = page.getByTestId(`dt-option-${rank}`)
      const text = await option.innerText()
      const men = ['E2E Minh Chủ', 'E2E Bình'].filter((n) => text.includes(n))
      expect(men).toHaveLength(2)
      // Hai nam không được ở cùng một đội: tên hai nam không nằm trong cùng dòng "A & B".
      expect(text).not.toContain('E2E Minh Chủ & E2E Bình')
      expect(text).not.toContain('E2E Bình & E2E Minh Chủ')
      expect(text).not.toContain('E2E Phương & E2E Giang')
      expect(text).not.toContain('E2E Giang & E2E Phương')
    }
  })

  test('the pages fit a phone screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, email('dt-me'))
    await page.goto('/partners/double-teams')
    await expect(page.getByTestId('dt-players')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, 'dt-select-phone')
    const batchId = runSql(`SELECT batch_id FROM double_team_suggestions WHERE requested_by = '${idOf('dt-me')}' LIMIT 1`)
    await page.goto(`/partners/double-teams/${batchId}`)
    await expect(page.getByTestId('dt-option-1')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, 'dt-result-phone')
  })
})

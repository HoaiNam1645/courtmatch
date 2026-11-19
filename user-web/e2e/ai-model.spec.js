import fs from 'node:fs'
import path from 'node:path'
import { expect, test } from '@playwright/test'
import { loginByApi, runSql, SEED_ADMIN_EMAIL, shot, watchRuntimeErrors } from './helpers'

const ARTIFACTS = path.resolve(import.meta.dirname, '../../ai-service/artifacts')
const dirs = fs.existsSync(ARTIFACTS) ? fs.readdirSync(ARTIFACTS) : []
const PARTNER = dirs.filter((name) => name.startsWith('partner-catboost-')).sort().at(-1)
const TEAM = dirs.filter((name) => name.startsWith('team-catboost-')).sort().at(-1)
const COPY = 'partner-catboost-e2ecopy'
const versions = [PARTNER, COPY, TEAM].filter(Boolean)
let previousActive = null

function purge() {
  if (versions.length === 0) return
  const list = versions.map((v) => `'${v}'`).join(',')
  const ids = `SELECT id FROM ai_models WHERE model_version IN (${list})`
  runSql(`DELETE FROM partner_suggestions WHERE model_id IN (${ids})`)
  runSql(`DELETE FROM double_team_suggestions WHERE model_id IN (${ids})`)
  runSql(`DELETE FROM matching_runs WHERE model_id IN (${ids})`)
  runSql(`DELETE FROM audit_logs WHERE action LIKE 'ai_model.%' AND entity_id IN (${ids})`)
  runSql(`DELETE FROM ai_models WHERE model_version IN (${list})`)
}

test.describe.serial('2.2.16, 2.2.19, 2.2.28-2.2.30 AI model with the real AI service', () => {
  test.skip(!PARTNER, 'Chua huan luyen model: chay "uv run python -m train partner" trong ai-service/ truoc.')

  test.beforeAll(() => {
    purge()
    previousActive = runSql(`SELECT id FROM ai_models WHERE model_type = 'catboost_ranker' AND status = 'active'`) || null
    if (previousActive) runSql(`UPDATE ai_models SET status = 'archived' WHERE id = '${previousActive}'`)
    fs.rmSync(path.join(ARTIFACTS, COPY), { recursive: true, force: true })
    fs.cpSync(path.join(ARTIFACTS, PARTNER), path.join(ARTIFACTS, COPY), { recursive: true })
  })
  test.afterAll(() => {
    purge()
    fs.rmSync(path.join(ARTIFACTS, COPY), { recursive: true, force: true })
    if (previousActive) runSql(`UPDATE ai_models SET status = 'active' WHERE id = '${previousActive}'`)
  })

  test('admin imports the trained model, the AI service loads it on activation and the previous version rolls back', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/matching?tab=models')
    await expect(page.getByTestId('aim')).toBeVisible()
    await expect(page.getByTestId('aim-service')).toContainText('AI service đang kết nối')
    await expect(page.getByTestId('aim-current-partner')).toHaveAttribute('data-source', 'baseline')

    await page.getByTestId(`aim-importable-${PARTNER}`).getByTestId('aim-import').click()
    await page.getByTestId('aim-dialog-confirm').click()
    await expect(page.getByTestId('aim-notice')).toContainText('Đã nhập')
    const row = page.getByTestId(`aim-${PARTNER}`)
    await expect(row).toHaveAttribute('data-status', 'ready')
    await expect(row.getByTestId('aim-metric')).toContainText('NDCG@10')
    await expect(row.getByTestId('aim-gate')).toContainText('Đạt kiểm thử')
    await shot(page, '2216-ai-model-imported')

    await row.getByTestId('aim-activate').click()
    await page.getByTestId('aim-dialog-confirm').click()
    await expect(page.getByTestId('aim-notice')).toContainText('Đã kích hoạt')
    await expect(row).toHaveAttribute('data-status', 'active')
    await expect(page.getByTestId('aim-current-partner')).toHaveAttribute('data-source', 'ai')
    const health = await (await page.request.get('http://127.0.0.1:8001/health')).json()
    expect(health.models.partner.modelVersion).toBe(PARTNER)

    // Phien ban thu hai (ban sao): kich hoat thi phien ban dau thanh luu tru, quay lai thi doi nguoc lai.
    await page.getByTestId(`aim-importable-${COPY}`).getByTestId('aim-import').click()
    await page.getByTestId('aim-dialog-confirm').click()
    await page.getByTestId(`aim-${COPY}`).getByTestId('aim-activate').click()
    await page.getByTestId('aim-dialog-confirm').click()
    await expect(page.getByTestId(`aim-${COPY}`)).toHaveAttribute('data-status', 'active')
    await expect(row).toHaveAttribute('data-status', 'archived')
    await row.getByTestId('aim-rollback').click()
    await page.getByTestId('aim-dialog-confirm').click()
    await expect(row).toHaveAttribute('data-status', 'active')
    await expect(page.getByTestId(`aim-${COPY}`)).toHaveAttribute('data-status', 'archived')
    expect(runSql(`SELECT count(*) FROM ai_models WHERE model_type = 'catboost_ranker' AND status = 'active'`)).toBe('1')
    await shot(page, '2219-ai-model-rolled-back')
    assertNoErrors()
  })

  test('a model that did not beat the baseline cannot be activated and says why', async ({ page }) => {
    test.skip(!TEAM, 'Chua huan luyen model chia doi')
    const gate = JSON.parse(fs.readFileSync(path.join(ARTIFACTS, TEAM, 'metadata.json'), 'utf8')).gate
    test.skip(gate.passes, 'Model chia doi hien tai dat kiem thu')
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/matching?tab=models')
    await page.getByTestId(`aim-importable-${TEAM}`).getByTestId('aim-import').click()
    await page.getByTestId('aim-dialog-confirm').click()
    const row = page.getByTestId(`aim-${TEAM}`)
    await expect(row.getByTestId('aim-gate')).toContainText('Chưa đạt kiểm thử')
    await expect(row.getByTestId('aim-activate')).toHaveCount(0)
    await expect(page.getByTestId('aim-current-team')).toHaveAttribute('data-source', 'baseline')
  })

  test('player search ranks with the AI model, labels the source and explains in plain words; without a model it says formula', async ({ page }) => {
    const assertNoErrors = watchRuntimeErrors(page)
    await loginByApi(page, 'binh.tran@example.com')
    await page.goto('/partners')
    const responsePromise = page.waitForResponse((r) => r.request().method() === 'GET' && r.url().includes('/api/v1/partners/search?'))
    await page.getByRole('button', { name: 'Tìm đối tác phù hợp' }).click()
    const payload = await (await responsePromise).json()
    expect(payload.content.length).toBeGreaterThan(0)
    const scores = payload.content.map((item) => Number(item.totalScore))
    expect([...scores].sort((a, b) => b - a)).toEqual(scores)
    expect(payload.content.every((item) => item.scoringSource === 'ai')).toBe(true)
    await expect(page.getByTestId('partner-source').first()).toContainText('Gợi ý bằng AI')
    await expect(page.getByTestId('partner-score').first()).toBeVisible()
    await expect(page.getByTestId('partner-reasons').first()).toBeVisible()
    await expect(page.getByTestId('partner-confidence').first()).toContainText('Độ tin cậy')
    await expect(page.getByText(/modelVersion|catboost|SHAP/i)).toHaveCount(0)
    await shot(page, '2230-ai-partner-results')

    expect(runSql(`SELECT scoring_source || '|' || (model_id IS NOT NULL)::text FROM partner_suggestions ORDER BY created_at DESC LIMIT 1`)).toBe('ai|true')

    runSql(`UPDATE ai_models SET status = 'archived' WHERE model_type = 'catboost_ranker' AND status = 'active'`)
    const second = page.waitForResponse((r) => r.request().method() === 'GET' && r.url().includes('/api/v1/partners/search?'))
    await page.getByRole('button', { name: 'Tìm đối tác phù hợp' }).click()
    expect((await (await second).json()).content.every((item) => item.scoringSource === 'baseline')).toBe(true)
    await expect(page.getByTestId('partner-source').first()).toContainText('Gợi ý theo công thức')
    assertNoErrors()
  })

  test('phone: the model panel and the partner card with the AI source fit the screen', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await loginByApi(page, SEED_ADMIN_EMAIL)
    await page.goto('/admin/matching?tab=models')
    await expect(page.getByTestId('aim')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await shot(page, '2216-ai-model-phone')
    await loginByApi(page, 'binh.tran@example.com')
    await page.goto('/partners')
    await page.getByRole('button', { name: 'Tìm đối tác phù hợp' }).click()
    await expect(page.getByTestId('partner-source').first()).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
})

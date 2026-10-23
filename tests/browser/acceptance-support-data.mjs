import assert from 'node:assert/strict'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const seed = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/acceptance-workspace-seed.json', import.meta.url)))
const target = new URL('../../.implementation-work/runtime/acceptance-support-data.json', import.meta.url)
const ledger = existsSync(target) ? JSON.parse(readFileSync(target)) : { data_kind: 'SIMULATION', scope: 'LOCAL_ACCEPTANCE_WORKSPACE', checks: [] }
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
try {
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/login', { headers: { Origin: origin, [csrf.header_name]: csrf.token }, form: owner, maxRedirects: 0 })).status(), 302)
  const page = await context.newPage()
  await page.exposeFunction('retainAcceptanceRequest', pending => {
    ledger.retention_pending = pending
    writeFileSync(target, JSON.stringify(ledger, null, 2), { mode: 0o600 })
  })
  await page.goto(origin + '/index', { waitUntil: 'networkidle', timeout: 90000 })
  const result = await page.evaluate(async ({ project, original }) => {
    const state = async () => (await (await fetch('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()).data
    const folders = await import('/src/api/medical/source-governance.ts')
    const s = await state(), signal = AbortSignal.timeout(180000)
    const before = await folders.readSourceFolders(s, null, signal)
    const name = 'SIMULATION 企业验收资料'
    const folder = before.items.find(value => value.name === name) ?? (await folders.createSourceFolder(s, { schema_version: '1.0.0', ownership: 'ENTERPRISE', owner_project_id: null, parent_id: null, category: 'LOCAL_ACCEPTANCE', name })).folder
    const after = await folders.readSourceFolders(await state(), null, signal)
    if (!after.items.some(value => value.folder_id === folder.folder_id)) throw new Error('验收文件夹未能重新读取')
    const retention = await import('/src/api/medical/runtime-retention.ts')
    const current = await state()
    if (!await retention.retentionAvailability(current, signal)) throw new Error('当前保存策略服务未启用')
    let pending = original
    if (!pending) {
      pending = await retention.prepareRetention(current, 'APPROVE', {
        schema_version: '1.0.0', project_id: project, classification: 'INTERNAL', effective_from: '2099-01-01T00:00:00Z', effective_until: '2100-01-01T00:00:00Z',
        terms: ['RUN', 'STREAM'].map(category => ({ category, start_event: category + '_CREATED', retention_duration_seconds: '31536000', backup_expiry_seconds: '31536000', hold_override: false, purge_method: 'VERIFIED_DELETE' })),
        reason: 'SIMULATION 本地验收项目的未来保存策略，仅验证登记与读取。', replaces: null
      })
      await window.retainAcceptanceRequest(pending)
    }
    const recovered = await retention.recoverRetention(current, pending, signal)
    const receipt = recovered.status === 'FOUND' ? recovered.receipt : await retention.sendRetention(current, pending, signal)
    const detail = await retention.retentionDetail(await state(), receipt.policy_revision, signal)
    if (detail.lifecycle !== 'SCHEDULED' || detail.fixed.request.project_id !== project) throw new Error('验收保存策略范围或时间状态不一致')
    return { folder_id: folder.folder_id, retention_policy: detail.fixed.policy_revision, retention_lifecycle: detail.lifecycle, checks: ['SOURCE_FOLDER_CREATE_AND_READ', 'PROJECT_FUTURE_RETENTION_SAVE_AND_READ'] }
  }, { project: seed.project_id, original: ledger.retention_pending ?? null })
  Object.assign(ledger, result, { verified_at: new Date().toISOString() })
  writeFileSync(target, JSON.stringify(ledger, null, 2), { mode: 0o600 })
  console.log(JSON.stringify({ data_kind: ledger.data_kind, checks: ledger.checks, retention_lifecycle: ledger.retention_lifecycle }))
} finally { await context.close(); await browser.close() }

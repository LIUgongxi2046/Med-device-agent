import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const root = new URL('../../', import.meta.url)
const owner = JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json', root)))
const assets = JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-template-assets.json', root)))
assert.equal(assets.state, 'REGISTERED')
const target = new URL('.implementation-work/runtime/acceptance-template-workflow.json', root)
const ledger = existsSync(target) ? JSON.parse(readFileSync(target)) : { data_kind: 'SIMULATION', tenant_id: assets.tenant_id, checks: [] }
function save(value = {}) { Object.assign(ledger, value); writeFileSync(target, JSON.stringify(ledger, null, 2), { mode: 0o600 }) }
const basis = kind => { const value = assets.assets.find(item => item.kind === kind); assert.equal(value.state, 'REGISTERED'); return { version_id: value.artifact_id, content_sha256: value.sha256 } }
if (!ledger.rule_input) {
  ledger.rule_input = { schema_version: '1.0.0', base_version: null, content_schema_ref: 'urn:medical:rule-expressions:1.0.0#/$defs/Content',
    content: { schema_version: '1.0.0', engine_revision: 'spring-spel-6.2.19-boolean-v1', expression: { schema_version: '1.0.0', engine_revision: 'spring-spel-6.2.19-boolean-v1', expression: "#attributes['section_number'] != null", input_keys: ['section_number'], constants: [] }, basis_manifest: [basis('DEPENDENCY'), basis('LICENSE')] },
    reason: 'SIMULATION 自拟目录编号检查：仅检查编号字段已填写。依据公开字体许可和本地编辑说明，不判断监管适用性。' }
  save()
}
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
const failures = []
let page
try {
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/login', { headers: { Origin: origin, [csrf.header_name]: csrf.token }, form: owner, maxRedirects: 0 })).status(), 302)
  page = await context.newPage()
  await page.addInitScript(() => window.addEventListener('medical-failure', event => console.error('TEMPLATE_ERROR', event.detail?.code, event.detail?.message)))
  page.on('console', message => { if (message.text().startsWith('TEMPLATE_ERROR')) { console.log(message.text()); failures.push(message.text()) } })
  page.on('requestfailed', request => console.log('REQUEST_FAILED', new URL(request.url()).pathname, request.failure()?.errorText))
  page.setDefaultTimeout(120000)
  page.on('pageerror', error => failures.push(error.message))
  page.on('response', response => { const path = new URL(response.url()).pathname; if (path.startsWith('/api/')) { console.log(response.status(), path); if (response.status() >= 400) failures.push({ path, status: response.status() }) } })
  await page.exposeFunction('retainTemplateAcceptance', save)
  await page.goto(origin + (ledger.rule_version ? '/rules/' + ledger.rule_version : '/templates'), { waitUntil: 'networkidle', timeout: 120000 })
  if (!ledger.rule_version) {
    assert.ok(!ledger.rule_pending, '原规则创建请求需要核查，禁止生成第二个请求')
    const created = await page.evaluate(async body => {
      const api = await import('/src/api/medical/rule-versions.ts'), sessions = await import('/src/api/medical/session.ts')
      const signal = AbortSignal.timeout(120000), session = await sessions.readSession(signal)
      const pending = await api.prepareRuleWrite(session, body)
      await window.retainTemplateAcceptance({ rule_pending: pending })
      const result = await api.submitRuleCreate(session, pending, body)
      await window.retainTemplateAcceptance({ rule_version: result.rule.rule_version, rule_sha256: result.rule.content_sha256, rule_operation: result.operation_id })
      const operation = await api.confirmRuleOperation(session, pending, result.operation_id, signal)
      api.clearRuleOperation(session, pending, operation, result.rule)
      return result
    }, ledger.rule_input)
    assert.equal(created.rule.status, 'DRAFT')
  }
  await page.evaluate(path => document.getElementById('app').__vue_app__.config.globalProperties.$router.push(path), '/rules/' + ledger.rule_version)
  await page.locator('[data-rule-preview]').waitFor()
  const rule = await page.evaluate(async id => {
    const api = await import('/src/api/medical/rule-versions.ts'), sessions = await import('/src/api/medical/session.ts'), signal = AbortSignal.timeout(120000)
    return api.readRule(await sessions.readSession(signal), id, signal)
  }, ledger.rule_version)
  if (rule.rule.status === 'DRAFT') {
    await page.getByRole('button', { name: '登记规则人工决定', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '确认规则人工决定', exact: true })
    await dialog.locator('.el-select__wrapper').click()
    await page.getByRole('option', { name: '核准', exact: true }).click()
    await dialog.getByLabel('决定理由', { exact: true }).fill('SIMULATION 已核查编号字段存在性表达式、实际公开许可及本地编辑说明，仅用于本地模板流程验收。')
    await dialog.getByText('已经核查规则表达式、输入属性、全部常量及获授权依据', { exact: true }).click()
    const response = page.waitForResponse(response => response.url().includes('/approval-decisions') && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: '确认登记规则决定', exact: true }).click()
    const received = await response
    assert.equal(received.status(), 200)
    const receipt = (await received.json()).data
    assert.equal(receipt.status, 'APPROVED')
    save({ rule_decision: receipt })
    await page.locator('[data-rule-completed]').waitFor()
  } else assert.equal(rule.rule.status, 'APPROVED')
  if (!ledger.template_input) {
    const always = { mode: 'ALWAYS', rule_revision: null, engine_revision: null, input_keys: [] }
    const evidence = { mode: 'NARRATIVE', authority_source_types: [], verification_rule_revisions: [], calculation_rule_revision: null, input_field_ids: [], require_original_locator: true, require_human_verification: true }
    const nodes = ['SIMULATION 资料目录', 'SIMULATION 产品描述', 'SIMULATION 核验记录'].map((title, index) => ({ node_id: randomUUID(), parent_id: null, official_number: 'SIMULATION.' + (index + 1), title, ordinal: index + 1, condition: always, required: true, content_mode: 'NARRATIVE', evidence_rule: evidence, basis_refs: [basis('DEPENDENCY')], nature: 'ENTERPRISE_SUPPLEMENT', content_requirements: ['保留资料固定版本及原始定位，未知信息保持待确认。'] }))
    const content = { schema_version: '1.0.0', document_type: 'OVERVIEW', application_path: 'FIRST_REGISTRATION', ivd: false, directory_profile: null,
      nodes, field_definitions: [{ field_id: randomUUID(), node_id: nodes[0].node_id, key: 'material_name', name: '资料名称', description: '填写待整理资料的实际名称', value_type: 'TEXT', constraints: { max_length: 200, precision: null, minimum: null, maximum: null, unit: null, range_basis: [basis('DEPENDENCY')], enum_options: [], nullable_reasons: [] }, condition: always, required: true, evidence_rule: evidence, output_mapping: { node_id: nodes[0].node_id, repeat_id: null, column_key: null, internal_only: false, ordinal: 1 }, repeat_id: null, attachment_requirement_id: null, basis_refs: [basis('DEPENDENCY')] }],
      repeat_definitions: [], attachment_requirements: [], cross_document_rules: [], condition_schema: { attribute_keys: [], applicability: always, unknown_result: 'UNKNOWN' }, basis_manifest: [basis('DEPENDENCY'), basis('LICENSE')],
      format_profile: { format_revision: basis('FORMAT'), output_types: ['DOCX'], numbering_rule_revision: { version_id: ledger.rule_version, content_sha256: ledger.rule_sha256 }, font_artifacts: [basis('FONT')], node_displays: [], attachment_order: 'DECLARED_ORDINAL' } }
    save({ template_input: { schema_version: '1.0.0', base_version: null, schema_ref: 'urn:medical:templates:1.0.0#/$defs/Content', content, reason: 'SIMULATION 公开素材验收模板：Noto Sans CJK SC及OFL许可使用实际原件，自拟A4版式及目录。仅验证本地人工审查流程。' } })
  }
  if (!ledger.template_version) {
    assert.ok(!ledger.template_pending || process.argv.includes('--resume-pending'), '原模板创建请求需要核查，禁止生成第二个请求')
    await page.evaluate(async ({ body, original }) => {
      const api = await import('/src/api/medical/template-create.ts'), sessions = await import('/src/api/medical/session.ts'), signal = AbortSignal.timeout(120000)
      const session = await sessions.readSession(signal)
      if (original) {
        const operations = await import('/src/api/medical/operations.ts'), protocol = await import('/src/api/medical/template-approval.ts')
        const found = await operations.lookupOperation(session, original.operation, original.key, signal)
        if (found.lookup_status !== 'NOT_FOUND') throw new Error('原模板请求已有操作状态，需要核查原回执')
        if (protocol.canonicalTemplateValue(original.context) !== protocol.canonicalTemplateValue(session.current_context)) throw new Error('原模板请求上下文已经变化，需要保留原请求核查')
        sessionStorage.setItem('medical.template.create:' + session.user.user_id + ':' + session.current_context.tenant_id, JSON.stringify(original))
      }
      const pending = await api.prepareTemplateCreate(session, body)
      await window.retainTemplateAcceptance({ template_pending: pending })
      const receipt = await api.submitTemplateCreate(session, pending, body)
      await window.retainTemplateAcceptance({ template_version: receipt.template.template_version, template_operation: receipt.operation_id })
      await api.confirmTemplateCreate(session, pending, receipt)
    }, { body: ledger.template_input, original: ledger.template_pending ?? null })
  }
  await page.evaluate(path => document.getElementById('app').__vue_app__.config.globalProperties.$router.push(path), '/templates/' + ledger.template_version + '/approval')
  await page.locator('[data-template-preview]').waitFor()
  await page.getByRole('button', { name: '登记人工决定', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '确认模板人工决定', exact: true })
  await dialog.locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '核准', exact: true }).click()
  await dialog.getByLabel('决定理由', { exact: true }).fill('SIMULATION 已核查目录、资料名称字段、实际DOCX版式、Noto字体、完整OFL许可、编号检查规则及固定摘要，仅用于本地流程验收。')
  await dialog.getByText('已经核查完整目录、字段、附件、规则、版式和获授权依据', { exact: true }).click()
  const response = page.waitForResponse(response => response.url().includes('/approval-decisions') && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: '确认登记决定', exact: true }).click()
  const received = await response
  assert.equal(received.status(), 200)
  const receipt = (await received.json()).data
  assert.equal(receipt.status, 'APPROVED')
  save({ template_decision: receipt })
  await page.getByText('原模板决定已完成并核查', { exact: true }).waitFor()
  assert.deepEqual(failures, [])
  save({ verified_at: new Date().toISOString(), checks: ['REAL_PUBLIC_ASSET_OBJECTS', 'RULE_CREATE_AND_ACTUAL_APPROVAL', 'TEMPLATE_CREATE_AND_ACTUAL_APPROVAL'], actual_browser_forms: true, regulatory_approval: false })
  console.log(JSON.stringify({ checks: ledger.checks, rule_version: ledger.rule_version, template_version: ledger.template_version, status: receipt.status }))
} finally {
  if (page) writeFileSync(new URL('.implementation-work/public-template-browser-state.json', root), JSON.stringify({ url: page.url(), text: await page.locator('body').innerText(), failures }, null, 2), { mode: 0o600 })
  await context.close(); await browser.close()
}

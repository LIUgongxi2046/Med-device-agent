import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { createServer } from '../web/node_modules/vite/dist/node/index.js'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : '../tests/browser/node_modules/playwright/index.mjs')
const root = fileURLToPath(new URL('../', import.meta.url)); const output = root + '.implementation-work/MODEL-CONFIGURATION-UI-SIMULATION'
await mkdir(output, { recursive: true }); process.chdir(root + 'web')
const server = await createServer({ configFile: root + 'web/vite.config.js', mode: 'medical', cacheDir: output + '/vite-cache',
  server: { host: '127.0.0.1', port: 18131, strictPort: true, open: false } })
let browser
try {
  await server.listen(); browser = await chromium.launch({ executablePath: process.env.CHROMIUM_EXECUTABLE ?? '/usr/bin/chromium', headless: true })
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } }); const origin = 'http://127.0.0.1:18131'
  const errors = [], external = []; const writes = []; let lookupCount = 0, listCount = 0, readCount = 0, checks = 0
  const actor = randomUUID(), tenant = randomUUID(), config = randomUUID(), credential = randomUUID(), price = randomUUID(), policy = randomUUID()
  const session = { schema_version: '1.0.0', user: { user_id: actor, username: null, display_name: 'SIMULATION', platform_role: null },
    current_context: { tenant_id: tenant, context_revision: '1', auth_epoch: '1', governance_epoch: '1' },
    memberships: [{ tenant_id: tenant, member_id: randomUUID(), status: 'ACTIVE', valid_from: '2020-01-01T00:00:00Z', valid_to: null, revision: '1' }],
    csrf: { header_name: 'X-CSRF-TOKEN', token: 'SIMULATION-CSRF' }, invitation_entry: { method: 'POST', path_template: '/api/member-invitations/{id}/acceptances' },
    action_summary: ['model.configure','configuration.read'].map(action => ({ action, project_id: null, resource_kind: 'ENTERPRISE', resource_id: tenant })) }
  let currentSession = structuredClone(session), fixedView = null, currentOperation = null, receipt = null, abortSave = false, allowConfigure = true, lookupStatus = 'FOUND'
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => { if (request.url().startsWith('http') && new URL(request.url()).origin !== origin) external.push(request.url()) })
  async function respond(route, data, revision = null, status = 200) {
    const requestId = randomUUID()
    await route.fulfill({ status, contentType: 'application/json', headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': requestId },
      body: JSON.stringify({ schema_version: '1.0.0', request_id: requestId, resource_revision: revision, data }) })
  }
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url())
    if (url.pathname === '/api/model-config-revisions' && request.method() === 'POST') {
      const body = request.postDataJSON(); writes.push({ body, headers: request.headers() })
      const revision = (BigInt(body.expected_revision) + 1n).toString(), fixed = randomUUID(), operationId = randomUUID()
      const { auth_ref: _a, credential_revision: _c, ...definition } = body.definition
      fixedView = { schema_version: '1.0.0', tenant_id: tenant, project_id: body.project_id, config_id: body.config_id ?? config, model_config_revision: fixed,
        based_on_revision: body.based_on_revision, definition, content_sha256: 'd'.repeat(64), status: 'DRAFT', current_model_revision: fixed, revision,
        created_by: actor, created_at: '2026-10-16T01:02:03Z', dependency_validation: 'REGISTRY_REFERENCE_ONLY', allowed_actions: ['configuration.read','model.configure'] }
      currentSession = { ...currentSession, current_context: { ...currentSession.current_context, context_revision: (BigInt(currentSession.current_context.context_revision) + 1n).toString(), governance_epoch: (BigInt(currentSession.current_context.governance_epoch) + 1n).toString() } }
      currentOperation = { operation_id: operationId, resource_id: fixed, status: 'COMPLETED', status_url: '/api/operations/' + operationId }
      receipt = { schema_version: '1.0.0', operation_id: operationId, tenant_id: tenant, project_id: body.project_id, config_id: fixedView.config_id,
        model_config_revision: fixed, content_sha256: fixedView.content_sha256, status: 'DRAFT', revision, current_context: currentSession.current_context }
      await page.evaluate(value => window.modelFixture.setNextSession(value), currentSession)
      if (abortSave) await route.abort('failed'); else await respond(route, receipt, revision, 201)
    } else if (url.pathname === '/api/model-config-revisions' && request.method() === 'GET') {
      listCount++; const items = fixedView && (!url.searchParams.get('q') || fixedView.definition.model_id.includes(url.searchParams.get('q'))) ? [{ ...fixedView, allowed_actions: allowConfigure ? ['configuration.read','model.configure'] : ['configuration.read'] }] : []
      await respond(route, { schema_version: '1.0.0', tenant_id: tenant, current_context: currentSession.current_context, items, next_after: null }, currentSession.current_context.context_revision)
    } else if (url.pathname.startsWith('/api/model-config-revisions/') && request.method() === 'GET') {
      readCount++; assert.equal(url.pathname, '/api/model-config-revisions/' + fixedView.model_config_revision)
      await respond(route, { ...fixedView, allowed_actions: allowConfigure ? ['configuration.read','model.configure'] : ['configuration.read'] }, fixedView.revision)
    } else if (url.pathname === '/api/operation-lookups') {
      lookupCount++; assert.equal(request.postDataJSON().operation_id, 'CP-08')
      await respond(route, { lookup_status: lookupStatus, operation: lookupStatus === 'FOUND' ? currentOperation : null })
    } else if (url.pathname.startsWith('/api/operations/')) await respond(route, currentOperation)
    else throw new Error('Unexpected simulation route: ' + request.method() + ' ' + url.pathname)
  })
  await page.goto(origin + '/tests/model-configurations/index.html'); await page.waitForFunction(() => window.modelFixture)
  await page.evaluate(value => window.modelFixture.setSession(value), currentSession)
  await page.getByText('当前授权与筛选范围没有模型配置').waitFor(); checks++
  async function fillForm(reason = 'SIMULATION 保存配置') {
    for (const [id,value] of Object.entries({ provider: 'SIMULATION supplier', base_url: 'https://simulation.invalid/v1', region: 'simulation-region', model_id: 'simulation-model', model_version: 'version-1',
      credential_reference_id: credential, auth_ref: 'vault:simulation/model', credential_revision: 'version-1', context_window: '128000', max_output_tokens: '8192', max_concurrency: '2', requests_per_minute: '10', tokens_per_minute: '200000',
      'price-version': price, 'price-hash': 'a'.repeat(64), currency: 'CNY', 'policy-version': policy, 'policy-hash': 'b'.repeat(64), classifications: 'INTERNAL', regions: 'simulation-region', retention: '0',
      'parameter-ref': 'urn:simulation:request:1', 'parameter-hash': 'c'.repeat(64), 'protocol-revision': '1.0.0', reason })) await page.locator('#model-' + id).fill(value)
    await page.getByRole('checkbox', { name: 'none', exact: true }).check()
    await page.getByRole('checkbox', { name: '我确认以上为实际配置和已有引用；本次仅保存草稿，不代表批准或启用。' }).check()
  }
  await page.getByRole('button', { name: '新建草稿', exact: true }).click(); await fillForm()
  await page.locator('#model-max_output_tokens').fill('128001'); await page.getByRole('button', { name: '保存DRAFT草稿', exact: true }).click()
  await page.getByText('输出上限不能超过上下文窗口').first().waitFor(); assert.equal(writes.length, 0); checks++
  await page.locator('#model-max_output_tokens').fill('8192')
  await page.getByRole('button', { name: '保存DRAFT草稿', exact: true }).evaluate(button => { button.click(); button.click() })
  await page.getByText(/已确认原操作完成，并回读固定草稿/).waitFor(); assert.equal(writes.length, 1); checks++
  assert.equal(writes[0].headers['x-context-revision'], '1'); assert.equal(writes[0].headers['x-csrf-token'], 'SIMULATION-CSRF'); assert.ok(writes[0].headers['idempotency-key']); checks++
  await page.locator('[data-model-list]').getByText('simulation-model', { exact: true }).waitFor(); checks++
  assert.equal(await page.locator('body').innerText().then(value => value.includes('vault:simulation/model')), false)
  assert.equal(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.model.configuration.write:')).length), 0); checks++
  await page.getByRole('button', { name: '基于此固定版本修订草稿' }).click()
  assert.equal(await page.locator('#model-auth_ref').inputValue(), ''); assert.equal(await page.locator('#model-credential_revision').inputValue(), '')
  assert.equal(await page.locator('#model-model_id').inputValue(), 'simulation-model'); checks++
  await page.locator('#model-auth_ref').fill('vault:must-clear/reference')
  await page.evaluate(() => window.modelFixture.navigate('/away'))
  await page.getByRole('dialog').waitFor(); await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  assert.equal(await page.locator('#model-auth_ref').inputValue(), 'vault:must-clear/reference'); checks++
  await page.getByRole('button', { name: '清除表单', exact: true }).click(); await page.getByRole('button', { name: '清除并继续', exact: true }).click()
  await page.locator('#model-auth_ref').waitFor({ state: 'detached' }); checks++
  allowConfigure = false
  await page.locator('#model-read-id').fill(fixedView.model_config_revision); await page.getByRole('button', { name: '读取草稿', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('[aria-label="固定草稿详情"]') !== null)
  await page.getByRole('button', { name: '基于此固定版本修订草稿' }).waitFor(); assert.equal(await page.getByRole('button', { name: '基于此固定版本修订草稿' }).isDisabled(), true); checks++
  allowConfigure = true
  await page.getByRole('button', { name: '新建草稿', exact: true }).click(); await fillForm('SIMULATION 不确定回执')
  abortSave = true; await page.getByRole('button', { name: '保存DRAFT草稿', exact: true }).click()
  await page.getByRole('button', { name: '查询原操作', exact: true }).waitFor(); await page.waitForFunction(() => !window.modelFixture.user.medicalWriting)
  assert.equal(writes.length, 2)
  let pendingData = await page.evaluate(() => JSON.parse(sessionStorage.getItem(Object.keys(sessionStorage).find(key => key.startsWith('medical.model.configuration.write:')))))
  assert.equal(pendingData.key, writes[1].headers['idempotency-key'])
  assert.equal(JSON.stringify(pendingData).includes('vault:'), false); assert.equal(JSON.stringify(pendingData).includes('simulation.invalid'), false); checks++
  await page.evaluate(value => window.modelFixture.setSession(value), currentSession)
  await page.locator('[data-model-list]').getByText('simulation-model', { exact: true }).waitFor()
  await page.getByRole('button', { name: '查询原操作', exact: true }).click(); await page.getByRole('button', { name: '回读原固定草稿并核对', exact: true }).click()
  await page.getByText(/已确认原操作完成，并回读固定草稿/).waitFor(); assert.equal(writes.length, 2); assert.equal(lookupCount, 1); checks++
  await page.getByRole('button', { name: '新建草稿', exact: true }).click(); await page.locator('#model-auth_ref').fill('vault:tenant-private/reference')
  await page.evaluate(value => window.modelFixture.setSession(value), { ...currentSession, current_context: { ...currentSession.current_context, tenant_id: randomUUID() }, action_summary: [] })
  assert.equal(await page.locator('#model-auth_ref').count(), 0); assert.equal(await page.locator('[aria-label="固定草稿详情"]').count(), 0)
  assert.equal(await page.locator('#model-read-id').inputValue(), ''); assert.equal(await page.getByRole('button', { name: '新建草稿', exact: true }).isDisabled(), true); checks++
  assert.equal(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.model.configuration.write:')).length), 0)
  assert.deepEqual(external, []); assert.deepEqual(errors, []); checks++
  const handled = await page.evaluate(() => window.modelFixture.errors)
  assert.equal(handled.length, 1); assert.equal(handled[0].code, 'DEPENDENCY_UNAVAILABLE'); checks++
  await page.screenshot({ path: output + '/cleared-tenant.png', fullPage: true })
  const result = { result: 'PASS', checks, data_kind: 'SIMULATION', actual_http_verified: false, writes: writes.length, lookups: lookupCount, lists: listCount, fixed_reads: readCount, external_requests: external.length }
  await writeFile(output + '/checks.json', JSON.stringify(result, null, 2)); console.log(JSON.stringify(result))
} finally { await browser?.close(); await server.close() }

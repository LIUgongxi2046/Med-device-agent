import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview, runnerImport } from '../../web/node_modules/vite/dist/node/index.js'
import { checkExactActions, checkExactRevocation } from './source-exact-actions.js'

const root = process.cwd()
globalThis.window = new EventTarget()
const { module: protocol } = await runnerImport(path.join(root, 'web/src/api/medical/protocol.ts'), { root: path.join(root, 'web'), mode: 'medical' })
const [outputValue, backendValue, tenant, sourceVersion, authorization, hash, mode, frontendValue, projectId] = process.argv.slice(2)
const output = path.resolve(outputValue); const backend = new URL(backendValue)
assert.equal(backend.hostname, '127.0.0.1'); assert(['development', 'production'].includes(mode))
const owner = JSON.parse(await readFile(path.join(root, '.implementation-work/runtime/owner-credentials.json'), 'utf8'))
const proxy = {}
for (const prefix of ['/api', '/auth', '/local-auth']) proxy[prefix] = { target: backend.origin, changeOrigin: true, autoRewrite: true,
  configure(value) { value.on('proxyReq', (forward, request) => {
    if (request.headers.origin !== undefined) {
      assert(['http://127.0.0.1:18083', 'http://127.0.0.1:18084'].includes(request.headers.origin))
      forward.setHeader('Origin', 'http://127.0.0.1:18080')
    }
  }) } }
process.chdir(path.join(root, 'web'))
const origin = mode === 'development' ? 'http://127.0.0.1:18083' : 'http://127.0.0.1:18084'
const configuration = { mode: 'medical', configFile: path.join(root, 'web/vite.config.js'),
  cacheDir: path.join(root, '.implementation-work/project-browser-vite-cache'), build: { outDir: path.resolve(frontendValue) },
  server: { host: '127.0.0.1', port: 18083, strictPort: true, open: false, proxy },
  preview: { host: '127.0.0.1', port: 18084, strictPort: true, proxy } }
const server = mode === 'development' ? await createServer(configuration) : await preview(configuration)
try {
  if (mode === 'development') await server.listen()
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  let inspectedPage = null; let inspectedErrors = []; const sourceResponses = []; const sessionResponses = []; const uploadResponses = []
  const requestFailures = []; const responseStatuses = []
  try {
    const page = await browser.newPage(); const errors = []; const attempts = []; const checks = []
    inspectedPage = page; inspectedErrors = errors
    page.setDefaultNavigationTimeout(120000); page.setDefaultTimeout(60000)
    page.on('pageerror', error => { errors.push(error.message) })
    page.on('requestfailed', request => { requestFailures.push({ pathname: new URL(request.url()).pathname,
      resource_type: request.resourceType(), error: request.failure()?.errorText ?? null }) })
    page.on('response', async response => {
      const pathname = new URL(response.url()).pathname
      responseStatuses.push({ pathname, resource_type: response.request().resourceType(), status: response.status() })
      if (pathname.startsWith('/api/source-versions/')) sourceResponses.push({ pathname, status: response.status() })
      if (pathname.startsWith('/api/uploads')) uploadResponses.push({ pathname, method: response.request().method(), status: response.status(),
        cache_control: response.headers()['cache-control'] ?? null, schema_version: response.headers()['x-schema-version'] ?? null })
      if (pathname === '/api/session' && response.status() === 200) {
        const value = await response.json()
        sessionResponses.push({ schema_issues: protocol.schemaIssues('urn:medical:identity', value.data),
          current_context: value.data.current_context, action_summary: value.data.action_summary })
      }
    })
    page.on('request', request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/rights-decisions')) {
        attempts.push({ key: request.headers()['idempotency-key'], authorization: request.headers().authorization ?? null, body: request.postDataJSON() })
      }
    })
    const rightsPath = '/library/versions/' + sourceVersion + '/rights'
    const pick = async (name, option) => {
      const input = page.getByRole('combobox', { name, exact: true })
      await page.waitForFunction(element => !element.disabled, await input.elementHandle())
      await input.focus(); await input.press('ArrowDown')
      await page.getByRole('option', { name: option, exact: true }).click()
    }
    const token = async (name, value) => {
      const input = page.getByRole('combobox', { name, exact: true })
      await input.fill(value); await page.getByRole('option', { name: value, exact: true }).click()
    }
    const select = label => pick('权利决定', label)
    const check = async (label, surface = page) => {
      const control = surface.getByRole('checkbox', { name: label, exact: true })
      if (!(await control.isChecked())) await surface.getByText(label, { exact: true }).click()
      assert.equal(await control.isChecked(), true)
    }
    const readPreview = async () => {
      await page.getByRole('button', { name: '读取固定权利审查', exact: true }).click()
      await page.getByRole('region', { name: '固定权利审查', exact: true }).waitFor()
    }
    const proposal = async () => {
      await select('批准完整权利范围')
      await page.getByRole('textbox', { name: '用途代码', exact: true }).fill('DOCUMENT_BODY')
      for (const label of ['读取', '保存', '企业使用']) await check(label)
      await token('允许区域', 'LOCAL')
      await page.getByRole('textbox', { name: '权利人', exact: true }).fill('SIMULATION 样例企业')
      await page.getByRole('textbox', { name: '完整许可说明', exact: true }).fill('SIMULATION 资料许可，仅用于本轮开发')
      await token('允许环境', 'LOCAL'); await check('内部')
      await page.getByRole('button', { name: '新增授权文件', exact: true }).click()
      await page.getByRole('textbox', { name: '授权文件版本 1', exact: true }).fill(authorization)
      await page.getByRole('textbox', { name: '授权文件摘要 1', exact: true }).fill(hash)
    }
    const complete = async (label, expected) => {
      await page.getByRole('textbox', { name: '决定理由', exact: true }).fill('SIMULATION ' + label + ' 浏览器核查')
      await check('已核查来源版本、完整许可范围及授权文件')
      const resultPromise = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/rights-decisions'))
      await page.getByRole('button', { name: '提交固定权利决定', exact: true }).click()
      const response = await resultPromise; assert.equal(response.status(), 200)
      const result = (await response.json()).data
      await page.getByRole('region', { name: '权利决定完成回执', exact: true }).waitFor()
      assert.equal(result.rights_status, expected)
      assert((await page.getByRole('region', { name: '权利决定完成回执', exact: true }).innerText()).includes(result.rights.version_id))
      return result
    }
    await page.goto(origin + '/access')
    await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor({ timeout: 120000 })
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
    await page.getByLabel('密码', { exact: true }).fill(owner.password)
    await page.getByRole('button', { name: '登录账户', exact: true }).click(); await page.waitForURL(origin + '/index')
    await page.goto(origin + rightsPath)
    await page.getByRole('heading', { name: '来源权利审查', exact: true }).waitFor()
    assert.equal(await page.getByRole('combobox', { name: '权利决定', exact: true }).isDisabled(), true)
    assert.equal(attempts.length, 0); checks.push('enterprise_identity_required_without_rights_writes')
    const tenantSelect = page.getByRole('region', { name: '当前账户和企业空间', exact: true }).getByRole('combobox')
    await tenantSelect.focus(); await tenantSelect.press('ArrowDown')
    await page.getByRole('option').filter({ hasText: tenant }).click()
    await page.getByRole('button', { name: '确认选择企业', exact: true }).click()
    await page.getByRole('combobox', { name: '权利决定', exact: true }).waitFor()
    const indexChecks = []
    await page.waitForFunction(element => !element.disabled, await page.getByRole('combobox', { name: '权利决定', exact: true }).elementHandle())
    await page.goto(origin + '/library/versions/' + sourceVersion)
    const indexRegion = page.getByRole('region', { name: '固定来源索引构建记录', exact: true })
    await indexRegion.waitFor()
    await page.getByRole('textbox', { name: '索引执行项目编号', exact: true }).fill(projectId)
    const indexPromise = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/index-builds'))
    await page.getByRole('button', { name: '读取完整索引记录', exact: true }).click()
    const indexResponse = await indexPromise; assert.equal(indexResponse.status(), 200)
    const indexValue = (await indexResponse.json()).data
    assert.equal(indexValue.source_version, sourceVersion); assert.equal(indexValue.project_id, projectId)
    assert.equal(indexValue.total, '0'); assert.deepEqual(indexValue.items, [])
    await page.getByText('当前来源与项目没有索引构建记录', { exact: true }).waitFor()
    indexChecks.push('actual_fixed_source_project_and_empty_complete_index_listing')
    await page.getByRole('textbox', { name: '索引执行项目编号', exact: true }).fill('invalid-project')
    assert.equal(await page.getByRole('button', { name: '读取完整索引记录', exact: true }).isDisabled(), true)
    assert.equal(await page.getByText('当前来源与项目没有索引构建记录', { exact: true }).count(), 0)
    indexChecks.push('changed_project_clears_original_result_and_invalid_identity_blocks_read')
    await page.reload(); await indexRegion.waitFor()
    assert.equal(await page.getByRole('textbox', { name: '索引执行项目编号', exact: true }).inputValue(), '')
    indexChecks.push('refresh_requires_explicit_new_index_project_selection')
    console.log(JSON.stringify({ mode, stage: 'INDEX_READ_VERIFIED', checks: indexChecks.length }))
    await page.goto(origin + rightsPath)
    await page.getByRole('heading', { name: '来源权利审查', exact: true }).waitFor()
    await select('批准完整权利范围')
    assert.equal(await page.getByRole('button', { name: '读取固定权利审查', exact: true }).isDisabled(), true)
    assert.equal(attempts.length, 0); checks.push('incomplete_license_and_authorization_prevent_submission')
    await select('退回待审权利'); await readPreview()
    assert.equal(await page.getByRole('button', { name: '提交固定权利决定', exact: true }).isDisabled(), true)
    await select('批准完整权利范围')
    assert.equal(await page.getByRole('region', { name: '固定权利审查', exact: true }).count(), 0)
    checks.push('changing_decision_invalidates_original_review_and_attestation')
    await select('退回待审权利'); await readPreview()
    const returned = await complete('退回待审权利', 'PENDING_REVIEW')
    assert.equal(returned.revision, '2'); checks.push('return_uses_actual_operation_and_current_rights_reopening')
    console.log(JSON.stringify({ mode, stage: 'RIGHTS_RETURN_VERIFIED' }))
    await proposal(); await readPreview()
    assert((await page.getByLabel('服务端授权文件清单', { exact: true }).innerText()).includes(authorization))
    await page.getByRole('textbox', { name: '决定理由', exact: true }).fill('SIMULATION 批准未知响应恢复')
    await check('已核查来源版本、完整许可范围及授权文件')
    const requestPromise = page.waitForRequest(request => request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/rights-decisions'))
    await page.getByRole('button', { name: '提交固定权利决定', exact: true }).click()
    const originalRequest = await requestPromise
    await page.goto(origin + '/library')
    await page.goto(origin + rightsPath)
    await page.waitForFunction(() => ['来源权利审查', '重新核查业务会话'].includes(document.querySelector('main h1')?.textContent ?? ''), undefined, { timeout: 120000 })
    if (new URL(page.url()).pathname === '/session-recovery') {
      await page.getByRole('alert').waitFor()
      await page.getByRole('button', { name: '重新读取会话并继续页面', exact: true }).click()
    }
    await page.getByRole('heading', { name: '来源权利审查', exact: true }).waitFor({ timeout: 120000 })
    await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
    await page.getByRole('region', { name: '原权利请求恢复', exact: true }).waitFor()
    const pending = await page.evaluate(() => Object.values(sessionStorage).map(value => JSON.parse(value)).find(value => value?.operation === 'KN-RIGHTS-01'))
    assert.equal(pending.key, originalRequest.headers()['idempotency-key'])
    const stored = await page.evaluate(() => JSON.stringify(Object.fromEntries(Object.keys(sessionStorage).map(key => [key, sessionStorage.getItem(key)]))))
    assert.equal(stored.includes('SIMULATION 批准未知响应恢复'), false); assert.equal(stored.includes('SIMULATION 资料许可'), false)
    await page.getByRole('button', { name: '查询原请求', exact: true }).click()
    await page.getByText(/原操作查询：(?:COMPLETED|NOT_FOUND)/).waitFor()
    const observed = await page.getByText(/原操作查询：(?:COMPLETED|NOT_FOUND)/).innerText()
    await page.getByRole('region', { name: '原权利请求恢复', exact: true }).locator('summary').click()
    await page.getByRole('textbox', { name: '原权利决定完整JSON', exact: true }).fill(originalRequest.postData())
    const recoveryPromise = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/rights-decisions'))
    await page.getByRole('button', { name: observed.includes('COMPLETED') ? '读取原决定回执' : '重新发送原请求', exact: true }).click()
    let recovery = await recoveryPromise
    if (recovery.status() === 409) {
      const conflict = await recovery.json(); assert.equal(conflict.code, 'CONTEXT_CHANGED')
      await page.getByRole('alert').filter({ hasText: '当前企业上下文已经变化' }).waitFor()
      await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
      await page.getByRole('region', { name: '原权利请求恢复', exact: true }).waitFor()
      await page.getByRole('button', { name: '查询原请求', exact: true }).click()
      await page.getByText('原操作查询：COMPLETED', { exact: true }).waitFor()
      await page.getByRole('region', { name: '原权利请求恢复', exact: true }).locator('summary').click()
      await page.getByRole('textbox', { name: '原权利决定完整JSON', exact: true }).fill(originalRequest.postData())
      const fixedPromise = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/rights-decisions'))
      await page.getByRole('button', { name: '读取原决定回执', exact: true }).click()
      recovery = await fixedPromise
    }
    assert.equal(recovery.status(), 200)
    const approved = (await recovery.json()).data
    await page.getByRole('region', { name: '权利决定完成回执', exact: true }).waitFor()
    assert.equal(approved.rights_status, 'APPROVED'); assert.equal(approved.revision, '3')
    assert.notEqual(approved.rights.version_id, returned.rights.version_id)
    assert.equal(attempts.at(-1).key, pending.key)
    checks.push('real_page_navigation_cancellation_and_same_original_request_recovery')
    assert.equal(approved.evidence.length, 1); assert.equal(approved.evidence[0].artifact.version_id, authorization)
    assert.equal(approved.evidence[0].artifact.content_sha256, hash)
    checks.push('full_approval_scope_and_fixed_authorization_artifact_returned_and_reopened')
    const exact = await checkExactActions({ page, origin, projectId, sourceVersion, pick, check })
    console.log(JSON.stringify({ mode, stage: 'EXACT_FIELDS_VERIFIED', checks: exact.checks.length }))
    await select('撤销已批准权利'); await readPreview()
    const revoked = await complete('撤销已批准权利', 'REVOKED')
    assert.equal(revoked.revision, '4'); assert.deepEqual(revoked.rights, approved.rights)
    checks.push('revocation_preserves_immutable_rights_and_reopens_actual_revoked_status')
    await checkExactRevocation(page, exact.historical)
    exact.checks.push('actual_revoked_source_rights_deny_old_exact_retrieval_and_clear_results')
    await page.goto(origin + rightsPath)
    await page.reload(); await page.getByRole('heading', { name: '来源权利审查', exact: true }).waitFor()
    assert.equal(await page.getByRole('region', { name: '固定权利审查', exact: true }).count(), 0)
    assert.equal(await page.getByRole('region', { name: '原权利请求恢复', exact: true }).count(), 0)
    assert.equal(await page.getByRole('textbox', { name: '完整许可说明', exact: true }).count(), 0)
    assert.equal(new Set(attempts.map(item => item.key)).size, 3); assert(attempts.every(item => item.authorization === null))
    assert.deepEqual(errors, []); checks.push('refresh_clears_review_and_sensitive_input_without_duplicate_decisions')
    const uploadChecks = []
    await page.goto(origin + '/projects/' + projectId + '/uploads')
    await page.getByRole('heading', { name: '上传项目资料', exact: true }).waitFor()
    await page.locator('input[type=file]').setInputFiles({ name: 'simulation-upload.txt', mimeType: 'text/plain',
      buffer: Buffer.from('SIMULATION 本轮前端开发的上传资料，用于核查原件传输、原操作和独立处理结果。') })
    const declaration = page.getByRole('dialog', { name: '逐文件来源与许可声明' })
    await declaration.waitFor()
    await declaration.getByLabel('资料所有者', { exact: true }).fill('SIMULATION 样例企业')
    await declaration.getByLabel('使用目的编号', { exact: true }).fill('DOCUMENT_BODY')
    await declaration.getByLabel('许可说明', { exact: true }).fill('SIMULATION 获授权前端开发资料')
    for (const label of ['读取', '保存']) await check(label, declaration)
    await check('已经按实际资料核查本文件的归属、版本意图、来源及许可', declaration)
    await declaration.getByRole('button', { name: '核查声明格式', exact: true }).click()
    assert.equal(await declaration.getByRole('alert').count(), 0)
    await declaration.locator('.el-dialog__headerbtn').click()
    const completionPromise = page.waitForResponse(response => response.request().method() === 'POST' && /\/api\/uploads\/[^/]+\/completions$/.test(new URL(response.url()).pathname), { timeout: 240000 })
    await page.getByRole('button', { name: '接收已确认文件', exact: true }).click()
    const completionResponse = await completionPromise; assert.equal(completionResponse.status(), 202)
    const completedOperation = (await completionResponse.json()).data
    assert.equal(completedOperation.status, 'PROCESSING')
    await page.getByRole('button', { name: '查询固定处理结果', exact: true }).waitFor()
    await page.getByRole('button', { name: '查询固定处理结果', exact: true }).click()
    await page.getByText('已核查固定处理结果及原完成操作', { exact: true }).waitFor()
    const queue = page.locator('[data-source-upload-queue]')
    await queue.locator('.el-table__expand-icon').click()
    const result = page.getByRole('region', { name: '固定上传处理结果', exact: true })
    await result.waitFor(); assert((await result.innerText()).includes(completedOperation.operation_id))
    assert.equal(await page.getByRole('region', { name: '上传关联固定来源', exact: true }).count(), 0)
    assert.equal(await page.getByRole('region', { name: '独立解析处理尝试', exact: true }).count(), 0)
    uploadChecks.push('actual_original_file_registration_parts_and_completion_remain_pending_processing')
    await page.reload(); await page.getByRole('heading', { name: '上传项目资料', exact: true }).waitFor()
    await page.getByRole('button', { name: '查询原上传记录', exact: true }).click()
    await page.getByText('原上传记录查询完成，逐文件结果见队列', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '核查并继续接收', exact: true }).isDisabled(), true)
    await queue.locator('.el-table__expand-icon').click(); await result.waitFor()
    assert((await result.innerText()).includes(completedOperation.operation_id))
    uploadChecks.push('refresh_recovers_actual_upload_result_without_file_resending')
    await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
    await page.waitForURL(origin + '/access')
    assert.equal(await result.count(), 0); assert.deepEqual(errors, [])
    uploadChecks.push('logout_clears_actual_upload_status_and_source_content')
    await writeFile(path.join(output, 'browser-' + mode + '.json'), JSON.stringify({ mode, browser_version: browser.version(), data_kind: 'SIMULATION',
      visual: 'NOT_RUN', checks, upload_checks: uploadChecks, index_checks: indexChecks, exact_checks: exact.checks, exact_revisions: exact.revisions, actual_upload_id: completedOperation.resource_id,
      original_operation_observed: observed, attempts, customer_success_verified: false }, null, 2) + '\n')
    console.log(JSON.stringify({ result: 'PASS', mode, checks: checks.length, unique_decision_keys: 3 }))
  } finally {
    if (inspectedPage !== null && !inspectedPage.isClosed()) {
      await writeFile(path.join(output, 'browser-dom-' + mode + '.json'), JSON.stringify({ url: inspectedPage.url(),
        headings: await inspectedPage.getByRole('heading').allTextContents(), alerts: await inspectedPage.getByRole('alert').allTextContents(),
        main_text: await inspectedPage.locator('main').allTextContents(), errors: inspectedErrors, source_responses: sourceResponses,
        session_responses: sessionResponses.map(value => ({ schema_issues: value.schema_issues, current_context: value.current_context })), upload_responses: uploadResponses,
        request_failures: requestFailures, response_statuses: responseStatuses,
        visual: 'NOT_RUN', data_kind: 'SIMULATION' }, null, 2) + '\n')
    }
    await browser.close()
  }
} finally {
  if (mode === 'development') await server.close()
  else await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
}

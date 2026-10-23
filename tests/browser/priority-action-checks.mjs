import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const seed = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/acceptance-workspace-seed.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
const proof = { verified_at: new Date().toISOString(), checks: [], failures: [], screenshots: false }
let page
try {
  page = await context.newPage()
  page.setDefaultTimeout(240000)
  page.on('pageerror', error => proof.failures.push(error.message))
  page.on('response', response => { if (new URL(response.url()).pathname.startsWith('/api/')) console.log('API', response.status(), new URL(response.url()).pathname) })
  async function route(path) {
    await page.evaluate(path => document.getElementById('app').__vue_app__.config.globalProperties.$router.push(path), path)
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    await page.waitForFunction(() => !document.querySelector('.fade-transform-leave-active,.fade-transform-enter-active'))
    await page.waitForLoadState('networkidle')
    assert.equal(new URL(page.url()).pathname, path.split('?')[0])
  }
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
  await page.getByLabel('密码', { exact: true }).fill(owner.password)
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index')
  const session = (await (await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()).data
  assert.equal(session.current_context.tenant_id, seed.tenant_id)
  await page.evaluate(() => {
    const config = document.getElementById('app').__vue_app__.config
    const handler = config.errorHandler
    config.errorHandler = (error, instance, info) => {
      console.error('ACCEPTANCE_ERROR', error.message, error.stack, info)
      return handler(error, instance, info)
    }
  })
  page.on('console', message => { if (message.text().startsWith('ACCEPTANCE_ERROR')) console.log(message.text()) })
  await route('/templates')
  const directory = page.locator('[data-template-list]')
  await directory.getByRole('button', { name: '审查模板', exact: true }).first().waitFor()
  const rows = await directory.locator('.el-table__body-wrapper tbody tr').count()
  assert.ok(rows > 0)
  const baseline = page.waitForResponse(response => response.url().includes('/approval-baseline'))
  await directory.getByRole('button', { name: '审查模板', exact: true }).first().click()
  const baselineResponse = await baseline
  assert.equal(baselineResponse.status(), 422)
  assert.equal((await baselineResponse.json()).code, 'CONTENT_MISMATCH')
  proof.checks.push({ name: 'authorized_template_directory', rows, result: 'PASS' }, { name: 'incomplete_template_dependencies_denied', result: 'PASS', approval_flow: 'REQUIRES_COMPLETE_FONT_FORMAT_AND_RULE_INPUTS' })
  await route('/sources/uploads')
  const project = page.getByRole('row').filter({ hasText: '验收测试 · 注册资料示例项目' })
  await project.getByRole('button', { name: '上传企业资料', exact: true }).click()
  await page.locator('.el-upload-dragger').waitFor()
  const name = '验收测试企业资料-' + Date.now() + '.txt'
  const transfer = await page.evaluateHandle(filename => {
    const value = new DataTransfer()
    value.items.add(new File(['SIMULATION 本地验收自编企业资料。仅用于上传、存储和页面功能验证。'], filename, { type: 'text/plain' }))
    return value
  }, name)
  await page.locator('.el-upload-dragger').dispatchEvent('drop', { dataTransfer: transfer })
  const dialog = page.getByRole('dialog', { name: '逐文件来源与许可声明', exact: true })
  await dialog.waitFor()
  await dialog.getByLabel('资料所有者', { exact: true }).fill('验收测试资料作者')
  await dialog.getByLabel('维护人员标识', { exact: true }).fill(session.user.user_id)
  await dialog.getByLabel('使用目的编号', { exact: true }).fill('LOCAL_ACCEPTANCE')
  await dialog.getByLabel('许可说明', { exact: true }).fill('自编验收文字，允许在当前本地工作组织内保存和读取。')
  await dialog.getByText('读取', { exact: true }).click()
  await dialog.getByText('保存', { exact: true }).click()
  await dialog.getByText('企业使用', { exact: true }).click()
  await dialog.getByText('已经按实际资料核查本文件的归属、版本意图、来源及许可', { exact: true }).click()
  await dialog.getByRole('button', { name: '核查声明格式', exact: true }).click()
  assert.equal(await dialog.locator('li[role=alert],ul[role=alert] li').count(), 0)
  await dialog.locator('.el-dialog__headerbtn').click()
  await page.getByRole('button', { name: '接收已确认文件', exact: true }).click()
  await page.getByText(/^完成命令：/).waitFor()
  assert.ok(await page.locator('[data-source-upload-queue]').innerText().then(value => value.includes(name)))
  proof.checks.push({ name: 'enterprise_upload_drag_declaration_transfer_and_receipt', file_name: name, result: 'PASS' })
  writeFileSync(new URL('../../.implementation-work/priority-action-checks.json', import.meta.url), JSON.stringify(proof, null, 2))
  await route(`/projects/${seed.project_id}/tasks/${seed.task_id}/documents/${seed.document_id}/write`)
  await page.getByRole('button', { name: '编辑正文', exact: true }).waitFor()
  await page.getByRole('button', { name: '编辑正文', exact: true }).click()
  const title = page.locator('.editor-content').getByRole('textbox', { name: '目录或段落标题', exact: true }).first()
  await title.fill('SIMULATION 验收文稿 · 保存验证 ' + new Date().toISOString())
  await page.getByLabel('本次修订理由', { exact: true }).fill('SIMULATION 本地验收：验证正文保存与审计关联。')
  const response = page.waitForResponse(response => response.url().includes('/revisions') && response.request().method() === 'POST' && response.status() === 201)
  await page.getByRole('button', { name: '保存正文修订', exact: true }).click()
  await response
  await page.waitForLoadState('networkidle')
  proof.checks.push({ name: 'actual_document_revision_save', result: 'PASS' })
  await route('/document-save-audit')
  await page.locator('.audit-query .el-select__wrapper').click()
  await page.getByRole('option').filter({ hasText: '验收测试 · 注册资料示例项目' }).click()
  await page.getByRole('button', { name: '查询保存记录', exact: true }).click()
  const records = page.locator('[data-audit-records]')
  await records.getByText(seed.document_id, { exact: true }).first().waitFor()
  await records.getByRole('button', { name: '读取固定版本', exact: true }).first().click()
  await page.getByRole('region', { name: '固定版本只读正文', exact: true }).waitFor()
  proof.checks.push({ name: 'save_audit_query_and_fixed_revision_read', result: 'PASS' })
  assert.deepEqual(proof.failures, [])
  console.log(JSON.stringify(proof))
} finally {
  proof.page = await page.locator('.app-main').innerText()
  proof.state = await page.evaluate(() => { const user = document.getElementById('app').__vue_app__.config.globalProperties.$pinia._s.get('user'); return { loading: user.medicalLoading, writing: user.medicalWriting, hidden: document.hidden, error: user.medicalError?.code } })
  writeFileSync(new URL('../../.implementation-work/priority-action-checks.json', import.meta.url), JSON.stringify(proof, null, 2))
  await context.close()
  await browser.close()
}

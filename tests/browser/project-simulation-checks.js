import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const [outputValue, backendValue, tenant, actor, basis, hash, frontendValue] = process.argv.slice(2)
const output = path.resolve(outputValue)
const backend = new URL(backendValue)
assert.equal(backend.hostname, '127.0.0.1')
const owner = JSON.parse(await readFile(path.join(root, '.implementation-work/runtime/owner-credentials.json'), 'utf8'))
process.chdir(path.join(root, 'web'))
const proxy = {}
for (const prefix of ['/api', '/auth', '/local-auth']) proxy[prefix] = {
  target: backend.origin, changeOrigin: true, autoRewrite: true,
  configure(value) {
    value.on('proxyReq', (forward, request) => {
      if (request.headers.origin !== undefined) {
        assert(['http://127.0.0.1:18083', 'http://127.0.0.1:18084'].includes(request.headers.origin))
        forward.setHeader('Origin', 'http://127.0.0.1:18080')
      }
    })
  }
}
const results = []
const requestedMode = process.env.MEDICAL_BROWSER_MODE
assert.ok(requestedMode === undefined || ['development', 'production'].includes(requestedMode))
for (const mode of requestedMode === undefined ? ['development', 'production'] : [requestedMode]) {
  const origin = mode === 'development' ? 'http://127.0.0.1:18083' : 'http://127.0.0.1:18084'
  const configuration = { mode: 'medical', configFile: path.join(root, 'web/vite.config.js'),
    cacheDir: path.join(root, '.implementation-work/project-browser-vite-cache'),
    build: { outDir: path.resolve(frontendValue) },
    server: { host: '127.0.0.1', port: 18083, strictPort: true, open: false, proxy },
    preview: { host: '127.0.0.1', port: 18084, strictPort: true, proxy } }
  const server = mode === 'development' ? await createServer(configuration) : await preview(configuration)
  try {
    if (mode === 'development') await server.listen()
    const browser = await chromium.launch({ channel: 'chrome', headless: true })
    try {
      const context = await browser.newContext()
      try {
        const page = await context.newPage(); const errors = []; const writes = []; const checks = []
        page.setDefaultNavigationTimeout(120000); page.setDefaultTimeout(60000)
        page.on('pageerror', error => { errors.push(error.name); console.error(error.message) })
        page.on('console', message => { if (message.type() === 'error') console.error(message.text()) })
        page.on('response', response => {
          const route = new URL(response.url()).pathname
          if (response.status() >= 400) console.error('HTTP', response.status(), route)
        })
        page.on('requestfailed', request => console.error('REQUEST_FAILED', new URL(request.url()).pathname, request.failure()?.errorText))
        page.on('request', request => {
          if (request.method() === 'POST' && new URL(request.url()).pathname.startsWith('/api/projects')) {
            writes.push({ path: new URL(request.url()).pathname, key: request.headers()['idempotency-key'],
              authorization: request.headers().authorization ?? null })
          }
        })
        const waitWrite = suffix => page.waitForResponse(response => response.request().method() === 'POST'
          && new URL(response.url()).pathname === suffix)
        const submit = async (suffix, status) => {
          const response = waitWrite(suffix)
          await page.getByRole('button', { name: '确认提交项目请求', exact: true }).click()
          const actual = await response
          assert.equal(actual.status(), status)
          const result = (await actual.json()).data
          await page.locator('[data-project-write-receipt]').waitFor()
          await page.getByText('已读取当前项目版本：' + result.revision + '；属性版本：' + result.profile_revision + '；状态：', { exact: false }).waitFor()
          assert.equal(await page.locator('[data-pending-project-write]').count(), 0)
          assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.project.write:'))), [])
          return result
        }
        assert.equal((await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })).status(), 200)
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor({ timeout: 120000 })
        console.log(JSON.stringify({ mode, stage: 'LOGIN_READY' }))
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/index')
        console.log(JSON.stringify({ mode, stage: 'LOGIN_COMPLETE' }))
        await page.goto(origin + '/projects')
        await page.getByRole('region', { name: '当前账户和企业空间' }).getByRole('combobox').press('Enter')
        await page.getByRole('option', { name: tenant, exact: true }).click()
        await page.getByRole('button', { name: '确认选择企业', exact: true }).click()
        await page.getByRole('button', { name: '创建项目', exact: true }).click()
        const dialog = page.getByRole('dialog', { name: '创建项目', exact: true })
        await dialog.waitFor()
        await dialog.getByRole('button', { name: '确认提交项目请求', exact: true }).click()
        await dialog.getByText('请核查实际依据并确认本次授权声明', { exact: true }).waitFor()
        assert.equal(writes.length, 0)
        checks.push('required_fields_prevent_incomplete_submission')
        const name = 'SIMULATION 浏览器项目验收 ' + mode
        await page.getByLabel('项目名称', { exact: true }).fill(name)
        await page.getByLabel('负责人账户标识', { exact: true }).fill(actor)
        await page.getByLabel('建档依据版本', { exact: true }).fill(basis)
        await page.getByLabel('建档依据内容摘要（SHA-256）', { exact: true }).fill(hash)
        await page.getByLabel('建档原因', { exact: true }).fill('虚拟项目浏览器验收')
        await dialog.getByText('本人已核查本次项目请求的实际依据并获准执行', { exact: true }).click()
        assert.equal(await dialog.getByRole('checkbox').isChecked(), true)
        const created = await submit('/api/projects', 201)
        console.log(JSON.stringify({ mode, stage: 'PROJECT_CREATED' }))
        assert.equal(created.status, 'PENDING'); assert.equal(created.revision, '1')
        checks.push('creation_and_operation_and_resource_reopening')
        await page.getByRole('link', { name: '读取当前项目', exact: true }).click()
        await page.getByRole('heading', { name, exact: true }).waitFor()
        await page.getByRole('button', { name: '修订项目属性', exact: true }).click()
        const revise = page.getByRole('dialog', { name: '修订项目属性', exact: true })
        await revise.waitFor()
        await revise.getByRole('button', { name: '添加项目属性', exact: true }).click()
        await revise.getByLabel('属性名称', { exact: true }).fill('simulation_usage')
        await revise.getByLabel('未知原因', { exact: true }).fill('虚拟项目用途待确认')
        await revise.getByLabel('决定原因', { exact: true }).fill('虚拟项目属性修订验收')
        await revise.getByText('本人已核查本次项目请求的实际依据并获准执行', { exact: true }).click()
        assert.equal(await revise.getByRole('checkbox').isChecked(), true)
        const revised = await submit('/api/projects/' + created.project_id + '/profile-revisions', 200)
        assert.equal(revised.revision, '2'); assert.notEqual(revised.profile_revision, created.profile_revision)
        await page.getByRole('link', { name: '读取当前项目', exact: true }).click()
        await page.getByRole('heading', { name, exact: true }).waitFor()
        const attribute = page.locator('[data-project-detail] .el-table__body-wrapper tbody tr').filter({ hasText: 'simulation_usage' })
        await attribute.getByText('尚未确认', { exact: true }).first().waitFor()
        await attribute.getByText('虚拟项目用途待确认', { exact: true }).waitFor()
        checks.push('unknown_attribute_and_immutable_profile_reopening')
        let revision = 2
        for (const [decision, label, status] of [['ACTIVATE', '启用项目', 'ACTIVE'], ['PAUSE', '暂停项目', 'PAUSED'],
            ['RESTORE', '恢复项目', 'ACTIVE'], ['ARCHIVE', '归档项目', 'ARCHIVED']]) {
          await page.getByRole('button', { name: '决定项目状态', exact: true }).click()
          const form = page.getByRole('dialog', { name: '决定项目状态', exact: true })
          await form.waitFor()
          await form.getByRole('combobox').press('Enter')
          await page.getByRole('option', { name: label, exact: true }).click()
          await form.getByLabel('决定原因', { exact: true }).fill('虚拟项目状态验收')
          await form.getByText('本人已核查本次项目请求的实际依据并获准执行', { exact: true }).click()
          assert.equal(await form.getByRole('checkbox').isChecked(), true)
          const receipt = await submit('/api/projects/' + created.project_id + '/state-decisions', 200)
          assert.equal(receipt.decision, decision); assert.equal(receipt.status, status); assert.equal(receipt.revision, String(++revision))
          await page.getByRole('link', { name: '读取当前项目', exact: true }).click()
          await page.getByRole('heading', { name, exact: true }).waitFor()
        }
        checks.push('all_project_state_decisions_and_reopening')
        await page.reload()
        await page.getByRole('heading', { name, exact: true }).waitFor()
        const state = page.locator('[data-project-detail] tr').filter({ hasText: '项目状态' })
        assert.equal((await state.locator('td').last().innerText()).trim(), '已归档')
        assert.equal(await page.getByRole('button', { name: '修订项目属性', exact: true }).count(), 0)
        assert.equal(writes.length, 6); assert.equal(new Set(writes.map(write => write.key)).size, 6)
        assert(writes.every(write => write.authorization === null)); assert.deepEqual(errors, [])
        checks.push('refresh_preserves_archived_state_and_current_actions')
        for (const [pathname, filterName] of [['/library', '资料筛选'], ['/library/processing', '处理队列筛选'], ['/library/artifacts', '成果筛选']]) {
          await page.goto(origin + pathname)
          const filters = page.locator('form[aria-label="' + filterName + '"]')
          await filters.waitFor()
          const projectInput = filters.getByRole('textbox').first()
          await projectInput.fill(created.project_id)
          if (pathname === '/library') await page.getByPlaceholder('文件夹名称', { exact: true }).fill('SIMULATION 文件夹输入')
          await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
          await page.waitForFunction(() => !document.querySelector('[aria-label="当前账户和企业空间"] [role="status"]'))
          assert.equal(await projectInput.inputValue(), '', '当前会话变化必须清理资料筛选范围')
          if (pathname === '/library') assert.equal(await page.getByPlaceholder('文件夹名称', { exact: true }).inputValue(), '', '当前会话变化必须清理文件夹输入')
          checks.push('session_change_clears_inputs:' + pathname)
        }
        assert.deepEqual(errors, [])
        results.push({ mode, browser_version: browser.version(), data_kind: 'SIMULATION', project_id: created.project_id, checks, writes })
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally {
    if (mode === 'development') await server.close()
    else await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
  }
}
await writeFile(path.join(output, 'project-browser-result.json'), JSON.stringify({ results, customer_success_verified: false }, null, 2) + '\n')

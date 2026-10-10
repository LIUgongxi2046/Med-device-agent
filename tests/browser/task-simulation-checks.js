import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'
import { verifyCurrentTaskDetail } from './task-detail-simulation-checks.js'

const root = process.cwd()
const [outputValue, backendValue, tenant, project, otherProject, firstTask, otherOwner, frontendValue] = process.argv.slice(2)
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
for (const mode of ['development', 'production']) {
  const origin = mode === 'development' ? 'http://127.0.0.1:18083' : 'http://127.0.0.1:18084'
  const configuration = { mode: 'medical', configFile: path.join(root, 'web/vite.config.js'),
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
        const page = await context.newPage(); const errors = []; const requests = []; const checks = []
        page.on('console', message => { if (message.type() === 'error') console.error(message.text()) })
        page.on('pageerror', error => { errors.push(error.name); console.error(error.message) })
        page.on('response', response => {
          if (response.status() >= 400) console.error('HTTP', response.status(), new URL(response.url()).pathname)
        })
        page.on('request', request => {
          const url = new URL(request.url())
          if (url.pathname.startsWith('/api/projects/') && url.pathname.endsWith('/tasks')) requests.push({ path: url.pathname, query: url.search,
            authorization: request.headers().authorization ?? null })
        })
        const waitTasks = () => page.waitForResponse(response => new URL(response.url()).pathname === '/api/projects/' + project + '/tasks')
        const table = page.locator('[data-task-table]')
        const rows = table.locator('.el-table__body-wrapper tbody tr')
        const verifyTotal = async total => {
          await page.getByText(new RegExp('获授权任务数量：' + total + '；分页水位：')).waitFor()
        }
        assert.equal((await page.goto(origin + '/access')).status(), 200)
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor({ timeout: 60000 })
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        const enterprise = page.getByRole('region', { name: '当前账户和企业空间' })
        await enterprise.getByRole('combobox').press('Enter')
        await page.getByRole('option', { name: tenant, exact: true }).click()
        await page.getByRole('button', { name: '确认选择企业', exact: true }).click()
        const projectResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/projects/' + project)
        await page.getByRole('link', { name: 'SIMULATION 项目任务验收', exact: true }).click()
        await page.getByRole('heading', { name: '项目详情', exact: true }).waitFor()
        assert.equal((await projectResponse).status(), 200)
        await page.getByRole('heading', { name: 'SIMULATION 项目任务验收', exact: true }).waitFor()
        const countRow = page.locator('[data-project-detail] tr').filter({ hasText: '任务数量' })
        assert.equal((await countRow.locator('td').last().innerText()).trim(), '23')
        const initialResponse = waitTasks()
        await page.getByRole('button', { name: '阅读项目任务', exact: true }).click()
        assert.equal((await initialResponse).status(), 200)
        await verifyTotal(23); assert.equal(await rows.count(), 20)
        checks.push('project_entry_and_authorized_count')
        await rows.first().getByRole('button', { name: '阅读完整信息', exact: true }).click()
        const dialog = page.getByRole('dialog', { name: '任务固定版本及当前许可' })
        await dialog.getByText(firstTask, { exact: true }).waitFor()
        await dialog.getByText('SIMULATION_REQUIRED_SOURCE', { exact: true }).waitFor()
        assert.equal(await dialog.getByText('task.read', { exact: true }).count(), 1)
        assert.equal(await dialog.getByText('task.plan.confirm', { exact: true }).count(), 0)
        await dialog.getByRole('button', { name: '关闭此对话框' }).click()
        checks.push('complete_task_details_and_current_missing_actions')
        await verifyCurrentTaskDetail(page, { origin, project, task: firstTask, otherProject }, checks)
        await verifyTotal(23); assert.equal(await rows.count(), 20)
        const paginationResponse = waitTasks()
        await page.locator('.el-pager li.number').filter({ hasText: /^2$/ }).click()
        const secondResponse = await paginationResponse
        assert.equal(secondResponse.status(), 200)
        const second = (await secondResponse.json()).data
        assert.equal(second.page_info.page, 2); assert.equal(second.page_info.total, '23')
        assert(new URL(secondResponse.url()).searchParams.has('dataset_watermark'))
        assert(new URL(secondResponse.url()).searchParams.has('filter_sha256'))
        await verifyTotal(23); assert.equal(await rows.count(), 3)
        checks.push('original_pagination_binds_dataset_and_filter')
        const statusField = page.locator('.el-form-item').filter({ hasText: '任务状态' })
        await statusField.getByRole('combobox').press('Enter')
        await page.getByRole('option', { name: '资料待补充', exact: true }).click()
        assert.equal(await table.count(), 0)
        const filteredResponse = waitTasks()
        await page.getByRole('button', { name: '查询任务', exact: true }).click()
        assert.equal((await filteredResponse).status(), 200)
        await verifyTotal(1); assert.equal(await rows.count(), 1)
        await rows.first().getByText('SIMULATION 任务读取验收 ' + firstTask, { exact: true }).waitFor()
        const refreshResponse = waitTasks()
        await page.reload(); assert.equal((await refreshResponse).status(), 200)
        await verifyTotal(23)
        const ownerField = page.locator('.el-form-item').filter({ hasText: '责任人员 UUID' })
        await ownerField.getByRole('textbox').fill(otherOwner)
        const ownerResponse = waitTasks()
        await page.getByRole('button', { name: '查询任务', exact: true }).click()
        const filteredOwner = (await (await ownerResponse).json()).data
        assert.equal(filteredOwner.page_info.total, '22')
        assert(filteredOwner.items.every(item => item.detail.owner_id === otherOwner))
        await verifyTotal(22)
        checks.push('status_and_owner_filters_and_refresh')
        const requestCount = requests.length
        await page.goto(origin + '/projects/' + otherProject + '/tasks')
        await page.getByText('任务列表需要当前企业身份及项目任务读取许可', { exact: true }).waitFor()
        assert.equal(await table.count(), 0); assert.equal(requests.length, requestCount)
        checks.push('project_change_clears_records_and_checks_permission')
        const resumedResponse = waitTasks()
        await page.goto(origin + '/projects/' + project + '/tasks')
        assert.equal((await resumedResponse).status(), 200); await verifyTotal(23)
        const session = (await (await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()).data
        const logout = await context.request.post(origin + '/auth/logout', {
          headers: { [session.csrf.header_name]: session.csrf.token, Origin: origin }
        })
        assert.equal(logout.status(), 204)
        await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
        assert.equal(await table.count(), 0)
        assert.deepEqual(errors, []); assert(requests.every(request => request.authorization === null))
        checks.push('revoked_session_clears_task_content')
        results.push({ mode, browser_version: browser.version(), data_kind: 'SIMULATION', checks, task_requests: requests })
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await server.close() }
}
await writeFile(path.join(output, 'task-browser-result.json'), JSON.stringify({ results, customer_success_verified: false }, null, 2) + '\n')

import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { userInfo } from 'node:os'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const output = path.resolve(process.argv[2])
const owner = JSON.parse(await readFile(path.join(root, '.implementation-work/runtime/owner-credentials.json'), 'utf8'))
assert.equal(owner.username, userInfo().username)
const modes = process.argv.includes('--development-only') ? ['development'] : ['development', 'production']
process.chdir(path.join(root, 'web'))
for (const mode of modes) {
  const origin = mode === 'development' ? 'http://127.0.0.1:18083' : 'http://127.0.0.1:18084'
  const configuration = { mode: 'medical', configFile: path.join(root, 'web/vite.config.js'),
    build: { outDir: path.join(output, 'production') },
    server: { host: '127.0.0.1', port: 18083, strictPort: true, open: false } }
  const server = mode === 'development' ? await createServer(configuration) : await preview(configuration)
  try {
    if (mode === 'development') await server.listen()
    const browser = await chromium.launch({ channel: 'chrome', headless: true })
    try {
      const context = await browser.newContext()
      try {
        const page = await context.newPage()
        const errors = []; const businessRequests = []; const checks = []
        page.on('pageerror', error => errors.push(error.name))
        page.on('request', request => {
          const pathname = new URL(request.url()).pathname
          if (pathname.startsWith('/api/tasks') || pathname.startsWith('/api/projects/')) businessRequests.push(request.method() + ' ' + pathname)
        })
        await page.goto(origin + '/access')
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        const response = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(response.status(), 200)
        const session = (await response.json()).data
        assert.equal(session.current_context, null); assert.deepEqual(session.memberships, [])
        const target = '/projects/' + session.user.user_id + '/tasks/' + session.user.user_id + '/config'
        await page.goto(origin + target)
        await page.getByRole('heading', { name: '任务当前状态', exact: true }).waitFor({ timeout: 10000 })
        await page.getByText('读取任务需要当前企业身份及任务读取权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-task-detail]').count(), 0)
        assert.equal(await page.getByRole('button', { name: '重新读取任务', exact: true }).isDisabled(), true)
        assert.deepEqual(businessRequests, [])
        checks.push({ name: 'actual_owner_without_enterprise_cannot_read_task_or_linked_resources', result: 'PASS' })
        await page.reload()
        await page.getByRole('heading', { name: '任务当前状态', exact: true }).waitFor()
        await page.getByText('读取任务需要当前企业身份及任务读取权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-task-detail]').count(), 0); assert.deepEqual(businessRequests, [])
        checks.push({ name: 'task_route_refresh_preserves_current_permission_requirement', result: 'PASS' })
        assert.equal(await page.getByRole('button', { name: '确认计划', exact: true }).count(), 0)
        assert.equal(await page.getByRole('button', { name: '启动任务', exact: true }).count(), 0)
        assert.equal(await page.getByRole('link', { name: '返回项目任务', exact: true }).count(), 0)
        checks.push({ name: 'restricted_task_page_does_not_present_unavailable_business_actions', result: 'PASS' })
        await page.getByRole('link', { name: '返回项目中心', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        assert.equal(await page.locator('[data-task-detail]').count(), 0)
        assert.deepEqual(businessRequests, [])
        checks.push({ name: 'leaving_task_page_releases_detail_without_preloading_other_resources', result: 'PASS' })
        await page.goto(origin + target)
        await page.getByRole('heading', { name: '任务当前状态', exact: true }).waitFor()
        await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
        await page.waitForURL(origin + '/access')
        assert.equal(await page.locator('[data-task-detail]').count(), 0)
        assert.deepEqual(errors, []); assert.deepEqual(businessRequests, [])
        checks.push({ name: 'logout_clears_current_task_page_and_stops_resource_reads', result: 'PASS' })
        await writeFile(path.join(output, 'task-detail-result-' + mode + '.json'), JSON.stringify({
          check_id: 'IC-12-TASK-DETAIL-BROWSER', scope: 'ACTUAL_OWNER_WITHOUT_ENTERPRISE', mode,
          actor: session.user.user_id, browser_version: browser.version(), visual: 'NOT_RUN', checks
        }, null, 2) + '\n')
        console.log(JSON.stringify({ check_id: 'IC-12-TASK-DETAIL-BROWSER', mode, result: 'PASS', checks: checks.length }))
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await (mode === 'development' ? server.close() : new Promise(resolve => server.httpServer.close(resolve))) }
}

import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { userInfo } from 'node:os'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const output = path.resolve(process.argv[2])
const modes = process.argv.includes('--development-only') ? ['development'] : ['development', 'production']
const owner = JSON.parse(await readFile(path.join(root, '.implementation-work/runtime/owner-credentials.json'), 'utf8'))
assert.equal(owner.username, userInfo().username)
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
        const errors = []; const requests = []; const checks = []
        page.on('pageerror', error => errors.push(error.name))
        page.on('request', request => {
          const pathname = new URL(request.url()).pathname
          if (pathname.startsWith('/api/projects/') || pathname.startsWith('/api/template-versions')) requests.push(request.method() + ' ' + pathname)
        })
        await page.goto(origin + '/access')
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        const response = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(response.status(), 200)
        const session = (await response.json()).data
        assert.equal(session.current_context, null)
        assert.deepEqual(session.memberships, [])
        const target = '/projects/' + session.user.user_id + '/templates'
        await page.goto(origin + target)
        await page.getByRole('heading', { name: '项目适用模板', exact: true }).waitFor({ timeout: 10000 })
        await page.getByText('查询适用模板需要当前企业身份及项目读取权限', { exact: true }).waitFor()
        assert.equal(await page.getByRole('button', { name: '查询适用模板', exact: true }).isDisabled(), true)
        assert.equal(await page.locator('[data-applicable-template]').count(), 0)
        assert.deepEqual(requests, [])
        checks.push({ name: 'actual_owner_requires_project_permission', result: 'PASS' })

        await page.reload()
        await page.getByRole('heading', { name: '项目适用模板', exact: true }).waitFor()
        await page.getByText('查询适用模板需要当前企业身份及项目读取权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-applicability-results]').count(), 0)
        assert.deepEqual(requests, [])
        checks.push({ name: 'refresh_preserves_permission_boundary', result: 'PASS' })

        await page.getByRole('link', { name: '返回项目详情', exact: true }).click()
        await page.getByRole('heading', { name: '项目详情', exact: true }).waitFor()
        assert.equal(await page.getByRole('button', { name: '查询适用模板', exact: true }).count(), 0)
        assert.deepEqual(requests, [])
        checks.push({ name: 'project_entry_requires_actual_project', result: 'PASS' })

        await page.goto(origin + target)
        await page.getByRole('heading', { name: '项目适用模板', exact: true }).waitFor()
        assert.equal(await page.getByRole('combobox', { name: '目标文档', exact: true }).isDisabled(), true)
        const saved = await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.includes('applicability')))
        assert.deepEqual(saved, [])
        checks.push({ name: 'restricted_query_has_no_saved_content', result: 'PASS' })

        await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
        await page.waitForURL(origin + '/access')
        assert.equal(await page.locator('[data-applicability-results]').count(), 0)
        assert.deepEqual(errors, [])
        assert.deepEqual(requests, [])
        checks.push({ name: 'logout_clears_query_page', result: 'PASS' })
        await writeFile(path.join(output, 'template-applicability-result-' + mode + '.json'), JSON.stringify({
          check_id: 'IC-11-APPLICABILITY-BROWSER', scope: 'ACTUAL_OWNER_WITHOUT_ENTERPRISE', mode,
          actor: session.user.user_id, browser_version: browser.version(), visual: 'NOT_RUN', checks
        }, null, 2) + '\n')
        console.log(JSON.stringify({ check_id: 'IC-11-APPLICABILITY-BROWSER', mode, result: 'PASS', checks: checks.length }))
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await (mode === 'development' ? server.close() : new Promise(resolve => server.httpServer.close(resolve))) }
}

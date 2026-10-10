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
process.chdir(path.join(root, 'web'))
for (const mode of ['development', 'production']) {
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
        const errors = []; const requests = []
        page.on('pageerror', error => { errors.push(error.name); console.error(error.message) })
        page.on('request', request => {
          if (new URL(request.url()).pathname.startsWith('/api/collections')) requests.push(request.method())
        })
        await page.goto(origin + '/access')
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        const sessionResponse = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(sessionResponse.status(), 200)
        const session = (await sessionResponse.json()).data
        assert.equal(session.current_context, null); assert.equal(session.memberships.length, 0)
        await page.goto(origin + '/library?view=collections')
        await page.getByRole('heading', { name: '资料集与固定版本', exact: true }).waitFor({ timeout: 10000 })
        await page.getByText('资料集操作需要当前企业的明确权限', { exact: true }).waitFor()
        assert.equal(await page.getByRole('button', { name: '创建资料集', exact: true }).isDisabled(), true)
        assert.equal(await page.getByRole('button', { name: '打开资料集', exact: true }).isDisabled(), true)
        assert.equal(await page.locator('[data-collection-form]').count(), 0)
        assert.deepEqual(requests, [])
        const checks = [{ name: 'actual_owner_requires_enterprise_permissions', result: 'PASS' }]
        await page.goto(origin + '/library/collections/' + session.user.user_id)
        await page.getByRole('heading', { name: '资料集与固定版本', exact: true }).waitFor()
        await page.getByText('资料集操作需要当前企业的明确权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-collection-history]').count(), 0)
        assert.equal(await page.locator('[data-collection-form]').count(), 0)
        assert.deepEqual(requests, [])
        checks.push({ name: 'actual_owner_deep_link_does_not_read_or_write', result: 'PASS' })
        await page.reload()
        await page.getByText('资料集操作需要当前企业的明确权限', { exact: true }).waitFor()
        assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.collection.'))), [])
        checks.push({ name: 'refresh_preserves_empty_request_state', result: 'PASS' })
        await page.goto(origin + '/library/collections/new')
        await page.getByRole('heading', { name: '资料集与固定版本', exact: true }).waitFor()
        await page.getByText('资料集操作需要当前企业的明确权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-collection-form]').count(), 0)
        assert.deepEqual(requests, [])
        checks.push({ name: 'creation_deep_link_requires_current_permission', result: 'PASS' })
        await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
        await page.waitForURL(origin + '/access')
        assert.equal(await page.locator('[data-collection-history]').count(), 0)
        assert.equal(await page.locator('[data-collection-form]').count(), 0)
        assert.deepEqual(errors, []); assert.deepEqual(requests, [])
        checks.push({ name: 'logout_removes_restricted_collection_content', result: 'PASS' })
        await writeFile(path.join(output, 'collection-result-' + mode + '.json'), JSON.stringify({
          check_id: 'IC-10-COLLECTION-BROWSER', scope: 'ACTUAL_OWNER_WITHOUT_ENTERPRISE', mode,
          actor: session.user.user_id, browser_version: browser.version(), visual: 'NOT_RUN', checks
        }, null, 2) + '\n')
        console.log(JSON.stringify({ check_id: 'IC-10-COLLECTION-BROWSER', mode, result: 'PASS', checks: checks.length }))
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await (mode === 'development' ? server.close() : new Promise(resolve => server.httpServer.close(resolve))) }
}

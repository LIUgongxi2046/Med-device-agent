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
        page.on('pageerror', error => errors.push(error.name))
        page.on('request', request => {
          if (new URL(request.url()).pathname.startsWith('/api/template-versions')) requests.push(request.method())
        })
        await page.goto(origin + '/access')
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        const sessionResponse = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(sessionResponse.status(), 200)
        const session = (await sessionResponse.json()).data
        assert.equal(session.current_context, null)
        assert.equal(session.memberships.length, 0)
        await page.goto(origin + '/templates')
        await page.getByRole('heading', { name: '模板人工审查', exact: true }).waitFor({ timeout: 10000 })
        await page.getByText('模板审查需要当前企业的明确核准权限', { exact: true }).waitFor()
        assert.equal(await page.getByRole('button', { name: '打开模板审查', exact: true }).isDisabled(), true)
        assert.equal(await page.locator('[data-template-preview]').count(), 0)
        const checks = [{ name: 'actual_owner_entry_requires_enterprise', result: 'PASS' }]

        await page.goto(origin + '/templates/' + session.user.user_id + '/approval')
        await page.getByRole('heading', { name: '模板人工审查', exact: true }).waitFor()
        await page.getByText('模板审查需要当前企业的明确核准权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-template-preview]').count(), 0)
        assert.equal(await page.locator('[data-template-decide]').count(), 0)
        assert.deepEqual(requests, [])
        checks.push({ name: 'actual_owner_deep_link_without_template_request', result: 'PASS' })

        await page.reload()
        await page.getByText('模板审查需要当前企业的明确核准权限', { exact: true }).waitFor()
        const stored = await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.template.approval:')))
        assert.deepEqual(stored, [])
        assert.deepEqual(requests, [])
        checks.push({ name: 'refresh_does_not_create_approval_identity', result: 'PASS' })

        await page.goto(origin + '/templates/new')
        await page.getByRole('heading', { name: '创建模板草稿', exact: true }).waitFor({ timeout: 10000 })
        await page.getByText('创建模板需要当前企业的明确维护权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-template-create-form]').count(), 0)
        assert.equal(await page.locator('[data-template-created]').count(), 0)
        assert.deepEqual(requests, [])
        checks.push({ name: 'actual_owner_creation_requires_enterprise', result: 'PASS' })

        await page.reload()
        await page.getByRole('heading', { name: '创建模板草稿', exact: true }).waitFor()
        await page.getByText('创建模板需要当前企业的明确维护权限', { exact: true }).waitFor()
        const pendingCreation = await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.template.create:')))
        assert.deepEqual(pendingCreation, [])
        assert.deepEqual(requests, [])
        checks.push({ name: 'refresh_does_not_create_template_request', result: 'PASS' })

        await page.goto(origin + '/templates')
        await page.getByRole('heading', { name: '模板人工审查', exact: true }).waitFor()
        assert.equal(await page.getByRole('button', { name: '创建模板草稿', exact: true }).isDisabled(), true)
        checks.push({ name: 'approval_entry_creation_action_requires_permission', result: 'PASS' })

        await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
        await page.waitForURL(origin + '/access')
        assert.equal(await page.locator('[data-template-preview]').count(), 0)
        assert.deepEqual(errors, [])
        checks.push({ name: 'logout_clears_restricted_page', result: 'PASS' })
        await writeFile(path.join(output, 'template-approval-result-' + mode + '.json'), JSON.stringify({
          check_id: 'IC-11-APPROVAL-BROWSER', scope: 'ACTUAL_OWNER_WITHOUT_ENTERPRISE', mode,
          actor: session.user.user_id, browser_version: browser.version(), visual: 'NOT_RUN', checks
        }, null, 2) + '\n')
        console.log(JSON.stringify({ check_id: 'IC-11-APPROVAL-BROWSER', mode, result: 'PASS', checks: checks.length }))
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await (mode === 'development' ? server.close() : new Promise(resolve => server.httpServer.close(resolve))) }
}

import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const [outputValue, backendValue, tenant, roleValue, basis, hash, frontendValue] = process.argv.slice(2)
const output = path.resolve(outputValue); const backend = new URL(backendValue)
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
  const role = roleValue.split(',')[mode === 'development' ? 0 : 1]
  const origin = mode === 'development' ? 'http://127.0.0.1:18083' : 'http://127.0.0.1:18084'
  const configuration = { mode: 'medical', configFile: path.join(root, 'web/vite.config.js'), build: { outDir: path.resolve(frontendValue) },
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
        page.on('pageerror', error => { errors.push(error.name); console.error(error.message) })
        page.on('request', request => {
          if (request.method() === 'POST' && new URL(request.url()).pathname.startsWith('/api/enterprise-roles')) {
            writes.push({ path: new URL(request.url()).pathname, key: request.headers()['idempotency-key'], authorization: request.headers().authorization ?? null })
          }
        })
        await page.goto(origin + '/access')
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor({ timeout: 60000 })
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        await page.getByRole('region', { name: '当前账户和企业空间' }).getByRole('combobox').press('Enter')
        await page.getByRole('option', { name: tenant, exact: true }).click()
        await page.getByRole('button', { name: '确认选择企业', exact: true }).click()
        await page.getByText('当前企业：' + tenant + '；上下文版本：', { exact: false }).waitFor()
        await page.goto(origin + '/enterprises/roles')
        await page.getByRole('heading', { name: '企业岗位', exact: true }).waitFor()
        await page.getByRole('button', { name: '发布岗位版本', exact: true }).click()
        const dialog = page.getByRole('dialog', { name: '发布岗位版本', exact: true })
        await dialog.waitFor()
        await dialog.getByRole('button', { name: '确认岗位决定', exact: true }).click()
        await dialog.getByText('请核查岗位依据并确认授权声明', { exact: true }).waitFor()
        assert.equal(writes.length, 0); checks.push('required_inputs_prevent_submission')
        const fillPublish = async name => {
          await page.getByLabel('岗位标识', { exact: true }).fill(role)
          await page.getByLabel('岗位名称', { exact: true }).fill(name)
          await page.getByLabel('原发布序号', { exact: true }).fill('0')
          await page.getByLabel('有效起始时间（UTC）', { exact: true }).fill('2026-10-07T01:00:00Z')
          await page.getByLabel('批准依据版本', { exact: true }).fill(basis)
          await page.getByLabel('批准依据摘要（SHA-256）', { exact: true }).fill(hash)
          await dialog.getByText('已批准没有固定截止时间', { exact: true }).click()
          await page.getByLabel('岗位动作（JSON 数组）', { exact: true }).fill('["task.read"]')
          await page.getByLabel('岗位条件（JSON 对象）', { exact: true }).fill(JSON.stringify({ schema_version: '1.0.0', identity_modes: ['local'],
            purposes: ['TASK_READING'], delegation: { actions: [], role_ids: [], resource_kinds: [], project_ids: [], resource_ids: [] } }))
          await page.getByLabel('决定原因', { exact: true }).fill('虚拟岗位浏览器验收')
          await dialog.getByText('本人已核查本次岗位的批准依据并获准执行', { exact: true }).click()
        }
        const submit = async (suffix, status) => {
          const response = page.waitForResponse(value => value.request().method() === 'POST' && new URL(value.url()).pathname === suffix)
          await page.getByRole('button', { name: '确认岗位决定', exact: true }).click()
          const actual = await response; assert.equal(actual.status(), status)
          const result = (await actual.json()).data
          await page.locator('[data-role-receipt]').waitFor()
          await page.locator('[data-current-role] tr').filter({ hasText: '不可变版本' }).getByText(result.role.version_id, { exact: true }).waitFor()
          assert.equal(await page.locator('[data-pending-role]').count(), 0)
          assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.role.write:'))), [])
          return result
        }
        await fillPublish('SIMULATION 浏览器岗位 ' + mode)
        const first = await submit('/api/enterprise-roles/' + role + '/versions', 201)
        assert.equal(first.role.status, 'ACTIVE'); checks.push('publish_operation_and_resource_reopening')
        await page.reload()
        await page.locator('[data-role-list]').getByText(first.role.version_id, { exact: true }).waitFor()
        await page.locator('[data-role-list] tr').filter({ hasText: first.role.version_id }).getByRole('button', { name: '读取岗位版本', exact: true }).click()
        await page.locator('[data-current-role]').getByText(first.role.content_sha256, { exact: true }).waitFor()
        checks.push('refresh_and_fixed_version_reading')
        await page.getByRole('button', { name: '修订岗位发布', exact: true }).click()
        await dialog.waitFor()
        await page.getByLabel('岗位名称', { exact: true }).fill('SIMULATION 岗位新版本 ' + mode)
        await page.getByLabel('决定原因', { exact: true }).fill('虚拟岗位版本修订验收')
        await dialog.getByText('本人已核查本次岗位的批准依据并获准执行', { exact: true }).click()
        const second = await submit('/api/enterprise-roles/' + role + '/versions', 201)
        assert.notEqual(first.role.version_id, second.role.version_id)
        await page.getByLabel('历史岗位版本标识', { exact: true }).fill(first.role.version_id)
        await page.getByRole('button', { name: '读取历史岗位版本', exact: true }).click()
        await page.locator('[data-current-role]').getByText(first.role.version_id, { exact: true }).waitFor()
        assert.equal(await page.locator('[data-current-role]').getByText('有效', { exact: true }).count(), 1)
        checks.push('new_head_and_immutable_history')
        await page.getByRole('button', { name: '撤销岗位版本', exact: true }).click()
        const revoke = page.getByRole('dialog', { name: '撤销岗位版本', exact: true })
        await revoke.waitFor()
        await revoke.getByLabel('决定原因', { exact: true }).fill('虚拟历史岗位撤销验收')
        await revoke.getByText('本人已核查本次岗位的批准依据并获准执行', { exact: true }).click()
        const revoked = await submit('/api/enterprise-roles/' + role + '/versions/' + first.role.version_id + '/revocations', 200)
        assert.equal(revoked.role.status, 'REVOKED')
        assert.equal(await page.getByRole('button', { name: '撤销岗位版本', exact: true }).count(), 0)
        checks.push('historical_revocation_and_current_actions')
        const session = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
        const current = (await session.json()).data
        const listing = await context.request.get(origin + '/api/enterprise-roles', { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current.current_context.context_revision } })
        assert.equal(listing.status(), 200)
        const listed = (await listing.json()).data.roles
        const currentRole = listed.find(value => value.role_id === role)
        assert.equal(currentRole.version_id, second.role.version_id); assert.equal(currentRole.status, 'ACTIVE')
        await page.getByRole('button', { name: '修订岗位发布', exact: true }).click()
        await dialog.waitFor()
        await page.getByLabel('岗位名称', { exact: true }).fill('SIMULATION 岗位连接恢复 ' + mode)
        await page.getByLabel('决定原因', { exact: true }).fill('虚拟岗位中断恢复验收')
        await dialog.getByText('本人已核查本次岗位的批准依据并获准执行', { exact: true }).click()
        const publishedResponse = page.waitForResponse(value => value.request().method() === 'POST'
          && new URL(value.url()).pathname === '/api/enterprise-roles/' + role + '/versions')
        const cutConnection = async response => {
          if (response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/enterprise-roles/' + role + '/versions') await context.setOffline(true)
        }
        page.on('response', cutConnection)
        await page.getByRole('button', { name: '确认岗位决定', exact: true }).click()
        const interrupted = await publishedResponse
        assert.equal(interrupted.status(), 201)
        const interruptedReceipt = (await interrupted.json()).data
        await page.getByRole('alert').filter({ hasText: '连接' }).waitFor()
        page.off('response', cutConnection)
        assert.equal(await page.locator('[data-role-receipt]').count(), 0)
        const saved = await page.evaluate(() => Object.entries(sessionStorage).filter(([key]) => key.startsWith('medical.role.write:')).map(([, value]) => JSON.parse(value)))
        assert.equal(saved.length, 1)
        assert.equal(saved[0].key, writes.at(-1).key)
        assert.equal(saved[0].role_id, role)
        assert.equal(Object.hasOwn(saved[0], 'body'), false)
        await context.setOffline(false)
        await page.reload()
        await page.locator('[data-pending-role]').waitFor()
        await page.getByRole('button', { name: '查询原岗位操作', exact: true }).click()
        await page.getByRole('button', { name: '核查原操作与当前岗位', exact: true }).click()
        await page.locator('[data-current-role] tr').filter({ hasText: '不可变版本' }).getByText(interruptedReceipt.role.version_id, { exact: true }).waitFor()
        assert.equal(await page.locator('[data-pending-role]').count(), 0)
        assert.equal(writes.length, 4)
        checks.push('committed_write_interruption_refresh_and_original_operation_recovery')
        await context.setOffline(true)
        await page.getByRole('button', { name: '重新读取岗位', exact: true }).click()
        await page.getByRole('alert').filter({ hasText: '连接' }).waitFor()
        assert.equal(await page.locator('[data-current-role]').count(), 0)
        await context.setOffline(false)
        await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
        await page.locator('[data-role-list]').getByText(interruptedReceipt.role.version_id, { exact: true }).waitFor()
        assert.equal(writes.length, 4); assert.equal(new Set(writes.map(value => value.key)).size, 4)
        assert(writes.every(value => value.authorization === null))
        assert.deepEqual(errors, [])
        checks.push('connection_failure_clears_content_and_recovery_reloads')
        results.push({ mode, data_kind: 'SIMULATION', browser_version: browser.version(), checks, writes })
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await server.close() }
}
await writeFile(path.join(output, 'role-browser-result.json'), JSON.stringify({ results, customer_success_verified: false }, null, 2) + '\n')

import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
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
        const errors = []; const writes = []; const reads = []; const checks = []
        page.on('pageerror', error => errors.push(error.name))
        page.on('request', request => {
          const pathname = new URL(request.url()).pathname
          if (!pathname.startsWith('/api/uploads') && pathname !== '/common/upload' && !pathname.startsWith('/api/projects/')) return
          if (request.method() === 'GET') reads.push(pathname)
          else writes.push(request.method() + ' ' + pathname)
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
        const target = '/projects/' + session.user.user_id + '/uploads'
        await page.goto(origin + target)
        await page.getByRole('heading', { name: '上传项目资料', exact: true }).waitFor({ timeout: 10000 })
        await page.getByText('上传资料需要当前企业身份及来源维护权限', { exact: true }).waitFor()
        assert.equal(await page.locator('input[type=file]').count(), 0)
        assert.equal(await page.locator('[data-source-upload-queue]').count(), 0)
        assert.deepEqual(writes, []); assert.deepEqual(reads, [])
        checks.push({ name: 'actual_owner_cannot_select_or_upload_files_without_enterprise', result: 'PASS' })

        await page.reload()
        await page.getByRole('heading', { name: '上传项目资料', exact: true }).waitFor()
        await page.getByText('上传资料需要当前企业身份及来源维护权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-source-upload-queue]').count(), 0)
        assert.deepEqual(writes, []); assert.deepEqual(reads, [])
        checks.push({ name: 'refresh_does_not_create_source_or_restore_other_identity', result: 'PASS' })

        await page.getByRole('link', { name: '返回项目详情', exact: true }).click()
        await page.getByRole('heading', { name: '项目详情', exact: true }).waitFor()
        assert.equal(await page.getByRole('button', { name: '上传项目资料', exact: true }).count(), 0)
        assert.deepEqual(writes, []); assert.deepEqual(reads, [])
        checks.push({ name: 'project_upload_entry_requires_actual_permission', result: 'PASS' })

        await page.goto(origin + target)
        await page.getByRole('heading', { name: '上传项目资料', exact: true }).waitFor()
        const saved = await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.source.upload:')))
        assert.deepEqual(saved, [])
        checks.push({ name: 'restricted_entry_does_not_save_file_or_request_identity', result: 'PASS' })

        await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
        await page.waitForURL(origin + '/access')
        assert.equal(await page.locator('[data-source-upload-queue]').count(), 0)
        assert.deepEqual(errors, []); assert.deepEqual(writes, []); assert.deepEqual(reads, [])
        checks.push({ name: 'logout_clears_upload_context', result: 'PASS' })
        const componentChecks = []
        if (mode === 'development') {
          const componentWrites = []
          page.on('request', request => {
            if (!['GET', 'HEAD'].includes(request.method())) componentWrites.push(request.method() + ' ' + new URL(request.url()).pathname)
          })
          await page.goto(origin + '/tests/source-file-upload/index.html')
          await page.getByRole('heading', { name: '原始文件选择检查', exact: true }).waitFor()
          const originals = [path.join(root, 'PRD.md'), path.join(root, 'HLD.md')]
          const expected = await Promise.all(originals.map(async filename => {
            const bytes = await readFile(filename)
            return { name: path.basename(filename), size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') }
          }))
          await page.locator('input[type=file]').setInputFiles(originals)
          await page.waitForFunction(() => JSON.parse(document.querySelector('[data-selected-files]').textContent).length === 2)
          const selected = JSON.parse(await page.locator('[data-selected-files]').textContent())
          assert.deepEqual(selected.sort((a, b) => a.name.localeCompare(b.name)), expected.sort((a, b) => a.name.localeCompare(b.name)))
          assert.deepEqual(writes, []); assert.deepEqual(componentWrites, []); assert.deepEqual(errors, [])
          componentChecks.push({ name: 'original_file_upload_emits_actual_files_with_exact_size_and_hash_without_auto_request', result: 'PASS' })
          await page.locator('input[type=file]').setInputFiles(originals[0])
          await page.waitForFunction(() => JSON.parse(document.querySelector('[data-selected-files]').textContent).length === 3)
          assert.deepEqual(writes, []); assert.deepEqual(componentWrites, []); assert.deepEqual(errors, [])
          componentChecks.push({ name: 'original_file_selection_can_repeat_after_control_clears_internal_selection', result: 'PASS' })
          await page.getByRole('button', { name: '清理选择', exact: true }).click()
          assert.equal(await page.locator('input[type=file]').count(), 0)
          assert.equal(await page.locator('[data-selected-files]').textContent(), '[]')
          assert.deepEqual(writes, []); assert.deepEqual(componentWrites, []); assert.deepEqual(errors, [])
          componentChecks.push({ name: 'disabled_original_upload_removes_file_input_and_releases_selected_files', result: 'PASS' })
        }
        await writeFile(path.join(output, 'source-upload-result-' + mode + '.json'), JSON.stringify({
          check_id: 'IC-08-SOURCE-BROWSER', scope: 'ACTUAL_OWNER_WITHOUT_ENTERPRISE', mode,
          actor: session.user.user_id, browser_version: browser.version(), visual: 'NOT_RUN', checks, component_checks: componentChecks
        }, null, 2) + '\n')
        console.log(JSON.stringify({ check_id: 'IC-08-SOURCE-BROWSER', mode, result: 'PASS', checks: checks.length }))
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await (mode === 'development' ? server.close() : new Promise(resolve => server.httpServer.close(resolve))) }
}

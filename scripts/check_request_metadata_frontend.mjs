import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : '../tests/browser/node_modules/playwright/index.mjs')
import { build, createServer, preview } from '../web/node_modules/vite/dist/node/index.js'

const root = fileURLToPath(new URL('../', import.meta.url))
const output = root + '.implementation-work/FRONT-REQUEST-METADATA-20261013'
await mkdir(output, { recursive: true })
process.chdir(root + 'web')
const options = { configFile: root + 'web/vite.config.js', mode: 'medical', cacheDir: output + '/vite-cache',
  server: { host: '127.0.0.1', port: 18088, strictPort: true, open: false },
  build: { outDir: output + '/production', emptyOutDir: true,
    rollupOptions: { input: root + 'web/tests/request-metadata/index.html' } } }
await build(options)
const production = await preview({ ...options, preview: { host: '127.0.0.1', port: 18089, strictPort: true, open: false } })
const development = await createServer(options)
try {
  await development.listen()
  const browser = await chromium.launch({ ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}), headless: true })
  try {
    const checks = []
    for (const [mode, origin] of [['development', 'http://127.0.0.1:18088'], ['production', 'http://127.0.0.1:18089']]) {
      const page = await browser.newPage()
      try {
        const external = []; const errors = []
        page.on('request', request => { if (request.url().startsWith('http') && new URL(request.url()).origin !== origin) external.push(request.url()) })
        page.on('pageerror', error => errors.push(error.message))
        await page.goto(origin + '/tests/request-metadata/index.html')
        await page.waitForFunction(() => window.metadataChecks !== undefined)
        const context = { tenant_id: randomUUID(), context_revision: '1', auth_epoch: '1', governance_epoch: '1' }
        const session = { user: { user_id: randomUUID() }, current_context: context }
        const target = randomUUID()
        const body = { schema_version: '1.0.0', project_id: target, query: 'SIMULATION 查询', purpose: 'VERIFICATION' }
        const data = { session, target, body }
        const prepare = value => page.evaluate(input => window.metadataChecks.prepareRequestMetadata(input.session, 'KN-14-S04', input.target, input.target, input.target, input.body, new AbortController().signal), value)
        const pending = await prepare(data); const repeated = await prepare(data)
        assert.equal(repeated.key, pending.key)
        assert.deepEqual(await page.evaluate(input => window.metadataChecks.readRequestMetadata(input.session, 'KN-14-S04', input.target), data), pending)
        await assert.rejects(() => prepare({ ...data, body: { ...body, query: 'SIMULATION 其他查询' } }), /原请求仍待核查/)
        const stored = await page.evaluate(() => sessionStorage.getItem(sessionStorage.key(0)))
        assert.equal(stored.includes(body.query), false)
        assert.deepEqual(Object.keys(JSON.parse(stored)).sort(), ['actor', 'body_sha256', 'context', 'key', 'operation', 'resource_id', 'scope_id', 'state', 'target_id', 'tenant_id'].sort())
        assert.ok(await page.evaluate(value => window.metadataChecks.schemaIssues('urn:medical:request-metadata:1.0.0', { ...value, query: 'SIMULATION' }).length, pending) > 0)
        const other = { ...session, current_context: { ...context, tenant_id: randomUUID() } }
        assert.equal(await page.evaluate(input => window.metadataChecks.readRequestMetadata(input.session, 'KN-14-S04', input.target), { session: other, target }), null)
        const otherActor = { ...session, user: { user_id: randomUUID() } }
        assert.deepEqual(await page.evaluate(value => window.metadataChecks.listRequestMetadata(value), otherActor), [])
        await assert.rejects(() => page.evaluate(input => window.metadataChecks.clearExpiredRequestMetadata(input.session, input.pending), { session, pending }), /尚未取得过期结果/)
        await assert.rejects(() => page.evaluate(input => {
          const abort = new AbortController(); abort.abort()
          return window.metadataChecks.prepareRequestMetadata(input.session, 'EX-01', input.target, input.target, input.target, input.body, abort.signal)
        }, data), /AbortError/)
        await page.evaluate(input => window.metadataChecks.expireRequestMetadata(input.session, input.pending), { session, pending })
        await assert.rejects(() => prepare(data), /原请求已经过期/)
        await page.evaluate(input => window.metadataChecks.clearExpiredRequestMetadata(input.session,
          window.metadataChecks.readRequestMetadata(input.session, 'KN-14-S04', input.target)), data)
        const fresh = await prepare(data); assert.notEqual(fresh.key, pending.key)
        const operationId = randomUUID()
        const operation = { operation_id: operationId, resource_id: target, status: 'COMPLETED', status_url: '/api/operations/' + operationId }
        const confirm = value => page.evaluate(input => window.metadataChecks.confirmRequestMetadata(input.session, input.pending, input.operation, input.target), value)
        await assert.rejects(() => confirm({ session, pending, operation, target }), /原请求元数据已经变化/)
        await assert.rejects(() => confirm({ session, pending: fresh, operation: { ...operation, status: 'PROCESSING' }, target }), /原请求尚未取得/)
        await confirm({ session, pending: fresh, operation, target })
        assert.equal(await page.evaluate(input => window.metadataChecks.readRequestMetadata(input.session, 'KN-14-S04', input.target), data), null)
        const documentId = randomUUID(); const exportId = randomUUID(); const exportItemId = randomUUID()
        const exportBody = { schema_version: '1.0.0', document_revision: randomUUID(), purpose: 'INTERNAL_EXCHANGE', formats: ['DOCX'],
          template_version: randomUUID(), review_manifest_sha256: 'a'.repeat(64), format_profile_revision: randomUUID(), channel_profile_revision: null }
        const exportRequest = await page.evaluate(input => window.metadataChecks.prepareRequestMetadata(input.session, 'EX-01',
          input.documentId, input.documentId, input.documentId, input.body, new AbortController().signal), { session, documentId, body: exportBody })
        const reason = 'SIMULATION 格式处理重试'
        const retryRequest = await page.evaluate(input => window.metadataChecks.prepareRequestMetadata(input.session, 'EX-04',
          input.exportItemId, input.exportId, input.documentId, { schema_version: '1.0.0', expected_revision: '1', reason: input.reason },
          new AbortController().signal), { session, documentId, exportId, exportItemId, reason })
        assert.equal(retryRequest.resource_id, exportId); assert.equal(retryRequest.scope_id, documentId)
        assert.deepEqual((await page.evaluate(value => window.metadataChecks.listRequestMetadata(value), session)).map(value => value.key).sort(), [exportRequest.key, retryRequest.key].sort())
        assert.equal(await page.evaluate(value => Object.keys(sessionStorage).some(key => sessionStorage.getItem(key).includes(value)), reason), false)
        const locator = { kind: 'TXT', encoding: 'UTF-8', newline_policy: 'PRESERVE', char_start: 0, char_end: 10, line_start: 1, line_end: 1 }
        assert.equal(await page.evaluate(value => window.metadataChecks.schemaIssues('https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json#/$defs/locator', value).length, locator), 0)
        assert.ok(await page.evaluate(value => window.metadataChecks.schemaIssues('https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json#/$defs/locator', { ...value, unknown_field: true }).length, locator) > 0)
        assert.deepEqual(errors, []); assert.deepEqual(external, [])
        checks.push({ mode, data_kind: 'SIMULATION', result: 'PASS' })
      } finally { await page.close() }
    }
    await writeFile(output + '/checks.json', JSON.stringify({ scope: 'REQUEST_METADATA', checks }, null, 2) + '\n')
    console.log(JSON.stringify({ scope: 'REQUEST_METADATA', data_kind: 'SIMULATION', result: 'PASS', modes: checks.length }))
  } finally { await browser.close() }
} finally {
  await development.close()
  await new Promise((resolve, reject) => production.httpServer.close(error => error ? reject(error) : resolve()))
}

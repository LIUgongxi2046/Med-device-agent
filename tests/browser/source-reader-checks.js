import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { build, createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const output = path.join(root, '.implementation-work/FRONT-SOURCE-READER-20261013')
await mkdir(output, { recursive: true })
const dataset = JSON.parse(await readFile(path.join(root, 'data-design/sample_dataset.json'), 'utf8'))
const originals = dataset.sources.filter(value => ['DOCX', 'TXT', 'MARKDOWN', 'PDF'].includes(value.format))
for (const source of originals) {
  assert.ok(source.allowed_use.includes('LOCAL_PARSER_CHECK'))
  assert.equal(createHash('sha256').update(await readFile(path.join(root, source.source_ref))).digest('hex'), source.sha256)
}
for (const format of ['CSV', 'XLSX', 'PPTX']) {
  const source_ref = '.implementation-work/document-tests/simulation-tasks.' + format.toLowerCase()
  const bytes = await readFile(path.join(root, source_ref))
  originals.push({ source_id: 'SIMULATION-' + format, source_ref, format, data_kind: 'SIMULATION',
    sha256: createHash('sha256').update(bytes).digest('hex') })
}
const developmentOnly = process.argv.includes('--development-only')
process.chdir(path.join(root, 'web'))
const options = { configFile: path.join(root, 'web/vite.config.js'), mode: 'medical',
  cacheDir: path.join(output, 'vite-cache'),
  server: { host: '127.0.0.1', port: 18086, strictPort: true, open: false },
  build: { outDir: path.join(output, 'preview-build'), emptyOutDir: true,
    rollupOptions: { input: path.join(root, 'web/tests/source-reader/index.html') } } }
if (!developmentOnly) await build(options)
const production = developmentOnly ? null : await preview({ ...options, preview: { host: '127.0.0.1', port: 18087, strictPort: true, open: false } })
const development = await createServer(options)
try {
  await development.listen()
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const checks = []
    const modes = [['development', 'http://127.0.0.1:18086']]
    if (!developmentOnly) modes.push(['production', 'http://127.0.0.1:18087'])
    for (const [mode, origin] of modes) {
      const page = await browser.newPage()
      try {
        const errors = []; const externalRequests = []
        page.on('pageerror', error => errors.push(error.message))
        page.on('request', request => { if (request.url().startsWith('http') && new URL(request.url()).origin !== origin) externalRequests.push(request.url()) })
        await page.goto(origin + '/tests/source-reader/index.html')
        const latex = String.raw`\frac{12.500}{2}\,\mathrm{mm}`
        await page.getByRole('textbox', { name: '模拟公式原文', exact: true }).fill(latex)
        await page.getByRole('button', { name: '预览模拟公式', exact: true }).click()
        await page.locator('.formula-check .katex').waitFor()
        assert.equal(await page.locator('.formula-check annotation').textContent(), latex)
        await page.getByRole('textbox', { name: '模拟公式原文', exact: true }).fill(String.raw`\href{https://127.0.0.1:18089/probe}{SIMULATION}`)
        assert.equal(await page.locator('.formula-check .katex').count(), 0)
        await page.getByRole('button', { name: '预览模拟公式', exact: true }).click()
        await page.locator('.formula-check .katex').waitFor()
        assert.equal(await page.locator('.formula-check a[href],.formula-check script,.formula-check iframe').count(), 0)
        checks.push({ mode, feature: 'FORMULA', data_kind: 'SIMULATION', result: 'PASS' })
        for (const source of originals) {
          await page.getByLabel('选择获准原件', { exact: true }).setInputFiles(path.join(root, source.source_ref))
          if (source.format === 'DOCX' || source.format === 'PPTX') {
            const frame = page.frameLocator('iframe[title="Office原件转换预览"]')
            await frame.locator(source.format === 'DOCX' ? 'section.docx' : '.pptx-preview-wrapper').first().waitFor()
            const iframe = page.locator('iframe[title="Office原件转换预览"]')
            assert.equal(await iframe.getAttribute('sandbox'), '')
            assert.ok((await iframe.getAttribute('srcdoc')).includes('default-src'))
            const content = await frame.locator('body').innerText()
            if (source.format === 'DOCX') assert.ok(content.length > 100)
            else { assert.ok(content.includes('SIMULATION 任务读取验收')); assert.ok(content.includes('分页与过滤验收')) }
          } else if (source.format === 'TXT') {
            await page.locator('.text-content').waitFor()
            assert.equal(await page.locator('.text-content').textContent(), await readFile(path.join(root, source.source_ref), 'utf8'))
          } else if (source.format === 'MARKDOWN') {
            await page.locator('.markdown-content h1').waitFor()
            assert.equal(await page.locator('.markdown-content script,.markdown-content iframe,.markdown-content img,.markdown-content a[href]').count(), 0)
          } else if (source.format === 'XLSX' || source.format === 'CSV') {
            await page.locator('.sheet-scroll table').waitFor()
            const content = await page.locator('.sheet-scroll table').innerText()
            assert.ok(content.includes('SIMULATION'))
            if (source.format === 'CSV') assert.ok(content.includes('企业任务\n读取验收'))
            else assert.ok(content.includes('=LEN(C2)'), 'XLSX公式原文必须明确呈现且不执行计算')
          } else {
            await page.locator('iframe[title="固定PDF原件"]').waitFor()
            const url = await page.locator('iframe[title="固定PDF原件"]').getAttribute('src')
            const digest = await page.evaluate(async value => {
              const response = await fetch(value); const bytes = await response.arrayBuffer()
              return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), byte => byte.toString(16).padStart(2, '0')).join('')
            }, url)
            assert.equal(digest, source.sha256)
          }
          await page.getByRole('button', { name: '清理阅读内容', exact: true }).click()
          await page.waitForFunction(() => !document.querySelector('.file-frame,.markdown-content,.text-content,table'))
          checks.push({ mode, source_id: source.source_id, format: source.format, data_kind: source.data_kind ?? 'LOCAL_PARSER_CHECK', result: 'PASS' })
        }
        assert.deepEqual(errors, []); assert.deepEqual(externalRequests, [])
      } finally { await page.close() }
    }
    await writeFile(path.join(output, developmentOnly ? 'development-checks.json' : 'browser-checks.json'), JSON.stringify({ scope: 'LOCAL_DOCUMENT_PREVIEW', visual_check: 'NOT_RUN', checks }, null, 2) + '\n')
    console.log(JSON.stringify({ result: 'PASS', checks: checks.length, scope: 'LOCAL_DOCUMENT_PREVIEW' }))
  } finally { await browser.close() }
} finally {
  await development.close()
  if (production) await new Promise((resolve, reject) => production.httpServer.close(error => error ? reject(error) : resolve()))
}

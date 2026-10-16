import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { canonicalizeEx } from '../../web/node_modules/json-canonicalize/dist/index.mjs'
import { chromium } from 'playwright'
import { build, createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const output = path.join(root, '.implementation-work/FRONT-DOCUMENT-SELECTION-20261013')
await mkdir(output, { recursive: true })
const developmentOnly = process.argv.includes('--development-only')
process.chdir(path.join(root, 'web'))
const options = { configFile: path.join(root, 'web/vite.config.js'), mode: 'medical', cacheDir: path.join(output, 'vite-cache'),
  server: { host: '127.0.0.1', port: 18090, strictPort: true, open: false },
  build: { outDir: path.join(output, 'production'), emptyOutDir: true,
    rollupOptions: { input: path.join(root, 'web/tests/document-selection/index.html') } } }
if (!developmentOnly) await build(options)
const development = await createServer(options)
const production = developmentOnly ? null : await preview({ ...options, preview: { host: '127.0.0.1', port: 18091, strictPort: true, open: false } })
try {
  await development.listen()
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const checks = []
    for (const [mode, origin] of developmentOnly ? [['development', 'http://127.0.0.1:18090']] : [['development', 'http://127.0.0.1:18090'], ['production', 'http://127.0.0.1:18091']]) {
      const page = await browser.newPage()
      page.setDefaultNavigationTimeout(120000)
      page.setDefaultTimeout(60000)
      try {
        const errors = []; const writes = []
        page.on('pageerror', error => errors.push(error.message))
        page.on('request', request => { if (!['GET', 'HEAD'].includes(request.method())) writes.push(request.method() + ' ' + new URL(request.url()).pathname) })
        await page.goto(origin + '/tests/document-selection/index.html')
        await page.getByRole('heading', { name: 'SIMULATION 正文选区检查', exact: true }).waitFor()
        await page.locator('[data-selection-text]').getByText('丙🧪丁', { exact: true }).waitFor()
        await page.getByRole('heading', { name: '正文目录', exact: true }).waitFor({ timeout: 5000 })
        const body = await page.evaluate(() => window.documentSelectionChecks.body)
        const hash = value => createHash('sha256').update(value).digest('hex')
        const first = body.nodes.find(node => node.content.kind === 'TEXT')
        const last = body.nodes.filter(node => node.content.kind === 'TEXT').at(-1)
        const image = body.nodes.find(node => node.content.kind === 'IMAGE')
        const table = body.nodes.find(node => node.content.kind === 'TABLE')
        assert.equal(await page.getByRole('navigation', { name: '正文目录' }).getByRole('button').count(), 3)
        await page.getByRole('navigation', { name: '正文目录' }).getByRole('button', { name: 'SIMULATION 合并表格', exact: true }).click()
        assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-label')), '表格内容')
        const textStyle = node => page.locator('[data-selection-text="' + node.node_id + '"]').evaluate(element => ({
          fontWeight: element.style.fontWeight, fontStyle: element.style.fontStyle,
          textDecoration: element.style.textDecoration, verticalAlign: element.style.verticalAlign, fontSize: element.style.fontSize
        }))
        assert.deepEqual(await textStyle(first), { fontWeight: 'bold', fontStyle: 'italic', textDecoration: 'underline', verticalAlign: 'super', fontSize: 'smaller' })
        assert.deepEqual(await textStyle(last), { fontWeight: '', fontStyle: '', textDecoration: '', verticalAlign: 'sub', fontSize: 'smaller' })
        checks.push({ mode, name: 'complete_document_outline_and_all_text_marks', result: 'PASS' })
        await page.evaluate(() => {
          const elements = document.querySelectorAll('[data-selection-text]')
          const range = document.createRange(); range.setStart(elements[0].firstChild, 1); range.setEnd(elements[1].firstChild, 3)
          const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range)
        })
        await page.getByRole('button', { name: '保留当前文字选区', exact: true }).click()
        await page.locator('[data-document-selection]').waitFor()
        const read = async () => JSON.parse(await page.locator('[data-document-selection] pre').textContent())
        let selected = await read()
        assert.deepEqual(selected.segments, [
          { kind: 'TEXT', node_id: first.node_id, node_sha256: hash(canonicalizeEx(first)), start_cp: 1, end_cp: 6, quote_sha256: hash('😀e\u0301\n乙') },
          { kind: 'TEXT', node_id: last.node_id, node_sha256: hash(canonicalizeEx(last)), start_cp: 0, end_cp: 2, quote_sha256: hash('丙🧪') }
        ])
        const crossingEmpty = await page.evaluate(() => {
          const original = structuredClone(window.documentSelectionChecks.body)
          const nodes = original.nodes.filter(node => node.content.kind === 'TEXT')
          for (const node of original.nodes) if (node.parent_id === nodes[0].parent_id && node.ordinal >= 1) node.ordinal += 1
          const empty = { ...structuredClone(nodes[0]), node_id: crypto.randomUUID(), ordinal: 1, content: { kind: 'TEXT', text: '', marks: [] } }
          original.nodes.push(empty)
          window.documentSelectionChecks.orderedDocumentNodes(original)
          const content = document.createElement('section')
          for (const node of [nodes[0], empty, nodes[1]]) {
            const span = document.createElement('span'); span.dataset.selectionText = node.node_id; span.textContent = node.content.text
            content.append(span)
          }
          const range = document.createRange(); range.setStart(content.children[0].firstChild, 1); range.setEnd(content.children[2].firstChild, 3)
          return window.documentSelectionChecks.browserTextSelection(content, range, original)
        })
        assert.deepEqual(crossingEmpty, [
          { kind: 'TEXT', node_id: first.node_id, start_utf16: 1, end_utf16: first.content.text.length },
          { kind: 'TEXT', node_id: last.node_id, start_utf16: 0, end_utf16: 3 }
        ])
        checks.push({ mode, name: 'browser_cross_node_unicode_selection_and_original_hashes', result: 'PASS' })
        await page.getByRole('button', { name: '选择全文文字', exact: true }).click()
        await page.waitForFunction(() => JSON.parse(document.querySelector('[data-document-selection] pre').textContent).segments[0].start_cp === 0)
        selected = await read(); assert.equal(selected.segments.length, 2)
        assert.equal(selected.segments[1].end_cp, 3)
        await page.getByText('使用键盘选择文字范围', { exact: true }).click()
        await page.getByRole('combobox', { name: '文字节点', exact: true }).focus()
        await page.getByRole('combobox', { name: '文字节点', exact: true }).press('ArrowDown')
        await page.getByRole('option', { name: '丙🧪丁', exact: true }).click()
        await page.getByRole('spinbutton', { name: '起始字符位置', exact: true }).fill('1')
        await page.getByRole('spinbutton', { name: '结束字符位置', exact: true }).fill('2')
        await page.getByRole('button', { name: '确认文字范围', exact: true }).click()
        await page.waitForFunction(() => JSON.parse(document.querySelector('[data-document-selection] pre').textContent).segments.length === 1)
        selected = await read(); assert.equal(selected.segments[0].quote_sha256, hash('🧪'))
        checks.push({ mode, name: 'full_text_and_keyboard_code_point_selection', result: 'PASS' })
        await page.getByRole('button', { name: '选择当前图片节点', exact: true }).click()
        await page.waitForFunction(() => JSON.parse(document.querySelector('[data-document-selection] pre').textContent).segments[0].kind === 'IMAGE')
        assert.deepEqual((await read()).segments, [{ kind: 'IMAGE', node_id: image.node_id, image_artifact_id: image.content.artifact_id, image_sha256: image.content.sha256 }])
        const merged = page.locator('[data-node-id="' + table.node_id + '"]')
        await merged.locator('[data-selection-cell="' + table.content.cells[0].cell_id + '"]').click()
        assert.equal(await merged.locator('[data-selection-cell="' + table.content.cells[0].cell_id + '"]').getByRole('checkbox').isChecked(), true)
        await merged.getByRole('button', { name: '确认表格选区', exact: true }).click()
        await page.waitForFunction(() => JSON.parse(document.querySelector('[data-document-selection] pre').textContent).segments[0].kind === 'TABLE')
        selected = await read()
        assert.deepEqual(selected.segments[0].row_ids, table.content.row_ids)
        assert.deepEqual(selected.segments[0].column_ids, [table.content.column_ids[0]])
        assert.deepEqual(selected.segments[0].cell_ids, [table.content.cells[0].cell_id])
        assert.equal(selected.segments[0].table_sha256, hash(canonicalizeEx(table)))
        checks.push({ mode, name: 'fixed_image_and_merged_table_selection', result: 'PASS' })
        await assert.rejects(() => page.evaluate(() => window.documentSelectionChecks.unicodeCodePointOffset('甲😀乙', 2)), /完整Unicode/)
        await assert.rejects(() => page.evaluate(value => window.documentSelectionChecks.createDocumentSelection(window.documentSelectionChecks.body,
          value.revision, [{ kind: 'TEXT', node_id: value.id, start_utf16: 0, end_utf16: 1 }, { kind: 'TEXT', node_id: value.id, start_utf16: 1, end_utf16: 2 }], new AbortController().signal),
          { revision: selected.document_revision, id: first.node_id }), /正文顺序/)
        checks.push({ mode, name: 'surrogate_boundary_and_duplicate_node_rejected', result: 'PASS' })
        await page.getByRole('button', { name: '更换正文版本', exact: true }).click()
        assert.equal(await page.locator('[data-document-selection]').count(), 0)
        assert.equal(await merged.locator('[data-selection-cell="' + table.content.cells[0].cell_id + '"]').getByRole('checkbox').isChecked(), false)
        await page.getByRole('button', { name: '选择全文文字', exact: true }).click()
        await page.locator('[data-document-selection]').waitFor()
        assert.notEqual((await read()).document_revision, selected.document_revision)
        await page.getByRole('button', { name: '更换编辑状态', exact: true }).click()
        assert.equal(await page.locator('[data-document-selection]').count(), 0)
        await page.getByRole('button', { name: '选择全文文字', exact: true }).click()
        await page.getByText('未保存正文选区 · 2 个节点', { exact: true }).waitFor()
        assert.equal(await page.evaluate(() => Object.keys(sessionStorage).some(key => sessionStorage.getItem(key).includes('丙🧪丁'))), false)
        await page.getByRole('button', { name: '清理正文上下文', exact: true }).click()
        assert.equal(await page.locator('[data-document-selection]').count(), 0)
        assert.equal(await page.locator('[data-selection-text]').count(), 0)
        checks.push({ mode, name: 'version_dirty_context_changes_clear_original_selection', result: 'PASS' })
        assert.deepEqual(errors, []); assert.deepEqual(writes, [])
      } finally { await page.close() }
    }
    await writeFile(path.join(output, 'checks.json'), JSON.stringify({ scope: 'DOCUMENT_SELECTION', data_kind: 'SIMULATION', visual: 'NOT_RUN', checks }, null, 2) + '\n')
    console.log(JSON.stringify({ result: 'PASS', checks: checks.length }))
  } finally { await browser.close() }
} finally {
  await development.close()
  if (production) await new Promise((resolve, reject) => production.httpServer.close(error => error ? reject(error) : resolve()))
}

import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
import { chromium } from 'playwright'
import { build, createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd(); const output = path.join(root, '.implementation-work/FRONT-TEMPLATE-FIELDS-20261013')
await mkdir(output, { recursive: true })
const developmentOnly = process.argv.includes('--development-only')
process.chdir(path.join(root, 'web'))
const options = { configFile: path.join(root, 'web/vite.config.js'), mode: 'medical', cacheDir: path.join(output, 'vite-cache'),
  server: { host: '127.0.0.1', port: 18092, strictPort: true, open: false },
  build: { outDir: path.join(output, 'production'), emptyOutDir: true,
    rollupOptions: { input: path.join(root, 'web/tests/template-fields/index.html') } } }
if (!developmentOnly) await build(options)
const development = await createServer(options)
const production = developmentOnly ? null : await preview({ ...options, preview: { host: '127.0.0.1', port: 18093, strictPort: true, open: false } })
try {
  await development.listen()
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const checks = []
    for (const [mode, origin] of developmentOnly ? [['development', 'http://127.0.0.1:18092']] : [['development', 'http://127.0.0.1:18092'], ['production', 'http://127.0.0.1:18093']]) {
      const page = await browser.newPage()
      page.setDefaultNavigationTimeout(120000)
      page.setDefaultTimeout(60000)
      try {
        const errors = []; const writes = []
        page.on('pageerror', error => errors.push(error.message))
        page.on('request', request => { if (!['GET', 'HEAD'].includes(request.method())) writes.push(request.method() + ' ' + new URL(request.url()).pathname) })
        await page.goto(origin + '/tests/template-fields/index.html')
        await page.getByRole('heading', { name: '创建模板草稿', exact: true }).waitFor()
        await page.getByRole('button', { name: '校验完整定义', exact: true }).waitFor()
        await page.getByRole('button', { name: '编辑字段定义', exact: true }).waitFor({ timeout: 5000 })
        const basis = [{ version_id: randomUUID(), content_sha256: 'a'.repeat(64) }]
        const nodeId = randomUUID()
        const condition = { mode: 'RULE', rule_revision: basis[0], engine_revision: 'SIMULATION_ENGINE_V1', input_keys: ['SIMULATION.attribute'] }
        const evidence = { mode: 'DIRECT_REFERENCE', authority_source_types: ['SIMULATION'], verification_rule_revisions: basis,
          calculation_rule_revision: null, input_field_ids: [], require_original_locator: true, require_human_verification: true }
        const types = ['TEXT', 'LONG_TEXT', 'DATE', 'NUMBER', 'ENUM', 'MULTI_SELECT', 'RELATION', 'TABLE', 'IMAGE', 'FORMULA', 'FILE']
        const fields = types.map((type, index) => ({ field_id: randomUUID(), node_id: nodeId, key: 'SIMULATION.' + type,
          name: 'SIMULATION ' + type, description: 'SIMULATION 字段说明', value_type: type,
          constraints: { max_length: 1024, precision: 8, minimum: '9007199254740993.12345678', maximum: '9007199254740994.87654321', unit: 'SIMULATION_UNIT',
            range_basis: basis, enum_options: [{ value: 'SIMULATION_OPTION', label: 'SIMULATION 选项', basis_refs: basis }], nullable_reasons: ['SIMULATION_UNKNOWN'] },
          condition, required: true, evidence_rule: evidence,
          output_mapping: { node_id: nodeId, repeat_id: null, column_key: null, internal_only: false, ordinal: index + 1 },
          repeat_id: null, attachment_requirement_id: null, basis_refs: basis }))
        const rawFields = page.getByRole('textbox', { name: '全部字段完整定义（JSON）', exact: true })
        await rawFields.fill(JSON.stringify(fields, null, 2))
        checks.push({ mode, name: 'structured_template_field_entry', result: 'PASS' })
        await page.getByRole('button', { name: '编辑字段定义', exact: true }).click()
        const dialog = page.getByRole('dialog', { name: '字段定义编辑', exact: true })
        assert.equal(await dialog.locator('tr.el-table__row').count(), 11)
        await dialog.locator('tr.el-table__row').filter({ hasText: fields[3].field_id }).getByRole('button', { name: '编辑本项定义', exact: true }).click()
        await dialog.getByRole('combobox', { name: '字段类型', exact: true }).focus()
        await dialog.getByRole('combobox', { name: '字段类型', exact: true }).press('ArrowDown')
        for (const label of ['单行文本', '多行文本', '日期', '精确数值', '单项枚举', '多项选择', '关系', '表格', '图片', '公式', '文件']) {
          await page.getByRole('option', { name: label, exact: true }).waitFor()
        }
        await page.getByRole('option', { name: '精确数值', exact: true }).click()
        const originalConstraints = JSON.parse(await dialog.getByRole('textbox', { name: '完整字段限制（JSON）', exact: true }).inputValue())
        assert.deepEqual(originalConstraints, fields[3].constraints)
        await dialog.getByRole('textbox', { name: '字段说明', exact: true }).fill('SIMULATION 新字段说明')
        await dialog.getByRole('button', { name: '确认当前定义', exact: true }).click()
        await dialog.getByRole('button', { name: '采用本次定义修改', exact: true }).click()
        fields[3].description = 'SIMULATION 新字段说明'
        assert.deepEqual(JSON.parse(await rawFields.inputValue()), fields)
        checks.push({ mode, name: 'eleven_types_complete_definition_and_exact_decimal_preserved', result: 'PASS' })
        await page.getByRole('button', { name: '编辑字段定义', exact: true }).click()
        await dialog.locator('tr.el-table__row').filter({ hasText: fields[0].field_id }).getByRole('button', { name: '编辑本项定义', exact: true }).click()
        await dialog.getByRole('textbox', { name: '字段名称', exact: true }).fill('SIMULATION 尚未采用')
        await dialog.getByRole('button', { name: '放弃本次定义修改', exact: true }).click()
        assert.deepEqual(JSON.parse(await rawFields.inputValue()), fields)
        await page.getByRole('button', { name: '编辑字段定义', exact: true }).click()
        await dialog.locator('tr.el-table__row').filter({ hasText: fields[0].field_id }).getByRole('button', { name: '编辑本项定义', exact: true }).click()
        const requiredFields = dialog.getByRole('radio', { name: '确认需要', exact: true })
        assert.equal(await requiredFields.isChecked(), true)
        await dialog.getByRole('textbox', { name: '完整适用条件（JSON）', exact: true }).fill('{')
        await dialog.getByRole('button', { name: '确认当前定义', exact: true }).click()
        assert.equal(await dialog.locator('[data-template-definition-form]').count(), 1)
        assert.ok(await dialog.locator('.el-form-item.is-error').count() > 0)
        await dialog.getByRole('button', { name: '放弃当前定义编辑', exact: true }).click()
        await dialog.locator('tr.el-table__row').filter({ hasText: fields[0].field_id }).getByRole('button', { name: '编辑本项定义', exact: true }).click()
        assert.equal(await dialog.getByRole('button', { name: '移除当前定义', exact: true }).isEnabled(), false)
        await dialog.getByText('确认移除当前定义', { exact: true }).click()
        assert.equal(await dialog.getByRole('checkbox', { name: '确认移除当前定义', exact: true }).isChecked(), true)
        await dialog.getByRole('button', { name: '移除当前定义', exact: true }).click()
        await dialog.getByRole('button', { name: '采用本次定义修改', exact: true }).click()
        fields.shift(); assert.deepEqual(JSON.parse(await rawFields.inputValue()), fields)
        checks.push({ mode, name: 'cancel_invalid_json_and_explicit_removal', result: 'PASS' })
        const definitions = [
          { button: '编辑目录定义', title: '目录定义编辑', input: '完整目录完整定义（JSON）', content: [{ node_id: nodeId, parent_id: null, official_number: 'SIMULATION.1', title: 'SIMULATION 目录', ordinal: 1,
            condition, required: true, content_mode: 'NARRATIVE', evidence_rule: evidence, basis_refs: basis, nature: 'ENTERPRISE_SUPPLEMENT', content_requirements: ['SIMULATION 内容要求'] }] },
          { button: '编辑重复表格定义', title: '重复表格定义编辑', input: '重复表格完整定义（JSON）', content: [{ repeat_id: randomUUID(), node_id: nodeId, name: 'SIMULATION 重复表格', field_ids: [fields[0].field_id],
            min_count: '9007199254740993', max_count: '18446744073709551615', count_basis: basis, row_id_required: true }] },
          { button: '编辑附件定义', title: '附件定义编辑', input: '证据附件完整定义（JSON）', content: [{ attachment_requirement_id: randomUUID(), node_id: nodeId, name: 'SIMULATION 附件', kind: 'RAW_DATA', mode: 'AUTHORIZED_ORIGINAL_ONLY',
            condition, required: true, min_count: '1', max_count: null, count_basis: basis, allowed_media_types: ['text/csv'], evidence_rule: evidence, ordinal: 1 }] },
          { button: '编辑跨文档规则定义', title: '跨文档规则定义编辑', input: '跨文档关系完整定义（JSON）', content: [{ rule_id: randomUUID(), rule_revision: basis[0], field_ids: [fields[0].field_id],
            related_document_types: ['PRODUCT_TECHNICAL_REQUIREMENTS', 'INSTRUCTIONS_FOR_USE'], severity: 'CRITICAL' }] }
        ]
        for (const definition of definitions) {
          const input = page.getByRole('textbox', { name: definition.input, exact: true })
          await input.fill(JSON.stringify(definition.content, null, 2))
          await page.getByRole('button', { name: definition.button, exact: true }).click()
          const editor = page.getByRole('dialog', { name: definition.title, exact: true })
          await editor.getByRole('button', { name: '编辑本项定义', exact: true }).click()
          await editor.getByRole('button', { name: '确认当前定义', exact: true }).click()
          await editor.getByRole('button', { name: '采用本次定义修改', exact: true }).click()
          assert.deepEqual(JSON.parse(await input.inputValue()), definition.content)
        }
        checks.push({ mode, name: 'directory_repeat_attachment_and_cross_document_rules_round_trip', result: 'PASS' })
        await page.getByRole('button', { name: '编辑字段定义', exact: true }).click()
        await page.evaluate(() => window.dispatchEvent(new Event('medical-context-invalidated')))
        await dialog.waitFor({ state: 'hidden' })
        await rawFields.fill('[]')
        await page.getByRole('button', { name: '编辑字段定义', exact: true }).click()
        assert.equal(await dialog.locator('tr.el-table__row').count(), 0)
        await dialog.getByRole('button', { name: '放弃本次定义修改', exact: true }).click()
        assert.equal(await rawFields.inputValue(), '[]')
        assert.equal(await page.evaluate(() => Object.keys(sessionStorage).some(key => sessionStorage.getItem(key).includes('SIMULATION 新字段说明'))), false)
        checks.push({ mode, name: 'invalidated_context_clears_editor_and_new_input_remains_without_browser_storage', result: 'PASS' })
        assert.deepEqual(errors, []); assert.deepEqual(writes, [])
      } finally { await page.close() }
    }
    await writeFile(path.join(output, 'checks.json'), JSON.stringify({ scope: 'TEMPLATE_FIELDS', data_kind: 'SIMULATION', visual: 'NOT_RUN', checks }, null, 2) + '\n')
    console.log(JSON.stringify({ result: 'PASS', checks: checks.length }))
  } finally { await browser.close() }
} finally {
  await development.close()
  if (production) await new Promise((resolve, reject) => production.httpServer.close(error => error ? reject(error) : resolve()))
}

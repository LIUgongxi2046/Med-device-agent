import assert from 'node:assert/strict'
import { readdirSync, readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { parse as parseTemplate } from '@vue/compiler-dom'
import Papa from 'papaparse'

const root = new URL('../src/views/medical/', import.meta.url)
const delivery = new URL('../../ui-delivery/workspace-experience-20261021/', import.meta.url)
const rows = []
const counts = []
let modelSections = 0
function tags(node, name) {
  return (node.tag === name ? 1 : 0) + (node.children ?? []).reduce((count, child) => count + tags(child, name), 0)
}
function fieldSections(node) {
  return (node.props?.some(property => property.name === 'data-model-field-section') ? 1 : 0)
    + (node.children ?? []).reduce((count, child) => count + fieldSections(child), 0)
}
for (const name of readdirSync(root).filter(name => name.endsWith('.vue')).sort()) {
  const filename = fileURLToPath(new URL(name, root))
  const parsed = parse(readFileSync(filename, 'utf8'), { filename })
  assert.deepEqual(parsed.errors, [], filename)
  if (!parsed.descriptor.template) continue
  const tree = parseTemplate(parsed.descriptor.template.content)
  const count = tags(tree, 'el-form')
  const fields = tags(tree, 'el-form-item')
  if (name === 'model-configuration-fields.vue') modelSections = fieldSections(tree)
  if (!count && !fields) continue
  const script = compileScript(parsed.descriptor, { id: name })
  const template = compileTemplate({ source: parsed.descriptor.template.content, filename, id: name, compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [], filename)
  rows.push({ source_id: 'medical/' + name, screen_id: name.replace('.vue', ''), name: name.replace('.vue', ''),
    source_type: 'EXPLICIT', status: 'OBSERVED', artifact_path: '../../web/src/views/medical/' + name,
    required_states: '字段/帮助/错误/禁用/窄视口/主题', notes: '共享表单规范；源码编译通过；实际交互范围见ui-audit.md' })
  counts.push({ file: name, forms: count, field_items: fields })
}
assert.ok(rows.length > 60, '需要完整盘点当前业务表单')
assert.equal(modelSections, 5, '模型配置需要完整的五个字段分区')
mkdirSync(delivery, { recursive: true })
writeFileSync(new URL('form-source-map.csv', delivery), Papa.unparse(rows) + '\n')
const result = { verified_at: new Date().toISOString(), source_files: rows.length, form_count: counts.reduce((total, row) => total + row.forms, 0),
  compiled_files: counts, model_field_sections: modelSections, evidence_kind: 'SOURCE_COMPILE', interaction_verified: false, screenshots: false }
writeFileSync(new URL('../../.implementation-work/workspace-form-inventory.json', import.meta.url), JSON.stringify(result, null, 2))
console.log(JSON.stringify({ source_files: result.source_files, form_count: result.form_count, compiled: true }))

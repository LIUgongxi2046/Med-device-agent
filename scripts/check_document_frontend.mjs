import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createHash, randomUUID } from 'node:crypto'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

globalThis.window = new EventTarget()

const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: bodyModule } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/document-body.ts', import.meta.url)), { root, mode: 'medical' })
const { module: editing } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/document-editing.ts', import.meta.url)), { root, mode: 'medical' })
const { module: draftCache } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/document-session-drafts.ts', import.meta.url)), { root, mode: 'medical' })
const { module: comparison } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/document-comparison.ts', import.meta.url)), { root, mode: 'medical' })
const { module: tables } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/document-tables.ts', import.meta.url)), { root, mode: 'medical' })
const original = await readFile(new URL('../backend-design/接口补充设计.md', import.meta.url), 'utf8')
const documentId = randomUUID(), paragraphId = randomUUID(), textId = randomUUID()
// 工程文档作为实际编辑内容；新建身份不代表专业模板或批准记录。
const body = { schema_version: '1.0.0', template_version: randomUUID(), nodes: [
  { node_id: documentId, parent_id: null, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'DOCUMENT', title: '' } },
  { node_id: paragraphId, parent_id: documentId, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'PARAGRAPH', title: '' } },
  { node_id: textId, parent_id: paragraphId, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'TEXT', text: original, marks: [] } }
], fields: [], evidence_links: [], fact_usages: [] }
const encoded = await bodyModule.encodeDocumentBody(body)
const bytes = new Uint8Array(await encoded.blob.arrayBuffer())
assert.deepEqual(encoded.body, body)
assert.equal(encoded.body.nodes[2].content.text, original)
assert.equal(encoded.sha256, createHash('sha256').update(bytes).digest('hex'))
assert.equal(encoded.sizeBytes, String(bytes.length))
assert.equal(new TextDecoder('utf-8', { fatal: true }).decode(bytes), encoded.text)
const untouchedText = encoded.body.nodes[2].content.text
body.nodes[2].content.text += '\n'
assert.equal(encoded.body.nodes[2].content.text, untouchedText)
assert.ok(Object.isFrozen(encoded.body) && Object.isFrozen(encoded.body.nodes[2].content))
const malformed = structuredClone(body)
malformed.nodes[2].content.text += String.fromCharCode(0xd800)
await assert.rejects(() => bodyModule.encodeDocumentBody(malformed), error => error.code === 'SCHEMA_INVALID')
const cyclic = structuredClone(body)
cyclic.nodes[2].content.cycle = cyclic
await assert.rejects(() => bodyModule.encodeDocumentBody(cyclic), error => error.code === 'SCHEMA_INVALID')
// 两次编辑使用同一实际文档中的原始段落，核验保存期间继续输入的状态。
const buffer = new bodyModule.DocumentEditBuffer(encoded.body, 100)
assert.equal(buffer.dirty, false)
const firstEdit = structuredClone(encoded.body)
firstEdit.nodes[2].content.text += original.slice(0, original.indexOf('\n') + 1)
buffer.edit(firstEdit)
const firstCapture = buffer.capture()
assert.equal(buffer.isCurrentCapture(firstCapture), true)
assert.equal(buffer.dirty, true)
assert.equal(buffer.saving, true)
const continued = structuredClone(firstEdit)
continued.nodes[2].content.text += original
buffer.edit(continued)
assert.equal(buffer.capture(), firstCapture)
buffer.confirm(firstCapture, firstEdit)
assert.equal(buffer.dirty, true)
assert.equal(buffer.saving, false)
assert.equal(buffer.isCurrentCapture(firstCapture), false)
assert.deepEqual(buffer.body, continued)
const secondCapture = buffer.capture()
assert.ok(secondCapture.generation > firstCapture.generation)
buffer.confirm(secondCapture, continued)
assert.equal(buffer.dirty, false)
const retained = structuredClone(continued)
retained.nodes[2].content.text += original
buffer.edit(retained)
buffer.capture()
buffer.replaceBaseline(continued, retained)
assert.equal(buffer.saving, false)
assert.equal(buffer.dirty, true)
assert.deepEqual(buffer.body, retained)
const unissued = buffer.capture()
buffer.releaseUnissued(unissued)
assert.equal(buffer.saving, false)
assert.deepEqual(buffer.body, retained)
buffer.invalidate()
assert.equal(buffer.dirty, false)
assert.equal(buffer.saving, false)
assert.throws(() => buffer.body, error => error.code === 'ACTION_STALE')
const segmented = editing.editDocumentText(encoded.body, textId, [
  { kind: 'TEXT', text: 'SIMULATION 原始文本🙂', marks: ['BOLD'] },
  { kind: 'TEXT', text: 'SIMULATION 后续文本', marks: ['ITALIC', 'UNDERLINE'] }
])
assert.equal(segmented.nodes[2].node_id, textId)
assert.equal(segmented.nodes[3].parent_id, paragraphId)
assert.equal(segmented.nodes[3].ordinal, 1)
assert.equal(segmented.nodes[2].content.text, 'SIMULATION 原始文本🙂')
const fieldId = randomUUID(), definition = randomUUID()
const withField = structuredClone(encoded.body)
withField.nodes[2].field_ids = [fieldId]
withField.fields = [{ field_id: fieldId, node_id: textId, field_definition_id: definition,
  original_value: 'SIMULATION 12.500 mm', decimal_value: '12.500', unit: 'mm', model: null, condition: null, evidence_link_ids: [] }]
const confirmed = editing.confirmDocumentField(withField, { ...withField.fields[0], decimal_value: '-0.000000000001',
  model: 'SIMULATION 型号', condition: 'SIMULATION 条件', node_id: randomUUID(), field_definition_id: randomUUID() })
assert.equal(confirmed.fields[0].field_definition_id, definition)
assert.equal(confirmed.fields[0].node_id, textId)
assert.equal(confirmed.fields[0].decimal_value, '-0.000000000001')
assert.equal(confirmed.fields[0].original_value, 'SIMULATION 12.500 mm')
assert.throws(() => editing.confirmDocumentField(withField, { ...withField.fields[0], decimal_value: '-' }), error => error.code === 'SCHEMA_INVALID')
const session = { user: { user_id: randomUUID() }, current_context: { tenant_id: randomUUID(), context_revision: '1', auth_epoch: '1', governance_epoch: '1' } }
const key = draftCache.documentDraftKey(session, randomUUID(), documentId)
const draft = new bodyModule.DocumentEditBuffer(encoded.body, 100); draft.edit(firstEdit)
const reference = { artifact_id: randomUUID(), sha256: encoded.sha256, schema_ref: bodyModule.documentReference + 'DocumentBody',
  coverage: { scope_sha256: encoded.sha256, delivered_sha256: encoded.sha256, complete: true, next_cursor: null } }
const snapshot = { document_id: documentId, project_id: randomUUID(), document_revision: randomUUID(), revision: '1',
  plan_revision: randomUUID(), template_version: encoded.body.template_version, body: reference, fields: reference, evidence_links: reference,
  body_schema_revision: randomUUID(), check_ids: [], review_ids: [], signature_ids: [], invalidation_reasons: [], issue_watermark: '0',
  evidence_watermark: '0', auth_epoch: '1', governance_epoch: '1', allowed_actions: ['document.edit'], stream_cursors: [] }
const sessionDraft = { snapshot, baseline: encoded.body, draft, dependency: randomUUID(), pending: null, reason: 'SIMULATION 会话草稿', fieldInputs: {} }
assert.equal(draftCache.retainDocumentDraft(key, sessionDraft), true)
assert.equal(draftCache.resumeDocumentDraft(key), sessionDraft)
assert.equal(draftCache.resumeDocumentDraft(key), null)
draftCache.retainDocumentDraft(key, sessionDraft)
window.dispatchEvent(new Event('medical-context-invalidated'))
assert.equal(draftCache.retainDocumentDraft(key, sessionDraft), false)
assert.throws(() => draft.body, error => error.code === 'ACTION_STALE')
assert.equal(draftCache.resumeDocumentDraft(key), null)
const server = structuredClone(withField), local = structuredClone(withField)
server.nodes[2].content.text = 'SIMULATION 服务器修改'
local.nodes[2].content.text = 'SIMULATION 本地修改'
server.fields[0].condition = 'SIMULATION 服务器新增条件'
const compared = comparison.compareDocumentBodies(withField, server, local)
const conflict = compared.rows.find(row => row.kind === 'NODE' && row.identity === textId)
assert.equal(conflict.choice, null)
assert.throws(() => comparison.mergeDocumentComparison(compared), error => error.code === 'ACTION_STALE')
conflict.choice = 'LOCAL'
const merged = comparison.mergeDocumentComparison(compared)
assert.equal(merged.nodes[2].content.text, 'SIMULATION 本地修改')
assert.equal(merged.fields[0].condition, 'SIMULATION 服务器新增条件')
assert.equal(merged.fields[0].field_id, fieldId)
assert.equal(merged.nodes[2].node_id, textId)
assert.equal(merged.fields[0].decimal_value, '12.500')
const history = new bodyModule.DocumentEditBuffer(withField, 100)
history.edit(confirmed)
const capturedHistory = history.capture()
history.undo(); assert.deepEqual(history.body, withField); assert.equal(history.canRedo, true)
history.confirm(capturedHistory, confirmed); assert.equal(history.dirty, true)
history.redo(); assert.deepEqual(history.body, confirmed); assert.equal(history.dirty, false)
history.undo(); history.edit(local); assert.equal(history.canRedo, false)
history.replaceBaseline(server, merged); history.undo(); assert.deepEqual(history.body, server)
history.redo(); assert.deepEqual(history.body, merged)
history.invalidate(); assert.equal(history.canUndo, false); assert.equal(history.canRedo, false)
assert.throws(() => history.undo(), error => error.code === 'ACTION_STALE')
let structured = editing.insertDocumentContent(withField, documentId, tables.newDocumentTable())
const tableId = structured.nodes.at(-1).node_id
structured = tables.appendTableDimension(structured, tableId, 'COLUMN')
structured = tables.appendTableDimension(structured, tableId, 'ROW')
const table = value => value.nodes.find(node => node.node_id === tableId).content
const firstCell = table(structured).cells[0].cell_id
const allCells = table(structured).cells.map(cell => cell.cell_id)
assert.throws(() => tables.mergeTableCells(structured, tableId, [allCells[0], allCells[3]]), error => error.code === 'ACTION_STALE')
structured = tables.appendTableText(structured, tableId, firstCell)
const tableTextId = table(structured).cells[0].node_ids[0]
structured = editing.editDocumentText(structured, tableTextId, [
  { kind: 'TEXT', text: 'SIMULATION 单元格内容🙂', marks: ['BOLD'] }, { kind: 'TEXT', text: 'SIMULATION 后续格式', marks: ['ITALIC'] }
])
assert.equal(table(structured).cells[0].node_ids.length, 2)
assert.equal(table(structured).cells[0].node_ids[0], tableTextId)
assert.throws(() => tables.removeEmptyTableDimension(structured, tableId, 'ROW', table(structured).row_ids[0]), error => error.code === 'ACTION_STALE')
structured = tables.mergeTableCells(structured, tableId, allCells)
assert.equal(table(structured).cells[0].cell_id, firstCell)
assert.equal(table(structured).cells[0].row_span, 2)
assert.equal(table(structured).cells[0].column_span, 2)
const tableDiff = comparison.compareDocumentBodies(withField, structured, structured).rows.find(row => row.identity === tableId)
assert.ok(comparison.comparisonText(tableDiff.server).includes(firstCell))
assert.ok(comparison.comparisonText(tableDiff.server).includes(tableTextId))
structured = tables.separateTableCell(structured, tableId, firstCell)
assert.equal(table(structured).cells.length, 4)
assert.equal(table(structured).cells[0].cell_id, firstCell)
structured = tables.removeEmptyTableDimension(structured, tableId, 'COLUMN', table(structured).column_ids[1])
structured = tables.appendTableDimension(structured, tableId, 'COLUMN')
structured = editing.insertDocumentContent(structured, paragraphId, { kind: 'FORMULA', latex: 'SIMULATION \\frac{a}{b}', description: 'SIMULATION 原始公式', rendered_artifact_id: null })
const formulaId = structured.nodes.at(-1).node_id
structured = editing.editDocumentContent(structured, formulaId, { kind: 'FORMULA', latex: 'SIMULATION a^2+b^2', description: 'SIMULATION 编辑公式', rendered_artifact_id: null })
assert.equal(structured.fields[0].field_id, fieldId)
assert.equal(structured.fields[0].decimal_value, '12.500')
assert.equal(structured.nodes.find(node => node.node_id === formulaId).content.latex, 'SIMULATION a^2+b^2')
const structureEncoded = await bodyModule.encodeDocumentBody(structured)
await writeFile(new URL('../.implementation-work/document-frontend-structure.json', import.meta.url), JSON.stringify({ data_kind: 'SIMULATION',
  body: structureEncoded.body, body_sha256: structureEncoded.sha256, table_node_id: tableId, first_cell_id: firstCell,
  text_node_id: tableTextId, field_id: fieldId, formula_node_id: formulaId, formal_customer_acceptance: false }) + '\n', { mode: 0o600 })
process.stdout.write(JSON.stringify({ document_body_encoding_verified: true, immutable_original_bytes_verified: true,
  edit_generation_verified: true, explicit_conflict_input_retained_verified: true, editor_node_identity_verified: true,
  exact_decimal_field_identity_verified: true, session_draft_resume_and_context_invalidation_verified: true,
  explicit_three_version_comparison_verified: true, table_merge_separation_and_text_ownership_verified: true,
  complete_comparison_structure_verified: true, undo_during_save_and_redo_verified: true, input_data_kind: 'SIMULATION',
  professional_document_save_verified: false, visual_verified: false }) + '\n')

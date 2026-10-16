<template>
  <main>
    <h1>SIMULATION 正文选区检查</h1>
    <el-button @click="active = false">清理正文上下文</el-button>
    <el-button @click="changeRevision">更换正文版本</el-button>
    <el-button @click="dirty = !dirty">更换编辑状态</el-button>
    <document-body-reader :body="body" :document-revision="revision" :active="active" :dirty="dirty" />
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { DocumentBody } from '../../../contracts/typescript/src/interfaces'
import DocumentBodyReader from '../../src/views/medical/document-body-reader.vue'
import * as selectionMethods from '../../src/api/medical/document-selection'

const active = ref(true); const revision = ref(crypto.randomUUID()); const dirty = ref(false)
function changeRevision(): void { revision.value = crypto.randomUUID() }
const root = crypto.randomUUID(); const first = crypto.randomUUID(); const last = crypto.randomUUID()
const rowA = crypto.randomUUID(); const rowB = crypto.randomUUID(); const columnA = crypto.randomUUID(); const columnB = crypto.randomUUID()
const body: DocumentBody = { schema_version: '1.0.0', template_version: crypto.randomUUID(), fields: [], evidence_links: [], fact_usages: [], nodes: [
  { node_id: root, parent_id: null, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'SECTION', title: 'SIMULATION 章节' } },
  { node_id: first, parent_id: root, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'TEXT', text: '甲😀e\u0301\n乙', marks: ['BOLD', 'ITALIC', 'UNDERLINE', 'SUPERSCRIPT'] } },
  { node_id: last, parent_id: root, ordinal: 1, field_ids: [], evidence_link_ids: [], content: { kind: 'TEXT', text: '丙🧪丁', marks: ['SUBSCRIPT'] } },
  { node_id: crypto.randomUUID(), parent_id: root, ordinal: 2, field_ids: [], evidence_link_ids: [], content: { kind: 'IMAGE', artifact_id: crypto.randomUUID(), sha256: 'a'.repeat(64), alt_text: 'SIMULATION 图片节点', caption: 'SIMULATION 图片说明' } },
  { node_id: crypto.randomUUID(), parent_id: root, ordinal: 3, field_ids: [], evidence_link_ids: [], content: { kind: 'TABLE', caption: 'SIMULATION 合并表格', row_ids: [rowA, rowB], column_ids: [columnA, columnB], cells: [
    { cell_id: crypto.randomUUID(), row_id: rowA, column_id: columnA, row_span: 2, column_span: 1, node_ids: [] },
    { cell_id: crypto.randomUUID(), row_id: rowA, column_id: columnB, row_span: 1, column_span: 1, node_ids: [] },
    { cell_id: crypto.randomUUID(), row_id: rowB, column_id: columnB, row_span: 1, column_span: 1, node_ids: [] }
  ] } }
] }
declare global { interface Window { documentSelectionChecks: typeof selectionMethods & { body: DocumentBody } } }
window.documentSelectionChecks = { ...selectionMethods, body }
</script>

<template>
  <article :data-node-id="node.node_id" :aria-label="labels[node.content.kind]" :class="{ 'selection-text-node': selectionMode && node.content.kind === 'TEXT' }" tabindex="-1">
    <template v-if="node.content.kind === 'DOCUMENT' || node.content.kind === 'SECTION' || node.content.kind === 'PARAGRAPH'">
      <el-input :model-value="node.content.title" :readonly="readonly" aria-label="目录或段落标题" @update:model-value="title" />
      <div v-if="!readonly">
        <el-button @click="add('TEXT')">新增文本</el-button><el-button @click="add('PARAGRAPH')">新增段落</el-button>
        <el-button @click="add('TABLE')">新增表格</el-button><el-button @click="add('FORMULA')">新增公式</el-button>
        <el-select v-if="images.length > 0" :model-value="null" placeholder="使用已有图片版本" @change="reuseImage">
          <el-option v-for="image in images" :key="image.node_id" :label="image.content.kind === 'IMAGE' ? image.content.alt_text : image.node_id" :value="image.node_id" />
        </el-select>
      </div>
      <document-node-editor v-for="child in children" :key="child.node_id" :node="child" :body="body" :readonly="readonly" :selection-mode="selectionMode" @changed="emit('changed', $event)" @selected="emit('selected', $event)">
        <template #chapter-end="scope: { node: Node }"><slot name="chapter-end" v-bind="scope" /></template>
      </document-node-editor>
    </template>
    <span v-else-if="node.content.kind === 'TEXT' && selectionMode" :data-selection-text="node.node_id" :style="textStyle(node.content)" class="selection-text">{{ node.content.text }}</span>
    <document-text-editor v-else-if="node.content.kind === 'TEXT'" :content="node.content" :readonly="readonly" @changed="text" />
    <section v-else-if="node.content.kind === 'IMAGE' && selectionMode" aria-label="固定图片节点">
      <p>{{ node.content.alt_text }} · {{ node.content.caption }}</p>
      <p>固定图片版本：{{ node.content.artifact_id }}；内容摘要：{{ node.content.sha256 }}</p>
      <el-button @click="emit('selected', { kind: 'IMAGE', node_id: node.node_id })">选择当前图片节点</el-button>
    </section>
    <el-form v-else-if="node.content.kind === 'IMAGE'" :disabled="readonly" label-position="top">
      <p>固定图片版本：{{ node.content.artifact_id }}；内容摘要：{{ node.content.sha256 }}</p>
      <el-form-item label="图片替代文字"><el-input :model-value="node.content.alt_text" @update:model-value="image('alt_text', $event)" /></el-form-item>
      <el-form-item label="图片说明"><el-input :model-value="node.content.caption" type="textarea" @update:model-value="image('caption', $event)" /></el-form-item>
    </el-form>
    <el-form v-else-if="node.content.kind === 'FORMULA'" :disabled="readonly" label-position="top">
      <div v-if="formulaPreview" class="formula-preview" aria-label="公式预览" v-html="formulaPreview" />
      <el-button v-if="!readonly" :disabled="!node.content.latex" @click="previewFormula">预览当前公式</el-button>
      <el-form-item label="LaTeX 公式"><el-input :model-value="node.content.latex" type="textarea" @update:model-value="formula('latex', $event)" /></el-form-item>
      <el-form-item label="公式说明"><el-input :model-value="node.content.description" type="textarea" @update:model-value="formula('description', $event)" /></el-form-item>
      <p v-if="node.content.rendered_artifact_id">固定公式图片版本：{{ node.content.rendered_artifact_id }}</p>
    </el-form>
    <section v-else-if="node.content.kind === 'TABLE'" aria-label="结构化表格">
      <el-input :model-value="node.content.caption" :readonly="readonly" aria-label="表格说明" @update:model-value="caption" />
      <el-table :data="tableRows" :span-method="span" border row-key="row_id">
        <el-table-column v-for="column in node.content.column_ids" :key="column" :label="'列 ' + (node.content.column_ids.indexOf(column) + 1)">
          <template #default="{ row }">
            <template v-if="anchor(row.row_id, column)">
              <el-checkbox v-if="!readonly || selectionMode" :data-selection-cell="cell(row.row_id, column).cell_id" :model-value="selected.includes(cell(row.row_id, column).cell_id)" @change="select(cell(row.row_id, column).cell_id, $event)">选择单元格</el-checkbox>
              <document-node-editor v-for="id in cell(row.row_id, column).node_ids" :key="id" :node="child(id)" :body="body" :readonly="readonly" :selection-mode="selectionMode" @changed="emit('changed', $event)" @selected="emit('selected', $event)">
                <template #chapter-end="scope: { node: Node }"><slot name="chapter-end" v-bind="scope" /></template>
              </document-node-editor>
              <el-button v-if="!readonly" @click="apply(appendTableText(body, node.node_id, cell(row.row_id, column).cell_id))">新增单元格文本</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="selectionMode" :disabled="selected.length === 0" @click="emit('selected', { kind: 'TABLE', node_id: node.node_id, cell_ids: [...selected] })">确认表格选区</el-button>
      <div v-if="!readonly">
        <el-button @click="apply(appendTableDimension(body, node.node_id, 'ROW'))">新增行</el-button>
        <el-button @click="apply(appendTableDimension(body, node.node_id, 'COLUMN'))">新增列</el-button>
        <el-button :disabled="selected.length < 2" @click="apply(mergeTableCells(body, node.node_id, selected))">合并选择单元格</el-button>
        <el-button :disabled="selected.length !== 1" @click="separate">分离选择单元格</el-button>
        <el-select :model-value="null" placeholder="移除空行" @change="remove('ROW', $event)">
          <el-option v-for="(id, index) in node.content.row_ids" :key="id" :label="'行 ' + (index + 1)" :value="id" />
        </el-select>
        <el-select :model-value="null" placeholder="移除空列" @change="remove('COLUMN', $event)">
          <el-option v-for="(id, index) in node.content.column_ids" :key="id" :label="'列 ' + (index + 1)" :value="id" />
        </el-select>
      </div>
    </section>
    <slot v-if="node.content.kind === 'SECTION' || node.content.kind === 'DOCUMENT'" name="chapter-end" :node="node" />
  </article>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CSSProperties } from 'vue'
import type { Cell, DocumentBody, Node, TextContent } from '../../../../contracts/typescript/src/interfaces'
import { editDocumentContent, editDocumentText, insertDocumentContent } from '@/api/medical/document-editing'
import { appendTableDimension, appendTableText, mergeTableCells, newDocumentTable, removeEmptyTableDimension, separateTableCell, tablePositions } from '@/api/medical/document-tables'
import { fail } from '@/api/medical/protocol'
import { renderDocumentFormula } from '@/api/medical/content-rendering'
import type { SelectionRequest } from '@/api/medical/document-selection'
import 'katex/dist/katex.min.css'
import DocumentTextEditor from './document-text-editor.vue'

defineOptions({ name: 'DocumentNodeEditor' })
defineSlots<{ 'chapter-end': (props: { node: Node }) => unknown }>()
const props = withDefaults(defineProps<{ node: Node; body: DocumentBody; readonly: boolean; selectionMode?: boolean }>(), { selectionMode: false })
const emit = defineEmits<{ changed: [body: DocumentBody]; selected: [request: SelectionRequest] }>()
const labels = { DOCUMENT: '文稿目录', SECTION: '章节内容', PARAGRAPH: '段落内容', TEXT: '文本内容', IMAGE: '图片内容', TABLE: '表格内容', FORMULA: '公式内容' }
const children = computed(() => props.body.nodes.filter(value => value.parent_id === props.node.node_id).sort((a, b) => a.ordinal - b.ordinal))
const images = computed(() => props.body.nodes.filter(value => value.content.kind === 'IMAGE'))
const positions = computed(() => props.node.content.kind === 'TABLE' ? tablePositions(props.node.content) : [])
const tableRows = computed(() => props.node.content.kind === 'TABLE' ? props.node.content.row_ids.map(row_id => ({ row_id })) : [])
const selected = ref<string[]>([])
const formulaHtml = ref('')
const formulaPreview = computed(() => props.readonly && props.node.content.kind === 'FORMULA' && props.node.content.latex
  ? renderDocumentFormula(props.node.content.latex) : formulaHtml.value)
function textStyle(content: TextContent): CSSProperties {
  const style: CSSProperties = {}
  if (content.marks.includes('BOLD')) style.fontWeight = 'bold'
  if (content.marks.includes('ITALIC')) style.fontStyle = 'italic'
  if (content.marks.includes('UNDERLINE')) style.textDecoration = 'underline'
  if (content.marks.includes('SUPERSCRIPT')) { style.verticalAlign = 'super'; style.fontSize = 'smaller' }
  if (content.marks.includes('SUBSCRIPT')) { style.verticalAlign = 'sub'; style.fontSize = 'smaller' }
  return style
}
function previewFormula(): void {
  if (props.node.content.kind !== 'FORMULA') fail('ACTION_STALE', '当前节点没有公式')
  formulaHtml.value = renderDocumentFormula(props.node.content.latex)
}
function apply(body: DocumentBody): void { if (props.readonly) fail('ACTION_STALE', '当前正文无法编辑'); selected.value = []; emit('changed', body) }
function content(value: Node['content']): void { apply(editDocumentContent(props.body, props.node.node_id, value)) }
function title(value: string): void {
  const previous = props.node.content
  if (previous.kind !== 'DOCUMENT' && previous.kind !== 'SECTION' && previous.kind !== 'PARAGRAPH') fail('SCHEMA_INVALID', '当前内容没有标题')
  content({ ...previous, title: value })
}
function text(value: TextContent[]): void { apply(editDocumentText(props.body, props.node.node_id, value)) }
function image(key: 'alt_text' | 'caption', value: string): void {
  if (props.node.content.kind !== 'IMAGE') fail('SCHEMA_INVALID', '当前内容没有图片')
  content({ ...props.node.content, [key]: value })
}
function formula(key: 'latex' | 'description', value: string): void {
  if (props.node.content.kind !== 'FORMULA') fail('SCHEMA_INVALID', '当前内容没有公式')
  content({ ...props.node.content, [key]: value, rendered_artifact_id: key === 'latex' && value !== props.node.content.latex ? null : props.node.content.rendered_artifact_id })
}
function caption(value: string): void {
  if (props.node.content.kind !== 'TABLE') fail('SCHEMA_INVALID', '当前内容没有表格')
  content({ ...props.node.content, caption: value })
}
function add(kind: 'TEXT' | 'PARAGRAPH' | 'TABLE' | 'FORMULA'): void {
  const value: Node['content'] = kind === 'TEXT' ? { kind, text: '', marks: [] } : kind === 'PARAGRAPH' ? { kind, title: '' }
    : kind === 'TABLE' ? newDocumentTable() : { kind, latex: '', description: '', rendered_artifact_id: null }
  apply(insertDocumentContent(props.body, props.node.node_id, value))
}
function reuseImage(id: string): void {
  const source = child(id)
  if (source.content.kind !== 'IMAGE') fail('ACTION_STALE', '选择的图片版本已经变化')
  apply(insertDocumentContent(props.body, props.node.node_id, source.content))
}
function child(id: string): Node {
  const value = props.body.nodes.find(item => item.node_id === id)
  if (value === undefined) fail('SCHEMA_INVALID', '正文节点身份缺失')
  return value
}
function anchor(row: string, column: string): boolean { return positions.value.some(value => value.cell.row_id === row && value.cell.column_id === column) }
function cell(row: string, column: string): Cell {
  const value = positions.value.find(value => value.cell.row_id === row && value.cell.column_id === column)
  if (value === undefined) fail('SCHEMA_INVALID', '表格单元格身份缺失')
  return value.cell
}
function span({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }): [number, number] {
  const value = positions.value.find(position => position.row === rowIndex && position.column === columnIndex)
  return value === undefined ? [0, 0] : [value.cell.row_span, value.cell.column_span]
}
function select(id: string, chosen: string | number | boolean): void {
  selected.value = chosen === true ? [...new Set([...selected.value, id])] : selected.value.filter(value => value !== id)
}
function separate(): void {
  const id = selected.value[0]
  if (selected.value.length !== 1 || id === undefined) fail('ACTION_STALE', '请选择一个合并单元格')
  apply(separateTableCell(props.body, props.node.node_id, id))
}
function remove(dimension: 'ROW' | 'COLUMN', id: string): void { apply(removeEmptyTableDimension(props.body, props.node.node_id, dimension, id)) }
watch(() => props.node.content, () => { selected.value = selected.value.filter(id => positions.value.some(value => value.cell.cell_id === id)) })
watch(() => props.node.content, () => { formulaHtml.value = '' }, { flush: 'sync' })
</script>

<style scoped>
.formula-preview { overflow: auto; padding: 12px; }
.selection-text-node { display: inline; }
.selection-text { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

import type { DocumentBody, FieldValue, Node, TextContent } from '../../../../contracts/typescript/src/interfaces'
import { documentReference, canonicalDocumentValue } from './document-body'
import { fail, validate } from './protocol'

export function editDocumentText(body: DocumentBody, nodeId: string, contents: TextContent[]): DocumentBody {
  const original = body.nodes.find(node => node.node_id === nodeId)
  if (original === undefined || original.content.kind !== 'TEXT' || original.parent_id === null || contents.length === 0) {
    fail('SCHEMA_INVALID', '当前文本节点与编辑内容无效')
  }
  contents.forEach(content => validate(documentReference + 'TextContent', content))
  const next = structuredClone(body); const position = next.nodes.findIndex(node => node.node_id === nodeId)
  const additions: Node[] = contents.map((content, index) => ({ node_id: index === 0 ? original.node_id : crypto.randomUUID(),
    parent_id: original.parent_id, ordinal: original.ordinal + index, field_ids: index === 0 ? [...original.field_ids] : [],
    evidence_link_ids: index === 0 ? [...original.evidence_link_ids] : [], content: structuredClone(content) }))
  for (const node of next.nodes) if (node.parent_id === original.parent_id && node.ordinal > original.ordinal) node.ordinal += additions.length - 1
  next.nodes.splice(position, 1, ...additions)
  const parent = next.nodes.find(node => node.node_id === original.parent_id)
  if (parent?.content.kind === 'TABLE') {
    const cell = parent.content.cells.find(value => value.node_ids.includes(nodeId))
    if (cell === undefined) fail('SCHEMA_INVALID', '表格文本需要原单元格归属')
    cell.node_ids.splice(cell.node_ids.indexOf(nodeId), 1, ...additions.map(node => node.node_id))
  }
  canonicalDocumentValue(next); validate(documentReference + 'DocumentBody', next); return next
}

export function editDocumentContent(body: DocumentBody, nodeId: string, content: Node['content']): DocumentBody {
  const next = structuredClone(body); const node = next.nodes.find(value => value.node_id === nodeId)
  if (node === undefined || node.content.kind !== content.kind) fail('SCHEMA_INVALID', '正文内容需要原节点身份与类型')
  node.content = structuredClone(content)
  canonicalDocumentValue(next); validate(documentReference + 'DocumentBody', next); return next
}

export function insertDocumentContent(body: DocumentBody, parentId: string, content: Node['content']): DocumentBody {
  const next = structuredClone(body); const parent = next.nodes.find(value => value.node_id === parentId)
  if (parent === undefined || !['DOCUMENT', 'SECTION', 'PARAGRAPH'].includes(parent.content.kind) || content.kind === 'DOCUMENT') {
    fail('SCHEMA_INVALID', '新增内容需要当前目录或段落作为父节点')
  }
  const siblings = next.nodes.filter(value => value.parent_id === parentId)
  next.nodes.push({ node_id: crypto.randomUUID(), parent_id: parentId, ordinal: siblings.length === 0 ? 0 : Math.max(...siblings.map(value => value.ordinal)) + 1,
    field_ids: [], evidence_link_ids: [], content: structuredClone(content) })
  canonicalDocumentValue(next); validate(documentReference + 'DocumentBody', next); return next
}

export function editDocumentField(body: DocumentBody, fieldId: string, key: 'original_value' | 'decimal_value' | 'unit' | 'model' | 'condition', value: string | null): DocumentBody {
  const next = structuredClone(body); const field = next.fields.find(item => item.field_id === fieldId)
  if (field === undefined || key === 'original_value' && value === null) fail('SCHEMA_INVALID', '字段身份或输入无效')
  if (key === 'original_value') {
    if (value === null) fail('SCHEMA_INVALID', '原始字段值需要完整文本')
    field.original_value = value
  } else field[key] = value
  canonicalDocumentValue(next); validate(documentReference + 'DocumentBody', next); return next
}

export function confirmDocumentField(body: DocumentBody, value: FieldValue): DocumentBody {
  const next = structuredClone(body); const index = next.fields.findIndex(item => item.field_id === value.field_id)
  if (index < 0) fail('SCHEMA_INVALID', '当前正文没有对应字段')
  const previous = next.fields[index]
  if (previous === undefined) fail('SCHEMA_INVALID', '当前字段内容缺失')
  // 字段值与适用条件可以编辑，原节点、定义和证据身份继续由当前正文管理。
  next.fields[index] = { ...previous, original_value: value.original_value, decimal_value: value.decimal_value,
    unit: value.unit, model: value.model, condition: value.condition }
  canonicalDocumentValue(next); validate(documentReference + 'DocumentBody', next); return next
}

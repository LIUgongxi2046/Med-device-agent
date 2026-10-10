import type { Cell, DocumentBody, TableContent } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue, documentReference } from './document-body'
import { fail, validate } from './protocol'

export interface TablePosition { cell: Cell; row: number; column: number }
export function tablePositions(table: TableContent): TablePosition[] {
  const positions = table.cells.map(cell => ({ cell, row: table.row_ids.indexOf(cell.row_id), column: table.column_ids.indexOf(cell.column_id) }))
  let area = 0
  for (const position of positions) {
    const { row, column, cell } = position
    if (row < 0 || column < 0 || cell.row_span > table.row_ids.length - row || cell.column_span > table.column_ids.length - column) {
      fail('SCHEMA_INVALID', '单元格跨度超出当前表格')
    }
    area += cell.row_span * cell.column_span
  }
  for (let index = 0; index < positions.length; index++) {
    const position = positions[index]
    if (position === undefined) fail('SCHEMA_INVALID', '表格单元格身份缺失')
    for (const other of positions.slice(index + 1)) {
      if (position.row < other.row + other.cell.row_span && other.row < position.row + position.cell.row_span
          && position.column < other.column + other.cell.column_span && other.column < position.column + position.cell.column_span) {
        fail('SCHEMA_INVALID', '表格包含相互覆盖的单元格')
      }
    }
  }
  if (area !== table.row_ids.length * table.column_ids.length) fail('SCHEMA_INVALID', '表格单元格需要覆盖全部行列')
  return positions.sort((a, b) => a.row - b.row || a.column - b.column)
}
function blank(row: string, column: string): Cell {
  return { cell_id: crypto.randomUUID(), row_id: row, column_id: column, row_span: 1, column_span: 1, node_ids: [] }
}
export function newDocumentTable(): TableContent {
  const row = crypto.randomUUID(), column = crypto.randomUUID()
  return { kind: 'TABLE', caption: '', row_ids: [row], column_ids: [column], cells: [blank(row, column)] }
}
function tableDraft(body: DocumentBody, nodeId: string): { body: DocumentBody; table: TableContent } {
  const next = structuredClone(body), node = next.nodes.find(value => value.node_id === nodeId)
  if (node?.content.kind !== 'TABLE') fail('SCHEMA_INVALID', '表格操作需要当前表格节点')
  tablePositions(node.content); return { body: next, table: node.content }
}
function finished(body: DocumentBody, nodeId: string, table: TableContent): DocumentBody {
  const ordered = tablePositions(table).flatMap(value => value.cell.node_ids)
  const children = body.nodes.filter(value => value.parent_id === nodeId)
  if (new Set(ordered).size !== ordered.length || children.length !== ordered.length || children.some(value => !ordered.includes(value.node_id))) {
    fail('SCHEMA_INVALID', '表格正文必须具有完整且唯一的单元格归属')
  }
  for (const child of children) child.ordinal = ordered.indexOf(child.node_id)
  canonicalDocumentValue(body); validate(documentReference + 'DocumentBody', body); return body
}
export function appendTableDimension(body: DocumentBody, nodeId: string, dimension: 'ROW' | 'COLUMN'): DocumentBody {
  const draft = tableDraft(body, nodeId), id = crypto.randomUUID()
  if (dimension === 'ROW') { draft.table.row_ids.push(id); for (const column of draft.table.column_ids) draft.table.cells.push(blank(id, column)) }
  else { draft.table.column_ids.push(id); for (const row of draft.table.row_ids) draft.table.cells.push(blank(row, id)) }
  return finished(draft.body, nodeId, draft.table)
}
export function removeEmptyTableDimension(body: DocumentBody, nodeId: string, dimension: 'ROW' | 'COLUMN', id: string): DocumentBody {
  const draft = tableDraft(body, nodeId), values = dimension === 'ROW' ? draft.table.row_ids : draft.table.column_ids
  const index = values.indexOf(id)
  if (index < 0 || values.length < 2) fail('ACTION_STALE', '表格需要保留有效的行列')
  const positions = tablePositions(draft.table)
  const affected = positions.filter(value => dimension === 'ROW' ? value.row <= index && value.row + value.cell.row_span > index
    : value.column <= index && value.column + value.cell.column_span > index)
  if (affected.some(value => value.cell.node_ids.length > 0 || (dimension === 'ROW' ? value.cell.row_span : value.cell.column_span) > 1)) {
    fail('ACTION_STALE', '移除行列需要单元格为空且跨度为一')
  }
  values.splice(index, 1); const removed = new Set(affected.map(value => value.cell.cell_id))
  draft.table.cells = draft.table.cells.filter(value => !removed.has(value.cell_id))
  return finished(draft.body, nodeId, draft.table)
}
export function mergeTableCells(body: DocumentBody, nodeId: string, ids: string[]): DocumentBody {
  const draft = tableDraft(body, nodeId), positions = tablePositions(draft.table)
  if (new Set(ids).size !== ids.length || ids.length < 2) fail('ACTION_STALE', '合并需要选择多个不同单元格')
  const selected = positions.filter(value => ids.includes(value.cell.cell_id))
  if (selected.length !== ids.length) fail('ACTION_STALE', '选择的单元格已经变化')
  const row = Math.min(...selected.map(value => value.row)), column = Math.min(...selected.map(value => value.column))
  const endRow = Math.max(...selected.map(value => value.row + value.cell.row_span)), endColumn = Math.max(...selected.map(value => value.column + value.cell.column_span))
  const area = selected.reduce((total, value) => total + value.cell.row_span * value.cell.column_span, 0)
  if (area !== (endRow - row) * (endColumn - column)) fail('ACTION_STALE', '合并选择需要组成完整矩形')
  const first = selected[0]
  if (first === undefined || first.row !== row || first.column !== column) fail('SCHEMA_INVALID', '合并矩形缺少起始单元格')
  const replacement: Cell = { ...first.cell, row_span: endRow - row, column_span: endColumn - column,
    node_ids: selected.flatMap(value => value.cell.node_ids) }
  draft.table.cells = draft.table.cells.filter(value => !ids.includes(value.cell_id)); draft.table.cells.push(replacement)
  return finished(draft.body, nodeId, draft.table)
}
export function separateTableCell(body: DocumentBody, nodeId: string, id: string): DocumentBody {
  const draft = tableDraft(body, nodeId), original = tablePositions(draft.table).find(value => value.cell.cell_id === id)
  if (original === undefined || original.cell.row_span === 1 && original.cell.column_span === 1) fail('ACTION_STALE', '选择的单元格没有合并跨度')
  draft.table.cells = draft.table.cells.filter(value => value.cell_id !== id)
  for (let row = original.row; row < original.row + original.cell.row_span; row++) {
    for (let column = original.column; column < original.column + original.cell.column_span; column++) {
      const rowId = draft.table.row_ids[row], columnId = draft.table.column_ids[column]
      if (rowId === undefined || columnId === undefined) fail('SCHEMA_INVALID', '单元格行列身份缺失')
      draft.table.cells.push(row === original.row && column === original.column ? { ...original.cell, row_span: 1, column_span: 1 } : blank(rowId, columnId))
    }
  }
  return finished(draft.body, nodeId, draft.table)
}
export function appendTableText(body: DocumentBody, nodeId: string, cellId: string): DocumentBody {
  const draft = tableDraft(body, nodeId), cell = draft.table.cells.find(value => value.cell_id === cellId)
  if (cell === undefined) fail('ACTION_STALE', '选择的单元格已经变化')
  const id = crypto.randomUUID(); cell.node_ids.push(id)
  draft.body.nodes.push({ node_id: id, parent_id: nodeId, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'TEXT', text: '', marks: [] } })
  return finished(draft.body, nodeId, draft.table)
}

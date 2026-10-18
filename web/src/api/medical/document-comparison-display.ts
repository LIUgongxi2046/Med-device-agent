/** Presentation only. Callers must first validate the complete typed server result. */
export type DifferenceCategory = 'BODY_METADATA' | 'NODE' | 'FIELD' | 'EVIDENCE' | 'FACT' | 'TABLE_ROW' | 'TABLE_COLUMN' | 'TABLE_CELL' | 'ORDER'
export type DifferenceStatus = 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED'
export interface DifferenceRecord {
  category: DifferenceCategory
  identity: string
  status: DifferenceStatus
  before_json: string | null
  after_json: string | null
  before_sha256: string | null
  after_sha256: string | null
  text_change: { node_id: string; left: TextSelection; right: TextSelection } | null
}
export interface TextSelection { start: number; end: number; quote: string; quote_sha256: string }
export const categoryLabels: Record<DifferenceCategory, string> = {
  BODY_METADATA: '正文元数据', NODE: '正文节点', FIELD: '字段', EVIDENCE: '证据引用',
  FACT: '共享事实引用', TABLE_ROW: '表格行', TABLE_COLUMN: '表格列', TABLE_CELL: '表格单元', ORDER: '排列顺序'
}
export const statusLabels: Record<DifferenceStatus, string> = {
  ADDED: '新增', REMOVED: '删除', CHANGED: '修改', UNCHANGED: '未变化'
}
const labels: Record<string, string> = {
  text: '文字', title: '标题', type: '类型', node_type: '节点类型', node_id: '节点标识',
  parent_id: '父节点', parent_node_id: '父节点', original_value: '原始值', decimal_value: '数值',
  unit: '单位', model: '型号', condition: '条件', relation: '证据关系', field_id: '字段标识',
  field_definition_id: '字段定义', evidence_link_id: '证据关联', evidence_ref: '固定证据',
  fact_version_id: '事实版本', node_ids: '关联节点', field_ids: '关联字段', description: '描述',
  latex: '公式原文', row_id: '行标识', column_id: '列标识', cell_id: '单元标识',
  row_span: '跨行', column_span: '跨列', rows: '行', columns: '列', cells: '单元',
  source_version_id: '来源版本', content_sha256: '内容摘要', value: '值', order: '顺序'
}
export interface DisplayField { label: string; value: string }
/** This never renders HTML or interprets numbers/units/clinical meaning. */
export function displayFields(canonicalJson: string | null): DisplayField[] {
  if (canonicalJson === null) return [{ label: '内容', value: '此侧不存在' }]
  const value: unknown = JSON.parse(canonicalJson)
  if (value === null || typeof value !== 'object') return [{ label: '内容', value: scalar(value) }]
  if (Array.isArray(value)) return value.map((entry, index) => ({ label: `第 ${index + 1} 项`, value: scalar(entry) }))
  return Object.entries(value).map(([key, entry]) => ({ label: labels[key] ?? key, value: scalar(entry) }))
}
function scalar(value: unknown): string {
  if (value === null) return '未设置'
  if (typeof value === 'string') return value === '' ? '（空字符串）' : value
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}
export function differencePage(
  records: readonly DifferenceRecord[], category: DifferenceCategory | 'ALL',
  status: DifferenceStatus | 'ALL', page: number, pageSize = 10
) {
  if (!Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > 100) throw new Error('非法本地页大小')
  const filtered = records.filter(record => (category === 'ALL' || record.category === category) && (status === 'ALL' || record.status === status))
  const pages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const current = Math.min(pages, Math.max(1, Number.isSafeInteger(page) ? page : 1))
  return { total: records.length, filtered: filtered.length, page: current, pages, items: filtered.slice((current - 1) * pageSize, current * pageSize) }
}

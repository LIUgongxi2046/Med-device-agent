import type { DocumentBody, EvidenceLink, FactUsage, FieldValue, Node } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue, documentReference } from './document-body'
import { fail, validate } from './protocol'

export type DocumentChoice = 'BASE' | 'SERVER' | 'LOCAL'
type Item = Node | FieldValue | EvidenceLink | FactUsage
export interface DocumentComparisonRow {
  key: string; kind: 'NODE' | 'FIELD' | 'EVIDENCE' | 'FACT'; identity: string
  base: Item | null; server: Item | null; local: Item | null; changed: boolean; choice: DocumentChoice | null
}
export interface DocumentComparison { templateVersion: string; rows: DocumentComparisonRow[] }
function collection<T extends Item>(kind: DocumentComparisonRow['kind'], base: T[], server: T[], local: T[], identity: (value: T) => string): DocumentComparisonRow[] {
  const original = new Map(base.map(value => [identity(value), value])); const remote = new Map(server.map(value => [identity(value), value]))
  const edited = new Map(local.map(value => [identity(value), value])); const ids = new Set([...original.keys(), ...remote.keys(), ...edited.keys()])
  return [...ids].map(id => {
    const before = original.get(id) ?? null, current = remote.get(id) ?? null, chosen = edited.get(id) ?? null
    const a = canonicalDocumentValue(before), b = canonicalDocumentValue(current), c = canonicalDocumentValue(chosen)
    return { key: kind + ':' + id, kind, identity: id, base: before, server: current, local: chosen, changed: a !== b || a !== c,
      choice: c === a ? 'SERVER' : b === a || b === c ? 'LOCAL' : null }
  })
}
export function compareDocumentBodies(base: DocumentBody, server: DocumentBody, local: DocumentBody): DocumentComparison {
  for (const value of [base, server, local]) validate(documentReference + 'DocumentBody', value)
  if (base.template_version !== server.template_version || base.template_version !== local.template_version) fail('SCOPE_STALE', '版本比较需要同一固定模板')
  return { templateVersion: base.template_version, rows: [
    ...collection('NODE', base.nodes, server.nodes, local.nodes, value => value.node_id),
    ...collection('FIELD', base.fields, server.fields, local.fields, value => value.field_id),
    ...collection('EVIDENCE', base.evidence_links, server.evidence_links, local.evidence_links, value => value.evidence_link_id),
    ...collection('FACT', base.fact_usages, server.fact_usages, local.fact_usages, value => value.fact_version_id)
  ] }
}
export function mergeDocumentComparison(comparison: DocumentComparison): DocumentBody {
  function selected<T extends Item>(kind: DocumentComparisonRow['kind']): T[] {
    const values: T[] = []
    for (const row of comparison.rows.filter(item => item.kind === kind)) {
      if (row.choice === null) fail('ACTION_STALE', '存在尚未选择处理方式的文稿差异')
      const value = row.choice === 'BASE' ? row.base : row.choice === 'SERVER' ? row.server : row.local
      if (value !== null) values.push(structuredClone(value) as T)
    }
    return values
  }
  const body: DocumentBody = { schema_version: '1.0.0', template_version: comparison.templateVersion, nodes: selected<Node>('NODE'),
    fields: selected<FieldValue>('FIELD'), evidence_links: selected<EvidenceLink>('EVIDENCE'), fact_usages: selected<FactUsage>('FACT') }
  canonicalDocumentValue(body); validate(documentReference + 'DocumentBody', body); return body
}
export function comparisonText(value: Item | null): string {
  if (value === null) return '没有此项内容'
  if ('content' in value) {
    const content = value.content
    const position = '\n节点：' + value.node_id + '\n父节点：' + (value.parent_id ?? '文稿根目录') + '\n顺序：' + value.ordinal
      + '\n字段：' + value.field_ids.join('、') + '\n证据关系：' + value.evidence_link_ids.join('、')
    if (content.kind === 'TEXT') return content.text + '\n格式：' + content.marks.join('、') + position
    if (content.kind === 'IMAGE') return '图片替代文字：' + content.alt_text + '\n说明：' + content.caption + '\n图片版本：' + content.artifact_id + '\n摘要：' + content.sha256 + position
    if (content.kind === 'FORMULA') return content.latex + '\n说明：' + content.description + '\n公式图片版本：' + (content.rendered_artifact_id ?? '未提供') + position
    if (content.kind === 'TABLE') return content.caption + '\n行顺序：' + content.row_ids.join('、') + '\n列顺序：' + content.column_ids.join('、')
      + '\n单元格：\n' + content.cells.map(cell => '单元格 ' + cell.cell_id + '；行 ' + cell.row_id + '；列 ' + cell.column_id
        + '；行跨度 ' + cell.row_span + '；列跨度 ' + cell.column_span + '；正文节点 ' + cell.node_ids.join('、')).join('\n') + position
    return content.title + position
  }
  if ('original_value' in value) return '原始值：' + value.original_value + '\n精确数值：' + (value.decimal_value ?? '未提供')
    + '\n单位：' + (value.unit ?? '未提供') + '\n型号：' + (value.model ?? '未提供') + '\n条件：' + (value.condition ?? '未提供')
    + '\n定义：' + value.field_definition_id + '\n正文节点：' + value.node_id + '\n证据关系：' + value.evidence_link_ids.join('、')
  if ('evidence' in value) return '关系：' + value.relation + '\n正文节点：' + value.node_ids.join('、') + '\n来源：' + value.evidence.source_id
    + '\n来源版本：' + value.evidence.source_version + '\n解析版本：' + value.evidence.parse_version + '\n原文片段：' + value.evidence.chunk_id
    + '\n批准版本：' + value.evidence.approval_revision + '\n完整定位：' + JSON.stringify(value.evidence.locator, null, 2) + '\n原文摘要：' + value.evidence.quote_hash
  return '事实版本：' + value.fact_version_id + '\n正文节点：' + value.node_ids.join('、') + '\n字段：' + value.field_ids.join('、')
}

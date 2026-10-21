import type { DocumentBody, EvidenceLink } from '../../../contracts/typescript/src/interfaces'
const id = (n: number) => '00000000-0000-4000-8000-' + String(n).padStart(12, '0')
const evidence = (n: number, relation: EvidenceLink['relation']): EvidenceLink => ({ evidence_link_id: id(n), node_ids: [id(2)], relation, evidence: {
  schema_version: '1.0.0', tenant_id: id(80), project_id: id(81), source_id: id(82), source_version: id(83), parse_version: id(84), chunk_id: id(n+100),
  locator_kind: 'PDF', locator: { kind: 'PDF', page_index: 2, page_basis: 'ORIGINAL', printed_label: '3', region: { kind: 'WHOLE_PAGE' } }, quote_hash: 'a'.repeat(64), approval_revision: id(85)
} })
export const body: DocumentBody = { schema_version: '1.0.0', template_version: id(1), nodes: [], fact_usages: [],
  fields: [
    { field_id: id(10), node_id: id(2), field_definition_id: id(20), original_value: '示例型号 AX-100', decimal_value: null, unit: null, model: 'AX-100', condition: '示例常温条件', evidence_link_ids: [id(30), id(31)] },
    { field_id: id(11), node_id: id(2), field_definition_id: id(21), original_value: '', decimal_value: null, unit: 'mm', model: 'AX-200', condition: null, evidence_link_ids: [] },
    { field_id: id(12), node_id: id(2), field_definition_id: id(22), original_value: '0001.2300', decimal_value: '0001.2300', unit: 'mm', model: 'AX-300', condition: '仅用于界面演示', evidence_link_ids: [id(30)] }
  ], evidence_links: [evidence(30, 'SUPPORT'), evidence(31, 'CONFLICT')] }

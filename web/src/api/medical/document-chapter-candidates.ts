import type { SectionCandidate, ObjectRef } from '../../../../contracts/typescript/src/artifacts'
import type { DocumentBody, Node } from '../../../../contracts/typescript/src/interfaces'

/** Read-only presentation input; the caller supplies already-authorized, decoded complete objects. */
export interface CandidateBody { object_ref: ObjectRef; body: DocumentBody }
export interface CandidateSelection { body_object_ref: ObjectRef; node_ids: string[] }
export function candidateKey(ref: ObjectRef): string { return JSON.stringify([ref.object_key, ref.object_version, ref.sha256, ref.size_bytes, ref.media_type]) }
export function candidateBody(candidate: SectionCandidate | null, bodies: CandidateBody[]): DocumentBody | null {
  if (!candidate) return null
  const matches = bodies.filter(item => candidateKey(item.object_ref) === candidateKey(candidate.body_object_ref))
  return matches.length === 1 ? matches[0]!.body : null
}
export function selectableNodes(candidate: SectionCandidate): string[] {
  const counts = new Map<string, number>()
  candidate.node_manifest.forEach(node => counts.set(node.node_id, (counts.get(node.node_id) ?? 0) + 1))
  return candidate.node_manifest.filter(node => counts.get(node.node_id) === 1 && candidate.target_sections.includes(node.section_id) && node.kind !== 'ROOT').map(node => node.node_id)
}
export function exactSelection(candidate: SectionCandidate, requested: string[]): string[] {
  const allowed = new Set(selectableNodes(candidate))
  return [...new Set(requested)].filter(id => allowed.has(id))
}
export function nodeText(node: Node | undefined): string {
  if (!node) return '此版本没有该节点；不据此推断新增或删除操作。'
  const c = node.content
  switch (c.kind) {
    case 'TEXT': return c.text
    case 'DOCUMENT': case 'SECTION': case 'PARAGRAPH': return c.title
    case 'IMAGE': return `${c.alt_text}\n${c.caption}\n图片固定版本：${c.artifact_id}\n摘要：${c.sha256}（未加载图片文件）`
    case 'FORMULA': return `${c.description}\n${c.latex}`
    case 'TABLE': return `${c.caption}\n${c.row_ids.length} 行 × ${c.column_ids.length} 列；单元格内容见关联文字节点。\n${JSON.stringify(c.cells, null, 2)}`
  }
}
export function referenceChanges(current: DocumentBody | null, proposed: DocumentBody | null, nodeIds: string[]) {
  if (!current || !proposed) return null
  const selected = new Set(nodeIds)
  const before = current.evidence_links.filter(link => link.node_ids.some(id => selected.has(id)))
  const after = proposed.evidence_links.filter(link => link.node_ids.some(id => selected.has(id)))
  return {
    before, after,
    added: after.filter(link => !before.some(old => old.evidence_link_id === link.evidence_link_id)),
    removed: before.filter(link => !after.some(next => next.evidence_link_id === link.evidence_link_id)),
    changed: after.filter(link => before.some(old => old.evidence_link_id === link.evidence_link_id && JSON.stringify(old) !== JSON.stringify(link)))
  }
}
export function candidateBlockers(input: { candidate: SectionCandidate | null; currentRevision: string | null; body: DocumentBody | null; active: boolean; dirty: boolean; permission: boolean | null; selected: string[] }): string[] {
  const reasons: string[] = []
  if (!input.active) reasons.push('当前工作区未激活，不能采用候选。')
  if (!input.candidate) reasons.push('尚未选择已提供的章节候选。')
  if (!input.currentRevision) reasons.push('当前正文版本未知，需要重新读取当前正文。')
  else if (input.candidate && input.candidate.base_document_revision !== input.currentRevision) reasons.push('候选基于旧正文版本；请重新比较，不能自动改写基准版本。')
  if (input.dirty) reasons.push('当前正文有未保存修改，请先处理草稿。')
  if (input.permission === null) reasons.push('候选采用权限未知，需要服务端核验。')
  else if (!input.permission) reasons.push('当前未获候选采用权限。')
  if (!input.body) reasons.push('候选完整正文尚未载入，不能采用。')
  if (!input.selected.length) reasons.push('请选择明确的目标节点；选择父节点不会自动包含子节点。')
  reasons.push('章节候选采用接口尚未接入；本页只支持本地选择与比较，不会保存或覆盖正文。')
  return reasons
}

/** Local-only review summary, deliberately not an API request or adoption receipt. */
export function adoptionPreview(candidate: SectionCandidate, requested: string[], currentRevision: string | null, current: DocumentBody | null, proposed: DocumentBody | null) {
  const nodeIds = exactSelection(candidate, requested)
  return {
    body_object_ref: { ...candidate.body_object_ref },
    base_document_revision: candidate.base_document_revision,
    current_document_revision: currentRevision,
    selected_node_ids: nodeIds,
    direct_section_ids: [...candidate.target_sections],
    reference_changes: referenceChanges(current, proposed, nodeIds)
  }
}

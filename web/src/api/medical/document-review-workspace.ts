import type { DocumentSnapshot, DocumentBody, ReviewTarget } from '../../../../contracts/typescript/src/interfaces'
import type { ReviewBasis, ReviewAssignmentView } from './document-review-return-types'

/** Presentation only. No status in this model authorizes a review or a signature. */
export interface ReviewWorkspaceInput {
  returnContextKey?: string | undefined
  snapshot: DocumentSnapshot
  savedBody?: { documentRevision: string; body: DocumentBody } | null | undefined
  dirty?: boolean | undefined
  isCurrent?: boolean | undefined
  active?: boolean | undefined
  taskStatus?: string | null | undefined
  basis?: ReviewBasis | null | undefined
  assignments?: ReviewAssignmentView[] | null | undefined
}
export interface ReviewChecklistItem {
  id: string
  title: string
  status: 'RECORDED' | 'BLOCKED' | 'UNKNOWN'
  detail: string
  canNavigate: boolean
}
const returnBlockers: Record<ReviewBasis['blockers'][number], string> = {
  DOCUMENT_NOT_CURRENT: '不是当前正文版本', PROJECT_NOT_ACTIVE: '项目当前未启用',
  DOCUMENT_SIGNED: '文稿已有签发状态', TASK_NOT_REVIEW_PENDING: '任务不处于待审阶段',
  TASK_REVISION_CHANGED: '关联任务版本已经变化'
}
export function reviewWorkspaceModel(input: ReviewWorkspaceInput) {
  const s = input.snapshot
  const basis = input.basis && input.basis.document_id === s.document_id && input.basis.document_revision === s.document_revision && input.basis.body_sha256 === s.body.sha256 ? input.basis : null
  const assignments = input.assignments?.filter(row => row.assignment.document_id === s.document_id && row.assignment.document_revision === s.document_revision) ?? []
  const blockers = [...s.invalidation_reasons]
  if (input.active === false) blockers.push('当前工作区未激活，不能操作此版本')
  if (input.dirty) blockers.push('存在未保存修改，请返回撰写处理；审查不使用本地草稿')
  if (input.isCurrent !== true) blockers.push('当前性尚未确认或正文已更新，请重新读取当前版本')
  if (input.basis && !basis) blockers.push('审查依据不属于当前固定正文，已忽略旧依据')
  if (basis) blockers.push(...basis.blockers.map(code => returnBlockers[code]))
  const checklist: ReviewChecklistItem[] = [
    { id: 'version', title: '已保存正文与版本', status: input.isCurrent && !input.dirty ? 'RECORDED' : 'BLOCKED', detail: input.dirty ? '本地仍有修改，不能用于审查确认。' : input.isCurrent ? '已读取固定版本；这不代表审查通过。' : '需要重新核对当前已保存版本。', canNavigate: true },
    { id: 'checks', title: '专业检查与覆盖范围', status: 'UNKNOWN', detail: `${s.check_ids.length} 个检查引用；当前有效性与完整覆盖尚未核验。技术结构检查不替代专业检查。`, canNavigate: true },
    { id: 'issues', title: '阻断问题与失效原因', status: s.invalidation_reasons.length ? 'BLOCKED' : 'UNKNOWN', detail: s.invalidation_reasons.length ? `正文回读包含 ${s.invalidation_reasons.length} 条失效原因，需修订后复验。` : '问题明细尚未接入，不能将未知数量当作零个问题。', canNavigate: true },
    { id: 'evidence', title: '证据与适用条件', status: 'UNKNOWN', detail: `固定证据水位 ${s.evidence_watermark}；引用支持性、适用条件与证据决定尚未核验。`, canNavigate: true },
    { id: 'annotations', title: '标注与重新定位', status: 'UNKNOWN', detail: '请在全文标注区核对意见；当前未汇总未解决及失效标注，不推断全部已处理。', canNavigate: false },
    { id: 'review', title: '章节与总稿确认', status: 'UNKNOWN', detail: `${s.review_ids.length} 个审查记录引用；未回读完整章节决定及当前有效性，不能推断审查通过。`, canNavigate: false },
    { id: 'identity', title: '签发权限与身份核验', status: 'UNKNOWN', detail: '签发策略、用途及绑定此版本的一次性身份核验尚未接入。', canNavigate: false },
    { id: 'signature', title: '不可变签发记录', status: 'UNKNOWN', detail: `${s.signature_ids.length} 个签发记录引用；尚未回读签发人、时间及正文摘要，不展示签发成功。`, canNavigate: false }
  ]
  return { basis, assignments, blockers: [...new Set(blockers)], checklist, canConfirm: false as const, canResubmit: false as const, canSign: false as const }
}

/** A local worksheet, deliberately not a ReviewBatch or a transport request. */
export interface LocalReviewDraft { scope: 'DOCUMENT' | 'SECTION'; nodeId: string; decision: ReviewTarget['decision']; opinion: string; reason: string }
export function savedReviewSections(input: ReviewWorkspaceInput) {
  if (input.savedBody?.documentRevision !== input.snapshot.document_revision) return []
  return input.savedBody.body.nodes.flatMap(node => node.content.kind === 'SECTION' ? [{ nodeId: node.node_id, title: node.content.title }] : [])
}
export function localReviewIssues(input: ReviewWorkspaceInput, draft: LocalReviewDraft): string[] {
  const issues: string[] = []
  if (!draft.opinion.trim()) issues.push('请填写审查意见。')
  if (!draft.reason.trim()) issues.push('请填写决定理由与依据。')
  if (draft.scope === 'SECTION' && !savedReviewSections(input).some(section => section.nodeId === draft.nodeId)) issues.push('请选择当前已保存正文中的章节。')
  if (draft.opinion.length > 16000 || draft.reason.length > 2000) issues.push('意见或理由超出长度限制。')
  if (input.active === false || input.dirty || input.isCurrent !== true) issues.push('当前版本不可用于准备决定，请先处理草稿或版本变化。')
  return issues
}
export function localReviewPreview(input: ReviewWorkspaceInput, draft: LocalReviewDraft) {
  if (localReviewIssues(input, draft).length) return null
  return { localOnly: true as const, documentId: input.snapshot.document_id, documentRevision: input.snapshot.document_revision,
    recordRevision: input.snapshot.revision, bodySha256: input.snapshot.body.sha256, evidenceWatermark: input.snapshot.evidence_watermark,
    issueWatermark: input.snapshot.issue_watermark, checkIds: [...input.snapshot.check_ids], decision: draft.decision,
    scope: draft.scope, target: draft.scope === 'DOCUMENT' ? '总稿' : savedReviewSections(input).find(row => row.nodeId === draft.nodeId)!.title,
    nodeId: draft.scope === 'SECTION' ? draft.nodeId : null, sectionRevision: null,
    opinion: draft.opinion, reason: draft.reason }
}
export function revisionResubmissionRows(input: ReviewWorkspaceInput) {
  return (input.assignments ?? []).filter(row => row.assignment.document_id === input.snapshot.document_id).map(row => ({
    assignmentId: row.assignment.assignment_id, originalRevision: row.assignment.document_revision,
    currentRevision: input.snapshot.document_revision, observedCurrentRevision: row.current_document_revision,
    changed: row.assignment.document_revision !== input.snapshot.document_revision,
    matchesObservedCurrent: row.current_document_revision === input.snapshot.document_revision,
    reason: row.assignment.reason, checkCount: input.snapshot.check_ids.length
  }))
}

/** Explicit UI handoff only; never a request body or a review confirmation. */
export interface LocalReturnHandoff {
  contextKey: string
  projectId: string
  documentId: string
  documentRevision: string
  recordRevision: string
  bodySha256: string
  evidenceWatermark: string
  issueWatermark: string
  reason: string
}
export function localReturnHandoff(input: ReviewWorkspaceInput, draft: LocalReviewDraft): LocalReturnHandoff | null {
  if (draft.decision !== 'RETURN' || !input.returnContextKey || localReviewIssues(input, draft).length) return null
  // The independent endpoint returns the whole document, not an invented chapter target.
  const preview = localReviewPreview(input, draft)!
  const reason = `${draft.scope === 'SECTION' ? `章节意见（仍退回整份文稿）：${preview.target}\n` : ''}${draft.opinion.trim()}\n\n理由与依据：${draft.reason.trim()}`
  if (reason.length > 16000) return null
  return { contextKey: input.returnContextKey, projectId: input.snapshot.project_id, documentId: preview.documentId,
    documentRevision: preview.documentRevision, recordRevision: preview.recordRevision, bodySha256: preview.bodySha256,
    evidenceWatermark: preview.evidenceWatermark, issueWatermark: preview.issueWatermark, reason }
}
export function matchesReturnHandoff(handoff: LocalReturnHandoff, snapshot: DocumentSnapshot, contextKey: string): boolean {
  return !!contextKey && handoff.contextKey === contextKey && handoff.projectId === snapshot.project_id &&
    handoff.documentId === snapshot.document_id && handoff.documentRevision === snapshot.document_revision &&
    handoff.recordRevision === snapshot.revision && handoff.bodySha256 === snapshot.body.sha256 &&
    handoff.evidenceWatermark === snapshot.evidence_watermark && handoff.issueWatermark === snapshot.issue_watermark &&
    typeof handoff.reason === 'string' && !!handoff.reason.trim() && handoff.reason.length <= 16000
}

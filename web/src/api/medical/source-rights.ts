import request from '@/utils/request'
import { canonicalizeEx } from 'json-canonicalize'
import { envelope, fail, revision, schemaIssues, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { sourceApprovalAllowed } from './source-governance'
import { requestBodyDigest, requireRequestMetadata } from './request-metadata'
import type { RequestMetadata } from './request-metadata'

export type RightsDecision = 'APPROVE' | 'RETURN' | 'REVOKE'
export type RightsStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REVOKED' | 'EXPIRED'
export type RightsAction = 'READ' | 'STORE' | 'ENTERPRISE_USE' | 'DISTRIBUTE' | 'EXTERNAL_PROCESS'
export type RightsClassification = 'PUBLIC' | 'INTERNAL' | 'RESTRICTED' | 'PERSONAL_HEALTH'
export interface RightsProposal {
  purpose: string; allowed_actions: RightsAction[]; providers: string[]; regions: string[]
  valid_from: string | null; valid_to: string | null
  evidence_manifest: { owner: string; license: string; environments: string[]; classifications: RightsClassification[]; authorization_evidence: Basis[] }
}
export interface RightsPreviewInput { schema_version: '1.0.0'; decision: RightsDecision; proposal: RightsProposal | null }
export interface RightsDecisionInput extends RightsPreviewInput {
  expected_revision: string; base_rights: Basis; reviewed_manifest_sha256: string; reason: string
}
export interface RightsEvidence {
  artifact: Basis; artifact_kind: string; classification: RightsClassification; media_type: string; size_bytes: string
  schema_ref: string; schema_document_sha256: string
}
export interface RightsPreview extends RightsPreviewInput {
  source_id: string; source_version: string; owner_project_id: string | null; ownership: 'ENTERPRISE' | 'PROJECT' | 'SESSION'
  classification: RightsClassification; original_sha256: string; base_rights: Basis; rights_status: RightsStatus
  revision: string; evidence: RightsEvidence[]; reviewed_manifest_sha256: string
}
export interface RightsReceipt {
  schema_version: '1.0.0'; operation_id: string; decision_id: string; source_version: string; base_rights: Basis; rights: Basis
  decision: RightsDecision; rights_status: RightsStatus; revision: string; reviewed_manifest_sha256: string
  evidence: RightsEvidence[]; actor_id: string; decided_at: string; current_context: Context
}
const schema = 'urn:medical:source-rights-decisions:1.0.0#/$defs/'

export function rightsInputIssues(input: RightsPreviewInput | RightsDecisionInput): { path: string; code: string }[] {
  const definition = input !== null && typeof input === 'object' && 'expected_revision' in input ? 'DecisionInput' : 'PreviewInput'
  const issues = schemaIssues(schema + definition, input)
  if (issues.length > 0) return issues
  if ('expected_revision' in input) {
    if (BigInt(input.expected_revision) >= 18446744073709551615n) issues.push({ path: '/expected_revision', code: 'REVISION_LIMIT' })
    if (input.reason.trim() !== input.reason || input.reason.normalize('NFC') !== input.reason) issues.push({ path: '/reason', code: 'NORMALIZED_TEXT' })
  }
  if (input.proposal !== null) {
    if (!input.proposal.allowed_actions.includes('STORE')) issues.push({ path: '/proposal/allowed_actions', code: 'STORE_REQUIRED' })
    if (input.proposal.allowed_actions.includes('EXTERNAL_PROCESS') && input.proposal.providers.length === 0) {
      issues.push({ path: '/proposal/providers', code: 'PROVIDER_REQUIRED' })
    }
    const ids = input.proposal.evidence_manifest.authorization_evidence.map(item => item.version_id)
    if (new Set(ids).size !== ids.length) issues.push({ path: '/proposal/evidence_manifest/authorization_evidence', code: 'DUPLICATE_ID' })
  }
  if (new TextEncoder().encode(canonicalizeEx(input)).byteLength > 131072) issues.push({ path: '', code: 'CONTENT_LIMIT' })
  return issues
}
function requireInput(input: RightsPreviewInput | RightsDecisionInput): void {
  if (rightsInputIssues(input).length > 0) fail('SCHEMA_INVALID', '来源权利决定需要完整且有效的许可范围、证据和原始修订')
}
function current(session: MedicalSession): Context {
  if (session.current_context === null) fail('ACCESS_DENIED', '权利审查需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
function requireEvidence(evidence: RightsEvidence[], proposal: RightsProposal | null): void {
  const expected = proposal?.evidence_manifest.authorization_evidence ?? []
  if (evidence.length !== expected.length || evidence.some((item, index) => canonicalizeEx(item.artifact) !== canonicalizeEx(expected[index]))) {
    fail('CONTENT_MISMATCH', '权利审查授权文件与完整原始选择不一致')
  }
  for (const item of evidence) revision(item.size_bytes)
}
export function rightsDecisionBody(preview: RightsPreview, reason: string): RightsDecisionInput {
  validate(schema + 'Preview', preview); revision(preview.revision)
  const body: RightsDecisionInput = { schema_version: '1.0.0', expected_revision: preview.revision,
    base_rights: { ...preview.base_rights }, decision: preview.decision, proposal: structuredClone(preview.proposal),
    reviewed_manifest_sha256: preview.reviewed_manifest_sha256, reason }
  requireInput(body); return body
}
export async function previewSourceRights(session: MedicalSession, sourceVersion: string, input: RightsPreviewInput,
    signal: AbortSignal): Promise<RightsPreview> {
  signal.throwIfAborted(); const context = current(session)
  validate('urn:medical:templates:1.0.0#/$defs/Id', sourceVersion); requireInput(input)
  if (!session.action_summary.some(item => item.action === 'source.approve')) fail('ACCESS_DENIED', '当前会话没有来源权利审查许可')
  const raw = await request.post('/api/source-versions/' + sourceVersion + '/rights-reviews', input, {
    medical: 'envelope', signal, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
      [session.csrf.header_name]: session.csrf.token } })
  signal.throwIfAborted(); const value = envelope(raw); const result = validate<RightsPreview>(schema + 'Preview', value.data)
  revision(result.revision)
  if (raw.status !== 200 || value.resource_revision !== result.revision || result.source_version !== sourceVersion
      || result.decision !== input.decision || canonicalizeEx(result.proposal) !== canonicalizeEx(input.proposal)
      || (result.ownership === 'ENTERPRISE') !== (result.owner_project_id === null)
      || !sourceApprovalAllowed(session, result.source_id, result.owner_project_id)) fail('CONTENT_MISMATCH', '权利审查的版本、归属或决定范围不一致')
  requireEvidence(result.evidence, input.proposal); return result
}
export function verifyRightsReceipt(session: MedicalSession, sourceVersion: string, pending: RequestMetadata,
    body: RightsDecisionInput, result: RightsReceipt, resourceRevision: string | null): void {
  validate(schema + 'Receipt', result); revision(result.revision); validContext(result.current_context)
  const context = current(session)
  if (pending.operation !== 'KN-RIGHTS-01' || pending.target_id !== sourceVersion || pending.resource_id !== sourceVersion
      || pending.actor !== session.user.user_id || pending.tenant_id !== context.tenant_id
      || resourceRevision !== result.revision || result.source_version !== sourceVersion || result.actor_id !== pending.actor
      || result.decision !== body.decision || canonicalizeEx(result.base_rights) !== canonicalizeEx(body.base_rights)
      || result.reviewed_manifest_sha256 !== body.reviewed_manifest_sha256
      || BigInt(result.revision) !== BigInt(body.expected_revision) + 1n
      || result.current_context.tenant_id !== pending.tenant_id || result.current_context.auth_epoch !== pending.context.auth_epoch
      || BigInt(result.current_context.context_revision) !== BigInt(pending.context.context_revision) + 1n
      || BigInt(result.current_context.governance_epoch) !== BigInt(pending.context.governance_epoch) + 1n) {
    fail('CONTENT_MISMATCH', '权利决定回执与原人员、请求、版本或治理修订不一致')
  }
  const status = { APPROVE: 'APPROVED', RETURN: 'PENDING_REVIEW', REVOKE: 'REVOKED' } as const
  if (result.rights_status !== status[body.decision]
      || (body.decision === 'APPROVE' ? result.rights.version_id === body.base_rights.version_id
        : canonicalizeEx(result.rights) !== canonicalizeEx(body.base_rights))) fail('CONTENT_MISMATCH', '权利决定没有保持对应的不可变权利版本和状态')
  requireEvidence(result.evidence, body.proposal)
}
export async function submitSourceRights(session: MedicalSession, sourceVersion: string, pending: RequestMetadata,
    body: RightsDecisionInput, signal: AbortSignal): Promise<RightsReceipt> {
  signal.throwIfAborted(); current(session); requireInput(body); requireRequestMetadata(session, pending)
  if (pending.state !== 'PENDING' || pending.operation !== 'KN-RIGHTS-01' || pending.target_id !== sourceVersion
      || pending.body_sha256 !== await requestBodyDigest(body)) fail('IDEMPOTENCY_CONFLICT', '权利决定恢复必须保留原始参数和请求身份')
  signal.throwIfAborted()
  const raw = await request.post('/api/source-versions/' + sourceVersion + '/rights-decisions', body, {
    medical: 'envelope', signal, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision,
      'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  signal.throwIfAborted(); const value = envelope(raw); const result = validate<RightsReceipt>(schema + 'Receipt', value.data)
  if (raw.status !== 200) fail('CONTENT_MISMATCH', '权利决定没有返回正式完成回执')
  verifyRightsReceipt(session, sourceVersion, pending, body, result, value.resource_revision); return result
}

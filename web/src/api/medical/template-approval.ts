import request from '@/utils/request'
import { canonicalizeEx } from 'json-canonicalize'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { projectTextIssue } from './project-write'
import { readOperation } from './operations'
import type { Operation } from './operations'

export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue }
export type JsonObject = { [key: string]: JsonValue }
export type TemplateDecision = 'APPROVE' | 'RETURN' | 'DISABLE'
export type TemplateStatus = 'DRAFT' | 'APPROVED' | 'RETURNED' | 'DISABLED'
export interface TemplateCondition { mode: 'ALWAYS' | 'RULE'; rule_revision: Basis | null; engine_revision: string | null; input_keys: string[] }
export interface TemplateNode {
  node_id: string; parent_id: string | null; official_number: string | null; title: string; ordinal: number
  condition: TemplateCondition; required: boolean; content_mode: string; evidence_rule: JsonObject
  basis_refs: Basis[]; nature: string; content_requirements: string[]
}
export interface TemplateField {
  field_id: string; node_id: string; key: string; name: string; description: string; value_type: string
  constraints: JsonObject; condition: TemplateCondition; required: boolean; evidence_rule: JsonObject
  output_mapping: JsonObject; repeat_id: string | null; attachment_requirement_id: string | null; basis_refs: Basis[]
}
export interface TemplateRepeat {
  repeat_id: string; node_id: string; name: string; field_ids: string[]; min_count: string; max_count: string | null
  count_basis: Basis[]; row_id_required: boolean
}
export interface TemplateAttachment {
  attachment_requirement_id: string; node_id: string; name: string; kind: string; mode: 'AUTHORIZED_ORIGINAL_ONLY'
  condition: TemplateCondition; required: boolean; min_count: string; max_count: string | null; count_basis: Basis[]
  allowed_media_types: string[]; evidence_rule: JsonObject; ordinal: number
}
export interface TemplateContent {
  schema_version: '1.0.0'; document_type: string; application_path: string; ivd: boolean; directory_profile: string | null
  nodes: TemplateNode[]; field_definitions: TemplateField[]; repeat_definitions: TemplateRepeat[]
  attachment_requirements: TemplateAttachment[]; cross_document_rules: JsonObject[]; condition_schema: JsonObject
  basis_manifest: Basis[]; format_profile: JsonObject
}
export interface ReviewTemplate {
  tenant_id: string; template_version: string; status: TemplateStatus; revision: string; content: TemplateContent
  content_sha256: string; base_version: Basis | null; reason: string; authority_grant_id: string; authority_revision: string
  created_by: string; created_at: string; dependency_refs: Basis[]
}
export interface ApprovalBaseline {
  schema_version: '1.0.0'; template_version: string; content_sha256: string; definition_manifest_sha256: string
  dependency_refs: Basis[]; dependency_authorizations_sha256: string
}
export interface TemplatePreview { schema_version: '1.0.0'; template: ReviewTemplate; baseline_manifest: ApprovalBaseline; allowed_actions: string[] }
export interface ApprovalBody {
  schema_version: '1.0.0'; target_revision: string; expected_revision: string
  decision: TemplateDecision; reason: string; baseline_manifest: ApprovalBaseline
}
export interface PendingApproval {
  actor: string; tenant_id: string; template_version: string; operation: 'TP-02'; key: string; context: Context
  expected_revision: string; decision: TemplateDecision; body_sha256: string; content_sha256: string
}
export interface ApprovalReceipt {
  schema_version: '1.0.0'; operation_id: string; decision_id: string; template_version: string; decision: TemplateDecision
  status: Exclude<TemplateStatus, 'DRAFT'>; revision: string; content_sha256: string; actor_id: string; decided_at: string
  current_context: Context; allowed_actions: []
}

const schema = 'urn:medical:template-approval:1.0.0#/$defs/'
const pendingSchema = 'urn:medical:template-approval-view:1.0.0'
const prefix = 'medical.template.approval:'
const maximum = '18446744073709551615'
export const templateStatusLabels: Record<TemplateStatus, string> = { DRAFT: '草稿', APPROVED: '已核准', RETURNED: '已退回', DISABLED: '已停用' }
export const templateDecisionLabels: Record<TemplateDecision, string> = { APPROVE: '核准', RETURN: '退回', DISABLE: '停用' }

export function templateApprovalAllowed(session: MedicalSession | null, id?: string): boolean {
  if (!session?.current_context) return false
  const tenant = session.current_context.tenant_id
  return session.action_summary.some(item => item.action === 'template.approve' && item.project_id === null && (
    item.resource_kind === 'ENTERPRISE' && item.resource_id === tenant
    || item.resource_kind === 'TEMPLATE' && (item.resource_id === null || id === undefined || item.resource_id === id)
  ))
}
function current(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '模板审查需要当前企业身份')
  validContext(session.current_context)
  return session.current_context
}
function target(id: string): void { validate(schema + 'Id', id) }
function positive(value: string): void { if (revision(value) === '0') fail('SCHEMA_INVALID', '模板治理修订必须为正整数') }
function canonical(value: unknown): string { return canonicalizeEx(value, { strictUndefined: true }) }
async function digest(value: unknown): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical(value)))
  return Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('')
}
function key(session: MedicalSession, id: string): string { target(id); return prefix + session.user.user_id + ':' + current(session).tenant_id + ':' + id }

export function approvalDecisions(preview: TemplatePreview): TemplateDecision[] {
  if (!preview.allowed_actions.includes('template.approve')) return []
  switch (preview.template.status) {
    case 'DRAFT': return ['APPROVE', 'RETURN', 'DISABLE']
    case 'APPROVED': return ['RETURN', 'DISABLE']
    case 'RETURNED': return ['APPROVE', 'DISABLE']
    case 'DISABLED': return []
  }
}

export async function readTemplatePreview(session: MedicalSession, id: string, signal: AbortSignal): Promise<TemplatePreview> {
  const context = current(session); target(id)
  if (!templateApprovalAllowed(session, id)) fail('ACCESS_DENIED', '当前模板没有人工核准权限')
  const response = envelope(await request.get('/api/template-versions/' + id + '/approval-baseline', { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const value = validate<TemplatePreview>(schema + 'Preview', response.data)
  const stored = value.template; const baseline = value.baseline_manifest
  validate('urn:medical:templates:1.0.0#/$defs/Content', stored.content)
  positive(stored.revision); positive(stored.authority_revision)
  for (const repeat of stored.content.repeat_definitions) { revision(repeat.min_count); if (repeat.max_count !== null) revision(repeat.max_count) }
  for (const attachment of stored.content.attachment_requirements) { revision(attachment.min_count); if (attachment.max_count !== null) revision(attachment.max_count) }
  if (response.resource_revision !== stored.revision || stored.tenant_id !== context.tenant_id || stored.template_version !== id
      || baseline.template_version !== id || baseline.content_sha256 !== stored.content_sha256
      || await digest(stored.content) !== stored.content_sha256 || canonical(baseline.dependency_refs) !== canonical(stored.dependency_refs)
      || new Set(stored.dependency_refs.map(ref => ref.version_id)).size !== stored.dependency_refs.length
      || projectTextIssue(stored.reason, 2000)) fail('CONTENT_MISMATCH', '模板审查内容、摘要或固定版本不一致')
  return value
}

export function readPendingApproval(session: MedicalSession, id: string): PendingApproval | null {
  const saved = sessionStorage.getItem(key(session, id))
  if (saved === null) return null
  const value = validate<PendingApproval>(pendingSchema, JSON.parse(saved)); positive(value.expected_revision); validContext(value.context)
  if (value.actor !== session.user.user_id || value.tenant_id !== current(session).tenant_id
      || value.template_version !== id || value.context.tenant_id !== value.tenant_id) fail('CONTENT_MISMATCH', '原模板决定与当前身份不一致')
  return value
}
function checkBody(id: string, body: ApprovalBody): void {
  target(id); validate(schema + 'Decide', body); positive(body.expected_revision)
  if (body.target_revision !== id || body.baseline_manifest.template_version !== id || projectTextIssue(body.reason, 2000)) {
    fail('SCHEMA_INVALID', '模板决定目标、理由或审查清单无效')
  }
}
export async function prepareTemplateApproval(session: MedicalSession, id: string, body: ApprovalBody): Promise<PendingApproval> {
  const context = current(session); checkBody(id, body)
  if (!templateApprovalAllowed(session, id)) fail('ACCESS_DENIED', '当前模板没有人工核准权限')
  const hash = await digest(body); const saved = readPendingApproval(session, id)
  if (saved) {
    if (saved.body_sha256 !== hash) fail('IDEMPOTENCY_CONFLICT', '原模板决定仍待核查，请保留原参数和请求标识')
    return saved
  }
  if ([body.expected_revision, context.context_revision, context.governance_epoch].includes(maximum)) fail('REVISION_LIMIT', '模板或治理修订无法继续增加')
  const pending: PendingApproval = { actor: session.user.user_id, tenant_id: context.tenant_id, template_version: id, operation: 'TP-02',
    key: crypto.randomUUID(), context: { ...context }, expected_revision: body.expected_revision, decision: body.decision,
    body_sha256: hash, content_sha256: body.baseline_manifest.content_sha256 }
  validate(pendingSchema, pending)
  // 仅保存原请求身份及摘要；模板正文、审查理由和清单继续保存在当前页面内存。
  sessionStorage.setItem(key(session, id), JSON.stringify(pending))
  return pending
}
export async function submitTemplateApproval(session: MedicalSession, pending: PendingApproval, body: ApprovalBody): Promise<ApprovalReceipt> {
  const context = current(session); validate(pendingSchema, pending); checkBody(pending.template_version, body)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== context.tenant_id || pending.context.tenant_id !== context.tenant_id
      || pending.expected_revision !== body.expected_revision || pending.decision !== body.decision
      || pending.content_sha256 !== body.baseline_manifest.content_sha256 || await digest(body) !== pending.body_sha256
      || !templateApprovalAllowed(session, pending.template_version)) fail('CONTENT_MISMATCH', '原模板决定与当前主体、企业或参数不一致')
  const raw = await request.post('/api/template-versions/' + pending.template_version + '/approval-decisions', body, { medical: 'envelope',
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision,
      'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const response = envelope(raw); const result = validate<ApprovalReceipt>(schema + 'Receipt', response.data)
  validContext(result.current_context); positive(result.revision)
  const expectedStatus = { APPROVE: 'APPROVED', RETURN: 'RETURNED', DISABLE: 'DISABLED' } as const
  if (raw.status !== 200 || response.resource_revision !== result.revision || result.template_version !== pending.template_version
      || result.actor_id !== pending.actor || result.decision !== pending.decision || result.status !== expectedStatus[pending.decision]
      || result.content_sha256 !== pending.content_sha256 || result.current_context.tenant_id !== pending.tenant_id
      || result.revision !== (BigInt(pending.expected_revision) + 1n).toString()
      || result.current_context.context_revision !== (BigInt(pending.context.context_revision) + 1n).toString()
      || result.current_context.governance_epoch !== (BigInt(pending.context.governance_epoch) + 1n).toString()
      || result.current_context.auth_epoch !== pending.context.auth_epoch) fail('CONTENT_MISMATCH', '人工决定回执与原目标、状态或治理修订不一致')
  return result
}
export async function confirmTemplateApproval(session: MedicalSession, pending: PendingApproval, operationId: string, signal: AbortSignal): Promise<Operation> {
  const context = current(session)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== context.tenant_id) fail('ACCESS_DENIED', '原模板决定需要当前本人及企业身份')
  const operation = await readOperation(session, operationId, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== pending.template_version) fail('CONTENT_MISMATCH', '原模板操作尚未取得一致的完成结果')
  return operation
}
export function clearCompletedApproval(session: MedicalSession, pending: PendingApproval, operation: Operation): void {
  const saved = readPendingApproval(session, pending.template_version)
  if (!saved || saved.key !== pending.key || saved.body_sha256 !== pending.body_sha256
      || operation.status !== 'COMPLETED' || operation.resource_id !== pending.template_version) fail('CONTENT_MISMATCH', '原模板决定仍需核查')
  sessionStorage.removeItem(key(session, pending.template_version))
}

export { canonical as canonicalTemplateValue, digest as templateValueDigest }

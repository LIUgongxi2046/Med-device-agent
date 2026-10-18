import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { canonicalTemplateValue as canonical, templateValueDigest as digest } from './template-approval'
import type { JsonValue } from './template-approval'
import { projectTextIssue } from './project-write'
import { readOperation } from './operations'
import type { Operation } from './operations'

export const ruleEngine = 'spring-spel-6.2.19-boolean-v1'
export const ruleSchema = 'urn:medical:rule-version-workflow:1.0.0#/$defs/'
const contentSchema = 'urn:medical:rule-expressions:1.0.0#/$defs/Content'
const pendingSchema = 'urn:medical:rule-write-view:1.0.0'
export type RuleStatus = 'DRAFT' | 'APPROVED' | 'RETURNED' | 'DISABLED'
export type RuleDecision = 'APPROVE' | 'RETURN' | 'DISABLE'
export interface RuleConstant { key: string; value: Exclude<JsonValue, null>; basis_refs: Basis[] }
export interface RuleContent {
  schema_version: '1.0.0'; engine_revision: typeof ruleEngine
  expression: { schema_version: '1.0.0'; engine_revision: typeof ruleEngine; expression: string; input_keys: string[]; constants: RuleConstant[] }
  basis_manifest: Basis[]
}
export interface RuleCreateBody {
  schema_version: '1.0.0'; base_version: Basis | null; content_schema_ref: typeof contentSchema; content: RuleContent; reason: string
}
export interface StoredRule {
  tenant_id: string; rule_version: string; status: RuleStatus; revision: string; content: RuleContent; content_sha256: string
  base_version: Basis | null; reason: string; authority_grant_id: string; authority_revision: string; created_by: string; created_at: string; dependency_refs: Basis[]
}
export interface RuleBaseline {
  schema_version: '1.0.0'; rule_version: string; content_sha256: string; definition_manifest_sha256: string; dependency_refs: Basis[]; dependency_authorizations_sha256: string
}
export interface RuleRead { schema_version: '1.0.0'; rule: StoredRule; allowed_actions: [] }
export interface RulePreview { schema_version: '1.0.0'; rule: StoredRule; baseline_manifest: RuleBaseline; allowed_actions: string[] }
export interface RuleCreated extends RuleRead { operation_id: string }
export interface RuleApprovalBody {
  schema_version: '1.0.0'; target_revision: string; expected_revision: string; decision: RuleDecision; reason: string; baseline_manifest: RuleBaseline
}
export interface RuleReceipt {
  schema_version: '1.0.0'; operation_id: string; decision_id: string; rule_version: string; decision: RuleDecision; status: Exclude<RuleStatus, 'DRAFT'>
  revision: string; content_sha256: string; actor_id: string; decided_at: string; current_context: Context; allowed_actions: []
}
export interface PendingRuleWrite {
  actor: string; tenant_id: string; operation: 'TP-03' | 'TP-04'; key: string; context: Context; target_id: string | null
  expected_revision: string | null; decision: RuleDecision | null; body_sha256: string; content_sha256: string
}
export const ruleStatusLabels: Record<RuleStatus, string> = { DRAFT: '草稿', APPROVED: '已核准', RETURNED: '已退回', DISABLED: '已停用' }
export const ruleDecisionLabels: Record<RuleDecision, string> = { APPROVE: '核准', RETURN: '退回', DISABLE: '停用' }

export function ruleAllowed(session: MedicalSession | null, action: 'rule.read' | 'rule.write' | 'rule.approve', id?: string): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => item.action === action && item.project_id === null && (
    item.resource_kind === 'ENTERPRISE' && item.resource_id === session.current_context?.tenant_id
    || action !== 'rule.write' && item.resource_kind === 'RULE' && (item.resource_id === null || id === undefined || item.resource_id === id)))
}
function current(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '规则操作需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
function positive(value: string): void { if (revision(value) === '0') fail('SCHEMA_INVALID', '规则治理修订必须为正整数') }
function storageKey(session: MedicalSession, id: string | null): string {
  if (id !== null) validate(ruleSchema + 'Id', id)
  return 'medical.rule.write:' + session.user.user_id + ':' + current(session).tenant_id + ':' + (id ?? 'new')
}
export function ruleDependencies(body: Pick<RuleCreateBody, 'base_version' | 'content'>): Basis[] {
  const refs = new Map<string, Basis>()
  for (const ref of [...(body.base_version ? [body.base_version] : []), ...body.content.basis_manifest, ...body.content.expression.constants.flatMap(value => value.basis_refs)]) {
    validate(ruleSchema + 'Basis', ref)
    if (refs.has(ref.version_id) && refs.get(ref.version_id)?.content_sha256 !== ref.content_sha256) fail('CONTENT_MISMATCH', '同一规则依据版本包含不同摘要')
    refs.set(ref.version_id, ref)
  }
  return [...refs.values()].sort((a, b) => a.version_id.localeCompare(b.version_id))
}
export function validateRuleCreate(body: unknown): RuleCreateBody {
  const value = validate<RuleCreateBody>(ruleSchema + 'VersionInput', body)
  validate(contentSchema, value.content)
  if (projectTextIssue(value.reason, 2000) || projectTextIssue(value.content.expression.expression, 10000)) fail('SCHEMA_INVALID', '规则表达式或创建理由无效')
  const constants = value.content.expression.constants
  if (new Set(constants.map(item => item.key)).size !== constants.length) fail('SCHEMA_INVALID', '规则常量标识不能重复')
  const manifest = new Map(value.content.basis_manifest.map(ref => [ref.version_id, ref.content_sha256]))
  if (manifest.size !== value.content.basis_manifest.length || constants.some(item => item.basis_refs.some(ref => manifest.get(ref.version_id) !== ref.content_sha256))) {
    fail('SCHEMA_INVALID', '常量依据必须属于完整规则依据清单，清单中的版本不能重复')
  }
  ruleDependencies(value); return value
}
async function checkStored(session: MedicalSession, id: string, value: StoredRule, resourceRevision: string | null): Promise<void> {
  validate(ruleSchema + 'StoredRule', value); positive(value.revision); positive(value.authority_revision)
  const body = validateRuleCreate({ schema_version: '1.0.0', content_schema_ref: contentSchema, content: value.content, base_version: value.base_version, reason: value.reason })
  if (value.tenant_id !== current(session).tenant_id || value.rule_version !== id || value.revision !== resourceRevision
      || await digest(value.content) !== value.content_sha256
      || canonical(ruleDependencies(body)) !== canonical([...value.dependency_refs].sort((a, b) => a.version_id.localeCompare(b.version_id)))) {
    fail('CONTENT_MISMATCH', '规则内容、固定依据、企业或治理修订不一致')
  }
}
export async function readRule(session: MedicalSession, id: string, signal: AbortSignal): Promise<RuleRead> {
  validate(ruleSchema + 'Id', id)
  if (!ruleAllowed(session, 'rule.read', id)) fail('ACCESS_DENIED', '当前规则没有读取权限')
  const response = envelope(await request.get('/api/rule-versions/' + id, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const result = validate<RuleRead>(ruleSchema + 'Read', response.data)
  await checkStored(session, id, result.rule, response.resource_revision); return result
}
export async function readRulePreview(session: MedicalSession, id: string, signal: AbortSignal): Promise<RulePreview> {
  validate(ruleSchema + 'Id', id)
  if (!ruleAllowed(session, 'rule.approve', id)) fail('ACCESS_DENIED', '当前规则没有人工核准权限')
  const response = envelope(await request.get('/api/rule-versions/' + id + '/approval-baseline', { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const value = validate<RulePreview>(ruleSchema + 'Preview', response.data)
  await checkStored(session, id, value.rule, response.resource_revision)
  if (value.baseline_manifest.rule_version !== id || value.baseline_manifest.content_sha256 !== value.rule.content_sha256
      || canonical(value.baseline_manifest.dependency_refs) !== canonical(value.rule.dependency_refs)) fail('CONTENT_MISMATCH', '规则审查清单与固定内容不一致')
  return value
}
export function ruleDecisions(preview: RulePreview): RuleDecision[] {
  if (!preview.allowed_actions.includes('rule.approve')) return []
  switch (preview.rule.status) {
    case 'DRAFT': return ['APPROVE', 'RETURN', 'DISABLE']
    case 'APPROVED': return ['RETURN', 'DISABLE']
    case 'RETURNED': return ['APPROVE', 'DISABLE']
    case 'DISABLED': return []
  }
}
export function readPendingRule(session: MedicalSession, id: string | null): PendingRuleWrite | null {
  const saved = sessionStorage.getItem(storageKey(session, id)); if (saved === null) return null
  const value = validate<PendingRuleWrite>(pendingSchema, JSON.parse(saved)); validContext(value.context)
  if (value.expected_revision !== null) positive(value.expected_revision)
  if (value.actor !== session.user.user_id || value.tenant_id !== current(session).tenant_id || value.context.tenant_id !== value.tenant_id
      || value.target_id !== id || value.operation !== (id === null ? 'TP-03' : 'TP-04')) fail('CONTENT_MISMATCH', '原规则请求与当前主体或企业不一致')
  return value
}
function checkApproval(body: RuleApprovalBody): void {
  validate(ruleSchema + 'Decide', body); positive(body.expected_revision)
  if (body.target_revision !== body.baseline_manifest.rule_version || projectTextIssue(body.reason, 2000)) fail('SCHEMA_INVALID', '规则决定目标、理由或审查清单无效')
}
export async function prepareRuleWrite(session: MedicalSession, body: RuleCreateBody | RuleApprovalBody): Promise<PendingRuleWrite> {
  const context = current(session); const approval = 'decision' in body; const id = approval ? body.target_revision : null
  if (approval) checkApproval(body); else validateRuleCreate(body)
  if (!ruleAllowed(session, approval ? 'rule.approve' : 'rule.write', id ?? undefined)) fail('ACCESS_DENIED', '当前规则操作缺少明确权限')
  const hash = await digest(body); const saved = readPendingRule(session, id)
  if (saved) {
    if (saved.body_sha256 !== hash || canonical(saved.context) !== canonical(context)) fail('IDEMPOTENCY_CONFLICT', '原规则请求待核查，请保留完整原参数、上下文和请求标识')
    return saved
  }
  if (approval && [body.expected_revision, context.context_revision, context.governance_epoch].includes('18446744073709551615')) fail('REVISION_LIMIT', '规则或治理修订无法继续增加')
  const value: PendingRuleWrite = { actor: session.user.user_id, tenant_id: context.tenant_id, operation: approval ? 'TP-04' : 'TP-03', key: crypto.randomUUID(),
    context: { ...context }, target_id: id, expected_revision: approval ? body.expected_revision : null, decision: approval ? body.decision : null,
    body_sha256: hash, content_sha256: approval ? body.baseline_manifest.content_sha256 : await digest(body.content) }
  validate(pendingSchema, value)
  // Only request identity and hashes persist. Rule text, source details and reasons stay in page memory.
  sessionStorage.setItem(storageKey(session, id), JSON.stringify(value)); return value
}
async function verifyWrite(session: MedicalSession, pending: PendingRuleWrite, body: RuleCreateBody | RuleApprovalBody): Promise<void> {
  validate(pendingSchema, pending); validContext(pending.context)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== current(session).tenant_id || canonical(pending.context) !== canonical(current(session))
      || !ruleAllowed(session, pending.operation === 'TP-03' ? 'rule.write' : 'rule.approve', pending.target_id ?? undefined)
      || await digest(body) !== pending.body_sha256) fail('CONTENT_MISMATCH', '原规则请求与当前身份、上下文或完整参数不一致')
  const saved = readPendingRule(session, pending.target_id)
  if (!saved || canonical(saved) !== canonical(pending)) fail('CONTENT_MISMATCH', '规则请求标识需要重新核查')
}
function headers(session: MedicalSession, pending: PendingRuleWrite): Record<string, string> {
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision, 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token }
}
export async function submitRuleCreate(session: MedicalSession, pending: PendingRuleWrite, body: RuleCreateBody): Promise<RuleCreated> {
  validateRuleCreate(body); await verifyWrite(session, pending, body)
  if (pending.operation !== 'TP-03' || pending.target_id !== null || pending.content_sha256 !== await digest(body.content)) fail('CONTENT_MISMATCH', '原规则创建身份不一致')
  const raw = await request.post('/api/rule-versions', body, { medical: 'envelope', headers: headers(session, pending) })
  const response = envelope(raw); const value = validate<RuleCreated>(ruleSchema + 'Created', response.data)
  await checkStored(session, value.rule.rule_version, value.rule, response.resource_revision)
  if (raw.status !== 201 || value.rule.revision !== '1' || value.rule.status !== 'DRAFT' || value.rule.created_by !== pending.actor
      || value.rule.content_sha256 !== pending.content_sha256 || canonical(value.rule.content) !== canonical(body.content)
      || canonical(value.rule.base_version) !== canonical(body.base_version) || value.rule.reason !== body.reason) fail('CONTENT_MISMATCH', '规则创建回执与原参数或主体不一致')
  return value
}
export async function submitRuleApproval(session: MedicalSession, pending: PendingRuleWrite, body: RuleApprovalBody): Promise<RuleReceipt> {
  checkApproval(body); await verifyWrite(session, pending, body)
  if (pending.operation !== 'TP-04' || pending.target_id !== body.target_revision || pending.expected_revision !== body.expected_revision
      || pending.decision !== body.decision || pending.content_sha256 !== body.baseline_manifest.content_sha256) fail('CONTENT_MISMATCH', '原规则决定目标或基线不一致')
  const raw = await request.post('/api/rule-versions/' + pending.target_id + '/approval-decisions', body, { medical: 'envelope', headers: headers(session, pending) })
  const response = envelope(raw); const value = validate<RuleReceipt>(ruleSchema + 'Receipt', response.data); validContext(value.current_context); positive(value.revision)
  const statuses = { APPROVE: 'APPROVED', RETURN: 'RETURNED', DISABLE: 'DISABLED' } as const
  if (raw.status !== 200 || response.resource_revision !== value.revision || value.rule_version !== pending.target_id || value.actor_id !== pending.actor
      || value.decision !== body.decision || value.status !== statuses[body.decision] || value.content_sha256 !== pending.content_sha256
      || value.revision !== (BigInt(body.expected_revision) + 1n).toString() || value.current_context.tenant_id !== pending.tenant_id
      || value.current_context.context_revision !== (BigInt(pending.context.context_revision) + 1n).toString()
      || value.current_context.governance_epoch !== (BigInt(pending.context.governance_epoch) + 1n).toString()
      || value.current_context.auth_epoch !== pending.context.auth_epoch) fail('CONTENT_MISMATCH', '规则决定回执与原目标或治理修订不一致')
  return value
}
export async function confirmRuleOperation(session: MedicalSession, pending: PendingRuleWrite, operationId: string, signal: AbortSignal): Promise<Operation> {
  validate(pendingSchema, pending)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== current(session).tenant_id) fail('ACCESS_DENIED', '原规则操作需要当前本人及企业身份')
  const operation = await readOperation(session, operationId, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id === null || pending.target_id !== null && operation.resource_id !== pending.target_id) fail('CONTENT_MISMATCH', '规则操作未取得一致的完成结果')
  return operation
}
export function clearRuleOperation(session: MedicalSession, pending: PendingRuleWrite, operation: Operation, rule: StoredRule): void {
  const saved = readPendingRule(session, pending.target_id)
  if (!saved || canonical(saved) !== canonical(pending) || operation.status !== 'COMPLETED' || operation.resource_id !== rule.rule_version
      || rule.tenant_id !== pending.tenant_id || rule.content_sha256 !== pending.content_sha256
      || pending.operation === 'TP-03' && rule.created_by !== pending.actor
      || pending.operation === 'TP-04' && (rule.rule_version !== pending.target_id || BigInt(rule.revision) <= BigInt(pending.expected_revision!))) fail('CONTENT_MISMATCH', '原规则操作结果仍需核查')
  sessionStorage.removeItem(storageKey(session, pending.target_id))
}
export { canonical as canonicalRule, digest as ruleDigest }

// Only schema-defined rule references produce rule links; generic source bases do not.
export function referencedTemplateRules(content: unknown): Basis[] {
  const refs = new Map<string, Basis>()
  function add(value: unknown): void {
    if (value === null) return
    const basis = validate<Basis>(ruleSchema + 'Basis', value)
    const previous = refs.get(basis.version_id)
    if (previous && previous.content_sha256 !== basis.content_sha256) fail('CONTENT_MISMATCH', '模板引用的同一规则版本摘要不一致')
    refs.set(basis.version_id, basis)
  }
  function visit(value: unknown): void {
    if (value === null || typeof value !== 'object') return
    if (Array.isArray(value)) { value.forEach(visit); return }
    for (const [key, child] of Object.entries(value)) {
      if (['rule_revision', 'calculation_rule_revision', 'numbering_rule_revision'].includes(key)) add(child)
      else if (key === 'verification_rule_revisions' && Array.isArray(child)) child.forEach(add)
      else visit(child)
    }
  }
  visit(content); return [...refs.values()].sort((a, b) => a.version_id.localeCompare(b.version_id))
}

export interface RuleListQuery { limit?: number; after?: string; q?: string }
export interface RuleList {
  schema_version: '1.0.0'; tenant_id: string; current_context: Context; items: RuleRead[]; next_after: string | null
}
export async function readRuleList(session: MedicalSession, query: RuleListQuery, signal: AbortSignal): Promise<RuleList> {
  const context = current(session)
  validate('urn:medical:configuration-discovery:1.0.0#/$defs/Query', query)
  if (!ruleAllowed(session, 'rule.read')) fail('ACCESS_DENIED', '当前企业没有规则列表读取权限')
  const response = envelope(await request.get('/api/rule-versions', { medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const result = validate<RuleList>('urn:medical:configuration-discovery:1.0.0#/$defs/RuleList', response.data)
  validContext(result.current_context)
  if (response.resource_revision !== context.context_revision || result.tenant_id !== context.tenant_id
      || canonical(result.current_context) !== canonical(context) || result.items.length > (query.limit ?? 50)
      || result.next_after !== null && result.next_after === query.after
      || new Set(result.items.map(item => item.rule.rule_version)).size !== result.items.length) fail('CONTENT_MISMATCH', '规则列表与当前企业、上下文或分页不一致')
  let prior = ''
  for (const item of result.items) {
    if (prior && item.rule.rule_version <= prior || !ruleAllowed(session, 'rule.read', item.rule.rule_version)) fail('CONTENT_MISMATCH', '规则列表顺序或当前读取范围不一致')
    await checkStored(session, item.rule.rule_version, item.rule, item.rule.revision); prior = item.rule.rule_version
  }
  return result
}

export { ruleConstantJsonIssue, readRuleConstantJson } from './rule-values'

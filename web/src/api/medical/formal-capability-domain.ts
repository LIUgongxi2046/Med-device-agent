import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import { canonicalizeEx } from 'json-canonicalize'
import contract from '../../../../business/medical-persistence/src/main/resources/agent-publication.schema.json'
import cancellationSchema from '../../../../business/medical-persistence/src/main/resources/formal-capability-cancellation.schema.json'
import availabilitySchema from '../../../../business/medical-persistence/src/main/resources/agent-authoring-feature-availability.schema.json'
import catalogue from '../../../../contracts/catalogue.json'
import { fail, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
import { projectJsonIssue } from './project-json'

export const capabilityOperation = 'AGENT-PUBLICATION-FORMAL_CAPABILITY_AUTHOR' as const
export const capabilityRecordKind = 'FORMAL_CAPABILITY_AUTHOR' as const
export interface CapabilityPermission {
  scope: { tenant_id: string; project_id: string | null; resource_kind: string; resource_id: string }
  action: string; purpose: string
}
export interface FormalCapability {
  schema_version: '1.0.0'; execution_mode: 'PRODUCTION'; project_id: string | null; kind: 'ROLE' | 'PROMPT'
  source_artifact: Basis; input_schema_ref: { schema_ref: string; schema_sha256: string }
  output_schema_ref: { schema_ref: string; schema_sha256: string }
  tool_scope: { schema_version: '1.0.0'; permissions: CapabilityPermission[] } | never[]; reason: string
}
export interface FormalCapabilityRecord {
  schema_version: '1.0.0'; record_id: string; resource_id: string; kind: typeof capabilityRecordKind
  content_sha256: string; status: 'DRAFT'; production_eligible: false
  content: { schema_version: '1.0.0'; tenant_id: string; config_id: string; version_id: string; created_by: string; created_at: string; request: FormalCapability }
}
export interface CapabilityReceipt {
  schema_version: '1.0.0'; operation_id: string; resource_id: string; kind: typeof capabilityRecordKind
  content_sha256: string; request_body_sha256: string
}
export interface CapabilityPage { schema_version: '1.0.0'; items: FormalCapabilityRecord[]; next_cursor: string | null }
export interface CapabilityAvailability { schema_version: '1.0.0'; current_context: Context; features: { agent_authoring: boolean } }
export interface PendingCapability {
  schema_version: '1.0.0'; actor: string; tenant_id: string; context: Context; key: string
  operation: typeof capabilityOperation; body: FormalCapability; body_sha256: string; phase: 'PENDING' | 'UNKNOWN'; receipt: CapabilityReceipt | null
}
export type CapabilitySessionReader = () => MedicalSession
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true })
addFormats(ajv); ajv.addSchema(contract); ajv.addSchema(availabilitySchema); ajv.addSchema(cancellationSchema)
const prefix = contract.$id + '#/$defs/'
const pendingSchema = {
  type: 'object', additionalProperties: false,
  required: ['schema_version', 'actor', 'tenant_id', 'context', 'key', 'operation', 'body', 'body_sha256', 'phase', 'receipt'],
  properties: { schema_version: { const: '1.0.0' }, actor: { $ref: prefix + 'Id' }, tenant_id: { $ref: prefix + 'Id' }, context: { $ref: prefix + 'Context' },
    key: { $ref: prefix + 'Id' }, operation: { const: capabilityOperation }, body: { $ref: prefix + 'FormalCapability' }, body_sha256: { $ref: prefix + 'Hash' },
    phase: { enum: ['PENDING', 'UNKNOWN'] }, receipt: { anyOf: [{ $ref: prefix + 'Receipt' }, { type: 'null' }] } }
}
const checkPending = ajv.compile<PendingCapability>(pendingSchema)
export interface CapabilityCancelRequest { schema_version: '1.0.0'; original_kind: 'FORMAL_CAPABILITY_AUTHOR'; original_key: string; original_request: FormalCapability }
export interface PendingCapabilityCancellation { type: 'CANCEL'; key: string; original: PendingCapability; body: CapabilityCancelRequest; body_sha256: string }
const checkCancellation = ajv.compile<PendingCapabilityCancellation>({ type:'object',additionalProperties:false,required:['type','key','original','body','body_sha256'],properties:{type:{const:'CANCEL'},key:{$ref:prefix+'Id'},original:pendingSchema,body:{$ref:cancellationSchema.$id+'#/$defs/CancelRequest'},body_sha256:{$ref:prefix+'Hash'}} })
export function validateCapabilityCancellation<T>(type: string, value: unknown): T { const check = ajv.getSchema(cancellationSchema.$id+'#/$defs/'+type); if (!check || !check(value)) fail('CONTENT_MISMATCH','取消回执不符合固定合同'); return value as T }
export function readCapabilityCancellation(session: MedicalSession): PendingCapabilityCancellation | null {
 const raw=sessionStorage.getItem(storageKey(session));if(raw===null)return null
 if(new TextEncoder().encode(raw).byteLength>2200000 || projectJsonIssue(raw))fail('CONTENT_MISMATCH','原请求恢复记录损坏')
 const value:unknown=JSON.parse(raw);if(!value || typeof value!=='object' || !('type' in value))return null
 if(!checkCancellation(value))fail('CONTENT_MISMATCH','取消原请求记录损坏')
 const pending=value as PendingCapabilityCancellation
 if(pending.original.actor!==session.user.user_id || pending.original.tenant_id!==capabilityContext(session).tenant_id || pending.body.original_key!==pending.original.key || capabilityCanonical(pending.body.original_request)!==capabilityCanonical(pending.original.body))fail('CONTENT_MISMATCH','取消请求的原身份或完整正文不一致')
 return detached(pending)
}
export function assertNoCapabilityCancellation(session: MedicalSession): void { if(readCapabilityCancellation(session))fail('ACTION_STALE','已发起原请求取消，请先核查取消回执') }
export const capabilitySchemas = catalogue.schemas.map(item => ({ label: item.name, schema_ref: catalogue.base_uri + item.path, schema_sha256: item.sha256 }))
export function capabilityCanonical(value: unknown): string { return canonicalizeEx(value, { strictUndefined: true }) }
export function detached<T>(value: T): T { return JSON.parse(capabilityCanonical(value)) as T }
export function capabilityValidate<T>(name: string, value: unknown): T {
  const check = ajv.getSchema(prefix + name)
  if (!check || !check(value)) fail('SCHEMA_INVALID', '正式能力合同不匹配：' + (check?.errors?.[0]?.instancePath || name))
  return value as T
}
export function capabilityInputIssue(value: unknown): string {
  const check = ajv.getSchema(prefix + 'FormalCapability')!
  if (!check(value)) return '请完整填写能力声明：' + (check.errors?.[0]?.instancePath || '请求格式')
  const body = value as FormalCapability
  if (!body.reason.trim() || body.reason.trim() !== body.reason || body.reason.normalize('NFC') !== body.reason || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(body.reason)) return '请填写规范、非空的登记原因'
  for (const binding of [body.input_schema_ref, body.output_schema_ref]) if (!capabilitySchemas.some(item => item.schema_ref === binding.schema_ref && item.schema_sha256 === binding.schema_sha256)) return '输入和输出必须选择已发布的真实契约'
  if (new TextEncoder().encode(capabilityCanonical(body)).byteLength > 1048576) return '能力声明超过 1 MiB 上限'
  return ''
}
export function capabilityContext(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '请先选择当前企业')
  validContext(session.current_context); return session.current_context
}
export function capabilityAllowed(session: MedicalSession | null, project: string | null): boolean {
  const tenant = session?.current_context?.tenant_id
  return !!tenant && !!session?.action_summary.some(item => item.action === 'capability.configure' && (
    item.resource_kind === 'ENTERPRISE' && item.resource_id === tenant && item.project_id === null
    || project !== null && item.resource_kind === 'PROJECT' && item.resource_id === project && item.project_id === project))
}
/** Exact concrete current scopes only. A wildcard summary is not a resource selection or a new grant. */
export function capabilityScopeCandidates(session: MedicalSession, project: string | null): Omit<CapabilityPermission, 'purpose'>[] {
  const tenant = capabilityContext(session).tenant_id, seen = new Set<string>()
  return session.action_summary.flatMap(item => {
    const value = { scope: { tenant_id: tenant, project_id: item.project_id, resource_kind: item.resource_kind, resource_id: item.resource_id }, action: item.action }
    if (item.resource_id === null || item.project_id !== null && item.project_id !== project || !ajv.getSchema(prefix + 'RuntimeScope')!(value.scope) || !ajv.getSchema(prefix + 'RuntimeAction')!(value.action)) return []
    if (item.resource_kind === 'ENTERPRISE' && (item.resource_id !== tenant || item.project_id !== null) || item.resource_kind === 'PROJECT' && (item.project_id === null || item.project_id !== item.resource_id)) return []
    const key = capabilityCanonical(value); if (seen.has(key)) return []; seen.add(key)
    return [value as Omit<CapabilityPermission, 'purpose'>]
  })
}
export function assertCapabilityScopes(session: MedicalSession, body: FormalCapability): void {
  if (body.kind !== 'ROLE' || Array.isArray(body.tool_scope)) return
  const candidates = capabilityScopeCandidates(session, body.project_id)
  for (const { scope, action } of body.tool_scope.permissions) if (!candidates.some(item => capabilityCanonical(item) === capabilityCanonical({ scope, action }))) fail('ACCESS_DENIED', '角色权限必须选择当前身份已有的精确资源与动作；用途仍由服务端逐项核验')
}
export function capabilityGuard(reader: CapabilitySessionReader, signal: AbortSignal): { session: MedicalSession; context: Context; assert: () => void } {
  const session = reader(), context = detached(capabilityContext(session)), actor = session.user.user_id
  const assert = () => { signal.throwIfAborted(); const current = reader(); if (current !== session || current.user.user_id !== actor || capabilityCanonical(current.current_context) !== capabilityCanonical(context)) fail('CONTEXT_CHANGED', '当前身份或授权上下文已变化，请重新读取') }
  assert(); return { session, context, assert }
}
export function storageKey(session: MedicalSession): string { return 'medical.formal-capability.pending:' + session.user.user_id + ':' + capabilityContext(session).tenant_id }
export function readPendingCapability(session: MedicalSession): PendingCapability | null {
  const raw = sessionStorage.getItem(storageKey(session)); if (raw === null) return null
  if (new TextEncoder().encode(raw).byteLength > 2200000) fail('CONTENT_MISMATCH', '原请求记录超过允许容量，请保留记录并人工核查')
  if (projectJsonIssue(raw)) fail('CONTENT_MISMATCH', '原请求记录含有无效或重复字段，不能自动重新创建')
  let value: unknown; try { value = JSON.parse(raw) } catch { fail('CONTENT_MISMATCH', '原请求记录损坏，不能自动重新创建') }
  if(value && typeof value==='object' && 'type' in value){const wrapper=readCapabilityCancellation(session);if(!wrapper)fail('CONTENT_MISMATCH','恢复记录类型不一致');value=wrapper.original}
  if (!checkPending(value)) fail('CONTENT_MISMATCH', '原请求记录不符合固定恢复合同')
  const pending = value as PendingCapability
  validContext(pending.context)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== capabilityContext(session).tenant_id || pending.context.tenant_id !== pending.tenant_id
      || capabilityInputIssue(pending.body) || pending.receipt !== null && pending.receipt.kind !== capabilityRecordKind) fail('CONTENT_MISMATCH', '原请求的身份、声明或范围不一致')
  return detached(pending)
}
export async function verifyPendingCapability(session: MedicalSession, pending: PendingCapability): Promise<void> {
  const actual = readPendingCapability(session)
  if (!actual || capabilityCanonical(actual) !== capabilityCanonical(pending) || await sourceDigest(pending.body) !== pending.body_sha256) fail('CONTENT_MISMATCH', '原请求正文、摘要或请求标识已变化')
  if (pending.receipt) verifyCapabilityReceipt(pending, pending.receipt, '1')
}
function persist(session: MedicalSession, pending: PendingCapability): void {
  assertNoCapabilityCancellation(session)
  if (!checkPending(pending)) fail('CONTENT_MISMATCH', '不能保存不完整的原请求')
  sessionStorage.setItem(storageKey(session), capabilityCanonical(pending))
  if (capabilityCanonical(readPendingCapability(session)) !== capabilityCanonical(pending)) fail('CONTENT_MISMATCH', '原请求未能可靠保存，本次不会发送')
}
export async function prepareCapability(reader: CapabilitySessionReader, body: FormalCapability, signal: AbortSignal): Promise<PendingCapability> {
  const guard = capabilityGuard(reader, signal), frozen = detached(body), issue = capabilityInputIssue(frozen)
  if (issue) fail('SCHEMA_INVALID', issue)
  if (!capabilityAllowed(guard.session, frozen.project_id)) fail('ACCESS_DENIED', '当前范围没有登记正式能力的权限')
  assertCapabilityScopes(guard.session, frozen)
  if (readPendingCapability(guard.session)) fail('IDEMPOTENCY_CONFLICT', '已有待核查原请求，只能读取原结果，不能创建或重发')
  const digest = await sourceDigest(frozen); guard.assert()
  if (readPendingCapability(guard.session)) fail('IDEMPOTENCY_CONFLICT', '另一请求已保存，请先核查原操作')
  const pending: PendingCapability = { schema_version: '1.0.0', actor: guard.session.user.user_id, tenant_id: guard.context.tenant_id,
    context: guard.context, key: crypto.randomUUID(), operation: capabilityOperation, body: frozen, body_sha256: digest, phase: 'PENDING', receipt: null }
  persist(guard.session, pending); return detached(pending)
}
/** UNKNOWN is saved before network dispatch. Reload/recovery never invokes this transition. */
export async function markCapabilityDispatched(reader: CapabilitySessionReader, pending: PendingCapability, signal: AbortSignal): Promise<PendingCapability> {
  const guard = capabilityGuard(reader, signal); await verifyPendingCapability(guard.session, pending); guard.assert()
  if (pending.phase !== 'PENDING' || capabilityCanonical(pending.context) !== capabilityCanonical(guard.context)) fail('ACTION_STALE', '原请求已发送或上下文已变化，只能读取恢复')
  const next: PendingCapability = { ...detached(pending), phase: 'UNKNOWN' }; persist(guard.session, next); return next
}
export function verifyCapabilityReceipt(pending: PendingCapability, input: unknown, resourceRevision: string | null): CapabilityReceipt {
  const receipt = capabilityValidate<CapabilityReceipt>('Receipt', input)
  if (receipt.kind !== capabilityRecordKind || resourceRevision !== '1' || receipt.request_body_sha256 !== pending.body_sha256) fail('CONTENT_MISMATCH', '类型回执与原始声明摘要不一致')
  return detached(receipt)
}
export async function rememberCapabilityReceipt(reader: CapabilitySessionReader, pending: PendingCapability, receipt: CapabilityReceipt, signal: AbortSignal): Promise<PendingCapability> {
  const guard = capabilityGuard(reader, signal); await verifyPendingCapability(guard.session, pending); guard.assert(); verifyCapabilityReceipt(pending, receipt, '1')
  const next = { ...detached(pending), receipt: detached(receipt) }; persist(guard.session, next); return next
}
export async function verifyCapabilityRecord(session: MedicalSession, input: unknown, resourceRevision: string | null, recordId?: string): Promise<FormalCapabilityRecord> {
  const value = capabilityValidate<FormalCapabilityRecord>('RecordView', input)
  if (value.kind !== capabilityRecordKind || value.status !== 'DRAFT' || value.production_eligible !== false) fail('CONTENT_MISMATCH', '响应不是正式能力草稿')
  capabilityValidate('FormalCapabilityFixed', value.content)
  if (capabilityInputIssue(value.content.request) || value.content.tenant_id !== capabilityContext(session).tenant_id || value.content.version_id !== value.resource_id
      || recordId !== undefined && value.record_id !== recordId || resourceRevision !== '1' || await sourceDigest(value.content) !== value.content_sha256) fail('CONTENT_MISMATCH', '能力固定版本的身份、企业、声明或摘要不一致')
  return detached(value)
}
export function verifyCapabilityAvailability(input: unknown, context: Context, resourceRevision: string | null): CapabilityAvailability {
  const check = ajv.getSchema(availabilitySchema.$id)!
  if (!check(input)) fail('SCHEMA_INVALID', '正式能力可用性响应不符合固定合同')
  const value = input as CapabilityAvailability; validContext(value.current_context)
  if (capabilityCanonical(value.current_context) !== capabilityCanonical(context) || resourceRevision !== context.context_revision) fail('CONTENT_MISMATCH', '部署可用性不属于当前身份上下文')
  return detached(value)
}
export async function settleCapability(reader: CapabilitySessionReader, pending: PendingCapability, receipt: CapabilityReceipt, record: FormalCapabilityRecord, signal: AbortSignal): Promise<void> {
  const guard = capabilityGuard(reader, signal); await verifyPendingCapability(guard.session, pending); guard.assert()
  verifyCapabilityReceipt(pending, receipt, '1'); const checked = await verifyCapabilityRecord(guard.session, record, '1', receipt.resource_id); guard.assert()
  if (checked.content.created_by !== pending.actor || checked.content.request.project_id !== pending.body.project_id || checked.content_sha256 !== receipt.content_sha256
      || capabilityCanonical(checked.content.request) !== capabilityCanonical(pending.body) || await sourceDigest(checked.content.request) !== pending.body_sha256) fail('CONTENT_MISMATCH', '原操作回读结果与完整原声明不一致')
  guard.assert(); await verifyPendingCapability(guard.session, pending); guard.assert()
  assertNoCapabilityCancellation(guard.session)
  sessionStorage.removeItem(storageKey(guard.session))
}

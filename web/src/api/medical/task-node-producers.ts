import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import request from '@/utils/request'
import type { AxiosResponse } from 'axios'
import contract from '../../../../business/medical-persistence/src/main/resources/task-node-producer.schema.json'
import { envelope, fail, schemaIssues } from './protocol'
import type { ApiError } from './protocol'
import type { MedicalSession } from './protocol'
import { canonical, detached, fieldTargetGuard } from './task-field-target-domain'
import { sourceDigest } from './content-digest'
import { taskActionAllowed } from './task-detail'
import { readBudget } from './budgets'
import { projectTextIssue } from './project-text'
import type { InputAuthor, InputDecide, InputView, InputReceipt, VersionRef } from './task-node-producer-types'
export type * from './task-node-producer-types'
export { canonical }
type Reader = () => MedicalSession
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true }); addFormats(ajv); ajv.addSchema(contract)
export function checked<T>(name: string, value: unknown): T { const v = ajv.getSchema(contract.$id + '#/$defs/' + name); if (!v || !v(value)) fail('CONTENT_MISMATCH', '节点生产授权合同不匹配：' + name); return value as T }
type ChoiceKey = 'maximum_bytes' | 'maximum_artifacts' | 'maximum_tokens' | 'maximum_calls' | 'maximum_amount' | 'valid_from' | 'valid_until' | 'artifact_kinds' | 'operations' | 'reason' | 'enterprise_budget_basis'
export interface Preparation { pins: Omit<InputAuthor, ChoiceKey | 'expected_revision'>; ceilings: Record<'maximum_tokens' | 'maximum_calls' | 'maximum_amount' | 'maximum_artifacts' | 'valid_from' | 'valid_until', string>; choices: { artifact_kinds: InputAuthor['artifact_kinds']; operations: InputAuthor['operations']; required_selections: string[]; enterprise_budget_guidance: string } }
export interface OriginalRule { original_rule_id: string; original_rule_sha256: string; original_approval_sha256: string; reason: string; role_version: VersionRef; execution_policy: VersionRef; budget_revision: string; valid_from: string; valid_until: string }
export interface RuleItem { rule_id: string; rule_sha256: string; status: InputView['status']; revision: string; created_by: string; reason: string }
export interface PendingProducer { actor: string; tenant_id: string; project_id: string; task_id: string; kind: 'AUTHOR' | 'DECIDE'; resource_id: string | null; key: string; body: InputAuthor | InputDecide; body_sha256: string }
const base = (task: string) => { checked('Id', task); return '/api/tasks/' + task }
const headers = (s: MedicalSession) => ({ 'X-Schema-Version': '1.0.0', 'X-Context-Revision': s.current_context!.context_revision })
const storageKey = (s: MedicalSession, p: string, t: string) => ['medical-node-producer-v1', s.user.user_id, s.current_context?.tenant_id, p, t].join(':')
export function readPendingProducer(s: MedicalSession, p: string, t: string): PendingProducer | null {
  const raw = sessionStorage.getItem(storageKey(s, p, t)); if (!raw) return null
  let v: PendingProducer; try { v = JSON.parse(raw) } catch { return fail('CONTENT_MISMATCH', '原请求记录损坏，不能创建替代写入') }
  if (v.actor !== s.user.user_id || v.tenant_id !== s.current_context?.tenant_id || v.project_id !== p || v.task_id !== t || !['AUTHOR', 'DECIDE'].includes(v.kind) || (v.kind === 'DECIDE') !== (v.resource_id !== null)) fail('CONTENT_MISMATCH', '原请求归属无效')
  checked('Key', v.key); checked('Hash', v.body_sha256); checked(v.kind === 'AUTHOR' ? 'InputAuthor' : 'InputDecide', v.body)
  if (v.body.project_id !== p || v.kind === 'AUTHOR' && (v.body as InputAuthor).task_id !== t) fail('CONTENT_MISMATCH', '原请求目标无效')
  if (v.resource_id) checked('Id', v.resource_id)
  return v
}
async function get(reader: Reader, path: string, signal: AbortSignal) { const g = fieldTargetGuard(reader, signal); const raw = await request.get(path, { medical: 'envelope', signal, headers: headers(g.session) }); g.assert(); if (raw.status !== 200) fail('CONTENT_MISMATCH', '读取状态不匹配'); return envelope(raw) }
export async function listProducerRules(reader: Reader, project: string, task: string, original: boolean, signal: AbortSignal): Promise<OriginalRule[] | RuleItem[]> {
  const response = await get(reader, base(task) + (original ? '/node-producer-original-rules' : '/node-producer-rules'), signal)
  const v = response.data as { project_id: string; task_id: string; items: unknown[] }
  if (!v || v.project_id !== project || v.task_id !== task || !Array.isArray(v.items) || v.items.length > 1024) fail('CONTENT_MISMATCH', '授权目录归属或边界无效')
  const ids = new Set<string>()
  for (const item of v.items) { const r = item as OriginalRule & RuleItem; const id = original ? r.original_rule_id : r.rule_id; checked('Id', id); checked('Hash', original ? r.original_rule_sha256 : r.rule_sha256); if (ids.has(id) || typeof r.reason !== 'string') fail('CONTENT_MISMATCH', '授权目录身份重复或无效'); ids.add(id)
    if (original) { checked('Hash', r.original_approval_sha256); checked('VersionRef', r.role_version); checked('VersionRef', r.execution_policy); checked('Id', r.budget_revision); checked('Instant', r.valid_from); checked('Instant', r.valid_until) }
    else { checked('Positive', r.revision); checked('Id', r.created_by); if (!['DRAFT', 'APPROVED', 'DISABLED'].includes(r.status)) fail('CONTENT_MISMATCH', '授权状态无效') }
  }
  return v.items as OriginalRule[] | RuleItem[]
}
export async function readProducerPreparation(reader: Reader, project: string, task: string, original: string, signal: AbortSignal): Promise<Preparation> {
  checked('Id', original); const v = (await get(reader, base(task) + '/node-producer-preparation?original_rule_id=' + original, signal)).data as Preparation
  if (!v?.pins || v.pins.project_id !== project || v.pins.task_id !== task || v.pins.original_rule_id !== original || !v.ceilings || !v.choices || !Array.isArray(v.choices.artifact_kinds) || !Array.isArray(v.choices.operations)) fail('CONTENT_MISMATCH', '准备信息与所选原授权不符')
  // Validate fixed pins and enum choices through the actual closed author contract, without creating an authorization.
  checked('InputAuthor', { ...v.pins, ...v.ceilings, maximum_bytes: '1', expected_revision: '0', artifact_kinds: v.choices.artifact_kinds, operations: v.choices.operations, reason: '准备信息格式核查', enterprise_budget_basis: { version_id: original, content_sha256: v.pins.original_rule_sha256 } })
  return v
}
export async function readProducerRule(reader: Reader, project: string, task: string, id: string, signal: AbortSignal): Promise<InputView> {
  const g = fieldTargetGuard(reader, signal); checked('Id', id); const r = await get(reader, base(task) + '/node-producer-rules/' + id, signal); g.assert(); const v = checked<InputView>('InputView', r.data)
  if (v.manifest.tenant_id !== g.context.tenant_id || v.manifest.project_id !== project || v.manifest.task_id !== task || v.manifest.rule_id !== id || r.resource_revision !== v.revision || await sourceDigest(v.manifest) !== v.rule_sha256) fail('CONTENT_MISMATCH', '固定授权身份、内容或摘要不一致'); g.assert(); if ((v.status === 'DRAFT') !== (v.latest_decision_id === null) || v.allowed_actions.includes('APPROVE') && (v.status !== 'DRAFT')) fail('CONTENT_MISMATCH', '当前状态与独立决定权限不一致'); return v
}
const decimal = (v: string) => { if (!/^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$/.test(v)) fail('SCHEMA_INVALID', '金额格式无效'); const [a,b = ''] = v.split('.'); return BigInt(a!) * 1000000n + BigInt(b.padEnd(6, '0')) }
export function producerIssue(body: InputAuthor, prep: Preparation): string {
  try { const validator = ajv.getSchema(contract.$id + '#/$defs/InputAuthor'); if (!validator || !validator(body)) return '请完整填写全部授权边界、企业预算和理由'
    if (projectTextIssue(body.reason, 2000)) return '请填写完整、规范的理由'
    for (const key of Object.keys(prep.pins) as Array<keyof typeof prep.pins>) if (canonical(body[key]) !== canonical(prep.pins[key])) return '固定依据已变化，请重新准备'
    for (const key of ['maximum_bytes', 'maximum_artifacts', 'maximum_tokens', 'maximum_calls'] as const) if (BigInt(body[key]) > 18446744073709551615n) return '额度超过无符号整数上限'
    for (const key of ['maximum_artifacts', 'maximum_tokens', 'maximum_calls'] as const) if (BigInt(body[key]) > BigInt(prep.ceilings[key])) return '额度超过原授权上限'
    if (decimal(body.maximum_amount) > decimal(prep.ceilings.maximum_amount)) return '金额超过原授权上限'
    if (Date.parse(body.valid_until) <= Date.parse(body.valid_from) || Date.parse(body.valid_from) < Date.parse(prep.ceilings.valid_from) || Date.parse(body.valid_until) > Date.parse(prep.ceilings.valid_until)) return '有效期必须在原授权范围内'
    if (body.artifact_kinds.some(v => !prep.choices.artifact_kinds.includes(v)) || body.operations.some(v => !prep.choices.operations.includes(v))) return '只能选择当前允许的制品及消费操作'
    return ''
  } catch { return '请完整填写全部授权边界、企业预算和理由' }
}
async function verifyOriginal(reader: Reader, pending: PendingProducer, signal: AbortSignal) { const g = fieldTargetGuard(reader, signal); if (canonical(readPendingProducer(g.session, pending.project_id, pending.task_id)) !== canonical(pending) || await sourceDigest(pending.body) !== pending.body_sha256) fail('CONTENT_MISMATCH', '原请求完整内容不一致'); g.assert() }
async function verifyReceipt(reader: Reader, p: PendingProducer, raw: AxiosResponse<unknown>, signal: AbortSignal, recovery: boolean): Promise<InputReceipt | null> {
  const g = fieldTargetGuard(reader, signal), e = envelope(raw)
  if (recovery && e.data === null && raw.status === 200) return null
  const r = checked<InputReceipt>('InputReceipt', e.data)
  if (raw.status !== (recovery || p.kind === 'DECIDE' ? 200 : 201) || r.tenant_id !== p.tenant_id || r.project_id !== p.project_id || r.task_id !== p.task_id || r.created_by !== p.actor || r.request_sha256 !== p.body_sha256 || r.revision !== (BigInt(p.body.expected_revision) + 1n).toString() || e.resource_revision !== r.revision) fail('CONTENT_MISMATCH', '原操作回执不匹配')
  const v = await readProducerRule(reader, p.project_id, p.task_id, r.rule_id, signal); g.assert()
  if (v.rule_sha256 !== r.rule_sha256 || r.classification !== v.manifest.classification) fail('CONTENT_MISMATCH', '原回执固定声明不一致')
  // A historical receipt remains valid after a later decision. Never restore its old state.
  if (p.kind === 'AUTHOR') { if (r.status !== 'DRAFT' || r.decision_id !== null || v.manifest.created_by !== p.actor) fail('CONTENT_MISMATCH', '创作只允许登记未批准草稿'); for (const key of Object.keys(p.body) as Array<keyof InputAuthor>) { if (key === 'reason' || key === 'expected_revision') continue; if (canonical(v.manifest[key]) !== canonical((p.body as InputAuthor)[key])) fail('CONTENT_MISMATCH', '固定草稿与原请求不一致') } }
  else { const b = p.body as InputDecide; if (r.rule_id !== p.resource_id || r.rule_sha256 !== b.rule_sha256 || !r.decision_id || r.status !== (b.decision === 'APPROVE' ? 'APPROVED' : 'DISABLED') || b.decision === 'APPROVE' && v.manifest.created_by === p.actor) fail('CONTENT_MISMATCH', '决定不符合原请求或独立审批') }
  await verifyOriginal(reader, p, signal); g.assert(); sessionStorage.removeItem(storageKey(g.session, p.project_id, p.task_id)); return r
}
/** Only the freshly created, single-dispatch call site may archive a rejected attempt.
 * The header says nothing about earlier attempts. Recovery and readback never use it. */
async function archiveFirstDispatchRejection(reader: Reader, p: PendingProducer, raw: AxiosResponse<unknown>, signal: AbortSignal): Promise<void> {
  const h = raw.headers, value = raw.data as ApiError
  if (![403, 409, 422].includes(raw.status) || h['x-task-node-producer-outcome'] !== 'REJECTED_THIS_ATTEMPT') return
  if (h['cache-control'] !== 'no-store' || h['x-schema-version'] !== '1.0.0' || schemaIssues('urn:medical:shared#/$defs/Error', value).length
      || !['SCHEMA_INVALID', 'ACCESS_DENIED', 'REVISION_CONFLICT', 'SCOPE_STALE'].includes(value.code) || h['x-request-id'] !== value.request_id
      || h['x-task-node-producer-task'] !== p.task_id || h['x-task-node-producer-operation'] !== p.kind
      || (h['x-task-node-producer-rule'] ?? null) !== p.resource_id || h['x-task-node-producer-key'] !== p.key
      || h['x-task-node-producer-request-sha256'] !== p.body_sha256) return
  const g = fieldTargetGuard(reader, signal); await verifyOriginal(reader, p, signal); g.assert()
  const key = storageKey(g.session, p.project_id, p.task_id)
  const record = canonical({ original: p, first_dispatch_only: true, outcome: 'REJECTED_THIS_ATTEMPT', http_status: raw.status,
    request_id: value.request_id, error_code: value.code, wire_sha256: p.body_sha256 })
  const archiveKey = key + ':rejected:' + p.key
  // Failure to retain the rejection evidence must leave the active request untouched.
  sessionStorage.setItem(archiveKey, record)
  if (sessionStorage.getItem(archiveKey) !== record) fail('CONTENT_MISMATCH', '拒绝回执未能完整保存，原请求仍保留')
  g.assert(); sessionStorage.removeItem(key)
}
export async function submitProducer(reader: Reader, project: string, task: string, body: InputAuthor | InputDecide, resource: string | null, budgetId: string | null, signal: AbortSignal, onPending: (p: PendingProducer) => void): Promise<InputReceipt> {
  const g = fieldTargetGuard(reader, signal), kind = resource === null ? 'AUTHOR' : 'DECIDE'; body = detached(body); checked(kind === 'AUTHOR' ? 'InputAuthor' : 'InputDecide', body)
  if (body.project_id !== project || projectTextIssue(body.reason, 2000)) fail('SCHEMA_INVALID', '目标或理由不完整')
  if (readPendingProducer(g.session, project, task)) fail('IDEMPOTENCY_CONFLICT', '先核查原请求，不能另建替代写入')
  if (kind === 'AUTHOR') { const b = body as InputAuthor; if (!taskActionAllowed(g.session, project, task, 'task.node_producer.write')) fail('ACCESS_DENIED', '没有独立节点授权登记权限'); const prep = await readProducerPreparation(reader, project, task, b.original_rule_id, signal); g.assert(); const issue = producerIssue(b, prep); if (issue) fail('SCHEMA_INVALID', issue); if (!budgetId) fail('SCHEMA_INVALID', '必须明确选择企业预算'); const budget = await readBudget(g.session, budgetId, signal); g.assert(); if (budget.project_id !== null || budget.tenant_id !== g.context.tenant_id || budget.currency !== b.currency || budget.budget_revision !== b.enterprise_budget_basis.version_id || budget.content_sha256 !== b.enterprise_budget_basis.content_sha256 || BigInt(b.maximum_tokens) > BigInt(budget.approval.limits.tokens) || BigInt(b.maximum_calls) > BigInt(budget.approval.limits.calls) || decimal(b.maximum_amount) > decimal(budget.approval.limits.amount) || Date.parse(b.valid_until) > Date.parse(budget.approval.limits.deadline_at)) fail('REVISION_CONFLICT', '企业预算固定版本、币种或额度不匹配') }
  else { const b = body as InputDecide; const v = await readProducerRule(reader, project, task, resource!, signal); g.assert(); if (!v.allowed_actions.includes(b.decision) || b.decision === 'APPROVE' && v.manifest.created_by === g.session.user.user_id) fail('ACCESS_DENIED', '必须由独立授权审批人明确决定'); if (v.revision !== b.expected_revision || v.rule_sha256 !== b.rule_sha256) fail('REVISION_CONFLICT', '授权已变化，请重新读取') }
  const p: PendingProducer = { actor: g.session.user.user_id, tenant_id: g.context.tenant_id, project_id: project, task_id: task, kind, resource_id: resource, key: crypto.randomUUID(), body: detached(body), body_sha256: await sourceDigest(body) }; g.assert()
  if (readPendingProducer(g.session, project, task)) fail('IDEMPOTENCY_CONFLICT', '已有原请求待核查')
  sessionStorage.setItem(storageKey(g.session, project, task), canonical(p)); onPending(detached(p)); g.assert()
  const raw = await request.post(base(task) + '/node-producer-rules' + (resource ? '/' + resource + '/decisions' : ''), detached(p.body), { medical: 'envelope', signal, transformRequest: [canonical], headers: { ...headers(g.session), 'Idempotency-Key': p.key, [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert()
  // There is no replay path: p was created in this invocation and dispatched exactly once.
  await archiveFirstDispatchRejection(reader, p, raw, signal); g.assert()
  const r = await verifyReceipt(reader, p, raw, signal, false); g.assert(); if (!r) fail('CONTENT_MISMATCH', '写入未返回实际回执'); return r
}
export async function recoverProducer(reader: Reader, p: PendingProducer, signal: AbortSignal): Promise<InputReceipt | null> { const g = fieldTargetGuard(reader, signal); await verifyOriginal(reader, p, signal); g.assert(); const raw = await request.post(base(p.task_id) + '/node-producer-rules/recovery', { schema_version: '1.0.0', project_id: p.project_id, original_idempotency_key: p.key }, { medical: 'envelope', signal, headers: { ...headers(g.session), [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert(); const r = await verifyReceipt(reader, p, raw, signal, true); g.assert(); return r }

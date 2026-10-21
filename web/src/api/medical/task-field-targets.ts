import request from '@/utils/request'
import type { AxiosResponse } from 'axios'
import { envelope, fail } from './protocol'
import type { Context, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
import { canonical, checked, detached, fieldTargetGuard, mappingBasisIssue, mappingIssue, readPendingFieldTarget, storageKey, validateWrite, verifyInput, verifyMapping, verifyPreparation, verifyStatus } from './task-field-target-domain'
import type { PendingFieldTarget, WriteBody, WriteKind, Preparation, MappingAuthor, MappingReceipt, MappingView, Produce, Status, InputAuthor, InputContext, InputDecide, InputReceipt, InputView } from './task-field-target-domain'
export * from './task-field-target-domain'
type Reader = () => MedicalSession
export type WriteReceipt = MappingReceipt | Status | InputReceipt
const base = (task: string) => { checked('Id', task); return '/api/tasks/' + task }
const headers = (context: Context) => ({ 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision })
function path(task: string, kind: WriteKind, resource: string | null): string {
  if (kind === 'MAPPING') return base(task) + '/field-mappings'
  if (kind === 'PRODUCE') return base(task) + '/field-targets'
  const prefix = base(task) + '/field-target-input-capabilities'
  return kind === 'INPUT_AUTHOR' ? prefix : prefix + '/' + resource + '/decisions'
}
export async function readFieldTargetPreparation(reader: Reader, project: string, task: string, signal: AbortSignal): Promise<Preparation> {
  const guard = fieldTargetGuard(reader, signal); checked('Id', project)
  const raw = await request.get(base(task) + '/field-target-preparation', { medical: 'envelope', signal, headers: headers(guard.context) }); guard.assert()
  const response = envelope(raw), value = checked<Preparation>('Preparation', response.data)
  await verifyPreparation(value, guard.context.tenant_id, project, task); guard.assert()
  if (raw.status !== 200 || response.resource_revision !== value.head_revision) fail('CONTENT_MISMATCH', '准备信息没有返回对应修订')
  return value
}
export async function readFieldMapping(reader: Reader, project: string, task: string, assignment: string, signal: AbortSignal): Promise<MappingView> {
  const guard = fieldTargetGuard(reader, signal); checked('Id', assignment)
  const raw = await request.get(base(task) + '/field-mappings/' + assignment, { medical: 'envelope', signal, headers: headers(guard.context) }); guard.assert()
  const response = envelope(raw), value = checked<MappingView>('MappingView', response.data)
  await verifyMapping(value, guard.context.tenant_id, project, task); guard.assert()
  if (raw.status !== 200 || value.manifest.assignment_id !== assignment || response.resource_revision !== value.revision) fail('CONTENT_MISMATCH', '固定映射读取的身份或修订不一致')
  return value
}
export async function readFieldTargetInputContext(reader: Reader, project: string, task: string, signal: AbortSignal): Promise<InputContext> {
  const guard = fieldTargetGuard(reader, signal); checked('Id', project)
  const raw = await request.get(base(task) + '/field-target-input-capabilities', { medical: 'envelope', signal, headers: headers(guard.context) }); guard.assert()
  const value = checked<InputContext>('InputContext', envelope(raw).data)
  if (raw.status !== 200 || value.project_id !== project || value.task_id !== task) fail('CONTENT_MISMATCH', '输入能力目录归属不一致')
  for (const candidate of value.candidates) if (candidate.project_id !== project || candidate.task_id !== task) fail('CONTENT_MISMATCH', '输入能力候选指向其他任务')
  for (const item of value.capabilities) { await verifyInput(item, guard.context.tenant_id, project, task); guard.assert() }
  if (new Set(value.capabilities.map(item => item.manifest.capability_id)).size !== value.capabilities.length) fail('CONTENT_MISMATCH', '输入能力目录存在重复身份')
  return value
}
export async function readFieldTargetInput(reader: Reader, project: string, task: string, id: string, signal: AbortSignal): Promise<InputView> {
  const guard = fieldTargetGuard(reader, signal); checked('Id', id)
  const raw = await request.get(base(task) + '/field-target-input-capabilities/' + id, { medical: 'envelope', signal, headers: headers(guard.context) }); guard.assert()
  const response = envelope(raw), value = checked<InputView>('InputView', response.data)
  await verifyInput(value, guard.context.tenant_id, project, task); guard.assert()
  if (raw.status !== 200 || value.manifest.capability_id !== id || response.resource_revision !== value.revision) fail('CONTENT_MISMATCH', '输入能力读取身份或修订不一致')
  return value
}
async function authorize(reader: Reader, project: string, task: string, kind: WriteKind, body: WriteBody, resource: string | null, signal: AbortSignal): Promise<void> {
  if (kind === 'MAPPING' || kind === 'PRODUCE') {
    const prep = await readFieldTargetPreparation(reader, project, task, signal)
    if (!prep.allowed_actions.includes(kind === 'MAPPING' ? 'WRITE_MAPPING' : 'PRODUCE')) fail('ACCESS_DENIED', '当前任务没有这项独立字段目标权限')
    if (body.expected_revision !== prep.head_revision) fail('REVISION_CONFLICT', '固定准备修订已变化，请重新读取')
    if (kind === 'MAPPING') {
      const input = body as MappingAuthor, issue = mappingIssue(input, prep)
      if (issue) fail('SCHEMA_INVALID', issue)
      for (const key of ['project_id', 'task_revision', 'task_input_sha256', 'plan_revision', 'plan_sha256', 'template_version', 'template_sha256', 'profile_revision', 'scope_snapshot', 'base_mapping_id'] as const)
        if (input[key] !== prep[key]) fail('REVISION_CONFLICT', '映射固定依据已变化，请重新读取')
    } else {
      const input = body as Produce, mapping = prep.current_mapping
      if (!mapping?.is_current || mappingBasisIssue(prep) || input.assignment_id !== mapping.manifest.assignment_id || input.assignment_sha256 !== mapping.assignment_sha256
          || input.task_revision !== prep.task_revision || input.plan_revision !== prep.plan_revision) fail('REVISION_CONFLICT', '目标登记必须使用当前真实提议映射')
    }
  } else if (kind === 'INPUT_AUTHOR') {
    const context = await readFieldTargetInputContext(reader, project, task, signal)
    const { reason: _reason, ...pins } = body as InputAuthor
    if (!context.allowed_actions.includes('AUTHOR')) fail('ACCESS_DENIED', '当前身份没有独立输入能力登记权限')
    if (!context.candidates.some(({ reason: _candidateReason, ...candidate }) => canonical(candidate) === canonical(pins))) fail('REVISION_CONFLICT', '只能选择当前真实制品、生产回执及发布固定依据')
  } else {
    if (!resource) fail('SCHEMA_INVALID', '需要明确现有能力身份')
    const view = await readFieldTargetInput(reader, project, task, resource, signal), input = body as InputDecide
    if (!view.allowed_actions.includes(input.decision) || input.decision === 'APPROVE' && view.manifest.created_by === reader().user.user_id) fail('ACCESS_DENIED', '必须由获授权的独立审批人明确决定；作者不能审批自己的能力')
    if (input.capability_sha256 !== view.capability_sha256 || input.expected_revision !== view.revision) fail('REVISION_CONFLICT', '能力内容或修订已变化，请重新读取')
  }
}
async function validateOriginal(session: MedicalSession, pending: PendingFieldTarget): Promise<void> {
  const saved = readPendingFieldTarget(session, pending.project_id, pending.task_id)
  if (!saved || canonical(saved) !== canonical(pending) || await sourceDigest(pending.body) !== pending.body_sha256) fail('CONTENT_MISMATCH', '原请求身份、完整参数或摘要不一致')
}
async function verifyReceipt(reader: Reader, pending: PendingFieldTarget, raw: AxiosResponse<unknown>, signal: AbortSignal, recovery: boolean): Promise<WriteReceipt | null> {
  const guard = fieldTargetGuard(reader, signal), response = envelope(raw)
  if (recovery && response.data === null) { if (raw.status !== 200) fail('CONTENT_MISMATCH', '恢复查询状态无效'); return null }
  if (pending.kind === 'PRODUCE') {
    const result = checked<Status>('Status', response.data); verifyStatus(result, pending.tenant_id, pending.project_id, pending.task_id)
    const body = pending.body as Produce
    if (result.assignment_id !== null && (result.assignment_id !== body.assignment_id || result.assignment_sha256 !== body.assignment_sha256)) fail('CONTENT_MISMATCH', '原目标生产返回了其他映射')
    if (raw.status !== 200) fail('CONTENT_MISMATCH', '目标生产状态码无效')
    if (result.receipt) {
      const receipt = result.receipt
      if (receipt.request_sha256 !== pending.body_sha256 || receipt.created_by !== pending.actor || receipt.task_revision !== body.task_revision
          || receipt.plan_revision !== body.plan_revision) fail('CONTENT_MISMATCH', '原目标生产回执与原请求不一致')
    }
    return result
  }
  const mapping = pending.kind === 'MAPPING'
  const result = checked<MappingReceipt | InputReceipt>(mapping ? 'MappingReceipt' : 'InputReceipt', response.data)
  const expectedStatus = recovery || pending.kind === 'INPUT_DECIDE' ? 200 : 201
  if (raw.status !== expectedStatus || result.tenant_id !== pending.tenant_id || result.project_id !== pending.project_id || result.task_id !== pending.task_id
      || result.created_by !== pending.actor || result.request_sha256 !== pending.body_sha256
      || result.revision !== (BigInt(pending.body.expected_revision) + 1n).toString() || response.resource_revision !== result.revision) fail('CONTENT_MISMATCH', '原操作回执与完整请求、作者或修订不一致')
  if (mapping) {
    const receipt = result as MappingReceipt, body = pending.body as MappingAuthor
    const fixed = await readFieldMapping(reader, pending.project_id, pending.task_id, receipt.assignment_id, signal); guard.assert()
    if (receipt.assignment_sha256 !== fixed.assignment_sha256 || receipt.task_revision !== body.task_revision || receipt.plan_revision !== body.plan_revision
        || canonical(fixed.manifest.mappings) !== canonical(body.mappings)) fail('CONTENT_MISMATCH', '已保存映射与原提议清单不一致')
    for (const key of ['project_id', 'task_revision', 'task_input_sha256', 'plan_revision', 'plan_sha256', 'template_version', 'template_sha256', 'profile_revision', 'scope_snapshot'] as const)
      if (fixed.manifest[key] !== body[key]) fail('CONTENT_MISMATCH', '固定映射依据不一致')
  } else {
    const receipt = result as InputReceipt
    if (pending.resource_id !== null && receipt.capability_id !== pending.resource_id) fail('CONTENT_MISMATCH', '能力决定返回其他能力身份')
    const fixed = await readFieldTargetInput(reader, pending.project_id, pending.task_id, receipt.capability_id, signal); guard.assert()
    if (fixed.capability_sha256 !== receipt.capability_sha256 || fixed.revision !== receipt.revision || fixed.status !== receipt.status
        || fixed.latest_decision_id !== receipt.decision_id) fail('CONTENT_MISMATCH', '能力固定内容、状态与决定回执不一致')
    if (pending.kind === 'INPUT_AUTHOR') {
      if (receipt.status !== 'DRAFT' || receipt.decision_id !== null || fixed.manifest.created_by !== pending.actor) fail('CONTENT_MISMATCH', '能力创建只能得到未批准草稿')
      const body = pending.body as InputAuthor
      for (const key of Object.keys(body) as Array<keyof InputAuthor>) {
        if (key === 'reason' || key === 'expected_revision') continue
        if (canonical(fixed.manifest[key]) !== canonical(body[key])) fail('CONTENT_MISMATCH', '能力草稿与原固定制品和消费边界不一致')
      }
    } else {
      const body = pending.body as InputDecide
      if (receipt.capability_sha256 !== body.capability_sha256 || receipt.status !== (body.decision === 'APPROVE' ? 'APPROVED' : 'DISABLED')
          || receipt.decision_id === null || body.decision === 'APPROVE' && fixed.manifest.created_by === pending.actor) fail('CONTENT_MISMATCH', '能力决定不符合独立审批及原请求')
    }
  }
  return result
}
function completed(kind: WriteKind, receipt: WriteReceipt | null): boolean { return receipt !== null && (kind !== 'PRODUCE' || (receipt as Status).state === 'REGISTERED') }
async function clearVerified(reader: Reader, pending: PendingFieldTarget, receipt: WriteReceipt | null, signal: AbortSignal) {
  const guard = fieldTargetGuard(reader, signal); await validateOriginal(guard.session, pending); guard.assert()
  if (completed(pending.kind, receipt)) sessionStorage.removeItem(storageKey(guard.session, pending.project_id, pending.task_id))
}
export async function submitFieldTarget(reader: Reader, project: string, task: string, kind: WriteKind, body: WriteBody,
  resource: string | null, signal: AbortSignal, onPending: (pending: PendingFieldTarget) => void): Promise<WriteReceipt> {
  const guard = fieldTargetGuard(reader, signal); checked('Id', project); checked('Id', task); validateWrite(kind, body)
  if (body.project_id !== project || (kind === 'INPUT_DECIDE') !== (resource !== null)) fail('SCHEMA_INVALID', '请求目标与当前任务不一致')
  if (resource !== null) checked('Id', resource)
  if (readPendingFieldTarget(guard.session, project, task)) fail('IDEMPOTENCY_CONFLICT', '已有原请求待核查，不能生成替代写入')
  await authorize(reader, project, task, kind, body, resource, signal); guard.assert()
  const pending: PendingFieldTarget = { schema_version: '1.0.0', actor: guard.session.user.user_id, tenant_id: guard.context.tenant_id,
    project_id: project, task_id: task, context: guard.context, kind, resource_id: resource, key: crypto.randomUUID(), body: detached(body), body_sha256: await sourceDigest(body) }
  guard.assert()
  if (readPendingFieldTarget(guard.session, project, task)) fail('IDEMPOTENCY_CONFLICT', '已有原请求待核查')
  const stored = canonical(pending)
  if (new TextEncoder().encode(stored).length > 8 * 1024 * 1024) fail('SCHEMA_INVALID', '完整原请求超出本地可恢复大小，不会发送不可恢复写入')
  sessionStorage.setItem(storageKey(guard.session, project, task), stored); onPending(detached(pending)); guard.assert()
  const raw = await request.post(path(task, kind, resource), detached(pending.body), { medical: 'envelope', signal,
    headers: { ...headers(guard.context), 'Idempotency-Key': pending.key, [guard.session.csrf.header_name]: guard.session.csrf.token } }); guard.assert()
  const receipt = await verifyReceipt(reader, pending, raw, signal, false); guard.assert()
  if (!receipt) fail('CONTENT_MISMATCH', '写入没有返回真实结果')
  await clearVerified(reader, pending, receipt, signal); guard.assert(); return receipt
}
export async function recoverFieldTarget(reader: Reader, pending: PendingFieldTarget, signal: AbortSignal): Promise<{ complete: boolean; receipt: WriteReceipt | null }> {
  const guard = fieldTargetGuard(reader, signal); await validateOriginal(guard.session, pending); guard.assert()
  const recoveryPath = pending.kind === 'INPUT_DECIDE' ? base(pending.task_id) + '/field-target-input-capabilities/recovery' : path(pending.task_id, pending.kind, pending.resource_id) + '/recovery'
  const raw = await request.post(recoveryPath, { schema_version: '1.0.0', project_id: pending.project_id, original_idempotency_key: pending.key },
    { medical: 'envelope', signal, headers: { ...headers(guard.context), [guard.session.csrf.header_name]: guard.session.csrf.token } }); guard.assert()
  const receipt = await verifyReceipt(reader, pending, raw, signal, true); guard.assert()
  await clearVerified(reader, pending, receipt, signal); guard.assert()
  return { complete: completed(pending.kind, receipt), receipt }
}

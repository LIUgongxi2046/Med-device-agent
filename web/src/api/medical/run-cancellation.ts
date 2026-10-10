import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { taskActionAllowed } from './task-detail'
import { taskIdReference } from './tasks'
import { templateValueDigest } from './template-approval'
import { lookupOperation } from './operations'
import type { OperationLookup } from './operations'

export interface CancelRequest { expected_revision: string; reason: string }
export interface PendingCancellation {
  operation: 'RN-04'; actor_id: string; tenant_id: string; project_id: string; task_id: string; run_id: string
  expected_revision: string; key: string; body_sha256: string
}
export interface CancellationReceipt {
  operation: { operation_id: string; resource_id: string; status: 'COMPLETED'; status_url: string }
  command_id: string | null
  run: { run_id: string; task_id: string; status: 'CANCEL_REQUESTED' | 'CANCELLED' | 'SUCCEEDED' | 'FAILED'
    reason_code: string | null; revision: string; dispatch_closed_at: string | null; ended_at: string | null }
}
const pendingReference = 'urn:medical:run-cancellation-view:1.0.0'

function headers(session: MedicalSession, projectId: string, taskId: string): Record<string, string> {
  for (const id of [projectId, taskId]) validate(taskIdReference, id)
  const context = session.current_context
  if (!context || !taskActionAllowed(session, projectId, taskId, 'task.run')) fail('ACCESS_DENIED', '停止操作需要当前任务执行许可')
  validContext(context)
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
}
function storageKey(session: MedicalSession, projectId: string, taskId: string, runId: string): string {
  headers(session, projectId, taskId); validate(taskIdReference, runId)
  return ['medical.run.cancellation', session.user.user_id, session.current_context?.tenant_id, projectId, taskId, runId].join(':')
}
function owned(session: MedicalSession, pending: PendingCancellation): void {
  validate(pendingReference, pending); revision(pending.expected_revision); headers(session, pending.project_id, pending.task_id)
  if (pending.actor_id !== session.user.user_id || pending.tenant_id !== session.current_context?.tenant_id) {
    fail('ACCESS_DENIED', '原停止请求需要同一本人和企业的当前许可')
  }
}
export function pendingCancellation(session: MedicalSession, projectId: string, taskId: string, runId: string): PendingCancellation | null {
  const raw = sessionStorage.getItem(storageKey(session, projectId, taskId, runId))
  if (raw === null) return null
  const saved = validate<PendingCancellation>(pendingReference, JSON.parse(raw)); owned(session, saved)
  if (saved.project_id !== projectId || saved.task_id !== taskId || saved.run_id !== runId) fail('CONTENT_MISMATCH', '原停止请求与当前运行不一致')
  return saved
}
function checkedBody(body: CancelRequest): void {
  validate('urn:medical:shared#/$defs/CancelMessage', body); revision(body.expected_revision)
  if (body.reason.trim().length === 0 || Array.from(body.reason).length > 2048) fail('SCHEMA_INVALID', '停止操作需要完整理由且最多2048个字符')
}
export async function prepareCancellation(session: MedicalSession, projectId: string, taskId: string, runId: string, body: CancelRequest): Promise<PendingCancellation> {
  checkedBody(body)
  const key = storageKey(session, projectId, taskId, runId); const original = pendingCancellation(session, projectId, taskId, runId)
  const digest = await templateValueDigest(body)
  if (original) {
    if (original.expected_revision !== body.expected_revision || original.body_sha256 !== digest) fail('IDEMPOTENCY_CONFLICT', '原停止请求需要保留原版本和完整理由')
    return original
  }
  const saved: PendingCancellation = { operation: 'RN-04', actor_id: session.user.user_id, tenant_id: session.current_context!.tenant_id,
    project_id: projectId, task_id: taskId, run_id: runId, expected_revision: body.expected_revision, key: crypto.randomUUID(), body_sha256: digest }
  validate(pendingReference, saved)
  // 页面刷新后保留请求身份与摘要，理由仅保存在当前页面。
  sessionStorage.setItem(key, JSON.stringify(saved)); return saved
}
function completed(session: MedicalSession, pending: PendingCancellation): void {
  const saved = pendingCancellation(session, pending.project_id, pending.task_id, pending.run_id)
  if (!saved || saved.key !== pending.key || saved.body_sha256 !== pending.body_sha256) fail('CONTENT_MISMATCH', '原停止请求的保存身份不一致')
  sessionStorage.removeItem(storageKey(session, pending.project_id, pending.task_id, pending.run_id))
}
export async function submitCancellation(session: MedicalSession, pending: PendingCancellation, body: CancelRequest, signal: AbortSignal): Promise<CancellationReceipt> {
  owned(session, pending); checkedBody(body)
  if (pending.expected_revision !== body.expected_revision || pending.body_sha256 !== await templateValueDigest(body)) fail('IDEMPOTENCY_CONFLICT', '停止操作必须使用原请求参数')
  const raw = await request.post('/api/runs/' + pending.run_id + '/cancel-requests', body, { medical: 'envelope', signal,
    headers: { ...headers(session, pending.project_id, pending.task_id), 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const response = envelope(raw); const result = validate<CancellationReceipt>('urn:medical:run-control:1.0.0#/$defs/View', response.data)
  if (raw.status !== 200 || result.run.run_id !== pending.run_id || result.run.task_id !== pending.task_id
      || result.operation.resource_id !== pending.run_id || result.operation.status_url !== '/api/operations/' + result.operation.operation_id
      || response.resource_revision !== result.run.revision) fail('CONTENT_MISMATCH', '停止回执与原运行、操作或版本不一致')
  revision(result.run.revision); completed(session, pending); return result
}
export async function recoverCancellation(session: MedicalSession, pending: PendingCancellation, signal: AbortSignal): Promise<OperationLookup> {
  owned(session, pending)
  const result = await lookupOperation(session, pending.operation, pending.key, signal)
  if (result.operation !== null) {
    if (result.operation.resource_id !== pending.run_id) fail('CONTENT_MISMATCH', '原停止操作与运行身份不一致')
    if (result.operation.status === 'COMPLETED') completed(session, pending)
  }
  return result
}

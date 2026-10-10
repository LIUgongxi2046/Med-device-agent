import request from '@/utils/request'
import type { AxiosResponse } from 'axios'
import type { SourceScope } from '../../../../contracts/typescript/src/interfaces-common'
import { envelope, fail, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { projectActionAllowed, projectTextIssue } from './project-write'
import { templateValueDigest } from './template-approval'
import type { DocumentType } from './template-applicability'
import type { CurrentTask } from './task-detail'
import type { TaskDetail } from './tasks'
import { taskDetailReference } from './task-detail'

export type { SourceScope }
export interface TaskCreate {
  schema_version: '1.0.0'; project_id: string; profile_revision: string; document_type: DocumentType
  template_version: string; agent_release_id: string; scope_selection: SourceScope; owner_id: string; goal: string
  based_on_document_revision?: string | null
}
export interface PendingTaskCreate {
  actor_id: string; tenant_id: string; project_id: string; key: string; body_sha256: string; operation: 'TK-01'
}
export interface TaskCreateDraft {
  schema_version: '1.0.0'; operation_id: string; tenant_id: string; project_id: string; actor_id: string
  created_at: string; body_sha256: string; content: TaskCreate
}
export const taskCreateReference = 'urn:medical:tasks:1.0.0#/$defs/Create'
const draftReference = 'urn:medical:task-create-draft:1.0.0'
const pendingReference = 'urn:medical:task-create-view:1.0.0'
function headers(session: MedicalSession, project: string): Record<string, string> {
  const context = session.current_context
  if (!context || !projectActionAllowed(session, 'task.create', project)) fail('ACCESS_DENIED', '创建任务需要当前企业身份与项目创建任务许可')
  validContext(context)
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
}
function storageKey(session: MedicalSession, project: string): string {
  headers(session, project)
  return 'medical.task.create:' + session.user.user_id + ':' + session.current_context?.tenant_id + ':' + project
}
function owned(session: MedicalSession, pending: PendingTaskCreate): void {
  validate(pendingReference, pending); headers(session, pending.project_id)
  if (pending.actor_id !== session.user.user_id || pending.tenant_id !== session.current_context?.tenant_id)
    fail('ACCESS_DENIED', '创建操作需要原本人、企业与当前许可')
}
export function pendingTaskCreate(session: MedicalSession, project: string): PendingTaskCreate | null {
  const raw = sessionStorage.getItem(storageKey(session, project)); if (raw === null) return null
  const pending = validate<PendingTaskCreate>(pendingReference, JSON.parse(raw)); owned(session, pending)
  if (pending.project_id !== project) fail('CONTENT_MISMATCH', '原创建操作与当前项目不一致')
  return pending
}
export async function prepareTaskCreate(session: MedicalSession, body: TaskCreate): Promise<PendingTaskCreate> {
  validate(taskCreateReference, body); headers(session, body.project_id)
  if (projectTextIssue(body.goal, Number.MAX_SAFE_INTEGER)) fail('SCHEMA_INVALID', '任务目标需要有效正文')
  const digest = await templateValueDigest(body); const original = pendingTaskCreate(session, body.project_id)
  if (original) {
    if (original.body_sha256 !== digest) fail('IDEMPOTENCY_CONFLICT', '再次提交需要完整原创建参数')
    return original
  }
  const context = session.current_context
  if (!context) fail('CONTEXT_CHANGED', '创建任务需要当前企业上下文')
  const pending: PendingTaskCreate = { actor_id: session.user.user_id, tenant_id: context.tenant_id, project_id: body.project_id,
    key: crypto.randomUUID(), body_sha256: digest, operation: 'TK-01' }
  validate(pendingReference, pending); sessionStorage.setItem(storageKey(session, body.project_id), JSON.stringify(pending))
  return pending
}
function receipt(session: MedicalSession, pending: PendingTaskCreate, raw: AxiosResponse<unknown>): CurrentTask {
  owned(session, pending); const response = envelope(raw); const detail = validate<TaskDetail>(taskDetailReference, response.data)
  if (raw.status !== 201 || response.resource_revision !== '1' || detail.project_id !== pending.project_id || detail.snapshot_id === null
      || detail.plan_revision !== null || !['NEEDS_MATERIALS', 'PLAN_PENDING'].includes(detail.status)
      || detail.document_ids.length !== 0 || detail.waiting_item_ids.length !== 0 || detail.active_run_ids.length !== 0
      || detail.status === 'NEEDS_MATERIALS' && detail.missing_requirements.length === 0
      || detail.status === 'PLAN_PENDING' && detail.missing_requirements.length !== 0
      || raw.headers.location !== '/api/tasks/' + detail.task_id
      || new Set(detail.missing_requirements.map(item => item.requirement_id)).size !== detail.missing_requirements.length) {
    fail('CONTENT_MISMATCH', '创建回执与原项目、固定版本或输入状态不一致')
  }
  return { detail, revision: '1' }
}
export async function submitTaskCreate(session: MedicalSession, pending: PendingTaskCreate, body: TaskCreate): Promise<CurrentTask> {
  owned(session, pending); validate(taskCreateReference, body)
  if (body.project_id !== pending.project_id || await templateValueDigest(body) !== pending.body_sha256)
    fail('IDEMPOTENCY_CONFLICT', '创建任务需要完整原请求及同一操作键')
  await saveTaskCreateDraft(session, pending, body)
  const raw = await request.post('/api/tasks', body, { medical: 'envelope',
    headers: { ...headers(session, pending.project_id), 'Idempotency-Key': pending.key } })
  const result = receipt(session, pending, raw)
  if (result.detail.owner_id !== body.owner_id) fail('CONTENT_MISMATCH', '创建回执与原责任人员不一致')
  return result
}
async function draftReceipt(session: MedicalSession, pending: PendingTaskCreate, raw: AxiosResponse<unknown>): Promise<TaskCreateDraft> {
  owned(session, pending); const response = envelope(raw); const draft = validate<TaskCreateDraft>(draftReference, response.data)
  if (raw.status !== 201 || response.resource_revision !== null || draft.actor_id !== pending.actor_id
      || draft.tenant_id !== pending.tenant_id || draft.project_id !== pending.project_id
      || draft.content.project_id !== pending.project_id || draft.body_sha256 !== pending.body_sha256
      || await templateValueDigest(draft.content) !== pending.body_sha256)
    fail('CONTENT_MISMATCH', '输入草稿与本人原创建操作不一致')
  return draft
}
export async function saveTaskCreateDraft(session: MedicalSession, pending: PendingTaskCreate, body: TaskCreate): Promise<TaskCreateDraft> {
  owned(session, pending); validate(taskCreateReference, body)
  if (body.project_id !== pending.project_id || await templateValueDigest(body) !== pending.body_sha256)
    fail('IDEMPOTENCY_CONFLICT', '保存草稿需要完整原参数及同一操作键')
  const raw = await request.post('/api/projects/' + pending.project_id + '/task-create-drafts', body, { medical: 'envelope',
    headers: { ...headers(session, pending.project_id), 'Idempotency-Key': pending.key } })
  return draftReceipt(session, pending, raw)
}
export async function originalTaskCreateDraft(session: MedicalSession, pending: PendingTaskCreate, signal: AbortSignal): Promise<TaskCreateDraft | null> {
  owned(session, pending)
  const raw = await request.get('/api/projects/' + pending.project_id + '/task-create-drafts', { medical: 'envelope', signal,
    headers: { ...headers(session, pending.project_id), 'Idempotency-Key': pending.key } })
  const response = envelope(raw)
  if (response.data === null) {
    if (raw.status !== 200 || response.resource_revision !== null) fail('CONTENT_MISMATCH', '未保存的输入草稿需要明确空结果')
    return null
  }
  return draftReceipt(session, pending, raw)
}
export async function originalTaskCreate(session: MedicalSession, pending: PendingTaskCreate, signal: AbortSignal): Promise<CurrentTask | null> {
  owned(session, pending)
  const raw = await request.get('/api/projects/' + pending.project_id + '/task-create-operations', { medical: 'envelope', signal,
    headers: { ...headers(session, pending.project_id), 'Idempotency-Key': pending.key } })
  const response = envelope(raw)
  if (response.data === null) {
    if (raw.status !== 200 || response.resource_revision !== null) fail('CONTENT_MISMATCH', '未登记的原操作需要明确空结果')
    return null
  }
  return receipt(session, pending, raw)
}
export function clearTaskCreate(session: MedicalSession, pending: PendingTaskCreate): void {
  const actual = pendingTaskCreate(session, pending.project_id)
  if (!actual || actual.key !== pending.key || actual.body_sha256 !== pending.body_sha256)
    fail('IDEMPOTENCY_CONFLICT', '原创建操作身份已经变化')
  sessionStorage.removeItem(storageKey(session, pending.project_id))
}

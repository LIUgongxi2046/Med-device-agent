import request from '@/utils/request'
import { projectTextIssue } from './project-text'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Attribute, Basis, Context, MedicalSession } from './protocol'
import { formatProjectJson } from './project-json'
import { readOperation } from './operations'
import type { Operation } from './operations'

export type ProjectDecision = 'ACTIVATE' | 'PAUSE' | 'ARCHIVE' | 'RESTORE'
export type ProjectWriteOperation = 'PJ-02' | 'PJ-04' | 'PJ-05'
export interface CreateProject {
  schema_version: '1.0.0'; name: string; responsible_user_id: string; attributes: Attribute[]
  creation_basis: Basis; reason: string; approval_attested: true
}
export interface ReviseProject {
  schema_version: '1.0.0'; expected_revision: string; based_on_profile_revision: string
  attributes: Attribute[]; reason: string; approval_attested: true
}
export interface DecideProject {
  schema_version: '1.0.0'; expected_revision: string; decision: ProjectDecision; reason: string; approval_attested: true
}
export type ProjectWriteBody = CreateProject | ReviseProject | DecideProject
export interface PendingProjectWrite {
  actor: string; tenant_id: string; operation: ProjectWriteOperation; project_id: string | null; key: string; context: Context
  body_sha256: string; expected_revision: string | null; based_on_profile_revision: string | null
  decision: 'CREATE' | 'REVISE' | ProjectDecision
}
export interface ProjectReceipt {
  schema_version: '1.0.0'; operation_id: string; decision_id: string | null; decision: PendingProjectWrite['decision']
  tenant_id: string; project_id: string; status: 'PENDING' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED'; revision: string; profile_revision: string
  current_context: Context
  impact: {
    old_profile_revision: string | null; new_profile_revision: string; dependent_tasks: string | null; task_ids: string[] | null
    active_runs: string | null; runs: { run_id: string; status: string; revision: string }[] | null; complete: boolean
    new_dispatch: 'PENDING' | 'DENIED' | 'REVALIDATE'; inflight: 'RECONCILE_ACTUAL_INVOCATIONS'
  }
}

const pendingSchema = 'urn:medical:project-write-view:1.0.0'
const prefix = 'medical.project.write:'
const definitions = { 'PJ-02': 'Create', 'PJ-04': 'Revise', 'PJ-05': 'Decide' } as const
const actions = { 'PJ-02': 'project.create', 'PJ-04': 'profile.confirm', 'PJ-05': 'project.manage' } as const

export function projectActionAllowed(session: MedicalSession | null, action: string, projectId?: string | null): boolean {
  const context = session?.current_context
  if (!session || !context) return false
  return session.action_summary.some(item => item.action === action && (
    item.resource_kind === 'ENTERPRISE' && item.resource_id === context.tenant_id && item.project_id === null
    || projectId !== null && item.resource_kind === 'PROJECT' && item.project_id !== null && item.resource_id === item.project_id
      && (projectId === undefined || item.project_id === projectId)
  ))
}

function current(session: MedicalSession): Context {
  if (session.current_context === null) fail('ACCESS_DENIED', '项目写入需要当前企业身份')
  validContext(session.current_context)
  return session.current_context
}

function storageKey(session: MedicalSession, projectId: string | null): string {
  return prefix + session.user.user_id + ':' + current(session).tenant_id + ':' + (projectId ?? 'CREATE')
}

export function readPendingProjectWrite(session: MedicalSession, projectId: string | null): PendingProjectWrite | null {
  const saved = sessionStorage.getItem(storageKey(session, projectId))
  if (saved === null) return null
  const value = validate<PendingProjectWrite>(pendingSchema, JSON.parse(saved))
  validContext(value.context)
  if (value.expected_revision !== null && revision(value.expected_revision) === '0') fail('SCHEMA_INVALID', '原项目版本无效')
  if (value.actor !== session.user.user_id || value.tenant_id !== current(session).tenant_id || value.project_id !== projectId
      || value.context.tenant_id !== value.tenant_id) fail('CONTENT_MISMATCH', '原项目请求身份与当前主体及企业不一致')
  return value
}

export { projectTextIssue } from './project-text'

export function projectBodyReference(operation: ProjectWriteOperation): string {
  return 'urn:medical:projects:1.0.0#/$defs/' + definitions[operation]
}

function checkBody(operation: ProjectWriteOperation, body: ProjectWriteBody): void {
  validate(projectBodyReference(operation), body)
  if (projectTextIssue(body.reason, 2000)) fail('SCHEMA_INVALID', '项目决定原因无效')
  if ('name' in body && projectTextIssue(body.name, 256)) fail('SCHEMA_INVALID', '项目名称无效')
  if ('expected_revision' in body && (revision(body.expected_revision) === '0' || body.expected_revision === '18446744073709551615')) {
    fail('REVISION_LIMIT', '项目版本无法继续增加')
  }
  if ('attributes' in body) {
    const keys = new Set<string>()
    for (const attribute of body.attributes) {
      if (projectTextIssue(attribute.key, 128) || projectTextIssue(attribute.reason, 2000) || keys.has(attribute.key)
          || new Set(attribute.authority_refs.map(basis => basis.version_id)).size !== attribute.authority_refs.length) {
        fail('SCHEMA_INVALID', '项目属性、依据或原因无效')
      }
      keys.add(attribute.key)
    }
  }
}

async function bodyHash(body: ProjectWriteBody): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(formatProjectJson(body)))
  return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('')
}

export async function prepareProjectWrite(session: MedicalSession, operation: ProjectWriteOperation,
  projectId: string | null, body: ProjectWriteBody): Promise<PendingProjectWrite> {
  const context = current(session)
  if (!projectActionAllowed(session, actions[operation], projectId)
      || operation !== 'PJ-02' && !projectActionAllowed(session, 'project.read', projectId)) fail('ACCESS_DENIED', '当前项目动作没有明确授权')
  checkBody(operation, body)
  const digest = await bodyHash(body)
  const previous = readPendingProjectWrite(session, projectId)
  if (previous !== null) {
    if (previous.operation !== operation || previous.body_sha256 !== digest) fail('IDEMPOTENCY_CONFLICT', '原项目请求仍待核查，请保留原参数和原版本')
    return previous
  }
  if (context.context_revision === '18446744073709551615' || context.governance_epoch === '18446744073709551615') {
    fail('REVISION_LIMIT', '当前上下文或治理版本无法继续增加')
  }
  const value: PendingProjectWrite = {
    actor: session.user.user_id, tenant_id: context.tenant_id, operation, project_id: projectId, key: crypto.randomUUID(),
    context: { ...context }, body_sha256: digest,
    expected_revision: 'expected_revision' in body ? body.expected_revision : null,
    based_on_profile_revision: 'based_on_profile_revision' in body ? body.based_on_profile_revision : null,
    decision: 'decision' in body ? body.decision : operation === 'PJ-02' ? 'CREATE' : 'REVISE'
  }
  validate(pendingSchema, value)
  // 浏览器仅保存请求身份、摘要和版本；客户名称、正文及批准原因保存在本次操作内存。
  sessionStorage.setItem(storageKey(session, projectId), JSON.stringify(value))
  return value
}

export async function submitProjectWrite(session: MedicalSession, pending: PendingProjectWrite, body: ProjectWriteBody): Promise<ProjectReceipt> {
  const context = current(session)
  validate(pendingSchema, pending); checkBody(pending.operation, body)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== context.tenant_id
      || pending.context.tenant_id !== context.tenant_id || await bodyHash(body) !== pending.body_sha256
      || !projectActionAllowed(session, actions[pending.operation], pending.project_id)
      || pending.operation !== 'PJ-02' && !projectActionAllowed(session, 'project.read', pending.project_id)) {
    fail('CONTENT_MISMATCH', '原项目请求与当前主体、企业或完整参数不一致')
  }
  const path = pending.operation === 'PJ-02' ? '/api/projects'
    : '/api/projects/' + pending.project_id + (pending.operation === 'PJ-04' ? '/profile-revisions' : '/state-decisions')
  const response = await request.post(path, formatProjectJson(body), { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision,
    'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token
  } })
  const value = envelope(response)
  if (response.status !== (pending.operation === 'PJ-02' ? 201 : 200)) fail('CONTENT_MISMATCH', '项目写入未取得明确完成响应')
  const result = validate<ProjectReceipt>('urn:medical:projects:1.0.0#/$defs/Receipt', value.data)
  validContext(result.current_context); revision(result.revision)
  for (const count of [result.impact.dependent_tasks, result.impact.active_runs]) if (count !== null) revision(count)
  for (const run of result.impact.runs ?? []) revision(run.revision)
  const impact = result.impact
  if ((impact.dependent_tasks === null) !== (impact.task_ids === null)
      || (impact.active_runs === null) !== (impact.runs === null)
      || impact.task_ids !== null && BigInt(impact.dependent_tasks!) < BigInt(impact.task_ids.length)
      || impact.runs !== null && (BigInt(impact.active_runs!) < BigInt(impact.runs.length)
        || new Set(impact.runs.map(run => run.run_id)).size !== impact.runs.length)
      || impact.complete !== (impact.task_ids !== null && impact.runs !== null
        && BigInt(impact.dependent_tasks!) === BigInt(impact.task_ids.length)
        && BigInt(impact.active_runs!) === BigInt(impact.runs.length))) {
    fail('CONTENT_MISMATCH', '项目影响数量、身份或完整范围不一致')
  }
  const status = pending.decision === 'CREATE' ? 'PENDING' : pending.decision === 'PAUSE' ? 'PAUSED'
    : pending.decision === 'ARCHIVE' ? 'ARCHIVED' : pending.decision === 'REVISE' ? null : 'ACTIVE'
  if (result.tenant_id !== pending.tenant_id || result.current_context.tenant_id !== pending.tenant_id
      || pending.project_id !== null && result.project_id !== pending.project_id || result.decision !== pending.decision
      || value.resource_revision !== result.revision || result.revision !== (pending.expected_revision === null ? '1' : (BigInt(pending.expected_revision) + 1n).toString())
      || result.current_context.context_revision !== (BigInt(pending.context.context_revision) + 1n).toString()
      || result.current_context.governance_epoch !== (BigInt(pending.context.governance_epoch) + 1n).toString()
      || result.current_context.auth_epoch !== pending.context.auth_epoch || result.impact.new_profile_revision !== result.profile_revision
      || pending.operation === 'PJ-04' && result.impact.old_profile_revision !== pending.based_on_profile_revision
      || pending.operation !== 'PJ-04' && result.impact.old_profile_revision !== null
      || pending.operation === 'PJ-04' && !['PENDING', 'ACTIVE'].includes(result.status)
      || impact.new_dispatch !== (result.status === 'PENDING' ? 'PENDING' : result.status === 'ACTIVE' ? 'REVALIDATE' : 'DENIED')
      || status !== null && result.status !== status || (pending.operation === 'PJ-05') !== (result.decision_id !== null)) {
    fail('CONTENT_MISMATCH', '项目回执与原目标、版本或上下文不一致')
  }
  return result
}

export async function confirmProjectOperation(session: MedicalSession, pending: PendingProjectWrite,
  operationId: string, projectId: string, signal: AbortSignal): Promise<Operation> {
  const context = current(session)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== context.tenant_id
      || pending.project_id !== null && pending.project_id !== projectId) fail('ACCESS_DENIED', '原请求核查没有当前本人及企业身份')
  const operation = await readOperation(session, operationId, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== projectId) fail('CONTENT_MISMATCH', '原项目操作尚未取得一致的完成结果')
  return operation
}

export function clearCompletedProjectWrite(session: MedicalSession, pending: PendingProjectWrite, operation: Operation): void {
  const saved = readPendingProjectWrite(session, pending.project_id)
  if (saved === null || saved.key !== pending.key || saved.body_sha256 !== pending.body_sha256
      || operation.status !== 'COMPLETED' || operation.resource_id === null
      || pending.project_id !== null && pending.project_id !== operation.resource_id) fail('CONTENT_MISMATCH', '原项目请求仍需核查')
  sessionStorage.removeItem(storageKey(session, pending.project_id))
}

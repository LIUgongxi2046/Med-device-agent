import request from '@/utils/request'
import type { PlanCandidate } from '../../../../contracts/typescript/src/artifacts'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import { taskActionAllowed } from './task-detail'
import type { CurrentTask } from './task-detail'
import { taskIdReference } from './tasks'
import { canonicalTemplateValue, templateValueDigest } from './template-approval'
import { projectTextIssue } from './project-write'
import type { TemplateContent } from './template-approval'
import type { TemplateConditionResult } from './template-applicability'

export type { PlanCandidate, PlanSection, ResearchQuestion } from '../../../../contracts/typescript/src/artifacts'
export interface TaskPlanView {
  schema_version: '1.0.0'; tenant_id: string; project_id: string; task_id: string; task_revision: string
  plan_revision: string; snapshot_id: string; template_version: string; profile_revision: string
  status: 'DRAFT' | 'CONFIRMED' | 'SUPERSEDED'; content: PlanCandidate; content_sha256: string
  current_plan_revision: string | null; current_plan_head_revision: string | null
}
export interface CurrentPlan { plan: TaskPlanView | null; revision: string }
export interface PlanEditBasis {
  schema_version: '1.0.0'; tenant_id: string; project_id: string; task_id: string; task_revision: string
  snapshot_id: string; template_version: string; profile_revision: string; template_content: TemplateContent
  conditions: TemplateConditionResult[]; completion_rule_ids: string[]
}
export interface PlanVersionInput {
  schema_version: '1.0.0'; expected_revision: string; base_version: string | null
  schema_ref: 'https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/PlanCandidate'
  content: PlanCandidate; reason: string
}
export interface PendingPlan {
  actor: string; tenant_id: string; project_id: string; task_id: string; task_revision: string; key: string; operation: 'TK-03'
  context: Context; expected_revision: string; base_version: string | null; body_sha256: string; content_sha256: string
}
const viewReference = 'urn:medical:task-plan-view:1.0.0#/$defs/View'
const pendingReference = 'urn:medical:task-plan-write-view:1.0.0'
const prefix = 'medical.task.plan:'
export const planContentReference = 'https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/PlanCandidate'

function context(session: MedicalSession, projectId: string, taskId: string, action: string): Context {
  validate(taskIdReference, projectId); validate(taskIdReference, taskId)
  if (!session.current_context || !taskActionAllowed(session, projectId, taskId, action)) fail('ACCESS_DENIED', '计划操作需要当前企业身份及对应任务许可')
  validContext(session.current_context); return session.current_context
}
function storageKey(session: MedicalSession, projectId: string, taskId: string): string {
  return prefix + session.user.user_id + ':' + context(session, projectId, taskId, 'plan.write').tenant_id + ':' + projectId + ':' + taskId
}
async function checkedPlan(session: MedicalSession, projectId: string, taskId: string, value: unknown): Promise<TaskPlanView> {
  const result = validate<TaskPlanView>(viewReference, value)
  if (result.tenant_id !== session.current_context?.tenant_id || result.project_id !== projectId || result.task_id !== taskId
      || result.template_version !== result.content.template_version || result.profile_revision !== result.content.profile_revision
      || await templateValueDigest(result.content) !== result.content_sha256
      || result.current_plan_head_revision !== null && revision(result.current_plan_head_revision) === '0') {
    fail('CONTENT_MISMATCH', '计划内容、固定版本、企业或任务归属不一致')
  }
  return result
}
export async function readTaskPlan(session: MedicalSession, projectId: string, taskId: string, planId: string | null,
  signal: AbortSignal): Promise<CurrentPlan> {
  const current = context(session, projectId, taskId, 'task.read')
  if (planId !== null) validate(taskIdReference, planId)
  const raw = await request.get('/api/tasks/' + taskId + '/plan-revisions' + (planId === null ? '' : '/' + planId), {
    medical: 'envelope', signal, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current.context_revision }
  })
  const response = envelope(raw)
  if (raw.status !== 200 || response.resource_revision === null || revision(response.resource_revision) === '0') fail('CONTENT_MISMATCH', '计划读取没有返回当前任务修订')
  const plan = response.data === null ? null : await checkedPlan(session, projectId, taskId, response.data)
  if (planId !== null && (!plan || plan.plan_revision !== planId)
      || planId === null && plan !== null && plan.current_plan_revision !== plan.plan_revision) fail('CONTENT_MISMATCH', '计划读取返回的版本与请求不一致')
  return { plan, revision: response.resource_revision }
}
export async function readPlanEditBasis(session: MedicalSession, current: CurrentTask, signal: AbortSignal): Promise<PlanEditBasis> {
  const detail = current.detail; const active = context(session, detail.project_id, detail.task_id, 'plan.write')
  const raw = await request.get('/api/tasks/' + detail.task_id + '/plan-edit-basis', { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': active.context_revision } })
  const response = envelope(raw); const result = validate<PlanEditBasis>('urn:medical:task-plan-view:1.0.0#/$defs/Basis', response.data)
  if (raw.status !== 200 || response.resource_revision !== current.revision || result.tenant_id !== active.tenant_id
      || result.project_id !== detail.project_id || result.task_id !== detail.task_id || result.task_revision !== detail.task_revision
      || result.snapshot_id !== detail.snapshot_id) fail('REVISION_CONFLICT', '计划编辑依据与当前任务或来源快照不一致')
  const content = result.template_content
  if (content.nodes.length === 0 || new Set(content.nodes.map(node => node.node_id)).size !== content.nodes.length) fail('CONTENT_MISMATCH', '计划编辑需要完整且唯一的模板目录')
  const expected = new Set(['PROFILE:' + detail.project_id, 'TEMPLATE:' + result.template_version,
    ...content.nodes.map(item => 'NODE:' + item.node_id), ...content.field_definitions.map(item => 'FIELD:' + item.field_id),
    ...content.attachment_requirements.map(item => 'ATTACHMENT:' + item.attachment_requirement_id)])
  const actual = new Set<string>()
  for (const condition of result.conditions) {
    const key = condition.scope + ':' + condition.target_id
    if (!expected.has(key) || actual.has(key)
        || condition.unknown_input_keys.some(name => !condition.input_keys.includes(name))
        || (condition.status === 'UNKNOWN') !== (condition.unknown_input_keys.length > 0)) fail('CONTENT_MISMATCH', '模板适用条件与编辑依据不一致')
    actual.add(key)
  }
  if (actual.size !== expected.size) fail('CONTENT_MISMATCH', '计划编辑依据缺少完整适用条件')
  return result
}
export function validatePlanInput(body: unknown): PlanVersionInput {
  const result = validate<PlanVersionInput>('urn:medical:tasks:1.0.0#/$defs/PlanVersionInput', body)
  if (revision(result.expected_revision) === '0' || projectTextIssue(result.reason, 2000)) fail('SCHEMA_INVALID', '计划保存需要有效任务修订及完整理由')
  const candidate = result.content
  const nodes = new Set(candidate.requirement_node_ids); const covered = new Set<string>(); const sections = new Map(candidate.sections.map(item => [item.section_id, item]))
  if (nodes.size === 0 || sections.size !== candidate.sections.length) fail('SCHEMA_INVALID', '计划需要完整目录和唯一章节身份')
  for (const section of candidate.sections) {
    if (projectTextIssue(section.goal, Number.MAX_SAFE_INTEGER) || section.requirement_node_ids.length === 0) fail('SCHEMA_INVALID', '每个章节需要目标和目录节点')
    for (const node of section.requirement_node_ids) {
      if (!nodes.has(node) || covered.has(node)) fail('SCHEMA_INVALID', '全部目录节点需要由章节完整且唯一覆盖')
      covered.add(node)
    }
    if (section.depends_on_section_ids.some(id => id === section.section_id || !sections.has(id))) fail('SCHEMA_INVALID', '章节依赖需要引用计划内其他章节')
  }
  if (covered.size !== nodes.size) fail('SCHEMA_INVALID', '计划仍有目录节点尚未分配章节')
  const questions = new Set(candidate.questions.map(item => item.question_key))
  if (questions.size !== candidate.questions.length) fail('SCHEMA_INVALID', '研究问题需要唯一身份')
  for (const question of candidate.questions) {
    const section = sections.get(question.section_id)
    if (!section || projectTextIssue(question.question, Number.MAX_SAFE_INTEGER)
        || question.requirement_ids.some(id => !section.requirement_node_ids.includes(id))
        || question.depends_on_question_keys.some(key => key === question.question_key || !questions.has(key))) {
      fail('SCHEMA_INVALID', '研究问题的目标、目录或依赖与当前章节不一致')
    }
  }
  return result
}
export function readPendingPlan(session: MedicalSession, projectId: string, taskId: string): PendingPlan | null {
  const saved = sessionStorage.getItem(storageKey(session, projectId, taskId)); if (saved === null) return null
  const result = validate<PendingPlan>(pendingReference, JSON.parse(saved)); validContext(result.context)
  if (result.actor !== session.user.user_id || result.tenant_id !== session.current_context?.tenant_id || result.context.tenant_id !== result.tenant_id
      || result.project_id !== projectId || result.task_id !== taskId || revision(result.expected_revision) === '0') fail('CONTENT_MISMATCH', '原计划保存与当前本人、企业或任务不一致')
  return result
}
export async function preparePlan(session: MedicalSession, current: CurrentTask, body: PlanVersionInput): Promise<PendingPlan> {
  const detail = current.detail; const active = context(session, detail.project_id, detail.task_id, 'plan.write'); validatePlanInput(body)
  if (body.expected_revision !== current.revision || body.content === null) fail('REVISION_CONFLICT', '计划保存需要本次读取的任务修订')
  const hash = await templateValueDigest(body); const saved = readPendingPlan(session, detail.project_id, detail.task_id)
  if (saved) {
    if (saved.body_sha256 !== hash || saved.task_revision !== detail.task_revision || canonicalTemplateValue(saved.context) !== canonicalTemplateValue(active)) fail('IDEMPOTENCY_CONFLICT', '原保存仍待核查，需要保留原参数和请求身份')
    return saved
  }
  const result: PendingPlan = { actor: session.user.user_id, tenant_id: active.tenant_id, project_id: detail.project_id, task_id: detail.task_id,
    task_revision: detail.task_revision, key: crypto.randomUUID(), operation: 'TK-03', context: { ...active }, expected_revision: current.revision,
    base_version: body.base_version, body_sha256: hash, content_sha256: await templateValueDigest(body.content) }
  validate(pendingReference, result)
  // 计划正文和理由只保存在页面内存，浏览器保存原操作身份及摘要。
  sessionStorage.setItem(storageKey(session, detail.project_id, detail.task_id), JSON.stringify(result)); return result
}
export async function savePlan(session: MedicalSession, pending: PendingPlan, body: PlanVersionInput): Promise<CurrentPlan> {
  const active = context(session, pending.project_id, pending.task_id, 'plan.write'); validate(pendingReference, pending); validatePlanInput(body)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== active.tenant_id || canonicalTemplateValue(pending.context) !== canonicalTemplateValue(active)
      || body.expected_revision !== pending.expected_revision || body.base_version !== pending.base_version
      || await templateValueDigest(body) !== pending.body_sha256 || await templateValueDigest(body.content) !== pending.content_sha256) fail('CONTENT_MISMATCH', '计划保存的身份、修订或正文与原请求不一致')
  const raw = await request.post('/api/tasks/' + pending.task_id + '/plan-revisions', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision, 'Idempotency-Key': pending.key,
    [session.csrf.header_name]: session.csrf.token
  } })
  const response = envelope(raw); const plan = await checkedPlan(session, pending.project_id, pending.task_id, response.data)
  if (raw.status !== 201 || response.resource_revision !== (BigInt(pending.expected_revision) + 1n).toString()
      || plan.task_revision !== pending.task_revision || plan.status !== 'DRAFT' || plan.plan_revision !== plan.current_plan_revision
      || plan.content_sha256 !== pending.content_sha256 || canonicalTemplateValue(plan.content) !== canonicalTemplateValue(body.content)) fail('CONTENT_MISMATCH', '计划保存回执与完整原正文或任务修订不一致')
  return { plan, revision: response.resource_revision }
}
export async function recoverSavedPlan(session: MedicalSession, pending: PendingPlan, signal: AbortSignal): Promise<CurrentPlan> {
  const active = context(session, pending.project_id, pending.task_id, 'plan.write'); validate(pendingReference, pending)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== active.tenant_id) fail('ACCESS_DENIED', '原计划保存需要当前本人及企业许可')
  const raw = await request.get('/api/tasks/' + pending.task_id + '/plan-operations', { medical: 'envelope', signal, headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': active.context_revision, 'Idempotency-Key': pending.key
  } })
  const response = envelope(raw)
  if (raw.status !== 200 || response.resource_revision === null || revision(response.resource_revision) === '0') fail('CONTENT_MISMATCH', '原计划操作没有有效任务修订')
  const plan = response.data === null ? null : await checkedPlan(session, pending.project_id, pending.task_id, response.data)
  if (plan && (plan.content_sha256 !== pending.content_sha256 || plan.task_revision !== pending.task_revision || plan.status !== 'DRAFT'
      || response.resource_revision !== (BigInt(pending.expected_revision) + 1n).toString())) fail('CONTENT_MISMATCH', '原计划操作的结果与固定参数不一致')
  return { plan, revision: response.resource_revision }
}
export async function clearVerifiedPlan(session: MedicalSession, pending: PendingPlan, result: CurrentPlan, signal: AbortSignal): Promise<void> {
  if (!result.plan || result.plan.content_sha256 !== pending.content_sha256 || result.plan.task_revision !== pending.task_revision) fail('CONTENT_MISMATCH', '清理原操作需要完整保存结果')
  const fixed = await readTaskPlan(session, pending.project_id, pending.task_id, result.plan.plan_revision, signal)
  if (!fixed.plan || canonicalTemplateValue(fixed.plan.content) !== canonicalTemplateValue(result.plan.content)
      || fixed.plan.content_sha256 !== pending.content_sha256) fail('CONTENT_MISMATCH', '计划保存后重新读取的内容不一致')
  const saved = readPendingPlan(session, pending.project_id, pending.task_id)
  if (!saved || canonicalTemplateValue(saved) !== canonicalTemplateValue(pending)) fail('CONTENT_MISMATCH', '原计划请求身份需要保持一致')
  sessionStorage.removeItem(storageKey(session, pending.project_id, pending.task_id))
}
export async function releaseUnregisteredPlan(session: MedicalSession, pending: PendingPlan, signal: AbortSignal): Promise<void> {
  const result = await recoverSavedPlan(session, pending, signal)
  if (result.plan !== null || result.revision !== pending.expected_revision) fail('REVISION_CONFLICT', '原保存已经登记或任务已经变化，需要读取完整保存结果')
  const saved = readPendingPlan(session, pending.project_id, pending.task_id)
  if (!saved || canonicalTemplateValue(saved) !== canonicalTemplateValue(pending)) fail('CONTENT_MISMATCH', '原保存请求身份需要保持一致')
  sessionStorage.removeItem(storageKey(session, pending.project_id, pending.task_id))
}

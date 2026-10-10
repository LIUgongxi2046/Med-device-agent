import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { projectActionAllowed } from './project-write'

export const taskStatusLabels = {
  NEEDS_MATERIALS: '资料待补充', PLAN_PENDING: '计划待确认', READY: '准备完成', RUNNING: '正在执行',
  REVIEW_PENDING: '审查待完成', SIGN_PENDING: '签发待完成', SIGNED: '已经签发', FAILED: '执行失败',
  RETURNED: '已经退回', CANCEL_REQUESTED: '正在取消', CANCELLED: '已经取消'
} as const
export const taskDocumentLabels = {
  PRODUCT_TECHNICAL_REQUIREMENTS: '产品技术要求', REGISTRATION_DIRECTORY: '注册文档目录', OVERVIEW: '综述资料',
  INSTRUCTIONS_FOR_USE: '说明书', LABEL: '标签', ESSENTIAL_PRINCIPLES: '基本原则', RISK_MANAGEMENT: '风险管理',
  RESEARCH_REPORT: '研究报告', CLINICAL_EVALUATION: '临床评价', QUALITY_SYSTEM: '质量体系',
  CHANGE_REGISTRATION: '变更资料', RENEWAL_REGISTRATION: '延续资料', REGISTRATION_DOSSIER: '注册资料总稿'
} as const
export type TaskStatus = keyof typeof taskStatusLabels
export interface TaskRequirement {
  requirement_id: string; node_id: string | null; field_id: string | null; reason_code: string; allowed_actions: string[]
}
export interface TaskDetail {
  task_id: string; task_revision: string; project_id: string; owner_id: string; status: TaskStatus
  plan_revision: string | null; snapshot_id: string | null; document_ids: string[]
  missing_requirements: TaskRequirement[]; waiting_item_ids: string[]; active_run_ids: string[]
}
export interface TaskItem {
  detail: TaskDetail; document_type: keyof typeof taskDocumentLabels; goal: string; updated_at: string
}
export interface TaskFilter {
  page: number; page_size: number; status?: TaskStatus; owner_id?: string; updated_from?: string; updated_to?: string
  dataset_watermark?: string; filter_sha256?: string
}
export interface TaskPage {
  items: TaskItem[]
  page_info: { page: number; page_size: number; total: string; watermark: string; filter_sha256: string }
}
export const taskFilterReference = 'urn:medical:task-list:1.0.0#/$defs/Filter'
export const taskIdReference = 'urn:medical:template-approval:1.0.0#/$defs/Id'

export async function listTasks(session: MedicalSession, projectId: string, filter: TaskFilter,
  previous: TaskPage | null, signal: AbortSignal): Promise<TaskPage> {
  validate(taskIdReference, projectId)
  const context = session.current_context
  if (!context || !projectActionAllowed(session, 'project.task.read', projectId)) fail('ACCESS_DENIED', '任务列表需要当前企业身份及项目任务读取许可')
  validContext(context); validate(taskFilterReference, filter)
  if (filter.updated_from && filter.updated_to && Date.parse(filter.updated_from) > Date.parse(filter.updated_to)) {
    fail('SCHEMA_INVALID', '任务更新时间范围无效')
  }
  if (filter.dataset_watermark !== undefined) revision(filter.dataset_watermark)
  if (filter.page > 1 && (!previous || previous.page_info.page_size !== filter.page_size
      || previous.page_info.watermark !== filter.dataset_watermark || previous.page_info.filter_sha256 !== filter.filter_sha256)) {
    fail('REVISION_CONFLICT', '任务分页需要同一授权集合和过滤条件')
  }
  const response = envelope(await request.get('/api/projects/' + projectId + '/tasks', {
    medical: 'envelope', signal, params: filter,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
  }))
  const result = validate<TaskPage>('urn:medical:task-list:1.0.0#/$defs/Page', response.data)
  const info = result.page_info
  revision(info.total); revision(info.watermark)
  if (response.resource_revision !== info.watermark || info.page !== filter.page || info.page_size !== filter.page_size
      || filter.dataset_watermark !== undefined && (info.watermark !== filter.dataset_watermark || info.filter_sha256 !== filter.filter_sha256)
      || filter.page > 1 && previous !== null && info.total !== previous.page_info.total) {
    fail('CONTENT_MISMATCH', '任务响应与请求分页、版本或过滤范围不一致')
  }
  const total = BigInt(info.total)
  if (total > BigInt(Number.MAX_SAFE_INTEGER) || (total + BigInt(info.page_size) - 1n) / BigInt(info.page_size) > 2147483647n) {
    fail('REVISION_LIMIT', '任务数量超出原分页组件和接口支持范围')
  }
  const remaining = total - BigInt(info.page - 1) * BigInt(info.page_size)
  const expected = remaining <= 0n ? 0n : remaining < BigInt(info.page_size) ? remaining : BigInt(info.page_size)
  if (BigInt(result.items.length) !== expected || new Set(result.items.map(item => item.detail.task_id)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '任务数量或唯一标识与当前授权集合不一致')
  }
  let preceding: TaskItem | null = null
  for (const item of result.items) {
    const detail = item.detail
    for (const id of [detail.task_id, detail.task_revision, detail.project_id, detail.owner_id, detail.plan_revision,
      detail.snapshot_id, ...detail.document_ids, ...detail.waiting_item_ids, ...detail.active_run_ids]) {
      if (id !== null) validate(taskIdReference, id)
    }
    for (const requirement of detail.missing_requirements) {
      if (requirement.node_id !== null) validate(taskIdReference, requirement.node_id)
      if (requirement.field_id !== null) validate(taskIdReference, requirement.field_id)
    }
    const updated = Date.parse(item.updated_at)
    if (detail.project_id !== projectId || filter.status !== undefined && detail.status !== filter.status
        || filter.owner_id !== undefined && detail.owner_id !== filter.owner_id
        || filter.updated_from !== undefined && updated < Date.parse(filter.updated_from)
        || filter.updated_to !== undefined && updated > Date.parse(filter.updated_to)
        || preceding !== null && (updated > Date.parse(preceding.updated_at)
          || item.updated_at === preceding.updated_at && detail.task_id >= preceding.detail.task_id)) {
      fail('CONTENT_MISMATCH', '任务归属、过滤结果或排列顺序不一致')
    }
    preceding = item
  }
  return result
}

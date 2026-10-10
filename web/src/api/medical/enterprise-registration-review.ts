import request from '@/utils/request'
import { envelope, fail, validate } from './protocol'
import type { MedicalSession } from './protocol'
import { readOwnAccount } from './account'
import { registrationAllowed, registrationTextIssue, validateRegistrationDetail } from './enterprise-registration'
import type { PendingRegistration, RegistrationDetail } from './enterprise-registration'
import { canonicalTemplateValue, templateValueDigest } from './template-approval'

export type ReviewStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'
export type GovernanceAction = 'member.manage' | 'role.manage' | 'project.create'
export interface GovernanceCondition {
  schema_version: '1.0.0'; identity_modes: ('local' | 'oidc')[]; purposes: string[] | null
  delegation: { actions: string[]; role_ids: string[]; resource_kinds: string[]; project_ids: string[] | null; resource_ids: string[] | null }
}
export interface GovernanceInput { action: GovernanceAction; condition: GovernanceCondition; valid_to: string | null }
export interface ReviewBody {
  schema_version: '1.0.0'; decision: 'APPROVE' | 'REJECT'; expected_revision: '1'; reason: string
  approval_attested: true; governance_grants: GovernanceInput[]
}
export interface ReviewReceipt {
  schema_version: '1.0.0'; registration_id: string; operation_id: string; decision: 'APPROVE' | 'REJECT'
  status: 'APPROVED' | 'REJECTED'; revision: '2'; registration_content_sha256: string; request: ReviewBody
  decided_by: string; decided_at: string; tenant_id: string | null; member_id: string | null
  basis: { version_id: string; content_sha256: string } | null
  governance_grants: (GovernanceInput & { grant_id: string; valid_from: string; revision: '1' })[]; content_sha256: string
}
export interface ReviewDetail { schema_version: '1.0.0'; application: RegistrationDetail; review: ReviewReceipt | null; status: ReviewStatus; revision: '1' | '2' }
export interface ReviewPage { schema_version: '1.0.0'; items: ReviewDetail[]; next_cursor: string | null }
const schema = 'urn:medical:enterprise-registration-review:1.0.0#/$defs/'
const pendingSchema = 'urn:medical:enterprise-registration-view:1.0.0'
const endpoint = '/api/enterprise-registration-reviews'
const headers = { 'X-Schema-Version': '1.0.0' }
const purposes: Record<GovernanceAction, string> = {
  'member.manage': 'PERMISSION_ADMINISTRATION', 'role.manage': 'ROLE_ADMINISTRATION', 'project.create': 'PROJECT_ADMINISTRATION'
}
function storageKey(session: MedicalSession, id: string): string {
  if (!registrationAllowed(session)) fail('ACCESS_DENIED', '企业审查需要当前平台管理员身份')
  validate(schema + 'Id', session.user.user_id); validate(schema + 'Id', id)
  return 'medical.enterprise.review:' + session.user.user_id + ':' + id
}
async function current(session: MedicalSession, signal: AbortSignal): Promise<void> {
  if (!registrationAllowed(session)) fail('ACCESS_DENIED', '企业审查需要当前平台管理员身份')
  const account = await readOwnAccount(session, signal)
  if (account.platform_role !== 'PLATFORM_ADMIN') fail('ACCESS_DENIED', '企业审查需要当前平台管理员身份')
}
export function reviewBody(value: ReviewBody): ReviewBody {
  validate(schema + 'Request', value)
  if (registrationTextIssue(value.reason, 4096)) fail('SCHEMA_INVALID', '请填写完整规范的人工审查理由')
  const actions = new Set<string>()
  const grants = value.governance_grants.map(grant => {
    const condition = validate<GovernanceCondition>('urn:medical:permission-decisions:1.0.0#/$defs/Condition', grant.condition)
    if (actions.has(grant.action) || !condition.identity_modes.includes('local')
        || condition.purposes !== null && !condition.purposes.includes(purposes[grant.action])) {
      fail('SCHEMA_INVALID', '治理动作需要唯一标识、本地身份及对应管理用途')
    }
    actions.add(grant.action)
    // 与原审查入口使用相同字段顺序，跨入口核查同一原请求。
    return { action: grant.action, condition: { schema_version: '1.0.0' as const, identity_modes: condition.identity_modes,
      purposes: condition.purposes, delegation: { actions: condition.delegation.actions, role_ids: condition.delegation.role_ids,
        resource_kinds: condition.delegation.resource_kinds, project_ids: condition.delegation.project_ids,
        resource_ids: condition.delegation.resource_ids } }, valid_to: grant.valid_to }
  })
  if (value.decision === 'APPROVE' && !actions.has('member.manage')) fail('SCHEMA_INVALID', '批准企业需要明确成员管理授权')
  const body: ReviewBody = { schema_version: '1.0.0', decision: value.decision, expected_revision: '1', reason: value.reason,
    approval_attested: true, governance_grants: grants }
  if (new TextEncoder().encode(JSON.stringify(body)).byteLength > 65536) fail('SCHEMA_INVALID', '审查请求超过原接口字节上限')
  return body
}
async function requestDigest(body: ReviewBody): Promise<string> {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(reviewBody(body))))
  return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('')
}
async function validReceipt(input: unknown): Promise<ReviewReceipt> {
  const value = validate<ReviewReceipt>(schema + 'Receipt', input)
  const body = reviewBody(value.request); const approved = body.decision === 'APPROVE'
  if (value.decision !== body.decision || value.status !== (approved ? 'APPROVED' : 'REJECTED')
      || (value.tenant_id !== null) !== approved || (value.member_id !== null) !== approved || (value.basis !== null) !== approved
      || value.governance_grants.length !== body.governance_grants.length) fail('CONTENT_MISMATCH', '审查决定、企业身份及治理结果不一致')
  const ids = new Set<string>()
  for (const [index, grant] of value.governance_grants.entries()) {
    const original = body.governance_grants[index]
    if (ids.has(grant.grant_id) || grant.valid_from !== value.decided_at
        || canonicalTemplateValue({ action: grant.action, condition: grant.condition, valid_to: grant.valid_to }) !== canonicalTemplateValue(original)) {
      fail('CONTENT_MISMATCH', '治理授权身份、条件及有效期限不一致')
    }
    ids.add(grant.grant_id)
  }
  if (value.basis !== null) {
    const basis = { schema_version: value.schema_version, registration_id: value.registration_id,
      registration_content_sha256: value.registration_content_sha256, request: value.request, decided_by: value.decided_by,
      decided_at: value.decided_at, tenant_id: value.tenant_id, member_id: value.member_id }
    if (await templateValueDigest(basis) !== value.basis.content_sha256) fail('CONTENT_MISMATCH', '固定企业批准依据摘要不一致')
  }
  const { content_sha256, ...content } = value
  if (await templateValueDigest(content) !== content_sha256) fail('CONTENT_MISMATCH', '固定审查回执完整摘要不一致')
  return value
}
async function validDetail(input: unknown): Promise<ReviewDetail> {
  const value = validate<ReviewDetail>(schema + 'Detail', input)
  const application = await validateRegistrationDetail(value.application)
  if (value.review === null) {
    if (value.status !== 'PENDING_REVIEW' || value.revision !== '1') fail('CONTENT_MISMATCH', '未处理申请的审查状态或版本无效')
  } else {
    const review = await validReceipt(value.review)
    if (value.status !== review.status || value.revision !== review.revision || application.registration_id !== review.registration_id
        || application.content_sha256 !== review.registration_content_sha256) fail('CONTENT_MISMATCH', '原申请与固定审查结果不一致')
  }
  return value
}
export async function listReviews(session: MedicalSession, status: ReviewStatus, cursor: string | null, signal: AbortSignal): Promise<ReviewPage> {
  await current(session, signal)
  if (!['PENDING_REVIEW', 'APPROVED', 'REJECTED'].includes(status)) fail('SCHEMA_INVALID', '审查筛选状态无效')
  if (cursor !== null) validate(schema + 'Id', cursor)
  const response = envelope(await request.get(endpoint, { medical: 'envelope', signal, headers,
    params: { limit: '50', status, ...(cursor === null ? {} : { cursor }) } }))
  const page = validate<ReviewPage>(schema + 'Page', response.data); const ids = new Set<string>()
  for (const input of page.items) {
    const item = await validDetail(input)
    if (item.status !== status || ids.has(item.application.registration_id)) fail('CONTENT_MISMATCH', '审查筛选或分页身份不一致')
    ids.add(item.application.registration_id)
  }
  if (response.resource_revision !== null || page.items.length > 50
      || page.next_cursor !== null && (page.next_cursor === cursor || page.next_cursor !== page.items.at(-1)?.application.registration_id)) {
    fail('CONTENT_MISMATCH', '审查分页版本或游标无效')
  }
  await current(session, signal); return page
}
export async function readReview(session: MedicalSession, id: string, signal: AbortSignal): Promise<ReviewDetail> {
  await current(session, signal); validate(schema + 'Id', id)
  const response = envelope(await request.get(endpoint + '/' + id, { medical: 'envelope', signal, headers }))
  const value = await validDetail(response.data)
  if (value.application.registration_id !== id || response.resource_revision !== value.revision) fail('CONTENT_MISMATCH', '审查详情身份或版本不一致')
  await current(session, signal); return value
}
export function readPendingReview(session: MedicalSession, id: string): PendingRegistration | null {
  const value = sessionStorage.getItem(storageKey(session, id))
  return value === null ? null : validate<PendingRegistration>(pendingSchema, JSON.parse(value))
}
export async function prepareReview(session: MedicalSession, id: string, input: ReviewBody): Promise<PendingRegistration> {
  const body = reviewBody(input); const saved = readPendingReview(session, id); const digest = await requestDigest(body)
  if (saved !== null) {
    if (saved.digest !== digest) fail('IDEMPOTENCY_CONFLICT', '原审查请求待核查，请恢复相同完整参数')
    return saved
  }
  if (body.governance_grants.some(grant => grant.valid_to !== null && Date.parse(grant.valid_to) <= Date.now())) {
    fail('SCHEMA_INVALID', '治理授权期限必须晚于当前提交时刻')
  }
  const pending = validate<PendingRegistration>(pendingSchema, { key: crypto.randomUUID(), digest })
  sessionStorage.setItem(storageKey(session, id), JSON.stringify(pending)); return pending
}
export async function restoreReviewRequest(session: MedicalSession, detail: ReviewDetail, pending: PendingRegistration, signal: AbortSignal): Promise<ReviewBody> {
  await current(session, signal); await validDetail(detail)
  const review = detail.review; const saved = readPendingReview(session, detail.application.registration_id)
  if (review === null || review.decided_by !== session.user.user_id || saved?.key !== pending.key || saved.digest !== pending.digest
      || await requestDigest(review.request) !== pending.digest) fail('CONTENT_MISMATCH', '现有决定无法确认原请求，请保留请求身份并核查完整原参数')
  return reviewBody(review.request)
}
export async function submitReview(session: MedicalSession, detail: ReviewDetail, pending: PendingRegistration, input: ReviewBody, signal: AbortSignal): Promise<ReviewDetail> {
  await current(session, signal); await validDetail(detail); const body = reviewBody(input)
  validate(pendingSchema, pending); const id = detail.application.registration_id; const saved = readPendingReview(session, id)
  if (!saved || saved.key !== pending.key || saved.digest !== pending.digest || await requestDigest(body) !== pending.digest) {
    fail('IDEMPOTENCY_CONFLICT', '原审查请求身份与完整参数不一致')
  }
  const raw = await request.post(endpoint + '/' + id + '/decisions', body, { medical: 'envelope', signal,
    headers: { ...headers, 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const response = envelope(raw); const receipt = await validReceipt(response.data)
  if (raw.status !== 200 || response.resource_revision !== '2' || receipt.registration_id !== id
      || receipt.registration_content_sha256 !== detail.application.content_sha256 || receipt.decided_by !== session.user.user_id
      || canonicalTemplateValue(receipt.request) !== canonicalTemplateValue(body)) fail('CONTENT_MISMATCH', '固定审查回执与原申请及本次决定不一致')
  const actual = await readReview(session, id, signal)
  if (actual.review === null || canonicalTemplateValue(actual.review) !== canonicalTemplateValue(receipt)
      || canonicalTemplateValue(actual.application) !== canonicalTemplateValue(detail.application)) fail('CONTENT_MISMATCH', '审查保存回执与实际重读不一致')
  return actual
}
export async function confirmReview(session: MedicalSession, pending: PendingRegistration, detail: ReviewDetail, signal: AbortSignal): Promise<void> {
  await restoreReviewRequest(session, detail, pending, signal)
  sessionStorage.removeItem(storageKey(session, detail.application.registration_id))
}
export async function abandonReview(session: MedicalSession, id: string, pending: PendingRegistration, signal: AbortSignal): Promise<void> {
  await current(session, signal); const saved = readPendingReview(session, id)
  if (!saved || saved.key !== pending.key || saved.digest !== pending.digest) fail('CONTENT_MISMATCH', '待清除的原审查请求身份已经变化')
  sessionStorage.removeItem(storageKey(session, id))
}

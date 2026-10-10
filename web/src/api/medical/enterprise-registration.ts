import request from '@/utils/request'
import { envelope, fail, revision, validate } from './protocol'
import type { MedicalSession } from './protocol'
import { readOwnAccount } from './account'
import { projectTextIssue } from './project-write'
import { canonicalTemplateValue, templateValueDigest } from './template-approval'

export interface RegistrationBody {
  schema_version: '1.0.0'; legal_name: string; jurisdiction: string; legal_identifier: string
  first_admin_user_id: string; authorization_basis: string; authorization_attested: true
}
export interface RegistrationDetail {
  schema_version: '1.0.0'; registration_id: string; operation_id: string; application: RegistrationBody
  first_admin_binding: { mode: 'local'; user_id: string; issuer: null; subject: null }
  first_admin_revision: string; submitted_by: string; created_at: string; status: 'PENDING_REVIEW'; revision: '1'; content_sha256: string
}
export interface RegistrationPage { schema_version: '1.0.0'; items: RegistrationDetail[]; next_cursor: string | null }
export interface RegistrationAccount {
  user_id: string; username: string; display_name: string; status: 'ACTIVE' | 'DISABLED'
  platform_role: 'USER' | 'PLATFORM_ADMIN'; revision: string; credential_revision: string
}
export interface PendingRegistration { key: string; digest: string }
const schema = 'urn:medical:enterprise-registration:1.0.0#/$defs/'
const pendingSchema = 'urn:medical:enterprise-registration-view:1.0.0'
const endpoint = '/api/enterprise-registrations'
const prefix = 'medical.enterprise.registration:'
const headers = { 'X-Schema-Version': '1.0.0' }

export function registrationAllowed(session: MedicalSession | null): boolean { return session?.user.platform_role === 'PLATFORM_ADMIN' }
function requirePlatform(session: MedicalSession): void {
  if (!registrationAllowed(session)) fail('ACCESS_DENIED', '企业登记需要当前平台管理员身份')
  validate(schema + 'Uuid', session.user.user_id)
}
export function registrationTextIssue(value: string, maximum: number): string {
  return projectTextIssue(value, maximum) || (/\p{Cf}|\p{Cs}/u.test(value) ? '请核查格式字符和Unicode编码' : '')
}
export function registrationBody(value: RegistrationBody): RegistrationBody {
  validate(schema + 'Request', value)
  for (const [field, limit] of [['legal_name', 512], ['jurisdiction', 128], ['legal_identifier', 256], ['authorization_basis', 4096]] as const) {
    if (registrationTextIssue(value[field], limit)) fail('SCHEMA_INVALID', '企业登记字段需要完整规范文本')
  }
  // 保持原登记入口的字段顺序，原请求摘要可以跨入口继续核验。
  return { schema_version: '1.0.0', legal_name: value.legal_name, jurisdiction: value.jurisdiction,
    legal_identifier: value.legal_identifier, first_admin_user_id: value.first_admin_user_id,
    authorization_basis: value.authorization_basis, authorization_attested: true }
}
async function requestDigest(body: RegistrationBody): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(registrationBody(body))))
  return Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('')
}
async function current(session: MedicalSession, signal: AbortSignal): Promise<void> {
  requirePlatform(session)
  const account = await readOwnAccount(session, signal)
  if (account.platform_role !== 'PLATFORM_ADMIN') fail('ACCESS_DENIED', '企业登记需要当前平台管理员身份')
}
export async function validateRegistrationDetail(input: unknown): Promise<RegistrationDetail> {
  const value = validate<RegistrationDetail>(schema + 'Detail', input)
  registrationBody(value.application)
  const { content_sha256, ...content } = value
  if (revision(value.first_admin_revision) === '0'
      || value.first_admin_binding.user_id !== value.application.first_admin_user_id
      || await templateValueDigest(content) !== content_sha256) fail('CONTENT_MISMATCH', '企业申请内容、指定管理员或完整摘要不一致')
  return value
}
async function validDetail(session: MedicalSession, input: unknown): Promise<RegistrationDetail> {
  const value = await validateRegistrationDetail(input)
  if (value.submitted_by !== session.user.user_id) fail('CONTENT_MISMATCH', '本人申请提交身份不一致')
  return value
}
export function readPendingRegistration(session: MedicalSession): PendingRegistration | null {
  requirePlatform(session)
  const value = sessionStorage.getItem(prefix + session.user.user_id)
  return value === null ? null : validate<PendingRegistration>(pendingSchema, JSON.parse(value))
}
export async function prepareRegistration(session: MedicalSession, body: RegistrationBody): Promise<PendingRegistration> {
  requirePlatform(session)
  const digest = await requestDigest(body); const saved = readPendingRegistration(session)
  if (saved && saved.digest !== digest) fail('IDEMPOTENCY_CONFLICT', '原企业登记请求仍待核查，请恢复完整原参数')
  if (saved) return saved
  const pending = validate<PendingRegistration>(pendingSchema, { key: crypto.randomUUID(), digest })
  sessionStorage.setItem(prefix + session.user.user_id, JSON.stringify(pending))
  return pending
}
export async function readRegistrationAccounts(session: MedicalSession, signal: AbortSignal): Promise<RegistrationAccount[]> {
  await current(session, signal)
  let cursor: string | null = null
  const seen = new Set<string>(); const accounts: RegistrationAccount[] = []
  do {
    const value = envelope(await request.get('/api/local-accounts', { medical: 'envelope', signal,
      headers, params: { limit: '100', ...(cursor === null ? {} : { cursor }) } }))
    const page = validate<{ schema_version: '1.0.0'; items: RegistrationAccount[]; next_cursor: string | null }>('urn:medical:identity#/$defs/LocalAccountPage', value.data)
    if (value.resource_revision !== null) fail('CONTENT_MISMATCH', '管理员候选分页版本无效')
    for (const account of page.items) {
      if (revision(account.revision) === '0' || revision(account.credential_revision) === '0' || seen.has(account.user_id)) {
        fail('CONTENT_MISMATCH', '管理员候选版本或分页身份重复')
      }
      seen.add(account.user_id)
      if (account.status === 'ACTIVE') accounts.push(account)
    }
    if (page.next_cursor !== null && (page.next_cursor === cursor || page.next_cursor !== page.items.at(-1)?.user_id)) {
      fail('CONTENT_MISMATCH', '管理员候选游标与当前页不一致')
    }
    cursor = page.next_cursor
  } while (cursor !== null)
  await current(session, signal)
  return accounts
}
export async function listRegistrations(session: MedicalSession, cursor: string | null, signal: AbortSignal): Promise<RegistrationPage> {
  await current(session, signal)
  if (cursor !== null) validate(schema + 'Uuid', cursor)
  const response = envelope(await request.get(endpoint, { medical: 'envelope', signal, headers,
    params: { limit: '50', ...(cursor === null ? {} : { cursor }) } }))
  const page = validate<RegistrationPage>(schema + 'Page', response.data)
  const ids = new Set<string>()
  for (const item of page.items) {
    await validDetail(session, item)
    if (ids.has(item.registration_id)) fail('CONTENT_MISMATCH', '本人申请分页身份重复')
    ids.add(item.registration_id)
  }
  if (response.resource_revision !== null || page.items.length > 50
      || page.next_cursor !== null && (page.next_cursor === cursor || page.next_cursor !== page.items.at(-1)?.registration_id)) {
    fail('CONTENT_MISMATCH', '本人申请分页及游标无效')
  }
  await current(session, signal)
  return page
}
export async function readRegistration(session: MedicalSession, id: string, signal: AbortSignal): Promise<RegistrationDetail> {
  await current(session, signal); validate(schema + 'Uuid', id)
  const response = envelope(await request.get(endpoint + '/' + id, { medical: 'envelope', signal, headers }))
  const value = await validDetail(session, response.data)
  if (response.resource_revision !== value.revision || value.registration_id !== id) fail('CONTENT_MISMATCH', '原申请详情身份与版本不一致')
  await current(session, signal)
  return value
}
export async function submitRegistration(session: MedicalSession, pending: PendingRegistration, input: RegistrationBody, signal: AbortSignal): Promise<RegistrationDetail> {
  const body = registrationBody(input)
  await current(session, signal); validate(pendingSchema, pending)
  const saved = readPendingRegistration(session)
  if (!saved || saved.key !== pending.key || saved.digest !== pending.digest || await requestDigest(body) !== pending.digest) {
    fail('IDEMPOTENCY_CONFLICT', '原申请身份与完整参数不一致')
  }
  const raw = await request.post(endpoint, body, { medical: 'envelope', signal,
    headers: { ...headers, 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const response = envelope(raw); const value = await validDetail(session, response.data)
  if (raw.status !== 201 || response.resource_revision !== '1' || canonicalTemplateValue(value.application) !== canonicalTemplateValue(body)) {
    fail('CONTENT_MISMATCH', '企业登记原回执与本次完整参数不一致')
  }
  const actual = await readRegistration(session, value.registration_id, signal)
  if (canonicalTemplateValue(value) !== canonicalTemplateValue(actual)) fail('CONTENT_MISMATCH', '原申请保存回执与实际重读不一致')
  return actual
}
export async function confirmRegistration(session: MedicalSession, pending: PendingRegistration, detail: RegistrationDetail): Promise<void> {
  const saved = readPendingRegistration(session)
  if (!saved || saved.key !== pending.key || saved.digest !== pending.digest || await requestDigest(detail.application) !== pending.digest) {
    fail('CONTENT_MISMATCH', '原申请结果仍需核查')
  }
  sessionStorage.removeItem(prefix + session.user.user_id)
}

export async function abandonRegistration(session: MedicalSession, pending: PendingRegistration, signal: AbortSignal): Promise<void> {
  await current(session, signal)
  const saved = readPendingRegistration(session)
  if (!saved || saved.key !== pending.key || saved.digest !== pending.digest) fail('CONTENT_MISMATCH', '待清除的原企业申请身份已经变化')
  // 人员明确核查后只清除浏览器请求身份，服务器原申请继续保留。
  sessionStorage.removeItem(prefix + session.user.user_id)
}

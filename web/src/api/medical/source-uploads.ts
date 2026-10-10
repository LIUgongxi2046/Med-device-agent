import request from '@/utils/request'
import { canonicalizeEx } from 'json-canonicalize'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Context, MedicalSession } from '@/api/medical/protocol'
import { lookupOperation, readOperation } from '@/api/medical/operations'
import type { Operation, OperationLookup } from '@/api/medical/operations'

export type SourceOwnership = 'ENTERPRISE' | 'PROJECT' | 'SESSION'
export type SourceClassification = 'PUBLIC' | 'INTERNAL' | 'RESTRICTED' | 'PERSONAL_HEALTH'
export interface SourceProvenance {
  owner: string; publisher: string | null; maintainer_id: string; original_url: string | null; final_url: string | null
  publisher_version: string | null; published_at: string | null; issued_on: string | null; effective_from: string | null
  effective_to: string | null; observed_status: string | null; verified_at: string | null; status_evidence: string[]
  acquired_at: string | null; acquired_by: string | null; acquisition_method: string | null; update_frequency: string | null
  next_check_at: string | null; retention_policy_id: string | null; legal_hold: boolean | null
}
export interface SourceRights {
  purpose: string; license: string; allowed_actions: ('READ' | 'STORE' | 'ENTERPRISE_USE' | 'DISTRIBUTE' | 'EXTERNAL_PROCESS')[]
  providers: string[]; regions: string[]; valid_from: string | null; valid_to: string | null; authorization_evidence: string[]
}
export interface SourceUploadCreate {
  schema_version: '1.0.0'; batch_id: string; project_id: string; ownership: SourceOwnership; session_id?: string | null
  file_name: string; media_type: string; size_bytes: string; sha256?: string | null; source_id?: string | null
  version_intent: 'NEW_SOURCE' | 'NEW_VERSION'; provenance: SourceProvenance; classification: SourceClassification; rights_declaration: SourceRights
}
export interface SourcePart { part_no: number; size_bytes: string; sha256: string; state: 'RESERVED' | 'WRITING' | 'STORED' | 'FAILED' }
export interface SourceRegistration {
  upload_id: string; request: SourceUploadCreate; received_size: string
  upload_status: 'PENDING' | 'UPLOADING' | 'RECEIVED' | 'FAILED'
  transfer_state: 'ACTIVE' | 'COMPLETING' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED' | 'FAILED'
  max_part_bytes: string; expires_at: string; parts: SourcePart[]; next_part: number | null
}
export interface SourceUploadState { registration: SourceRegistration; revision: string }
export interface UploadRequestIdentity {
  operation: 'KN-01' | 'KN-02' | 'KN-03'; key: string; context: Context; body_sha256: string
  part_no: number | null; size_bytes: string | null; sha256: string | null; expected_revision: string | null
}
export interface UploadJournal {
  schema_version: '1.0.0'; actor: string; tenant_id: string; project_id: string; item_id: string; batch_id: string
  file_sha256: string; size_bytes: string; declaration_sha256: string; upload_id: string | null
  requests: UploadRequestIdentity[]
}
export interface SourceCompletion {
  schema_version: '1.0.0'; expected_revision: string; version_intent: 'NEW_SOURCE' | 'NEW_VERSION'; source_id?: string | null
  size_bytes: string; sha256: string; parts: { part_no: number; size_bytes: string; sha256: string }[]
}
const sourceSchema = 'urn:medical:source-uploads:1.0.0#/$defs/'
const journalSchema = 'urn:medical:source-upload-view:1.0.0'
const prefix = 'medical.source.upload:'
export const uploadStatusLabels = { PENDING: '待上传', UPLOADING: '接收中', RECEIVED: '已接收', FAILED: '接收失败' } as const
export const transferStatusLabels = { ACTIVE: '允许发送', COMPLETING: '正在完成接收', COMPLETED: '传输完成', CANCELLED: '已取消', EXPIRED: '已过期', FAILED: '传输失败' } as const
export const sourceMediaTypes: Record<string, string> = {
  pdf: 'application/pdf', docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', csv: 'text/csv',
  pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation', md: 'text/markdown', markdown: 'text/markdown', txt: 'text/plain'
}
export function sourceMediaType(file: File): string {
  const extension = file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase()
  const media = sourceMediaTypes[extension]
  if (!media) fail('FILE_TYPE_UNSUPPORTED', '请选择 PDF、DOCX、XLSX、CSV、PPTX、Markdown 或 TXT 文件')
  if (file.size === 0 || !Number.isSafeInteger(file.size)) fail('SCHEMA_INVALID', '文件必须包含内容并具有可核验的大小')
  return media
}
export async function sourceDigest(value: unknown): Promise<string> {
  return byteDigest(new TextEncoder().encode(canonicalizeEx(value, { strictUndefined: true })))
}
export async function byteDigest(value: BufferSource): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', value)
  return Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('')
}
function context(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '上传资料需要当前企业身份及来源维护权限')
  validContext(session.current_context); return session.current_context
}
export function sourceUploadAllowed(session: MedicalSession | null, projectId: string, declaration?: SourceUploadCreate): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => {
    if (item.action !== 'source.write') return false
    if (!declaration) return item.resource_kind === 'ENTERPRISE' && item.resource_id === session.current_context?.tenant_id && item.project_id === null
      || item.project_id === projectId && item.resource_kind === 'PROJECT' && item.resource_id === projectId
      || item.resource_kind === 'SOURCE' && (item.project_id === null || item.project_id === projectId)
    if (declaration.version_intent === 'NEW_VERSION') return item.resource_kind === 'SOURCE' && (item.resource_id === null || item.resource_id === declaration.source_id)
      && (item.project_id === null || declaration.ownership !== 'ENTERPRISE' && item.project_id === projectId)
      || item.resource_kind === 'ENTERPRISE' && item.resource_id === session.current_context?.tenant_id && item.project_id === null
      || declaration.ownership !== 'ENTERPRISE' && item.resource_kind === 'PROJECT' && item.resource_id === projectId && item.project_id === projectId
    return declaration.ownership === 'ENTERPRISE'
      ? item.resource_kind === 'ENTERPRISE' && item.resource_id === session.current_context?.tenant_id && item.project_id === null
      : item.resource_kind === 'ENTERPRISE' && item.resource_id === session.current_context?.tenant_id && item.project_id === null
        || item.resource_kind === 'PROJECT' && item.resource_id === projectId && item.project_id === projectId
  })
}
function storagePrefix(session: MedicalSession, projectId: string): string {
  validate(sourceSchema + 'Id', projectId)
  return prefix + session.user.user_id + ':' + context(session).tenant_id + ':' + projectId + ':'
}
function requireJournal(session: MedicalSession, value: UploadJournal): void {
  validate(journalSchema, value)
  if (value.actor !== session.user.user_id || value.tenant_id !== context(session).tenant_id) fail('CONTENT_MISMATCH', '上传记录与当前主体及企业不一致')
  revision(value.size_bytes)
  if (BigInt(value.size_bytes) > BigInt(Number.MAX_SAFE_INTEGER) || value.requests.filter(item => item.operation === 'KN-01').length !== 1) {
    fail('CONTENT_MISMATCH', '上传记录缺少唯一登记操作或文件大小超出浏览器范围')
  }
  const identities = new Set<string>(); const keys = new Set<string>()
  for (const item of value.requests) {
    validContext(item.context)
    const identity = item.operation + ':' + item.part_no
    if (item.context.tenant_id !== value.tenant_id || identities.has(identity) || keys.has(item.key)
        || item.operation === 'KN-01' && item.body_sha256 !== value.declaration_sha256
        || item.operation === 'KN-02' && item.body_sha256 !== item.sha256
        || item.operation !== 'KN-01' && value.upload_id === null) fail('CONTENT_MISMATCH', '上传原请求的企业、摘要或操作身份不一致')
    identities.add(identity); keys.add(item.key)
    if (item.size_bytes !== null) revision(item.size_bytes)
    if (item.expected_revision !== null) revision(item.expected_revision)
  }
}
export function saveUploadJournal(session: MedicalSession, value: UploadJournal): void {
  requireJournal(session, value)
  sessionStorage.setItem(storagePrefix(session, value.project_id) + value.item_id, JSON.stringify(value))
}
export function readUploadJournals(session: MedicalSession, projectId: string): UploadJournal[] {
  const keyPrefix = storagePrefix(session, projectId)
  return Object.keys(sessionStorage).filter(key => key.startsWith(keyPrefix)).map(key => {
    const raw = sessionStorage.getItem(key)
    if (raw === null) fail('CONTENT_MISMATCH', '上传记录在读取期间发生变化')
    const value = validate<UploadJournal>(journalSchema, JSON.parse(raw)); requireJournal(session, value)
    if (value.project_id !== projectId || key !== keyPrefix + value.item_id) fail('CONTENT_MISMATCH', '上传记录与当前项目不一致')
    return value
  })
}
export async function prepareSourceUpload(session: MedicalSession, itemId: string, body: SourceUploadCreate, fileHash: string, signal: AbortSignal): Promise<UploadJournal> {
  validate(sourceSchema + 'Create', body); validate(sourceSchema + 'Hash', fileHash)
  if (!sourceUploadAllowed(session, body.project_id, body)) fail('ACCESS_DENIED', '当前资料归属或版本没有来源维护权限')
  if (body.sha256 !== fileHash) fail('CONTENT_MISMATCH', '上传声明与所选文件摘要不一致')
  const declarationHash = await sourceDigest(body)
  signal.throwIfAborted()
  const saved = readUploadJournals(session, body.project_id).find(value => value.item_id === itemId)
  if (saved) {
    if (saved.declaration_sha256 !== declarationHash || saved.file_sha256 !== fileHash || saved.size_bytes !== body.size_bytes) {
      fail('IDEMPOTENCY_CONFLICT', '原上传仍待核查，请使用完整原声明和原文件')
    }
    return saved
  }
  const value: UploadJournal = { schema_version: '1.0.0', actor: session.user.user_id, tenant_id: context(session).tenant_id,
    project_id: body.project_id, item_id: itemId, batch_id: body.batch_id, file_sha256: fileHash, size_bytes: body.size_bytes,
    declaration_sha256: declarationHash, upload_id: null, requests: [{ operation: 'KN-01', key: crypto.randomUUID(),
      context: { ...context(session) }, body_sha256: declarationHash, part_no: null, size_bytes: null, sha256: null, expected_revision: null }] }
  saveUploadJournal(session, value); return value
}
function headers(session: MedicalSession, identity: UploadRequestIdentity): Record<string, string> {
  if (identity.context.tenant_id !== context(session).tenant_id) fail('CONTENT_MISMATCH', '原请求与当前企业不一致')
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(session).context_revision,
    'Idempotency-Key': identity.key, [session.csrf.header_name]: session.csrf.token }
}
export function uploadIdentity(journal: UploadJournal, operation: UploadRequestIdentity['operation'], partNo: number | null = null): UploadRequestIdentity {
  const value = journal.requests.find(item => item.operation === operation && item.part_no === partNo)
  if (!value) fail('CONTENT_MISMATCH', '上传操作缺少原请求身份')
  return value
}
export async function lookupUploadRequest(session: MedicalSession, journal: UploadJournal, identity: UploadRequestIdentity, signal: AbortSignal): Promise<OperationLookup> {
  requireJournal(session, journal)
  const value = await lookupOperation(session, identity.operation, identity.key, signal)
  if (value.operation && (value.operation.resource_id === null || journal.upload_id !== null && value.operation.resource_id !== journal.upload_id)) {
    fail('CONTENT_MISMATCH', '原上传操作与上传对象不一致')
  }
  if (value.operation) {
    const actual = await readOperation(session, value.operation.operation_id, signal)
    if (actual.resource_id !== value.operation.resource_id) fail('CONTENT_MISMATCH', '上传操作在读取期间更换了目标')
    value.operation = actual
  }
  return value
}
export async function createSourceUpload(session: MedicalSession, journal: UploadJournal, body: SourceUploadCreate, signal: AbortSignal): Promise<SourceUploadState> {
  requireJournal(session, journal); validate(sourceSchema + 'Create', body)
  if (await sourceDigest(body) !== journal.declaration_sha256 || !sourceUploadAllowed(session, journal.project_id, body)) {
    fail('CONTENT_MISMATCH', '上传原声明或当前许可不一致')
  }
  const identity = uploadIdentity(journal, 'KN-01')
  const raw = await request.post('/api/uploads', body, { medical: 'envelope', signal, headers: headers(session, identity) })
  const response = envelope(raw)
  const value = validate<{ upload_id: string; max_part_bytes: string; expires_at: string }>(sourceSchema + 'Created', response.data)
  if (raw.status !== 201 || response.resource_revision !== '1' || journal.upload_id !== null && journal.upload_id !== value.upload_id) {
    fail('CONTENT_MISMATCH', '上传登记回执与原请求不一致')
  }
  journal.upload_id = value.upload_id; saveUploadJournal(session, journal)
  const result = await readSourceUpload(session, journal, signal)
  const operation = await lookupUploadRequest(session, journal, identity, signal)
  if (operation.lookup_status !== 'FOUND' || operation.operation?.status !== 'COMPLETED') fail('CONTENT_MISMATCH', '上传登记尚未取得完整原操作结果')
  return result
}
export async function readSourceUpload(session: MedicalSession, journal: UploadJournal, signal: AbortSignal): Promise<SourceUploadState> {
  requireJournal(session, journal)
  if (journal.upload_id === null) fail('STEP_NOT_READY', '原上传登记需要通过原操作查询核查')
  let after = 0; let result: SourceUploadState | null = null; const parts: SourcePart[] = []
  do {
    const response = envelope(await request.get('/api/uploads/' + journal.upload_id, { medical: 'envelope', signal,
      params: { after_part: after, limit: 100 }, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(session).context_revision } }))
    const value = validate<SourceRegistration>(sourceSchema + 'Registration', response.data)
    if (response.resource_revision === null || revision(response.resource_revision) === '0') fail('CONTENT_MISMATCH', '上传记录缺少当前修订')
    if (value.upload_id !== journal.upload_id || value.request.project_id !== journal.project_id || value.request.batch_id !== journal.batch_id
        || value.request.size_bytes !== journal.size_bytes || await sourceDigest(value.request) !== journal.declaration_sha256
        || result && (result.revision !== response.resource_revision || result.registration.received_size !== value.received_size
          || result.registration.transfer_state !== value.transfer_state || result.registration.upload_status !== value.upload_status)) {
      fail('CONTENT_MISMATCH', '上传声明、分段分页或版本发生变化，请重新查询')
    }
    revision(value.received_size); revision(value.max_part_bytes)
    for (const part of value.parts) {
      if (part.part_no <= after || part.part_no > Math.ceil(Number(journal.size_bytes) / Number(value.max_part_bytes))) fail('CONTENT_MISMATCH', '分段序号与上传范围不一致')
      revision(part.size_bytes); after = part.part_no; parts.push(part)
    }
    if (value.next_part !== null && (value.parts.length !== 100 || value.next_part !== after)) fail('CONTENT_MISMATCH', '上传分段分页游标无效')
    result = { registration: value, revision: response.resource_revision }
  } while (result.registration.next_part !== null)
  result.registration.parts = parts; result.registration.next_part = null
  const stored = parts.filter(part => part.state === 'STORED').reduce((sum, part) => sum + BigInt(part.size_bytes), 0n)
  if (stored.toString() !== result.registration.received_size || stored > BigInt(journal.size_bytes)) fail('CONTENT_MISMATCH', '分段记录与服务器接收数量不一致')
  return result
}
export async function prepareUploadPart(session: MedicalSession, journal: UploadJournal, partNo: number, blob: Blob, signal: AbortSignal): Promise<UploadRequestIdentity> {
  requireJournal(session, journal)
  if (journal.upload_id === null) fail('STEP_NOT_READY', '分段发送需要已核查的原上传对象')
  const sha256 = await byteDigest(await blob.arrayBuffer())
  signal.throwIfAborted()
  const saved = journal.requests.find(item => item.operation === 'KN-02' && item.part_no === partNo)
  if (saved) {
    if (saved.body_sha256 !== sha256 || saved.sha256 !== sha256 || saved.size_bytes !== String(blob.size)) fail('IDEMPOTENCY_CONFLICT', '原分段文件内容已经变化')
    return saved
  }
  const value: UploadRequestIdentity = { operation: 'KN-02', key: crypto.randomUUID(), context: { ...context(session) }, body_sha256: sha256,
    part_no: partNo, size_bytes: String(blob.size), sha256, expected_revision: null }
  journal.requests.push(value); saveUploadJournal(session, journal); return value
}
export async function sendUploadPart(session: MedicalSession, journal: UploadJournal, identity: UploadRequestIdentity, blob: Blob,
  signal: AbortSignal, progress: (bytes: number) => void): Promise<SourcePart | Operation> {
  requireJournal(session, journal)
  const original = uploadIdentity(journal, 'KN-02', identity.part_no)
  if (identity.key !== original.key || identity.body_sha256 !== original.body_sha256 || !journal.upload_id || identity.part_no === null || identity.sha256 === null || identity.size_bytes !== String(blob.size)
      || await byteDigest(await blob.arrayBuffer()) !== identity.sha256) fail('CONTENT_MISMATCH', '分段请求与原文件、大小或摘要不一致')
  const raw = await request.put('/api/uploads/' + journal.upload_id + '/parts/' + identity.part_no, blob, {
    medical: 'envelope', signal, timeout: 70000,
    headers: { ...headers(session, identity), 'Content-Type': 'application/octet-stream', 'Content-SHA256': identity.sha256 },
    onUploadProgress: event => progress(event.loaded)
  })
  const response = envelope(raw)
  if (raw.status === 202) {
    const operation = validate<Operation>('urn:medical:shared#/$defs/Operation', response.data)
    if (response.resource_revision !== null || operation.resource_id !== journal.upload_id
        || operation.status_url !== '/api/operations/' + operation.operation_id) fail('CONTENT_MISMATCH', '分段处理操作与当前上传不一致')
    return operation
  }
  const part = validate<SourcePart>(sourceSchema + 'Part', response.data)
  if (raw.status !== 200 || response.resource_revision === null || part.part_no !== identity.part_no || part.sha256 !== identity.sha256
      || part.size_bytes !== identity.size_bytes || part.state !== 'STORED') fail('CONTENT_MISMATCH', '分段保存回执未通过原内容核查')
  return part
}
export async function completeSourceUpload(session: MedicalSession, journal: UploadJournal, state: SourceUploadState, signal: AbortSignal): Promise<Operation> {
  requireJournal(session, journal)
  const registration = state.registration
  if (registration.upload_id !== journal.upload_id || registration.received_size !== journal.size_bytes
      || registration.parts.some(part => part.state !== 'STORED')) fail('STEP_NOT_READY', '全部分段接收并核查后才可提交完成命令')
  const saved = journal.requests.find(item => item.operation === 'KN-03')
  const body: SourceCompletion = { schema_version: '1.0.0', expected_revision: saved?.expected_revision ?? state.revision,
    version_intent: registration.request.version_intent, size_bytes: journal.size_bytes, sha256: journal.file_sha256,
    parts: registration.parts.map(part => ({ part_no: part.part_no, size_bytes: part.size_bytes, sha256: part.sha256 })) }
  if (registration.request.source_id !== undefined) body.source_id = registration.request.source_id
  validate(sourceSchema + 'Completion', body)
  const hash = await sourceDigest(body)
  signal.throwIfAborted()
  if (saved && saved.body_sha256 !== hash) fail('IDEMPOTENCY_CONFLICT', '原上传完成命令与当前分段不一致')
  const identity: UploadRequestIdentity = saved ?? { operation: 'KN-03', key: crypto.randomUUID(), context: { ...context(session) },
    body_sha256: hash, part_no: null, size_bytes: null, sha256: null, expected_revision: body.expected_revision }
  if (!saved) { journal.requests.push(identity); saveUploadJournal(session, journal) }
  const raw = await request.post('/api/uploads/' + journal.upload_id + '/completions', body, { medical: 'envelope', signal, headers: headers(session, identity) })
  const response = envelope(raw); const operation = validate<Operation>('urn:medical:shared#/$defs/Operation', response.data)
  if (raw.status !== 202 || response.resource_revision !== null || operation.resource_id !== journal.upload_id
      || operation.status_url !== '/api/operations/' + operation.operation_id) fail('CONTENT_MISMATCH', '完成命令回执与原上传不一致')
  return operation
}

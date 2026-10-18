import request from '@/utils/request'
import { envelope, fail, validate, validContext } from './protocol'
import type { ApiError, Context, MedicalSession } from './protocol'
import type { DocumentSnapshot } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue as canonical } from './document-body'

export interface DocumentRevisionEntry {
  document_revision: string
  body_sha256: string
  created_by: string
  created_at: string
  is_current: boolean
  read_url: string
  check_ids: string[]
  review_ids: string[]
  signature_ids: string[]
}
export interface DocumentRevisionPage {
  schema_version: '1.0.0'
  document_id: string
  current_document_revision: string | null
  document_head_revision: string
  items: DocumentRevisionEntry[]
  next_cursor: string | null
  complete: boolean
}
export interface DocumentHistoryAvailability {
  schema_version: '1.0.0'
  current_context: Context
  features: { document_history: boolean }
  maximum_page_size: number | null
}
export type DocumentRevisionRead =
  | { kind: 'PAGE'; page: DocumentRevisionPage }
  | { kind: 'CURSOR_STALE' }
const directoryRef = 'urn:medical:document-revision-directory:1.0.0#/$defs/'
const availabilityRef = 'urn:medical:document-history-feature-availability:1.0.0'

function scope(session: MedicalSession) {
  if (!session.current_context) fail('ACCESS_DENIED', '历史目录需要当前企业身份')
  validContext(session.current_context)
  return { actor: session.user.user_id, context: { ...session.current_context } }
}
function headers(context: Context) {
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
}
function unchanged(session: MedicalSession, expected: ReturnType<typeof scope>, signal: AbortSignal) {
  signal.throwIfAborted()
  if (session.user.user_id !== expected.actor || canonical(session.current_context) !== canonical(expected.context)) {
    fail('CONTEXT_CHANGED', '历史目录读取期间身份已变化')
  }
}
export async function documentHistoryAvailability(session: MedicalSession, signal: AbortSignal): Promise<DocumentHistoryAvailability> {
  const expected = scope(session)
  const raw = await request.get('/api/document-history-feature-availability', {
    medical: 'envelope', signal, headers: headers(expected.context)
  })
  unchanged(session, expected, signal)
  const wrapped = envelope(raw)
  const value = validate<DocumentHistoryAvailability>(availabilityRef, wrapped.data)
  if (raw.status !== 200 || wrapped.resource_revision !== expected.context.context_revision ||
      canonical(value.current_context) !== canonical(expected.context) ||
      value.features.document_history !== (value.maximum_page_size !== null) ||
      value.maximum_page_size !== null && (!Number.isSafeInteger(value.maximum_page_size) || value.maximum_page_size < 1)) {
    fail('CONTENT_MISMATCH', '文稿历史服务状态与当前身份或容量不一致')
  }
  return value
}

export async function readDocumentRevisionDirectory(
  session: MedicalSession, snapshot: DocumentSnapshot, limit: number,
  signal: AbortSignal, cursor?: string
): Promise<DocumentRevisionRead> {
  const expected = scope(session)
  validate(directoryRef + 'Id', snapshot.document_id)
  if (!Number.isSafeInteger(limit) || limit < 1 || limit > 20) fail('SCHEMA_INVALID', '历史目录分页容量无效')
  if (cursor !== undefined && !/^[A-Za-z0-9._~-]{16,2048}$/.test(cursor)) fail('SCHEMA_INVALID', '历史目录分页标识无效')
  const documentId = snapshot.document_id
  const raw = await request.get('/api/documents/' + documentId + '/revisions', {
    medical: 'envelope', signal, headers: headers(expected.context),
    params: { limit, ...(cursor === undefined ? {} : { cursor }) }
  })
  unchanged(session, expected, signal)
  if (raw.status === 409 && ['CURSOR_EXPIRED', 'CURSOR_INVALID'].includes((raw.data as { code?: string } | null)?.code ?? '')) {
    if (cursor === undefined || raw.headers['cache-control'] !== 'no-store' || raw.headers['x-schema-version'] !== '1.0.0') {
      fail('SCHEMA_INVALID', '历史目录分页失效响应不完整')
    }
    const error = validate<ApiError>('urn:medical:shared#/$defs/Error', raw.data)
    if (raw.headers['x-request-id'] !== error.request_id || error.operation_id !== null || error.resource_revision !== null ||
        error.allowed_actions.length !== 0 || error.details.length !== 0 || error.retryable !== false) {
      fail('CONTENT_MISMATCH', '历史目录分页失效响应不一致')
    }
    return { kind: 'CURSOR_STALE' }
  }
  const wrapped = envelope(raw)
  const page = validate<DocumentRevisionPage>(directoryRef + 'Page', wrapped.data)
  if (raw.status !== 200 || wrapped.resource_revision !== page.document_head_revision ||
      raw.headers['x-resource-revision'] !== page.document_head_revision || page.document_id !== documentId ||
      page.items.length > limit || page.complete !== (page.next_cursor === null) ||
      cursor !== undefined && page.next_cursor === cursor) {
    fail('CONTENT_MISMATCH', '历史目录与文稿或分页范围不一致')
  }
  const timestamp = (value: string) => {
    const parts = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/.exec(value)
    if (!parts) fail('CONTENT_MISMATCH', '历史目录时间不是固定 UTC 微秒格式')
    return parts[1] + '.' + (parts[2] ?? '').padEnd(6, '0') + 'Z'
  }
  let previous: DocumentRevisionEntry | undefined
  const ids = new Set<string>()
  for (const entry of page.items) {
    const order = previous === undefined ? -1 : timestamp(entry.created_at).localeCompare(timestamp(previous.created_at))
    timestamp(entry.created_at)
    if (ids.has(entry.document_revision) || !Number.isFinite(Date.parse(entry.created_at)) ||
        entry.is_current !== (entry.document_revision === page.current_document_revision) ||
        entry.read_url !== '/api/documents/' + documentId + '/revisions/' + entry.document_revision ||
        previous !== undefined && (order > 0 || order === 0 && entry.document_revision >= previous.document_revision)) {
      fail('CONTENT_MISMATCH', '历史条目版本、顺序或固定读取地址不一致')
    }
    ids.add(entry.document_revision)
    previous = entry
  }
  return { kind: 'PAGE', page }
}

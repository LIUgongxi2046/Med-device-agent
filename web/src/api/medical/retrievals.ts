import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import type { Operation } from './operations'
import { projectActionAllowed } from './project-write'
import { lookupOperation } from './operations'
import { expireRequestMetadata, prepareRequestMetadata, readRequestMetadata } from './request-metadata'

export interface RetrievalInput {
  schema_version: '1.0.0'; project_id: string; query: string; purpose: 'VERIFICATION'
}
export interface RetrievalHit {
  rank: number; source_id: string; source_version: string; parse_version: string; index_version: string
  chunk_id: string; approval_revision: string; title: string; excerpt: string; excerpt_truncated: boolean
  locator_kind: 'PDF' | 'DOCX' | 'XLSX' | 'CSV' | 'TXT' | 'MARKDOWN' | 'PPTX'
  locator: Record<string, unknown>; quote_hash: string; relevance: string
}
export interface RetrievalPage {
  schema_version: '1.0.0'; retrieval_id: string; project_id: string; query: string
  purpose: 'VERIFICATION'; mode: 'FULLTEXT'; status: 'FOUND' | 'EMPTY'; created_at: string
  items: RetrievalHit[]
  page_info: { page: number; page_size: number; total: string; selection_sha256: string; next_page: number | null }
}

export function retrievalAllowed(session: MedicalSession | null, projectId?: string): boolean {
  const context = session?.current_context
  return Boolean(session && context && projectId && projectActionAllowed(session, 'project.read', projectId)
    && session.action_summary.some(item => item.action === 'source.read' && (
      item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === context.tenant_id
      || item.resource_kind === 'PROJECT' && item.project_id === projectId && item.resource_id === projectId
      || item.resource_kind === 'SOURCE' && (item.project_id === null || item.project_id === projectId)
    )))
}

function current(session: MedicalSession): string {
  if (session.current_context === null) fail('ACCESS_DENIED', '资料检索需要当前企业身份')
  validContext(session.current_context)
  return session.current_context.context_revision
}

export async function createRetrieval(session: MedicalSession, input: RetrievalInput, signal: AbortSignal): Promise<Operation> {
  if (!retrievalAllowed(session, input.project_id)) fail('ACCESS_DENIED', '资料检索需要当前项目和来源读取许可')
  validate<RetrievalInput>('urn:medical:retrieval-view:1.0.0#/$defs/SearchInput', input)
  const previous = readRequestMetadata(session, 'KN-14-S04', input.project_id)
  const pending = await prepareRequestMetadata(session, 'KN-14-S04', input.project_id, input.project_id, input.project_id, input, signal)
  if (previous !== null) {
    if (previous.state === 'EXPIRED') fail('ACTION_STALE', '原检索请求已经过期')
    const lookup = await lookupOperation(session, previous.operation, previous.key, signal)
    if (lookup.lookup_status === 'EXPIRED') { expireRequestMetadata(session, previous); fail('ACTION_STALE', '原检索请求已经过期') }
    if (lookup.operation !== null) {
      if (lookup.operation.status !== 'COMPLETED' || lookup.operation.resource_id === null) fail('ACTION_STALE', '原检索请求尚未取得完成结果')
      return lookup.operation
    }
  }
  signal.throwIfAborted()
  const value = envelope(await request.post('/api/retrievals', input, { medical: 'envelope', signal, headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision, 'Idempotency-Key': pending.key,
    [session.csrf.header_name]: session.csrf.token
  } }))
  const operation = validate<Operation>('urn:medical:shared#/$defs/Operation', value.data)
  if (value.resource_revision !== null || operation.status !== 'COMPLETED' || operation.resource_id === null
      || operation.status_url !== '/api/operations/' + operation.operation_id) {
    fail('CONTENT_MISMATCH', '资料检索受理结果与固定集合身份不一致')
  }
  return operation
}

export async function readRetrieval(session: MedicalSession, retrievalId: string, page: number,
    pageSize: number, signal: AbortSignal): Promise<RetrievalPage> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', retrievalId)
  if (!Number.isSafeInteger(page) || page < 1 || !Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > 100) {
    fail('SCHEMA_INVALID', '资料检索分页范围无效')
  }
  const value = envelope(await request.get('/api/retrievals/' + retrievalId, { medical: 'envelope', signal,
    params: { page: page.toString(), page_size: pageSize.toString() }, headers: {
      'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session)
    } }))
  const result = validate<RetrievalPage>('urn:medical:retrieval-view:1.0.0#/$defs/Page', value.data)
  revision(result.page_info.total)
  const start = BigInt(page - 1) * BigInt(pageSize)
  const count = BigInt(result.page_info.total)
  const remaining = count > start ? count - start : 0n
  const expectedLength = Number(remaining > BigInt(pageSize) ? BigInt(pageSize) : remaining)
  const expectedNext = start + BigInt(expectedLength) < count ? page + 1 : null
  if (value.resource_revision !== null || result.retrieval_id !== retrievalId || result.page_info.page !== page
      || result.page_info.page_size !== pageSize || (result.status === 'EMPTY') !== (result.page_info.total === '0')
      || result.items.length !== expectedLength || result.page_info.next_page !== expectedNext
      || new Set(result.items.map(item => item.rank)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '资料检索页面与固定集合不一致')
  }
  for (const [index, item] of result.items.entries()) {
    validate('https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json#/$defs/locator', item.locator)
    if (item.locator.kind !== item.locator_kind || BigInt(item.rank) !== start + BigInt(index + 1)) {
      fail('CONTENT_MISMATCH', '资料检索命中位置或顺序无效')
    }
  }
  return result
}

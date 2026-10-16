import request from '@/utils/request'
import { canonicalizeEx } from 'json-canonicalize'
import { envelope, fail, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { lookupOperation } from './operations'
import type { Operation } from './operations'
import { expireRequestMetadata, prepareRequestMetadata, requireRequestMetadata, requestBodyDigest } from './request-metadata'
import type { RequestMetadata } from './request-metadata'
import { exactLiteral, exactReference, verifyExactManifest, verifyExactPage, verifyExactPublication, verifyExactReceipt } from './source-exact-domain'
import type { ExactManifest, ExactPage, ExactPublication, ExactReceipt, ExactSearchInput } from './source-exact-domain'
import { retrievalAllowed } from './retrievals'

function current(session: MedicalSession) {
  if (!session.current_context) fail('ACCESS_DENIED', '结构化字段需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
function readHeaders(session: MedicalSession) { return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }
export async function readExactFields(session: MedicalSession, source: string, projectId: string,
    selection: { field_revision: string } | { parse_version: string; approval_revision: string }, signal: AbortSignal): Promise<ExactManifest> {
  for (const id of [source, projectId, ...Object.values(selection)]) validate('urn:medical:templates:1.0.0#/$defs/Id', id)
  const fixed = 'field_revision' in selection
  const raw = await request.get('/api/source-versions/' + source + '/exact-fields' + (fixed ? '/' + selection.field_revision : ''), {
    medical: 'envelope', signal, params: { project_id: projectId, ...(fixed ? {} : selection) }, headers: readHeaders(session) })
  const response = envelope(raw), value = validate<ExactManifest>(exactReference + 'Manifest', response.data)
  verifyExactManifest(value, current(session).tenant_id, source, projectId)
  if (raw.status !== 200 || response.resource_revision !== value.revision || (fixed ? value.field_revision !== selection.field_revision
      : value.request.parse_version !== selection.parse_version || value.request.approval_revision !== selection.approval_revision)) {
    fail('CONTENT_MISMATCH', '字段读取没有返回指定固定版本及修订')
  }
  return value
}
export async function publishExactFields(session: MedicalSession, source: string, input: ExactPublication,
    pending: RequestMetadata, signal: AbortSignal): Promise<ExactReceipt> {
  verifyExactPublication(input); requireRequestMetadata(session, pending)
  if (pending.operation !== 'KN-EXACT-PUBLISH' || pending.target_id !== source || pending.scope_id !== input.project_id
      || pending.state !== 'PENDING' || pending.body_sha256 !== await requestBodyDigest(input)
      || canonicalizeEx(pending.context) !== canonicalizeEx(current(session))) fail('IDEMPOTENCY_CONFLICT', '字段核准需要相同原输入及原上下文')
  signal.throwIfAborted()
  const raw = await request.post('/api/source-versions/' + source + '/exact-fields', input, { medical: 'envelope', signal,
    headers: { ...readHeaders(session), 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const response = envelope(raw), value = validate<ExactReceipt>(exactReference + 'PublicationReceipt', response.data)
  await verifyExactReceipt(value, input, current(session).tenant_id, source)
  const context = current(session)
  if (raw.status !== 201 || response.resource_revision !== value.revision || value.manifest.actor_id !== session.user.user_id
      || BigInt(value.current_context.context_revision) !== BigInt(context.context_revision) + 1n
      || value.current_context.auth_epoch !== context.auth_epoch
      || BigInt(value.current_context.governance_epoch) !== BigInt(context.governance_epoch) + 1n) fail('CONTENT_MISMATCH', '字段核准缺少完整保存回执及相同主体上下文')
  return value
}
export async function createExactRetrieval(session: MedicalSession, input: ExactSearchInput, signal: AbortSignal): Promise<Operation> {
  validate(exactReference + 'SearchInput', input); exactLiteral(input.query)
  if (!retrievalAllowed(session, input.project_id)) fail('ACCESS_DENIED', '等值检索需要当前项目和来源许可')
  const pending = await prepareRequestMetadata(session, 'KN-14-EXACT', input.project_id, input.project_id, input.project_id, input, signal)
  const lookup = await lookupOperation(session, pending.operation, pending.key, signal)
  if (lookup.lookup_status === 'EXPIRED') { expireRequestMetadata(session, pending); fail('ACTION_STALE', '等值查询原请求已经过期') }
  if (lookup.operation !== null) {
    if (lookup.operation.status !== 'COMPLETED' || !lookup.operation.resource_id) fail('ACTION_STALE', '原等值查询尚未取得固定结果')
    return lookup.operation
  }
  if (canonicalizeEx(pending.context) !== canonicalizeEx(current(session))) fail('CONTEXT_CHANGED', '原查询上下文已经变化，请继续核查原操作')
  const raw = await request.post('/api/retrievals/exact', input, { medical: 'envelope', signal, headers: {
    ...readHeaders(session), 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const response = envelope(raw), value = validate<Operation>('urn:medical:shared#/$defs/Operation', response.data)
  if (raw.status !== 201 || response.resource_revision !== null || value.status !== 'COMPLETED' || !value.resource_id
      || value.status_url !== '/api/operations/' + value.operation_id) fail('CONTENT_MISMATCH', '等值查询原操作与固定结果不一致')
  return value
}
export async function readExactRetrieval(session: MedicalSession, retrievalId: string, page: number, pageSize: number,
    signal: AbortSignal): Promise<ExactPage> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', retrievalId)
  if (!Number.isSafeInteger(page) || page < 1 || !Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > 100) fail('SCHEMA_INVALID', '等值查询页码及容量无效')
  const raw = await request.get('/api/retrievals/exact/' + retrievalId, { medical: 'envelope', signal,
    params: { page: String(page), page_size: String(pageSize) }, headers: readHeaders(session) })
  const response = envelope(raw), value = validate<ExactPage>(exactReference + 'Page', response.data)
  if (raw.status !== 200 || response.resource_revision !== null) fail('CONTENT_MISMATCH', '等值查询页面缺少完整读取响应')
  await verifyExactPage(value, retrievalId, page, pageSize); signal.throwIfAborted(); return value
}

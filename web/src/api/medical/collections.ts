import request from '@/utils/request'
import collectionsText from '../../../../business/medical-persistence/src/main/resources/collections.schema.json?raw'
import templatesText from '../../../../business/medical-persistence/src/main/resources/templates.schema.json?raw'
import interfacesText from '../../../../backend-design/interfaces.schema.yaml?raw'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { canonicalTemplateValue, templateValueDigest } from './template-approval'
import { projectTextIssue } from './project-write'
import { readOperation } from './operations'

export interface CollectionSelection { source_version: string; parse_version: string }
export interface CollectionContent { schema_version: '1.0.0'; title: string; purpose: string; members: CollectionSelection[] }
export interface CollectionCreate { schema_version: '1.0.0'; owner_project_id: string | null; content: CollectionContent; reason: string }
export interface CollectionVersionInput {
  schema_version: '1.0.0'; expected_revision: string; base_version: Basis
  schema_ref: 'urn:medical:collections:1.0.0#/$defs/Content'; content: CollectionContent; reason: string
}
export type CollectionBody = CollectionCreate | CollectionVersionInput
export interface CollectionMember extends CollectionSelection { source_id: string; rights_revision: string; original_sha256: string; parse_sha256: string }
export interface CollectionManifest {
  schema_version: '1.0.0'; tenant_id: string; collection_id: string; collection_version: string; owner_project_id: string | null
  title: string; purpose: string; members: CollectionMember[]; base_version: Basis | null; revision: string; reason: string
  authority_grant_id: string; authority_revision: string; created_by: string; created_at: string
}
export interface CollectionView {
  schema_version: '1.0.0'; manifest: CollectionManifest; content_sha256: string
  schema_ref: 'urn:medical:collections:1.0.0#/$defs/Manifest'; schema_manifest_sha256: string; allowed_actions: string[]
}
export interface CollectionCreated { schema_version: '1.0.0'; operation_id: string; collection: CollectionView }
export interface CollectionPage {
  schema_version: '1.0.0'; collection_id: string; owner_project_id: string | null; title: string; current_version: string
  revision: string; items: CollectionView[]
  page_info: { page: number; page_size: number; total: string; watermark: string; filter_sha256: string }
}
export interface PendingCollection {
  actor: string; tenant_id: string; collection_id: string | null; owner_project_id: string | null
  operation: 'KN-11' | 'KN-12'; key: string; context: Context; expected_revision: string | null; base_version: Basis | null; body_sha256: string
}
const schema = 'urn:medical:collections:1.0.0#/$defs/'
const pendingSchema = 'urn:medical:collection-view:1.0.0'
const prefix = 'medical.collection.write:'

function current(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '资料集操作需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
function positive(value: string): void { if (revision(value) === '0') fail('SCHEMA_INVALID', '资料集修订必须为正整数') }
function id(value: string): void { validate('urn:medical:templates:1.0.0#/$defs/Id', value) }
export function collectionAllowed(session: MedicalSession | null, action: 'collection.read' | 'collection.write', project?: string | null): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => item.action === action && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === session.current_context?.tenant_id
    || project !== null && item.resource_kind === 'PROJECT' && item.project_id !== null && item.resource_id === item.project_id
      && (project === undefined || item.project_id === project)))
}
function storageKey(session: MedicalSession, collectionId: string | null): string {
  if (collectionId !== null) id(collectionId)
  return prefix + session.user.user_id + ':' + current(session).tenant_id + ':' + (collectionId ?? 'CREATE')
}
function checkPending(value: PendingCollection, session: MedicalSession, collectionId: string | null): void {
  validate(pendingSchema, value); validContext(value.context)
  if (value.expected_revision !== null) positive(value.expected_revision)
  if (value.actor !== session.user.user_id || value.tenant_id !== current(session).tenant_id
      || value.context.tenant_id !== value.tenant_id || value.collection_id !== collectionId) fail('CONTENT_MISMATCH', '资料集原请求与当前主体或归属不一致')
}
export function validateCollectionBody(value: unknown, collectionId: string | null): CollectionBody {
  if (collectionId !== null) id(collectionId)
  const body = validate<CollectionBody>(schema + (collectionId === null ? 'Create' : 'VersionInput'), value)
  if (projectTextIssue(body.reason, 2000) || projectTextIssue(body.content.title, 256)
      || new Set(body.content.members.map(member => member.source_version)).size !== body.content.members.length) {
    fail('SCHEMA_INVALID', '资料集名称、理由或者成员版本重复检查未通过')
  }
  if ('expected_revision' in body) {
    positive(body.expected_revision)
    if (body.expected_revision === '18446744073709551615') fail('REVISION_LIMIT', '资料集修订已经达到上限')
  }
  return body
}
async function textHash(value: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('')
}
async function schemaIdentity(): Promise<string> {
  return templateValueDigest({ schema_version: '1.0.0', 'collections.schema.json': await textHash(collectionsText),
    'templates.schema.json': await textHash(templatesText), 'backend-design/interfaces.schema.yaml': await textHash(interfacesText) })
}
async function checkView(view: CollectionView, tenant: string, collectionId: string, project: string | null): Promise<void> {
  validate(schema + 'View', view); const manifest = view.manifest
  positive(manifest.revision); positive(manifest.authority_revision)
  if (manifest.tenant_id !== tenant || manifest.collection_id !== collectionId || manifest.owner_project_id !== project
      || projectTextIssue(manifest.title, 256) || projectTextIssue(manifest.reason, 2000)
      || new Set(manifest.members.map(member => member.source_version)).size !== manifest.members.length
      || await templateValueDigest(manifest) !== view.content_sha256 || await schemaIdentity() !== view.schema_manifest_sha256) {
    fail('CONTENT_MISMATCH', '资料集固定清单、成员、摘要或Schema身份不一致')
  }
}
export async function readCollectionPage(session: MedicalSession, collectionId: string, page: number, pageSize: number,
    previous: CollectionPage | null, signal: AbortSignal): Promise<CollectionPage> {
  const context = current(session); id(collectionId)
  if (!collectionAllowed(session, 'collection.read')) fail('ACCESS_DENIED', '资料集读取需要当前许可')
  if (!Number.isInteger(page) || page < 1 || !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100
      || page > 1 && previous === null) fail('SCHEMA_INVALID', '资料集历史页码或固定水位无效')
  if (previous !== null && (previous.collection_id !== collectionId || previous.page_info.page_size !== pageSize)) {
    fail('CONTENT_MISMATCH', '资料集历史查询与原筛选不一致')
  }
  const params: Record<string, string | number> = { page, page_size: pageSize }
  if (previous !== null) params.dataset_watermark = previous.page_info.watermark
  const response = envelope(await request.get('/api/collections/' + collectionId + '/versions', { medical: 'envelope', signal, params,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const result = validate<CollectionPage>(schema + 'Page', response.data); const info = result.page_info
  positive(result.revision); positive(info.watermark); revision(info.total)
  const filter = { collection_id: collectionId, page_size: pageSize, subject_id: session.user.user_id, tenant_id: context.tenant_id, watermark: result.revision }
  const start = BigInt(page - 1) * BigInt(pageSize); const remaining = BigInt(info.total) - start
  const expected = remaining <= 0n ? 0 : Number(remaining > BigInt(pageSize) ? BigInt(pageSize) : remaining)
  if (response.resource_revision !== result.revision || result.collection_id !== collectionId || info.page !== page || info.page_size !== pageSize
      || info.total !== result.revision || info.watermark !== result.revision || info.filter_sha256 !== await templateValueDigest(filter)
      || result.items.length !== expected || new Set(result.items.map(item => item.manifest.collection_version)).size !== result.items.length
      || new Set(result.items.map(item => item.manifest.revision)).size !== result.items.length
      || previous !== null && (info.watermark !== previous.page_info.watermark || info.filter_sha256 !== previous.page_info.filter_sha256
        || result.current_version !== previous.current_version || result.owner_project_id !== previous.owner_project_id)) {
    fail('CONTENT_MISMATCH', '资料集历史分页、固定水位或筛选范围不一致')
  }
  for (const item of result.items) {
    await checkView(item, context.tenant_id, collectionId, result.owner_project_id)
    if (BigInt(item.manifest.revision) > BigInt(result.revision) || item.manifest.collection_version === result.current_version
        && (item.manifest.revision !== result.revision || item.manifest.title !== result.title)) fail('CONTENT_MISMATCH', '资料集历史与当前版本不一致')
  }
  return result
}
export function readPendingCollection(session: MedicalSession, collectionId: string | null): PendingCollection | null {
  const text = sessionStorage.getItem(storageKey(session, collectionId)); if (text === null) return null
  const saved = validate<PendingCollection>(pendingSchema, JSON.parse(text)); checkPending(saved, session, collectionId); return saved
}
export async function prepareCollection(session: MedicalSession, collectionId: string | null, project: string | null,
    body: CollectionBody): Promise<PendingCollection> {
  const context = current(session); validateCollectionBody(body, collectionId)
  if (project !== null) id(project)
  if (!collectionAllowed(session, 'collection.write', project) || !collectionAllowed(session, 'collection.read', project)) {
    fail('ACCESS_DENIED', '保存资料集需要当前归属的维护和读取许可')
  }
  if ('owner_project_id' in body && body.owner_project_id !== project) fail('CONTENT_MISMATCH', '资料集创建归属与明确选择不一致')
  const hash = await templateValueDigest(body); const saved = readPendingCollection(session, collectionId)
  if (saved) {
    if (saved.body_sha256 !== hash || saved.owner_project_id !== project || canonicalTemplateValue(saved.context) !== canonicalTemplateValue(context)) {
      fail('IDEMPOTENCY_CONFLICT', '资料集原请求需要完整原参数、身份和上下文核查')
    }
    return saved
  }
  const value: PendingCollection = { actor: session.user.user_id, tenant_id: context.tenant_id, collection_id: collectionId, owner_project_id: project,
    operation: collectionId === null ? 'KN-11' : 'KN-12', key: crypto.randomUUID(), context: { ...context },
    expected_revision: 'expected_revision' in body ? body.expected_revision : null, base_version: 'base_version' in body ? { ...body.base_version } : null, body_sha256: hash }
  checkPending(value, session, collectionId)
  // 浏览器只保存原请求身份、固定版本及摘要，成员和理由保持受控内存。
  sessionStorage.setItem(storageKey(session, collectionId), JSON.stringify(value)); return value
}
export async function submitCollection(session: MedicalSession, pending: PendingCollection, body: CollectionBody): Promise<CollectionCreated> {
  const context = current(session); checkPending(pending, session, pending.collection_id); validateCollectionBody(body, pending.collection_id)
  if (!collectionAllowed(session, 'collection.write', pending.owner_project_id) || !collectionAllowed(session, 'collection.read', pending.owner_project_id)) {
    fail('ACCESS_DENIED', '资料集原请求需要当前归属的维护和读取许可')
  }
  if (canonicalTemplateValue(context) !== canonicalTemplateValue(pending.context) || await templateValueDigest(body) !== pending.body_sha256
      || 'owner_project_id' in body && body.owner_project_id !== pending.owner_project_id
      || 'expected_revision' in body && (body.expected_revision !== pending.expected_revision || canonicalTemplateValue(body.base_version) !== canonicalTemplateValue(pending.base_version))) {
    fail('CONTENT_MISMATCH', '资料集原请求与当前上下文或者完整参数不一致')
  }
  const url = pending.collection_id === null ? '/api/collections' : '/api/collections/' + pending.collection_id + '/versions'
  const raw = await request.post(url, body, { medical: 'envelope', headers: { 'X-Schema-Version': '1.0.0',
    'X-Context-Revision': pending.context.context_revision, 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const response = envelope(raw); const result = validate<CollectionCreated>(schema + 'Created', response.data); const view = result.collection
  const manifest = view.manifest; const expectedRevision = pending.expected_revision === null ? '1' : (BigInt(pending.expected_revision) + 1n).toString()
  await checkView(view, pending.tenant_id, pending.collection_id ?? manifest.collection_id, pending.owner_project_id)
  if (raw.status !== 201 || response.resource_revision !== expectedRevision || manifest.revision !== expectedRevision || manifest.created_by !== pending.actor
      || manifest.title !== body.content.title || manifest.purpose !== body.content.purpose || manifest.reason !== body.reason
      || canonicalTemplateValue(manifest.base_version) !== canonicalTemplateValue(pending.base_version)
      || canonicalTemplateValue(manifest.members.map(member => ({ source_version: member.source_version, parse_version: member.parse_version }))) !== canonicalTemplateValue(body.content.members)) {
    fail('CONTENT_MISMATCH', '资料集保存回执与原成员、版本和主体不一致')
  }
  return result
}
export async function confirmCollection(session: MedicalSession, pending: PendingCollection, receipt: CollectionCreated, signal: AbortSignal): Promise<void> {
  checkPending(pending, session, pending.collection_id); validate(schema + 'Created', receipt)
  const operation = await readOperation(session, receipt.operation_id, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== receipt.collection.manifest.collection_version) fail('CONTENT_MISMATCH', '资料集原操作没有一致的完成结果')
  const saved = readPendingCollection(session, pending.collection_id)
  if (!saved || saved.key !== pending.key || saved.body_sha256 !== pending.body_sha256) fail('CONTENT_MISMATCH', '资料集原请求身份需要核查')
  sessionStorage.removeItem(storageKey(session, pending.collection_id))
}

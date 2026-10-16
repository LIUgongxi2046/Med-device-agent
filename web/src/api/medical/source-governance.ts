import request from '@/utils/request'
import { canonicalizeEx } from 'json-canonicalize'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import { projectActionAllowed } from './project-write'
import { byteDigest } from './source-uploads'
import { VerifiedByteAssembly } from './document-body'

export type SourceDecision = 'APPROVE' | 'RETURN' | 'DISABLE'
export interface SourceReview {
  parse_version: string; parse_status: 'QUEUED' | 'PROCESSING' | 'COMPLETE' | 'PARTIAL_FAILED' | 'FAILED'
  index_version: string | null; index_mode: string | null; index_status: string | null
  expected_count: string | null; verified_count: string | null; approval_revision: string | null
  approval_status: 'PENDING' | 'APPROVED' | 'DISABLED'; upload_status: 'PENDING' | 'UPLOADING' | 'RECEIVED' | 'FAILED'
  lifecycle: 'ACTIVE' | 'RECYCLED' | 'PURGE_PENDING' | 'PURGED'; gate_revision: string
  current_decision: SourceDecision | null; reviewed_manifest_sha256: string | null; decided_at: string | null
  review_manifest: Record<string, unknown>; review_manifest_sha256: string
}
export interface SourceGovernanceView {
  schema_version: '1.0.0'; source_id: string; source_version: string; owner_project_id: string | null
  ownership: 'ENTERPRISE' | 'PROJECT' | 'SESSION'; title: string; classification: string
  original_sha256: string; media_type: string; size_bytes: string; rights_revision: string
  rights_purpose: string; rights_status: string; lifecycle_state: SourceLifecycleStatus; lifecycle_revision: string
  last_lifecycle_decision: string | null; deletion_id: string | null; deletion_status: string | null
  versions: SourceVersionSummary[]
  reviews: SourceReview[]; bindings: SourceBindingView[]; allowed_actions: []
}
export interface SourceVersionSummary {
  source_version: string; version_no: string; publisher_version: string | null; original_sha256: string
  media_type: string; size_bytes: string; rights_revision: string; lifecycle: SourceLifecycleStatus
  created_at: string; selected: boolean
}
export type SourceApplicability = 'APPLICABLE' | 'NOT_APPLICABLE' | 'UNKNOWN'
export interface SourceBindingView {
  project_id: string; binding_revision: string; applicability: SourceApplicability; decision_manifest: Record<string, unknown>
}
export interface SourceBindingInput {
  schema_version: '1.0.0'; source_version: string; profile_revision: string; profile_sha256: string
  applicability: SourceApplicability; reason: string; expected_binding_revision: string | null; approval_attested: true
}
export interface SourceBindingReceipt {
  schema_version: '1.0.0'; operation_id: string; decision_id: string; project_id: string; source_version: string
  binding_revision: string; profile_revision: string; applicability: SourceApplicability
  actor_id: string; decided_at: string; current_context: Context
}
export interface SourceCatalogueItem {
  source_id: string; source_version: string; owner_project_id: string | null; ownership: 'ENTERPRISE' | 'PROJECT' | 'SESSION'
  title: string; classification: string; media_type: string; size_bytes: string; rights_revision: string; rights_status: string
  folder_id: string | null; category: string | null; labels: string[]; organization_revision: string | null; bookmarked: boolean
  processing_status: string | null; approval_status: string | null; lifecycle: string | null; created_at: string
}
export interface SourceCataloguePage {
  schema_version: '1.0.0'; items: SourceCatalogueItem[]; total: string; limit: number; next_after: string | null
}
export interface SourceCatalogueQuery {
  limit: number; after?: string; project_id?: string; ownership?: SourceCatalogueItem['ownership']
  approval_status?: 'PENDING' | 'APPROVED' | 'DISABLED'; lifecycle?: 'ACTIVE' | 'RECYCLED' | 'PURGE_PENDING' | 'PURGED'
  bookmarked?: boolean; category?: string; tag?: string; folder_id?: string; collection_version?: string
}
export type SourceFolderOwnership = 'ENTERPRISE' | 'PROJECT'
export interface SourceFolder {
  folder_id: string; parent_id: string | null; owner_project_id: string | null; ownership: SourceFolderOwnership
  category: string; name: string; revision: string; created_at: string; updated_at: string
}
export interface SourceFolderList {
  schema_version: '1.0.0'; owner_project_id: string | null; items: SourceFolder[]
}
export interface SourceFolderCreateInput {
  schema_version: '1.0.0'; ownership: SourceFolderOwnership; owner_project_id: string | null
  parent_id: string | null; category: string; name: string
}
export interface SourceFolderDecisionInput {
  schema_version: '1.0.0'; decision: 'RENAME' | 'MOVE' | 'DELETE'; name: string | null
  target_parent_id: string | null; expected_revision: string
}
export interface SourceFolderReceipt {
  schema_version: '1.0.0'; operation_id: string; decision: 'CREATE' | SourceFolderDecisionInput['decision']; folder: SourceFolder
}
export interface SourceOrganizationItemInput {
  item_id: string; source_version: string; decision: 'MOVE' | 'LABEL' | 'BOOKMARK' | 'UNBOOKMARK'
  folder_id: string | null; labels: string[] | null; expected_revision: string | null
}
export interface SourceOrganizationInput { schema_version: '1.0.0'; items: SourceOrganizationItemInput[] }
export interface SourceOrganizationReceiptItem {
  item_id: string; operation_id: string; status: 'COMPLETED'; source_version: string
  decision: SourceOrganizationItemInput['decision']; folder_id: string | null; labels: string[]
  organization_revision: string | null; bookmarked: boolean
}
export interface SourceOrganizationReceipt { schema_version: '1.0.0'; items: SourceOrganizationReceiptItem[] }
export interface SourceDecisionInput {
  schema_version: '1.0.0'; parse_version: string; index_version: string; rights_revision: string
  binding_revision: string | null; reviewed_manifest_sha256: string; decision: SourceDecision
  reason: string; expected_revision: string
}
export interface SourceDecisionReceipt {
  schema_version: '1.0.0'; operation_id: string; decision_id: string; source_version: string
  parse_version: string; index_version: string; decision: SourceDecision
  approval_status: 'PENDING' | 'APPROVED' | 'DISABLED'; approval_revision: string
  gate_revision: string; actor_id: string; decided_at: string; current_context: Context
}
export type SourceProcessingStage = 'PARSE_ONLY' | 'PARSE_AND_INDEX'
export interface SourceProcessingInput {
  schema_version: '1.0.0'; project_id: string; config_revision: string; stage: SourceProcessingStage; reason: string
}
export interface SourceProcessingReceipt {
  schema_version: '1.0.0'; operation_id: string; attempt_id: string; run_id: string; project_id: string
  source_version: string; parse_version: string; config_revision: string; job_type: 'PARSE'
  stage: SourceProcessingStage; status: 'QUEUED'; actor_id: string; queued_at: string; current_context: Context
}
export interface SourceProcessingQueueItem {
  attempt_id: string; project_id: string; source_id: string; source_version: string; title: string
  parse_version: string | null; job_type: string; config_revision: string; status: string; parse_status: string | null
  failure_manifest: Record<string, unknown>; request_manifest: Record<string, unknown>; run_id: string
  created_by: string; created_at: string; revision: string; updated_at: string
}
export interface SourceProcessingQueuePage {
  schema_version: '1.0.0'; items: SourceProcessingQueueItem[]; total: string; limit: number; next_after: string | null
}
export interface SourceProcessingQueueQuery {
  limit: number; after?: string; project_id?: string
  status?: 'QUEUED' | 'PROCESSING' | 'COMPLETE' | 'PARTIAL_FAILED' | 'FAILED'
}
export interface ArtifactCatalogueItem {
  artifact_id: string; project_id: string | null; run_id: string | null; document_revision: string | null
  artifact_kind: string; sha256: string; size_bytes: string; media_type: string; created_at: string
}
export interface ArtifactCataloguePage {
  schema_version: '1.0.0'; items: ArtifactCatalogueItem[]; total: string; limit: number; next_after: string | null
}
export interface ArtifactCatalogueQuery {
  limit: number; after?: string; project_id?: string; artifact_kind?: string
}
export interface ArtifactSubmissionEvidence { source_version: string; rights_revision: string }
export interface ArtifactSubmissionRights {
  purpose: string; license: string; allowed_actions: ('READ' | 'STORE' | 'ENTERPRISE_USE' | 'DISTRIBUTE' | 'EXTERNAL_PROCESS')[]
  providers: string[]; regions: string[]; valid_from: string | null; valid_to: string | null
}
export interface ArtifactSubmissionInput {
  schema_version: '1.0.0'; ownership: 'ENTERPRISE' | 'PROJECT'; target_project_id: string | null
  category: string; title: string; original_evidence: ArtifactSubmissionEvidence[]; rights: ArtifactSubmissionRights
  maintainer_id: string; applicable_project_ids: string[]; reviewer_id: string
}
export interface ArtifactSubmissionReceipt {
  schema_version: '1.0.0'; operation_id: string; submission_id: string; artifact_id: string; source_id: string
  source_version: string; parse_version: string; rights_revision: string; sharing_decision: string
  status: 'PENDING'; approval_status: 'PENDING'; lifecycle: 'ACTIVE'; actor_id: string; created_at: string
  current_context: Context
}
export type SourceLifecycleDecision = 'DISABLE' | 'RECYCLE' | 'RESTORE' | 'PURGE'
export type SourceLifecycleStatus = 'ACTIVE' | 'DISABLED' | 'RECYCLED' | 'PURGE_PENDING' | 'PURGED'
export interface SourceLifecycleInput {
  schema_version: '1.0.0'; decision: SourceLifecycleDecision; reason: string
  policy_revision: string | null; expected_revision: string
}
export interface SourceLifecycleReceipt {
  schema_version: '1.0.0'; operation_id: string; decision_id: string; source_version: string
  decision: SourceLifecycleDecision; previous_status: SourceLifecycleStatus; current_status: SourceLifecycleStatus
  lifecycle_revision: string; policy_revision: string | null; deletion_id: string | null
  deletion_status: string | null; deletion_item_count: string; actor_id: string; decided_at: string
  current_context: Context
}
export interface SourceDeletionReceiptInput {
  schema_version: '1.0.0'; deletion_id: string; item_id: string
  store_kind: 'ORIGINAL' | 'PARSED' | 'FULLTEXT' | 'VECTOR' | 'CACHE' | 'DIAGNOSTIC'
  target_sha256: string; expected_revision: string; expected_lifecycle_revision: string
  receipt_ref: { deleted_at: string; query_confirmed_at: string; backup_confirmed_at: string; evidence_sha256: string }
}
export interface SourceDeletionReceipt {
  schema_version: '1.0.0'; operation_id: string; deletion_id: string; item_id: string
  store_kind: SourceDeletionReceiptInput['store_kind']; item_status: 'CONFIRMED'; item_revision: string
  job_status: 'RUNNING' | 'COMPLETED'; job_revision: string; source_version: string
  lifecycle_status: 'PURGE_PENDING' | 'PURGED'; lifecycle_revision: string; current_context: Context
}
export interface SourceDeletionItem {
  item_id: string; store_kind: SourceDeletionReceiptInput['store_kind']; target_ref: Record<string, unknown>
  target_sha256: string; status: 'QUEUED' | 'RUNNING' | 'CONFIRMED'; receipt_ref: Record<string, unknown> | null
  backup_expiry: string | null; revision: string
}
export interface SourceDeletionJob {
  schema_version: '1.0.0'; deletion_id: string; source_version: string; policy_revision: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED'; revision: string; updated_at: string
  lifecycle_status: 'PURGE_PENDING' | 'PURGED'; lifecycle_revision: string; items: SourceDeletionItem[]
}
export type SourceUsageKind = 'PROJECT_BINDING' | 'COLLECTION_MEMBER' | 'SCOPE_SNAPSHOT'
  | 'DOCUMENT_EVIDENCE' | 'LINEAGE_DEPENDENCY' | 'REFERENCE_HOLD'
export interface SourceUsageItem {
  usage_key: string; usage_kind: SourceUsageKind; relation_kind: string; project_id: string | null
  resource_kind: 'PROJECT' | 'COLLECTION' | 'DOCUMENT' | 'VERSION'; resource_id: string; version_id: string
  title: string | null; status: string; impact_status: 'BLOCKING' | 'OBSERVED' | 'HISTORICAL' | 'HELD' | 'EXPIRED'
  created_at: string | null
}
export interface SourceUsagePage {
  schema_version: '1.0.0'; source_version: string; items: SourceUsageItem[]; total: string; limit: number
  next_after: string | null; dataset_watermark: string
}
export interface SourceUsageQuery {
  limit: number; after?: string; project_id?: string; usage_kind?: SourceUsageKind; dataset_watermark?: string
}
export interface ParsedContentItem {
  chunk_id: string; ordinal: string; node_id: string; node_kind: string; locator: Record<string, unknown>
  locator_sha256: string; content_ref: Record<string, unknown>; node_hash: string; quote_hash: string; raw_text: string
}
export interface OriginalSourceContent {
  schema_version: '1.0.0'; source_id: string; source_version: string; parse_version: string
  representation: 'ORIGINAL'; purpose: string; media_type: string; original_sha256: string; content_sha256: string
  start_byte: string; end_byte: string; size_bytes: string; encoding: 'BASE64'; content: string
  chunk_sha256: string; full_sha256: string; next_start_byte: string | null; read_at: string
}
export interface ParsedSourceContent {
  schema_version: '1.0.0'; source_id: string; source_version: string; parse_version: string
  representation: 'PARSED'; purpose: string; media_type: string; original_sha256: string; content_sha256: string
  items: ParsedContentItem[]; next_after_ordinal: string | null; read_at: string
}
export type SourceContent = OriginalSourceContent | ParsedSourceContent
export type SourceContentQuery = {
  parse_version: string; representation: 'ORIGINAL'; purpose: string; start_byte?: string; end_byte?: string
} | {
  parse_version: string; representation: 'PARSED'; purpose: string; chunk_id?: string; locator_sha256?: string
  after_ordinal?: string; limit?: number
}

const schema = 'urn:medical:source-governance:1.0.0#/$defs/'
function current(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '资料版本读取需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
export function sourceApprovalAllowed(session: MedicalSession | null, sourceId?: string, projectId?: string | null): boolean {
  const context = session?.current_context
  return Boolean(context && session.action_summary.some(item => item.action === 'source.approve' && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === context.tenant_id
    || item.resource_kind === 'PROJECT' && projectId !== undefined && projectId !== null
      && item.project_id === projectId && item.resource_id === projectId
    || item.resource_kind === 'SOURCE' && (item.resource_id === null || sourceId === undefined || item.resource_id === sourceId)
  )))
}
export function sourceProcessingAllowed(session: MedicalSession | null, sourceId?: string, projectId?: string | null): boolean {
  const context = session?.current_context
  return Boolean(context && session.action_summary.some(item => item.action === 'source.write' && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === context.tenant_id
    || item.resource_kind === 'PROJECT' && projectId !== undefined && projectId !== null
      && item.project_id === projectId && item.resource_id === projectId
    || item.resource_kind === 'SOURCE' && (item.resource_id === null || sourceId === undefined || item.resource_id === sourceId)
  )))
}
export const sourceLifecycleAllowed = sourceProcessingAllowed
export function sourcePurgeAllowed(session: MedicalSession | null, sourceId?: string, projectId?: string | null): boolean {
  const context = session?.current_context
  return Boolean(context && session.action_summary.some(item => item.action === 'source.purge' && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === context.tenant_id
    || item.resource_kind === 'PROJECT' && projectId !== undefined && projectId !== null
      && item.project_id === projectId && item.resource_id === projectId
    || item.resource_kind === 'SOURCE' && (item.resource_id === null || sourceId === undefined || item.resource_id === sourceId)
  )))
}
export function sourceBindingAllowed(session: MedicalSession | null, projectId: string): boolean {
  return Boolean(session?.current_context && projectActionAllowed(session, 'project.read', projectId)
    && projectActionAllowed(session, 'profile.confirm', projectId))
}
export async function readSourceGovernance(session: MedicalSession, sourceVersion: string, signal: AbortSignal): Promise<SourceGovernanceView> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', sourceVersion)
  const value = envelope(await request.get('/api/source-versions/' + sourceVersion, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const result = validate<SourceGovernanceView>(schema + 'View', value.data)
  if (value.resource_revision !== null || result.source_version !== sourceVersion) fail('CONTENT_MISMATCH', '资料版本详情与请求身份不一致')
  revision(result.lifecycle_revision)
  if (result.versions.filter(item => item.selected).length !== 1
      || result.versions.find(item => item.selected)?.source_version !== sourceVersion
      || new Set(result.versions.map(item => item.source_version)).size !== result.versions.length) {
    fail('CONTENT_MISMATCH', '资料历史版本与固定版本不一致')
  }
  for (const item of result.versions) revision(item.version_no)
  for (const item of result.reviews) revision(item.gate_revision)
  return result
}
export function sourceContentBytes(value: OriginalSourceContent): Uint8Array<ArrayBuffer> {
  const binary = atob(value.content); const bytes = new Uint8Array(new ArrayBuffer(binary.length))
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index)
  return bytes
}
export async function readSourceContent(session: MedicalSession, sourceVersion: string, query: SourceContentQuery,
    signal: AbortSignal): Promise<SourceContent> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', sourceVersion)
  validate('urn:medical:templates:1.0.0#/$defs/Id', query.parse_version)
  if (!/^[A-Z][A-Z0-9_]{0,127}$/.test(query.purpose)) fail('SCHEMA_INVALID', '资料读取用途无效')
  if (query.representation === 'ORIGINAL') {
    if ((query.start_byte === undefined) !== (query.end_byte === undefined)) fail('SCHEMA_INVALID', '原件读取范围不完整')
    if (query.start_byte !== undefined) revision(query.start_byte)
    if (query.end_byte !== undefined) revision(query.end_byte)
  } else {
    if ((query.chunk_id === undefined) !== (query.locator_sha256 === undefined)
        || query.chunk_id !== undefined && query.after_ordinal !== undefined) fail('SCHEMA_INVALID', '解析内容定位条件无效')
    if (query.chunk_id !== undefined) validate('urn:medical:templates:1.0.0#/$defs/Id', query.chunk_id)
    if (query.after_ordinal !== undefined) revision(query.after_ordinal)
    if (query.limit !== undefined && (!Number.isInteger(query.limit) || query.limit < 1 || query.limit > 100)) {
      fail('SCHEMA_INVALID', '解析内容分页范围无效')
    }
  }
  const value = envelope(await request.get('/api/source-versions/' + sourceVersion + '/content', { medical: 'envelope', signal,
    params: query, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  if (value.resource_revision !== null) fail('CONTENT_MISMATCH', '资料内容响应包含意外资源版本')
  if (query.representation === 'ORIGINAL') {
    const result = validate<OriginalSourceContent>(schema + 'OriginalContent', value.data)
    const bytes = sourceContentBytes(result)
    if (result.source_version !== sourceVersion || result.parse_version !== query.parse_version || result.purpose !== query.purpose
        || query.start_byte !== undefined && (result.start_byte !== query.start_byte || result.end_byte !== query.end_byte)
        || BigInt(result.end_byte) > BigInt(result.size_bytes) || BigInt(result.end_byte) <= BigInt(result.start_byte)
        || (result.next_start_byte === null) !== (result.end_byte === result.size_bytes)
        || BigInt(result.end_byte) - BigInt(result.start_byte) !== BigInt(bytes.length)
        || result.next_start_byte !== null && result.next_start_byte !== result.end_byte
        || result.full_sha256 !== result.original_sha256 || await byteDigest(bytes) !== result.chunk_sha256) {
      fail('CONTENT_MISMATCH', '原件分段与固定读取范围不一致')
    }
    return result
  }
  const result = validate<ParsedSourceContent>(schema + 'ParsedContent', value.data)
  if (result.source_version !== sourceVersion || result.parse_version !== query.parse_version || result.purpose !== query.purpose
      || result.items.length > (query.limit ?? 50)
      || result.next_after_ordinal !== null && result.next_after_ordinal !== result.items.at(-1)?.ordinal
      || new Set(result.items.map(item => item.chunk_id)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '解析内容与固定读取范围不一致')
  }
  for (const item of result.items) {
    revision(item.ordinal)
    validate('https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json#/$defs/locator', item.locator)
    if (query.chunk_id !== undefined && (result.items.length !== 1 || item.chunk_id !== query.chunk_id
        || item.locator_sha256 !== query.locator_sha256)
        || query.after_ordinal !== undefined && BigInt(item.ordinal) <= BigInt(query.after_ordinal)) {
      fail('CONTENT_MISMATCH', '解析内容没有使用指定位置或分页范围')
    }
    if (await byteDigest(new TextEncoder().encode(item.raw_text)) !== item.quote_hash
        || await byteDigest(new TextEncoder().encode(canonicalizeEx(item.locator, { strictUndefined: true }))) !== item.locator_sha256) {
      fail('CONTENT_MISMATCH', '解析内容与固定定位摘要不一致')
    }
  }
  for (let index = 1; index < result.items.length; index++) {
    if (BigInt(result.items[index]!.ordinal) <= BigInt(result.items[index - 1]!.ordinal)) {
      fail('CONTENT_MISMATCH', '解析内容顺序没有前进')
    }
  }
  return result
}

export async function readCompleteSourceOriginal(session: MedicalSession, view: SourceGovernanceView,
    review: SourceReview, signal: AbortSignal, progress: (received: string, total: string) => void): Promise<Blob> {
  const assembly = new VerifiedByteAssembly(view.size_bytes, view.original_sha256)
  const size = BigInt(view.size_bytes); const pageBytes = 262144n
  let start = 0n
  try {
    while (start < size) {
      signal.throwIfAborted()
      const end = start + pageBytes < size ? start + pageBytes : size
      const part = await readSourceContent(session, view.source_version, { representation: 'ORIGINAL',
        parse_version: review.parse_version, purpose: view.rights_purpose,
        start_byte: start.toString(), end_byte: end.toString() }, signal)
      signal.throwIfAborted()
      if (part.representation !== 'ORIGINAL' || part.source_id !== view.source_id
          || part.size_bytes !== view.size_bytes || part.media_type !== view.media_type
          || part.original_sha256 !== view.original_sha256) fail('CONTENT_MISMATCH', '原件身份或字节数在读取期间发生变化')
      await assembly.append(part.start_byte, sourceContentBytes(part), part.chunk_sha256)
      signal.throwIfAborted(); start = end; progress(assembly.receivedBytes, view.size_bytes)
    }
    const blob = await assembly.complete(view.media_type)
    signal.throwIfAborted()
    return blob
  } finally { assembly.dispose() }
}
export async function readSourceCatalogue(session: MedicalSession, query: SourceCatalogueQuery,
    signal: AbortSignal): Promise<SourceCataloguePage> {
  if (!Number.isInteger(query.limit) || query.limit < 1 || query.limit > 100) fail('SCHEMA_INVALID', '资料列表分页范围无效')
  for (const id of [query.after, query.project_id, query.folder_id, query.collection_version]) {
    if (id !== undefined) validate('urn:medical:templates:1.0.0#/$defs/Id', id)
  }
  const value = envelope(await request.get('/api/source-versions', { medical: 'envelope', signal,
    params: query, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const result = validate<SourceCataloguePage>(schema + 'Listing', value.data)
  revision(result.total)
  if (value.resource_revision !== null || result.limit !== query.limit || result.items.length > query.limit
      || result.next_after !== null && result.next_after !== result.items.at(-1)?.source_version
      || new Set(result.items.map(item => item.source_version)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '资料列表与查询条件不一致')
  }
  for (const item of result.items) {
    if (query.lifecycle !== undefined && item.lifecycle !== query.lifecycle
        || query.bookmarked !== undefined && item.bookmarked !== query.bookmarked
        || query.approval_status !== undefined && item.approval_status !== query.approval_status) {
      fail('CONTENT_MISMATCH', '资料条目与查询条件不一致')
    }
  }
  return result
}
export async function readSourceProcessingQueue(session: MedicalSession, query: SourceProcessingQueueQuery,
    signal: AbortSignal): Promise<SourceProcessingQueuePage> {
  if (!Number.isInteger(query.limit) || query.limit < 1 || query.limit > 100) fail('SCHEMA_INVALID', '处理队列分页范围无效')
  for (const id of [query.after, query.project_id]) if (id !== undefined) validate('urn:medical:templates:1.0.0#/$defs/Id', id)
  const value = envelope(await request.get('/api/processing-attempts', { medical: 'envelope', signal,
    params: query, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const result = validate<SourceProcessingQueuePage>(schema + 'ProcessingQueuePage', value.data)
  revision(result.total)
  if (value.resource_revision !== null || result.limit !== query.limit || result.items.length > query.limit
      || result.next_after !== null && result.next_after !== result.items.at(-1)?.attempt_id
      || new Set(result.items.map(item => item.attempt_id)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '处理队列与查询条件不一致')
  }
  for (const item of result.items) {
    revision(item.revision)
    if (query.project_id !== undefined && item.project_id !== query.project_id
        || query.status !== undefined && item.status !== query.status) {
      fail('CONTENT_MISMATCH', '处理记录与查询条件不一致')
    }
  }
  return result
}
export async function readArtifactCatalogue(session: MedicalSession, query: ArtifactCatalogueQuery,
    signal: AbortSignal): Promise<ArtifactCataloguePage> {
  if (!Number.isInteger(query.limit) || query.limit < 1 || query.limit > 100) fail('SCHEMA_INVALID', '成果列表分页范围无效')
  for (const id of [query.after, query.project_id]) if (id !== undefined) validate('urn:medical:templates:1.0.0#/$defs/Id', id)
  if (query.artifact_kind !== undefined && !/^[A-Z][A-Z0-9_]{0,31}$/.test(query.artifact_kind)) {
    fail('SCHEMA_INVALID', '成果类型范围无效')
  }
  const value = envelope(await request.get('/api/artifacts', { medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const result = validate<ArtifactCataloguePage>(schema + 'ArtifactCataloguePage', value.data)
  revision(result.total)
  if (value.resource_revision !== null || result.limit !== query.limit || result.items.length > query.limit
      || result.next_after !== null && result.next_after !== result.items.at(-1)?.artifact_id
      || new Set(result.items.map(item => item.artifact_id)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '成果列表与查询条件不一致')
  }
  for (const item of result.items) {
    if (query.project_id !== undefined && item.project_id !== query.project_id
        || query.artifact_kind !== undefined && item.artifact_kind !== query.artifact_kind) {
      fail('CONTENT_MISMATCH', '成果条目与查询条件不一致')
    }
  }
  return result
}
export function artifactSubmissionAllowed(session: MedicalSession | null, artifactId: string,
    artifactProjectId: string | null, targetProjectId: string | null): boolean {
  const context = session?.current_context
  if (!session || !context) return false
  const permits = (action: string): boolean => session.action_summary.some(item => item.action === action && (
    item.resource_kind === 'ARTIFACT' && item.resource_id === artifactId && item.project_id === artifactProjectId
    || item.resource_kind === 'PROJECT' && artifactProjectId !== null && item.resource_id === artifactProjectId
      && item.project_id === artifactProjectId
    || item.resource_kind === 'ENTERPRISE' && item.resource_id === context.tenant_id && item.project_id === null
  ))
  return permits('artifact.read') && permits('artifact.submit') && permits('source.share')
    && projectActionAllowed(session, 'source.write', targetProjectId)
}
export async function submitArtifactAsSource(session: MedicalSession, artifactId: string,
    body: ArtifactSubmissionInput): Promise<ArtifactSubmissionReceipt> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', artifactId)
  validate<ArtifactSubmissionInput>(schema + 'ArtifactSubmissionInput', body)
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const response = await request.post('/api/artifacts/' + artifactId + '/source-submissions', body, {
    medical: 'envelope', headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
      'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token }
  })
  if (response.status !== 201) fail('CONTENT_MISMATCH', '成果转为资料没有取得建立响应')
  const value = envelope(response)
  const result = validate<ArtifactSubmissionReceipt>(schema + 'ArtifactSubmissionReceipt', value.data)
  validContext(result.current_context)
  const contextAdvanced = result.current_context.context_revision === (BigInt(context.context_revision) + 1n).toString()
    && result.current_context.governance_epoch === (BigInt(context.governance_epoch) + 1n).toString()
  const contextReused = result.current_context.context_revision === context.context_revision
    && result.current_context.governance_epoch === context.governance_epoch
  if (value.resource_revision !== '1' || result.artifact_id !== artifactId || result.status !== 'PENDING'
      || result.approval_status !== 'PENDING' || result.lifecycle !== 'ACTIVE' || result.actor_id !== session.user.user_id
      || result.current_context.tenant_id !== context.tenant_id
      || !contextAdvanced && !contextReused
      || result.current_context.auth_epoch !== context.auth_epoch) {
    fail('CONTENT_MISMATCH', '成果转为资料的回执与输入不一致')
  }
  return result
}
export async function readSourceFolders(session: MedicalSession, projectId: string | null,
    signal: AbortSignal): Promise<SourceFolderList> {
  if (projectId !== null) validate('urn:medical:templates:1.0.0#/$defs/Id', projectId)
  const value = envelope(await request.get('/api/source-folders', { medical: 'envelope', signal,
    params: projectId === null ? {} : { project_id: projectId },
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const result = validate<SourceFolderList>(schema + 'FolderList', value.data)
  if (value.resource_revision !== null || result.owner_project_id !== projectId
      || new Set(result.items.map(item => item.folder_id)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '资料文件夹列表与读取范围不一致')
  }
  for (const item of result.items) revision(item.revision)
  return result
}
export async function createSourceFolder(session: MedicalSession,
    body: SourceFolderCreateInput): Promise<SourceFolderReceipt> {
  validate<SourceFolderCreateInput>(schema + 'FolderCreateInput', body)
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const value = envelope(await request.post('/api/source-folders', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
    'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token
  } }))
  const result = validate<SourceFolderReceipt>(schema + 'FolderReceipt', value.data)
  if (result.decision !== 'CREATE' || result.folder.owner_project_id !== body.owner_project_id
      || result.folder.parent_id !== body.parent_id || result.folder.category !== body.category
      || result.folder.name !== body.name || value.resource_revision !== result.folder.revision) {
    fail('CONTENT_MISMATCH', '资料文件夹创建回执与输入不一致')
  }
  return result
}
export async function decideSourceFolder(session: MedicalSession, folderId: string,
    body: SourceFolderDecisionInput): Promise<SourceFolderReceipt> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', folderId)
  validate<SourceFolderDecisionInput>(schema + 'FolderDecisionInput', body)
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const value = envelope(await request.post('/api/source-folders/' + folderId + '/decisions', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
    'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token
  } }))
  const result = validate<SourceFolderReceipt>(schema + 'FolderReceipt', value.data)
  if (result.decision !== body.decision || result.folder.folder_id !== folderId
      || value.resource_revision !== result.folder.revision) fail('CONTENT_MISMATCH', '资料文件夹操作回执与输入不一致')
  return result
}
export async function organizeSources(session: MedicalSession,
    body: SourceOrganizationInput): Promise<SourceOrganizationReceipt> {
  validate<SourceOrganizationInput>(schema + 'OrganizationInput', body)
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const value = envelope(await request.post('/api/source-organization-decisions', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
    'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token
  } }))
  const result = validate<SourceOrganizationReceipt>(schema + 'OrganizationReceipt', value.data)
  if (value.resource_revision !== null || result.items.length !== body.items.length) {
    fail('CONTENT_MISMATCH', '资料组织回执数量与输入不一致')
  }
  for (let index = 0; index < result.items.length; index++) {
    const receipt = result.items[index]!; const input = body.items[index]!
    if (receipt.item_id !== input.item_id || receipt.source_version !== input.source_version
        || receipt.decision !== input.decision) fail('CONTENT_MISMATCH', '资料组织回执与输入不一致')
  }
  return result
}
export async function readSourceUsages(session: MedicalSession, sourceVersion: string, query: SourceUsageQuery,
    signal: AbortSignal): Promise<SourceUsagePage> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', sourceVersion)
  if (!Number.isInteger(query.limit) || query.limit < 1 || query.limit > 100
      || query.after !== undefined && !/^[0-9a-f:-]{1,256}$/.test(query.after)
      || query.after !== undefined && query.dataset_watermark === undefined) fail('SCHEMA_INVALID', '资料引用分页条件无效')
  if (query.project_id !== undefined) validate('urn:medical:templates:1.0.0#/$defs/Id', query.project_id)
  if (query.dataset_watermark !== undefined) revision(query.dataset_watermark)
  const value = envelope(await request.get('/api/source-versions/' + sourceVersion + '/usages', { medical: 'envelope', signal,
    params: query, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const result = validate<SourceUsagePage>(schema + 'UsagePage', value.data)
  revision(result.total); revision(result.dataset_watermark)
  if (value.resource_revision !== null || result.source_version !== sourceVersion || result.limit !== query.limit
      || result.items.length > query.limit || query.dataset_watermark !== undefined && result.dataset_watermark !== query.dataset_watermark
      || result.next_after !== null && result.next_after !== result.items.at(-1)?.usage_key
      || new Set(result.items.map(item => item.usage_key)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '资料引用结果与固定查询不一致')
  }
  let previous = query.after
  for (const item of result.items) {
    if (previous !== undefined && item.usage_key <= previous
        || query.project_id !== undefined && item.project_id !== query.project_id
        || query.usage_kind !== undefined && item.usage_kind !== query.usage_kind) {
      fail('CONTENT_MISMATCH', '资料引用顺序或过滤条件不一致')
    }
    previous = item.usage_key
  }
  return result
}
export async function decideSource(session: MedicalSession, sourceVersion: string, body: SourceDecisionInput): Promise<SourceDecisionReceipt> {
  validate<SourceDecisionInput>(schema + 'DecisionInput', body)
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const value = envelope(await request.post('/api/source-versions/' + sourceVersion + '/approval-decisions', body, { medical: 'envelope',
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
      'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token } }))
  const result = validate<SourceDecisionReceipt>(schema + 'Receipt', value.data); validContext(result.current_context)
  const expectedStatus = { APPROVE: 'APPROVED', RETURN: 'PENDING', DISABLE: 'DISABLED' } as const
  if (value.resource_revision !== result.gate_revision || result.source_version !== sourceVersion
      || result.parse_version !== body.parse_version || result.index_version !== body.index_version
      || result.decision !== body.decision || result.approval_status !== expectedStatus[body.decision]
      || result.actor_id !== session.user.user_id || result.gate_revision !== (BigInt(body.expected_revision) + 1n).toString()
      || result.current_context.tenant_id !== context.tenant_id
      || result.current_context.context_revision !== (BigInt(context.context_revision) + 1n).toString()
      || result.current_context.governance_epoch !== (BigInt(context.governance_epoch) + 1n).toString()
      || result.current_context.auth_epoch !== context.auth_epoch) fail('CONTENT_MISMATCH', '资料核准回执与固定审核输入不一致')
  return result
}
export async function requestSourceProcessing(session: MedicalSession, sourceId: string, sourceVersion: string,
    body: SourceProcessingInput): Promise<SourceProcessingReceipt> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', sourceId)
  validate('urn:medical:templates:1.0.0#/$defs/Id', sourceVersion)
  validate<SourceProcessingInput>(schema + 'ProcessingInput', body)
  if (!sourceProcessingAllowed(session, sourceId, body.project_id)) fail('ACCESS_DENIED', '资料重新处理需要来源写入许可')
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const value = envelope(await request.post('/api/source-versions/' + sourceVersion + '/processing-attempts', body, {
    medical: 'envelope', headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
      'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token }
  }))
  const result = validate<SourceProcessingReceipt>(schema + 'ProcessingReceipt', value.data); validContext(result.current_context)
  if (value.resource_revision !== '1' || result.source_version !== sourceVersion || result.project_id !== body.project_id
      || result.config_revision !== body.config_revision || result.stage !== body.stage || result.job_type !== 'PARSE'
      || result.status !== 'QUEUED' || result.actor_id !== session.user.user_id
      || result.current_context.tenant_id !== context.tenant_id
      || result.current_context.context_revision !== (BigInt(context.context_revision) + 1n).toString()
      || result.current_context.governance_epoch !== (BigInt(context.governance_epoch) + 1n).toString()
      || result.current_context.auth_epoch !== context.auth_epoch) fail('CONTENT_MISMATCH', '资料处理回执与固定输入不一致')
  return result
}
export async function decideSourceLifecycle(session: MedicalSession, sourceId: string, sourceVersion: string,
    body: SourceLifecycleInput): Promise<SourceLifecycleReceipt> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', sourceId)
  validate('urn:medical:templates:1.0.0#/$defs/Id', sourceVersion)
  validate<SourceLifecycleInput>(schema + 'LifecycleInput', body)
  if (!sourceLifecycleAllowed(session, sourceId)
      || body.decision === 'PURGE' && !sourcePurgeAllowed(session, sourceId)) fail('ACCESS_DENIED', '资料生命周期决定需要对应维护许可')
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const value = envelope(await request.post('/api/source-versions/' + sourceVersion + '/lifecycle-decisions', body, {
    medical: 'envelope', headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
      'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token }
  }))
  const result = validate<SourceLifecycleReceipt>(schema + 'LifecycleReceipt', value.data); validContext(result.current_context)
  const expectedStatus: Record<SourceLifecycleDecision, SourceLifecycleStatus> = {
    DISABLE: 'DISABLED', RECYCLE: 'RECYCLED', RESTORE: 'ACTIVE', PURGE: 'PURGE_PENDING'
  }
  const purge = body.decision === 'PURGE'
  if (value.resource_revision !== result.lifecycle_revision || result.source_version !== sourceVersion
      || result.decision !== body.decision || result.current_status !== expectedStatus[body.decision]
      || result.previous_status === result.current_status || result.policy_revision !== body.policy_revision
      || purge !== (result.deletion_id !== null) || purge !== (result.deletion_status === 'QUEUED')
      || !purge && result.deletion_item_count !== '0' || purge && BigInt(result.deletion_item_count) < 3n
      || result.actor_id !== session.user.user_id
      || result.lifecycle_revision !== (BigInt(body.expected_revision) + 1n).toString()
      || result.current_context.tenant_id !== context.tenant_id
      || result.current_context.context_revision !== (BigInt(context.context_revision) + 1n).toString()
      || result.current_context.governance_epoch !== (BigInt(context.governance_epoch) + 1n).toString()
      || result.current_context.auth_epoch !== context.auth_epoch) fail('CONTENT_MISMATCH', '资料生命周期回执与固定输入不一致')
  return result
}
export async function confirmSourceDeletion(session: MedicalSession, itemId: string,
    body: SourceDeletionReceiptInput): Promise<SourceDeletionReceipt> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', itemId)
  validate<SourceDeletionReceiptInput>(schema + 'DeletionReceiptInput', body)
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const value = envelope(await request.post('/api/deletion-items/' + itemId + '/receipts', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
    'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token
  } }))
  const result = validate<SourceDeletionReceipt>(schema + 'DeletionReceipt', value.data); validContext(result.current_context)
  if (result.item_id !== itemId || result.item_id !== body.item_id || result.deletion_id !== body.deletion_id
      || result.store_kind !== body.store_kind || result.item_status !== 'CONFIRMED'
      || result.item_revision !== (BigInt(body.expected_revision) + 1n).toString()
      || value.resource_revision !== result.item_revision) fail('CONTENT_MISMATCH', '删除回执与固定输入不一致')
  return result
}
export async function readSourceDeletionJob(session: MedicalSession, deletionId: string,
    signal: AbortSignal): Promise<SourceDeletionJob> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', deletionId)
  const value = envelope(await request.get('/api/deletion-jobs/' + deletionId, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current(session).context_revision } }))
  const result = validate<SourceDeletionJob>(schema + 'DeletionJobView', value.data)
  if (result.deletion_id !== deletionId || value.resource_revision !== result.revision || result.items.length === 0
      || new Set(result.items.map(item => item.item_id)).size !== result.items.length) {
    fail('CONTENT_MISMATCH', '删除作业内容与请求身份不一致')
  }
  revision(result.revision); revision(result.lifecycle_revision)
  for (const item of result.items) revision(item.revision)
  return result
}
export async function decideSourceBinding(session: MedicalSession, projectId: string,
    body: SourceBindingInput): Promise<SourceBindingReceipt> {
  validate('urn:medical:templates:1.0.0#/$defs/Id', projectId)
  validate<SourceBindingInput>(schema + 'BindingInput', body)
  if (!sourceBindingAllowed(session, projectId)) fail('ACCESS_DENIED', '项目适用决定需要项目读取和属性确认许可')
  const context = current(session); const key = crypto.randomUUID() + ':' + crypto.randomUUID()
  const value = envelope(await request.post('/api/projects/' + projectId + '/source-bindings', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision,
    'Idempotency-Key': key, [session.csrf.header_name]: session.csrf.token
  } }))
  const result = validate<SourceBindingReceipt>(schema + 'BindingReceipt', value.data); validContext(result.current_context)
  if (value.resource_revision !== null || result.project_id !== projectId || result.source_version !== body.source_version
      || result.profile_revision !== body.profile_revision || result.applicability !== body.applicability
      || result.actor_id !== session.user.user_id || result.current_context.tenant_id !== context.tenant_id
      || result.current_context.context_revision !== (BigInt(context.context_revision) + 1n).toString()
      || result.current_context.governance_epoch !== (BigInt(context.governance_epoch) + 1n).toString()
      || result.current_context.auth_epoch !== context.auth_epoch) fail('CONTENT_MISMATCH', '项目适用决定回执与固定输入不一致')
  return result
}

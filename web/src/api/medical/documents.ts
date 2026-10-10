import request from '@/utils/request'
import type { BodyPage, DocumentBody, DocumentSnapshot, FieldValue, EvidenceLink, FactUsage,
  RevisionInput, UploadRequest, UploadGrant, UploadCompletion, ArtifactReceipt } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue, documentBodyReference, documentBytesDigest, documentReference,
  DocumentEditBuffer, encodeDocumentBody, immutableDocumentValue } from './document-body'
import type { EncodedDocumentBody } from './document-body'
import { envelope, fail, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import { readOperation } from './operations'
import type { Operation } from './operations'
import type { SourcePart } from './source-uploads'

export interface DocumentReadLimits { maximumPages: number; maximumBytes: number; pageSize: number }
export interface SavedDocument { snapshot: DocumentSnapshot; body: DocumentBody }
export type DocumentSaveResult = { kind: 'SAVED'; document: SavedDocument }
  | { kind: 'PENDING'; operation: Operation }

export async function prepareDocumentDraftSave(session: () => MedicalSession, saved: DocumentSnapshot, draft: DocumentEditBuffer,
  dependencyId: string, reason: string, capacity: DocumentReadLimits): Promise<{
    advance: (signal: AbortSignal, progress: (sentBytes: number, totalBytes: number) => void) => Promise<DocumentSaveResult>
    invalidate: () => void
  }> {
  if (draft.saving) fail('ACTION_STALE', '请通过原正文保存操作继续处理')
  const capture = draft.capture()
  let attached = false
  try {
    const pending = await PendingDocumentSave.prepare(session, saved, capture.body, dependencyId, reason, capacity)
    attached = true
    let confirmed = false
    return {
      async advance(signal, progress) {
        if (!confirmed && !draft.isCurrentCapture(capture)) fail('ACTION_STALE', '当前文稿已经更换保存操作')
        draft.body
        const result = await pending.advance(signal, progress)
        if (result.kind === 'SAVED' && !confirmed) { draft.confirm(capture, result.document.body); confirmed = true }
        return result
      },
      invalidate() { pending.invalidate(); draft.invalidate() }
    }
  } finally {
    if (!attached) draft.releaseUnissued(capture)
  }
}

function context(session: MedicalSession): Context {
  if (session.current_context === null) fail('ACCESS_DENIED', '文稿操作需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
function headers(session: MedicalSession, key?: string): Record<string, string> {
  const result: Record<string, string> = { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(session).context_revision }
  if (key !== undefined) { result['Idempotency-Key'] = key; result[session.csrf.header_name] = session.csrf.token }
  return result
}
function limits(value: DocumentReadLimits): DocumentReadLimits {
  if (![value.maximumPages, value.maximumBytes, value.pageSize].every(item => Number.isSafeInteger(item) && item > 0)
      || value.pageSize > 100) fail('SCHEMA_INVALID', '完整正文读取需要明确容量和有效分页大小')
  return Object.freeze({ ...value })
}
function current(session: MedicalSession, original: Context, actor: string, signal: AbortSignal): void {
  signal.throwIfAborted()
  if (session.user.user_id !== actor || canonicalDocumentValue(context(session)) !== canonicalDocumentValue(original)) {
    fail('CONTEXT_CHANGED', '文稿处理期间企业身份或授权水位已经变化')
  }
}
function snapshot(value: unknown, resourceRevision: string | null, projectId: string, documentId: string): DocumentSnapshot {
  const result = validate<DocumentSnapshot>(documentReference + 'DocumentSnapshot', value)
  if (result.document_id !== documentId || result.project_id !== projectId || result.revision !== resourceRevision
      || result.revision === '0' || ![documentBodyReference, documentReference + 'DocumentBody'].includes(result.body.schema_ref)
      || new Set([result.body.artifact_id, result.fields.artifact_id, result.evidence_links.artifact_id]).size !== 3) {
    fail('CONTENT_MISMATCH', '文稿快照的归属、修订或独立制品身份不一致')
  }
  return result
}

export async function readDocumentSnapshot(session: MedicalSession, projectId: string, documentId: string,
  signal: AbortSignal, documentRevision?: string): Promise<DocumentSnapshot> {
  validate(documentReference + 'Id', projectId); validate(documentReference + 'Id', documentId)
  if (documentRevision !== undefined) validate(documentReference + 'Id', documentRevision)
  const original = { ...context(session) }, actor = session.user.user_id
  const path = '/api/documents/' + documentId + (documentRevision === undefined ? '' : '/revisions/' + documentRevision)
  const raw = await request.get(path, { medical: 'envelope', signal, timeout: 60000, headers: headers(session) })
  current(session, original, actor, signal)
  const response = envelope(raw)
  if (raw.status !== 200) fail('CONTENT_MISMATCH', '文稿读取响应状态无效')
  const result = snapshot(response.data, response.resource_revision, projectId, documentId)
  if (documentRevision !== undefined && result.document_revision !== documentRevision) fail('CONTENT_MISMATCH', '历史正文没有返回指定固定版本')
  if (result.auth_epoch !== original.auth_epoch || result.governance_epoch !== original.governance_epoch) {
    fail('CONTEXT_CHANGED', '文稿快照与当前授权水位不一致')
  }
  return immutableDocumentValue(result)
}

export async function readDocumentDependency(session: MedicalSession, saved: DocumentSnapshot, signal: AbortSignal): Promise<string> {
  const original = { ...context(session) }, actor = session.user.user_id
  validate(documentReference + 'DocumentSnapshot', saved)
  if (!saved.allowed_actions.includes('document.edit')) fail('ACCESS_DENIED', '当前文稿没有编辑权限')
  const raw = await request.get('/api/documents/' + saved.document_id + '/dependency-access-set', { medical: 'envelope', signal, timeout: 60000,
    params: { document_revision: saved.document_revision, expected_revision: saved.revision }, headers: headers(session) })
  current(session, original, actor, signal); const response = envelope(raw)
  if (raw.status !== 200 || response.resource_revision !== saved.revision) fail('REVISION_CONFLICT', '正文编辑依据与当前修订不一致')
  return validate<string>(documentReference + 'Id', response.data)
}

function append<T>(target: T[], seen: Map<string, string>, values: T[], identity: (value: T) => string): void {
  const pageIdentities = new Set<string>()
  for (const value of values) {
    const key = identity(value), content = canonicalDocumentValue(value)
    if (pageIdentities.has(key) || seen.has(key) && seen.get(key) !== content) {
      fail('CONTENT_MISMATCH', '同版投影包含重复身份或不同内容')
    }
    pageIdentities.add(key)
    if (!seen.has(key)) { seen.set(key, content); target.push(value) }
  }
}

export async function readDocumentBody(session: MedicalSession, saved: DocumentSnapshot, suppliedLimits: DocumentReadLimits,
  signal: AbortSignal): Promise<DocumentBody> {
  const capacity = limits(suppliedLimits), original = { ...context(session) }, actor = session.user.user_id
  validate(documentReference + 'DocumentSnapshot', saved)
  if (![documentBodyReference, documentReference + 'DocumentBody'].includes(saved.body.schema_ref) || saved.auth_epoch !== original.auth_epoch
      || saved.governance_epoch !== original.governance_epoch) fail('SCOPE_STALE', '固定正文与当前Schema或授权水位不一致')
  const nodes: DocumentBody['nodes'] = [], fields: FieldValue[] = [], evidence: EvidenceLink[] = [], facts: FactUsage[] = []
  const nodeIds = new Set<string>(), cursors = new Set<string>()
  const fieldIds = new Map<string, string>(), evidenceIds = new Map<string, string>(), factIds = new Map<string, string>()
  let cursor: string | null = null, selection: string | null = null, pages = 0, receivedBytes = 0
  do {
    current(session, original, actor, signal)
    if (++pages > capacity.maximumPages) fail('CONTEXT_LIMIT', '正文分页超过配置容量')
    const raw = await request.get('/api/documents/' + saved.document_id + '/body', { medical: 'envelope', signal, timeout: 60000,
      params: { document_revision: saved.document_revision, limit: capacity.pageSize, ...(cursor === null ? {} : { cursor }) },
      headers: headers(session) })
    current(session, original, actor, signal)
    const response = envelope(raw), page = validate<BodyPage>(documentReference + 'BodyPage', response.data)
    if (raw.status !== 200 || page.document_revision !== saved.document_revision || page.body_sha256 !== saved.body.sha256
        || selection !== null && selection !== page.coverage.selection_sha256) {
      fail('CONTENT_MISMATCH', '正文分页版本、完整摘要或读取范围发生变化')
    }
    selection = page.coverage.selection_sha256
    receivedBytes += new TextEncoder().encode(canonicalDocumentValue(page)).length
    if (receivedBytes > capacity.maximumBytes) fail('CONTEXT_LIMIT', '正文分页内容超过配置容量')
    for (const node of page.nodes) {
      if (nodeIds.has(node.node_id)) fail('CONTENT_MISMATCH', '正文分页重复返回节点')
      nodeIds.add(node.node_id); nodes.push(node)
    }
    append(fields, fieldIds, page.fields, value => value.field_id)
    append(evidence, evidenceIds, page.evidence_links, value => value.evidence_link_id)
    append(facts, factIds, page.fact_usages, canonicalDocumentValue)
    cursor = page.coverage.next_cursor
    if (cursor !== null) {
      if (cursors.has(cursor) || page.nodes.length === 0) fail('CONTENT_MISMATCH', '正文分页未推进或重复返回游标')
      cursors.add(cursor)
    }
  } while (cursor !== null)
  const body: DocumentBody = { schema_version: '1.0.0', template_version: saved.template_version,
    nodes, fields, evidence_links: evidence, fact_usages: facts }
  const encoded = await encodeDocumentBody(body)
  current(session, original, actor, signal)
  if (encoded.sha256 !== saved.body.sha256 || BigInt(encoded.sizeBytes) > BigInt(capacity.maximumBytes)) {
    fail('COVERAGE_INCOMPLETE', '完整正文与固定制品摘要不一致')
  }
  return encoded.body
}

export async function readSavedDocument(session: MedicalSession, projectId: string, documentId: string,
  capacity: DocumentReadLimits, signal: AbortSignal): Promise<SavedDocument> {
  return readCompleteDocument(session, projectId, documentId, capacity, signal)
}

export async function readSavedDocumentRevision(session: MedicalSession, projectId: string, documentId: string, documentRevision: string,
  capacity: DocumentReadLimits, signal: AbortSignal): Promise<SavedDocument> {
  validate(documentReference + 'Id', documentRevision)
  return readCompleteDocument(session, projectId, documentId, capacity, signal, documentRevision)
}

async function readCompleteDocument(session: MedicalSession, projectId: string, documentId: string,
  capacity: DocumentReadLimits, signal: AbortSignal, documentRevision?: string): Promise<SavedDocument> {
  const original = { ...context(session) }, actor = session.user.user_id
  const first = await readDocumentSnapshot(session, projectId, documentId, signal, documentRevision)
  current(session, original, actor, signal)
  const body = await readDocumentBody(session, first, capacity, signal)
  current(session, original, actor, signal)
  const latest = await readDocumentSnapshot(session, projectId, documentId, signal, documentRevision)
  current(session, original, actor, signal)
  if (first.document_revision !== latest.document_revision || first.revision !== latest.revision
      || first.body.sha256 !== latest.body.sha256 || first.body.artifact_id !== latest.body.artifact_id
      || first.body_schema_revision !== latest.body_schema_revision || first.plan_revision !== latest.plan_revision
      || first.template_version !== latest.template_version
      || canonicalDocumentValue(first.fields) !== canonicalDocumentValue(latest.fields)
      || canonicalDocumentValue(first.evidence_links) !== canonicalDocumentValue(latest.evidence_links)) {
    fail('REVISION_CONFLICT', '完整读取期间文稿修订已经变化')
  }
  return { snapshot: latest, body }
}

// 同一保存对象只保存在内存；明确恢复复用全部原键和字节，不启动后台查询循环。
export class PendingDocumentSave {
  readonly id = crypto.randomUUID()
  private readonly grantKey = crypto.randomUUID()
  private readonly completionKey = crypto.randomUUID()
  private readonly revisionKey = crypto.randomUUID()
  private readonly partKeys: string[]
  private grant: UploadGrant | null = null
  private receipt: ArtifactReceipt | null = null
  private saved: DocumentSnapshot | null = null
  private partNo = 1
  private pendingOperation: Operation | null = null
  private busy = false
  private cleared = false
  private readonly actor: string
  private readonly original: Context

  private constructor(private readonly session: () => MedicalSession, readonly baseline: DocumentSnapshot,
    readonly content: EncodedDocumentBody, private readonly dependencyId: string, private readonly reason: string,
    private readonly capacity: DocumentReadLimits) {
    this.actor = session().user.user_id; this.original = immutableDocumentValue(context(session()))
    this.partKeys = Array.from({ length: Math.ceil(content.blob.size / 8388608) }, () => crypto.randomUUID())
  }

  static async prepare(session: () => MedicalSession, baseline: DocumentSnapshot, body: DocumentBody,
    dependencyId: string, reason: string, suppliedLimits: DocumentReadLimits): Promise<PendingDocumentSave> {
    const capacity = limits(suppliedLimits), original = { ...context(session()) }, actor = session().user.user_id
    validate(documentReference + 'DocumentSnapshot', baseline); validate(documentReference + 'Id', dependencyId)
    if (reason.trim().length === 0 || !baseline.allowed_actions.includes('document.edit')) {
      fail('ACCESS_DENIED', '保存正文需要文稿编辑许可和修改原因')
    }
    if (baseline.auth_epoch !== original.auth_epoch || baseline.governance_epoch !== original.governance_epoch
        || body.template_version !== baseline.template_version) fail('SCOPE_STALE', '正文与当前模板或授权水位不一致')
    const encoded = await encodeDocumentBody(body)
    if (session().user.user_id !== actor || canonicalDocumentValue(context(session())) !== canonicalDocumentValue(original)) {
      fail('CONTEXT_CHANGED', '正文编码期间当前企业身份已经变化')
    }
    if (BigInt(encoded.sizeBytes) > BigInt(capacity.maximumBytes)) fail('CONTEXT_LIMIT', '待保存正文超过配置容量')
    return new PendingDocumentSave(session, immutableDocumentValue(baseline), encoded, dependencyId, reason, capacity)
  }

  invalidate(): void { this.cleared = true }
  private current(signal: AbortSignal): MedicalSession {
    if (this.cleared) fail('ACTION_STALE', '原正文保存已经终止')
    const session = this.session(); current(session, this.original, this.actor, signal); return session
  }

  async advance(signal: AbortSignal, progress: (sentBytes: number, totalBytes: number) => void): Promise<DocumentSaveResult> {
    if (this.busy) fail('ACTION_STALE', '原正文保存正在处理')
    this.busy = true
    try {
      this.current(signal)
      if (this.pendingOperation !== null) {
        const operation = await readOperation(this.current(signal), this.pendingOperation.operation_id, signal)
        this.current(signal)
        if (operation.resource_id !== this.grant?.upload_id) fail('CONTENT_MISMATCH', '原分段操作更换了上传对象')
        if (operation.status === 'PROCESSING') return { kind: 'PENDING', operation }
        if (operation.status === 'FAILED') fail('ACTION_STALE', '原正文分段操作已经失败')
        this.pendingOperation = null
      }
      if (this.grant === null) {
        const body: UploadRequest = { purpose: 'DOCUMENT_BODY', document_id: this.baseline.document_id,
          expected_revision: this.baseline.revision, media_type: 'application/json', size_bytes: this.content.sizeBytes,
          sha256: this.content.sha256, schema_ref: documentBodyReference, dependency_access_set_id: this.dependencyId }
        validate(documentReference + 'UploadRequest', body)
        const raw = await request.post('/api/artifact-upload-grants', body, { medical: 'envelope', signal, timeout: 60000,
          headers: headers(this.current(signal), this.grantKey) })
        this.current(signal)
        const response = envelope(raw), grant = validate<UploadGrant>(documentReference + 'UploadGrant', response.data)
        if (raw.status !== 201 || response.resource_revision !== this.baseline.revision
            || grant.upload_url !== '/api/uploads/' + grant.upload_id + '/parts/') fail('CONTENT_MISMATCH', '正文许可与原请求不一致')
        this.grant = immutableDocumentValue(grant)
      }
      while (this.partNo <= this.partKeys.length) {
        const start = (this.partNo - 1) * this.grant.max_part_bytes, part = this.content.blob.slice(start, start + this.grant.max_part_bytes)
        const key = this.partKeys[this.partNo - 1]
        if (key === undefined) fail('CONTENT_MISMATCH', '正文分段缺少原请求身份')
        const hash = await documentBytesDigest(await part.arrayBuffer())
        const raw = await request.put(this.grant.upload_url + this.partNo, part, { medical: 'envelope', signal, timeout: 70000,
          headers: { ...headers(this.current(signal), key), 'Content-Type': 'application/octet-stream', 'Content-SHA256': hash },
          onUploadProgress: event => progress(start + event.loaded, this.content.blob.size) })
        this.current(signal)
        const response = envelope(raw)
        if (raw.status === 202) {
          const operation = validate<Operation>(documentReference + 'Operation', response.data)
          if (operation.resource_id !== this.grant.upload_id || response.resource_revision !== null
              || operation.status_url !== '/api/operations/' + operation.operation_id) fail('CONTENT_MISMATCH', '正文分段处理身份不一致')
          this.pendingOperation = immutableDocumentValue(operation); return { kind: 'PENDING', operation }
        }
        const saved = validate<SourcePart>('urn:medical:source-uploads:1.0.0#/$defs/Part', response.data)
        if (raw.status !== 200 || response.resource_revision === null || saved.part_no !== this.partNo
            || saved.size_bytes !== String(part.size) || saved.sha256 !== hash || saved.state !== 'STORED') {
          fail('CONTENT_MISMATCH', '正文分段回执与实际字节不一致')
        }
        this.partNo++; progress(Math.min(start + part.size, this.content.blob.size), this.content.blob.size)
      }
      if (this.receipt === null) {
        const body: UploadCompletion = { sha256: this.content.sha256, size_bytes: this.content.sizeBytes, expected_revision: this.baseline.revision }
        validate(documentReference + 'UploadCompletion', body)
        const raw = await request.post('/api/artifact-upload-grants/' + this.grant.grant_id + '/completions', body,
          { medical: 'envelope', signal, timeout: 60000, headers: headers(this.current(signal), this.completionKey) })
        this.current(signal)
        const response = envelope(raw), receipt = validate<ArtifactReceipt>(documentReference + 'ArtifactReceipt', response.data)
        if (raw.status !== 200 || receipt.sha256 !== this.content.sha256 || ![documentBodyReference, documentReference + 'DocumentBody'].includes(receipt.schema_ref)
            || response.resource_revision !== receipt.revision) fail('CONTENT_MISMATCH', '完整正文制品回执未通过核验')
        this.receipt = immutableDocumentValue(receipt)
      }
      if (this.saved === null) {
        const body: RevisionInput = { base_revision: this.baseline.document_revision, expected_revision: this.baseline.revision,
          body_artifact_id: this.receipt.artifact_id, body_sha256: this.content.sha256,
          body_schema_revision: this.baseline.body_schema_revision, reason: this.reason }
        validate(documentReference + 'RevisionInput', body)
        const raw = await request.post('/api/documents/' + this.baseline.document_id + '/revisions', body,
          { medical: 'envelope', signal, timeout: 60000, headers: headers(this.current(signal), this.revisionKey) })
        this.current(signal)
        const response = envelope(raw), saved = snapshot(response.data, response.resource_revision,
          this.baseline.project_id, this.baseline.document_id)
        if (raw.status !== 201 || saved.document_revision === this.baseline.document_revision
            || BigInt(saved.revision) <= BigInt(this.baseline.revision) || saved.body.artifact_id !== this.receipt.artifact_id
            || saved.body.sha256 !== this.content.sha256 || saved.plan_revision !== this.baseline.plan_revision
            || saved.template_version !== this.baseline.template_version || saved.body_schema_revision !== this.baseline.body_schema_revision) {
          fail('CONTENT_MISMATCH', '正文保存没有返回对应的新修订')
        }
        this.saved = immutableDocumentValue(saved)
      }
      const document = await readSavedDocument(this.current(signal), this.baseline.project_id, this.baseline.document_id, this.capacity, signal)
      this.current(signal)
      if (document.snapshot.document_revision !== this.saved.document_revision || document.snapshot.revision !== this.saved.revision
          || canonicalDocumentValue(document.body) !== this.content.text) fail('REVISION_CONFLICT', '正文重读与本次保存内容不一致')
      return { kind: 'SAVED', document }
    } finally { this.busy = false }
  }
}

import type { ContentChunk, DocumentSnapshot } from '../../../../contracts/typescript/src/interfaces'
import { byteDigest } from './content-digest'
import { envelope, MedicalFailure, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import type { AxiosResponse } from 'axios'
import { canonicalizeEx } from 'json-canonicalize'
import { readBoundedJson } from './project-json'

const reference = 'https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/'
export const documentArtifactLimits = Object.freeze({ pageBytes: 65536, maximumBytes: 104857600, maximumPages: 1600 })
export interface ArtifactReadLimits { pageBytes: number; maximumBytes: number; maximumPages: number }
export interface ArtifactContentIdentity { artifactId: string; resourceId: string; sha256: string; schemaRef: string; mediaType: string }
function reject(code: string, message: string): never { throw new MedicalFailure(code, message) }

export function parseArtifactContentResponse(text: unknown, pageBytes: number): unknown {
  if (!Number.isSafeInteger(pageBytes) || pageBytes < 1 || pageBytes > 1048576) reject('SCHEMA_INVALID', '制品分页容量无效')
  if (typeof text !== 'string') reject('SCHEMA_INVALID', '制品分页响应需要原始 JSON 文本')
  if (text.length > 4 * Math.ceil(pageBytes / 3) + 16384) reject('FILE_TOO_LARGE', '制品分页响应超过字节与元数据容量')
  return readBoundedJson(text)
}

/** A final page alone is never treated as a complete artifact. All ordered bytes must match the snapshot's fixed hash. */
export class VerifiedArtifactContent {
  private readonly identity: ArtifactContentIdentity
  readonly limits: ArtifactReadLimits
  private parts: Uint8Array<ArrayBuffer>[] = []
  private bytes = 0
  private pages = 0
  private selection: string | null = null
  private mediaType: string | null = null
  private schemaRef: string | null = null
  private readonly cursors = new Set<string>()
  private readonly delivered = new Set<string>()
  private readonly receipts = new Set<string>()
  private next: string | null = null
  private final = false
  private disposed = false
  private busy = false
  private completed = false

  constructor(identity: ArtifactContentIdentity, capacity: ArtifactReadLimits = documentArtifactLimits) {
    validate(reference + 'Id', identity.artifactId); validate(reference + 'Id', identity.resourceId)
    if (!/^[a-f0-9]{64}$/.test(identity.sha256)) reject('SCHEMA_INVALID', '固定制品必须提供完整 SHA-256')
    if (![capacity.pageBytes, capacity.maximumBytes, capacity.maximumPages].every(value => Number.isSafeInteger(value) && value > 0)
        || capacity.pageBytes > 1048576 || capacity.maximumBytes > documentArtifactLimits.maximumBytes
        || capacity.maximumPages > documentArtifactLimits.maximumPages) reject('SCHEMA_INVALID', '固定制品读取容量无效')
    if (!identity.schemaRef || identity.schemaRef.length > 2048 || !/^[a-z0-9!#$&^_.+-]+\/[a-z0-9!#$&^_.+-]+$/.test(identity.mediaType)) {
      reject('SCHEMA_INVALID', '固定制品必须提供明确 Schema 与媒体类型')
    }
    this.identity = Object.freeze({ ...identity }); this.limits = Object.freeze({ ...capacity })
  }
  get receivedBytes(): number { return this.bytes }
  get nextCursor(): string | null { return this.next }
  get completeCoverage(): boolean { return this.final }
  private current(): void {
    if (this.disposed) reject('ACTION_STALE', '固定制品读取已经清理')
  }
  async append(value: unknown): Promise<void> {
    this.current()
    if (this.busy || this.completed || this.final) reject('ACTION_STALE', '固定制品分段不能并发、重复或在终页后追加')
    if (this.pages >= this.limits.maximumPages) reject('CONTEXT_LIMIT', '制品分页超过阅读上限')
    const page = structuredClone(validate<ContentChunk>(reference + 'ContentChunk', value))
    if (page.artifact_id !== this.identity.artifactId || page.resource_id !== this.identity.resourceId
        || page.full_sha256 !== this.identity.sha256 || page.encoding !== 'BASE64'
        || page.schema_ref !== this.identity.schemaRef || page.media_type !== this.identity.mediaType
        || this.schemaRef !== null && page.schema_ref !== this.schemaRef
        || this.mediaType !== null && page.media_type !== this.mediaType
        || this.selection !== null && page.coverage.selection_sha256 !== this.selection
        || this.delivered.has(page.coverage.delivered_sha256) || this.receipts.has(page.read_receipt_id)
        || page.coverage.next_cursor !== null && (page.coverage.next_cursor.length > 8192 || this.cursors.has(page.coverage.next_cursor))) {
      reject('CONTENT_MISMATCH', '制品分段的固定身份、格式或连续读取范围不一致')
    }
    if (page.content.length > 4 * Math.ceil(this.limits.pageBytes / 3)) reject('FILE_TOO_LARGE', '制品分段超过请求的字节上限')
    if (!/^[A-Za-z0-9+/]*={0,2}$/.test(page.content) || !page.content.length || page.content.length % 4 !== 0) {
      reject('CONTENT_MISMATCH', '制品分段没有使用完整标准 Base64 编码')
    }
    const binary = atob(page.content)
    if (btoa(binary) !== page.content) reject('CONTENT_MISMATCH', '制品分段 Base64 编码不规范')
    if (binary.length > this.limits.pageBytes || this.bytes + binary.length > this.limits.maximumBytes) {
      reject('FILE_TOO_LARGE', '固定制品超过配置的总字节或分段上限')
    }
    if (!page.coverage.complete && binary.length !== this.limits.pageBytes) reject('COVERAGE_INCOMPLETE', '制品非终页未包含请求的完整分段')
    if (!page.coverage.complete && (this.bytes + binary.length >= this.limits.maximumBytes || this.pages + 1 >= this.limits.maximumPages)) {
      reject('CONTEXT_LIMIT', '制品仍有后续内容，超过配置的完整读取容量')
    }
    const copy = Uint8Array.from(binary, char => char.charCodeAt(0))
    this.busy = true
    try {
      if (await byteDigest(copy) !== page.chunk_sha256) reject('CONTENT_MISMATCH', '固定制品分段 SHA-256 校验失败')
      this.current()
      this.parts.push(copy); this.bytes += copy.length; this.pages++
      this.mediaType = page.media_type; this.schemaRef = page.schema_ref; this.selection = page.coverage.selection_sha256
      this.delivered.add(page.coverage.delivered_sha256); this.receipts.add(page.read_receipt_id)
      this.next = page.coverage.next_cursor; if (this.next !== null) this.cursors.add(this.next)
      this.final = page.coverage.complete
    } finally { this.busy = false }
  }
  async complete(): Promise<Blob> {
    this.current()
    if (this.busy || this.completed) reject('ACTION_STALE', '固定制品已经交付或正在核验')
    if (!this.final || !this.mediaType || this.bytes === 0) reject('COVERAGE_INCOMPLETE', '尚未取得固定制品的全部分段')
    this.busy = true
    try {
      const blob = new Blob(this.parts, { type: this.mediaType })
      const bytes = new Uint8Array(await blob.arrayBuffer())
      if (await byteDigest(bytes) !== this.identity.sha256) reject('CONTENT_MISMATCH', '完整制品 SHA-256 与正文固定版本不一致')
      this.current()
      this.completed = true; this.parts = []; return blob
    } finally { this.busy = false }
  }
  dispose(): void {
    this.disposed = true
    for (const part of this.parts) part.fill(0)
    this.parts = []; this.cursors.clear(); this.delivered.clear(); this.receipts.clear(); this.next = null; this.final = false
    this.bytes = 0; this.pages = 0; this.selection = null; this.mediaType = null; this.schemaRef = null
  }
}

export type DocumentArtifactKind = 'body' | 'fields' | 'evidence_links'
export type ArtifactPageReader = (artifactId: string, cursor: string | null, limit: number,
  contextRevision: string, signal: AbortSignal) => Promise<AxiosResponse<unknown>>

/** Public byte transport is deliberately independent of the document's node-oriented projection endpoint. */
export async function readCompleteFixedDocumentArtifact(session: () => MedicalSession, snapshot: DocumentSnapshot,
  kind: DocumentArtifactKind, readPage: ArtifactPageReader, signal: AbortSignal, progress: (bytes: number) => void,
  capacity: ArtifactReadLimits = documentArtifactLimits): Promise<Blob> {
  validate(reference + 'DocumentSnapshot', snapshot)
  if (!['body', 'fields', 'evidence_links'].includes(kind)) reject('SCHEMA_INVALID', '请选择正文、字段或证据固定制品')
  const original = session(), context = original.current_context
  if (context === null) reject('ACCESS_DENIED', '固定制品读取需要当前企业身份')
  validContext(context)
  const actor = original.user.user_id, contextKey = canonicalizeEx(context, { strictUndefined: true })
  const contextRevision = context.context_revision, revision = snapshot.revision
  if (snapshot.auth_epoch !== context.auth_epoch || snapshot.governance_epoch !== context.governance_epoch) {
    reject('CONTEXT_CHANGED', '固定文稿与当前制品读取授权水位不一致')
  }
  const artifact = snapshot[kind], artifactId = artifact.artifact_id
  const assembly = new VerifiedArtifactContent({ artifactId, resourceId: snapshot.document_id,
    sha256: artifact.sha256, schemaRef: artifact.schema_ref, mediaType: 'application/json' }, capacity)
  function current(): void {
    signal.throwIfAborted()
    const value = session()
    if (value.user.user_id !== actor || canonicalizeEx(value.current_context, { strictUndefined: true }) !== contextKey) {
      reject('CONTEXT_CHANGED', '制品读取期间企业身份或授权水位已经变化')
    }
  }
  try {
    do {
      current()
      const raw = await readPage(artifactId, assembly.nextCursor, assembly.limits.pageBytes, contextRevision, signal)
      current(); const response = envelope(raw)
      if (raw.headers['x-content-type-options'] !== 'nosniff') reject('SCHEMA_INVALID', '制品响应未声明禁止媒体类型嗅探')
      if (raw.status !== 200 || response.resource_revision !== revision) {
        reject('REVISION_CONFLICT', '制品内容响应与固定文稿记录修订不一致')
      }
      await assembly.append(response.data); current(); progress(assembly.receivedBytes); current()
    } while (!assembly.completeCoverage)
    const blob = await assembly.complete(); current(); return blob
  } finally { assembly.dispose() }
}

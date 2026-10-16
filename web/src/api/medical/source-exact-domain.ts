import { canonicalizeEx } from 'json-canonicalize'
import { fail, revision, validate, validContext } from './protocol'
import type { Context } from './protocol'
import { byteDigest, sourceDigest } from './content-digest'

export const exactReference = 'urn:medical:source-exact:1.0.0#/$defs/'
export const exactKindLabels = { MODEL: '器械型号', PTR_NUMBER: '产品技术要求编号', DOCUMENT_NUMBER: '文件文号',
  STANDARD_NUMBER: '标准编号', STANDARD_YEAR: '标准年代号' } as const
export type ExactKind = keyof typeof exactKindLabels
export interface ExactField {
  field_kind: ExactKind; raw_value: string; alias_value: string | null; alias_reason: string | null
  chunk_id: string; quote_hash: string; locator_sha256: string; start_codepoint: number; end_codepoint: number
}
export interface ExactPublication {
  schema_version: '1.0.0'; project_id: string; parse_version: string; index_version: string; approval_revision: string
  binding_revision: string; rights_revision: string; expected_revision: string; fields: ExactField[]; reason: string
}
export interface ExactManifest {
  schema_version: '1.0.0'; tenant_id: string; source_version: string; field_revision: string; revision: string
  original_sha256: string; request: ExactPublication; actor_id: string; created_at: string
}
export interface ExactReceipt {
  schema_version: '1.0.0'; operation_id: string; source_version: string; field_revision: string; revision: string
  manifest_sha256: string; field_count: number; manifest: ExactManifest; current_context: Context
}
export interface ExactSearchInput {
  schema_version: '1.0.0'; project_id: string; field_kind: ExactKind; query: string; purpose: 'VERIFICATION'
}
export interface ExactHit {
  rank: number; source_id: string; source_version: string; parse_version: string; index_version: string; chunk_id: string
  approval_revision: string; rights_revision: string; binding_revision: string; title: string; excerpt: string
  excerpt_truncated: boolean; locator: Record<string, unknown>; quote_hash: string; field_revision: string
  field_manifest_sha256: string; matches: { ordinal: number; field: ExactField }[]
}
export interface ExactPage extends ExactSearchInput {
  retrieval_id: string; mode: 'EXACT'; status: 'FOUND' | 'EMPTY'; created_at: string; selection_sha256: string
  total: string; page: number; page_size: number; next_page: number | null; items: ExactHit[]
}
export function exactHasText(value: string): boolean {
  return value.length > 0 && !/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/u.test(value)
}
export function exactLiteral(value: string): void {
  if (typeof value !== 'string' || !exactHasText(value) || Array.from(value).length > 512
      || Array.from(value).some(character => { const code = character.codePointAt(0)!; return code >= 0xd800 && code <= 0xdfff })) {
    fail('SCHEMA_INVALID', '字段原值及查询需要包含有效的完整Unicode文字')
  }
}
export function verifyExactField(field: ExactField): void {
  validate(exactReference + 'Field', field); exactLiteral(field.raw_value)
  if (!Number.isSafeInteger(field.start_codepoint) || !Number.isSafeInteger(field.end_codepoint)
      || field.end_codepoint <= field.start_codepoint || (field.alias_value === null) !== (field.alias_reason === null)) {
    fail('SCHEMA_INVALID', '字段范围或别名与审核理由不一致')
  }
  if (field.alias_value !== null) {
    exactLiteral(field.alias_value)
    if (field.alias_value === field.raw_value || !field.alias_reason?.trim()
        || field.alias_reason !== field.alias_reason.trim() || field.alias_reason !== field.alias_reason.normalize('NFC')) {
      fail('SCHEMA_INVALID', '别名需要独立值和完整规范审核理由')
    }
  }
}
export function verifyExactPublication(input: ExactPublication): void {
  validate(exactReference + 'PublicationInput', input); revision(input.expected_revision)
  if (input.expected_revision === '18446744073709551615' || !input.reason.trim()
      || input.reason !== input.reason.trim() || input.reason !== input.reason.normalize('NFC')) fail('SCHEMA_INVALID', '字段修订或核准理由无效')
  const seen = new Set<string>()
  for (const field of input.fields) {
    verifyExactField(field)
    const identity = canonicalizeEx([field.field_kind, field.raw_value, field.alias_value, field.chunk_id, field.start_codepoint, field.end_codepoint])
    if (seen.has(identity)) fail('SCHEMA_INVALID', '完整字段清单包含重复的原值、位置及别名')
    seen.add(identity)
  }
}
export function verifyExactManifest(value: ExactManifest, tenantId: string, sourceVersion: string, projectId: string): void {
  validate(exactReference + 'Manifest', value); verifyExactPublication(value.request); revision(value.revision)
  if (value.tenant_id !== tenantId || value.source_version !== sourceVersion || value.request.project_id !== projectId
      || BigInt(value.revision) !== BigInt(value.request.expected_revision) + 1n) fail('CONTENT_MISMATCH', '字段清单与固定企业、来源、项目及修订不一致')
}
export async function verifyExactReceipt(value: ExactReceipt, input: ExactPublication, tenantId: string, sourceVersion: string): Promise<void> {
  validate(exactReference + 'PublicationReceipt', value); validContext(value.current_context)
  verifyExactManifest(value.manifest, tenantId, sourceVersion, input.project_id)
  if (value.current_context.tenant_id !== tenantId || value.source_version !== sourceVersion
      || value.field_revision !== value.manifest.field_revision || value.revision !== value.manifest.revision
      || value.field_count !== input.fields.length || canonicalizeEx(value.manifest.request) !== canonicalizeEx(input)
      || await sourceDigest(value.manifest) !== value.manifest_sha256) fail('CONTENT_MISMATCH', '字段核准回执与完整原输入及清单摘要不一致')
}
export async function verifyExactPage(value: ExactPage, retrievalId: string, page: number, pageSize: number): Promise<void> {
  validate(exactReference + 'Page', value); revision(value.total); exactLiteral(value.query)
  const start = BigInt(page - 1) * BigInt(pageSize), total = BigInt(value.total)
  const length = total <= start ? 0n : total - start < BigInt(pageSize) ? total - start : BigInt(pageSize)
  if (value.retrieval_id !== retrievalId || value.page !== page || value.page_size !== pageSize
      || (value.status === 'EMPTY') !== (total === 0n) || BigInt(value.items.length) !== length
      || value.next_page !== (start + length < total ? page + 1 : null)) fail('CONTENT_MISMATCH', '等值查询页面与固定集合及完整数量不一致')
  const seen = new Set<string>()
  for (const [index, hit] of value.items.entries()) {
    validate('https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json#/$defs/locator', hit.locator)
    const identity = canonicalizeEx([hit.source_version, hit.parse_version, hit.chunk_id])
    if (BigInt(hit.rank) !== start + BigInt(index + 1) || seen.has(identity)) fail('CONTENT_MISMATCH', '等值查询命中顺序或完整片段身份重复')
    seen.add(identity)
    const locator = await sourceDigest(hit.locator); let previous = -1
    for (const match of hit.matches) {
      verifyExactField(match.field)
      if (!Number.isSafeInteger(match.ordinal) || match.ordinal <= previous || match.field.field_kind !== value.field_kind
          || match.field.chunk_id !== hit.chunk_id || match.field.quote_hash !== hit.quote_hash || match.field.locator_sha256 !== locator
          || match.field.raw_value !== value.query && match.field.alias_value !== value.query) fail('CONTENT_MISMATCH', '匹配字段、原值、别名或原文位置与查询不一致')
      previous = match.ordinal
    }
    if (!hit.excerpt_truncated && await byteDigest(new TextEncoder().encode(hit.excerpt)) !== hit.quote_hash) fail('CONTENT_MISMATCH', '完整检索摘录与原文摘要不一致')
  }
}

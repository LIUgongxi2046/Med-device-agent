import type { SchemaCatalogue } from './catalogue.js'

// 字段来自 dataset.schema.json；约束始终由发布集合的 Schema 执行。
export type Locator = PdfLocator | DocxLocator | XlsxLocator | CsvLocator | TextLocator | MarkdownLocator | PptxLocator
export interface PdfLocator {
  kind: 'PDF'; page_index: number; page_basis: 'ORIGINAL' | 'CONVERTED_PREVIEW'; printed_label: string | null
  region: { kind: 'WHOLE_PAGE' } | { kind: 'BOX'; x0: number; y0: number; x1: number; y1: number; coordinate_system: 'CROPBOX_TOP_LEFT_NORMALIZED'; rotation: 0 | 90 | 180 | 270 }
}
export interface DocxLocator {
  kind: 'DOCX'; part: string
  address: { kind: 'PARAGRAPH'; paragraph_index: number } | { kind: 'CELL'; table_index: number; row_index: number; column_index: number } | { kind: 'NODE'; node_id: string; node_hash: string; structural_path: number[] }
}
export interface XlsxLocator {
  kind: 'XLSX'; sheet_id: string; sheet_name: string; cell_range: string
  value_basis: 'LITERAL' | 'FORMULA' | 'CACHED_RESULT' | 'VERIFIED_CALCULATION'; calculation_artifact_id: string | null
}
export interface CsvLocator { kind: 'CSV'; table_id: string; dialect_version: string; record_index: number; column_indices: number[]; header_record_count: number }
export interface TextLocator { kind: 'TXT'; encoding: 'UTF-8'; newline_policy: 'PRESERVE'; char_start: number; char_end: number; line_start: number; line_end: number }
export interface MarkdownLocator { kind: 'MARKDOWN'; node_index: number; line_start: number; line_end: number; heading_path: string[] }
export interface PptxLocator { kind: 'PPTX'; slide_id: string; slide_index: number; shape_id: string; paragraph_index: number | null; table_cell: { row_index: number; column_index: number } | null }
export interface EvidenceRef {
  schema_version: '1.0.0'; tenant_id: string; project_id: string; source_id: string; source_version: string; parse_version: string; chunk_id: string
  locator_kind: Locator['kind']; locator: Locator; quote_hash: string; approval_revision: string
}
export interface RegisteredSource {
  source_id: string; source_ref: string; owner: string; official_url: string | null; publisher_version: string; sha256: string; size_bytes: number
  format: Locator['kind'] | 'DOC_REFERENCE'; nature: 'OFFICIAL_ORIGINAL' | 'DERIVED_PREVIEW' | 'DERIVED_TEXT' | 'PROJECT_SPECIFICATION'
  parent_source_id: string | null; license: string
  allowed_use: ('LOCAL_DESIGN_REVIEW' | 'LOCAL_PARSER_CHECK' | 'APPROVED_PRODUCTION' | 'APPROVED_EXTERNAL_PROCESSING')[]
  sensitivity: 'PUBLIC' | 'INTERNAL' | 'RESTRICTED' | 'PERSONAL_HEALTH'; retention: string; production_status: 'PENDING_APPROVAL' | 'APPROVED' | 'DISABLED'
}
export interface RegisteredRecord {
  record_id: string; source_id: string; source_version: string; parse_version: string; chunk_id: string; section_title: string; text: string
  quote_hash: string; locator: Locator; checks: string[]; formal_evidence_eligible: boolean
}
export interface SourceDataset { schema_version: '1.0.0'; dataset_version: string; created_at: string; sources: RegisteredSource[]; records: RegisteredRecord[] }

export function decodeDataset(catalogue: SchemaCatalogue, value: unknown): SourceDataset {
  catalogue.validate('dataset', value)
  return value as unknown as SourceDataset
}

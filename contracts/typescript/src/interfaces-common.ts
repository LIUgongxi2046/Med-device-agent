// 类型依据 interfaces.schema.yaml；字段格式与跨语言表示在读取入口校验。
export type Id = string
export type Revision = string
export type Hash = string
export type NullableId = Id | null
export type NullableHash = Hash | null
export type NullableText = string | null
export type Ids = Id[]
export type Texts = string[]
export type Timestamp = string

export type Coverage = {
  selection_sha256: Hash
  delivered_sha256: Hash
} & ({ complete: true; next_cursor: null } | { complete: false; next_cursor: string })

export interface ArtifactRef {
  artifact_id: Id
  sha256: Hash
  schema_ref: string
  coverage: Coverage
}

export interface Selection {
  document_revision: Id
  segments: (TextSelection | ImageSelection | TableSelection)[]
}

export interface TextSelection {
  kind: 'TEXT'
  node_id: Id
  node_sha256: Hash
  start_cp: number
  end_cp: number
  quote_sha256: Hash
}

export interface ImageSelection {
  kind: 'IMAGE'
  node_id: Id
  image_artifact_id: Id
  image_sha256: Hash
}

export interface TableSelection {
  kind: 'TABLE'
  node_id: Id
  row_ids: Ids
  column_ids: Ids
  cell_ids: Ids
  table_sha256: Hash
}

export type SourceScope = (
  { mode: 'PROJECT_APPROVED'; collection_versions: []; source_versions: [] }
  | { mode: 'COLLECTION_VERSIONS'; collection_versions: Ids; source_versions: [] }
  | { mode: 'SOURCE_VERSIONS'; collection_versions: []; source_versions: Ids }
  | { mode: 'COMBINED'; collection_versions: Ids; source_versions: Ids }
) & (
  { network_enabled: false; network_policy_revision: null; approved_query_revision: null }
  | { network_enabled: true; network_policy_revision: Id; approved_query_revision: Id }
)

export interface EvidenceDecision {
  evidence_link_id: Id
  selection: Selection
  access: 'PASS' | 'FAIL'
  location: 'PASS' | 'FAIL'
  support: 'SUPPORTED' | 'CONTRADICTED' | 'INSUFFICIENT' | 'NOT_APPLICABLE'
  reason: string
  rule_revision: NullableId
}

export interface CommonDefinitions {
  Id: Id; Revision: Revision; Hash: Hash; NullableId: NullableId; NullableHash: NullableHash; NullableText: NullableText
  Ids: Ids; Texts: Texts; Timestamp: Timestamp; Coverage: Coverage; ArtifactRef: ArtifactRef; Selection: Selection
  TextSelection: TextSelection; ImageSelection: ImageSelection; TableSelection: TableSelection; SourceScope: SourceScope
  EvidenceDecision: EvidenceDecision
}

export const COMMON_DEFINITIONS = [
  'Id', 'Revision', 'Hash', 'NullableId', 'NullableHash', 'NullableText', 'Ids', 'Texts', 'Timestamp', 'Coverage',
  'ArtifactRef', 'Selection', 'TextSelection', 'ImageSelection', 'TableSelection', 'SourceScope', 'EvidenceDecision',
] as const satisfies readonly (keyof CommonDefinitions)[]

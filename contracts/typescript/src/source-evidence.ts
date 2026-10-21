import type { SchemaCatalogue } from './catalogue.js'
import type { EvidenceRef, Locator } from './dataset.js'
export interface ImageLocator {
  schema_version: '1.0.0'; kind: 'IMAGE'; frame_index: number; original_sha256: string
  coordinate_system: 'ORIGINAL_TOP_LEFT_NORMALIZED'; width: number; height: number
  x0: number; y0: number; x1: number; y1: number
}
export interface HtmlLocator {
  schema_version: '1.0.0'; kind: 'HTML'; original_sha256: string
  address: { kind: 'HTML_ELEMENT'; element_path: string; text_start: number; text_end: number }
}
export type LocatorV2 = Locator | ImageLocator | HtmlLocator
export interface EvidenceRefV2 extends Omit<EvidenceRef, 'schema_version' | 'locator_kind' | 'locator'> {
  schema_version: '2.0.0'; locator_kind: LocatorV2['kind']; locator: LocatorV2
}
export function decodeEvidence(catalogue: SchemaCatalogue, value: unknown): EvidenceRef | EvidenceRefV2 {
  const version = (value as { schema_version?: unknown } | null)?.schema_version
  if (version !== '1.0.0' && version !== '2.0.0') throw new Error('EVIDENCE_CONTRACT_UNSUPPORTED')
  catalogue.validate(version === '2.0.0' ? 'source-evidence-v2' : 'dataset', value, 'EvidenceRef')
  const result = value as unknown as EvidenceRef | EvidenceRefV2
  if (result.locator_kind !== result.locator.kind) throw new Error('LOCATOR_KIND_MISMATCH')
  if (result.locator.kind === 'IMAGE' && !(result.locator.x0 < result.locator.x1 && result.locator.y0 < result.locator.y1)) throw new Error('IMAGE_LOCATION_INVALID')
  if (result.locator.kind === 'HTML' && result.locator.address.text_start >= result.locator.address.text_end) throw new Error('HTML_LOCATION_INVALID')
  return result
}

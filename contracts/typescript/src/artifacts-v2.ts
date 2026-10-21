import type { SchemaCatalogue, JsonValue } from './catalogue.js'
import type { EvidenceRef } from './dataset.js'
import type { EvidenceRefV2 } from './source-evidence.js'
import { validateArtifactWire } from './artifacts.js'
import type { ProfessionalArtifact, ArtifactDefinitions, ArtifactBinding } from './artifacts.js'
import { decimalValue, uuidValue } from './wire.js'
type WithV2Evidence<T> = T extends EvidenceRef ? EvidenceRefV2 : T extends readonly unknown[] ? { [K in keyof T]: WithV2Evidence<T[K]> } : T extends object ? { [K in keyof T]: WithV2Evidence<T[K]> } : T
export type ProfessionalArtifactV2 = Omit<WithV2Evidence<ProfessionalArtifact>, 'schema_version'> & { schema_version: '2.0.0' }
export type ArtifactDefinitionsV2 = { [K in keyof ArtifactDefinitions]: WithV2Evidence<ArtifactDefinitions[K]> }
export function bindArtifactV2<K extends keyof ArtifactDefinitionsV2>(catalogue: SchemaCatalogue, definition: K, value: unknown): ArtifactBinding<ArtifactDefinitionsV2[K]>
export function bindArtifactV2(catalogue: SchemaCatalogue, definition: undefined, value: unknown): ArtifactBinding<ProfessionalArtifactV2>
export function bindArtifactV2(catalogue: SchemaCatalogue, definition: keyof ArtifactDefinitionsV2 | undefined, value: unknown): ArtifactBinding<unknown> {
  const saved = catalogue.bind('artifacts-v2', value, definition)
  const parsed = JSON.parse(saved.canonicalJson) as JsonValue
  if ((definition === 'Id' || definition === 'NullableId') && parsed !== null) uuidValue(parsed)
  if (definition === 'Ids') {
    if (!Array.isArray(parsed)) throw new Error('IDENTITY_ARRAY_REQUIRED')
    for (const id of parsed) uuidValue(id)
  }
  if (definition === 'Decimal') decimalValue(parsed, 38, 12)
  if (definition !== 'EvidenceRef' && definition !== 'EvidenceRefs') validateArtifactWire(parsed)
  return Object.freeze({ ...saved, read: (): unknown => JSON.parse(saved.canonicalJson) })
}

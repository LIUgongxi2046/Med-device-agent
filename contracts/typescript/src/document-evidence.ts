import type { SchemaCatalogue } from './catalogue.js'
import type { DocumentBody, EvidenceLink, DocumentEvidenceProjection, DocumentFieldProjection, BodyPage, UploadRequest } from './interfaces.js'
import type { EvidenceRefV2 } from './source-evidence.js'
export interface EvidenceLinkV2 extends Omit<EvidenceLink, 'evidence'> { evidence: EvidenceRefV2 }
export interface DocumentBodyV2 extends Omit<DocumentBody, 'schema_version' | 'evidence_links'> { schema_version: '2.0.0'; evidence_links: EvidenceLinkV2[] }
export interface DocumentEvidenceProjectionV2 extends Omit<DocumentEvidenceProjection, 'schema_version' | 'evidence_links'> { schema_version: '2.0.0'; evidence_links: EvidenceLinkV2[] }
export interface DocumentFieldProjectionV2 extends Omit<DocumentFieldProjection, 'schema_version'> { schema_version: '2.0.0' }
export interface BodyPageV2 extends Omit<BodyPage, 'evidence_links'> { evidence_links: EvidenceLinkV2[] }
export interface UploadRequestV2 extends Omit<UploadRequest, 'schema_ref'> { schema_ref: 'https://schemas.medical-agent.invalid/2.0.0/document-body#/$defs/DocumentBody' }
export function decodeDocumentBodyV2(catalogue: SchemaCatalogue, value: unknown): DocumentBodyV2 {
  catalogue.validate('document-body-v2', value, 'DocumentBody')
  return value as unknown as DocumentBodyV2
}

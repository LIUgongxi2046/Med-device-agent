import request from '@/utils/request'
import type { DocumentSnapshot } from '../../../../contracts/typescript/src/interfaces'
import { parseArtifactContentResponse, readCompleteFixedDocumentArtifact } from './artifact-content'
import type { ArtifactReadLimits, DocumentArtifactKind } from './artifact-content'
import type { MedicalSession } from './protocol'

/** Fetch only the snapshot's server-resolved document artifact. Never accepts arbitrary URLs or client authorization scope. */
export function readFixedDocumentArtifact(session: () => MedicalSession, snapshot: DocumentSnapshot, kind: DocumentArtifactKind,
  signal: AbortSignal, progress: (bytes: number) => void, capacity?: ArtifactReadLimits): Promise<Blob> {
  return readCompleteFixedDocumentArtifact(session, snapshot, kind, (artifactId, cursor, limit, contextRevision, reading) =>
    request.get('/api/artifacts/' + artifactId + '/content', { medical: 'envelope', signal: reading, timeout: 60000,
      transformResponse: [(value: unknown) => parseArtifactContentResponse(value, limit)],
      params: { limit, ...(cursor === null ? {} : { cursor }) },
      headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': contextRevision } }), signal, progress, capacity)
}

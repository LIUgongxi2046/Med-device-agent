import type { DocumentBody, DocumentSnapshot, Selection } from '../../../../contracts/typescript/src/interfaces'
import { readSavedDocumentRevision } from './documents'
import type { SavedDocument } from './documents'
import { annotationAllowed, annotationDigest, assertAnnotationSession, captureAnnotationSession, verifyAnnotationSelection } from './annotation-domain'
import type { SessionReader } from './annotation-domain'
import { fail, validate } from './protocol'
import { documentReference } from './document-body'

// Never reuse the viewed body's bytes as proof for a Selection belonging to another fixed revision.
// This cache is request-local: authorization/context changes cannot expose an earlier identity's sources.
export function annotationSources(session: SessionReader, snapshot: DocumentSnapshot, body: DocumentBody, signal: AbortSignal) {
  const original = captureAnnotationSession(session())
  const sources = new Map<string, SavedDocument>([[snapshot.document_revision, { snapshot, body }]])
  return async (selection: Selection): Promise<SavedDocument> => {
    assertAnnotationSession(session(), original, signal)
    let source = sources.get(selection.document_revision)
    if (!source) {
      source = await readSavedDocumentRevision(session(), snapshot.project_id, snapshot.document_id, selection.document_revision,
        { maximumPages: 1000, maximumBytes: 104857600, pageSize: 100 }, signal)
      assertAnnotationSession(session(), original, signal)
      if (source.snapshot.document_id !== snapshot.document_id || source.snapshot.project_id !== snapshot.project_id
          || !annotationAllowed(session(), source.snapshot, 'annotation.read')) fail('ACCESS_DENIED', '无法读取原固定正文的标注来源')
      sources.set(selection.document_revision, source)
    }
    validate(documentReference + 'DocumentSnapshot', source.snapshot); validate(documentReference + 'DocumentBody', source.body)
    if (source.body.template_version !== source.snapshot.template_version || await annotationDigest(source.body) !== source.snapshot.body.sha256) fail('CONTENT_MISMATCH', '选区来源不是完整固定正文')
    await verifyAnnotationSelection(source.body, source.snapshot.document_revision, selection, signal)
    assertAnnotationSession(session(), original, signal)
    return source
  }
}

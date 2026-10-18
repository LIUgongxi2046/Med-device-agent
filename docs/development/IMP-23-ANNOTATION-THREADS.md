# IMP-23 Annotation threads: additive persistence slice

## Implemented scope

`POST /api/documents/{id}/annotation-threads` and fixed-revision `GET` create and read real annotation rows and actual immutable Selection objects. The existing `/api/annotation-threads/{id}/replies` extension consumes the created rows without changing its CAS or state semantics.

This is not completion of all IMP-23, RV-04, or BX-11. Frozen RV-04/BX-11 require Record/RecordPage with a genuine AnnotationDetail ArtifactRef; this extension returns explicitly versioned inline details. It does not manufacture detail ArtifactRefs, expose new generic BX-37 content routes, resolve/reopen/reanchor annotations, confirm a document, alter task state, or sign a document.

## Contract and limits

- The request uses original AnnotationInput fields: `document_revision`, `selection`, `content`, `expected_revision`. The expected revision is the document **head** revision; a new annotation starts with its own revision `1`.
- Complete listing requires `document_revision` and permits optional `status`. No pagination or silent truncation. Stable ordering is `(created_at, annotation_id)`.
- Maximum request bytes: 1,048,576. Servlet validates identity before reading and caps both declared length and actual stream.
- Maximum comment: 16,000 Unicode code points. Exact original Unicode, spaces and newlines survive; whitespace-only, malformed Unicode and non-text controls are rejected.
- Maximum per-annotation response reservation: 65,536 canonical UTF-8 bytes. Admission calculates the worst future status/location/timestamp and 20-digit annotation revision/reply count. This is checked before an operation or object is created.
- Maximum annotations per fixed revision: 100. Thus all successfully admitted records still fit as a complete collection when replies and status change. The complete response ceiling is 8,388,608 bytes.
- Maximum whole-document confirmation invalidations per create: 1,000. Excess means explicit `CONTEXT_LIMIT`, never a partial set.
- Selection object size is additionally limited by the configured document-storage maximum and the 1 MiB hard request ceiling.
- Receipt `revision`/Envelope.resource_revision is the new document-head revision. `annotation.revision` is the separate reply concurrency identity.
- API `selection_sha256` is SHA-256 of the exact RFC8785 Selection bytes. For this additive storage format only, legacy `annotation.quoted_sha256` stores that same complete Selection digest. `annotation.node_id` is the first segment's compatibility anchor, not a substitute for all segments. Historical rows are never rewritten or reinterpreted. Unknown annotation schema manifests fail closed.

## Selection meaning

`DocumentSelectionSemantics` validates the shared Selection types against the actually verified fixed DocumentBody. It matches frontend full-Node canonical hashes and depth-first sibling-ordinal order, code-point text offsets, exact UTF-8 quote hashes, fixed image identities, and merged-table selected-cell coverage. No Unicode normalization or artificial joins/newlines are introduced. Whole nonempty text nodes use `[0, codePointCount)`, whole images use IMAGE, and full tables enumerate all actual cells. Containers do not receive a fabricated WHOLE_NODE type.

A TABLE segment and text inside its selected cells overlap and are rejected; disjoint table-cell content remains selectable. Repeated nodes, wrong node kinds or nonexistent positions return `SELECTION_INVALID`/422. Stale fixed revisions or hashes return `SELECTION_STALE`/409.

## Real durable object flow

1. Current annotation.write permission and fixed body are verified. `DocumentReadService.readFixed` reuses the existing complete actual body/field/evidence reads, full dependency/source authority, semantic checks and final state digest.
2. A durable PROCESSING operation fixes annotation ID, artifact ID, request ID, write ID, object key, canonical bytes digest/size, fixed body digest, authorization digest and crossed validity-boundary clock.
3. Outside all tenant transactions, existing `SourceUploadTransport.writeDocumentProjection` calls actual `S3ObjectWriter.writeVerified` or `recoverVerified`, using actual transfer admission, private bounded temporary files, immutable object versions, metadata and digest checks. No new unimplemented storage port is introduced.
4. The returned real object reference is checked and persisted back into the command with CAS. After an uncertain PUT, a retry retains the exact intent and verifies the original object. It does not issue a replacement create/key.
5. One final tenant transaction rechecks current body, project, permissions, sources, time boundaries and exact command; registers ARTIFACT version, SELECTION artifact and DOCUMENT binding; saves the full inherited lineage; inserts OPEN annotation; advances document-head revision and same-version issue watermark; invalidates current whole-document CONFIRM decisions; and saves the original 201 operation, safe audit and Outbox.
6. All domain records, post-CAS head/watermarks, invalidations, audit and Outbox are read back and compared. Timed grant/source boundaries are checked again before return. The service expects its own head/watermark mutations rather than comparing the obsolete pre-write digest afterward.

The Selection artifact reuses the body's actual dependency access-set and exact complete member set. Adding undeclared body/revision lineage edges would violate DependencyLineage's exact membership proof, so the service does not do so. The actual artifact document_revision is separately queried and validated.

The operation extension creates no expiration policy; non-null dedup expiry/policy fields are rejected rather than silently ignored. Completed replay verifies the original operation ID, current rights, original actual object, annotation identity/content and recorded invalidations/audit/Outbox, returning the original receipt even after later replies or head movement.

## Authority and invalidation

annotation.read/write uses DOCUMENT scope and DOCUMENT_REVIEW purpose. Actual content continues through the original DOCUMENT_BODY document/artifact/source permission contract; no source access purpose is widened. Each read/write transfer callback and final transaction rechecks current authority. `DocumentReadService.requireCurrent(Admission, FixedRead)` avoids prohibited nested EnterpriseAccess transactions.

New writes require ACTIVE project, current fixed document and unsigned document. Historical reads retain HISTORY source authority and ORIGINAL_VERSION display. Signed history is never modified. Creating OPEN increments the document resource revision and `document_revision_state.issue_watermark`, preserving the fixed body and evidence watermark. Existing current whole-document CONFIRM decisions receive actual review_invalidation rows whose trigger_version is the real Selection artifact version. Chapter decisions and historical signature rows are untouched. A future confirmation/signing consumer must honor these records and current watermarks; no SIGN_PENDING task demotion is guessed here.

Audit and Outbox include identities, revisions, watermarks and hashes only. Comment text stays in the classified annotation/operation response; Selection and private object addresses are excluded from audit/events/errors. Error envelopes are safe and uncached.

## Verification boundary

Focused offline tests use actual shared/local schemas, canonicalization, pure Selection semantics, actual MyBatis/JSqlParser tenant-subject SQL guards, direct servlet checks, and a transactional rollback shim with storage/source doubles. They cover actual-byte persistence assertions, unknown PUT recovery, completed replay, historical location, corruption, permission/time denial, CAS/outbox rollback, full-document invalidation and complete-list capacity. The receipt roundtrip regression explicitly exercises typed long offsets `0/1` against parsed JSON integer nodes for both create and replay. Capacity tests admit an exact 65,536-byte worst-case detail, reject 65,537 bytes before any operation/object write, and validate 100 maximum detail items after counters/status/location grow.

On 2026-10-18 the focused offline suite passed 77 tests across AnnotationThread{Service,Schema,Mapper,Controller}Test, AnnotationReply{Service,Schema,Mapper,Controller}Test, DocumentSelectionSemanticsTest and DocumentBodySemanticsTest. Mockito used its existing local startup agent with non-forked Surefire, not runtime self-attachment.

These tests do not run a database, S3 endpoint, Redis, HTTP server, browser, cloud provider or paid model. End-to-end runtime storage/transaction behavior is not claimed by this slice. No migration is needed: existing annotation/artifact/binding/lineage/watermark/review tables support these records, and migration 038 restricts uniqueness only to the three fixed document projections.

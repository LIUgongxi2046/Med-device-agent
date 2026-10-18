# EX-05 document revision directory

Frozen public path: GET /api/documents/{documentId}/revisions.
Headers: exactly one X-Schema-Version: 1.0.0 and X-Context-Revision. Current authenticated application session required. No request body, no CSRF/write key. Query: required `limit` positive decimal integer within deployment maximumPageSize; optional `cursor` opaque UUID. Duplicate/unknown query parameters fail schema validation. No default limit is fabricated.

Response existing Envelope with schema_version/request_id/resource_revision/data; envelope revision and X-Resource-Revision equal actual document head revision. Cache-Control: no-store. Data schema file: history/document-revision-directory.schema.json. Java helper: DocumentRevisionDirectorySchema. Fields:
- schema_version = 1.0.0
- document_id
- current_document_revision (actual UUID or null if no current body)
- document_head_revision (decimal string)
- items (authorized entries only)
- next_cursor (opaque token or null), complete (true iff next_cursor null)

Each item: document_revision, body_sha256, created_by, created_at (UTC), is_current, read_url, check_ids, review_ids, signature_ids. read_url is exactly /api/documents/{documentId}/revisions/{documentRevision}. Reference arrays contain actual fixed-version record IDs, not an approval/currentness assertion. No titles/reasons/private storage keys/source metadata are invented. Parent revision IDs are omitted because an authorized revision can reference an unauthorized older revision. No total count field.

The directory is live keyset pagination ordered created_at descending then revision UUID descending as deterministic tie-breaker. It is not an immutable frozen directory. Cursor binds tenant, actor, session, context, auth/governance epochs, document, head revision and limit. Changed head/session/context/epochs => CURSOR_INVALID; expired/evicted token => CURSOR_EXPIRED. Never silently restart page one. Both errors should be local to the history panel: discard stale page chain and offer explicit reload from page one. Session authorization loss/context changes immediately clear visible pages.

A bounded raw-candidate scan can yield an empty items array with non-null next_cursor because inaccessible entries are hidden. Continue only using the returned token; never infer exhausted history from items.length==0 or a short page. No unknown count is rendered as zero. A user-selected item emits its actual fixed revision to the parent editor, which calls existing fixed revision/full body reading; this directory itself never downloads body bytes or produces a FixedRead/content-read receipt.

Permissions: initial real DOCUMENT owner resolution and document.read/DOCUMENT_BODY grant; every disclosed entry uses existing DocumentReadService.authorizeMetadata and requireMetadataCurrent. Current entries use that authority's FORMAL mode, old entries HISTORY. Explicit ACCESS_DENIED/SOURCE_NOT_READY/SOURCE_MISSING rows are hidden without IDs/reasons; corrupt data and generic storage failures abort the page. Global/current document grant denial aborts. Merely receiving directory metadata does not imply later body read remains permitted.

Deployment feature is default disabled via medical.document-history.enabled. Explicit positive maximum-page-size, maximum-scan-rows (at least max page size), maximum-references, maximum-cursor-entries, cursor-seconds required. Actual feature discovery is defined below; route 404 is not proof of an empty directory.

No compare/merge/adopt/check/review transition is offered. Backend files were published after root release; aggregate integration and deployment validation remain separately required.

Availability contract: unconditional authenticated GET /api/document-history-feature-availability, identical schema/context headers and no-store envelope. Data: {schema_version:'1.0.0', current_context:{tenant_id,context_revision,auth_epoch,governance_epoch}, features:{document_history:boolean}, maximum_page_size:positive_integer|null}. False requires null; true returns actual bounded policy maximum. The flag is actual controller registration, not document permission or storage success. Frontend can choose min(10,maximum_page_size), always sending explicit limit. Its schema is availability/document-history-feature-availability.schema.json. No availability may be inferred from route errors.

Explicit capacity availability boundary: bounded check/review/signature reference preflight runs before the current MetadataRead authorization method, because that existing method constructs full reference lists internally. Therefore an oversized candidate reference set causes CONTEXT_LIMIT for the whole request even if a subsequent source authorization would have denied that candidate. The service does not swallow CONTEXT_LIMIT or claim an empty authorized page. This can reduce directory availability; it exposes no revision/reference IDs. Removing that limitation safely requires a separately tested source-aware metadata authorization phase that does not assemble those references; a UI permission flag or bare document grant is insufficient.

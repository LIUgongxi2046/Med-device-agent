# Fixed saved-revision structural comparison API (staging, 1.0.0)

Public shape frozen 2026-10-18. Implementation/test verification ongoing; no live database or S3 deployment claim.

## Schemas and original compatibility
- `document-comparison-views.schema.json`: `urn:medical:document-comparison-views:1.0.0#/$defs/Read` and `#/$defs/Recovery`.
- `document-comparison-feature-availability.schema.json`: `urn:medical:document-comparison-feature-availability:1.0.0` root.
- `../comparator/document-structural-comparison-core.schema.json`: `urn:medical:document-structural-comparison-core:1.0.0#/$defs/CoreResult`.
- Existing published `interfaces` CompareRequest and Comparison unchanged. Embedded corresponding schema definitions are copies for standalone client validation; server additionally uses published SchemaCatalogue. CoreResult embedded definition must equal actual core resource on server initialization.

All endpoints authenticated business session, X-Schema-Version: 1.0.0 and X-Context-Revision exact current decimal revision. HTTP success is 200 Envelope {schema_version,request_id,resource_revision,data}, Cache-Control:no-store. No query parameters on these endpoints. Create additionally requires existing CSRF policy and Idempotency-Key. Header presence does not bypass security filter verification. IDs are lowercase UUIDs. No grants created.

## Availability
GET /api/document-comparison-feature-availability
Data: {schema_version,current_context,features:{document_comparison:boolean},maximum_request_bytes:integer|null,maximum_result_bytes:integer|null,maximum_records:integer|null}.
Actual controller registration determines feature flag; configured capacities returned only if enabled; disabled capacities null. Context includes tenant_id,context_revision,auth_epoch,governance_epoch. Availability is deployment metadata, never a permission/health guarantee. Envelope resource_revision=context revision.

## Create (original BX-05)
POST /api/document-comparisons
Original closed body: {document_id,left_revision,right_revision}; NO schema_version body field.
Response data is original Comparison: {comparison_id,comparison_revision:"1",left_id,right_revision,differences:{artifact_id,sha256,schema_ref,coverage},affected_node_ids}. left_id is the selected saved left revision. Both inputs must be actual saved revisions of document_id, fully readable now under each existing DocumentReadService FORMAL/HISTORY policy. document.read is required, no invented comparison.execute action.

Creation is synchronous bounded deterministic execution, then one immutable object write/readback and transactional registration. No asynchronous worker/job implied. Only complete output is registered; unchanged records retained. Config limits can reject large input/result/work/time with CONTEXT_LIMIT/FILE_TOO_LARGE. Unknown object write retains original operation/intent and key. Never start new key automatically.

## Typed result
GET /api/document-comparisons/{comparisonId}
Data Read: {schema_version,current_document_revision:UUID|null,document_head_revision:decimal,left_is_current:boolean,right_is_current:boolean,comparison:original Comparison,result:CoreResult}.
Envelope resource_revision="1" is immutable comparison revision; document_head_revision separately describes current document head. Newer document heads do not rewrite comparison. Both real saved versions and their complete current source rights/grants are checked again; actual output additionally requires artifact.read with DOCUMENT_BODY, same real DOCUMENT owner. No public ObjectRef/storage address.

Full result is read within configured maximum_result_bytes (canonical CoreResult bytes); maximum_records bounds its complete ordered records array. Wrapper/envelope add bounded metadata and duplicate affected-node IDs, so result-byte limit is not an HTTP envelope byte limit. Client total UTF-8 envelope limit is 2 * maximum_result_bytes + 16384, using safe integer arithmetic; count before JSON parse where possible. Browser buffering before JS remains browser-managed. There is NO server records pagination endpoint. Client pagination is local display of a previously fully received and verified result; must retain complete server coverage and not replace it with local page coverage.

Core records include unchanged identities and complete canonical before_json/after_json strings. Core coverage selection_sha256 is input_sha256; delivered_sha256 is complete ordered records canonical hash; complete=true,next_cursor=null. Comparison artifact sha256 is canonical entire CoreResult SHA, affected_node_ids equals core affected_node_ids, and its coverage equals core coverage. Two actual input bodies plus original request and registered limits are recomputed by DocumentStructuralComparisonEngine.verifyAgainstInputs, and compared canonically to actual object result; self-consistent deletion/rehashed truncated records is rejected. Structural effects do not assert medical/regulatory semantics and do not merge anything.

## Read-only original-operation recovery
GET /api/document-comparison-operations
Original key supplied in the unique Idempotency-Key header, never in a URL path. Keys retain original BX-05 visible ASCII contract: 16–128 characters, U+0021 through U+007E inclusive (including /, ?, #).
Data Recovery: {schema_version,state:NOT_FOUND|PROCESSING|COMPLETED,request_body_sha256:hash|null,comparison_id:UUID|null,comparison:Comparison|null}.
NOT_FOUND requires both immutable original-key intent and operation to be absent, and all nullable fields null. An immutable intent with missing/moved operation is CONTENT_MISMATCH, never permission to submit a fresh key. PROCESSING has original request_body_sha256 and comparison_id, comparison=null. COMPLETED adds original Comparison and typed current result access validation. Hash is canonical SHA256 of exact original three-field CompareRequest, NOT operation method/path manifest hash and not mutable local input. Client must compare against its frozen originally submitted body before accepting recovery. Never substitute newly edited input and recalculate a digest as if original submission were unchanged.

Recovery is a distinct DTO, never a Comparison. It checks same actor's original operation and actual immutable intent/start audit/outbox/dependency registry. PROCESSING is unresolved synchronous operation, not evidence a background worker is executing. Read-only recovery makes no object write or retry. Explicit identical-key identical-body retry can only recover the exact previous write intent after attempted=true, never overwrite. Original immutable DB deadline/session/context still bind mutation retry; expiry keeps readable unresolved state but forbids further writes. No abandon/reissue endpoint is implemented.

## Frontend error behavior
Unknown timeout/network outcome retains frozen body+key. Schema/hash/permission failures remain real protocol failures, not invented success. Feature disabled is explicit availability false, never infer empty history/result from 404. Context/session changes clear displayed private content and invalidate old in-flight responses. Recovery across a new session may inspect original own operation after current reads; mutation cannot impersonate original session. No auto rerun/merge/approval. Browser can show local filters/pagination only after complete provenance validation.

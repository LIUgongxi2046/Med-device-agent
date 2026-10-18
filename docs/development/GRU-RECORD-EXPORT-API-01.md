# Local GRU user-record JSON export contract

This is a published local implementation contract for owner-only RECORD_ONLY exports, not a completion claim for formal GR-08, `/api/conversations`, asynchronous workers, model execution, or production readiness. No shared schemas are modified.

## Routes

- `GET /api/gru-conversations/{id}/export-basis`: returns `Basis` after complete authoritative history, fixed document/body, current owner/context and source permission verification.
- `POST /api/gru-conversations/{id}/exports`: takes `Request` plus the existing session/context and idempotency headers. Request is exactly `{schema_version,basis,reason,purpose:"GRU_RECORD_EXPORT",format:"JSON"}`. Returns `Receipt` only after verified external object bytes and atomic persistent completion.
- `GET /api/gru-record-exports/{id}`: returns `View` with immutable receipt and `status:"AVAILABLE"` only after current domain authorization; this does not claim a download occurred.
- `GET /api/gru-record-exports/{id}/content`: bounded complete canonical UTF-8 JSON bytes matching receipt media type, size and SHA. No generic ArtifactRegistry bypass, fake document export, or fake validation artifact. No partial-body success or undocumented range/pagination protocol.
- `GET /api/gru-conversations/{id}/export-operations`: unchanged original `Idempotency-Key` header, read-only `Recovery`; must not create/advance operations or write/retry objects. `NOT_FOUND` means no currently visible matching operation, not proof a timed-out write did not occur. `PROCESSING` means persisted incomplete operation, not an active background worker. `COMPLETED` includes the real typed receipt. Other states never include a receipt. Only NOT_FOUND has null `request_body_sha256`; other states expose the original full canonical Request SHA. A new valid owner session may recover using current permission checks.

## DTOs and content boundary

`Basis` contains schema_version, tenant_id, project_id, conversation_id, owner_id, conversation_revision, document_id, document_revision, body_sha256, scope_snapshot_id, scope_sha256, mode=RECORD_ONLY, execution_state=NOT_EXECUTED, message_count, message_manifest, message_manifest_sha256, complete=true, next_cursor=null, current_context.

`current_context` is exactly tenant_id, context_revision, auth_epoch, governance_epoch. These are public revision bindings, never private grants or authorization evidence.

Manifest entries are exactly message_id, sequence (decimal string), content_sha256. Maximum 500, contiguous sequence strings `2` through `message_count+1`, conversation_revision exactly `message_count+1`. Empty conversations have revision `1`, empty manifest, and its actual canonical-array SHA. Duplicate message IDs, missing/out-of-order sequences, and manifest count/hash mismatch are rejected.

`Document` repeats Basis fields except current_context and adds purpose=GRU_RECORD_EXPORT, format=JSON, messages. Each message contains exactly message_id, sequence, text, intent, execution_state=NOT_EXECUTED, created_at, content_sha256. Intent only QUERY/RETRIEVE/CHECK/PROPOSE_EDIT records the user's declared intent and never implies execution. Service must project text from actual original frozen USER Message.content, created_at from that Message, intent from its verified record, and content_sha256 from the complete original shared Message canonical bytes. The minimal projection cannot independently reconstruct that original SHA. The schema checks identity/order/hash agreement with the manifest; service must prove original message authenticity and projection fidelity.

Exported bytes exclude SourceScope contents, Selection/citations, source bodies, ObjectRef, grant details, session, reason, original request metadata, run/candidate fields and assistant/system messages. Minimal scope hash and immutable snapshot/document IDs bind the context without exporting those source values. All objects are closed.

`Receipt` contains schema_version, export_id, operation_id, conversation_id, conversation_revision, document_id, document_revision, message_manifest_sha256, request_body_sha256, artifact_id, content_sha256, size_bytes (positive decimal string), media_type=application/json, format=JSON, mode=RECORD_ONLY, execution_state=NOT_EXECUTED, created_at. `View` contains schema_version, receipt, status=AVAILABLE, current_context. `Recovery` contains schema_version, conversation_id, idempotency_key, status, request_body_sha256, receipt, current_context.

## Hashes, limits and required service guarantees

All canonicalization uses existing ContentReadAuthorization.canonicalBytes. message_manifest_sha256 hashes exactly the ordered manifest array. scope_sha256 hashes the authoritative complete original shared SourceScope. body_sha256 is the original fixed document body SHA. request_body_sha256 hashes the entire Request. content_sha256 hashes exact exported UTF-8 bytes. Internal operation requestHash additionally binds POST route, context revision, session ID and local/shared schema manifest. These hashes are not interchangeable.

Wrapper hard ceilings: request 256 KiB; canonical content 32 MiB; messages 500. A deployment may impose smaller documented limits and a bounded I/O deadline. These constants do not enable the feature or establish a deployed setting. Oversize must fail whole, never truncate. Strict duplicate JSON member detection and trailing-token rejection apply to request parsing.

Creation requires current real owner, conversation.read, new conversation.export on the fixed DOCUMENT scope for GRU_RECORD_EXPORT, and existing original document/source rights (DOCUMENT_BODY remains their read purpose). Download additionally requires artifact.read for GRU_RECORD_EXPORT and renewed current original domain/source checks. No implicit grant or sharing. The feature remains disabled until the full controlled authorization contract and deployment checks exist.

At first admission rebuild full Basis from verified original evidence; reject stale head/context or mismatching bindings. On same-key replay preserve original Request, operation and exact write intent; a different body must conflict. Attempted unknown writes permit verified read-only recovery, never overwrite. Atomically complete artifact/export/operation/audit/outbox after lineage and permission revalidation. Completed exports remain immutable; later head revisions may be greater only if the entire frozen prefix is still independently valid. Current authorization always applies. Unknown results, ordinary stale errors, and NOT_FOUND never authorize silently generating a new key.

## Validation evidence

Java standalone regression source is included under tests. No Maven/global build was run. Java compilation requires an existing JDK and the repository's prior dependency classpath; absence of a compiler is not a passing test. Python jsonschema structural tests exercise the closed local schema only; Java cross-field/service/storage checks require separate execution and integration acceptance.

## Deployment and implementation status

Published service, controller, policy and both local schemas were compiled in isolation. The domain service has 28 focused regressions using the actual GruService verifier and explicit persistence/object-transport doubles. Local schema has 15 Java assertions. These checks do not establish a real MySQL/S3 deployment.

Both `medical.gru-records.enabled=true` and `medical.gru-record-export.enabled=true` are required for exporter service/controller registration. No actual configuration is changed. Explicit positive non-secret export capacities are `medical.gru-record-export.maximum-bytes` (at most 33554432), `maximum-seconds`, `maximum-nodes`, and `maximum-relationships`. The existing SourceUploadTransport configuration and actual versioned object storage remain required. Authenticated `GET /api/gru-record-export-feature-availability` reports `{schema_version,current_context,features:{gru_record_export:boolean}}` using actual controller registration; this is separate from grants and actual eligible resources.

Read-only recovery uses the original key header, not a URL segment: valid existing keys may contain `/`, `?`, or `#`. An expired write deadline leaves a verifiable PROCESSING record; recovery does not claim a worker is running and cannot reset the original deadline. No automatic retry or key replacement is authorized.

All immutable typed JSON responses use envelope/header revision `1`. The real selected conversation revision is `Basis.conversation_revision` / `Receipt.conversation_revision`; callers must compare it explicitly and never interpret the envelope revision as the current head.

Independent-review fix: after this call performs verified storage I/O, a concurrently completed registration is returned only when its entire stored ObjectRef equals this call’s actual ObjectRef, including object_version. Same-version completion succeeds; differing versions fail closed while preserving the independently valid original receipt and immutable attempt.

The immutable attempt also persists required schema_version, schema_manifest_sha256, classification and created_at without defaults. They are copied from the verified original intent; created_at equals actual attempted_at. Readback verifies all four fields, actor and original deadline, rejecting altered schema, manifest, classification or creation time. This updates only undeployed 070; older migrations and public JSON remain unchanged.

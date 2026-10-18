# Fixed-document technical check API (1.0.0)

## Availability and boundary

Routes exist only when `medical.document-technical-check.enabled=true`. Deployment must explicitly configure positive `maximum-bytes`, `maximum-nodes`, `maximum-table-cells`, `maximum-relationships`, and `maximum-seconds`; there are no implicit capacities or permission grants. Existing application-session authentication and CSRF protection remain required.

All routes require exactly one `X-Schema-Version: 1.0.0` and `X-Context-Revision` header, reject query strings, and return the existing `InterfaceProtocol.Envelope` with `Cache-Control: no-store`, `X-Schema-Version: 1.0.0`, and `X-Resource-Revision: 1`. The envelope revision describes the immutable check receipt, not a guarantee that its currentness remains unchanged. The dedicated reader revalidates access and currentness.

## Create

`POST /api/documents/{documentId}/technical-checks`, `Content-Type: application/json`.

Also requires one `Idempotency-Key` and one `X-CSRF-TOKEN`. The body is the closed local Request definition: `schema_version`, `document_revision` (UUID), `expected_revision` (positive unsigned-64 decimal string), and trimmed nonempty `reason` (maximum 2,000 characters). Caller-supplied tenant, project, check, artifact, job, or manifest identities are rejected. The controller authenticates before reading body bytes and reads at most the configured maximum plus one sentinel byte. Successful creation/replay returns HTTP 201 and a Receipt in `data`.

The service binds the saved body, its actual published schema, internal baseline digest, explicit capacities, and a persisted execution-manifest identity. Receipt includes `check_id`, `job_id`, `operation_id`, fixed document identity, `input_sha256`, verified coverage/findings artifact identities, and the canonical request-body digest. Preserve the original request, context and key for an uncertain same-session outcome; never invent replacement input under the same key. Use the read-only operation lookup to inspect an uncertain outcome before any resend. A new session does not authorize changing or resubmitting the original command.

## Read

`GET /api/document-technical-checks/{checkId}` returns HTTP 200 with the closed Read definition in `data`: `schema_version`, `currentness` (`CURRENT` or `STALE`), `invalidation_reasons`, `receipt`, actual `result`, and actual `coverage`.

CURRENT requires no invalidation reasons; STALE requires at least one. The typed reader verifies stored artifact bytes before returning them. Contract validation binds receipt document/revision, input digest and technical result to the returned result; returned coverage must exactly equal result coverage, and both returned objects must hash to their receipt artifact references. Neither internal baseline content nor object-storage references are exposed.

## Recover an uncertain outcome

`GET /api/documents/{documentId}/technical-check-operations/{key}` uses the same schema/context headers and current application-session authentication. It performs a read-only lookup of the original operation. The closed Recovery response contains `schema_version`, `state` (`NOT_FOUND`, `PROCESSING`, or `COMPLETED`), `request_body_sha256`, `check_id`, `receipt`, `execution_mode: SYNC_REQUEST`, and `overall_review_ready: false`.

NOT_FOUND has null request digest, check ID and receipt. PROCESSING has the original request digest and check ID, with null receipt. COMPLETED has all three and binds the check ID and request digest to the receipt; the service also revalidates typed-read permissions. The response never exposes upload intents or object-storage references. Lookup itself does not write objects, start work, grant access, or provide professional review readiness.

## Meaning of outcomes

The only checker is the stable built-in `STRUCTURE_FIXED_REFERENCES` implementation. It executes the real fixed-body structural/internal-reference validator. `execution_status: COMPLETED` and receipt `technical_execution: COMPLETE` describe execution; `technical_result: PASS|FAIL` describes this limited check. Structural failure produces the fixed `DOCUMENT_STRUCTURE_INVALID` finding. Capacity, content-identity, authorization, or infrastructure failures do not become successful check results.

Every result retains `professional_coverage: UNKNOWN` and `review_ready: false`; every receipt retains `professional_coverage: UNKNOWN` and `overall_review_ready: false`. Coverage means the selected fixed technical input was completely delivered and checked. It does not establish clinical support, regulatory applicability, document adequacy, source authenticity, or professional approval. A technical PASS does not authorize RETURNED to REVIEW_PENDING, signing, or export.

The local schema has separate identities: `schemaDocumentSha256()` hashes the actual local schema file bytes for content bindings; `manifest()` binds that digest, the actual shared body-schema document digest, and the checker identity for artifact registration. ExecutionManifest additionally records the actual compiled engine bytecode digest. It does not fabricate approved RuleVersion identities.

Errors use the existing sanitized identity-error response conventions: schema rejection 422, authentication 401, access denial 403, oversized body 413, missing object/source 404, other domain conflicts 409, unexpected failures 500.

## Frontend feature discovery and required behavior

The staged unconditional `GET /api/document-technical-check-feature-availability` uses the same current session/schema/context headers and a no-store envelope. Its closed data is `{schema_version:"1.0.0",current_context:{tenant_id,context_revision,auth_epoch,governance_epoch},features:{document_technical_check:boolean}}`. The flag comes only from actual conditional controller registration, without instantiating that controller or inspecting credentials. It is deployment availability, not document permission, object-storage health, execution success, or professional readiness. Five isolated tests cover absence/presence, no eager instantiation, default-disabled switches, context mismatch, and closed schema validation.

Frontend creation requires an actual loaded document/revision and expected current document-head revision, a local validated reason, deployment flag, and current `document.check` plus existing document/content read authority. The server derives the actual project and baseline; do not substitute a selected project ID or construct a manifest/job/artifact ID in the browser. Result reading independently requires real `artifact.read` with purpose `DOCUMENT_BODY` for both produced artifacts, in addition to the full source-aware document read. A creator lacking those result-read grants can receive a creation receipt yet cannot read output bytes; do not treat a denied read as a failed creation or create a replacement run.

Preserve original pending body, canonical body digest, context and key across uncertain network outcomes. Compare recovered `request_body_sha256` to the frozen original body, never to a newly edited body. On context/session/permission changes, clear visible data immediately; querying an old operation uses current authorization and does not grant permission to replay it. NOT_FOUND may be shown only after an actual authenticated response. PROCESSING is unresolved; it is neither success nor failure. A stale completed result remains visibly STALE and never becomes the current check merely because its technical result is PASS.

Current recovery limitation: the read-only operation route reports the persisted original operation and performs current document authorization. It does not probe S3 after the execution deadline. Same-command retry within the original valid session/context/deadline may call `recoverVerified` for attempted outputs; it never issues a second write. Once deadline or authority/currentness is lost, unresolved intents remain preserved for controlled operational reconciliation. There is currently no reconciliation/admin endpoint or automatic terminalization. Do not present PROCESSING as an actively running asynchronous worker.

Remaining integration gates: install migration 068, subject-parameter mapper registration and both immutable-table protections; explicitly provision bounded deployment configuration and real existing storage dependencies; perform root aggregate verification and a real MySQL/S3 deployment test. No clinical/regulatory required-check definition, approved professional executor, or full-review transition is supplied by this technical slice.

## Pending ledger verification

Before returning PROCESSING or performing object recovery, the service verifies the actual RUNNING check, system job, execution registry, immutable execution manifest and version registry against the command. Original created/started timestamps bound the deadline to the configured synchronous window. Changes to that configured window fail closed for existing pending commands. The original canonical request, context and session are hash-bound to the operation; current authorization is checked independently. Artifact keys are rederived from tenant/document/check/artifact identity, and content hash, size, media type and schema are verified before storage access. No new public fields expose the private command or authorization metadata.

Independent regression tests include deadline and object-key changes, request/body-hash rewrite, missing or inconsistent pending ledger rows, missing manifest version, current permission revocation, and read-only inspection after expiry. These isolated tests use explicit database/storage doubles; they do not establish real MySQL/S3 execution.

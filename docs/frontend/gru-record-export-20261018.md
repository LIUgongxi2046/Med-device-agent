# 070 Gru USER-record export frontend

2026-10-18. Published and integrated after root approval. No global build, Maven, browser/backend/database/S3 acceptance performed by this frontend task. The actual backend public contract is docs/development/GRU-RECORD-EXPORT-API-01.md; both frozen schemas are registered from the actual backend resources.

## Published files and integration

- api/gru-record-export.ts -> web/src/api/medical/gru-record-export.ts
- api/gru-export-json.ts -> web/src/api/medical/gru-export-json.ts
- components/gru-record-export.vue -> web/src/views/medical/gru-record-export.vue
- Tests: web/tests/gru-record-export/export.test.mjs, transport.test.mjs and editor-integration.test.mjs use the formal API/components/resources.
- protocol.ts registers the two actual backend schemas without modifying them.
- request.js routes exact lowercase-UUID export paths to raw JSON or raw ArrayBuffer, preserving medical session/CSRF/error handling and unrelated paths. No generic arbitrary URL fallback.
- document-gru-records.vue mounts the component only for actual view from the existing Gru reader, with session/selected/active and disabled=busy||pending. It does not use the browser's displayed message page as export content.

Only the narrow staged patch changes were applied to shared integration files. Existing comparison transport and user/global error strategies remain unchanged. Root owns aggregate validation.

## Behavior and genuine boundaries

The component independently reads actual feature availability, then checks a distinct conversation.export action summary alongside conversation.read. Read/use never implies export. The backend checks current exact DOCUMENT purpose GRU_RECORD_EXPORT and original document/source rights; the frontend action summary does not contain purpose and is not treated as final authority. Download separately requires matching artifact.read summary and actual backend ARTIFACT purpose authorization.

A real selected conversation's complete fixed Basis is fetched. Identity, project/document/scope, owner, actual revision and all message IDs/sequences/hashes must match. Manifest count/order/contiguous sequences and canonical-array SHA are checked, including true zero-message revision 1. A valid basis whose sole mismatch is a newer conversation revision yields local ACTION_STALE, prompting the user to reread the conversation. It is not silently treated as the old selection. Wrong identity/reference/manifest still fails closed.

The user supplies a local-validated reason and explicitly acknowledges GRU_RECORD_EXPORT / RECORD_ONLY / NOT_EXECUTED purpose. Before dispatch the complete basis/request, original key, canonical body hash and integrity digest are saved. Network uncertainty, PROCESSING, NOT_FOUND, scope changes and download errors retain that original request. No automatic replay, S3 retry, new key or abandoned-operation inference. Cross-session recovery uses the same actor's original key in Idempotency-Key header at the constant per-conversation export-operations path, with current backend reads. Full printable ASCII keys remain header values, never path segments.

A create response is a real Receipt, separate from permission to download. Download first verifies current typed View/receipt, then fetches the actual bytes. Before constructing a Blob, it checks strict UTF-8, actual byte length, Content-Length, X-Content-SHA256, expected media type/cache/schema/revision/nosniff/request metadata, the complete byte SHA, closed Document schema, canonical representation, exact original basis and complete ordered message-manifest binding. The original full Message content_sha256 is not recalculated from projected text, because that projection cannot reconstruct the original shared Message. Server evidence verification remains authority for original message authenticity.

The file contains only the allowed minimal fixed context and USER text/intent/time/hash. CHECK and PROPOSE_EDIT are recorded user intentions, not completed checks or model actions. No source body, private SourceScope, selection/citation object, grants, session, request reason, model answer or draft body is exported. No draft mutation or body-read event is emitted.

Verified bytes create an ObjectURL only after current identity/generation checks and an explicit user download action. The link uses an internally generated UUID-based filename. URLs are revoked on replacement, failure, scope/visibility change, unmount, or a 30-second cleanup timer. UI says download was initiated, not that the browser saved the file. After download, the user may explicitly confirm file preservation and start another export: the completed original request+receipt is archived in sessionStorage before the active pending key is removed. Unknown requests cannot enter that path. This is a local completed receipt archive, not a server export-history endpoint.

## Capacity and security events

Raw metadata envelopes have an explicit 1 MiB client UTF-8 ceiling before JSON parsing; file bytes have a 32 MiB hard ceiling (backend deployments may be smaller). Client capacity rejection throws MedicalFailure locally, without dispatching medical-failure or clearing valid session/pending state. Non-text/malformed/schema/hash/identity responses retain strict failure behavior. Browser networking may buffer data before these checks; there is no browser streaming/backpressure or memory guarantee. Nothing truncates a successful export.

## Verification

Run from repository root:

node --test web/tests/gru-record-export/*.test.mjs web/tests/saved-document-comparison/*.test.mjs web/tests/protocol-schemas/lazy-compilation.test.mjs tests/frontend/gru-records-checks.mjs

93/93 focused tests passed, zero failures/skips: 27 new export tests plus comparison/protocol/existing Gru regressions. Log: .implementation-work/logs/part19-gru-export-frontend.log. Tests execute formal adapters and backend schemas, actual compiled component setup/template and actual request interceptor. The mounted editor/Gru/export integration verifies real selected conversation changes, complete-basis fetching independent of displayed message page, original pending restoration, global write locks, local capacity errors, and dirty body preservation. Existing Gru setup tests only add a stub for the new child import; their original assertions remain unchanged.

Coverage includes full and empty manifests, real fixed head change, read/use versus export and artifact permission separation, local reason errors, original request integrity, ASCII recovery headers, real bytes/length/SHA/schema and private-payload rejection, pre-parse capacity refusal without global events, purpose confirmation, unknown outcomes, successful download URL lifecycle, late blob suppression, identity/old-finally lock ownership, pending/receipt preservation, and explicit completed archive before a new request.

Fixtures are test-only and do not establish a real exported medical record. Full global typecheck/build and live backend/browser tests remain root-owned and separately required. No service or permission was enabled.

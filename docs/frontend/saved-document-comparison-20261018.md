# Fixed saved-version comparison frontend delivery

2026-10-18: published and integrated after root approval. Backend contract is published at docs/backend/document-comparison-api-20261018.md. Public schemas used by tests are the actual resources in business/medical-persistence/src/main/resources.

## Publication map

- api/saved-document-comparison.ts → web/src/api/medical/saved-document-comparison.ts
- api/document-comparison-display.ts → web/src/api/medical/document-comparison-display.ts
- api/document-comparison-json.ts → web/src/api/medical/document-comparison-json.ts
- components/saved-document-comparison.vue → web/src/views/medical/saved-document-comparison.vue
- tests/comparison.test.mjs → web/tests/saved-document-comparison/comparison.test.mjs (dual-layout source resolution supported)

protocol.ts registers the three frozen schemas; request.js routes only exact comparison endpoints through the dedicated bounded reader; the directory supports an optional selectionOnly label while preserving default fixed-body reading. Existing unrelated endpoint behavior and schemas are unchanged. No changes to ordinary editor conflict comparison.

document-editor.vue mounts the panel with session, saved snapshot, active && !historyVisible, and disabled covering editor writing/loading/comparing/annotationBusy/historyLoading/global lock, only when a saved snapshot exists. Component has no body/draft mutation or body-load event; it uses the directory's explicit selectionOnly mode solely to pick two fixed saved IDs. Current unsaved draft is visibly excluded.

## Actual APIs

- authenticated GET /api/document-comparison-feature-availability → actual feature and nullable capacities
- POST /api/document-comparisons → original three-field CompareRequest and Comparison, HTTP 200
- GET /api/document-comparisons/{comparisonId} → complete bounded Read/CoreResult
- GET /api/document-comparison-operations + Idempotency-Key header → separate read-only Recovery, original body hash

Original request is stored in sessionStorage before dispatch, with original actor/tenant/context/key/body/body hash and integrity digest. Context changes clear private UI but retain unresolved original records. Recovery may use the same actor's new current context, with current backend source/read authority. NOT_FOUND/PROCESSING retain original records and never automatically create a new key. Result read failure also keeps the original request. Only explicit finish after successful complete result read clears that completed pending record. No abandon endpoint or persistent browser secret is introduced.

POST deliberately does not forward AbortSignal after dispatch: aborting UI cannot establish that a write did not commit. Generation guards ignore late responses; the write owner releases only its own still-owned global lock. If an external reset releases the lock, the old operation cannot later clear another writer's lock.

## Reading and completeness

UI shows actual category/status, readable left/right values, exact text changes, order and reference changes; no medical meaning, numeric conversion or merge/adoption is inferred. Local category/status filters and ten-record pages retain the original complete total/coverage. A filtered empty view is not an empty complete comparison.

Client validates actual closed schemas, request/receipt/side identities, current flags, artifact/core/record/input/snippet hashes, unique record identity, status/null consistency and text code-point quote digest. These are self-consistency checks. Server typed read supplies complete provenance by independently recomputing from both actual authorized fixed bodies. Browser does not claim independently validated source completeness from digest checks or load every history body's bytes.

The narrow transport helper rejects raw text above an explicit 8 MiB client UTF-8 ceiling before JSON parsing using a local MedicalFailure(CONTEXT_LIMIT), without dispatching medical-failure or clearing the authenticated session. Original pending requests and editor drafts remain intact. Non-text, malformed JSON and structural corruption retain the existing fail-closed behavior. It then uses the existing duplicate-field/safe-number JSON validator. This client safety cap is independent of deployment limits; a larger server-supported result can be explicitly rejected by this client. The adapter also checks canonical CoreResult bytes and record count against actual availability capacities. Browser networking may buffer bytes before the transform runs; no browser-level streaming/backpressure guarantee is claimed. This is not a generic URL fallback.

## Verification

Run from repository root: node --test web/tests/saved-document-comparison/*.test.mjs web/tests/document-revision-directory/*.test.mjs web/tests/protocol-schemas/lazy-compilation.test.mjs tests/frontend/document-history-entry-checks.mjs

55/55 tests passed, zero failures/skips, including 26 comparison tests and 29 history/protocol regressions. Log: .implementation-work/logs/part18-fixed-comparison-frontend.log. Actual parent/comparison/directory mounts verify selection does not read body or overwrite a dirty draft; actual request interceptor tests verify exact comparison URL routing and unchanged unrelated URL transforms. Actual published schemas, actual compiled component setup and template are exercised. Checks cover retained original keys/hash, read-only recovery, strict malformed responses, complete result binding, capacity rejection before parsing, category/local pages, success and denied reads, snapshot preservation, identity changes, old finally and independent write-lock ownership, unknown outcome and unmount.

No full frontend typecheck/build, Maven, live browser/backend/database/S3 validation was run by this frontend task. The Java comparator's independent actual-input recomputation tests are backend-owned; test fixtures here do not establish live medical results.

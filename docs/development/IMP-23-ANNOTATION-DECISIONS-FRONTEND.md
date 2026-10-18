# IMP-23 annotation human decisions: frontend additive slice

## Implemented

The saved-document annotation panel supports explicit human RESOLVE and REOPEN decisions through the versioned additive `/api/annotation-threads/{id}/decisions` API. It displays the complete immutable decision history, including original reason, actor, time, prior/new state and prior/new annotation revision. It does not implement REANCHOR, document confirmation/signing, task transitions, or the frozen RV-06 Record/ArtifactRef contract.

- OPEN → RESOLVED and RESOLVED → OPEN only. Replies retain state and are never treated as a decision.
- Decision authority is explicitly `review.decide`, with the existing matching DOCUMENT, PROJECT or ENTERPRISE scope interpretation. `annotation.write` does not imply review authority. `annotation.read` is required to read history. Server permission/dependency enforcement remains authoritative.
- Fresh decisions require the current unsigned saved fixed body, no dirty body/field drafts, matching document-head basis, matching reply/history annotation revision and status, and CURRENT_VERSION location. RETURNED and REANCHOR_REQUIRED have no fabricated action.
- The human reason is required and preserves exact Unicode/whitespace. Client and schema reject blank, malformed Unicode, control characters, unknown properties, unsupported actions and invalid revisions; limits are 16,000 code points and 65,536 request bytes. Complete history is bounded to 100 decisions, with a visible capacity explanation.
- Requests preserve separate annotation CAS (`expected_revision`) and document-head CAS (`expected_document_revision`). Both decisions advance annotation/head/issue watermark by one and preserve evidence watermark, as checked against the original receipt.

## Recovery and integrity

The original shared pending-write slot now admits `RV-06-DECISION-1` alongside unchanged create/reply operation identities. Its additional decision metadata consists only of decision kind and document-head CAS. Raw reasons, comments, selections and document bodies never enter sessionStorage; drafts remain memory-only and are discarded on context invalidation.

Recovery preserves the exact original operation name, idempotency key, fixed-body digest, request digest, annotation CAS, document-head CAS and captured status. A changed input cannot replace an unresolved create, reply or decision. After memory loss, the user re-enters the exact reason. A mistyped reconstruction is validated before retaining it, so the user can correct it without being trapped behind an invalid cached request.

Submission freezes the request, hashes/verifies it asynchronously, then rechecks cancellation, context and explicit write authority before POST. A dirty/history/current/signature change aborts pending panel work. Late results after cancellation or context switch cannot mutate the active panel. Historical/signed/dirty recovery only retries an already registered original operation; NOT_FOUND and EXPIRED do not mint a replacement operation.

Settlement requires a verified receipt, matching COMPLETED operation, full annotation reread and the exact immutable decision in complete history. History uniqueness and order use annotation revision, not timestamp or UUID ordering; equal timestamps and clock adjustments are permitted. Reply-induced revision gaps preserve the latest decision status. Later reopen/resolve operations do not invalidate an earlier exact receipt. Pending identity is cleared only after these checks, then the existing abort-aware parent refresh holds the shared writing lock and preserves local editing/undo/conflict behavior.

## Verification

Verified locally on 2026-10-18 with existing dependencies (all commands exited 0):

- `node tests/unit/annotation-frontend-checks.mjs`: 20 groups, retaining the original 13 plus 7 decision groups. These are pure-domain, mocked-transport and DOM-free Vue SFC setup checks. They cover permission denial, state/CAS gates, exact reason validation, repeated reopen, history corruption/order, no raw text in storage, cancellation/context/authority changes during hashing, late parent refresh, uncertain operation recovery and corrected reload reconstruction.
- `node tests/unit/document-selection-checks.mjs`: 9 existing Selection regression groups passed.
- `npm run typecheck:medical` in `web`: passed.
- `npm run build:prod` in `web`: passed, 3,793 modules; existing unset VITE_APP_TITLE and large-chunk warnings remain.

No HTTP server, browser, sockets, database, object storage endpoint, model/provider, deployment or push is exercised. Visual appearance, real end-to-end authorization, persistence and production behavior are not claimed by these frontend checks.

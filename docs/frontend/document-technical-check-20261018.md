# 068 technical structure check frontend (staged)

## Publish map

Copy only these files, preserving relative paths under the repository:
- web/src/api/medical/document-technical-check-types.ts
- web/src/api/medical/document-technical-check.ts
- web/src/views/medical/document-technical-check.vue
- tests/frontend/document-technical-check-checks.mjs
- docs/frontend/document-technical-check-20261018.md

No production source, shared protocol, route, editor, build output or backend was changed by this frontend slice.

## Parent integration

Component props:
- session: MedicalSession
- snapshot: DocumentSnapshot, from an actual fixed document read
- active: boolean
- dirty: boolean
- isCurrent: boolean, from actual currentSnapshot.document_revision comparison, not a guessed status

Event: completed(receipt: TechnicalReceipt). This signals a verified technical receipt only. It fires after owned write lock release and loading completion. It must not advance the task or mark professional review ready. Parent rereads, if any, remain actual authorized reads.

Suggested editor mount with saved body available, active && !historyVisible, dirty=hasUnsaved, and the existing currentSnapshot-to-saved version comparison. Default UI is collapsed and does not fetch results or create automatically.

Root must register the exact staged document-technical-check schema and all definitions, plus the document-technical-check-feature-availability root/definitions. Feature availability uses the unconditional real controller-registration endpoint, not a guessed 404.

## Workflow and boundaries

The user sees the limited structural/internal-reference scope and UNKNOWN professional coverage before creation. Current document.read and document.check action summaries, actual server gate, current fixed body, no dirty draft and parent lock are required locally. Server remains the authority for complete source/document rights and actual execution.

A local reason/schema problem preserves input and does not invoke global failure. A complete pending command (actor, tenant/context, actual document/project/body SHA, original key/body and integrity digest) is stored before POST. Storage failure sends no write. An uncertain outcome never resets the original key or creates a replacement request. Only explicit read-only recovery is exposed; no automatic replay.

Recovery uses current authorization across same-owner context changes. NOT_FOUND is inconclusive. PROCESSING means an unresolved synchronous operation record, not an active background worker. Full original body digest is matched against the server response, independently of the local pending integrity digest. A completed pending remains recoverable until the user explicitly chooses to begin another check; unknown pending cannot be cleared via that control.

A real creation receipt is independent of output-read rights. The dedicated result GET must revalidate both actual artifact grants and document/source access. Read denial preserves the successful receipt and never creates a replacement. The adapter verifies fixed body/project/document/tenant/input bindings, exact receipt, result/coverage canonical digests, and real currentness. STALE remains visible even for technical PASS.

Current backend Result.findings has maxItems=1, coverage.complete=true and next_cursor=null. There is no findings or history pagination endpoint; the controller rejects query strings. UI therefore displays the actual complete 0/1 findings without invented pagination. A larger server-side listing requires a new real contract.

## Focused validation

From repository root, while staged:

STAGED_FRONTEND_ROOT=/tmp/stage16-document-check/frontend TECHNICAL_SCHEMA_ROOT=/tmp/stage16-document-check node --test --test-timeout=15000 /tmp/stage16-document-check/frontend/tests/frontend/document-technical-check-checks.mjs

After publication:

node --test --test-timeout=15000 tests/frontend/document-technical-check-checks.mjs

18/18 tests pass with actual compiled SFC setup and real frozen schema validation. Coverage includes disabled gate, local input errors, permission/currentness/dirty/parent-lock guards, storage failure, unknown/NOT_FOUND/PROCESSING preservation, true result hash verification, mismatched original body, cross-context recovery, foreign actor rejection, late write/unmount lock ownership, artifact-read denial, STALE PASS, body-hash mismatch, missing read grant, modified body with recomputed local digest, and same-object actor change clearing old results.

No browser, real MySQL/S3, aggregate typecheck, build, or Maven result is claimed.

## Published integration

After part15 archive release, the five mapped files were published. The backend owner confirmed both public schemas frozen; their unchanged staged copies are now in business/medical-persistence/src/main/resources. protocol.ts registers both schema documents, all definitions, and the availability root reference.

document-editor mounts the technical panel only within actual saved/body content, passes saved as the fixed snapshot, hasUnsaved as dirty, actual currentSnapshot comparison as isCurrent, and disables the panel during history view or parent loading/writing/annotation/comparison activity. No completed-event handler changes the task, current document, draft, or review stage.

Additional test file: tests/frontend/document-technical-check-mount-checks.mjs. It mounts the actual compiled editor and actual technical panel together using Vue's custom renderer with doubled API/storage boundaries. Three tests verify fixed prop wiring and disabled availability, no body replacement/review transition on completed, and unmount on inactive parent. This is actual component mounting, not a browser rendering or pixel test.

Published combined command:
node --test --test-timeout=15000 tests/frontend/document-technical-check-checks.mjs tests/frontend/document-technical-check-mount-checks.mjs tests/frontend/document-history-entry-checks.mjs tests/frontend/document-review-return-checks.mjs tests/frontend/document-revision-assignment-queue-checks.mjs

Result: 60/60 pass (18 technical API/setup, 3 parent-child mounting, 39 prior history/return/queue checks), 2.23 seconds. git diff --check passes. Aggregate typecheck/build/Maven remains root-owned and is not claimed here.

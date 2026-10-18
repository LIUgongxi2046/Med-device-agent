# Gru fixed-document record-only frontend

## Scope

`document-gru-records.vue` is an initially collapsed section for the existing saved-document workspace. Root integrates its session, saved snapshot, active/dirty state and real saved-body selection. The component does not modify document drafts, annotations or reanchors. It uses existing Element controls, 16/24px spacing, 8px radius, border/text/feedback colors and the 650px compact breakpoint from `ui-delivery/tokens.json`.

The first slice persists USER messages with `RECORD_ONLY` and displays `NOT_EXECUTED`. It does not produce an assistant answer, execution, run, candidate, approval or completed review. Saving a persistence operation is not execution completion.

## Actual interfaces

- Authenticated `GET /api/gru-feature-availability` must return the current context and `features.gru_records=true` before business calls. Disabled and failed availability are distinguished. This is not a permission grant.
- `GET /api/gru-conversations/context?document_id=&document_revision=` obtains the actual fixed document's project, source snapshot and full SourceScope. The page does not invent a snapshot or rebuild today's source set. Context is read-only and tied to the saved document revision.
- `GET /api/gru-conversations?project_id=` supplies only actual owner-visible conversations; the page filters by current document.
- `POST /api/gru-conversations` creates a fixed context with the explicit reason.
- `GET /api/gru-conversations/{id}?page=&page_size=25&dataset_watermark=` reads actual message pages. Old page detail is cleared before paging. No total-history scan.
- `POST /api/gru-conversations/{id}/messages` stores the exact user text and optionally the actual saved-body selection. Dirty/different-revision documents cannot append from this panel.
- Unknown write: original key, full original request, original context and digest remain in actor/tenant/document-scoped session storage. Explicit lookup uses `GRU-RECORD-CREATE` or `GRU-RECORD-APPEND`, then the generic operation read, then `GET /api/gru-conversations/{conversationId}/receipts/{operationId}` and fixed conversation read. No automatic retry or new key. Explicit replay is available only after NOT_FOUND, with original context and user confirmation. NOT_FOUND is not proof that no write occurred.

Current owner, conversation.read/use, document/source permissions and source rights are checked by the backend. Frontend action-summary gating supplements that verification and never synthesizes grants. Requests/responses use strict local schema plus frozen SourceScope/Selection/Message schemas. Invalid local reason/text reports issues without clearing session; damaged stored requests, wrong scope/response/operation identities fail closed.

Write-lock ownership survives reset/unmount until the outstanding request settles. Generation, current session/context, active tab and visibility checks prevent late responses from reappearing. Unknown requests remain available after refresh or context changes; replay is prohibited across contexts.

## Verification

`node --test tests/frontend/gru-records-checks.mjs` executes the actual SFC setup and API adapter with strict real schemas and mocked transport. It covers default-disabled gating, true fixed-context reading, invalid local input, retained unknown writes, real receipt recovery, no automatic retry, dirty/old-revision blocking, tampered retained data, permission absence and late write completion after unmount. The template is compiled and the default collapsed structure checked. Final run: 17/17 passed (1.81 seconds). Additional cases verify that an existing parent write lock is not stolen or released, original user text is retained exactly, parent snapshot/selection/dirty state is unchanged, paging clears old detail, and a wrong typed operation receipt fails closed. A source-level parent integration assertion supplements these runtime checks; it is not a parent browser test.

This is source/runtime-unit verification, not browser, pixel, live HTTP or database acceptance. Root owns aggregate typecheck/build and document-editor integration. Full model execution, conversational AI response, cancellation and other public Gru workflows remain outside this slice.

## Recovery binding hardening

Creation context reads and creation require separate `conversation.read` and `conversation.use` grants. A use-only grant cannot trigger context, create, or create replay from setup. Receipt schema now requires server `request_body_sha256`; both direct write and typed recovery compare this with the entire frozen original request's canonical SHA-256 before unlocking. Recomputing a local pending digest is insufficient to substitute a different request under an old key. Regression cases change CREATE reason and APPEND text/selection/intent/reason individually while retaining the old key and recomputing the local digest; all must reject the original server receipt. An unchanged original request can recover across a same-actor session/context change. Focused suite now 24 cases.

The authenticated read-only `POST /api/gru-conversations/{conversationId}/append-recovery` closes the stale-append exit. It carries the original key and complete original Append in its body, current schema/context/CSRF headers and no Idempotency-Key header. `FOUND` requires the matching original receipt. `NOT_APPLIED_STALE` requires exact actor, current context, conversation, key, complete-body hash and expected revision, with a strictly newer current revision. `UNKNOWN`, ordinary NOT_FOUND, or an HTTP stale error never permits clearing pending.

After a verified NOT_APPLIED_STALE response the user explicitly acknowledges saving the old proof and draft. The page archives both before ending pending, restores exact text/reason/intent, clears old selection consent, and performs a fresh read. Only a later explicit save creates a new key. Archive download is available; refresh restores its draft and historical proof but never reuses it as a current unlock proof. Context/identity changes require a new authorized recovery read. Final focused suite: 34/34 passed (2.97 seconds), including FOUND, UNKNOWN, mismatched proofs, unacknowledged proof, context change, restored archive and explicit fresh-key submission.

Additional archive failure regressions: quota/storage failure preserves the original pending/key/draft; export failure resets confirmation and blocks ending until export can be retried. Final focused suite 36/36 passed (3.11 seconds).

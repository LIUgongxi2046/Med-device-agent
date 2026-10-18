# Independent document return-for-revision UI

Published after the stage-14 archive release. This replaces the earlier staged formal ReviewBatch frontend plan; that old route was not published.

## Real workflow

`document-review-return.vue` is an initially collapsed saved-document section. Root supplies the real session, DocumentSnapshot, active and dirty states, and optionally the actual task ID already read by the workbench. The panel leaves saved-body reading, selection, drafts, annotations and reanchors untouched.

The independent command is labeled 文稿退回修改. It can request missing material without a check producer, but does not construct a formal ReviewBatch or fabricate check IDs. It never presents review confirmation, approval, signing, completed checks, or resubmission. Server `formal_review_available=false` and `formal_check_currentness=NOT_VERIFIED` are preserved. Checks are informational records only.

The current fixed document must be unsigned and the actual task must be REVIEW_PENDING. The frontend obeys true `return_available`, all returned blockers and the fixed body hash/identity/current context; it does not derive readiness from check status. The current reason and assignee are explicit; edits revoke prior confirmation. Assignees come only from the backend's exact existing-task directory. Names are not supplied by this domain, so the control clearly labels real member account IDs rather than inventing names.

## Interfaces

- Authenticated `GET /api/document-review-availability`: current context and actual default-disabled deployment flag.
- Fixed-document headers `X-Schema-Version`, `X-Context-Revision`, `X-Document-Revision`; no query parameters.
- `GET /api/documents/{id}/revision-return-basis`, `/revision-return-history`, `/revision-return-assignees`, `/revision-return-assignments`.
- `POST /api/documents/{id}/revision-returns` with original Idempotency-Key and CSRF. Body is exactly `{document_revision,expected_revision,expected_task_revision,reason,assignee_id,basis}`. HTTP 200 receipt is checked against the complete original basis, reason, assignee, actor, document, task and revision increments, including canonical basis/decision digests. The backend request_sha256 incorporates schema manifests; it is not misrepresented as a plain body hash.
- `GET /api/documents/{id}/revision-return-operations` with original key and fixed document header recovers a real receipt. Null is NOT_FOUND and never clears or automatically replays pending.

Unknown writes retain full original request/key/context/digest under actor+tenant+document session storage. Current-context explicit replay, cross-context same-actor read recovery, owned write locks, stale generation/hidden/unmount guards remain enforced. Successful persistence emits `returned` for root to refresh actual document/task state; the receipt is labeled historical result, not a claimed latest task state.

`document-review-assignments.vue` is a separate read-only renderer of actual assignment rows. OPEN, REVISION_AVAILABLE and INACTIVE are distinct; neither a new revision nor inactivity means completion. `resubmission_available=false` is explained with the missing current-check verifier; no resubmission or completion action exists. Assignment reading requires a real known task binding and current task.read/work_item.read permissions. A reader without review.decide can read permitted history; no grant is synthesized.

## Validation

Run from repo root:
`node --test tests/frontend/document-review-return-checks.mjs`

Actual SFC setup and API adapter execute against the final production schema with mocked transport. Cases cover feature disabled, invalid task state, no-check independent command, exact original CAS/body, local invalid opinion, confirmation invalidation, unknown write/no automatic retry, cross-actor denial, same-actor cross-context recovery, parent lock/dirty guards, late write completion, and changed-version states without completion actions. Both Vue templates compile. This is not browser/pixel, live HTTP, database or full-build acceptance.

Root must register all defs from schema `$id=urn:medical:document-return-for-revision:1.0.0`; source filename remains `document-review-return.schema.json`. Parent document/workbench routing and refresh are root-owned. The published tests use repository-root imports and the production schema.

Final published focused run: 15/15 passed (1.64 seconds). Additional cases verify successful receipt refresh events without parent-body mutation, absence of task work-item permission causing zero assignment requests, and a changed opinion with a recomputed response digest still failing original-request binding.

## Published queue and parent refresh integration

The workbench component is `web/src/views/medical/document-revision-assignment-queue.vue`, with props `session: MedicalSession` and `active: boolean`, no events. It uses the actual review-return availability endpoint, finishes availability loading, then reads the first queue page once. Additional pages require an explicit click; filtered empty pages can retain a next cursor. Expiry clears the current page and asks for manual first-page refresh, without scanning or retrying.

The adapter is `web/src/api/medical/document-revision-assignment-queue.ts`. Register all definitions of `urn:medical:document-revision-assignment-queue:1.0.0` in shared protocol. The endpoint is GET `/api/my/document-revision-assignments?limit=10&cursor=<server UUID>`. Each displayed row comes from a strictly validated, hashed response for the current actor/context; navigation uses its real project/task/document IDs. “打开当前撰写工作区” opens the current body and explicitly distinguishes it from the original fixed revision. There is no completion, formal review, or resubmission action.

Backend inspection confirmed current identity validation in the controller and EnterpriseAccess admission plus current-context comparison before cursor resolution in DocumentRevisionAssignmentQueueService. The adapter handles only a fully validated HTTP 409 CURSOR_EXPIRED response locally: valid shared Error schema, matching schema/request headers, no-store, null operation/resource revision, empty actions/details and nonretryable. Other errors, malformed error bodies and changed context retain fail-closed behavior.

The return panel now emits `returned` only after releasing its owned write lock, or after read-recovery loading ends. Parent rereads can therefore run immediately; the old operation has no remaining finally block that could release a new lock obtained by the parent. Read-only recovery does not require review.decide; writes and explicit replays still do.

Focused acceptance command:
`node --test --test-timeout=15000 tests/frontend/document-review-return-checks.mjs tests/frontend/document-revision-assignment-queue-checks.mjs`

Result: 31/31 passing (17 return panel/API and 14 queue tests), including compiled SFC setup runtime, no-check independent return, parent refresh/lock order, read-only recovery, cursor paging, genuine expiry without logout, forged expiry/header/schema/request/revision failures, changed context, foreign assignee, and late unmounted response. `git diff --check` passed. This is source/offline runtime verification, not browser or pixel acceptance.

## Fixed-version entry integration

The workbench now mounts the queue after ordinary waiting items and before key projects, only with a real current enterprise context, passing routeActive. Queue rows retain the current-writing link and additionally link to the same existing workspace with history_revision equal to the verified decision.document_revision.

task-detail passes this untrusted query through to document-editor. workspaceHistoryRevision accepts exactly one UUID string (arrays, null, empty, whitespace and malformed values produce local feedback). The editor displays an explicit “读取退回原固定版本” button; it never fetches history automatically because a query exists. The click uses the existing readSavedDocumentRevision, preserving all fixed-source permission, response and digest checks. Current body, draft, main annotation selection and unsaved inputs remain unchanged.

History display and history selection are cleared before each attempted read. Query changes synchronously abort and invalidate the history-only request without initiating another read. Old/unmounted results cannot publish a dialog. Invalid query/manual input is local; server rejection still propagates through the original strict adapter.

Integration test command:
node --test --test-timeout=15000 tests/frontend/document-history-entry-checks.mjs tests/frontend/document-review-return-checks.mjs tests/frontend/document-revision-assignment-queue-checks.mjs

This adds real compiled editor setup tests plus compilation checks for all four touched templates. No browser visual acceptance or aggregate build/typecheck is claimed.

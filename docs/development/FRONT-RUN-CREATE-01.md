# First-run task launch frontend

2026-10-18 frontend implementation. No backend mutation, live run creation, provider execution, or production acceptance was performed.

## Verified gap and scope

`RunCreateController` already exposes RN-01 `POST /api/tasks/{taskId}/runs`; the frontend previously only read and cancelled existing runs. `task-run-create.vue` now plugs into the configuration stage of `task-detail.vue`. `run-create.ts` consumes the authoritative closed `run-create.schema.json` using its own validator, without global schema/router changes.

The existing task-creation Agent release field still requires a fixed release identifier: no real published-release discovery controller was found. Likewise RN-05 later attempts/continue are not exposed by a controller in this slice. Neither interface is fabricated; the new panel is explicitly first-run only.

## Request and recovery

- Task must be READY, have a fixed confirmed plan and source snapshot, have no missing inputs or active runs, and have valid record CAS. `task.run` and `run.read` are separate gates.
- Actual budget accounts come from the existing paginated list. Only enterprise/current-project records are selectable. Before confirmation, the current task and exact selected budget account are reread; a changed approval or task record requires a fresh selection. The dialog displays immutable task/plan/snapshot/budget IDs, CAS, budget total, used/reserved amount, currency and deadline. Server admission remains authoritative for balance, currency, policy, source rights and providers.
- SessionStorage contains only actor/tenant/project/task/budget identifiers, original idempotency key, exact fixed-reference request and its digest. No customer prose or credentials are persisted.
- Submission uses current context, CSRF and original key. HTTP 202 and QUEUED are validated as admission, never execution completion. Receipt fields must match all fixed references and operation/target identity.
- RN-01 lookup precedes recovery; any returned run is read via the existing run adapter and bound to the original task revision and budget account/revision. PROCESSING retains pending metadata. NOT_FOUND and EXPIRED retain it too; only NOT_FOUND may open an exact-input retry confirmation. Unknown results cannot generate a new key. Terminal operation metadata is removed only after the actual run is verified.
- Revoked execute/read permission does not delete unknown operation metadata. Identity/visibility/unmount invalidation clears UI state and cancels reads, while outstanding writes remain unknown until queried.
- Existing run-detail handles the actual stream/cancellation; no synthetic running/success status is added.

## Verification

`node tests/unit/run-create-checks.mjs` passes: readiness, missing plan/snapshot/active-run rejection, CAS ceiling, action authority, exact idempotency metadata, revoked-permission metadata retention, CSRF/context/key headers, 202 fixed-reference checks, lookup states, wrong-budget rejection, verified terminal cleanup, and compilation of the new and containing Vue components. All transport/records are isolated synthetic fixtures, not production records or live acceptance.

Coordinated full frontend typecheck/build and browser verification remain owned by the root integration task.

## Async component review

The focused suite now executes the actual Vue component setup with a custom renderer and controlled asynchronous dependencies. It covers task revision changes before/after confirmation, changed budget approval, two real picker page transitions, identity switches during preflight and after POST dispatch, stale-read finally versus a newer generation, unknown POST outcomes retaining the original key, blocking retries until NOT_FOUND, external write-lock release/reacquisition, and emitting the accepted event only after the component releases its own write lock.

Read completion flags are generation-bound. The write lock tracks local ownership and invalidates ownership if another actor clears the shared flag; its finally cannot release a subsequently acquired lock. The parent refresh event was moved after this component's lock release so the existing parent reload guard permits the refresh.

POST intentionally does not carry the navigation AbortSignal after its final pre-dispatch abort check, matching the existing immutable configuration-write approach. Aborting a client connection cannot establish rollback of RN-01. Navigation/identity invalidation stops reads and discards stale UI updates while retaining the pending request key; it does not claim to cancel server execution. Scripts and styles have been expanded for reviewability.

Missing budget selection is now checked locally before even reading current task data. An actual setup test verifies zero `medical-failure` events, preserved session and no request. CAS changes, mismatched server objects, permission failures and immutable reference validation retain their original fail-closed handling.

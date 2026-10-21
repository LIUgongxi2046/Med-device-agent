# B11 cancellation progress (not completion)

Implemented production code:

- `TaskWorkflowWaitingV2Impl` has `task-owned-activity-cancellation-v1` using `Workflow.getVersion(DEFAULT_VERSION, 1)`. Legacy/default histories retain their original `Async.function` path. New histories use WAIT_CANCELLATION_COMPLETED and cancel owned activity scopes only after the persistent cancellation response says dispatch is closed. Cancellation is not exit evidence and cannot finalize the run on its own.
- `TaskRunTerminationReconciler` reads N02/N04/N05 CLOSED/export evidence from each independent supervisor outside SQL. Fresh lifecycle authority and privately minted `PendingClosure` original-session capabilities are rechecked inside SQL before a genuine CLOSED receipt is recorded. Existing supervisor owned-waitid, process-unit-empty, admission linkage, certificate/trust/deployment and export-byte checks are reused. HTTP 404/409 for CLOSED means pending, not a fabricated process receipt. Invocation settlement remains an independent requirement.
- The first cancellation response is returned before any closure reconciliation so the workflow can actually deliver cancellation. Subsequent polls reconcile original sessions only. No new dispatch, token, provider invocation, replacement lease or budget reset is introduced.
- RN-04 invokes `TaskRunPreRuntimeCancellation` after its original request commits. An actual original decision/operation/audit/outbox chain, locked closed run and active pointer, and absence of any workflow attempt, runtime intent, step, invocation or session are mandatory. Any attempted/unknown workflow launch remains ineligible. A genuine never-started cancellation gets an immutable receipt, terminal run/task CAS, active-pointer removal and normal public run.updated event. started_at remains NULL.
- The V2 relay excludes proven pre-runtime terminal events and can consume an already queued exact original event as PRE_RUNTIME_CANCELLED without inventing Temporal acceptance.
- Migration 141 adds the immutable pre-runtime receipt and owner FKs, replaces the old end-time check so actual never-started cancellation does not require a fake start, and protects terminal status/fixed input fields. Root still owns master/coverage registration.

Not yet implemented / do not count B11 complete:

1. Full claimed-before-session/partial-dispatch quiescence and deterministic N03/N06 cancellation disposition. Existing lease-without-session remains fail-closed, not inferred exited. Accepted Temporal workflows cancelled before graph/runtime materialization also still need their exact-original barrier path.
2. Actual original provider-stop/reconciliation path for in-flight invocations beyond existing cancellation intents and independently recorded settlement; UNKNOWN remains pending, with no blind redispatch or fake billing receipt.
3. RN-05 RESUME, REEXECUTE_PINNED and RERUN_UPDATED; private continuation authority, original fixed model/input/budget revalidation, successful receipt reuse, new attempt/successor relationship and task accumulated-budget binding.
4. TK-05/06 input proposals, impact decisions, newly confirmed task/plan/input revision creation and signed-version continuation without modifying signed bytes.
5. Real Temporal captured-history replay, migration/database integration, runtime/provider integration and tests are deferred under the user's code-first instruction. A version marker is not a claim that replay testing passed.

Validation performed: only narrow Java 17 production compilation of the 12 touched classes plus the existing relay controller dependency into `/tmp/b11-compile`, using the existing compiled dependencies. No test suite, full build, database, runtime, provider, network or push executed.

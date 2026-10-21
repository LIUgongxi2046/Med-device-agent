# B11 continuation lifecycle implementation

## Implementation boundaries

This change extends the restored RN-05 private successor authority rather than treating a parent run ID, terminal status, lease timeout, cancellation request, or result UUID as permission. Frozen replay/model/plan/snapshot/policy/budget bindings and original accounting remain unchanged. UNKNOWN invocations and process exits remain blockers. No provider, test, full build, service, migration, or network execution was performed for this work.

### Cancellation

- Existing durable N02/N04/N05 pre-admission launch intents without sessions remain reconciled through their original private supervisor proofs. No absent session is interpreted as exit.
- Migration 158 adds actual N03/N06 server-body entry/exit ownership, including N06 prepare, dispatch, commit and original-reuse preparation. Only the private ticket's same-thread `finally` may record CLOSED; OPEN, lost workers and legacy missing exit witnesses remain unknown.
- Cancellation disposal closes future dispatch/commit permission but explicitly does not assert business success, physical upload completion, provider refund, or N03/N06 fulfillment.
- N06 disposal reconciles the exact original command, not a different closed read-only reuse probe. Run finalization still requires independent invocation/process facts and owned activity completion.

### RESUME

- N02/N03/N04/N05 aliases reference fully verified original successful COMMITs and original artifacts. Successor run_step rows remain lease generation 0, without a new dispatch, session, invocation, ordinary COMMIT, or provider charge.
- Typed original readers preserve original N03 field artifacts, initial document identity, N04 candidate objects, N05 reviewed candidate and rule members. Only explicit run-local transport identities are normalized; material semantic mismatches fail closed, not by silently rerunning a successful call.
- Original ancestry is bounded and cycle checked. An intermediate alias is flattened to its actual successful origin, retaining the immediate RN-05 parent and every traversed alias hash.
- N06 is distinct: it has no leased run_step. `task_run_assembly_success_reuse` stores a separate immutable alias after recover-only physical verification of the original four-object family, actual host check and exact current semantic input. It creates no synthetic successor prepare/dispatch/COMMIT, check, candidate, or document save.
- New private `/internal/medical/task-run/reuse-originals/v1` transport and Temporal marker `task-original-success-reuse-v1` carry aliases separately. Internal N02 scheduler projections are never serialized as ordinary successor lease receipts. Original signed schemas and DEFAULT_VERSION history behavior remain intact.
- N06 reuse preparation has a single owned attempt. An ambiguous response enters original-query-only reconciliation, never replacement assembly dispatch. A definite no-original result permits the normal fresh stage.
- Finalization counts genuine current and original reused facts together. A successful N06 alias does not mean document adoption or N07 approval.
- RN-05 RESUME with saved successes requires the configured waiting-V2 deployment and service ingress. Older deployments without the additive path remain fail-closed.

## Persistence and integration

- 158: `task_deterministic_execution`, append-only identity with only OPEN-to-CLOSED transition and delete rejection.
- 160: `task_run_success_reuse` and `task_run_assembly_success_reuse`, immutable journals with tenant/project/origin/creator FKs, explicit version/manifests, hash/classification checks and update/delete rejection.
- Root owns master registration and coverage counts. No database changes were executed.
- `B11-N04-N05-ORIGINAL-REUSE-CONSUMERS.md` describes the detailed typed consumer APIs, including N03/N06.

## Verification status and deferred cases

No javac or Java parser tool was available during this implementation; production compilation and tests are not claimed. `TaskRunReuseProtocolV1Test` is written but not run.

Before deployment, execute focused production compile and deferred cases covering:
1. Cancellation before session admission, during provider admission, after provider unknown, during N03/N06 deterministic I/O, and after actual worker exit. Assert independent settlement/exit and no fictitious refund.
2. Historical Temporal DEFAULT_VERSION replay and additive marker path, including mixed current/reused N02 nodes and in-flight current commands.
3. N02 through N06 immutable original proof and exact semantic pin mutations, same-actor/project/task restrictions, revoked current rights and forged saved alias rows/journals.
4. Two or more consecutive RESUME runs, bounded ancestry/cycle rejection, old schema bytes and original receipt IDs/hashes unchanged.
5. Real N03 original field output, N04 initial-document/candidate reuse, N05 rule/candidate review reuse, and N06 original four-object/check family; assert zero replacement provider invocation/upload/COMMIT.
6. New wire unknown/duplicate/missing/null/coerced fields, wrong run/step/hash, duplicate aliases, forged original generation and N06 mistakenly routed as a leased step.
7. N06 original-read transport interruption/unknown response, alias write race, finalization race, and cancellation during read; prove query-only behavior and owned exit accounting.
8. Successful/failed/cancelled finalization with aliases; ordinary supplied receipts must not contain internal alias projections, and no pending stage becomes fulfilled by disposal.

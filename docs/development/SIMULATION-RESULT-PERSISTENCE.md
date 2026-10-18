# SIMULATION result persistence and nonfinancial settlement

## Implemented slice

`SimulationResultPersistenceService.persist` is an original-service-only continuation of a recorded `LATE_RESULT`. It never creates a new dispatch permit or assumes an application-user session. `SimulationInvocationService.originalFactTransaction` revalidates the original immutable admission and exact action, mTLS service identity, current separate retention policy and original approval grant before and after each transaction. Existing execution-grant expiry/revocation and dispatch-lease expiry do not by themselves erase a fact. Retention revocation/expiry still stops new retained writes.

The additive 062 operation fixes two artifact IDs and S3 write intents before object I/O. An ID in this operation is only a plan, not a registered artifact. Each write is marked WRITING in a committed transaction before transport. The transport receives exact canonical bytes and a fresh admission callback; its real `S3ObjectWriter` performs an encrypted conditional write, immutable-version metadata checks and a full verified reread. A previously started write is recovered by HEAD/GET only. A lost write acknowledgment, absent recovery object, partial sidecar or expired retention remains unresolved; there is no second PUT or model execution.

After both references have been verified, a single tenant transaction registers the `version_registry`, `artifact`, `artifact_content_binding` and source-lineage rows, rereads their identity and ObjectRefs, consumes both original quota reservations, updates both quota accounts, stores an immutable `simulation_quota_settlement`, and advances only the original canonical invocation/action. The same transaction records audit/outbox metadata and verifies all resulting references/counters/receipt fields. A repeated COMPLETED operation rereads the receipt and registered artifacts without charging again. Actual database rollback and race behavior still require live MySQL evidence.

The local charge is canonical exact-input bytes plus canonical ModelResult bytes, and one call. Fixture tokens are not this counter. Missing fixture usage remains SQL NULL / UNKNOWN. Provider request identity, provider receipt, currency, price version, total tokens and settled amount stay NULL in canonical invocation; `settlement_status=PENDING` is unchanged. The `SIMULATION:` identifier inside the retained ModelResult is simulation provenance, not an external provider request. No production eligibility or evaluation score is created.

A single-cell simulation job may terminate only after result persistence and quota settlement, when the original run has exactly one action and step, the cell is ISSUED, and the run/step remain RUNNING. Cancellation-before-result never restores a parent job to success. More complex or non-running parents are left unchanged for their own coordinator.

## Dependency and reading boundary

The artifacts reuse the context's real source-only dependency access manifest. Their lineage is precisely that source closure; it is not represented as the candidate/configuration graph. The separately frozen `simulation_context_dependency_manifest` preserves candidate/prompt/configuration provenance and is checked during finalization. Current permissions for result consumption are a separate concern: these new evaluation artifacts require a dedicated current candidate/context read guard in addition to ordinary artifact/source access checks. They must not become a production model-history or tool-execution path merely because persistence succeeded.

## Recovery and retention limits

A crash before SQL fact ingestion requires the separately implemented original-service read-only journal reconciliation route. Reconciliation must only read a complete existing local commit and verify its hashes; partial or absent commit stays unknown. It cannot invoke the provider, reset a dispatch, create a new local lock/journal or prove cancellation.

No retention deletion is implemented here. Existing local journal files lack a durable linkage to approved cleanup and backend settlement acknowledgment. Removing `started.json`/`commit.json` would destroy replay protection. Inventory/reconciliation and an eventual approved tombstone-preserving cleanup design are separate work.

## Verification scope

Tests use local schema fixtures, transport and service fault injection, and actual JDBC statement/parameter and reread code with JDBC doubles. They do not establish a running MySQL/S3 service, physical concurrent transaction rollback, real provider usage, production eligibility or customer acceptance. Runtime sockets/database/object-store tests remain blocked in the current execution environment. No commit, push, cloud task, production data write or journal deletion is part of this slice.

### Frozen verification checkpoint, 2026-10-18

The final coordinated offline Java selection passed 71 tests with zero failures/errors/skips: store JDBC doubles 11, result orchestration 8, retention approval 3, invocation service 13, invocation schema 13, dispatch 5, bridge configuration 2, read-guard/mapper/hook 13, local recovery service 3. Evidence: `.implementation-work/logs/simulation-result-final-targeted.log`.

An earlier broader selection accidentally included the existing runtime-dependent `ArtifactRegistryReadTest`; it stopped at missing runtime secrets before a connection. It was excluded rather than provisioning runtime infrastructure. The blocked attempt is preserved in `.implementation-work/logs/simulation-result-final-with-runtime-fixture-blocked.log`. No live MySQL or S3 behavior is claimed by the passing selection. Backend scope was frozen after this checkpoint for the user's frontend-first priority.

# Simulation result persistence and nonfinancial settlement (062)

This additive migration is offline-reviewed only. It has not been applied to a database and does not prove object-storage, database, runtime or production integration. Historical migration bytes 001–061 remain unchanged.

## Durable command and original identity

`simulation_result_persistence` has one row per original `(tenant_id, invocation_id)`. Its foreign keys require an existing SIMULATION invocation in the same project scope, an actual fact for that exact invocation and payload hash, an existing retention policy and a tenant member. Additive unique fact keys make the complete original scope/hash foreign keys possible. No fictional invocation or artifact is inserted by the migration.

`command_json` is the durable typed command containing two fixed artifact IDs, their object-write intents, writing flags and nullable verified object references. The application must validate its full shape, canonical binding/fact digest, exact object key/version/digest/size and retention binding. SQL enforces an object-shaped command, hash formats, positive safe revision, chronology and the states `PREPARED`, `WRITING`, `VERIFIED`, `COMPLETED`. It does not independently prove the contents of the object store or prevent privileged mutation. CAS updates and recovery must preserve the IDs and intents; retries must not allocate replacement artifacts or rerun model execution.

The preparation row deliberately has no artifact foreign keys: allocated artifact IDs are not yet actual artifacts. The final receipt does require them. Retention-policy ownership, approval status/revision and exact deadline must be revalidated in the application; the tenant-level policy FK is existence protection, not authorization.

## Exactly-once quota receipt

`simulation_quota_settlement` is immutable through application insert-only behavior and uniquely keyed by the original invocation. Both result and provenance IDs and hashes must reference actual same-scope artifact rows, using the existing artifact content unique key. Their IDs must differ. Its fact FK cannot point to another invocation's fact. The fixture `receipt_id` is not unique: repeated evaluation invocations may legitimately reuse the same fixture registration receipt.

The receipt records input/output counter units, their sum as charged units, exactly one call, the pinned counter/hash, execution status, and optional fixture usage. Units are unsigned and safely bounded; the sum is independently bounded. `UNKNOWN` requires SQL NULL fixture usage. `SIMULATION_FIXTURE` requires a JSON object; JSON null is not a substitute. `production_eligible` must be false. The receipt contains no provider request, currency, price, financial settlement amount or actual-provider token claims.

The canonical invocation's financial `settlement_status` remains `PENDING` under unchanged 060 constraints. This table records nonfinancial local quota accounting only. Artifact registration, account/reservation changes, receipt insertion and terminal local state need an atomic application transaction. A unique receipt alone does not prove those other changes were atomic.

## Job terminal-state extension

062 atomically replaces only the fourth unnamed CHECK from 061, using MySQL's generated name `simulation_evaluation_job_chk_4` and `DROP CONSTRAINT` (supported by the project's MySQL 8 baseline). Its replacement has a stable explicit name `ck_simulation_job_result_state`. All other checks and 061 bytes remain unchanged. The offline test identifies the fourth constraint from the original CREATE AST; a deployment must still validate the real schema rather than assume an altered external database matches this migration lineage.

Existing READY/revision 1, CLAIMED/revision 2 and ISSUED/revision 3 branches remain unchanged. New SUCCEEDED/FAILED states require revision 4 and preserve non-null authority plus object-shaped admission data. No cancellation state is changed to success by DDL. Application finalization must require the original job, a still-running parent, and the exact single action/step; a cancelled parent must not be restored to success. Existing system-job and run-step status constraints are unchanged.

## Offline verification

Use the existing toolchain only, without Maven or database access:

    source .implementation-work/toolchain-env.sh
    export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -XX:-UsePerfData'
    .implementation-work/python-contracts/bin/python tests/test_simulation_result_settlement_migration.py
    .implementation-work/python-contracts/bin/python tests/test_simulation_evaluation_job_migration.py
    .implementation-work/python-contracts/bin/python tests/test_simulation_ledger_migration.py
    .implementation-work/python-contracts/bin/python tests/test_migration_bundle.py

The new suite parses with JSQLParser and SQLGlot, pins all 60 historical SQL/XML files through 061, checks complete original identity and artifact FKs, verifies terminal CHECK replacement, and evaluates extracted unit/fixture CHECK expressions in an in-memory SQLite expression test. This expression test is not MySQL migration execution. The complete bundle validates physical table coverage and foreign-key target uniqueness/type compatibility. The Java inspection helper retains constraint-drop ASTs rather than skipping the altered constraint. `-XX:-UsePerfData` prevents concurrent source-file Java runs from injecting temporary perf-data lock warnings into JSON output.

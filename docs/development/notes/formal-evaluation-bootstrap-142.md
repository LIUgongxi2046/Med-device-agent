# A12: dedicated evaluation bootstrap and original launch

Development slice only. A12 remains in progress. No tests, database migrations, provider calls or live workflows were run for this slice. Narrow production javac used restored dependency caches; it is not integration certification.

## Implemented normal path

- POST `/api/formal-evaluation-executions/bootstrap`, explicit opt-in `medical.formal-evaluation-execution.enabled=true`.
- Closed request: `schema_version: 1.0.0`, `execution_id`, `start` (the complete existing 083 Start contract), normal context/CSRF/idempotency headers.
- Physical 079 input preflight and current 083/088 bound admission occur first. The tenant transaction writes real SYSTEM_JOB/EVALUATION_VERIFICATION jobs, run principals and every cell/node obligation. It does not fabricate a TASK, confirmed Plan, professional outcome, invocation, score or acceptance.
- The cell input points at the real registered 079 sample artifact. This is explicitly not a compiled prompt or authorization to transmit the expected answer. Per-node executors must independently produce/read their typed input.
- Approved policy actions are resolved to real owners and current grants. Future registered artifacts do not receive wildcard grants. TASK selectors and approval/scoring/publishing actions are not delegated by this bootstrap.
- Immutable launch intent plus one durable attempt precede transport. Temporal delivery checks the exact original started event, workflow type, queues, memo command and full fixed reference. Only private Accepted evidence can move a queued job to RUNNING.
- UNKNOWN uses POST `/api/formal-evaluation-executions/recovery` with `schema_version: 1.0.0`, `run_id`. It only describes and checks the original workflow; it cannot create a missing attempt, command or restart.
- Migration 142 adds real FK-backed job/launch rows, append-only triggers, and permits EVAL workflow_id NULL only while QUEUED. No success data or permissions are seeded.

## Integration API

`FormalEvaluationJobs.byRun(admission, run)` re-reads the original root request, 088 binding, cell, complete node obligations, execution registry, principal, system job and queued audit/outbox. Job is immutable data, not a capability. `Job.nodes()` contains NodeRow(stepId, nodeSha256, original 083 Node).

`FormalEvaluationLaunchLedger.original(admission, job)` yields the exact immutable Intent. `requireAccepted(admission, job, Reference)` validates original acceptance and job identity but allows historical terminal states for reconciliation; callers must separately enforce RUNNING/current authority before new dispatch.

`FormalEvaluationProtocol.Reference` pins tenant/project/execution/evaluation/run/sample/repeat, original input, candidate/policy, admission, exact node IDs+hashes, queues and timeouts. `FormalEvaluationActivities` defines awaitLaunch, execute, recover, complete and cancel. The workflow awaits persisted launch acceptance before dispatch, runs the fixed node obligations in node order, never automatically repeats execution, and reconciles UNKNOWN through the original recover path. It cannot infer success from missing nodes or host exit.

The independent runtime owner registers the workflow and actual professional/control activities. Bootstrap configuration deliberately does not install an empty runtime or completion implementation.

## Deferred verification

- Contract tests added as source, not executed.
- Database FK/immutability and rollback, concurrent duplicate keys, complete matrix preservation, launch network timeout/readback, original-start mismatch, current-grant revocation, original recovery after closure, and real workflow cancellation need the later test phase.
- Entire A12 additionally requires dedicated typed node execution, independent supervisor/object evidence, private completion, independent qualified scoring and current publication consumers. This slice alone does not satisfy that task.

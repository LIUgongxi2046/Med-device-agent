# N01 PLAN_DRAFT runtime handoff (2026-10-20)

## Implemented code path

The runtime is isolated from TASK/N02: SYSTEM_JOB / PLAN_DRAFT / N01, a real queued job and principal, null pre-plan revision, literal approved scope, and original TASK-PLAN-DRAFT operation. Private admission, physical package preflight, registered original input, current grants, fixed publication/policy, source rights, retention and the dedicated producer authority are required. No caller JSON grants or fabricated plan are accepted.

The dedicated outbox consumer calls authenticated queued/launch endpoints. Original input transfer and registration precede actual Temporal start. A durable launch attempt is recovered by original request only; an uncertain start is never replaced with a second start. Actual Temporal acceptance produces the private QUEUED-to-RUNNING transition. Migration 105 explicitly allows only a queued, unaccepted PLAN_DRAFT to have a null workflow ID.

A separate signed N01 worker path claims the real step and binds its original host action. The SQL prepared envelope binds original input registration, actual prompt/package pins, deployment and producer pins. Its supervisor route accurately describes a prepared SQL envelope, not an S3 object. The independent supervisor physically admits and observes the process and exact model request. Model business-purpose remains TASK, but context, schema, dispatch and provider routes are independently pinned to N01.

The first round is explicitly text-only planning: Planner model tools are empty. The actual registered model output is deterministically expanded against the actual template, with normalization provenance separate from original invocation provenance. Successful process exit, original export and settled invocation facts are required to produce an artifact. Registration, independent server checking, real host AT10 dispatch, signed plan.write and an immutable save receipt precede the exact RUNNING-to-WAITING_INPUT transition. No human approval or subsequent TASK execution is synthesized.

## Failure and recovery boundary

Admission CLOSED is only a gate state, never evidence that a process exited. An abort, kill request or host_process_exited boolean cannot produce CLOSED supervision, FAILED execution, a successful artifact, or a fee release. Host failure observations preserve original provenance and require reconciliation. The status endpoint is an authenticated, read-only original-operation view that remains available after the initiating user's permissions expire. It explicitly says that host observations do not attest process exit. Recovery never automatically redispatches the model. Only independently established original termination and settlement facts may justify a later terminal transition.

## Configuration and verification

All Java routes/beans/workflow registration are disabled unless medical.professional-runtime.plan-draft-enabled is explicitly enabled. Operator-pinned deployment, exact worker/supervisor identities, TLS material, reviewed profile/role/prompt packages, queues, object storage and provider configuration remain required. No credential, permission, deployment or provider execution was performed.

Java17 compile-only diagnostic passed for the 123-source integration slice at 14:44 UTC; a subsequent Jobs guard requires private package_preflight pins. Python syntax was parsed, not executed. Full tests are intentionally deferred under the user's code-first instruction. These diagnostics are not integration certification.

## Tests authored and execution backlog

Authored: TaskPlanDraftRuntimeCapabilityTest; professional-runtime/tests/test_plan_draft_validation.py; model-gateway/tests/test_plan_draft_gateway.py. None executed.

The root test lane must exercise:

- Actual queued input physical registration, start timeout and original acceptance recovery without replacement start.
- Fresh and expired/revoked current grant/source/retention/producer authority; no fake package preflight.
- Dedicated N01 signing, claim, host/model/tool action separation, exact lease/generation/input and cross-TASK rejection.
- Independent supervisor TLS, launch digest, observed model request, real exit/export and session binding.
- Text-only model projection, one original paid invocation, genuine result registration and deterministic normalization.
- Physical candidate registration, independent check, signed AT10 save, exact completion revision and replay.
- Failure before and after dispatch; UNKNOWN is retained, host boolean never proves exit, no unknown-cost release or model replay.
- Historical human reading uses the current operator's rights and immutable original facts, not the original actor's current grants.
- Migration 105 compatibility, default-off Spring wiring, existing N02 and N03 regression paths, and Python/TypeScript package integration.

The owned-file manifest and SHA-256 snapshot are adjacent to this note; shared files may still change through coordinated sibling/root edits.

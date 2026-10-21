# Independent EVAL runtime integration

Status: implementation source delivered; only Python AST parsing and TypeScript `--noEmit` performed. Unit tests are supplied for a later authorized test stage. No runtime, services, provider calls, database migrations or integration tests were started.

## Lane and authority

This is `SYSTEM_JOB / EVALUATION_VERIFICATION`, not TASK and not CHECK_DOCUMENT. `evaluation_activity.EvaluationHost` does not subclass the generic TASK host. It only reuses the inventory comparison primitive. `EvaluationSupervisorPlan` has a separate exact-input/envelope validator. N04/N05 TASK restrictions remain unchanged.

The private backend is authoritative for actor, tenant, role, scope, model-send rights, live lease, original launch, invocation receipts, tool receipts, outputs and business outcome. The host never submits model output, a host exit flag, a success flag or claimed supervision evidence to `/finish`.

## Operator wiring

1. Install and hash-pin the approved paired SDK/runtime wheels, professional-executor plugin bundle, `evaluation_runner.py`, Python executable, runtime executable, all transitive runtime files and profile files. Use the existing operator-provisioned namespace, cgroup, seccomp, capability and LSM slots. Runtime code creates none of these privileges.
2. Use the backend `FormalEvaluationDeployments` registry: one canonical, digest-pinned deployment per node kind. Its deployment `model_tool_mode` is `EVALUATION_NODE_V1`; this is distinct from the SDK plugin mode below. Each entry pins its own role, profile, SDK inventory and runtime manifest. All entries must use the same supervisor service/endpoint/certificate/trust when sharing a supervisor.
3. The SDK plugin configuration must explicitly specify `modelToolMode: EVALUATION_VERIFICATION_V1` and `evaluationToolIds`. IDs are restricted to AT-01/02/03/04/08 intersected with that role's scope. An empty array is an explicit no-tools EVAL profile. TASK configurations cannot use this property. These are independent evaluation read/question tools; TASK mutation tools are not silently redirected.
4. Pin the approved exact-input tools to the actual plugin's names, descriptions and canonical parameter schemas. `expected_inventory.tool_schema_hashes` must exactly equal that approved input's name-to-parameter-hash map. No runtime expansion or fallback to the generic role's full tool set is allowed.
5. Supply existing pinned mTLS transports for host-to-backend, host-to-supervisor, supervisor-to-backend and supervisor-to-host. The worker and supervisor/backend identities must remain distinct as required by the existing supervisor service. The combined host entry point uses one already-authorized worker identity across its node deployments. Deploy distinct worker identities in separately wired instances; do not forward another worker's identity.
6. Construct `evaluation_deployment.supervisor_service(...)` with `OperatorSlot` instances and serve its application using an existing client-certificate-required TLS listener. Slots include the dedicated EVAL runner, independent process inspection, immutable deployment bytes and pre-provisioned isolation policy.
7. Run `evaluation_deployment.serve_worker_with_relay(...)` with node-kind-to-`Deployment` mapping, pinned control/supervisor transports, existing relay TLS identity, and explicit protocol digest. It serves the bounded authenticated relay and registers the five exact Java activity names, separated across control and professional queues.

The explicit worker protocol object contains exactly:

- schema_version: `1.0.0`
- revision: existing UUID
- namespace: configured Temporal namespace
- control_task_queue and professional_task_queue: distinct published queue names
- workflow_type: `medical.system.formal-evaluation.v1`
- execution_kind: `SYSTEM_JOB`
- job_type: `EVALUATION_VERIFICATION`
- model_tool_mode: `EVALUATION_VERIFICATION_V1`

Use its canonical SHA-256 as `protocol_sha256`. The source provides no invented deployment IDs, manifests, wheel digests, certificates, credentials or isolation receipts.

## Actual execution path

- `await-launch` checks original backend acceptance.
- `open` is called once. The backend may return an actual WAITING_INPUT NodeReceipt without a launch. Otherwise only its issued PREPARE starts the original session. Missing/uncertain responses never cause a second open or a second provider send.
- The host creates the isolated original job directory, materializes pinned profiles with only UDS transport substitutions, and asks the independent supervisor to prepare. The supervisor independently loads the private prepared plan; it never trusts a host-supplied PID, executable, profile or exit boolean.
- Independent kernel-backed F1/F2 observations and process ownership establish admission. Backend `/admit` returns the original START pins. The supervisor independently checks backend admission before releasing the SDK gate.
- The runner passes the approved exact-input serialization as an SDK-only seed. The EVAL adapter extracts its approved system/user prefix into the actual on-wire model projection, discards the SDK's generic system prefix, and preserves the real ordered assistant/tool history unchanged. The serialization is never used as the model's user message. The backend independently checks every exact-input/history byte and current model-send rights.
- The supervisor observes `/v1/model-contexts` using SO_PEERCRED and pins the EVAL projection schema. The relay calls C07 `/internal/v1/evaluation-runtime/model` using only the original observed request ID and server-owned session/step locator. It returns the backend's settled ModelResult, never an emulated provider stream. The SDK adapter validates/replays all returned text and tool blocks; provider retry count remains zero.
- Actual SDK tool requests go to `/internal/v1/evaluation-runtime/tool`; schema-150 receipts and current role/source permissions remain backend authority. Full tool-success payloads are validated and rendered canonically so the next model call contains the actual recorded response.
- Heartbeats renew backend authority while waiting for the independently owned process. Only actual owned wait/trace evidence and complete session archive produce CLOSED. Backend `/finish` re-reads closure/export/invocations/tools and registers full node outputs under the node's business schema. No host-derived business checker is added.

## Recovery and cancellation

`recover-node` never calls open/admit/model/tool and never constructs a receipt. It returns only the private backend's original NodeReceipt, including UNKNOWN. Temporal continues its existing recovery loop for UNKNOWN. No absent state, empty cgroup or process disappearance is interpreted as a terminal result.

On interruption the host first fences backend admission, then closes its bridge and asks the owning supervisor to abort the original session. An uncertain abort is not retried with a new process or session. `cancel-plan` first atomically fences admission and returns the original sessions and expected nodes. The control activity checks this original cancellation receipt against the workflow, asks the supervisor to abort only those sessions, memoizes each one-shot abort attempt, and then invokes `cancel-cell`. `cancel-cell` remains a private backend action; process and provider uncertainty cannot be converted into fabricated CANCELLED/SUCCEEDED. Session/export files follow their existing retention policy and are not silently deleted.

## Deferred verification

`tests/test_evaluation_runtime.py` and `professional-executor/tests/evaluation-mode.test.mjs` cover exact system/user ordering, EVAL versus TASK separation, lossless actual history, one-shot dispatch, original receipt binding, separate queues and recovery that cannot launch. Later approved runtime tests must additionally exercise real mTLS identities, lost open/dispatch responses, expired leases, restart recovery, cancellation, real supervisor F1/F2, real SDK inventories, C07 receipts, source-rights revocation and N02–N07 output registration. Static compilation is not evidence that these runtime checks have passed.

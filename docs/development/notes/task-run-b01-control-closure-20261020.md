# B01 task workflow control closure

Status: implementation batch, not verified or enabled. This document does not approve RN-01, deploy configuration, create credentials, call a provider, or claim full run success.

## Concrete control routes

All eight existing RunActivities operations have real persistence implementations. `load` proves actual accepted Temporal start before RUNNING; `dispatch` commits a private command reservation and original lease receipt; `commit` consumes genuine N02 production; `failure` requires actual invocation settlement and supervisor process closure; `dependencyFailed` consumes a genuine failed ancestor; `consume` verifies an actual RN-04 cancellation decision; `cancellation` closes dispatch and persists actual incomplete or complete termination observations; `finish` checks the full graph, all actual receipts and physical termination before an exact CAS/audit/outbox transition. A transport exception, expired lease, empty journal, or request acknowledgement is not a successful dispatch or termination proof.

New writes require operation-scoped transaction-bound current authority. RUN_CANCELLATION does not authorize success/failure settlement. Original receipt recovery uses the original principal and launch pins without regaining producer grants, minting tokens, reclaiming a lease, resubmitting Temporal start, or invoking a model. Dispatch itself charges no paid budget; actual paid reservation remains at the invocation boundary.

## Explicit V2 only

V1 WorkflowProtocol and default activity names remain unchanged. The V2 worker checks the original deployment's canonical hash, start command and SDK start memo. The immutable deployment has 16 fields: schema_version, revision, namespace, control_task_queue, professional_task_queue, workflow_type, professional_activity, waiting_activity, version_marker, load_activity, recovery_activity, recovery_poll_millis, undispatched_failure_activity, execute_stage_activity, commit_stage_activity, recover_stage_activity. The new recovery activity is exactly `medical.run.recover-stage.v2`. Missing or different pins fail closed. The DEFAULT version-marker branch retains the original V1 scheduling sequence.

The V2 loader carries the complete legacy planned inventory together with original in-flight dispatches, generation-zero undispatched failures, typed stage commands and typed stage commits. A READY input failure remains distinct from an unmaterialized input. Neither is assigned a fictitious lease. Restarted or uncertain N02 work uses the original dispatch query, not professional execution. A lost result/finish response is recovered from genuine produced or committed facts.

N03 is a separate deterministic tool path. It never masquerades as a professional model step or a WorkflowProtocol.Output artifact. Its internal completion locator binds the original stage command, node, fixed input, real action/generation and genuine result receipt. The producer independently registers and verifies the real FIELD_CHECK_RESULT output. The stage COMMIT is separately immutable and binds the original graph obligation. SATISFIED requires full target coverage, no waiting entries, every entry actually COMPUTED and MATCH; merely running a check does not satisfy the stage. BLOCKED creates no fictitious waiting item. A failed or lost execute/commit response switches permanently to bounded query-only PENDING/PRODUCED/COMMITTED recovery of that same command. It never retries execute to reconstruct an unknown outcome.

N04-N06 remain explicitly pending until their genuine producers and typed routing are implemented in their own node tasks. N03 success cannot hide them or produce overall SUCCESS. The workflow polls only original in-flight work at the immutable bounded interval and fixed deadline; it does not busy-spin.

## Waiting and cancellation boundaries

AT-08 WAITING_CONTROL consumes A2's genuine produced waiting completion, with no nullable V1 result artifact. Its answer path verifies the original waiting COMMIT and links the actual human decision and real new run. It does not wake the old workflow. Legacy RESOLVE_WAITING and TERMINATION_PROOF command kinds are not granted by a UUID, STALE work item, caller assertion, or cancellation permission: they require their corresponding genuine business decision producer. No such command producer is silently synthesized in this batch. RN-04 REQUEST_CANCEL delivery verifies the real operation/audit/outbox, and reports SIGNALLED_NOT_CONSUMED until the actual Temporal consumed receipt is independently observed.

## Deployment and validation scope

Worker/relay production factories are separately default-off and require explicit protected existing deployment, TLS/client material, exact service pins, namespace/target, bounded capacities and lifecycle settings. HTTP requests use fresh TLS state, TLS 1.3, HTTPS endpoint identification, bounded bodies/timeouts and no redirect/retry. There is no guessed default endpoint or generated credential. Missing strict-field or waiting dependencies fail enabled startup, rather than authorizing a partial configuration.

The constructor-graph test source now includes actual lifecycle, stage endpoint/commit, strict dispatch/execution/input/producer interfaces and existing invocation participants. SQL, policy and physical transports remain explicit peripheral doubles. Real Temporal SDK tests cover original V1 history replay, V2 waiting, original-running restart, lost stage response, original stage restart and absent later-stage obligations. They are source only in this development batch; the user requested full regression/integration after coding. Java 17 diagnostics do not substitute for those tests or production acceptance.

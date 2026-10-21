# Recovery inventory after execution-environment replacement

Status: incomplete reconstructed working source, not a validated release.

## Verified recovery base

The recovery process restored the delivered 096 source archive and overlaid all 252 files from the separately saved 03:14 UTC N02 WIP archive. Those archive hashes and per-file manifests were verified during recovery. This document does not claim a Git history was recovered. The previously used recovery-20261019 worktrees, /workspace/shared package/host staging, and /tmp test evidence were absent when checked at 07:10 UTC.

The 03:14 archive was explicitly WIP. Historical local commits and later selected test results are descriptions of work in the lost environment, not validation of these restored bytes. No post-03:14 archive was independently saved for the backend integration.

## Lost or not yet recovered source

1. Activity preparation and dispatch integration: TaskRunActivityPreparation, TaskRunActivitiesAdapter, TaskRunAcceptedStart and the narrow private QUEUED-to-RUNNING accepted-start transition; exact load/dispatch/commit HTTP adapter and tests; original-only running replay reads. Their presence must be reconstructed and revalidated, not inferred from older run bootstrap primitives.
2. C07 iterative history: TaskInvocationHistoryData, same-session history projection, current source/model transmission checks, exact context-ID session lookup and tests. The restored Context still rejects ordinal other than 1, and Originals requires the original ordinal 1 path. Initial invocation code survived; iterative support did not.
3. Java original-only invocation RECOVER: TaskInvocationRecovery, private metadata-only VerifiedRecovery, original exact dispatch/request/service journals, one-use bridge recovery call and endpoint. Observation must not create a replacement dispatch or turn expired write authorization into new output registration authority.
4. TLS and enabled dependency wiring: ServiceClientTlsStore, TaskProfessionalTlsConfiguration, TaskRunActivitiesClientFactory, ConnectionVerificationPassedAuthority and corresponding real in-memory TLS, eager-context and factory tests are absent. No pre-send pinning or production TLS proof can currently be claimed from the restored tree.
5. Host hardening after 03:14: exact context-bound session recheck, descriptor-relative no-follow profile file reads, safe relative component semantics preserving exact Unicode, pooled supervisor peer-chain/CRL revalidation, and supporting tests require inventory/reconstruction.
6. Integration changes: exact immutable N02 table guard additions, wired 095/097/098/099 master/coverage and any post-archive commit-writer corrections must be compared against restored files. Their historical test reports are not available as current evidence.
7. Typed JSON package producer 100: all backend producer/schema/migration/semantic/configuration files, normal package UI, and strict trigger parser helper were later work and are absent unless separately recovered. Reconstruct the closed frontend-facing contract first. A newly reconstructed file must use its actual digest; the historical authoring schema digest eb53171d8f359d2b0bd4bc8d0c7022a0c33a1b4fe25d396ca68a785f45329422 is only a comparison target, never a substitute for recovered bytes.

## Required package100 semantics to preserve

- Project-owned PROMPT, PROFILE and ROLE JSON only. No arbitrary schema or binary import, grants, approval records, or document-upload widening.
- Real conditional PUT plus full original GET verification and immutable registration. Original recovery may complete only the original registration; never replace an unknown PUT, key or artifact.
- Exact current capability.configure, project/source/dependency rights. Typed semantic edges are not source-rights lineage.
- A private pre-intent rejection proof only after confirmed original absence and successful read-only transaction. Exact first-dispatch marker binding to project, operation, key digest, wire-body digest and context. Four codes only: SCHEMA_INVALID, ACCESS_DENIED, REVISION_CONFLICT, SCOPE_STALE. No generic mismatch/4xx/NOT_FOUND inference.
- Known authored blank or over-262144-UTF8-byte prompt content must fail SCHEMA_INVALID before intent. Safe profile paths reject empty, dot, dot-dot, absolute, backslash, percent and controls, preserving exact other Unicode names.
- Recovery reads validate immutable pinned candidate identities and current rights, not the expanding current candidate catalogue. The lost first implementation had this defect; the later fix used a private transaction/admission/thread-bound original token.
- Normal capability authoring rechecks semantic provenance only for artifacts actually produced by 100, without inventing historical producer records. No optional/permissive production dependency fallback.

## Unfinished designs, not recovered implementations

Versioned TASK cancellation was staged outside the source and not validated. The smallest approved design uses an actual original RN04 decision plus locked dispatch/quiescence barrier and existing operation/audit/outbox for pre-runtime finalization; 075 is used only when a real runtime intent exists. No fake graph, root, lease or execution intent. Active termination remains incomplete without genuine independent stop/exit evidence. Legacy Temporal history compatibility requires actual version-gated replay testing.

Predecessor-evidence prompt encoding V2 was design-only. Preserve all V1 bytes and behavior; require explicit new authoring opt-in and separately authorized actual predecessor content. N03–N07 producers remain incomplete. No RN01 activation or full workflow acceptance is implied.

## Reconstruction and validation order

Frontend-first remains the user's priority. Restore the package authoring contract and frontend dependencies first. Backend owners are performing read-only inventories before narrowly assigned reconstruction. No Maven, service setup, migration execution, credentials or provider calls have been started in this replacement environment. Re-establish dependencies and source ownership before builds, retain failures, and report only tests run on the recovered/reconstructed source.

## Read-only owner checks at 07:12 UTC

The C07 owner checked 38 restored C07/shared076/producer files against the WIP manifest; all matched. The independent TaskResearchToolReceipts current and original-history readers survived and should be reused. Python task_gateway_entrypoint.py matches its previously recorded 55250635a92c79119e443b230ada02e665dad07e5c1c8c3cd21f9456e63c0f35 digest. This verifies restored source identity, not current execution.

The host owner confirmed the exact context-ID session overload and SQL predicate are absent, while core host/compiler/finish/tool-receipt sources survive. The eager token/model/invocation/worker dependency cycle is present again because its later factual reader split was lost. Finish orchestration source survives, but its five later tests do not. Tool-history verifier and its eight tests survive. Whole published package/tool-read contract files match their earlier pins, unlike the later package100 authoring schema which is absent.

Stage naming correction: N05 is independent source/rule checking; N06 is assembled-draft/cross-file checking; N07 rechecks the currently saved body. Export is a separate EX delivery path. Existing shared task detail and document editor features must not be described as entirely absent pages merely because stage execution actions remain incomplete.

## Concrete C07 reconstruction ownership (not yet authorized implementation)

The original C07 owner identified these lost integration files: new TaskInvocationHistoryData; changes to TaskInvocationProjection, TaskInvocationService, TaskInvocationContext, TaskInvocationOriginals, TaskInvocationResults, TaskInvocationContextOrchestrator and TaskInvocationConfiguration. Private history must be constructed from actual session/admission/model-request or exact registered context proofs, validate the complete ordered invocation/tool chain, and preserve full current source/model-send checks. Saved history binds model_results/model_pins/tool_responses/tool_pins. Missing conversation/history tests had respectively eight and seven cases in the lost environment.

Original-only Java recovery requires changes to FormalInvocationOutcomeAuthority, TaskInvocationLocalBridge, TaskInvocationController/HttpSchema/Configuration/SecurityConfiguration, TaskInvocationRecords/Originals and TaskNodeProducerAuthority, plus new TaskInvocationRecovery. Its private recovery proof binds tenant/actor/run/step/invocation/attempt, current authenticated service/trust and original full dispatch journals, not live execution permission. The exact adapter request is schema_version + mode RECOVER + invocation_id + dispatch_attempt_id. A single bounded process invocation may observe facts, never redispatch. Returned recorded=true alone is not a backend settlement proof. Previously installed policy-phase tests were not executed before replacement.

The host implementation requires the five-argument requireCurrentSession admission/input/binding/session/contextId overload and exact context_manifest_id predicate, plus the TLS and factual connection-reader changes described above. Preserve old initial-call behavior while rebuilding. These are inventories, not currently present implementations or test claims.

Direct integration recheck at 07:14 UTC: master.xml already includes 095/097/098/099 in the restored bytes, so those includes must not be duplicated. TaskResearchCommitService already contains loadCommitted and recover; compare its complete verification details rather than replacing it from memory. ProtectedTenantInterceptor does not contain the later exact N02 immutable-table names checked above. This corrects uncertainty in item 6; presence is not a fresh migration or Java validation result.

## Activity integration inventory at 07:14 UTC

Missing installed-source family: TaskRunAcceptedStart, TaskRunActivityPreparation, TaskRunActivitiesAdapter, TaskRunActivitySchema, TaskRunDispatchReservation, TaskNodeRuntimeConfiguration, TaskRunActivityEndpointFactory, TaskRunActivityController, TaskRunActivitySecurityConfiguration, TaskRunActivitiesClientFactory, task-run-activity.schema.json, and workflow TaskRunActivitiesHttpClient. Missing tests mirror those classes and include TaskRunReplayOriginalReadTest.

FormalRunControlAccess and FormalWorkerControlAuthority survive without accepted-start proof rebasing. TaskRunReplayLedger, TaskRunReplayGovernanceLedger and TaskRunReplayRegistration survive without running original-only replay support; their restored signatures remain queued-only.

Cancellation authority/ledger/protocol, version-gated workflow changes, historical-run journal helper, frozen legacy workflow fixture and cancellation endpoint tests were off-source unfinished work, not lost validated implementation. Do not count them as previously delivered.

At 07:15 UTC, only the minimal package100 frontend contract was reconstructed separately at /workspace/shared/task-package-contract-recovery/task-professional-package-authoring.schema.json. Actual SHA256: 4e1904ea23571d016a344ce4ef2212c202e8a4fad078cc7fcd305ec1d9b11e19. This is not the historical eb53171 byte sequence and is not backend implementation acceptance. The frontend reconstruction uses this path and verified hash. All broader backend reconstruction and heavy validation are paused to honor the user's renewed frontend-first delivery priority.

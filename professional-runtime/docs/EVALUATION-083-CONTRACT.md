# Closed formal evaluation execution contract (integration work in progress)

This is an implementation handoff, not evidence that an evaluation ran. No producer tables or records have been manufactured. Migration 083 is reserved by the parent. Public frozen contracts and hashes stay unchanged.

## Start

`FormalEvaluationExecutionStarts.start(actor, contextRevision, idempotencyKey, Start)` receives only:
- schemaVersion=1.0.0
- evaluationId, definitionSha256, expectedEvaluationRevision
- candidate: original versionId/contentSha256
- executionPolicy: original independently approved versionId/contentSha256
- enterpriseBudget, parentBudget: approved original budget revisions

No input bytes, approved booleans, custom source purposes, observed outputs, human scores, runtime success flags, invocation IDs or sample subsets are accepted as start authority. The complete sample × repeat × policy-node matrix is derived from original records. Missing actual deployment or dispatch adapters returns DEPENDENCY_UNAVAILABLE before a job is started. A DRAFT candidate may execute; a published Agent is not required (avoids first-publication deadlock).

## Original authorities, all required

1. New `EvaluationService.requireExecutionDefinition(a,id)` must read original immutable definition through existing `load`, reject finalized/disabled/changed definitions for new runs, enforce actual current `evaluation.run` on EVALUATION for `EVALUATION_RESULTS`, and expose a private consumer-bound ExecutionDefinitionSnapshot. It must not call requireAcceptance (which requires a completed evaluation result).
2. `FormalAgentCandidateAuthority.require(a,candidate,project,EVALUATION_RESULTS)` yields only original candidate identity, not execution approval.
3. `AgentExecutionPolicyAuthority.requireByVersion(a,policy.id,policy.hash,project,classification,RUN_CREATION)` yields actual independent approved policy. Candidate's policy must exactly match. Original policy node RoleScope and current grants narrow principal/actions.
4. Each sample input/expected original must be read and checked under its original approved business purpose. Metadata read purpose EVALUATION_RESULTS is not permission to external-process DOCUMENT_BODY bytes. Provider/model/region/training/source rights and current model.send must all be checked at actual C07 send.
5. Enterprise/parent budgets, approved model profile, tokenizer implementation and runtime deployment manifest must all be genuine fixed records. No MODEL_VERIFICATION intent or SIMULATION artifact substitutes.

## Persistent producer

A SYSTEM_JOB of `job_type=EVALUATION_VERIFICATION` targets the exact evaluation definition. Each sample/repeat gets a unique run and immutable input manifest; approved professional nodes get real run_step rows. Persist original candidate, policy, evaluation, sample input/expected, source family/split, runtime manifest and budget bindings together with execution_registry/run_principal/system_job_run/run_step, audit and outbox in the same original tenant transaction. Temporal workflow_id is tenant/run (unlike CP09 MODEL_VERIFICATION direct-adapter jobs). Pending human checkpoints remain WAITING_INPUT; they are not auto-approved.

Each real step dispatch records original lease_generation, action and envelope; each model call uses actual step_action_binding→invocation→registered result/capture records. FormalWorkerAccess and E currently explicitly reject this job type; an independent private admission path is required. Do not widen them to every SYSTEM_JOB or spoof a verification UUID.

## Reader consumed by 080

`FormalEvaluationExecutionEvidence.require(a,evaluationId,candidate,executionPolicy)` returns private-constructor `VerifiedEvaluationExecutionEvidence` only after original ledger and original object re-reads.
- `records()` returns every expected cell/node (no SQL inner-join omission accepted).
- `authorizations()` returns original read/current-grant witnesses.
- `requireConsumer(a,evaluationId,candidate,policy)` is exact admission/target bound.
- `requireUnchanged()` re-reads all original dependencies and completion facts.

Per observed node record: evaluationId/definitionSha256, candidate Basis, policy Basis, sampleId/sourceFamily/split/repeatIndex, inputRef/expectedRef, nodeKind/role Basis, runId/stepId/leaseGeneration, inputManifestRef, runtimeManifestSha256, parametersSha256, tokenizer Basis, modelProfile Basis, session export reference/hash, original step-result reference/hash, original invocation IDs and each registered result reference/hash, actual completed run/step status, immutable audit/outbox identities.

N00-only technical checks cannot satisfy professional nodes. Missing, duplicate, extra, cross-sample, cross-repeat or mismatched cells fail closed. MODEL_VERIFICATION and SIMULATION never qualify. ExpertScore remains separate 081 original Score/scorer/time/qualification/approval evidence; the runtime never produces expert judgment or professional acceptance.

## Host integration

The actual Python host exists in this package. Its concrete mTLS client uses closed `/internal/v1/professional-runtime/{open,heartbeat,close-admission,bridge,finish,failure}` routes; these server controllers do NOT exist yet and are a blocking integration dependency. `finish` must authenticate original session/lease, read registered invocations/results and process-isolation exit evidence, register/read original session export and StepResult, then return existing WorkflowProtocol.Completion. It cannot trust SDK `completed`, final_response, file hashes, or a host-supplied boolean as business success or provider termination evidence. The host deliberately cannot synthesize that Completion.

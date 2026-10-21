# N05 independent review dispatch and workflow contract

Implementation-only status. Regression sources are authored, not executed. No provider,
runtime, deployment, full build, network integration, or release verification is claimed.

## Dispatch ownership

`TaskSourceVerifierDispatchReservation` owns a private, transaction-commit-activated,
same-thread, single-use reservation. Its before/after hooks bind the actual current N05
node, exact input, worker certificate, step revision and lease generation to one claim
transaction. Neither a reservation alone nor an expired lease authorizes another claim.

`TaskSourceVerifierDispatchJournal` registers a physically transferred `STEP_ENVELOPE`
through the N05 producer ledger before recording dispatch. Recovery verifies the original
reservation/claim, accepted workflow reference, immutable runtime preparation, original
producer pins, physical object intent/attempt/registration, artifact content binding,
and audit/outbox witnesses. Original recovery and current/lifecycle `StageFacts` are
separate privately constructed proof types; N04 proofs cannot substitute.

Opening a professional worker records one launch witness. Existing/uncertain launch or
model attempts are not redispatched. A genuinely completed AT07 receipt is returned as
`COMPLETED` through original recovery before current lease checks are needed.

## Typed stage and ordering

The new `TaskSourceVerifierStageProtocolV1` is not `WorkflowProtocol.Completion`.
`ReviewCompletedV1` accepts only the exact 22-field AT07 receipt, including candidate/body,
findings/coverage physical receipt identities, coverage completeness, actual issue IDs,
and `professional_approval=false`. The submit action is intentionally distinct from the
runtime dispatch action; provenance verifies their respective identities.

The backend prepare/dispatch endpoint requires accepted N04 and N05 extension bindings
and full genuine N04 stage inventory satisfaction. The workflow likewise starts N05 only
after all N04 chapter assignments have actual committed READY candidates. Complete,
passing reviews satisfy only N05. Incomplete, contradicted, insufficient, failed,
blocked, or issue-bearing results remain `WAITING_INPUT`; they never imply PASS,
professional approval, document adoption, N06 fulfillment, or overall run success.

## Compatibility and activation

The additive `SourceVerifierWorkflowExtensionV1` pins both the unchanged base V2 protocol
and exact N04 extension hash, owns a distinct professional queue, and has its own memo and
version marker. Existing V1, base V2, and N04-only histories retain their prior wire shapes.
The N05 branch uses private source-verifier stage routes and separate activity names.
Unknown activity results enter original-query-only recovery, not fresh execution.

The associated launch binding, worker registration, lifecycle facts, and AT07 physical
recovery are separate components and must be included in the same integration gate.
N06 is intentionally still unfulfilled.

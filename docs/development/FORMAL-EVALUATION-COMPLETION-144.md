# Formal evaluation completion and independent scoring (144)

## Production path

1. The 142 job/bootstrap fixes the definition, candidate, policy, sample family/split/repeat and node matrix.
2. The 143 node runtime creates a private `FormalEvaluationNodeRuntime.Completed` only from real registered outputs, closed independent supervisor records, original business outcomes and settled accounting. Raw records, host FINISH, connection verification and policy approval are not completion authority.
3. The node activity calls `FormalEvaluationCompletionService.record(admission, completed)` in the original tenant transaction after the original node-result insert. It rereads the private runtime original, locks the node, writes an immutable 144 completion receipt and original audit/outbox witnesses, then rereads them. Exact duplicate completion reuses the original receipt; mismatch fails.
4. GET `/api/evaluation-batches/{evaluation}/candidates/{candidate}/execution-results`, with the normal schema/context headers, rereads the entire fixed matrix and original objects. It returns real records and original read receipt IDs before scoring. Partial records, unfinished closure, unknown cost or missing obligations cannot be represented as `execution_complete=true`. No fallback preview invents a completed result.
5. POST `/api/evaluation-batches/{id}/scores` remains the normal scoring API. `FormalEvaluationScoringGate` prepares real object reads outside the score transaction and verifies the exact output/evidence/sample/repeat inside it. The actor must be human, independent of the candidate author and currently hold one unambiguous approved, current, dimension-qualified EVALUATION_RESULTS qualification. Missing configuration or credentials fails closed. Scores remain supplied human observations; the implementation never calculates or fabricates an expert score.
6. The original finalize API requires a human independent of every candidate author and first rereads complete original execution for every fixed candidate, then computes the real verdict and retains INSUFFICIENT_EVIDENCE semantics. Missing runtime evidence cannot create a finalized PASS from historical scores. 081 formal score binding further pins the approved domain, real original score authority and qualification. 084 statistics and 086 acceptance/publication continue consuming the shared execution evidence, now backed by the concrete 144 original completion consumer. Policy approval and SIM success alone remain ineligible.

## Important boundaries

- Completion is not PASS or production eligibility. Fixed result reads explicitly return `production_eligible=false`.
- Execution evidence now reads the formal definition metadata rather than requiring an already finalized scoring result. This removes the execution -> finalized score -> execution cycle without bypassing the later acceptance result and policy checks.
- Qualification is checked both at score time and at current consumption time; expired/revoked qualifications and inactive expert membership cannot support acceptance/publication.
- Migration 144 includes real run/step/result/session/supervisor/output/audit/event FKs, reserved metadata and immutable completion/original-score/result/binding triggers. It seeds no credentials, approvals, scores or completion history.
- `medical.formal-evaluation-execution.enabled` defaults disabled. Enabling requires real node runtime, transport, artifact profiles and scope authorities. Expert-qualified scoring additionally requires `medical.expert-qualification.enabled`. Bean presence is not a claim that any real execution has completed.

## Deferred verification

Per the user's request, no tests, full build, service startup, database migration, provider call or network exercise was performed for this slice. Necessary narrow production-source compilation is tracked separately by the integration owner.

The subsequent verification pass must cover: normal full-matrix run -> fixed results -> independent scoring -> finalize -> 081 binding -> 084/086 accept/publish; missing/duplicate/cross-cell nodes; stale leases; mismatched candidate/rule/output; unknown or unsettled costs; unresolved obligations; no independent closure; raw host FINISH; N00/non-professional output; expired/revoked/ambiguous expert qualification; inactive expert; candidate self-score or self-finalization; forged/cross-tenant receipts; immutable update/delete rejection; completion idempotency races; SIM results never becoming formal; expired current qualification invalidating acceptance/publication; provider transport and original byte re-read failures.

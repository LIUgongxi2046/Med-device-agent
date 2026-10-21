# A2 registered tools closure (2026-10-20)

Status: code complete, deferred verification. No test suite, build, migration, database, provider, deployment, or network execution was performed for this closure.

## Reused actual implementation

- `TaskRegisteredToolAuthority` binds N02 Researcher/NetworkResearcher calls to the actual model call, supervised host session, frozen input/replay, role and approved capability identities, permissions and current grants. The existing deliberately closed N02 boundary remains; this does not grant these tools to N01/N03–N07.
- `TaskRegisteredToolBridge` reads exact authorized predecessor EvidenceBundle bytes or a fixed approved Skill package through the governed object services. It does not accept filesystem paths or arbitrary registered UUIDs. Invalid UTF-8 Skill bytes, unsupported media, incomplete/sliced artifact requests, mismatched digests or current authorization changes fail closed.
- `TaskRegisteredToolReceipts` stores the full returned content and actual physical-read witness, actual invocation/action/lease linkage and immutable audit/outbox receipts. It rechecks original history independently and separately requires provider-send permission when content enters a later model request.
- `TaskRegisteredToolWaitingService`, the V2 gate/finish/completion/commit path and human answer authority already persist one genuine question per node/action, terminate the current model loop, and record WAITING_INPUT without pretending to produce a professional result. Answers require the actual assigned owner, original waiting commit, independently confirmed changed business inputs and independently created new run. Answer prose is never an approved fact.

## Closed gap: answer versus actual continuation

The answer receipt continues to mean `LINK_CONFIRMED_REVISION_AND_NEW_RUN`, with the obsolete waiting item becoming STALE. Recording this real answer and invalidating the obsolete baseline must not depend on Temporal launch availability.

The authorized question read now adds a separate observational `continuation` object after the answer's immutable recovery is verified:

- `CREATED_NOT_STARTED`: the separately authorized RN01 creation is real, but no actual Temporal acceptance was recorded. A launch intent or attempt alone does not upgrade this state.
- `WORKFLOW_ACCEPTED`: the original registration, launch, attempt and actual Temporal acceptance all verify, but no accepted-start transition is recorded.
- `STARTED`: the actual acceptance plus the original accepted-start operation/audit/outbox and task-run start timestamp all verify. This remains an historical start fact even if the target later finishes; `is_current_run_status` is false.

Every state retains `can_resume_frozen_run: false`. Observational state never modifies the answer receipt, creates a new run, calls Temporal, authorizes a worker, or wakes the old frozen workflow. Missing expected records or contradictory start timestamps fail closed rather than being repaired. Existing F1/F2 model/host/backend provenance and recovery contracts remain in force.

## Deferred checks added

Source tests cover creation/intent/attempt versus acceptance, receipt tampering, revoked question proof, historical accepted-start after terminal run state, missing start journal, missing/cross-run acceptance and receipt hash mismatch. These are explicitly boundary doubles, not real SQL/Temporal/provider evidence. Run these with the later consolidated regression, including existing registered-tool/history/waiting and accepted-start suites. Browser and real customer acceptance remain NOT_RUN.

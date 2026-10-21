# N07 saved-document professional review

## Implemented core

- `DocumentRevisionService.finish` creates an immutable, audited `CHECK_DOCUMENT` intent from the original completed DC-02 operation in the same transaction. It preserves the original operation ID, idempotency key, request digest, actor, actual body revision and digest. A repeated command cannot create a new operation identity. New saved sections enter `DRAFT_CHECK_PENDING`; existing saved-check and human-review invalidation remains active.
- `DocumentReviewAuthority` accepts only the existing complete physical `DocumentReadService.FixedRead`, retaining exact validated body bytes. It fixes document/body/schema/plan/task/snapshot/access-set/node/field/source/watermark baselines and rechecks current document and original-source authority. Worker reads use bounded `EvidenceReadAccess`, never a fabricated human session.
- Author-session independence is proved from actual N04 object provenance and persisted runtime sessions. An empty writer-session set is accepted only for a real completed human DC-02 save of that revision. Missing generated provenance does not become a human-origin claim.
- `DocumentReviewRules` independently reconstructs approved template/plan/profile dependency closures, authenticates rule versions, source rights and fixed content hashes, and recomputes explicit completion rules using the fixed project attributes. Unknown or failing completion conditions cannot be overridden by a model. N07 does not accept unauthenticated model `NOT_APPLICABLE` results.
- `TaskSourceVerifierNormalizer.normalizeDocument` shares the independent original-read and full-coverage algorithm but rejects CANDIDATE findings. It never relabels candidate PASS. Required claims are derived from actual saved text/fields, with unreferenced content remaining uncovered.
- `DocumentReviewResultService` consumes only the invocation service's private terminal proof, whose genuine invocation, accounting, independent reads and successful process closure are verified. It produces real DOCUMENT findings and coverage. Host output action and model invocation action remain distinct.
- `DocumentReviewObjectLedger` persists immutable canonical byte intents and committed first-attempt records. Only physical `RuntimeObjectTransport.VerifiedTransfer` can register an artifact. Unknown transfer outcomes recover the same object; no replacement write is inferred. Output dependencies and lineage use the real exhaustive context closure.
- Real `check_run`, issues, audit and outbox records are persisted after both artifacts are registered. Only complete, unblocked results advance the actual sections/document to `REVIEW_PENDING`. No human approval or signing permission is created. Completed candidate TASK runs are never reopened.
- Result recovery verifies original execution and result identities, physical-registration receipts, current body/source/rule authority, watermarks, auth/governance epochs and original genuine invocation provenance without reviving an expired lease. A transaction-bound private completion proof supports the exact post-result transition after the result itself advances issue watermarks.

## Integration responsibilities

Migration 122 owns original-intent, output-intent/attempt/registration and result tables. Migration 124 owns execution/launch/completion; runtime and invocation migrations are maintained separately. The parent integrates master migration ordering.

The runtime host must reconstruct current private proof in each transaction, perform object transfers outside transactions, register exact physical results, submit the result, and consume its private completion in a separate current-control transaction. Arbitrary JSON, an exit code, a technical-check result, or a candidate check is never sufficient.

## Explicit remaining scope

The explicit DC-03 preview and one-use human confirmation path now persists a genuine saved DOCUMENT revision, including INITIAL adoption from the authorized blank draft. Its atomic completion enqueues the separate CHECK_DOCUMENT job against that actual revision. It never automatically adopts generated text or reopens a completed TASK. See candidate-adoption-and-comparison.md. Implementation is integrated; runtime and centralized tests remain deferred.

## Verification status

Necessary narrow `javac` compilation of the core files succeeded during implementation, with annotation processing and implicit class emission disabled. Recompile after the final concurrent helper integration. Unit/migration regression source was added, but no tests, full builds, provider calls, runtime execution, network operations or push were performed during this code-only phase.

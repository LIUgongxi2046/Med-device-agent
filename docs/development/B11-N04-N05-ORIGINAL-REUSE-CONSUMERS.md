# B11 N04 / N05 original-success consumers

Implementation scope: original successful candidate and independent-review reads in a RESUME successor. No signed stage schema was changed. Original COMMITs stay in their original run. Successor reuse is represented by the separate lifecycle-owned reuse receipt with generation zero.

## Reader chain

- `TaskSectionWriterInputAuthority.requireOriginal` and `TaskSourceVerifierInputAuthority.requireOriginal` require a private `TaskRunResumeOriginalAuthority.Source`, verify the registered successor input under current rights, compare the complete semantic input, then validate the original physical input registration. Original run, step, object, and receipt identities remain unchanged.
- Only transport-local run, step, input, graph and replay identities are normalized. N04 predecessor step IDs are normalized but predecessor object and completion receipts are not. N05 run-local rule-set IDs/hashes are normalized only because both actual registered manifests are independently checked against the same exact approved rule member list.
- `TaskSectionWriterStageCommitService.ForConsumer` adds a typed read-only original completion proof parallel to N05. It verifies the actual stage journal, original dispatch reservation, candidate registration, invocation settlement and closed supervisor through existing StageFacts. Both stages expose typed original reuse proofs to the lifecycle-owned producer.
- `TaskSourceVerifierTargetAuthority` resolves a candidate from another run only through the exact successor N04 reuse edge and re-verifies the original writer completion. It still checks current replay closure, source membership, original candidate/body physical registrations and current task read authorization.
- N05 preparation and N06 assembly select either current-run candidates or candidates joined through exact current-run N04 reuse edges, never unrestricted historical candidates.
- `TaskSourceVerifierCommittedReviewAuthority` resolves an N05 alias to the actual original registered input and passing stage COMMIT, preserving independent read receipts and current watermark/resource checks.
- N06 obtains writer input through the verified candidate target so original writer input receipts are retained.

## Deliberate boundaries

A changed N03 field-check artifact/object/receipt does not silently qualify for N04 reuse. Such a successor requires normal N04 execution unless the lifecycle's original-field-output strategy preserves those exact verified input pins. No original actor impersonation, expanded bootstrap scope, new original-run action, copied COMMIT, fabricated lease, new provider invocation or successor session is introduced.

## Verification status

No tests, full build, provider calls, services, migrations or network actions were executed. No javac or Java AST parser is available in this environment. `TaskResumeSemanticInputsTest` was authored for deferred execution. It covers routing-only changes, original predecessor receipt substitution, candidate identity substitution, rule-set identity normalization, and changed field receipt rejection. Runtime integration and database-backed positive/negative cases remain unexecuted under the user's skip-tests instruction.

## N03 and initial-document dependency closure

The integration now includes genuine N03 original completion, not rerun-output equivalence:
- Source-bound original reservation, original deterministic strict-field evaluation/source receipts, actual registered field output and stage COMMIT are rechecked.
- `TaskStrictFieldWriterReadAuthority.requireOriginal(Source, publication, policy)` returns a private `ForWriter` proof exposing the same typed original-receipt API for the shared reuse writer. All semantic N03 inputs remain exact apart from transport IDs and already-verified predecessor step mapping.
- N04 selection reads the original N03 artifact/object/receipt through the saved current-run N03 reuse edge. No field output pin is normalized away.
- `DocumentInitialRevisionService.resumeInitial` reads the actual original initial document, schema, body, field/evidence objects, dependency manifest, node/field/section rows and audit/outbox receipt under that Source. It rechecks the exact producer pins and current frozen template/section/source closure.
- N04 skips initial-document upload/registration when this original proof exists and retains the original document/base-revision/body-schema IDs. Original document `Prepared` proofs cannot enter attempts, transfer or registration APIs. A read-only original initial-producer proof cannot authorize new initial-document creation and does not reserve another three objects.

Additional deferred integration cases: successful N03→N04→N05 reuse keeps all original physical identities; changed N03 target/policy/source pins rejects reuse; missing original initial registration fails closed; tampered initial object, template, dependency member, audit or outbox is rejected; original-document proof cannot authorize write APIs; changed current rule approval/source rights rejects original reads; no successor invocation/session/dispatch or nonzero lease is created by these readers.

## N06 original successful assembly consumer

`TaskCandidateAssemblyResumeReader.read(http, successorReference, callback)` returns empty when no eligible original successful N06 exists. Otherwise it obtains the new separate assembly Source capability, physically rereads all four original registered objects in recover-only mode, then mints a private `Reusable` proof inside the fresh successor transaction. The callback is the integration point for the lifecycle-owned separate assembly reuse journal; no current `PreparedV1`, `ProducedV1`, `CommitV1` or `run_step` is fabricated.

The proof validates original stage journals and successful check receipt, current rights and exact current candidate/review/baseline/cross-file physical-read inputs, independently compares full old/new approved rule manifests apart from their local revision IDs, and compares deterministic body, companion and cross-file report. Assembly manifests are exact after replacing only the independently checked run-local rule revision and removing exactly one original host check ID, which remains preserved in the original artifact and reuse proof. Original artifact/check IDs and all actual physical object versions and receipt hashes are retained.

API: `source()`, `current()`, `successorInputSha256()`, `successorStepId()`, `originalReceipt()`, `originalReceiptId()`, `originalReceiptSha256()`, `artifacts()`, `originalCheckId()`, `originalCheckReceipt()`, `requireUnchanged()`. Proof getters do not authorize execution; the owner must recheck `requireUnchanged()` in the callback before persisting its alias. `TaskCandidateAssemblyResumeReaderTest` is authored but unexecuted. Deferred integration cases include modified or missing physical object, changed candidate/review, changed cross-file read/watermark, current source revocation, missing original stage/check journal, and rejection of mixing an already dispatched current assembly with reuse.

N03 signed stage records retain their existing camelCase workflow wire serialization. The N03 journal's workflow-valued nested fields and reusable original receipt now use the workflow JsonMapper rather than the snake_case business-data mapper. The signed schema itself remains unchanged.

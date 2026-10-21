# N06 deterministic stage integration

Status: implementation written; targeted source compilation only. Unit, integration, database, Temporal and external-object tests are deliberately deferred until the user opens the testing gate. No production gate, worker, service, migration, provider or network operation was enabled or run by this change.

## Closed extension

N06 uses separate `CandidateAssemblyWorkflowExtensionV1` memo keys and version marker. The original V2 deployment and N04/N05 extension schemas are unchanged. The operator-owned extension contains exactly:

- `schema_version`, `revision`, `base_protocol_sha256`, `source_verifier_extension_sha256`
- `prepare_activity`, `dispatch_activity`, `commit_activity`, `recover_activity`, `version_marker`

All activity names are fixed `medical.run.candidate-assembly.{operation}.v1`. There is no professional-execution queue, model call or generic N02 completion alias. The deployment loader requires an absolute non-symlink regular file, exact owner, owner-only mode, bounded bytes and exact SHA-256. Launch intent and actual Temporal acceptance retain separate immutable extension witnesses. Recovery reads the original extension rather than current operator configuration.

The new private POST route is `/internal/medical/task-run/candidate-assembly-stage/v1`. Requests have the closed fields `schema_version`, `operation`, `reference`, `command`, `produced`, including explicit null operation slots. Browser headers and session credentials are rejected by an independent mTLS service-only filter.

## Actual execution

1. PREPARE records the exact current authorized input and its prepared graph/obligation hashes in an immutable audited stage journal.
2. DISPATCH records one immutable command before physical I/O. N05 committed passing review coverage is checked by the current assembly authority.
3. The host produces the actual report, genuine aggregate check, body, companion and final manifest. Stage output accepts only four privately validated physical registrations and a committed check. Returned artifacts are ordered by the closed contract, not by physical creation order.
4. COMMIT compares the complete actual family with the expected output and writes a separate audited stage receipt. `document_saved=false` and `n07_complete=false` are mandatory. Existing committed results return through original-only recovery; a new commit still requires current authority.
5. RECOVER reads original input/command, original physical object versions, registrations, dependency closure, historical producer approval and genuine aggregate-check journals. It retains current task/source/artifact read authorization, but cannot select a producer, PUT an object, register missing output, create a check or write a stage journal. Partial or absent physical families cannot become success.

Temporal starts N06 only after N05 inventory is satisfied. An ambiguous dispatch is never redispatched; only original recovery is polled. The original command is retained in workflow history. A blocked result cannot satisfy N06. Lifecycle success independently rechecks exact commit/object/check/manifest facts. Cancellation cannot manufacture terminal completion for a dispatched N06 with no committed factual receipt.

## Downstream boundary

The four outputs are candidate assembly artifacts, not a saved business document. N07 remains a separate saved-body CHECK_DOCUMENT path. B03/DC03 must perform its own current authorization, explicit confirmation, aggregate materialization and real document save.

`TaskCandidateAssemblyStageJournal.Original` is privately constructed and bound to its admission, transaction, thread and tenant scope. `TaskCandidateAssemblyOriginalReader.Verified` provides the immutable physically read family. These are factual read proofs only and cannot authorize adoption.

## Deferred verification

- Closed wire rejects missing, extra, duplicate or coerced fields and cross-command replies.
- Four originals and real aggregate check are mandatory; blockers prohibit success.
- Lost dispatch/commit replies recover originals without a second write or fabricated registration.
- Producer revocation does not select a new producer; source/task/artifact read revocation still denies reads.
- Memo/config drift cannot change a historical launch; legacy base protocol fields remain unchanged.
- Lifecycle checks original object bytes, receipt hashes, audit/outbox and genuine manifest check references.
- N06 never saves a business document, adopts a candidate or completes N07.

# N04 physical dispatch and original recovery

## Authority chain

The writer scheduler calls `TaskSectionWriterDispatchJournal.dispatch(http, reference, step, command)`. It requires the real accepted workflow reference and current N04 input. `TaskSectionWriterDispatchReservation` writes one immutable reservation with independent audit and outbox witnesses. Only its private, committed, same-thread `Pending` can enter `RunTokenAuthority.claimWriterReserved`; its before/after hooks compare the actual READY step, service certificate/trust revision, original input receipt, step revision and generation. A prior reservation cannot claim again, including when its previous result is unknown.

The claim opens a server-owned N04 HOST_CONTROL action. The actual compiled `Envelope` is authorized by the N04 producer and written through `TaskSectionWriterObjectLedger` and `RuntimeObjectTransport`. The ledger commits one physical attempt before I/O, verifies full PUT/recovery readback, registers the actual `STEP_ENVELOPE` artifact and content binding, then records the dispatch. Its contract is `task-section-writer-dispatch.schema.json`, not the N02 envelope contract.

The returned `WorkflowProtocol.Dispatch` contains the genuine host action, input hash, lease generation, envelope artifact and envelope hash. No dummy envelope identifiers or synthetic completion objects are used.

## Worker transport

- `POST /internal/v1/section-writer-runtime/open-workflow`: exact `{reference, dispatch}` with Java workflow camelCase records. Returns the real PREPARE launch, or COMPLETED with the original AT06 receipt.
- `POST /internal/v1/section-writer-runtime/completed-original`: same body; returns only the actual historical AT06 receipt. Missing completion fails unavailable.
- The arbitrary actor-locator `/open` route is removed.

A fresh PREPARE response consumes a durable worker-launch journal. Repeating an uncertain open never starts another process. Admission and subsequent live worker handle calls require that exact launch witness. A lost response or partial reservation remains unresolved; absence is not evidence permitting a retry.

## Read-only original proof

`requireOriginal(recoveryAdmission, http, reference, dispatch)` yields a privately constructed, transaction-scoped `Original`. It verifies accepted launch, original reservation/claim, operation/audit/outbox dispatch, N04 input, registered envelope/intent/attempt/object/receipt, original producer author and independent approval, and prepared runtime journals. No current lease, source read, model.send, token minting or object transfer is performed by historical recovery.

`TaskSectionWriterCandidateRecovery.original(Original)` supplies the original AT06 submission receipt only after original invocation settlement and independently authenticated successful process closure. It is evaluated before current-lease checks, so a completed original remains readable after the live lease expires. Candidate submission is not document adoption.

Inventory readers use `commands` (including partial reservations), `originalByCommand`, and `dispatched`. They cannot quietly discard an unresolved command and claim a replacement.

## Verification status

Focused Java source compilation performed for the dispatch/reservation/ledger/token/controller/configuration slice. Offline contract tests were written but not run. No full build, database migration, provider call, runtime process, network execution or push was performed for this slice. Migration 113 adds immutable update/delete guards to `task_section_writer_runtime_prepare`; master inclusion is owned by integration.

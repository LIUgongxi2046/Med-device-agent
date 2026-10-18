# E / C07 formal invocation contract (staging)

Original endpoints IN08–IN11 remain `/internal/v1/invocations`, `/{id}/begin-send`, `/{id}/receipts`, `/{id}/reconciliations`. MODEL_VERIFICATION is an explicit real SYSTEM_JOB bootstrap. No model requests, credential generation or permission changes are executed during implementation.

## B/E dependency break

B creates a real immutable verification intent and connection_verification PENDING row, binding actor, exact draft configuration/credential, canonical original request and original key. E receives B's private-constructor intent proof, never a caller-supplied verification UUID alone. E validates actual approved input, role/runtime, rule/price/data-policy dependencies and budgets, writes real execution_registry/system_job_run/run_step/step_action_binding/context/scope/rule records and returns a private-constructor StartProof. B binds the same proof in a subsequent tenant transaction; no independently fabricated counterpart ID.

Bootstrap does not require already-enabled model qualification or prior PASSED verification, only the exact validated B intent for MODEL_VERIFICATION. Ordinary TASK invocation requires current B enabled/PASSED eligibility. Both paths require real model.send authority, current source/external-use permission, exact model/credential/price/data policy, bounded approved content and current budget approval. No purpose fallback.

## Integration APIs (to freeze with B/D)

- `startVerification(ApplicationSessionIdentity.Verified actor, String context, ConnectionVerificationIntentProof intent) throws IOException -> StartProof`: bounded actual fixed-object reads and tokenizer outside the transaction, currentness recheck and actual job registration inside a second transaction.
- `readVerificationStart(EnterpriseAccess.Admission actor, String verificationId) throws IOException -> Optional<StartProof>` is read-only. It returns empty only when no immutable E job exists; damaged associations fail closed. StartProof.invocationId is null until a real leased reserve.
- `reserve(RunTokenAuthority.Admission worker, FixedInvocationRequest request) -> ReservationView` inside actual IN08 token transaction. D supplies a narrow FormalWorkerAccess bridge only where existing source readers require EnterpriseAccess.Admission.
- `beginSend(...) -> Dispatch` returns single-use private DispatchPermit only after commit of RESERVED→DISPATCHING plus immutable attempt. Lost response never grants another send. An HTTP response cannot serialize a reusable dispatch permission.
- `requireCompletedVerification(EnterpriseAccess.Admission actor, String invocationId, String verificationId) -> SettledProof`, private constructor. Rechecks real completed invocation, actual immutable adapter receipt/object, usage/price settlement, model/credential/job bindings and audit/outbox. B consumes proof, never external JSON asserting success.

Exact request JSON and Java accessors remain under negotiation, not yet a frozen frontend contract.

## Mandatory state/accounting rules

Reservation locks accounts in stable ID order and checks exact decimal money/token/call upper bounds against current approved limits and deadline. Enterprise and parent budgets reserve atomically; same account cannot accidentally be double charged. Invocation belongs to the actual leased run/step/action and input hash. Actual lease/service/grants/dependencies are rechecked before dispatch. Immutable attempt persists before transport. Dispatch admission and external outcome are distinct.

Unknown network/send outcome keeps all uncertain reservations; no automatic generation retry. Cancellation before dispatch releases reservations with a real ledger transition; after dispatch marks cancellation requested and requires provider-supported confirmed termination/reconciliation. Receipt ingestion authenticates the original service/certificate/run/attempt and may record facts after initiator read revocation; it must not dispatch new work or claim user read permission. Unknown/missing/unsupported provider usage cannot become zero cost. Execution terminal and settlement terminal are separate.

Prices must come from a real fixed approved profile; generic price_basis references alone do not define arithmetic. Initial supported pricing is explicit token categories with fixed decimal per-million rates and declared rounding to DECIMAL(24,8); unsupported tariffs block admission. The original price version is retained for settlement, not replaced by a newer price. Usage outside reserved limits is recorded as disputed liability, not silently capped or dropped.

## Constraints and remaining real inputs

007 invocation requires real connection_verification, step_action_binding and context_manifest FKs; 076 retains these. Receipt rows require real fixed artifact references. 004 run_step requires real role_version and system_job requires real scope/rule manifests; no random placeholder dependencies. D supplies actual lease/mTLS action proof via existing RunTokenAuthority; SIM worker permission whitelist is untouched. Existing secure resolver, service certificates, approved role/runtime/rules, price schedule, real source permissions and budgets remain deployment/business inputs. Missing input fails closed; none is created as an implicit approval.

## Confirmed initial scope

Root confirmed paid MODEL_VERIFICATION requires a real project and fixed scope_snapshot_basis. Nullable project in shared request syntax is rejected for paid execution until a separately approved enterprise send contract exists. Enterprise-owned sources are usable only through their real project snapshot selection, actual parse/index/approval currentness, MODEL_SEND and source.external.process. Never use REVIEW as an external-send substitute.

The real service adapter owns job execution; it is not a Temporal workflow. 076 must add only MODEL_VERIFICATION to the explicit workflow_id=NULL check branch and bind an immutable ADAPTER_MTLS execution-mode sidecar. Existing worker claim queries remain exact job type. Ordinary TASK workflow behavior is unchanged.

## Explicit business purpose (confirmed)
VerificationRequest.business_purpose is required, enum DOCUMENT_BODY or MODEL_VERIFICATION. It binds the original complete request hash and model.verify decision. Missing fields reject without a guessed default. The SOURCE closure uses this exact rights purpose and actual MODEL_SEND/provider/region/external-process checks. Entry model.verify/model.send purpose remains MODEL_VERIFICATION. Package/rule governance remains its actual separate approved purpose; governance reads never authorize external transmission.

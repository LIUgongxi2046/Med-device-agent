# N07 saved-document invocation integration

This lane is `SYSTEM_JOB` / `CHECK_DOCUMENT` / `N07`. It does not reopen a TASK run, use a candidate receipt as a saved document, or reuse the N01 PLAN_DRAFT input. The published model request parameter `purpose` remains `TASK`; that field is a business-purpose enum, not execution identity.

## Executable path

1. The dedicated host loads a real immutable CHECK_DOCUMENT job, its exact saved document revision/body hash, independently read sources/rules, and physically read approved role/prompt/profile packages.
2. The independently authenticated supervisor observes the actual SDK projection. `DocumentReviewInvocationContextOrchestrator` reacquires current runtime authority around each physical price/tokenizer read, tokenizer execution, and exact-input object write/readback. Only a single text-only first model request is supported; tools/history are rejected rather than silently discarded.
3. `DocumentReviewInvocationObjects` persists immutable intent/attempt/registration (migration 126). The context includes full exact bytes, the saved-body baseline, source/rule read receipts, package pins, observed request, independent session binding, price, limits and both original budgets. Generic artifact reads cannot bypass the dedicated exact-context boundary.
4. `DocumentReviewInvocationService` binds a private deterministic MODEL_CALL action and issues the distinct scoped token. Current model.send, selected-source MODEL_SEND and package transmission checks run again for reservation and at dispatch.
5. The original076 dual-budget reservation, compare-and-swap dispatch generation, provider capture, immutable output transfer/registration and settlement are reused through distinct sealed N07 adapters. N07 outputs are DOCUMENT resources. Settling a model call does not prematurely finish the document review job.
6. A private `Terminal` exists only after actual successful supervisor closure and verification of original result, provider capture and accounting. The result producer receives the model output plus independently verified reads, never caller-supplied findings or a candidate status.

## Gateway deployment

Enable `medical.professional-runtime.document-review-enabled` and the existing formal model invocation deployment. No TASK execution lane needs to be enabled merely for shared package reads.

The `medical.professional-runtime.document-review-gateway.*` settings pin the executable, `document_review_gateway_entrypoint.py`, package tree manifest, private configuration and timeout. Set `medical.professional-runtime.document-review-model-token-lifetime-seconds` to a positive duration no longer than 300 seconds.

The gateway private configuration must select `execution_kind=SYSTEM_JOB`, `job_type=CHECK_DOCUMENT`, `node_kind=N07`, `model_tool_mode=DOCUMENT_REVIEW_N07_V1`. Its closed profile selects only `/internal/v1/document-review-invocations/{id}/{begin-send,receipts,reconciliations}`. Secrets and provider body never appear in argv or ordinary stdout.

## Recovery boundary

After lease expiry/revocation the original authenticated provider adapter may register only already-dispatched original outputs. The original saved-result verifier checks immutable job admission, original physical read journals, supervisor F1/F2 records, dispatch/capture/accounting and object provenance. It returns no live model token, Terminal, new dispatch or new input write.

## Verification status

Boundary and gateway tests were written but intentionally not executed. Lightweight source compilation is a compatibility diagnostic only, not runtime, database, provider, security or integration-test evidence. No deployment, provider call, network runtime, migration execution or full build was performed for this implementation.

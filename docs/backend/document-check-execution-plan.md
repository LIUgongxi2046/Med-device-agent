# N07 saved-document execution and launch

## Implemented boundary

A saved DC-02/DC-03 operation produces an immutable document-review intent. Admission of that intent uses `DocumentReviewStartService`, with the real human application identity, current context, physically read saved body, approved rule closure, actual publication, execution-policy node N07, approved budgets, and current resource/source/retention grants. The input contains actual saved document bytes and baseline, never candidate findings or a fabricated prior TASK run.

`POST /api/document-review-intents/{intentId}/executions` accepts only schema version, agent release, execution-policy basis, enterprise-budget basis, parent-budget basis and currency. No run ID, principal ID, findings, check ID or authority flag can be provided. One immutable intent identifies at most one execution. This explicit admission endpoint is the current bridge from a saved intent; saving alone does not silently select a release or budgets.

The transaction creates a distinct SYSTEM_JOB / CHECK_DOCUMENT execution, principal, fixed scope and rule manifests, READY N07 step and HOST_CONTROL action. The linked TASK/revision/plan/snapshot remain evidence in the input and are never resumed or rewritten. Fixed document.read/document.check capabilities derive exclusively from the private actual-body proof. Existing execution-policy V1 bytes and selector meanings are unchanged.

## Durable launch

The mutually authenticated launch route is `/internal/v1/document-review-executions/launch`; recovery is `/internal/v1/document-review-executions/recover`. Both accept tenantId, runId and originalOperationId and reconstruct the dedicated bounded control authority. Real body/publication I/O runs outside tenant transactions. Immutable launch reference and start attempt commit before Temporal I/O.

A fresh start uses `medical.system.check-document.v1`, distinct control/professional queues, duplicate rejection, original command memo and request ID. Acceptance independently rereads the first Temporal event and verifies exact reference, workflow, original execution run and original memo. A failed or uncertain attempt only queries the original workflow. NOT_FOUND never authorizes a second start. Only actual accepted evidence transitions the new job QUEUED to RUNNING.

The workflow returns only the registered actual-document check receipt and compares target document/revision/body/baseline/input hashes. Completion consumes the result service's private transaction-bound proof. It updates only the new job and step; no professional approval or signature is generated. Original receipt recovery is immutable and does not regenerate findings.

## Runtime integration

Separate Python modules provide the N07 activity, control client, independent supervisor plan/admission reader, pinned deployment and TLS relay entry. Host launch purpose is SYSTEM_JOB; the persisted identity remains SYSTEM_JOB/CHECK_DOCUMENT/N07. C07 published model purpose remains TASK, not a new unsupported purpose enum.

The independently authenticated prepared envelope selects a dedicated request-observation schema. The executor's `DOCUMENT_REVIEW_N07_V1` mode allows no tools, compaction, title calls or tool history. N01/N05 paths retain their old schema, queues and defaults.

The prompt validator verifies canonical immutable input bytes, actual saved body, exact body object/version/hash, current rules manifest and rule read receipts, complete source references and original/parsed object pins, separate verifier session, and writer-session exclusion. These are integrity checks; authority still comes from current Java permissions, mTLS, signed lease/action token and independent supervisor observation.

## Delivery checks and remaining integration responsibility

Migration 124 owns immutable execution, launch intent/attempt/receipt and completion ledgers. Runtime migration and dedicated source-read/result/object ledgers are supplied by their owning components. Root integration must order migrations after document intent migration 122.

Tests have been authored but not run, as requested. Python AST compilation was performed without imports or execution. TypeScript no-emit and narrow Java compilation are development-only syntax/API checks. No provider calls, Temporal service, application runtime, database migration, test suite, network operation or push was performed by this slice.

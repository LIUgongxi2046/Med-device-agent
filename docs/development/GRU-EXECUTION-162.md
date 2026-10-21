# B12 additive Gru execution

Implemented source path: document Gru panel → dedicated explicit execution form → `/api/gru-conversations/{conversationId}/execution/actions` → exact request/target-version pending action → separate authenticated confirmation → existing production authority → current-authorized tool/result projection → immutable AGENT message and audit/outbox event.

The existing record-only conversations, append receipts and exports remain unchanged. They do not silently become executed conversations. The new action/message tables are a separate versioned domain.

## Real execution and prerequisites

- RETRIEVE uses existing RetrievalService with exact snapshot source/parse/index/approval/rights pins and independently current VERIFICATION rights. Empty real search is a result, not fabricated evidence. The shared internal fixed-selection SQL value type is reused; no worker identity or model receipt is constructed.
- CHECK uses DocumentTechnicalCheckService, real object writer and persisted receipt/read/recovery. Technical success is not professional approval.
- QUERY and PROPOSE_EDIT use an existing approved TASK with exact same project, snapshot, base document and exact user goal text. Explicit parameters include task/run revisions, plan and budget. RunCreateService is the only launch path; RunReadService is the current-authorized state source. On genuine SUCCEEDED, DocumentCandidateAdoptionEntryService materializes and rereads original candidate output with existing provenance authority. This is a task-backed tool result, not a general-purpose streaming conversational LLM answer.
- ADOPT_EDIT is opt-in. Full original DC03 arguments must match persisted owner preview, its digest and target document/base. Confirmation invokes the genuine DC03 one-use adoption service, never SQL mutation of project/document facts.
- A STARTING outcome remains uncertain if interrupted. Recovery invokes only the original downstream idempotent key and immutable parameters. Technical-check completion can be recovered after a session change; unfinished session-bound technical work requires its original session rather than forging its identity.
- Cancellation of PENDING is local and final. RUNNING forwards the real run cancellation request and original key. Accepted cancellation is not terminal; only current runtime CANCELLED becomes Gru CANCELLED. STARTING must be recovered to identify the real target before cancellation.

## Personal memory

Owner-only HABIT/SUMMARY author/read endpoints store presentation-only text. `/owner-summary` produces bounded exact USER quotations from verified old messages. Active typed habits are captured in an immutable, versioned per-action presentation context at explicit confirmation. The actual Gru result-wrapper producer consumes those pins, rechecking current ACTIVE status, revision and hash every time. Removed/edited pins are excluded; new executions capture only current active habits. User-selected paragraph/bullet and compact/full options change the actual assistant result text and UI expansion. Free-form prose and summaries are never injected into model/action parameters, project facts, source permissions or grants.

Published template and fixed project conventions always take priority: canonical task/candidate/document output is never rewritten by personal habits. The consumer is explicitly limited to GRU_RESULT_WRAPPER_ONLY. The pinned template/document hash remains checked; the formatted explanation sits beside the unchanged authorized result. This is presentation personalization, not unconstrained personal instructions to the formal writer.

Owner-only CAS modification and removal are wired through the API and UI. Removal stops current/future consumption while retaining the original records and required before/after audit. Summaries include original conversation revision and USER message IDs/content hashes and are marked non-evidence. Maximum 100 active personal entries avoids silent preference truncation.

## API and recovery

All routes require real identity, current context, conversation owner and current resource/source authority. Mutations require CSRF. Requests are bounded, strict duplicate/trailing-JSON parsed, and additive schema-manifest bound. Pending action has immutable original request/body hash, target version, expiry, owner and session. Confirmation is exact boolean true plus original hash, parameters and target. Identity/basis/result/confirmation immutability and monotonic state revision are guarded in migration 162.

Frontend shows full immutable request and requires an explicit separate checkbox/button. Unknown propose retains the original key/body in component memory for exact retry; durable action history permits returning to known actions. No automatic retries or changed-parameter retries. Historical record-only export is untouched.

## Verification status

NOT_RUN: Java compilation (javac/mvn absent), all tests, builds, database migration, DB concurrency, actual providers, object store, runtime/Temporal and browser acceptance. Deferred source-contract tests are provided under `tests/deferred/test_gru_execution_contract.py`; they are not substitutes for those deployment checks.

Required deferred live cases: concurrent confirmation/recovery; interrupted after downstream completion before Gru receipt; cross-session technical check recovery; cancellation race and quiescence; source/rights revocation during result read; task/plan/budget mismatch; stale adoption and full exact arguments; no cross-owner access; immutable old exports; real candidate outputs and provider trace; UI identity change and unknown retry.

Adoption authoring has its own `/adoption-preview` producer. It invokes genuine DC03 preview, then reads owner-bound canonical arguments and builds the proposed Gru action. The client does not have to invent or reconstruct arguments from a digest. The preview itself is not adoption confirmation.

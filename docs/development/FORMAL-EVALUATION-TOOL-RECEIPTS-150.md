# Independent EVAL tools and original history (150)

Implemented production entry: `POST /internal/v1/evaluation-runtime/tool`, authenticated through the independent worker mTLS boundary. The route accepts only exact input arguments and obtains the private settled C07 ToolCall proof itself. It does not accept output bytes, TASK authority, or host-synthesized calls.

- AT01 performs bounded literal-substring retrieval over the complete, fixed, physically consumed source/parse scope. Full processor result validation applies even to empty results. It records algorithm, scope, matched evidence, original objects and read provenance.
- AT02 selects an exact authorized source/parse/chunk and verifies original bytes, complete processor output, locator and quote hash. No partial read is presented as complete.
- AT03 reads only registered context artifacts already physically consumed under current EVAL governance.
- AT04 selects an approved Skill version and its exact physically consumed source artifact. Context preparation now includes approved Tool/Skill source artifact roots, rather than inventing package contents.
- AT08 records a genuine unanswered question in `formal_evaluation_tool_question`. It produces WAITING_INPUT, blocks subsequent model history consumption, and is rejected by `requireCompleteOriginal`. No synthetic answer or PASS is produced.

All paths require exact published input/output schemas and current role/tool permission grants. EVAL tools use the standard `tool-success` output contract and independent `EVAL_TOOL_RECEIPT` provenance, never the TASK-only read-result kind. The per-call action is tied to its originating actual model action and frozen input/session/lease. The tool receipt and question tables have tenant/owner foreign keys and immutable update/delete triggers.

History replays original receipts in actual model-call order and checks exact model settlement pins, arguments, response digests, audit and outbox. Missing historical tools are never re-executed or synthesized. Current sends independently recheck provider-specific fixed-content transmission permissions. Factual historical verification does not manufacture a live lease.

## Deferred verification

As requested, no tests, database migration, runtime launch, provider call, network operation, or full build was run in this work. Necessary narrow production compilation is coordinated with C07.

Required later tests: mTLS rejection; invented/stale/duplicate/cross-session ToolCall rejection; replay of original receipt; missing receipt fail-closed; full-scope EMPTY search; malformed parsed bundle; mismatched locator and quote; role/tool/schema revocation; sibling artifact or unapproved Skill rejection; concurrent identical call; provider grant revocation between prepare/send; AT08 waiting blockade; migration ownership and immutable triggers. AT03 deliberately does not admit arbitrary predecessor artifacts outside the physically registered context scope.

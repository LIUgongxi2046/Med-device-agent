# A12 cancellation and original runtime recovery

Implemented in this recovery pass (not a claim of whole-A12 completion):

- Dedicated EVALUATION service endpoints `/internal/v1/evaluation-runtime/cancel-plan` and `cancel-cell` now match the existing Python Temporal consumer. They require the original accepted cell reference and current EVAL service authority.
- The cancellation intent fences new dispatch, closes admission only (not a claim of process termination), retains all original prepared session bindings, and closes original C07 reservations through the dedicated evaluation cancellation authority.
- Cancellation completion independently fetches authenticated supervisor ADMITTED/CLOSED evidence outside transactions, validates exact original process membership/exit observations and export bytes, then records in a fresh authority transaction. It supports a lost application START without creating a fake runtime session or opening admission.
- Every issued host launch needs original termination evidence. A prepared launch never issued to a host is separately identified; it is not an invented exit. An uncertain launch with no independent termination proof remains UNKNOWN.
- Every invocation requires original provider-terminal accounting or the existing authentic before-send cancellation disposition. No fabricated usage, settled zero amount, or final status is accepted.
- CANCELLED cell receipt records session terminations, invocation dispositions, completed node receipts, and every unfinished obligation. The Temporal detached cancellation scope continues polling until genuine CANCELLED, rather than treating UNKNOWN as finished.
- Terminal retry rereads the original persisted receipt. Normal completion now checks run-wide invocation quiescence and cannot race past an existing cancellation fence.
- Stage input original recovery physically reads the registered immutable object and rechecks original compiler/dependency/context evidence. It does not call the input producer, tokenizer, or upload transport. Original successful node recovery also ensures the private completion consumer receipt exists.

Verification boundary: production Python AST parsing covered six evaluation modules. No tests, full build, migrations, services, provider calls, network, or pushes were executed. Source-only deferred regressions were added to `professional-runtime/tests/test_evaluation_runtime.py`. Java compiler and executor-local TypeScript dependencies were unavailable; Java changes remain uncompiled here.

The same pass now includes dedicated pure N03 and model-free N06 deterministic producers, private immutable execution/object/outcome journals (migration 159), original artifact ownership dispatch, typed completion, per-node input ledger, scoring/current-eligibility consumers, and predecessor availability. N03 uses the strict field comparison engine; pure N06 runs original chapter assembly, independent review, applicability, and cross-file checks. Neither creates a model invocation, professional session, tokenizer run, fake read, or fake check_run. Actual process-backed nodes keep their independent supervisor and C07 path.

Fixed execution-result GET now has a separate ORIGINAL_FACTS_ONLY reader: immutable originals, hashes, supervisor/provider receipts and current object read authorization remain required, while historical facts do not rerun a changed deterministic implementation. This factual capability cannot satisfy scoring/publication; those retain strict current implementation/source/approval checks. Factual responses explicitly set current_use_eligible=false.

Whole-A12 integration remains subject to parent source review; execution validation is intentionally deferred. An issued launch lacking authentic original process evidence or an invocation lacking original accounting remains unresolved, not CANCELLED.

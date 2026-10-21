# B07 development integration, 2026-10-20

Implemented producer-to-consumer path:
- BX-06 v1.0 preserves the published CheckDetail schema and digest. N07 and N05 read actual findings and coverage objects, not a database status alone. Exact target, input, approved rule identities, complete required claims/rules, physical hashes, original receipts and current sources/lineage are checked. N05 remains candidate-only. N06 uses its physical original-family reader and explicit v1.1 CANDIDATE_SET shape, with the private HumanRead authority separating the current authorized reviewer from the original producer actor; complete stage-family receipts are rechecked. Technical checks reuse their independent physical reader and are never professional PASS.
- RV-02 requires an exact document revision, fixed original body/source authorization and actual anchor validation. Pagination/filtering explicitly confers no professional coverage. RV-03 has a closed additive request/result contract, body/issue/watermark CAS, valid active assignee checks, audit/outbox, immutable original operation recovery and source/artifact reauthorization. RESOLVE requires newer physical check evidence for the same document, matching rule plus anchor/ancestor scope, supported corresponding claims and complete full-body coverage; omitted/filtered findings alone do not resolve anything.
- N07 original input manifest/hash are unchanged by output effects. The producer stores actual issue occurrence links preserving the original finding and check. Semantic identity excludes run-dependent issue_key and human-readable message; CREATED/REOPENED/UNCHANGED effects are recorded. Only actual state transitions increment watermarks. Output marks are read back and checked against that exact transition count. Repeated operation recovery adds no occurrence or transition.
- The B08 transition is accepted only through its privately constructed CurrentBatch: immutable preceding-check receipt, full batch and exact +1 evidence transition. B07 then independently checks every remaining target/input/issue/rule/coverage branch. This is not a general stale-check override and does not authorize professional approval.

Configuration:
- Human check/issues API uses existing medical.professional-runtime.document-review-enabled, configured DocumentUploadPolicy object and lineage limits/profiles, real source permissions and object transport.
- N06 reader additionally needs medical.task-run.candidate-assembly-v1.enabled and medical.task-candidate-assembly.enabled, matching its original reader. Missing configuration or source/lineage authorization fails closed; no provider or test fallback.
- Migration 133 adds immutable decision and occurrence ledgers plus optional issue semantic identity. Root owns migration master registration and global coverage bookkeeping.

Validation boundary:
- Only narrow Java 17 production compilation was performed; final B07 slice exited successfully at 16:51 UTC, output cache /tmp/b07-compile. Shared N06 HumanRead classes were consumed from /tmp/n07-core-compile.
- No tests, complete build, migrations, DB/provider/runtime/network operations or push were performed.
- Deferred pure contract/coverage tests are in ProfessionalIssueContractTest. They do not establish SQL transaction or object-store guarantees.

Deferred integration/e2e checks (must use real authorized fixtures):
1. N03/N05/N06 outputs → N07 → BX-06 v1.0/v1.1; verify exact schema headers and no candidate/structure promotion.
2. Revoke document/source/artifact/rule access before read, between physical chunks and before commit; ensure no result leak/decision write.
3. Same idempotency key/original request recovery after expiry and after own watermark change; changed request rejects, concurrent CAS commits one operation only.
4. Same finding across repeated runs preserves original issue provenance, occurrence mapping and exact CREATED/REOPENED/UNCHANGED delta; same run retry produces zero new effects.
5. Stale body/input/rules/evidence/issues, filtered page, missing physical artifact, altered intent/receipt/audit/event or unknown schema fail closed.
6. New matching rule and claims resolve; unrelated rule, removed target node, non-supported claim, old check and incomplete coverage reject. Resolution/reopen invalidates checks through actual issue watermark change.
7. N07→B08→B09 exact +1 works without unnecessary rerun, but additional evidence/issue/rule/body changes reject even when evidence mismatch is the first visible reason.

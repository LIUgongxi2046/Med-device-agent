# N03 typed comparison governance and original numeric provenance

Status: implementation candidate, tests intentionally deferred until development is complete. No production permission, professional rule approval, source approval, or provider result is seeded or claimed.

## Normative scope

- Fixed ledger A4: explicit operator, precision, unit, model/condition, applicability, coverage and implementation; independent comparison approval; actual authorized original numeric provenance.
- LLD-Agent AS-05/AS-06, N03, V04: preserve original values; exact Decimal; unit conversion requires approved formula; complete indicator/model/condition combinations. This implementation supports TEXT_EXACT and DECIMAL_EXACT only, never conversion, tolerance, locale guessing or rounding.
- LLD-后端 data types: DECIMAL(38,12), original string retained, overflow rejected.
- Existing RuleVersion remains Boolean applicability only. Existing 096 remains TEXT-only source-exact preparation. Neither is comparison-policy approval.

## Additive route contract

All routes require the standard authenticated human session, X-Schema-Version: 1.0.0, X-Context-Revision, and no query parameters. Writes require Idempotency-Key. Requests use closed snake_case typed records in StrictComparisonProtocol; missing creator properties, unknown properties, duplicate JSON keys, trailing tokens and scalar coercion are rejected.

- GET /api/projects/{project}/strict-comparison-context: actual installed engine/parser identities and supported dimensions; both policy-read and evidence-read required; no secrets or approvals.
- POST /api/projects/{project}/strict-comparison-policies: Author(schema_version, scope, definition, reason).
- GET /api/projects/{project}/strict-comparison-policies/{id}: immutable definition/receipt and separately verified state.
- GET /api/projects/{project}/strict-comparison-policies/{id}/approval-baseline: baseline and canonical baseline_sha256.
- POST /api/projects/{project}/strict-comparison-policies/{id}/decisions: Decide(schema_version, expected_revision, definition_sha256, baseline_sha256, decision, reason).
- POST /api/projects/{project}/strict-decimal-evidence: Parse(schema_version, scope, evidence, rights_revision, purpose, numeric_span, unit, parser, reason).
- GET /api/projects/{project}/strict-decimal-evidence/{id}: exact numeric evidence and receipt, explicitly not professional approval.

Scope fixes task_id, task_revision, plan_revision, template_version and scope_snapshot. Definition fixes operator, precision(mode and scale), unit(mode and literal), applicability(state, nullable rule for unconditional fields, fixed scope version/hash), explicit coverage, actual implementation identity, and nonempty source/template/rule basis. Null unit means explicitly unitless, not unknown. Unknown units cannot produce evidence. Unknown applicability cannot be approved. A field's actual applicability rule must match the independently computed template condition.

Unit source is SOURCE_SPAN with an explicit non-overlapping code-point span, UNITLESS with null span, or UNKNOWN with null span. No expected decimal or candidate value is accepted. Numeric precision is derived only from actual exact lexical text. The implementation/parser revision UUID is deterministic over the semantic code revision and actual compiled class SHA; it is an implementation identity, not a professional approval or release record.

Permissions are project scoped: strict_comparison_policy.write/read/approve and strict_decimal_evidence.produce/read, purpose STRICT_FIELD_COMPARISON, in addition to existing actual task, plan, template, profile and source permissions. No grants are provisioned.

## Persistence and current authority

Migration 102 adds immutable strict_comparison_policy_revision, strict_comparison_policy_decision and strict_decimal_evidence_revision, plus the mutable strict_comparison_policy_state CAS projection. Definitions are never modified by decisions. Each immutable row verifies exact version-registry, operation, audit, outbox and receipt content when re-read. Decisions require independent approval action and current baseline, and current consumers re-read the exact approved decision/state.

Human author/approval preserves the existing READY task and confirmed-plan admission. StrictComparisonRunScope separately consumes the privately minted TaskNodeProductionAuthority.Verified, verifies fixed replay task/plan/template/profile/snapshot identity, current approved template and source rights, and rechecks the real running production authority. It never changes human READY guards. Only the running scope proof can invoke fixed-run policy/evidence consumers.

Numeric production reads and hashes all physical original bytes plus the exact registered parsed object, verifies the existing successful processing command/result and parser manifest, finds the original-derived chunk and exact locator/hash, parses a selected Unicode code-point span, and stores a source-specific immutable receipt. Source rights, plan and mapping-independent authority are reacquired around every bounded object segment and before persistence. Cached receipts are not used without current revalidation. Values are never populated from a candidate.

## A5 integration

- StrictComparisonRunScope.require(realRunningProductionProof) returns privately minted Verified.
- StrictComparisonPolicyService.requireApprovedFixedRun(runScope, revision, sha256) returns verified definition/scope/decision with requireUnchanged().
- StrictDecimalEvidenceService.requireCurrentFixedRun(runScope, revision, sha256) returns verified value(), provenance(), evidence(), parsed(), unit(), receipt() and requireUnchanged().
- Numeric read receipt IDs are exact registered immutable evidence revision IDs. ParsedDecimalEvidence pins that row's content digest and the actual lexical parser class revision/digest.
- A5 must independently resolve the complete target inventory, call definition.requireCoverage, bind candidate and source contexts, and compare all entries. Policy approval is not template-wide completeness or medical verification.

The initial supported policy basis kinds are exact approved template/current template rules and authorized original sources in the confirmed plan. Other kinds fail unsupported rather than skipping lineage or rights checks. Formal capability action grants remain necessary; the new API does not bypass ResourceReadAuthority.

## Deferred gates

Unit tests supplied: StrictComparisonDefinitionTest, StrictDecimalLexicalParserTest and StrictComparisonCodecTest. Full persistence, authorization, migration and concurrency/integration tests remain to be executed and expanded by the root gate. Actual customer-approved policy/source fixtures and provider execution are not fabricated.

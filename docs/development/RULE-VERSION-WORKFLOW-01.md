# TP-03/04 rule version workflow

Date: 2026-10-16. This record describes the bounded implementation slice. It does not establish customer approval, a deployed release, or database transaction acceptance.

## Delivered code

- TP-03: `POST /api/rule-versions` stores an immutable rule draft in the original `rule_version` and `version_registry`. The server owns tenant, version identity, initial DRAFT status, revision, creator, time, permission witness, content digest and manifests.
- `GET /api/rule-versions/{id}` rereads complete content and immutable metadata, comparing the original projection, registry digest and physical evidence lineage.
- `GET /api/rule-versions/{id}/approval-baseline` returns the fixed revision, complete content, evidence references and current permission/rights fingerprint for review. This read does not approve anything.
- TP-04: `POST /api/rule-versions/{id}/approval-decisions` accepts only an explicit APPROVE, RETURN or DISABLE, human reason, current revision and exact baseline. It reuses `TemplateApprovalService.nextStatus`; approved content cannot be edited in place.
- Rule expressions use the existing published `rule-expressions.schema.json`, `TemplateApplicabilityProtocol.RuleContent`, `TemplateApplicabilitySchema`, original SpEL AST allowlist and immutable canonical content identity. Unknown inputs remain UNKNOWN. Neither expression success nor a UI preview constitutes approval. Boolean operators are checked throughout the complete AST, including beneath comparisons; known nonboolean constants cannot serve as boolean operands. Numerical constants must survive canonical JSON encoding and exact mathematical-value comparison without rounding. Exactly equivalent forms such as 3, 3.0 and 3e0 share one canonical representation before request hashing; any actual value loss is rejected. Unsupported precision is rejected with SCHEMA_INVALID before persistence. Do not round a medical threshold or convert it to a string to bypass this check; values requiring greater precision need a separately versioned exact-number contract.

## Rights and provenance

`TemplateDependencyAuthority.requireRule` reuses the original complete lineage, concrete version ownership, current source rights, permitted environment/region/purpose and source admission gates. Every constant evidence reference must be an exact member of the complete nonempty basis manifest. Project-private evidence is not accepted into enterprise rule definitions. Optional base rule versions must be approved and readable under current rights; RULE_BASE_VERSION is distinct from RULE_DEFINITION evidence lineage.

For new workflow-owned versions, a current approved rule must have a matching TP-04 decision, current state revision, operation receipt, audit event and outbox event before dependency adoption. This check applies where `rule_definition` exists. Legacy approved `rule_version` rows without that new metadata retain the preexisting consumer checks; there is no automatic migration, approval manufacture or retrospective approval claim.

## Atomicity and immutable reads

Creation and decisions run in the existing `EnterpriseAccess` / `TenantTransaction` scope. Approval locks the fixed rule and metadata, checks CAS revision and evidence baseline, advances status and revision without replacing content, advances governance and current session context, and writes the decision, operation receipt, audit and outbox. Failed write counts or inconsistent rereads throw before a success response. Application rollback still depends on the existing real transaction boundary.

Idempotency hashes bind method, target path, session, original context and complete request. Exact approval retries can recover their original receipt after the successful action advanced context. New decisions must use the current context and fresh baseline. Creation retries retain the original draft receipt even after later approval, while rechecking immutable identity and current rights. Revoked evidence rights are not bypassed by replay.

## Contract and migration

The closed workflow schema is `business/medical-persistence/src/main/resources/rule-version-workflow.schema.json`. Its manifest binds its bytes, the original rule manifest and permission manifest. The original rule content manifest is retained so existing applicability and task-plan consumers read the same identity.

Migration `055-rule-version-workflow.sql` adds rule digest referential integrity, immutable `rule_definition` metadata and `rule_approval_decision`. It contains no rule contents, sources, source rights, permissions or approvals. `master.xml`, coverage metadata and the `RuleVersionMapper` tenant subject guard are registered. SQL follows the existing tenant-injecting interceptor.

## Validation boundaries

- `RuleVersionSchemaTest`: closed contracts, executable AST rejection, original consumer content roundtrip, UNKNOWN preservation, exact evidence memberships, digest/receipt integrity, baseline/CAS requirements and state machine.
- `RuleVersionMapperTest`: actual MyBatis mapper loading, tenant injection across new SQL, subject/transaction guards, immutable update shape, separate TP-03/04 operations, decision metadata and original rule consumer table/lineage compatibility.
- `RuleVersionServiceTest`: actual creation/store/read/decision services with explicit mocked authority and storage boundaries; legal create/read/approve/replay, original applicability condition evaluation, stale baseline/revision, revoked rights and failed audit/outbox rejection.

These are engineering component/service tests with synthetic in-memory values. They are not real source authorizations or customer approvals. Actual MySQL migration, HTTP/session workflow, concurrent transactions, rollback and failure recovery still require the authorized local acceptance environment and genuine role/source evidence. Full typed rule publication, directory applicability human decisions and broader template management remain separate work.

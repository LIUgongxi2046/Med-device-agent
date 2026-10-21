# Pre-N04 strict-field target contract

Status: additive technical design and bounded computation implementation. No registered target, current source authority, business rule approval, confirmed fact, N03 result receipt, or runnable TASK is established by validating these objects or running the pure engine. Migration 095 remains reserved for the later planned graph and is not used by this slice.

## Why an additive target is needed

LLD-Agent §4.1, rows N02–N04, places strict-field checking before section writing. `ArtifactProtocol.FieldCheckResult` requires one actual `targetArtifactId` and `ruleRevision`; AT-05 `FieldArgs` already requires an artifact ID and occurrence field IDs. An `EvidenceBundle` has source references and coverage, not candidate-side field values. A future section candidate, section ID, plan-node ID, or source version cannot stand in for a registered field target.

The real `DOCUMENT_FIELDS` producer (`DocumentRevisionService`) projects a saved document body. It is suitable only when that actual document is the intended target, not as a name for a new prewriting artifact. `InterfaceProtocol.FieldValue` also distinguishes occurrence `field_id` from template `field_definition_id`. The new target preserves that distinction; later N04 must persist an explicit target-occurrence-to-body-occurrence mapping.

Existing `RuleVersionService` publishes the Boolean applicability contract `spring-spel-6.2.19-boolean-v1`. Its approval is not approval for a guessed Decimal tolerance, conversion, comparison operator or coverage algorithm. Comparison-rule references in a computation input are data until a future authority verifies an approved, typed comparison-policy definition.

## Bounded contract and computation

Target entries are proposed values, never confirmed medical facts. Each entry fixes the tenant/project/task/input/confirmed-plan/template and assignment identities, actual template field definition and owning requirement/section, explicit occurrence and row identity, model/condition context, unmodified lexical value and provenance. The comparison plan binds the exact whole target digest and exact entry coverage. Authoritative source-side observations are independently supplied; the engine never fills them by copying the candidate side.

The first engine supports only explicit exact-text and exact-Decimal comparison contracts. It preserves raw strings, uses `BigDecimal` rather than floating point, and requires fully specified parsed evidence for Decimal. It does not infer a locale, convert units, round, apply a tolerance, infer model identity from its display name, or collapse unknown conditions into empty arrays. Unit, model, condition, precision and value mismatches have distinct outcomes. Missing observations or usable evidence remain missing; unknown applicability remains unknown; an unsupported parser/operator/type remains unsupported rather than successful or permanently invalid business data.

`PLAIN_DECIMAL_SCALE_V1` is a new local technical parser contract, not a reinterpretation of the published `FieldValue.precision`. It accepts only `-?(0|[1-9][0-9]*)(\.[0-9]+)?`; the raw value, parsed lexical value and `decimal_value` must agree exactly, and precision denotes the fractional digit count. Both operands must have the same explicit unit and precision before exact numeric comparison. No leading plus, exponent, grouping, locale parsing or rounding is accepted. Negative zero retains its lexical representation even where numeric equality holds. This computation support does not establish that any Decimal provenance producer exists.

Shape checks and immutable hashes are not authorization. Schema-checked structures must not be accepted as source-read, approval, registration or current-task proofs. The pure engine does not manufacture `FieldCheckResult.target_artifact_id`, IA-04/check-run records, or success receipts.

## First genuine producer and normal authoring route

The first physical producer should be TEXT-only. `SourceExactService.publish` already verifies exact original substrings and registers immutable `SOURCE_EXACT_REVISION` metadata with source/parse/index/approval/rights/binding lineage. The closed five kinds are MODEL, PTR_NUMBER, DOCUMENT_NUMBER, STANDARD_NUMBER and STANDARD_YEAR. They are not arbitrary template field definitions and their revisions are not artifact IDs.

The normal entry must let an authenticated task actor author a versioned proposed mapping, rather than requiring a developer to fill hidden data. Its authorization must be explicitly specified: existing `plan.write` / `PLAN_MAINTENANCE` describes plan maintenance and cannot silently authorize a new persisted mapping or producer capability. Reuse its authenticated admission, revision, idempotency and audit patterns, not an inferred permission inheritance. The initial UI should expose unresolved mappings and distinguish proposed values from approved facts:


1. Read the exact confirmed plan projection, including genuine persisted research-question IDs, fixed template definitions and source-exact records that the actor can currently read.
2. Submit an explicit mapping of each target occurrence to template field definition, section/requirement, row/model/condition identities and source-exact revision/hash/ordinal. Do not infer these from the source field kind or text. Report missing mapping/context through the existing task waiting flow; do not call it a malformed confirmed plan.
3. Preview the complete mapped-field inventory and unresolved entries. Preserve conditional fields and explicitly empty field inventories; do not silently drop fields because `required=false` or a condition is unknown.
4. Persist an immutable mapping-input revision with authenticated author, task/input/plan/template pins, canonical request hash, idempotency receipt, audit and lineage. Its status remains PROPOSED. The mapping author does not thereby confirm strict facts or approve comparison rules.
5. The host re-reads actual source-exact manifests and original bytes under current FORMAL rights; consumes the literal raw value, not an alias; assigns occurrence IDs only for the actual materialized target and persists their mapping idempotently.
6. Persist a real object intent before transport, recheck all content rights during each physical segment, verify exact versioned readback, then register STRICT_FIELD_TARGET and its complete content/governance lineage under a specifically approved producer capability. Only this actual registration supplies the artifact ID used by N03.

The first producer does not cover numeric or other strict fields. A later numeric producer needs a separately published exact parse contract and full original/parsed provenance. An unsupported numeric mapping must remain visible, not be coerced into text or omitted.

## Rule authoring and authorization boundaries

A typed comparison-policy authoring entry is required before persisted N03 execution: immutable template/field/requirement applicability, exact operator semantics and implementation version, source-authority requirements, model/condition/unit/precision behavior, and any coverage obligations must be explicit. Reuse existing permission/journal machinery for a separate closed policy type and independent approval; do not reinterpret an already approved Boolean rule. No policy or approval records are generated by this development checkpoint.

Current 078 `RUN_REGISTERED_ARTIFACT` actions are read-only, and 093 registration is the dedicated singleton REPLAY_MANIFEST capability. Adding a string to an enum would not authorize the new producer. A later additive producer-capability contract must name only its allowed artifact kind, actual owning task/run and producing receipt, exact publication/policy/role/mapping/target pins and bounds. No wildcard future artifacts, role-status changes, MODEL_VERIFICATION reuse, or inheritance from `task.run` is permitted. Existing 078/093 manifest bytes and semantics stay unchanged.

## Persisted field-check result and orchestration

IA-04 `CheckResultRequest` and the `check_run` table currently permit only DOCUMENT/CANDIDATE. A prewriting field target must therefore have a separate typed field-check receipt with actual target artifact/version/hash FKs, or a fully versioned additive FIELD_TARGET check contract and storage path. It must not be mislabeled CANDIDATE. The receipt must bind the current approved comparison policy, exact input/entry set, original source read receipts, deterministic result digest and genuine producer identity.

Only after that producer/result path is defined should the executable TASK partition be fixed. The previously considered Q+3S+1 count is not asserted: N03 target/rule grouping cannot be selected from section count alone. Preserve genuine plan section/question DAGs and all template fields; distinguish unsupported execution partition from corruption of the confirmed plan.

The existing scheduler should remain the only scheduler. A minimal later materialization adapter holds a closed planned graph separately from `WorkflowProtocol.Step`, whose input hash remains mandatory. It creates input-bound steps only from actual registered input receipts after predecessor results exist. Required unmaterialized nodes must still prevent premature run success. Dependency failure before materialization needs a genuine planned-node/run outcome, not a fabricated failed step with an invented input hash. Missing external inputs need an authenticated receipt-driven wake path, not a permanently pending activity. N07 is conditional on actual saved-body authority and is not an unconditional initial node.

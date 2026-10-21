# B25 implementation slice, 2026-10-21

Status: implementation added; NOT_RUN. No claim of regulatory acceptance or real-sample closure.

## Scope and contracts

PRD §8.5.8 and AC-46, with the explicit IVD/non-IVD separation in §8.5.2/E06.

- RegistrationLifecycleSchema has three disjoint strict records: IvdSelection, ChangeSelection, RenewalSelection. Distinct definition prefixes reject cross-path fields and prefix/payload mismatches.
- POST /api/registration-lifecycle/ivd/specifications, /change/specifications and /renewal/specifications encode only the typed INPUTS definition. Existing B14 authoring, independent review, release publication, fixed template and dependencies remain mandatory. The enclosing scenario may use the existing additive B14 1.1.0 contract when its definition exceeds v1 limits; no historical schema changed here.
- The template is bound by exact version and digest to author applicability, then selected template ID at admission. All source bases must be approved knowledge roots and selected at admission; N04 checks source parse/approval/rights identities and section IDs against the actual frozen plan.
- Catalogue source contains a typed exact approved catalogue with authority, edition, effective date, path and product category. Directory keys/titles must match that source exactly. There is no built-in invented IVD catalogue, and a missing IVD catalogue produces IVD_APPROVED_CATALOGUE_MISSING. Author-supplied nature labels alone do not create approval: B14 and independent physical N05 source checks remain required.
- Structured source-backed path decisions bind path/category/catalogue/template/history; applicability decisions bind directory item/outcome/evidence. The approval decision itself stays in provenance rather than being forced into formal body prose.
- Changes record original/changed facts, reasons/models, risk, validation, clinical impact and instruction/label/QMS impacts, with before/after source documents. Registration history approval binds the original certificate, complete prior-change list and current documents.
- Renewal independently checks certificate validity, mandatory-standard updates, continuing studies and changed technical requirements. It never assumes a statutory deadline or eligibility. Source-backed criteria and structured human assessment bind condition/outcome and evidence list.
- Cross-file comparison preserves original values, units, models, conditions and sources. Conflicting values under matching conditions block; different conditions or insufficient comparator sources remain incomplete, not silently equivalent. Nine grouped PRD cross-file categories must be present.
- N05 checks source literal hashes, physical complete/located/authorized read receipts, exact node/section/content, entity facts in actual body, original certificate number, directory titles and SUPPORT citation links. Undeclared content blocks. Results are machine-only, section-scoped, and force coverage false when unresolved.
- GET /api/registration-lifecycle/tasks/{taskId}/runs/{runId}/results/{actionId} reads only REGISTERED findings joined to the actual task/run/submission; validates payload/receipt/audit/outbox hashes and current artifact/scenario dependency authorizations. Recovery and coverage recompute the same path result.

## Files

Added four production files: RegistrationLifecycleSchema, RegistrationLifecyclePipeline, RegistrationLifecycleController, RegistrationLifecycleResults under business/medical-persistence/src/main/java/com/ruoyi/medical/persistence.
Added RegistrationLifecycleSchemaTest under the corresponding test package. Five deferred synthetic contract checks cover distinct paths, foreign-field rejection, prefix mismatch, missing IVD catalogue and missing renewal history/conditions. They were not executed.
Extended ScenarioSpecificationService, DocumentScenarioInputs, TaskSectionWriterContextReads and TaskSourceVerifierReviewService after B24 released ownership, preserving B19/B23/B24 hooks.

## Explicit remaining evidence and limits

No approved real IVD catalogue/rules, project registration history, actual change/renewal criteria or real sample was supplied or fabricated. Real path acceptance remains blocked pending those inputs and human professional review. No actual legal/regulatory catalogue freshness claim is made. IVD currently conservatively requires actual clinical evidence; a future explicit source-backed exemption contract is not inferred from a generic approved-decision label. Change filing is not silently treated as change registration and remains outside these three routes.

No new tables or migration needed: existing B14 and registered N05 objects preserve creator/time/classification/schema/manifest, immutable/CAS/recovery, audit/outbox and tenancy controls. Reserved migration 174 is unused and can be released by parent. No tests, build, service, migration, provider, OCR or network operations executed. javac and Maven are unavailable in this restored environment. Compile and runtime acceptance are NOT_RUN.

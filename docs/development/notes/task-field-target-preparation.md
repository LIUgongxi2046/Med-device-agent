# Pre-run strict field target preparation (096)

This additive slice registers a proposed, task-owned input before RN01. It does not launch a run, enable RN01, execute N03, grant permissions, approve a business mapping, or establish clinical/regulatory correctness. The existing DOCUMENT_FIELDS, published 078 and 093 contracts are unchanged. Migration 095 remains reserved and absent.

## Normal route

The task detail links to field-target preparation and the separate input-capability page. Current task/confirmed plan/template identity and full inventory are obtained from server authority, not supplied by the browser. Actors author an immutable PROPOSED mapping against exact task input/plan/template/profile/scope pins with mapping-head CAS and an original idempotency key. The inventory retains required, optional, conditional, repeated, unsupported and unassigned fields, plus all nodes/repeat definitions/attachments/current conditions. NOT_APPLICABLE is derived only from actual template applicability evidence; mapping input has no such shortcut. Missing inventory is not a genuinely empty template.

The initial physical producer supports only genuine TEXT from the five existing SourceExact kinds MODEL, PTR_NUMBER, DOCUMENT_NUMBER, STANDARD_NUMBER and STANDARD_YEAR. SourceExact publication remains bound to the requesting project, as existing currentFields requires. Source and original processing ownership may be distinct and are retained separately. Aliases, guessed conversions, numeric extraction, arbitrary user-entered candidate values and provider calls are not producers. Unknown/unsupported inventory returns WAITING_INPUT before source reads or intent writes. A genuinely empty field inventory remains empty and PROPOSED; it is not successful N03 computation.

For each selected assignment, the producer completely reads the original binary object and canonical parsed object, verifies processing lineage and exact Unicode source substring/locator, and records an immutable read receipt. Original and parsed byte authority is checked during each segment. A committed immutable intent/attempt reserves one object identity. The real transport returns verified physical object/version/hash evidence; only then does registration write artifact/content/dependency records and complete the original operation. Complete current source ancestry is checked before intent and on every PUT/recovery GET callback. Governance pins are not granted invented SOURCE content rights.

A retry uses the original key/body/actor identity. An existing attempt is recovery-only, never replacement PUT. A completed verified receipt can be returned without fresh object transfer. Unknown original recovery returns null, not a new operation. Target recovery can complete registration of the original stored attempt, unlike the read-only mapping and capability recovery routes.

## Separate authorization

Mapping requires explicit TASK `task.field_mapping.write` / `STRICT_FIELD_PREPARATION`; production requires `task.field_target.produce` with the same purpose. Input author/read/approve/disable use their separately defined task-field-input actions and `TASK_FIELD_TARGET_INPUT`. Existing task.read/task.run and literal ROLE v1 do not imply these new actions.

The input capability pins an existing registered artifact, actual producing receipt, exact object version/hash/schema, occurrence set/read bound, task/plan/template/mapping identity and genuine 086 publication, approved 078 and N03 role identities. Independent approver authority, original author/approver current rights, disable state and exact transaction-bound proof are checked. A proposal is not an approval, and neither root chat nor generated code creates a domain approval record. Generic artifact reading cannot bypass this boundary even by changing its stored kind/schema: original intent ownership is checked too.

## Deployment and validation limits

Technical configuration has no permissive defaults: mapping/inventory/byte bounds, environment/region, lineage version/edge limits and private object directory must be explicitly provided. Missing configuration is visible and fails before production writes. The existing deployed SourceUploadTransport is reused; no credentials or service setup are created here.

Exactly ten 096 tables are added: mapping revision/head, source read receipt, target intent/entry/attempt/registration, and input capability/state/decision. Existing journal, registry, content binding and dependency access tables are reused. Head and capability state are CAS-updatable; original receipts and histories are immutable.

Unit/service tests use explicit database, source writer, session and current-authority doubles where live services are unavailable. They are not real DB/S3/Temporal acceptance. Downstream TASK node materialization, independent N02 observation assembly, N03 typed target reading/check-result registration and required-field coverage at that consumer remain the next runtime slice. No runtime-ready claim is made.

### Exact authority witness integers

The new 096 internal witnesses encode database grant revisions, gate revisions, and verification counts as validated unsigned 64-bit decimal strings before canonical hashing. Current-versus-persisted grant identity uses full typed record equality, including exact `BigInteger` revision and timestamp fields. This is not a change to historical publication manifests or global JSON canonicalization. Fractional, negative, noncanonical, missing mandatory revision, and out-of-range values fail closed. Regressions cover timestamp-bearing records and adjacent revisions above 2^53.

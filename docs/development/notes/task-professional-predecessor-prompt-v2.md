# Explicit N02 predecessor-evidence Prompt V2

This slice is source implementation and offline boundary validation, not live package approval,
model execution, deployment, or end-to-end acceptance. A1 includes a separately governed V2
package producer; the existing V1 authoring route must not be used as a schema-upgrade bypass.

## Immutable V1 boundary

The existing package schema and its catalogue remain byte-identical:

- `task-professional-package.schema.json`: `f4510bb51711d57e11aa0fb5405249ecc8e51437a21cafb644f89d7da690069e`.
- `task-professional-package.catalogue.json`: `f1b0d3877e11eb4d87eb69dd809f8c1c2a285bd56a05b2cb24d8ae9ebab370fc`.

V1 keeps `N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1`, the original four-field compiled
JSON document, schema version `1.0.0`, and `TEXT_SYSTEM_USER_V1`. It performs no predecessor GET
and the model-context path still refuses predecessors. No profile files or runtime defaults are
installed or changed by selecting V2 code.

A narrow compatibility repair also allows existing V1 source-backed TASK packages to perform
their separate MODEL_SEND check using the actual current registered input. The new overload
supplies the exact fixed snapshot, parse, version, index, approval, binding and rights pins; it
does not infer missing selections. The legacy overload and all V1 wire/compiled payloads remain
unchanged. Missing or substituted pins still deny transmission for both versions.

## Explicit selection and closed ABI

V2 Prompt and Profile artifacts must both reference the separately published
`urn:medical:task-professional-package:2.0.0` definitions and its exact schema digest. A genuine
unchanged V1 RolePackage pins their prompt identity and profile artifact; the profile must also
match the published replay and operator-pinned deployment. Mixed V1/V2 packages are denied.

- Prompt encoding: `TASK_NODE_INPUT_WITH_REGISTERED_PREDECESSORS_V2`.
- Profile projection: `TEXT_SYSTEM_USER_WITH_REGISTERED_PREDECESSORS_V2`.
- Compiler: `N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_PREDECESSOR_EVIDENCE_V2`.
- Compiled document: `schema_version`, `node_kind`, literal `instructions`, original
  `task_node_input`, and ordered `predecessor_evidence` entries containing the exact predecessor
  pin and the physically read `evidence_bundle`.
- Provenance retains all V1 package/input pins and adds ordered predecessor/object/schema/source
  closure pins. Its schema version is `2.0.0`.

The authenticated host accepts this exact additive ABI. The independent supervisor still
matches prompt text, digest and provenance to actual registered StepEnvelope bytes. A rehashed
SDK replacement cannot override those bytes. Host integrity checks are not business authority.

## Independent evidence and current permission checks

`TaskProfessionalPredecessorEvidence.Reads` has a private constructor. It can be obtained only
through actual exact-version object reads outside SQL transactions. Each physical segment
re-enters the current consuming HOST_CONTROL admission and validates:

1. The original registered 097 input and its 095 graph predecessor pins.
2. Historical successful completion, real atomic COMMIT, and original 075 producing records.
3. Same tenant, actor, project, task, run and exact producing step/artifact/version/receipt.
4. Current consuming lease plus the explicit 098 `HOST_PREDECESSOR_CONSUME` operation and bounds.
5. Source roots and rights members exactly equal to the consuming input's fixed source closure.
6. All selected, conflict and coverage references remain within the exact fixed selections.
7. Physical object identity, complete bytes/hash, canonical payload, and EvidenceBundle schema.

Current MODEL_CALL context validation rechecks original evidence pins and then separately
checks MODEL_SEND for the exact fixed source selections, business purpose and provider. An
internal FORMAL read or a historical receipt never substitutes for transmission permission.
Missing, changed, revoked, reordered, duplicated, cross-scope, oversized or unregistered evidence
is denied; there is no generic artifact, raw answer, SDK-history or network fallback.

## Offline evidence

Focused tests cover canonical V1 bytes, explicit V2 selection, mixed versions, malformed pins,
source closure and rights changes, physical read substitution, revocation during I/O, missing
098 authority, changed completion receipts, current MODEL_SEND denial, and independent
supervisor matching. The Python consumer aggregate passed 120 tests on 2026-10-20. Java gate
results and producer acceptance are recorded separately; this note does not claim those gates
passed before they run. No real database, storage, mTLS, Temporal or provider execution occurred.

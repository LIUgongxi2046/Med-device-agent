# SIM authorized-use evidence and bounded opaque approval witness

This is an immutable description of existing business-source authority. It never creates a grant or approval. First release is enterprise scope only.

## Interfaces and public document

- `SimulationAuthorizedUseEvidenceAuthority.preview(admission, specification)` reconstructs current server records. No object download occurs.
- `verifySourceDocument(admission, document)` compares every field with a current reconstruction and returns only the actual SIM business dependency VersionRefs for preparation lineage.
- `require(admission, validatedCreate)` is for all-SIMULATION evaluation definitions. It verifies separately registered canonical content and its current business authorization.
- Specification remains `schema_version`, `dataset`, `sample_manifest_artifact`, and exact `samples`.
- Public source coverage contains the business source/right refs, business source ObjectRef hash, approval decision identity, `witness_sha256`, and decimal-string `evidence_count`.
- No private licensing-artifact IDs, source IDs, file addresses, licensing metadata, or review-policy documents are returned. There is no `evidence_sources` field or public `evidence` list.
- Local schema ref stays `urn:medical:simulation-authorized-use-evidence:1.0.0#/$defs/Document`; its actual classpath hash changes with the document schema. Frozen catalogue is unchanged. Canonical bytes are bounded to 1 MiB.

## Why licensing evidence is not a SIM content dependency

Real SourceRights approval reads licensing artifacts and their sources using SOURCE_RIGHTS_REVIEW. Each current source rights revision has one exact purpose. The resulting business source may legitimately receive SIMULATION_EVALUATION rights while its license source remains SOURCE_RIGHTS_REVIEW.

Requiring the worker to reread the licensing file for SIM would introduce a contradictory purpose requirement. The bounded internal witness instead checks the existing governance evidence without granting or performing licensing content access. It does not call SourceReadAuthority for those private files, impersonate the reviewer, expand worker purpose permissions, or infer a new approval.

Actual business dataset, sample manifest, input and expected refs still use complete current SIM grants, source rights, REVIEW use, configured execution environment/region, and complete lineage. No actual business edge is filtered. If a private licensing artifact is genuinely included in that graph, it must satisfy ordinary SIM access or the request fails.

## Internal witness and supported proof shape

The verified current business rights head must lead to a real APPROVE decision, operation, canonical request/reviewed manifest, byte proof, receipt, and exact audit/outbox witnesses. Bootstrap APPROVED status alone is insufficient. The opaque witness hashes exact approved evidence-authorization and byte-proof arrays; its input cannot be a client-selected licensing artifact.

V1 supports only an existing server-configured AUTHORIZATION profile with SOURCE owner, source.read semantics, a published fixed schema, and a source-original artifact whose complete graph is exactly that ARTIFACT and one direct enterprise SOURCE_VERSION parent. The artifact's complete ObjectRef must equal both the approved byte proof and the actual source original. Artifact binding, dependency manifest, registered versions, reviewed source and current rights are compared with the approved snapshot.

The private source's current rights must remain the exact reviewed revision/hash, APPROVED, SOURCE_RIGHTS_REVIEW, and valid for the configured governance review environment/region/time. Its lifecycle/upload/approval gates must remain ACTIVE/RECEIVED/APPROVED. The current complete review policy must equal the approved policy snapshot; a policy change requires renewed governance. Historical reviewer permissions are not replayed using a worker identity.

Unsupported DOCUMENT/TEMPLATE/RULE owners, transformed proofs, nested artifacts, multi-source proofs, missing ledger rows, scope drift, revoked/replaced/expired rights, changed objects/bindings/manifests, or audit drift fail closed. This limitation is explicit rather than a broad bypass of ArtifactRegistry content authorization.

Private witness metadata remains in the already-protected SourceRights ledger. It is not returned as a normal preparation content dependency. SourceRights AUTHORIZATION_EVIDENCE lineage belongs to the rights revision, not automatically to the business source version.

## Producer/consumer integrity

Preparation registration validates actual uploaded raw bytes and stores canonical content. Consumers verify the preparation's schema version, canonical request/document, operation request hash, strict receipt, operation, audit and outbox; they also bind the exact actual source ObjectRef and current rights. The authorized document's own uploaded source is independently authorized at consumption and is not included in its pre-generated coverage.

Mapper calls use actorId(), including worker admissions. There is no fabricated user session. Ordinary/mixed evaluations keep their original branch. Root owns the exact local-schema ArtifactRegistry exception and aggregate Maven verification.

## Verification boundary

Earlier 76 JVM cases tested ledger/schema integrity but did not prove the contradictory two-purpose business path. The opaque revision adds a real ResourceReadAuthority/PermissionAuthority/SourceReadAuthority/SourceRightsSchema regression across review and consumption, with only persistence rows mocked. Such tests still do not represent a live MySQL/object-store customer approval execution. Aggregate Maven is coordinated by root.

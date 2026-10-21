# Stage 23: original disable recovery and closed policy reads

All implementation remains staged pending parent integration. The two new schemas alone were copied into the actual repository resources with parent approval. COPY-MAP.json gives new files; patches/ contains four narrow source edits against unchanged current snapshots. No frozen 084/086 contract, journal hash, DDL, or migration was changed.

## Contract

POST /api/formal-agent-publications/disable/recovery is a read-only lookup. It remains under the existing default-disabled medical.formal-agent-publication.enabled controller and authenticated global HTTP/CSRF handling. There is no new exemption or enable flag.

Headers: X-Schema-Version: 1.0.0 and current X-Context-Revision. An Idempotency-Key header is forbidden. Query parameters, duplicate required headers, duplicate JSON fields, trailing JSON, unknown fields, and oversized bodies fail closed. The recovery envelope is bounded at 2 MiB and its unchanged Disable body at the existing 1 MiB.

Closed request: schema_version, original_key, request (the complete original frozen Disable).
Closed success: schema_version, state=COMPLETED, original_key, actor_id, release_id, expected_revision, resulting_revision, request_body_sha256, receipt (unchanged journal receipt, kind=PUBLICATION).

The server performs historical publication preparation and physical original object recovery, then checks the final current read authority, original registration, journal/audit/event/operation, disable sidecar, and exact candidate/scope revisions. Original actor/key/body must match. It never calls the disable mutation, publication preparation for writing, registration finish, or any operation/audit/outbox insert/update. It cannot enable a release. Current agent.publish or artifact.register grants are not required by recovery; actual current read and historical evidence remain required.

Recovery-only damaged-join handling: if historical preparation reports OBJECT_UNAVAILABLE, the current tenant/context publication join is checked again. If it exists, the physical-object failure remains OBJECT_UNAVAILABLE. If the join is missing, corruption is disclosed only after an exact current-actor PUBLICATION journal key, original project configuration.read, canonical original body, and immutable journal proof all verify. Unknown/foreign actor/key or unavailable read scope returns ACCESS_DENIED. No arbitrary release counts or cross-actor sidecar probes are used; this path always fails and never substitutes for historical preparation.

Error semantics: genuine absent disable on an otherwise readable publication = OBJECT_UNAVAILABLE (404); inconsistent/partial evidence = CONTENT_MISMATCH (422); original body mismatch = IDEMPOTENCY_CONFLICT (409); another actor/key = ACCESS_DENIED (403); stale current context = CONTEXT_CHANGED (409). These are errors, never NOT_FOUND/PROCESSING success projections. An unavailable original physical object also remains an OBJECT_UNAVAILABLE error.

The added 084 response schema seals the actual EvaluationAcceptancePolicyService.read projection for DRAFT/APPROVE/REJECT and fixes production_eligible=false. Frozen author and policy definitions are copied transitively into the new local read schema. The service validates the response immediately before returning it. This historical display projection is not an approval/execution grant.

## Integration

1. Review/apply the four patches to existing Java files; do not replace any concurrently edited source wholesale.
2. Copy the two new Java classes and four new test classes using COPY-MAP.json. New resource bytes are already present and must stay exact.
3. Register the new recovery route in parent-owned API/interface metadata, if those catalogues list exact paths. No security exemption, migration, or change to frozen contracts is needed.
4. Use the existing front-end adapter's same original pending actor/tenant/context/body/hash/key identity. Recovery carries the current context while preserving the immutable original body/key. Retain the exact original request for both unavailable and corrupt failures. Do not treat 404 errors as absence, clear the pending original, or resend the disable automatically.

## Verification

103/103 isolated JDK 17 JUnit tests passed: 48 new and 55 existing policy/publication regressions. The actual controller, parser, outer service, private historical entry, original ledger/transfer/registration/lifecycle/journal run together. JDBC/S3 writer, current professional evidence, and authenticated/permission boundaries are explicit test fixtures. This does not claim a deployed database, S3, production professional proof, or end-to-end browser acceptance.

Reproduce:

./run-tests.sh PublicationDisableRecoveryTest PublicationDisableRecoverySchemaTest FormalEvaluationPolicyReadSchemaTest PublicationDisableRecoveryHttpTest PublicationLifecycleTest PublicationHistoricalAuthorityTest FormalPublicationControllerTest FormalEvaluationPolicyServiceTest

See targeted-tests.log and EVIDENCE.json. The runner uses repository-built dependency classes and repository JDK; it does not invoke Maven or install tools.


## Parent integration verification
Integrated into the working source after remote checkpoint62ed22f; targeted formal Maven tests:103 passed,0 failure/error/skip. This increment is not yet committed or pushed. Live environment acceptance remains not run.

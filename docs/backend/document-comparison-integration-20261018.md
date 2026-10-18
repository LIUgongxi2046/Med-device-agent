# Comparison persistence staging release map

No production edits or Maven run by this owner. Publish only after root review/release.

- `DocumentComparison*.java` (this directory) -> `business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/`.
- `../comparator/DocumentStructuralComparisonEngine.java`, `DocumentStructuralComparisonSchema.java` -> same package.
- `storage/DocumentComparisonMapper.java` -> persistence `mapper/` package.
- `storage/DocumentComparisonMapper.xml` -> persistence `src/main/resources/medical-mappers/`.
- Both local `*.schema.json`, plus `../comparator/document-structural-comparison-core.schema.json` -> persistence `src/main/resources/`.
- `storage/069-document-comparison.sql` -> existing migration directory; root alone integrates master, coverage and migration test accounting. Old migrations untouched.
- `tests/DocumentComparisonServiceTest.java`, `tests/DocumentComparisonBoundaryTest.java` -> persistence tests; comparator owner provides corresponding core tests.
- API.md/INTEGRATION.md -> developer frontend/backend documents as root chooses.

## Root-owned tenant integration

Whitelist the exact `DocumentComparisonMapper.` prefix under current admitted tenant/user guard, without adding any global permission/purpose fallback. Tables `document_comparison_intent`, `document_comparison_registration`, `document_comparison_attempt` are tenant-scoped and immutable (INSERT/SELECT only through normal repository). Each map includes current userId, never impersonates original createdBy. Historical creatorId is only operation historical filter, not admitted identity. No table grants/credentials seeded. Normal operation_record is sole mutable CAS state.

## Actual execution/storage boundary

No system_job: original CompareRequest defines synchronous bounded comparison, with no job/workflow field and no asynchronous worker support. Actual operation + immutable input manifest/intent (including original full-ASCII idempotency key, unique per tenant/creator), start audit/outbox, immutable first attempt, completed registration + final audit/outbox close lifecycle. Sidecars do not fabricate a check/rule manifest. Both saved revisions must already exist in real DocumentRevisionService history and each undergo full current source-aware FixedRead.

Create and recovery independently query the immutable original key before accepting the mutable operation lookup. Only both records absent means NOT_FOUND; deleted/moved/mismatched operations fail CONTENT_MISMATCH and cannot create another comparison. This lookup binds current actor, while historical creator filters never impersonate the actor.

The first attempted object write is recorded in its own immutable attempt row in the same DB transaction as the operation command transition. A later attempted=false cannot restore permission to write. Existing saved reference is only a hint: retry must read-only recoverVerified the original deterministic write intent and compare exact ObjectRef including version. Final registration transaction compares its newly read command reference against the actual reference verified in that invocation, preventing between-transaction JSON drift. Unknown write never calls writeVerified again. Original DB deadline, actor, session/context and all current source grants remain mandatory for mutations. Read-only recovery can explain an unresolved expired operation without mutating it.

A complete dependency union is registered using actual existing manifests. Duplicate upstream with conflicting rights_revision is SCOPE_STALE. Actual union access header, manifest JSON/digest/members/registry version/closure are reread. Both side proofs retain their own proper FORMAL/HISTORY policy. Registered output gets real ARTIFACT version, actual fixed object, DOCUMENT owner binding and exact union. No widening of generic ArtifactRegistry local profile trust: the typed reader checks this narrow profile independently and additionally requires artifact.read/DOCUMENT_BODY on actual owner.

Before registration and at reading actual stored bytes, engine.verifyAgainstInputs recomputes the complete bounded canonical result from the two actual fixed bodies. Rehashed truncation or omitted unchanged/affected records is rejected, not accepted as complete. Only one algorithm implementation version is installed currently: implementation or policy drift fails closed. Retaining readers for previous algorithm versions requires future explicit implementation; never silently rewrite an old comparison.

## Deployment limits

`medical.document-comparison.enabled` defaults false. All policy capacities must be explicitly positive when enabled: maximum-nodes, maximum-cells, maximum-relationships, maximum-input-bytes, maximum-result-bytes, maximum-records, maximum-text-code-points, maximum-work, maximum-milliseconds, storage-seconds, maximum-versions, maximum-lineage-relations. Execution is technical/structural only, no medical meaning or automatic merge. Availability derives actual controller registration and reports request/core-byte/record bounds, not grants or storage readiness.

A full typed Read HTTP envelope is bounded by `2 * maximum_result_bytes + 16384` UTF-8 bytes: the repeated affected-node list already occurs within CoreResult; other metadata is bounded UUID/hash/decimal fields. This differs from configured canonical object-byte limit. Client must count bytes before JSON parse where supported; browser buffering before JS remains browser-managed.

## Verification boundaries

Isolated javac/JUnit tests use real pure engine, published schema validation, dependency manifest validator, and actual Mapper XML BoundSql resolution. SQL, EnterpriseAccess/auth and S3 transport are test doubles. Controller tests are direct method tests, not a servlet/security-filter integration. No claim of live MySQL FK enforcement, S3 durability, real permissions deployment or browser E2E. Root Maven/full integration and deployment validation remain required.

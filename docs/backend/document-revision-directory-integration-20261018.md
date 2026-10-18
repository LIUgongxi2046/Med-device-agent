# History directory publication and validation record

Published by root authorization on 2026-10-18. No DDL or Maven was run by this worker; no new migration is needed. Root owns TenantScope and aggregate integration.

- DocumentRevisionDirectoryMapper.java -> business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/mapper/
- DocumentRevisionDirectoryMapper.xml -> business/medical-persistence/src/main/resources/medical-mappers/
- DocumentRevisionDirectorySchema.java, service/*.java and availability/DocumentRevisionDirectoryFeatureAvailability*.java -> business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/
- document-revision-directory.schema.json and availability/document-history-feature-availability.schema.json -> business/medical-persistence/src/main/resources/
- tests/DocumentRevisionDirectoryServiceTest.java, tests/DocumentRevisionDirectoryControllerTest.java, tests/DocumentRevisionDirectoryFeatureAvailabilityTest.java -> business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/
- API.md -> docs/backend/document-revision-directory-20261018.md
- Do not copy classes/, the local test launcher, or scratch scripts.

Root integration: add DocumentRevisionDirectoryMapper namespace to TenantScope subject-parameter enforcement. All queries already carry current actor userId and actual owner projectId. Existing table tenant injection remains mandatory; no new immutable table registration. DocumentReadService already has MetadataRead / authorizeMetadata / requireMetadataCurrent from 067; reuse these exact production methods. No new method or conversion into FixedRead is added.

Default disabled: medical.document-history.enabled=true plus explicit positive maximum-page-size, maximum-scan-rows >= maximum-page-size, maximum-references, maximum-cursor-entries, cursor-seconds. Feature availability always exists and reports actual route registration plus maximum_page_size (null disabled).

Bounded metadata reference preflight occurs before calling MetadataRead because existing DocumentReadService metadata assembly itself reads check/review/signature lists. Over-limit reference sets fail the request, never truncate a successful entry. No source-denial IDs or causes appear in responses. Accepted proofs are rechecked before return. Head lock and final head comparison protect each page; later head changes invalidate continuation. This is live keyset navigation, not frozen-list completeness. No total count is supplied.

Isolated javac/JUnit: 21 tests (13 service/cursor, 5 availability, 3 controller). Actual new service, schema, cursor and controller execute; SQL, session admission and existing source-aware MetadataRead are test doubles. No claim of real MySQL/HTTP/S3 E2E or independent recreation of SourceReadAuthority tests. Tests cover honest empty authorized continuation, known hidden source failures, corruption/storage failure propagation, per-row proof revocation, bounded references, exact item routes/currentness, valid cursor progression, cross-session/head rejection, expiry/eviction, query/header identity plumbing and deployment availability.

Public contract: history/API.md is authoritative for staging; parameter is `limit` (not page_size). Existing application Envelope field is `resource_revision`. Comparison remains a separately planned slice and does not gate this directory.

Post-review correction: scan now LEFT JOINs document_revision and version_registry, preserving a meta row with a missing/corrupted foreign row as a null integrity sentinel; service rejects it. The redundant document_head join was removed (head is separately read/locked/validated). Normal existing FKs prevent those missing-parent states, but restore/corruption/fault-injection must not silently turn them into an empty directory. Added DocumentRevisionDirectoryMapperTest: actual MyBatis BoundSql verifies both LEFT JOINs and sentinel, plus service fault tests for null/drifted registry values. This is not a real MySQL FK-bypass test.

Updated isolated suite: 24/24 passed (15 service/cursor including oversized-but-unauthorized candidate capacity boundary, 5 availability, 3 controller, 1 actual mapper SQL generation). Add tests/DocumentRevisionDirectoryMapperTest.java to test publication mapping. Public schema/routes remain unchanged and frozen.

# Document-save audit staging integration

Not published. Root alone may release and integrate shared mapper/tenant/migration catalogues.

## Publish map
- BusinessAudit*.java -> medical-persistence src/main/java/com/ruoyi/medical/persistence/.
- storage/BusinessAuditMapper.java -> same base mapper/ package.
- storage/BusinessAuditMapper.xml -> medical-persistence src/main/resources/medical-mappers/ (not mapper/).
- business-audit.schema.json -> medical-persistence src/main/resources/; frontend copies same frozen bytes.
- storage/072-business-audit-read-index.sql -> medical-data migration directory. Additive index only; no table, seed, grant or credential. Do not modify 009 or any delivered migration.
- Exact BusinessAuditMapper prefix to existing current-subject tenant gate. No wildcard mapper access, permission-purpose fallback, or simulation-worker whitelist expansion.
- No audit event producer change. No new operation/job. Read-only feature, no guessed rows.

## Explicit policy

medical.business-audit.enabled defaults false. Enabled requires positive maximum-page-size, maximum-scan-rows >= page size, maximum-payload-bytes, maximum-response-bytes, maximum-window-seconds, cursor-seconds, maximum-cursor-entries. Candidate bounds exclude int overflow. SQL statements have a hardcoded five-second timeout (explicit implementation cap); live driver/server timeout enforcement still requires verification. Cursor is bounded process-local memory; eviction/restart is CURSOR_EXPIRED, not data loss. A multi-instance deployment can use sticky routing or accept honest cursor restart; no distributed frozen cursor is claimed.

The supported indexed scan is real tenant/project/action/from-to/actor with keyset. document_id is an adapter filter after bounded candidate validation and current source permission, not an unindexed whole-table JSON search. There are no totals. An empty authorized page can continue.

## Authority

Shared narrow patch: `shared/DocumentReadService.java`, `shared/DocumentReadMapper.java` and `shared/DocumentReadMapper.xml` must be merged by root, not overwrite unrelated shared changes. It adds bounded metadata-only queries and excludes ancillary collections for MetadataRead. Both authorization and currentness use the exact same complete source/three-artifact proof path. Existing body FixedRead remains unchanged. Internal artifactReference is registered metadata, never a byte-read proof. History directory and 067 queue use only header/currentness and retain signatures; their returned related IDs continue to use their own bounded readers.

Private SavedEvent schema matches only actual DocumentRevisionService event fields. Original schema manifest is PermissionSchema's actual producer manifest. Operation is DC-02, COMPLETED, 201; original actor/target/schema and response request ID are bound. Reconstruct original RevisionInput using base_revision, resulting revision minus one, actual Snapshot body artifact/hash/schema and private reason; compare exact stored operation request hash using the original service's method/path/schema descriptor. Validate actual revision/meta/registry/document ownership without hiding missing join rows, and all three complete ArtifactRefs against the real registered ObjectRef-derived whole-object coverage. Require exact DC02 empty arrays/evidence-watermark/allowed-actions constants; never replace saved historical auth/governance/issue watermarks with current ones. Registry kind must be DOCUMENT_REVISION, not DOCUMENT. MetadataRead then checks current source rights and full dependency authorization, using existing FORMAL/HISTORY rules; opening target separately validates actual document bytes.

The public summary is only document save audit facts. No reason/trace/raw event/operation/private evidence/ObjectRef body escapes. Audit grant is audit.read/AUDIT_READ on actual AUDIT owner; target/source rights remain DOCUMENT_BODY. Existing grants must genuinely cover it; code supplies none. A generic all-event JSON UI or entity metadata fallback is forbidden.

## Remaining validation boundary

All work in this directory is isolated staging. Storage checks use real SQL parser/MyBatis BoundSql/current tenant rewrite offline. Runtime tests can exercise actual ResourceReadAuthority and schema with mapper/storage doubles, but no live MySQL execution plan, DB timeout, deployed grants, S3/storage availability or browser E2E is implied. Root package/full migrations and approved deployment verification remain required.

## Isolated test status

31/31 runtime tests pass: BusinessAuditRuntimeTest (23) and DocumentMetadataReadBoundedTest (8). Storage BusinessAuditMapperTest has 5 passing tests. Shared merge mapping/base hashes and exact new SQL IDs are in shared/MERGE.md. Independent adversarial receipt review remains pending; do not publish before root release.

Independent review completed: VerifyFix 14 cases passed (valid baseline, each body/fields/evidence_links artifact ID/hash/schema/coverage tampering rejected, false nonempty check_ids rejected). Original forged-fields probe now rejects CONTENT_MISMATCH. Independent actual metadata tests 8/8 pass, including FixedRead full-path regression. Evidence: /tmp/business-audit-independent-review/verified-fix-results.txt and metadata-after-results.txt. Staging is frozen pending root release; no live DB or source-right graph integration is implied.

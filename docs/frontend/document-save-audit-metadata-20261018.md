# Narrow metadata patch merge notes

Staging only. Merge against current formal files; do not overwrite unrelated edits. MetadataRead remains a private-state capability without body-byte validation. FixedRead keeps original full preparation/currentness.

- `shared/DocumentReadService.java` → `src/main/java/com/ruoyi/medical/persistence/DocumentReadService.java`
  - Reviewed formal base SHA-256: `36b19524ccc0a495f171cdae14249dc1d9a462af2bf66059ded6cc1737542a31`
  - Staged SHA-256: `aab7fb399795c63617ea91794204900adec4ed0b33a25474b11f060c925df5fb`
- `shared/DocumentReadMapper.java` → `src/main/java/com/ruoyi/medical/persistence/mapper/DocumentReadMapper.java`
  - Reviewed formal base SHA-256: `e0b576db046f9ab311f992e82a2fb946f838ed7e736d648a1a3d2642457c9c1d`
  - Staged SHA-256: `bfa6f151b5df145831452a065535f24f90c5d79939eb5000b39bddcae93ed09c`
- `shared/DocumentReadMapper.xml` → `src/main/resources/medical-mappers/DocumentReadMapper.xml`
  - Reviewed formal base SHA-256: `edafebe1727a452ee34cf5379eaeb08525c134a55c26ab15512f3d8562bfc6b0`
  - Staged SHA-256: `d48e04a3443a693fbda8dd1fb76051fe68c74b51f0bde880dbe6f299e7114c23`

Added SQL IDs: metadataHeader, metadataProjections, metadataWatermarks, metadataSnapshot, metadataMembers, metadataSourceCount, metadataSnapshotBytes. Existing DocumentReadMapper subject-gate prefix applies; no wildcard authorization addition is required. Every new query has timeout=5. Cardinality or capacity overflow rejects instead of accepting a truncated result.

Consumers: document history directory and review queue retain their existing MetadataRead signatures. Metadata currentness intentionally no longer depends on unrelated UI collections; it still binds actual document grant, complete sources/three registered artifacts, header/snapshot and current watermarks. Their own related-reference readers remain responsible for their separately returned references.

Tests: BusinessAuditRuntimeTest (23), DocumentMetadataReadBoundedTest (8), BusinessAuditMapperTest (5). The FixedRead regression creates a reflected wrapper around actual full prepared state to exercise the actual currentness overload; it does not claim object bytes were read. ArtifactSourceAuthority is a boundary double in metadata tests. Live source-right graph and DB timeout enforcement remain integration checks.

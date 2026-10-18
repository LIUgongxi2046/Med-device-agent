# 068 offline migration validation

Current coverage verified: **211 business / 248 total tables**. Added exactly `document_technical_check_manifest` and `document_technical_check`. Historical001–067 (66 SQL/XML files) were byte-compared with part15 source archive, all unchanged; frozen aggregate SHA-256 `6a793fe61e80a1583b019478b2764ce12ab879c8c860ff5855e774f2ef20d1cf` is now tested.

`tests/test_document_technical_check_migration.py`: 7/7 specialized tests passed. Includes full scoped FK sets, matching manifest identity/digest unique keys, JSON/hash and immutable metadata, STORED project_key, actor-requiring TenantScope registration, 30 mapper statements and original actor/key/request/CAS recovery guards. Job insertion uses DOCUMENT_TECHNICAL_CHECK and NULL workflow; the complete workflow CHECK AST permits NULL exactly for SIMULATION_EVALUATION or DOCUMENT_TECHNICAL_CHECK, and requires non-null for every other job type.

Exact table totals were updated in existing migration tests; bundle base-plus-additions formula includes the new two-table group. Full bundle's existing FK checks still verify real parent unique keys across historical native/XML ALTER definitions.

## Parser compatibility and evidence

JSQLParser5.2 via existing InspectAlterMigrations.java timed out after60s on both full staged and published068. Splitting the unchanged source with sqlparse showed statement0 (compound ALTER DROP CHECK/ADD CHECK) also timed out; both CREATE TABLE statements passed independently. Reproducer inputs and observation log: `.implementation-work/068-parser/statement-{0,1,2}.sql`, `result.txt`.

Root approved a compatibility branch only for068. `test_migration_bundle.parse_068_native_statements` parses all original SQL with existing mature SQLGlot mysql parser, requires exactly one ALTER plus two CREATE TABLE ASTs and adds every statement to the same full FK/unique/type checks. No executable SQL, constraint or historical DDL is rewritten or skipped. All001–067 retain their prior parsing path. Existing full-bundle Java helper subprocesses now have60s timeout instead of hanging indefinitely.

Java `business/medical-data/.../MigrationBundleTest.java` uses Liquibase and does not invoke JSQLParser; this Python helper issue does not itself require aborting Maven.

Full-suite log: `.implementation-work/logs/part16-migration-compat-checks.log`. The first abandoned run is `part16-migration-checks.log`; it is not a passing result. The full offline migration suite passed **129/129 tests in 72.238 seconds**. Its strict packaged-resource check passed for the rebuilt CLI and library, including 068, master.xml and coverage.json (all migration resources compared byte-for-byte). No Maven/frontend build by this author; no actual MySQL/S3/HTTP acceptance claimed.

Final artifact confirmation: full-suite CLI hash equals the current rebuilt jar SHA-256 `ad1bd0c71b03116392b26322c52e10b206dc462781635d4c0cb5ececf2a8980e`; its 69 resources were checked. A separate final `test_migration_cli.py` rerun passed **4/4 in 5.450 seconds**, log `.implementation-work/logs/part16-migration-cli-final-checks.log`. CLI/library file mtimes are 2026-10-18 17:40:25 UTC. Tests and this validation note are frozen for part16 backup.

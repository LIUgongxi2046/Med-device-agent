# Migration 069 offline review

Date: 2026-10-18. Scope: formal migration, mapper contract, exact registered table counts and frozen predecessor bytes. No MySQL execution, Maven run, packaged-resource acceptance or production deployment is claimed.

## Exact scope and preservation

- 069 adds exactly `document_comparison_intent`, `document_comparison_registration`, and `document_comparison_attempt`.
- Registered business tables: **214**. Complete business/platform/framework table set: **251**.
- The bundle formula retains the existing base of 178 and adds the explicitly enumerated incremental groups, including the three comparison tables; it does not replace exact counts with loose bounds.
- All 67 numbered SQL/XML migrations through 068 were compared byte-for-byte with the sealed part17 ZIP and are unchanged. The ordered `filename + NUL + bytes + NUL` SHA256 is `aef81c64a51c1096ede9dc4c2c651d4f344887992f9037ce9af6df646722ed0c`.
- The baseline ZIP is `Med-device-agent-20261018-part17-source-and-validation.zip`, archive SHA256 `bbc5d049af53c976c4d511f87526527de9cb8ba817b7e51147d7bccf5444f1fe`.

## Actual parser and integrity checks

069 uses the existing real JSQLParser inspector and SQLGlot AST projection. The narrowly guarded SQLGlot compatibility helper remains exclusive to 068's compound workflow ALTER; no parser fallback was expanded.

The complete bundle validates all 251 tables and every foreign key's complete parent unique key, column/type compatibility and tenant-leading identity. The comparison intent binds both fixed document revisions, immutable input version, dependency manifest and operation. Registration binds the complete intent/artifact identities; attempt binds comparison/operation/write ID. New tables are insert-only under the registered immutable-table guard and carry explicit schema version, schema manifest, actor, creation time and classification metadata.

Mapper recovery writes remain operation-key/actor/request/target/state/revision scoped. The immutable attempt row supplements the monotonic operation phase; it is not replaced by a caller flag. The attempt read need not independently lock an immutable row: the mutation path locks the original operation and performs its attempt insertion plus command CAS in the same transaction.

## Executed verification

- Full migration bundle: **23/23 passed**, including complete parent-key checks.
- Six changed existing migration/count suites: **47/47 passed**.
- New 069 focused migration/mapper suite: **21/21 passed**, covering all 25 mapper IDs. Combined executed offline checks: **91/91 passed**. No concrete 069 DDL defect was found.
- Logs: `part18-comparison-migration-bundle.log` and `part18-comparison-migration-counts.log`, and `part18-comparison-migration-focused.log` in the workspace validation logs.
- Existing launcher/packaged-resource tests must be evaluated against root's newly rebuilt package. Old JAR/source resource differences are not treated as successful current-package validation.

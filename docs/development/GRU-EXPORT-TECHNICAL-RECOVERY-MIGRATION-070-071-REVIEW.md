# Migrations 070–071 offline review

2026-10-18. Scope: actual formal SQL/mapper contracts, exact table coverage, immutable registration and predecessor preservation. These checks do not execute MySQL or prove object-storage transaction behavior.

## Scope

- 070 adds `gru_record_export_intent`, `gru_record_export_attempt`, `gru_record_export`.
- 071 adds `document_technical_check_intent`, `document_technical_check_attempt`.
- Exact coverage is **219 business tables / 256 total tables**. The bundle formula retains base 178 plus explicitly enumerated additive groups.
- All five tables are registered in the actual immutable-table guard. Gru export has its own actor-required mapper prefix; technical recovery uses the existing actor-required technical-check mapper.
- 001–068 remain frozen against the part17 source archive, verified by the comparison migration suite. The authorized, undeployed 069 original-key repair is retained.
- Both migrations use the real JSQLParser inspector plus SQLGlot AST verification. The isolated 068 compatibility exception is unchanged.

## Findings and verification

The first full-bundle pass identified a genuine 070 attempt metadata defect: `created_at` and `classification` were absent. The standard metadata assertion was preserved. The author added those fields together with explicit schema identity and readback checks. The final strict bundle passes with no metadata exception.

Final verification after the metadata repair:

- Full migration bundle: **23/23 passed**, all 256 tables and complete FK parent unique keys/types.
- Eight existing focused suites, including repaired 069 and 071: **74/74 passed**.
- New `test_gru_record_export_migration.py`: **23/23 passed**, all 23 mapper IDs and 19 FK bindings, owner/key/attempt/immutable/CAS and exact metadata checks.
- Combined executed offline verification: **120/120 passed**.
- Logs: `part19-070-071-migration-bundle-final.log`, `part19-070-071-migration-focused-final.log`, `part19-gru-export-migration-focused.log`. The initial failing bundle log is retained separately.
- `git diff --check` for the review tests/document passed.

Packaged-resource checks require the newly rebuilt package. An old JAR mismatch is not accepted as current-package validation. No Maven run, deployment, or live database acceptance is claimed by this review.

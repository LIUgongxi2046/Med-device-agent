# 067 offline migration validation

2026-10-18. Verified actual source coverage: **209 business tables, 246 total tables**. 067 adds `document_revision_return` and `document_revision_return_assignment` and no other tables. Historical 066 totals were 207/244.

## Frozen baseline

Every numbered SQL/XML file 001–066 (65 files) was byte-compared against `Med-device-agent-20261018-part14-source-and-validation.zip`; no differences. Added frozen aggregate SHA-256 test: `4b4350b647b4608ae3d8c827e1e8ce45386b17ffdc8b3a064d430fbca21a6aed`. Neither historical DDL nor 067 DDL was modified.

## Test changes

`tests/test_document_review_return_migration.py` now has 13 tests using the existing JSQLParser/sqlglot pipeline and real mapper XML. Added:
- master include exactly once, after066; coverage native registration and exact two-table group;
- exact 209/246 counts and immutable interceptor / actor-requiring TenantScope mapper registration;
- AST index column order for `ix_revision_assignment_queue(tenant_id,assignee_id,assignment_id)` and `ix_revision_return_assignee(tenant_id,project_id,assignee_id,status,created_at)`;
- assignment unique identities, eight-column return binding FK and matching parent unique key;
- immutable001–066 baseline digest.

Updated old precise counts in migration bundle, annotation anchor, Gru records, simulation ledger and simulation preparation tests. Added the explicit `document_return_for_revision` group to the bundle's independent base-plus-additions table count equation. Existing full bundle validates actual foreign keys against historical parent unique keys, including native/XML ALTER definitions.

## Evidence and limits

067 dedicated suite: **13/13 passed**. Full migration suite log: `.implementation-work/logs/part15-migration-checks.log` (final result reported by author/root separately). Python executable: `.implementation-work/python-contracts/bin/python`.

No Maven, global frontend build, production schema edit, real MySQL execution or network service validation was performed. A stale packaged CLI remains a packaging failure, not permission to weaken byte-equality assertions.

Final aggregate run: **121 tests executed; one CLI resource-equality test has three failing subchecks** (067 SQL missing from old executable jar; master.xml and coverage.json stale). All other tests, including full bundle FK-parent unique-key checks and updated exact counts, passed. Recorded result is `FAILED (failures=2, errors=1)` because these are subtest results, not three independent test methods. Await root repackage and CLI rerun; assertions remain strict.

## Root final result after repackage

At 2026-10-18 17:34 UTC, root's 12-module package completed with 121 selected Java suites / 921 passing tests. The final complete offline migration run **122/122 passed** (62.993 seconds), including the added independent invalidation-ledger mapper contract and executable CLI resource equality. Log: `.implementation-work/logs/part15-migrations-final.log`. This supersedes the stale-package failure above, which remains as historical evidence. No real database migration is claimed.

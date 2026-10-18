# Tenant INSERT SELECT compatibility regression

## Observed behavior

While validating the new Gru export mapper with the actual ProtectedTenantInterceptor, `Insert.getValues()` in the protected preflight cast a PlainSelect to Values under JSQLParser 5.2. This also reproduced for three existing mapper paths: SourceGovernanceMapper.insertDeletionItems, TaskPlanWriteMapper.copyTaskInput, and SourceCompletionMapper.saveCheckpoint.

Changing only that type check was insufficient. Root's actual mapper regression then failed for insertDeletionItems: the superclass INSERT traversal did not add the current-tenant predicates to its six derived UNION branches. Its appended source tenant-column projection was not a verified server tenant value. This is evidence of an incorrect SQL rewrite, not evidence of a successful cross-tenant database operation; no live MySQL execution was performed.

## Narrow fix

- Detect Values with `insert.getSelect() instanceof Values`, avoiding the invalid cast.
- Preserve all existing rejections: caller tenant columns, missing columns, IGNORE/duplicate updates, top-level CTE/UNION, no-FROM SELECT and wildcard projections.
- For the already allowed PlainSelect form, invoke the existing full SELECT tenant traversal so nested physical source tables are filtered.
- Append only the current server-verified tenant constant to the outer inserted projection. Do not rely on an ambiguous source column.
- Preserve subject-parameter admission, immutable-table protections, and update/delete restrictions.

## Evidence

TenantAccessTest (6 tests) plus TenantInsertSelectRegressionTest (4 tests) passed in the normal Maven reactor. The first attempt's failure is retained in part18-tenant-insert-select-first.log; the corrected run is part18-tenant-insert-select-second.log (22.823 seconds).

The new tests parse actual bound SQL from all three existing mappers. The derived UNION regression checks all six real branches for both tenant and original source-version predicates, verifies the outer target/projection arity, and checks that the final tenant expression is the verified server constant. Unsupported forms and caller tenant injection remain rejected.

The 071 technical-check recovery changes and new 069/070 domain functionality are separate work; they do not follow from these parser tests. Full project aggregate validation and actual database integration must still be reported separately.

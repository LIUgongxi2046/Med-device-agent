# Simulation evaluation producer persistence (061)

This is an additive, offline-reviewed migration. It has not been applied to any database and does not establish an integrated execution result.

## Storage and identity

- `simulation_evaluation_job` has one unique `(tenant_id, evaluation_id, sample_id, agent_version_id, repeat_index)` cell. Its real execution registry/system job, principal, step/action, simulation context, scope version, parent quota account and fixture registration must exist in the producer transaction. Candidate definition hash and fixture model revision are pinned by compound foreign keys.
- The runtime ROLE is a `capability_version` of kind `ROLE`, not an enterprise RBAC role. Enterprise and parent quota account kinds are separately constrained. Parent account identity must match the actual run and project.
- `invocation_id` is a unique allocated identity for later admission. No invocation row is invented merely to satisfy a foreign key. Start/issuance is not evidence that an invocation ran. Admission must retain the exact ID and bind it to the stored action, context and authority.
- `simulation_scope_manifest` stores actual canonical JSON content, with its ID and content hash registered in `version_registry`. A fabricated registry ID without stored content is insufficient.
- `simulation_context_dependency_manifest` preserves the actual candidate/prompt dependency graph separately from the existing source-only dependency-access manifest. It binds the simulation context and registered candidate/prompt versions. The producer/verifier must reconstruct the closure and compare the stored canonical bytes/hash at use time.

## Lifecycle and immutable content

The only supported job states are `READY`/revision 1, `CLAIMED`/revision 2, and `ISSUED`/revision 3. The first two cannot have issued authority/admission content; the third requires both and binds the authority to the same tenant, project, run, step, action, principal and evaluation. These states do not claim completion or production eligibility.

The producer inserts the cell, source binding, prepared payload and their canonical SHA-256 hashes once. Runtime update statements must modify only the lifecycle fields through compare-and-swap. Unique cell constraints prohibit duplicate starts; they do not independently make arbitrary privileged SQL UPDATEs impossible. Hash recomputation, source rights, grant revision/expiry, current role approval, service binding, retention approval and token issuance remain mandatory application checks. Database JSON shape and digest-format checks are not authorization.

Raw execution tokens are never stored in the job. A lost issuance acknowledgement must not cause credential reissuance. No migration seeds a grant, fixture, source, credential, token, execution or successful outcome.

## Temporal compatibility

Only `system_job_run.job_type='SIMULATION_EVALUATION'` permits (and requires) a null workflow ID. The nullable conversion and compensating CHECK are in the same ALTER. All other system-job types retain their old required workflow IDs; task-run workflow constraints are unchanged. No fictional Temporal workflow is created.

## Offline verification

Run with the existing Python contracts environment:

- `python tests/test_simulation_evaluation_job_migration.py`
- `python tests/test_simulation_ledger_migration.py`
- `python tests/test_migration_bundle.py`

The first suite pins all 59 historical SQL/XML migration files through 060, checks changelog registration, parses new table constraints, and checks the workflow conversion, unique cell, scope content and lifecycle. The bundle suite parses the complete effective schema and validates foreign-key/index compatibility. These parser checks neither connect to MySQL nor prove that a real migration or end-to-end execution succeeded.

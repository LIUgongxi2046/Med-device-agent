# Explicit simulation execution and quota permissions

This additive vocabulary registration does not issue any permission, authority proof, retention approval, quota approval, credential or execution. Existing PermissionSchema/catalogue bytes remain unchanged. Existing grant evaluation still requires current actor, actual resource owner, tenant/project, status, validity interval, identity mode, action and purpose.

## Execute

- Action: `model.simulation.execute`
- Purpose: `SIMULATION_EVALUATION`
- Resource: actual `EVALUATION`, identity `evaluation_batch.evaluation_id`, never the execution registry/job ID.
- Scope inheritance remains the existing explicit ENTERPRISE/PROJECT grant behavior; registration does not add inherited grants.
- `evaluation.create/read/score/finalize`, `configuration.read`, and `model.configure` are not substitutes.
- Applies only to the separately constrained candidate simulation evaluation path. It does not authorize TASK execution, ordinary production SYSTEM_JOB, external provider calls or ROLE publication.
- `SimulationExecutionPermissionPolicy.require` delegates to the actual ResourceReadAuthority and returns the exact GrantRow for stored identity/revision comparison.

## Configure local simulation quota

- Action: `model.simulation.quota.configure`
- Purpose: `SIMULATION_EVALUATION`
- Resource: actual ENTERPRISE for enterprise account approval, actual EVALUATION for the parent evaluation account approval.
- Units: CALL and CANONICAL_UTF8_BYTE only. No currency, token, external provider price or financial budget approval is implied.
- A quota configuration permission does not grant execution. Account/proof consumers must verify the actual approving subject, grant ID/revision, scope, expiry and explicit finite limits. No approved account is seeded here.
- `SimulationQuotaPermissionPolicy` registers the bounded definition and supplies the two actual ResourceReadAuthority scope helpers.

The packaged resources have an independent manifest identity; changing registration data fails the matching code definition. The original permission manifest remains unchanged. Policy tests use the actual existing PermissionAuthority with test grants and do not create live users or approvals.

## Counter provenance pin

`simulation-counter-identity.json` is generated from the actual Python `simulation_control.current_counter()` output. Its implementation hash binds the source bytes; its measurement hash binds the RFC8785 projection schema and counting algorithm. `tests/test_simulation_counter_pin.py` compares all four fields to the actual implementation and repository source bytes. It is build provenance, not approved quota or execution authority. A changed implementation cannot silently reuse a historical counter identity; publication/version transition must be explicit.

No database grant, object service, HTTP invocation or browser acceptance is established by this registration.

## Configure simulation-record retention

`model.simulation.retention.configure` with `SIMULATION_EVALUATION` on the actual EVALUATION registers the distinct retention approval action. `SimulationRetentionPermissionPolicy` delegates real resource authorization. Explicit retain_until and maximum_record_bytes must come from the actual approved simulation retention record and approving actor/grant/revision; SOURCE/SOURCE_CREATED retention is not a substitute. This registration creates no approved records, schedules no deletion and grants no execution permission. Permission to execute or configure quotas does not itself approve retention.

# Formal worker boundary (stable staging subset)

Files `java/FormalWorkerAccess.java`, `java/EnterpriseAccess.java`, `java/ResourceReadAuthority.java`, `java/RunTokenAuthority.java`. The latter three are full staging copies solely to review small diffs; root must merge hunks, not overwrite concurrent shared work.

`execute(http, token, action, Function<EnterpriseAccess.Admission,T>)` delegates to real RunTokenAuthority.execute. `executeAction(..., BiFunction<EnterpriseAccess.Admission,RunTokenAuthority.Admission,T>)` exposes the same authenticated action for invocation/ledger binding. No nested transaction, synthetic session, credential or new grant.

Private formal proof lifetime is strictly the callback and original thread. Its read permission intersection is exact principal `(scope,action,purpose)` entries filtered by immutable authenticated JWT `delegatedActions`, then real ResourceReadAuthority owner/current actor grant checks. Step generation/owner/expiry, persisted actor/principal/scope and current binding recheck occur before and after the operation; original token authority continues mTLS, execution/role/action/current-grant checks. Proof reuse after callback or across threads fails.

Allowed execution kinds are ordinary TASK and the precise SYSTEM_JOB MODEL_VERIFICATION / CONFIGURATION producer. SIMULATION_EVALUATION is excluded. A TASK must retain project/task, no closed dispatch or superseding run, active principal, live lease. Context revision 0 denotes the existing session-free worker transaction context, never a fabricated human session; subject() rejects.

Read-only model/source context needs explicitly delegated read actions. One model action token does not inherit all principal rights. Receipts for already-sent calls after revocation are a separate E-owned original-attempt/mTLS reconciliation authority; this formal read/send bridge does not grant that capability or block E's safe fact recording.

Validation: 10/10 JUnit tests passed using actual FormalWorkerAccess, schema and ResourceReadAuthority, mocked authenticated transport/persistence/grant provider. Positive and negative resource-owner/grant paths, missing delegation, incorrect purpose/scope, stale lease, revoked binding, callback/thread lifetime, private proof constructors are covered. No real DB/mTLS deployment claim. Logs `tests/formal-results.txt`.

## Root review hardening (19:00 UTC)

Now 13/13 focused tests pass. Admission additionally exposes the service URI copied only from the authenticated mTLS service; FormalWorkerAccess checks exact equality with persisted lease_owner. Proof retains the original TenantScope object, exact actor, current thread, and identities of all Spring transaction resource holders. Switching scope/actor or suspending into a different transaction cannot reuse it. Nested TenantScope.open is already forbidden and is explicitly tested. Empty/non-database transaction resource sets fail closed.

Context revision `0` is the existing run-token worker transaction convention, not a real session revision. ResourceReadAuthority, SourceReadAuthority, DependencySourceAuthority, ArtifactRegistry, DependencyLineage and VersionResourceAuthority do not compare it with human session state. Public session handlers remain prohibited (`subject()` fails). The formal contextId/context/watermarks accessors now also check the live proof, so indirect authority reads cannot retain a usable admission after callback exit.

`SourceReadAuthority.java` is an additional minimal shared staging patch: formal worker SESSION reads are denied before any grant/rights/conversation lookup, including REVIEW and HISTORY. PRD §8.3 source ownership table (line 325) requires saving session files to a project and approval before formal task use. LLD-Agent line 93 limits attachments to conversation scope, LLD-数据 line 275 requires separate conversation authorization, and LLD-后端 lines 108/114 require that scope independently. An initiator actor UUID is not a delegated conversation membership proof. Existing human readers and SIM behavior are unchanged.

## 19:05 focused validation and pending policy path

21/21 isolated Java tests now pass: 13 formal boundary, 4 actual workflow waiting transition, and 4 pure exact node permission intersection tests. The node intersection helper is deliberately not an authority: only a real private approved-policy proof and verified token may supply its inputs. It prevents same-action permissions on other node resources leaking through a run-wide permission union. It is not yet wired until A/E private policy/bootstrap producers are available.

The existing frozen RoleScope validator remains unchanged. Its whole-principal-subset check rejects multi-role union; formal policy integration needs a separate exact node branch, not an enlarged published RoleScope. Similarly, MODEL_VERIFICATION DRAFT eligibility requires E's real immutable job/original model.verify decision and current FORMAL role proof before any status/manifest exception. Merely matching job_type is insufficient.

Python N00 validation now has 10/10 tests. Its local StepResult schema permits only actual technical SUCCEEDED with no fabricated artifact/candidate/invocation/waiting IDs and a null error code. Host failures are not fake successful StepResults. This does not claim the durable object/registration/activities/Harness chain is implemented.

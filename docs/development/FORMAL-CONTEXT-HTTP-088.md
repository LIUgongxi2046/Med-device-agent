# Formal evaluation context HTTP entry (088)

Status: implementation staged; isolated verification only. This entry authors, independently reviews, lists and reads context bindings. It does not start a formal execution, create model invocations, settle budgets, approve an Agent for production, or attest professional completion.

## Feature boundary

- Opt in with `medical.formal-evaluation-context.enabled=true`; absent/false keeps the context controller and domain configuration absent.
- `GET /api/formal-evaluation-context-feature-availability` is available to an authenticated current enterprise session even when the feature is disabled.
- When enabled, optional domain wiring is deferred until normal component definitions are registered and conditional on candidate, execution-policy, formal capability, runtime capability and provenance authorities. A missing domain prerequisite leaves the context service/store absent; the opt-in controller remains usable and returns 503 DEPENDENCY_UNAVAILABLE after checking identity. Unrelated application beans can start normally. Mandatory shared identity/tenant/SQL/permission infrastructure retains the application's existing startup contract.
- Availability resolves actual controller, service, store, execution dependency, historical-read authority and frozen-schema bean instances. A type declaration alone is not readiness. `features.bound_preparation` is always false in this slice. Availability describes registered entry dependencies, not database migration health or permission to perform an action.
- No changes to migration 088, its four tables, or `formal-evaluation-context.schema.json`. The separate transport schema is `formal-evaluation-context-http.schema.json`.

## Routes and closed transport

All responses use the normal `InterfaceProtocol.Envelope`. `X-Schema-Version: 1.0.0` and `X-Context-Revision` are required exactly once. Every POST requires ordinary-session CSRF. The existing local/OIDC security chains authenticate `/api/**` and verify CSRF; controllers additionally require the verified current application session. No custom security bypass is added.

| Method/path | Request | Response data/status |
| --- | --- | --- |
| GET `/api/formal-evaluation-context-feature-availability` | No body or query | Availability / 200 |
| POST `/api/formal-evaluation-context-bindings` | Frozen 088 Author; Idempotency-Key | Receipt / 201 |
| POST `/api/formal-evaluation-context-decisions` | Frozen 088 Decide; Idempotency-Key | Receipt / 201 |
| GET `/api/formal-evaluation-context-bindings/{id}` | Canonical UUID; no body or query | View / 200 |
| POST `/api/formal-evaluation-context-bindings/query` | Query | Page / 200 |
| POST `/api/formal-evaluation-context-bindings/recovery` | Recovery | Exact original Receipt / 200 |

`$defs` in the transport schema define every named shape. Author, Decide and the node Definition are copies of the immutable 088 contract; tests compare the complete original definition set for equality. Read-only routes reject Idempotency-Key, including POST query/recovery. All routes reject query parameters, send `Cache-Control: no-store`, and return schema/request/resource-revision headers. Resource revision equals data.revision for View/Receipt and `1` for availability/query. Query input is bounded to 4 KiB; other POST bodies to 1 MiB.

## Permission and original-record semantics

- Author remains `agent.configure`, purpose `AGENT_PUBLICATION`, on the exact project. It persists an immutable DRAFT and original operation/audit/outbox receipt.
- Decide remains `agent.publish` on that project. The deciding actor must differ from both context author and candidate author. Legal transitions remain DRAFT → APPROVED or REJECTED, APPROVED → DISABLED, with exact expected revision.
- Read, query and recovery require current project.read plus current candidate/policy/ROLE/PROMPT/TOOL dependency access. The separate historical-read authority uses the explicit AGENT_PUBLICATION governance purpose consistently for fixed candidate identity, the existing historical-policy reader, and generic version/provenance/source-read checks. It does not require EVALUATION_RESULTS permission merely to inspect governance history. It deliberately does not call the executable policy proof or require a mutable current DRAFT capability head: disabled/expired policies, disabled candidates and superseded/disabled TOOL versions remain inspectable when current privacy/read rights permit. Original fixed membership and policy-node bindings are still checked. Author, decide and execution Approved proof keep their original executable checks unchanged. Recovery requires no new configure/publish write grant solely to read the original receipt.
- Recovery accepts kind AUTHOR or DECISION, original_key and the complete typed original request. It looks up only current tenant/current actor's original key, compares the original canonical request and hash, and rereads the original fixed/state/decision/lineage/version/operation/audit/outbox chain. It never calls author or decide. Missing operation is OBJECT_UNAVAILABLE; different body/revision is IDEMPOTENCY_CONFLICT.
- An author receipt remains DRAFT/revision 1 even after approval; an approval receipt remains APPROVED/revision 2 even after disable. This is an original operation receipt, not current state or a usable approval proof.
- View exposes fixed content, its binding hash, original author reason, current state/revision and the original decision contents. It does not expose stored authorization grants. Historical APPROVED alone does not confer execution authority.

## Directory boundary

Query requires one exact project UUID, limit 1–20 and nullable opaque cursor. SQL uses tenant/project + version_id keyset order and fetches at most limit+1 identifiers; no offset, arbitrary sorting or cross-project search exists. The cursor namespace is `088:CONTEXT_BINDING:<project>` and binds tenant, actor, session, context revision, auth epoch, governance epoch and limit, using the existing five-minute bounded cursor store. Metadata summaries come only after original-record verification and current access checks. Inaccessible records may yield a short/empty page with a next cursor. Integrity failures are never silently treated as empty results.

## Verification and limits

`/tmp/stage23-context-api/tests/run.sh` compiles the staged implementation and invokes JUnit against existing dependency jars and current compiled application classes. Tests cover original 088 regression plus new servlet, real controller→service→store entry, schema, Spring configuration/actual availability including non-lazy enabled startup with each missing domain prerequisite, historical disabled/superseded dependency reads, directory cursor isolation, current-rights revocation and zero-write recovery. SQL, sessions and authority dependencies in these tests are explicit doubles.

No live MySQL migration, deployed session/CSRF filter integration, production capability registration, 083 bean/proof chain, formal EVAL run, provider dispatch, settlement or professional completion was executed. No commit, push, deployment or automatic feature enablement is part of this delivery.


## Parent integration verification
Integrated into the working source after remote checkpoint62ed22f; targeted formal Maven tests:97 passed,0 failure/error/skip. This increment is not yet committed or pushed. Live environment acceptance remains not run.

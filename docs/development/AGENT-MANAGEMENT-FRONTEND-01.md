# S13 Agent management frontend

Implemented 2026-10-18. Frontend-only; no backend changes, real candidate creation, provider calls, production publication or live acceptance performed.

## Delivered

- `/agents` page (`agents.vue`): two top sections, seven keyboard-navigable tabs, design-token-aligned responsive cards, six explicit responsibility descriptions, current configuration metadata from `/api/scenarios`, and immutable authored Agent reads. Directory metadata remains explicitly unverified; existing statuses do not become production eligibility.
- Existing model, simulation-model, budget, evaluation and task pages remain their own authoritative workflows, linked from S13. They are not rewritten or mounted multiple times.
- `configuration-authoring.vue`: genuine closed-contract capability and simulation-Agent draft creation, published local input/output schema choices, current project picker, actual artifact picker, actual simulation-model picker, and full explicit limits. Paginated pickers expose further pages. ROLE/PROMPT fixed-version reads bind hashes automatically; no capability list endpoint exists.
- `configuration-authoring.ts`: imports the authoritative backend closed schema using a local validator, current scope gates, CSRF/context headers, exact-request idempotency metadata, immutable-definition SHA-256 checking, operation lookup and completed-operation reread recovery. Only technical request metadata persists in sessionStorage; source content and authored definitions remain in page memory.
- Unknown/processing/expired writes cannot be resubmitted. NOT_FOUND allows only the identical body, key and original context. A completed operation must be reread and match creator, project, original request digest and optional receipt before metadata is removed.
- Context changes, visibility loss and unmount clear page data and cancel reads. Agent tool/cost upper limits are fixed at zero under the actual SIMULATION contract. No production action is inferred.

## Remaining boundaries

- Skill/tool directory, capability list discovery, production publishing, lifecycle editing/disable and Agent-scoped invocation aggregation have no connected frontend contract here and show bounded unavailable states or link to existing actual task records.
- The six responsibility cards are design guidance only, never persisted or presented as existing executable configuration.
- Sources still require actual registered artifacts, purpose-specific rights, grants and deployment artifact classifications. UI read access cannot grant authoring or dependency use.
- Manual fixed IDs are restricted to ROLE/PROMPT receipt versions because discovery is absent. Project, artifact, model and schema choices use existing real catalogues.
- Full browser visual QA and live DB/service acceptance remain outstanding. Root task owns coordinated typecheck/build/browser verification.

## Focused verification

`node tests/unit/agent-management-checks.mjs` passes. It bundles the adapter with an isolated transport, compiles both Vue SFC scripts/templates, checks authorization boundaries, published schemas, closed tool/cost limits, explicit count relations, idempotency retention and body mismatch, fixed identity/hash checks, exact POST headers/receipt and operation-bound recovery. Test fixtures are isolated synthetic data; they are not product records or evidence of live acceptance.

## Local input preflight correction

Ordinary authoring mistakes now use pure `authorFieldIssue` / `authorInputIssue` preflight and locally caught form errors before the strict adapter. Missing selections, malformed entered capability IDs, missing/invalid seconds or name and inconsistent token limits do not dispatch `medical-failure`, clear the session or initiate requests. Actual component-setup regression tests verify these properties. Strict response schema, hash, identity, permissions, stored-request integrity and immutable-reference checks still use the original fail-closed path; a negative AgentView test explicitly proves that malformed response validation continues to dispatch failure. No global protocol/error handler was relaxed.

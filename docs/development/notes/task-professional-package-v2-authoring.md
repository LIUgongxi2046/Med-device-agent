# Governed physical professional package V2 authoring

This is an A1 sub-scope, not an additional ledger task. Registration creates a governed immutable source artifact only. It never approves a capability or agent release, installs profiles or read-policy entries, configures credentials, or invokes a model.

## Explicit contract selection

The existing V1 routes, authoring resource bytes, package publication bytes, HTTP protocol and default Spring service remain V1. The new closed server-side profile is selected only by these routes:

- GET `/api/projects/{projectId}/task-professional-package-v2-preparation`
- POST `/api/projects/{projectId}/task-professional-packages-v2`
- GET `/api/projects/{projectId}/task-professional-packages-v2/{operationId}`
- POST `/api/projects/{projectId}/task-professional-package-v2-recoveries`

Shared `X-Schema-Version`, `InterfaceProtocol.Envelope`, and `Error` remain `1.0.0`. The separate authoring V2 contract defines Request, Recovery, Preparation, and Status payload roots as `2.0.0`; it does not redefine the shared envelope. Caller-supplied profile, schema reference, route, authority, or approval fields are rejected.

Prompt and Profile package payloads use the published `urn:medical:task-professional-package:2.0.0` definitions and the exact published document hash. ROLE is genuinely the existing `1.0.0` RolePackage, including its V1 document hash. Its prompt capability must resolve to an actual V2 PromptPackage artifact, and its profile must be an actual V2 ProfilePackage artifact. The two dependency originals are physically read and validated before the ROLE intent is created. Preparation selects prompt candidates by their actual registered source-artifact schema, allowing V1 and V2 capabilities to coexist without mixing.

## Immutable identity and recovery

The shared actor lock and globally reserved actor/idempotency key span both versions, including durable pre-intent rejections. V2 originals bind the canonical create route, internal profile, authoring contract hash, and proof-operation discriminator into the immutable intent and canonical request scope. Cross-route reads, key reuse, and recovery fail closed. Recovery retains the original immutable object key and permits only the existing original-object recovery flow, never a replacement PUT.

V2 rejection proof uses `TASK-PROFESSIONAL-PACKAGE-V2` in `X-Task-Package-Operation`, with `X-Task-Package-Route`, `X-Task-Package-Profile`, and `X-Task-Package-Contract-Sha256` derived from the immutable proof. It cannot satisfy the frozen V1 browser's proof discriminator. Ordinary errors never emit pre-intent proof headers.

Artifact, intent, operation, receipt, audit and manifest row metadata retain their existing `schema_version=1.0.0`. That version describes the row contract, not the stored package payload. Package schema references and document hashes are selected per actual kind. No global schema acceptance was broadened and no database migration is needed.

## Reused safeguards and verification boundary

The same implementation retains commit-bound single-use write authority, conditional original PUT, full immutable-original GET, current authority and source-right rechecks, complete dependency manifest and hash readback, immutable registration, lineage, audit/outbox receipts, and revision CAS. Unknown write outcomes remain PROCESSING; phase-two semantic drift remains a generic error without fabricated pre-intent proof.

The V2 tests use explicit offline SQL, source/capability authority and object-store doubles. They do not claim live S3, database, business approval, profile installation, credential use or model execution. The reusable fixture executes the real ledger, transfer/full GET and registration, then exposes actual registered rows and original bytes to the separately owned actual `TaskInvocationPackages.readWorker` and actual A1 Compiler roundtrip tests. Maven verification is coordinated by the root integration gate, not run independently by this worker.

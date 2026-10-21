# B13 customer connector implementation boundary, 2026-10-21

## Latest simulated development update

The user subsequently authorized simulated APIs and testing after remaining development. The historical source-only status below describes the preceding phase, not a continuing test prohibition.

Implemented an explicit `synthetic-customer-http/1.0.0` demonstration contract, an actual bounded HTTPS adapter, opt-in Spring configuration, and a real loopback TLS fixture with eleven targeted JUnit cases. See [the synthetic protocol and execution guide](customer-connector-synthetic-http-20261021.md). The ordinary author/configuration decision/verification/sync/source-import routes are reused; no parallel approval or source-rights path exists. The fixture seeds only clearly labeled synthetic documents, not production principals, grants, approvals or credentials.

Current execution status: authored, not yet executed in this workspace because Maven is absent from PATH. Parent-owned full-suite validation will determine runtime evidence. Adapter-level TLS tests are not full database/application acceptance. Actual customer compatibility remains an external prerequisite, never inferred from successful simulation.

## Historical status before simulation authorization

The source-level application pipeline is implemented; a physical customer protocol is **BLOCKED**. No customer API contract, account mapping or ownership/retention/synchronization semantics have been supplied and approved. No real connection, verification, customer data, credential or approval has been invented. This is not whole-B13 acceptance.

This change was written without running tests, builds, services, migration application, network/provider calls or pushes. Compilation and runtime verification: **NOT_RUN**. The deferred JUnit protocol test source is not execution evidence.

## Implemented application flow

- CT-01 POST `/api/connector-configs` authors an immutable configuration revision. The same stable connector ID has a CAS-protected head. A new revision invalidates old in-flight configuration work, not old source rights. Incomplete input is a persisted draft with `BLOCKED` read status.
- GET `/api/connector-configs/{revision}` returns exact definition/digest, approval state, cursor/revision, missing inputs, adapter/credential-directory availability and the last actual verification observation. Merely creating or approving configuration never means connected.
- POST `/api/connector-configs/{revision}/decisions` records an immutable APPROVED/REVOKED decision pinned to the definition digest and expected revision. Approval checks complete input, existing approved credential reference, local account mappings, current approver membership, binding and enterprise grant. Runtime rechecks current approval authority, expiry, mapping and head.
- CT-02 POST `/api/connector-configs/{revision}/verifications` creates a durable operation before calling a registered exact-spec adapter. Its receipt contains actual adapter exchanges and account/scope observations. No adapter or approved credential directory yields a persisted BLOCKED operation.
- CT-03 POST `/api/connector-configs/{revision}/sync-runs` requires the latest verification to have succeeded. It pins the current cursor revision, maximum page size and exact definition; permits one unresolved sync per revision; persists bounded actual originals and item receipts; detects same-external-version/content conflicts; advances the cursor only in the transaction that commits the whole captured page without conflict. Captured does not mean approved or indexed.
- `/verification-recovery` and `/sync-recovery` accept the original request and original Idempotency-Key. They require adapter recovery mode. A repeated non-recovery request for an unresolved operation also enters recovery-only mode, never blindly resends an uncertain request. A physical adapter must implement durable transport journaling and a per-operation concurrency fence; none is substituted by a fake adapter here.
- GET `/api/connector-runs/{run}` returns durable request/result digests and originals under current connector/project read authority. After actual item-ownership revocation, whole-page originals are conservatively withheld pending filtered review. Immutable operation receipts are not rewritten to conceal earlier observations.
- POST `/api/connector-items/{item}/source-import` requires caller-supplied provenance and rights/retention declaration matching captured bytes, project, media type and acquisition metadata. It uses SourceUploadService and SourceUploadTransport.receiveCapturedSource, not a second source-ingest path. Normal upload completion is durably frozen before queueing, with deterministic create/part/completion keys. Formal rights evidence, rights decision, processing and approval remain the normal source workflow. Initially supported import formats are PDF, text and HTML; other original formats remain captured but are explicitly unsupported for this import path.
- Only an adapter-confirmed actual document-ownership revocation marks imported items REVOKED. SourceReadMapper uses the real `source_upload_result` linkage to deny those source versions. Connector disablement, expired credentials and a disconnected transport do not erase existing stored-source rights. Customer deletion versus ownership revocation semantics must be supplied before the real adapter can map any event into the revocation flag.

## Application contract

The bundled `customer-connector.schema.json` is an application envelope, not a guessed customer API. It requires fields for `schema_version`, `connector_id`, `project_id`, `expected_revision`, nullable `interface_spec`, nullable `account_mapping`, nullable `ownership_scope`, nullable existing `credential_reference_id`/`credential_revision`, expiry and classification. The interface specification is a version plus the exact supplied document. Ownership contains the local project, explicit external scope and explicit document-ownership rules. Mapping lists external account IDs and existing local user IDs. No defaults are silently supplied.

An adapter is selected by SHA-256 of the canonical whole `interface_spec` object. Zero matching adapters means BLOCKED; multiple matches are rejected. The adapter declares its real deployed provider/endpoint identity. CustomerConnectorCredentials checks that identity against the existing approved manifest. The optional deployment property `medical.customer-connector.approved-credential-manifest` references an already-approved manifest in the existing private-file format; this code never creates or saves secrets or provisions persistent access.

Limits: 25 items/page, 8 MiB/original, 32 MiB cumulative exchange bodies and 32 MiB page-original bytes, 8 KiB cursor, 26 exchanges/page, 64 headers per side. These are enforced before durable ingestion. A future physical adapter must enforce them while reading network data, pin/validate its destination and DNS/TLS, refuse unapproved redirects and retries, check current authority around every exchange, and use its existing approved credential handle. Authorization/cookie/key bytes must stay outside durable application receipts; receipts preserve exact non-secret request/response fields, body bytes and the fixed credential handle. Do not add a default HTTP protocol or infer endpoint methods/pagination from arbitrary JSON.

New explicit enterprise actions: `connector.configure`, `connector.read`, `connector.approve`, `connector.sync`, purpose `CUSTOMER_CONNECTOR`. Normal project `source.write`/`source.read` grants are also required. No grants or roles are seeded by this change.

## Remaining customer inputs and acceptance

1. Actual versioned API documentation and approved environment/endpoint identity: authentication reference usage, verification response, list/change feed, original fetch, pagination/cursor consistency, error/rate-limit/retry behavior, and revocation/deletion events.
2. Explicit external-account to local-user mapping, with the local users actually provisioned through normal administration.
3. Explicit document ownership, sync scope, rights, retention and deletion/revocation rules. Define which changes require new source versions and how external version conflicts are reconciled; there is no automatic destructive conflict override.
4. Existing approved credential reference/revision and deployed exact customer adapter. Do not send raw secrets in configuration documents.
5. Authorized compilation, migration registration/application, offline/integration tests and real bounded acceptance after the user permits these steps. Root owns migration master/coverage registration.

A conflict retains the old cursor and all actual operation originals. Resolution must use the approved customer semantics (for example, a corrected upstream external version) followed by a new explicit run. This implementation deliberately does not invent a skip/overwrite policy.

## Deferred verification

`CustomerConnectorProtocolTest` contains offline contract cases for incomplete input, no implicit approval, closed fields, duplicated account mapping, cross-project ownership, unmapped accounts, revocation payload constraints, cursor nonprogress, duplicate IDs, byte/request immutability and secret-header exclusion. Not executed.

Required database/application integration cases, also NOT_RUN: revision races; approval authority revocation; immutable/hash triggers; idempotency mismatch; no-adapter/no-credential BLOCKED receipts; actual verification failure; two concurrent sync producers; crash before/after transport journal commit; recovery-only no-resend; conflict cursor hold; item-import recovery at each upload checkpoint; queued completion followed by actual ownership revocation; source-read anti-join tenant isolation; schema/metadata/FK integrity; original payload bounds; permission denials; and live customer protocol acceptance.

## Files

Production: CustomerConnectorAdapter, CustomerConnectorProtocol, CustomerConnectorController, CustomerConnectorService, CustomerConnectorCredentials, CustomerConnectorConfiguration; customer-connector.schema.json; migration 167-customer-connector.sql; isolated SourceReadMapper.xml source-query anti-join. Deferred test: CustomerConnectorProtocolTest.

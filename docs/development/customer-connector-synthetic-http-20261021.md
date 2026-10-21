# B13 synthetic HTTP demonstration contract, 2026-10-21

## Boundary

Contract version: `synthetic-customer-http/1.0.0`. Every request and response is labeled `mode: SYNTHETIC`. This contract was designed for demonstration and tests; it is not a claim about any customer's actual API. Real compatibility remains blocked on the customer's versioned API, mappings, rights policy and approved credential/environment. Simulation results must not be reported as live customer acceptance or formal source rights evidence.

The server exists only in test sources (`SyntheticCustomerHttpFixture`). It listens on numeric loopback with TLS, generates a short-lived self-signed fixture certificate through the installed JDK keytool, and installs that exact trust anchor in its test client. There is no trust-all manager, TLS-warning bypass, network service dependency, real account, real secret or customer document. The fixed fixture password protects only this newly generated throwaway test certificate; it is not an account credential.

The adapter is a main-source implementation because it exercises the actual `CustomerConnectorAdapter` extension point, protocol validation and ordinary service path. It is not auto-discovered as a component. Spring registration requires BOTH profile `customer-connector-synthetic` and `medical.customer-connector.synthetic.enabled=true`, plus explicit endpoint and journal-directory properties. There is no production fallback registration. Endpoint must be HTTPS numeric `127.0.0.1` with an explicit port, no path/query/userinfo/fragment. Redirects are forbidden and standard TLS verification remains enabled.

## Specification identity

`SyntheticCustomerConnectorAdapter.specification()` returns the exact interface-spec JSON and `specificationSha256()` derives its canonical hash. Use this JSON unchanged in the ordinary connector configuration; adapter matching is exact, not a loose version name. The provider identity is `SYNTHETIC_CUSTOMER_DEMO`; endpoint identity is the explicitly configured URI.

The adapter does not invent an approved credential directory. A separately provisioned, synthetic-only test directory may bind a synthetic test credential handle to that provider/endpoint. The main service still requires its ordinary current approved reference, local membership/mapping, connector approval and enterprise/project grants. No such rows or permissions are created by this adapter or fixture. The adapter does not authenticate to a real customer service; transport access is isolated test TLS only.

## Wire requests

Both endpoints use POST, application/json, a bounded body and a 10-second total transport deadline:

- `/v1/verify`: verify the explicitly requested synthetic account/scope.
- `/v1/page`: obtain one page of the immutable synthetic fixture snapshot.

Request fields: `mode`, `version`, `operation_id`, `project_id`, `maximum_items`, `external_scope`, `cursor`, `accounts`. Accounts contain the explicitly mapped external account identifiers, not local user details. The seeded scope is `{"fixture":"SYNTHETIC"}`, the external account is `SYNTHETIC_ACCOUNT_001`, and the project is `00000000-0000-4000-8000-000000000002`. Other account/project/scope requests return 403. Limits outside 1–25 return 400. Cursor mismatch returns 409.

Verify response fields: `mode`, `version`, boolean `verified`, `external_account_id`, `actual_scope`. The adapter checks account membership and exact scope equality.

Page response fields: `mode`, `version`, array `items`, `next_cursor`, boolean `has_more`. Initial cursor is null. Subsequent cursor is `{"snapshot":"SYNTHETIC_SNAPSHOT_1","offset":N}`. It always identifies the same immutable three-document fixture. The server does not silently mutate the snapshot or reset an invalid cursor.

Each item has: `external_id`, `external_version`, `external_account_id`, `owner_project_id`, `media_type`, `original_url`, `acquired_at`, `original_base64`, and `event`. Originals are embedded to bound one page to one exchange, rather than inventing a customer fetch protocol. Documents are UTF-8 text explicitly containing `SYNTHETIC DEMONSTRATION DOCUMENT`. Events are exactly `UPSERT` or `OWNERSHIP_REVOKED`; revocation requires empty original bytes. A missing item/deletion/transport disconnect is never interpreted as revocation.

The adapter enforces existing original (8 MiB), page (32 MiB), item (25), metadata and cursor limits. The HTTP response is bounded while consuming it, before JSON decoding; timeout or overflow cancels the response subscription. Current authorization is checked before transport, on incoming chunks, after transport and on journal replay. Exact application-specified request headers/body and received response status/headers/body become the exchange receipt. Secret response headers are rejected before journaling.

## Recovery, conflicts and normal workflow

Per-operation file locks fence concurrent transport. A force-synced request digest is atomically stored BEFORE sending. A complete exchange is force-synced and atomically stored before returning to the service. Request digest pins tenant, operation, definition, cursor, limit, kind and endpoint. Reusing an operation with different pins fails. A completed journal can be replayed after process restart without another network request. A started operation with no committed response is uncertain and is never resent, including on ordinary non-recovery calls. Recovery without a journal is also denied. Local durable storage must support atomic rename and directory fsync; do not place the journal on ephemeral/shared storage with weaker guarantees. This demonstration does not promise recovery of a lost journal.

The fixture's test-only `conflict` scenario deliberately returns different bytes for the same ID/version. The adapter preserves that identity; existing service conflict detection retains the cursor rather than overwriting. The `revocation` scenario emits an explicit ownership event. `foreign-project` and `foreign-account` scenarios test rejection. Scenario switches are test harness controls, not HTTP administration endpoints.

Ordinary route sequence remains:

1. Author the exact synthetic configuration through `/api/connector-configs` with synthetic-only mappings and a separately authorized test credential reference.
2. An authorized approver calls the normal `/decisions` route. Registration is not approval.
3. Run `/verifications`; actual loopback exchanges, not stubbed connection booleans, produce receipts.
4. Run `/sync-runs`, then subsequent runs using the durable service cursor. Recover using the original request/key through existing recovery routes.
5. Import captured items through `/api/connector-items/{item}/source-import` with explicit synthetic provenance/rights/retention declarations. The normal source upload, source-rights decision, processing and approval controls remain mandatory. Synthetic content is not valid evidence for production rights or business acceptance.

## Test scope and outstanding evidence

`SyntheticCustomerConnectorAdapterTest` contains eleven executable JUnit methods exercising real HTTPS, verification and two-page capture, durable restart recovery, uncertain journal no-resend, changed operation pins, cross-project/account denial, explicit revocation, conflict-byte preservation, authorization denial endpoint restrictions, concurrent producers and invalid-snapshot no-retry. These prove adapter/transport behavior only once actually executed.

The full database/controller author → approve → verify → sync → import → rights lifecycle, cursor transaction conflict handling, credential revocation and source-read denial still require existing application integration fixtures and database infrastructure. Do not label the eleven transport tests as that full end-to-end scenario. No database migration is needed for the mock adapter. No real service is contacted, no production permission is granted and no push is performed.

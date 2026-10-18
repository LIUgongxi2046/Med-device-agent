# Configuration and rule discovery

## Endpoints

- `GET /api/model-config-revisions` lists the current revision of each MODEL configuration, ordered by `config_id` ascending. Historical fixed revisions remain available through the existing UUID detail endpoint.
- `GET /api/rule-versions` lists readable fixed rule versions, ordered by `rule_version` ascending. It does not imply approval permission.

Both require the existing authenticated identity, `X-Schema-Version: 1.0.0` and exact `X-Context-Revision` headers. The response is the shared envelope, with `resource_revision` equal to `data.current_context.context_revision`, `Cache-Control: no-store`, schema and request ID headers.

Query parameters are closed, scalar and validated:

| Parameter | Contract |
| --- | --- |
| `limit` | Integer 1–100, default 50 |
| `after` | Opaque base64url continuation, at most 2048 characters |
| `q` | Optional NFC, trimmed, nonblank text, at most 256 codepoints, with no control characters |

Search is a case-insensitive literal substring using bound `LOCATE` parameters, never SQL wildcard matching. Models search configuration UUID, provider, model ID and model version; rules search version UUID and engine revision. Characters such as `%` and `_` are literal.

Response contract: `urn:medical:configuration-discovery:1.0.0#/$defs/ModelList` or `RuleList` in `business/medical-persistence/src/main/resources/configuration-discovery.schema.json`. `Query` describes the typed query.

`data` contains exactly `schema_version`, `tenant_id`, `current_context`, `items`, and `next_after`. Model items are the original `model-configuration` `View`; rule items are the original `rule-version-workflow` `Read` (including its nested `rule`). The separate read schema does not alter immutable definition or workflow manifests. No total or hidden record identifier is returned.

## Authorization and bounded work

Candidate queries execute inside the existing enterprise transaction and tenant/subject interceptor. The service calls the original immutable domain readers for every result, including current resource permissions, model redaction, integrity checks and rule dependency authorities. `ACCESS_DENIED` or unavailable candidates are omitted; integrity failures remain failures rather than fabricated or partial records. Model read/save/approval behavior is unchanged apart from extracting a package-private read entry point inside the same admission transaction.

Original PageHelper pagination uses counts disabled, up to 101 candidate rows per query and at most 500 candidate reads per request. When a caller has little visibility, a page may contain zero items and a non-null continuation. The UI must keep Continue available in this case; only a null continuation establishes the end. A cursor may encrypt an internal scan checkpoint so work remains bounded even for predominantly denied data; the checkpoint is never exposed as a readable UUID. The service checks result ordering, current model heads and tenant identity before returning.

Continuations are bound to list kind, query, limit, actor, session, identity mode, context ID, all context revisions/epochs and a deterministic database-time authorization boundary. The temporal boundary covers all actor grant validity transitions and all tenant source-rights validity transitions, conservatively including transitive dependency authorities. It is rechecked after the scan. Grant activation/expiry or source-rights validity changes invalidate the prior sequence even when epochs did not change. Every request still performs live authorization; a cursor grants no access.

## Cursor configuration

Deployment must explicitly provide a dedicated shared AES-256 cursor key, key UUID and lifetime under:

- `medical.configuration-discovery.cursor-key-id`
- `medical.configuration-discovery.cursor-key` (canonical unpadded base64url for 32 bytes)
- `medical.configuration-discovery.cursor-lifetime` (positive, at most one hour)

No runtime credential or configuration is created by this implementation. The missing configuration state fails closed with HTTP 503 `DEPENDENCY_UNAVAILABLE`; partial/invalid configuration fails startup validation. The key identity is authenticated along with the domain separator. Replicas/restarts with the same configured key and identity can consume the same cursors. Key rotation, expiry, tampering, changed query/limit/context/authorization return HTTP 422 `CURSOR_INVALID`. The UI should reload the first page, preserving its search text where appropriate. Never log the key, cursor plaintext, session or internal checkpoints.

The list is current-state keyset discovery, not a frozen catalog export. Refresh from the beginning to see newly inserted records whose IDs sort before the current checkpoint.

## Isolated verification

- `ConfigurationDiscoveryServiceTest`: allowed/denied rows, original domain-reader delegation, stable multi-page results, current context, bounded empty continuation, literal/UUID search, malformed limits, all cursor binding dimensions, unchanged-epoch authorization transitions and fail-closed corrupt rows.
- `ConfigurationDiscoverySchemaTest`: closed query, duplicate parameters, limits, Unicode/text boundaries, exact contexts, absence of totals, authenticated encryption, expiry/tampering, shared-key replicas and missing policy.
- `ConfigurationDiscoveryMapperTest`: real mapper loading, framework page SQL, parameterized literal search, tenant constraints on both candidate joins and all four temporal-boundary branches, subject/transaction guards and cache suppression.

These tests use synthetic fixtures and do not provision credentials, create accounts, call providers or demonstrate live database behavior. Existing model/rule domain service tests remain applicable.

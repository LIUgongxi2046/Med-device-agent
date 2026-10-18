# Document-save business audit reading, 1.0.0 (staging)

Public shape frozen; implementation validation in progress. This is DOCUMENT_SAVE_AUDIT, supporting only document.revision.saved. It is not an all-business/all-audit view. Notifications are not implemented here.

Schema file: business-audit.schema.json, URN urn:medical:business-audit:1.0.0; public definitions Page, Query, Availability. SavedEvent is private validation of an existing producer, never an API response. No original frozen catalogue changes.

## Shared HTTP

Authenticated current business session, exact unique X-Schema-Version:1.0.0 and X-Context-Revision headers. GET only, no body or Transfer-Encoding. Responses are no-store and standard Envelope {schema_version,request_id,resource_revision,data}; resource_revision is current context revision (no invented audit sequence). Request/context/resource headers correspond to envelope. No keys, writes, exports, new grants or jobs.

## Availability

GET /api/business-audit-feature-availability (no query)
Data Availability:
- schema_version 1.0.0
- current_context {tenant_id,context_revision,auth_epoch,governance_epoch}
- features {business_audit:boolean}, from actual reader controller registration
- coverage_scope DOCUMENT_SAVE_AUDIT
- supported_actions [document.revision.saved]
- scope_mode PROJECT_REQUIRED
- maximum_page_size, maximum_window_seconds, maximum_response_bytes: configured positive integers when enabled; null when disabled

Availability describes deployment, not access to any audit/document/source. Disabled does not mean zero events.

## Page

GET /api/audit-events
Required singleton query parameters: project_id (lowercase UUID), action=document.revision.saved, from/to (UTC Z timestamp, at most microsecond precision), limit (positive integer <=availability maximum_page_size). `from` is inclusive and `to` exclusive; from<to and interval <= maximum_window_seconds.
Optional singleton parameters: actor_id (lowercase UUID), document_id (lowercase UUID), cursor (opaque lowercase UUID). All filters must be kept identical through a cursor chain. Reject unknown/duplicate parameters. Client can choose a real project using existing actual project metadata picker; no invented projects/users. Actor filter accepts an already known UUID, not guessed profile names or unauthorized directory autocomplete.

Page data:
{schema_version,current_context,coverage_scope:"DOCUMENT_SAVE_AUDIT",pagination_mode:"LIVE_AUTHORIZED_SCAN",query:{project_id,action,from,to,actor_id:null|UUID,document_id:null|UUID,limit},items:[Entry],complete:boolean,next_cursor:null|opaqueUUID}

Entry:
{audit_id,action:"document.revision.saved",occurred_at:UTC timestamp,actor_id,outcome:"SUCCESS",target:{resource_kind:"DOCUMENT",project_id,document_id,document_revision,read_url}}

The target link is exactly /api/documents/{document_id}/revisions/{document_revision}; selecting it invokes the existing actual fixed-read path, with fresh target/source authorization. Do not treat this summary as downloaded/verified document-body bytes or an approval state. Event actor UUID is historical identity; no automatic name/email resolution.

## Authorization and truthful scope

Per-row real AUDIT ownership + audit.read/AUDIT_READ AND actual DocumentReadService.MetadataRead document.read/DOCUMENT_BODY with complete current upstream source rights/ancestors. Current saved version uses existing FORMAL; old version existing HISTORY. Auditing does not widen any source purpose. No simulation-worker whitelist expansion.

Known event is bound to the real DC-02 completed operation. Reconstruct its original request digest from event+actual snapshot and compare stored request_sha256. Match audit request ID to completed envelope, target/actor/schema/classification, then exact real fixed revision/meta/registry/owner and source-aware metadata proof. Payload reason is consumed only internally to verify original operation hash and is NEVER returned. No raw resource_ref, before/after, private evidence IDs, ObjectRefs/storage addresses, body excerpts, reason text or arbitrary URLs are public.

Only authorized matching saved-document rows appear. No matching/authorized entries is an honest empty page, not a fabricated success event. Other producer categories are outside coverage_scope and do not contribute counts.

## Pagination and UI

Live created_at descending + audit_id descending keyset. Opaque cursor is server-held, bound to current actor, session, tenant, context/auth/governance epochs and full normalized query. It contains no client-readable hidden row ID. Bounded candidate scanning may yield items=[] with complete=false and a valid next_cursor; UI must allow next. No total_count or skipped_count. Do not label complete as complete enterprise audit coverage.

CURSOR_EXPIRED: locally discard accumulated pages/cursor and offer reload with the same filters. CURSOR_INVALID/filter or session/context changes: stop the chain and clear private rows; do not retry stale cursor. Current permission or source revocation hides candidates only for explicit ACCESS_DENIED/SOURCE_NOT_READY/SOURCE_MISSING. Integrity/schema/storage/capacity failure fails the request; do not show it as an empty result. Late commits/new rows ahead of cursor may require refresh; this is not a frozen historical watermark.

Maximum_response_bytes bounds canonical complete HTTP Envelope bytes on server. Browser clients should count UTF-8 before JSON parse using a text response and enforce the same bound; axios browser maxContentLength does not bound transport buffering. No background refresh beyond current authorized view is implied.

## Deployment caveats

Default disabled. Explicit policy capacities plus fixed SQL timeout=5 seconds. Additive 072 adds real tenant/project/action/time/audit index; LIMIT alone is not a bound on scanned DB rows. Live query plans/timeouts remain deployment verification. MetadataRead does not download body bytes but requires actual configured document-reader policy. MetadataRead uses the staged narrow path: bounded fixed header/snapshot/source members and exactly two registered projections; it never assembles checks/reviews/signatures/reasons/document-wide streams. Complete three-artifact/source authorization and same-path currentness remain mandatory. Never swallow CONTEXT_LIMIT.

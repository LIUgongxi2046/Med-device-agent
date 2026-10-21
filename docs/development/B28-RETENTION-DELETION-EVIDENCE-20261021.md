# B28 — Source retention expiry and copy evidence

Implementation-only continuation, 2026-10-21. All new tests, compilation, services,
provider calls, migrations and actual deletions: **NOT_RUN**. No actual data was deleted.

## Implemented production path

The existing source lifecycle uses the approved `retention_policy_revision` SOURCE /
SOURCE_CREATED policy. Source age, positive configured duration, explicit purge
permission, current tenant identity, active references and retention holds are
rechecked before worker operations. Each provider call passes through the existing
lease/current-authority transaction guard. The 074 runtime policy remains separate;
it is never substituted for source deletion authority.

The worker now produces private v2 copy evidence from actual provider operations:

- S3 originals, parsed objects and diagnostics enumerate every version and delete
  marker under their exact authorised key/prefix. All body versions are digest-checked
  where a source digest is available. Object-lock legal holds and unexpired provider
  retention prevent deletion; no governance bypass is used.
- Each acknowledged S3 version deletion records the provider request ID, exact key
  and version and a subsequent absence observation. Already absent is explicitly
  different from a newly acknowledged deletion. A crash after deletion but before
  receipt persistence may recover as ALREADY_ABSENT, never as an invented receipt.
- Redis evidence records actual per-key deletion counts; Qdrant evidence binds the
  completed provider operation and exact zero-count query to tenant/source/parse/index.
- Backups retain the existing authenticated, signed historical catalogue and its
  expiry constraints. Once the source job's backup wait has elapsed, each listed
  backup version can be purged only when the actual object's tenant/source metadata,
  content hash and provider retention agree. Shared/unlabelled archives are refused.
  The worker deletes only that exact version, not an entire backup key's history.
- Backup absence is independently checked by exhausting a version listing over the
  entire configured backup data prefix, including versions omitted from the catalogue.
  Metadata keys are `tenant-id`, `source-version`, `sha256`, `retention-expires-at`.
  Missing/invalid ownership, unknown expiry/hold metadata, pagination/capacity errors,
  provider failures or surviving content cannot yield complete absence evidence.
- Production inventory explicitly says CONFIGURED_STORE_SCOPE. It does not claim to
  discover arbitrary undeclared replicas, remote model-provider retention, offline
  archives or storage accounts for which no authority exists.
- Complete receipts bind the canonical persisted evidence hash. Both worker completion
  and manual confirmation validate the actual stored v2 primary/backup evidence,
  current approved configuration revision and exact configured backup IDs/prefixes.
  Caller-supplied counts/complete flags are insufficient. Old aggregate v1 primary
  evidence is re-observed; immutable historical schemas and receipts are not rewritten.

## Retention, recovery and stop conditions

Source restoration remains limited to RECYCLED sources, verifies the original object
and current authorization, and restores to the existing pending-review gates. Neither
PURGE_PENDING nor PURGED can be restored by presenting an old request. Retry continues
under a new lease generation and current permission/hold checks. Source/grant
revocation, unresolved copies and uncertain provider outcomes remain incomplete.
Missing source policy disables purge; elapsed runtime policy lifetime is not source
purge approval. No new autonomous cleanup of all sources has been introduced.

## Required deployment input, not fabricated evidence

The deployment owner must configure every authoritative original/vector/backup store,
actual credentials and signing key, source policy and backup ownership/retention
metadata. The authenticated historical catalogue must come from the backup authority;
a local inventory with complete=false remains unusable. This implementation produces
and consumes the independent live inventory itself, but does not self-appoint a local
snapshot as the deployment-wide historical backup authority.

Encrypted shared archives, provider-side model retention, offline stores, or data
outside configured scope require their own genuine authoritative adapter/receipt.
Their absence must not be represented as globally verified deletion. Existing dense
cleanup removes local durable gateway originals and SQL vectors; it does not claim
remote provider erasure. Deployment-wide inventory authority must account for this.

## Deferred verification

Added SourceCopyInventoryTest and SourceDeletionEvidenceTest covering incomplete
listings, missing ownership, unknown expiry/hold, retained or expired-but-present
copies, markers over old content, duplicate/out-of-scope identities, legacy evidence
and missing provider acknowledgements. Execute these only when testing is authorised.

Required later provider/integration cases: real versioned S3 pagination and object
locks, backup expiry and shared archive rejection, revoke between provider calls,
crash after delete acknowledgement before persistence, grant/lease expiry, missing
backup catalogue, catalogue omission with live copy, exact configuration changes,
real Redis/Qdrant responses, and database transactional confirmation. No claim that
these scenarios have passed.

## Governed full-copy scope authority (171 additive v2)

The initial configured-scope implementation was insufficient for the B28 completion
condition. The production worker now additionally requires a governed full-copy scope
authority before **every** destructive action, and final manual confirmation requires
the same exact authority ID/hash. This is mandatory, not an optional report field.

Normal authenticated routes under `/api/source-versions/{source}/copy-authority`:

- GET `/configuration`: current non-secret configured topology and exact digest.
- POST `/decisions`: DECLARE, independent APPROVE, or REVOKE under explicit v2 headers.
- GET `/{id}`: reviewer view of the declaration/decision, without private grant proof.
- GET `/recovery/{key}`: original-key read-only recovery; it does not repeat commands.

Both declarer and reviewer must have the existing issuable source.purge / SOURCE_PURGE
permission for the exact source. Approval cannot be by the declarer. Declarations bind
all configured storage locations, explicit external/offline/other scopes, ownership,
configuration digest, reason and at most24-hour validity. Independent approval runs
actual bounded read-only storage probes. A caller cannot supply inventory or a complete
flag as approval evidence. Full object-store scopes are enumerated, not only registered
source keys. Missing ownership/retention metadata, inaccessible adapters and unproven
external stores persist INCOMPLETE, never APPROVED.

Known dense model-provider sends (including uncertain/withdrawn operations lacking
proof of never sending) independently prevent approval/completion. Declaring no provider
copies cannot override those records. OFFLINE/PROVIDER/OTHER scopes have no fabricated
adapter: they remain UNSUPPORTED_AUTHORITY until genuine adapters are implemented.
Thus deployment inputs are distinct from currently unsupported remote-erasure code.

Declarations and decisions are immutable, source/project-bound, tenant-scoped and linked
to actual creator/time/classification, a pinned schema digest, audit and outbox witnesses.
Current use verifies both original identities/grants and their revisions, declaration
hash, independent approval, scope/configuration hash, expiry and absence of revocation.
Final source completion additionally requires fresh whole-object-scope absence, whole
source-filter vector absence, and source cache absence, catching unregistered copies
that per-item receipts would miss. Unmanaged surviving copies block completion rather
than being silently treated as deleted. A renewed approved scope causes primary evidence
to be re-observed, not promoted from stale authority.

New table: source_copy_authority. Immutable UPDATE and DELETE guards apply to every row;
new decisions append rather than mutate. Migration171 and source-copy-authority-v2.schema.json
require root registration. Existing v1 schemas remain untouched. Six additional authority
contract/decision tests were authored and not run (18 total new deferred tests).

Provider-side remote/offline erasure adapters remain explicitly unsupported; normal
SOURCE storage with complete declared/probed authority has its production pathway.
No actual authority, grant, inventory, delete result or test pass was created during
this implementation. Root owns ledger status and formal registration.

## 2026-10-21 external protocol implementation (supersedes unsupported-code note above)

External OFFLINE and PROVIDER erasure now have an explicit, closed
`medical-external-erasure/1` protocol, HTTP client, registered-authority selection,
authenticated application producer/consumer, durable original-key journal and
SOURCE authority integration. This is a generic protocol requiring a real authority
implementation; it does not claim any commercial provider already supports it.
OTHER and unregistered scopes still fail closed. No real service has been called.

Configuration is opt-in through `medical.external-erasure.authorities`, a JSON array
of `scopeId`, `kind`, `revision`, `proof`, `endpoint`, `credentialProperty`, and
`journalDirectory`. HTTPS endpoint must end in `/`, have no query/fragment/userinfo,
and redirects and retries are disabled. Credential property must be under
`medical.external-erasure.credentials.`; credentials never enter evidence/topology.
Journal directory must already exist on deployment-owned persistent private storage.
The journal uses serialized cross-process CAS, atomic replacement and fsync before
dispatch. Preserve it across restarts; never replace it with ephemeral storage.
No automatic mock fallback, bean registration of a mock, or default provider exists.

Protocol endpoints are POST inventory, hold, delete and recovery with explicit
X-Schema-Version 1.0.0 and closed records in external-erasure-v1.schema.json. Responses
are bounded to 1 MiB. Inventory pages must bind one scope/revision/snapshot/time,
exhaust pagination and report authoritative coverage of known provider operation IDs.
Coverage remains after erasure; an empty copy list alone cannot hide dense sends.
Snapshots identify an immutable authorized inventory generation; a changed snapshot
requires a renewed independent approval before another delete. Hold and exact-copy
identity/version/digest are checked immediately before dispatch and must also be
atomically enforced by the authoritative provider API. Holds may never be bypassed.

Normal routes:
- POST /api/source-versions/{source}/external-erasure
- GET /api/source-versions/{source}/external-erasure/{authority}/{scope}/inventory
- GET /api/source-versions/{source}/external-erasure/{authority}/{scope}/recovery/{key}

The body cannot select proof mode, endpoint, completeness or approval contents.
Existing independent source declaration/approval supplies these. Current identities,
grants, source policy/age, references/holds, PURGE_PENDING state, authority expiry,
configuration and revocation are rechecked around each external call. Each exact
request is persisted before its single dispatch. DISPATCHING/UNKNOWN recovery only
reads the original provider key; missing acknowledgement never permits resend.
Revoked recovery is denied rather than disclosing stored receipts.

SOURCE approval probes every declared/registered external scope and demands coverage
of all non-PREPARED dense-operation IDs, including uncertain/WITHDRAWN operations.
Before any real SOURCE destructive action or completion, all external inventories
are re-observed and must contain no surviving copies. This stronger ordering means
external erasure must finish first. Existing SOURCE retention, lease and configured
store checks remain in place. An empty registry keeps the original SOURCE-only path;
external copies without registered authority remain blocked.

SyntheticErasureService is an explicitly constructed, isolated in-memory API with
sim-* fixture identities and invented digests only. Its receipts are SIMULATED.
Neither registered simulation scopes nor simulation receipts can satisfy production
SourceCopyAuthority, final real absence, or PURGED. The normal application path test
uses an explicit mocked identity/source-governance seam to supply a synthetic
independent approval; it is not evidence of real database authorization or erasure.

Added 18 adapter/journal cases and 4 application/contract cases: offline/provider
inventory and exact deletion; independent review; incomplete inventory; unknown and
lost acknowledgements; no resend after restart; legal/unknown/retention holds; final
absence with hidden surviving copy; revoked authority and recovery; simulated-proof
rejection; and closed contracts. Standalone SPI javac compilation passed against the
prepared project dependency cache. JUnit and complete application/database integration
are pending the root's full native test run. No migration is required.

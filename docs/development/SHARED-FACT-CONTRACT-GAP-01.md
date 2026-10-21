# Shared-fact producer and consumer contract review

Status: historical gap review. The additive implementation contract, migration 136 and current scope are recorded in SHARED-FACT-LIFECYCLE-136.md. The historical observations below describe the pre-136 system; they are not current implementation claims.

## Exact evidence

- PRD.md BR-06/07 (line 156): each verifiable fact has authorized original evidence and location; strict fields are compared to designated authoritative material. Accessible evidence is not proof that it supports the statement.
- PRD.md BR-23 (174) and AC-55 (644): preserve original values/unit/models/conditions; list actual affected document positions; preserve issued versions and trigger re-verification.
- HLD.md 452, 502: SharedFactVersion is verified by research/registration personnel; a new fact affects direct and dependent usages, invalidates affected pending confirmations, never overwrites old signed content.
- LLD-后端.md 118: field.confirm requires valid checking results at the authorized project/field/section scope. No existing fact-specific check-baseline contract was found.
- LLD-后端.md 215: shared_fact_version.status is only S(24); DC-04 at 475 specifies raw value/unit/model/condition/authority/verification decision and a new fact plus impact, without a closed request/response schema.
- LLD-数据.md 209: fact authority, evidence and verifier are required; there is no fact-status enumeration. The six states at 323 explicitly describe FieldValue, not SharedFactVersion. They cannot silently be reused as a fact lifecycle.
- LLD-数据.md 298–307 requires actual change proposals, bounded complete dependency impact, graph watermarks and current legality; asynchronous impact cannot leave old confirmations usable.
- docs/后端实施计划.md 281–286: BE-24 depends on BE-10/11/16/23 and requires actual use locations, current authorization and complete impact before confirmation.
- business/medical-data/src/main/resources/db/migration/005-document-evidence.sql 176–190: immutable fact-version columns include raw_value, optional DECIMAL(38,12) value/unit, model_ids, condition_id, evidence_manifest, verifier_id and unbounded-enum status VARCHAR(24). There is no fact head/current pointer, CAS revision or status CHECK.
- The same migration 212–225 defines actual fact_usage, fixed document/node/field/row foreign keys and reverse indexes. No fake usage is needed or permitted.
- backend-design/interfaces.schema.yaml 207–212 defines FactUsage only. No SharedFactVersion producer or verification schema exists there. data-design/dataset.schema.json defines closed EvidenceRef but no fact content contract. agent-design/artifacts.schema.json 160–161 references fact_version, not its allowed lifecycle.

## Existing consumer behavior and compatibility risk

VersionResourceMapper.xml SHARED_FACT branch (122–125) returns actual project owner and content_sha256, but no status or verification record. VersionResourceAuthority.requireOwner checks registry/tenant/project/hash and requires fact.read. DocumentBodyDomainAuthority.java 85–88 accepts a referenced version if its domain is SHARED_FACT and its owner project equals the document project.

Consequently a new unverified row in shared_fact_version would currently be structurally consumable as a fact. A producer must not create REVIEW_PENDING/DRAFT SHARED_FACT records and rely on the UI label to prevent formal use. No draft producer is included. Adding status=CONFIRMED or VERIFIED to the reader would invent an enum and could reject historical valid facts without a migration/compatibility specification.

No product-model or condition identity producer/table was found for the model_ids/condition_id columns. The model_config_revision table is an AI model configuration, not a device model directory. Arbitrary UUID entry must not be presented as verified device model/condition binding.

## Specification decisions required before safe implementation

1. Define closed fact content and allowed statuses, including whether drafts are a separate entity/domain and the status of historical existing records.
2. Define the real authoritative evidence manifest and canonical content digest, including value type/decimal scale, unit policy, device model identities, conditions and explicit unknown values.
3. Define human verification decision identity, required current checks, authority action/purpose, approval separation where applicable, and the semantics of verifier_id. Mere requester identity cannot attest verification.
4. Define immutable version lineage and fact-key head/CAS semantics, original-key recovery, and the exact point at which the version may enter SHARED_FACT consumption.
5. Define direct/dependent impact completeness, source/currentness watermark ownership, confirmation invalidation and signed-history behavior. Do not reuse impact_manifest without its required real change_proposal.
6. Define compatibility: existing records need a documented policy or migration, not blanket rejection based on an invented state.

## Optional narrow read-only actual-use inventory

A useful independent endpoint could enumerate actual fact_usage for one already registered same-project formal fact version, without producing or approving a fact:

- Require current project fact.read and impact.read plus actual fixed document.read/source authorization for every returned usage. Prefer the authorization-only metadata proof for list access; open the original fixed body when the user follows a position.
- Verify the fact's real registry/project/hash against shared_fact_version. This structural validation does not imply a missing professional verification contract has been fulfilled.
- Query real usage_id, document_revision, document_id, node_id, field_id, row_id, current document head and actual signature state. Verify joins and ledger/body relationships where applicable. Zero rows means actual zero direct usages for that exact fixed version in the scan scope.
- Return a clearly named direct_usage inventory, not a globally complete change impact. Other dependencies/exports and professional review effects are not inferred from fact_usage.
- A bounded complete scan under TenantTransaction's locked tenant watermark can prove a single-transaction direct-use result. DocumentRevisionService.saveUsages (343–353) inserts real usages and rechecks projections but does not advance governance_epoch. Therefore governance_epoch alone is not a durable fact-usage graph watermark across pagination.
- For pagination either persist a real immutable scan snapshot/canonical member digest or introduce an explicit correctly maintained usage watermark in all producers. Do not claim a total graph watermark merely because page cursors are signed.
- If unauthorized items are hidden, distinguish completeness of the authorized projection from completeness of all usages. Do not return hidden counts/IDs. “All impacts reviewed” cannot be enabled from a filtered projection.
- Reading usages does not mutate facts, documents, task states, review decisions or signatures and does not mark a revision workflow complete.

## Deliverable boundary

No 069 migration was authored. No frozen schema/old SQL or shared consumer was modified. The independent fact draft and producer were paused on the explicit decision that they would not deliver the formal cross-document workflow. The next safe step is the missing fact verification/lifecycle specification, followed by focused consumer negative tests grounded in that specification.

## Minimal proposed lifecycle and verification contract (proposal, not an existing enum)

The following is a design proposal requiring contract approval. These names are not claimed to exist in the current database and are not implemented as gates.

1. Separate a `FactProposal` from the consumable `SharedFactVersion`. Proposal revisions are immutable, have a real current head/CAS and remain outside the SHARED_FACT version domain. Proposed proposal states are EDITING, SUBMITTED, RETURNED and ADOPTED. Saving or submitting a proposal does not verify a fact. An explicit adoption transaction is the only prospective path that could register the final immutable fact version once all actual required records exist.
2. Use append-only `FactHumanVerification` for the current user's explicit evidence assessment. Bind proposal revision/content hash, exact original source/parse/chunk/locator/quote/approval/rights references, actual model/condition scope, decision (SUPPORTS / CONFLICT / NEEDS_EVIDENCE as proposed names), reason, actor, action/grant witness, timestamp and context. The server takes actor/time from actual admission/database, never from client claims. A SUPPORTS decision alone cannot assert all professional checks passed.
3. Keep `FactCheckBaseline` distinct from the human decision. It must cite actual fixed check results, their defined coverage/rule versions, exact fact and evidence inputs and currentness proofs. A missing or undefined applicable rule set must produce an explicit check-requirement gap; never invent a rule/version ID, default PASS or let an empty set masquerade as complete. A required-rule manifest must itself come from the authorized rules domain, not an ad hoc constant in a service.
4. Define an explicit adoption policy for each supported fact class. It determines which human assessment and which actual deterministic/professional checks are necessary. A future class requiring only an explicit human assessment still requires an approved policy saying so; the implementation must not infer that no checks are needed. Conflicts and UNKNOWN input remain unresolved.
5. Store immutable `FactAdoption` referencing the proposal revision, human decision, policy revision and real check baseline; only then create a version in shared_fact_version and registry with approved canonical content and lineage edges. A separate head/status history expresses current adoption/retirement. New consumption validates this ledger and policy; legal historical reads can retain prior versions according to the approved history policy. New versions do not rewrite existing document bodies or signatures.
6. Changing an adopted fact requires a new proposal and new independent adoption record. It computes an actual direct/dependent impact snapshot and current invalidation transaction before claims of complete propagation. Issued bodies retain their bytes/signatures; the system adds revision-needed information rather than mutating the historical content.

### Existing-record migration and rollout

First perform a read-only inventory of actual existing shared_fact_version rows: distinct literal status values, registry/hash/schema identities, verifier references, evidence manifest shapes, source authorization/currentness, fact_usage bindings and any real human decision/audit records. Offline repository fixtures are not proof that deployed data is absent.

Do not rewrite a legacy status to an invented new enum or backfill human/check/adoption records. Existing records lacking an adoption ledger need an explicit reviewed compatibility classification, with evidence-based mapping of any known old status meaning. A proposed migration can add a sidecar compatibility assessment containing the original row hash, assessment actor/time/reason, evidence and permitted history/new-use policy. It preserves original bytes and records and is not itself professional verification. Unknown legacy records remain explicitly unresolved in the rollout report.

Only an approved migration/compatibility policy may decide which established old records remain valid for future consumption, which are history-only, and which need fresh verification. Feature rollout must account for every impacted consumer before enabling a stricter gate. Existing signed document history must not become silently rewritten or presented as unsigned; if authorization or provenance no longer permits reading, apply the already defined read-denial path with clear reasons.

### Suggested initial tests after contract approval

- Proposal IDs cannot be used as SHARED_FACT document dependencies.
- Human evidence assessment without required actual checks cannot adopt.
- Correctly authorized original evidence with a mismatching quote/locator or unsupported statement remains rejected/unresolved.
- Cross-project facts, device-model scope mismatch, unknown conditions, stale evidence/rights/check inputs and replaced rule policy all block new adoption/consumption as the approved policy defines.
- Real legacy mapped records follow their explicit compatibility policy; unmapped values are not silently normalized.
- CAS races, duplicate original keys and lost write permission during read-only recovery preserve the original operation receipt.
- Direct usage zero is returned only after the real query; incomplete/filtered dependency impact never enables confirmation.
- New fact revisions cannot change existing saved/signed document bytes or signatures.

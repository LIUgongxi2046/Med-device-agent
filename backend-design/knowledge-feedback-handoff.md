# B30 knowledge and rule feedback (PRD 10.1 step 7)

Implementation status: code and deferred test cases written. Tests, builds, services, migrations, source processors, providers and external calls were NOT_RUN. No feedback, approval, impact application or business closure was executed by this implementation task.

## Boundary

This is distinct from the existing formal-Agent OperationsFeedback family. SOURCE and RULE feedback never requires an Agent evaluation, acceptance or publication. A feedback closure does not approve a document, restore an invalidated review, sign a revision, grant source rights or replace a source/rule head. Receipts explicitly identify `KNOWLEDGE_RULE_FEEDBACK_ONLY`, and `downstream_document_confirmation_claimed` remains false. TP06's application records downstream invalidation/revision work; its original `version_transition_performed=false` semantics remain unchanged.

## HTTP entry points

All routes use `/api/business-operations/knowledge-feedback`, `X-Schema-Version: 1.0.0`, the current `X-Context-Revision`, and the authenticated human session. Mutation calls also require a printable 16–128 character `Idempotency-Key`.

- POST base: `project_id`, `kind` (SOURCE or RULE), `target_version`, `document_revision`, `revised_document_revision`, `review_id`, `return_id`, `reason`. All reference fields must be present; unused references are null. Starts PENDING_REVIEW.
- POST `/{id}/proposals`: `expected_revision`, `proposal_id`, `reason`. Links an actual same-project, same-kind TP06 proposal, targeting the original version and a different actual revision. Moves to PROPOSAL_LINKED.
- POST `/{id}/reviews`: `expected_revision`, `decision_id`, `reason`. Consumes a genuine ACCEPT decision, complete hashed impact manifest, actual application and matching downstream revision/invalidation entries. Moves to REVIEWED.
- POST `/{id}/closures`: `expected_revision`, `approval_id`, `source_selection`, `reason`. SOURCE requires source_selection with parse_version, index_version and binding_revision; RULE requires null. Moves to CLOSED only with the genuine current source/rule approval.
- POST `/{id}/rejections`: `expected_revision`, `reason`. Disposes of pending or proposal-linked feedback, without changing or pretending to reject the linked proposal itself.
- GET base with `project_id` and optional `after_id`: 100-item keyset listing. A page requiring unavailable source/document authority fails closed, rather than leaking or silently skipping it.
- GET `/{id}`: original immutable references and full chronological event chain, limited to 1000. Listing/history are explicitly historical, not a certificate of present approval validity.

## Existing workflow integration

1. Create feedback before producing its revised SOURCE/RULE version. Original source/rule content remains immutable.
2. Produce the new version through existing source or rule governance. SOURCE revisions must be versions of the same source identity. RULE revisions must declare the original target as their exact base version. New version and proposal creation must not predate feedback creation.
3. Use existing VersionChangeImpactController POST `/api/change-proposals`, POST `/{id}/impact-scans`, and POST `/{id}/decisions`. Existing source rights, reviewer, complete graph, target coverage and downstream invalidation restrictions remain mandatory.
4. Link the proposal and accepted review using the new routes.
5. Finish source approval or rule approval through its existing workflow, then close with that actual approval ID. SOURCE consumes SourceReadAuthority.requireCandidate (current rights, approved gate, complete parse, verified index, current applicable project binding) and the registry-hashed approval decision plus KN-09 operation. RULE consumes RuleVersionStore.read (full definition/hash/dependency verification), current APPROVED revision, matching rule_approval_decision, receipt/baseline/dependency hashes and operation.
6. Downstream document revision/reconfirmation work continues through the existing TP06/B09 gates. Closing feedback never completes those tasks implicitly.

## Preserved originals and authority

The optional original document revision is read through DocumentReadService.authorizeMetadata. A supplied revised document must be a genuine direct revision of that same document in the same project. `review_id` must be an actual RETURN review for the original revision; `return_id` must be an actual RETURN_FOR_REVISION request. Their immutable identifiers, original hashes, reasons and actors are retained; none is overwritten or represented as clinical approval.

All operations require a human admission, current context and project feedback permissions. Actual source rights and owner-project scope are checked on every read/mutation/replay; private SESSION sources are rejected. Rule definition readers enforce real rule.read and dependency authority. Impact review consumes the designated reviewer's still-current identity and unchanged original grant. Origin classification is the maximum of project, target, rights and document/return/review evidence. Later higher-classified dependencies are rejected rather than laundered under the original classification.

## Storage and registration

Migration: `business/medical-data/src/main/resources/db/migration/177-knowledge-rule-feedback.sql`.

New tables:
- knowledge_feedback_origin: immutable original JSON + SHA256; schema version/manifest, creator/time/classification; tenant/project feedback identity; target/version digest FK; original/revised document, review and return FKs.
- knowledge_feedback_event: immutable per-revision receipt chain + SHA256; actor/idempotency/request digest; schema/creator/time/classification; actual proposal, revision, application, source approval or rule approval references.

The existing feedback row remains the CAS-protected head. Its original fields and proposal_id are never rewritten. Migration 177 adds conditional head guards and mutual exclusion between knowledge and formal-Agent sidecars, without modifying migration 172. Insert triggers verify hashes, references, chain, actual impact application and current approval kind/status. Audit/outbox writes share the same tenant transaction. Retried mutations return the exact recorded receipt only after current source/document authorization; they do not create a second event or repeat approval actions.

The owning parent registers migration 177 in master.xml and updates table/schema coverage. This slice does not edit those shared files or task counts.

## Deferred verification matrix

Written but not run:
- KnowledgeFeedbackSchemaTest: SOURCE/RULE acceptance, AGENT/FACT rejection, explicit original fields, duplicate/trailing/unknown JSON rejection, no publication or caller success flags, transition matrix, classification downgrade rejection.
- KnowledgeFeedbackControllerTest: exact closure route/body forwarding, no-store/schema response headers, unsupported schema short-circuit.

Still requires an authorized MySQL/application fixture run after the user resumes testing:
- SOURCE and RULE full create/link/review/close with actual governance originals.
- Wrong-tenant/wrong-project/private SESSION targets; expired/revoked rights; denied rule dependencies; stale reviewer identity/grant.
- Unrelated source identity, unrelated rule base, revision/proposal predating feedback, changed/missing manifest/hash and incomplete impact graph.
- Missing or mismatched application, revision target and original review invalidation.
- Missing/current-withdrawn source approval, pending/failed parsing/index, noncurrent project binding, returned/disabled rule or stale rule approval revision.
- Changed approval ID, forged receipt/baseline/dependency digest, conflicting idempotency key, stale expected revision, exact replay after a later workflow change without new approval claims.
- Both feedback domains cannot attach to the same ID, both head triggers coexist, immutable updates/deletes fail, and transaction rollback leaves no head/event/audit partial state.
- Required downstream reconfirmation remains open and cannot be bypassed by feedback closure.

No claim of successful compilation, migration application, schema fixture execution, integration tests or production business completion is made.

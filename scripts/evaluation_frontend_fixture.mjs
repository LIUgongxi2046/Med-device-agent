// Synthetic protocol data for isolated UI tests only. No server, model, customer or production evidence.
import { randomUUID, createHash } from 'node:crypto'
import { canonicalizeEx } from '../web/node_modules/json-canonicalize/dist/index.mjs'
export const digest = value => createHash('sha256').update(canonicalizeEx(value, { strictUndefined: true })).digest('hex')
export const basis = () => ({ version_id: randomUUID(), content_sha256: 'a'.repeat(64) })
export function evaluationFixture() {
  const actor = randomUUID(), tenant = randomUUID(), evaluationId = randomUUID(), candidateAuthor = randomUUID()
  const session = { schema_version: '1.0.0', user: { user_id: actor, username: null, display_name: 'SIMULATION', platform_role: null },
    current_context: { tenant_id: tenant, context_revision: '1', auth_epoch: '1', governance_epoch: '1' },
    memberships: [{ tenant_id: tenant, member_id: randomUUID(), status: 'ACTIVE', valid_from: '2020-01-01T00:00:00Z', valid_to: null, revision: '1' }],
    csrf: { header_name: 'X-CSRF-TOKEN', token: 'SIMULATION-CSRF' }, invitation_entry: { method: 'POST', path_template: '/api/member-invitations/{id}/acceptances' },
    action_summary: ['evaluation.create', 'evaluation.read', 'evaluation.score', 'evaluation.finalize'].map(action => ({ action, project_id: null, resource_kind: 'ENTERPRISE', resource_id: tenant })) }
  const sample = { sample_id: randomUUID(), source_family: randomUUID(), split: 'ACCEPTANCE', document_type: 'SIMULATION document', evidence_class: 'SIMULATION', input_ref: basis(), expected_ref: basis(),
    expectations: [{ dimension: 'EVAL-02', applicable_item_ids: ['SIMULATION-item-1', 'SIMULATION-item-2'], control_item_ids: [] }] }
  sample.expected_ref.content_sha256 = digest({ schema_version: '1.0.0', sample_id: sample.sample_id, source_family: sample.source_family, document_type: sample.document_type, input_ref: sample.input_ref, expectations: sample.expectations })
  const create = { schema_version: '1.0.0', project_id: null, dataset: basis(), sample_manifest_artifact: { ...basis(), content_sha256: digest([sample]) }, samples: [sample], candidates: [basis()],
    rule_revision: basis(), required_dimensions: ['EVAL-02'], variance_policy: { repeat_count: 1, policy: 'ALL_REQUIRED_RUNS', confidence_interval_method: null }, authorized_use_evidence: basis(), environment: 'SIMULATION', reason: 'SIMULATION 登记已有输出' }
  const definition = { schema_version: '1.0.0', tenant_id: tenant, evaluation_id: evaluationId, request: create,
    candidate_snapshots: [{ ...create.candidates[0], agent_id: randomUUID(), created_by: candidateAuthor, dependency_manifest: {}, limits: {}, schema_version: '1.0.0', schema_manifest_sha256: 'b'.repeat(64) }],
    approved_expectation_samples: [sample.sample_id], authority_grant_id: randomUUID(), authority_revision: '1', created_by: actor, created_at: '2026-10-18T00:00:00Z' }
  const view = { schema_version: '1.0.0', evaluation_id: evaluationId, content_sha256: digest(definition), status: 'QUEUED', revision: '1', definition, score_history: [], result: null, result_sha256: null, production_eligible: false, allowed_actions: [] }
  const append = { schema_version: '1.0.0', expected_revision: '1', definition_sha256: view.content_sha256,
    cell: { sample_id: sample.sample_id, agent_version_id: create.candidates[0].version_id, dimension: 'EVAL-02', repeat_index: 0 }, input_ref: sample.input_ref, output_ref: basis(), evidence_refs: [basis()],
    numerator: '2', denominator: '2', unknown_count: '0', violation_count: '0', control_numerator: null, control_denominator: null, status: 'SCORED', supersedes: null, reason: 'SIMULATION 专家观察' }
  function score(body) { return { schema_version: '1.0.0', score_id: randomUUID(), evaluation_id: evaluationId, definition_sha256: view.content_sha256, rule_revision: create.rule_revision,
    observation: structuredClone(body), scorer_id: actor, authority_grant_id: randomUUID(), authority_revision: '1', created_at: '2026-10-18T00:01:00Z' } }
  function result(history) {
    const superseded = new Set(history.map(item => item.observation.supersedes)), effective = history.filter(item => !superseded.has(item.score_id)), latest = effective[0]
    const status = latest?.observation.status === 'SCORED' ? 'PASS' : 'INSUFFICIENT_EVIDENCE'
    const reason = !latest ? 'MISSING_SCORE' : status === 'PASS' ? 'DOCUMENTED_INVARIANTS_MET' : latest.observation.status + '_SCORE'
    return { schema_version: '1.0.0', evaluation_id: evaluationId, definition_sha256: view.content_sha256, score_set_sha256: digest(effective), effective_scores: effective,
      cells: [{ cell: append.cell, score_id: latest?.score_id ?? null, status, reason, value: status === 'PASS' ? '1.0000' : null }],
      dimensions: [{ dimension: 'EVAL-02', expected_cells: 1, observed_cells: latest ? 1 : 0, passed_cells: status === 'PASS' ? 1 : 0, failed_cells: 0, insufficient_cells: status === 'PASS' ? 0 : 1 }],
      missing_requirements: ['SIMULATION_IS_NOT_BUSINESS_ACCEPTANCE', ...(status === 'PASS' ? [] : [reason])].sort(), verdict: 'INSUFFICIENT_EVIDENCE', production_eligible: false }
  }
  return { actor, tenant, evaluationId, candidateAuthor, session, create, definition, view, append, score, result }
}

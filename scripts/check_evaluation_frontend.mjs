import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'
import { evaluationFixture, digest, basis } from './evaluation_frontend_fixture.mjs'
globalThis.window = new EventTarget()
const saved = new Map()
globalThis.sessionStorage = { getItem: key => saved.get(key) ?? null, setItem: (key, value) => saved.set(key, value), removeItem: key => saved.delete(key) }
const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: domain } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/evaluation-domain.ts', import.meta.url)), { root, mode: 'medical' })
const { module: protocol } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/protocol.ts', import.meta.url)), { root, mode: 'medical' })
const f = evaluationFixture(); let checks = 0
const ok = (actual, expected = true) => { assert.equal(actual, expected); checks++ }
const rejects = async (task, code) => { await assert.rejects(task, error => error.code === code); checks++ }
const throws = (task, code) => { assert.throws(task, error => error.code === code); checks++ }
const read = view => domain.verifyEvaluationRead(f.session, f.evaluationId, view, view.revision)
await domain.validateEvaluationCreate(f.create); checks++
await read(f.view); checks++
ok(domain.evaluationMatrix(f.create).length, 1)
for (const text of ['{"schema_version":"1.0.0","schema_version":"1.0.0"}', '{} {}', '{/*comment*/}', '{"repeat_count":9007199254740993}', '{"repeat_count":1.000000000000000001}']) throws(() => domain.parseEvaluationJson(text), 'SCHEMA_INVALID')
ok(domain.parseEvaluationJson(JSON.stringify(f.create)).environment, 'SIMULATION')
for (const change of [{ samples: [f.create.samples[0], f.create.samples[0]] }, { candidates: [f.create.candidates[0], f.create.candidates[0]] }, { reason: ' padded ' }, { extra: true }]) await rejects(() => domain.validateEvaluationCreate({ ...f.create, ...change }), 'SCHEMA_INVALID')
await rejects(() => domain.validateEvaluationCreate({ ...f.create, sample_manifest_artifact: basis() }), 'CONTENT_MISMATCH')
const wrongInput = structuredClone(f.create); wrongInput.samples[0].input_ref = basis()
await rejects(() => domain.validateEvaluationCreate(wrongInput), 'CONTENT_MISMATCH')
const second = structuredClone(f.create.samples[0]); second.sample_id = randomUUID(); second.split = 'TUNING'; second.expected_ref = { ...basis(), content_sha256: digest({ schema_version: '1.0.0', sample_id: second.sample_id, source_family: second.source_family, document_type: second.document_type, input_ref: second.input_ref, expectations: second.expectations }) }
await rejects(() => domain.validateEvaluationCreate({ ...f.create, samples: [f.create.samples[0], second] }), 'SCHEMA_INVALID')
const maximum = structuredClone(f.create); maximum.required_dimensions = ['EVAL-01', 'EVAL-02']; maximum.variance_policy.repeat_count = 100; maximum.samples = Array.from({ length: 100 }, () => ({ ...f.create.samples[0], sample_id: randomUUID() }))
await rejects(() => domain.validateEvaluationCreate(maximum), 'SCHEMA_INVALID')
const empty = { ...f.create, samples: [], sample_manifest_artifact: { ...basis(), content_sha256: digest([]) } }; await domain.validateEvaluationCreate(empty); checks++
const project = randomUUID(), other = randomUUID()
ok(domain.evaluationAllowed(f.session, 'evaluation.create', null, null))
ok(domain.evaluationAllowed(null, 'evaluation.read'), false)
const projectOnly = { ...f.session, action_summary: [{ action: 'evaluation.create', project_id: project, resource_kind: 'PROJECT', resource_id: project }] }
ok(domain.evaluationAllowed(projectOnly, 'evaluation.create', project, null)); ok(domain.evaluationAllowed(projectOnly, 'evaluation.create', null, null), false)
ok(domain.evaluationAllowed(projectOnly, 'evaluation.create', other, null), false)
const evalOnly = { ...f.session, action_summary: [{ action: 'evaluation.read', project_id: project, resource_kind: 'EVALUATION', resource_id: f.evaluationId }] }
ok(domain.evaluationAllowed(evalOnly, 'evaluation.read', project, f.evaluationId)); ok(domain.evaluationAllowed(evalOnly, 'evaluation.read', null, f.evaluationId), false)
ok(domain.evaluationAllowed(evalOnly, 'evaluation.read', project, other), false)
ok(domain.evaluationAllowed({ ...f.session, action_summary: [{ action: 'evaluation.create', project_id: null, resource_kind: 'EVALUATION', resource_id: f.evaluationId }] }, 'evaluation.create', null, null), false)
for (const change of [{ tenant_id: other }, { evaluation_id: other }, { candidate_snapshots: [] }]) await rejects(() => read({ ...f.view, definition: { ...f.definition, ...change } }), change.candidate_snapshots ? 'SCHEMA_INVALID' : 'CONTENT_MISMATCH')
await rejects(() => read({ ...f.view, production_eligible: true }), 'SCHEMA_INVALID')
await rejects(() => read({ ...f.view, content_sha256: 'f'.repeat(64) }), 'CONTENT_MISMATCH')
await rejects(() => read({ ...f.view, revision: '2' }), 'CONTENT_MISMATCH')
await rejects(() => domain.verifyEvaluationRead({ ...f.session, action_summary: [] }, f.evaluationId, f.view, '1'), 'CONTENT_MISMATCH')
const pending = await domain.prepareEvaluation(f.session, 'EV-01', f.create, null)
ok((await domain.prepareEvaluation(f.session, 'EV-01', f.create, null)).key, pending.key)
await rejects(() => domain.prepareEvaluation(f.session, 'EV-01', { ...f.create, reason: 'SIMULATION changed' }, null), 'IDEMPOTENCY_CONFLICT')
await domain.verifyEvaluationRequest(f.session, pending, f.create); checks++
await rejects(() => domain.verifyEvaluationRequest({ ...f.session, current_context: { ...f.session.current_context, context_revision: '2' } }, pending, f.create), 'CONTENT_MISMATCH')
await rejects(() => domain.verifyEvaluationRequest({ ...f.session, action_summary: [] }, pending, f.create), 'CONTENT_MISMATCH')
const stored = [...saved.values()].join('')
for (const secret of [f.create.reason, f.append.reason, f.session.csrf.token, f.create.dataset.version_id, f.create.samples[0].sample_id]) ok(stored.includes(secret), false)
ok(domain.readPendingEvaluation({ ...f.session, user: { ...f.session.user, user_id: other } }), null)
ok(domain.readPendingEvaluation({ ...f.session, current_context: { ...f.session.current_context, tenant_id: other } }), null)
const operationId = randomUUID(), operation = { operation_id: operationId, resource_id: f.evaluationId, status: 'COMPLETED', status_url: '/api/operations/' + operationId }
const receipt = { schema_version: '1.0.0', operation_id: operationId, evaluation_id: f.evaluationId, definition_sha256: f.view.content_sha256, revision: '1', status: 'QUEUED', score_id: null, result_sha256: null, verdict: null, production_eligible: false }
domain.verifyEvaluationReceipt(pending, receipt, '1'); checks++
throws(() => domain.verifyEvaluationReceipt(pending, { ...receipt, status: 'COMPLETED' }, '1'), 'CONTENT_MISMATCH')
await rejects(() => domain.confirmEvaluationResult(f.session, pending, { ...operation, status: 'PROCESSING' }, f.view, receipt), 'CONTENT_MISMATCH')
ok(domain.readPendingEvaluation(f.session).key, pending.key)
await domain.confirmEvaluationResult(f.session, pending, operation, f.view, receipt); ok(saved.size, 0)
domain.validateEvaluationScoreTarget(f.view, f.append, f.actor); checks++
for (const change of [{ numerator: '3' }, { denominator: null }, { output_ref: null }, { evidence_refs: [] }, { status: 'UNKNOWN' }, { numerator: 2 }, { unknown_count: '-1' }]) throws(() => domain.validateEvaluationAppend({ ...f.append, ...change }), 'SCHEMA_INVALID')
throws(() => domain.validateEvaluationScoreTarget(f.view, { ...f.append, denominator: '3' }, f.actor), 'CONTENT_MISMATCH')
throws(() => domain.validateEvaluationScoreTarget(f.view, { ...f.append, cell: { ...f.append.cell, repeat_index: 1 } }, f.actor), 'CONTENT_MISMATCH')
throws(() => domain.validateEvaluationScoreTarget(f.view, f.append, f.candidateAuthor), 'ACCESS_DENIED')
throws(() => domain.validateEvaluationScoreTarget(f.view, { ...f.append, supersedes: other }, f.actor), 'REVISION_CONFLICT')
const first = f.score(f.append), running = { ...f.view, status: 'RUNNING', revision: '2', score_history: [first] }
await read(running); checks++
throws(() => domain.validateEvaluationScoreTarget(running, { ...f.append, expected_revision: '2' }, f.actor), 'REVISION_CONFLICT')
const correctedBody = { ...f.append, expected_revision: '2', supersedes: first.score_id, reason: 'SIMULATION 更正理由' }, correction = f.score(correctedBody), corrected = { ...running, revision: '3', score_history: [first, correction] }
await read(corrected); checks++
ok(domain.effectiveEvaluationScores(f.definition, f.view.content_sha256, corrected.score_history).values().next().value.score_id, correction.score_id)
await rejects(() => read({ ...corrected, score_history: [first, { ...correction, observation: { ...correction.observation, output_ref: basis() } }] }), 'CONTENT_MISMATCH')
await rejects(() => read({ ...corrected, score_history: [first, { ...correction, observation: { ...correction.observation, supersedes: other } }] }), 'CONTENT_MISMATCH')
const scorePending = await domain.prepareEvaluation(f.session, 'EV-02', f.append, f.view)
const scoreReceipt = { ...receipt, revision: '2', status: 'RUNNING', score_id: first.score_id }
domain.verifyEvaluationReceipt(scorePending, scoreReceipt, '2'); checks++
await domain.confirmEvaluationResult(f.session, scorePending, operation, corrected, scoreReceipt); ok(saved.size, 0)
const finalBody = { schema_version: '1.0.0', expected_revision: '3', definition_sha256: f.view.content_sha256 }, finalPending = await domain.prepareEvaluation(f.session, 'EV-03', finalBody, corrected)
const result = f.result(corrected.score_history), finished = { ...corrected, revision: '4', status: 'INSUFFICIENT_EVIDENCE', result, result_sha256: digest(result) }
await read(finished); checks++
for (const change of [{ production_eligible: true }, { cells: [] }, { effective_scores: [] }, { dimensions: [{ ...result.dimensions[0], observed_cells: 0 }] }, { verdict: 'PASS', missing_requirements: [] }]) {
  const changed = { ...result, ...change }; await rejects(() => read({ ...finished, result: changed, result_sha256: digest(changed) }), change.production_eligible ? 'SCHEMA_INVALID' : 'CONTENT_MISMATCH')
}
const finalReceipt = { ...receipt, revision: '4', status: 'INSUFFICIENT_EVIDENCE', result_sha256: finished.result_sha256, verdict: 'INSUFFICIENT_EVIDENCE' }
domain.verifyEvaluationReceipt(finalPending, finalReceipt, '4'); checks++
await domain.confirmEvaluationResult(f.session, finalPending, operation, finished, finalReceipt); ok(saved.size, 0)
await rejects(() => domain.prepareEvaluation(f.session, 'EV-02', { ...f.append, expected_revision: '4', supersedes: correction.score_id }, finished), 'REVISION_CONFLICT')
const aborted = new AbortController(); aborted.abort(); await assert.rejects(() => domain.prepareEvaluation(f.session, 'EV-01', f.create, null, aborted.signal), error => error.name === 'AbortError'); checks++; ok(saved.size, 0)
throws(() => protocol.validate(domain.evaluationViewSchema + 'Pending', { ...pending, reason: 'forbidden' }), 'SCHEMA_INVALID')
// Response-integrity adversaries: internally consistent hashes must never promote insufficient evidence to PASS.
function finishedView(fixture, history, result) {
  return { ...fixture.view, revision: String(history.length + 2), status: result.verdict === 'PASS' ? 'COMPLETED' : result.verdict === 'FAIL' ? 'FAILED' : 'INSUFFICIENT_EVIDENCE', score_history: history, result, result_sha256: digest(result) }
}
function falsePass(result) {
  return { ...result, cells: result.cells.map(item => ({ ...item, status: 'PASS', reason: 'DOCUMENTED_INVARIANTS_MET', value: '1.0000' })),
    dimensions: result.dimensions.map(item => ({ ...item, passed_cells: item.expected_cells, failed_cells: 0, insufficient_cells: 0 })), missing_requirements: [], verdict: 'PASS' }
}
const missingResult = f.result([])
await read(finishedView(f, [], missingResult)); checks++
await rejects(() => read(finishedView(f, [], falsePass(missingResult))), 'CONTENT_MISMATCH')
for (const status of ['UNKNOWN', 'BLOCKED']) {
  const observation = { ...f.append, status, numerator: null, denominator: null, unknown_count: null, violation_count: null }, observed = f.score(observation), history = [observed]
  const result = f.result(history); await read(finishedView(f, history, result)); checks++
  await rejects(() => read(finishedView(f, history, falsePass(result))), 'CONTENT_MISMATCH')
}
await rejects(() => read({ ...corrected, score_history: [first, { ...correction, observation: { ...correction.observation, expected_revision: '1' } }] }), 'CONTENT_MISMATCH')
await rejects(() => read({ ...running, score_history: [{ ...first, scorer_id: f.candidateAuthor }] }), 'CONTENT_MISMATCH')
const wrongRatio = { ...result, cells: result.cells.map(item => ({ ...item, value: '0.0000' })) }
await rejects(() => read(finishedView(f, corrected.score_history, wrongRatio)), 'CONTENT_MISMATCH')
const missingReason = { ...result, missing_requirements: [...result.missing_requirements, 'FAKE_REQUIREMENT'].sort() }
await rejects(() => read(finishedView(f, corrected.score_history, missingReason)), 'CONTENT_MISMATCH')
const scopedReadOnly = { ...f.session, action_summary: [
  { action: 'evaluation.create', project_id: null, resource_kind: 'ENTERPRISE', resource_id: f.tenant },
  { action: 'evaluation.read', project_id: null, resource_kind: 'EVALUATION', resource_id: other }
] }
await rejects(() => domain.prepareEvaluation(scopedReadOnly, 'EV-01', f.create, null), 'ACCESS_DENIED'); ok(saved.size, 0)
// Empty definitions remain valid data; even hashes and aggregate claims cannot turn them into production evidence.
const emptyFixture = evaluationFixture(); emptyFixture.definition.request.samples = []; emptyFixture.definition.request.sample_manifest_artifact.content_sha256 = digest([]); emptyFixture.definition.approved_expectation_samples = []
emptyFixture.view.content_sha256 = digest(emptyFixture.definition)
const emptyResult = { schema_version: '1.0.0', evaluation_id: emptyFixture.evaluationId, definition_sha256: emptyFixture.view.content_sha256,
  score_set_sha256: digest([]), effective_scores: [], cells: [], dimensions: [{ dimension: 'EVAL-02', expected_cells: 0, observed_cells: 0, passed_cells: 0, failed_cells: 0, insufficient_cells: 0 }],
  missing_requirements: ['NO_SAMPLES', 'SIMULATION_IS_NOT_BUSINESS_ACCEPTANCE'], verdict: 'INSUFFICIENT_EVIDENCE', production_eligible: false }
await domain.verifyEvaluationRead(emptyFixture.session, emptyFixture.evaluationId, finishedView(emptyFixture, [], emptyResult), '2'); checks++
await rejects(() => domain.verifyEvaluationRead(emptyFixture.session, emptyFixture.evaluationId, finishedView(emptyFixture, [], falsePass(emptyResult)), '2'), 'CONTENT_MISMATCH')
const zeroEpochSession = { ...f.session, current_context: { ...f.session.current_context, auth_epoch: '0', governance_epoch: '0' } }
const zeroEpochPending = await domain.prepareEvaluation(zeroEpochSession, 'EV-01', f.create, null)
ok(domain.readPendingEvaluation(zeroEpochSession).context.auth_epoch, '0')
await domain.confirmEvaluationResult(zeroEpochSession, zeroEpochPending, operation, f.view, receipt); ok(saved.size, 0)
console.log(JSON.stringify({ result: 'PASS', checks, data_kind: 'SIMULATION', actual_http_verified: false, provider_calls: 0 }))

import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'
import { evaluationFixture, digest } from './evaluation_frontend_fixture.mjs'
globalThis.window = new EventTarget()
const saved = new Map(), calls = []
globalThis.sessionStorage = { getItem: key => saved.get(key) ?? null, setItem: (key, value) => saved.set(key, value), removeItem: key => saved.delete(key) }
const root = fileURLToPath(new URL('../web/', import.meta.url))
const mock = { name: 'evaluation-no-network-transport', enforce: 'pre', resolveId(id) { if (id === '@/utils/request') return '\0evaluation-mock-request' }, load(id) {
  if (id === '\0evaluation-mock-request') return `export default { get: (...args) => globalThis.evaluationTransport('GET', ...args), post: (...args) => globalThis.evaluationTransport('POST', ...args) }`
} }
const { module: api } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/evaluations.ts', import.meta.url)), { root, mode: 'medical', plugins: [mock] })
const { module: domain } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/evaluation-domain.ts', import.meta.url)), { root, mode: 'medical' })
const f = evaluationFixture(); let checks = 0, view = structuredClone(f.view), currentOperation = null, failWrite = false
const ok = (actual, expected = true) => { assert.equal(actual, expected); checks++ }
function envelope(data, revision = null, status = 200) {
  const requestId = randomUUID(); return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': requestId },
    data: { schema_version: '1.0.0', request_id: requestId, resource_revision: revision, data } }
}
globalThis.evaluationTransport = async (method, path, first, second) => {
  const body = method === 'POST' ? first : null, config = method === 'POST' ? second : first
  calls.push({ method, path, body, config })
  assert.equal(config.headers['X-Schema-Version'], '1.0.0'); assert.equal(config.headers['X-Context-Revision'], '1'); assert.equal(config.params, undefined)
  if (method === 'GET') {
    assert.equal(config.headers['Idempotency-Key'], undefined)
    if (path === '/api/evaluation-batches/' + f.evaluationId) return envelope(view, view.revision)
    if (path === '/api/evaluation-batches/' + f.evaluationId + '/scores') { const { definition, ...history } = view; return envelope(history, view.revision) }
    if (path === currentOperation?.status_url) return envelope(currentOperation)
    throw new Error('Unexpected mock read: ' + path)
  }
  assert.equal(config.headers['X-CSRF-TOKEN'], 'SIMULATION-CSRF'); assert.match(config.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
  if (failWrite) throw new Error('SIMULATION uncertain transport failure')
  const operationId = randomUUID(); currentOperation = { operation_id: operationId, resource_id: f.evaluationId, status: 'COMPLETED', status_url: '/api/operations/' + operationId }
  let scoreId = null, status = 201
  if (path === '/api/evaluation-batches') assert.deepEqual(body, f.create)
  else if (path === '/api/evaluation-batches/' + f.evaluationId + '/scores') {
    const score = f.score(body); scoreId = score.score_id; view = { ...view, revision: String(BigInt(view.revision) + 1n), status: 'RUNNING', score_history: [...view.score_history, score] }
  } else if (path === '/api/evaluation-batches/' + f.evaluationId + '/finalize') {
    const result = f.result(view.score_history); status = 200; view = { ...view, revision: String(BigInt(view.revision) + 1n), status: 'INSUFFICIENT_EVIDENCE', result, result_sha256: digest(result) }
  } else throw new Error('Unexpected mock write: ' + path)
  return envelope({ schema_version: '1.0.0', operation_id: operationId, evaluation_id: f.evaluationId, definition_sha256: view.content_sha256, revision: view.revision,
    status: view.status, score_id: scoreId, result_sha256: view.result_sha256, verdict: view.result?.verdict ?? null, production_eligible: false }, view.revision, status)
}
const signal = new AbortController().signal
const created = await domain.prepareEvaluation(f.session, 'EV-01', f.create, null)
failWrite = true
await assert.rejects(() => api.submitEvaluation(f.session, created, f.create, signal), /uncertain/); checks++
ok(domain.readPendingEvaluation(f.session).key, created.key)
failWrite = false
const receipt = await api.submitEvaluation(f.session, created, f.create, signal)
ok(calls[0].config.headers['Idempotency-Key'], calls[1].config.headers['Idempotency-Key'])
await api.recoverEvaluation(f.session, created, receipt.operation_id, receipt, signal); ok(saved.size, 0)
const loaded = await api.readEvaluation(f.session, f.evaluationId, signal); ok(loaded.revision, '1')
const scorePending = await domain.prepareEvaluation(f.session, 'EV-02', f.append, loaded)
const scoreReceipt = await api.submitEvaluation(f.session, scorePending, f.append, signal)
let scored = await api.recoverEvaluation(f.session, scorePending, scoreReceipt.operation_id, scoreReceipt, signal); ok(scored.score_history.length, 1)
scored = await api.readEvaluationScores(f.session, scored, signal); ok(scored.revision, '2')
const correctedBody = { ...f.append, expected_revision: scored.revision, supersedes: scored.score_history[0].score_id, reason: 'SIMULATION 更正观察' }
const correction = await domain.prepareEvaluation(f.session, 'EV-02', correctedBody, scored), correctedReceipt = await api.submitEvaluation(f.session, correction, correctedBody, signal)
scored = await api.recoverEvaluation(f.session, correction, correctedReceipt.operation_id, null, signal); ok(scored.score_history.length, 2)
const body = { schema_version: '1.0.0', expected_revision: scored.revision, definition_sha256: scored.content_sha256 }
const finalize = await domain.prepareEvaluation(f.session, 'EV-03', body, scored), finalReceipt = await api.submitEvaluation(f.session, finalize, body, signal)
const finished = await api.recoverEvaluation(f.session, finalize, finalReceipt.operation_id, finalReceipt, signal)
ok(finished.result.verdict, 'INSUFFICIENT_EVIDENCE'); ok(finished.production_eligible, false); ok(saved.size, 0)
const before = calls.length
await assert.rejects(() => api.readEvaluation(f.session, '../other', signal)); checks++; ok(calls.length, before)
const aborted = new AbortController(); aborted.abort()
await assert.rejects(() => api.submitEvaluation(f.session, finalize, body, aborted.signal)); checks++; ok(calls.length, before)
console.log(JSON.stringify({ result: 'PASS', checks, mocked_transport_calls: calls.length, data_kind: 'SIMULATION', actual_http_verified: false, provider_calls: 0 }))

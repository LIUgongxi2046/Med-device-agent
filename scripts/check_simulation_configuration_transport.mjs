import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'
import { simulationConfigurationFixture } from './simulation_configuration_fixture.mjs'

globalThis.window = new EventTarget()
const stored = new Map(), calls = []
globalThis.sessionStorage = { getItem: key => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value), removeItem: key => stored.delete(key) }
const root = fileURLToPath(new URL('../web/', import.meta.url))
const mock = { name: 'simulation-configuration-no-network-transport', enforce: 'pre', resolveId(id) { if (id === '@/utils/request') return '\0simulation-configuration-mock-request' }, load(id) {
  if (id === '\0simulation-configuration-mock-request') return `export default { get: (...args) => globalThis.simulationConfigurationTransport('GET', ...args), post: (...args) => globalThis.simulationConfigurationTransport('POST', ...args) }`
} }
const importLocal = async (name, mocked = false) => (await runnerImport(fileURLToPath(new URL('../web/src/api/medical/' + name + '.ts', import.meta.url)), { root, mode: 'medical', ...(mocked ? { plugins: [mock] } : {}) })).module
const api = await importLocal('simulation-model-configurations', true), domain = await importLocal('simulation-model-configuration-domain'), operations = await importLocal('operations', true)
const f = simulationConfigurationFixture(), signal = new AbortController().signal
let checks = 0, failWrite = false, responseOverride = null
const ok = (actual, expected = true) => { assert.equal(actual, expected); checks++ }
const rejects = async (task, code) => { await assert.rejects(task, error => error.code === code); checks++ }
function envelope(data, revision = null, status = 200) {
  const requestId = randomUUID(); return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': requestId },
    data: { schema_version: '1.0.0', request_id: requestId, resource_revision: revision, data } }
}
globalThis.simulationConfigurationTransport = async (method, path, first, second) => {
  const body = method === 'POST' ? first : null, config = method === 'POST' ? second : first
  calls.push({ method, path, body: structuredClone(body), config })
  assert.equal(config.medical, 'envelope'); assert.equal(config.headers['X-Schema-Version'], '1.0.0'); assert.equal(config.headers['X-Context-Revision'], '1')
  if (responseOverride) return responseOverride
  if (method === 'GET') {
    assert.equal(config.signal, signal); assert.equal(config.headers['Idempotency-Key'], undefined)
    if (path === '/api/simulation-model-config-revisions') { assert.deepEqual(config.params, { limit: 20, q: 'SIMULATION:declared', after: 'opaque_cursor' }); return envelope(f.listing, '1') }
    assert.equal(config.params, undefined)
    if (path === '/api/simulation-model-config-revisions/' + f.fixed) return envelope(f.view, '1')
    if (path === f.operation.status_url) return envelope(f.operation)
    throw new Error('Unexpected mock read: ' + path)
  }
  assert.equal(config.headers['X-CSRF-TOKEN'], 'SIMULATION-CSRF')
  if (path === '/api/operation-lookups') {
    assert.deepEqual(body, { operation_id: 'CP-08', idempotency_key: domain.readPendingSimulation(f.session).key })
    assert.equal(config.headers['Idempotency-Key'], undefined); return envelope({ lookup_status: 'FOUND', operation: f.operation })
  }
  assert.equal(path, '/api/simulation-model-config-revisions'); assert.deepEqual(body, f.body)
  assert.match(config.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
  if (failWrite) throw new Error('SIMULATION uncertain transport failure')
  return envelope(f.receipt, '1', 201)
}
const pending = await domain.prepareSimulationSave(f.session, f.body)
failWrite = true; await assert.rejects(() => api.saveSimulationConfiguration(f.session, pending, f.body), /uncertain/); checks++
ok(domain.readPendingSimulation(f.session).key, pending.key)
failWrite = false; const receipt = await api.saveSimulationConfiguration(f.session, pending, f.body)
ok(calls[0].config.headers['Idempotency-Key'], calls[1].config.headers['Idempotency-Key'])
ok(receipt.execution_mode, 'SIMULATION'); ok(receipt.production_eligible, false)
await api.listSimulationConfigurations(f.session, { limit: 20, q: 'SIMULATION:declared', after: 'opaque_cursor' }, signal); checks++
const lookup = await operations.lookupOperation(f.session, 'CP-08', pending.key, signal); ok(lookup.lookup_status, 'FOUND')
const operation = await operations.readOperation(f.session, lookup.operation.operation_id, signal)
const loaded = await api.readSimulationConfiguration(f.session, operation.resource_id, signal)
await domain.confirmSimulationResult(f.session, pending, operation, loaded, receipt, signal); ok(stored.size, 0)
const next = await domain.prepareSimulationSave(f.session, f.body), before = calls.length
await rejects(() => api.readSimulationConfiguration(f.session, '../external', signal), 'SCHEMA_INVALID')
await rejects(() => api.readSimulationConfiguration({ ...f.session, action_summary: [] }, f.fixed, signal), 'ACCESS_DENIED')
await rejects(() => api.listSimulationConfigurations(f.session, { limit: 20, q: ' padded ' }, signal), 'SCHEMA_INVALID')
await rejects(() => api.listSimulationConfigurations(f.session, { limit: 20, q: 'e\u0301' }, signal), 'SCHEMA_INVALID')
await rejects(() => api.saveSimulationConfiguration(f.session, next, { ...f.body, reason: 'changed' }), 'CONTENT_MISMATCH')
const aborted = new AbortController(); aborted.abort()
await assert.rejects(() => api.readSimulationConfiguration(f.session, f.fixed, aborted.signal), error => error.name === 'AbortError'); checks++
await assert.rejects(() => api.saveSimulationConfiguration(f.session, next, f.body, aborted.signal), error => error.name === 'AbortError'); checks++
await assert.rejects(() => api.listSimulationConfigurations(f.session, { limit: 20 }, aborted.signal), error => error.name === 'AbortError'); checks++
ok(calls.length, before)
// Invalidation while WebCrypto verifies the request must prevent first dispatch, not merely discard its response.
const digest = crypto.subtle.digest
let enterDigest, releaseDigest
const entered = new Promise(resolve => { enterDigest = resolve }), gate = new Promise(resolve => { releaseDigest = resolve })
const page = new AbortController(), raceCalls = calls.length
try {
  crypto.subtle.digest = async function (...args) { enterDigest(); await gate; return digest.apply(this, args) }
  const racingWrite = api.saveSimulationConfiguration(f.session, next, f.body, page.signal)
  await entered; page.abort(); releaseDigest()
  await assert.rejects(() => racingWrite, error => error.name === 'AbortError'); checks++
  ok(calls.length, raceCalls); ok(domain.readPendingSimulation(f.session).key, next.key)
} finally { releaseDigest(); crypto.subtle.digest = digest }
for (const field of ['execution_mode', 'production_eligible']) {
  const data = { ...f.receipt }; delete data[field]; responseOverride = envelope(data, '1', 201)
  await rejects(() => api.saveSimulationConfiguration(f.session, next, f.body), 'SCHEMA_INVALID')
  ok(domain.readPendingSimulation(f.session).key, next.key)
}
responseOverride = envelope(f.receipt, '1', 200)
await rejects(() => api.saveSimulationConfiguration(f.session, next, f.body), 'CONTENT_MISMATCH')
responseOverride = envelope({ ...f.view, definition: { ...f.view.definition, execution_mode: 'EXTERNAL' } }, '1')
await rejects(() => api.readSimulationConfiguration(f.session, f.fixed, signal), 'SCHEMA_INVALID')
responseOverride = envelope(f.view, '1'); responseOverride.headers['cache-control'] = 'public'
await rejects(() => api.readSimulationConfiguration(f.session, f.fixed, signal), 'SCHEMA_INVALID')
responseOverride = envelope(f.view, '1'); responseOverride.headers['x-request-id'] = randomUUID()
await rejects(() => api.readSimulationConfiguration(f.session, f.fixed, signal), 'SCHEMA_INVALID')
ok(domain.readPendingSimulation(f.session).key, next.key)
responseOverride = null
await domain.confirmSimulationResult(f.session, next, f.operation, f.view, null, signal); ok(stored.size, 0)
console.log(JSON.stringify({ result: 'PASS', checks, mocked_transport_calls: calls.length, data_kind: 'SIMULATION', actual_http_verified: false, provider_calls: 0 }))

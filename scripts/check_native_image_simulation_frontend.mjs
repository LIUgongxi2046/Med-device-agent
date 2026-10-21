import assert from 'node:assert/strict'
import vm from 'node:vm'
import { createHash, randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { readFileSync } from 'node:fs'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'
import { simulationConfigurationFixture } from './simulation_configuration_fixture.mjs'
globalThis.window = new EventTarget()
const calls = []
let override = null
const bytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64')
const fixture = { source_version: randomUUID(), rights_revision: randomUUID(), object_ref: 'fixture/synthetic.png', sha256: createHash('sha256').update(bytes).digest('hex'), mime: 'image/png', width: 1, height: 1, region: { x: 0, y: 0, width: 1, height: 1 }, base64: bytes.toString('base64') }
const identity = { schema_version: '1.0.0', mode: 'SIM', synthetic: true, formal_evidence_eligible: false }
const profile = { ...identity, provider: 'local-synthetic-image-v1', capability: 'NATIVE_IMAGE_TRANSPORT_SIM', fixture, persistence: 'PROCESS_LOCAL_ONLY' }
globalThis.nativeImageTestTransport = async (method, path, body, config) => {
  if (method === 'GET') { config = body; body = undefined }
  calls.push({ method, path, body: structuredClone(body), config })
  assert.equal(config.medical, 'envelope')
  assert.equal(config.headers['X-Schema-Version'], '1.0.0')
  assert.equal(config.headers['X-Context-Revision'], '1')
  if (method === 'POST') {
    assert.equal(config.headers['Idempotency-Key'], (body.answer ?? body).request_id)
    assert.equal(config.headers['X-CSRF-TOKEN'], 'SIMULATION-CSRF')
  }
  if (override) return structuredClone(override)
  const data = method === 'GET' ? structuredClone(profile) : { ...identity, reserved_units: 100, settled_units: 1, data: path.endsWith('/review') ? { verdict: 'SYNTHETIC_TRANSPORT_VERIFIED_NOT_SEMANTIC_SUPPORT' } : { ...identity, request_id: body.request_id, session_id: body.session_id, result: 'synthetic only' } }
  return { status: 200, headers: { 'x-schema-version': '1.0.0' }, data }
}
const mock = { name: 'native-image-no-network-test', enforce: 'pre', resolveId(id) { if (id === '@/utils/request') return '\0native-image-request' }, load(id) { if (id === '\0native-image-request') return `export default { get: (...a) => globalThis.nativeImageTestTransport('GET', ...a), post: (...a) => globalThis.nativeImageTestTransport('POST', ...a) }` } }
const { module: api } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/native-image-simulation.ts', import.meta.url)), { root: fileURLToPath(new URL('../web/', import.meta.url)), mode: 'medical', plugins: [mock] })
const { session, project } = simulationConfigurationFixture()
let checks = 0
const ok = (actual, expected) => { assert.deepEqual(actual, expected); checks++ }
const rejects = async (fn, code) => { await assert.rejects(fn, error => error.code === code); checks++ }
const read = () => api.readImageSimulationProfile(session, project, new AbortController().signal)
const loaded = await read(); ok(loaded, profile)
const answer = api.newImageSimulationContext(session, project, 'synthetic question', loaded)
const reviewer = api.newImageSimulationContext(session, project, 'synthetic question', loaded)
ok(answer.tenant_id, session.current_context.tenant_id); ok(answer.request_id !== reviewer.request_id, true); ok(answer.session_id !== reviewer.session_id, true)
loaded.fixture.region.x = 1; ok(answer.images[0].region.x, 0)
for (const operation of ['prepare', 'dispatch', 'recover']) { const result = await api.callImageSimulation(session, operation, answer); ok(result.data.request_id, answer.request_id) }
await api.callImageSimulation(session, 'review', { answer, reviewer }); checks++
const envelope = data => ({ status: 200, headers: { 'x-schema-version': '1.0.0' }, data })
for (const [patch, code] of [[{ mode: 'LIVE' }, 'CONTENT_MISMATCH'], [{ formal_evidence_eligible: true }, 'CONTENT_MISMATCH'], [{ provider: 'external-provider' }, 'CONTENT_MISMATCH'], [{ persistence: 'DATABASE' }, 'CONTENT_MISMATCH'], [{ fixture: { ...fixture, sha256: 'a'.repeat(64) } }, 'CONTENT_MISMATCH'], [{ fixture: { ...fixture, width: 0 } }, 'SCHEMA_INVALID'], [{ fixture: { ...fixture, region: { x: 0, y: 0, width: 0, height: 1 } } }, 'SCHEMA_INVALID'], [{ fixture: { ...fixture, region: { x: 1, y: 0, width: 1, height: 1 } } }, 'SCHEMA_INVALID'], [{ fixture: { ...fixture, source_version: 'invalid' } }, 'SCHEMA_INVALID'], [{ fixture: { ...fixture, base64: '' } }, 'SCHEMA_INVALID'], [{ fixture: { ...fixture, base64: 'a' } }, 'SCHEMA_INVALID'], [{ fixture: { ...fixture, object_ref: 'fixture/../escape' } }, 'SCHEMA_INVALID'], [{ fixture: { ...fixture, width: 16_000_001 } }, 'SCHEMA_INVALID']]) {
 override = envelope({ ...profile, ...patch }); await rejects(read, code)
}
override = { ...envelope(profile), status: 404 }; await rejects(read, 'DEPENDENCY_UNAVAILABLE')
override = { ...envelope(profile), headers: {} }; await rejects(read, 'CONTENT_MISMATCH')
override = envelope({ ...identity, reserved_units: -1, settled_units: 0, data: {} }); await rejects(() => api.callImageSimulation(session, 'dispatch', answer), 'SCHEMA_INVALID')
override = envelope({ ...identity, reserved_units: 100, settled_units: 1, data: { ...identity, request_id: randomUUID(), session_id: answer.session_id } }); await rejects(() => api.callImageSimulation(session, 'recover', answer), 'CONTENT_MISMATCH')
override = envelope({ ...identity, reserved_units: 100, settled_units: 1, data: { verdict: 'SEMANTIC_SUPPORT' } }); await rejects(() => api.callImageSimulation(session, 'review', { answer, reviewer }), 'CONTENT_MISMATCH')
const before = calls.length
await rejects(() => api.callImageSimulation(session, 'prepare', { ...answer, tenant_id: randomUUID() }), 'CONTEXT_CHANGED')
await rejects(() => api.readImageSimulationProfile(session, '../external', new AbortController().signal), 'SCHEMA_INVALID')
ok(calls.length, before)
const requestSource = readFileSync(new URL('../web/src/utils/request.js', import.meta.url), 'utf8')
const { module: json } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/project-json.ts', import.meta.url)), { root: fileURLToPath(new URL('../web/', import.meta.url)), mode: 'medical' })
let intercept
const service = { interceptors: { request: { use(fn) { intercept = fn } }, response: { use() {} } } }
const sandbox = { axios: { defaults: { headers: {} }, create: () => service }, URL, location: { origin: 'http://127.0.0.1:18083' }, ...json, fail(code) { throw Object.assign(new Error(code), { code }) } }
vm.runInNewContext(requestSource.replace(/^import .*$/gm, '').replace(/export default service/, '').replace(/export /g, '').replace(/import\.meta\.env\.[A-Z_]+/g, '"medical"'), sandbox)
for (const path of [`projects/${project}/profile`, 'prepare', 'dispatch', 'recover', 'review']) {
  const headers = new Map([['Authorization', 'forbidden-bearer']]); headers.setContentType = value => headers.set('Content-Type', value)
  const transformed = intercept({ medical: 'envelope', url: `/api/multimodal-qa/simulations/${path}`, headers })
  ok(transformed.responseType, 'text'); ok(transformed.transformResponse[0], json.readBoundedJson)
  ok(transformed.transformRequest[0], json.writeProjectJson); ok(headers.has('Authorization'), false)
  ok(transformed.transformResponse[0](JSON.stringify(profile)), profile)
  ok(JSON.parse(transformed.transformRequest[0](answer)), answer)
}
assert.throws(() => intercept({ medical: 'envelope', url: 'https://external.invalid/api/multimodal-qa/simulations/dispatch', headers: new Map() }), error => error.code === 'ACCESS_DENIED'); checks++
console.log(JSON.stringify({ checks, calls: calls.length, input_data_kind: 'SIMULATION', network_requests: 0, real_provider_verified: false, browser_verified: false }))

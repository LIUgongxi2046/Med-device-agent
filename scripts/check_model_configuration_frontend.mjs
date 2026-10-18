import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

globalThis.window = new EventTarget()
const stored = new Map()
globalThis.sessionStorage = { getItem: key => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value), removeItem: key => stored.delete(key) }
const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: domain } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/model-configuration-domain.ts', import.meta.url)), { root, mode: 'medical' })
const { module: protocol } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/protocol.ts', import.meta.url)), { root, mode: 'medical' })
const tenant = randomUUID(), actor = randomUUID(), project = randomUUID(), config = randomUUID(), fixed = randomUUID()
const session = { user: { user_id: actor }, current_context: { tenant_id: tenant, context_revision: '1', auth_epoch: '1', governance_epoch: '1' },
  action_summary: ['model.configure', 'configuration.read'].map(action => ({ action, resource_kind: 'ENTERPRISE', resource_id: tenant, project_id: null })) }
const body = { schema_version: '1.0.0', project_id: null, config_id: null, expected_revision: '0', based_on_revision: null, reason: 'SIMULATION 保存模型草稿',
  definition: { provider: 'SIMULATION supplier', base_url: 'https://simulation.invalid/v1', region: 'simulation-region', model_id: 'simulation-model', model_version: 'version-1',
    auth_ref: 'vault:simulation/model', credential_reference_id: randomUUID(), credential_revision: 'version-1',
    limits: { context_window: '128000', max_output_tokens: '8192', max_concurrency: '2', requests_per_minute: '10', tokens_per_minute: '200000', deadline_seconds: 60,
      temperature_enabled: true, reasoning_efforts: ['none', 'high'], stop_limit: 4 },
    price_basis: { version_id: randomUUID(), content_sha256: 'a'.repeat(64) }, currency: 'CNY',
    data_policy: { allowed_classifications: ['INTERNAL'], allowed_regions: ['simulation-region'], allow_training: false, retention_days: '0', policy_basis: { version_id: randomUUID(), content_sha256: 'b'.repeat(64) } },
    request_parameter_schema_ref: 'urn:simulation:request:1', request_parameter_schema_sha256: 'c'.repeat(64), model_protocol_revision: '1.0.0' } }
let checks = 0
const ok = (value, expected = true) => { assert.equal(value, expected); checks++ }
const rejects = async (task, code) => { await assert.rejects(task, error => error.code === code); checks++ }
const bad = change => { const value = structuredClone(body); change(value); ok(domain.modelInputIssues(value).length > 0) }
ok(domain.modelInputIssues(body).length, 0)
for (const endpoint of ['http://simulation.invalid', 'https://name:password@simulation.invalid', 'https://simulation.invalid?secret=forbidden', 'https://simulation.invalid#token', 'https://simulation.invalid:0', 'https://simulation.invalid/../v1', 'https://simulation.invalid?']) bad(value => { value.definition.base_url = endpoint })
for (const field of ['context_window', 'max_output_tokens', 'max_concurrency', 'requests_per_minute', 'tokens_per_minute']) {
  bad(value => { value.definition.limits[field] = '18446744073709551616' }); bad(value => { value.definition.limits[field] = '0' })
}
bad(value => { value.definition.limits.max_output_tokens = '128001' })
bad(value => { value.definition.limits.reasoning_efforts = ['high'] })
bad(value => { value.definition.data_policy.allowed_regions = ['another-region'] })
bad(value => { value.definition.data_policy.allowed_regions = ['simulation-region', 'simulation-region'] })
bad(value => { value.definition.data_policy.retention_days = '18446744073709551616' })
bad(value => { value.definition.data_policy.policy_basis = { ...value.definition.price_basis, content_sha256: 'd'.repeat(64) } })
bad(value => { value.definition.provider = ' padded ' })
bad(value => { value.definition.api_key = 'forbidden' })
bad(value => { value.config_id = config })
bad(value => { value.expected_revision = '1' })
bad(value => { value.based_on_revision = { version_id: fixed, content_sha256: 'd'.repeat(64) } })
ok(domain.modelAllowed(session, 'model.configure', null, null))
const resourceOnly = { ...session, action_summary: [{ action: 'model.configure', resource_kind: 'CONFIGURATION', resource_id: config, project_id: null }] }
ok(domain.modelAllowed(resourceOnly, 'model.configure', null, null), false)
ok(domain.modelAllowed(resourceOnly, 'model.configure', null, config)); ok(domain.modelAllowed(resourceOnly, 'model.configure', null, randomUUID()), false)
const projectOnly = { ...session, action_summary: [{ action: 'model.configure', resource_kind: 'PROJECT', resource_id: project, project_id: project }] }
ok(domain.modelAllowed(projectOnly, 'model.configure', null, null), false); ok(domain.modelAllowed(projectOnly, 'model.configure', project, null))
ok(domain.modelAllowed(projectOnly, 'model.configure', randomUUID(), null), false)
ok(domain.modelAllowed(null, 'configuration.read'), false)
const original = await domain.prepareModelSave(session, body)
ok((await domain.prepareModelSave(session, body)).key, original.key)
await rejects(() => domain.prepareModelSave(session, { ...body, reason: 'changed' }), 'IDEMPOTENCY_CONFLICT')
await domain.verifyModelRequest(session, original, body); checks++
const content = [...stored.values()].join('')
for (const secret of [body.reason, body.definition.base_url, body.definition.auth_ref, body.definition.credential_revision, body.definition.provider]) ok(content.includes(secret), false)
ok(Object.keys(JSON.parse(content)).length, 11)
ok(domain.readPendingModel({ ...session, user: { user_id: randomUUID() } }), null)
ok(domain.readPendingModel({ ...session, current_context: { ...session.current_context, tenant_id: randomUUID() } }), null)
await rejects(() => domain.verifyModelRequest({ ...session, current_context: { ...session.current_context, context_revision: '2' } }, original, body), 'CONTENT_MISMATCH')
await rejects(() => domain.verifyModelRequest({ ...session, action_summary: [] }, original, body), 'CONTENT_MISMATCH')
const definition = domain.modelPublicDefinition(body.definition)
ok('auth_ref' in definition, false); ok('credential_revision' in definition, false)
const view = { schema_version: '1.0.0', tenant_id: tenant, project_id: null, config_id: config, model_config_revision: fixed, based_on_revision: null,
  definition, content_sha256: 'd'.repeat(64), status: 'DRAFT', current_model_revision: fixed, revision: '1', created_by: actor,
  created_at: '2026-10-16T00:00:00Z', dependency_validation: 'REGISTRY_REFERENCE_ONLY', allowed_actions: ['configuration.read', 'model.configure'] }
domain.verifyModelView(session, fixed, view, '1'); checks++
for (const change of [{ tenant_id: randomUUID() }, { model_config_revision: randomUUID() }, { allowed_actions: [] }]) {
  assert.throws(() => domain.verifyModelView(session, fixed, { ...view, ...change }, '1'), error => error.code === 'CONTENT_MISMATCH'); checks++
}
for (const change of [{ definition: { ...definition, auth_ref: body.definition.auth_ref } }, { definition: { ...definition, credential_revision: '1' } }, { status: 'ENABLED' }, { dependency_validation: 'VERIFIED' }]) {
  assert.throws(() => domain.verifyModelView(session, fixed, { ...view, ...change }, '1'), error => error.code === 'SCHEMA_INVALID'); checks++
}
const operationId = randomUUID(), operation = { operation_id: operationId, resource_id: fixed, status: 'COMPLETED', status_url: '/api/operations/' + operationId }
const receipt = { schema_version: '1.0.0', operation_id: operationId, tenant_id: tenant, project_id: null, config_id: config, model_config_revision: fixed,
  content_sha256: view.content_sha256, status: 'DRAFT', revision: '1', current_context: { ...session.current_context, context_revision: '2', governance_epoch: '2' } }
domain.verifyModelReceipt(session, original, receipt, '1'); checks++
for (const changed of [{ ...receipt, revision: '2' }, { ...receipt, current_context: { ...receipt.current_context, governance_epoch: '1' } }]) {
  assert.throws(() => domain.verifyModelReceipt(session, original, changed, changed.revision), error => error.code === 'CONTENT_MISMATCH'); checks++
}
await rejects(() => domain.confirmModelResult(session, original, { ...operation, resource_id: randomUUID() }, view, receipt), 'CONTENT_MISMATCH')
await rejects(() => domain.confirmModelResult(session, original, { ...operation, status: 'PROCESSING' }, view, receipt), 'CONTENT_MISMATCH')
await rejects(() => domain.confirmModelResult(session, original, operation, { ...view, definition: { ...definition, provider: 'changed' } }, receipt), 'CONTENT_MISMATCH')
await rejects(() => domain.confirmModelResult(session, original, operation, { ...view, content_sha256: 'e'.repeat(64) }, receipt), 'CONTENT_MISMATCH')
ok(domain.readPendingModel(session).key, original.key)
// A later head must not change which immutable version the original operation resolves to.
await domain.confirmModelResult(session, original, operation, { ...view, current_model_revision: randomUUID(), revision: '4' }, receipt); checks++
ok(domain.readPendingModel(session), null)
const updateBody = { ...body, config_id: config, expected_revision: '4', based_on_revision: { version_id: randomUUID(), content_sha256: 'e'.repeat(64) } }
ok(domain.modelInputIssues(updateBody).length, 0)
const update = await domain.prepareModelSave(session, updateBody)
await domain.confirmModelResult(session, update, operation, { ...view, based_on_revision: updateBody.based_on_revision, revision: '5' }, null); checks++
ok(domain.readPendingModel(session), null)
const abort = new AbortController(); abort.abort()
await assert.rejects(() => domain.prepareModelSave(session, body, abort.signal), error => error.name === 'AbortError'); checks++
ok(stored.size, 0)
assert.throws(() => protocol.validate('urn:medical:model-configuration-write-view:1.0.0', { ...original, auth_ref: 'vault:forbidden' }), error => error.code === 'SCHEMA_INVALID'); checks++
const listing = { schema_version: '1.0.0', tenant_id: tenant, current_context: session.current_context, items: [view], next_after: null }
domain.verifyModelListing(session, { limit: 20 }, listing, '1'); checks++
domain.verifyModelListing(session, { limit: 20 }, { ...listing, items: [], next_after: 'opaque_cursor' }, '1'); checks++
for (const change of [{ tenant_id: randomUUID() }, { current_context: { ...session.current_context, governance_epoch: '2' } }, { items: [view, view] },
  { items: [{ ...view, current_model_revision: randomUUID() }] }, { items: [{ ...view, tenant_id: randomUUID() }] }]) {
  assert.throws(() => domain.verifyModelListing(session, { limit: 20 }, { ...listing, ...change }, '1'), error => error.code === 'CONTENT_MISMATCH'); checks++
}
for (const query of [{ limit: '20' }, { limit: 101 }, { limit: 0 }, { limit: 20, q: '' }, { limit: 20, after: 'invalid cursor' }, { limit: 20, extra: true }]) {
  assert.throws(() => domain.verifyModelListing(session, query, listing, '1'), error => error.code === 'SCHEMA_INVALID'); checks++
}
assert.throws(() => domain.verifyModelListing({ ...session, action_summary: [] }, { limit: 20 }, listing, '1'), error => error.code === 'CONTENT_MISMATCH'); checks++
// No first dispatch after session/page invalidation during asynchronous request hashing.
const mock = { name: 'model-configuration-no-dispatch-race', enforce: 'pre', resolveId(id) { if (id === '@/utils/request') return '\0model-configuration-no-dispatch' }, load(id) {
  if (id === '\0model-configuration-no-dispatch') return `export default { post: (...args) => globalThis.modelConfigurationRaceTransport(...args) }`
} }
const { module: api } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/model-configurations.ts', import.meta.url)), { root, mode: 'medical', plugins: [mock] })
let writes = 0
// This test must never call the mocked transport; no HTTP is available.
globalThis.modelConfigurationRaceTransport = async () => { writes++; throw new Error('Unexpected write after invalidation') }
const racePending = await domain.prepareModelSave(session, body), digest = crypto.subtle.digest, page = new AbortController()
let enterDigest, releaseDigest
const entered = new Promise(resolve => { enterDigest = resolve }), gate = new Promise(resolve => { releaseDigest = resolve })
try {
  crypto.subtle.digest = async function (...args) { enterDigest(); await gate; return digest.apply(this, args) }
  const racingWrite = api.saveModelConfiguration(session, racePending, body, page.signal)
  await entered; page.abort(); releaseDigest()
  await assert.rejects(() => racingWrite, error => error.name === 'AbortError'); checks++
  ok(writes, 0); ok(domain.readPendingModel(session).key, racePending.key)
} finally { releaseDigest(); crypto.subtle.digest = digest }
await assert.rejects(() => api.saveModelConfiguration(session, racePending, body, page.signal), error => error.name === 'AbortError'); checks++
ok(writes, 0); ok(domain.readPendingModel(session).key, racePending.key)
await domain.confirmModelResult(session, racePending, operation, view, null); ok(domain.readPendingModel(session), null)
console.log(JSON.stringify({ result: 'PASS', checks, data_kind: 'SIMULATION', actual_http_verified: false, provider_calls: 0 }))

import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'
import { parse, compileScript, compileTemplate } from '../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js'
import { simulationConfigurationFixture, fileDigest } from './simulation_configuration_fixture.mjs'

globalThis.window = new EventTarget()
const stored = new Map()
globalThis.sessionStorage = { getItem: key => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value), removeItem: key => stored.delete(key) }
const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: domain } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/simulation-model-configuration-domain.ts', import.meta.url)), { root, mode: 'medical' })
const { module: protocol } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/protocol.ts', import.meta.url)), { root, mode: 'medical' })
const f = simulationConfigurationFixture(), { tenant, actor, project, config, fixed, session, body, view, operation, receipt, listing } = f
let checks = 0
const ok = (value, expected = true) => { assert.equal(value, expected); checks++ }
const throws = (task, code = 'SCHEMA_INVALID') => { assert.throws(task, error => error.code === code); checks++ }
const rejects = async (task, code) => { await assert.rejects(task, error => error.code === code); checks++ }
const bad = change => { const value = structuredClone(body); change(value); ok(domain.simulationInputIssues(value).length > 0) }
ok(domain.simulationInputIssues(body).length, 0)
assert.deepEqual(domain.simulationIdentity, f.identity); checks++
for (const key of Object.keys(body.definition)) bad(value => { delete value.definition[key] })
for (const key of Object.keys(f.identity)) bad(value => { value.definition[key] = typeof value.definition[key] === 'boolean' ? true : 'wrong' })
for (const path of ['', '/definition', '/definition/limits', '/definition/case_binding']) bad(value => { let object = value; for (const key of path.split('/').filter(Boolean)) object = object[key]; object.extra = true })
for (const field of ['provider', 'base_url', 'auth_ref', 'credential_reference_id', 'credential_revision', 'price_basis', 'currency', 'data_policy', 'connection_verification_id', 'execution_approved']) bad(value => { value.definition[field] = 'forbidden' })
for (const field of ['model_id', 'model_version']) for (const text of ['external-model', 'SIMULATION:', 'SIMULATION:has space', ' SIMULATION:model']) bad(value => { value.definition[field] = text })
for (const field of ['context_window', 'max_output_tokens', 'max_concurrency', 'requests_per_minute', 'tokens_per_minute']) {
  for (const count of ['18446744073709551616', '0', '01', '1.0', 1]) bad(value => { value.definition.limits[field] = count })
}
bad(value => { value.definition.limits.max_output_tokens = '128001' }); bad(value => { value.definition.limits.reasoning_efforts = ['high'] })
bad(value => { value.definition.limits.reasoning_efforts = ['none', 'none'] }); bad(value => { value.definition.limits.reasoning_efforts = [] })
for (const val of [0, 301, 1.5]) bad(value => { value.definition.limits.deadline_seconds = val })
for (const val of [-1, 17, 0.5]) bad(value => { value.definition.limits.stop_limit = val })
for (const val of [' a ', '\u0000', 'e\u0301', '\ud800']) bad(value => { value.reason = val })
bad(value => { value.definition.case_binding.validation = 'VERIFIED' }); bad(value => { value.definition.case_binding.case_id = '../case' })
bad(value => { value.definition.case_binding.case_sha256 = 'A'.repeat(64) })
bad(value => { value.config_id = config }); bad(value => { value.expected_revision = '1' }); bad(value => { value.expected_revision = '18446744073709551616' })
bad(value => { value.based_on_revision = { version_id: fixed, content_sha256: 'd'.repeat(64) } })
ok(domain.simulationAllowed(session, 'model.configure', null, null))
const resourceOnly = { ...session, action_summary: [{ action: 'model.configure', resource_kind: 'CONFIGURATION', resource_id: config, project_id: null }] }
ok(domain.simulationAllowed(resourceOnly, 'model.configure', null, null), false)
ok(domain.simulationAllowed(resourceOnly, 'model.configure', null, config)); ok(domain.simulationAllowed(resourceOnly, 'model.configure', null, randomUUID()), false)
const projectOnly = { ...session, action_summary: [{ action: 'model.configure', resource_kind: 'PROJECT', resource_id: project, project_id: project }] }
ok(domain.simulationAllowed(projectOnly, 'model.configure', null, null), false); ok(domain.simulationAllowed(projectOnly, 'model.configure', project, null))
ok(domain.simulationAllowed(projectOnly, 'model.configure', randomUUID(), null), false); ok(domain.simulationAllowed(null, 'configuration.read'), false)
const original = await domain.prepareSimulationSave(session, body)
ok((await domain.prepareSimulationSave(session, body)).key, original.key)
await rejects(() => domain.prepareSimulationSave(session, { ...body, reason: 'changed' }), 'IDEMPOTENCY_CONFLICT')
await domain.verifySimulationRequest(session, original, body); checks++
const content = [...stored.values()].join('')
for (const excluded of [body.reason, body.definition.model_id, body.definition.case_binding.case_id, body.definition.case_binding.case_sha256, body.definition.simulator_id]) ok(content.includes(excluded), false)
ok(Object.keys(JSON.parse(content)).length, 11)
ok([...stored.keys()][0].startsWith('medical.simulation.configuration.write:'), true)
ok(domain.readPendingSimulation({ ...session, user: { user_id: randomUUID() } }), null)
ok(domain.readPendingSimulation({ ...session, current_context: { ...session.current_context, tenant_id: randomUUID() } }), null)
await rejects(() => domain.verifySimulationRequest({ ...session, current_context: { ...session.current_context, context_revision: '2' } }, original, body), 'CONTENT_MISMATCH')
await rejects(() => domain.verifySimulationRequest({ ...session, action_summary: [] }, original, body), 'CONTENT_MISMATCH')
domain.verifySimulationView(session, fixed, view, '1'); checks++
for (const change of [{ tenant_id: randomUUID() }, { model_config_revision: randomUUID() }, { allowed_actions: [] }]) throws(() => domain.verifySimulationView(session, fixed, { ...view, ...change }, '1'), 'CONTENT_MISMATCH')
for (const change of [{ definition: { ...view.definition, auth_ref: 'forbidden' } }, { definition: { ...view.definition, execution_mode: 'EXTERNAL' } }, { definition: { ...view.definition, production_eligible: true } }, { status: 'ENABLED' }, { dependency_validation: 'VERIFIED' }]) throws(() => domain.verifySimulationView(session, fixed, { ...view, ...change }, '1'))
domain.verifySimulationReceipt(session, original, receipt, '1'); checks++
for (const key of ['execution_mode', 'production_eligible']) { const partial = { ...receipt }; delete partial[key]; throws(() => domain.verifySimulationReceipt(session, original, partial, '1')) }
for (const changed of [{ ...receipt, revision: '2' }, { ...receipt, current_context: { ...receipt.current_context, governance_epoch: '1' } }]) throws(() => domain.verifySimulationReceipt(session, original, changed, changed.revision), 'CONTENT_MISMATCH')
for (const changed of [{ ...operation, resource_id: randomUUID() }, { ...operation, status: 'PROCESSING' }, { ...operation, status_url: '/api/operations/' + randomUUID() }]) await rejects(() => domain.confirmSimulationResult(session, original, changed, view, receipt), 'CONTENT_MISMATCH')
await rejects(() => domain.confirmSimulationResult(session, original, operation, { ...view, definition: { ...view.definition, model_id: 'SIMULATION:changed' } }, receipt), 'CONTENT_MISMATCH')
await rejects(() => domain.confirmSimulationResult(session, original, operation, { ...view, content_sha256: 'e'.repeat(64) }, receipt), 'CONTENT_MISMATCH')
ok(domain.readPendingSimulation(session).key, original.key)
await domain.confirmSimulationResult(session, original, operation, { ...view, current_model_revision: randomUUID(), revision: '4' }, receipt); checks++
ok(domain.readPendingSimulation(session), null)
const updateBody = { ...body, config_id: config, expected_revision: '4', based_on_revision: { version_id: randomUUID(), content_sha256: 'e'.repeat(64) } }
ok(domain.simulationInputIssues(updateBody).length, 0)
const update = await domain.prepareSimulationSave(session, updateBody)
await domain.confirmSimulationResult(session, update, operation, { ...view, based_on_revision: updateBody.based_on_revision, revision: '5' }, null); checks++
ok(domain.readPendingSimulation(session), null)
const abort = new AbortController(); abort.abort()
await assert.rejects(() => domain.prepareSimulationSave(session, body, abort.signal), error => error.name === 'AbortError'); checks++
ok(stored.size, 0)
throws(() => protocol.validate('urn:medical:simulation-model-configuration-write-view:1.0.0', { ...original, case_binding: body.definition.case_binding }))
domain.verifySimulationListing(session, { limit: 20 }, listing, '1'); checks++
domain.verifySimulationListing(session, { limit: 20 }, { ...listing, items: [], next_after: 'opaque_cursor' }, '1'); checks++
for (const change of [{ tenant_id: randomUUID() }, { current_context: { ...session.current_context, governance_epoch: '2' } }, { items: [view, view] }, { items: [{ ...view, current_model_revision: randomUUID() }] }, { items: [{ ...view, tenant_id: randomUUID() }] }]) throws(() => domain.verifySimulationListing(session, { limit: 20 }, { ...listing, ...change }, '1'), 'CONTENT_MISMATCH')
for (const query of [{ limit: '20' }, { limit: 101 }, { limit: 0 }, { limit: 20, q: '' }, { limit: 20, after: 'invalid cursor' }, { limit: 20, extra: true }]) throws(() => domain.verifySimulationListing(session, query, listing, '1'))
throws(() => domain.verifySimulationListing({ ...session, action_summary: [] }, { limit: 20 }, listing, '1'), 'CONTENT_MISMATCH')
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8')
for (const file of ['simulation-model-configurations.vue', 'simulation-model-configuration-fields.vue']) {
  const source = read('web/src/views/medical/' + file), { descriptor, errors } = parse(source, { filename: file })
  ok(errors.length, 0); ok(!source.includes('v-simulation')); ok(source.includes('v-model'))
  const script = compileScript(descriptor, { id: file }), template = compileTemplate({ id: file, source: descriptor.template.content, filename: file, compilerOptions: { bindingMetadata: script.bindings } })
  ok(template.errors.length, 0)
  for (const absent of ['v-html', 'auth_ref', 'base_url', 'credential_reference_id', 'price_basis']) ok(source.includes(absent), false)
}
const page = read('web/src/views/medical/simulation-model-configurations.vue')
for (const required of ['SimulationModelConfigurationFields', 'simulation-model-configuration-fields', 'DECLARED_ONLY', '未执行模拟', 'lookupOperation', 'document.hidden', 'onBeforeRouteLeave', "lookup_status === 'NOT_FOUND'", 'current_model_revision']) ok(page.includes(required))
ok(read('web/src/router/index.js').includes("path: '/simulation-model-configurations'"))
ok(read('web/src/utils/request.js').includes("target.pathname === '/api/simulation-model-config-revisions'"))
for (const [file, hash] of Object.entries({
  'business/medical-persistence/src/main/resources/model-configuration.schema.json': '0ff65a1d7c939fed8042372f93486da2f5790122736a5aed69ceb075bfffef1b',
  'business/medical-persistence/src/main/resources/configuration-discovery.schema.json': '84bcf78bc35cad4f9b0e5e61e2a010719f8f0ec4f5bc2d6b24bc616d9f4fadfb',
  'business/medical-persistence/src/main/resources/permission-decisions.schema.json': 'fca270edf7d9697f340333c15992ecaa77fff4a8f1ac7bc51d2ffa8d56094843',
  'contracts/catalogue.json': 'dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915'
})) ok(fileDigest(read(file)), hash)
console.log(JSON.stringify({ result: 'PASS', checks, data_kind: 'SIMULATION', actual_http_verified: false, browser_verified: false, provider_calls: 0 }))

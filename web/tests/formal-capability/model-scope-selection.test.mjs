import test from 'node:test'
import assert from 'node:assert/strict'
import { loadModule, id, session, requestBody, plain, response } from './helpers.mjs'
import { broad, model, modelResource } from './model-scope-fixtures.mjs'
const signal = () => new AbortController().signal
const configurations = (d, s, resources, project = id(8)) => d.capabilityScopeCandidates(s, project, resources).filter(item => item.scope.resource_kind === 'CONFIGURATION')
const catalogue = (s, items, next_after = null) => ({ schema_version: '1.0.0', tenant_id: id(2), current_context: plain(s.current_context), items, next_after })

test('broad model.send produces only exact actual model config identities and preserves enterprise owner inside a project ROLE', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session()
 s.action_summary.push(broad('model.send'))
 const resources = [modelResource(d, s, 40, id(8)), modelResource(d, s, 41), modelResource(d, s, 42, id(9))]
 assert.deepEqual(plain(configurations(d, s, [])), [])
 const actual = configurations(d, s, resources).filter(p => p.action === 'model.send')
 assert.deepEqual(plain(actual.map(p => p.scope)), [resources[0].scope, resources[1].scope])
 assert.equal(actual.some(p => p.scope.resource_id === id(140)), false)
 const body = { ...requestBody(d, 'ROLE'), project_id: id(8), tool_scope: { schema_version: '1.0.0', permissions: actual.map(p => ({ ...p, purpose: 'EVALUATION_RESULTS' })) } }
 d.assertCapabilityScopes(s, body, resources)
 assert.throws(() => d.assertCapabilityScopes(s, body), e => e.code === 'ACCESS_DENIED')
 const pending = await d.prepareCapability(() => s, body, signal(), resources)
 assert.deepEqual(plain(pending.body.tool_scope.permissions), plain(body.tool_scope.permissions))
 assert.equal(pending.body.tool_scope.permissions[1].scope.project_id, null)
 assert.equal('model' in pending.body.tool_scope.permissions[0], false)
})

test('project grants match only actual model owner and exact granted action; no wildcard expansion', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session()
 for (const grant of [broad('model.send', id(8)), broad('model.verify', id(8)), broad('model.send', id(9)), { ...broad('model.send'), resource_id: id(99) }, { action: 'model.send', resource_kind: 'CONFIGURATION', resource_id: null, project_id: null }]) {
  s.action_summary = [grant]
  const resources = [modelResource(d, s, 40, id(8)), modelResource(d, s, 41), modelResource(d, s, 42, id(9))]
  const actual = configurations(d, s, resources)
  if (grant.resource_kind === 'PROJECT' && grant.project_id === id(8)) {
   assert.equal(actual.length, 1); assert.equal(actual[0].scope.resource_id, id(40)); assert.equal(actual[0].action, grant.action)
  } else assert.equal(actual.length, 0)
 }
})

test('model evidence rejects foreign tenant, wrong project, version-as-config, missing projection and stale actor/context/permission snapshot', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session()
 s.action_summary = [broad('model.send')]; const good = modelResource(d, s, 40, id(8))
 for (const mutate of [r => r.scope.tenant_id = id(99), r => r.scope.project_id = id(9), r => r.scope.resource_id = r.model.model_config_revision,
  r => delete r.model, r => r.model.model_config_revision = 'invented', r => r.model.content_sha256 = 'bad', r => r.model.status = 'ENABLED',
  r => r.actor = id(99), r => r.context.auth_epoch = '2', r => r.context.governance_epoch = '2', r => r.authorization = 'stale']) {
  const value = plain(good); mutate(value); assert.equal(configurations(d, s, [value]).length, 0)
 }
 s.action_summary.push(broad('model.verify'))
 assert.equal(configurations(d, s, [good]).length, 0)
 assert.equal(configurations(d, s, [modelResource(d, s, 40, id(8))]).length, 2)
})

test('model adapter reads real current model catalogue and uses config_id, owner, readable label and DRAFT lifecycle', async () => {
 const s = session(); s.action_summary.push(broad('configuration.read'), broad('model.send'))
 const rows = [model(40, id(8)), model(41), model(42, id(9))], calls = []
 const { api: a } = await loadModule('formal-capability-candidates', { request: { get: async (url, config) => { calls.push({ url, config }); return response(catalogue(s, rows, 'opaque-next')) } } })
 const { api: d } = await loadModule('formal-capability-domain')
 const page = await a.capabilityModelCandidates(() => s, null, signal())
 assert.equal(calls[0].url, '/api/model-config-revisions'); assert.deepEqual(plain(calls[0].config.params), { limit: 50 })
 assert.equal(calls[0].config.headers['X-Context-Revision'], '1'); assert.equal(page.next_after, 'opaque-next')
 assert.deepEqual(Array.from(page.resources, r => r.scope.resource_id), rows.map(m => m.config_id))
 assert.deepEqual(Array.from(page.resources, r => r.scope.project_id), [id(8), null, id(9)])
 assert.match(page.resources[0].label, /Readable model 40.*DRAFT/)
 assert.equal(page.resources[0].model.model_config_revision, id(140))
 assert.equal(page.resources[0].actor, s.user.user_id); assert.deepEqual(plain(page.resources[0].context), s.current_context)
 assert.equal(page.resources[0].authorization, d.capabilityAuthorization(s))
 assert.equal(configurations(d, s, page.resources).filter(p => p.action === 'model.send').length, 2)
})

test('model adapter rejects wrong tenant/context, unreadable project, noncurrent fixed projection and missing logical config identity', async () => {
 const s = session(); s.action_summary = [broad('configuration.read', id(8))]
 let listing = catalogue(s, [model(40, id(8))])
 const { api: a } = await loadModule('formal-capability-candidates', { request: { get: async () => response(listing) } })
 for (const mutate of [p => p.tenant_id = id(99), p => p.items[0].tenant_id = id(99), p => p.current_context.auth_epoch = '2', p => p.items[0].project_id = id(9),
  p => p.items[0].project_id = null, p => p.items[0].current_model_revision = id(99), p => delete p.items[0].config_id]) {
  listing = catalogue(s, [model(40, id(8))]); mutate(listing)
  await assert.rejects(a.capabilityModelCandidates(() => s, null, signal()))
 }
})

test('late model response cannot bind after actor/context replacement, abort or in-place action-summary change', async () => {
 const { api: a } = await loadModule('formal-capability-candidates', { request: { get: () => new Promise(resolve => done = resolve) } }); let done
 for (const change of [(state, controller) => state.current = { ...state.current, user: { ...state.current.user, user_id: id(99) } },
  state => state.current.current_context.governance_epoch = '2', state => state.current.action_summary.push(broad('model.verify')), (_state, controller) => controller.abort()]) {
  const s = session(); s.action_summary.push(broad('configuration.read'), broad('model.send'))
  const state = { current: s }, controller = new AbortController(), result = catalogue(s, [model()])
  const pending = a.capabilityModelCandidates(() => state.current, null, controller.signal)
  change(state, controller); done(response(result))
  await assert.rejects(pending)
 }
})

test('model pagination refuses nonadvancing cursor and conflicting owner/fixed identity across all readable pages', async () => {
 const s = session(); s.action_summary.push(broad('configuration.read'), broad('model.send'))
 let listing = catalogue(s, [model(40, id(8))], 'opaque-first')
 const { api: a } = await loadModule('formal-capability-candidates', { request: { get: async () => response(listing) } }), { api: d } = await loadModule('formal-capability-domain')
 const first = await a.capabilityModelCandidates(() => s, null, signal())
 await assert.rejects(a.capabilityModelCandidates(() => s, 'opaque-first', signal()), e => e.code === 'CONTENT_MISMATCH')
 for (const mutate of [m => m.project_id = id(9), m => m.project_id = null, m => { m.model_config_revision = id(99); m.current_model_revision = id(99) }, m => m.content_sha256 = 'e'.repeat(64), m => m.revision = '2']) {
  const row = model(40, id(8)); mutate(row); listing = catalogue(s, [row])
  const next = await a.capabilityModelCandidates(() => s, 'opaque-first', signal())
  assert.throws(() => d.mergeCapabilityModelResources(first.resources, next.resources), e => e.code === 'CONTENT_MISMATCH')
 }
 assert.equal(d.mergeCapabilityModelResources(first.resources, first.resources).length, 1)
})

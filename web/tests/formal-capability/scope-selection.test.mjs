import test from 'node:test'
import assert from 'node:assert/strict'
import { loadModule, id, session, requestBody, plain, response } from './helpers.mjs'

const signal = () => new AbortController().signal
const resource = (s, kind, n, project = null) => ({
 actor: s.user.user_id, context: plain(s.current_context), label: '已读取资源',
 scope: { tenant_id: s.current_context.tenant_id, project_id: project, resource_kind: kind, resource_id: id(n) }
})
const enterprise = (s, action) => ({ action, resource_kind: 'ENTERPRISE', resource_id: s.current_context.tenant_id, project_id: null })
const source = (n, project = null, ownership = project ? 'PROJECT' : 'ENTERPRISE') => ({
 source_id: id(n), source_version: id(n + 100), owner_project_id: project, ownership, title: '真实资料 ' + n,
 classification: 'INTERNAL', media_type: 'text/plain', size_bytes: '15', rights_revision: id(500), rights_status: 'VALID',
 folder_id: null, category: null, labels: [], organization_revision: null, bookmarked: false,
 processing_status: null, approval_status: null, lifecycle: null, created_at: '2026-10-18T00:00:00Z'
})
function candidates(d, s, resources, project = id(8)) { return d.capabilityScopeCandidates(s, project, resources) }
function has(items, action, kind, n, project) {
 return items.some(x => x.action === action && x.scope.resource_kind === kind && x.scope.resource_id === id(n) && x.scope.project_id === project)
}

test('enterprise grants narrow only to verified selected project and owned source identities with the same action', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session()
 s.action_summary = ['capability.configure', 'project.read', 'artifact.register', 'source.read', 'source.external.process'].map(action => enterprise(s, action))
 const resources = [resource(s, 'PROJECT', 8, id(8)), resource(s, 'PROJECT', 9, id(9)), resource(s, 'SOURCE', 11, id(8)), resource(s, 'SOURCE', 12)]
 assert.equal(has(candidates(d, s, []), 'project.read', 'PROJECT', 8, id(8)), false)
 const actual = candidates(d, s, resources)
 for (const action of ['project.read', 'artifact.register']) assert.ok(has(actual, action, 'PROJECT', 8, id(8)))
 for (const action of ['source.read', 'source.external.process']) {
  assert.ok(has(actual, action, 'SOURCE', 11, id(8)))
  assert.ok(has(actual, action, 'SOURCE', 12, null))
 }
 assert.equal(actual.some(x => x.scope.resource_id === id(9)), false)
 assert.equal(actual.some(x => x.action === 'project.write'), false)
 assert.equal(actual.filter(x => x.action === 'project.read' && x.scope.resource_kind === 'PROJECT').length, 1)
 const body = { ...requestBody(d, 'ROLE'), project_id: id(8) }
 body.tool_scope.permissions = [
  { ...actual.find(x => x.action === 'project.read' && x.scope.resource_kind === 'PROJECT'), purpose: 'AGENT_PUBLICATION' },
  { ...actual.find(x => x.action === 'artifact.register' && x.scope.resource_kind === 'PROJECT'), purpose: 'EVALUATION_RESULTS' },
  { ...actual.find(x => x.action === 'source.external.process' && x.scope.resource_id === id(11)), purpose: 'MODEL_EXECUTION' }
 ]
 assert.throws(() => d.assertCapabilityScopes(s, body), e => e.code === 'ACCESS_DENIED')
 d.assertCapabilityScopes(s, body, resources)
 const p = await d.prepareCapability(() => s, body, signal(), resources)
 assert.equal(p.body.execution_mode, 'PRODUCTION')
 assert.deepEqual(plain(p.body.tool_scope), plain(body.tool_scope))
 assert.equal('resources' in p.body, false)
})

test('project grants narrow to that actual owner project only, never enterprise or another project sources', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session()
 s.action_summary = [{ action: 'source.read', resource_kind: 'PROJECT', resource_id: id(8), project_id: id(8) }]
 const actual = candidates(d, s, [resource(s, 'SOURCE', 11, id(8)), resource(s, 'SOURCE', 12), resource(s, 'SOURCE', 13, id(9))])
 assert.ok(has(actual, 'source.read', 'SOURCE', 11, id(8)))
 assert.equal(actual.some(x => [id(12), id(13)].includes(x.scope.resource_id)), false)
 assert.equal(actual.some(x => x.action === 'source.external.process'), false)
})

test('foreign enterprise, wildcard summaries, malformed resource identities and stale actor/context cannot create narrow options', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session(), selected = resource(s, 'SOURCE', 11, id(8))
 for (const grant of [
  { ...enterprise(s, 'source.read'), resource_id: id(99) },
  { ...enterprise(s, 'source.read'), project_id: id(8) },
  { action: 'source.read', resource_kind: 'SOURCE', resource_id: null, project_id: null },
  { action: 'source.read', resource_kind: 'PROJECT', resource_id: id(8), project_id: id(9) }
 ]) {
  s.action_summary = [grant]
  assert.equal(candidates(d, s, [selected]).some(x => x.scope.resource_id === id(11)), false)
 }
 s.action_summary = [enterprise(s, 'source.read')]
 for (const invalid of [
  { ...selected, actor: id(99) },
  { ...selected, context: { ...selected.context, auth_epoch: '2' } },
  { ...selected, context: { ...selected.context, tenant_id: id(99) } },
  { ...selected, scope: { ...selected.scope, tenant_id: id(99) } },
  { ...selected, scope: { ...selected.scope, project_id: id(9) } },
  { ...selected, scope: { ...selected.scope, resource_id: null } },
  { ...selected, scope: { ...selected.scope, resource_kind: 'ARTIFACT' } }
 ]) assert.equal(candidates(d, s, [invalid]).some(x => x.scope.resource_kind === 'SOURCE'), false)
 const fresh = { ...s, current_context: { ...s.current_context, context_revision: '2' } }
 assert.equal(candidates(d, fresh, [selected]).some(x => x.scope.resource_kind === 'SOURCE'), false)
})

test('source candidate adapter keeps real source identity and ownership, filters foreign/session rows and deduplicates versions', async () => {
 const calls = [], s = session(), rows = [source(11, id(8)), { ...source(11, id(8)), source_version: id(211) }, source(12), source(13, id(9)), source(14, null, 'SESSION')]
 const { api: a } = await loadModule('formal-capability-candidates', { request: { get: async (url, config) => {
  calls.push({ url, config }); return response({ schema_version: '1.0.0', items: rows, total: '5', limit: 50, next_after: rows.at(-1).source_version }, null)
 } } })
 const page = await a.capabilitySourceCandidates(() => s, id(8), null, signal())
 assert.deepEqual(Array.from(page.resources, x => x.scope.resource_id), [id(11), id(12)])
 assert.deepEqual(Array.from(page.resources, x => x.scope.project_id), [id(8), null])
 assert.equal(page.resources[0].actor, s.user.user_id)
 assert.deepEqual(plain(page.resources[0].context), s.current_context)
 assert.equal(page.resources.some(x => x.scope.resource_id === id(111)), false)
 assert.equal(page.next_after, id(114))
 assert.equal(calls[0].url, '/api/source-versions')
 assert.deepEqual(plain(calls[0].config.params), { limit: 50, project_id: id(8) })
 assert.equal(calls[0].config.headers['X-Context-Revision'], '1')
})

test('enterprise source reads use enterprise filter and invalid owner metadata is rejected', async () => {
 let rows = [source(11)], observed
 const s = session(), { api: a } = await loadModule('formal-capability-candidates', { request: { get: async (_url, config) => {
  observed = config.params; return response({ schema_version: '1.0.0', items: rows, total: '1', limit: 50, next_after: null }, null)
 } } })
 await a.capabilitySourceCandidates(() => s, null, null, signal())
 assert.deepEqual(plain(observed), { limit: 50, ownership: 'ENTERPRISE' })
 for (const row of [source(11, id(8), 'ENTERPRISE'), source(11, null, 'PROJECT')]) {
  rows = [row]
  await assert.rejects(a.capabilitySourceCandidates(() => s, id(8), null, signal()), e => e.code === 'CONTENT_MISMATCH')
 }
})

test('late source catalogue read cannot bind identities after actor or tenant/context changes', async () => {
 let resolve, s = session()
 const { api: a } = await loadModule('formal-capability-candidates', { request: { get: () => new Promise(r => resolve = r) } })
 const pending = a.capabilitySourceCandidates(() => s, id(8), null, signal())
 s = { ...s, user: { ...s.user, user_id: id(99) } }
 resolve(response({ schema_version: '1.0.0', items: [source(11, id(8))], total: '1', limit: 50, next_after: null }, null))
 await assert.rejects(pending, e => e.code === 'CONTEXT_CHANGED')
})

test('project adapter binds validated current-tenant project metadata and rejects foreign or late context responses', async () => {
 const s = session(), at = '2026-10-18T00:00:00Z', basis = { version_id: id(90), content_sha256: 'a'.repeat(64) }
 const project = { schema_version: '1.0.0', tenant_id: id(2), project_id: id(8), name: '实际项目', responsible_user_id: id(1), status: 'ACTIVE', revision: '1',
  profile: { profile_revision: id(91), attributes: [], authority_refs: [], content_sha256: 'a'.repeat(64), created_by: id(1), created_at: at }, creation_basis: basis,
  creation_reason: '登记真实项目', created_by: id(1), created_at: at, updated_at: at, children: { tasks: null, active_runs: null, waiting_items: null }, allowed_actions: ['project.read'] }
 let projects = [project], url
 const { api: a } = await loadModule('formal-capability-candidates', { request: { get: async (path) => { url = path; return response({ schema_version: '1.0.0', projects, total: '1', next_after: null }, null) } } })
 const page = await a.capabilityProjectCandidates(() => s, null, signal())
 assert.equal(url, '/api/projects'); assert.equal(page.projects[0].name, '实际项目')
 assert.deepEqual(plain(page.resources[0]), { ...resource(s, 'PROJECT', 8, id(8)), label: '实际项目' })
 projects = [{ ...project, tenant_id: id(99) }]
 await assert.rejects(a.capabilityProjectCandidates(() => s, null, signal()), e => e.code === 'CONTENT_MISMATCH')
 let resolve, current = s
 const late = await loadModule('formal-capability-candidates', { request: { get: () => new Promise(r => resolve = r) } })
 const pending = late.api.capabilityProjectCandidates(() => current, null, signal())
 current = { ...s, current_context: { ...s.current_context, governance_epoch: '2' } }
 resolve(response({ schema_version: '1.0.0', projects: [project], total: '1', next_after: null }, null))
 await assert.rejects(pending, e => e.code === 'CONTEXT_CHANGED')
})

test('source adapter refuses conflicting ownership and a nonadvancing catalogue cursor', async () => {
 const s = session(); let rows = [source(11, id(8)), { ...source(11, id(9)), source_version: id(211) }], cursor = null
 const { api: a } = await loadModule('formal-capability-candidates', { request: { get: async () => response({ schema_version: '1.0.0', items: rows, total: String(rows.length), limit: 50, next_after: cursor }, null) } })
 await assert.rejects(a.capabilitySourceCandidates(() => s, id(8), null, signal()), e => e.code === 'CONTENT_MISMATCH')
 rows = [source(11)]; cursor = rows[0].source_version
 await assert.rejects(a.capabilitySourceCandidates(() => s, null, cursor, signal()), e => e.code === 'CONTENT_MISMATCH')
})

test('catalogue read is not an authorization grant and removing the matching grant revokes derived choices', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session(), sources = [resource(s, 'SOURCE', 11)]
 s.action_summary = [enterprise(s, 'source.read'), { action: 'source.read', resource_kind: 'SOURCE', resource_id: id(11), project_id: null }]
 assert.equal(candidates(d, s, sources).filter(x => x.scope.resource_id === id(11)).length, 1)
 s.action_summary = [enterprise(s, 'project.read')]
 assert.equal(candidates(d, s, sources).some(x => x.scope.resource_id === id(11) && x.action === 'source.read'), false)
 s.action_summary = []
 assert.deepEqual(plain(candidates(d, s, sources)), [])
})

// Isolated schema/API checks with synthetic fixtures and an in-memory transport.
// These are not live-backend, browser, visual or end-to-end checks.
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { webcrypto } from 'node:crypto'
import { createRequire } from 'node:module'
import { build } from '../../web/node_modules/esbuild/lib/main.js'
import { parse, compileScript, compileTemplate } from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js'

const root = path.resolve(import.meta.dirname, '../..')
const output = path.join(root, '.implementation-work/rule-ui-unit')
const dependency = createRequire(path.join(root, 'web/package.json'))
await mkdir(output, { recursive: true })
await build({ entryPoints: [path.join(root, 'web/src/api/medical/rule-versions.ts')], bundle: true, platform: 'node', format: 'cjs',
  outfile: path.join(output, 'rules.cjs'), plugins: [
    { name: 'isolated-transport', setup(bundler) {
      bundler.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'transport', namespace: 'synthetic' }))
      bundler.onLoad({ filter: /.*/, namespace: 'synthetic' }, () => ({ contents: `export default {
        get: (url, config) => globalThis.__ruleTransport('GET', url, null, config),
        post: (url, body, config) => globalThis.__ruleTransport('POST', url, body, config)
      }`, loader: 'js' }))
      bundler.onResolve({ filter: /^[a-z]/ }, args => ({ path: dependency.resolve(args.path), external: true }))
      bundler.onResolve({ filter: /\?raw$/ }, args => ({ path: path.resolve(args.resolveDir, args.path.replace(/\?raw$/, '')), namespace: 'raw' }))
      bundler.onLoad({ filter: /.*/, namespace: 'raw' }, async args => ({ contents: await readFile(args.path, 'utf8'), loader: 'text' }))
    } }
  ] })
Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
globalThis.window = new EventTarget()
const storage = new Map()
globalThis.sessionStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
const api = (await import(pathToFileURL(path.join(output, 'rules.cjs')))).default
const id = () => crypto.randomUUID()
const tenant = id(); const actor = id(); const ruleId = id(); const basis = { version_id: id(), content_sha256: 'a'.repeat(64) }
const context = { tenant_id: tenant, context_revision: '7', auth_epoch: '2', governance_epoch: '5' }
const session = { schema_version: '1.0.0', user: { user_id: actor, username: null, display_name: 'SIMULATION ONLY', platform_role: null },
  memberships: [], current_context: context, csrf: { header_name: 'X-CSRF-TOKEN', token: 'SIMULATION_NOT_A_CREDENTIAL' },
  action_summary: ['rule.write', 'rule.read', 'rule.approve'].map(action => ({ action, project_id: null, resource_kind: 'ENTERPRISE', resource_id: tenant })),
  invitation_entry: { method: 'POST', path_template: '/api/member-invitations/{id}/acceptances' } }
const clone = value => structuredClone(value)
const body = { schema_version: '1.0.0', base_version: null, content_schema_ref: 'urn:medical:rule-expressions:1.0.0#/$defs/Content', reason: 'SIMULATION rule authoring reason',
  content: { schema_version: '1.0.0', engine_revision: api.ruleEngine,
    expression: { schema_version: '1.0.0', engine_revision: api.ruleEngine, expression: "#attributes['SIMULATION.risk'] == #constants['SIMULATION.expected']",
      input_keys: ['SIMULATION.risk'], constants: [{ key: 'SIMULATION.expected', value: 'SIMULATION_VALUE', basis_refs: [basis] }] }, basis_manifest: [basis] } }
const checks = []
function pass(name) { checks.push({ name, result: 'PASS' }) }
function rejected(fn, code) { assert.throws(fn, error => error.code === code) }
async function rejectedAsync(fn, code) { await assert.rejects(fn, error => error.code === code) }
let calls = []; let handle = () => { throw new Error('Unplanned transport call') }
globalThis.__ruleTransport = async (method, url, payload, config) => { calls.push({ method, url, payload: clone(payload), config }); return handle(method, url, payload, config) }
function response(data, revision, status = 200) { const requestId = id(); return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': requestId },
  data: { schema_version: '1.0.0', request_id: requestId, resource_revision: revision, data } } }

assert.equal(api.ruleAllowed(null, 'rule.write'), false)
assert.equal(api.ruleAllowed({ ...session, current_context: null }, 'rule.write'), false)
assert.equal(api.ruleAllowed(session, 'rule.write'), true)
assert.equal(api.ruleAllowed({ ...session, action_summary: [{ action: 'rule.write', project_id: null, resource_kind: 'RULE', resource_id: ruleId }] }, 'rule.write', ruleId), false)
assert.equal(api.ruleAllowed({ ...session, action_summary: [{ action: 'rule.approve', project_id: null, resource_kind: 'RULE', resource_id: ruleId }] }, 'rule.approve', id()), false)
assert.equal(api.ruleAllowed({ ...session, action_summary: [{ action: 'rule.read', project_id: id(), resource_kind: 'RULE', resource_id: ruleId }] }, 'rule.read', ruleId), false)
pass('current enterprise and exact resource permission gates')
assert.deepEqual(api.validateRuleCreate(body), body)
const extra = clone(body); extra.content.expression.simulated_success = true; rejected(() => api.validateRuleCreate(extra), 'SCHEMA_INVALID')
const missing = clone(body); missing.content.basis_manifest = []; rejected(() => api.validateRuleCreate(missing), 'SCHEMA_INVALID')
const invented = clone(body); invented.content.expression.constants[0].basis_refs[0] = { ...basis, version_id: id() }; rejected(() => api.validateRuleCreate(invented), 'SCHEMA_INVALID')
const duplicate = clone(body); duplicate.content.expression.constants.push(clone(duplicate.content.expression.constants[0])); rejected(() => api.validateRuleCreate(duplicate), 'SCHEMA_INVALID')
pass('strict content schema and evidenced unique constants')
for (const value of ['3', '3.0', '3e0', '3.000e+0', '0.125', '1.25e-1', '-0.50', '0e300', '9007199254740991']) {
  assert.equal(api.ruleConstantJsonIssue(value), '', value)
  assert.equal(api.readRuleConstantJson(value), Number(value), value)
}
assert.deepEqual(api.readRuleConstantJson('{"thresholds":[3.0,3e0,{"fraction":0.125}],"value":"0.1234567890123456789"}'),
  { thresholds: [3, 3, { fraction: 0.125 }], value: '0.1234567890123456789' })
for (const value of ['0.1234567890123456789', '9007199254740993', '9007199254740993.0', '9007199254740992.0',
  '9.007199254740993e15', '1e-400', '1e400', '{"nested":[1,{"threshold":0.1234567890123456789}]}',
  '[3,{"nested":{"value":9007199254740993}}]']) {
  assert.notEqual(api.ruleConstantJsonIssue(value), '', value)
  rejected(() => api.readRuleConstantJson(value), 'SCHEMA_INVALID')
}
const exactBase = clone(body); exactBase.content.expression.constants[0].value = api.readRuleConstantJson('3.0')
const exactExponent = clone(body); exactExponent.content.expression.constants[0].value = api.readRuleConstantJson('3e0')
assert.equal(await api.ruleDigest(exactBase), await api.ruleDigest(exactExponent))
pass('original nested numeric tokens reject precision loss, overflow and underflow before JSON.parse; equivalent decimal/exponent forms stay numeric')

assert.deepEqual(api.referencedTemplateRules({ nodes: [{ condition: { rule_revision: basis } }], basis_manifest: [{ version_id: id(), content_sha256: 'b'.repeat(64) }], format_profile: { numbering_rule_revision: basis } }), [basis])
const conflictingRule = { first: { rule_revision: basis }, second: { rule_revision: { ...basis, content_sha256: 'e'.repeat(64) } } }
rejected(() => api.referencedTemplateRules(conflictingRule), 'CONTENT_MISMATCH')
pass('template rule links come from explicit rule references and reject conflicting hashes')
const pending = await api.prepareRuleWrite(session, body)
assert.equal(pending.operation, 'TP-03'); assert.equal(pending.target_id, null)
assert.equal((await api.prepareRuleWrite(session, clone(body))).key, pending.key)
assert.deepEqual(api.readPendingRule(session, null), pending)
const saved = [...storage.values()].join('')
assert.equal(saved.includes(body.content.expression.expression), false); assert.equal(saved.includes(body.reason), false); assert.equal(saved.includes(session.csrf.token), false)
await rejectedAsync(() => api.prepareRuleWrite(session, { ...body, reason: 'SIMULATION different reason' }), 'IDEMPOTENCY_CONFLICT')
assert.equal([...storage.values()].join(''), saved)
await rejectedAsync(() => api.prepareRuleWrite({ ...session, current_context: { ...context, context_revision: '8' } }, body), 'IDEMPOTENCY_CONFLICT')
pass('pending identity preserves original key, context and hashes without content or credentials')
const stored = { tenant_id: tenant, rule_version: ruleId, status: 'DRAFT', revision: '1', content: clone(body.content), content_sha256: await api.ruleDigest(body.content),
  base_version: null, reason: body.reason, authority_grant_id: id(), authority_revision: '2', created_by: actor, created_at: '2026-10-16T05:00:00Z', dependency_refs: [basis] }
const created = { schema_version: '1.0.0', operation_id: id(), rule: stored, allowed_actions: [] }
handle = (method, url, payload, config) => {
  assert.equal(method, 'POST'); assert.equal(url, '/api/rule-versions'); assert.deepEqual(payload, body)
  assert.equal(config.headers['Idempotency-Key'], pending.key); assert.equal(config.headers['X-Context-Revision'], context.context_revision)
  assert.equal(config.headers['X-CSRF-TOKEN'], session.csrf.token); return response(created, '1', 201)
}
assert.deepEqual(await api.submitRuleCreate(session, pending, body), created)
const invalidCreated = clone(created); invalidCreated.rule.content_sha256 = 'b'.repeat(64)
handle = () => response(invalidCreated, '1', 201); await rejectedAsync(() => api.submitRuleCreate(session, pending, body), 'CONTENT_MISMATCH')
assert.deepEqual(api.readPendingRule(session, null), pending)
pass('draft POST uses original metadata and rejects inconsistent receipts without clearing pending identity')
const operation = { operation_id: created.operation_id, resource_id: ruleId, status: 'COMPLETED', status_url: '/api/operations/' + created.operation_id }
handle = () => response({ ...operation, status: 'PROCESSING' }, null)
await rejectedAsync(() => api.confirmRuleOperation(session, pending, operation.operation_id, new AbortController().signal), 'CONTENT_MISMATCH')
assert.deepEqual(api.readPendingRule(session, null), pending)
handle = () => response(operation, null)
assert.deepEqual(await api.confirmRuleOperation(session, pending, operation.operation_id, new AbortController().signal), operation)
api.clearRuleOperation(session, pending, operation, stored); assert.equal(api.readPendingRule(session, null), null)
pass('unknown or processing operations stay pending; only verified completed matching operation clears')
const baseline = { schema_version: '1.0.0', rule_version: ruleId, content_sha256: stored.content_sha256, definition_manifest_sha256: 'c'.repeat(64), dependency_refs: [basis], dependency_authorizations_sha256: 'd'.repeat(64) }
const preview = { schema_version: '1.0.0', rule: stored, baseline_manifest: baseline, allowed_actions: ['rule.approve'] }
handle = (method, url) => { assert.equal(method, 'GET'); assert.equal(url, '/api/rule-versions/' + ruleId + '/approval-baseline'); return response(preview, '1') }
assert.deepEqual(await api.readRulePreview(session, ruleId, new AbortController().signal), preview)
const badPreview = clone(preview); badPreview.baseline_manifest.content_sha256 = 'e'.repeat(64)
handle = () => response(badPreview, '1'); await rejectedAsync(() => api.readRulePreview(session, ruleId, new AbortController().signal), 'CONTENT_MISMATCH')
pass('baseline read verifies content digest, fixed identity and dependencies')
assert.deepEqual(api.ruleDecisions(preview), ['APPROVE', 'RETURN', 'DISABLE'])
assert.deepEqual(api.ruleDecisions({ ...preview, allowed_actions: [] }), [])
assert.deepEqual(api.ruleDecisions({ ...preview, rule: { ...stored, status: 'DISABLED' } }), [])
assert.deepEqual(api.ruleDecisions({ ...preview, rule: { ...stored, status: 'APPROVED' } }), ['RETURN', 'DISABLE'])
pass('approval transition choices respect actual state and server allowed actions')
const approval = { schema_version: '1.0.0', target_revision: ruleId, expected_revision: '1', decision: 'APPROVE', reason: 'SIMULATION explicit review reason', baseline_manifest: baseline }
const approvalPending = await api.prepareRuleWrite(session, approval)
const receipt = { schema_version: '1.0.0', operation_id: id(), decision_id: id(), rule_version: ruleId, decision: 'APPROVE', status: 'APPROVED', revision: '2', content_sha256: stored.content_sha256,
  actor_id: actor, decided_at: '2026-10-16T05:01:00Z', current_context: { ...context, context_revision: '8', governance_epoch: '6' }, allowed_actions: [] }
handle = (method, url, payload, config) => {
  assert.equal(method, 'POST'); assert.equal(url, '/api/rule-versions/' + ruleId + '/approval-decisions'); assert.deepEqual(payload, approval)
  assert.equal(config.headers['Idempotency-Key'], approvalPending.key); assert.equal(config.headers['X-Context-Revision'], '7'); return response(receipt, '2')
}
assert.deepEqual(await api.submitRuleApproval(session, approvalPending, approval), receipt)
const staleReceipt = clone(receipt); staleReceipt.current_context.governance_epoch = '5'
handle = () => response(staleReceipt, '2'); await rejectedAsync(() => api.submitRuleApproval(session, approvalPending, approval), 'CONTENT_MISMATCH')
assert.deepEqual(api.readPendingRule(session, ruleId), approvalPending)
await rejectedAsync(() => api.prepareRuleWrite(session, { ...approval, decision: 'DISABLE' }), 'IDEMPOTENCY_CONFLICT')
pass('TP04 carries CAS and exact baseline and verifies context/governance advancement')
const noRight = { ...session, action_summary: [] }; const before = calls.length
await rejectedAsync(() => api.readRule(noRight, ruleId, new AbortController().signal), 'ACCESS_DENIED')
await rejectedAsync(() => api.submitRuleApproval(noRight, approvalPending, approval), 'CONTENT_MISMATCH')
assert.equal(calls.length, before)
pass('denied session cannot read or mutate through API helpers')


const page = { schema_version: '1.0.0', tenant_id: tenant, current_context: context, items: [{ schema_version: '1.0.0', rule: stored, allowed_actions: [] }], next_after: 'SIMULATION_CURSOR' }
handle = (method, url, payload, config) => {
  assert.equal(method, 'GET'); assert.equal(url, '/api/rule-versions'); assert.deepEqual(config.params, { limit: 25, q: api.ruleEngine })
  assert.equal(config.headers['X-Context-Revision'], '7'); return response(page, '7')
}
assert.deepEqual(await api.readRuleList(session, { limit: 25, q: api.ruleEngine }, new AbortController().signal), page)
const badPage = clone(page); badPage.current_context = { ...context, governance_epoch: '6' }
handle = () => response(badPage, '7'); await rejectedAsync(() => api.readRuleList(session, { limit: 25 }, new AbortController().signal), 'CONTENT_MISMATCH')
handle = () => response({ ...page, items: [], next_after: 'SIMULATION_MORE' }, '7')
assert.deepEqual((await api.readRuleList(session, { limit: 25 }, new AbortController().signal)).items, [])
handle = () => response({ ...page, items: [page.items[0], page.items[0]] }, '7')
await rejectedAsync(() => api.readRuleList(session, { limit: 25 }, new AbortController().signal), 'CONTENT_MISMATCH')
handle = () => response(page, '7')
await rejectedAsync(() => api.readRuleList(session, { limit: 25, after: 'SIMULATION_CURSOR' }, new AbortController().signal), 'CONTENT_MISMATCH')
const callCount = calls.length
await rejectedAsync(() => api.readRuleList(session, { limit: 101 }, new AbortController().signal), 'SCHEMA_INVALID')
assert.equal(calls.length, callCount)
pass('authorized paginated discovery validates context, bounded query, cursor and duplicates, preserving empty continuation pages')

for (const file of ['rule-create.vue', 'rule-basis-editor.vue', 'rule-management.vue', 'rule-list.vue']) {
  const filename = path.join(root, 'web/src/views/medical', file); const source = await readFile(filename, 'utf8')
  const parsed = parse(source, { filename }); assert.deepEqual(parsed.errors, [])
  const script = compileScript(parsed.descriptor, { id: 'isolated-rule-' + file })
  const template = compileTemplate({ source: parsed.descriptor.template.content, filename, id: 'isolated-rule-' + file,
    compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
}
const view = await readFile(path.join(root, 'web/src/views/medical/rule-management.vue'), 'utf8')
assert.ok(view.indexOf('const latest = await readRulePreview') < view.indexOf('const original = await prepareRuleWrite'))
assert.ok(view.includes('canonicalRule(latest.baseline_manifest) !== canonicalRule(value.baseline_manifest)'))
assert.ok(view.includes('attested.value = false')); assert.ok(view.includes('onBeforeRouteUpdate(guard)'))
const createView = await readFile(path.join(root, 'web/src/views/medical/rule-create.vue'), 'utf8')
assert.ok(createView.includes('readRuleConstantJson(item.value)')); assert.ok(createView.includes('ruleConstantJsonIssue(constant.value)'));
assert.ok(createView.includes('UNKNOWN')); assert.ok(createView.includes('clearRuleOperation(session, original, operation, result.rule)'))
const router = await readFile(path.join(root, 'web/src/router/index.js'), 'utf8')
assert.ok(router.includes("path: '/rules'")); assert.ok(router.includes("name: 'MedicalRuleCreate'")); assert.ok(router.includes("path: ':ruleId'"))
pass('Vue SFC compilation, reachable routes, pre-submit fresh baseline and navigation guard contracts')
await writeFile(path.join(output, 'result.json'), JSON.stringify({ scope: 'ISOLATED_SYNTHETIC_SCHEMA_API_AND_VUE_COMPILE', browser: 'NOT_RUN', live_backend: 'NOT_RUN', checks }, null, 2) + '\n')
console.log(JSON.stringify({ result: 'PASS', scope: 'ISOLATED_SYNTHETIC_SCHEMA_API_AND_VUE_COMPILE', checks: checks.length }))

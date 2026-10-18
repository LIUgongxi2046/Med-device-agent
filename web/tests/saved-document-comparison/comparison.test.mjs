import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname } from 'node:path'
import { resolve } from 'node:path'
import { createRequire } from 'node:module'
import { webcrypto } from 'node:crypto'
const root = process.env.MEDICAL_REPO || process.cwd()
const here = dirname(fileURLToPath(import.meta.url))
const staged = existsSync(resolve(here, '../api'))
const base = staged ? resolve(here, '..') : resolve(root, 'web/src')
const require = createRequire(resolve(root, 'web/package.json'))
const ts = require('typescript'), vue = require('vue')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const Ajv = require('ajv/dist/2020.js').default, formats = require('ajv-formats').default
const ajv = new Ajv({ strict: false, allErrors: true }); formats(ajv)
for (const name of ['document-comparison-views.schema.json', 'document-comparison-feature-availability.schema.json']) ajv.addSchema(JSON.parse(readFileSync(resolve(root, 'business/medical-persistence/src/main/resources', name))))
const canonical = require('json-canonicalize').canonicalize
const sourceDigest = async value => byteDigest(new TextEncoder().encode(canonical(value)))
const byteDigest = async bytes => Buffer.from(await webcrypto.subtle.digest('SHA-256', bytes)).toString('hex')
const source = file => readFileSync(resolve(base, staged ? file : file.replace('api/', 'api/medical/').replace('components/', 'views/medical/')), 'utf8')
const transpile = text => ts.transpileModule(text, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const strip = text => transpile(text).replace(/^import .*;\n/gm, '').replace(/export /g, '')
const id = n => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`
const session = n => ({ user: { user_id: id(n) }, current_context: { tenant_id: id(1), context_revision: '1', auth_epoch: '1', governance_epoch: '1' }, csrf: { header_name: 'X-CSRF-TOKEN', token: 'test' }, action_summary: [] })
const body = () => ({ document_id: id(2), left_revision: id(3), right_revision: id(4) })
const cap = s => ({ schema_version: '1.0.0', current_context: s.current_context, features: { document_comparison: true }, maximum_request_bytes: 4096, maximum_result_bytes: 1000000, maximum_records: 100 })
const response = data => ({ status: 200, data: { resource_revision: '1', data } })
function adapter(raw) {
  const calls = [], failures = [], storage = new Map()
  const fail = (code, message) => { failures.push(code); throw Object.assign(new Error(message), { code }) }
  const validate = (ref, value) => { if (!ajv.getSchema(ref)?.(value)) fail('SCHEMA_INVALID', JSON.stringify(ajv.getSchema(ref)?.errors)); return value }
  const env = { request: Object.fromEntries(['get', 'post'].map(method => [method, async (...args) => { calls.push({ method, args }); return typeof raw === 'function' ? raw() : raw }])), fail, validate, validContext() {}, envelope: raw => raw.data, canonical, sourceDigest, byteDigest, crypto: webcrypto, sessionStorage: { setItem: (k,v) => storage.set(k,v), getItem: k => storage.get(k), removeItem: k => storage.delete(k) } }
  return { ...new Function(...Object.keys(env), strip(source('api/saved-document-comparison.ts')) + '\nreturn {prepareComparison,verifyComparisonPending,createComparison,recoverComparison,comparisonAvailability,verifyComparisonRead,retainComparison,restoreComparison,forgetComparison,readComparison}')( ...Object.values(env)), calls, failures, storage }
}
test('original request/key/hash is retained and mutation is rejected before write', async () => {
  const a = adapter(), s = session(5), p = await a.prepareComparison(s, body())
  a.retainComparison(s, p); assert.deepEqual(a.restoreComparison(s, id(2)), p)
  p.body.left_revision = id(6)
  await assert.rejects(a.createComparison(s, p, new AbortController().signal)); assert.equal(a.calls.length, 0)
})
test('read-only recovery uses original hash; NOT_FOUND retains pending and wrong original hash fails', async () => {
  let data = { schema_version: '1.0.0', state: 'NOT_FOUND', request_body_sha256: null, comparison_id: null, comparison: null }
  const a = adapter(() => response(data)), s = session(5), p = await a.prepareComparison(s, body()); a.retainComparison(s, p)
  s.current_context.context_revision = '2'
  assert.equal((await a.recoverComparison(s, p, new AbortController().signal)).state, 'NOT_FOUND')
  assert.ok(a.restoreComparison(s, id(2)))
  data = { ...data, state: 'PROCESSING', request_body_sha256: 'a'.repeat(64), comparison_id: id(9) }
  await assert.rejects(a.recoverComparison(s, p, new AbortController().signal))
  assert.equal(a.calls.every(c => c.method === 'get'), true)
  assert.equal(a.calls[0].args[0], '/api/document-comparison-operations')
  assert.equal(a.calls[0].args[1].headers['Idempotency-Key'], p.key)
})
test('identity mismatch, unknown recovery properties and stale write context fail closed', async () => {
  const a = adapter(response({ schema_version: '1.0.0', state: 'NOT_FOUND', request_body_sha256: null, comparison_id: null, comparison: null, extra: 1 })), s = session(5), p = await a.prepareComparison(s, body())
  await assert.rejects(a.recoverComparison(s, p, new AbortController().signal))
  s.current_context.auth_epoch = '2'; await assert.rejects(a.createComparison(s, p, new AbortController().signal))
  await assert.rejects(a.recoverComparison(session(6), p, new AbortController().signal))
  assert.equal(a.calls.length, 1)
})
test('availability validates real capacities and aborted stale result does not signal identity failure', async () => {
  const s = session(5), a = adapter(response(cap(s)))
  assert.equal((await a.comparisonAvailability(s, new AbortController().signal)).maximum_records, 100)
  const signal = new AbortController(); const b = adapter(() => { signal.abort(); return response(cap(s)) })
  await assert.rejects(b.comparisonAvailability(s, signal.signal)); assert.deepEqual(b.failures, [])
})
const display = new Function(strip(source('api/document-comparison-display.ts')) + '\nreturn {differencePage,displayFields,categoryLabels,statusLabels}')()
test('local display filtering keeps complete total and preserves exact original text', () => {
  const records = Array.from({ length: 25 }, (_, i) => ({ category: i % 2 ? 'NODE' : 'ORDER', status: i % 3 ? 'CHANGED' : 'UNCHANGED' }))
  const page = display.differencePage(records, 'NODE', 'ALL', 2, 10)
  assert.equal(page.total, 25); assert.equal(page.filtered, 12); assert.equal(page.items.length, 2)
  assert.deepEqual(display.displayFields('{"original_value":" 10 mg ","unit":"mg"}')[0], { label: '原始值', value: ' 10 mg ' })
  assert.throws(() => display.differencePage(records, 'ALL', 'ALL', 1, 0))
})
const componentSource = source('components/saved-document-comparison.vue')
const compiled = transpile(compileScript(parse(componentSource).descriptor, { id: 'saved-comparison' }).content).replace(/^import .*;\n/gm, '').replace('export default', 'return')
const tick = async () => { for (let i = 0; i < 6; i++) { await Promise.resolve(); await vue.nextTick() } }
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness() {
  const props = vue.reactive({ session: session(5), snapshot: { document_id: id(2), document_revision: id(4), revision: '1' }, active: true, disabled: false })
  const user = vue.reactive({ medicalWriting: false }), calls = { availability: [], prepare: [], create: [], recover: [], read: [] }, retained = [], forgotten = []
  const api = type => (...args) => { const d = deferred(); calls[type].push({ ...d, args }); return d.promise }
  let stop; const document = { hidden: false, addEventListener() {}, removeEventListener() {} }
  const env = { ...vue, _defineComponent: vue.defineComponent, useUserStore: () => user, canonical, document, DocumentRevisionDirectory: {}, ...display,
    comparisonAvailability: api('availability'), prepareComparison: api('prepare'), verifyComparisonPending: async () => {}, createComparison: api('create'), recoverComparison: api('recover'), readComparison: api('read'), retainComparison: (...v) => retained.push(v), restoreComparison: () => null, forgetComparison: (...v) => forgotten.push(v), onBeforeUnmount: fn => { stop = fn } }
  const component = new Function(...Object.keys(env), compiled)(...Object.values(env))
  const scope = vue.effectScope(); const vm = scope.run(() => component.setup(props, { expose() {} }))
  return { props, user, calls, retained, forgotten, vm, stop: () => { stop(); scope.stop() } }
}
async function ready(h) { h.calls.availability[0].resolve(cap(h.props.session)); await tick() }
async function started(h) {
  await ready(h); h.vm.select(id(3)); h.vm.select(id(4)); const work = h.vm.create(); await tick()
  h.calls.prepare[0].resolve({ actor: id(5), context: h.props.session.current_context, key: id(8), body: body(), request_body_sha256: 'a'.repeat(64), digest: 'b'.repeat(64) }); await tick(); return { work }
}
test('actual setup selects explicit sides and persists original before dispatching a non-cancelled write', async () => {
  const h = harness(); const { work } = await started(h)
  assert.equal(h.retained.length, 1); assert.equal(h.calls.create.length, 1); assert.equal(h.user.medicalWriting, true)
  h.vm.select(id(99)); assert.equal(h.vm.left.value, id(3)); h.calls.create[0].reject(new Error('unknown')); await work
  assert.ok(h.vm.pending.value); assert.equal(h.user.medicalWriting, false); assert.equal(h.forgotten.length, 0); h.stop()
})
test('disabled deployment sends no write and local selection never changes parent snapshot', async () => {
  const h = harness(), before = JSON.stringify(h.props.snapshot)
  h.calls.availability[0].resolve({ ...cap(h.props.session), features: { document_comparison: false }, maximum_request_bytes: null, maximum_result_bytes: null, maximum_records: null }); await tick()
  h.vm.select(id(3)); h.vm.select(id(4)); await h.vm.create(); assert.equal(h.calls.prepare.length, 0); assert.equal(JSON.stringify(h.props.snapshot), before); h.stop()
})
test('scope reset clears private data; old write finally cannot clear newer read busy or another writer lock', async () => {
  const h = harness(); const { work } = await started(h)
  h.props.session.current_context.context_revision = '2'; await tick(); assert.equal(h.vm.pending.value, null); assert.equal(h.vm.loading.value, true)
  h.user.medicalWriting = false; h.user.medicalWriting = true
  h.calls.create[0].reject(new Error('late')); await work
  assert.equal(h.user.medicalWriting, true); assert.equal(h.vm.loading.value, true); assert.equal(h.retained.length, 1); h.stop()
})
test('unknown recovery retains key; completed-only explicit finish may clear', async () => {
  const h = harness(); const { work } = await started(h); h.calls.create[0].reject(new Error('unknown')); await work
  const p = h.vm.pending.value, recover = h.vm.recover(); await tick(); h.calls.recover[0].resolve({ state: 'NOT_FOUND', comparison: null }); await recover
  h.vm.finish(); assert.equal(h.vm.pending.value, p); assert.equal(h.forgotten.length, 0); h.stop()
})
test('unmount keeps original request and ignores late result without loading body into parent', async () => {
  const h = harness(); const { work } = await started(h); h.stop(); h.calls.create[0].resolve({ comparison_id: id(9) }); await work
  assert.equal(h.calls.read.length, 0); assert.equal(h.retained.length, 1); assert.equal(h.forgotten.length, 0); assert.equal(h.user.medicalWriting, false)
})
test('actual template compiles and displays structured local pages without merge/adopt actions', () => {
  const output = compileTemplate({ source: parse(componentSource).descriptor.template.content, filename: 'saved-document-comparison.vue', id: 'comparison' })
  assert.deepEqual(output.errors, []); assert.match(componentSource, /displayFields\(record.before_json\)/); assert.match(componentSource, /selection-only/); assert.doesNotMatch(componentSource, /v-html|readSavedDocumentRevision|replaceBaseline/)
})
async function fixedResult() {
  const records = [{ category: 'ORDER', identity: 'nodes', status: 'UNCHANGED', before_json: '[]', after_json: '[]', before_sha256: await byteDigest(new TextEncoder().encode('[]')), after_sha256: await byteDigest(new TextEncoder().encode('[]')), text_change: null }]
  const input = { schema_version: '1.0.0', kind: 'FIXED_DOCUMENT_STRUCTURE_CORE', algorithm_id: 'urn:medical:fixed-structure:stable-ids-prefix-suffix:1.0.0', implementation_sha256: 'a'.repeat(64), schema_sha256: 'b'.repeat(64), body_schema_sha256: 'c'.repeat(64), request: body(), left_body_sha256: 'd'.repeat(64), right_body_sha256: 'e'.repeat(64), limits: { maximum_nodes: 100, maximum_cells: 100, maximum_relationships: 100, maximum_input_bytes: 100000, maximum_result_bytes: 1000000, maximum_records: 100, maximum_text_code_points: 10000, maximum_work: 100000, maximum_milliseconds: 1000 }, semantic_assessment: 'NOT_PERFORMED' }
  const hash = await sourceDigest(input), coverage = { selection_sha256: hash, delivered_sha256: await sourceDigest(records), complete: true, next_cursor: null }
  const result = { ...input, input_sha256: hash, records, record_count: 1, affected_node_ids: [], left_affected_section_ids: [], right_affected_section_ids: [], coverage }
  const comparison = { comparison_id: id(10), comparison_revision: '1', left_id: id(3), right_revision: id(4), differences: { artifact_id: id(11), sha256: await sourceDigest(result), schema_ref: 'urn:medical:document-structural-comparison-core:1.0.0#/$defs/CoreResult', coverage }, affected_node_ids: [] }
  return { schema_version: '1.0.0', current_document_revision: id(4), document_head_revision: '9', left_is_current: false, right_is_current: true, comparison, result }
}
test('typed complete result validates actual published schema and binds original request/artifact/coverage', async () => {
  const a = adapter(), s = session(5), value = await fixedResult()
  assert.equal(await a.verifyComparisonRead(body(), value.comparison, value, cap(s)), value)
  for (const mutate of [v => v.result.records.pop(), v => v.result.record_count++, v => v.left_is_current = true, v => v.result.request.left_revision = id(99), v => v.result.affected_node_ids.push(id(99)), v => v.result.extra = 1]) {
    const broken = structuredClone(value); mutate(broken)
    await assert.rejects(a.verifyComparisonRead(body(), value.comparison, broken, cap(s)))
  }
})
test('rehashing altered snippet or input metadata cannot bypass original record/snippet/input binding', async () => {
  const a = adapter(), s = session(5)
  for (const mutate of [v => v.result.records[0].before_json = '[1]', v => v.result.left_body_sha256 = 'f'.repeat(64)]) {
    const value = await fixedResult(); mutate(value)
    value.result.coverage.delivered_sha256 = await sourceDigest(value.result.records)
    value.comparison.differences.coverage = value.result.coverage
    value.comparison.differences.sha256 = await sourceDigest(value.result)
    await assert.rejects(a.verifyComparisonRead(body(), value.comparison, value, cap(s)))
  }
})
test('POST keeps exact frozen 3-field request, non-aborted dispatch, strict receipt', async () => {
  const value = await fixedResult(), a = adapter(response(value.comparison)), s = session(5), p = await a.prepareComparison(s, body())
  assert.deepEqual(await a.createComparison(s, p, new AbortController().signal), value.comparison)
  assert.deepEqual(a.calls[0].args[1], body()); assert.equal(a.calls[0].args[2].signal, undefined)
  assert.equal(a.calls[0].args[2].headers['Idempotency-Key'], p.key)
})
test('comparison-only transport rejects raw UTF-8 overflow before parsing', () => {
  let parses = 0
  const fail = (code, message) => { throw Object.assign(new Error(message), { code }) }
  const reader = new Function('fail', 'readBoundedJson', 'MedicalFailure', strip(source('api/document-comparison-json.ts')) + '\nreturn readComparisonJson')(fail, value => { parses++; return JSON.parse(value) }, class MedicalFailure extends Error { constructor(code, message) { super(message); this.code = code } })
  assert.deepEqual(reader('{"valid":true}'), { valid: true }); assert.equal(parses, 1)
  assert.throws(() => reader('汉'.repeat(3000000)), e => e.code === 'CONTEXT_LIMIT'); assert.equal(parses, 1)
  const transport = staged ? source('patches/request-bounded.patch') : readFileSync(resolve(root, 'web/src/utils/request.js'), 'utf8'); assert.match(transport, /readComparisonJson/); assert.doesNotMatch(transport, /startsWith\('\/api\/document-comparisons/)
})

test('complete success displays typed result; explicit finish never changes parent draft snapshot', async () => {
  const h = harness(), before = JSON.stringify(h.props.snapshot); const { work } = await started(h)
  const result = await fixedResult(); h.calls.create[0].resolve(result.comparison); await tick()
  assert.equal(h.calls.read.length, 1); h.calls.read[0].resolve(result); await work
  assert.equal(h.vm.result.value, result); assert.equal(h.vm.displayed.value.total, 1)
  assert.equal(h.vm.recoveryState.value, 'COMPLETED'); assert.equal(h.user.medicalWriting, false)
  h.vm.finish(); assert.equal(h.forgotten.length, 1); assert.equal(h.vm.pending.value, null)
  assert.equal(JSON.stringify(h.props.snapshot), before); h.stop()
})
test('result read denial retains original request for read-only recovery, no new POST', async () => {
  const h = harness(); const { work } = await started(h)
  h.calls.create[0].resolve({ comparison_id: id(10) }); await tick(); h.calls.read[0].reject(new Error('ACCESS_DENIED')); await work
  assert.ok(h.vm.pending.value); assert.equal(h.vm.result.value, null); assert.equal(h.calls.create.length, 1); assert.equal(h.forgotten.length, 0); h.stop()
})
test('late prior availability cannot restore private state or clear next request busy', async () => {
  const h = harness(); h.props.session = session(6); await tick()
  h.calls.availability[0].resolve(cap(session(5))); await tick()
  assert.equal(h.vm.availability.value, null); assert.equal(h.vm.loading.value, true)
  h.calls.availability[1].resolve(cap(h.props.session)); await tick(); assert.equal(h.vm.loading.value, false); h.stop()
})

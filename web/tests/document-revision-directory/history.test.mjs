import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = process.env.MEDICAL_REPO || process.cwd()
const require = createRequire(resolve(root, 'web/package.json'))
const vue = require('vue'), ts = require('typescript')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const Ajv = require('ajv/dist/2020.js').default
const addFormats = require('ajv-formats').default
const here = dirname(fileURLToPath(import.meta.url))
const staged = existsSync(resolve(here, '../api'))
const source = name => readFileSync(staged ? resolve(here, '../' + name) : resolve(root, 'web/src/' + name.replace('components/', 'views/medical/').replace('api/', 'api/medical/')), 'utf8')
const schemaRoot = staged ? '/tmp/stage18-document-comparison/history' : resolve(root, 'business/medical-persistence/src/main/resources')
const schema = name => JSON.parse(readFileSync(resolve(schemaRoot, name), 'utf8'))
const directorySchema = schema('document-revision-directory.schema.json')
const availabilitySchema = schema(staged ? 'availability/document-history-feature-availability.schema.json' : 'document-history-feature-availability.schema.json')
const ajv = new Ajv({ strict: false, allErrors: true }); addFormats(ajv); ajv.addSchema(directorySchema); ajv.addSchema(availabilitySchema)
const shared = require('yaml').parse(readFileSync(resolve(root, 'backend-design/interfaces.schema.yaml'), 'utf8'))
ajv.addSchema({ ...shared, $id: 'urn:medical:shared' })
const transpile = text => ts.transpileModule(text, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const apiText = transpile(source('api/document-revision-directory.ts')).replace(/^import .*;\n/gm, '').replace(/export /g, '')
const id = n => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`
const session = n => ({ user: { user_id: id(n) }, current_context: { tenant_id: id(1), context_revision: '1', auth_epoch: '1', governance_epoch: '1' }, action_summary: [] })
const snapshot = n => ({ document_id: id(2), project_id: id(3), document_revision: id(n), revision: '1' })
const entry = (n, time = '2026-10-18T10:00:00Z') => ({ document_revision: id(n), body_sha256: 'a'.repeat(64), created_by: id(4), created_at: time, is_current: n === 10, read_url: `/api/documents/${id(2)}/revisions/${id(n)}`, check_ids: [], review_ids: [], signature_ids: [] })
const page = (items = [entry(10)], cursor = null) => ({ schema_version: '1.0.0', document_id: id(2), current_document_revision: id(10), document_head_revision: '2', items, next_cursor: cursor, complete: cursor === null })
const response = data => ({ status: 200, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': id(90), 'x-resource-revision': data.document_head_revision }, data: { schema_version: '1.0.0', request_id: id(90), resource_revision: data.document_head_revision ?? '1', data } })
function adapter(raw) {
  const calls = [], failures = []
  const fail = (code, message) => { failures.push(code); throw Object.assign(new Error(message), { code }) }
  const validate = (ref, value) => {
    if (!ajv.getSchema(ref)?.(value)) fail('SCHEMA_INVALID', 'schema')
    return value
  }
  const envelope = raw => {
    if (raw.headers['cache-control'] !== 'no-store' || raw.headers['x-schema-version'] !== '1.0.0' || raw.headers['x-request-id'] !== raw.data.request_id) fail('SCHEMA_INVALID', 'header')
    if (raw.status !== 200) fail(raw.data.code, raw.data.message)
    return validate('urn:medical:shared#/$defs/Envelope', raw.data)
  }
  const env = { request: { get: async (...args) => { calls.push(args); return typeof raw === 'function' ? raw() : raw } }, envelope, fail, validate, validContext() {}, canonical: JSON.stringify }
  const api = new Function(...Object.keys(env), apiText + '\nreturn {documentHistoryAvailability,readDocumentRevisionDirectory}')( ...Object.values(env))
  return { ...api, calls, failures }
}
test('strict API validates real page and only requests metadata endpoint', async () => {
  const a = adapter(response(page())); const result = await a.readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal)
  assert.equal(result.kind, 'PAGE'); assert.equal(a.calls.length, 1)
  assert.deepEqual(a.calls[0][1].params, { limit: 20 }); assert.equal(a.calls[0][0], `/api/documents/${id(2)}/revisions`)
})
test('schema extras, wrong current marker and unsafe read URL fail closed', async () => {
  for (const mutate of [p => { p.total = 1 }, p => { p.items[0].is_current = false }, p => { p.items[0].read_url = 'https://elsewhere.invalid/' }]) {
    const value = page(); mutate(value); const a = adapter(response(value))
    await assert.rejects(a.readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal)); assert.equal(a.failures.length, 1)
  }
})
test('microsecond ordering does not collapse to milliseconds', async () => {
  const value = page([entry(8, '2026-10-18T10:00:00.000002Z'), entry(9, '2026-10-18T10:00:00.000001Z')])
  const a = adapter(response(value)); assert.equal((await a.readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal)).kind, 'PAGE')
  value.items.reverse(); await assert.rejects(a.readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal))
})
test('verified expected stale cursor is local while malformed expiry and ACCESS_DENIED fail closed', async () => {
  const error = code => ({ status: 409, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': id(90) }, data: { schema_version: '1.0.0', request_id: id(90), code, message: 'stale', operation_id: null, resource_revision: null, allowed_actions: [], details: [], retryable: false } })
  for (const code of ['CURSOR_EXPIRED', 'CURSOR_INVALID']) {
    const a = adapter(error(code)); assert.equal((await a.readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal, id(20))).kind, 'CURSOR_STALE'); assert.deepEqual(a.failures, [])
    await assert.rejects(a.readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal))
  }
  const denied = adapter(error('ACCESS_DENIED')); await assert.rejects(denied.readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal, id(20))); assert.deepEqual(denied.failures, ['ACCESS_DENIED'])
})
test('forged cursor errors and mismatched envelope head never become a local empty page', async () => {
  const badHead = response(page()); badHead.headers['x-resource-revision'] = '999'
  await assert.rejects(adapter(badHead).readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal))
  const malformed = { status: 409, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': id(90) }, data: {
    schema_version: '1.0.0', request_id: id(90), code: 'CURSOR_EXPIRED', message: 'expired', operation_id: null,
    resource_revision: null, allowed_actions: [], details: [], retryable: false, extra: true
  } }
  const a = adapter(malformed)
  await assert.rejects(a.readDocumentRevisionDirectory(session(7), snapshot(8), 20, new AbortController().signal, id(20)))
  assert.deepEqual(a.failures, ['SCHEMA_INVALID'])
})
test('availability uses actual closed server state and current identity', async () => {
  const s = session(7); const data = { schema_version: '1.0.0', current_context: s.current_context, features: { document_history: false }, maximum_page_size: null }
  const a = adapter(response(data)); assert.equal((await a.documentHistoryAvailability(s, new AbortController().signal)).features.document_history, false)
  data.maximum_page_size = 20; await assert.rejects(a.documentHistoryAvailability(s, new AbortController().signal))
})
test('aborted responses cannot produce fail-closed identity events for obsolete scope', async () => {
  const c = new AbortController(); const a = adapter(() => { c.abort(); return response(page()) })
  await assert.rejects(a.readDocumentRevisionDirectory(session(7), snapshot(8), 20, c.signal)); assert.deepEqual(a.failures, [])
})

const componentSource = source('components/document-revision-directory.vue')
const compiled = transpile(compileScript(parse(componentSource).descriptor, { id: 'history-test' }).content).replace(/^import .*;\n/gm, '').replace('export default', 'return')
const tick = async () => { for (let i = 0; i < 5; i++) { await Promise.resolve(); await vue.nextTick() } }
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness() {
  const props = vue.reactive({ session: session(7), snapshot: snapshot(8), active: true, disabled: false })
  const user = vue.reactive({ medicalWriting: false }); const calls = { availability: [], pages: [] }; const emitted = []
  let stop; const listeners = new Map(); const document = { hidden: false, addEventListener: (k, fn) => listeners.set(k, fn), removeEventListener: k => listeners.delete(k) }
  const api = type => (...args) => { const d = deferred(); calls[type].push({ ...d, args }); return d.promise }
  const env = { ...vue, _defineComponent: vue.defineComponent, onBeforeUnmount: fn => { stop = fn }, document, useUserStore: () => user,
    canonicalDocumentValue: JSON.stringify, documentHistoryAvailability: api('availability'), readDocumentRevisionDirectory: api('pages') }
  const component = new Function(...Object.keys(env), compiled)(...Object.values(env)); const effect = vue.effectScope()
  const state = effect.run(() => component.setup(props, { expose() {}, emit: (...args) => emitted.push(args) }))
  return { props, user, calls, emitted, state, document, listeners, stop() { stop(); effect.stop() } }
}
async function ready(h, index = 0) { h.calls.availability[index].resolve({ features: { document_history: true }, maximum_page_size: 5 }); await tick() }
test('actual setup reads gate then metadata and emits only explicitly selected known revision', async () => {
  const h = harness(); await ready(h); assert.equal(h.calls.pages[0].args[2], 5)
  h.calls.pages[0].resolve({ kind: 'PAGE', page: page() }); await tick()
  assert.deepEqual(h.emitted, []); h.state.select(id(999)); assert.deepEqual(h.emitted, [])
  h.state.select(id(10)); assert.deepEqual(h.emitted, [['select', id(10)]])
  assert.equal(h.props.snapshot.document_revision, id(8)); h.stop()
})
test('disabled server gate never loads list', async () => {
  const h = harness(); h.calls.availability[0].resolve({ features: { document_history: false }, maximum_page_size: null }); await tick()
  assert.equal(h.calls.pages.length, 0); assert.equal(h.state.enabled.value, false); h.stop()
})
test('identity change aborts old requests; old finally cannot clear new busy or restore old page', async () => {
  const h = harness(); await ready(h)
  h.props.session = session(9); assert.equal(h.calls.pages[0].args[3].aborted, true); assert.equal(h.state.page.value, null)
  h.calls.pages[0].resolve({ kind: 'PAGE', page: page() }); await tick(); assert.equal(h.state.loading.value, true); assert.equal(h.state.page.value, null)
  await ready(h, 1); h.calls.pages[1].resolve({ kind: 'PAGE', page: page([]) }); await tick(); assert.equal(h.state.page.value.items.length, 0); h.stop()
})
test('head/scope mutations clear current page and revoked permission summary resets immediately', async () => {
  const h = harness(); await ready(h); h.calls.pages[0].resolve({ kind: 'PAGE', page: page() }); await tick()
  h.props.snapshot.revision = '2'; assert.equal(h.state.page.value, null); assert.equal(h.calls.availability.length, 2)
  h.props.session.action_summary.push({ action: 'something-changed' }); assert.equal(h.calls.availability.length, 3); h.stop()
})
test('empty candidate page preserves next cursor; stale cursor only offers explicit first-page reload', async () => {
  const h = harness(); await ready(h); h.calls.pages[0].resolve({ kind: 'PAGE', page: page([], id(20)) }); await tick()
  const next = h.state.load(id(20)); assert.equal(h.calls.pages[1].args[4], id(20))
  h.calls.pages[1].resolve({ kind: 'CURSOR_STALE' }); await next; assert.equal(h.state.page.value, null); assert.match(h.state.error.value, /首页/); assert.equal(h.props.session.user.user_id, id(7)); assert.equal(h.props.session.current_context.context_revision, '1')
  assert.equal(h.calls.pages.length, 2); const reload = h.state.load(); assert.equal(h.calls.pages[2].args[4], undefined)
  h.calls.pages[2].resolve({ kind: 'PAGE', page: page() }); await reload; h.stop()
})
test('writing/inactive/hidden state blocks selection and unmount discards late response', async () => {
  const h = harness(); await ready(h); h.calls.pages[0].resolve({ kind: 'PAGE', page: page() }); await tick()
  h.user.medicalWriting = true; h.state.select(id(10)); assert.deepEqual(h.emitted, [])
  h.user.medicalWriting = false; h.document.hidden = true; h.listeners.get('visibilitychange')(); assert.equal(h.state.page.value, null)
  h.document.hidden = false; h.listeners.get('visibilitychange')(); h.stop()
  h.calls.availability.at(-1).resolve({ features: { document_history: true }, maximum_page_size: 5 }); await tick(); assert.equal(h.state.page.value, null)
})
test('component template compiles without a link that fetches arbitrary read_url or mutates drafts', () => {
  const result = compileTemplate({ source: parse(componentSource).descriptor.template.content, filename: 'directory.vue', id: 'history-test' })
  assert.deepEqual(result.errors, []); assert.doesNotMatch(componentSource, /:href=|readSavedDocumentRevision|replace\(|\.edit\(/)
})

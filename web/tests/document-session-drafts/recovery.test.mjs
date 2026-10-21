import { loadModule as loadSourceReturnModule } from '../formal-capability/helpers.mjs'
const sourceReturnApi = (await loadSourceReturnModule('source-return-navigation')).api
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
const require = createRequire(new URL('../../package.json', import.meta.url))
const Vue = require('vue'), ts = require('typescript'), { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const source = fs.readFileSync(new URL('../../src/views/medical/document-editor.vue', import.meta.url), 'utf8')
const cacheSource = fs.readFileSync(new URL('../../src/api/medical/document-session-drafts.ts', import.meta.url), 'utf8')
const transpile = content => ts.transpileModule(content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
function harness() {
  const listeners = new Map(), window = { addEventListener(name, fn) { if (!listeners.has(name)) listeners.set(name, new Set()); listeners.get(name).add(fn) }, removeEventListener(name, fn) { listeners.get(name)?.delete(fn) } }
  const props = { session: { user: { user_id: 'actor' }, current_context: { tenant_id: 'tenant', context_revision: '1', auth_epoch: '1', governance_epoch: '1' } }, projectId: 'project', documentId: 'document', active: false, reviewMode: false }
  const snapshot = { document_id: 'document', project_id: 'project', document_revision: 'revision', allowed_actions: ['document.edit'], body: { sha256: 'a'.repeat(64) } }
  const baseline = { nodes: [], fields: [], evidence_links: [], template_version: 'template' }
  const draft = { body: { ...baseline, nodes: [{ node_id: 'local-edit' }] }, dirty: true, invalidated: false, invalidate() { this.invalidated = true } }
  const value = { snapshot, baseline, draft, dependency: 'dependency', pending: null, reason: 'retain this reason', fieldInputs: { field: { field_id: 'field', original_value: 'unconfirmed local input' } } }
  const cache = {}, protocol = { fail(code) { throw Error(code) } }
  vm.runInNewContext(transpile(cacheSource), { exports: cache, require: () => protocol, window, Map, Set })
  const key = cache.documentDraftKey(props.session, props.projectId, props.documentId)
  cache.retainDocumentDraft(key, value)
  let read = async () => ({ snapshot, body: baseline })
  const imports = path => {
    if (path === 'vue') return Vue
    if (path.endsWith('/source-return-navigation')) return sourceReturnApi
    if (path === 'vue-router') return { useRoute: () => ({ path: '', query: {} }), useRouter: () => ({ push() {} }) }
    if (path === '@/store/modules/user') return { default: () => ({ medicalWriting: false }) }
    if (path.endsWith('/protocol')) return protocol
    if (path.endsWith('/task-workspace')) return { workspaceHistoryRevision: () => ({ revision: null, issue: '' }) }
    if (path.endsWith('/document-body')) return { canonicalDocumentValue: JSON.stringify }
    if (path.endsWith('/document-session-drafts')) return cache
    if (path.endsWith('/documents')) return { readSavedDocument: (...args) => read(...args) }
    return {}
  }
  const result = {}
  vm.runInNewContext(transpile(compileScript(parse(source).descriptor, { id: 'draft-recovery' }).content), { exports: result, require: imports, window, document: { hidden: false }, AbortController })
  let state
  const Host = Vue.defineComponent({ setup() { state = result.default.setup(props, { expose() {}, emit() {} }); return () => null } })
  const renderer = Vue.createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode() {}, nextSibling() {} })
  const app = renderer.createApp(Host); app.mount({})
  return { props, cache, key, value, draft, state, setRead(fn) { read = fn }, invalidate() { for (const fn of listeners.get('medical-context-invalidated') ?? []) fn() }, close() { app.unmount() } }
}
test('actual editor retries failed authorization/body read without losing draft, field inputs or reason', async () => {
  const h = harness()
  try {
    h.props.active = true
    h.setRead(async () => { throw Error('temporary read failure') })
    await assert.rejects(h.state.restore(), /temporary read failure/)
    assert.equal(h.state.body.value, null, 'no cached body before fresh authorized read')
    assert.equal(h.draft.invalidated, false)
    h.setRead(async () => ({ snapshot: h.value.snapshot, body: h.value.baseline }))
    await h.state.restore()
    assert.equal(h.state.draft.value, h.draft)
    assert.equal(h.state.body.value, h.draft.body)
    assert.equal(h.state.reason.value, 'retain this reason')
    assert.equal(h.state.fieldInputs.value, h.value.fieldInputs)
    assert.equal(h.cache.resumeDocumentDraft(h.key), null, 'only one consumer owns the draft')
  } finally { h.close() }
})
test('route abort returns checkout without displaying it; later restoration keeps all edits', async () => {
  const h = harness(); let reject
  try {
    h.props.active = true; h.setRead(() => new Promise((_resolve, no) => { reject = no }))
    const pending = h.state.restore(); h.props.active = false; h.state.clear(); reject(Error('aborted'))
    await assert.rejects(pending, /aborted/)
    assert.equal(h.state.body.value, null); assert.equal(h.draft.invalidated, false)
    h.props.active = true; h.setRead(async () => ({ snapshot: h.value.snapshot, body: h.value.baseline }))
    await h.state.restore(); assert.equal(h.state.body.value, h.draft.body); assert.equal(h.state.reason.value, h.value.reason)
  } finally { h.close() }
})
test('explicit context invalidation revokes checked-out draft even if identical scope is recreated before read settles', async () => {
  const h = harness(); let reject
  try {
    h.props.active = true; h.setRead(() => new Promise((_resolve, no) => { reject = no }))
    const pending = h.state.restore(); h.invalidate()
    const recreated = h.cache.documentDraftKey(h.props.session, h.props.projectId, h.props.documentId)
    reject(Error('read aborted after logout')); await assert.rejects(pending)
    assert.equal(h.draft.invalidated, true); assert.equal(h.state.body.value, null)
    assert.equal(h.cache.resumeDocumentDraft(recreated), null, 'late finally must not resurrect revoked content')
  } finally { h.close() }
})
test('tenant/auth scope switch revokes in-flight checkout and cannot restore it into another document', async () => {
  const h = harness(); let reject
  try {
    h.props.active = true; h.setRead(() => new Promise((_resolve, no) => { reject = no }))
    const pending = h.state.restore()
    const nextSession = { ...h.props.session, current_context: { ...h.props.session.current_context, tenant_id: 'other-tenant', auth_epoch: '2' } }
    const nextKey = h.cache.documentDraftKey(nextSession, h.props.projectId, h.props.documentId)
    reject(Error('scope switched')); await assert.rejects(pending)
    assert.equal(h.draft.invalidated, true)
    assert.equal(h.cache.resumeDocumentDraft(nextKey), null)
    assert.equal(h.cache.resumeDocumentDraft(h.key), null)
  } finally { h.close() }
})
test('accepted checkout releases its lease for later clean reload/edit cycles', async () => {
  const h = harness()
  try {
    h.props.active = true; await h.state.restore()
    const next = { ...h.value, draft: { ...h.draft, invalidated: false } }
    assert.equal(h.cache.retainDocumentDraft(h.key, next), true)
    assert.equal(h.cache.resumeDocumentDraft(h.key), next)
    assert.equal(h.cache.resumeDocumentDraft(h.key), null)
  } finally { h.close() }
})
test('actual read button retries restoration rather than bypassing retained draft', () => {
  assert.match(source, /@click="restore">读取完整正文/)
  assert.deepEqual(compileTemplate({ source: parse(source).descriptor.template.content, filename: 'document-editor.vue', id: 'recovery' }).errors, [])
})

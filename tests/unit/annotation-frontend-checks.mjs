// Pure domain, mocked transport and DOM-free Vue setup checks. No HTTP, browser, socket, database or provider.
import assert from 'node:assert/strict'
import { createHash, randomUUID, webcrypto } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { build } from '../../web/node_modules/esbuild/lib/main.js'
const root = path.resolve(import.meta.dirname, '../..'), output = path.join(root, '.implementation-work/annotation-frontend-unit')
const dep = createRequire(path.join(root, 'web/package.json')); const { canonicalizeEx } = dep('json-canonicalize')
const { parse, compileScript } = dep('@vue/compiler-sfc')
const vue = dep('vue'); const stored = new Map(), calls = [], checks = []
const pass = name => checks.push(name)
globalThis.window = new EventTarget()
globalThis.sessionStorage = { getItem: key => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value), removeItem: key => stored.delete(key) }
let delayDigest = null
Object.defineProperty(globalThis, 'crypto', { configurable: true, value: { randomUUID, subtle: { digest: async (...args) => {
  const wait = delayDigest; delayDigest = null; if (wait) { wait.started(); await wait.promise }
  return webcrypto.subtle.digest(...args)
} } } })
const digest = value => createHash('sha256').update(canonicalizeEx(value)).digest('hex')
await mkdir(output, { recursive: true })
const entry = path.join(output, 'entry.ts')
await writeFile(entry, `export * from '../../web/src/api/medical/annotations';\nexport * from '../../web/src/api/medical/annotation-domain';\nexport * from '../../web/src/api/medical/document-selection';\nexport * from '../../web/src/api/medical/document-body';\nexport { default as AnnotationPanel } from '../../web/src/views/medical/document-annotations.vue';\nexport { default as DocumentEditor } from '../../web/src/views/medical/document-editor.vue';\n`)
await build({ entryPoints: [entry], bundle: true, platform: 'node', format: 'cjs', outfile: path.join(output, 'suite.cjs'), plugins: [{
  name: 'pure-local-mocks', setup(b) {
    b.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'mock' }))
    b.onResolve({ filter: /^@\/store\/modules\/user$/ }, () => ({ path: 'user', namespace: 'mock' }))
    b.onResolve({ filter: /^@\// }, args => ({ path: path.join(root, 'web/src', args.path.slice(2)) + (path.extname(args.path) ? '' : '.ts') }))
    b.onResolve({ filter: /^[a-z]/ }, args => ({ path: dep.resolve(args.path), external: true }))
    b.onResolve({ filter: /\?raw$/ }, args => ({ path: path.resolve(args.resolveDir, args.path.replace(/\?raw$/, '')), namespace: 'raw' }))
    b.onLoad({ filter: /.*/, namespace: 'raw' }, async args => ({ contents: await readFile(args.path, 'utf8'), loader: 'text' }))
    b.onLoad({ filter: /.*/, namespace: 'mock' }, args => ({ contents: args.path === 'request'
      ? 'export default { get: (...a) => globalThis.annotationTransport("GET", ...a), post: (...a) => globalThis.annotationTransport("POST", ...a) }'
      : 'export default () => globalThis.annotationUser', loader: 'js' }))
    b.onLoad({ filter: /\.vue$/ }, async args => {
      if (!['document-annotations.vue', 'document-editor.vue'].includes(path.basename(args.path))) return { contents: 'export default {}', loader: 'js' }
      const { descriptor } = parse(await readFile(args.path, 'utf8'), { filename: args.path })
      const script = compileScript(descriptor, { id: path.basename(args.path), inlineTemplate: false })
      return { contents: script.content, loader: 'ts', resolveDir: path.dirname(args.path) }
    })
  }
}] })
const api = (await import(pathToFileURL(path.join(output, 'suite.cjs')))).default
function fixture() {
  const project = randomUUID(), document = randomUUID(), documentRevision = randomUUID(), rootId = randomUUID(), textId = randomUUID()
  const body = { schema_version: '1.0.0', template_version: randomUUID(), fields: [], evidence_links: [], fact_usages: [], nodes: [
    { node_id: rootId, parent_id: null, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'DOCUMENT', title: 'SIMULATION' } },
    { node_id: textId, parent_id: rootId, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'TEXT', text: '甲😀e\u0301\n乙 SIMULATION', marks: [] } }
  ] }
  const reference = schema => ({ artifact_id: randomUUID(), sha256: digest(body), schema_ref: schema,
    coverage: { selection_sha256: digest(body), delivered_sha256: digest(body), complete: true, next_cursor: null } })
  const snapshot = { document_id: document, project_id: project, document_revision: documentRevision, revision: '4', plan_revision: randomUUID(),
    template_version: body.template_version, body: reference(api.documentReference + 'DocumentBody'), fields: reference('FIELDS'), evidence_links: reference('EVIDENCE_LINKS'),
    body_schema_revision: randomUUID(), check_ids: [], review_ids: [], signature_ids: [], invalidation_reasons: [], issue_watermark: '2', evidence_watermark: '1',
    auth_epoch: '1', governance_epoch: '1', allowed_actions: ['document.edit'], stream_cursors: [] }
  const session = { schema_version: '1.0.0', user: { user_id: randomUUID(), username: 'SIMULATION', display_name: 'SIMULATION', platform_role: 'USER' }, memberships: [],
    current_context: { tenant_id: randomUUID(), context_revision: '1', auth_epoch: '1', governance_epoch: '1' }, csrf: { header_name: 'X-CSRF-TOKEN', token: 'SIMULATION-CSRF' },
    action_summary: ['annotation.read', 'annotation.write'].map(action => ({ action, project_id: project, resource_kind: 'DOCUMENT', resource_id: document })),
    invitation_entry: { method: 'POST', path_template: '/api/member-invitations/{id}/acceptances' } }
  return { body, snapshot, session, textId }
}
const f = fixture(), signal = new AbortController().signal
const selection = await api.createDocumentSelection(f.body, f.snapshot.document_revision, [{ kind: 'TEXT', node_id: f.textId, start_utf16: 1, end_utf16: 5 }], signal)
const create = { document_revision: f.snapshot.document_revision, expected_revision: f.snapshot.revision, selection, content: '  SIMULATION 标注 😀e\u0301\n保留原文  ' }
const annotation = { annotation_id: randomUUID(), document_revision: f.snapshot.document_revision, revision: '1', selection, selection_artifact_id: randomUUID(),
  selection_sha256: digest(selection), content: create.content, actor_id: f.session.user.user_id, created_at: '2026-10-18T05:00:00.123456Z', status: 'OPEN', location_status: 'CURRENT_VERSION', reply_count: '0' }
const operationId = randomUUID()
const receipt = { schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision, revision: '5', issue_watermark: '3', evidence_watermark: '1',
  operation_id: operationId, previous_revision: '4', annotation, annotation_sha256: digest(annotation), invalidated_review_ids: [] }
const withHash = content => ({ ...content, listing_sha256: digest(content) })
const threads = items => withHash({ schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision,
  revision: items.length ? '5' : '4', issue_watermark: items.length ? '3' : '2', evidence_watermark: '1', status_filter: null, total: String(items.length), complete: true, items })
const replies = items => withHash({ schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision,
  annotation_id: annotation.annotation_id, revision: String(items.length + 1), status: 'OPEN', location_status: 'CURRENT_VERSION', total: String(items.length), complete: true, items })
const envelope = (data, status = 200, resourceRevision = data?.revision ?? null) => { const id = randomUUID(); return { status,
  headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': id },
  data: { schema_version: '1.0.0', request_id: id, resource_revision: resourceRevision, data } } }
const operation = (id, target) => ({ operation_id: id, status: 'COMPLETED', resource_id: target, status_url: '/api/operations/' + id })
let currentThreads = threads([]), currentReplies = replies([]), writeFailure = false, activeReceipt = receipt, lookupStatus = 'FOUND', onGet = null
let heldWrite = null
const transport = async (method, url, first, second) => {
  const input = method === 'POST' ? first : null, config = method === 'POST' ? second : first
  calls.push({ method, url, input, config }); config.signal?.throwIfAborted()
  assert.equal(config.headers['X-Schema-Version'], '1.0.0'); assert.equal(config.headers['X-Context-Revision'], '1')
  if (method === 'GET') {
    if (onGet) await onGet(url)
    if (url.endsWith('/annotation-threads')) { assert.deepEqual(config.params, { document_revision: f.snapshot.document_revision }); return envelope(currentThreads) }
    if (url.endsWith('/decisions')) return envelope(withHash({ ...((({ listing_sha256, ...rest }) => rest)(currentReplies)), items: [], total: '0' }))
    if (url.endsWith('/replies')) { assert.equal(config.params, undefined); return envelope(currentReplies) }
    if (url.startsWith('/api/operations/')) return envelope(operation(activeReceipt.operation_id, activeReceipt.reply ? annotation.annotation_id : f.snapshot.document_id))
    if (url === '/api/documents/' + f.snapshot.document_id) return envelope(f.snapshot)
    if (url.endsWith('/dependency-access-set')) return envelope(randomUUID(), 200, f.snapshot.revision)
    if (url.endsWith('/body')) return envelope({ document_revision: f.snapshot.document_revision, body_sha256: f.snapshot.body.sha256, nodes: f.body.nodes,
      fields: [], evidence_links: [], fact_usages: [], coverage: { selection_sha256: 'a'.repeat(64), delivered_sha256: 'b'.repeat(64), next_cursor: null, complete: true } })
    throw new Error('Unexpected mock GET ' + url)
  }
  assert.equal(config.headers['X-CSRF-TOKEN'], 'SIMULATION-CSRF')
  if (url === '/api/operation-lookups') return envelope({ lookup_status: lookupStatus, operation: lookupStatus === 'FOUND' ? operation(activeReceipt.operation_id, input.operation_id === 'RV-04-THREAD-1' ? f.snapshot.document_id : annotation.annotation_id) : null })
  assert.match(config.headers['Idempotency-Key'], /^[0-9a-f-]{36}$/)
  if (heldWrite) await heldWrite
  if (writeFailure) throw new Error('SIMULATION uncertain transport failure')
  if (url.endsWith('/annotation-threads')) { currentThreads = threads([annotation]); activeReceipt = receipt; return envelope(receipt, 201) }
  if (url.endsWith('/replies')) {
    const reply = { reply_id: randomUUID(), annotation_id: annotation.annotation_id, actor_id: f.session.user.user_id, created_at: '2026-10-18T05:00:01Z', content: input.content }
    activeReceipt = { schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision,
      annotation_id: annotation.annotation_id, revision: '2', status: 'OPEN', operation_id: randomUUID(), previous_revision: '1', reply, reply_sha256: digest(reply) }
    currentReplies = replies([reply]); currentThreads = threads([{ ...annotation, revision: '2', reply_count: '1' }]); return envelope(activeReceipt, 201)
  }
  throw new Error('Unexpected mock POST ' + url)
}
globalThis.annotationTransport = transport
const rejects = (action, code) => assert.rejects(action, failure => failure.code === code)
await api.verifyAnnotationListing(f.snapshot, f.body, threads([annotation]), '5', signal)
await api.verifyReplyListing(f.snapshot, annotation.annotation_id, replies([]), '1', signal)
assert.equal(api.selectionSummary(f.body, selection), '“😀é”')
pass('real Selection and exact Unicode preview verify against fixed saved body')
for (const patch of [{ complete: false }, { unknown: true }, { total: '02' }]) await rejects(() => api.verifyAnnotationListing(f.snapshot, f.body, { ...threads([annotation]), ...patch }, '5', signal), 'SCHEMA_INVALID')
await rejects(() => api.verifyAnnotationListing(f.snapshot, f.body, threads([annotation, annotation]), '5', signal), 'CONTENT_MISMATCH')
await rejects(() => api.verifyAnnotationListing(f.snapshot, f.body, { ...threads([annotation]), total: '2' }, '5', signal), 'CONTENT_MISMATCH')
const changed = structuredClone(f.body); changed.nodes[1].content.text += ' unsaved'
await rejects(() => api.verifyAnnotationListing(f.snapshot, changed, threads([annotation]), '5', signal), 'CONTENT_MISMATCH')
const staleSelection = structuredClone(selection); staleSelection.segments[0].quote_sha256 = 'f'.repeat(64)
await rejects(() => api.verifyAnnotationSelection(f.body, f.snapshot.document_revision, staleSelection, signal), 'SELECTION_STALE')
for (const text of ['', ' \n\t', '\u2003', '\ud800', 'x\u0000', 'a'.repeat(16001)]) assert.throws(() => api.annotationContent(text))
for (const text of [' 😀e\u0301\n ', '\u00a0', '\u2007', '\u202f']) api.annotationContent(text)
pass('strict shape, completeness, duplicates, hashes, fixed body and Java-compatible Unicode boundaries reject corruption')
assert.equal(api.annotationAllowed({ ...f.session, action_summary: [] }, f.snapshot, 'annotation.write'), false)
assert.equal(api.annotationAllowed({ ...f.session, current_context: null }, f.snapshot, 'annotation.write'), false)
const pending = await api.prepareAnnotation(() => f.session, f.snapshot, f.body, create, null, signal)
assert.equal((await api.prepareAnnotation(() => f.session, f.snapshot, f.body, create, null, signal)).key, pending.key)
await rejects(() => api.prepareAnnotation(() => f.session, f.snapshot, f.body, { ...create, content: 'other' }, null, signal), 'IDEMPOTENCY_CONFLICT')
const persisted = [...stored.values()].join('')
for (const secret of [create.content, f.body.nodes[1].content.text, f.textId, f.session.csrf.token, 'segments', 'selection']) assert.equal(persisted.includes(secret), false)
assert.equal(api.readPendingAnnotation({ ...f.session, user: { ...f.session.user, user_id: randomUUID() } }, f.snapshot.document_id), null)
pass('request recovery persists only identities/digests, rejects replacements and isolates actor')
writeFailure = true
await assert.rejects(() => api.submitAnnotation(() => f.session, pending, f.snapshot, f.body, create, signal), /uncertain/)
const failedKey = calls.at(-1).config.headers['Idempotency-Key']; assert.equal(api.readPendingAnnotation(f.session, f.snapshot.document_id).key, pending.key)
writeFailure = false
const found = await api.lookupAnnotation(() => f.session, pending, signal); assert.equal(found.operation.status, 'COMPLETED')
const result = await api.submitAnnotation(() => f.session, pending, f.snapshot, f.body, create, signal)
assert.equal(calls.at(-1).config.headers['Idempotency-Key'], failedKey)
await api.settleAnnotation(() => f.session, pending, f.snapshot, f.body, create, result, signal)
assert.equal(stored.size, 0)
pass('uncertain create recovers original operation/key/body, validates receipt + completed operation + full readback')
const parent = currentReplies, replyInput = { document_revision: f.snapshot.document_revision, expected_revision: parent.revision, content: 'SIMULATION 回复\n原样保留' }
const replyPending = await api.prepareAnnotation(() => f.session, f.snapshot, f.body, replyInput, parent, signal)
const posted = await api.submitAnnotation(() => f.session, replyPending, f.snapshot, f.body, replyInput, signal)
await rejects(() => api.verifyAnnotationReceipt(replyPending, f.snapshot, f.body, replyInput, { ...posted, status: 'RESOLVED' }, '2', signal), 'CONTENT_MISMATCH')
await api.settleAnnotation(() => f.session, replyPending, f.snapshot, f.body, replyInput, posted, signal)
assert.equal(stored.size, 0); assert.equal(currentReplies.status, 'OPEN')
const originalVersion = withHash({ ...((({ listing_sha256, ...rest }) => rest)(currentReplies)), location_status: 'ORIGINAL_VERSION', status: 'REANCHOR_REQUIRED' })
await api.verifyReplyListing(f.snapshot, annotation.annotation_id, originalVersion, '2', signal)
pass('reply uses parent annotation CAS and exact original document; CURRENT/ORIGINAL and REANCHOR_REQUIRED remain honest')
function gate() { let release, started; const entered = new Promise(r => { started = r }); const promise = new Promise(r => { release = r }); delayDigest = { started, promise }; return { entered, release } }
const abortedPending = await api.prepareAnnotation(() => f.session, f.snapshot, f.body, create, null, signal)
let before = calls.length; const cancellation = new AbortController(), wait = gate()
const aborted = api.submitAnnotation(() => f.session, abortedPending, f.snapshot, f.body, create, cancellation.signal)
await wait.entered; cancellation.abort(); wait.release(); await assert.rejects(aborted, error => error.name === 'AbortError'); assert.equal(calls.length, before)
let liveSession = f.session; const contextGate = gate()
const changedContext = api.submitAnnotation(() => liveSession, abortedPending, f.snapshot, f.body, create, signal)
await contextGate.entered; liveSession = { ...f.session, current_context: { ...f.session.current_context, context_revision: '2' } }; contextGate.release()
await rejects(() => changedContext, 'CONTEXT_CHANGED'); assert.equal(calls.length, before)
assert.equal(api.readPendingAnnotation(f.session, f.snapshot.document_id).key, abortedPending.key)
pass('cancellation or session switch during async digest prevents POST dispatch and retains original recovery identity')
await rejects(() => api.verifyAnnotationReceipt(abortedPending, f.snapshot, f.body, create, { ...receipt, annotation_sha256: 'f'.repeat(64) }, '5', signal), 'CONTENT_MISMATCH')
assert.equal(api.readPendingAnnotation(f.session, f.snapshot.document_id).key, abortedPending.key)
lookupStatus = 'NOT_FOUND'; await api.lookupAnnotation(() => f.session, abortedPending, signal)
assert.equal(api.readPendingAnnotation(f.session, f.snapshot.document_id).key, abortedPending.key)
lookupStatus = 'EXPIRED'; await api.lookupAnnotation(() => f.session, abortedPending, signal)
assert.equal(api.readPendingAnnotation(f.session, f.snapshot.document_id).key, abortedPending.key); lookupStatus = 'FOUND'
pass('bad receipt, NOT_FOUND and EXPIRED never clear recovery identity or mint a replacement key')
api.clearPendingAnnotation(f.session, abortedPending)

// Execute actual SFC setup with Vue's custom renderer: no DOM/browser and no HTTP transport.
const renderer = vue.createRenderer({ createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: text => ({ text }),
  setText: () => {}, setElementText: () => {}, parentNode: () => null, nextSibling: () => null, insert: () => {}, remove: () => {}, patchProp: () => {} })
function mount(component, props) { const app = renderer.createApp({ ...component, render: () => null }, props); const router = dep('vue-router'); app.provide(router.routeLocationKey, { path: '', query: {} }); app.provide(router.routerKey, { push() {} }); app.config.warnHandler = () => {}; const vm = app.mount({}); return { app, state: vm.$.setupState, componentProps: vm.$.props } }
async function idle(state) { for (let i = 0; i < 200 && state.busy; i++) await new Promise(r => setTimeout(r, 1)); assert.equal(state.busy, false) }
globalThis.annotationUser = vue.reactive({ medicalWriting: false })
currentThreads = threads([]); currentReplies = replies([])
const panelProps = { session: f.session, snapshot: f.snapshot, baseline: f.body, selection, active: true, dirty: false, busy: false, history: false, isCurrent: true }
let refreshed = 0
const panel = mount(api.AnnotationPanel, { ...panelProps, refreshDocument: async incomingSignal => { incomingSignal.throwIfAborted(); assert.equal(globalThis.annotationUser.medicalWriting, true); refreshed++ } })
await idle(panel.state); assert.equal(panel.state.canCreate, true)
panel.state.content = create.content; await panel.state.createThread(); assert.equal(panel.state.error, ''); assert.equal(refreshed, 1); assert.equal(panel.state.content, ''); assert.equal(panel.state.pending, null)
assert.equal(panel.state.selectedId, annotation.annotation_id); assert.equal(globalThis.annotationUser.medicalWriting, false)
pass('actual panel discovers annotations, creates from validated Selection and holds write lock across parent refresh')
panel.app.unmount()
for (const patch of [{ dirty: true }, { history: true }, { isCurrent: false }, { selection: null }, { snapshot: { ...f.snapshot, signature_ids: [randomUUID()] } },
  { session: { ...f.session, action_summary: f.session.action_summary.filter(item => item.action !== 'annotation.write') } }]) {
  const mounted = mount(api.AnnotationPanel, { ...panelProps, ...patch }); await idle(mounted.state); before = calls.length
  mounted.state.content = create.content; await mounted.state.createThread(); assert.equal(mounted.state.canCreate, false); assert.equal(calls.length, before); mounted.app.unmount()
}
pass('panel creation gates dirty fields/body, history, obsolete version, missing Selection, signatures and missing permission')
currentThreads = threads([]); currentReplies = replies([])
const cancelled = mount(api.AnnotationPanel, panelProps); await idle(cancelled.state); cancelled.state.content = create.content
let unblock; heldWrite = new Promise(resolve => { unblock = resolve }); const ongoing = cancelled.state.createThread()
for (let i = 0; i < 200 && !calls.at(-1)?.url.endsWith('/annotation-threads'); i++) await new Promise(r => setTimeout(r, 1))
// Wait for persistence+dispatch rather than a previous GET.
for (let i = 0; i < 200 && !(calls.at(-1)?.method === 'POST' && calls.at(-1)?.url.endsWith('/annotation-threads')); i++) await new Promise(r => setTimeout(r, 1))
const originalKey = cancelled.state.pending.key; cancelled.state.stopWaiting(); unblock(); heldWrite = null; await ongoing
assert.equal(cancelled.state.content, create.content); assert.equal(cancelled.state.pending.key, originalKey); assert.equal(globalThis.annotationUser.medicalWriting, false)
assert.equal(api.readPendingAnnotation(f.session, f.snapshot.document_id).key, originalKey)
await cancelled.state.recover(); assert.equal(cancelled.state.pending, null); assert.equal(cancelled.state.content, '')
cancelled.app.unmount()
pass('actual panel stop/recover preserves input, ignores late result and retries only original idempotency key')

// The current document head can move while an old exact request remains pending.
const historicalPending = await api.prepareAnnotation(() => f.session, f.snapshot, f.body, create, null, signal)
const historicalSnapshot = { ...f.snapshot, revision: '6', issue_watermark: '4' }
const historicalThreads = withHash({ ...((({ listing_sha256, ...rest }) => rest)(threads([{ ...annotation, location_status: 'ORIGINAL_VERSION' }]))), revision: '6', issue_watermark: '4' })
currentThreads = historicalThreads; currentReplies = withHash({ ...((({ listing_sha256, ...rest }) => rest)(replies([]))), location_status: 'ORIGINAL_VERSION' }); activeReceipt = receipt
const historyTransport = globalThis.annotationTransport
globalThis.annotationTransport = async (...args) => { const value = await historyTransport(...args); if (args[0] === 'POST' && args[1].endsWith('/annotation-threads')) currentThreads = historicalThreads; return value }
const historical = mount(api.AnnotationPanel, { ...panelProps, snapshot: historicalSnapshot, history: true, isCurrent: false })
await idle(historical.state); historical.state.content = create.content
assert.equal(historical.state.canCreate, false); assert.equal(historical.state.canReply, false)
lookupStatus = 'NOT_FOUND'; before = calls.filter(call => call.method === 'POST' && call.url.endsWith('/annotation-threads')).length
await historical.state.recover(); assert.equal(calls.filter(call => call.method === 'POST' && call.url.endsWith('/annotation-threads')).length, before)
assert.equal(historical.state.pending.key, historicalPending.key)
lookupStatus = 'FOUND'; await historical.state.recover(); assert.equal(historical.state.error, ''); assert.equal(historical.state.pending, null)
assert.equal(stored.size, 0); historical.app.unmount(); globalThis.annotationTransport = historyTransport
pass('history panel recovers registered original operation after document head moves; NOT_FOUND makes no new write')

const editor = mount(api.DocumentEditor, { session: f.session, projectId: f.snapshot.project_id, documentId: f.snapshot.document_id, active: true })
// The editor now auto-loads its current saved version on mount. Set up local edits only after that real setup finishes.
for (let i = 0; i < 200 && editor.state.loading; i++) await new Promise(resolve => setTimeout(resolve, 1))
assert.equal(editor.state.loading, false)
assert.equal(editor.state.saved.document_revision, f.snapshot.document_revision)
const editBuffer = new api.DocumentEditBuffer(f.body, 100); const local = structuredClone(f.body); local.nodes[1].content.text += ' local draft'; editBuffer.edit(local)
editor.state.saved = f.snapshot; editor.state.currentSnapshot = f.snapshot; editor.state.baseline = f.body; editor.state.draft = editBuffer; editor.state.body = editBuffer.body
editor.state.fieldInputs = { local: { original_value: 'unconfirmed field' } }; editor.state.annotationMainBusy = true; globalThis.annotationUser.medicalWriting = true
assert.equal(editor.state.editable, false); assert.equal(editor.state.hasUnsaved, true)
const localBody = editor.state.body, fields = editor.state.fieldInputs
const originalTransport = globalThis.annotationTransport
const snapshotAfter = { ...f.snapshot, revision: '5', issue_watermark: '3' }
globalThis.annotationTransport = async (method, url, config) => {
  if (url === '/api/documents/' + f.snapshot.document_id) return envelope(snapshotAfter)
  if (url.endsWith('/dependency-access-set')) return envelope(randomUUID(), 200, '5')
  if (url.endsWith('/body')) return envelope({ document_revision: f.snapshot.document_revision, body_sha256: f.snapshot.body.sha256,
    nodes: f.body.nodes, fields: [], evidence_links: [], fact_usages: [], coverage: { selection_sha256: 'a'.repeat(64), delivered_sha256: 'b'.repeat(64), complete: true, next_cursor: null } })
  return originalTransport(method, url, config)
}
await editor.state.refreshAfterAnnotation(signal)
assert.equal(editor.state.saved.revision, '5'); assert.equal(editor.state.body, localBody); assert.equal(editor.state.fieldInputs, fields); assert.equal(editBuffer.dirty, true); assert.equal(editBuffer.canUndo, true)
const old = editor.state.saved; const refreshedRevision = randomUUID(); const newerSnapshot = { ...snapshotAfter, document_revision: refreshedRevision, revision: '6' }
globalThis.annotationTransport = async (method, url) => {
  if (url === '/api/documents/' + f.snapshot.document_id) return envelope(newerSnapshot)
  if (url.endsWith('/dependency-access-set')) return envelope(randomUUID(), 200, '6')
  if (url.endsWith('/body')) return envelope({ document_revision: refreshedRevision, body_sha256: f.snapshot.body.sha256,
    nodes: f.body.nodes, fields: [], evidence_links: [], fact_usages: [], coverage: { selection_sha256: 'a'.repeat(64), delivered_sha256: 'b'.repeat(64), complete: true, next_cursor: null } })
  throw new Error('unexpected editor transport')
}
await rejects(() => editor.state.refreshAfterAnnotation(signal), 'REVISION_CONFLICT')
assert.equal(editor.state.saved, old); assert.equal(editor.state.currentSnapshot.document_revision, refreshedRevision); assert.equal(editor.state.body, localBody); assert.equal(editor.state.fieldInputs, fields)
// Abort during a delayed parent refresh must not apply even valid late data.
let resumeRefresh, refreshStarted
const enteredRefresh = new Promise(resolve => { refreshStarted = resolve }), pendingRefresh = new Promise(resolve => { resumeRefresh = resolve })
globalThis.annotationTransport = async (method, url) => { refreshStarted(); await pendingRefresh; return envelope({ ...snapshotAfter, revision: '7' }) }
const refreshCancellation = new AbortController(); const snapshotBeforeAbort = editor.state.saved; const currentBeforeAbort = editor.state.currentSnapshot
const refreshing = editor.state.refreshAfterAnnotation(refreshCancellation.signal); await enteredRefresh; refreshCancellation.abort(); resumeRefresh()
await assert.rejects(refreshing, failure => failure.name === 'AbortError')
assert.equal(editor.state.saved, snapshotBeforeAbort); assert.equal(editor.state.currentSnapshot, currentBeforeAbort); assert.equal(editor.state.body, localBody); assert.equal(editor.state.fieldInputs, fields)
editor.state.selectedForAnnotation(selection, false); assert.equal(editor.state.annotationSelection, selection)
editor.state.selectedForAnnotation(selection, true); assert.equal(editor.state.annotationSelection, null)
pass('parent abort blocks late refresh mutation; dirty field/body selection is never admitted for annotation')
editor.app.unmount(); globalThis.annotationUser.medicalWriting = false
pass('actual editor refresh under global write lock preserves local body/field input/undo and retains explicit conflict on newer fixed version')

// Decision extension regression: independent review authority, both CAS identities, immutable human history.
const reviewSession = structuredClone(f.session)
reviewSession.action_summary.push({ action: 'review.decide', project_id: f.snapshot.project_id, resource_kind: 'DOCUMENT', resource_id: f.snapshot.document_id })
let decisionSnapshot = { ...f.snapshot, revision: '5', issue_watermark: '3' }
let decisionItems = [], decisionStatus = 'OPEN', decisionRevision = '2', decisionHead = '5', decisionWatermark = '3'
let decisionLocation = 'CURRENT_VERSION', decisionReceipt = null, decisionLookup = 'FOUND', decisionWriteFailure = false, decisionWriteGate = null
const decisionHistory = () => withHash({ schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision,
  annotation_id: annotation.annotation_id, revision: decisionRevision, status: decisionStatus, location_status: decisionLocation, total: String(decisionItems.length), complete: true, items: decisionItems })
const decisionThreads = () => withHash({ schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision,
  revision: decisionHead, issue_watermark: decisionWatermark, evidence_watermark: '1', status_filter: null, total: '1', complete: true,
  items: [{ ...annotation, revision: decisionRevision, status: decisionStatus, location_status: decisionLocation }] })
const decisionReplies = () => withHash({ ...((({ listing_sha256, ...rest }) => rest)(replies([]))), revision: decisionRevision, status: decisionStatus, location_status: decisionLocation })
const originalDecisionReceipts = new Map()
globalThis.annotationTransport = async (method, url, first, second) => {
  const config = method === 'POST' ? second : first; config.signal?.throwIfAborted(); calls.push({ method, url, input: method === 'POST' ? first : null, config })
  if (method === 'GET') {
    if (url.endsWith('/annotation-threads')) return envelope(decisionThreads())
    if (url.endsWith('/replies')) return envelope(decisionReplies())
    if (url.endsWith('/decisions')) return envelope(decisionHistory())
    if (url.startsWith('/api/operations/')) return envelope(operation(url.split('/').at(-1), annotation.annotation_id))
  }
  if (url === '/api/operation-lookups') return envelope({ lookup_status: decisionLookup,
    operation: decisionLookup === 'FOUND' ? operation(decisionReceipt?.operation_id ?? randomUUID(), annotation.annotation_id) : null })
  if (method === 'POST' && url.endsWith('/decisions')) {
    assert.equal(config.headers['X-CSRF-TOKEN'], 'SIMULATION-CSRF'); assert.equal(config.headers['X-Context-Revision'], '1')
    if (decisionWriteGate) await decisionWriteGate
    if (decisionWriteFailure) throw new Error('SIMULATION uncertain decision')
    const key = config.headers['Idempotency-Key']
    if (originalDecisionReceipts.has(key)) return envelope(originalDecisionReceipts.get(key), 201)
    const item = { decision_id: randomUUID(), annotation_id: annotation.annotation_id, actor_id: reviewSession.user.user_id,
      created_at: `2026-10-18T05:10:${String(decisionItems.length).padStart(2, '0')}Z`, decision: first.decision, reason: first.reason,
      previous_status: decisionStatus, status: first.decision === 'RESOLVE' ? 'RESOLVED' : 'OPEN', previous_revision: first.expected_revision,
      revision: String(BigInt(first.expected_revision) + 1n) }
    decisionRevision = item.revision; decisionStatus = item.status; decisionHead = String(BigInt(first.expected_document_revision) + 1n); decisionWatermark = String(BigInt(decisionWatermark) + 1n)
    decisionItems = [...decisionItems, item]
    decisionReceipt = { schema_version: '1.0.0', annotation_id: annotation.annotation_id, document_id: f.snapshot.document_id,
      document_revision: f.snapshot.document_revision, revision: decisionRevision, status: decisionStatus, operation_id: randomUUID(),
      previous_revision: first.expected_revision, previous_document_revision: first.expected_document_revision, document_head_revision: decisionHead,
      issue_watermark: decisionWatermark, evidence_watermark: '1', invalidated_review_ids: [], decision: item, decision_sha256: digest(item) }
    originalDecisionReceipts.set(key, decisionReceipt); return envelope(decisionReceipt, 201)
  }
  throw new Error('Unexpected decision mock ' + method + ' ' + url)
}
const decisionInput = () => ({ document_revision: f.snapshot.document_revision, expected_revision: decisionRevision,
  expected_document_revision: decisionHead, decision: decisionStatus === 'OPEN' ? 'RESOLVE' : 'REOPEN', reason: '  SIMULATION 人工理由 😀e\u0301\n原样保留  ' })
const decisionRead = () => reviewSession
await rejects(() => api.prepareAnnotation(() => f.session, decisionSnapshot, f.body, decisionInput(), decisionHistory(), signal), 'ACCESS_DENIED')
for (const grant of [
  { action: 'review.decide', resource_kind: 'DOCUMENT', resource_id: randomUUID(), project_id: f.snapshot.project_id },
  { action: 'review.decide', resource_kind: 'PROJECT', resource_id: randomUUID(), project_id: randomUUID() },
  { action: 'review.decide', resource_kind: 'ENTERPRISE', resource_id: randomUUID(), project_id: null }
]) assert.equal(api.annotationAllowed({ ...f.session, action_summary: [grant] }, f.snapshot, 'review.decide'), false)
for (const status of ['OPEN', 'RESOLVED', 'RETURNED', 'REANCHOR_REQUIRED']) for (const decision of ['RESOLVE', 'REOPEN']) {
  assert.equal(api.decisionTransition(status, decision), status === 'OPEN' && decision === 'RESOLVE' || status === 'RESOLVED' && decision === 'REOPEN')
}
for (const reason of ['', ' \n\t', '\u0000', '\ud800', '😀'.repeat(16001)]) {
  assert.throws(() => api.validateAnnotationRequest('RV-06-DECISION-1', { ...decisionInput(), reason }))
}
for (const patch of [{ unknown: true }, { decision: 'REANCHOR' }, { expected_document_revision: '01' }, { expected_document_revision: '0' }, { expected_revision: '18446744073709551615' }]) {
  assert.throws(() => api.validateAnnotationRequest('RV-06-DECISION-1', { ...decisionInput(), ...patch }))
}
api.validateAnnotationRequest('RV-06-DECISION-1', { ...decisionInput(), reason: '😀'.repeat(16000) })
for (const patch of [{ status: 'RETURNED' }, { status: 'REANCHOR_REQUIRED' }, { status: 'RESOLVED' }, { location_status: 'ORIGINAL_VERSION' }]) {
  await rejects(() => api.prepareAnnotation(decisionRead, decisionSnapshot, f.body, decisionInput(), { ...decisionHistory(), ...patch }, signal), 'ACTION_STALE')
}
await rejects(() => api.prepareAnnotation(decisionRead, { ...decisionSnapshot, signature_ids: [randomUUID()] }, f.body, decisionInput(), decisionHistory(), signal), 'ACTION_STALE')
await rejects(() => api.prepareAnnotation(decisionRead, decisionSnapshot, f.body, { ...decisionInput(), expected_document_revision: '4' }, decisionHistory(), signal), 'ACTION_STALE')
pass('decision requires explicit scoped review.decide; validates both CAS identities, state transitions and exact Unicode reason')

const firstDecisionInput = decisionInput(), firstDecisionPending = await api.prepareAnnotation(decisionRead, decisionSnapshot, f.body, firstDecisionInput, decisionHistory(), signal)
assert.equal(firstDecisionPending.operation, 'RV-06-DECISION-1'); assert.equal(firstDecisionPending.expected_document_revision, '5')
for (const raw of stored.values()) { assert.equal(raw.includes(firstDecisionInput.reason), false); assert.equal(raw.includes('SIMULATION'), false); assert.equal(raw.includes('selection'), false) }
await rejects(() => api.prepareAnnotation(decisionRead, decisionSnapshot, f.body, { ...firstDecisionInput, reason: 'changed' }, decisionHistory(), signal), 'IDEMPOTENCY_CONFLICT')
await rejects(() => api.prepareAnnotation(decisionRead, decisionSnapshot, f.body, { ...replyInput, expected_revision: decisionRevision }, decisionReplies(), signal), 'IDEMPOTENCY_CONFLICT')
let decisionCancel = new AbortController(), decisionDigestGate = gate(); before = calls.length
let decisionDispatch = api.submitAnnotation(decisionRead, firstDecisionPending, decisionSnapshot, f.body, firstDecisionInput, decisionCancel.signal)
await decisionDigestGate.entered; decisionCancel.abort(); decisionDigestGate.release(); await assert.rejects(decisionDispatch, e => e.name === 'AbortError'); assert.equal(calls.length, before)
// A revoked summary must be rechecked after asynchronous hashing even if context epochs have not yet refreshed.
let deniedDuringDigest = reviewSession; decisionDigestGate = gate()
decisionDispatch = api.submitAnnotation(() => deniedDuringDigest, firstDecisionPending, decisionSnapshot, f.body, firstDecisionInput, signal)
await decisionDigestGate.entered; deniedDuringDigest = f.session; decisionDigestGate.release()
await rejects(() => decisionDispatch, 'ACCESS_DENIED'); assert.equal(calls.length, before)
let decisionLiveSession = reviewSession; decisionDigestGate = gate()
decisionDispatch = api.submitAnnotation(() => decisionLiveSession, firstDecisionPending, decisionSnapshot, f.body, firstDecisionInput, signal)
await decisionDigestGate.entered; decisionLiveSession = { ...reviewSession, current_context: { ...reviewSession.current_context, auth_epoch: '2' } }; decisionDigestGate.release()
await rejects(() => decisionDispatch, 'CONTEXT_CHANGED'); assert.equal(calls.length, before)
decisionWriteFailure = true
await assert.rejects(() => api.submitAnnotation(decisionRead, firstDecisionPending, decisionSnapshot, f.body, firstDecisionInput, signal), /uncertain decision/)
assert.equal(api.readPendingAnnotation(reviewSession, f.snapshot.document_id).key, firstDecisionPending.key)
decisionWriteFailure = false
const firstDecisionReceipt = await api.submitAnnotation(decisionRead, firstDecisionPending, decisionSnapshot, f.body, firstDecisionInput, signal)
for (const patch of [{ issue_watermark: '3' }, { document_head_revision: '7' }, { status: 'OPEN' }, { decision_sha256: 'a'.repeat(64) }]) {
  await rejects(() => api.verifyAnnotationReceipt(firstDecisionPending, decisionSnapshot, f.body, firstDecisionInput, { ...firstDecisionReceipt, ...patch }, firstDecisionReceipt.revision, signal), 'CONTENT_MISMATCH')
}
await api.settleAnnotation(decisionRead, firstDecisionPending, decisionSnapshot, f.body, firstDecisionInput, firstDecisionReceipt, signal)
assert.equal(stored.size, 0); assert.equal(decisionItems[0].reason, firstDecisionInput.reason)
pass('decision cancellation/context guards block POST; uncertain recovery preserves identity and verifies receipt, operation and full immutable history')

// Repeated human cycles retain every event; replies may create revision gaps but cannot alter history.
for (let i = 0; i < 3; i++) {
  decisionSnapshot = { ...decisionSnapshot, revision: decisionHead, issue_watermark: decisionWatermark }
  const input = decisionInput(), pending = await api.prepareAnnotation(decisionRead, decisionSnapshot, f.body, input, decisionHistory(), signal)
  const receipt = await api.submitAnnotation(decisionRead, pending, decisionSnapshot, f.body, input, signal)
  await api.settleAnnotation(decisionRead, pending, decisionSnapshot, f.body, input, receipt, signal)
}
assert.deepEqual(decisionItems.map(item => item.decision), ['RESOLVE', 'REOPEN', 'RESOLVE', 'REOPEN'])
assert.deepEqual(decisionItems[0], firstDecisionReceipt.decision)
await api.verifyDecisionListing(decisionSnapshot, annotation.annotation_id, decisionHistory(), decisionRevision, signal)
const copyHistory = () => structuredClone(decisionHistory())
for (const mutate of [v => { v.total = '0' }, v => { v.items[1].reason += 'tamper' }, v => { v.items[1].actor_id = randomUUID() }, v => { v.items.reverse() }]) {
  const bad = copyHistory(); mutate(bad); await rejects(() => api.verifyDecisionListing(decisionSnapshot, annotation.annotation_id, bad, decisionRevision, signal), 'CONTENT_MISMATCH')
}
const badChain = copyHistory(); badChain.items[1].previous_revision = '1'; badChain.listing_sha256 = digest((({ listing_sha256, ...rest }) => rest)(badChain))
await rejects(() => api.verifyDecisionListing(decisionSnapshot, annotation.annotation_id, badChain, decisionRevision, signal), 'CONTENT_MISMATCH')
// Ordering is revision-based: equal or backwards timestamps and descending UUIDs remain valid.
const tiedHistory = copyHistory()
tiedHistory.items.forEach((item, index) => { item.created_at = index < 2 ? '2026-10-18T05:10:00Z' : '2026-10-18T05:09:59Z'; item.decision_id = `ffffffff-ffff-4fff-8fff-${String(999999999999 - index)}` })
tiedHistory.listing_sha256 = digest((({ listing_sha256, ...rest }) => rest)(tiedHistory))
await api.verifyDecisionListing(decisionSnapshot, annotation.annotation_id, tiedHistory, decisionRevision, signal)
const duplicatedHistory = structuredClone(tiedHistory); duplicatedHistory.items[1].decision_id = duplicatedHistory.items[0].decision_id
duplicatedHistory.listing_sha256 = digest((({ listing_sha256, ...rest }) => rest)(duplicatedHistory))
await rejects(() => api.verifyDecisionListing(decisionSnapshot, annotation.annotation_id, duplicatedHistory, decisionRevision, signal), 'CONTENT_MISMATCH')
const afterReplyHistory = withHash({ ...((({ listing_sha256, ...rest }) => rest)(decisionHistory())), revision: String(BigInt(decisionRevision) + 1n) })
await api.verifyDecisionListing(decisionSnapshot, annotation.annotation_id, afterReplyHistory, afterReplyHistory.revision, signal)
const wrongAfterReply = withHash({ ...((({ listing_sha256, ...rest }) => rest)(afterReplyHistory)), status: 'RESOLVED' })
await rejects(() => api.verifyDecisionListing(decisionSnapshot, annotation.annotation_id, wrongAfterReply, wrongAfterReply.revision, signal), 'CONTENT_MISMATCH')
pass('repeated resolve/reopen preserves ordered immutable reasons/actors/revisions; digest and state-chain corruption fail closed')

// Restore a pending original receipt after later decisions; current OPEN must not invalidate the original RESOLVED receipt.
const pendingStorageKey = [...stored.keys()][0] ?? 'medical.annotation.write:' + reviewSession.user.user_id + ':' + reviewSession.current_context.tenant_id + ':' + f.snapshot.document_id
stored.set(pendingStorageKey, JSON.stringify(firstDecisionPending))
await api.settleAnnotation(decisionRead, firstDecisionPending, decisionSnapshot, f.body, firstDecisionInput, firstDecisionReceipt, signal)
assert.equal(stored.size, 0)
pass('settlement accepts exact original decision after later reopen without equating current and historical status')

const decisionPanelProps = () => ({ ...panelProps, session: reviewSession, snapshot: { ...decisionSnapshot, revision: decisionHead, issue_watermark: decisionWatermark } })
for (const patch of [{ dirty: true }, { history: true }, { isCurrent: false }, { snapshot: { ...decisionSnapshot, revision: decisionHead, signature_ids: [randomUUID()] } }, { session: f.session }]) {
  const mounted = mount(api.AnnotationPanel, { ...decisionPanelProps(), ...patch }); await idle(mounted.state); before = calls.length
  mounted.state.decisionReason = 'SIMULATION'; await mounted.state.decide('RESOLVE'); assert.equal(mounted.state.canDecide, false); assert.equal(calls.length, before); mounted.app.unmount()
}
for (const status of ['RETURNED', 'REANCHOR_REQUIRED']) {
  const savedStatus = decisionStatus; decisionStatus = status
  const mounted = mount(api.AnnotationPanel, decisionPanelProps()); await idle(mounted.state); before = calls.length
  assert.equal(mounted.state.canDecide, false); await mounted.state.decide('RESOLVE'); assert.equal(calls.length, before); mounted.app.unmount(); decisionStatus = savedStatus
}
// Dirty edits arriving during validation cancel before a fresh POST or recovery identity is created.
const changedDuringValidation = mount(api.AnnotationPanel, decisionPanelProps()); await idle(changedDuringValidation.state)
changedDuringValidation.state.decisionReason = 'SIMULATION changed during digest'; const uiDigestGate = gate(); before = calls.length
const interruptedDecision = changedDuringValidation.state.decide('RESOLVE'); await uiDigestGate.entered
changedDuringValidation.componentProps.dirty = true; uiDigestGate.release(); await interruptedDecision
assert.equal(calls.length, before); assert.equal(changedDuringValidation.state.pending, null); assert.equal(globalThis.annotationUser.medicalWriting, false)
assert.equal(changedDuringValidation.state.decisionReason, 'SIMULATION changed during digest'); changedDuringValidation.app.unmount()
pass('actual panel gates decisions on clean current unsigned fixed body, explicit permission and OPEN/RESOLVED states only; dirty changes cancel pre-dispatch')

let decisionRefreshStarted, releaseDecisionRefresh
const decisionRefreshEntered = new Promise(resolve => { decisionRefreshStarted = resolve }), decisionRefreshWait = new Promise(resolve => { releaseDecisionRefresh = resolve })
let refreshMutated = false
const decidingPanel = mount(api.AnnotationPanel, { ...decisionPanelProps(), refreshDocument: async signal => {
  assert.equal(globalThis.annotationUser.medicalWriting, true); decisionRefreshStarted(); await decisionRefreshWait; signal.throwIfAborted(); refreshMutated = true
} })
await idle(decidingPanel.state); assert.equal(decidingPanel.state.canDecide, true); decidingPanel.state.decisionReason = 'SIMULATION deliberate resolve'
const deciding = decidingPanel.state.decide('RESOLVE'); await decisionRefreshEntered
assert.equal(globalThis.annotationUser.medicalWriting, true); assert.equal(decidingPanel.state.pending, null); assert.equal(decidingPanel.state.decisions.items.length, 5)
decidingPanel.state.stopWaiting(); releaseDecisionRefresh(); await deciding
assert.equal(refreshMutated, false); assert.equal(globalThis.annotationUser.medicalWriting, false); decidingPanel.app.unmount()
pass('actual decision panel verifies full history and holds shared lock through abortable parent refresh')

const uncertainPanel = mount(api.AnnotationPanel, decisionPanelProps()); await idle(uncertainPanel.state); uncertainPanel.state.decisionReason = 'SIMULATION deliberate reopen'
let unblockDecision; decisionWriteGate = new Promise(resolve => { unblockDecision = resolve })
const uncertain = uncertainPanel.state.decide('REOPEN')
for (let i = 0; i < 200 && !(calls.at(-1)?.method === 'POST' && calls.at(-1)?.url.endsWith('/decisions')); i++) await new Promise(resolve => setTimeout(resolve, 1))
const uncertainIdentity = uncertainPanel.state.pending
assert.ok(uncertainIdentity); uncertainPanel.state.stopWaiting(); unblockDecision(); decisionWriteGate = null; await uncertain
assert.equal(uncertainPanel.state.pending.key, uncertainIdentity.key); assert.equal(uncertainPanel.state.decisionReason, 'SIMULATION deliberate reopen')
uncertainPanel.app.unmount()
const historicalDecisionPanel = mount(api.AnnotationPanel, { ...decisionPanelProps(), history: true, isCurrent: false })
await idle(historicalDecisionPanel.state); assert.equal(historicalDecisionPanel.state.pending.key, uncertainIdentity.key)
decisionLookup = 'NOT_FOUND'; before = calls.filter(call => call.method === 'POST' && call.url.endsWith('/decisions')).length
await historicalDecisionPanel.state.recover(); assert.equal(calls.filter(call => call.method === 'POST' && call.url.endsWith('/decisions')).length, before)
assert.equal(historicalDecisionPanel.state.pending.key, uncertainIdentity.key)
// Emulate reload losing memory: a mistyped reason fails before POST, and a corrected reason remains recoverable.
historicalDecisionPanel.state.originalRequest = null; historicalDecisionPanel.state.decisionReason = 'wrong reason'
decisionLookup = 'FOUND'; await historicalDecisionPanel.state.recover()
assert.equal(historicalDecisionPanel.state.originalRequest, null); assert.equal(historicalDecisionPanel.state.pending.key, uncertainIdentity.key)
assert.equal(calls.filter(call => call.method === 'POST' && call.url.endsWith('/decisions')).length, before)
historicalDecisionPanel.state.decisionReason = 'SIMULATION deliberate reopen'; await historicalDecisionPanel.state.recover(); assert.equal(historicalDecisionPanel.state.error, ''); assert.equal(historicalDecisionPanel.state.pending, null)
assert.equal(calls.filter(call => call.method === 'POST' && call.url.endsWith('/decisions')).at(-1).config.headers['Idempotency-Key'], uncertainIdentity.key)
assert.equal(stored.size, 0); historicalDecisionPanel.app.unmount()
pass('decision stop/unmount/historical recovery retains exact reason and both original CAS identities; NOT_FOUND never dispatches historical write')

await writeFile(path.join(output, 'result.json'), JSON.stringify({ result: 'PASS', scope: 'PURE_DOMAIN_MOCKED_TRANSPORT_DOM_FREE_VUE_SETUP', actual_http_verified: false,
  browser: 'NOT_RUN', sockets: 0, databases: 0, providers: 0, checks, mocked_transport_calls: calls.length }, null, 2) + '\n')
console.log(JSON.stringify({ result: 'PASS', checks: checks.length, mocked_transport_calls: calls.length, actual_http_verified: false, browser: 'NOT_RUN' }))

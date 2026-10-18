// Pure domain, mocked transport and DOM-free Vue setup checks. No HTTP, browser, socket, database or provider.
import assert from 'node:assert/strict'
import { createHash, randomUUID, webcrypto } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { build } from '../../web/node_modules/esbuild/lib/main.js'
const root = path.resolve(import.meta.dirname, '../..'), output = path.join(root, '.implementation-work/reanchor-frontend-unit')
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
await writeFile(entry, `export * from '../../web/src/api/medical/annotation-reanchors';\nexport * from '../../web/src/api/medical/annotations';\nexport * from '../../web/src/api/medical/annotation-domain';\nexport * from '../../web/src/api/medical/document-selection';\nexport * from '../../web/src/api/medical/document-body';\nexport { default as ReanchorPanel } from '../../web/src/views/medical/document-reanchors.vue';\nexport { default as AnnotationPanel } from '../../web/src/views/medical/document-annotations.vue';\nexport { default as DocumentEditor } from '../../web/src/views/medical/document-editor.vue';\n`)
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
      if (!['document-reanchors.vue', 'document-annotations.vue', 'document-editor.vue'].includes(path.basename(args.path))) return { contents: 'export default {}', loader: 'js' }
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
f.session.action_summary.push({ ...f.session.action_summary[0], action: 'review.decide' })
const selection = await api.createDocumentSelection(f.body, f.snapshot.document_revision, [{ kind: 'TEXT', node_id: f.textId, start_utf16: 1, end_utf16: 5 }], signal)
const annotation = { annotation_id: randomUUID(), document_revision: f.snapshot.document_revision, revision: '2', selection, selection_artifact_id: randomUUID(), selection_sha256: digest(selection), content: 'SIMULATION original comment', actor_id: f.session.user.user_id, created_at: '2026-10-18T05:00:00Z', status: 'REANCHOR_REQUIRED', location_status: 'CURRENT_VERSION', reply_count: '0' }
const origin = { anchor_id: randomUUID(), previous_anchor_id: null, document_revision: f.snapshot.document_revision, selection_artifact_id: annotation.selection_artifact_id, selection_sha256: digest(selection), selection, annotation_revision: '1', actor_id: annotation.actor_id, created_at: annotation.created_at, reason: null }
const item = { annotation, effective_anchor: origin, anchor_revision: '1', location_status: 'REANCHOR_REQUIRED' }
const input = { schema_version: '1.0.0', document_revision: f.snapshot.document_revision, expected_revision: '2', expected_document_revision: '4', expected_anchor_id: origin.anchor_id, expected_anchor_revision: '1', selection, reason: 'SIMULATION deliberate manual relocation 😀', human_confirmed: true }
const withHash = value => ({ ...value, listing_sha256: digest(value) })
const list = items => withHash({ schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision, revision: items[0]?.annotation.status === 'OPEN' ? '5' : '4', issue_watermark: items[0]?.annotation.status === 'OPEN' ? '3' : '2', evidence_watermark: '1', complete: true, total: String(items.length), items })
const history = items => withHash({ lifecycle: [{ kind: 'INVALIDATE', previous_revision: '1', revision: '2', previous_status: 'OPEN', status: 'REANCHOR_REQUIRED' }, ...(items.length === 1 ? [] : [{ kind: 'REANCHOR', previous_revision: '2', revision: '3', previous_status: 'REANCHOR_REQUIRED', status: 'OPEN' }])], schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision, annotation_id: annotation.annotation_id, revision: items.length === 1 ? '2' : '3', status: items.length === 1 ? 'REANCHOR_REQUIRED' : 'OPEN', anchor_revision: String(items.length), effective_anchor_id: items.at(-1).anchor_id, complete: true, total: String(items.length), items })
const newAnchor = { ...origin, anchor_id: randomUUID(), previous_anchor_id: origin.anchor_id, annotation_revision: '3', reason: input.reason, created_at: '2026-10-18T05:02:00Z' }
const receiptData = { schema_version: '1.0.0', document_id: f.snapshot.document_id, document_revision: f.snapshot.document_revision, annotation_id: annotation.annotation_id, revision: '3', status: 'OPEN', previous_revision: '2', document_head_revision: '5', previous_document_revision: '4', issue_watermark: '3', evidence_watermark: '1', operation_id: randomUUID(), previous_anchor_id: origin.anchor_id, previous_anchor_revision: '1', anchor_revision: '2', effective_anchor: newAnchor, effective_anchor_sha256: digest(newAnchor), invalidated_review_ids: [] }
const receipt = { ...receiptData, receipt_sha256: digest(receiptData) }
const envelope = (data, status = 200, resourceRevision = data?.revision ?? null) => { const id = randomUUID(); return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': id }, data: { schema_version: '1.0.0', request_id: id, resource_revision: resourceRevision, data } } }
let listing = list([item]), histories = history([origin]), uncertain = false, lookup = 'FOUND', writeGate = null
const transport = async (method, url, first, second) => {
  const config = method === 'GET' ? first : second, body = method === 'POST' ? first : null
  calls.push({ method, url, body, config }); config.signal?.throwIfAborted()
  if (url.endsWith('/annotation-anchors')) return envelope(listing)
  if (url.endsWith('/anchors')) return envelope(histories)
  if (url.startsWith('/api/operations/')) return envelope({ operation_id: receipt.operation_id, resource_id: annotation.annotation_id, status: 'COMPLETED', status_url: '/api/operations/' + receipt.operation_id }, 200, null)
  if (url === '/api/operation-lookups') return envelope({ lookup_status: lookup, operation: lookup === 'FOUND' ? { operation_id: receipt.operation_id, resource_id: annotation.annotation_id, status: 'COMPLETED', status_url: '/api/operations/' + receipt.operation_id } : null }, 200, null)
  if (url.endsWith('/reanchors')) {
    assert.equal(config.headers['X-CSRF-TOKEN'], 'SIMULATION-CSRF'); assert.deepEqual(body, input)
    if (writeGate) await writeGate
    if (uncertain) throw new Error('SIMULATION uncertain response')
    listing = list([{ ...item, annotation: { ...annotation, revision: '3', status: 'OPEN' }, effective_anchor: newAnchor, anchor_revision: '2', location_status: 'CURRENT_VERSION' }]); histories = history([origin, newAnchor]); return envelope(receipt, 201)
  }
  throw new Error('Unexpected mock ' + url)
}
globalThis.annotationTransport = transport
globalThis.annotationUser = { medicalWriting: false }
const rejects = (action, code) => assert.rejects(action, error => error.code === code)
await api.readAnnotationAnchors(() => f.session, f.snapshot, f.body, signal)
const changed = structuredClone(f.body); changed.nodes[1].content.text += 'unsaved'
await rejects(() => api.readAnnotationAnchors(() => f.session, f.snapshot, changed, signal), 'CONTENT_MISMATCH')
const savedListing = listing
listing = withHash({ ...(({ listing_sha256, ...rest }) => rest)(listing), issue_watermark: '1' })
await rejects(() => api.readAnnotationAnchors(() => f.session, f.snapshot, f.body, signal), 'CONTENT_MISMATCH'); listing = savedListing
pass('complete effective projection verifies real saved body, original Selection, effective Selection, digest and watermark floors')
await rejects(() => api.prepareReanchor(() => f.session, f.snapshot, f.body, item, { ...input, human_confirmed: false }, signal), 'SCHEMA_INVALID')
await rejects(() => api.prepareReanchor(() => ({ ...f.session, action_summary: f.session.action_summary.filter(x => x.action !== 'review.decide') }), f.snapshot, f.body, item, input, signal), 'ACCESS_DENIED')
await rejects(() => api.prepareReanchor(() => f.session, { ...f.snapshot, signature_ids: [randomUUID()] }, f.body, item, input, signal), 'ACTION_STALE')
await rejects(() => api.prepareReanchor(() => f.session, f.snapshot, f.body, { ...item, annotation: { ...annotation, status: 'OPEN' } }, input, signal), 'ACTION_STALE')
pass('new write requires review authority, unsigned saved target, explicit human confirmation and REANCHOR_REQUIRED')
const pending = await api.prepareReanchor(() => f.session, f.snapshot, f.body, item, input, signal)
assert.equal((await api.prepareReanchor(() => f.session, f.snapshot, f.body, item, input, signal)).key, pending.key)
for (const secret of [input.reason, annotation.content, 'segments', f.textId, f.session.csrf.token]) assert.equal([...stored.values()].join('').includes(secret), false)
await rejects(() => api.prepareReanchor(() => f.session, f.snapshot, f.body, item, { ...input, reason: 'replacement' }, signal), 'IDEMPOTENCY_CONFLICT')
pass('only identities and digests persist; exact pending body/CAS identity cannot be replaced')
uncertain = true; await assert.rejects(() => api.submitReanchor(() => f.session, pending, f.snapshot, f.body, input, signal), /uncertain/)
const before = calls.length
await rejects(() => api.submitReanchor(() => f.session, pending, f.snapshot, f.body, { ...input, reason: 'wrong' }, signal), 'IDEMPOTENCY_CONFLICT'); assert.equal(calls.length, before)
uncertain = false; await api.lookupReanchor(() => f.session, pending, signal)
const result = await api.submitReanchor(() => f.session, pending, f.snapshot, f.body, input, signal)
assert.equal(calls.at(-1).config.headers['Idempotency-Key'], pending.key)
await api.settleReanchor(() => f.session, pending, f.snapshot, f.body, input, result, signal); assert.equal(stored.size, 0)
pass('uncertain response recovers original operation/key/CAS; immutable receipt + operation + current projection + exact history clear pending')
for (const patch of [{ status: 'RESOLVED' }, { previous_anchor_revision: '2' }, { effective_anchor_sha256: 'f'.repeat(64) }, { issue_watermark: '4' }]) await assert.rejects(() => api.verifyReanchorReceipt(pending, input, { ...receipt, ...patch }, '3', signal))
const savedHistory = histories
histories = history([origin, { ...newAnchor, previous_anchor_id: randomUUID() }]); await rejects(() => api.readAnchorHistory(() => f.session, annotation.annotation_id, signal), 'CONTENT_MISMATCH'); histories = savedHistory
pass('receipt OPEN, source anchor, watermark and full previous-anchor history chain cannot be substituted')
const renderer = vue.createRenderer({ patchProp() {}, insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}), createComment: () => ({}), setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null })
function mount(props) { const app = renderer.createApp({ ...api.ReanchorPanel, render: () => null }, props); app.config.warnHandler = () => {}; const vm = app.mount({}); return { app, state: vm.$.setupState, props: vm.$.props } }
async function idle(state) { for (let i = 0; i < 300 && state.busy; i++) await new Promise(resolve => setTimeout(resolve, 1)); assert.equal(state.busy, false); assert.equal(state.error, '') }
listing = list([item]); histories = history([origin])
const props = { session: f.session, snapshot: f.snapshot, baseline: f.body, selection, active: true, dirty: false, busy: false, history: false, isCurrent: true }
for (const patch of [{ dirty: true }, { history: true }, { isCurrent: false }, { snapshot: { ...f.snapshot, signature_ids: [randomUUID()] } }]) {
  const panel = mount({ ...props, ...patch }); await idle(panel.state); panel.state.reason = input.reason; panel.state.chooseSelection(); panel.state.confirmed = true
  const count = calls.length; await panel.state.reanchor(); assert.equal(calls.length, count); assert.equal(panel.state.canReanchor, false); panel.app.unmount()
}
pass('DOM-free real SFC rejects dirty, historical, noncurrent and signed new writes')
const panel = mount(props); await idle(panel.state); panel.state.reason = input.reason; panel.state.confirmed = true
assert.equal(panel.state.chosenSelection, null); const count = calls.length; await panel.state.reanchor(); assert.equal(calls.length, count)
panel.state.chooseSelection(); assert.equal(panel.state.confirmed, false); panel.state.confirmed = true
let release; writeGate = new Promise(resolve => { release = resolve }); const writing = panel.state.reanchor()
for (let i = 0; i < 300 && !panel.state.pending; i++) await new Promise(resolve => setTimeout(resolve, 1))
assert.ok(panel.state.pending); const originalPending = panel.state.pending; assert.equal(globalThis.annotationUser.medicalWriting, true)
panel.state.stopWaiting(); release(); writeGate = null; await writing
assert.equal(panel.state.pending.key, originalPending.key); assert.equal(panel.state.reason, input.reason); assert.equal(globalThis.annotationUser.medicalWriting, false)
panel.app.unmount()
pass('explicit selection adoption resets human checkbox; stop releases locks and retains exact request and reason')
const recovery = mount({ ...props, history: true, isCurrent: false }); await idle(recovery.state)
lookup = 'NOT_FOUND'; let posts = calls.filter(x => x.url.endsWith('/reanchors')).length; await recovery.state.recover(); assert.equal(calls.filter(x => x.url.endsWith('/reanchors')).length, posts)
lookup = 'FOUND'; recovery.state.originalRequest = null; recovery.state.reason = 'wrong'; recovery.state.confirmed = true
await recovery.state.recover(); assert.equal(recovery.state.originalRequest, null); assert.equal(calls.filter(x => x.url.endsWith('/reanchors')).length, posts)
recovery.state.reason = input.reason; recovery.state.confirmed = true; await recovery.state.recover(); assert.equal(recovery.state.error, ''); assert.equal(recovery.state.pending, null); assert.equal(stored.size, 0); recovery.app.unmount()
pass('historical recovery requires FOUND; mistyped reconstruction never replaces original identity, corrected exact request replays')
// Cross-version source and effective discussion composition, using actual API/domain/SFC consumers.
const targetBody = structuredClone(f.body); targetBody.nodes[1].content.text += ' SIMULATION saved V2'
const target = { ...f.snapshot, document_revision: randomUUID(), revision: '5', issue_watermark: '3', body: { ...f.snapshot.body, artifact_id: randomUUID(), sha256: digest(targetBody) } }
const targetSelection = await api.createDocumentSelection(targetBody, target.document_revision, [{ kind: 'TEXT', node_id: f.textId, start_utf16: 1, end_utf16: 5 }], signal)
const targetAnchor = { ...newAnchor, document_revision: target.document_revision, selection: targetSelection, selection_sha256: digest(targetSelection) }
let threadRevision = '3', threadStatus = 'OPEN', head = '5', issue = '3', replyRows = [], decisionRows = []
let lifecycle = [{ kind: 'INVALIDATE', previous_revision: '1', revision: '2', previous_status: 'OPEN', status: 'REANCHOR_REQUIRED' }, { kind: 'REANCHOR', previous_revision: '2', revision: '3', previous_status: 'REANCHOR_REQUIRED', status: 'OPEN' }]
let opId = randomUUID()
const effectiveListing = () => withHash({ schema_version: '1.0.0', document_id: target.document_id, document_revision: target.document_revision, revision: head, issue_watermark: issue, evidence_watermark: '1', complete: true, total: '1', items: [{ ...item, annotation: { ...annotation, revision: threadRevision, status: threadStatus, reply_count: String(replyRows.length), location_status: 'ORIGINAL_VERSION' }, effective_anchor: targetAnchor, anchor_revision: '2', location_status: 'CURRENT_VERSION' }] })
const effectiveHistory = () => withHash({ ...(({ listing_sha256, ...rest }) => rest)(history([origin, targetAnchor])), document_revision: target.document_revision, revision: threadRevision, status: threadStatus, lifecycle })
const discussion = items => withHash({ schema_version: '1.0.0', annotation_id: annotation.annotation_id, document_id: target.document_id, document_revision: target.document_revision, revision: threadRevision, status: threadStatus, location_status: 'CURRENT_VERSION', total: String(items.length), complete: true, items })
const crossTransport = async (method, url, first, second) => {
  const config = method === 'GET' ? first : second, body = method === 'POST' ? first : null
  calls.push({ method, url, body, config }); config.signal?.throwIfAborted()
  if (method === 'GET') {
    if (url === '/api/documents/' + target.document_id) return envelope({ ...target, revision: head, issue_watermark: issue })
    if (url.endsWith('/annotation-anchors')) return envelope(effectiveListing())
    if (url.endsWith('/anchors')) return envelope(effectiveHistory())
    if (url.endsWith('/replies')) return envelope(discussion(replyRows))
    if (url.endsWith('/decisions')) return envelope(discussion(decisionRows))
    if (url.endsWith('/revisions/' + f.snapshot.document_revision)) return envelope(f.snapshot)
    if (url.endsWith('/revisions/' + target.document_revision)) return envelope({ ...target, revision: head, issue_watermark: issue })
    if (url.endsWith('/body')) return envelope({ document_revision: f.snapshot.document_revision, body_sha256: f.snapshot.body.sha256, nodes: f.body.nodes, fields: [], evidence_links: [], fact_usages: [], coverage: { selection_sha256: 'a'.repeat(64), delivered_sha256: 'b'.repeat(64), next_cursor: null, complete: true } })
    if (url.startsWith('/api/operations/')) return envelope({ operation_id: opId, resource_id: annotation.annotation_id, status: 'COMPLETED', status_url: '/api/operations/' + opId }, 200, null)
  }
  if (url.endsWith('/replies')) {
    const previous = threadRevision; threadRevision = String(BigInt(previous) + 1n)
    const row = { reply_id: randomUUID(), annotation_id: annotation.annotation_id, actor_id: f.session.user.user_id, created_at: '2026-10-18T05:03:00Z', content: body.content }
    replyRows.push(row); lifecycle.push({ kind: 'REPLY', previous_revision: previous, revision: threadRevision, previous_status: threadStatus, status: threadStatus }); opId = randomUUID()
    return envelope({ schema_version: '1.0.0', annotation_id: annotation.annotation_id, document_id: target.document_id, document_revision: target.document_revision, revision: threadRevision, status: threadStatus, operation_id: opId, previous_revision: previous, reply: row, reply_sha256: digest(row) }, 201)
  }
  if (url.endsWith('/decisions')) {
    const previous = threadRevision, previousHead = head; threadRevision = String(BigInt(previous) + 1n); head = String(BigInt(head) + 1n); issue = String(BigInt(issue) + 1n)
    const row = { decision_id: randomUUID(), annotation_id: annotation.annotation_id, actor_id: f.session.user.user_id, created_at: '2026-10-18T05:04:00Z', decision: body.decision, reason: body.reason, previous_status: threadStatus, status: 'RESOLVED', previous_revision: previous, revision: threadRevision }
    threadStatus = 'RESOLVED'; decisionRows.push(row); lifecycle.push({ kind: 'DECISION', previous_revision: previous, revision: threadRevision, previous_status: 'OPEN', status: threadStatus }); opId = randomUUID()
    return envelope({ schema_version: '1.0.0', annotation_id: annotation.annotation_id, document_id: target.document_id, document_revision: target.document_revision, revision: threadRevision, status: threadStatus, operation_id: opId, previous_revision: previous, previous_document_revision: previousHead, document_head_revision: head, issue_watermark: issue, evidence_watermark: '1', invalidated_review_ids: [], decision: row, decision_sha256: digest(row) }, 201)
  }
  throw new Error('Unexpected cross-version mock ' + url)
}
globalThis.annotationTransport = crossTransport
const projection = await api.readAnnotationThreads(() => f.session, target, targetBody, signal, true)
assert.equal(projection.items[0].document_revision, f.snapshot.document_revision)
assert.equal(projection.items[0].effective_anchor.document_revision, target.document_revision)
assert.equal(projection.items[0].location_status, 'CURRENT_VERSION')
assert.ok(calls.some(call => call.url.endsWith('/revisions/' + f.snapshot.document_revision)))
pass('current effective projection independently fetches original saved source and preserves original revision/Selection while exposing new effective position')
const app = renderer.createApp({ ...api.AnnotationPanel, render: () => null }, { ...props, snapshot: target, baseline: targetBody, selection: targetSelection, effective: true })
app.config.warnHandler = () => {}; const discussionVm = app.mount({}).$.setupState; await idle(discussionVm)
assert.equal(discussionVm.canReply, true); assert.equal(discussionVm.canDecide, true)
discussionVm.replyText = 'SIMULATION reply after reanchor'; await discussionVm.appendReply(); assert.equal(discussionVm.error, ''); assert.equal(replyRows.length, 1); assert.equal(discussionVm.pending, null)
discussionVm.decisionReason = 'SIMULATION resolve after reanchor'; await discussionVm.decide('RESOLVE'); assert.equal(discussionVm.error, ''); assert.equal(decisionRows.length, 1); assert.equal(discussionVm.pending, null); assert.equal(stored.size, 0)
app.unmount()
pass('actual normal discussion SFC replies then resolves reanchored current thread through effective readback and merged lifecycle verification')
const oldDecision = { ...decisionRows[0], previous_revision: '1', revision: '2' }
const mergedDecisions = withHash({ ...(({ listing_sha256, ...rest }) => rest)(discussion([oldDecision, { ...decisionRows[0], decision_id: randomUUID(), previous_revision: '4', revision: '5' }])), revision: '5' })
const mergedLifecycle = [ { kind: 'DECISION', previous_revision: '1', revision: '2', previous_status: 'OPEN', status: 'RESOLVED' }, { kind: 'INVALIDATE', previous_revision: '2', revision: '3', previous_status: 'RESOLVED', status: 'REANCHOR_REQUIRED' }, { kind: 'REANCHOR', previous_revision: '3', revision: '4', previous_status: 'REANCHOR_REQUIRED', status: 'OPEN' }, { kind: 'DECISION', previous_revision: '4', revision: '5', previous_status: 'OPEN', status: 'RESOLVED' } ]
await api.verifyDecisionListing(target, annotation.annotation_id, mergedDecisions, '5', signal, mergedLifecycle)
await rejects(() => api.verifyDecisionListing(target, annotation.annotation_id, mergedDecisions, '5', signal, mergedLifecycle.filter(event => event.kind !== 'DECISION')), 'CONTENT_MISMATCH')
pass('repeated RESOLVE separated by invalidation/reanchor requires correlated immutable lifecycle; missing decision evidence fails closed')

const thirdBody = structuredClone(targetBody); thirdBody.nodes[1].content.text += ' SIMULATION saved V3 changed again'
const third = { ...target, document_revision: randomUUID(), revision: '8', issue_watermark: '5', body: { ...target.body, artifact_id: randomUUID(), sha256: digest(thirdBody) } }
const thirdSelection = await api.createDocumentSelection(thirdBody, third.document_revision, [{ kind: 'TEXT', node_id: f.textId, start_utf16: 1, end_utf16: 5 }], signal)
threadRevision = '7'; threadStatus = 'REANCHOR_REQUIRED'; head = '8'; issue = '5'
lifecycle.push({ kind: 'INVALIDATE', previous_revision: '5', revision: '6', previous_status: 'RESOLVED', status: 'REANCHOR_REQUIRED' }, { kind: 'INVALIDATE', previous_revision: '6', revision: '7', previous_status: 'REANCHOR_REQUIRED', status: 'REANCHOR_REQUIRED' })
let thirdAnchors = [origin, targetAnchor], activeAnchor = targetAnchor, pointer = '2', replayReceipt = null, replayNormal = null
const thirdInput = { ...input, document_revision: third.document_revision, expected_revision: '7', expected_document_revision: '8', expected_anchor_id: targetAnchor.anchor_id, expected_anchor_revision: '2', selection: thirdSelection, reason: 'SIMULATION manual V3 location' }
const thirdAnchor = { ...targetAnchor, anchor_id: randomUUID(), previous_anchor_id: targetAnchor.anchor_id, document_revision: third.document_revision, selection: thirdSelection, selection_sha256: digest(thirdSelection), annotation_revision: '8', reason: thirdInput.reason, created_at: '2026-10-18T05:06:00Z' }
const thirdReceiptData = { ...receiptData, document_revision: third.document_revision, revision: '8', previous_revision: '7', previous_document_revision: '8', document_head_revision: '9', issue_watermark: '6', operation_id: randomUUID(), previous_anchor_id: targetAnchor.anchor_id, previous_anchor_revision: '2', anchor_revision: '3', effective_anchor: thirdAnchor, effective_anchor_sha256: digest(thirdAnchor) }
const thirdReceipt = { ...thirdReceiptData, receipt_sha256: digest(thirdReceiptData) }
const thirdItem = () => ({ ...item, annotation: { ...annotation, revision: threadRevision, status: threadStatus, reply_count: String(replyRows.length), location_status: 'ORIGINAL_VERSION' }, effective_anchor: activeAnchor, anchor_revision: pointer, location_status: threadStatus === 'REANCHOR_REQUIRED' ? 'REANCHOR_REQUIRED' : 'CURRENT_VERSION' })
const versions = new Map([[f.snapshot.document_revision, { snapshot: f.snapshot, body: f.body }], [target.document_revision, { snapshot: target, body: targetBody }], [third.document_revision, { snapshot: third, body: thirdBody }]])
const thirdTransport = async (method, url, first, second) => {
  const config = method === 'GET' ? first : second; config.signal?.throwIfAborted()
  if (method === 'GET' && url === '/api/documents/' + third.document_id) return envelope({ ...third, revision: head, issue_watermark: issue })
  if (method === 'GET' && (url.endsWith('/replies') || url.endsWith('/decisions'))) return envelope(withHash({ ...(({ listing_sha256, ...rest }) => rest)(discussion(url.endsWith('/replies') ? replyRows : decisionRows)), document_revision: activeAnchor.document_revision }))
  if (method === 'POST' && replayNormal && (url.endsWith('/replies') || url.endsWith('/decisions'))) { calls.push({ method, url, body: first, config }); opId = replayNormal.operation_id; return envelope(replayNormal, 201) }
  if (method === 'GET' && url.endsWith('/annotation-anchors')) {
    calls.push({ method, url, config })
    const row = thirdItem(); if (row.location_status === 'CURRENT_VERSION' && activeAnchor.document_revision !== config.params.document_revision) row.location_status = 'ORIGINAL_VERSION'
    return envelope(withHash({ schema_version: '1.0.0', document_id: third.document_id, document_revision: config.params.document_revision, revision: head, issue_watermark: issue, evidence_watermark: '1', complete: true, total: '1', items: [row] }))
  }
  if (method === 'GET' && url.endsWith('/anchors')) return envelope(withHash({ schema_version: '1.0.0', document_id: third.document_id, document_revision: activeAnchor.document_revision, annotation_id: annotation.annotation_id, revision: threadRevision, status: threadStatus, anchor_revision: pointer, effective_anchor_id: activeAnchor.anchor_id, total: String(thirdAnchors.length), complete: true, items: thirdAnchors, lifecycle }))
  const version = url.split('/revisions/')[1]
  if (method === 'GET' && version && versions.has(version)) return envelope(versions.get(version).snapshot)
  if (method === 'GET' && url.endsWith('/body')) {
    const saved = versions.get(config.params.document_revision)
    return envelope({ document_revision: saved.snapshot.document_revision, body_sha256: saved.snapshot.body.sha256, nodes: saved.body.nodes, fields: [], evidence_links: [], fact_usages: [], coverage: { selection_sha256: 'a'.repeat(64), delivered_sha256: 'b'.repeat(64), next_cursor: null, complete: true } })
  }
  if (method === 'POST' && url.endsWith('/reanchors')) {
    calls.push({ method, url, body: first, config })
    if (replayReceipt) { opId = replayReceipt.operation_id; return envelope(replayReceipt, 201) }
    assert.deepEqual(first, thirdInput); threadRevision = '8'; threadStatus = 'OPEN'; head = '9'; issue = '6'; pointer = '3'; activeAnchor = thirdAnchor; thirdAnchors.push(thirdAnchor); opId = thirdReceipt.operation_id
    lifecycle.push({ kind: 'REANCHOR', previous_revision: '7', revision: '8', previous_status: 'REANCHOR_REQUIRED', status: 'OPEN' }); return envelope(thirdReceipt, 201)
  }
  return crossTransport(method, url, first, second)
}
globalThis.annotationTransport = thirdTransport
const rr = await api.readAnnotationAnchors(() => f.session, third, thirdBody, signal)
assert.equal(rr.items[0].annotation.document_revision, f.snapshot.document_revision); assert.equal(rr.items[0].effective_anchor.document_revision, target.document_revision)
const rrApp = renderer.createApp({ ...api.AnnotationPanel, render: () => null }, { ...props, snapshot: third, baseline: thirdBody, selection: thirdSelection, effective: true }); rrApp.config.warnHandler = () => {}
const rrVm = rrApp.mount({}).$.setupState; await idle(rrVm); assert.equal(rrVm.error, ''); assert.ok(rrVm.replies); assert.ok(rrVm.decisions); assert.equal(rrVm.replies.document_revision, target.document_revision); assert.equal(rrVm.canReply, false); assert.equal(rrVm.canDecide, false); rrApp.unmount()
const thirdPending = await api.prepareReanchor(() => f.session, third, thirdBody, rr.items[0], thirdInput, signal)
const thirdResult = await api.submitReanchor(() => f.session, thirdPending, third, thirdBody, thirdInput, signal)
await api.settleReanchor(() => f.session, thirdPending, third, thirdBody, thirdInput, thirdResult, signal); assert.equal(stored.size, 0)
pass('three distinct saved bodies retain V1 original/V2 old effective/V3 target; repeated RR invalidations remain readable and second manual reanchor reopens')
const secondInput = { ...input, document_revision: target.document_revision, selection: targetSelection }
const secondData = { ...receiptData, document_revision: target.document_revision, effective_anchor: targetAnchor, effective_anchor_sha256: digest(targetAnchor) }
replayReceipt = { ...secondData, receipt_sha256: digest(secondData) }
const secondSnapshot = { ...target, revision: '4', issue_watermark: '2' }
const secondPending = await api.prepareReanchor(() => f.session, secondSnapshot, targetBody, item, secondInput, signal)
const replayed = await api.submitReanchor(() => f.session, secondPending, secondSnapshot, targetBody, secondInput, signal)
assert.equal(replayed.effective_anchor.anchor_id, targetAnchor.anchor_id)
const afterReplay = await api.settleReanchor(() => f.session, secondPending, secondSnapshot, targetBody, secondInput, replayed, signal)
assert.equal(afterReplay.items[0].effective_anchor.anchor_id, thirdAnchor.anchor_id); assert.equal(afterReplay.items[0].annotation.revision, '8'); assert.equal(stored.size, 0)
pass('exact older reanchor receipt replays after another reanchor, replies and decision; original operation clears only after immutable-history proof and latest projection')

const oldReplyInput = { document_revision: target.document_revision, expected_revision: '3', content: replyRows[0].content }
const oldReplyParent = { ...discussion([]), document_revision: target.document_revision, revision: '3', status: 'OPEN' }
const oldReplyPending = await api.prepareAnnotation(() => f.session, target, targetBody, oldReplyInput, oldReplyParent, signal)
replayNormal = { schema_version: '1.0.0', annotation_id: annotation.annotation_id, document_id: target.document_id, document_revision: target.document_revision, revision: '4', status: 'OPEN', operation_id: randomUUID(), previous_revision: '3', reply: replyRows[0], reply_sha256: digest(replyRows[0]) }
const oldReplyResult = await api.submitAnnotation(() => f.session, oldReplyPending, target, targetBody, oldReplyInput, signal)
assert.equal(calls.at(-1).config.headers['Idempotency-Key'], oldReplyPending.key)
await api.settleAnnotation(() => f.session, oldReplyPending, target, targetBody, oldReplyInput, oldReplyResult, signal, true); assert.equal(stored.size, 0)
const oldDecisionInput = { document_revision: target.document_revision, expected_revision: '4', expected_document_revision: '5', decision: 'RESOLVE', reason: decisionRows[0].reason }
const oldDecisionParent = { ...discussion([]), document_revision: target.document_revision, revision: '4', status: 'OPEN' }
const oldDecisionPending = await api.prepareAnnotation(() => f.session, target, targetBody, oldDecisionInput, oldDecisionParent, signal)
replayNormal = { schema_version: '1.0.0', annotation_id: annotation.annotation_id, document_id: target.document_id, document_revision: target.document_revision, revision: '5', status: 'RESOLVED', operation_id: randomUUID(), previous_revision: '4', previous_document_revision: '5', document_head_revision: '6', issue_watermark: '4', evidence_watermark: '1', invalidated_review_ids: [], decision: decisionRows[0], decision_sha256: digest(decisionRows[0]) }
const oldDecisionResult = await api.submitAnnotation(() => f.session, oldDecisionPending, target, targetBody, oldDecisionInput, signal)
assert.equal(calls.at(-1).config.headers['Idempotency-Key'], oldDecisionPending.key)
const settledDecision = await api.settleAnnotation(() => f.session, oldDecisionPending, target, targetBody, oldDecisionInput, oldDecisionResult, signal, true)
assert.equal(stored.size, 0); assert.equal(settledDecision.decisions.status, 'OPEN'); assert.equal(settledDecision.threads.document_revision, third.document_revision)
replayNormal = null
pass('old V2 reply and decision recover immutable original receipts after V3 reanchor using separate current source/lifecycle settlement, with no replacement key')

const abortPending = await api.prepareReanchor(() => f.session, secondSnapshot, targetBody, item, secondInput, signal)
let releaseDigest, enteredDigest
const digestEntered = new Promise(resolve => { enteredDigest = resolve })
delayDigest = { started: enteredDigest, promise: new Promise(resolve => { releaseDigest = resolve }) }
const controller = new AbortController(), beforeAbort = calls.length
const aborting = api.submitReanchor(() => f.session, abortPending, secondSnapshot, targetBody, secondInput, controller.signal)
await digestEntered; controller.abort(); releaseDigest(); await assert.rejects(aborting)
assert.equal(calls.length, beforeAbort); assert.equal(api.readPendingReanchor(f.session, target.document_id).key, abortPending.key)
let liveSession = f.session, releaseContext, enteredContext
const contextEntered = new Promise(resolve => { enteredContext = resolve })
delayDigest = { started: enteredContext, promise: new Promise(resolve => { releaseContext = resolve }) }
const switching = api.submitReanchor(() => liveSession, abortPending, secondSnapshot, targetBody, secondInput, signal)
await contextEntered; liveSession = { ...f.session, current_context: { ...f.session.current_context, context_revision: '2' } }; releaseContext()
await rejects(() => switching, 'CONTEXT_CHANGED'); assert.equal(calls.length, beforeAbort); stored.clear()
pass('reanchor abort or identity-context change during asynchronous hashing prevents POST and preserves pending exact operation')

await writeFile(path.join(output, 'result.json'), JSON.stringify({ result: 'PASS', scope: 'PURE_DOMAIN_MOCK_TRANSPORT_DOM_FREE_SFC', actual_http_verified: false, browser: 'NOT_RUN', checks, mocked_transport_calls: calls.length }, null, 2) + '\n')
console.log(JSON.stringify({ result: 'PASS', checks: checks.length, mocked_transport_calls: calls.length, actual_http_verified: false, browser: 'NOT_RUN' }))

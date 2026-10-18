// Pure domain, mocked transport and DOM-free Vue setup checks. No HTTP, browser, socket, database or provider.
import assert from 'node:assert/strict'
import { createHash, randomUUID, webcrypto } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { build } from '../../web/node_modules/esbuild/lib/main.js'
const root = path.resolve(import.meta.dirname, '../..'), output = path.join(root, '.implementation-work/reconciliation-frontend-unit')
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
await writeFile(entry, `export * from '../../web/src/api/medical/annotation-anchor-reconciliations';\nexport * from '../../web/src/api/medical/annotation-reanchors';\nexport * from '../../web/src/api/medical/annotations';\nexport * from '../../web/src/api/medical/annotation-domain';\nexport * from '../../web/src/api/medical/document-selection';\nexport * from '../../web/src/api/medical/document-body';\nexport { default as ReanchorPanel } from '../../web/src/views/medical/document-reanchors.vue';\nexport { default as AnnotationPanel } from '../../web/src/views/medical/document-annotations.vue';\nexport { default as DocumentEditor } from '../../web/src/views/medical/document-editor.vue';\n`)
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
const targetBody = structuredClone(f.body); targetBody.nodes[1].content.text += ' SAVED V2'
const target = { ...f.snapshot, document_revision: randomUUID(), body: { ...f.snapshot.body, artifact_id: randomUUID(), sha256: digest(targetBody) } }
const targetSelection = await api.createDocumentSelection(targetBody, target.document_revision, [{ kind: 'TEXT', node_id: f.textId, start_utf16: 1, end_utf16: 5 }], signal)
const annotation = { annotation_id: randomUUID(), document_revision: f.snapshot.document_revision, revision: '1', selection, selection_artifact_id: randomUUID(), selection_sha256: digest(selection), content: 'SIMULATION preserved original quote', actor_id: randomUUID(), created_at: '2026-10-18T05:00:00Z', status: 'OPEN', location_status: 'ORIGINAL_VERSION', reply_count: '0' }
const origin = { anchor_id: annotation.annotation_id, previous_anchor_id: null, document_revision: f.snapshot.document_revision, selection_artifact_id: annotation.selection_artifact_id, selection_sha256: digest(selection), selection, annotation_revision: '1', actor_id: annotation.actor_id, created_at: annotation.created_at, reason: null }
const withHash = value => ({ ...value, listing_sha256: digest(value) })
const envelope = (data, status = 200, resourceRevision = data?.revision ?? null) => { const id = randomUUID(); return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': id }, data: { schema_version: '1.0.0', request_id: id, resource_revision: resourceRevision, data } } }
let status, threadRevision, head, issue, lifecycle, anchors, receipt, currentSnapshot, currentBody, uncertain = false, lookup = 'FOUND', gate = null, failCurrentRead = false
const versions = new Map([[f.snapshot.document_revision, { snapshot: f.snapshot, body: f.body }], [target.document_revision, { snapshot: target, body: targetBody }]])
function reset(initial = 'OPEN') {
  stored.clear(); status = initial; threadRevision = initial === 'OPEN' ? '1' : '2'; head = '4'; issue = '2'; anchors = [origin]; receipt = null; currentSnapshot = target; currentBody = targetBody
  lifecycle = initial === 'OPEN' ? [] : [{ kind: 'DECISION', previous_revision: '1', revision: '2', previous_status: 'OPEN', status: 'RESOLVED' }]
}
function row(revision = currentSnapshot.document_revision) { return { annotation: { ...annotation, revision: threadRevision, status }, effective_anchor: anchors.at(-1), anchor_revision: String(anchors.length), location_status: status === 'REANCHOR_REQUIRED' ? 'REANCHOR_REQUIRED' : anchors.at(-1).document_revision === revision ? 'CURRENT_VERSION' : 'ORIGINAL_VERSION' } }
function history() { return withHash({ schema_version: '1.0.0', annotation_id: annotation.annotation_id, document_id: target.document_id, document_revision: anchors.at(-1).document_revision, status, anchor_revision: String(anchors.length), effective_anchor_id: anchors.at(-1).anchor_id, revision: threadRevision, complete: true, total: String(anchors.length), items: anchors, lifecycle }) }
function input() { return { schema_version: '1.0.0', document_revision: target.document_revision, expected_revision: threadRevision, expected_document_revision: head, expected_anchor_id: origin.anchor_id, expected_anchor_revision: '1', reason: 'SIMULATION explicit legacy location review', human_confirmed: true } }
globalThis.annotationUser = { medicalWriting: false }
globalThis.annotationTransport = async (method, url, first, second) => {
  const config = method === 'GET' ? first : second, body = method === 'POST' ? first : null
  calls.push({ method, url, body, config }); config.signal?.throwIfAborted()
  if (url.endsWith('/annotation-anchors')) return envelope(withHash({ schema_version: '1.0.0', document_id: target.document_id, document_revision: config.params.document_revision, revision: head, issue_watermark: issue, evidence_watermark: '1', complete: true, total: '1', items: [row(config.params.document_revision)] }))
  if (url.endsWith('/anchors')) return envelope(history())
  if (url === '/api/documents/' + target.document_id && failCurrentRead) throw new Error('SIMULATION current snapshot unavailable')
  if (url === '/api/documents/' + target.document_id) return envelope({ ...currentSnapshot, revision: head, issue_watermark: issue })
  const version = url.split('/revisions/')[1]
  if (version && versions.has(version)) return envelope(versions.get(version).snapshot)
  if (url.endsWith('/body')) {
    const saved = versions.get(config.params.document_revision)
    return envelope({ document_revision: saved.snapshot.document_revision, body_sha256: saved.snapshot.body.sha256, nodes: saved.body.nodes, fields: [], evidence_links: [], fact_usages: [], coverage: { selection_sha256: 'a'.repeat(64), delivered_sha256: 'b'.repeat(64), next_cursor: null, complete: true } })
  }
  if (url.startsWith('/api/operations/')) return envelope({ operation_id: url.split('/').at(-1), resource_id: annotation.annotation_id, status: 'COMPLETED', status_url: url }, 200, null)
  if (url === '/api/operation-lookups') return envelope({ lookup_status: lookup, operation: lookup === 'FOUND' ? { operation_id: receipt?.operation_id ?? randomUUID(), resource_id: annotation.annotation_id, status: 'COMPLETED', status_url: '/api/operations/' + (receipt?.operation_id ?? randomUUID()) } : null }, 200, null)
  if (url.endsWith('/reanchors')) {
    const previous = threadRevision, previousHead = head, previousAnchor = anchors.at(-1), previousPointer = String(anchors.length)
    threadRevision = String(BigInt(threadRevision) + 1n); head = String(BigInt(head) + 1n); issue = String(BigInt(issue) + 1n)
    const anchor = { ...origin, anchor_id: randomUUID(), previous_anchor_id: previousAnchor.anchor_id, document_revision: body.document_revision, selection: body.selection, selection_sha256: digest(body.selection), selection_artifact_id: randomUUID(), annotation_revision: threadRevision, actor_id: f.session.user.user_id, created_at: '2026-10-18T06:01:00Z', reason: body.reason }
    anchors.push(anchor); lifecycle.push({ kind: 'REANCHOR', previous_revision: previous, revision: threadRevision, previous_status: status, status: 'OPEN' }); status = 'OPEN'
    const data = { schema_version: '1.0.0', document_id: target.document_id, document_revision: body.document_revision, annotation_id: annotation.annotation_id, revision: threadRevision, status, previous_revision: previous, document_head_revision: head, previous_document_revision: previousHead, issue_watermark: issue, evidence_watermark: '1', operation_id: randomUUID(), previous_anchor_id: previousAnchor.anchor_id, previous_anchor_revision: previousPointer, anchor_revision: String(anchors.length), effective_anchor: anchor, effective_anchor_sha256: digest(anchor), invalidated_review_ids: [] }
    return envelope({ ...data, receipt_sha256: digest(data) }, 201)
  }
  if (url.endsWith('/anchor-reconciliations')) {
    assert.equal(config.headers['X-CSRF-TOKEN'], 'SIMULATION-CSRF')
    if (gate) await gate
    if (!receipt) {
      const previous = threadRevision, previousStatus = status, previousHead = head
      threadRevision = String(BigInt(threadRevision) + 1n); head = String(BigInt(head) + 1n); issue = String(BigInt(issue) + 1n); status = 'REANCHOR_REQUIRED'
      lifecycle.push({ kind: 'INVALIDATE', previous_revision: previous, revision: threadRevision, previous_status: previousStatus, status })
      const data = { schema_version: '1.0.0', document_id: target.document_id, document_revision: target.document_revision, annotation_id: annotation.annotation_id, operation_id: randomUUID(), previous_revision: previous, revision: threadRevision, previous_status: previousStatus, status, previous_document_revision: previousHead, document_head_revision: head, anchor_id: origin.anchor_id, anchor_revision: '1', issue_watermark: issue, evidence_watermark: '1', transition_id: randomUUID(), actor_id: f.session.user.user_id, created_at: '2026-10-18T06:00:00Z', reason: body.reason, invalidated_review_ids: [], origin_anchor_sha256: digest(origin) }
      receipt = { ...data, receipt_sha256: digest(data) }
    }
    if (uncertain) throw new Error('SIMULATION lost receipt')
    return envelope(receipt, 201)
  }
  throw new Error('Unexpected mock ' + url)
}
const rejects = (fn, code) => assert.rejects(fn, failure => failure.code === code)
reset()
for (const initial of ['OPEN', 'RESOLVED']) {
  reset(initial); const req = input(), pending = await api.prepareReconciliation(() => f.session, target, targetBody, row(), req, signal)
  for (const secret of [req.reason, annotation.content, f.session.csrf.token, 'segments']) assert.equal([...stored.values()].join('').includes(secret), false)
  const result = await api.submitReconciliation(() => f.session, pending, target, targetBody, req, signal)
  assert.equal(result.previous_status, initial); assert.equal(result.actor_id, f.session.user.user_id); assert.notEqual(result.actor_id, annotation.actor_id)
  await api.settleReconciliation(() => f.session, pending, target, targetBody, req, result, signal); assert.equal(stored.size, 0); assert.deepEqual(anchors, [origin])
  const reanchorInput = { ...req, expected_revision: threadRevision, expected_document_revision: head, selection: targetSelection }
  const normal = await api.prepareReanchor(() => f.session, { ...target, revision: head, issue_watermark: issue }, targetBody, row(), reanchorInput, signal)
  assert.equal(normal.operation, 'RV-06-REANCHOR-1')
  const saved = { ...target, revision: head, issue_watermark: issue }
  const relocated = await api.submitReanchor(() => f.session, normal, saved, targetBody, reanchorInput, signal)
  await api.settleReanchor(() => f.session, normal, saved, targetBody, reanchorInput, relocated, signal)
  assert.equal(status, 'OPEN'); assert.deepEqual(anchors[0], origin); assert.equal(stored.size, 0)
}
pass('legacy OPEN and RESOLVED explicitly reconcile with current actor/time, unchanged original Selection/anchor and then enter existing manual reanchor')
reset(); const req = input()
for (const [patch, code] of [[{ human_confirmed: false }, 'SCHEMA_INVALID'], [{ reason: '' }, 'SCHEMA_INVALID'], [{ expected_revision: '2' }, 'ACTION_STALE']]) await rejects(() => api.prepareReconciliation(() => f.session, target, targetBody, row(), { ...req, ...patch }, signal), code)
await rejects(() => api.prepareReconciliation(() => ({ ...f.session, action_summary: [] }), target, targetBody, row(), req, signal), 'ACCESS_DENIED')
await rejects(() => api.prepareReconciliation(() => f.session, { ...target, signature_ids: [randomUUID()] }, targetBody, row(), req, signal), 'ACTION_STALE')
assert.equal(api.legacyAnchorEligible({ ...row(), effective_anchor: { ...origin, anchor_id: randomUUID() } }, target, history()), false)
assert.equal(api.legacyAnchorEligible(row(), f.snapshot, history()), false)
pass('reason, human confirmation, review authority, unsigned target, exact CAS and true origin-only stale eligibility are mandatory')
const pending = await api.prepareReconciliation(() => f.session, target, targetBody, row(), req, signal)
await rejects(() => api.prepareAnnotation(() => f.session, target, targetBody, { document_revision: target.document_revision, selection: targetSelection, content: 'SIMULATION', expected_revision: '4' }, null, signal), 'IDEMPOTENCY_CONFLICT')
await rejects(() => api.prepareReconciliation(() => f.session, target, targetBody, row(), { ...req, reason: 'replacement' }, signal), 'IDEMPOTENCY_CONFLICT')
uncertain = true; await assert.rejects(() => api.submitReconciliation(() => f.session, pending, target, targetBody, req, signal), /lost receipt/); uncertain = false
const before = calls.filter(call => call.url.endsWith('/anchor-reconciliations')).length
await rejects(() => api.submitReconciliation(() => f.session, pending, target, targetBody, { ...req, reason: 'wrong' }, signal), 'IDEMPOTENCY_CONFLICT')
assert.equal(calls.filter(call => call.url.endsWith('/anchor-reconciliations')).length, before)
assert.equal(api.readPendingReconciliation(f.session, target.document_id).key, pending.key)
pass('pending metadata excludes PHI; mutual exclusion and changed reason preserve original operation without replacement POST')
for (const patch of [{ actor_id: annotation.actor_id }, { anchor_revision: '2' }, { status: 'OPEN' }, { evidence_watermark: '2' }, { previous_status: 'RESOLVED' }]) {
  const { receipt_sha256, ...content } = receipt; const bad = { ...content, ...patch }; await assert.rejects(() => api.verifyReconciliationReceipt(pending, req, { ...bad, receipt_sha256: digest(bad) }, receipt.revision, signal))
}
pass('receipt rejects changed actor/status/original pointer/evidence watermarks even with recomputed digest')
// A later reanchor, reply, decision and saved V3 cannot replace the old receipt or strand settlement on V2.
const newAnchor = { ...origin, anchor_id: randomUUID(), previous_anchor_id: origin.anchor_id, document_revision: target.document_revision, selection: targetSelection, selection_sha256: digest(targetSelection), selection_artifact_id: randomUUID(), annotation_revision: '3', actor_id: f.session.user.user_id, created_at: '2026-10-18T06:01:00Z', reason: 'SIMULATION manual relocation' }
anchors.push(newAnchor); lifecycle.push({ kind: 'REANCHOR', previous_revision: '2', revision: '3', previous_status: 'REANCHOR_REQUIRED', status: 'OPEN' }, { kind: 'REPLY', previous_revision: '3', revision: '4', previous_status: 'OPEN', status: 'OPEN' }, { kind: 'DECISION', previous_revision: '4', revision: '5', previous_status: 'OPEN', status: 'RESOLVED' }); threadRevision = '5'; status = 'RESOLVED'; head = '9'; issue = '5'
const thirdBody = structuredClone(targetBody); thirdBody.nodes[1].content.text += ' SAVED V3'
currentSnapshot = { ...target, document_revision: randomUUID(), revision: head, issue_watermark: issue, body: { ...target.body, artifact_id: randomUUID(), sha256: digest(thirdBody) } }; currentBody = thirdBody; versions.set(currentSnapshot.document_revision, { snapshot: currentSnapshot, body: thirdBody })
const replay = await api.submitReconciliation(() => f.session, pending, target, targetBody, req, signal)
assert.equal(calls.at(-1).config.headers['Idempotency-Key'], pending.key); assert.equal(replay.revision, '2')
failCurrentRead = true; await assert.rejects(() => api.settleReconciliation(() => f.session, pending, target, targetBody, req, replay, signal), /current snapshot unavailable/); assert.equal(api.readPendingReconciliation(f.session, target.document_id).key, pending.key); failCurrentRead = false
const settled = await api.settleReconciliation(() => f.session, pending, target, targetBody, req, replay, signal)
assert.equal(settled.document_revision, currentSnapshot.document_revision); assert.equal(settled.items[0].annotation.status, 'RESOLVED'); assert.equal(stored.size, 0)
pass('late original receipt survives later reanchor/reply/decision and actual changed saved body; settlement independently reads current V3')
const renderer = vue.createRenderer({ patchProp() {}, insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}), createComment: () => ({}), setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null })
function mount(props) { const app = renderer.createApp({ ...api.ReanchorPanel, render: () => null }, props); app.config.warnHandler = () => {}; const vm = app.mount({}); return { app, state: vm.$.setupState, props: vm.$.props } }
async function idle(state) { for (let i = 0; i < 500 && state.busy; i++) await new Promise(resolve => setTimeout(resolve, 1)); assert.equal(state.busy, false); assert.equal(state.error, '') }
reset(); const props = { session: f.session, snapshot: target, baseline: targetBody, selection: targetSelection, active: true, dirty: false, busy: false, history: false, isCurrent: true }
for (const patch of [{ dirty: true }, { history: true }, { isCurrent: false }, { snapshot: { ...target, signature_ids: [randomUUID()] } }, { session: { ...f.session, action_summary: f.session.action_summary.filter(x => x.action !== 'review.decide') } }]) {
  const panel = mount({ ...props, ...patch }); await idle(panel.state); panel.state.reconciliationReason = req.reason; panel.state.reconciliationConfirmed = true
  const count = calls.length; await panel.state.reconcile(); assert.equal(calls.length, count); assert.equal(panel.state.canReconcile, false); panel.app.unmount()
}
pass('real SFC setup blocks dirty/historical/noncurrent/signed/unauthorized fresh reconciliation')
const panel = mount(props); await idle(panel.state); assert.equal(panel.state.selected.annotation.status, 'OPEN'); assert.equal(panel.state.canReconcile, true)
panel.state.reason = 'SIMULATION preserve separate manual reanchor draft'; panel.state.chooseSelection(); panel.state.reconciliationReason = req.reason; panel.state.reconciliationConfirmed = true
let release; gate = new Promise(resolve => { release = resolve }); const writing = panel.state.reconcile()
for (let i = 0; i < 500 && !calls.at(-1)?.url.endsWith('/anchor-reconciliations'); i++) await new Promise(resolve => setTimeout(resolve, 1))
assert.ok(panel.state.reconciliationPending); assert.ok(panel.state.reconciliationRequest); assert.equal(panel.state.selected.annotation.status, 'OPEN'); assert.equal(globalThis.annotationUser.medicalWriting, true)
const originalKey = panel.state.reconciliationPending.key; panel.state.stopWaiting(); release(); gate = null; await writing
assert.equal(panel.state.reconciliationPending.key, originalKey); assert.equal(globalThis.annotationUser.medicalWriting, false); assert.equal(panel.state.reason, 'SIMULATION preserve separate manual reanchor draft'); panel.app.unmount()
const recovery = mount({ ...props, history: true, isCurrent: false }); await idle(recovery.state)
lookup = 'NOT_FOUND'; const posts = calls.filter(call => call.url.endsWith('/anchor-reconciliations')).length; await recovery.state.recoverReconciliation(); assert.equal(calls.filter(call => call.url.endsWith('/anchor-reconciliations')).length, posts)
lookup = 'FOUND'; await recovery.state.recoverReconciliation(); assert.equal(recovery.state.error, ''); assert.equal(recovery.state.reconciliationPending, null); assert.equal(stored.size, 0); assert.equal(recovery.state.selected.annotation.status, 'REANCHOR_REQUIRED'); recovery.app.unmount()
pass('SFC never relabels before server success; stop releases lock, keeps separate drafts/exact key; historical recovery requires FOUND and refreshes list/history')
reset('RESOLVED'); let refreshed = 0, successful
successful = mount({ ...props, refreshDocument: async () => { refreshed++; successful.props.snapshot = { ...target, revision: head, issue_watermark: issue }; await vue.nextTick() } })
await idle(successful.state); successful.state.reconciliationReason = req.reason; successful.state.reconciliationConfirmed = true
await successful.state.reconcile(); await idle(successful.state); assert.equal(successful.state.error, ''); assert.equal(refreshed, 1); assert.equal(successful.state.selected.annotation.status, 'REANCHOR_REQUIRED')
assert.equal(successful.state.anchorHistory.revision, threadRevision); assert.equal(successful.state.listing.revision, successful.props.snapshot.revision); assert.equal(successful.state.canReanchor, true); successful.app.unmount()
pass('successful real SFC reconciliation refreshes parent snapshot, complete list and history before enabling manual reanchor')
reset(); const abortPending = await api.prepareReconciliation(() => f.session, target, targetBody, row(), input(), signal)
let releaseDigest, enteredDigest; const entered = new Promise(resolve => { enteredDigest = resolve })
delayDigest = { started: enteredDigest, promise: new Promise(resolve => { releaseDigest = resolve }) }
const controller = new AbortController(), beforeAbort = calls.length
const aborting = api.submitReconciliation(() => f.session, abortPending, target, targetBody, input(), controller.signal)
await entered; controller.abort(); releaseDigest(); await assert.rejects(aborting); assert.equal(calls.length, beforeAbort); assert.equal(api.readPendingReconciliation(f.session, target.document_id).key, abortPending.key)
let liveSession = f.session, releaseContext, enterContext
const enteredContext = new Promise(resolve => { enterContext = resolve })
delayDigest = { started: enterContext, promise: new Promise(resolve => { releaseContext = resolve }) }
const switching = api.submitReconciliation(() => liveSession, abortPending, target, targetBody, input(), signal)
await enteredContext; liveSession = { ...f.session, current_context: { ...f.session.current_context, context_revision: '2' } }; releaseContext()
await rejects(() => switching, 'CONTEXT_CHANGED'); assert.equal(calls.length, beforeAbort)
pass('abort or identity-context change during async digest prevents dispatch and retains exact pending key')
await writeFile(path.join(output, 'result.json'), JSON.stringify({ result: 'PASS', scope: 'PURE_DOMAIN_MOCK_TRANSPORT_DOM_FREE_SFC', actual_http_verified: false, browser: 'NOT_RUN', checks, mocked_transport_calls: calls.length }, null, 2) + '\n')
console.log(JSON.stringify({ result: 'PASS', checks: checks.length, mocked_transport_calls: calls.length, actual_http_verified: false, browser: 'NOT_RUN' }))

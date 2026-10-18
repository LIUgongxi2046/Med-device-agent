// Isolated real-SFC setup and strict adapters. No browser/live writes or business acceptance.
import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { webcrypto } from 'node:crypto';
import { createRequire } from 'node:module';
import { build } from '../../web/node_modules/esbuild/lib/main.js';
import { parse, compileScript, compileTemplate } from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js';
const root = path.resolve(import.meta.dirname, '../..'), out = path.join(root, '.implementation-work/simulation-rule-management'), dep = createRequire(path.join(root, 'web/package.json'));
await mkdir(out, { recursive: true });
await writeFile(path.join(out, 'entry.ts'), `export * from '../../web/src/api/medical/simulation-rule-versions'; export * from '../../web/src/api/medical/simulation-rule-ui'; export {default as Create} from '../../web/src/views/medical/simulation-rule-create.vue';export {default as Manage} from '../../web/src/views/medical/simulation-rule-management.vue';`);
await build({ entryPoints: [path.join(out, 'entry.ts')], bundle: true, platform: 'node', format: 'cjs', outfile: path.join(out, 'suite.cjs'), plugins: [{ name: 'offline-real-setup', setup(b) {
                b.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'mock' }));
                b.onResolve({ filter: /^@\/store\/modules\/user$/ }, () => ({ path: 'user', namespace: 'mock' }));
                b.onResolve({ filter: /^vue-router$/ }, () => ({ path: 'router', namespace: 'mock' }));
                b.onLoad({ filter: /.*/, namespace: 'mock' }, a => ({ contents: a.path === 'request' ? 'export default {get:(...a)=>globalThis.localTransport("GET",...a),post:(...a)=>globalThis.localTransport("POST",...a)}' : a.path === 'user' ? 'export default ()=>globalThis.localUser' : 'export const onBeforeRouteLeave=()=>{};export const onBeforeRouteUpdate=()=>{};export const useRoute=()=>globalThis.localRoute;export const useRouter=()=>({push:async value=>globalThis.destinations.push(value)})', loader: 'js' }));
                b.onResolve({ filter: /^@\// }, a => ({ path: path.join(root, 'web/src', a.path.slice(2)) + (path.extname(a.path) ? '' : '.ts') }));
                b.onResolve({ filter: /^[a-z]/ }, a => ({ path: dep.resolve(a.path), external: true }));
                b.onResolve({ filter: /\?raw$/ }, a => ({ path: path.resolve(a.resolveDir, a.path.replace(/\?raw$/, '')), namespace: 'raw' }));
                b.onLoad({ filter: /.*/, namespace: 'raw' }, async (a) => ({ contents: await readFile(a.path, 'utf8'), loader: 'text' }));
                b.onLoad({ filter: /\.vue$/ }, async (a) => {
                    if (!['simulation-rule-create.vue', 'simulation-rule-management.vue'].includes(path.basename(a.path)))
                        return { contents: 'export default {}', loader: 'js' };
                    const { descriptor, errors } = parse(await readFile(a.path, 'utf8'), { filename: a.path });
                    assert.deepEqual(errors, []);
                    const template = compileTemplate({ source: descriptor.template.content, filename: a.path, id: a.path, compilerOptions: { expressionPlugins: ['typescript'] } });
                    assert.deepEqual(template.errors, []);
                    return { contents: compileScript(descriptor, { id: a.path }).content, loader: 'ts', resolveDir: path.dirname(a.path) };
                });
            } }] });
const api = (await import(pathToFileURL(path.join(out, 'suite.cjs')))).default, vue = dep('vue'), id = () => webcrypto.randomUUID(), clone = v => JSON.parse(JSON.stringify(v));
Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true });
globalThis.window = new EventTarget();
globalThis.document = new EventTarget();
document.hidden = false;
document.getElementById = () => null;
const storage = new Map();
globalThis.sessionStorage = { getItem: k => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v), removeItem: k => storage.delete(k) };
const tenant = id(), actor = id(), ruleId = id(), basis = { version_id: id(), content_sha256: 'a'.repeat(64) };
const session = { user: { user_id: actor }, current_context: { tenant_id: tenant, context_revision: '7', auth_epoch: '2', governance_epoch: '5' }, csrf: { header_name: 'X-CSRF-TOKEN', token: 'OFFLINE_TEST_ONLY' }, action_summary: ['rule.write', 'rule.read', 'rule.approve'].map(action => ({ action, project_id: null, resource_kind: 'ENTERPRISE', resource_id: tenant })) };
const body = { schema_version: '1.0.0', dependency_use: 'SIMULATION_EVALUATION', production_eligible: false, base_version: null, content_schema_ref: 'urn:medical:rule-expressions:1.0.0#/$defs/Content', reason: 'Document evaluation rule', content: { schema_version: '1.0.0', engine_revision: api.ruleEngine, expression: { schema_version: '1.0.0', engine_revision: api.ruleEngine, expression: "#attributes['risk'] == #constants['expected']", input_keys: ['risk'], constants: [{ key: 'expected', value: 3, basis_refs: [basis] }] }, basis_manifest: [basis] } };
const hash = await api.ruleDigest(body.content);
const stored = { tenant_id: tenant, rule_version: ruleId, status: 'DRAFT', revision: '1', content: body.content, content_sha256: hash, base_version: null, reason: body.reason, authority_grant_id: id(), authority_revision: '1', created_by: actor, created_at: '2026-10-18T00:00:00Z', dependency_refs: [basis], dependency_use: 'SIMULATION_EVALUATION', production_eligible: false, binding_sha256: await api.bindingDigest(tenant, ruleId, hash) };
const baseline = { schema_version: '1.0.0', rule_version: ruleId, content_sha256: hash, definition_manifest_sha256: await api.ruleContentManifestDigest(), dependency_refs: [basis], dependency_authorizations_sha256: 'c'.repeat(64), dependency_use: 'SIMULATION_EVALUATION', production_eligible: false, binding_sha256: stored.binding_sha256 };
const preview = { schema_version: '1.0.0', rule: stored, baseline_manifest: baseline, allowed_actions: ['rule.approve'] };
const renderer = vue.createRenderer({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), setText() { }, setElementText() { }, parentNode: () => null, nextSibling: () => null, insert() { }, remove() { }, patchProp() { } });
function mount(component) { const app = renderer.createApp({ ...component, render: () => null }); app.config.warnHandler = () => { }; const vm = app.mount({}); return { app, state: vm.$.setupState }; }
function mountEnabledManagement() { const panel = mount(api.Manage); panel.state.serverEnabled = true; return panel; }
function response(data, revision = null, status = 200) { const requestId = id(); return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': requestId }, data: { schema_version: '1.0.0', request_id: requestId, resource_revision: revision, data } }; }
let requests = [], failures = 0, handle = () => { throw Error('Unexpected request'); };
globalThis.localTransport = async (method, ...args) => { requests.push({ method, url: args[0], payload: method === 'POST' ? args[1] : null, config: method === 'POST' ? args[2] : args[1] }); return handle(method, ...args); };
globalThis.localUser = vue.reactive({ medicalSession: clone(session), medicalWriting: false, readMedical: async () => { throw Error('Unexpected identity refresh'); } });
globalThis.localRoute = vue.reactive({ fullPath: '/agents/simulation-rules', params: {}, query: {} });
globalThis.destinations = [];
window.addEventListener('medical-failure', () => { failures++; localUser.medicalSession = null; });
function reset() { storage.clear(); requests = []; failures = 0; localUser.medicalSession = clone(session); localUser.medicalWriting = false; }
function fill(panel) { Object.assign(panel.state.form, { base_mode: 'NEW', base_id: '', base_hash: '', expression: body.content.expression.expression, input_keys: ['risk'], basis_manifest: [clone(basis)], constants: [{ key: 'expected', value: '3', basis_refs: [clone(basis)] }], reason: body.reason }); panel.state.attested = true; }
async function settle() {
    for (let i = 0; i < 20; i++)
        await new Promise(r => setTimeout(r, 0));
}
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; }
assert.deepEqual(api.validateRuleCreate(body), body);
// Real setup rejects ordinary editable errors before transport, storage or global invalidation.
let panel = mount(api.Create);
for (const mutate of [s => s.form.reason = ' bad reason', s => s.form.constants[0].value = '{broken', s => s.form.input_keys.push('risk'), s => s.form.basis_manifest = [], s => s.form.constants.push(clone(s.form.constants[0])), s => { s.form.base_mode = 'REVISION'; s.form.base_id = 'bad-id'; s.form.base_hash = 'bad-hash'; }]) {
    fill(panel);
    mutate(panel.state);
    panel.state.attested = true;
    await panel.state.submit();
    assert.equal(requests.length, 0);
    assert.equal(storage.size, 0);
    assert.equal(failures, 0);
    assert.ok(localUser.medicalSession);
    assert.equal(localUser.medicalWriting, false);
}
fill(panel);
panel.state.form.base_id = 'bad-id';
await panel.state.loadBase();
assert.equal(requests.length, 0);
assert.equal(failures, 0);
panel.app.unmount();
reset();
// Strict valid read verifies full immutable content, enterprise identity and SIM binding digest.
handle = () => response({ schema_version: '1.0.0', rule: stored, allowed_actions: [] }, '1');
assert.equal((await api.readRule(session, ruleId, new AbortController().signal)).rule.rule_version, ruleId);
handle = () => response({ schema_version: '1.0.0', rule: { ...stored, binding_sha256: 'f'.repeat(64) }, allowed_actions: [] }, '1');
await assert.rejects(() => api.readRule(session, ruleId, new AbortController().signal), e => e.code === 'CONTENT_MISMATCH');
assert.equal(failures, 1);
reset();
panel = mount(api.Create);
fill(panel);
// Unknown POST preserves original key, and refresh cannot silently replay it.
handle = () => { throw Error('Unknown transport outcome'); };
await panel.state.submit();
assert.match(panel.state.writeError, /Unknown/);
assert.equal(storage.size, 1);
const raw = [...storage.values()][0];
assert.equal(JSON.parse(raw).operation, 'SIM-RULE-CREATE');
assert.equal(localUser.medicalWriting, false);
assert.equal(panel.state.mayReplay, false);
assert.equal(requests[0].config.signal, undefined);
const count = requests.length;
await panel.state.submit();
assert.equal(requests.length, count);
await assert.rejects(() => api.prepareRuleWrite(session, { ...body, reason: 'Changed reason' }), e => e.code === 'IDEMPOTENCY_CONFLICT');
assert.equal([...storage.values()][0], raw);
panel.app.unmount();
// The dispatched write is not aborted; stale completion cannot refresh identity, release a newer write, or clear its key.
reset();
panel = mount(api.Create);
fill(panel);
const pendingResponse = deferred();
handle = () => pendingResponse.promise;
const work = panel.state.submit();
await settle();
assert.equal(requests.length, 1);
const saved = [...storage.values()][0];
panel.app.unmount();
localUser.medicalWriting = false;
localUser.medicalWriting = true;
pendingResponse.resolve(response({ schema_version: '1.0.0', operation_id: id(), rule: stored, allowed_actions: [] }, '1', 201));
await work;
assert.equal(localUser.medicalWriting, true);
assert.equal([...storage.values()][0], saved);
assert.equal(requests.length, 1);
reset();
localRoute.params = {};
localRoute.fullPath = '/agents/simulation-rules';
let management = mountEnabledManagement();
management.state.entry = 'bad-id';
await management.state.open();
assert.equal(destinations.length, 0);
assert.equal(failures, 0);
management.app.unmount();
// Actual approval setup has no default decision, and a refreshed changed CAS/baseline prevents any write.
reset();
localRoute.params = { ruleVersion: ruleId };
localRoute.fullPath = '/agents/simulation-rules/' + ruleId;
handle = () => response(preview, '1');
management = mountEnabledManagement();
await settle();
assert.equal(management.state.decision, '');
assert.ok(management.state.preview);
management.state.reason = 'bad reason ';
management.state.decision = 'APPROVE';
management.state.attested = true;
const before = requests.length;
await management.state.submit();
assert.equal(requests.length, before);
assert.equal(failures, 0);
management.state.reason = 'Reviewed evidence';
management.state.attested = true;
handle = () => response({ ...preview, rule: { ...stored, revision: '2' } }, '2');
await management.state.submit();
assert.equal(requests.at(-1).method, 'GET');
assert.equal(storage.size, 0);
assert.equal(management.state.attested, false);
assert.match(management.state.notice, /变化/);
assert.equal(localUser.medicalWriting, false);
management.app.unmount();
// Ownership can be invalidated after unmount; a prior lock cannot release a newer operation.
reset();
const release = api.acquireSimulationRuleWrite(localUser);
localUser.medicalWriting = false;
localUser.medicalWriting = true;
release();
assert.equal(localUser.medicalWriting, true);
console.log('PASS: SIM rule strict adapter and actual create/management setups; local errors, binding digest, CAS refresh, unknown-key retention, stale unmount and write ownership. Offline synthetic fixtures only.');
// Successful creation is acknowledged only after exact-key lookup + operation verification.
reset();
localRoute.params = {};
localRoute.fullPath = '/agents/simulation-rules/new';
panel = mount(api.Create);
fill(panel);
const creationOperation = { operation_id: id(), resource_id: ruleId, status: 'COMPLETED' };
creationOperation.status_url = '/api/operations/' + creationOperation.operation_id;
handle = (method, url, payload) => {
    if (url === '/api/simulation-rule-versions')
        return response({ schema_version: '1.0.0', operation_id: creationOperation.operation_id, rule: stored, allowed_actions: [] }, '1', 201);
    if (url === '/api/operation-lookups') {
        assert.equal(payload.operation_id, 'SIM-RULE-CREATE');
        assert.equal(payload.idempotency_key, JSON.parse([...storage.values()][0]).key);
        return response({ lookup_status: 'FOUND', operation: creationOperation });
    }
    if (url === creationOperation.status_url)
        return response(creationOperation);
    throw Error('Unexpected creation path ' + url);
};
await panel.state.submit();
assert.equal(storage.size, 0);
assert.equal(panel.state.completedRule.rule_version, ruleId);
assert.equal(panel.state.creationReceipt.rule.status, 'DRAFT');
assert.equal(failures, 0);
panel.app.unmount();
// Recover after page refresh from the original key; current status may have legitimately advanced.
reset();
const original = await api.prepareRuleWrite(session, body);
panel = mount(api.Create);
handle = (method, url, payload) => {
    if (url === '/api/operation-lookups') {
        assert.equal(payload.idempotency_key, original.key);
        return response({ lookup_status: 'FOUND', operation: creationOperation });
    }
    if (url === creationOperation.status_url)
        return response(creationOperation);
    if (url === '/api/simulation-rule-versions/' + ruleId)
        return response({ schema_version: '1.0.0', rule: { ...stored, status: 'APPROVED', revision: '2' }, allowed_actions: [] }, '2');
    throw Error('Unexpected recovery path ' + url);
};
await panel.state.lookup();
await panel.state.recover();
assert.equal(storage.size, 0);
assert.equal(panel.state.completedRule.status, 'APPROVED');
assert.equal(panel.state.creationReceipt, null);
panel.app.unmount();
// A lookup for a different operation must not clear the original pending identity.
reset();
const wrongPending = await api.prepareRuleWrite(session, body);
handle = () => response({ lookup_status: 'FOUND', operation: { ...creationOperation, operation_id: id() } });
await assert.rejects(() => api.confirmRuleOperation(session, wrongPending, creationOperation.operation_id, new AbortController().signal));
assert.equal(storage.size, 1);
assert.equal(failures, 1);
// Explicit approval uses original CAS, refreshes identity, and independently rereads resulting state.
reset();
localRoute.params = { ruleVersion: ruleId };
localRoute.fullPath = '/agents/simulation-rules/' + ruleId;
const approvalOperation = { ...creationOperation, operation_id: id() };
approvalOperation.status_url = '/api/operations/' + approvalOperation.operation_id;
const freshSession = { ...clone(session), current_context: { ...session.current_context, context_revision: '8', governance_epoch: '6' } };
const approved = { ...stored, status: 'APPROVED', revision: '2' };
let approvedWrite = false;
localUser.readMedical = async () => { localUser.medicalSession = clone(freshSession); return localUser.medicalSession; };
handle = (method, url, payload) => {
    if (url.endsWith('/approval-baseline'))
        return response({ ...preview, rule: approvedWrite ? approved : stored }, approvedWrite ? '2' : '1');
    if (url.endsWith('/approval-decisions')) {
        assert.equal(payload.expected_revision, '1');
        assert.equal(payload.decision, 'APPROVE');
        assert.deepEqual(payload.baseline_manifest, baseline);
        approvedWrite = true;
        return response({ schema_version: '1.0.0', operation_id: approvalOperation.operation_id, decision_id: id(), rule_version: ruleId, decision: 'APPROVE', status: 'APPROVED', revision: '2', content_sha256: hash, actor_id: actor, decided_at: '2026-10-18T00:00:00Z', current_context: freshSession.current_context, allowed_actions: [], dependency_use: 'SIMULATION_EVALUATION', production_eligible: false, binding_sha256: stored.binding_sha256 }, '2');
    }
    if (url === '/api/operation-lookups') {
        assert.equal(payload.operation_id, 'SIM-RULE-APPROVE');
        return response({ lookup_status: 'FOUND', operation: approvalOperation });
    }
    if (url === approvalOperation.status_url)
        return response(approvalOperation);
    throw Error('Unexpected approval ' + url);
};
management = mountEnabledManagement();
await settle();
await management.state.openDecision();
assert.equal(management.state.dialog, true);
management.state.decision = 'APPROVE';
management.state.reason = 'Reviewed all fixed evidence';
management.state.attested = true;
await management.state.submit();
assert.equal(failures, 0);
assert.equal(storage.size, 0);
assert.equal(management.state.stored.status, 'APPROVED');
assert.equal(management.state.receipt.decision, 'APPROVE');
assert.equal(localUser.medicalWriting, false);
management.app.unmount();
console.log('PASS: real creation receipt, original-key recovery after refresh, mismatched operation rejection, explicit approval CAS/context refresh and reread.');
// Two overlapping base reads across an identity generation: old finally cannot clear the new busy flag.
reset();
localRoute.params = {};
panel = mount(api.Create);
panel.state.form.base_mode = 'REVISION';
panel.state.form.base_id = ruleId;
const firstBase = deferred(), secondBase = deferred();
let baseCalls = 0;
handle = () => ++baseCalls === 1 ? firstBase.promise : secondBase.promise;
const oldBaseWork = panel.state.loadBase();
await settle();
localUser.medicalSession = { ...clone(session), current_context: { ...session.current_context, context_revision: '9' } };
panel.state.form.base_mode = 'REVISION';
panel.state.form.base_id = ruleId;
const newBaseWork = panel.state.loadBase();
await settle();
assert.equal(panel.state.checking, true);
firstBase.resolve(response({ schema_version: '1.0.0', rule: approved, allowed_actions: [] }, '2'));
await oldBaseWork;
assert.equal(panel.state.checking, true);
assert.equal(panel.state.form.base_hash, '');
secondBase.resolve(response({ schema_version: '1.0.0', rule: approved, allowed_actions: [] }, '2'));
await newBaseWork;
assert.equal(panel.state.checking, false);
assert.equal(panel.state.form.base_hash, hash);
panel.app.unmount();
// Context cancellation stops pre-dispatch writes, while preserving the original pending metadata.
reset();
const abortPending = await api.prepareRuleWrite(session, body), controller = new AbortController();
controller.abort();
await assert.rejects(() => api.submitRuleCreate(session, abortPending, body, controller.signal), e => e.name === 'AbortError');
assert.equal(requests.length, 0);
assert.equal(storage.size, 1);
assert.equal(failures, 0);
console.log('PASS: actual overlapping base-read generations and pre-dispatch abort retain ownership and original request identity.');
reset();
handle = () => response({ ...preview, baseline_manifest: { ...baseline, definition_manifest_sha256: 'f'.repeat(64) } }, '1');
await assert.rejects(() => api.readRulePreview(session, ruleId, new AbortController().signal), e => e.code === 'CONTENT_MISMATCH');
assert.equal(failures, 1);
console.log('PASS: approval content manifest is pinned to the same three published schema digests as the backend.');

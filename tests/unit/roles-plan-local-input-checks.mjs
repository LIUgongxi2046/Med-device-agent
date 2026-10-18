// Isolated real-SFC setup and strict adapters. No browser/live writes or business acceptance.
import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { webcrypto } from 'node:crypto';
import { createRequire } from 'node:module';
import { build } from '../../web/node_modules/esbuild/lib/main.js';
import { parse, compileScript, compileTemplate } from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js';
const root = path.resolve(import.meta.dirname, '../..'), out = path.join(root, '.implementation-work/roles-plan-local-input'), dep = createRequire(path.join(root, 'web/package.json'));
await mkdir(out, { recursive: true });
await writeFile(path.join(out, 'entry.ts'), `export * from '../../web/src/api/medical/plan-input-issues';export * from '../../web/src/api/medical/task-plans';export {default as PlanEditor} from '../../web/src/views/medical/task-plan-editor.vue';export {default as Roles} from '../../web/src/views/medical/enterprise-roles.vue';export {default as PlanReturn} from '../../web/src/views/medical/task-plan-return.vue';`);
await build({ entryPoints: [path.join(out, 'entry.ts')], bundle: true, platform: 'node', format: 'cjs', outfile: path.join(out, 'suite.cjs'), plugins: [{ name: 'offline-real-setup', setup(b) {
                b.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'mock' }));
                b.onResolve({ filter: /^@\/store\/modules\/user$/ }, () => ({ path: 'user', namespace: 'mock' }));
                b.onResolve({ filter: /^vue-router$/ }, () => ({ path: 'router', namespace: 'mock' }));
                b.onLoad({ filter: /.*/, namespace: 'mock' }, a => ({ contents: a.path === 'request' ? 'export default {get:(...a)=>globalThis.localTransport("GET",...a),post:(...a)=>globalThis.localTransport("POST",...a)}' : a.path === 'user' ? 'export default ()=>globalThis.localUser' : 'export const onBeforeRouteLeave=()=>{};export const useRoute=()=>({fullPath:"/enterprise/roles"});export const useRouter=()=>({push:async()=>{}})', loader: 'js' }));
                b.onResolve({ filter: /^@\// }, a => ({ path: path.join(root, 'web/src', a.path.slice(2)) + (path.extname(a.path) ? '' : '.ts') }));
                b.onResolve({ filter: /^[a-z]/ }, a => ({ path: dep.resolve(a.path), external: true }));
                b.onResolve({ filter: /\?raw$/ }, a => ({ path: path.resolve(a.resolveDir, a.path.replace(/\?raw$/, '')), namespace: 'raw' }));
                b.onLoad({ filter: /.*/, namespace: 'raw' }, async (a) => ({ contents: await readFile(a.path, 'utf8'), loader: 'text' }));
                b.onLoad({ filter: /\.vue$/ }, async (a) => { if (!['task-plan-editor.vue', 'enterprise-roles.vue', 'task-plan-return.vue'].includes(path.basename(a.path)))
                    return { contents: 'export default {}', loader: 'js' }; const { descriptor, errors } = parse(await readFile(a.path, 'utf8'), { filename: a.path }); assert.deepEqual(errors, []); const template = compileTemplate({ source: descriptor.template.content, filename: a.path, id: a.path, compilerOptions: { expressionPlugins: ['typescript'] } }); assert.deepEqual(template.errors, []); return { contents: compileScript(descriptor, { id: a.path }).content, loader: 'ts', resolveDir: path.dirname(a.path) }; });
            } }] });
const api = (await import(pathToFileURL(path.join(out, 'suite.cjs')))).default, vue = dep('vue'), id = () => webcrypto.randomUUID(), clone = v => structuredClone(v);
Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true });
globalThis.window = new EventTarget();
globalThis.document = new EventTarget();
document.hidden = false;
document.getElementById = () => null;
const storage = new Map();
globalThis.sessionStorage = { getItem: k => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v), removeItem: k => storage.delete(k) };
const tenant = id(), actor = id(), project = id(), task = id(), node1 = id(), node2 = id(), section1 = id(), section2 = id();
const session = { user: { user_id: actor }, current_context: { tenant_id: tenant, context_revision: '7', auth_epoch: '2', governance_epoch: '5' }, csrf: { header_name: 'X-CSRF-TOKEN', token: 'ISOLATED_TEST' }, action_summary: ['task.read', 'plan.write', 'plan.confirm', 'role.manage'].map(action => ({ action, project_id: null, resource_kind: 'ENTERPRISE', resource_id: tenant })) };
const current = { revision: '3', detail: { task_id: task, project_id: project, task_revision: id(), status: 'PLAN_PENDING' } };
const content = { template_version: id(), profile_revision: id(), requirement_node_ids: [node1, node2], pending_condition_ids: [], sections: [{ section_id: section1, requirement_node_ids: [node1], goal: 'Chapter one', owner_id: null, method: 'HUMAN_PROVIDED', depends_on_section_ids: [], completion_rule_ids: [] }, { section_id: section2, requirement_node_ids: [node2], goal: 'Chapter two', owner_id: null, method: 'HUMAN_PROVIDED', depends_on_section_ids: [section1], completion_rule_ids: [] }], questions: [{ question_key: 'question-one', section_id: section1, question: 'Question one', requirement_ids: [node1], completion_rule_ids: [], depends_on_question_keys: [] }, { question_key: 'question-two', section_id: section2, question: 'Question two', requirement_ids: [node2], completion_rule_ids: [], depends_on_question_keys: ['question-one'] }] };
const body = { schema_version: '1.0.0', expected_revision: '3', base_version: null, schema_ref: api.planContentReference, content, reason: 'Change plan' };
assert.deepEqual(api.planInputIssues(body), []);
assert.deepEqual(api.validatePlanInput(body), body);
const renderer = vue.createRenderer({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), setText() { }, setElementText() { }, parentNode: () => null, nextSibling: () => null, insert() { }, remove() { }, patchProp() { } });
function mount(component, props = {}) { const app = renderer.createApp({ ...component, render: () => null }, props); app.config.warnHandler = () => { }; const vm = app.mount({}); return { app, state: vm.$.setupState, props: vm.$.props }; }
function response(data, revision = null, status = 200) { const requestId = id(); return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': requestId }, data: { schema_version: '1.0.0', request_id: requestId, resource_revision: revision, data } }; }
let requests = [], failures = 0, handle = () => { throw Error('Unexpected request'); };
globalThis.localTransport = async (method, ...a) => { requests.push({ method, url: a[0], body: method === 'POST' ? a[1] : null }); return handle(method, ...a); };
globalThis.localUser = vue.reactive({ medicalSession: clone(session), medicalWriting: false });
window.addEventListener('medical-failure', () => { failures++; localUser.medicalSession = null; });
handle = () => response({ schema_version: '1.0.0', roles: [], next_after: null });
let rolePanel = mount(api.Roles);
await vue.nextTick();
await new Promise(r => setTimeout(r, 0));
rolePanel.state.selected = { role_id: id(), version_id: id(), name: 'Preserved role' };
rolePanel.state.historyVersion = 'not-a-uuid';
const before = requests.length;
await rolePanel.state.history();
assert.equal(requests.length, before);
assert.equal(failures, 0);
assert.ok(localUser.medicalSession);
assert.equal(rolePanel.state.selected.name, 'Preserved role');
assert.equal(rolePanel.state.historyVersion, 'not-a-uuid');
assert.match(rolePanel.state.historyError, /历史岗位版本/);
// Valid user ID reaches the original strict response parser; malformed response still invalidates.
rolePanel.state.historyVersion = id();
handle = () => response({ broken: true }, '1');
await assert.rejects(() => rolePanel.state.history());
assert.equal(failures, 1);
assert.equal(localUser.medicalSession, null);
rolePanel.app.unmount();
localUser.medicalSession = clone(session);
failures = 0;
requests = [];
handle = () => response(null, '3');
const panel = mount(api.PlanEditor, { session: clone(session), current: clone(current), active: true });
for (let n = 0; n < 50 && panel.state.loading; n++)
    await new Promise(r => setTimeout(r, 1));
assert.equal(panel.state.loaded, true);
function fill() { panel.state.basis = { template_version: content.template_version, profile_revision: content.profile_revision, completion_rule_ids: [], conditions: [] }; panel.state.content = clone(content); panel.state.reason = body.reason; }
const cases = [
    ['reason', s => s.reason = ' bad reason '], ['control reason', s => s.reason = 'bad\nreason'], ['NFC reason', s => s.reason = 'e\u0301'],
    ['goal', s => s.content.sections[0].goal = ' bad goal'], ['question text', s => s.content.questions[0].question = 'bad\nquestion'],
    ['duplicate key', s => s.content.questions[1].question_key = s.content.questions[0].question_key],
    ['deleted chapter', s => { s.content.sections.splice(0, 1); s.content.sections[0].requirement_node_ids = [node1, node2]; s.content.questions = []; }],
    ['unknown question chapter', s => s.content.questions[0].section_id = id()], ['question outside chapter', s => s.content.questions[0].requirement_ids = [node2]],
    ['question self dependency', s => s.content.questions[0].depends_on_question_keys = ['question-one']], ['deleted question', s => s.content.questions.splice(0, 1)],
    ['chapter self dependency', s => s.content.sections[0].depends_on_section_ids = [section1]]
];
for (const [name, mutate] of cases) {
    fill();
    mutate(panel.state);
    const original = JSON.stringify({ content: panel.state.content, reason: panel.state.reason }), count = requests.length;
    assert.ok(panel.state.issues.length > 0, name);
    await panel.state.submit();
    assert.equal(JSON.stringify({ content: panel.state.content, reason: panel.state.reason }), original, name);
    assert.equal(requests.length, count, name);
    assert.equal(storage.size, 0, name);
    assert.equal(failures, 0, name);
    assert.ok(localUser.medicalSession, name);
    assert.equal(localUser.medicalWriting, false, name);
}
// Valid draft enters strict prepare/save; a bad server response still fails closed and keeps the original key.
fill();
assert.equal(panel.state.issues.length, 0);
handle = () => response({ broken: true }, '4', 201);
await assert.rejects(() => panel.state.submit());
assert.equal(requests.at(-1).method, 'POST');
assert.equal(requests.at(-1).url, '/api/tasks/' + task + '/plan-revisions');
assert.equal(failures, 1);
assert.equal(storage.size, 1);
const savedRaw = [...storage.values()][0], pending = JSON.parse(savedRaw);
assert.equal(pending.operation, 'TK-03');
assert.equal(localUser.medicalSession, null);
panel.app.unmount();
// Original pending integrity is not converted into a local draft issue or a replacement key.
await assert.rejects(() => api.preparePlan(session, current, { ...body, reason: 'Other valid reason' }), e => e.code === 'IDEMPOTENCY_CONFLICT');
assert.equal([...storage.values()][0], savedRaw);
// Direct handler invocation cannot bypass the existing return-reason button preflight.
localUser.medicalSession = clone(session);
localUser.medicalWriting = false;
const returning = mount(api.PlanReturn, { session: clone(session), current: clone(current), active: false });
returning.props.active = true;
await vue.nextTick();
returning.state.basis = { plan_revision: id(), expected_revision: '3' };
returning.state.reason = ' bad reason ';
const count = requests.length, oldFailures = failures;
await returning.state.submit();
assert.equal(requests.length, count);
assert.equal(failures, oldFailures);
assert.equal(returning.state.reason, ' bad reason ');
returning.app.unmount();
console.log('PASS: actual roles/plan/return setups preserve drafts/session on local input errors with zero writes; valid paths retain strict response and original-key validation. Offline synthetic fixtures only.');

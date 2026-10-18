// Isolated real-SFC setup and strict adapters. No browser/live writes or business acceptance.
import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { webcrypto } from 'node:crypto';
import { createRequire } from 'node:module';
import { build } from '../../web/node_modules/esbuild/lib/main.js';
import { parse, compileScript, compileTemplate } from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js';
const root = path.resolve(import.meta.dirname, '../..'), out = path.join(root, '.implementation-work/simulation-feature-availability'), dep = createRequire(path.join(root, 'web/package.json'));
await mkdir(out, { recursive: true });
await writeFile(path.join(out, 'entry.ts'), `export * from '../../web/src/api/medical/simulation-feature-availability';export {default as Availability} from '../../web/src/views/medical/simulation-feature-availability.vue';`);
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
                    if (!['simulation-feature-availability.vue'].includes(path.basename(a.path)))
                        return { contents: 'export default {}', loader: 'js' };
                    const { descriptor, errors } = parse(await readFile(a.path, 'utf8'), { filename: a.path });
                    assert.deepEqual(errors, []);
                    const script = compileScript(descriptor, { id: a.path });
                    const template = compileTemplate({ source: descriptor.template.content, filename: a.path, id: a.path, compilerOptions: { bindingMetadata: script.bindings, expressionPlugins: ['typescript'] } });
                    assert.deepEqual(template.errors, []);
                    return { contents: script.content.replace('export default', 'const Component =') + '\n' + template.code.replace('export function render', 'function render') + '\nComponent.render=render;export default Component;', loader: 'ts', resolveDir: path.dirname(a.path) };
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
const tenant = id(), actor = id();
const session = { user: { user_id: actor }, current_context: { tenant_id: tenant, context_revision: '7', auth_epoch: '2', governance_epoch: '5' }, csrf: { header_name: 'X-CSRF-TOKEN', token: 'OFFLINE' }, action_summary: [] };
const disabled = { schema_version: '1.0.0', current_context: session.current_context, features: { input_artifact_registration: false, rule_workflow: false, evaluation_preparation: false } };
function response(value, revision = '7', status = 200) { const requestId = id(); return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': requestId }, data: { schema_version: '1.0.0', request_id: requestId, resource_revision: revision, data: value } }; }
let requests = [], failures = 0, handle = () => response(disabled);
globalThis.localTransport = async (method, url, config) => { requests.push({ method, url, config }); return handle(method, url, config); };
globalThis.localUser = vue.reactive({ medicalSession: clone(session), medicalWriting: false });
window.addEventListener('medical-failure', () => { failures++; localUser.medicalSession = null; });
assert.deepEqual(await api.readSimulationFeatureAvailability(session, new AbortController().signal), disabled);
assert.equal(requests[0].method, 'GET');
assert.equal(requests[0].url, '/api/simulation-feature-availability');
assert.deepEqual(session.action_summary, []);
for (const invalid of [{ ...disabled, permissions: ['rule.approve'] }, { ...disabled, features: { ...disabled.features, rule_workflow: 'true' } }, { ...disabled, features: { rule_workflow: true } }, { ...disabled, current_context: { ...session.current_context, tenant_id: id() } }]) {
    handle = () => response(invalid);
    await assert.rejects(() => api.readSimulationFeatureAvailability(session, new AbortController().signal));
    assert.equal(localUser.medicalSession, null);
}
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; }
async function settle() { for (let i = 0; i < 12; i++)
    await new Promise(r => setTimeout(r, 0)); }
const renderer = vue.createRenderer({ createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: text => ({ text }), setText(n, t) { n.text = t; }, setElementText(n, t) { n.text = t; }, parentNode: n => n.parent ?? null, nextSibling: n => n.parent?.children[n.parent.children.indexOf(n) + 1] ?? null, insert(n, p, anchor) { if (n.parent)
        n.parent.children = n.parent.children.filter(c => c !== n); n.parent = p; p.children ??= []; const at = anchor ? p.children.indexOf(anchor) : -1; if (at >= 0)
        p.children.splice(at, 0, n);
    else
        p.children.push(n); }, remove(n) { if (n?.parent)
        n.parent.children = n.parent.children.filter(c => c !== n); }, patchProp() { } });
let mounted = 0, unmounted = 0;
const Probe = { setup() { mounted++; vue.onBeforeUnmount(() => unmounted++); return () => vue.h('span', 'protected workflow'); } };
function mount(feature = 'rule_workflow') { const instance = vue.shallowRef(), features = vue.ref(feature), events = []; const app = renderer.createApp({ render: () => vue.h(api.Availability, { ref: instance, feature: features.value, onChange: value => events.push(value) }, { default: () => vue.h(Probe) }) }); app.config.warnHandler = () => { }; app.mount({ children: [] }); return { app, features, events, state: () => instance.value.$.setupState }; }
// The real compiled template does not even mount the protected slot while disabled.
localUser.medicalSession = clone(session);
failures = 0;
requests = [];
handle = () => response(disabled);
let panel = mount();
await settle();
assert.equal(mounted, 0);
assert.equal(panel.state().enabled, false);
assert.equal(panel.state().checked, true);
assert.equal(panel.events.at(-1), false);
assert.equal(failures, 0);
assert.ok(localUser.medicalSession);
// A true feature mounts its slot, while leaving all grants unchanged (none exist here).
handle = () => response({ ...disabled, features: { ...disabled.features, rule_workflow: true } });
await panel.state().refresh();
await settle();
assert.equal(mounted, 1);
assert.deepEqual(localUser.medicalSession.action_summary, []);
assert.equal(panel.events.at(-1), true);
// Hidden/identity transitions invalidate availability immediately. Old responses/finally cannot replace new state.
document.hidden = true;
document.dispatchEvent(new Event('visibilitychange'));
await vue.nextTick();
assert.equal(unmounted, 1);
assert.equal(panel.state().enabled, false);
document.hidden = false;
const first = deferred(), second = deferred();
handle = () => first.promise;
const old = panel.state().refresh();
handle = () => second.promise;
localUser.medicalSession = { ...clone(session), current_context: { ...session.current_context, context_revision: '8' } };
handle = () => second.promise;
// Session watcher already started with first transport; start a fresh owned read explicitly.
const current = panel.state().refresh();
assert.equal(panel.state().loading, true);
first.resolve(response({ ...disabled, features: { ...disabled.features, rule_workflow: true } }));
await old;
await settle();
assert.equal(panel.state().enabled, false);
assert.equal(panel.state().loading, true);
second.resolve(response({ ...disabled, current_context: localUser.medicalSession.current_context }, '8'));
await current;
assert.equal(panel.state().enabled, false);
assert.equal(panel.state().loading, false);
panel.app.unmount();
// No session never calls the endpoint; availability errors never produce a fallback success.
localUser.medicalSession = null;
requests = [];
panel = mount();
await settle();
assert.equal(requests.length, 0);
assert.equal(panel.state().enabled, false);
panel.app.unmount();
localUser.medicalSession = clone(session);
handle = () => { throw Error('Unavailable transport'); };
panel = mount('evaluation_preparation');
await settle();
assert.equal(panel.state().enabled, false);
assert.match(panel.state().error, /Unavailable/);
assert.equal(panel.events.at(-1), false);
panel.app.unmount();
console.log('PASS: authenticated feature API strict closed schema/context, actual compiled slot gating, no implicit grants, stale generation/busy, identity and unknown availability behavior. Offline only.');

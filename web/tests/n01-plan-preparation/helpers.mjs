import { build } from 'esbuild'
import { readFile } from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { webcrypto } from 'node:crypto'
import vm from 'node:vm'
const require = createRequire(import.meta.url)
export const webRoot = process.env.N01_WEB_ROOT || process.env.CAPABILITY_WEB_ROOT || fileURLToPath(new URL('../..', import.meta.url))
export const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0')
export const plain = value => JSON.parse(JSON.stringify(value))
export function storage() { const map = new Map(); return { map, getItem: key => map.get(key) ?? null, setItem: (key,value) => map.set(key,value), removeItem: key => map.delete(key), clear: () => map.clear() } }
export function session() { return { schema_version: '1.0.0', user: { user_id: id(1), username: null, display_name: null, platform_role: 'USER' }, memberships: [],
 current_context: { tenant_id: id(2), context_revision: '1', auth_epoch: '1', governance_epoch: '1' }, csrf: { header_name: 'X-CSRF-TOKEN', token: 'csrf-token' },
 action_summary: [{ action: 'capability.configure', project_id: null, resource_kind: 'ENTERPRISE', resource_id: id(2) }, { action: 'artifact.read', project_id: null, resource_kind: 'ARTIFACT', resource_id: id(3) }],
 invitation_entry: { method: 'POST', path_template: '/api/member-invitations/{id}/acceptances' } } }
export function response(data, revision = '1', status = 200) { return { status, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': id(90) }, data: { schema_version: '1.0.0', request_id: id(90), resource_revision: revision, data } } }
const bundles = new Map()
export async function loadModule(name, overrides = {}) {
 if (!bundles.has(name)) {
  const result = await build({ entryPoints: [path.join(webRoot, 'src/api/medical', name + '.ts')], tsconfig: path.join(webRoot, 'tsconfig.medical.json'), bundle: true, write: false, platform: 'node', format: 'cjs', logLevel: 'silent', external: ['jsonc-parser', 'lossless-json', 'json-custom-numbers', 'vue', '@vueuse/core'],
   plugins: [{ name: 'actual-contracts-and-request-boundary', setup(builder) {
    builder.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'test' }))
    builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({ contents: 'export default globalThis.__request', loader: 'js' }))
    builder.onResolve({ filter: /\.yaml\?raw$/ }, args => ({ path: path.resolve(args.resolveDir, args.path) }))
    builder.onLoad({ filter: /\.yaml\?raw$/ }, async args => ({ contents: await readFile(args.path.replace(/\?raw$/, ''), 'utf8'), loader: 'text' }))
   } }] })
  bundles.set(name, result.outputFiles[0].text)
 }
 const module = { exports: {} }, events = [], store = overrides.sessionStorage ?? storage()
 const context = vm.createContext({ module, exports: module.exports, require, console, crypto: webcrypto, TextEncoder, TextDecoder, AbortController, structuredClone,
  sessionStorage: store, window: { dispatchEvent: event => events.push(event) }, CustomEvent: class { constructor(type, details) { this.type = type; this.detail = details.detail } }, __request: overrides.request ?? {}, ...overrides.globals })
 new vm.Script(bundles.get(name), { filename: name + '.cjs' }).runInContext(context)
 return { api: module.exports, store, events, context }
}
export function setupVue(name, overrides = {}) {
 const vue = require('vue'), ts = require('typescript'), { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
 const filename = path.join(webRoot, 'src/views/medical', name + '.vue'), source = readFileSync(filename, 'utf8'), { descriptor } = parse(source, { filename })
 const compiled = compileScript(descriptor, { id: name }), template = compileTemplate({ source: descriptor.template.content, filename, id: name, compilerOptions: { bindingMetadata: compiled.bindings } })
 if (template.errors.length) throw Error(template.errors.join('\n'))
 const code = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
 const exports = {}, scope = vue.effectScope(), cleanups = [], listeners = new Map(), doc = { hidden: false, addEventListener: (name,fn) => listeners.set(name,fn), removeEventListener: name => listeners.delete(name) }
 const user = overrides.user ?? vue.reactive({ medicalSession: null, medicalWriting: false }), route = overrides.route ?? vue.reactive({ params: {} }), routes = []
 let guard
 const dependencies = { '@/store/modules/user': { default: () => user }, 'vue-router': { useRouter: () => ({ push: async value => { routes.push(value) } }), useRoute: () => route, onBeforeRouteLeave: fn => { guard = fn }, onBeforeRouteUpdate: () => {} }, ...overrides.modules }
 vm.runInNewContext(code, { exports, require: target => target === 'vue' ? { ...vue, onBeforeUnmount: fn => cleanups.push(fn) } : dependencies[target] ?? {}, console, AbortController, TextEncoder,
  ...overrides.globals, document: Object.assign(doc,overrides.doc ?? {}), window: { addEventListener: (name,fn) => listeners.set(name,fn), removeEventListener: name => listeners.delete(name) }, HTMLInputElement: class {} })
 const state = scope.run(() => exports.default.setup(overrides.props ?? {}, { expose() {} }))
 return { state, user, route, doc, listeners, routes, guard: (...args) => guard?.(...args), stop: () => { for (const fn of cleanups) fn(); scope.stop() } }
}
export const tick = async () => { for (let i=0;i<8;i++) await Promise.resolve() }

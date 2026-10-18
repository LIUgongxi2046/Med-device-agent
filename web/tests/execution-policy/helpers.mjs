import { build } from 'esbuild'
import { readFile } from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { webcrypto, createHash } from 'node:crypto'
import vm from 'node:vm'
const require = createRequire(import.meta.url)
export const webRoot = fileURLToPath(new URL('../..', import.meta.url))
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
 const context = vm.createContext({ module, exports: module.exports, require, console, crypto: webcrypto, TextEncoder, TextDecoder, AbortController, structuredClone, URL, Intl,
  sessionStorage: store, window: { dispatchEvent: event => events.push(event) }, CustomEvent: class { constructor(type, details) { this.type = type; this.detail = details.detail } }, __request: overrides.request ?? {}, ...overrides.globals })
 new vm.Script(bundles.get(name), { filename: name + '.cjs' }).runInContext(context)
 return { api: module.exports, store, events, context }
}
export function requestBody(domain, kind = 'PROMPT') { const schema = domain.capabilitySchemas[0]; return { schema_version: '1.0.0', execution_mode: 'PRODUCTION', project_id: null, kind,
 source_artifact: { version_id: id(3), content_sha256: 'a'.repeat(64) }, input_schema_ref: { schema_ref: schema.schema_ref, schema_sha256: schema.schema_sha256 }, output_schema_ref: { schema_ref: schema.schema_ref, schema_sha256: schema.schema_sha256 },
 tool_scope: kind === 'ROLE' ? { schema_version: '1.0.0', permissions: [{ scope: { tenant_id: id(2), project_id: null, resource_kind: 'ARTIFACT', resource_id: id(3) }, action: 'artifact.read', purpose: 'AGENT_PUBLICATION' }] } : [], reason: '核查真实来源并登记固定声明' } }
export function digest(domain, value) { return createHash('sha256').update(domain.capabilityCanonical(value)).digest('hex') }
export function record(domain, body = requestBody(domain)) { const content = { schema_version: '1.0.0', tenant_id: id(2), config_id: id(5), version_id: id(6), created_by: id(1), created_at: '2026-10-18T19:00:00Z', request: body }; return { schema_version: '1.0.0', record_id: id(4), resource_id: id(6), kind: domain.capabilityRecordKind, content_sha256: digest(domain, content), status: 'DRAFT', production_eligible: false, content } }
export function receipt(domain, body = requestBody(domain), view = record(domain,body)) { return { schema_version: '1.0.0', operation_id: id(7), resource_id: view.record_id, kind: domain.capabilityRecordKind, content_sha256: view.content_sha256, request_body_sha256: digest(domain, body) } }
export function setupVue(name, overrides = {}) {
 const vue = require('vue'), ts = require('typescript'), { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
 const filename = path.join(webRoot, 'src/views/medical', name + '.vue'), source = readFileSync(filename, 'utf8'), { descriptor } = parse(source, { filename })
 const compiled = compileScript(descriptor, { id: name }), template = compileTemplate({ source: descriptor.template.content, filename, id: name, compilerOptions: { bindingMetadata: compiled.bindings } })
 if (template.errors.length) throw Error(template.errors.join('\n'))
 const code = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
 const exports = {}, scope = vue.effectScope(), cleanups = [], listeners = new Map(), doc = { hidden: false, addEventListener: (name,fn) => listeners.set(name,fn), removeEventListener: name => listeners.delete(name) }
 const user = overrides.user ?? vue.reactive({ medicalSession: null, medicalWriting: false }), route = overrides.route ?? vue.reactive({ params: {} }), routes = []
 let guard
 const dependencies = { '@/store/modules/user': { default: () => user }, 'vue-router': { useRouter: () => ({ push: async value => { routes.push(value) } }), useRoute: () => route, onBeforeRouteLeave: fn => { guard = fn }, onBeforeRouteUpdate: fn => { guard = fn } }, ...overrides.modules }
 const runtimeRequire = target => target === 'vue' ? { ...vue, onBeforeUnmount: fn => cleanups.push(fn) } : dependencies[target] ?? {}
 const lifecycleCode=ts.transpileModule(readFileSync(path.join(webRoot,'src/api/medical/use-execution-policy-page.ts'),'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
 const lifecycleExports={};vm.runInNewContext(lifecycleCode,{exports:lifecycleExports,require:runtimeRequire,console,AbortController,document:doc})
 dependencies['@/api/medical/use-execution-policy-page']=lifecycleExports
 vm.runInNewContext(code, { exports, require: runtimeRequire, console, AbortController, TextEncoder,
  document: doc, window: { addEventListener: (name,fn) => listeners.set(name,fn), removeEventListener: name => listeners.delete(name) }, HTMLInputElement: class {} })
 const state = scope.run(() => exports.default.setup({}, { expose() {} }))
 return { state, user, route, doc, listeners, routes, guard: (...args) => guard?.(...args), stop: () => { for (const fn of cleanups) fn(); scope.stop() } }
}
export const tick = async () => { for (let i=0;i<8;i++) await Promise.resolve() }

export function policySession() { const s=session();s.action_summary.push(...['agent.configure','agent.publish'].map(action=>({action,project_id:null,resource_kind:'ENTERPRISE',resource_id:s.current_context.tenant_id})));return s }
export const hash = value => createHash('sha256').update(require('json-canonicalize').canonicalizeEx(value,{strictUndefined:true})).digest('hex')
export function policyBody(){return {schema_version:'1.0.0',reason:'登记真实 N00 范围',definition:{schema_version:'1.0.0',project_id:null,business_purpose:'DOCUMENT_BODY',profile_evidence_purpose:'TASK_EXECUTION',allowed_classifications:['INTERNAL'],source_selection:{mode:'FIXED_SOURCE_VERSIONS',sources:[]},retrieval_profiles:[],network_policy:{https_origins:[],external_requests_allowed:false},worker:{service_uri:'spiffe://medical.test/worker',audience:'medical-worker'},bindings:[{node_kind:'N00',role_version:{version_id:id(6),content_sha256:'a'.repeat(64)},model_version:null,tokenizer_version:null,tokenizer_artifact:null,generation_parameters:{},actions:[{resource_kind:'TASK',target_selector:'TASK',action:'task.read',purpose:'TASK_EXECUTION',fixed_version:null,artifact_kinds:[]}]}],locale:'zh-CN',timezone:'UTC',environment:'test',region:'test-region',training_allowed:false,valid_from:'2026-10-18T00:00:00Z',valid_until:null}}}
export function policyView(body=policyBody()){const d=plain(body.definition);d.source_selection={...d.source_selection,version:{version_id:id(20),content_sha256:hash(d.source_selection)}};d.network_policy={...d.network_policy,version:{version_id:id(21),content_sha256:hash(d.network_policy)}};const fixed={schema_version:'1.0.0',tenant_id:id(2),policy_id:id(10),version_id:id(11),author_record_id:id(12),definition:d,definition_sha256:hash(d),created_by:id(1),created_at:'2026-10-18T10:00:00Z',classification:'INTERNAL'};return {schema_version:'1.0.0',fixed,status:'DRAFT',revision:'1',latest_decision:null,missing_input_reasons:['POLICY_DRAFT']}}
export function decisionBody(v=policyView(),decision='APPROVE'){return {schema_version:'1.0.0',policy_version:{version_id:v.fixed.version_id,content_sha256:v.fixed.definition_sha256},expected_revision:v.revision,decision,reason:'独立核查固定策略'}}
export function decidedView(v=policyView(),body=decisionBody(v),actor=id(30),record=id(13)){const d={schema_version:'1.0.0',tenant_id:v.fixed.tenant_id,project_id:v.fixed.definition.project_id,policy_id:v.fixed.policy_id,version_id:v.fixed.version_id,definition_sha256:v.fixed.definition_sha256,decision_record_id:record,previous_status:v.status,previous_revision:v.revision,decision:body.decision,status:({APPROVE:'APPROVED',REJECT:'REJECTED',DISABLE:'DISABLED'})[body.decision],revision:String(BigInt(v.revision)+1n),actor_id:actor,decided_at:'2026-10-18T11:00:00Z',authority_grant_id:id(99),authority_revision:'1',reason:body.reason};return {...plain(v),status:d.status,revision:d.revision,latest_decision:d,missing_input_reasons:[]}}
export function policyReceipt(body=policyBody(),view=policyView(body)){return {schema_version:'1.0.0',operation_id:id(15),resource_id:'definition' in body?view.fixed.author_record_id:view.latest_decision.decision_record_id,kind:'definition' in body?'EXECUTION_POLICY_AUTHOR':'EXECUTION_POLICY_DECISION',content_sha256:hash('definition' in body?view.fixed:view.latest_decision),request_body_sha256:hash(body)}}
export const policyAvailability = s=>({schema_version:'1.0.0',current_context:s.current_context,features:{policy_authoring:true,model_policy_approval:false}})

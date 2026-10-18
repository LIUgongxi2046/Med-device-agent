// Isolated schema/API checks with synthetic fixtures and an in-memory transport.
// These are not live-backend, browser, visual or end-to-end checks.
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { webcrypto } from 'node:crypto'
import { createRequire } from 'node:module'
import { build } from '../../web/node_modules/esbuild/lib/main.js'
import { parse, compileScript, compileTemplate } from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js'

const root = path.resolve(import.meta.dirname, '../..')
const output = path.join(root, '.implementation-work/agent-ui-unit')
const dependency = createRequire(path.join(root, 'web/package.json'))
await mkdir(output, { recursive: true })
await build({ entryPoints: [path.join(root, 'web/src/api/medical/configuration-authoring.ts')], bundle: true, platform: 'node', format: 'cjs',
  outfile: path.join(output, 'authoring.cjs'), plugins: [
    { name: 'isolated-transport', setup(bundler) {
      bundler.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'transport', namespace: 'synthetic' }))
      bundler.onLoad({ filter: /.*/, namespace: 'synthetic' }, () => ({ contents: `export default {
        get: (url, config) => globalThis.__authorTransport('GET', url, null, config),
        post: (url, body, config) => globalThis.__authorTransport('POST', url, body, config)
      }`, loader: 'js' }))
      bundler.onResolve({ filter: /^[a-z]/ }, args => ({ path: dependency.resolve(args.path), external: true }))
      bundler.onResolve({ filter: /\?raw$/ }, args => ({ path: path.resolve(args.resolveDir, args.path.replace(/\?raw$/, '')), namespace: 'raw' }))
      bundler.onLoad({ filter: /.*/, namespace: 'raw' }, async args => ({ contents: await readFile(args.path, 'utf8'), loader: 'text' }))
    } }
  ] })
Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
globalThis.window = new EventTarget()
const storage = new Map()
globalThis.sessionStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
const api = (await import(pathToFileURL(path.join(output, 'authoring.cjs')))).default
const id = () => crypto.randomUUID()
const tenant=id(),actor=id();const context={tenant_id:tenant,context_revision:'7',auth_epoch:'2',governance_epoch:'5'}
const session={user:{user_id:actor},current_context:context,csrf:{header_name:'X-CSRF-TOKEN',token:'TEST_ONLY'},action_summary:['capability.configure','agent.configure','configuration.read'].map(action=>({action,project_id:null,resource_kind:'ENTERPRISE',resource_id:tenant}))}
const body={schema_version:'1.0.0',execution_mode:'SIMULATION',project_id:null,kind:'ROLE',source_artifact:{version_id:id(),content_sha256:'a'.repeat(64)},input_schema_ref:{schema_ref:api.publishedSchemas[0].schema_ref,schema_sha256:api.publishedSchemas[0].schema_sha256},output_schema_ref:{schema_ref:api.publishedSchemas[0].schema_ref,schema_sha256:api.publishedSchemas[0].schema_sha256},tool_scope:[]}
assert.equal(api.authorAllowed(null,'agent.configure'),false)
assert.equal(api.authorAllowed({...session,action_summary:[{action:'agent.configure',project_id:null,resource_kind:'CONFIGURATION',resource_id:id()}]},'agent.configure',null),false)
api.validateAuthorRequest('Capability',body)
assert.throws(()=>api.validateAuthorRequest('Capability',{...body,tool_scope:['write']}))
assert.throws(()=>api.validateAuthorRequest('Capability',{...body,input_schema_ref:{schema_ref:'https://untrusted.invalid',schema_sha256:'a'.repeat(64)}}))
const signal=new AbortController().signal;const pending=await api.prepareAuthor(session,'Capability',body,signal)
assert.equal((await api.prepareAuthor(session,'Capability',body,signal)).key,pending.key)
assert.equal(storage.size,1);assert.ok(![...storage.values()][0].includes('source_artifact'))
await assert.rejects(()=>api.prepareAuthor(session,'Capability',{...body,kind:'PROMPT'},signal),e=>e.code==='IDEMPOTENCY_CONFLICT')
const limits=Object.fromEntries(api.limitFields.map(f=>[f.key,f.seconds?10:f.key==='currency'?'USD':'1']));limits.heartbeat_interval_seconds=5;limits.max_total_tokens='2';Object.assign(limits,{paid_request_extra_retries:0,model_spawned_subagents:0,max_tool_calls:'0',max_cost_amount:'0'})
const agent={schema_version:'1.0.0',execution_mode:'SIMULATION',project_id:null,name:'Isolated test',role_version:{version_id:id(),content_sha256:'a'.repeat(64)},prompt_revision:{version_id:id(),content_sha256:'b'.repeat(64)},model_config_revision:{version_id:id(),content_sha256:'c'.repeat(64)},limits}
api.validateAuthorRequest('Agent',agent)
assert.throws(()=>api.validateAuthorRequest('Agent',{...agent,limits:{...limits,max_total_tokens:'1'}}))
assert.throws(()=>api.validateAuthorRequest('Agent',{...agent,limits:{...limits,max_cost_amount:'1'}}))
assert.throws(()=>api.validateAuthorRequest('Agent',{...agent,prompt_revision:agent.role_version}))
assert.throws(()=>api.validateAuthorRequest('Agent',{...agent,limits:{...limits,heartbeat_interval_seconds:10}}))
function response(data){const requestId=id();return {status:200,headers:{'cache-control':'no-store','x-schema-version':'1.0.0','x-request-id':requestId},data:{schema_version:'1.0.0',request_id:requestId,resource_revision:'1',data}}}
const {createHash}=await import('node:crypto');const canonical=dependency('json-canonicalize').canonicalize
const version=id(),definition={schema_version:'1.0.0',tenant_id:tenant,config_id:id(),version_id:version,request:body,created_by:actor,created_at:'2026-10-18T12:00:00Z'}
const view={definition,content_sha256:createHash('sha256').update(canonical(definition)).digest('hex'),status:'DRAFT',revision:'1',production_eligible:false}
let transportCalls=0;globalThis.__authorTransport=async(method,url,payload,config)=>{transportCalls++;assert.equal(method,'GET');assert.equal(url,'/api/capability-versions/'+version);assert.equal(config.headers['X-Context-Revision'],'7');return response(view)}
assert.equal((await api.readAuthored(session,'Capability',version,signal)).definition.version_id,version)
view.content_sha256='0'.repeat(64);await assert.rejects(()=>api.readAuthored(session,'Capability',version,signal),e=>e.code==='CONTENT_MISMATCH')
for(const file of ['agents.vue','configuration-authoring.vue']){const source=await readFile(path.join(root,'web/src/views/medical',file),'utf8');const {descriptor,errors}=parse(source);assert.deepEqual(errors,[]);compileScript(descriptor,{id:file});const result=compileTemplate({source:descriptor.template.content,filename:file,id:file,compilerOptions:{expressionPlugins:['typescript']}});assert.deepEqual(result.errors,[])}
console.log('PASS: agent management schema, exact-request metadata, identity/digest checks, permission boundaries and 2 Vue component compilation checks. Isolated tests only; no live business acceptance.')
// Verify submission uses the original key and CSRF, then requires operation and digest-bound reread.
view.content_sha256=createHash('sha256').update(canonical(definition)).digest('hex')
const operationId=id(),receipt={schema_version:'1.0.0',operation_id:operationId,tenant_id:tenant,project_id:null,config_id:definition.config_id,version_id:version,content_sha256:view.content_sha256,status:'DRAFT',revision:'1',production_eligible:false,current_context:{...context,context_revision:'8',governance_epoch:'6'}}
globalThis.__authorTransport=async(method,url,payload,config)=>{assert.equal(method,'POST');assert.equal(url,'/api/capability-versions');assert.deepEqual(payload,body);assert.equal(config.headers['Idempotency-Key'],pending.key);assert.equal(config.headers['X-CSRF-TOKEN'],'TEST_ONLY');return {...response(receipt),status:201}}
assert.equal((await api.submitAuthor(session,pending,body,signal)).operation_id,operationId)
const fresh={...session,current_context:receipt.current_context}
globalThis.__authorTransport=async(method,url)=>{assert.equal(method,'GET');if(url==='/api/operations/'+operationId){const r=response({operation_id:operationId,resource_id:version,status:'COMPLETED',status_url:'/api/operations/'+operationId});r.data.resource_revision=null;return r}assert.equal(url,'/api/capability-versions/'+version);return response(view)}
assert.equal((await api.recoverAuthor(fresh,pending,operationId,signal,receipt)).definition.version_id,version)
assert.equal(storage.size,0)
console.log('PASS: POST headers and receipt, completed operation + hash-bound reread, pending metadata cleared only after verification.')

// Actual authoring setup preflight: no global failure or session loss for ordinary incomplete input.
await build({entryPoints:[path.join(root,'web/src/views/medical/configuration-authoring.vue')],bundle:true,platform:'node',format:'cjs',outfile:path.join(output,'component.cjs'),plugins:[{name:'author-component',setup(b){
 b.onResolve({filter:/^@\/utils\/request$/},()=>({path:'request',namespace:'component-mock'}))
 b.onResolve({filter:/^@\/store\/modules\/user$/},()=>({path:'user',namespace:'component-mock'}))
 b.onResolve({filter:/^vue-router$/},()=>({path:'router',namespace:'component-mock'}))
 b.onLoad({filter:/.*/,namespace:'component-mock'},args=>({contents:args.path==='user'?'export default ()=>globalThis.__authorUser':args.path==='router'?'export const onBeforeRouteLeave=()=>{};export const useRouter=()=>({push:async()=>{}})':'export default {get:(...a)=>globalThis.__authorSetupTransport("GET",...a),post:(...a)=>globalThis.__authorSetupTransport("POST",...a)}',loader:'js'}))
 b.onResolve({filter:/^@\//},args=>({path:path.join(root,'web/src',args.path.slice(2))+(path.extname(args.path)?'':'.ts')}))
 b.onResolve({filter:/^[a-z]/},args=>({path:dependency.resolve(args.path),external:true}))
 b.onResolve({filter:/\?raw$/},args=>({path:path.resolve(args.resolveDir,args.path.replace(/\?raw$/,'')),namespace:'raw'}))
 b.onLoad({filter:/.*/,namespace:'raw'},async args=>({contents:await readFile(args.path,'utf8'),loader:'text'}))
 b.onLoad({filter:/\.vue$/},async args=>{const {descriptor}=parse(await readFile(args.path,'utf8'),{filename:args.path});return {contents:compileScript(descriptor,{id:'author-local-preflight'}).content,loader:'ts',resolveDir:path.dirname(args.path)}})
}}]})
const component=(await import(pathToFileURL(path.join(output,'component.cjs')))).default.default
const vue=dependency('vue');globalThis.document=new EventTarget();document.hidden=false
const renderer=vue.createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),setText(){},setElementText(){},parentNode:()=>null,nextSibling:()=>null,insert(){},remove(){},patchProp(){}})
globalThis.__authorUser=vue.reactive({medicalSession:structuredClone(session),medicalWriting:false})
let uiCalls=0,failures=0;globalThis.__authorSetupTransport=async()=>{uiCalls++;throw Error('No request expected for local preflight')}
const invalidate=()=>{failures++;__authorUser.medicalSession=null};window.addEventListener('medical-failure',invalidate)
const app=renderer.createApp({...component,render:()=>null});app.config.warnHandler=()=>{};const vm=app.mount({}),state=vm.$.setupState
state.attested=true;await state.submit();assert.match(state.error,/先核验专业角色/);assert.equal(failures,0);assert.equal(uiCalls,0);assert.ok(__authorUser.medicalSession)
state.kind='Capability';await state.submit();assert.match(state.error,/请选择来源成果/);assert.equal(failures,0)
state.capabilityIds.ROLE='not-a-version';await state.loadCapability('ROLE');assert.match(state.error,/有效的角色或提示词/);assert.equal(failures,0);assert.equal(uiCalls,0)
state.kind='Agent';await vue.nextTick();state.capabilities.ROLE={definition:{version_id:id(),request:{kind:'ROLE',project_id:null}},content_sha256:'a'.repeat(64)};state.capabilities.PROMPT={definition:{version_id:id(),request:{kind:'PROMPT',project_id:null}},content_sha256:'b'.repeat(64)}
const modelVersion=id();state.models=[{project_id:null,model_config_revision:modelVersion,content_sha256:'c'.repeat(64)}];state.modelId=modelVersion;await state.submit();assert.match(state.error,/整数秒数/);assert.equal(failures,0)
for(const field of api.limitFields)state.limits[field.key]=field.seconds?'10':field.key==='currency'?'USD':'1';state.limits.heartbeat_interval_seconds='5';state.limits.max_total_tokens='2';state.name='';await state.submit();assert.match(state.error,/配置字段缺失或格式/);assert.equal(failures,0)
state.name='Local preflight';state.limits.max_total_tokens='1';await state.submit();assert.match(state.error,/Token 上限之和/);assert.equal(failures,0);assert.equal(uiCalls,0);assert.ok(__authorUser.medicalSession);assert.equal(storage.size,0)
// Response/schema validation deliberately remains fail-closed, including the global session consequence.
assert.throws(()=>api.authorValidate('AgentView',{}));assert.equal(failures,1);assert.equal(__authorUser.medicalSession,null)
window.removeEventListener('medical-failure',invalidate);app.unmount()
console.log('PASS: actual authoring setup local missing fields, invalid entered ID, empty seconds/name and token limits preserve session and send no requests; strict response schema still dispatches failure.')

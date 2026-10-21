import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
const require = createRequire(new URL('../../package.json', import.meta.url))
const ts = require('typescript'), Vue = require('vue')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const Ajv = require('ajv/dist/2020.js').default
const ajv = new Ajv({strict:false}); require('ajv-formats')(ajv)
const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')
const base = 'https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml'
ajv.addSchema(require('yaml').parse(read('../../../backend-design/interfaces.schema.yaml')),base)
const source = read('../../src/api/medical/candidate-read.ts')
const panel = read('../../src/views/medical/candidate-read-panel.vue')
const transpile = s => ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
const id = n => `00000000-0000-0000-0000-${String(n).padStart(12,'0')}`
const session = () => ({user:{user_id:id(1)},current_context:{tenant_id:id(2),context_revision:'1',auth_epoch:'1',governance_epoch:'1'},action_summary:[{action:'candidate.read',project_id:id(3),resource_kind:'CANDIDATE',resource_id:id(4)}]})
const artifact = () => ({artifact_id:id(5),sha256:'a'.repeat(64),schema_ref:'fixed',coverage:{selection_sha256:'a'.repeat(64),delivered_sha256:'a'.repeat(64),complete:true,next_cursor:null}})
const candidate = () => ({candidate_id:id(4),revision:'1',status:'READY',base_document_revision:id(6),target_node_ids:[id(7)],content:artifact(),original_evidence:artifact(),new_evidence:artifact(),check_ids:[],input_sha256:'a'.repeat(64),snapshot_id:id(8),plan_revision:id(9),allowed_actions:['candidate.read'],adoption_id:null,adopted_document_revision:null})
const response = value => ({status:200,headers:{'cache-control':'no-store','x-schema-version':'1.0.0','x-request-id':'test','x-resource-revision':'1'},data:{schema_version:'1.0.0',request_id:'test',resource_revision:'1',data:value}})
function adapter(output) {
 const calls=[]
 const fail=(code)=>{throw Object.assign(Error(code),{code})}
 const validate=(ref,value)=>{if(!ajv.getSchema(ref)?.(value))fail('SCHEMA_INVALID');return value}
 const revision=value=>{if(!/^(0|[1-9][0-9]{0,19})$/.test(value)||BigInt(value)>18446744073709551615n)fail('SCHEMA_INVALID');return value}
 const protocol={fail,validate,revision,validContext:c=>{revision(c.context_revision);revision(c.auth_epoch);revision(c.governance_epoch)},envelope:r=>{
  if(r.headers['cache-control']!=='no-store'||r.headers['x-schema-version']!=='1.0.0'||r.headers['x-request-id']!==r.data.request_id)fail('SCHEMA_INVALID')
  if(r.status!==200)fail(r.data.code)
  return validate(base+'#/$defs/Envelope',r.data)
 }}
 const api={};vm.runInNewContext(transpile(source),{exports:api,require:p=>p==='@/utils/request'?{default:{get:async(...args)=>{calls.push(args);return typeof output==='function'?output():output}}}:protocol})
 return {...api,calls}
}
test('published closed CandidateView and GET fixed metadata only',async()=>{
 const api=adapter(response(candidate())),s=session(),signal=new AbortController().signal
 const value=await api.readCandidateMetadata(()=>s,id(3),id(4),signal)
 assert.equal(value.candidate_id,id(4));assert.equal(api.calls.length,1)
 assert.equal(api.calls[0][0],'/api/candidates/'+id(4));assert.equal(api.calls[0][1].headers['X-Schema-Version'],'1.0.0');assert.equal(api.calls[0][1].signal,signal)
 assert.match(read('../../src/api/medical/protocol.ts'),/'CandidateView'/)
})
test('read guard rejects absent/wrong candidate, project, action and context before transport',async()=>{
 for(const change of [s=>s.current_context=null,s=>s.action_summary=[],s=>s.action_summary[0].action='candidate.adopt',s=>s.action_summary[0].resource_id=id(77),s=>s.action_summary[0].project_id=id(77)]){
  const s=session();change(s);const api=adapter(response(candidate()))
  await assert.rejects(api.readCandidateMetadata(()=>s,id(3),id(4),new AbortController().signal),{code:'ACCESS_DENIED'});assert.equal(api.calls.length,0)
 }
 const api=adapter(response(candidate()));await assert.rejects(api.readCandidateMetadata(session,id(3),'invalid',new AbortController().signal),{code:'SCHEMA_INVALID'});assert.equal(api.calls.length,0)
})
test('rejects unknown properties, UUIDs, revisions, wrong id and transport headers',async()=>{
 for(const change of [r=>r.data.extra=true,r=>r.data.data.extra=true,r=>r.data.data.snapshot_id='bad',r=>r.data.data.target_node_ids.push(id(7)),r=>r.data.data.candidate_id=id(99),r=>r.data.data.revision='0',r=>r.data.data.revision='18446744073709551616',r=>r.data.resource_revision='2',r=>r.headers['x-resource-revision']='2',r=>r.headers['x-schema-version']='9']) {
  const raw=response(candidate());change(raw);const api=adapter(raw)
  await assert.rejects(api.readCandidateMetadata(session,id(3),id(4),new AbortController().signal))
 }
})
test('late responses are rejected after actor/context/permission changes or cancellation',async()=>{
 for(const change of [s=>s.user.user_id=id(88),s=>s.current_context.auth_epoch='2',s=>s.current_context.governance_epoch='2',s=>s.current_context.context_revision='2',s=>s.action_summary=[]]){
  let s=session();const api=adapter(()=>{change(s);return response(candidate())})
  await assert.rejects(api.readCandidateMetadata(()=>s,id(3),id(4),new AbortController().signal),{code:'CONTEXT_CHANGED'})
 }
 const c=new AbortController(),api=adapter(()=>{c.abort();return response(candidate())})
 await assert.rejects(api.readCandidateMetadata(session,id(3),id(4),c.signal),{name:'AbortError'})
 const before=new AbortController();before.abort();const a=adapter(null);await assert.rejects(a.readCandidateMetadata(session,id(3),id(4),before.signal));assert.equal(a.calls.length,0)
})
function mount(candidateId=null){
 const props=Vue.reactive({session:session(),projectId:id(3),candidateId,active:true}),calls=[]
 let resolve,reject;const pending=new Promise((r,j)=>{resolve=r;reject=j})
 const api=adapter(null),exports={}
 vm.runInNewContext(transpile(compileScript(parse(panel).descriptor,{id:'panel'}).content),{exports,require:p=>p==='vue'?Vue:{...api,readCandidateMetadata:async(...args)=>{calls.push(args);return pending}},AbortController,document:{hidden:false,addEventListener(){},removeEventListener(){}}})
 let state
 const renderer=Vue.createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),setText(){},setElementText(){},parentNode(){},nextSibling(){},patchProp(){},insert(){},remove(){}})
 const app=renderer.createApp({setup(){state=exports.default.setup(props,{expose(){}});return()=>null}});app.mount({})
 return {props,calls,resolve,reject,state,close:()=>app.unmount()}
}
const tick=async()=>{await Promise.resolve();await Vue.nextTick();await Promise.resolve()}
test('mounted panel absent reference never requests, contains no UUID input and compiles',async()=>{
 const h=mount();try{await h.state.load();assert.equal(h.calls.length,0);assert.equal(h.state.state.value,'UNAVAILABLE')}finally{h.close()}
 assert.doesNotMatch(panel,/<(?:el-)?input/);assert.deepEqual(compileTemplate({source:parse(panel).descriptor.template.content,filename:'panel.vue',id:'panel'}).errors,[])
})
test('mounted panel cancellation and changed permissions suppress late metadata',async()=>{
 for(const cancel of [h=>h.state.cancel(),h=>h.props.session.action_summary=[],h=>h.props.active=false]){
  const h=mount(id(4));try{const p=h.state.load();assert.equal(h.state.state.value,'LOADING');cancel(h);h.resolve(candidate());await p;await tick();assert.equal(h.state.value.value,null);assert.notEqual(h.state.state.value,'READY');assert.equal(h.calls[0][3].aborted,true)}finally{h.close()}
 }
})

test('mounted panel displays validated metadata then clears on identity change',async()=>{
 const h=mount(id(4));try{const pending=h.state.load();h.resolve(candidate());await pending;assert.equal(h.state.state.value,'READY');assert.equal(h.state.value.value.candidate_id,id(4));h.props.session.current_context.auth_epoch='2';assert.equal(h.state.state.value,'STALE_AUTH');assert.equal(h.state.value.value,null)}finally{h.close()}
})
test('mounted panel distinguishes read failures from stale authorization without keeping metadata',async()=>{
 for(const [code,expected] of [['INTERNAL_ERROR','ERROR'],['ACCESS_DENIED','STALE_AUTH'],['AUTHENTICATION_REQUIRED','STALE_AUTH']]){
  const h=mount(id(4));try{const pending=h.state.load();h.reject(Object.assign(Error('private server message'),{code}));await pending;assert.equal(h.state.state.value,expected);assert.equal(h.state.value.value,null);assert.doesNotMatch(h.state.error.value,/private server message/)}finally{h.close()}
 }
})
test('actual task workspace mounts read-only panel with verified session and route scope, no invented candidate reference',()=>{
 const parent=read('../../src/views/medical/task-detail.vue')
 assert.match(parent,/import CandidateReadPanel from '\.\/candidate-read-panel\.vue'/)
 assert.match(parent,/<candidate-read-panel v-if="session" :session="session" :project-id="projectId" :candidate-id="null" :active="routeActive"\s*\/>/)
 assert.match(parent,/当前任务记录尚未提供已授权的候选引用/)
 assert.deepEqual(compileTemplate({source:parse(parent).descriptor.template.content,filename:'task-detail.vue',id:'parent'}).errors,[])
})

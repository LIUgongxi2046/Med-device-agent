import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createRequire} from 'node:module'
import vm from 'node:vm'
import {loadModule,id,webRoot} from './helpers.mjs'
const require=createRequire(import.meta.url)
function transport(){
 let interceptor
 const source=readFileSync(webRoot+'/src/utils/request.js','utf8').replace(/^import .*\r?\n/gm,'').replace(/import\.meta\.env/g,"({VITE_APP_ENV:'medical',VITE_APP_BASE_API:''})").replace(/export default service/g,'').replace(/export /g,'')
 const readers={capability:v=>v,bounded:v=>v,comparison:v=>v,gru:v=>v,project:v=>v,write:v=>v},fail=(code,message)=>{throw Object.assign(Error(message),{code})}
 vm.runInNewContext(source,{URL,location:{origin:'https://medical.test'},axios:{defaults:{headers:{}},create:()=>({interceptors:{request:{use:fn=>{interceptor=fn}},response:{use(){}}}})},fail,readCapabilityJson:readers.capability,readBoundedJson:readers.bounded,readComparisonJson:readers.comparison,readGruExportJson:readers.gru,readProjectJson:readers.project,writeProjectJson:readers.write,prepareFrameworkRequest(){},verifyFrameworkResponse(){},console})
 return {readers,configure(url){const deleted=[],config=interceptor({url,method:'get',medical:'envelope',headers:{delete:k=>deleted.push(k),setContentType(){}}});return {config,deleted}}}
}
test('actual request interceptor routes only seven closed capability endpoint shapes to strict raw JSON',()=>{
 const {readers,configure}=transport()
 for(const url of ['/api/agent-authoring/capability-cancellations','/api/agent-authoring/capability-cancellation-operations','/api/agent-authoring/capabilities','/api/agent-authoring/records/query','/api/agent-authoring/records/'+id(4),'/api/agent-authoring/records/'+id(4)+'/operation-receipts/'+id(7),'/api/agent-authoring-feature-availability']){
  const {config,deleted}=configure(url);assert.equal(config.responseType,'text');assert.equal(config.transformResponse[0],readers.capability);assert.equal(config.transformRequest[0],readers.write);assert.equal(config.withCredentials,true);assert.deepEqual(deleted,['Authorization']);assert.equal(config.baseURL,'');assert.equal(config.validateStatus(500),true)
 }
 for(const url of ['/api/agent-authoring/capability-cancellations/extra','/api/agent-authoring/capability-cancellation-operations-extra','/api/agent-authoring/policies','/api/agent-authoring/records/not-an-id','/api/agent-authoring/records/'+id(4)+'/extra','/api/agent-authoring-feature-availability-extra'])assert.equal(configure(url).config.responseType,'json')
 assert.throws(()=>configure('https://attacker.invalid/api/agent-authoring/capabilities'),e=>e.code==='ACCESS_DENIED')
})
test('actual capability raw reader rejects duplicates, unsafe JSON numbers and UTF8 overflow',async()=>{
 const {api:r}=await loadModule('formal-capability-json')
 assert.equal(r.readCapabilityJson('{"status":"DRAFT"}').status,'DRAFT')
 for(const bad of ['{"status":"DRAFT","status":"APPROVE"}','{"x":9007199254740993}','{"x":1,}','{/* comment */"x":1}'])assert.throws(()=>r.readCapabilityJson(bad))
 assert.throws(()=>r.readCapabilityJson({}),e=>e.code==='SCHEMA_INVALID')
 assert.throws(()=>r.readCapabilityJson('汉'.repeat(2796203)),e=>e.code==='CONTEXT_LIMIT')
})
test('actual Vue Router resolves list/create/detail as normal no-cache business pages and preserves simulation routes',()=>{
 const {createRouter,createMemoryHistory}=require('vue-router'),source=readFileSync(webRoot+'/src/router/index.js','utf8')
 const start=source.indexOf('export const medicalRoutes = '),end=source.indexOf('\n// 公共路由',start)
 const section=source.slice(start,end>start?end:source.indexOf('export const constantRoutes',start)).replace('export const medicalRoutes = ','const medicalRoutes = ').replace(/\(\) => import\([^)]*\)/g,'({render(){}})')
 const routes=vm.runInNewContext(section+'\nmedicalRoutes',{Layout:{render(){}}})
 const router=createRouter({history:createMemoryHistory(),routes})
 for(const [path,name] of [['/agents/capabilities','MedicalFormalCapabilities'],['/agents/capabilities/new','MedicalFormalCapabilityCreate'],['/agents/capabilities/'+id(4),'MedicalFormalCapabilityDetail']]){const result=router.resolve(path);assert.equal(result.name,name);assert.equal(result.meta.medical,true);assert.equal(result.meta.noCache,true);assert.equal(result.meta.activeMenu,'/agents')}
 assert.equal(router.resolve('/agents/simulation-preparation').name,'MedicalSimulationPreparation')
 const java=readFileSync(webRoot+'/../business/medical-identity/src/main/java/com/ruoyi/medical/identity/FrameworkFrontendConfiguration.java','utf8')
 for(const path of ['/agents/capabilities','/agents/capabilities/new','/agents/capabilities/{recordId}'])assert.ok(java.includes('"'+path+'"'))
})
test('shared schema gate registrations compile the exact capability contracts without exposing policy authoring fragments',async()=>{
 const {api:p}=await loadModule('protocol'),{api:d}=await loadModule('formal-capability-domain')
 const {requestBody,session}=await import('./helpers.mjs'),body=requestBody(d)
 assert.equal(p.validate('urn:medical:agent-publication-workflow:1.0.0#/$defs/FormalCapability',body),body)
 assert.equal(p.validate('urn:medical:agent-publication-workflow:1.0.0#/$defs/CapabilityQuery',{schema_version:'1.0.0',kind:'FORMAL_CAPABILITY_AUTHOR',cursor:null}).cursor,null)
 assert.equal(p.validate('urn:medical:agent-authoring-feature-availability:1.0.0',{schema_version:'1.0.0',current_context:session().current_context,features:{agent_authoring:true}}).features.agent_authoring,true)
 assert.throws(()=>p.validate('urn:medical:agent-publication-workflow:1.0.0#/$defs/PolicyAuthor',{}),e=>e.code==='SCHEMA_INVALID')
})

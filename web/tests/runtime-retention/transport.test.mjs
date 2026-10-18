import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import {createRequire} from 'node:module'
import {loadModule,id,webRoot} from '../formal-capability/helpers.mjs'
const require=createRequire(import.meta.url)
test('actual retention interceptor uses bounded raw JSON only on closed route shapes',()=>{
 let intercept
 const source=readFileSync(webRoot+'/src/utils/request.js','utf8').replace(/^import .*\r?\n/gm,'').replace(/import\.meta\.env/g,"({VITE_APP_ENV:'medical',VITE_APP_BASE_API:''})").replace(/export default service/g,'').replace(/export /g,'')
 const readRetentionJson=v=>v,writeProjectJson=v=>v
 vm.runInNewContext(source,{URL,location:{origin:'https://medical.test'},axios:{defaults:{headers:{}},create:()=>({interceptors:{request:{use:fn=>intercept=fn},response:{use(){}}}})},readRetentionJson,writeProjectJson,fail:(code)=>{throw Object.assign(Error(code),{code})},prepareFrameworkRequest(){},verifyFrameworkResponse(){},console})
 const request=url=>intercept({url,medical:'envelope',headers:{delete(){},setContentType(){}}})
 for(const url of ['/api/runtime-retention-availability','/api/runtime-retention-policies','/api/runtime-retention-retirements','/api/runtime-retention-policy-operations','/api/runtime-retention-retirement-operations','/api/runtime-retention-cancellations','/api/runtime-retention-cancellation-operations','/api/runtime-retention-policies/'+id(5)]){
  const v=request(url);assert.equal(v.responseType,'text');assert.equal(v.transformResponse[0],readRetentionJson);assert.equal(v.transformRequest[0],writeProjectJson);assert.equal(v.withCredentials,true)
 }
 for(const url of ['/api/runtime-retention-policies/not-an-id','/api/runtime-retention-policies/'+id(5)+'/extra','/api/runtime-retention-cancellations-extra'])assert.equal(request(url).responseType,'json')
 assert.throws(()=>request('https://attacker.invalid/api/runtime-retention-policies'),e=>e.code==='ACCESS_DENIED')
})
test('actual retention parser rejects duplicate keys unsafe integers and over-cap UTF8',async()=>{
 const {api:r}=await loadModule('retention-json')
 assert.equal(r.readRetentionJson('{"status":"CANCELLED"}').status,'CANCELLED')
 for(const text of ['{"status":"CANCELLED","status":"COMPLETED"}','{"n":9007199254740993}','{"a":NaN}'])assert.throws(()=>r.readRetentionJson(text))
 assert.throws(()=>r.readRetentionJson('汉'.repeat(699051)),e=>e.code==='CONTEXT_LIMIT')
})
test('actual router and server fallback expose one retention business page',()=>{
 const {createRouter,createMemoryHistory}=require('vue-router'),s=readFileSync(webRoot+'/src/router/index.js','utf8'),start=s.indexOf('export const medicalRoutes = '),end=s.indexOf('\n// 公共路由',start)
 const section=s.slice(start,end>start?end:s.indexOf('export const constantRoutes',start)).replace('export const medicalRoutes = ','const medicalRoutes = ').replace(/\(\) => import\([^)]*\)/g,'({render(){}})')
 const routes=vm.runInNewContext(section+'\nmedicalRoutes',{Layout:{render(){}}}),router=createRouter({history:createMemoryHistory(),routes}),route=router.resolve('/agents/retention')
 assert.equal(route.name,'MedicalRuntimeRetention');assert.equal(route.meta.noCache,true);assert.equal(route.meta.medical,true)
 assert.ok(readFileSync(webRoot+'/../business/medical-identity/src/main/java/com/ruoyi/medical/identity/FrameworkFrontendConfiguration.java','utf8').includes('"/agents/retention"'))
})
test('real protocol whitelist includes exact cancellation and existing policy contracts',async()=>{
 const {api:p}=await loadModule('protocol')
 for(const name of ['CancelRequest','CancelReceipt','CancelRecovery','RetentionRequest','RetentionView','RetentionList','RetireRequest'])assert.notEqual(p.schemaIssues('urn:medical:runtime-retention:1.0.0#/$defs/'+name,{}).length,0)
 assert.equal(p.validate('urn:medical:runtime-retention:1.0.0#/$defs/Id',id(5)),id(5))
 assert.equal(p.validate('urn:medical:runtime-retention:1.0.0#/$defs/CancelRecovery',{schema_version:'1.0.0',status:'NOT_FOUND',receipt:null}).status,'NOT_FOUND')
})

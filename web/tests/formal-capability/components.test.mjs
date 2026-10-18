import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { loadModule, setupVue, session, id, requestBody, record, plain, tick } from './helpers.mjs'
const require=createRequire(import.meta.url),vue=require('vue')
async function createUI(options={}) {
 const {api:d,store}=await loadModule('formal-capability-domain'), user=vue.reactive({medicalSession:null,medicalWriting:false}), calls=[]
 const api={readCapabilityAvailability:async()=>({features:{agent_authoring:true}}),createCapability:async(...args)=>{calls.push(args);throw Error('unconfigured save')},recoverCapability:async()=>({lookup:{lookup_status:'NOT_FOUND',operation:null},record:null}),...options.api}
 const candidates={capabilityProjectCandidates:async()=>({projects:[],next_after:null}),capabilityArtifactCandidates:async()=>({items:[],next_after:null}),...options.candidates}
 const instance=setupVue('formal-capability-create',{user,modules:{'@/api/medical/formal-capability-domain':d,'@/api/medical/formal-capabilities':api,'@/api/medical/formal-capability-candidates':candidates}})
 if(options.beforeSession)await options.beforeSession(d,store)
 user.medicalSession=session();await tick()
 return {...instance,d,store,calls,api}
}
function fill(ui) { const s=ui.state, schema=ui.d.capabilitySchemas[0];s.artifacts.value=[{artifact_id:id(3),project_id:null,sha256:'a'.repeat(64),artifact_kind:'REAL_REGISTERED',created_at:'2026-10-18T00:00:00Z'}];s.artifactId.value=id(3);s.inputSchema.value=schema.schema_ref;s.outputSchema.value=schema.schema_ref;s.reason.value='真实登记';s.attested.value=true }
test('all four real SFC templates compile including responsive detail and exact role permissions',async()=>{
 const {api:d}=await loadModule('formal-capability-domain')
 for(const name of ['formal-capability-definition','formal-capability-list','formal-capability-detail']){
  const ui=setupVue(name,{modules:{'@/api/medical/formal-capability-domain':d}});ui.stop()
 }
 const ui=await createUI();assert.equal(ui.state.availability.value,true);ui.stop()
})
test('every declaration edit synchronously revokes user confirmation including nested permission scope',async()=>{
 const ui=await createUI();try{
  fill(ui)
  for(const change of [()=>ui.state.reason.value+='修改',()=>ui.state.inputSchema.value='',()=>ui.state.outputSchema.value='',()=>ui.state.artifactId.value='',()=>ui.state.kind.value='ROLE',()=>ui.state.scopeKey.value='candidate',()=>ui.state.purpose.value='AGENT_PUBLICATION',()=>ui.state.permissions.value.push(requestBody(ui.d,'ROLE').tool_scope.permissions[0]),()=>ui.state.permissions.value[0].scope.resource_id=id(99),()=>ui.state.project.value=id(9)]){
   ui.state.attested.value=true;change();assert.equal(ui.state.attested.value,false)
  }
 }finally{ui.stop()}
})
test('form binds actual selected ID/hash and published schema; no entered record/operation IDs exist',async()=>{
 const ui=await createUI();try{fill(ui);const body=ui.state.body();assert.equal(body.execution_mode,'PRODUCTION');assert.deepEqual(plain(body.source_artifact),{version_id:id(3),content_sha256:'a'.repeat(64)});assert.deepEqual(plain(body.tool_scope),[])
  ui.state.artifactId.value=id(99);assert.throws(()=>ui.state.body(),/请选择/)
  assert.equal('operationId'in ui.state,false);assert.equal('recordId'in ui.state,false)
 }finally{ui.stop()}
})
test('role author selects exact actual scope/action and rejects duplicate permissions without inventing a grant',async()=>{
 const ui=await createUI();try{ui.state.kind.value='ROLE';const candidate=ui.state.scopeCandidates.value.find(p=>p.action==='artifact.read')
  ui.state.scopeKey.value=ui.state.scopeIdentity(candidate);ui.state.purpose.value='AGENT_PUBLICATION';ui.state.addPermission();assert.equal(ui.state.permissions.value.length,1);assert.deepEqual(plain(ui.state.permissions.value[0].scope),{tenant_id:id(2),project_id:null,resource_kind:'ARTIFACT',resource_id:id(3)})
  ui.state.scopeKey.value=ui.state.scopeIdentity(candidate);ui.state.purpose.value='AGENT_PUBLICATION';ui.state.addPermission();assert.equal(ui.state.permissions.value.length,1);assert.match(ui.state.error.value,/重复/)
  ui.state.kind.value='PROMPT';assert.equal(ui.state.permissions.value.length,0)
 }finally{ui.stop()}
})
test('a foreign global writing lock blocks save and readers; clearing/unmounting cannot release it',async()=>{
 const ui=await createUI();fill(ui);ui.user.medicalWriting=true
 await ui.state.submit();await ui.state.loadArtifacts(false);assert.equal(ui.calls.length,0);assert.equal(ui.user.medicalWriting,true)
 ui.state.clear();assert.equal(ui.user.medicalWriting,true);ui.stop();assert.equal(ui.user.medicalWriting,true)
})
test('own writing lock spans async save, rejects double-click and is retained through hidden/unmount until settlement',async()=>{
 let resolve,calls=0
 const ui=await createUI({api:{createCapability:()=>{calls++;return new Promise(r=>{resolve=r})}}});fill(ui)
 const saving=ui.state.submit();assert.equal(ui.user.medicalWriting,true);await ui.state.submit();assert.equal(calls,1)
 ui.doc.hidden=true;ui.listeners.get('visibilitychange')();assert.equal(ui.user.medicalWriting,true);assert.equal(ui.state.reason.value,'')
 ui.stop();assert.equal(ui.user.medicalWriting,true);resolve({pending:{},receipt:{}});await saving;assert.equal(ui.user.medicalWriting,false)
})
test('identity replacement clears old display/form/candidates while late save cannot publish a result',async()=>{
 let resolve;const ui=await createUI({api:{createCapability:()=>new Promise(r=>{resolve=r})}});fill(ui)
 const saving=ui.state.submit();ui.user.medicalSession={...session(),user:{...session().user,user_id:id(77)}}
 assert.equal(ui.state.reason.value,'');assert.equal(ui.state.artifacts.value.length,0);assert.equal(ui.state.pending.value,null)
 resolve({pending:{},receipt:{}});await saving;assert.equal(ui.state.saved.value,null);assert.equal(ui.user.medicalWriting,false);ui.stop()
})
test('pending reload restores full original body and allows only read recovery, including NOT_FOUND',async()=>{
 let recoveries=0
 const ui=await createUI({beforeSession:async d=>{const s=session();await d.prepareCapability(()=>s,requestBody(d),new AbortController().signal)},api:{recoverCapability:async()=>{recoveries++;return {lookup:{lookup_status:'NOT_FOUND',operation:null},record:null}}}})
 try{assert.equal(ui.state.pending.value.body.reason,'核查真实来源并登记固定声明');fill(ui);await ui.state.submit();assert.equal(ui.calls.length,0)
  await ui.state.recover();assert.equal(recoveries,1);assert.ok(ui.state.pending.value);assert.match(ui.state.lookupMessage.value,/不能据此认定未执行/)
 }finally{ui.stop()}
})
test('late artifact candidate response cannot repopulate a different project scope',async()=>{
 let resolve;const ui=await createUI({candidates:{capabilityArtifactCandidates:()=>new Promise(r=>{resolve=r})}})
 try{const loading=ui.state.loadArtifacts(false);ui.state.project.value=id(50);resolve({items:[{artifact_id:id(3),project_id:null,sha256:'a'.repeat(64)}],next_after:null});await loading
  assert.equal(ui.state.artifacts.value.length,0);assert.equal(ui.state.artifactId.value,'');assert.equal(ui.state.artifactsLoaded.value,false)
 }finally{ui.stop()}
})
test('dirty-route guard distinguishes cancel and confirmed leave, preserving pending identity',async()=>{
 const ui=await createUI();try{fill(ui);assert.equal(ui.guard({fullPath:'/agents/capabilities'}),false);assert.equal(ui.state.leaveDialog.value,true)
  ui.state.leaveDialog.value=false;assert.equal(ui.state.reason.value,'真实登记');await ui.state.confirmLeave();assert.deepEqual(ui.routes,['/agents/capabilities']);assert.equal(ui.state.reason.value,'')
  ui.user.medicalWriting=true;assert.equal(ui.guard({fullPath:'/elsewhere'}),false);ui.user.medicalWriting=false
 }finally{ui.stop()}
})
test('successful save clears original pending only after recovery and keeps the verified result visible',async()=>{
 let d;const ui=await createUI({api:{createCapability:async(_reader,body,_signal,onPending)=>{const pending={body,key:id(18)};onPending(pending);return {pending,receipt:{}}},recoverCapability:async()=>({lookup:{lookup_status:'FOUND',operation:{status:'COMPLETED'}},record:record(d)})}});d=ui.d
 try{fill(ui);await ui.state.submit();assert.equal(ui.state.pending.value,null);assert.equal(ui.state.saved.value.record_id,id(4));assert.equal(ui.state.attested.value,false);assert.equal(ui.user.medicalWriting,false)}finally{ui.stop()}
})
test('deployment disabled prevents author form and candidate loading; it never becomes a permission grant',async()=>{
 let candidates=0;const ui=await createUI({api:{readCapabilityAvailability:async()=>({features:{agent_authoring:false}})},candidates:{capabilityProjectCandidates:async()=>{candidates++;return {projects:[],next_after:null}}}})
 try{assert.equal(ui.state.availability.value,false);assert.equal(candidates,0);fill(ui);await ui.state.submit();assert.equal(ui.calls.length,0)}finally{ui.stop()}
})

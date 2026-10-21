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
 user.medicalSession=options.session ?? session();await tick()
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

function scopeResource(s, kind, n, project = null) { return { actor: s.user.user_id, context: plain(s.current_context), label: '真实范围 ' + n, scope: { tenant_id: s.current_context.tenant_id, project_id: project, resource_kind: kind, resource_id: id(n) } } }
test('normal PRODUCTION ROLE form explicitly selects inherited project/source permissions and passes verified identities to save', async () => {
 const s = session(); s.action_summary.push(...['project.read', 'artifact.register', 'source.read', 'source.external.process'].map(action => ({ action, resource_kind: 'ENTERPRISE', resource_id: id(2), project_id: null })))
 const projectResource = scopeResource(s, 'PROJECT', 8, id(8)), sourceResource = scopeResource(s, 'SOURCE', 11, id(8)), enterpriseSource = scopeResource(s, 'SOURCE', 12)
 const ui = await createUI({ session: s, candidates: {
  capabilityProjectCandidates: async () => ({ projects: [{ project_id: id(8), tenant_id: id(2), name: '真实项目' }], resources: [projectResource], next_after: null }),
  capabilitySourceCandidates: async (_reader, project) => { assert.equal(project, id(8)); return { resources: [sourceResource, enterpriseSource], next_after: null } }
 } })
 try {
  ui.state.project.value = id(8); ui.state.kind.value = 'ROLE'; fill(ui)
  assert.equal(ui.state.permissions.value.length, 0)
  assert.equal(ui.state.scopeCandidates.value.some(x => x.scope.resource_id === id(11)), false)
  await ui.state.loadSources(false)
  assert.equal(ui.state.permissions.value.length, 0)
  assert.equal(ui.state.scopeKey.value, '')
  for (const [action, kind, n, purpose] of [['project.read', 'PROJECT', 8, 'MODEL_EXECUTION'], ['artifact.register', 'PROJECT', 8, 'EVALUATION_RESULTS'], ['source.read', 'SOURCE', 11, 'MODEL_EXECUTION'], ['source.external.process', 'SOURCE', 11, 'MODEL_EXECUTION'], ['source.read', 'SOURCE', 12, 'MODEL_EXECUTION']]) {
   const candidate = ui.state.scopeCandidates.value.find(x => x.action === action && x.scope.resource_kind === kind && x.scope.resource_id === id(n))
   assert.ok(candidate); ui.state.scopeKey.value = ui.state.scopeIdentity(candidate); ui.state.purpose.value = purpose; ui.state.addPermission()
  }
  const body = ui.state.body(); assert.equal(body.execution_mode, 'PRODUCTION'); assert.equal(body.tool_scope.permissions.length, 5); assert.equal(body.tool_scope.permissions[4].scope.project_id, null)
  ui.d.assertCapabilityScopes(ui.user.medicalSession, body, ui.state.scopeResources.value)
  ui.state.attested.value = true; await ui.state.submit()
  assert.equal(ui.calls.length, 1); assert.deepEqual(plain(ui.calls[0][4]), [projectResource, sourceResource, enterpriseSource])
  assert.equal(ui.calls[0][1].tool_scope.permissions[1].purpose, 'EVALUATION_RESULTS')
 } finally { ui.stop() }
})
test('refreshing actual scope identities invalidates selected option and attestation; in-place identity changes clear all evidence', async () => {
 const s = session(); s.action_summary.push({ action: 'source.read', resource_kind: 'ENTERPRISE', resource_id: id(2), project_id: null })
 let resources = [scopeResource(s, 'SOURCE', 11)]
 const ui = await createUI({ session: s, candidates: { capabilitySourceCandidates: async () => ({ resources, next_after: null }) } })
 try {
  ui.state.kind.value = 'ROLE'; await ui.state.loadSources(false)
  const candidate = ui.state.scopeCandidates.value.find(x => x.scope.resource_id === id(11))
  ui.state.scopeKey.value = ui.state.scopeIdentity(candidate); ui.state.attested.value = true
  resources = [scopeResource(s, 'SOURCE', 12)]; await ui.state.loadSources(false)
  assert.equal(ui.state.scopeKey.value, ''); assert.equal(ui.state.attested.value, false)
  assert.equal(ui.state.scopeCandidates.value.some(x => x.scope.resource_id === id(11)), false)
  ui.user.medicalSession.current_context.auth_epoch = '2'
  assert.equal(ui.state.sourceResources.value.length, 0); assert.equal(ui.state.projectResources.value.length, 0)
  ui.state.sourceResources.value = [scopeResource(s, 'SOURCE', 12)]
  ui.user.medicalSession.user.user_id = id(99)
  assert.equal(ui.state.sourceResources.value.length, 0); assert.equal(ui.state.permissions.value.length, 0)
 } finally { ui.stop() }
})
test('late source scope page cannot repopulate a different selected project or identity', async () => {
 let done; const ui = await createUI({ candidates: { capabilitySourceCandidates: () => new Promise(resolve => done = resolve) } })
 try {
  const read = ui.state.loadSources(false); ui.state.project.value = id(8)
  done({ resources: [scopeResource(session(), 'SOURCE', 11)], next_after: null }); await read
  assert.equal(ui.state.sourceResources.value.length, 0); assert.equal(ui.state.sourcesLoaded.value, false)
 } finally { ui.stop() }
})
test('ownership change on a later source page invalidates previously derived identities immediately', async () => {
 const s = session(); s.action_summary.push({ action: 'source.read', resource_kind: 'ENTERPRISE', resource_id: id(2), project_id: null })
 let n = 0
 const ui = await createUI({ session: s, candidates: { capabilitySourceCandidates: async () => ++n === 1
  ? { resources: [scopeResource(s, 'SOURCE', 11, id(8))], next_after: id(111) }
  : { resources: [scopeResource(s, 'SOURCE', 11)], next_after: null } } })
 try {
  ui.state.project.value = id(8); ui.state.kind.value = 'ROLE'; await ui.state.loadSources(false)
  ui.state.attested.value = true; await ui.state.loadSources(true)
  assert.equal(ui.state.sourceResources.value.length, 0); assert.equal(ui.state.sourceCursor.value, null)
  assert.equal(ui.state.attested.value, false); assert.match(ui.state.error.value, /归属已变化/)
 } finally { ui.stop() }
})

import { broad, modelResource } from './model-scope-fixtures.mjs'
test('normal project ROLE selects real enterprise/project config IDs for EVALUATION_RESULTS without granting or enabling a model', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session(); s.action_summary.push(broad('model.send'))
 const models = [modelResource(d, s, 40, id(8)), modelResource(d, s, 41), modelResource(d, s, 42, id(9))]
 const ui = await createUI({ session: s, candidates: {
  capabilityProjectCandidates: async () => ({ projects: [{ project_id: id(8), tenant_id: id(2), name: '真实项目' }], next_after: null }),
  capabilityModelCandidates: async (_reader, after) => { assert.equal(after, null); return { resources: models, next_after: null } }
 } })
 try {
  ui.state.project.value = id(8); ui.state.kind.value = 'ROLE'; fill(ui)
  assert.equal(ui.state.scopeCandidates.value.some(p => p.scope.resource_kind === 'CONFIGURATION'), false)
  await ui.state.loadModels(false); assert.equal(ui.state.permissions.value.length, 0)
  assert.equal(ui.state.scopeCandidates.value.some(p => p.scope.resource_id === id(42)), false)
  for (const n of [40, 41]) {
   const candidate = ui.state.scopeCandidates.value.find(p => p.action === 'model.send' && p.scope.resource_id === id(n))
   assert.ok(candidate); assert.match(ui.state.scopeLabel(candidate), /Readable model.*DRAFT/)
   ui.state.scopeKey.value = ui.state.scopeIdentity(candidate); ui.state.purpose.value = 'EVALUATION_RESULTS'; ui.state.addPermission()
  }
  const body = ui.state.body(); d.assertCapabilityScopes(ui.user.medicalSession, body, ui.state.scopeResources.value)
  assert.equal(body.tool_scope.permissions[1].scope.project_id, null); assert.equal(body.tool_scope.permissions[1].scope.resource_id, id(41))
  ui.state.attested.value = true; await ui.state.submit()
  assert.equal(ui.calls.length, 1); assert.deepEqual(plain(ui.calls[0][4]), models)
  assert.equal(ui.calls[0][1].tool_scope.permissions[0].purpose, 'EVALUATION_RESULTS')
 } finally { ui.stop() }
})

test('ROLE model refresh invalidates selector and confirmation; changed grants clear all loaded model evidence', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session(); s.action_summary.push(broad('model.send'))
 let resources = [modelResource(d, s)]
 const ui = await createUI({ session: s, candidates: { capabilityModelCandidates: async () => ({ resources, next_after: null }) } })
 try {
  ui.state.kind.value = 'ROLE'; await ui.state.loadModels(false)
  const candidate = ui.state.scopeCandidates.value.find(p => p.action === 'model.send' && p.scope.resource_id === id(40))
  ui.state.scopeKey.value = ui.state.scopeIdentity(candidate); ui.state.purpose.value = 'EVALUATION_RESULTS'; ui.state.addPermission()
  ui.state.scopeKey.value = ui.state.scopeIdentity(candidate); ui.state.attested.value = true
  resources = [modelResource(d, s, 41)]; await ui.state.loadModels(false)
  assert.equal(ui.state.scopeKey.value, ''); assert.equal(ui.state.attested.value, false)
  assert.equal(ui.state.scopeCandidates.value.some(p => p.scope.resource_id === id(40)), false)
  assert.throws(() => d.assertCapabilityScopes(ui.user.medicalSession, { ...requestBody(d, 'ROLE'), tool_scope: { schema_version: '1.0.0', permissions: plain(ui.state.permissions.value) } }, ui.state.scopeResources.value), e => e.code === 'ACCESS_DENIED')
  ui.user.medicalSession.action_summary.push(broad('model.verify'))
  assert.equal(ui.state.modelResources.value.length, 0); assert.equal(ui.state.permissions.value.length, 0)
  assert.equal(ui.state.modelsLoaded.value, false)
 } finally { ui.stop() }
})

test('ROLE later model pages reject changed foreign ownership/fixed identity and invalidate every previous model candidate', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session(); s.action_summary.push(broad('model.send'))
 for (const change of [r => r.scope.project_id = id(9), r => r.model.model_config_revision = id(99), r => r.model.content_sha256 = 'b'.repeat(64)]) {
  const first = modelResource(d, s, 40, id(8)), second = plain(first); change(second); let reads = 0
  const ui = await createUI({ session: s, candidates: { capabilityModelCandidates: async (_reader, after) => {
   assert.equal(after, reads ? 'next-model-page' : null); return ++reads === 1 ? { resources: [first], next_after: 'next-model-page' } : { resources: [second], next_after: null }
  } } })
  try {
   ui.state.kind.value = 'ROLE'; ui.state.project.value = id(8); await ui.state.loadModels(false); ui.state.attested.value = true
   await ui.state.loadModels(true)
   assert.equal(ui.state.modelResources.value.length, 0); assert.equal(ui.state.modelCursor.value, null); assert.equal(ui.state.modelsLoaded.value, false)
   assert.equal(ui.state.scopeCandidates.value.some(p => p.scope.resource_id === id(40)), false)
   assert.equal(ui.state.attested.value, false); assert.match(ui.state.error.value, /模型配置归属或固定版本已变化/)
  } finally { ui.stop() }
 }
})

test('ROLE stale model response is discarded on project, actor, authorization, hidden page and unmount changes', async () => {
 const { api: d } = await loadModule('formal-capability-domain')
 for (const invalidate of [ui => ui.state.project.value = id(9), ui => { ui.state.project.value = id(9); ui.state.project.value = '' }, ui => ui.state.clearForm(), ui => ui.user.medicalSession.user.user_id = id(99),
  ui => ui.user.medicalSession.action_summary.push(broad('model.verify')), ui => { ui.doc.hidden = true; ui.listeners.get('visibilitychange')() }, ui => ui.stop()]) {
  const s = session(); s.action_summary.push(broad('model.send')); let done
  const ui = await createUI({ session: s, candidates: { capabilityModelCandidates: () => new Promise(resolve => done = resolve) } })
  ui.state.kind.value = 'ROLE'; const reading = ui.state.loadModels(false); invalidate(ui)
  done({ resources: [modelResource(d, s)], next_after: null }); await reading
  assert.equal(ui.state.modelResources.value.length, 0); assert.equal(ui.state.modelsLoaded.value, false); ui.stop()
 }
})

test('ROLE invalidated model cursor error clears old evidence before presenting a reread error', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session(); s.action_summary.push(broad('model.send')); let calls = 0
 const ui = await createUI({ session: s, candidates: { capabilityModelCandidates: async () => { if (calls++) throw Error('CURSOR_INVALID 请重新读取'); return { resources: [modelResource(d, s)], next_after: 'next' } } } })
 try {
  ui.state.kind.value = 'ROLE'; await ui.state.loadModels(false); ui.state.attested.value = true; await ui.state.loadModels(true)
  assert.equal(ui.state.modelResources.value.length, 0); assert.equal(ui.state.modelCursor.value, null); assert.equal(ui.state.attested.value, false)
  assert.match(ui.state.error.value, /CURSOR_INVALID/)
 } finally { ui.stop() }
})

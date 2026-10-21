import test from 'node:test'
import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
import {loadModule,setupVue,session,id,plain,tick,webRoot} from './helpers.mjs'
const require=createRequire(webRoot+'/package.json'),vue=require('vue')
async function ui(options={}){const {api:d,store,events}=await loadModule('formal-agent-drafts'),{api:f}=await loadModule('formal-agent-manifest-form'),{api:capability}=await loadModule('formal-capability-domain');const user=vue.reactive({medicalSession:null,medicalWriting:false}),calls=[];const api={...d,draftAvailability:async()=>({features:{formal_agent_authoring:true}}),queryDrafts:async()=>({items:[],next_cursor:null}),createDraft:async(...args)=>{calls.push(args);throw Error('unknown result')},recoverDraft:async()=>({lookup:{lookup_status:'NOT_FOUND'},view:null}),...options.api};const page=setupVue('formal-agent-drafts',{user,modules:{'@/api/medical/formal-agent-drafts':api,'@/api/medical/formal-capability-domain':capability,'@/api/medical/formal-agent-manifest-form':f,'@/api/medical/formal-capability-candidates':{capabilityProjectCandidates:async()=>({projects:[],next_after:null}),capabilityArtifactCandidates:async()=>({items:[],next_after:null}),...options.candidates},'@/api/medical/tasks':{taskDocumentLabels:{CLINICAL_EVALUATION:'临床评价'}},...options.modules}});user.medicalSession=options.session ?? session();await tick();return {...page,d,f,store,events,calls}}
function fill(x){const s=x.state;s.kind.value='TOOL';s.artifacts.value=[{artifact_id:id(3),sha256:'a'.repeat(64),project_id:null,artifact_kind:'FIXED',size_bytes:'5',media_type:'application/json',created_at:'2026-10-18T00:00:00Z'}];s.artifactId.value=id(3);const schema=x.d.capabilitySchemas[0];s.inputSchema.value=schema.schema_ref;s.outputSchema.value=schema.schema_ref;s.permissions.value=[{scope:{tenant_id:id(2),project_id:null,resource_kind:'ARTIFACT',resource_id:id(3)},action:'artifact.read',purpose:'AGENT_PUBLICATION'}];s.reason.value='登记真实工具';s.attested.value=true}
test('actual normal entry template and recursive structured editor compile',async()=>{const x=await ui();assert.equal(x.state.available.value,true);const field=setupVue('formal-agent-manifest-field',{modules:{'@/api/medical/formal-agent-drafts':x.d,'@/api/medical/formal-agent-manifest-form':x.f}});field.stop();x.stop()})
test('structured TOOL form binds actual artifact and published input/output hashes',async()=>{const x=await ui();fill(x);const b=x.state.body();assert.equal(x.d.draftIssue('TOOL',b),'');assert.deepEqual(plain(b.source_artifact),{version_id:id(3),content_sha256:'a'.repeat(64)});assert.equal(b.execution_mode,'PRODUCTION');x.stop()})
test('pure required-field error does not write or dispatch identity failure',async()=>{const x=await ui();fill(x);x.state.artifactId.value='';x.state.attested.value=true;await x.state.submit();assert.equal(x.calls.length,0);assert.match(x.state.error.value,/补齐/);assert.equal(x.events.length,0);assert.ok(x.user.medicalSession);x.stop()})
test('changing nested declaration revokes confirmation synchronously',async()=>{const x=await ui();fill(x);x.state.permissions.value[0].purpose='OTHER';assert.equal(x.state.attested.value,false);x.state.attested.value=true;x.state.reason.value+='修改';assert.equal(x.state.attested.value,false);x.stop()})
test('foreign global write lock blocks reads and writes and is never released by clear/unmount',async()=>{let reads=0;const x=await ui({api:{queryDrafts:async()=>{reads++;return {items:[],next_cursor:null}}}});fill(x);x.user.medicalWriting=true;await x.state.submit();await x.state.list(false);x.state.clear();x.stop();assert.equal(x.calls.length,0);assert.equal(reads,0);assert.equal(x.user.medicalWriting,true)})
test('own lock persists through identity switch until operation settles; late result cannot leak',async()=>{let done;const x=await ui({api:{createDraft:async(_r,_k,_b,_s,pending)=>{await new Promise(r=>done=r);pending({key:id(8)});return {}}}});fill(x);const saving=x.state.submit();assert.equal(x.user.medicalWriting,true);x.user.medicalSession={...session(),user:{...session().user,user_id:id(77)}};assert.equal(x.state.reason.value,'');assert.equal(x.state.pending.value,null);assert.equal(x.user.medicalWriting,true);done();await saving;await tick();assert.equal(x.state.pending.value,null);assert.equal(x.user.medicalWriting,false);x.stop()})
test('old page finally cannot clear newer loading state after scope change',async()=>{let oldDone,newDone;let n=0;const x=await ui({api:{queryDrafts:()=>new Promise(r=>{if(n++===0)oldDone=r;else newDone=r})}});const old=x.state.list(false);x.state.project.value=id(10);const recent=x.state.list(false);assert.equal(x.state.loading.value,true);oldDone({items:[{record_id:id(3)}],next_cursor:null});await old;assert.equal(x.state.loading.value,true);assert.equal(x.state.items.value.length,0);newDone({items:[],next_cursor:null});await recent;assert.equal(x.state.loading.value,false);x.stop()})
test('hidden view clears displayed private data without claiming cancellation',async()=>{const x=await ui();fill(x);x.state.detail.value={fixed:{name:'private'}};x.doc.hidden=true;x.listeners.get('visibilitychange')();assert.equal(x.state.detail.value,null);assert.equal(x.state.reason.value,'');assert.equal(x.store.map.size,0);x.stop()})
test('manifest editor has no fabricated version/hash defaults and separates actual typed choices',async()=>{const x=await ui();const empty=x.f.emptyManifestValue(x.d.draftManifestSchema);assert.deepEqual(plain(empty.role_versions),[]);assert.equal(empty.schema_version,'1.0.0');assert.equal(empty.runtime_manifest.source_commit,'');const choices=[{group:'TOOL',label:'tool',value:{version_id:id(1),content_sha256:'a'.repeat(64)}},{group:'ROLE',label:'role',value:{version_id:id(2),content_sha256:'b'.repeat(64)}}];assert.equal(x.f.referenceChoices('role_versions.0','VERSION',choices)[0].group,'ROLE');assert.equal(x.f.referenceChoices('tool_versions.0','VERSION',choices)[0].group,'TOOL');x.stop()})
test('two artifact pages retain actual metadata and independent next cursor',async()=>{let page=0;const x=await ui({candidates:{capabilityArtifactCandidates:async(_r,_p,after)=>{assert.equal(after,page? id(3):null);return {items:[{artifact_id:id(++page+2)}],next_after:page===1?id(3):null}}}});await x.state.loadArtifacts(false);await x.state.loadArtifacts(true);assert.deepEqual(plain(x.state.artifacts.value.map(x=>x.artifact_id)),[id(3),id(4)]);assert.equal(x.state.artifactCursor.value,null);x.stop()})
test('explicit policy binding copies only actual fixed dependencies and still leaves missing runtime inputs',async()=>{const x=await ui();const basis=n=>({version_id:id(n),content_sha256:'a'.repeat(64)});x.state.policies.value=[{status:'APPROVED',fixed:{version_id:id(50),definition_sha256:'b'.repeat(64),definition:{bindings:[{role_version:basis(51),model_version:null}],retrieval_profiles:[{version:basis(52)}],source_selection:{version:basis(53)},allowed_classifications:['INTERNAL']}}}];x.state.policyId.value=id(50);x.state.applyPolicy();assert.deepEqual(plain(x.state.manifest.value.role_versions),[basis(51)]);assert.deepEqual(plain(x.state.manifest.value.model_config_revisions),[]);assert.deepEqual(plain(x.state.manifest.value.release_scope.source_scope_policy),basis(53));assert.equal(x.state.manifest.value.runtime_manifest.source_commit,'');assert.equal(x.state.attested.value,false);x.stop()})
test('identity scope replacement clears previous project and cannot query it for another enterprise',async()=>{const x=await ui();x.state.project.value=id(8);x.user.medicalSession={...session(),current_context:{...session().current_context,tenant_id:id(99)}};assert.equal(x.state.project.value,'');assert.equal(x.state.items.value.length,0);assert.equal(x.state.manifest.value.release_scope.tenant_id,'');x.stop()})

function scopeResource(s, kind, n, project = null) { return { actor: s.user.user_id, context: plain(s.current_context), label: '真实范围', scope: { tenant_id: s.current_context.tenant_id, project_id: project, resource_kind: kind, resource_id: id(n) } } }
for (const kind of ['TOOL', 'SKILL']) {
 test(kind + ' normal production form explicitly selects verified inherited project and source scopes', async () => {
  const s = session(); s.action_summary.push(...['project.read', 'source.read'].map(action => ({ action, resource_kind: 'ENTERPRISE', resource_id: id(2), project_id: null })))
  const project = scopeResource(s, 'PROJECT', 8, id(8)), source = scopeResource(s, 'SOURCE', 11, id(8))
  const x = await ui({ session: s, candidates: {
   capabilityProjectCandidates: async () => ({ projects: [{ project_id: id(8), tenant_id: id(2), name: '真实项目' }], resources: [project], next_after: null }),
   capabilitySourceCandidates: async () => ({ resources: [source], next_after: null })
  } })
  try {
   fill(x); x.state.kind.value = kind; await x.state.loadProjects(false); x.state.project.value = id(8)
   const schema = x.d.capabilitySchemas[0]
   x.state.artifacts.value = [{ artifact_id: id(3), sha256: 'a'.repeat(64), project_id: null }]; x.state.artifactId.value = id(3)
   x.state.inputSchema.value = schema.schema_ref; x.state.outputSchema.value = schema.schema_ref; x.state.reason.value = '登记当前项目固定能力'
   await x.state.loadSources(false)
   assert.equal(x.state.permissions.value.length, 0); assert.equal(x.state.scopeIndex.value, '')
   for (const [action, n] of [['project.read', 8], ['source.read', 11]]) {
    const index = x.state.scopeCandidates.value.findIndex(p => p.action === action && p.scope.resource_id === id(n))
    assert.notEqual(index, -1); x.state.scopeIndex.value = String(index); x.state.purpose.value = 'MODEL_EXECUTION'; x.state.addPermission()
   }
   assert.equal(x.state.permissions.value.length, 2); x.state.attested.value = true; await x.state.submit()
   assert.equal(x.calls.length, 1); assert.equal(x.calls[0][2].execution_mode, 'PRODUCTION'); assert.equal(x.calls[0][2].kind, kind)
   assert.deepEqual(plain(x.calls[0][5]), [project, source])
   x.user.medicalSession.user.user_id = id(99)
   assert.equal(x.state.projectResources.value.length, 0); assert.equal(x.state.sourceResources.value.length, 0); assert.equal(x.state.permissions.value.length, 0)
  } finally { x.stop() }
 })
}
test('refreshing TOOL source catalogue clears selected option and confirmation without replacing a declared permission', async () => {
 const s = session(); s.action_summary.push({ action: 'source.read', resource_kind: 'ENTERPRISE', resource_id: id(2), project_id: null })
 let resources = [scopeResource(s, 'SOURCE', 11)]
 const x = await ui({ session: s, candidates: { capabilitySourceCandidates: async () => ({ resources, next_after: null }) } })
 try {
  x.state.kind.value = 'TOOL'; await x.state.loadSources(false)
  x.state.scopeIndex.value = String(x.state.scopeCandidates.value.findIndex(p => p.scope.resource_id === id(11)))
  x.state.purpose.value = 'MODEL_EXECUTION'; x.state.addPermission(); x.state.attested.value = true
  resources = [scopeResource(s, 'SOURCE', 12)]; await x.state.loadSources(false)
  assert.equal(x.state.scopeIndex.value, ''); assert.equal(x.state.attested.value, false)
  assert.equal(x.state.scopeCandidates.value.some(p => p.scope.resource_id === id(11)), false)
  assert.equal(x.state.permissions.value[0].scope.resource_id, id(11))
  x.user.medicalSession.current_context.governance_epoch = '2'
  assert.equal(x.state.sourceResources.value.length, 0)
 } finally { x.stop() }
})
test('TOOL source pagination rejects a changed owner across pages and discards old narrowed identities', async () => {
 const s = session(); s.action_summary.push({ action: 'source.read', resource_kind: 'ENTERPRISE', resource_id: id(2), project_id: null })
 let reads = 0
 const x = await ui({ session: s, candidates: { capabilitySourceCandidates: async () => ++reads === 1
  ? { resources: [scopeResource(s, 'SOURCE', 11, id(8))], next_after: id(111) }
  : { resources: [scopeResource(s, 'SOURCE', 11)], next_after: null } } })
 try {
  x.state.kind.value = 'TOOL'; x.state.project.value = id(8); await x.state.loadSources(false)
  x.state.attested.value = true; await x.state.loadSources(true)
  assert.equal(x.state.sourceResources.value.length, 0); assert.equal(x.state.sourceCursor.value, null)
  assert.equal(x.state.attested.value, false); assert.match(x.state.error.value, /归属已变化/)
 } finally { x.stop() }
})

import { broad, modelResource } from '../formal-capability/model-scope-fixtures.mjs'
for (const kind of ['TOOL', 'SKILL']) {
 test(kind + ' normal form explicitly selects exact config scope including enterprise model in a project', async () => {
  const { api: d } = await loadModule('formal-capability-domain'), s = session(); s.action_summary.push(broad('model.send'))
  const models = [modelResource(d, s, 40, id(8)), modelResource(d, s, 41), modelResource(d, s, 42, id(9))]
  const x = await ui({ session: s, candidates: { capabilityModelCandidates: async () => ({ resources: models, next_after: null }) } })
  try {
   fill(x); x.state.kind.value = kind; x.state.project.value = id(8)
   x.state.artifacts.value = [{ artifact_id: id(3), sha256: 'a'.repeat(64), project_id: null }]; x.state.artifactId.value = id(3)
   x.state.inputSchema.value = d.capabilitySchemas[0].schema_ref; x.state.outputSchema.value = d.capabilitySchemas[0].schema_ref; x.state.reason.value = '登记固定模型范围'
   await x.state.loadModels(false); assert.equal(x.state.permissions.value.length, 0)
   assert.equal(x.state.scopeCandidates.value.some(p => p.scope.resource_id === id(42)), false)
   for (const n of [40, 41]) {
    const index = x.state.scopeCandidates.value.findIndex(p => p.action === 'model.send' && p.scope.resource_id === id(n)); assert.notEqual(index, -1)
    assert.match(x.state.scopeLabel(x.state.scopeCandidates.value[index]), /Readable model.*DRAFT/)
    x.state.scopeIndex.value = String(index); x.state.purpose.value = 'EVALUATION_RESULTS'; x.state.addPermission()
   }
   assert.equal(x.state.permissions.value[1].scope.project_id, null); assert.equal(x.state.permissions.value[1].scope.resource_id, id(41))
   x.state.attested.value = true; await x.state.submit(); assert.equal(x.calls.length, 1)
   assert.deepEqual(plain(x.calls[0][5]), models); assert.equal(x.calls[0][2].kind, kind)
   assert.equal(x.calls[0][2].tool_scope.permissions[0].purpose, 'EVALUATION_RESULTS')
  } finally { x.stop() }
 })
 test(kind + ' model scope refresh, permission edits and cross-page ownership conflicts discard stale evidence', async () => {
  const { api: d } = await loadModule('formal-capability-domain'), s = session(); s.action_summary.push(broad('model.send'))
  let resources = [modelResource(d, s, 40, id(8))], cursor = 'next'
  const x = await ui({ session: s, candidates: { capabilityModelCandidates: async () => ({ resources, next_after: cursor }) } })
  try {
   x.state.kind.value = kind; x.state.project.value = id(8); await x.state.loadModels(false)
   x.state.scopeIndex.value = String(x.state.scopeCandidates.value.findIndex(p => p.scope.resource_id === id(40))); x.state.attested.value = true
   await x.state.loadModels(false); assert.equal(x.state.scopeIndex.value, ''); assert.equal(x.state.attested.value, false)
   resources = [modelResource(d, s, 40, id(9))]; cursor = null; x.state.attested.value = true; await x.state.loadModels(true)
   assert.equal(x.state.modelResources.value.length, 0); assert.equal(x.state.modelCursor.value, null); assert.equal(x.state.attested.value, false)
   assert.match(x.state.error.value, /模型配置归属或固定版本已变化/)
   resources = [modelResource(d, s, 40, id(8))]; await x.state.loadModels(false)
   x.user.medicalSession.action_summary.push(broad('model.verify'))
   assert.equal(x.state.modelResources.value.length, 0); assert.equal(x.state.permissions.value.length, 0); assert.equal(x.state.modelsLoaded.value, false)
  } finally { x.stop() }
 })
}
test('TOOL stale model response after project replacement cannot restore model scope candidates', async () => {
 const { api: d } = await loadModule('formal-capability-domain'), s = session(); s.action_summary.push(broad('model.send')); let done
 const x = await ui({ session: s, candidates: { capabilityModelCandidates: () => new Promise(resolve => done = resolve) } })
 try {
  x.state.kind.value = 'TOOL'; const reading = x.state.loadModels(false); x.state.project.value = id(8)
  done({ resources: [modelResource(d, s)], next_after: null }); await reading
  assert.equal(x.state.modelResources.value.length, 0); assert.equal(x.state.modelsLoaded.value, false)
 } finally { x.stop() }
})

import test from 'node:test'
import assert from 'node:assert/strict'
import {createHash} from 'node:crypto'
import {loadModule,setupVue,id,session,response,plain,tick} from '../n01-plan-preparation/helpers.mjs'
import {createRequire} from 'node:module'
const require=createRequire(import.meta.url),vue=require('vue'),{canonicalizeEx}=require('json-canonicalize')
const s=()=>{const value=session();value.action_summary=['task.create','project.read'].map(action=>({action,project_id:id(10),resource_kind:'PROJECT',resource_id:id(10)}));return value}
function page(kind='SOURCE',release=id(20)) {const filter={schema_version:'1.0.0',tenant_id:id(2),project_id:id(10),actor_id:id(1),context_revision:'1',auth_epoch:'1',governance_epoch:'1',scope_kind:kind,agent_release_id:release,limit:20};return {...filter,items:[],next_after:null,filter_sha256:createHash('sha256').update(canonicalizeEx(filter,{strictUndefined:true})).digest('hex'),dataset_watermark:'a'.repeat(64)}}
function responsePage(kind,release){const p=page(kind,release);delete p.actor_id;return p}
test('picker sends selected release only and binds response/filter to it for both scope kinds',async()=>{
 for(const kind of ['SOURCE','COLLECTION']){let seen;const {api}=await loadModule('task-scope-selection',{request:{get:async(url,config)=>{seen=config.params;return response(responsePage(kind,id(20)),null)}}});
 const result=await api.listTaskScopeChoices(s(),id(10),kind,{agent_release_id:id(20),limit:20},null,new AbortController().signal);
 assert.deepEqual(plain(seen),{agent_release_id:id(20),limit:20});assert.equal(result.agent_release_id,id(20));assert.equal('purpose' in seen,false);
 }
});
test('picker rejects missing release, caller purpose, foreign release response and foreign cursor',async()=>{
 let calls=0;const {api}=await loadModule('task-scope-selection',{request:{get:async()=>{calls++;return response(responsePage('SOURCE',id(21)),null)}}});
 for(const query of [{limit:20},{agent_release_id:id(20),limit:20,purpose:'INTERNAL_RESEARCH'}])await assert.rejects(api.listTaskScopeChoices(s(),id(10),'SOURCE',query,null,new AbortController().signal));assert.equal(calls,0);
 await assert.rejects(api.listTaskScopeChoices(s(),id(10),'SOURCE',{agent_release_id:id(20),limit:20},null,new AbortController().signal));
 const previous={...responsePage('SOURCE',id(21)),next_after:id(40)};
 await assert.rejects(api.listTaskScopeChoices(s(),id(10),'SOURCE',{agent_release_id:id(20),limit:20,after:id(40),dataset_watermark:previous.dataset_watermark},previous,new AbortController().signal));assert.equal(calls,1);
});
function ui(options={}){const user=vue.reactive({medicalSession:null,medicalWriting:false}),route=vue.reactive({params:{projectId:id(10)},fullPath:'/task-create'}),reads=[];const mods={
 '@/api/medical/protocol':{schemaIssues:(_r,v)=>v&&typeof v==='object'||typeof v==='string'&&/^[0-9a-f-]{36}$/.test(v)?[]:[{}],fail:(_c,m)=>{throw Error(m)}},
 '@/api/medical/project-write':{projectActionAllowed:()=>true,projectTextIssue:()=>''},'@/api/medical/tasks':{taskIdReference:'id'},
 '@/api/medical/route-read':{useMedicalRouteRead:()=>vue.ref(true)},
 '@/api/medical/projects':{readProject:async()=>({status:'ACTIVE'})},
 '@/api/medical/task-create':{pendingTaskCreate:()=>null,...options.taskCreate},
 '@/api/medical/task-creation-selection':{listTaskCreationOwners:async()=>({items:[]})},
 '@/api/medical/task-scope-selection':{listTaskScopeChoices:async(...args)=>new Promise((resolve,reject)=>reads.push({args,resolve,reject}))}
 };const x=setupVue('task-create',{user,route,modules:mods});return {...x,reads}}
test('release change clears old choices, aborts old read, rejects late response and preserves pending create',async()=>{
 const x=ui();x.user.medicalSession=s();await tick();x.state.project.value={status:'ACTIVE'};x.state.releaseId.value=id(20);
 const read=x.state.loadScope('SOURCE',null);assert.equal(x.reads.length,1);assert.equal(x.reads[0].args[3].agent_release_id,id(20));
 x.state.sourceVersions.value=[id(30)];x.state.collectionVersions.value=[id(31)];x.state.sourceOptions.value=[{version_id:id(30)}];
 const pending={key:id(90)},original={agent_release_id:id(20)};x.state.pending.value=pending;x.state.original.value=original;
 x.state.releaseId.value=id(21);assert.equal(x.reads[0].args[5].aborted,true);assert.equal(x.state.sourceVersions.value.length,0);assert.equal(x.state.collectionVersions.value.length,0);assert.equal(x.state.sourceOptions.value.length,0);
 assert.equal(x.state.pending.value.key,pending.key);assert.equal(x.state.original.value.agent_release_id,id(20));
 x.reads[0].resolve({...responsePage('SOURCE',id(20)),items:[{version_id:id(30)}]});await read;assert.equal(x.state.sourceOptions.value.length,0);assert.equal(x.state.sourcePage.value,null);
 await x.state.loadScope('SOURCE',null);assert.equal(x.reads.length,1);x.stop();
});
test('older release request cannot release a newer picker busy state or replace its cursor',async()=>{
 const x=ui();x.user.medicalSession=s();await tick();x.state.project.value={status:'ACTIVE'};
 await x.state.loadScope('SOURCE',null);assert.equal(x.reads.length,0);
 x.state.releaseId.value=id(20);const old=x.state.loadScope('SOURCE',null);
 x.state.releaseId.value=id(21);const newer=x.state.loadScope('SOURCE',null);assert.equal(x.reads.length,2);
 x.reads[0].resolve(responsePage('SOURCE',id(20)));await old;assert.equal(x.state.busy.value,true);assert.equal(x.state.sourcePage.value,null);
 x.reads[1].resolve(responsePage('SOURCE',id(21)));await newer;assert.equal(x.state.busy.value,false);assert.equal(x.state.sourcePage.value.agent_release_id,id(21));x.stop();
});

test('stale rejected picker request is suppressed while current failures propagate',async()=>{
 const x=ui();x.user.medicalSession=s();await tick();x.state.project.value={status:'ACTIVE'};x.state.releaseId.value=id(20);
 const old=x.state.loadScope('SOURCE',null);x.state.releaseId.value=id(21);const next=x.state.loadScope('SOURCE',null);
 x.reads[0].reject(Error('old aborted transport'));await old;assert.equal(x.state.busy.value,true);
 x.reads[1].reject(Error('current outage'));await assert.rejects(next,/current outage/);assert.equal(x.state.busy.value,false);x.stop();
});
function createBody(){return {schema_version:'1.0.0',project_id:id(10),profile_revision:id(11),document_type:'OVERVIEW',template_version:id(12),agent_release_id:id(20),scope_selection:{mode:'SOURCE_VERSIONS',source_versions:[],collection_versions:[],network_enabled:false,network_policy_revision:null,approved_query_revision:null},owner_id:id(1),goal:'Original task',based_on_document_revision:null}}
test('metadata-only original input can be reconstructed without any request or new key',async()=>{
 const {api,store}=await loadModule('task-create',{request:{get:()=>assert.fail('no GET'),post:()=>assert.fail('no POST')}}),actor=s(),body=createBody();
 const pending=await api.prepareTaskCreate(actor,body),before=[...store.map];
 const restored=await api.reconstructTaskCreate(actor,pending,body);assert.deepEqual(plain(restored),body);assert.deepEqual([...store.map],before);assert.equal(api.pendingTaskCreate(actor,id(10)).key,pending.key);
 await assert.rejects(api.reconstructTaskCreate(actor,pending,{...body,goal:'Different task'}),/原请求摘要/);assert.deepEqual([...store.map],before);
 store.clear();await assert.rejects(api.reconstructTaskCreate(actor,pending,body),/已变化/);assert.equal(store.map.size,0);
});
test('reconstruction rejects replacement pending during digest and cannot reassign original key',async()=>{
 const {api,store}=await loadModule('task-create'),actor=s(),body=createBody(),pending=await api.prepareTaskCreate(actor,body),key=[...store.map.keys()][0];
 const operation=api.reconstructTaskCreate(actor,pending,body);store.setItem(key,JSON.stringify({...pending,key:id(99)}));
 await assert.rejects(operation,/已变化/);assert.equal(api.pendingTaskCreate(actor,id(10)).key,id(99));
});

test('verified missing draft exposes reconstruction but only explicit retry may send',async()=>{
 let attempts=0;const x=ui({taskCreate:{reconstructTaskCreate:async(_s,p,input)=>{attempts++;assert.equal(p.key,id(90));if(input.goal!=='Original task')throw Error('mismatch');return input}}});
 x.user.medicalSession=s();await tick();x.state.pending.value={key:id(90)};x.state.reconstructionText.value=JSON.stringify(createBody());
 await x.state.reconstruct();assert.equal(attempts,0);x.state.unregistered.value=true;
 x.state.reconstructionText.value=JSON.stringify({...createBody(),goal:'wrong'});await x.state.reconstruct();assert.equal(x.state.original.value,null);assert.equal(x.state.reconstructionError.value,'完整原参数无法核验');
 x.state.reconstructionText.value=JSON.stringify(createBody());await x.state.reconstruct();assert.equal(x.state.original.value.goal,'Original task');assert.equal(x.state.pending.value.key,id(90));assert.equal(x.state.reconstructionText.value,'');assert.equal(x.reads.length,0);x.stop();
});

test('structured reconstruction unlocks only after verified absence and arms separate retry without sending',async()=>{
 let verifies=0;const x=ui({taskCreate:{reconstructTaskCreate:async(_s,p,input)=>{verifies++;assert.equal(p.key,id(90));assert.deepEqual(plain(input),createBody());return input},submitTaskCreate:()=>assert.fail('reconstruction cannot send')}});
 x.user.medicalSession=s();await tick();x.state.pending.value={key:id(90)};assert.equal(x.state.formLocked.value,true);
 x.state.unregistered.value=true;assert.equal(x.state.formLocked.value,false);
 x.state.project.value={status:'ACTIVE',profile:{profile_revision:id(11)}};x.state.documentType.value='OVERVIEW';x.state.selected.value={template:{template_version:id(12)}};
 x.state.releaseId.value=id(20);x.state.ownerId.value=id(1);x.state.goal.value='Original task';
 await x.state.submit();assert.equal(verifies,1);assert.equal(x.state.original.value.goal,'Original task');assert.equal(x.state.formLocked.value,true);assert.equal(x.state.pending.value.key,id(90));x.stop();
});

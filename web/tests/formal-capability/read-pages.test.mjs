import test from 'node:test'
import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
import {loadModule,setupVue,session,id,record,tick,response} from './helpers.mjs'
const require=createRequire(import.meta.url),vue=require('vue')
test('list handles empty accessible scan with next cursor and explicitly restarts expired cursor',async()=>{
 const {api:d}=await loadModule('formal-capability-domain'),user=vue.reactive({medicalSession:null,medicalWriting:false}),calls=[]
 let expired=false
 const ui=setupVue('formal-capability-list',{user,modules:{'@/api/medical/formal-capability-domain':d,'@/api/medical/formal-capabilities':{readCapabilityAvailability:async()=>({features:{agent_authoring:true}}),queryCapabilities:async(_r,cursor)=>{calls.push(cursor);if(expired)throw Object.assign(Error('游标过期'),{code:'CURSOR_EXPIRED'});return {items:[],next_cursor:'opaque-server-cursor'}}}}})
 try{user.medicalSession=session();await tick();assert.equal(ui.state.loaded.value,true);assert.equal(ui.state.records.value.length,0);assert.equal(ui.state.cursor.value,'opaque-server-cursor')
  expired=true;await ui.state.load(true);assert.equal(ui.state.cursorExpired.value,true);assert.equal(calls.length,2);await ui.state.load(true);assert.equal(calls.length,2)
  expired=false;ui.state.restart();await tick();assert.deepEqual(calls,[null,'opaque-server-cursor',null]);assert.equal(ui.state.cursorExpired.value,false)
 }finally{ui.stop()}
})
test('list clears identity-owned data immediately and ignores an old request finally after new load begins',async()=>{
 const {api:d}=await loadModule('formal-capability-domain'),user=vue.reactive({medicalSession:null,medicalWriting:false});let firstResolve,secondResolve,count=0
 const ui=setupVue('formal-capability-list',{user,modules:{'@/api/medical/formal-capability-domain':d,'@/api/medical/formal-capabilities':{readCapabilityAvailability:async()=>({features:{agent_authoring:true}}),queryCapabilities:()=>new Promise(r=>{if(++count===1)firstResolve=r;else secondResolve=r})}}})
 try{user.medicalSession=session();await tick();user.medicalSession={...session(),user:{...session().user,user_id:id(10)}};await tick()
  firstResolve({items:[record(d)],next_cursor:null});await tick();assert.equal(ui.state.records.value.length,0);assert.equal(ui.state.loading.value,true)
  secondResolve({items:[],next_cursor:null});await tick();assert.equal(ui.state.loading.value,false);assert.equal(ui.state.records.value.length,0)
 }finally{ui.stop()}
})
test('detail route change clears old record and discards late old detail response',async()=>{
 const {api:d}=await loadModule('formal-capability-domain'),user=vue.reactive({medicalSession:null,medicalWriting:false}),route=vue.reactive({params:{recordId:id(4)}});let firstResolve,secondResolve,count=0
 const ui=setupVue('formal-capability-detail',{user,route,modules:{'@/api/medical/formal-capabilities':{readCapabilityAvailability:async()=>({features:{agent_authoring:true}}),readCapability:()=>new Promise(r=>{if(++count===1)firstResolve=r;else secondResolve=r})}}})
 try{user.medicalSession=session();await tick();route.params.recordId=id(44);await tick();firstResolve(record(d));await tick();assert.equal(ui.state.record.value,null);assert.equal(ui.state.loading.value,true)
  secondResolve({...record(d),record_id:id(44)});await tick();assert.equal(ui.state.record.value.record_id,id(44));ui.doc.hidden=true;ui.listeners.get('visibilitychange')();assert.equal(ui.state.record.value,null)
 }finally{ui.stop()}
})
test('detail does not request a malformed URL record id or accept a disabled deployment',async()=>{
 const user=vue.reactive({medicalSession:null,medicalWriting:false}),route=vue.reactive({params:{recordId:'invalid'}});let calls=0
 const ui=setupVue('formal-capability-detail',{user,route,modules:{'@/api/medical/formal-capabilities':{readCapabilityAvailability:async()=>({features:{agent_authoring:false}}),readCapability:async()=>{calls++;return {}}}}})
 try{user.medicalSession=session();await tick();assert.match(ui.state.error.value,/地址不完整/);assert.equal(calls,0);route.params.recordId=id(4);await tick();assert.equal(ui.state.availability.value,false);assert.equal(calls,0)}finally{ui.stop()}
})
test('actual artifact candidate API retains real enterprise/project items, filters unrelated projects, and preserves cursor',async()=>{
 const requests=[],row=(n,project)=>({artifact_id:id(n),project_id:project,run_id:null,document_revision:null,artifact_kind:'REGISTERED_ARTIFACT',sha256:'a'.repeat(64),size_bytes:'12',media_type:'application/json',created_at:'2026-10-18T00:00:00Z'})
 const request={get:async(url,config)=>{requests.push({url,config});return response({schema_version:'1.0.0',items:[row(3,null),row(5,id(8)),row(6,id(9))],total:'3',limit:50,next_after:id(6)},null)}}
 const {api:a}=await loadModule('formal-capability-candidates',{request}),s=session()
 const result=await a.capabilityArtifactCandidates(()=>s,id(8),null,new AbortController().signal);assert.deepEqual(Array.from(result.items,x=>x.artifact_id),[id(3),id(5)]);assert.equal(result.next_after,id(6));assert.equal(requests[0].url,'/api/artifacts');assert.deepEqual({...requests[0].config.params},{limit:50});assert.equal(result.items[0].sha256,'a'.repeat(64))
})

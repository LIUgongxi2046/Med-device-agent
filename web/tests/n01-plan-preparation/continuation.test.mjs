import test from 'node:test'
import assert from 'node:assert/strict'
import {loadModule,id,response,plain,storage} from './helpers.mjs'
import {current,evaluation,bound,registration,planRequest,preview,prepared,availability} from './fixtures.mjs'
const signal=()=>new AbortController().signal
async function setup() {
 const s=current(), store=storage(), calls=[]; let api, first=true;
 const controls={state:'NOT_FOUND',preview:preview(),feature:true,onPreview:()=>{},onStatus:()=>{},failWrite:false};
 ({api}=await loadModule('n01-plan-preparation',{sessionStorage:store,request:{
  get:async()=>{const a=availability();a.features.plan_preparation=controls.feature;return response(a)},
  post:async(url,body,config)=>{calls.push([url,plain(body),config]);
   if(url.endsWith('/status')){controls.onStatus();return response({schema_version:'1.0.0',state:controls.state,content_available:false,receipt:null})}
   if(url.endsWith('/preview')){controls.onPreview();return response(controls.preview)}
   if(first){first=false;throw Error('pre-intent connection lost')}
   if(controls.failWrite)throw Error('continuation outcome unknown');
   return response(prepared(api),'1',201)
  }
 }}));
 await assert.rejects(api.prepareN01Plan(()=>s,planRequest(),preview(),bound(),registration(api),evaluation(),signal(),()=>{}));
 const pending=api.readN01Pending(s,id(20),'PLAN');calls.length=0;
 return {s,store,calls,controls,api,pending};
}
test('explicit continuation uses exact original key/body after status and fresh preview without new pending identity',async()=>{
 const x=await setup(),{api,s,pending}=x;
 const fresh=await api.previewN01PlanContinuation(()=>s,pending,bound(),registration(api),evaluation(),signal());
 assert.deepEqual(x.calls.map(c=>c[0].split('/').at(-1)),['status','preview']);
 const saved=await api.continueN01Plan(()=>s,pending,fresh,bound(),registration(api),evaluation(),signal());
 const write=x.calls.at(-1);assert.equal(write[0],'/api/formal-evaluation-plan-preparations');assert.equal(write[2].headers['Idempotency-Key'],pending.key);
 assert.deepEqual(write[1],plain(pending.body));assert.equal(saved.key,pending.key);assert.equal(saved.identity_sha256,pending.identity_sha256);assert.equal(saved.receipt.runnable,false);
});
test('existing physical-intent status does not issue a continuation write',async()=>{
 const x=await setup();x.controls.state='PENDING';await assert.rejects(x.api.continueN01Plan(()=>x.s,x.pending,preview(),bound(),registration(x.api),evaluation(),signal()),/已经登记/);
 assert.equal(x.calls.length,1);assert.deepEqual(plain(x.api.readN01Pending(x.s,id(20),'PLAN')),plain(x.pending));
});
test('changed confirmed preview blocks original-key submission',async()=>{
 const x=await setup(),old=preview();x.controls.preview.plan_candidate.sections[0].goal='changed';
 await assert.rejects(x.api.continueN01Plan(()=>x.s,x.pending,old,bound(),registration(x.api),evaluation(),signal()),/预览已变化/);
 assert.equal(x.calls.filter(c=>c[0]==='/api/formal-evaluation-plan-preparations').length,0);
});
test('continuation refuses stale original context, lost writes, completed receipt, disabled deployment and changed definition',async()=>{
 for(const change of [x=>x.s.current_context.context_revision='2',x=>x.s.action_summary=x.s.action_summary.filter(a=>a.action!=='artifact.register'),x=>x.controls.feature=false]){
  const x=await setup();change(x);await assert.rejects(x.api.continueN01Plan(()=>x.s,x.pending,preview(),bound(),registration(x.api),evaluation(),signal()));
  assert.equal(x.calls.filter(c=>c[0]==='/api/formal-evaluation-plan-preparations').length,0);
 }
 const x=await setup(),v=evaluation();v.revision='2';await assert.rejects(x.api.continueN01Plan(()=>x.s,x.pending,preview(),bound(),registration(x.api),v,signal()));assert.equal(x.calls.length,0);
 const changed={...x.pending,receipt:prepared(x.api)};await assert.rejects(x.api.previewN01PlanContinuation(()=>x.s,changed,bound(),registration(x.api),evaluation(),signal()));
});
test('last-await mutation of stored original or current actor prevents transport',async()=>{
 for(const change of [x=>x.s.user.user_id=id(999),x=>x.store.removeItem(x.api.n01StorageKey(x.s,id(20),'PLAN'))]){
  const x=await setup();x.controls.onPreview=()=>change(x);
  await assert.rejects(x.api.continueN01Plan(()=>x.s,x.pending,preview(),bound(),registration(x.api),evaluation(),signal()));
  assert.equal(x.calls.filter(c=>c[0]==='/api/formal-evaluation-plan-preparations').length,0);
 }
});

test('unknown continuation outcome retains the same pending key and immutable body',async()=>{
 const x=await setup();x.controls.failWrite=true;
 await assert.rejects(x.api.continueN01Plan(()=>x.s,x.pending,preview(),bound(),registration(x.api),evaluation(),signal()),/outcome unknown/);
 assert.deepEqual(plain(x.api.readN01Pending(x.s,id(20),'PLAN')),plain(x.pending));
 assert.equal(x.calls.at(-1)[2].headers['Idempotency-Key'],x.pending.key);
});

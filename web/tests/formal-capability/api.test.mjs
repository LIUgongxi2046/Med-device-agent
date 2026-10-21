import test from 'node:test'
import assert from 'node:assert/strict'
import { loadModule, id, session, requestBody, record, receipt, plain, response, storage } from './helpers.mjs'
const signal=()=>new AbortController().signal
const operation=()=>({operation_id:id(7),resource_id:id(4),status:'COMPLETED',status_url:'/api/operations/'+id(7)})
async function fixture(handler) {
 const store=storage(), {api:d}=await loadModule('formal-capability-domain',{sessionStorage:store}), calls=[]
 const request={post:async(url,body,config)=>{calls.push({method:'POST',url,body:plain(body),config});return handler({method:'POST',url,body,config},d)}, get:async(url,config)=>{calls.push({method:'GET',url,config});return handler({method:'GET',url,config},d)}}
 const {api:a}=await loadModule('formal-capabilities',{request,sessionStorage:store});return {a,d,calls,store}
}
test('create sends exact body with schema/context/CSRF/original key and retains typed response',async()=>{
 const f=await fixture((call,d)=>response(receipt(d,call.body),'1',201)),s=session(),body=requestBody(f.d),phases=[]
 const result=await f.a.createCapability(()=>s,body,signal(),p=>phases.push(p.phase))
 assert.deepEqual(phases,['PENDING','UNKNOWN','UNKNOWN']); assert.equal(f.calls.length,1)
 const call=f.calls[0];assert.equal(call.url,'/api/agent-authoring/capabilities');assert.deepEqual(call.body,body)
 assert.equal(call.config.headers['X-Schema-Version'],'1.0.0');assert.equal(call.config.headers['X-Context-Revision'],'1');assert.equal(call.config.headers['X-CSRF-TOKEN'],'csrf-token');assert.equal(call.config.headers['Idempotency-Key'],result.pending.key)
 assert.equal(result.receipt.resource_id,id(4));assert.equal(result.pending.body.source_artifact.version_id,id(3));assert.equal(result.pending.phase,'UNKNOWN');assert.equal(f.store.map.size,1)
 await assert.rejects(f.a.createCapability(()=>s,body,signal(),()=>{}),e=>e.code==='IDEMPOTENCY_CONFLICT');assert.equal(f.calls.length,1)
})
test('network unknown preserves complete original request and read-only recovery never posts again',async()=>{
 let failWrite=true
 const f=await fixture((call,d)=>{if(call.url==='/api/agent-authoring/capabilities'){if(failWrite)throw Error('network disconnected');throw Error('must not resend')}
  if(call.url==='/api/operation-lookups')return response({lookup_status:'FOUND',operation:operation()},null)
  if(call.url==='/api/operations/'+id(7))return response(operation(),null)
  if(call.url.includes('/operation-receipts/'))return response(receipt(d))
  if(call.url==='/api/agent-authoring/records/'+id(4))return response(record(d))
  throw Error('unexpected '+call.url)
 }),s=session(),body=requestBody(f.d)
 await assert.rejects(f.a.createCapability(()=>s,body,signal(),()=>{}),/network disconnected/);const p=f.d.readPendingCapability(s)
 assert.equal(p.phase,'UNKNOWN');assert.deepEqual(plain(p.body),body)
 failWrite=false; const result=await f.a.recoverCapability(()=>s,p,signal())
 assert.equal(result.record.resource_id,id(6));assert.equal(f.store.map.size,0)
 assert.equal(f.calls.filter(c=>c.url==='/api/agent-authoring/capabilities').length,1)
 assert.equal(f.calls.some(c=>c.url==='/api/agent-authoring/records/query'),false)
 const lookup=f.calls.find(c=>c.url==='/api/operation-lookups');assert.deepEqual(lookup.body,{operation_id:'AGENT-PUBLICATION-FORMAL_CAPABILITY_AUTHOR',idempotency_key:p.key})
 assert.equal(lookup.config.headers['Idempotency-Key'],undefined)
})
test('NOT_FOUND, EXPIRED, PROCESSING and FAILED preserve pending and expose no replacement write',async()=>{
 for(const result of [{lookup_status:'NOT_FOUND',operation:null},{lookup_status:'EXPIRED',operation:null},{lookup_status:'FOUND',operation:{...operation(),status:'PROCESSING'}},{lookup_status:'FOUND',operation:{...operation(),status:'FAILED'}}]){
  const f=await fixture(()=>response(result,null)),s=session(),p=await f.d.prepareCapability(()=>s,requestBody(f.d),signal())
  const value=await f.a.recoverCapability(()=>s,p,signal());assert.equal(value.record,null);assert.equal(f.store.map.size,1)
  assert.deepEqual(f.calls.map(c=>c.url),['/api/operation-lookups'])
 }
})
test('typed recovery mismatch, creator mismatch and record hash mismatch never clear pending',async()=>{
 for(const mode of ['receipt','creator','hash']){
  const f=await fixture((call,d)=>{
   if(call.url==='/api/operation-lookups')return response({lookup_status:'FOUND',operation:operation()},null)
   if(call.url.startsWith('/api/operations/'))return response(operation(),null)
   if(call.url.includes('/operation-receipts/'))return response({...receipt(d),...(mode==='receipt'?{request_body_sha256:'0'.repeat(64)}:{})})
   const v=record(d);if(mode==='creator')v.content.created_by=id(55);if(mode==='hash')v.content_sha256='0'.repeat(64);return response(v)
  }),s=session(),p=await f.d.prepareCapability(()=>s,requestBody(f.d),signal())
  await assert.rejects(f.a.recoverCapability(()=>s,p,signal()));assert.equal(f.store.map.size,1)
 }
})
test('query is capability-only with opaque cursor, CSRF but no idempotency header or query string',async()=>{
 const f=await fixture((call,d)=>response({schema_version:'1.0.0',items:[record(d)],next_cursor:null})),s=session()
 const result=await f.a.queryCapabilities(()=>s,null,signal());assert.equal(result.items.length,1)
 const call=f.calls[0];assert.deepEqual(call.body,{schema_version:'1.0.0',kind:'FORMAL_CAPABILITY_AUTHOR',cursor:null});assert.equal(call.config.params,undefined);assert.equal(call.config.headers['Idempotency-Key'],undefined);assert.equal(call.config.headers['X-CSRF-TOKEN'],'csrf-token')
})
test('query rejects duplicate records, foreign modes and unverified fixed content',async()=>{
 for(const variant of ['duplicate','policy','hash']){
  const f=await fixture((call,d)=>{const v=record(d);if(variant==='policy')v.kind='POLICY';if(variant==='hash')v.content_sha256='0'.repeat(64);return response({schema_version:'1.0.0',items:variant==='duplicate'?[v,v]:[v],next_cursor:null})})
  const s=session();await assert.rejects(f.a.queryCapabilities(()=>s,null,signal()))
 }
})
test('identity switch while a read is in flight discards response before publication',async()=>{
 let resolve; const f=await fixture(()=>new Promise(r=>{resolve=r}));let s=session()
 const pending=f.a.readCapability(()=>s,id(4),signal());s={...s,user:{...s.user,user_id:id(80)}};resolve(response(record(f.d)))
 await assert.rejects(pending,e=>e.code==='CONTEXT_CHANGED')
})
test('availability has exact route/current revision and bad no-store/schema headers are rejected',async()=>{
 let bad=false;const f=await fixture((call,d)=>{const r=response({schema_version:'1.0.0',current_context:session().current_context,features:{agent_authoring:false}});if(bad)r.headers['cache-control']='public';return r}),s=session()
 assert.equal((await f.a.readCapabilityAvailability(()=>s,signal())).features.agent_authoring,false);assert.equal(f.calls[0].url,'/api/agent-authoring-feature-availability')
 bad=true;await assert.rejects(f.a.readCapabilityAvailability(()=>s,signal()),e=>e.code==='SCHEMA_INVALID')
})
test('missing original actor after reload or tampered pending cannot initiate even a recovery lookup',async()=>{
 const f=await fixture(()=>{throw Error('should not call')}),s=session(),p=await f.d.prepareCapability(()=>s,requestBody(f.d),signal())
 const other={...s,user:{...s.user,user_id:id(81)}};await assert.rejects(f.a.recoverCapability(()=>other,p,signal()),e=>e.code==='CONTENT_MISMATCH');assert.equal(f.calls.length,0)
 const bad={...p,body:{...p.body,reason:'tampered'}};await assert.rejects(f.a.recoverCapability(()=>s,bad,signal()));assert.equal(f.calls.length,0)
})
test('ROLE API accepts verified narrow inheritance and sends only the unchanged fixed declaration contract', async () => {
 const f = await fixture((call, d) => response(receipt(d, call.body), '1', 201)), s = session()
 s.action_summary.push({ action: 'artifact.register', resource_kind: 'ENTERPRISE', resource_id: id(2), project_id: null })
 const resource = { actor: id(1), context: plain(s.current_context), label: '真实项目', scope: { tenant_id: id(2), project_id: id(8), resource_kind: 'PROJECT', resource_id: id(8) } }
 const body = { ...requestBody(f.d, 'ROLE'), project_id: id(8), tool_scope: { schema_version: '1.0.0', permissions: [{ scope: resource.scope, action: 'artifact.register', purpose: 'EVALUATION_RESULTS' }] } }
 await assert.rejects(f.a.createCapability(() => s, body, signal(), () => {}), e => e.code === 'ACCESS_DENIED')
 assert.equal(f.calls.length, 0); assert.equal(f.store.map.size, 0)
 const result = await f.a.createCapability(() => s, body, signal(), () => {}, [resource])
 assert.equal(f.calls.length, 1); assert.deepEqual(f.calls[0].body, body)
 assert.equal('resources' in f.calls[0].body, false); assert.equal(result.pending.body.tool_scope.permissions[0].purpose, 'EVALUATION_RESULTS')
})

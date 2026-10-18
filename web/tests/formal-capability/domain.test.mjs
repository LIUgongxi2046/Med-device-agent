import test from 'node:test'
import assert from 'node:assert/strict'
import { loadModule, id, session, requestBody, record, receipt, plain, digest } from './helpers.mjs'
const signal = () => new AbortController().signal

test('actual frozen contract accepts only production ROLE/PROMPT and closed exact ResourceScope', async () => {
 const {api:d} = await loadModule('formal-capability-domain'), body = requestBody(d)
 assert.equal(d.capabilityInputIssue(body), '')
 for (const patch of [{ execution_mode: 'SIMULATION' }, { kind: 'SKILL' }, { kind: 'TOOL' }, { unexpected: true }, { tool_scope: [{ action: 'artifact.read' }] }, { reason: ' ' }]) assert.notEqual(d.capabilityInputIssue({...body,...patch}), '')
 const role = requestBody(d,'ROLE'); assert.equal(d.capabilityInputIssue(role),'')
 for (const scope of [[], { schema_version:'1.0.0', permissions:[] }, { schema_version:'1.0.0',permissions:[role.tool_scope.permissions[0],role.tool_scope.permissions[0]] }]) assert.notEqual(d.capabilityInputIssue({...role,tool_scope:scope}),'')
 for(const permission of [{ action:'artifact.read',purpose:'AGENT_PUBLICATION',scope:{...role.tool_scope.permissions[0].scope,resource_id:null} },{...role.tool_scope.permissions[0],effect:'allow'}]) assert.notEqual(d.capabilityInputIssue({...role,tool_scope:{schema_version:'1.0.0',permissions:[permission]}}),'')
})
test('input/output bindings come from actual published catalogue; limits and exact keys fail closed', async () => {
 const {api:d} = await loadModule('formal-capability-domain'), body=requestBody(d)
 assert.notEqual(d.capabilityInputIssue({...body,input_schema_ref:{...body.input_schema_ref,schema_sha256:'f'.repeat(64)}}),'')
 assert.notEqual(d.capabilityInputIssue({...body,reason:'x'.repeat(2001)}),'')
 assert.throws(()=>d.capabilityValidate('RuntimePermission',{action:'artifact.read',purpose:'agent_publication',scope:{}}))
})
test('scope candidates use exact current tenant/project/resource/action without wildcard expansion', async () => {
 const {api:d}=await loadModule('formal-capability-domain'), s=session()
 s.action_summary.push({ action:'source.read',resource_kind:'SOURCE',resource_id:null,project_id:null },{action:'source.read',resource_kind:'PROJECT',resource_id:id(8),project_id:id(8)}, {action:'source.read',resource_kind:'ENTERPRISE',resource_id:id(99),project_id:null})
 assert.equal(d.capabilityScopeCandidates(s,null).length,2)
 assert.equal(d.capabilityScopeCandidates(s,id(8)).length,3)
 const role=requestBody(d,'ROLE'); d.assertCapabilityScopes(s,role)
 role.tool_scope.permissions[0].scope.resource_id=id(66); assert.throws(()=>d.assertCapabilityScopes(s,role),e=>e.code==='ACCESS_DENIED')
 assert.equal(d.capabilityAllowed(s,id(88)),true); s.action_summary=[]; assert.equal(d.capabilityAllowed(s,null),false)
})
test('prepare persists detached full original body and key before dispatch, then forbids new write', async () => {
 const {api:d,store}=await loadModule('formal-capability-domain'), s=session(), body=requestBody(d)
 const p=await d.prepareCapability(()=>s,body,signal()); assert.equal(p.phase,'PENDING'); assert.equal(p.body_sha256,digest(d,body)); assert.equal(store.map.size,1)
 body.reason='修改未发送表单'; assert.notEqual(p.body.reason,body.reason); assert.equal(d.readPendingCapability(s).body.reason,p.body.reason)
 await assert.rejects(d.prepareCapability(()=>s,p.body,signal()),e=>e.code==='IDEMPOTENCY_CONFLICT')
 const sent=await d.markCapabilityDispatched(()=>s,p,signal()); assert.equal(sent.phase,'UNKNOWN'); assert.equal(sent.key,p.key); assert.deepEqual(plain(sent.body),plain(p.body))
 await assert.rejects(d.markCapabilityDispatched(()=>s,sent,signal()),e=>e.code==='ACTION_STALE')
})
test('pending is isolated by actor and tenant, corrupted bodies and unrecognized fields block instead of clearing', async () => {
 const {api:d,store}=await loadModule('formal-capability-domain'), s=session(), p=await d.prepareCapability(()=>s,requestBody(d),signal())
 assert.equal(d.readPendingCapability({...s,user:{...s.user,user_id:id(88)}}),null)
 assert.equal(d.readPendingCapability({...s,current_context:{...s.current_context,tenant_id:id(99)}}),null)
 const key=[...store.map.keys()][0]; store.setItem(key,JSON.stringify({...p,body:{...p.body,reason:'篡改正文'}}))
 await assert.rejects(d.verifyPendingCapability(s,d.readPendingCapability(s)),e=>e.code==='CONTENT_MISMATCH'); assert.equal(store.map.size,1)
 store.setItem(key,JSON.stringify({...p,extra:true})); assert.throws(()=>d.readPendingCapability(s),e=>e.code==='CONTENT_MISMATCH'); assert.equal(store.map.size,1)
})
test('context/identity changes during hashing do not create a pending request', async () => {
 const {api:d,store}=await loadModule('formal-capability-domain'); let s=session()
 const running=d.prepareCapability(()=>s,requestBody(d),signal()); s={...s,current_context:{...s.current_context,context_revision:'2'}}
 await assert.rejects(running,e=>e.code==='CONTEXT_CHANGED'); assert.equal(store.map.size,0)
})
test('storage quota failure aborts before a dispatchable request exists', async () => {
 const {api:d,store}=await loadModule('formal-capability-domain'), s=session();store.setItem=()=>{throw new Error('QuotaExceededError')}
 await assert.rejects(d.prepareCapability(()=>s,requestBody(d),signal()), /QuotaExceededError/)
 assert.equal(store.map.size,0)
})
test('record verification binds actual version, journal id, tenant, content hash, fixed kind and DRAFT', async () => {
 const {api:d}=await loadModule('formal-capability-domain'), s=session(), v=record(d)
 assert.equal((await d.verifyCapabilityRecord(s,v,'1',v.record_id)).resource_id,id(6))
 for(const bad of [{...v,resource_id:id(30)},{...v,status:'APPROVE'},{...v,production_eligible:true},{...v,kind:'POLICY_DRAFT'},{...v,content_sha256:'f'.repeat(64)},{...v,content:{...v.content,tenant_id:id(30)}}]) await assert.rejects(d.verifyCapabilityRecord(s,bad,'1',v.record_id))
 await assert.rejects(d.verifyCapabilityRecord(s,v,'2',v.record_id)); await assert.rejects(d.verifyCapabilityRecord(s,v,'1',id(31)))
})
test('typed receipt is capability-only and original-body-bound; settlement requires original creator and full body', async () => {
 const {api:d,store}=await loadModule('formal-capability-domain'), s=session(), body=requestBody(d), p=await d.prepareCapability(()=>s,body,signal()), v=record(d,body), r=receipt(d,body,v)
 for(const bad of [{...r,kind:'POLICY'},{...r,request_body_sha256:'0'.repeat(64)},{...r,extra:'x'}]) assert.throws(()=>d.verifyCapabilityReceipt(p,bad,'1'))
 const wrong=record(d,{...body,reason:'另一份声明'}); await assert.rejects(d.settleCapability(()=>s,p,r,wrong,signal())); assert.equal(store.map.size,1)
 const creator={...v,content:{...v.content,created_by:id(33)}}; creator.content_sha256=digest(d,creator.content)
 await assert.rejects(d.settleCapability(()=>s,p,{...r,content_sha256:creator.content_sha256},creator,signal())); assert.equal(store.map.size,1)
 await d.settleCapability(()=>s,p,r,v,signal()); assert.equal(store.map.size,0)
})
test('recovery tolerates a new current context for same actor/tenant but never treats availability as authority', async () => {
 const {api:d}=await loadModule('formal-capability-domain'), s=session(), body=requestBody(d), p=await d.prepareCapability(()=>s,body,signal()), v=record(d,body), r=receipt(d,body,v)
 const fresh={...s,current_context:{...s.current_context,context_revision:'4',governance_epoch:'3'}}
 await d.verifyPendingCapability(fresh,p); await d.settleCapability(()=>fresh,p,r,v,signal())
 const available={schema_version:'1.0.0',current_context:s.current_context,features:{agent_authoring:true}}
 assert.equal(d.verifyCapabilityAvailability(available,s.current_context,'1').features.agent_authoring,true)
 assert.equal(d.capabilityAllowed({...s,action_summary:[]},null),false)
 assert.throws(()=>d.verifyCapabilityAvailability({...available,features:{agent_authoring:true,publication:true}},s.current_context,'1'))
 assert.throws(()=>d.verifyCapabilityAvailability(available,fresh.current_context,'4'))
})

test('duplicate JSON properties in persisted request are rejected without silently changing identity', async () => {
 const {api:d,store}=await loadModule('formal-capability-domain'),s=session();await d.prepareCapability(()=>s,requestBody(d),signal())
 const key=[...store.map.keys()][0],raw=store.getItem(key);store.setItem(key,raw.replace('"phase":"PENDING"','"phase":"PENDING","phase":"UNKNOWN"'))
 assert.throws(()=>d.readPendingCapability(s), e=>e.code==='CONTENT_MISMATCH');assert.equal(store.map.size,1)
})

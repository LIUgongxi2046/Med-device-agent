import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { request } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const seed=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json',root)))
const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000})
try{
 const csrf=await(await api.get('/auth/csrf')).json()
 assert.equal((await api.post('/auth/login',{form:owner,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
 const headers={'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision,[session.csrf.header_name]:session.csrf.token}
 const definition={schema_version:'1.0.0',connector_id:randomUUID(),project_id:seed.project_id,expected_revision:'0',interface_spec:null,account_mapping:null,ownership_scope:null,credential_reference_id:null,credential_revision:null,expires_at:new Date(Date.now()+86400000).toISOString(),classification:'INTERNAL'}
 const profile={name:'SIMULATION 企业资料连接准备 '+Date.now(),owner_user_id:session.user.user_id,description:'SIMULATION 仅登记连接准备事项；客户接口、账号映射、资料归属及凭据均尚未提供，不建立客户连接。',scope_kind:'PROJECT',scope_project_id:seed.project_id}
 const key=randomUUID(),body={schema_version:'1.0.0',profile,definition}
 const created=await api.post('/api/business-connector-configs',{headers:{...headers,'Idempotency-Key':key},data:body})
 assert.equal(created.status(),200,'连接业务资料需要与实际配置同事务保存')
 const value=await created.json()
 assert.deepEqual(value.business_profile.profile,profile)
 assert.equal(value.connected,false);assert.equal(value.state,'BLOCKED');assert.ok(value.blockers.includes('interface_spec'))
 const read=await api.get('/api/connector-configs/'+value.config_revision,{headers})
 assert.equal(read.status(),200);assert.deepEqual((await read.json()).business_profile,value.business_profile)
 const lookup=await api.get('/api/connector-operations/CREATE/'+key,{headers})
 assert.equal(lookup.status(),200);const operation=await lookup.json();assert.equal(operation.state,'FOUND');assert.deepEqual(operation.record.request,body)
 const absent=await api.get('/api/connector-operations/CREATE/'+randomUUID(),{headers})
 assert.equal(absent.status(),200);assert.equal((await absent.json()).state,'NOT_FOUND')
 const directory=await api.get('/api/connector-directory',{headers,params:{project_id:seed.project_id}})
 assert.equal(directory.status(),200);assert.ok((await directory.json()).data.items.some(item=>item.configuration.config_revision===value.config_revision))
 const members=await api.get('/api/enterprise-member-labels',{headers})
 assert.equal(members.status(),200);const labels=(await members.json()).data;assert.equal(labels.tenant_id,session.current_context.tenant_id);assert.ok(labels.items.some(item=>item.user_id===session.user.user_id&&item.member_status==='ACTIVE'))
 for(const kind of ['DECIDE','VERIFY','SYNC']){const history=await api.get(`/api/connector-configs/${value.config_revision}/history/${kind}`,{headers});assert.equal(history.status(),200);const records=await history.json();assert.deepEqual(records.items,[]);assert.equal(records.complete,true);assert.equal(records.next_after,null)}
 const replay=await api.post('/api/business-connector-configs',{headers:{...headers,'Idempotency-Key':key},data:body})
 assert.equal(replay.status(),200);assert.deepEqual((await replay.json()).business_profile,value.business_profile)
 const conflict=await api.post('/api/business-connector-configs',{headers:{...headers,'Idempotency-Key':key},data:{...body,profile:{...profile,name:profile.name+' 参数变化'}}})
 assert.equal(conflict.status(),409);assert.equal((await conflict.json()).code,'IDEMPOTENCY_CONFLICT')
 const decision={decision:'APPROVED',definition_sha256:value.definition_sha256,expected_revision:value.revision,reason:'核查连接准备资料，实际客户接入仍缺少必要输入。'}
 const missing=await api.post(`/api/connector-configs/${value.config_revision}/decisions`,{headers:{...headers,'Idempotency-Key':randomUUID()},data:decision})
 assert.equal(missing.status(),422)
 const blocked=await api.post(`/api/connector-configs/${value.config_revision}/decisions`,{headers:{...headers,'Idempotency-Key':randomUUID(),'X-Connector-Profile-SHA256':value.business_profile.profile_sha256},data:decision})
 assert.equal(blocked.status(),503);assert.equal((await blocked.json()).code,'DEPENDENCY_UNAVAILABLE')
 writeFileSync(new URL('.implementation-work/connector-business-profile-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),config_revision:value.config_revision,checks:['SAME_TRANSACTION_PROFILE','ACTUAL_READ','ORIGINAL_REQUEST_LOOKUP','UNCONFIRMED_REQUEST_PRESERVED','ACTUAL_DIRECTORY','ACTUAL_MEMBER_LABELS','ACTUAL_EMPTY_HISTORY','EXACT_REPLAY','PARAMETER_CONFLICT','APPROVAL_PROFILE_REQUIRED','MISSING_CUSTOMER_INPUT_BLOCKED'],customer_connection_verified:false},null,2),{mode:0o600})
 console.log('连接业务资料、原请求恢复及客户条件阻止核查通过')
}finally{await api.dispose()}

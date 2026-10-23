import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { request } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const member=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-member-credentials.json',root)))
const seed=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json',root)))
const receipt=JSON.parse(readFileSync(new URL('.implementation-work/connector-business-profile-checks.json',root)))
const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000}),anonymous=await request.newContext({baseURL:origin,timeout:30000})
try{
 assert.equal((await anonymous.get('/api/connector-workspace',{headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':'1'}})).status(),401)
 const csrf=await(await api.get('/auth/csrf')).json();assert.equal((await api.post('/auth/login',{form:{username:member.username,password:member.password},headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
 assert.equal(session.user.platform_role,'USER');assert.equal(session.current_context.tenant_id,seed.tenant_id)
 assert.equal(session.action_summary.some(item=>item.action.startsWith('connector.')),false)
 const headers={'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}
 const workspace=await api.get('/api/connector-workspace',{headers});assert.equal(workspace.status(),200);assert.deepEqual((await workspace.json()).data.allowed_actions,[])
 for(const path of ['/api/connector-configs/'+receipt.config_revision,'/api/connector-directory?project_id='+seed.project_id,'/api/connector-configs/'+receipt.config_revision+'/history/DECIDE','/api/connector-operations/CREATE/'+randomUUID(),'/api/enterprise-member-labels']){const denied=await api.get(path,{headers});assert.equal(denied.status(),403);assert.equal((await denied.json()).code,'ACCESS_DENIED')}
 const body={schema_version:'1.0.0',profile:{name:'SIMULATION 受限账户连接准备',description:'SIMULATION 核查未授权连接维护请求被拒绝，不建立客户连接。',owner_user_id:session.user.user_id,scope_kind:'PROJECT',scope_project_id:seed.project_id},definition:{schema_version:'1.0.0',connector_id:randomUUID(),project_id:seed.project_id,expected_revision:'0',interface_spec:null,account_mapping:null,ownership_scope:null,credential_reference_id:null,credential_revision:null,expires_at:new Date(Date.now()+86400000).toISOString(),classification:'INTERNAL'}}
 const denied=await api.post('/api/business-connector-configs',{headers:{...headers,'Idempotency-Key':randomUUID(),[session.csrf.header_name]:session.csrf.token},data:body});assert.equal(denied.status(),403);assert.equal((await denied.json()).code,'ACCESS_DENIED')
 writeFileSync(new URL('.implementation-work/connector-permission-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),checks:['ANONYMOUS_AUTHENTICATION_REQUIRED','UNAUTHORIZED_WORKSPACE_ACTIONS_EMPTY','CONFIGURATION_READ_DENIED','DIRECTORY_DENIED','HISTORY_DENIED','ORIGINAL_REQUEST_DENIED','MEMBER_LABELS_DENIED','CONFIGURATION_WRITE_DENIED'],result:'PASS'},null,2),{mode:0o600})
 console.log('企业连接及成员名称目录的未授权读取和写入均被拒绝')
}finally{await api.dispose();await anonymous.dispose()}

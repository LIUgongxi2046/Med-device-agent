import assert from 'node:assert/strict'
import { existsSync,readFileSync,writeFileSync } from 'node:fs'
import { createHash,randomUUID } from 'node:crypto'
import { request } from '../tests/browser/node_modules/playwright/index.mjs'

const root=new URL('../',import.meta.url),origin='http://127.0.0.1:18083',path=new URL('.implementation-work/runtime/project-directory-workspace-input.json',root)
const workspace=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json',root))),owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
assert.equal(workspace.data_kind,'SIMULATION');assert.equal(workspace.scope,'LOCAL_ACCEPTANCE_WORKSPACE');assert.equal(workspace.state,'CREATED')
const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000})
function save(value){writeFileSync(path,JSON.stringify(value,null,2),{mode:0o600})}
async function current(){const response=await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}});assert.equal(response.status(),200);const session=(await response.json()).data;assert.equal(session.current_context.tenant_id,workspace.tenant_id);assert.equal(session.user.username,'liuhaonan');return session}
function headers(session){return {'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision,[session.csrf.header_name]:session.csrf.token}}
async function confirm(input,projectId,operationId){
 const session=await current(),operation=await api.get('/api/operations/'+operationId,{headers:headers(session)});assert.equal(operation.status(),200);const result=(await operation.json()).data;assert.equal(result.status,'COMPLETED');assert.equal(result.resource_id,projectId)
 const response=await api.get('/api/projects/'+projectId,{headers:headers(session)});assert.equal(response.status(),200);const project=(await response.json()).data
 assert.equal(project.name,input.body.name);assert.equal(project.responsible_user_id,input.actor);assert.equal(project.created_by,input.actor);assert.equal(project.creation_reason,input.body.reason);assert.deepEqual(project.creation_basis,input.body.creation_basis);assert.deepEqual(project.profile.attributes,input.body.attributes);assert.equal(project.status,'PENDING')
 save({...input,state:'CREATED',project_id:projectId,operation_id:operationId,verified_at:new Date().toISOString()});console.log(JSON.stringify({state:'CREATED',project_id:projectId,project_status:project.status,attributes_confirmed:0,scope:'LOCAL_ACCEPTANCE_WORKSPACE'}))
}
try{
 const csrf=await(await api.get('/auth/csrf')).json();assert.equal((await api.post('/auth/login',{form:owner,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const session=await current()
 if(existsSync(path)){
  const input=JSON.parse(readFileSync(path));assert.equal(input.tenant_id,workspace.tenant_id);assert.equal(input.actor,session.user.user_id);assert.equal(input.body_sha256,createHash('sha256').update(JSON.stringify(input.body)).digest('hex'))
  if(input.state==='CREATED')await confirm(input,input.project_id,input.operation_id)
  else{
   assert.equal(input.state,'PREPARED');const response=await api.post('/api/operation-lookups',{headers:headers(session),data:{operation_id:'PJ-02',idempotency_key:input.key}});assert.equal(response.status(),200);const found=(await response.json()).data
   assert.equal(found.lookup_status,'FOUND','原创建请求尚未确认，需要继续核查原操作');assert.equal(found.operation.status,'COMPLETED');assert.ok(found.operation.resource_id);await confirm(input,found.operation.resource_id,found.operation.operation_id)
  }
 }else{
  const original=await api.get('/api/projects/'+workspace.project_id,{headers:headers(session)});assert.equal(original.status(),200);const project=(await original.json()).data
  const body={schema_version:'1.0.0',name:'SIMULATION 项目目录核验工作区',responsible_user_id:session.user.user_id,attributes:[],creation_basis:project.creation_basis,reason:'根据用户的完整功能测试资料要求，建立实际本地项目草稿，核验目录分页与授权范围。产品属性尚未确认，当前记录用于工作站功能验收。',approval_attested:true}
  const input={state:'PREPARED',data_kind:'SIMULATION',scope:'LOCAL_ACCEPTANCE_WORKSPACE',tenant_id:workspace.tenant_id,actor:session.user.user_id,key:randomUUID(),context:session.current_context,body,body_sha256:createHash('sha256').update(JSON.stringify(body)).digest('hex')};save(input)
  const response=await api.post('/api/projects',{headers:{...headers(session),'Idempotency-Key':input.key},data:body});assert.equal(response.status(),201);const receipt=(await response.json()).data;assert.equal(receipt.decision,'CREATE');assert.equal(receipt.status,'PENDING');await confirm(input,receipt.project_id,receipt.operation_id)
 }
}finally{await api.dispose()}

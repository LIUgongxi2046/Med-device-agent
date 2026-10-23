import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { request } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const seed=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json',root)))
const input=JSON.parse(readFileSync(new URL('.implementation-work/runtime/project-directory-workspace-input.json',root)))
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const member=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-member-credentials.json',root)))
const inventory=JSON.parse(readFileSync(new URL('.implementation-work/task-release-directory-inventory.json',root)))
async function login(credentials){const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin}}),csrf=await(await api.get('/auth/csrf')).json();assert.equal((await api.post('/auth/login',{form:credentials,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302);const reply=await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}});assert.equal(reply.status(),200);return {api,session:(await reply.json()).data}}
function headers(actor){return {'X-Schema-Version':'1.0.0','X-Context-Revision':actor.session.current_context.context_revision}}
const actor=await login(owner),ordinary=await login(member),route='/api/projects/'+seed.project_id+'/task-creation-agent-releases',checks=[]
try{
 const response=await actor.api.get(route,{headers:headers(actor),params:{limit:20}});assert.equal(response.status(),200,'任务创建需要实际发布配置目录')
 const wrapped=await response.json(),page=wrapped.data;assert.equal(wrapped.resource_revision,actor.session.current_context.context_revision);assert.equal(page.tenant_id,seed.tenant_id);assert.equal(page.project_id,seed.project_id);assert.deepEqual(page.current_context,actor.session.current_context);assert.deepEqual(page.query,{limit:20,q:null});assert.equal(page.complete,page.next_after===null)
 for(const item of page.items){const stored=inventory.find(value=>value.release_id===item.release_id);assert.ok(stored);assert.equal(stored.status,'PUBLISHED');assert.equal(stored.current,1);assert.equal(item.name,stored.name);assert.equal(item.current,true);assert.equal(item.status,'PUBLISHED');assert.ok(Array.isArray(item.template_versions))}
 if(!inventory.some(value=>value.status==='PUBLISHED'&&value.current===1)){assert.deepEqual(page.items,[]);assert.equal(page.complete,true)}checks.push('ACTUAL_PUBLISHED_CURRENT_VERSIONS_ONLY')
 const searched=await actor.api.get(route,{headers:headers(actor),params:{limit:20,q:'没有已发布版本的目录校验'}});assert.equal(searched.status(),200);const empty=(await searched.json()).data;assert.deepEqual(empty.items,[]);assert.equal(empty.complete,true);checks.push('UNICODE_QUERY_AND_ACTUAL_EMPTY_STATE')
 for(const params of [{limit:0},{unexpected_parameter:'INVALID'},{limit:20,after:'invalid'}]){const invalid=await actor.api.get(route,{headers:headers(actor),params});assert.equal(invalid.status(),422)}checks.push('CLOSED_QUERY_AND_INVALID_CURSOR')
 const projects=await actor.api.get('/api/project-workspace-directory',{headers:headers(actor),params:{limit:1}});assert.equal(projects.status(),200);const foreignCursor=(await projects.json()).data.next_after;assert.ok(foreignCursor)
 const crossed=await actor.api.get(route,{headers:headers(actor),params:{limit:20,after:foreignCursor}});assert.equal(crossed.status(),422);assert.equal((await crossed.json()).code,'CURSOR_INVALID');checks.push('CURSOR_KIND_ISOLATION')
 const pending=await actor.api.get('/api/projects/'+input.project_id+'/task-creation-agent-releases',{headers:headers(actor)});assert.equal(pending.status(),403);checks.push('PENDING_PROJECT_CANNOT_CREATE')
 assert.equal(ordinary.session.action_summary.some(value=>value.action==='task.create'&&(value.project_id===null||value.project_id===seed.project_id)),false,'普通账户当前角色需要重新核查')
 assert.equal((await ordinary.api.get(route,{headers:headers(ordinary)})).status(),403);checks.push('ORDINARY_ACCOUNT_WITHOUT_CREATION_PERMISSION')
 writeFileSync(new URL('.implementation-work/task-release-directory-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),checks,listed:page.items.length,complete:page.complete,published_input_available:inventory.some(value=>value.status==='PUBLISHED'&&value.current===1),screenshots:false},null,2),{mode:0o600})
 console.log('任务发布配置目录、实际发布状态、查询、项目状态和权限核查通过')
}finally{await actor.api.dispose();await ordinary.api.dispose()}

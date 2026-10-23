import assert from 'node:assert/strict'
import { readFileSync,writeFileSync } from 'node:fs'
import { request } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083',seed=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json',root))),input=JSON.parse(readFileSync(new URL('.implementation-work/runtime/project-directory-workspace-input.json',root)))
assert.equal(seed.data_kind,'SIMULATION');assert.equal(input.state,'CREATED');assert.equal(input.tenant_id,seed.tenant_id)
const credentials={owner:JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root))),member:JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-member-credentials.json',root)))},actors=[],checks=[]
async function login(credentials){const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000}),csrf=await(await api.get('/auth/csrf')).json();assert.equal((await api.post('/auth/login',{form:credentials,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302);const session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data;assert.equal(session.current_context.tenant_id,seed.tenant_id);const actor={api,session,headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}};actors.push(actor);return actor}
async function get(actor,path,params){const response=await actor.api.get(path,{headers:actor.headers,params});const body=await response.json();assert.equal(response.status(),200,JSON.stringify(body));return body.data}
async function visibleProjects(actor){let after=null;const items=[];do{const page=await get(actor,'/api/projects',{limit:100,...(after?{after}:{})});items.push(...page.projects);after=page.next_after;assert.ok(items.length<20000)}while(after);return items}
async function materialCounts(actor,project){let after=null;const versions=new Map();do{const page=await get(actor,'/api/source-versions',{limit:100,project_id:project,...(after?{after}:{})});for(const item of page.items){assert.equal(versions.has(item.source_version),false);versions.set(item.source_version,item)}after=page.next_after;assert.ok(versions.size<20000)}while(after);const rows=[...versions.values()].filter(value=>value.ownership!=='SESSION'&&(value.lifecycle===null||value.lifecycle==='ACTIVE'));return {registered:String(rows.length),approved:String(rows.filter(value=>value.approval_status==='APPROVED').length)}}
try{
 const owner=await login(credentials.owner),member=await login(credentials.member)
 let ownerCursor=null,ownerQuery=null
 for(const [kind,actor] of [['owner',owner],['member',member]]){
  const base=await visibleProjects(actor),directory=await get(actor,'/api/project-workspace-directory',{limit:100});assert.equal(directory.total,String(base.length));assert.deepEqual(directory.current_context,actor.session.current_context);assert.deepEqual(directory.items.map(value=>value.project.project_id),base.map(value=>value.project_id))
  for(const item of directory.items){
   const project=item.project,actual=await get(actor,'/api/projects/'+project.project_id),materials=await materialCounts(actor,project.project_id)
   assert.deepEqual(project,actual);assert.equal(item.materials.registered,materials.registered);assert.equal(item.materials.approved,materials.approved);assert.equal(BigInt(item.materials.registered),BigInt(item.materials.approved)+BigInt(item.materials.not_approved));assert.equal(item.responsible.user_id,project.responsible_user_id)
   if(project.responsible_user_id===actor.session.user.user_id){assert.equal(item.responsible.identity_available,true);assert.equal(item.responsible.display_name,actor.session.user.display_name);assert.equal(item.responsible.username,actor.session.user.username)}
   else if(kind==='member'){assert.equal(item.responsible.identity_available,false);assert.equal(item.responsible.display_name,null);assert.equal(item.responsible.username,null)}
  }
  if(kind==='member'){const restricted=await actor.api.get('/api/enterprise-member-labels',{headers:actor.headers});assert.equal(restricted.status(),403)}
  const first=await get(actor,'/api/project-workspace-directory',{limit:1});let after=null,previous='',pages=0;const seen=new Set()
  do{const page=await get(actor,'/api/project-workspace-directory',{limit:1,...(after?{after}:{})});assert.equal(page.total,directory.total);for(const item of page.items){const id=item.project.project_id;assert.ok(previous===''||id>previous);assert.equal(seen.has(id),false);seen.add(id);previous=id}after=page.next_after;pages++;assert.ok(pages<200)}while(after)
  assert.deepEqual([...seen],directory.items.map(value=>value.project.project_id));checks.push({id:kind.toUpperCase()+'_ACTUAL_DIRECTORY_AND_SOURCE_TOTALS',state:'PASS',projects:seen.size,pages})
  if(kind==='owner'){assert.ok(first.next_after);ownerCursor=first.next_after;ownerQuery=first.items[0].project}
 }
 assert.ok(ownerCursor);assert.ok(ownerQuery)
 for(const change of [{q:ownerQuery.name},{status:ownerQuery.status},{responsible_user_id:ownerQuery.responsible_user_id},{limit:2}]){const response=await owner.api.get('/api/project-workspace-directory',{headers:owner.headers,params:{limit:1,after:ownerCursor,...change}});assert.equal(response.status(),422);assert.equal((await response.json()).code,'CURSOR_INVALID')}
 const otherActor=await member.api.get('/api/project-workspace-directory',{headers:member.headers,params:{limit:1,after:ownerCursor}});assert.equal(otherActor.status(),422);assert.equal((await otherActor.json()).code,'CURSOR_INVALID')
 const foreign=await get(owner,'/api/rule-versions',{limit:1});assert.ok(foreign.next_after);const cross=await owner.api.get('/api/project-workspace-directory',{headers:owner.headers,params:{limit:1,after:foreign.next_after}});assert.equal(cross.status(),422);assert.equal((await cross.json()).code,'CURSOR_INVALID')
 const found=await get(owner,'/api/project-workspace-directory',{limit:100,q:ownerQuery.name,status:ownerQuery.status});assert.ok(found.items.some(value=>value.project.project_id===ownerQuery.project_id))
 const empty=await get(owner,'/api/project-workspace-directory',{limit:100,q:'未登记的项目目录搜索验证'});assert.equal(empty.total,'0');assert.deepEqual(empty.items,[]);assert.equal(empty.next_after,null)
 for(const query of [{limit:0},{status:'INVALID'},{q:' '},{unexpected_parameter:'INVALID'}]){const response=await owner.api.get('/api/project-workspace-directory',{headers:owner.headers,params:query});assert.equal(response.status(),422);assert.equal((await response.json()).code,'SCHEMA_INVALID')}
 checks.push({id:'PROJECT_CURSOR_SCOPE_AND_FILTERS',state:'PASS'},{id:'PROJECT_MEMBER_IDENTITY_PERMISSION',state:'PASS'},{id:'PROJECT_COMBINED_QUERY_AND_EMPTY_RESULT',state:'PASS'})
 writeFileSync(new URL('.implementation-work/project-workspace-api-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),checks,screenshots:false},null,2),{mode:0o600});console.log('项目目录实际资料统计、完整分页、普通账户范围及游标绑定核查通过')
}finally{for(const actor of actors)await actor.api.dispose()}

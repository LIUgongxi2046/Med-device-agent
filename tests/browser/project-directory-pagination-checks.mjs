import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { request } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083',owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000})
try{
 const csrf=await(await api.get('/auth/csrf')).json();assert.equal((await api.post('/auth/login',{form:owner,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data,headers={'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}
 const first=await api.get('/api/projects',{headers,params:{limit:1}});assert.equal(first.status(),200);const initial=(await first.json()).data;assert.ok(initial.projects.length)
 const current=initial.projects[0],after=await api.get('/api/projects',{headers,params:{limit:1,after:current.project_id}});assert.equal(after.status(),200);const next=(await after.json()).data
 assert.equal(next.total,initial.total,'项目总数需要代表完整筛选范围，后续游标不能改变总数')
 const filtered=await api.get('/api/projects',{headers,params:{limit:1,name:current.name,status:current.status}});assert.equal(filtered.status(),200);const result=(await filtered.json()).data;assert.ok(result.projects.some(value=>value.project_id===current.project_id))
 for(const project of result.projects){assert.equal(project.status,current.status);assert.ok(project.name.includes(current.name))}
 const empty=await api.get('/api/projects',{headers,params:{limit:1,name:'未登记的项目目录搜索验证'}});assert.equal(empty.status(),200);const none=(await empty.json()).data;assert.deepEqual(none.projects,[]);assert.equal(none.total,'0');assert.equal(none.next_after,null)
 writeFileSync(new URL('.implementation-work/project-directory-pagination-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),checks:['ACTUAL_AUTHORIZED_TOTAL','CURSOR_PRESERVES_FILTERED_TOTAL','ACTUAL_NAME_AND_STAGE_FILTERS','ACTUAL_EMPTY_RESULT'],total:initial.total,screenshots:false},null,2),{mode:0o600});console.log('项目目录完整范围总数、实际筛选及游标查询核查通过')
}finally{await api.dispose()}

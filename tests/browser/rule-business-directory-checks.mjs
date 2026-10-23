import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { request } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root))),receipt=JSON.parse(readFileSync(new URL('.implementation-work/rule-business-ui-checks.json',root)))
const id=receipt.checks.find(item=>item.id==='PR02-UI-APPROVAL-READ').rule_version,api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000})
try{
 const csrf=await(await api.get('/auth/csrf')).json();assert.equal((await api.post('/auth/login',{form:owner,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data,headers={'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}
 const read=await api.get('/api/rule-versions/'+id,{headers});assert.equal(read.status(),200);const rule=(await read.json()).data.rule
 const business=await api.get('/api/rule-versions/'+id+'/business-definition',{headers});assert.equal(business.status(),200);const definition=(await business.json()).data.definition
 const input={limit:1,q:definition.name,dimension:definition.dimension,handling:definition.handling,status:rule.status}
 const response=await api.get('/api/business-rule-directory',{headers,params:input});assert.equal(response.status(),200,'业务规则目录需要执行完整组合筛选')
 const page=(await response.json()).data;assert.equal(page.query.q,definition.name);assert.equal(page.query.dimension,definition.dimension);assert.equal(page.query.handling,definition.handling);assert.equal(page.query.status,rule.status);assert.ok(page.items.some(item=>item.resource.rule.rule_version===id))
 for(const item of page.items){assert.equal(item.resource.rule.status,rule.status);assert.equal(item.business.definition.dimension,definition.dimension);assert.equal(item.business.definition.handling,definition.handling);assert.equal(item.business.rule_version,item.resource.rule.rule_version);assert.equal(item.business.rule_content_sha256,item.resource.rule.content_sha256)}
 const first=await api.get('/api/business-rule-directory',{headers,params:{limit:1}});assert.equal(first.status(),200);const initial=(await first.json()).data;assert.ok(initial.next_after,'实际规则目录需要具有后续分页')
 const coreCursor=await api.get('/api/rule-versions',{headers,params:{limit:1,after:initial.next_after}}),coreFailure=await coreCursor.json();assert.equal(coreCursor.status(),422,JSON.stringify(coreFailure));assert.equal(coreFailure.code,'CURSOR_INVALID')
 for(const change of [{q:'不同查询'},{dimension:definition.dimension},{handling:definition.handling},{status:rule.status},{limit:2}]){const changed=await api.get('/api/business-rule-directory',{headers,params:{limit:1,after:initial.next_after,...change}});assert.equal(changed.status(),422);assert.equal((await changed.json()).code,'CURSOR_INVALID')}
 let cursor=null,last=null,pages=0;const ids=new Set()
 do{const actual=await api.get('/api/business-rule-directory',{headers,params:{limit:1,...(cursor?{after:cursor}:{})}});assert.equal(actual.status(),200);const directory=(await actual.json()).data;for(const item of directory.items){const current=item.resource.rule.rule_version;assert.equal(ids.has(current),false);assert.ok(last===null||current>last);ids.add(current);last=current}cursor=directory.next_after;pages++;assert.ok(pages<200)}while(cursor)
 assert.ok(ids.has(id));assert.ok(pages>1)
 for(const params of [{dimension:'INVALID'},{handling:'INVALID'},{status:'INVALID'},{limit:0},{q:' '},{unexpected_parameter:'INVALID'}]){const invalid=await api.get('/api/business-rule-directory',{headers,params:{limit:1,...params}});assert.equal(invalid.status(),422)}
 writeFileSync(new URL('.implementation-work/rule-business-directory-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),rule_version:id,pages,rules:ids.size,checks:['ACTUAL_COMBINED_FILTERS','RULE_AND_BUSINESS_SAME_VERSION','COMPLETE_ACTUAL_PAGINATION','CURSOR_BOUND_TO_ALL_FILTERS','INVALID_FILTERS_REJECTED']},null,2),{mode:0o600});console.log('业务规则组合筛选、固定定义与完整游标分页核查通过')
}finally{await api.dispose()}

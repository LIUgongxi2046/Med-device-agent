import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { request } from 'playwright'
import { randomUUID } from 'node:crypto'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root))),member=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-member-credentials.json',root)))
const receipt=JSON.parse(readFileSync(new URL('.implementation-work/template-profile-controls-checks.json',root)))
async function login(credentials){const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000}),csrf=await(await api.get('/auth/csrf')).json();assert.equal((await api.post('/auth/login',{form:{username:credentials.username,password:credentials.password},headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302);const session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data;return {api,session,headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}}}
const authorized=await login(owner),restricted=await login(member)
try{
 const {api,headers}=authorized,id=receipt.template_version
 const read=await api.get('/api/template-versions/'+id+'/definition-view',{headers});assert.equal(read.status(),200)
 const value=(await read.json()).data;assert.equal(value.template.template_version,id);assert.deepEqual(value.allowed_actions,[])
 const baseline=await api.get('/api/template-versions/'+id+'/approval-baseline',{headers});assert.equal(baseline.status(),200);assert.deepEqual((await baseline.json()).data.template,value.template)
 for(const actor of [authorized,restricted]){const search=await actor.api.get('/api/templates',{headers:actor.headers,params:{limit:25,q:value.template.reason}});const result=await search.json();assert.equal(search.status(),200,JSON.stringify(result));assert.ok(result.data.items.some(item=>item.template_version===id))}
 const ruleReceipt=JSON.parse(readFileSync(new URL('.implementation-work/rule-business-ui-checks.json',root))),ruleId=ruleReceipt.checks.find(item=>item.id==='PR02-UI-APPROVAL-READ').rule_version
 const ruleBusiness=await api.get('/api/rule-versions/'+ruleId+'/business-definition',{headers});assert.equal(ruleBusiness.status(),200);const ruleName=(await ruleBusiness.json()).data.definition.name
 const ruleSearch=await api.get('/api/rule-versions',{headers,params:{limit:25,q:ruleName}});assert.equal(ruleSearch.status(),200);assert.ok((await ruleSearch.json()).data.items.some(item=>item.rule.rule_version===ruleId))
 const modelSearch=await api.get('/api/model-config-revisions',{headers,params:{limit:25,q:'未登记的中文目录搜索验证'}});assert.equal(modelSearch.status(),200);assert.deepEqual((await modelSearch.json()).data.items,[])
 assert.equal(restricted.session.action_summary.some(item=>item.action==='template.read'),true);assert.equal(restricted.session.action_summary.some(item=>item.action==='template.write'),true);assert.equal(restricted.session.action_summary.some(item=>item.action==='template.approve'),false)
 const reader=await restricted.api.get('/api/template-versions/'+id+'/definition-view',{headers:restricted.headers});assert.equal(reader.status(),200);const readOnly=(await reader.json()).data;assert.deepEqual(readOnly.allowed_actions,[]);assert.deepEqual(readOnly.template,value.template)
 const denied=await restricted.api.get('/api/template-versions/'+id+'/approval-baseline',{headers:restricted.headers});assert.equal(denied.status(),403);assert.equal((await denied.json()).code,'ACCESS_DENIED')
 const forbidden=await restricted.api.post('/api/template-versions/'+id+'/approval-decisions',{headers:{...restricted.headers,'Idempotency-Key':randomUUID(),[restricted.session.csrf.header_name]:restricted.session.csrf.token},data:{schema_version:'1.0.0',target_revision:id,expected_revision:readOnly.template.revision,decision:'RETURN',reason:'SIMULATION 核查模板编辑人员没有独立人工核准权限。',baseline_manifest:readOnly.baseline_manifest}});assert.equal(forbidden.status(),403);assert.equal((await forbidden.json()).code,'ACCESS_DENIED')
 const seen=new Set(),cursors=new Set();let after=null,pages=0,last=null
 do{const response=await api.get('/api/templates',{headers,params:{limit:1,...(after?{after}:{})}});assert.equal(response.status(),200);const page=(await response.json()).data;assert.ok(page.items.length<=1);for(const item of page.items){assert.equal(seen.has(item.template_version),false);assert.ok(last===null||item.template_version>last);seen.add(item.template_version);last=item.template_version}after=page.next_after;if(after){assert.equal(cursors.has(after),false);cursors.add(after)}pages++;assert.ok(pages<200)}while(after)
 assert.ok(seen.has(id));assert.ok(pages>1)
 const visible=await restricted.api.get('/api/templates',{headers:restricted.headers,params:{limit:100}});assert.equal(visible.status(),200);assert.ok((await visible.json()).data.items.some(item=>item.template_version===id))
 writeFileSync(new URL('.implementation-work/template-definition-read-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),template_version:id,checks:['EXPLICIT_DEFINITION_READ','NO_APPROVAL_ACTION_EXPOSED','EXACT_CONTENT_WITH_APPROVAL_VIEW','TEMPLATE_EDITOR_READ_WITHOUT_APPROVAL','APPROVAL_BASELINE_DENIED','APPROVAL_DECISION_DENIED','READER_DIRECTORY_VISIBLE','COMPLETE_ACTUAL_CURSOR_PAGINATION','DIRECTORY_UNICODE_SEARCH','RULE_UNICODE_SEARCH','MODEL_UNICODE_EMPTY_SEARCH'],pages,templates:seen.size},null,2),{mode:0o600});console.log('完整模板读取、核准权限区分及实际游标分页核查通过')
}finally{await authorized.api.dispose();await restricted.api.dispose()}

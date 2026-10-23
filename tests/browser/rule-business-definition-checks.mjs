import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { request } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const workflow=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-template-workflow.json',root)))
const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000})
try{
 const csrf=await(await api.get('/auth/csrf')).json()
 assert.equal((await api.post('/auth/login',{form:owner,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 let session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
 const headers=()=>({'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision,[session.csrf.header_name]:session.csrf.token})
 const legacy=await api.get(`/api/rule-versions/${workflow.rule_version}/business-definition`,{headers:headers()})
 assert.equal(legacy.status(),200,'已有规则需要明确返回业务定义登记状态')
 assert.equal((await legacy.json()).data,null)
 const input=structuredClone(workflow.rule_input)
 input.reason='SIMULATION 公开编辑资料业务规则验证 '+Date.now()
 const definition={name:'公开资料编号完整性检查',dimension:'CONTENT_COMPLETENESS',condition:'核查公开编辑样例的必需编号属性已经提供。',handling:'MANUAL_CONFIRMATION',classification:'INTERNAL'}
 const body={schema_version:'1.0.0',rule:input,definition},key=randomUUID()
 const response=await api.post('/api/business-rule-versions',{headers:{...headers(),'Idempotency-Key':key},data:body})
 assert.equal(response.status(),201,'业务规则必须保存名称、维度、判断条件、处置和资料分类')
 const created=(await response.json()).data,id=created.rule.rule_version
 const read=await api.get(`/api/rule-versions/${id}/business-definition`,{headers:headers()})
 assert.equal(read.status(),200)
 const original=(await read.json()).data
 assert.deepEqual(original.definition,definition)
 assert.equal(original.rule_content_sha256,created.rule.content_sha256)
 const replay=await api.post('/api/business-rule-versions',{headers:{...headers(),'Idempotency-Key':key},data:body})
 assert.equal(replay.status(),201)
 assert.deepEqual((await replay.json()).data,created)
 const conflict=await api.post('/api/business-rule-versions',{headers:{...headers(),'Idempotency-Key':key},data:{...body,definition:{...definition,name:'不同的完整参数'}}})
 assert.equal(conflict.status(),409)
 assert.equal((await conflict.json()).code,'IDEMPOTENCY_CONFLICT')
 const baseline=(await(await api.get(`/api/rule-versions/${id}/approval-baseline`,{headers:headers()})).json()).data
 const decision={schema_version:'1.0.0',target_revision:id,expected_revision:baseline.rule.revision,decision:'APPROVE',reason:'SIMULATION 已核查公开编辑资料规则的业务定义、完整表达式及实际依据，仅用于本地编辑流程。',baseline_manifest:baseline.baseline_manifest}
 const missing=await api.post(`/api/rule-versions/${id}/approval-decisions`,{headers:{...headers(),'Idempotency-Key':randomUUID()},data:decision})
 assert.equal(missing.status(),422)
 const approved=await api.post(`/api/rule-versions/${id}/approval-decisions`,{headers:{...headers(),'Idempotency-Key':randomUUID(),'X-Rule-Business-SHA256':original.definition_sha256},data:decision})
 assert.equal(approved.status(),200,await approved.text())
 assert.equal((await approved.json()).data.status,'APPROVED')
 session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
 const verified=(await(await api.get(`/api/rule-versions/${id}/business-definition`,{headers:headers()})).json()).data
 assert.deepEqual(verified,original)
 writeFileSync(new URL('.implementation-work/rule-business-definition-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),rule_version:id,checks:['BUSINESS_CREATE_READ','EXACT_REPLAY','PARAMETER_CONFLICT','APPROVAL_HASH_REQUIRED','IMMUTABLE_APPROVED_DEFINITION'],professional_approval:false},null,2),{mode:0o600})
 console.log('业务规则保存、完整参数恢复及人工审核绑定通过')
}finally{await api.dispose()}

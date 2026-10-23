import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { request } from 'playwright'
const origin='http://127.0.0.1:18083',root=new URL('../../',import.meta.url)
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000})
try{
 const csrf=await(await api.get('/auth/csrf')).json()
 assert.equal((await api.post('/auth/login',{form:owner,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
 const response=await api.get('/api/document-workspace-availability',{headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}})
 assert.equal(response.status(),200,'文稿工作区需要提供实际服务状态接口')
 const envelope=await response.json(),value=envelope.data
 assert.equal(envelope.resource_revision,session.current_context.context_revision)
 assert.deepEqual(value.current_context,session.current_context)
 for(const key of ['candidate_adoption_enabled','professional_review_enabled','review_confirmation_enabled','document_signing_enabled'])assert.equal(typeof value[key],'boolean')
 writeFileSync(new URL('.implementation-work/document-workspace-contract-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),availability:value,checks:[{id:'PR05-AVAILABILITY',state:'PASS'}]},null,2),{mode:0o600})
 console.log('文稿工作区服务状态核查通过')
}finally{await api.dispose()}

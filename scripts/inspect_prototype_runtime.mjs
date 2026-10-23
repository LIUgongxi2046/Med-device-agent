import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { request } from '../tests/browser/node_modules/playwright/index.mjs'
const origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('../.implementation-work/runtime/owner-credentials.json',import.meta.url)))
const api=await request.newContext({baseURL:origin,extraHTTPHeaders:{Origin:origin},timeout:120000})
try{
  const csrf=await(await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/auth/login',{form:owner,headers:{[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
  const session=(await(await api.get('/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
  console.log(JSON.stringify({context:session.current_context,actions:session.action_summary.filter(item=>/^(template\.|rule\.|report\.|feedback\.)/.test(item.action))},null,2))
  const response=await api.get('/api/rule-versions?limit=50',{headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}})
  assert.equal(response.status(),200)
  console.log(JSON.stringify((await response.json()).data.items.map(item=>({id:item.rule.rule_version,reason:item.rule.reason,status:item.rule.status})),null,2))
}finally{await api.dispose()}

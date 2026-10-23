import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { request } from '../tests/browser/node_modules/playwright/index.mjs'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const api = await request.newContext({ baseURL: origin, extraHTTPHeaders: { Origin: origin }, timeout: 90000 })
try {
  const csrf = await (await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/auth/login', { form: owner, headers: { [csrf.header_name]: csrf.token }, maxRedirects: 0 })).status(), 302)
  const session = (await (await api.get('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()).data
  for (const path of process.argv.slice(2)) {
    assert.ok(path.startsWith('/api/') && !path.includes('auth'))
    console.log('READ', path)
    const started = performance.now()
    const response = await api.get(path, { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision } })
    const body = await response.json()
    console.log(JSON.stringify({ path, status: response.status(), elapsed_ms: Math.round(performance.now() - started), code: body.code, message: body.message, fields: Object.keys(body.data ?? body), items: body.data?.items?.length, projects: body.data?.projects?.length, profile_keys: body.data?.profile?.attributes?.map(value=>({key:value.key,status:value.status})), document_ids: body.data?.document_ids, allowed_actions: body.data?.allowed_actions, error_details: body.details }))
  }
} finally { await api.dispose() }

import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { request } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const credentials = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const context = await request.newContext({ baseURL: origin, extraHTTPHeaders: { Origin: origin } })
try {
  const csrf = await (await context.get('/auth/csrf')).json()
  const login = await context.post('/auth/login', { form: { username: credentials.username, password: credentials.password, _csrf: csrf.token } })
  assert.equal(login.status(), 200)
  const read = async path => {
    const response = await context.get(path, { headers: { 'X-Schema-Version': '1.0.0' } })
    assert.equal(response.status(), 200)
    return (await response.json()).data
  }
  const members = (await read('/api/session')).memberships.filter(member => member.status === 'ACTIVE')
  const results = []
  for (const member of members) {
    const session = await read('/api/session')
    const options = await read('/api/session/tenant-selection-options')
    const selection = await context.post('/api/session/tenant-selections', { data: { tenant_id: member.tenant_id, expected_context_revision: options.expected_context_revision },
      headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': options.expected_context_revision,
        'Idempotency-Key': crypto.randomUUID(), [session.csrf.header_name]: session.csrf.token } })
    assert.equal(selection.status(), 200)
    const current = await read('/api/session')
    const models = await context.get('/api/model-config-revisions', { params: { limit: '20' }, headers: {
      'X-Schema-Version': '1.0.0', 'X-Context-Revision': current.current_context.context_revision } })
    const value = await models.json()
    const result = { tenant: member.tenant_id, actions: current.action_summary.filter(action => ['configuration.read', 'model.configure'].includes(action.action)),
      status: models.status(), code: value.code, message: value.message, response_tenant: value.data?.tenant_id, records: value.data?.items.length }
    results.push(result)
    console.log(JSON.stringify(result))
  }
  assert.ok(results.every(result => result.status === 200 && result.tenant === result.response_tenant))
  const finalCsrf = await (await context.get('/auth/csrf')).json()
  assert.equal((await context.post('/auth/logout', { headers: { Origin: origin, [finalCsrf.header_name]: finalCsrf.token } })).status(), 204)
  writeFileSync(new URL('../../.implementation-work/organization-model-contexts.json', import.meta.url), JSON.stringify(results, null, 2))
} finally { await context.dispose() }

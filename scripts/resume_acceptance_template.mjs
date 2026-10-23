import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { request } from '../tests/browser/node_modules/playwright/index.mjs'

const root = new URL('../', import.meta.url)
const target = new URL('.implementation-work/runtime/acceptance-template-workflow.json', root)
const ledger = JSON.parse(readFileSync(target))
const owner = JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json', root)))
assert.ok(ledger.template_pending && !ledger.template_version)
const origin = 'http://127.0.0.1:18083'
const api = await request.newContext({ baseURL: origin, extraHTTPHeaders: { Origin: origin }, timeout: 120000 })
try {
  const csrf = await (await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/auth/login', { form: owner, headers: { [csrf.header_name]: csrf.token }, maxRedirects: 0 })).status(), 302)
  const sessionResponse = await api.get('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
  assert.equal(sessionResponse.status(), 200)
  const session = (await sessionResponse.json()).data
  assert.deepEqual(session.current_context, ledger.template_pending.context)
  assert.equal(session.user.user_id, ledger.template_pending.actor)
  const headers = { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision, [session.csrf.header_name]: session.csrf.token }
  const lookup = await api.post('/api/operation-lookups', { headers, data: { operation_id: ledger.template_pending.operation, idempotency_key: ledger.template_pending.key } })
  assert.equal(lookup.status(), 200)
  assert.equal((await lookup.json()).data.lookup_status, 'NOT_FOUND')
  const response = await api.post('/api/template-versions', { headers: { ...headers, 'Idempotency-Key': ledger.template_pending.key }, data: ledger.template_input })
  const receipt = await response.json()
  writeFileSync(new URL('.implementation-work/template-create-response.json', root), JSON.stringify({ status: response.status(), body: receipt }, null, 2), { mode: 0o600 })
  console.log(JSON.stringify({ status: response.status(), code: receipt.code }))
  assert.equal(response.status(), 201)
  assert.equal(receipt.data.template.status, 'DRAFT')
  const operation = await api.get('/api/operations/' + receipt.data.operation_id, { headers })
  assert.equal(operation.status(), 200)
  const state = (await operation.json()).data
  assert.equal(state.status, 'COMPLETED')
  assert.equal(state.resource_id, receipt.data.template.template_version)
  ledger.template_version = receipt.data.template.template_version
  ledger.template_operation = receipt.data.operation_id
  ledger.template_creation_receipt = receipt
  writeFileSync(target, JSON.stringify(ledger, null, 2), { mode: 0o600 })
} finally { await api.dispose() }

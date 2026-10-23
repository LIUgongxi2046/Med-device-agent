import assert from 'node:assert/strict'
import { randomBytes, randomUUID } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { request } from 'playwright'

const root = new URL('../../', import.meta.url), origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json', root)))
const seed = JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json', root)))
const path = new URL('.implementation-work/runtime/acceptance-revoked-credentials.json', root)
const credentials = existsSync(path) ? JSON.parse(readFileSync(path)) : { username: 'acceptance_revoked', display_name: '验收测试已停用组织账户', password: randomBytes(24).toString('base64url'), platform_role: 'USER' }
if (!existsSync(path)) writeFileSync(path, JSON.stringify(credentials), { flag: 'wx', mode: 0o600 })
async function login(value) {
  const api = await request.newContext({ baseURL: origin, extraHTTPHeaders: { Origin: origin }, timeout: 90000 })
  const csrf = await (await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/auth/login', { form: { username: value.username, password: value.password }, headers: { [csrf.header_name]: csrf.token }, maxRedirects: 0 })).status(), 302)
  return api
}
async function state(api) { const response = await api.get('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } }); assert.equal(response.status(), 200); return (await response.json()).data }
let admin = await login(owner), session = await state(admin)
const accounts = await admin.get('/api/local-accounts?limit=100', { headers: { 'X-Schema-Version': '1.0.0' } })
assert.equal(accounts.status(), 200)
let account = (await accounts.json()).data.items.find(item => item.username === credentials.username)
if (!account) {
  const response = await admin.post('/api/local-accounts', { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision, [session.csrf.header_name]: session.csrf.token, 'Idempotency-Key': randomUUID() }, data: credentials })
  assert.equal(response.status(), 201)
  account = (await response.json()).data.account
}
await admin.dispose()
execFileSync(fileURLToPath(new URL('.implementation-work/object-storage-venv/bin/python', root)), ['scripts/seed_acceptance_members.py', account.user_id], { cwd: fileURLToPath(root), stdio: 'pipe' })
const active = await login(credentials)
const selected = await state(active)
if (selected.current_context !== null) assert.equal(selected.current_context.tenant_id, seed.tenant_id)
await active.dispose()
admin = await login(owner); session = await state(admin)
const headers = { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision }
const members = await admin.get('/api/enterprise-members?limit=100', { headers })
assert.equal(members.status(), 200)
const member = (await members.json()).data.items.find(item => item.user_id === account.user_id)
assert.ok(member)
const project = await admin.get('/api/projects/' + seed.project_id, { headers })
assert.equal(project.status(), 200)
const basis = (await project.json()).data.creation_basis
if (member.status === 'ACTIVE') {
  assert.ok(selected.current_context)
  const response = await admin.post('/api/permission-decisions', { headers: { ...headers, [session.csrf.header_name]: session.csrf.token, 'Idempotency-Key': randomUUID() }, data: { schema_version: '1.0.0', decision: 'DISABLE_MEMBER', subject_id: account.user_id, scope: { tenant_id: seed.tenant_id, project_id: null, resource_kind: 'MEMBER', resource_id: member.member_id }, action: null, grant_id: null, condition: null, valid_from: null, valid_to: null, basis, expected_revision: member.revision, reason: 'SIMULATION 验收：停用独立测试成员，验证默认组织不会产生访问资格。' } })
  assert.equal(response.status(), 200)
} else assert.equal(member.status, 'DISABLED')
await admin.dispose()
const disabled = await login(credentials)
const result = await state(disabled)
assert.equal(result.current_context, null)
assert.ok(!result.memberships.some(item => item.tenant_id === seed.tenant_id && item.status === 'ACTIVE'))
assert.equal((await disabled.get('/api/projects', { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': '0' } })).status(), 403)
await disabled.dispose()
const proof = { verified_at: new Date().toISOString(), actual_membership_disable: true, previously_bound_tenant: seed.tenant_id, restored_context: null, resource_access_denied: true }
writeFileSync(new URL('.implementation-work/revoked-workspace-checks.json', root), JSON.stringify(proof, null, 2))
console.log(JSON.stringify(proof))

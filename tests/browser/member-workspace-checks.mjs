import assert from 'node:assert/strict'
import { randomUUID, randomBytes } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { request } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const runtime = new URL('../../.implementation-work/runtime/', import.meta.url)
const owner = JSON.parse(readFileSync(new URL('owner-credentials.json', runtime)))
const member = JSON.parse(readFileSync(new URL('acceptance-member-credentials.json', runtime)))
const seed = JSON.parse(readFileSync(new URL('acceptance-workspace-seed.json', runtime)))
async function login(credentials) {
  const api = await request.newContext({ baseURL: origin, extraHTTPHeaders: { Origin: origin }, timeout: 90000 })
  const csrf = await (await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/auth/login', { form: { username: credentials.username, password: credentials.password }, headers: { [csrf.header_name]: csrf.token }, maxRedirects: 0 })).status(), 302)
  return api
}
async function session(api) {
  const response = await api.get('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
  assert.equal(response.status(), 200)
  return (await response.json()).data
}
const api = await login(member)
let initial = await session(api)
assert.equal(initial.user.platform_role, 'USER')
assert.ok(initial.current_context)
assert.ok(initial.memberships.some(item => item.tenant_id === initial.current_context.tenant_id && item.status === 'ACTIVE'))
if (initial.current_context.tenant_id !== seed.tenant_id) {
  const csrf = await (await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/api/session/tenant-selections', { headers: { [csrf.header_name]: csrf.token, 'X-Schema-Version': '1.0.0', 'X-Context-Revision': initial.current_context.context_revision, 'Idempotency-Key': randomUUID() }, data: { tenant_id: seed.tenant_id, expected_context_revision: initial.current_context.context_revision } })).status(), 200)
}
await api.dispose()
const restored = await login(member)
const selected = await session(restored)
assert.equal(selected.current_context.tenant_id, seed.tenant_id)
const native = await restored.get('/api/system/user/list?pageNum=1&pageSize=10', { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': selected.current_context.context_revision } })
assert.equal(native.status(), 403, '普通账号不能读取系统用户目录')
const projects = await restored.get('/api/projects', { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': selected.current_context.context_revision } })
assert.equal(projects.status(), 200)
assert.ok((await projects.json()).data.projects.some(item => item.project_id === seed.project_id))
await restored.dispose()
const admin = await login(owner)
const unassignedPath = new URL('acceptance-unassigned-credentials.json', runtime)
let unassigned
if (existsSync(unassignedPath)) unassigned = JSON.parse(readFileSync(unassignedPath))
else {
  unassigned = { username: 'acceptance_unassigned', display_name: '验收测试未分配组织账户', password: randomBytes(24).toString('base64url'), platform_role: 'USER' }
  writeFileSync(unassignedPath, JSON.stringify(unassigned), { mode: 0o600, flag: 'wx' })
  const state = await session(admin)
  assert.equal((await admin.post('/api/local-accounts', { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': state.current_context.context_revision, [state.csrf.header_name]: state.csrf.token, 'Idempotency-Key': randomUUID() }, data: unassigned })).status(), 201)
}
await admin.dispose()
const without = await login(unassigned)
const empty = await session(without)
assert.equal(empty.current_context, null)
assert.equal(empty.memberships.length, 0)
assert.equal((await without.get('/api/projects', { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': '0' } })).status(), 403)
await without.dispose()
const proof = { verified_at: new Date().toISOString(), default_selected: Boolean(initial.current_context), binding_persisted: true, native_management_denied: true, project_visible: true, unassigned_context: null, unassigned_projects_denied: true }
writeFileSync(new URL('../../.implementation-work/member-workspace-checks.json', import.meta.url), JSON.stringify(proof, null, 2))
console.log(JSON.stringify(proof))

import assert from 'node:assert/strict'
import { randomBytes, randomUUID } from 'node:crypto'
import { existsSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { request } from '../tests/browser/node_modules/playwright/index.mjs'

const root = new URL('../', import.meta.url)
const ownerPath = new URL('.implementation-work/runtime/owner-credentials.json', root)
assert.equal(statSync(ownerPath).mode & 0o777, 0o600)
const owner = JSON.parse(readFileSync(ownerPath))
const origin = 'http://127.0.0.1:18083'
const api = await request.newContext({ baseURL: origin, extraHTTPHeaders: { Origin: origin }, timeout: 90000 })
const proofPath = new URL('.implementation-work/runtime/acceptance-framework-data.json', root)
const proof = existsSync(proofPath) ? JSON.parse(readFileSync(proofPath)) : { data_kind: 'SIMULATION', scope: 'LOCAL_ACCEPTANCE_DATA', operations: [] }
function save() { writeFileSync(proofPath, JSON.stringify(proof, null, 2), { mode: 0o600 }) }
try {
  const csrf = await (await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/auth/login', { form: owner, headers: { [csrf.header_name]: csrf.token }, maxRedirects: 0 })).status(), 302)
  const sessionResponse = await api.get('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
  assert.equal(sessionResponse.status(), 200)
  const session = (await sessionResponse.json()).data
  assert.equal(session.user.platform_role, 'PLATFORM_ADMIN')
  assert.ok(session.current_context)
  const headers = { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision, [session.csrf.header_name]: session.csrf.token }
  async function seed(label, listPath, targetPath, itemsField, nameField, name, body) {
    const response = await api.get(listPath, { headers })
    assert.equal(response.status(), 200, listPath)
    const listing = await response.json()
    assert.equal(listing.code, 200, listPath)
    if (listing[itemsField].some(item => item[nameField] === name)) return
    const key = randomUUID()
    proof.operations.push({ label, path: targetPath, key, state: 'PENDING' }); save()
    const created = await api.post(targetPath, { headers: { ...headers, 'Idempotency-Key': key }, data: body })
    const result = await created.json()
    if (created.status() !== 200 || result.code !== 200) console.log('验收数据接口响应', JSON.stringify({ path: targetPath, status: created.status(), response: result }))
    Object.assign(proof.operations.at(-1), { http_status: created.status(), code: result.code, state: result.code === 200 ? 'CREATED' : 'FAILED' }); save()
    assert.equal(created.status(), 200, targetPath)
    assert.equal(result.code, 200, targetPath)
  }
  if (!process.argv.includes('--account-only')) {
  await seed('部门', '/api/system/dept/list', '/api/system/dept', 'data', 'deptName', '验收测试工作组织',
    { deptName: '验收测试工作组织', parentId: 100, orderNum: 1, status: '0', leader: '验收测试负责人' })
  await seed('岗位', '/api/system/post/list?pageNum=1&pageSize=100', '/api/system/post', 'rows', 'postName', '验收测试专员',
    { postName: '验收测试专员', postCode: 'acceptance_specialist', postSort: 1, status: '0', remark: 'SIMULATION 本地功能验收数据' })
  await seed('参数', '/api/system/config/list?pageNum=1&pageSize=100', '/api/system/config', 'rows', 'configKey', 'medical.acceptance.dataKind',
    { configName: '验收测试数据标识', configKey: 'medical.acceptance.dataKind', configValue: 'SIMULATION', configType: 'N', remark: '本地验收用途' })
  await seed('公告', '/api/system/notice/list?pageNum=1&pageSize=100', '/api/system/notice', 'rows', 'noticeTitle', '验收测试使用说明',
    { noticeTitle: '验收测试使用说明', noticeType: '1', status: '0', noticeContent: '<p>当前记录用于本地功能验收。测试资料、配置及流程使用 SIMULATION 标识，不用于医疗结论或客户申报。</p>' })
  }
  const memberPath = new URL('.implementation-work/runtime/acceptance-member-credentials.json', root)
  const member = existsSync(memberPath) ? JSON.parse(readFileSync(memberPath)) : { username: 'acceptance_member', display_name: '验收测试普通账户', password: randomBytes(24).toString('base64url'), platform_role: 'USER' }
  if (!existsSync(memberPath)) { writeFileSync(memberPath, JSON.stringify(member), { mode: 0o600, flag: 'wx' }) }
  const accounts = await api.get('/api/local-accounts?limit=100', { headers: { 'X-Schema-Version': '1.0.0' } })
  assert.equal(accounts.status(), 200)
  const existing = (await accounts.json()).data.items.find(account => account.username === member.username)
  if (existing) proof.member_user_id = existing.user_id
  else {
    const created = await api.post('/api/local-accounts', { headers: { ...headers, 'Idempotency-Key': randomUUID() }, data: member })
    assert.equal(created.status(), 201)
    proof.member_user_id = (await created.json()).data.account.user_id
  }
  proof.verified_at = new Date().toISOString(); proof.state = 'CREATED'; save()
  console.log(JSON.stringify({ state: proof.state, operations: proof.operations.map(({ label, state }) => ({ label, state })), member_user_id: proof.member_user_id }))
} finally { await api.dispose() }

import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { request } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const api = await request.newContext({ baseURL: origin, timeout: 90000 })
const proof = { data_kind: 'SIMULATION', database: 'framework_dev_acceptance', checks: [] }
try {
  const csrf = await (await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/auth/login', { headers: { Origin: origin, [csrf.header_name]: csrf.token }, form: owner, maxRedirects: 0 })).status(), 302)
  const session = (await (await api.get('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()).data
  const headers = { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision, [session.csrf.header_name]: session.csrf.token, Origin: origin }
  async function read(path) {
    const response = await api.get(path, { headers })
    assert.equal(response.status(), 200, path)
    const body = await response.json()
    assert.equal(body.code, 200, path)
    return body
  }
  let rows = (await read('/api/tool/gen/list?pageNum=1&pageSize=100')).rows
  if (!rows.some(row => row.tableName === 'sys_dept')) {
    const tables = await read('/api/tool/gen/db/list?pageNum=1&pageSize=100')
    assert.ok(tables.rows.some(row => row.tableName === 'sys_dept'))
    const response = await api.post('/api/tool/gen/importTable?tables=sys_dept&tplWebType=element-plus', { headers })
    assert.equal(response.status(), 200)
    assert.equal((await response.json()).code, 200)
    rows = (await read('/api/tool/gen/list?pageNum=1&pageSize=100')).rows
  }
  const table = rows.find(row => row.tableName === 'sys_dept')
  assert.ok(table)
  const detail = await read('/api/tool/gen/' + table.tableId)
  assert.ok(detail.data.rows.length > 0)
  const preview = await read('/api/tool/gen/preview/' + table.tableId)
  assert.ok(Object.keys(preview.data).length > 0)
  proof.checks.push('ISOLATED_TABLE_IMPORT_AND_READ', 'GENERATOR_COLUMNS_AND_PREVIEW')
  proof.table_id = table.tableId
  proof.verified_at = new Date().toISOString()
  console.log(JSON.stringify(proof))
} finally {
  writeFileSync(new URL('../../.implementation-work/generator-data-checks.json', import.meta.url), JSON.stringify(proof, null, 2))
  await api.dispose()
}

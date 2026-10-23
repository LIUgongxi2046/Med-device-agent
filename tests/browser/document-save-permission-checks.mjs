import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { request } from 'playwright'

const root = new URL('../../', import.meta.url)
const owner = JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json', root)))
const seed = JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json', root)))
const api = await request.newContext({ baseURL: 'http://127.0.0.1:18083', extraHTTPHeaders: { Origin: 'http://127.0.0.1:18083' }, timeout: 180000 })
try {
  const csrf = await (await api.get('/auth/csrf')).json()
  assert.equal((await api.post('/auth/login', { form: owner, headers: { [csrf.header_name]: csrf.token }, maxRedirects: 0 })).status(), 302)
  const session = (await (await api.get('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()).data
  const headers = { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision }
  assert.ok(session.action_summary.some(value => value.action === 'document.write'))
  assert.equal(session.action_summary.some(value => value.action === 'document.check'), false)
  const current = await api.get('/api/documents/' + seed.document_id, { headers })
  assert.equal(current.status(), 200)
  const snapshot = (await current.json()).data
  assert.equal(snapshot.allowed_actions.includes('document.edit'), false)
  const body = { base_revision: snapshot.document_revision, expected_revision: snapshot.revision, body_artifact_id: snapshot.body.artifact_id,
    body_sha256: snapshot.body.sha256, body_schema_revision: snapshot.body_schema_revision, reason: 'SIMULATION 验收：缺少保存后检查权限时拒绝保存。' }
  const denied = await api.post('/api/documents/' + seed.document_id + '/revisions', { data: body, headers: { ...headers, 'Idempotency-Key': randomUUID(), [session.csrf.header_name]: session.csrf.token } })
  assert.equal(denied.status(), 403)
  assert.equal((await denied.json()).code, 'ACCESS_DENIED')
  const result = { verified_at: new Date().toISOString(), checks: ['READ_ONLY_WITHOUT_REQUIRED_CHECK_PERMISSION', 'SAVE_PREFLIGHT_DENIED_WITHOUT_REQUIRED_CHECK_PERMISSION'], result: 'PASS', screenshots: false }
  writeFileSync(new URL('.implementation-work/document-save-permission-checks.json', root), JSON.stringify(result, null, 2), { mode: 0o600 })
  console.log(JSON.stringify(result))
} finally { await api.dispose() }

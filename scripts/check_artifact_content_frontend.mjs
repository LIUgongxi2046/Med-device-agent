import assert from 'node:assert/strict'
import { createHash, randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { readFile } from 'node:fs/promises'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

globalThis.window = new EventTarget()
const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: content } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/artifact-content.ts', import.meta.url)), { root, mode: 'medical' })
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const reference = 'https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/'
const bytes = Buffer.from(await readFile(new URL('../backend-design/接口补充设计.md', import.meta.url)))
const identity = { artifactId: randomUUID(), resourceId: randomUUID(), sha256: hash(bytes), schemaRef: reference + 'DocumentBody', mediaType: 'application/json' }
const capacity = { pageBytes: 1024, maximumBytes: bytes.length + 1024, maximumPages: Math.ceil(bytes.length / 1024) + 1 }
const selection = hash('SIMULATION fixed object selection')
function pages(value = bytes, expected = identity, limits = capacity) {
  const result = []
  for (let offset = 0; offset < value.length; offset += limits.pageBytes) {
    const part = value.subarray(offset, offset + limits.pageBytes), complete = offset + part.length === value.length
    result.push({ resource_id: expected.resourceId, artifact_id: expected.artifactId, schema_ref: expected.schemaRef,
      media_type: expected.mediaType, encoding: 'BASE64', content: part.toString('base64'), chunk_sha256: hash(part), full_sha256: expected.sha256,
      coverage: { selection_sha256: selection, delivered_sha256: hash('SIMULATION range ' + offset), complete,
        next_cursor: complete ? null : 'SIMULATION-cursor-' + (offset + part.length) }, read_receipt_id: randomUUID() })
  }
  return result
}
let checks = 0
async function rejects(action, code) { await assert.rejects(action, error => error.code === code); checks++ }
const chunks = pages()
assert.ok(chunks.length > 2)
const verified = new content.VerifiedArtifactContent(identity, capacity)
for (const chunk of chunks) await verified.append(chunk)
const blob = await verified.complete()
assert.deepEqual(Buffer.from(await blob.arrayBuffer()), bytes); assert.equal(blob.type, 'application/json')
assert.equal(verified.receivedBytes, bytes.length); checks++
await rejects(() => verified.complete(), 'ACTION_STALE')
await rejects(() => verified.append(chunks[0]), 'ACTION_STALE')
verified.dispose(); await rejects(() => verified.complete(), 'ACTION_STALE')

const incomplete = new content.VerifiedArtifactContent(identity, capacity)
await incomplete.append(chunks[0]); await rejects(() => incomplete.complete(), 'COVERAGE_INCOMPLETE')
const onlyLast = new content.VerifiedArtifactContent(identity, capacity)
await onlyLast.append(chunks.at(-1)); await rejects(() => onlyLast.complete(), 'CONTENT_MISMATCH')
const reordered = new content.VerifiedArtifactContent(identity, capacity)
for (const chunk of [chunks[1], chunks[0], ...chunks.slice(2)]) await reordered.append(chunk)
await rejects(() => reordered.complete(), 'CONTENT_MISMATCH')

for (const [key, value, code] of [
  ['artifact_id', randomUUID(), 'CONTENT_MISMATCH'], ['resource_id', randomUUID(), 'CONTENT_MISMATCH'],
  ['schema_ref', reference + 'DocumentFieldProjection', 'CONTENT_MISMATCH'], ['media_type', 'text/html', 'CONTENT_MISMATCH'],
  ['encoding', 'UTF8', 'CONTENT_MISMATCH'], ['full_sha256', 'a'.repeat(64), 'CONTENT_MISMATCH'],
  ['chunk_sha256', 'a'.repeat(64), 'CONTENT_MISMATCH'], ['content', 'not base64!<svg/>', 'CONTENT_MISMATCH'],
  ['content', 'Zg==\n', 'CONTENT_MISMATCH'], ['content', 'AAAA'.repeat(1025), 'FILE_TOO_LARGE'],
  ['injected_url', 'https://example.invalid/image.png', 'SCHEMA_INVALID']
]) {
  const current = new content.VerifiedArtifactContent(identity, capacity)
  await rejects(() => current.append({ ...chunks[0], [key]: value }), code)
  assert.equal(current.receivedBytes, 0); current.dispose()
}
for (const change of [
  page => { page.coverage.selection_sha256 = 'a'.repeat(64) },
  page => { page.coverage.delivered_sha256 = chunks[0].coverage.delivered_sha256 },
  page => { page.read_receipt_id = chunks[0].read_receipt_id },
  page => { page.coverage.next_cursor = chunks[0].coverage.next_cursor },
  page => { page.coverage.next_cursor = 'x'.repeat(8193) }
]) {
  const current = new content.VerifiedArtifactContent(identity, capacity)
  await current.append(chunks[0]); const changed = structuredClone(chunks[1]); change(changed)
  await rejects(() => current.append(changed), 'CONTENT_MISMATCH'); current.dispose()
}
const short = structuredClone(chunks[0]); short.content = Buffer.from('short').toString('base64'); short.chunk_sha256 = hash('short')
await rejects(() => new content.VerifiedArtifactContent(identity, capacity).append(short), 'COVERAGE_INCOMPLETE')
const padded = structuredClone(chunks.at(-1)); padded.content = 'Zh=='; padded.chunk_sha256 = hash('f')
await rejects(() => new content.VerifiedArtifactContent(identity, capacity).append(padded), 'CONTENT_MISMATCH')
await rejects(() => new content.VerifiedArtifactContent(identity, { ...capacity, maximumBytes: 1023 }).append(chunks[0]), 'FILE_TOO_LARGE')
await rejects(() => new content.VerifiedArtifactContent(identity, { ...capacity, maximumPages: 1 }).append(chunks[0]), 'CONTEXT_LIMIT')
assert.throws(() => new content.VerifiedArtifactContent(identity, { ...capacity, pageBytes: 1048577 }), error => error.code === 'SCHEMA_INVALID'); checks++
const changed = Buffer.from(bytes); changed[0] ^= 1
const damaged = new content.VerifiedArtifactContent(identity, capacity)
for (const chunk of pages(changed)) await damaged.append(chunk)
await rejects(() => damaged.complete(), 'CONTENT_MISMATCH')
const disposed = new content.VerifiedArtifactContent(identity, capacity)
const pending = disposed.append(chunks[0]); disposed.dispose(); await rejects(() => pending, 'ACTION_STALE')
const concurrent = new content.VerifiedArtifactContent(identity, capacity)
const first = concurrent.append(chunks[0]); await rejects(() => concurrent.append(chunks[1]), 'ACTION_STALE'); await first; concurrent.dispose()
const mutable = new content.VerifiedArtifactContent(identity, capacity)
const mutablePage = structuredClone(chunks[0]), receiving = mutable.append(mutablePage)
mutablePage.coverage.next_cursor = 'MUTATED'; mutablePage.media_type = 'text/html'; await receiving
assert.equal(mutable.nextCursor, chunks[0].coverage.next_cursor); checks++; mutable.dispose()

const session = { user: { user_id: randomUUID() }, current_context: { tenant_id: randomUUID(), context_revision: '1', auth_epoch: '2', governance_epoch: '3' } }
const fixedRef = { artifact_id: identity.artifactId, sha256: identity.sha256, schema_ref: identity.schemaRef,
  coverage: { selection_sha256: selection, delivered_sha256: selection, complete: true, next_cursor: null } }
const snapshot = { document_id: identity.resourceId, project_id: randomUUID(), document_revision: randomUUID(), revision: '7',
  plan_revision: randomUUID(), template_version: randomUUID(), body: fixedRef,
  fields: { ...fixedRef, artifact_id: randomUUID(), schema_ref: reference + 'DocumentFieldProjection' },
  evidence_links: { ...fixedRef, artifact_id: randomUUID(), schema_ref: reference + 'DocumentEvidenceProjection' },
  body_schema_revision: randomUUID(), check_ids: [], review_ids: [], signature_ids: [], invalidation_reasons: [], issue_watermark: '0',
  evidence_watermark: '0', auth_epoch: '2', governance_epoch: '3', allowed_actions: ['document.read'], stream_cursors: [] }
function response(page) {
  const requestId = randomUUID()
  return { status: 200, headers: { 'cache-control': 'no-store', 'x-schema-version': '1.0.0', 'x-request-id': requestId, 'x-content-type-options': 'nosniff' },
    data: { schema_version: '1.0.0', request_id: requestId, resource_revision: '7', data: structuredClone(page) } }
}
for (const kind of ['body', 'fields', 'evidence_links']) {
  const target = snapshot[kind], expected = { ...identity, artifactId: target.artifact_id, schemaRef: target.schema_ref }
  const sequence = pages(bytes, expected); let count = 0; const progress = []
  const result = await content.readCompleteFixedDocumentArtifact(() => session, snapshot, kind, async (id, cursor, limit, context, signal) => {
    assert.equal(id, target.artifact_id); assert.equal(cursor, count === 0 ? null : sequence[count - 1].coverage.next_cursor)
    assert.equal(limit, capacity.pageBytes); assert.equal(context, '1'); assert.equal(signal.aborted, false)
    return response(sequence[count++])
  }, new AbortController().signal, size => progress.push(size), capacity)
  assert.equal(count, sequence.length); assert.equal(progress.at(-1), bytes.length)
  assert.deepEqual(Buffer.from(await result.arrayBuffer()), bytes); checks++
}
async function scenario({ mutate = () => {}, mutateResponse = () => {}, progress = () => {}, before = () => {}, kind = 'body' } = {}) {
  let active = structuredClone(session), count = 0; const abort = new AbortController(); before(active, abort)
  const run = content.readCompleteFixedDocumentArtifact(() => active, snapshot, kind, async () => {
    const raw = response(chunks[count++]); mutate(active, abort); mutateResponse(raw)
    return raw
  }, abort.signal, () => progress(active, abort), capacity)
  return { run, calls: () => count }
}
for (const mutate of [
  value => { value.user.user_id = randomUUID() },
  value => { value.current_context.tenant_id = randomUUID() },
  value => { value.current_context.context_revision = '2' },
  value => { value.current_context.auth_epoch = '3' },
  value => { value.current_context.governance_epoch = '4' },
  value => { value.current_context = null }
]) {
  const current = await scenario({ mutate }); await rejects(() => current.run, 'CONTEXT_CHANGED'); assert.equal(current.calls(), 1)
}
const aborted = await scenario({ mutate: (_session, abort) => abort.abort() })
await assert.rejects(aborted.run, error => error.name === 'AbortError'); assert.equal(aborted.calls(), 1); checks++
const noRequest = await scenario({ before: (_session, abort) => abort.abort() })
await assert.rejects(noRequest.run, error => error.name === 'AbortError'); assert.equal(noRequest.calls(), 0); checks++
const progressChanged = await scenario({ progress: value => { value.current_context.auth_epoch = '5' } })
await rejects(() => progressChanged.run, 'CONTEXT_CHANGED'); assert.equal(progressChanged.calls(), 1)
for (const [mutateResponse, code] of [
  [raw => { raw.status = 206 }, 'REVISION_CONFLICT'],
  [raw => { raw.data.resource_revision = '8' }, 'REVISION_CONFLICT'],
  [raw => { raw.headers['cache-control'] = 'public' }, 'SCHEMA_INVALID'],
  [raw => { raw.headers['x-request-id'] = randomUUID() }, 'SCHEMA_INVALID'],
  [raw => { delete raw.headers['x-content-type-options'] }, 'SCHEMA_INVALID'],
  [raw => { raw.data.data.artifact_id = randomUUID() }, 'CONTENT_MISMATCH']
]) {
  const current = await scenario({ mutateResponse }); await rejects(() => current.run, code); assert.equal(current.calls(), 1)
}
const stale = await scenario({ before: value => { value.current_context.auth_epoch = '4' } })
await rejects(() => stale.run, 'CONTEXT_CHANGED'); assert.equal(stale.calls(), 0)
const missing = await scenario({ before: value => { value.current_context = null } })
await rejects(() => missing.run, 'ACCESS_DENIED'); assert.equal(missing.calls(), 0)
const wrongKind = await scenario({ kind: 'image' }); await rejects(() => wrongKind.run, 'SCHEMA_INVALID'); assert.equal(wrongKind.calls(), 0)
assert.deepEqual(content.parseArtifactContentResponse('{\"schema_version\":\"1.0.0\"}', 1024), { schema_version: '1.0.0' }); checks++
for (const [value, code] of [['{\"data\":1,\"data\":2}', 'SCHEMA_INVALID'], ['x'.repeat(20000), 'FILE_TOO_LARGE'], [{ data: 'already parsed' }, 'SCHEMA_INVALID']]) {
  assert.throws(() => content.parseArtifactContentResponse(value, 1024), error => error.code === code); checks++
}
for (const code of ['ACCESS_DENIED', 'DEPENDENCY_UNAVAILABLE']) {
  const current = await scenario({ mutateResponse: raw => {
    raw.status = code === 'ACCESS_DENIED' ? 403 : 422
    raw.data = { schema_version: '1.0.0', code, message: 'SIMULATION expected rejection', request_id: raw.headers['x-request-id'],
      resource_revision: null, allowed_actions: [], retryable: false, operation_id: null, details: [] }
  } })
  await rejects(() => current.run, code); assert.equal(current.calls(), 1)
}
const large = Buffer.alloc(1048576, 0x61), largeIdentity = { ...identity, sha256: hash(large) }
const largeCapacity = { pageBytes: 1048576, maximumBytes: 1048576, maximumPages: 1 }
const maximumPage = new content.VerifiedArtifactContent(largeIdentity, largeCapacity)
await maximumPage.append(pages(large, largeIdentity, largeCapacity)[0])
assert.equal((await maximumPage.complete()).size, 1048576); maximumPage.dispose(); checks++
console.log(JSON.stringify({ result: 'PASS', checks, scope: 'FIXED_DOCUMENT_ARTIFACT_CONSUMER', transport: 'SIMULATION', bytes: bytes.length,
  note: 'No real backend deployment or image display is asserted.' }))

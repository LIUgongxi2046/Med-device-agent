import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { randomUUID } from 'node:crypto'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

globalThis.window = new EventTarget()
const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: protocol } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/protocol.ts', import.meta.url)), { root, mode: 'medical' })
const { module: domain } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/source-exact-domain.ts', import.meta.url)), { root, mode: 'medical' })
const { module: digest } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/content-digest.ts', import.meta.url)), { root, mode: 'medical' })
const reference = 'urn:medical:source-exact:1.0.0#/$defs/'
const field = { field_kind: 'MODEL', raw_value: 'SIMULATION 型号 A-001', alias_value: null, alias_reason: null,
  chunk_id: randomUUID(), quote_hash: 'a'.repeat(64), locator_sha256: 'b'.repeat(64), start_codepoint: 0, end_codepoint: 19 }
for (const kind of ['MODEL', 'PTR_NUMBER', 'DOCUMENT_NUMBER', 'STANDARD_NUMBER', 'STANDARD_YEAR']) {
  assert.equal(protocol.validate(reference + 'Field', { ...field, field_kind: kind }).field_kind, kind)
}
for (const literal of [' A-001 ', 'a-001', 'e\u0301', 'é', '设备😀']) {
  assert.equal(protocol.validate(reference + 'SearchInput', { schema_version: '1.0.0', project_id: randomUUID(),
    field_kind: 'MODEL', query: literal, purpose: 'VERIFICATION' }).query, literal)
}
const publication = { schema_version: '1.0.0', project_id: randomUUID(), parse_version: randomUUID(), index_version: randomUUID(),
  approval_revision: randomUUID(), binding_revision: randomUUID(), rights_revision: randomUUID(), expected_revision: '0',
  fields: [field, { ...field, alias_value: 'SIMULATION-A001', alias_reason: 'SIMULATION 人工核查别名' }], reason: 'SIMULATION 字段核准' }
assert.deepEqual(protocol.validate(reference + 'PublicationInput', publication), publication)
assert.throws(() => protocol.validate(reference + 'Field', { ...field, inferred_value: 'A-001' }), failure => failure.code === 'SCHEMA_INVALID')
domain.verifyExactPublication(publication)
for (const value of [' ', '\ud800']) assert.throws(() => domain.exactLiteral(value), error => error.code === 'SCHEMA_INVALID')
domain.exactLiteral('\u00a0')
assert.throws(() => domain.exactLiteral('\u2003'), error => error.code === 'SCHEMA_INVALID')
for (const change of [{ end_codepoint: 0 }, { alias_value: 'A001' }, { alias_value: field.raw_value, alias_reason: '核查依据' }]) {
  assert.throws(() => domain.verifyExactField({ ...field, ...change }), error => error.code === 'SCHEMA_INVALID')
}
assert.throws(() => domain.verifyExactPublication({ ...publication, fields: [field, field] }), error => error.code === 'SCHEMA_INVALID')
assert.throws(() => domain.verifyExactPublication({ ...publication, expected_revision: '18446744073709551616' }), error => error.code === 'SCHEMA_INVALID')
const tenant = randomUUID(), source = randomUUID()
const manifest = { schema_version: '1.0.0', tenant_id: tenant, source_version: source, field_revision: randomUUID(), revision: '1',
  original_sha256: 'c'.repeat(64), request: publication, actor_id: randomUUID(), created_at: '2026-10-14T01:02:03.123456Z' }
domain.verifyExactManifest(manifest, tenant, source, publication.project_id)
for (const name of ['tenant_id', 'source_version']) assert.throws(() => domain.verifyExactManifest({ ...manifest, [name]: randomUUID() }, tenant, source, publication.project_id), error => error.code === 'CONTENT_MISMATCH')
const receipt = { schema_version: '1.0.0', operation_id: randomUUID(), source_version: source, field_revision: manifest.field_revision,
  revision: '1', manifest_sha256: await digest.sourceDigest(manifest), field_count: publication.fields.length, manifest,
  current_context: { tenant_id: tenant, context_revision: '2', auth_epoch: '1', governance_epoch: '2' } }
await domain.verifyExactReceipt(receipt, publication, tenant, source)
await assert.rejects(() => domain.verifyExactReceipt({ ...receipt, manifest_sha256: 'd'.repeat(64) }, publication, tenant, source), error => error.code === 'CONTENT_MISMATCH')
const locator = { kind: 'TXT', encoding: 'UTF-8', newline_policy: 'PRESERVE', char_start: 0, char_end: 19, line_start: 1, line_end: 1 }
const matched = { ...field, quote_hash: await digest.byteDigest(new TextEncoder().encode(field.raw_value)), locator_sha256: await digest.sourceDigest(locator) }
const page = { schema_version: '1.0.0', retrieval_id: randomUUID(), project_id: publication.project_id, field_kind: 'MODEL', query: field.raw_value,
  purpose: 'VERIFICATION', mode: 'EXACT', status: 'FOUND', created_at: manifest.created_at, selection_sha256: 'e'.repeat(64), total: '2', page: 1, page_size: 1, next_page: 2,
  items: [{ rank: 1, source_id: randomUUID(), source_version: source, parse_version: publication.parse_version, index_version: publication.index_version,
    chunk_id: field.chunk_id, approval_revision: publication.approval_revision, rights_revision: publication.rights_revision, binding_revision: publication.binding_revision,
    title: 'SIMULATION 字段资料', excerpt: field.raw_value, excerpt_truncated: false, locator, quote_hash: matched.quote_hash, field_revision: manifest.field_revision,
    field_manifest_sha256: receipt.manifest_sha256, matches: [{ ordinal: 0, field: matched }] }] }
await domain.verifyExactPage(page, page.retrieval_id, 1, 1)
await domain.verifyExactPage({ ...page, status: 'EMPTY', total: '0', next_page: null, items: [] }, page.retrieval_id, 1, 1)
for (const change of [{ rank: 2 }, { quote_hash: 'f'.repeat(64) }, { matches: [{ ordinal: 0, field: { ...matched, raw_value: 'different-model' } }] }]) {
  await assert.rejects(() => domain.verifyExactPage({ ...page, items: [{ ...page.items[0], ...change }] }, page.retrieval_id, 1, 1), error => error.code === 'CONTENT_MISMATCH')
}
await assert.rejects(() => domain.verifyExactPage({ ...page, next_page: null }, page.retrieval_id, 1, 1), error => error.code === 'CONTENT_MISMATCH')
console.log(JSON.stringify({ status: 'PASS', checks: 33, data_kind: 'SIMULATION', actual_http_verified: false }))

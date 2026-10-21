import assert from 'node:assert/strict'
import test from 'node:test'
import { SchemaCatalogue, sha256 } from '../src/catalogue.js'

async function create(document: unknown): Promise<SchemaCatalogue> {
  const bytes = new TextEncoder().encode(JSON.stringify(document))
  const path = 'contracts/standalone.schema.json'
  return SchemaCatalogue.create({ schema_version: '1.0.0', base_uri: 'https://schemas.example.invalid/',
    schemas: [{ name: 'standalone', path, sha256: await sha256(bytes) }] }, new Map([[path, bytes]]))
}

test('standalone root schemas validate without a definitions collection', async () => {
  const catalogue: SchemaCatalogue = await create({ type: 'object', properties: { value: { type: 'string' } }, required: ['value'], additionalProperties: false })
  catalogue.validate('standalone', { value: 'synthetic' })
  assert.equal(catalogue.definitionCount, 0)
  assert.throws(() => catalogue.validate('standalone', { value: 1 }), /SCHEMA_VALIDATION_FAILED/)
  assert.throws(() => catalogue.schemaRef('standalone', 'missing'), /SCHEMA_UNSUPPORTED/)
})

test('present malformed definitions collections remain rejected', async () => {
  for (const definitions of [null, [], true, 'invalid']) {
    await assert.rejects(create({ type: 'object', $defs: definitions }))
  }
})

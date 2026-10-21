import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import test from 'node:test'
import { mergePublishedCatalogue, SchemaCatalogue } from '../src/catalogue.js'
import { publicationPaths } from './publication-fixture.js'

const root = resolve('../..')
const inputs = await Promise.all(publicationPaths.map(path => readFile(resolve(root, path))))
assert.equal(inputs.length, 18)
const merge = (bytes: Uint8Array[]) => mergePublishedCatalogue(bytes[0]!, bytes[1]!, ...bytes.slice(2))

test('complete pinned TS publication includes task package v2 and evaluation stage output', async () => {
  const manifest = await merge(inputs)
  const files = new Map(await Promise.all(manifest.schemas.map(async entry => [entry.path, await readFile(resolve(root, entry.path))] as const)))
  const catalogue: SchemaCatalogue = await SchemaCatalogue.create(manifest, files)
  assert.equal(manifest.schemas.length, 51)
  assert.equal(catalogue.definitionCount, 919)
  for (const name of ['task-professional-package-v2', 'evaluation-stage-input', 'evaluation-business-output']) {
    const entry = manifest.schemas.find(schema => schema.name === name)!
    assert.ok(entry)
    const schema = JSON.parse(files.get(entry.path)!.toString())
    assert.equal(catalogue.schemaRef(name), schema.$id)
    for (const definition of Object.keys(schema.$defs)) assert.equal(catalogue.schemaRef(name, definition), `${schema.$id}#/$defs/${definition}`)
  }
})

test('new pinned extensions reject changed raw bytes, base identity and unsafe registrations', async () => {
  for (const index of [16, 17]) {
    const original = JSON.parse(inputs[index]!.toString())
    const badBase = { ...original, base_catalogue_sha256: '0'.repeat(64) }
    const badPath = structuredClone(original)
    badPath.schemas[0].path = '../outside.schema.json'
    for (const changed of [Buffer.concat([inputs[index]!, Buffer.from('\n')]), Buffer.from(JSON.stringify(badBase)), Buffer.from(JSON.stringify(badPath))]) {
      const bytes = [...inputs]
      bytes[index] = changed
      await assert.rejects(merge(bytes), /catalogue digest mismatch/)
    }
  }
})

import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import Ajv2020 from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'
import { webRoot } from './helpers.mjs'

const contract = JSON.parse(await readFile(path.join(webRoot, '../business/medical-persistence/src/main/resources/formal-evaluation-plan-preparation.schema.json'), 'utf8'))
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true })
addFormats(ajv)
ajv.addSchema(contract)

for (const [name, valid] of [
  ['Hash', 'a'.repeat(64)],
  ['Revision', '123'],
  ['Key', 'question_key-1'],
  ['KeyOriginal', 'original-key-12345678'],
]) {
  test(`final N01 schema ${name} rejects terminal line endings rather than partial regex matches`, () => {
    const check = ajv.getSchema(contract.$id + '#/$defs/' + name)
    assert.equal(typeof check, 'function')
    assert.equal(check(valid), true)
    for (const suffix of ['\n', '\r', '\r\n', '\u2028', '\u2029']) {
      assert.equal(check(valid + suffix), false, `${name} accepted ${JSON.stringify(suffix)}`)
    }
    assert.equal(check(valid), true)
  })
}

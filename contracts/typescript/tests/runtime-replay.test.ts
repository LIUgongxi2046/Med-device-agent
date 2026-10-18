import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import test from 'node:test'
import { loadProject } from '../src/load-project.js'
import { mergePublishedCatalogue } from '../src/catalogue.js'

const root = resolve('../..')
const fixturePath = 'business/medical-persistence/src/test/resources/fixtures/runtime-replay.synthetic.json'

test('fixed Replay schema accepts complete synthetic saved inputs', async () => {
  const catalogue = await loadProject(root)
  const fixture = JSON.parse(await readFile(resolve(root, fixturePath), 'utf8'))
  const bound = catalogue.bind('replay-manifest', fixture)
  assert.equal(bound.schemaRef, 'urn:medical:replay-manifest:1.0.0')
  assert.equal(catalogue.definitionCount, 503)
})

test('Replay schema refuses incomplete inputs and invented approvals', async () => {
  const catalogue = await loadProject(root)
  assert.throws(() => catalogue.bind('replay-manifest', { schema_version: '1.0.0' }), /SCHEMA_VALIDATION_FAILED/)
  const fixture = JSON.parse(await readFile(resolve(root, fixturePath), 'utf8'))
  assert.throws(() => catalogue.bind('replay-manifest', { ...fixture, production_approved: true }), /SCHEMA_VALIDATION_FAILED/)
})

test('runtime extension raw bytes are pinned independently of older extensions', async () => {
  const base = await readFile(resolve(root, 'contracts/catalogue.json'))
  const evaluation = await readFile(resolve(root, 'contracts/extensions/evaluation-results.catalogue.json'))
  const simulation = await readFile(resolve(root, 'contracts/extensions/simulation-execution.catalogue.json'))
  const runtime = await readFile(resolve(root, 'contracts/extensions/task-runtime.catalogue.json'))
  await assert.rejects(mergePublishedCatalogue(base, evaluation, simulation,
    Buffer.concat([runtime, Buffer.from('\n')])), /扩展清单摘要/)
})

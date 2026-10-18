import assert from 'node:assert/strict'
import { copyFile, mkdir, mkdtemp, readFile, rename, rm, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { parse } from 'yaml'
import { mergePublishedCatalogue, SchemaCatalogue, sha256, type CatalogueManifest } from '../src/catalogue.js'
import { loadProject } from '../src/load-project.js'

const root = resolve('../..')
const contextPath = 'contracts/extensions/evaluation-context-compiler.catalogue.json'
const context = JSON.parse(await readFile(resolve(root, contextPath), 'utf-8')) as CatalogueManifest
const runtimePath = 'contracts/extensions/task-runtime.catalogue.json'
const runtimeBytes = await readFile(resolve(root, runtimePath))
const runtime = JSON.parse(runtimeBytes.toString('utf-8')) as CatalogueManifest
const simulationPath = 'contracts/extensions/simulation-execution.catalogue.json'
const simulationBytes = await readFile(resolve(root, simulationPath))
const simulation = JSON.parse(simulationBytes.toString('utf-8')) as CatalogueManifest
const extensionPath = 'contracts/extensions/evaluation-results.catalogue.json'
const baseBytes = await readFile(resolve(root, 'contracts/catalogue.json'))
const extensionBytes = await readFile(resolve(root, extensionPath))
const base = JSON.parse(baseBytes.toString('utf-8')) as CatalogueManifest
const extension = JSON.parse(extensionBytes.toString('utf-8')) as { schema_version: string; base_catalogue_sha256: string; schemas: { name: string; path: string; sha256: string }[] }
const encode = (value: unknown): Uint8Array => new TextEncoder().encode(JSON.stringify(value))

async function copiedPublication(run: (project: string) => Promise<void>): Promise<void> {
  const directory = await mkdtemp(resolve(tmpdir(), 'medical-catalogue-'))
  const project = resolve(directory, 'project')
  try {
    for (const path of ['contracts/catalogue.json', extensionPath, simulationPath, runtimePath, contextPath, ...context.schemas.map(entry => entry.path), ...runtime.schemas.map(entry => entry.path), ...simulation.schemas.map(entry => entry.path), ...base.schemas.map(entry => entry.path), ...extension.schemas.map(entry => entry.path)]) {
      const target = resolve(project, path)
      await mkdir(dirname(target), { recursive: true })
      await copyFile(resolve(root, path), target)
    }
    await run(project)
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
}

test('pinned extension preserves all legacy bytes, identities, digests and definitions', async () => {
  assert.equal(await sha256(baseBytes), 'dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915')
  const manifest = await mergePublishedCatalogue(baseBytes, extensionBytes)
  assert.equal(manifest.base_uri, base.base_uri)
  assert.deepEqual(manifest.schemas.slice(0, base.schemas.length), base.schemas)
  assert.deepEqual(manifest.schemas.slice(base.schemas.length), extension.schemas)
  const files = new Map<string, Uint8Array>()
  for (const entry of base.schemas) files.set(entry.path, await readFile(resolve(root, entry.path)))
  const legacy = await SchemaCatalogue.create(base, files)
  const catalogue: SchemaCatalogue = await loadProject(root)
  for (const entry of base.schemas) {
    const bytes = files.get(entry.path)!
    const document = (entry.path.endsWith('.yaml') ? parse(new TextDecoder().decode(bytes)) : JSON.parse(new TextDecoder().decode(bytes))) as { $id?: string; $defs: Record<string, unknown> }
    assert.equal(await sha256(bytes), entry.sha256)
    assert.equal(catalogue.schemaRef(entry.name), legacy.schemaRef(entry.name))
    for (const definition of Object.keys(document.$defs)) assert.equal(catalogue.schemaRef(entry.name, definition), legacy.schemaRef(entry.name, definition))
  }
  assert.equal(catalogue.schemaRef('evaluation-results'), 'urn:medical:evaluation-results:1.0.0')
  assert.equal(catalogue.definitionCount, 503)
})

test('publication trust uses exact raw extension and base bytes', async () => {
  await assert.rejects(mergePublishedCatalogue(baseBytes, Buffer.concat([extensionBytes, Buffer.from('\n')])), /扩展清单摘要/)
  await assert.rejects(mergePublishedCatalogue(Buffer.concat([baseBytes, Buffer.from('\n')]), extensionBytes), /基础清单摘要/)
  await copiedPublication(async project => {
    await writeFile(resolve(project, extensionPath), Buffer.concat([extensionBytes, Buffer.from('\n')]))
    await assert.rejects(loadProject(project), /扩展清单摘要/)
  })
})

test('untrusted extensions cannot override names, published paths, base URI or escape paths', async () => {
  const legacy = base.schemas[0]!
  const duplicateName = structuredClone(extension)
  duplicateName.schemas[0]!.name = legacy.name
  const duplicateUri = structuredClone(extension)
  Object.assign(duplicateUri.schemas[0]!, { path: legacy.path, sha256: legacy.sha256 })
  const mutations = [duplicateName, duplicateUri, { ...extension, base_uri: 'https://unregistered.invalid/' }]
  for (const path of ['../outside.json', '/outside.json', 'https://unregistered.invalid/schema.json']) {
    const changed = structuredClone(extension)
    changed.schemas[0]!.path = path
    mutations.push(changed)
  }
  for (const changed of mutations) await assert.rejects(mergePublishedCatalogue(baseBytes, encode(changed)), /扩展清单摘要/)
})

test('loader reads only the fixed extension and rejects symlinks outside the project', async () => {
  await copiedPublication(async project => {
    await writeFile(resolve(project, 'contracts/extensions/untrusted.catalogue.json'), 'invalid JSON')
    const catalogue = await loadProject(project)
    assert.equal(catalogue.schemaRef('evaluation-results'), 'urn:medical:evaluation-results:1.0.0')
  })
  for (const path of ['contracts/catalogue.json', extensionPath, simulationPath, runtimePath, contextPath, ...context.schemas.map(entry => entry.path), ...runtime.schemas.map(entry => entry.path), ...simulation.schemas.map(entry => entry.path), extension.schemas[0]!.path]) {
    await copiedPublication(async project => {
      const outside = resolve(project, '../outside.json')
      await rename(resolve(project, path), outside)
      await symlink(outside, resolve(project, path))
      await assert.rejects(loadProject(project), /路径超出登记目录/)
    })
  }
})

test('offline factory independently rejects duplicate names, duplicate declared URIs and path traversal', async () => {
  const bytes = encode({ $schema: 'https://json-schema.org/draft/2020-12/schema', $id: 'urn:medical:test-only:1.0.0', $defs: { Text: { type: 'string' } } })
  const digest = await sha256(bytes)
  const first = { name: 'first', path: 'first.json', sha256: digest }
  const second = { name: 'second', path: 'second.json', sha256: digest }
  const files = new Map([['first.json', bytes], ['second.json', bytes]])
  const manifest = { schema_version: '1.0.0', base_uri: base.base_uri, schemas: [first, second] }
  await assert.rejects(SchemaCatalogue.create({ ...manifest, schemas: [first, { ...second, name: first.name }] }, files), /名称重复/)
  await assert.rejects(SchemaCatalogue.create(manifest, files), /发布引用重复/)
  for (const path of ['../outside.json', 'nested/../outside.json', '/outside.json', 'https://unregistered.invalid/schema.json', 'nested\\outside.json', '%2e%2e/outside.json']) {
    await assert.rejects(SchemaCatalogue.create({ ...manifest, schemas: [{ ...first, path }] }, new Map([[path, bytes]])), /固定相对路径/)
  }
})

test('offline factory does not resolve unregistered network references', async () => {
  const bytes = encode({ $schema: 'https://json-schema.org/draft/2020-12/schema', $defs: { External: { $ref: 'https://unregistered.invalid/schema.json' } } })
  await assert.rejects(SchemaCatalogue.create({ schema_version: '1.0.0', base_uri: base.base_uri, schemas: [{ name: 'external', path: 'external.json', sha256: await sha256(bytes) }] }, new Map([['external.json', bytes]])), /can't resolve reference/)
})

test('simulation extension is independently pinned and packaged without rewriting publications', async () => {
  const manifest = await mergePublishedCatalogue(baseBytes, extensionBytes, simulationBytes)
  assert.deepEqual(manifest.schemas.slice(0, base.schemas.length), base.schemas)
  assert.deepEqual(manifest.schemas.slice(-simulation.schemas.length), simulation.schemas)
  await assert.rejects(mergePublishedCatalogue(baseBytes, extensionBytes, Buffer.concat([simulationBytes, Buffer.from('\n')])), /模拟扩展清单摘要/)
  const catalogue: SchemaCatalogue = await loadProject(root)
  assert.equal(catalogue.schemaRef('simulation-execution', 'SimulationInvocationAdmission'), 'urn:medical:simulation-execution:1.0.0#/$defs/SimulationInvocationAdmission')
  assert.equal(catalogue.schemaRef('local-simulation-provenance'), 'urn:medical:local-simulation-provenance:1.0.0')
  const counter = { unit: 'CANONICAL_UTF8_BYTE', revision: 'LOCAL_SIMULATION_CANONICAL_UTF8_BYTE_V1', implementation_sha256: 'a'.repeat(64), measurement_schema_sha256: 'b'.repeat(64) }
  catalogue.validate('simulation-execution', counter, 'CounterIdentity')
  assert.throws(() => catalogue.validate('simulation-execution', { ...counter, unit: 'TOKEN' }, 'CounterIdentity'))
  assert.throws(() => catalogue.validate('simulation-execution', { ...counter, approved: true }, 'CounterIdentity'))
  for (const entry of simulation.schemas) assert.equal(await sha256(await readFile(resolve(root, entry.path))), entry.sha256)
})


test('simulation context binds exact source roles, original object and prompt identity', async () => {
  const catalogue: SchemaCatalogue = await loadProject(root)
  const context = JSON.parse(await readFile(resolve(root, 'contracts/fixtures/simulation-context.synthetic.json'), 'utf-8')) as Record<string, unknown>
  const original = catalogue.bind('simulation-execution', context, 'SimulationContextManifest')
  const reversed = structuredClone(context)
  ;(reversed['source_manifest'] as unknown[]).reverse()
  assert.throws(() => catalogue.validate('simulation-execution', reversed, 'SimulationContextManifest'))
  const missingPrompt = structuredClone(context)
  delete missingPrompt['prompt_revision']
  assert.throws(() => catalogue.validate('simulation-execution', missingPrompt, 'SimulationContextManifest'))
  const changed = { ...context, prompt_revision: '22222222-2222-4222-8222-222222222222' }
  assert.notEqual(catalogue.bind('simulation-execution', changed, 'SimulationContextManifest').canonicalJson, original.canonicalJson)
})


test('candidate dependency manifest has explicit independent version and bounded strict membership', async () => {
  const catalogue: SchemaCatalogue = await loadProject(root)
  const value = { schema_version: '1.0.0', manifest_kind: 'SIMULATION_CANDIDATE_DEPENDENCIES', dependencies: [1, 2, 3].map(n => ({ version_id: `${String(n).padStart(8, '0')}-1111-4111-8111-111111111111`, content_sha256: String(n).repeat(64) })) }
  catalogue.validate('simulation-execution', value, 'SimulationCandidateDependencyManifest')
  assert.throws(() => catalogue.validate('simulation-execution', { dependencies: value.dependencies }, 'SimulationCandidateDependencyManifest'))
  assert.throws(() => catalogue.validate('simulation-execution', { ...value, dependencies: [...value.dependencies, value.dependencies[0]] }, 'SimulationCandidateDependencyManifest'))
  assert.throws(() => catalogue.validate('simulation-execution', { ...value, approved: true }, 'SimulationCandidateDependencyManifest'))
})

test('fifth fixed context publication has exact 25 schema and 503 definition coverage', async () => {
  const bytes = await readFile(resolve(root, contextPath))
  const full = await mergePublishedCatalogue(baseBytes, extensionBytes, simulationBytes, runtimeBytes, bytes)
  assert.equal(full.schemas.length, 25)
  const catalogue = await loadProject(root)
  assert.equal(catalogue.definitionCount, 503)
  assert.equal(catalogue.schemaRef('evaluation-context-compiler'), 'urn:medical:evaluation-context-compiler:1.0.0')
  assert.equal(catalogue.schemaRef('formal-evaluation-input'), 'urn:medical:formal-evaluation-input:1.0.0')
})
test('fifth publication does not accept altered raw bytes', async () => {
  const bytes = await readFile(resolve(root, contextPath))
  await assert.rejects(mergePublishedCatalogue(baseBytes, extensionBytes, simulationBytes, runtimeBytes, Buffer.concat([bytes, Buffer.from('\n')])), /context extension digest/)
})

import { publicationFixture } from './publication-fixture.js'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import test from 'node:test'
import { SchemaRejection } from '../src/errors.js'
import { decodeDataset } from '../src/dataset.js'
import { loadProject } from '../src/load-project.js'
import { sha256 } from '../src/catalogue.js'

const root = resolve('../..')
const currentPublication = await publicationFixture(root)
const catalogue = await loadProject(root)
const dataset = decodeDataset(catalogue, JSON.parse(await readFile(resolve(root, 'data-design/sample_dataset.json'), 'utf-8')))

test('业务全部定义使用各自固定URN身份', async () => {
  for (const [name, file] of [['templates', 'templates.schema.json'], ['template-operations', 'template-operations.schema.json'], ['tasks', 'tasks.schema.json'], ['collections', 'collections.schema.json'], ['rule-expressions', 'rule-expressions.schema.json'], ['template-applicability', 'template-applicability.schema.json'], ['agent-release', 'agent-release.schema.json'], ['task-plan-view', 'task-plan-view.schema.json'], ['evaluation-results', 'evaluation-results.schema.json']] as const) {
    const bytes = await readFile(resolve(root, 'business/medical-persistence/src/main/resources', file))
    const document = JSON.parse(bytes.toString('utf-8')) as { $id: string; $defs: Record<string, unknown> }
    assert.equal(catalogue.schemaRef(name), document.$id)
    for (const definition of Object.keys(document.$defs)) assert.equal(catalogue.schemaRef(name, definition), `${document.$id}#/$defs/${definition}`)
  }
  assert.equal(catalogue.definitionCount, currentPublication.definitionCount)
})

test('获授权原件摘要通过模板Schema校验并保持固定摘要', async () => {
  const digest = await sha256(await readFile(resolve(root, 'business/medical-persistence/src/main/resources/templates.schema.json')))
  for (const source of dataset.sources) {
    const bound = catalogue.bind('templates', source.sha256, 'Hash')
    assert.equal(bound.schemaRef, 'urn:medical:templates:1.0.0#/$defs/Hash')
    assert.equal(bound.schemaSha256, digest)
    assert.equal(JSON.parse(bound.canonicalJson), source.sha256)
    assert.throws(() => catalogue.bind('templates', source, 'Hash'), SchemaRejection)
  }
})

test('模板发布保持封闭内容且真实参考资料不能成为客户模板', () => {
  assert.throws(() => catalogue.bind('templates', dataset, 'Content'), SchemaRejection)
  assert.throws(() => catalogue.bind('template-operations', dataset), SchemaRejection)
  for (const source of dataset.sources) {
    const visited = new Set<string>()
    const bound = catalogue.bind('templates', source.size_bytes.toString(), 'Count', location => visited.add(location.uri))
    assert.equal(JSON.parse(bound.canonicalJson), source.size_bytes.toString())
    assert.deepEqual([...visited], ['urn:medical:templates:1.0.0#/$defs/Count'])
  }
})

test('任务引用完整共享范围与计划且真实资料不能声明任务身份', () => {
  assert.throws(() => catalogue.bind('tasks', dataset, 'Create'), SchemaRejection)
  assert.throws(() => catalogue.bind('tasks', dataset, 'PlanVersionInput'), SchemaRejection)
  for (const mode of ['PROJECT_APPROVED', 'COLLECTION_VERSIONS', 'SOURCE_VERSIONS', 'COMBINED']) {
    const scope = { mode, collection_versions: [], source_versions: [], network_enabled: false, network_policy_revision: null, approved_query_revision: null }
    assert.deepEqual(JSON.parse(catalogue.bind('interfaces', scope, 'SourceScope').canonicalJson), scope)
    assert.throws(() => catalogue.bind('interfaces', { ...scope, network_enabled: true }, 'SourceScope'), SchemaRejection)
  }
})

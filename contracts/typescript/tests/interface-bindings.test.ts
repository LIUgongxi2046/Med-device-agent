import assert from 'node:assert/strict'
import { loadProject } from '../src/load-project.js'
import { unsignedRevision } from '../src/wire.js'
import test from 'node:test'
import { resolve } from 'node:path'
import { readFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { parse } from 'yaml'
import { bindInterface, INTERFACE_DEFINITIONS } from '../src/interface-bindings.js'
import { decodeDataset } from '../src/dataset.js'
import type { SchemaCatalogue } from '../src/catalogue.js'

const catalogue: SchemaCatalogue = await loadProject(resolve('../..'))
const dataset = decodeDataset(catalogue, JSON.parse(await readFile(resolve('../../data-design/sample_dataset.json'), 'utf-8')))

test('Schema 结构通过后按原始定义执行表示校验', () => {
  const beyondMaximum = (1n << 64n).toString()
  catalogue.validate('interfaces', beyondMaximum, 'Revision')
  assert.throws(() => catalogue.bind('interfaces', beyondMaximum, 'Revision', (location, value) => {
    if (location.uri === catalogue.schemaRef('interfaces', 'Revision')) unsignedRevision(value)
  }), /超出 BIGINT UNSIGNED 范围/)
})

test('接口类型绑定覆盖全部104项定义', async () => {
  const schema = parse(await readFile(resolve('../../backend-design/interfaces.schema.yaml'), 'utf-8')) as { $defs: Record<string, unknown> }
  assert.equal(new Set(INTERFACE_DEFINITIONS).size, 104)
  assert.deepEqual([...INTERFACE_DEFINITIONS].sort(), Object.keys(schema.$defs).sort())
  for (const definition of INTERFACE_DEFINITIONS) assert.throws(() => bindInterface(catalogue, definition, dataset), /SCHEMA_VALIDATION_FAILED/)
})

test('接口标量使用实际登记值并拒绝错误身份与范围', () => {
  for (const source of dataset.sources) {
    assert.equal(bindInterface(catalogue, 'Hash', source.sha256).read(), source.sha256)
    assert.equal(bindInterface(catalogue, 'Revision', source.size_bytes.toString()).read(), source.size_bytes.toString())
    assert.throws(() => bindInterface(catalogue, 'Id', source.source_id), /SCHEMA_VALIDATION_FAILED/)
    assert.equal(bindInterface(catalogue, 'NullableText', source.parent_source_id).read(), source.parent_source_id)
  }
  const execution = randomUUID()
  assert.equal(bindInterface(catalogue, 'Id', execution).read(), execution)
  assert.deepEqual(bindInterface(catalogue, 'Ids', [execution]).read(), [execution])
  assert.throws(() => bindInterface(catalogue, 'Id', execution.toUpperCase()), /小写标准形式/)
  assert.throws(() => bindInterface(catalogue, 'Ids', [execution.toUpperCase()]), /小写标准形式/)
  assert.throws(() => bindInterface(catalogue, 'Ids', [execution, execution]), /SCHEMA_VALIDATION_FAILED/)
  assert.equal(bindInterface(catalogue, 'NullableId', null).read(), null)
  assert.equal(bindInterface(catalogue, 'NullableHash', null).read(), null)
  assert.equal(bindInterface(catalogue, 'Timestamp', dataset.created_at).read(), dataset.created_at)
  assert.throws(() => bindInterface(catalogue, 'Timestamp', dataset.created_at.replace('+00:00', '+08:00')), /UTC/)
  const maximum = ((1n << 64n) - 1n).toString()
  assert.equal(bindInterface(catalogue, 'Revision', maximum).read(), maximum)
  assert.throws(() => bindInterface(catalogue, 'Revision', (1n << 64n).toString()), /BIGINT UNSIGNED/)
})

test('实际登记包的引用和联合分支由 Ajv 解析并提供原始身份', () => {
  const visits: { uri: string; format: string | undefined }[] = []
  const bound = catalogue.bind('dataset', dataset, undefined, (location) => { visits.push(location) })
  assert.deepEqual(JSON.parse(bound.canonicalJson), dataset)
  for (const visit of visits) assert.ok(visit.uri.startsWith(catalogue.schemaRef('dataset')))
  assert.ok(visits.some(visit => visit.format === 'date-time'))
  for (const record of dataset.records) assert.ok(visits.some(visit => visit.uri === catalogue.schemaRef('dataset', record.locator.kind === 'TXT' ? 'text' : record.locator.kind.toLowerCase())))
  assert.ok(visits.some(visit => visit.uri.includes('/oneOf/')))
})

test('真实登记读取的技术 Envelope 保持独立内容及自由数据', () => {
  const input = { schema_version: '1.0.0', request_id: randomUUID(), resource_revision: null, data: dataset }
  const bound = bindInterface(catalogue, 'Envelope', input)
  assert.ok(Object.isFrozen(bound))
  assert.deepEqual(bound.read(), input)
  const read = bound.read()
  assert.ok(read.data && typeof read.data === 'object' && !Array.isArray(read.data))
  read.data.sources = []
  assert.deepEqual(bound.read(), input)
  assert.throws(() => bindInterface(catalogue, 'Envelope', { ...input, resource_revision: (1n << 64n).toString() }), /BIGINT UNSIGNED/)
  assert.throws(() => bindInterface(catalogue, 'Envelope', { ...input, extra: true }), /SCHEMA_VALIDATION_FAILED/)
})

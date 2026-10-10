import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import test from 'node:test'
import { assertJson, SchemaCatalogue } from '../src/catalogue.js'
import { decodeDataset } from '../src/dataset.js'
import { ERROR_CODES, parseErrorCode, SchemaRejection } from '../src/errors.js'
import { loadProject } from '../src/load-project.js'
import { bindModelStream, decodeModelStream, MODEL_STREAM_DEFINITIONS } from '../src/model-stream.js'
import { codePointQuote, decimalValue, unsignedRevision, utcTime, uuidValue } from '../src/wire.js'

const root = resolve('../..')
const catalogue: SchemaCatalogue = await loadProject(root)
const dataset = decodeDataset(catalogue, JSON.parse(await readFile(resolve(root, 'data-design/sample_dataset.json'), 'utf-8')))

test('实际登记包与全部共享定义使用相同发布集合', () => {
  catalogue.validate('dataset', dataset)
  assert.equal(catalogue.definitionCount, 307)
  const bound = catalogue.bind('dataset', dataset)
  assert.deepEqual(JSON.parse(bound.canonicalJson), dataset)
})

test('未知字段、版本与时间损坏均拒绝', () => {
  assert.throws(() => catalogue.validate('dataset', { ...dataset, production_approved: true }), /SCHEMA_VALIDATION_FAILED/)
  assert.throws(() => catalogue.validate('dataset', { ...dataset, schema_version: dataset.schema_version + '-changed' }), /SCHEMA_VALIDATION_FAILED/)
  assert.throws(() => catalogue.validate('dataset', { ...dataset, created_at: dataset.created_at.replace('T', 'X') }), /SCHEMA_VALIDATION_FAILED/)
  assert.throws(() => catalogue.validate('https://unregistered.invalid/schema', dataset), /SCHEMA_UNSUPPORTED/)
})

test('JSON 属性名称与属性内容使用相同 Unicode 检查', () => {
  assertJson(dataset)
  const invalid = String.fromCharCode(0xd800)
  assert.throws(() => assertJson({ [invalid]: dataset.sources[0]!.source_id }), /无效 Unicode/)
  assert.throws(() => assertJson({ sources: [{ [invalid]: dataset.sources[0]!.source_id }] }), /无效 Unicode/)
  assert.throws(() => assertJson(invalid), /无效 Unicode/)
})

test('Schema 拒绝携带标准代码并保护实际资料内容', () => {
  const version = dataset.schema_version + '-changed'
  assert.throws(() => catalogue.validate('dataset', { ...dataset, schema_version: version }), (error: unknown) => {
    assert.ok(error instanceof SchemaRejection)
    assert.equal(error.code, 'SCHEMA_INVALID')
    assert.equal(error.message, 'SCHEMA_VALIDATION_FAILED')
    assert.equal(error.cause, undefined)
    assert.ok(!String(error).includes(version))
    assert.ok(Object.isFrozen(error))
    assert.equal(Reflect.set(error, 'code', 'INTERNAL_ERROR'), false)
    return true
  })
  const sourceId = dataset.sources[0]!.source_id
  for (const operation of [() => catalogue.schemaRef(sourceId), () => catalogue.schemaRef('dataset', sourceId)]) {
    assert.throws(operation, (error: unknown) => {
      assert.ok(error instanceof SchemaRejection)
      assert.equal(error.code, 'SCHEMA_UNSUPPORTED')
      assert.equal(error.message, 'SCHEMA_UNSUPPORTED')
      assert.equal(error.cause, undefined)
      return true
    })
  }
})

test('错误代码拒绝未知值和错误类型并保持枚举集合只读', () => {
  for (const value of [dataset.sources[0]!.source_id, null, dataset.sources, ERROR_CODES[0].toLowerCase()]) {
    assert.throws(() => parseErrorCode(value), (error: unknown) => {
      assert.ok(error instanceof SchemaRejection)
      assert.equal(error.code, 'SCHEMA_UNSUPPORTED')
      assert.equal(error.message, 'SCHEMA_UNSUPPORTED')
      return true
    })
  }
  assert.equal(parseErrorCode('ACCESS_DENIED'), 'ACCESS_DENIED')
  assert.ok(Object.isFrozen(ERROR_CODES))
  assert.equal(Reflect.set(ERROR_CODES, 0, dataset.sources[0]!.source_id), false)
})

test('从原文件重读 code point 范围与摘要', async () => {
  const sources = new Map<string, { source_ref: string }>(dataset.sources.map((entry: { source_id: string; source_ref: string }) => [entry.source_id, entry]))
  let read = 0
  for (const record of dataset.records) {
    const locator = record.locator
    catalogue.validate('dataset', locator, 'locator')
    if (locator.kind !== 'TXT') continue
    const source = sources.get(record.source_id)
    assert.ok(source)
    const text = await readFile(resolve(root, source.source_ref), 'utf-8')
    assert.equal(await codePointQuote(text, locator.char_start, locator.char_end, record.quote_hash), record.text)
    await assert.rejects(codePointQuote(text, locator.char_start, locator.char_end, record.source_version), /选区摘要不一致/)
    read++
  }
  assert.ok(read > 0)
})

test('技术表示检查不创建业务身份或收费记录', () => {
  const execution = randomUUID()
  assert.equal(uuidValue(execution), execution)
  assert.equal(decodeModelStream(catalogue, 'Id', execution), execution)
  assert.throws(() => decodeModelStream(catalogue, 'Id', execution.toUpperCase()), /UUID 必须使用小写标准形式/)
  assert.throws(() => uuidValue(execution.toUpperCase()))
  const maximum = ((1n << 64n) - 1n).toString()
  assert.equal(unsignedRevision(maximum).toString(), maximum)
  for (const value of [maximum + '0', '01', '1.0', 1, true]) assert.throws(() => unsignedRevision(value))
  assert.equal(decimalValue('0.00000001', 24, 8).toFixed(8), '0.00000001')
  assert.equal(decimalValue('0.000000000001', 38, 12).toFixed(12), '0.000000000001')
  for (const value of ['0.000000001', '10000000000000000', '1e-8', 'NaN', 0.1]) assert.throws(() => decimalValue(value, 24, 8))
  assert.equal(utcTime(dataset.created_at).toString({ fractionalSecondDigits: 6 }), dataset.created_at.replace('+00:00', 'Z'))
  assert.throws(() => utcTime(dataset.created_at.replace('+00:00', '+08:00')))
})

test('模型静态类型覆盖唯一 Schema 的全部定义', async () => {
  const document = JSON.parse(await readFile(resolve(root, 'agent-design/model-stream.schema.json'), 'utf-8')) as { $defs: Record<string, unknown> }
  assert.equal(new Set(MODEL_STREAM_DEFINITIONS).size, MODEL_STREAM_DEFINITIONS.length)
  assert.deepEqual([...MODEL_STREAM_DEFINITIONS].sort(), Object.keys(document.$defs).sort())
  for (const definition of MODEL_STREAM_DEFINITIONS) assert.match(catalogue.schemaRef('model-stream', definition), /#\/\$defs\//)
})

test('模型字段读取使用实际登记值，错误领域及未知字段拒绝', () => {
  for (const source of dataset.sources) {
    const bound = bindModelStream(catalogue, 'Text', source.source_id)
    assert.equal(bound.read(), source.source_id)
    assert.throws(() => decodeModelStream(catalogue, 'Id', source.source_id), /SCHEMA_VALIDATION_FAILED/)
    assert.equal(decodeModelStream(catalogue, 'Hash', source.sha256), source.sha256)
    assert.ok(Object.isFrozen(bound))
  }
  for (const record of dataset.records) assert.equal(decodeModelStream(catalogue, 'Text', record.section_title), record.section_title)
  for (const definition of MODEL_STREAM_DEFINITIONS) {
    assert.throws(() => decodeModelStream(catalogue, definition, dataset), /SCHEMA_VALIDATION_FAILED/)
  }
})

import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import test from 'node:test'
import ts from 'typescript'
import { parse } from 'yaml'
import { decodeDataset } from '../src/dataset.js'
import { COMMON_DEFINITIONS } from '../src/interfaces-common.js'
import { loadProject } from '../src/load-project.js'
import {
  bindResourceDetails, decodeResourceDetails, RESOURCE_DETAILS_DEFINITIONS, RESOURCE_DETAILS_WIRE_FIELDS,
} from '../src/resource-details.js'

interface SchemaDefinition {
  type?: string | string[]
  $ref?: string
  properties?: Record<string, SchemaDefinition>
  required?: string[]
  enum?: string[]
  const?: string | boolean | number
}

const root = resolve('../..')
const catalogue = await loadProject(root)
const dataset = decodeDataset(catalogue, JSON.parse(await readFile(resolve(root, 'data-design/sample_dataset.json'), 'utf-8')))
const common = parse(await readFile(resolve(root, 'backend-design/interfaces.schema.yaml'), 'utf-8')) as { $defs: Record<string, SchemaDefinition> }
const details = parse(await readFile(resolve(root, 'backend-design/resource-details.schema.yaml'), 'utf-8')) as { $defs: Record<string, SchemaDefinition> }

test('资源类型覆盖唯一 Schema 的全部定义', () => {
  assert.equal(new Set(RESOURCE_DETAILS_DEFINITIONS).size, RESOURCE_DETAILS_DEFINITIONS.length)
  assert.deepEqual([...RESOURCE_DETAILS_DEFINITIONS].sort(), Object.keys(details.$defs).sort())
  for (const definition of RESOURCE_DETAILS_DEFINITIONS) assert.match(catalogue.schemaRef('resource-details', definition), /#\/\$defs\//)
  for (const definition of COMMON_DEFINITIONS) assert.ok(Object.hasOwn(common.$defs, definition))
})

function literalValues(type: ts.Type): (string | boolean | number)[] {
  if (type.isUnion()) return type.types.flatMap(literalValues)
  if (type.isStringLiteral() || type.isNumberLiteral()) return [type.value]
  if (type.flags & ts.TypeFlags.BooleanLiteral) return [(type as ts.Type & { intrinsicName: string }).intrinsicName === 'true']
  throw new Error('静态枚举包含未限定类型')
}

test('实际 TypeScript 编译器核查全部资源及公共对象字段', () => {
  const configFile = ts.readConfigFile(resolve('tsconfig.json'), ts.sys.readFile)
  assert.equal(configFile.error, undefined)
  const config = ts.parseJsonConfigFileContent(configFile.config, ts.sys, resolve('.'))
  assert.equal(config.errors.length, 0)
  const program = ts.createProgram(config.fileNames, config.options)
  assert.equal(ts.getPreEmitDiagnostics(program).length, 0)
  const checker = program.getTypeChecker()
  const groups = [
    { file: 'src/interfaces-common.ts', definitions: COMMON_DEFINITIONS, schemas: common.$defs },
    { file: 'src/resource-details.ts', definitions: RESOURCE_DETAILS_DEFINITIONS, schemas: details.$defs },
  ]
  let objects = 0
  for (const group of groups) {
    const source = program.getSourceFile(resolve(group.file))
    assert.ok(source)
    const module = checker.getSymbolAtLocation(source)
    assert.ok(module)
    const exports = new Map(checker.getExportsOfModule(module).map(symbol => [symbol.name, symbol]))
    for (const name of group.definitions) {
      const symbol = exports.get(name)
      assert.ok(symbol, name)
      const schema = group.schemas[name]
      assert.ok(schema)
      if (!schema.properties) continue
      const declared = checker.getDeclaredTypeOfSymbol(symbol.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(symbol) : symbol)
      const properties = checker.getPropertiesOfType(declared)
      assert.deepEqual(properties.map(property => property.name).sort(), Object.keys(schema.properties).sort(), name)
      assert.deepEqual(
        properties.filter(property => !(property.flags & ts.SymbolFlags.Optional)).map(property => property.name).sort(),
        [...schema.required ?? []].sort(), name,
      )
      for (const property of properties) {
        const field: SchemaDefinition | undefined = schema.properties[property.name]
        assert.ok(field)
        const type = checker.getTypeOfSymbolAtLocation(property, source)
        if (field.enum) assert.deepEqual(literalValues(type).sort(), [...field.enum].sort(), `${name}.${property.name}`)
        if (field.const !== undefined) assert.deepEqual(literalValues(type), [field.const], `${name}.${property.name}`)
        if (field.type === 'string') assert.ok(type.flags & ts.TypeFlags.String, `${name}.${property.name}`)
        if (field.type === 'integer') assert.ok(type.flags & ts.TypeFlags.Number, `${name}.${property.name}`)
        if (field.type === 'boolean') assert.ok(type.flags & ts.TypeFlags.Boolean, `${name}.${property.name}`)
      }
      objects++
    }
  }
  assert.equal(objects, 27)
})

test('表示规则的字段集合与唯一 Schema 中的引用完整一致', () => {
  const collected = { Id: new Set<string>(), Ids: new Set<string>(), Revision: new Set<string>(), Timestamp: new Set<string>() }
  const kinds = ['Id', 'Ids', 'Revision', 'Timestamp'] as const
  const documents = [
    { definitions: COMMON_DEFINITIONS, schemas: common.$defs },
    { definitions: RESOURCE_DETAILS_DEFINITIONS, schemas: details.$defs },
  ]
  for (const document of documents) {
    for (const definition of document.definitions) {
      const schema = document.schemas[definition]
      assert.ok(schema)
      for (const [name, field] of Object.entries(schema.properties ?? {})) {
        if (!field.$ref) continue
        const reference = new URL(field.$ref, 'https://schemas.medical-agent.invalid/1.0.0/backend-design/resource-details.schema.yaml')
        for (const kind of kinds) {
          if (reference.hash === `#/$defs/${kind}` || (kind === 'Id' && reference.hash === '#/$defs/NullableId')) collected[kind].add(name)
        }
      }
    }
  }
  for (const kind of kinds) {
    assert.equal(new Set(RESOURCE_DETAILS_WIRE_FIELDS[kind]).size, RESOURCE_DETAILS_WIRE_FIELDS[kind].length, kind)
    assert.deepEqual([...RESOURCE_DETAILS_WIRE_FIELDS[kind]].sort(), [...collected[kind]].sort(), kind)
  }
})

test('实际登记标量和技术身份读取后保持独立内容', () => {
  for (const source of dataset.sources) {
    assert.equal(decodeResourceDetails(catalogue, 'Revision', source.size_bytes.toString()), source.size_bytes.toString())
    assert.equal(decodeResourceDetails(catalogue, 'NullableId', null), null)
    const bound = bindResourceDetails(catalogue, 'Texts', source.allowed_use)
    assert.ok(Object.isFrozen(bound))
    const read = bound.read()
    assert.deepEqual(read, source.allowed_use)
    read.pop()
    assert.deepEqual(bound.read(), source.allowed_use)
    assert.throws(() => decodeResourceDetails(catalogue, 'Id', source.source_id), /SCHEMA_VALIDATION_FAILED/)
  }
  const execution = randomUUID()
  assert.equal(decodeResourceDetails(catalogue, 'Id', execution), execution)
  assert.deepEqual(decodeResourceDetails(catalogue, 'Ids', [execution]), [execution])
  assert.throws(() => decodeResourceDetails(catalogue, 'Ids', [execution, execution]), /SCHEMA_VALIDATION_FAILED/)
  assert.throws(() => decodeResourceDetails(catalogue, 'Id', execution.toUpperCase()), /UUID 必须使用小写标准形式/)
  assert.throws(() => decodeResourceDetails(catalogue, 'Ids', [execution.toUpperCase()]), /UUID 必须使用小写标准形式/)
  const maximum = ((1n << 64n) - 1n).toString()
  assert.equal(decodeResourceDetails(catalogue, 'Revision', maximum), maximum)
  assert.throws(() => decodeResourceDetails(catalogue, 'Revision', ((1n << 64n)).toString()), /超出 BIGINT UNSIGNED 范围/)
  for (const definition of RESOURCE_DETAILS_DEFINITIONS) {
    assert.throws(() => decodeResourceDetails(catalogue, definition, dataset), /SCHEMA_VALIDATION_FAILED/)
  }
})

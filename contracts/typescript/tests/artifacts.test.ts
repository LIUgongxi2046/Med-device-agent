import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import test from 'node:test'
import ts from 'typescript'
import { ARTIFACT_DEFINITIONS, ARTIFACT_WIRE_FIELDS, bindArtifact } from '../src/artifacts.js'
import { decodeDataset } from '../src/dataset.js'
import { loadProject } from '../src/load-project.js'

interface Definition {
  $ref?: string; type?: string | string[]; properties?: Record<string, Definition>; required?: string[]
  enum?: (string | number | boolean)[]; const?: string | number | boolean; oneOf?: Definition[]; items?: Definition
}
const root = resolve('../..')
const document = JSON.parse(await readFile(resolve(root, 'agent-design/artifacts.schema.json'), 'utf-8')) as Definition & { $defs: Record<string, Definition> }
const catalogue = await loadProject(root)
const dataset = decodeDataset(catalogue, JSON.parse(await readFile(resolve(root, 'data-design/sample_dataset.json'), 'utf-8')))
const configFile = ts.readConfigFile(resolve('tsconfig.json'), ts.sys.readFile)
assert.equal(configFile.error, undefined)
const config = ts.parseJsonConfigFileContent(configFile.config, ts.sys, resolve('.'))
assert.equal(config.errors.length, 0)
const program = ts.createProgram(config.fileNames, config.options)
const checker = program.getTypeChecker()
const source = program.getSourceFile(resolve('src/artifacts.ts'))
assert.ok(source)
const module = checker.getSymbolAtLocation(source)
assert.ok(module)
const exports = new Map(checker.getExportsOfModule(module).map(symbol => [symbol.name, symbol]))

function declared(name: string): ts.Type {
  const symbol = exports.get(name)
  assert.ok(symbol, name)
  return checker.getDeclaredTypeOfSymbol(symbol.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(symbol) : symbol)
}
function field(type: ts.Type, name: string): ts.Type {
  const property = checker.getPropertyOfType(type, name)
  assert.ok(property, name)
  return checker.getTypeOfSymbolAtLocation(property, source!)
}
function literals(type: ts.Type): (string | number | boolean)[] {
  if (type.isUnion()) return type.types.flatMap(literals)
  if (type.isStringLiteral() || type.isNumberLiteral()) return [type.value]
  if (type.flags & ts.TypeFlags.BooleanLiteral) return [(type as ts.Type & { intrinsicName: string }).intrinsicName === 'true']
  throw new Error('枚举属性包含未限定类型')
}
function variant(name: string, discriminant: string, value: string): ts.Type {
  const type = declared(name)
  assert.ok(type.isUnion())
  const found: ts.Type | undefined = type.types.find(member => literals(field(member, discriminant)).includes(value))
  assert.ok(found)
  return found
}

test('产物全部49项定义和37个对象的静态字段来自原始 Schema', () => {
  assert.equal(ts.getPreEmitDiagnostics(program).length, 0)
  assert.equal(new Set(ARTIFACT_DEFINITIONS).size, 49)
  assert.deepEqual([...ARTIFACT_DEFINITIONS].sort(), Object.keys(document.$defs).sort())
  assert.deepEqual(checker.getPropertiesOfType(declared('ArtifactDefinitions')).map(property => property.name).sort(), Object.keys(document.$defs).sort())
  let objects = 0
  for (const [name, schema] of [...Object.entries(document.$defs), ['ProfessionalArtifact', document]] as [string, Definition][]) {
    const type = declared(name)
    if (!schema.properties) continue
    const properties = checker.getPropertiesOfType(type)
    assert.deepEqual(properties.map(property => property.name).sort(), Object.keys(schema.properties).sort(), name)
    assert.deepEqual(properties.filter(property => !(property.flags & ts.SymbolFlags.Optional)).map(property => property.name).sort(), [...schema.required!].sort(), name)
    for (const [name, fieldSchema] of Object.entries(schema.properties)) {
      const type_ = field(type, name)
      if (fieldSchema.const !== undefined) assert.deepEqual(literals(type_), [fieldSchema.const], name)
      if (fieldSchema.enum) assert.deepEqual(literals(type_).sort(), [...fieldSchema.enum].sort(), name)
      if (fieldSchema.type === 'integer') assert.ok(type_.flags & ts.TypeFlags.Number, name)
      if (fieldSchema.type === 'boolean') assert.ok(type_.flags & ts.TypeFlags.Boolean, name)
    }
    objects++
  }
  assert.equal(objects, 37)
})

test('八种产物输出的内容类型对应原始联合分支', () => {
  const type = declared('ArtifactOutput')
  assert.ok(type.isUnion())
  const branches = document.$defs.ArtifactOutput!.oneOf!
  assert.equal(type.types.length, branches.length)
  for (const branch of branches) {
    const name = new URL(branch.$ref!, 'https://schemas.medical-agent.invalid/artifacts.schema.json').hash.slice('#/$defs/'.length)
    const schema = document.$defs[name]!
    const kind = schema.properties!.artifact_kind!.const
    const found: ts.Type | undefined = type.types.find(member => literals(field(member, 'artifact_kind'))[0] === kind)
    assert.ok(found)
    const contentName = new URL(schema.properties!.content!.$ref!, 'https://schemas.medical-agent.invalid/artifacts.schema.json').hash.slice('#/$defs/'.length)
    assert.ok(checker.isTypeAssignableTo(field(found, 'content'), declared(contentName)))
    assert.ok(checker.isTypeAssignableTo(declared(contentName), field(found, 'content')))
  }
})

test('任务、Decimal、可核查陈述和正文汇总保留条件类型', () => {
  const task = variant('ProfessionalArtifact', 'execution_kind', 'TASK')
  for (const name of ['project_id', 'plan_revision', 'snapshot_id']) assert.ok(field(task, name).flags & ts.TypeFlags.String)
  const job = variant('ProfessionalArtifact', 'execution_kind', 'SYSTEM_JOB')
  for (const name of ['project_id', 'plan_revision', 'snapshot_id']) assert.ok(field(job, name).isUnion())
  const decimal = variant('FieldValue', 'value_kind', 'DECIMAL')
  assert.ok(field(decimal, 'raw_text').flags & ts.TypeFlags.String)
  assert.ok(field(decimal, 'decimal_value').flags & ts.TypeFlags.String)
  assert.ok(field(decimal, 'precision').flags & ts.TypeFlags.Number)
  for (const kind of ['PRODUCT_FACT', 'REGULATORY_REQUIREMENT', 'METHOD_REQUIREMENT', 'STUDY_RESULT']) {
    assert.ok(checker.isTupleType(field(variant('ClaimRecord', 'claim_kind', kind), 'evidence_links')))
  }
  const document_ = variant('AssemblyManifest', 'target_kind', 'DOCUMENT')
  assert.ok(field(document_, 'document_revision').flags & ts.TypeFlags.String)
  const sections = field(document_, 'sections')
  assert.ok(checker.isArrayType(sections))
  const section = checker.getTypeArguments(sections as ts.TypeReference)[0]!
  assert.deepEqual(literals(field(section, 'version_kind')), ['DOCUMENT'])
})

test('产物表示字段与原始 Schema 的身份及证据引用完整一致', () => {
  const expected = { Id: new Set<string>(), Ids: new Set<string>(), EvidenceRef: new Set<string>(), EvidenceRefs: new Set<string>() }
  for (const schema of [document, ...Object.values(document.$defs)]) {
    for (const [name, property] of Object.entries(schema.properties ?? {})) {
      for (const kind of ['Id', 'Ids', 'EvidenceRef', 'EvidenceRefs'] as const) {
        if (property.$ref === `#/$defs/${kind}` || (kind === 'Id' && property.$ref === '#/$defs/NullableId') || (kind === 'Ids' && property.items?.$ref === '#/$defs/Id')) expected[kind].add(name)
      }
    }
  }
  for (const kind of ['Id', 'Ids', 'EvidenceRef', 'EvidenceRefs'] as const) {
    assert.equal(new Set(ARTIFACT_WIRE_FIELDS[kind]).size, ARTIFACT_WIRE_FIELDS[kind].length)
    assert.deepEqual([...ARTIFACT_WIRE_FIELDS[kind]].sort(), [...expected[kind]].sort(), kind)
  }
})

test('实际登记标量及技术身份绑定后保持独立内容', () => {
  for (const source of dataset.sources) {
    assert.equal(bindArtifact(catalogue, 'Text', source.source_id).read(), source.source_id)
    assert.equal(bindArtifact(catalogue, 'Hash', source.sha256).read(), source.sha256)
    assert.throws(() => bindArtifact(catalogue, 'Id', source.source_id), /SCHEMA_VALIDATION_FAILED/)
  }
  const execution = randomUUID()
  const bound = bindArtifact(catalogue, 'Ids', [execution])
  assert.ok(Object.isFrozen(bound))
  const read = bound.read()
  read.pop()
  assert.deepEqual(bound.read(), [execution])
  assert.equal(bindArtifact(catalogue, 'NullableId', null).read(), null)
  assert.throws(() => bindArtifact(catalogue, 'Id', execution.toUpperCase()), /小写标准形式/)
  assert.throws(() => bindArtifact(catalogue, 'Ids', [execution.toUpperCase()]), /小写标准形式/)
  assert.throws(() => bindArtifact(catalogue, 'Ids', [execution, execution]), /SCHEMA_VALIDATION_FAILED/)
  assert.equal(bindArtifact(catalogue, 'Decimal', '0.000000000001').read(), '0.000000000001')
  assert.throws(() => bindArtifact(catalogue, 'Decimal', '0.0000000000001'), /字段精度/)
  assert.throws(() => bindArtifact(catalogue, 'Decimal', '100000000000000000000000000'), /字段精度/)
  for (const definition of ARTIFACT_DEFINITIONS) assert.throws(() => bindArtifact(catalogue, definition, dataset), /SCHEMA_VALIDATION_FAILED/)
  assert.throws(() => bindArtifact(catalogue, undefined, dataset), /SCHEMA_VALIDATION_FAILED/)
})

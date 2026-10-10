import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import test from 'node:test'
import ts from 'typescript'
import { parse } from 'yaml'

interface Definition {
  $ref?: string; type?: string | string[]; properties?: Record<string, Definition>; required?: string[]
  enum?: (string | number | boolean)[]; const?: string | number | boolean; allOf?: Definition[]; oneOf?: Definition[]
}
const document = parse(await readFile(resolve('../../backend-design/interfaces.schema.yaml'), 'utf-8')) as { $defs: Record<string, Definition> }
const configFile = ts.readConfigFile(resolve('tsconfig.json'), ts.sys.readFile)
assert.equal(configFile.error, undefined)
const config = ts.parseJsonConfigFileContent(configFile.config, ts.sys, resolve('.'))
assert.equal(config.errors.length, 0)
const program = ts.createProgram(config.fileNames, config.options)
const checker = program.getTypeChecker()
const source = program.getSourceFile(resolve('src/interfaces.ts'))
assert.ok(source)
const module = checker.getSymbolAtLocation(source)
assert.ok(module)
const exports = new Map(checker.getExportsOfModule(module).map(symbol => [symbol.name, symbol]))

function definitionType(name: string): ts.Type {
  const symbol = exports.get(name)
  assert.ok(symbol, name)
  return checker.getDeclaredTypeOfSymbol(symbol.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(symbol) : symbol)
}
function fields(definition: Definition): { properties: Record<string, Definition>; required: string[] } {
  const properties = { ...definition.properties }
  const required = new Set(definition.required)
  for (const base of definition.allOf ?? []) {
    if (!base.$ref) continue
    const name = new URL(base.$ref, 'https://schemas.medical-agent.invalid/interfaces.schema.yaml').hash.slice('#/$defs/'.length)
    const original = document.$defs[name]
    assert.ok(original)
    const inherited = fields(original)
    Object.assign(properties, inherited.properties)
    for (const field of inherited.required) required.add(field)
  }
  return { properties, required: [...required] }
}
function literals(type: ts.Type): (string | number | boolean)[] {
  if (type.isUnion()) return type.types.flatMap(literals)
  if (type.isStringLiteral() || type.isNumberLiteral()) return [type.value]
  if (type.flags & ts.TypeFlags.BooleanLiteral) return [(type as ts.Type & { intrinsicName: string }).intrinsicName === 'true']
  throw new Error('枚举属性包含未限定类型')
}
function propertyType(type: ts.Type, name: string): ts.Type {
  const property = checker.getPropertyOfType(type, name)
  assert.ok(property, name)
  return checker.getTypeOfSymbolAtLocation(property, source!)
}

test('实际编译器核查104项接口定义和全部继承对象字段', () => {
  assert.equal(ts.getPreEmitDiagnostics(program).length, 0)
  assert.equal(Object.keys(document.$defs).length, 104)
  assert.deepEqual([...exports.keys()].sort(), Object.keys(document.$defs).sort())
  let objects = 0
  for (const [name, definition] of Object.entries(document.$defs)) {
    const declared = definitionType(name)
    if (!definition.properties) continue
    const expected = fields(definition)
    const properties = checker.getPropertiesOfType(declared)
    assert.deepEqual(properties.map(field => field.name).sort(), Object.keys(expected.properties).sort(), name)
    assert.deepEqual(properties.filter(field => !(field.flags & ts.SymbolFlags.Optional)).map(field => field.name).sort(), expected.required.sort(), name)
    for (const [field, schema] of Object.entries(expected.properties)) {
      const type = propertyType(declared, field)
      if (schema.enum) assert.deepEqual(literals(type).sort(), [...schema.enum].sort(), `${name}.${field}`)
      if (schema.const !== undefined) assert.deepEqual(literals(type), [schema.const], `${name}.${field}`)
      if (schema.type === 'integer') assert.ok(type.flags & ts.TypeFlags.Number, `${name}.${field}`)
      if (schema.type === 'string') assert.ok(type.flags & ts.TypeFlags.String, `${name}.${field}`)
      if (schema.type === 'boolean') assert.ok(type.flags & ts.TypeFlags.Boolean, `${name}.${field}`)
    }
    objects++
  }
  assert.equal(objects, 95)
  const bindingSource = program.getSourceFile(resolve('src/interface-bindings.ts'))
  assert.ok(bindingSource)
  const bindingModule = checker.getSymbolAtLocation(bindingSource)
  assert.ok(bindingModule)
  const mapping = checker.getExportsOfModule(bindingModule).find(symbol => symbol.name === 'InterfaceDefinitions')
  assert.ok(mapping)
  const mappedType = checker.getDeclaredTypeOfSymbol(mapping)
  const mappedProperties = checker.getPropertiesOfType(mappedType)
  assert.deepEqual(mappedProperties.map(property => property.name).sort(), Object.keys(document.$defs).sort())
  for (const property of mappedProperties) {
    const mapped = checker.getTypeOfSymbolAtLocation(property, bindingSource)
    const original = definitionType(property.name)
    assert.ok(checker.isTypeAssignableTo(mapped, original), property.name)
    assert.ok(checker.isTypeAssignableTo(original, mapped), property.name)
  }
})

test('九种事件的静态载荷与原始 Schema 分支完整一致', () => {
  const type = definitionType('PublicEvent')
  assert.ok(type.isUnion())
  const branches = document.$defs.PublicEvent!.oneOf!
  assert.equal(type.types.length, branches.length)
  for (const branch of branches) {
    const fields = branch.properties!
    const variant: ts.Type | undefined = type.types.find(member => literals(propertyType(member, 'event_type'))[0] === fields.event_type!.const)
    assert.ok(variant)
    assert.deepEqual(literals(propertyType(variant, 'resource_kind')), [fields.resource_kind!.const])
    const payloadSchema = fields.payload!
    const name = new URL(payloadSchema.$ref!, 'https://schemas.medical-agent.invalid/interfaces.schema.yaml').hash.slice('#/$defs/'.length)
    const original = document.$defs[name]!
    const payload = propertyType(variant, 'payload')
    assert.deepEqual(checker.getPropertiesOfType(payload).map(field => field.name).sort(), Object.keys(original.properties!).sort())
    const read = payloadSchema.properties?.read_operation_id ?? original.properties!.read_operation_id!
    assert.deepEqual(literals(propertyType(payload, 'read_operation_id')), [read.const])
  }
})

test('管理模块的启用条件保留完整联合类型', () => {
  const type = definitionType('ManagementModule')
  assert.ok(type.isUnion())
  assert.equal(type.types.length, 2)
  for (const member of type.types) {
    const statuses = literals(propertyType(member, 'binding_status'))
    const operations = propertyType(member, 'operations')
    assert.ok(checker.isTupleType(operations))
    const arguments_ = checker.getTypeArguments(operations as ts.TypeReference)
    if (statuses.includes('BOUND')) {
      assert.equal(arguments_.length, 2)
      assert.ok(propertyType(member, 'unavailable_reason').flags & ts.TypeFlags.Null)
      for (const field of ['route_name', 'component_ref', 'source_commit']) assert.ok(propertyType(member, field).flags & ts.TypeFlags.String)
      assert.ok(checker.isTupleType(propertyType(member, 'permission_codes')))
    } else {
      assert.deepEqual(statuses.sort(), ['DISABLED', 'UNBOUND'])
      assert.equal(arguments_.length, 0)
      assert.ok(propertyType(member, 'unavailable_reason').flags & ts.TypeFlags.String)
    }
  }
})

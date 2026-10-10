import Ajv2020 from 'ajv/dist/2020.js'
import type { AnySchema, ValidateFunction } from 'ajv'
import { escapeFragment } from 'ajv/dist/compile/util.js'
import addFormats from 'ajv-formats'
import { canonicalize } from 'json-canonicalize'
import { parse } from 'yaml'
import { SchemaRejection } from './errors.js'

export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue }
export type SchemaName = 'dataset' | 'interfaces' | 'resource-details' | 'artifacts' | 'model-stream' | 'templates' | 'template-operations' | 'tasks' | 'collections' | 'rule-expressions' | 'template-applicability' | 'agent-release' | 'task-plan-view'
export interface SchemaEntry { readonly name: string; readonly path: string; readonly sha256: string }
export interface CatalogueManifest { readonly schema_version: string; readonly base_uri: string; readonly schemas: readonly SchemaEntry[] }
export interface ValidatedPayload { readonly schemaRef: string; readonly schemaSha256: string; readonly canonicalJson: string }
export interface RepresentationLocation { readonly uri: string; readonly format: string | undefined }
export type RepresentationVisitor = (location: RepresentationLocation, value: JsonValue) => void

export async function sha256(bytes: Uint8Array): Promise<string> {
  const owned = new Uint8Array(bytes)
  const digest = await globalThis.crypto.subtle.digest('SHA-256', owned)
  return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('')
}

export function assertJson(value: unknown): asserts value is JsonValue {
  if (value === null || typeof value === 'boolean') return
  if (typeof value === 'string') {
    const text = new TextDecoder('utf-8', { fatal: true }).decode(new TextEncoder().encode(value))
    if (text !== value) throw new Error('JSON 字符串包含无效 Unicode')
    return
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || (Number.isInteger(value) && !Number.isSafeInteger(value))) throw new Error('JSON 大整数必须使用字符串')
    return
  }
  if (Array.isArray(value)) {
    for (const child of value) assertJson(child)
    return
  }
  if (typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype) {
    for (const [key, child] of Object.entries(value)) {
      assertJson(key)
      assertJson(child)
    }
    return
  }
  throw new Error('载荷必须使用 JSON 数据类型')
}

export class SchemaCatalogue {
  private readonly ajv = new Ajv2020({ strictSchema: true, strictTypes: false, strictNumbers: true, allErrors: true, validateFormats: true, allowUnionTypes: true })
  private readonly documents = new Map<string, Record<string, JsonValue>>()
  private readonly uris = new Map<string, string>()
  private readonly digests = new Map<string, string>()
  private readonly validators = new Map<string, ValidateFunction>()
  private readonly origins = new WeakMap<object, string>()

  private constructor() { addFormats(this.ajv) }

  static async create(manifest: CatalogueManifest, files: ReadonlyMap<string, Uint8Array>): Promise<SchemaCatalogue> {
    if (manifest.schema_version !== '1.0.0') throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    const catalogue = new SchemaCatalogue()
    for (const entry of manifest.schemas) {
      if (catalogue.documents.has(entry.name)) throw new Error('Schema 名称重复')
      const bytes = files.get(entry.path)
      if (!bytes) throw new Error('Schema 制品缺少登记资源')
      if (await sha256(bytes) !== entry.sha256) throw new Error('Schema 摘要不一致')
      const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
      const document: unknown = entry.path.endsWith('.yaml') ? parse(text) : JSON.parse(text)
      assertJson(document)
      if (!document || Array.isArray(document) || typeof document !== 'object') throw new Error('Schema 必须使用对象')
      let uri: string
      if (Object.hasOwn(document, '$id')) {
        if (typeof document['$id'] !== 'string') throw new Error('Schema 声明身份必须使用文本')
        const declared = new URL(document['$id'])
        if (declared.hash || declared.search || declared.href !== document['$id']) throw new Error('Schema 声明身份必须使用完整固定引用')
        uri = document['$id']
      } else {
        uri = new URL(entry.path, manifest.base_uri).href
      }
      if ([...catalogue.uris.values()].includes(uri)) throw new Error('Schema 发布引用重复')
      catalogue.documents.set(entry.name, document)
      catalogue.origins.set(document, uri)
      catalogue.uris.set(entry.name, uri)
      catalogue.digests.set(entry.name, entry.sha256)
      catalogue.ajv.addSchema(document as AnySchema, uri)
    }
    // 只使用登记集合；Ajv 不配置远端读取或自动取得缺失 Schema。
    for (const [name, document] of catalogue.documents) {
      catalogue.compile(catalogue.schemaRef(name))
      const definitions = document['$defs']
      if (!definitions || Array.isArray(definitions) || typeof definitions !== 'object') throw new Error('Schema 缺少定义集合')
      for (const [definition, schema] of Object.entries(definitions)) {
        if (schema && typeof schema === 'object') catalogue.origins.set(schema, catalogue.schemaRef(name, definition))
        catalogue.compile(catalogue.schemaRef(name, definition))
      }
    }
    return catalogue
  }

  schemaRef(name: string, definition?: string): string {
    const uri = this.uris.get(name)
    const document = this.documents.get(name)
    if (!uri || !document) throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    if (definition === undefined) return uri
    const definitions = document['$defs']
    if (!definitions || Array.isArray(definitions) || typeof definitions !== 'object' || !Object.hasOwn(definitions, definition)) throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    return `${uri}#/$defs/${definition}`
  }

  private compile(uri: string): void {
    const validator = this.ajv.getSchema(uri)
    if (!validator) throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    this.validators.set(uri, validator)
  }

  validate(name: string, value: unknown, definition?: string): asserts value is JsonValue {
    const validator = this.validators.get(this.schemaRef(name, definition))
    if (!validator) throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    assertJson(value)
    if (!validator(value)) throw new SchemaRejection('SCHEMA_INVALID')
  }

  private reference(uri: string): { schema: AnySchema; uri: string } {
    const validator = this.ajv.getSchema(uri)
    if (!validator || typeof validator.schema !== 'object') throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    const origin = this.origins.get(validator.schema)
    if (!origin) throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    return { schema: validator.schema, uri: origin }
  }

  private matches(uri: string, value: JsonValue): boolean {
    const validator = this.ajv.getSchema(uri)
    if (!validator) throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    const result: unknown = validator(value)
    if (typeof result !== 'boolean') throw new Error('Schema 表示检查要求同步校验')
    return result
  }

  private visit(schema: AnySchema, uri: string, value: JsonValue, visitor: RepresentationVisitor): void {
    if (schema === true) return
    if (schema === false) throw new SchemaRejection('SCHEMA_INVALID')
    visitor(Object.freeze({ uri, format: typeof schema.format === 'string' ? schema.format : undefined }), value)
    if (typeof schema.$ref === 'string') {
      const target = this.reference(new URL(schema.$ref, uri).href)
      this.visit(target.schema, target.uri, value, visitor)
    }
    for (const keyword of ['allOf', 'oneOf', 'anyOf'] as const) {
      const branches = schema[keyword] as AnySchema[] | undefined
      if (!branches) continue
      for (const [index, branch] of branches.entries()) {
        const branchUri = `${uri}/${keyword}/${index}`
        if (keyword === 'allOf' || this.matches(branchUri, value)) this.visit(branch, branchUri, value, visitor)
      }
    }
    if (schema.if !== undefined) {
      const keyword = this.matches(`${uri}/if`, value) ? 'then' : 'else'
      const branch = schema[keyword] as AnySchema | undefined
      if (branch !== undefined) this.visit(branch, `${uri}/${keyword}`, value, visitor)
    }
    if (Array.isArray(value) && schema.items !== undefined) {
      for (const child of value) this.visit(schema.items as AnySchema, `${uri}/items`, child, visitor)
    }
    if (value !== null && !Array.isArray(value) && typeof value === 'object' && schema.properties) {
      for (const [field, childSchema] of Object.entries(schema.properties as Record<string, AnySchema>)) {
        if (Object.hasOwn(value, field)) this.visit(childSchema, `${uri}/properties/${escapeFragment(field)}`, value[field]!, visitor)
      }
    }
  }

  bind(name: string, value: unknown, definition?: string, representation?: RepresentationVisitor): ValidatedPayload {
    const saved: unknown = structuredClone(value)
    this.validate(name, saved, definition)
    if (representation) {
      const target = this.reference(this.schemaRef(name, definition))
      // Ajv 选择有效分支并解析发布集合引用；表示规则按原始定义身份执行。
      this.visit(target.schema, target.uri.includes('#') ? target.uri : `${target.uri}#`, saved, representation)
    }
    const digest = this.digests.get(name)
    if (!digest) throw new SchemaRejection('SCHEMA_UNSUPPORTED')
    return Object.freeze({ schemaRef: this.schemaRef(name, definition), schemaSha256: digest, canonicalJson: canonicalize(saved) })
  }

  get definitionCount(): number { return this.validators.size - this.documents.size }
}

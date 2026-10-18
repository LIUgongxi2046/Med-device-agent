import Ajv2020 from 'ajv/dist/2020.js'
import type { AnySchema, ValidateFunction } from 'ajv'
import { escapeFragment } from 'ajv/dist/compile/util.js'
import addFormats from 'ajv-formats'
import { canonicalize } from 'json-canonicalize'
import { parse } from 'yaml'
import { SchemaRejection } from './errors.js'

export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue }
export type SchemaName = 'dataset' | 'interfaces' | 'resource-details' | 'artifacts' | 'model-stream' | 'templates' | 'template-operations' | 'tasks' | 'collections' | 'rule-expressions' | 'template-applicability' | 'agent-release' | 'task-plan-view' | 'evaluation-results' | 'simulation-execution' | 'local-simulation-provenance' | 'replay-manifest' | 'runtime-governance' | 'agent-execution-policy' | 'run-token-authority' | 'projects' | 'budgets' | 'runtime-retention' | 'formal-evaluation-input' | 'evaluation-context-compiler'
export interface SchemaEntry { readonly name: string; readonly path: string; readonly sha256: string }
export interface CatalogueManifest { readonly schema_version: string; readonly base_uri: string; readonly schemas: readonly SchemaEntry[] }
export interface ValidatedPayload { readonly schemaRef: string; readonly schemaSha256: string; readonly canonicalJson: string }
export interface RepresentationLocation { readonly uri: string; readonly format: string | undefined }
export type RepresentationVisitor = (location: RepresentationLocation, value: JsonValue) => void

const CONTEXT_EXTENSION_SHA256 = '2dcccbadff66b247c8025c781fb1fafc2a167d28c5d45ac0b808fb66611b5000'
const TASK_RUNTIME_EXTENSION_SHA256 = '044860cfe13730927433a208daf56e2adbb89c1d9e8ef3d61937424d812fcb6c'
const SIMULATION_EXTENSION_SHA256 = 'c3837a5c8de536a6504bbb296cd63389fac07ced1eda93ba721d94fcdad8446a'
const EXTENSION_SHA256 = 'a1b3dce03c553d0e9166d76c30e5af287c3bb06dfe90c9cc696632a1663c891d'

function checkSchemaPath(path: string): void {
  if (typeof path !== 'string' || /[\\:%?#\0]/u.test(path) || path.split('/').some(part => ['', '.', '..'].includes(part))) {
    throw new Error('Schema 路径必须使用固定相对路径')
  }
}

/** Merge only the pinned publication, retaining every original identity and digest. */
export async function mergePublishedCatalogue(baseBytes: Uint8Array, extensionBytes: Uint8Array, simulationBytes?: Uint8Array, taskRuntimeBytes?: Uint8Array, contextBytes?: Uint8Array): Promise<CatalogueManifest> {
  // Copy before the first await so caller mutation cannot change checked bytes.
  const context = contextBytes === undefined ? undefined : new Uint8Array(contextBytes)
  const runtime = taskRuntimeBytes === undefined ? undefined : new Uint8Array(taskRuntimeBytes)
  const simulation = simulationBytes === undefined ? undefined : new Uint8Array(simulationBytes)
  const base = new Uint8Array(baseBytes)
  const extension = new Uint8Array(extensionBytes)
  if (await sha256(extension) !== EXTENSION_SHA256) throw new Error('Schema 扩展清单摘要不一致')
  const decode = new TextDecoder('utf-8', { fatal: true })
  const published = JSON.parse(decode.decode(extension)) as { schema_version: string; base_catalogue_sha256: string; schemas: SchemaEntry[] }
  if (Object.keys(published).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas') throw new Error('Schema 扩展清单字段不受支持')
  if (await sha256(base) !== published.base_catalogue_sha256) throw new Error('Schema 基础清单摘要不一致')
  const original = JSON.parse(decode.decode(base)) as CatalogueManifest
  if (original.schema_version !== '1.0.0' || published.schema_version !== '1.0.0') throw new SchemaRejection('SCHEMA_UNSUPPORTED')
  const names = new Set<string>()
  const uris = new Set<string>()
  const schemas = [...original.schemas, ...published.schemas]
  if (simulation !== undefined) {
    if (await sha256(simulation) !== SIMULATION_EXTENSION_SHA256) throw new Error('Schema 模拟扩展清单摘要不一致')
    const extra = JSON.parse(decode.decode(simulation)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Schema 模拟扩展基础身份不一致')
    schemas.push(...extra.schemas)
  }
  if (runtime !== undefined) {
    if (await sha256(runtime) !== TASK_RUNTIME_EXTENSION_SHA256) throw new Error('Schema 任务运行扩展清单摘要不一致')
    const extra = JSON.parse(decode.decode(runtime)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Schema 任务运行扩展基础身份不一致')
    schemas.push(...extra.schemas)
  }
  if (context !== undefined) {
    if (await sha256(context) !== CONTEXT_EXTENSION_SHA256) throw new Error('Schema context extension digest mismatch')
    const extra = JSON.parse(decode.decode(context)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Schema context extension base mismatch')
    schemas.push(...extra.schemas)
  }
  for (const entry of schemas) {
    if (Object.keys(entry).sort().join(',') !== 'name,path,sha256') throw new Error('Schema 登记字段不受支持')
    checkSchemaPath(entry.path)
    if (names.has(entry.name)) throw new Error('Schema 名称重复')
    const uri = new URL(entry.path, original.base_uri).href
    if (uris.has(uri)) throw new Error('Schema 发布引用重复')
    names.add(entry.name)
    uris.add(uri)
  }
  return { ...original, schemas }
}

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
      checkSchemaPath(entry.path)
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

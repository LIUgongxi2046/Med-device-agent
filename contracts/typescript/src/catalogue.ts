import Ajv2020 from 'ajv/dist/2020.js'
import type { AnySchema, ValidateFunction } from 'ajv'
import { escapeFragment } from 'ajv/dist/compile/util.js'
import addFormats from 'ajv-formats'
import { canonicalize } from 'json-canonicalize'
import { parse } from 'yaml'
import { SchemaRejection } from './errors.js'

export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue }
export type SchemaName = 'task-professional-package-v2' | 'evaluation-stage-input' | 'evaluation-business-output' | 'evaluation-stage-input-v2' | 'document-export-command-v2' | 'strict-field-typed-v2' | 'strict-field-comparison-v2' | 'task-strict-field-input-v2' | 'document-body-v2' | 'task-section-writer-package-v2' | 'task-source-verifier-package-v2' | 'document-review-package-v2' | 'template-application' | 'source-evidence-v2' | 'tool-inputs-v2' | 'tool-success-v2' | 'task-tool-read-success-v2' | 'artifacts-v2' | 'dataset' | 'interfaces' | 'resource-details' | 'artifacts' | 'model-stream' | 'templates' | 'template-operations' | 'tasks' | 'collections' | 'rule-expressions' | 'template-applicability' | 'agent-release' | 'task-plan-view' | 'evaluation-results' | 'simulation-execution' | 'local-simulation-provenance' | 'replay-manifest' | 'runtime-governance' | 'agent-execution-policy' | 'run-token-authority' | 'projects' | 'budgets' | 'runtime-retention' | 'formal-evaluation-input' | 'evaluation-context-compiler' | 'evaluation-planning-context-compiler'
export interface SchemaEntry { readonly name: string; readonly path: string; readonly sha256: string }
export interface CatalogueManifest { readonly schema_version: string; readonly base_uri: string; readonly schemas: readonly SchemaEntry[] }
export interface ValidatedPayload { readonly schemaRef: string; readonly schemaSha256: string; readonly canonicalJson: string }
export interface RepresentationLocation { readonly uri: string; readonly format: string | undefined }
export type RepresentationVisitor = (location: RepresentationLocation, value: JsonValue) => void

const TASK_PACKAGE_V2_EXTENSION_SHA256 = '71e97a233df6b0bc91e8ead87db7ca5cd5a19fd970964cbf00b22810f6f5e89b'
const EVALUATION_STAGE_EXTENSION_SHA256 = '37218c998d05b9f8001b36edfa385dfae41598832cb297ec4e1865a3d6580d8a'
const TEMPLATE_APPLICATION_EXTENSION_SHA256 = 'dca6a68792e6f93fbcb476faacb55df75acf1a51e67eb55e52478a4c27134a1c'
const DOCUMENT_OPERATIONS_V2_EXTENSION_SHA256 = '137165db0c075aeab30799a63acef93c58a106d3e5041c4661787b9f1153f1d3'
const EVALUATION_BUSINESS_V2_EXTENSION_SHA256 = '8dab2002a36b6b1783be70fb2a56c81496e4ea83aee979977c48ef4329440c63'
const STRICT_FIELD_EVIDENCE_V2_EXTENSION_SHA256 = '1833b4c7d4984c51166d1e34249f35fa92c50310c9c3f6565040b2e34d3589d9'
const DOCUMENT_EVIDENCE_V2_EXTENSION_SHA256 = '5f79cae29d60d5e28dd36c2b795c8ae7bb6cdf2ae1665e7342f6bab3bf0d5af1'
const SOURCE_EVIDENCE_V2_EXTENSION_SHA256 = '745d5f62b3ff70d9ca11610e11b263226339e71f6c14f9325e160f08468c13aa'
const REVIEW_PACKAGES_EXTENSION_SHA256 = 'abf2fd93515e4022975f16a6dc2938d17d6d855ee13e5f961f5a7a4dcd335420'
const PLAN_DRAFT_PACKAGE_EXTENSION_SHA256 = '58c11567e757b6da79337a05b44d42ab2d1bacd38ea81960b661922d6be60632'
const TASK_PACKAGE_EXTENSION_SHA256 = 'f1b0d3877e11eb4d87eb69dd809f8c1c2a285bd56a05b2cb24d8ae9ebab370fc'
const TASK_TOOL_READ_EXTENSION_SHA256 = 'd5401ee6c04ded4543b96bb7ec3c1fe57ef39ee3fa2cc9a6ddca191671c93cb1'
const PLANNING_EXTENSION_SHA256 = 'c091484892de819a9aa63ef7d75a3e33dc73a7a0b17af4bb16d8250da267e6c7'
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
export async function mergePublishedCatalogue(baseBytes: Uint8Array, extensionBytes: Uint8Array, simulationBytes?: Uint8Array, taskRuntimeBytes?: Uint8Array, contextBytes?: Uint8Array, planningBytes?: Uint8Array, taskToolReadBytes?: Uint8Array, taskPackageBytes?: Uint8Array, planDraftPackageBytes?: Uint8Array, reviewPackagesBytes?: Uint8Array, sourceEvidenceV2Bytes?: Uint8Array, documentEvidenceV2Bytes?: Uint8Array, templateApplicationBytes?: Uint8Array, strictFieldEvidenceV2Bytes?: Uint8Array, documentOperationsV2Bytes?: Uint8Array, evaluationBusinessV2Bytes?: Uint8Array, taskPackageV2Bytes?: Uint8Array, evaluationStageBytes?: Uint8Array): Promise<CatalogueManifest> {
  const taskPackageV2 = taskPackageV2Bytes === undefined ? undefined : new Uint8Array(taskPackageV2Bytes)
  const evaluationStage = evaluationStageBytes === undefined ? undefined : new Uint8Array(evaluationStageBytes)
  // Copy before the first await so caller mutation cannot change checked bytes.
  const templateApplication = templateApplicationBytes === undefined ? undefined : new Uint8Array(templateApplicationBytes)
  const documentOperationsV2 = documentOperationsV2Bytes === undefined ? undefined : new Uint8Array(documentOperationsV2Bytes)
  const evaluationBusinessV2 = evaluationBusinessV2Bytes === undefined ? undefined : new Uint8Array(evaluationBusinessV2Bytes)
  const strictFieldEvidenceV2 = strictFieldEvidenceV2Bytes === undefined ? undefined : new Uint8Array(strictFieldEvidenceV2Bytes)
  const documentEvidenceV2 = documentEvidenceV2Bytes === undefined ? undefined : new Uint8Array(documentEvidenceV2Bytes)
  const sourceEvidenceV2 = sourceEvidenceV2Bytes === undefined ? undefined : new Uint8Array(sourceEvidenceV2Bytes)
  const reviewPackages = reviewPackagesBytes === undefined ? undefined : new Uint8Array(reviewPackagesBytes)
  const planDraftPackage = planDraftPackageBytes === undefined ? undefined : new Uint8Array(planDraftPackageBytes)
  const taskPackage = taskPackageBytes === undefined ? undefined : new Uint8Array(taskPackageBytes)
  const taskToolRead = taskToolReadBytes === undefined ? undefined : new Uint8Array(taskToolReadBytes)
  const planning = planningBytes === undefined ? undefined : new Uint8Array(planningBytes)
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
  if (planning !== undefined) {
    if (await sha256(planning) !== PLANNING_EXTENSION_SHA256) throw new Error('Schema planning extension digest mismatch')
    const extra = JSON.parse(decode.decode(planning)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Schema planning extension base mismatch')
    schemas.push(...extra.schemas)
  }
  if (taskToolRead !== undefined) {
    if (await sha256(taskToolRead) !== TASK_TOOL_READ_EXTENSION_SHA256) throw new Error('Task tool read catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(taskToolRead)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Task tool read catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (taskPackage !== undefined) {
    if (await sha256(taskPackage) !== TASK_PACKAGE_EXTENSION_SHA256) throw new Error('Task package catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(taskPackage)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Task package catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (planDraftPackage !== undefined) {
    if (await sha256(planDraftPackage) !== PLAN_DRAFT_PACKAGE_EXTENSION_SHA256) throw new Error('Plan draft package catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(planDraftPackage)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Plan draft package catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (reviewPackages !== undefined) {
    if (await sha256(reviewPackages) !== REVIEW_PACKAGES_EXTENSION_SHA256) throw new Error('Review packages catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(reviewPackages)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Review packages catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (sourceEvidenceV2 !== undefined) {
    if (await sha256(sourceEvidenceV2) !== SOURCE_EVIDENCE_V2_EXTENSION_SHA256) throw new Error('Source evidence V2 catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(sourceEvidenceV2)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Source evidence V2 catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (documentOperationsV2 !== undefined) {
    if (await sha256(documentOperationsV2) !== DOCUMENT_OPERATIONS_V2_EXTENSION_SHA256) throw new Error('Document operations V2 catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(documentOperationsV2)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Document operations V2 catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (evaluationBusinessV2 !== undefined) {
    if (await sha256(evaluationBusinessV2) !== EVALUATION_BUSINESS_V2_EXTENSION_SHA256) throw new Error('Source evidence V2 catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(evaluationBusinessV2)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Source evidence V2 catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (strictFieldEvidenceV2 !== undefined) {
    if (await sha256(strictFieldEvidenceV2) !== STRICT_FIELD_EVIDENCE_V2_EXTENSION_SHA256) throw new Error('Source evidence V2 catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(strictFieldEvidenceV2)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Source evidence V2 catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (documentEvidenceV2 !== undefined) {
    if (await sha256(documentEvidenceV2) !== DOCUMENT_EVIDENCE_V2_EXTENSION_SHA256) throw new Error('Source evidence V2 catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(documentEvidenceV2)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Source evidence V2 catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (templateApplication !== undefined) {
    if (await sha256(templateApplication) !== TEMPLATE_APPLICATION_EXTENSION_SHA256) throw new Error('Template application catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(templateApplication)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Template application catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (taskPackageV2 !== undefined) {
    if (await sha256(taskPackageV2) !== TASK_PACKAGE_V2_EXTENSION_SHA256) throw new Error('Task package V2 catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(taskPackageV2)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Task package V2 catalogue base mismatch')
    schemas.push(...extra.schemas)
  }
  if (evaluationStage !== undefined) {
    if (await sha256(evaluationStage) !== EVALUATION_STAGE_EXTENSION_SHA256) throw new Error('Evaluation stage catalogue digest mismatch')
    const extra = JSON.parse(decode.decode(evaluationStage)) as typeof published
    if (Object.keys(extra).sort().join(',') !== 'base_catalogue_sha256,schema_version,schemas'
        || extra.schema_version !== '1.0.0' || extra.base_catalogue_sha256 !== await sha256(base)) throw new Error('Evaluation stage catalogue base mismatch')
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
      // A standalone root schema need not declare reusable definitions.
      if (!Object.hasOwn(document, '$defs')) continue
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

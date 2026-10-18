import { canonicalizeEx } from 'json-canonicalize'
import { fail, revision, schemaIssues, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
import type { Operation } from './operations'

export const modelSchema = 'urn:medical:model-configuration:1.0.0#/$defs/'
const pendingSchema = 'urn:medical:model-configuration-write-view:1.0.0'
const maximum = '18446744073709551615'
export interface ModelLimits {
  context_window: string; max_output_tokens: string; max_concurrency: string; requests_per_minute: string; tokens_per_minute: string
  deadline_seconds: number; temperature_enabled: boolean; reasoning_efforts: ('none' | 'low' | 'high' | 'max')[]; stop_limit: number
}
export interface ModelDataPolicy {
  allowed_classifications: string[]; allowed_regions: string[]; allow_training: boolean; retention_days: string; policy_basis: Basis
}
export interface PublicModelDefinition {
  provider: string; base_url: string; region: string; model_id: string; model_version: string; credential_reference_id: string
  limits: ModelLimits; price_basis: Basis; currency: string; data_policy: ModelDataPolicy
  request_parameter_schema_ref: string; request_parameter_schema_sha256: string; model_protocol_revision: string
}
export interface ModelDefinition extends PublicModelDefinition { auth_ref: string; credential_revision: string }
export interface ModelSave {
  schema_version: '1.0.0'; project_id: string | null; config_id: string | null; expected_revision: string
  based_on_revision: Basis | null; definition: ModelDefinition; reason: string
}
export interface ModelView {
  schema_version: '1.0.0'; tenant_id: string; project_id: string | null; config_id: string; model_config_revision: string
  based_on_revision: Basis | null; definition: PublicModelDefinition; content_sha256: string; status: 'DRAFT'
  current_model_revision: string; revision: string; created_by: string; created_at: string
  dependency_validation: 'REGISTRY_REFERENCE_ONLY'; allowed_actions: ('configuration.read' | 'model.configure')[]
}
export interface ModelReceipt {
  schema_version: '1.0.0'; operation_id: string; tenant_id: string; project_id: string | null; config_id: string
  model_config_revision: string; content_sha256: string; status: 'DRAFT'; revision: string; current_context: Context
}
export interface PendingModelSave {
  actor: string; tenant_id: string; operation: 'CP-08'; key: string; context: Context; config_id: string | null; project_id: string | null
  expected_revision: string; based_on_revision: Basis | null; body_sha256: string; public_definition_sha256: string
}
export function canonicalModel(value: unknown): string { return canonicalizeEx(value, { strictUndefined: true }) }
export function modelContext(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '模型配置需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
/** Summary is only an entry gate. The server also checks actual ownership, purpose and grant conditions. */
export function modelAllowed(session: MedicalSession | null, action: 'configuration.read' | 'model.configure', project?: string | null, config?: string | null): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => item.action === action && (project === undefined || item.project_id === null || item.project_id === project) && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === session.current_context?.tenant_id
    || item.resource_kind === 'PROJECT' && item.project_id !== null && item.resource_id === item.project_id && (project === undefined || project === item.project_id)
    || config !== null && item.resource_kind === 'CONFIGURATION' && (item.resource_id === null || config === undefined || item.resource_id === config)
  ))
}
export function modelPublicDefinition(value: ModelDefinition): PublicModelDefinition {
  const { auth_ref: _reference, credential_revision: _revision, ...publicValue } = value
  return publicValue
}
function normalizedText(value: string, maximum: number): boolean {
  return value.length > 0 && value.trim() === value && Array.from(value).length <= maximum && value.normalize('NFC') === value
    && !/[\u0000-\u001f\u007f-\u009f\ud800-\udfff]/u.test(value)
}
export interface ModelIssue { path: string; message: string }
export function modelInputIssues(input: unknown): ModelIssue[] {
  const schemaErrors = schemaIssues(modelSchema + 'Save', input)
  if (schemaErrors.length) return schemaErrors.map(error => ({ path: error.path, message: '必填字段、格式或范围不符合模型草稿规范' }))
  const value = input as ModelSave; const issues: ModelIssue[] = []
  const issue = (path: string, message: string) => issues.push({ path, message })
  const bounded = (field: string, value: string, positive = false) => {
    if (BigInt(value) > BigInt(maximum) || positive && value === '0') issue(field, '请填写范围内的十进制整数字符串')
  }
  bounded('/expected_revision', value.expected_revision)
  if (value.config_id === null ? value.expected_revision !== '0' || value.based_on_revision !== null
    : value.expected_revision === '0' || value.expected_revision === maximum || value.based_on_revision === null) {
    issue('/expected_revision', '新建使用版本0；修订必须基于当前固定版本及其摘要')
  }
  const definition = value.definition
  for (const [field, limit] of [['provider', 128], ['region', 128], ['model_id', 256], ['model_version', 256], ['model_protocol_revision', 64]] as const) {
    if (!normalizedText(definition[field], limit)) issue('/definition/' + field, '请保留规范原文，检查空白、控制字符及长度')
  }
  if (!normalizedText(value.reason, 2000)) issue('/reason', '请填写规范的保存理由（不超过2000字）')
  try {
    const endpoint = new URL(definition.base_url)
    const rawPath = definition.base_url.replace(/^https:\/\/[^/]+/, '').split(/[?#]/, 1)[0] ?? ''
    if (endpoint.protocol !== 'https:' || !endpoint.hostname || endpoint.username || endpoint.password || endpoint.search || endpoint.hash
        || definition.base_url.includes('?') || definition.base_url.includes('#') || endpoint.port === '0'
        || /(?:^|\/)\.{1,2}(?:\/|$)/.test(rawPath) || definition.base_url.includes('\\')) throw new Error('Invalid endpoint')
  } catch { issue('/definition/base_url', '地址须为规范HTTPS地址，不能包含账户、密码、查询参数或片段') }
  const limits = definition.limits
  for (const field of ['context_window', 'max_output_tokens', 'max_concurrency', 'requests_per_minute', 'tokens_per_minute'] as const) bounded('/definition/limits/' + field, limits[field], true)
  if (BigInt(limits.max_output_tokens) > BigInt(limits.context_window)) issue('/definition/limits/max_output_tokens', '输出上限不能超过上下文窗口')
  if (limits.temperature_enabled && !limits.reasoning_efforts.includes('none')) issue('/definition/limits/reasoning_efforts', '允许temperature时必须支持none推理档位')
  bounded('/definition/data_policy/retention_days', definition.data_policy.retention_days)
  for (const field of ['allowed_classifications', 'allowed_regions'] as const) {
    if (definition.data_policy[field].some(value => !normalizedText(value, field === 'allowed_regions' ? 128 : 24))) issue('/definition/data_policy/' + field, '请填写规范且不重复的政策条目')
  }
  if (!definition.data_policy.allowed_regions.includes(definition.region)) issue('/definition/data_policy/allowed_regions', '允许区域必须包含当前模型区域')
  const refs = [definition.price_basis, definition.data_policy.policy_basis, ...(value.based_on_revision ? [value.based_on_revision] : [])]
  const hashes = new Map<string, string>()
  for (const ref of refs) {
    if (hashes.has(ref.version_id) && hashes.get(ref.version_id) !== ref.content_sha256) issue('/definition/price_basis', '同一固定依据版本不能使用不同摘要')
    hashes.set(ref.version_id, ref.content_sha256)
  }
  return issues
}
export function validateModelSave(value: unknown): ModelSave {
  const issues = modelInputIssues(value)
  if (issues.length) fail('SCHEMA_INVALID', issues[0]!.message)
  return value as ModelSave
}
function storageKey(session: MedicalSession): string { return 'medical.model.configuration.write:' + session.user.user_id + ':' + modelContext(session).tenant_id }
function checkPending(session: MedicalSession, value: PendingModelSave): void {
  validate(pendingSchema, value); validContext(value.context); revision(value.expected_revision)
  if (value.actor !== session.user.user_id || value.tenant_id !== modelContext(session).tenant_id || value.context.tenant_id !== value.tenant_id
      || (value.config_id === null ? value.expected_revision !== '0' || value.based_on_revision !== null : value.expected_revision === '0' || value.based_on_revision === null)) {
    fail('CONTENT_MISMATCH', '原模型请求与当前主体、企业或固定版本不一致')
  }
}
export function readPendingModel(session: MedicalSession): PendingModelSave | null {
  const raw = sessionStorage.getItem(storageKey(session)); if (raw === null) return null
  const value = validate<PendingModelSave>(pendingSchema, JSON.parse(raw)); checkPending(session, value); return value
}
export async function prepareModelSave(session: MedicalSession, body: ModelSave, signal?: AbortSignal): Promise<PendingModelSave> {
  validateModelSave(body); const context = modelContext(session)
  if (!modelAllowed(session, 'model.configure', body.project_id, body.config_id)) fail('ACCESS_DENIED', '当前范围没有模型配置权限')
  const hash = await sourceDigest(body); signal?.throwIfAborted()
  const previous = readPendingModel(session)
  if (previous) {
    if (previous.body_sha256 !== hash || canonicalModel(previous.context) !== canonicalModel(context)) fail('IDEMPOTENCY_CONFLICT', '原模型请求仍待核查，必须保留原参数、上下文及请求标识')
    return previous
  }
  if ([context.context_revision, context.governance_epoch, body.expected_revision].includes(maximum)) fail('REVISION_LIMIT', '模型或治理修订无法继续增加')
  const pending: PendingModelSave = { actor: session.user.user_id, tenant_id: context.tenant_id, operation: 'CP-08', key: crypto.randomUUID(),
    context: { ...context }, config_id: body.config_id, project_id: body.project_id, expected_revision: body.expected_revision,
    based_on_revision: body.based_on_revision === null ? null : { ...body.based_on_revision }, body_sha256: hash,
    public_definition_sha256: await sourceDigest(modelPublicDefinition(body.definition)) }
  signal?.throwIfAborted(); checkPending(session, pending)
  // Persist only identity and digests. No endpoint, reference path/version, free text, or form body enters storage.
  sessionStorage.setItem(storageKey(session), JSON.stringify(pending)); return pending
}
export async function verifyModelRequest(session: MedicalSession, pending: PendingModelSave, body: ModelSave): Promise<void> {
  checkPending(session, pending); validateModelSave(body)
  if (canonicalModel(readPendingModel(session)) !== canonicalModel(pending) || canonicalModel(pending.context) !== canonicalModel(modelContext(session))
      || !modelAllowed(session, 'model.configure', body.project_id, body.config_id) || await sourceDigest(body) !== pending.body_sha256
      || body.config_id !== pending.config_id || body.project_id !== pending.project_id || body.expected_revision !== pending.expected_revision
      || canonicalModel(body.based_on_revision) !== canonicalModel(pending.based_on_revision)) fail('CONTENT_MISMATCH', '原模型请求的身份、范围、参数或上下文已经变化')
}
export function verifyModelView(session: MedicalSession, id: string, value: ModelView, resourceRevision: string | null): void {
  validate(modelSchema + 'View', value); revision(value.revision)
  for (const field of ['context_window', 'max_output_tokens', 'max_concurrency', 'requests_per_minute', 'tokens_per_minute'] as const) revision(value.definition.limits[field])
  revision(value.definition.data_policy.retention_days)
  if (value.tenant_id !== modelContext(session).tenant_id || value.model_config_revision !== id || value.revision !== resourceRevision
      || !value.allowed_actions.includes('configuration.read')) fail('CONTENT_MISMATCH', '模型固定版本、企业或读取许可不一致')
  // content_sha256 covers the internal fixed definition, including fields intentionally absent from this public view.
}
export function verifyModelReceipt(session: MedicalSession, pending: PendingModelSave, value: ModelReceipt, resourceRevision: string | null): void {
  checkPending(session, pending); validate(modelSchema + 'Receipt', value); validContext(value.current_context); revision(value.revision)
  if (value.tenant_id !== pending.tenant_id || value.project_id !== pending.project_id || pending.config_id !== null && value.config_id !== pending.config_id
      || value.revision !== resourceRevision || value.revision !== (BigInt(pending.expected_revision) + 1n).toString()
      || value.current_context.tenant_id !== pending.tenant_id || value.current_context.auth_epoch !== pending.context.auth_epoch
      || value.current_context.context_revision !== (BigInt(pending.context.context_revision) + 1n).toString()
      || value.current_context.governance_epoch !== (BigInt(pending.context.governance_epoch) + 1n).toString()) fail('CONTENT_MISMATCH', '模型草稿回执与原请求或治理修订不一致')
}
export async function confirmModelResult(session: MedicalSession, pending: PendingModelSave, operation: Operation, view: ModelView, receipt: ModelReceipt | null, signal?: AbortSignal): Promise<void> {
  checkPending(session, pending); verifyModelView(session, view.model_config_revision, view, view.revision)
  validate('urn:medical:shared#/$defs/Operation', operation)
  if (canonicalModel(readPendingModel(session)) !== canonicalModel(pending) || operation.status !== 'COMPLETED'
      || operation.status_url !== '/api/operations/' + operation.operation_id || operation.resource_id !== view.model_config_revision
      || view.project_id !== pending.project_id || pending.config_id !== null && view.config_id !== pending.config_id
      || view.created_by !== pending.actor || BigInt(view.revision) < BigInt(pending.expected_revision) + 1n
      || canonicalModel(view.based_on_revision) !== canonicalModel(pending.based_on_revision)
      || await sourceDigest(view.definition) !== pending.public_definition_sha256
      || receipt !== null && (receipt.operation_id !== operation.operation_id || receipt.config_id !== view.config_id
        || receipt.model_config_revision !== view.model_config_revision || receipt.content_sha256 !== view.content_sha256)) {
    fail('CONTENT_MISMATCH', '原操作与固定草稿尚未取得一致结果，请继续核查原请求')
  }
  signal?.throwIfAborted()
  sessionStorage.removeItem(storageKey(session))
}

export interface ModelListing { schema_version: '1.0.0'; tenant_id: string; current_context: Context; items: ModelView[]; next_after: string | null }
export interface ModelListQuery { limit: number; q?: string; after?: string }
export function verifyModelListing(session: MedicalSession, query: ModelListQuery, value: ModelListing, resourceRevision: string | null): void {
  validate('urn:medical:configuration-discovery:1.0.0#/$defs/Query', query)
  validate('urn:medical:configuration-discovery:1.0.0#/$defs/ModelList', value)
  const context = modelContext(session); validContext(value.current_context)
  if (value.tenant_id !== context.tenant_id || canonicalModel(value.current_context) !== canonicalModel(context) || resourceRevision !== context.context_revision
      || new Set(value.items.map(item => item.config_id)).size !== value.items.length || value.items.length > query.limit) fail('CONTENT_MISMATCH', '配置目录与当前企业、上下文或分页范围不一致')
  let previous: string | null = null
  for (const item of value.items) {
    verifyModelView(session, item.model_config_revision, item, item.revision)
    if (item.current_model_revision !== item.model_config_revision || !modelAllowed(session, 'configuration.read', item.project_id, item.config_id)
        || previous !== null && previous >= item.config_id) fail('CONTENT_MISMATCH', '配置目录未返回获授权且顺序一致的当前固定版本')
    previous = item.config_id
  }
}

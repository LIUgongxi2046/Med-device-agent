import { canonicalizeEx } from 'json-canonicalize'
import { fail, revision, schemaIssues, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
import type { Operation } from './operations'
import simulationContract from '../../../../business/medical-persistence/src/main/resources/simulation-model-configuration.schema.json'

export const simulationSchema = 'urn:medical:simulation-model-configuration:1.0.0#/$defs/'
const pendingSchema = 'urn:medical:simulation-model-configuration-write-view:1.0.0'
const maximum = '18446744073709551615'
export interface SimulationLimits {
  context_window: string; max_output_tokens: string; max_concurrency: string; requests_per_minute: string; tokens_per_minute: string
  deadline_seconds: number; temperature_enabled: boolean; reasoning_efforts: ('none' | 'low' | 'high' | 'max')[]; stop_limit: number
}
/** Fixed protocol identity is loaded from the server-owned closed schema, never editable form input. */
const constants = simulationContract.$defs.Definition.properties
export const simulationIdentity = Object.freeze({
  execution_mode: constants.execution_mode.const as 'SIMULATION', simulator_id: constants.simulator_id.const,
  simulator_version: constants.simulator_version.const, provenance_schema_ref: constants.provenance_schema_ref.const,
  provenance_schema_sha256: constants.provenance_schema_sha256.const, production_eligible: constants.production_eligible.const as false,
  provider_charge_amount: constants.provider_charge_amount.const as '0.00000000', charge_basis: constants.charge_basis.const as 'LOCAL_SIMULATION_NO_PROVIDER_REQUEST'
})
export interface SimulationDefinition {
  execution_mode: 'SIMULATION'; simulator_id: string; simulator_version: string; model_id: string; model_version: string
  case_binding: { case_id: string; case_sha256: string; validation: 'DECLARED_ONLY' }; limits: SimulationLimits
  provenance_schema_ref: string; provenance_schema_sha256: string; production_eligible: false
  provider_charge_amount: '0.00000000'; charge_basis: 'LOCAL_SIMULATION_NO_PROVIDER_REQUEST'
}
export interface SimulationSave {
  schema_version: '1.0.0'; project_id: string | null; config_id: string | null; expected_revision: string
  based_on_revision: Basis | null; definition: SimulationDefinition; reason: string
}
export interface SimulationView {
  schema_version: '1.0.0'; tenant_id: string; project_id: string | null; config_id: string; model_config_revision: string
  based_on_revision: Basis | null; definition: SimulationDefinition; content_sha256: string; status: 'DRAFT'
  current_model_revision: string; revision: string; created_by: string; created_at: string
  dependency_validation: 'DECLARED_SIMULATION_BINDING_ONLY'; allowed_actions: ('configuration.read' | 'model.configure')[]
}
export interface SimulationReceipt {
  schema_version: '1.0.0'; operation_id: string; tenant_id: string; project_id: string | null; config_id: string
  model_config_revision: string; content_sha256: string; status: 'DRAFT'; revision: string; current_context: Context; execution_mode: 'SIMULATION'; production_eligible: false
}
export interface PendingSimulationSave {
  actor: string; tenant_id: string; operation: 'CP-08'; key: string; context: Context; config_id: string | null; project_id: string | null
  expected_revision: string; based_on_revision: Basis | null; body_sha256: string; definition_sha256: string
}
export function canonicalSimulation(value: unknown): string { return canonicalizeEx(value, { strictUndefined: true }) }
export function simulationContext(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '模拟模型配置需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
/** Summary is only an entry gate. The server also checks actual ownership, purpose and grant conditions. */
export function simulationAllowed(session: MedicalSession | null, action: 'configuration.read' | 'model.configure', project?: string | null, config?: string | null): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => item.action === action && (project === undefined || item.project_id === null || item.project_id === project) && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === session.current_context?.tenant_id
    || item.resource_kind === 'PROJECT' && item.project_id !== null && item.resource_id === item.project_id && (project === undefined || project === item.project_id)
    || config !== null && item.resource_kind === 'CONFIGURATION' && (item.resource_id === null || config === undefined || item.resource_id === config)
  ))
}
function normalizedText(value: string, maximum: number): boolean {
  return value.length > 0 && value.trim() === value && Array.from(value).length <= maximum && value.normalize('NFC') === value
    && !/[\u0000-\u001f\u007f-\u009f\ud800-\udfff]/u.test(value)
}
export interface SimulationIssue { path: string; message: string }
export function simulationInputIssues(input: unknown): SimulationIssue[] {
  const schemaErrors = schemaIssues(simulationSchema + 'Save', input)
  if (schemaErrors.length) return schemaErrors.map(error => ({ path: error.path, message: '必填字段、格式或范围不符合模拟模型草稿规范' }))
  const value = input as SimulationSave; const issues: SimulationIssue[] = []
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
  for (const field of ['model_id', 'model_version'] as const) {
    if (!normalizedText(definition[field], 256)) issue('/definition/' + field, '模拟模型标识须使用规范的SIMULATION:前缀内容')
  }
  if (!normalizedText(value.reason, 2000)) issue('/reason', '请填写规范的保存理由（不超过2000字）')
  const limits = definition.limits
  for (const field of ['context_window', 'max_output_tokens', 'max_concurrency', 'requests_per_minute', 'tokens_per_minute'] as const) bounded('/definition/limits/' + field, limits[field], true)
  if (BigInt(limits.max_output_tokens) > BigInt(limits.context_window)) issue('/definition/limits/max_output_tokens', '输出上限不能超过上下文窗口')
  if (limits.temperature_enabled && !limits.reasoning_efforts.includes('none')) issue('/definition/limits/reasoning_efforts', '允许temperature时必须支持none推理档位')
  return issues
}
export function validateSimulationSave(value: unknown): SimulationSave {
  const issues = simulationInputIssues(value)
  if (issues.length) fail('SCHEMA_INVALID', issues[0]!.message)
  return value as SimulationSave
}
function storageKey(session: MedicalSession): string { return 'medical.simulation.configuration.write:' + session.user.user_id + ':' + simulationContext(session).tenant_id }
function checkPending(session: MedicalSession, value: PendingSimulationSave): void {
  validate(pendingSchema, value); validContext(value.context); revision(value.expected_revision)
  if (value.actor !== session.user.user_id || value.tenant_id !== simulationContext(session).tenant_id || value.context.tenant_id !== value.tenant_id
      || (value.config_id === null ? value.expected_revision !== '0' || value.based_on_revision !== null : value.expected_revision === '0' || value.based_on_revision === null)) {
    fail('CONTENT_MISMATCH', '原模拟模型请求与当前主体、企业或固定版本不一致')
  }
}
export function readPendingSimulation(session: MedicalSession): PendingSimulationSave | null {
  const raw = sessionStorage.getItem(storageKey(session)); if (raw === null) return null
  const value = validate<PendingSimulationSave>(pendingSchema, JSON.parse(raw)); checkPending(session, value); return value
}
export async function prepareSimulationSave(session: MedicalSession, body: SimulationSave, signal?: AbortSignal): Promise<PendingSimulationSave> {
  validateSimulationSave(body); const context = simulationContext(session)
  if (!simulationAllowed(session, 'model.configure', body.project_id, body.config_id)) fail('ACCESS_DENIED', '当前范围没有模拟模型配置权限')
  const hash = await sourceDigest(body); signal?.throwIfAborted()
  const previous = readPendingSimulation(session)
  if (previous) {
    if (previous.body_sha256 !== hash || canonicalSimulation(previous.context) !== canonicalSimulation(context)) fail('IDEMPOTENCY_CONFLICT', '原模拟模型请求仍待核查，必须保留原参数、上下文及请求标识')
    return previous
  }
  if ([context.context_revision, context.governance_epoch, body.expected_revision].includes(maximum)) fail('REVISION_LIMIT', '模拟模型或治理修订无法继续增加')
  const pending: PendingSimulationSave = { actor: session.user.user_id, tenant_id: context.tenant_id, operation: 'CP-08', key: crypto.randomUUID(),
    context: { ...context }, config_id: body.config_id, project_id: body.project_id, expected_revision: body.expected_revision,
    based_on_revision: body.based_on_revision === null ? null : { ...body.based_on_revision }, body_sha256: hash,
    definition_sha256: await sourceDigest(body.definition) }
  signal?.throwIfAborted(); checkPending(session, pending)
  // Persist only identity and digests. No case binding, free text, limits, or form body enters storage.
  sessionStorage.setItem(storageKey(session), JSON.stringify(pending)); return pending
}
export async function verifySimulationRequest(session: MedicalSession, pending: PendingSimulationSave, body: SimulationSave): Promise<void> {
  checkPending(session, pending); validateSimulationSave(body)
  if (canonicalSimulation(readPendingSimulation(session)) !== canonicalSimulation(pending) || canonicalSimulation(pending.context) !== canonicalSimulation(simulationContext(session))
      || !simulationAllowed(session, 'model.configure', body.project_id, body.config_id) || await sourceDigest(body) !== pending.body_sha256
      || body.config_id !== pending.config_id || body.project_id !== pending.project_id || body.expected_revision !== pending.expected_revision
      || canonicalSimulation(body.based_on_revision) !== canonicalSimulation(pending.based_on_revision)) fail('CONTENT_MISMATCH', '原模拟模型请求的身份、范围、参数或上下文已经变化')
}
export function verifySimulationView(session: MedicalSession, id: string, value: SimulationView, resourceRevision: string | null): void {
  validate(simulationSchema + 'View', value); revision(value.revision)
  const limits = value.definition.limits
  for (const field of ['context_window', 'max_output_tokens', 'max_concurrency', 'requests_per_minute', 'tokens_per_minute'] as const) revision(limits[field])
  if (BigInt(limits.max_output_tokens) > BigInt(limits.context_window) || limits.temperature_enabled && !limits.reasoning_efforts.includes('none')) fail('CONTENT_MISMATCH', '模拟模型能力与限额声明不一致')
  if (value.tenant_id !== simulationContext(session).tenant_id || value.model_config_revision !== id || value.revision !== resourceRevision
      || !value.allowed_actions.includes('configuration.read')) fail('CONTENT_MISMATCH', '模拟模型固定版本、企业或读取许可不一致')
  // content_sha256 covers the complete fixed record, including server-side authority metadata absent from this view.
}
export function verifySimulationReceipt(session: MedicalSession, pending: PendingSimulationSave, value: SimulationReceipt, resourceRevision: string | null): void {
  checkPending(session, pending); validate(simulationSchema + 'Receipt', value); validContext(value.current_context); revision(value.revision)
  if (value.tenant_id !== pending.tenant_id || value.project_id !== pending.project_id || pending.config_id !== null && value.config_id !== pending.config_id
      || value.revision !== resourceRevision || value.revision !== (BigInt(pending.expected_revision) + 1n).toString()
      || value.current_context.tenant_id !== pending.tenant_id || value.current_context.auth_epoch !== pending.context.auth_epoch
      || value.current_context.context_revision !== (BigInt(pending.context.context_revision) + 1n).toString()
      || value.current_context.governance_epoch !== (BigInt(pending.context.governance_epoch) + 1n).toString()) fail('CONTENT_MISMATCH', '模拟模型草稿回执与原请求或治理修订不一致')
}
export async function confirmSimulationResult(session: MedicalSession, pending: PendingSimulationSave, operation: Operation, view: SimulationView, receipt: SimulationReceipt | null, signal?: AbortSignal): Promise<void> {
  checkPending(session, pending); verifySimulationView(session, view.model_config_revision, view, view.revision)
  validate('urn:medical:shared#/$defs/Operation', operation)
  if (receipt !== null) verifySimulationReceipt(session, pending, receipt, receipt.revision)
  if (canonicalSimulation(readPendingSimulation(session)) !== canonicalSimulation(pending) || operation.status !== 'COMPLETED'
      || operation.status_url !== '/api/operations/' + operation.operation_id || operation.resource_id !== view.model_config_revision
      || view.project_id !== pending.project_id || pending.config_id !== null && view.config_id !== pending.config_id
      || view.created_by !== pending.actor || BigInt(view.revision) < BigInt(pending.expected_revision) + 1n
      || canonicalSimulation(view.based_on_revision) !== canonicalSimulation(pending.based_on_revision)
      || await sourceDigest(view.definition) !== pending.definition_sha256
      || receipt !== null && (receipt.operation_id !== operation.operation_id || receipt.config_id !== view.config_id
        || receipt.model_config_revision !== view.model_config_revision || receipt.content_sha256 !== view.content_sha256)) {
    fail('CONTENT_MISMATCH', '原操作与固定草稿尚未取得一致结果，请继续核查原请求')
  }
  signal?.throwIfAborted()
  sessionStorage.removeItem(storageKey(session))
}

export interface SimulationListing { schema_version: '1.0.0'; tenant_id: string; current_context: Context; items: SimulationView[]; next_after: string | null }
export interface SimulationListQuery { limit: number; q?: string; after?: string }
export function validateSimulationQuery(query: SimulationListQuery): void {
  validate('urn:medical:simulation-configuration-discovery:1.0.0#/$defs/Query', query)
  if (query.q !== undefined && !normalizedText(query.q, 256)) fail('SCHEMA_INVALID', '查询文本必须规范且不能含首尾空白或控制字符')
}
export function verifySimulationListing(session: MedicalSession, query: SimulationListQuery, value: SimulationListing, resourceRevision: string | null): void {
  validateSimulationQuery(query)
  validate('urn:medical:simulation-configuration-discovery:1.0.0#/$defs/SimulationModelList', value)
  const context = simulationContext(session); validContext(value.current_context)
  if (value.tenant_id !== context.tenant_id || canonicalSimulation(value.current_context) !== canonicalSimulation(context) || resourceRevision !== context.context_revision
      || new Set(value.items.map(item => item.config_id)).size !== value.items.length || value.items.length > query.limit) fail('CONTENT_MISMATCH', '配置目录与当前企业、上下文或分页范围不一致')
  let previous: string | null = null
  for (const item of value.items) {
    verifySimulationView(session, item.model_config_revision, item, item.revision)
    if (item.current_model_revision !== item.model_config_revision || !simulationAllowed(session, 'configuration.read', item.project_id, item.config_id)
        || previous !== null && previous >= item.config_id) fail('CONTENT_MISMATCH', '配置目录未返回获授权且顺序一致的当前固定版本')
    previous = item.config_id
  }
}

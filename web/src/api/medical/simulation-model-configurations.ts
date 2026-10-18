import request from '@/utils/request'
import { envelope, fail, validate } from './protocol'
import type { MedicalSession } from './protocol'
import { simulationAllowed, simulationContext, simulationSchema, verifySimulationReceipt, verifySimulationRequest, verifySimulationView, verifySimulationListing, validateSimulationQuery } from './simulation-model-configuration-domain'
import type { SimulationReceipt, SimulationSave, SimulationView, PendingSimulationSave, SimulationListing, SimulationListQuery } from './simulation-model-configuration-domain'

export async function readSimulationConfiguration(session: MedicalSession, id: string, signal: AbortSignal): Promise<SimulationView> {
  signal.throwIfAborted(); validate(simulationSchema + 'Id', id)
  if (!simulationAllowed(session, 'configuration.read')) fail('ACCESS_DENIED', '当前身份没有模拟模型配置读取权限')
  const response = envelope(await request.get('/api/simulation-model-config-revisions/' + id, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': simulationContext(session).context_revision } }))
  const value = validate<SimulationView>(simulationSchema + 'View', response.data)
  verifySimulationView(session, id, value, response.resource_revision)
  if (!simulationAllowed(session, 'configuration.read', value.project_id, value.config_id)) fail('ACCESS_DENIED', '当前身份没有此模拟模型配置的读取权限')
  return value
}
export async function saveSimulationConfiguration(session: MedicalSession, pending: PendingSimulationSave, body: SimulationSave, signal?: AbortSignal): Promise<SimulationReceipt> {
  signal?.throwIfAborted()
  await verifySimulationRequest(session, pending, body)
  // Do not dispatch a write after the page/session was invalidated during digest verification.
  signal?.throwIfAborted()
  const raw = await request.post('/api/simulation-model-config-revisions', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision, 'Idempotency-Key': pending.key,
    [session.csrf.header_name]: session.csrf.token
  } })
  const response = envelope(raw); const value = validate<SimulationReceipt>(simulationSchema + 'Receipt', response.data)
  if (raw.status !== 201) fail('CONTENT_MISMATCH', '模拟模型草稿未返回预期保存状态')
  verifySimulationReceipt(session, pending, value, response.resource_revision); return value
}

export type { SimulationListing, SimulationListQuery } from './simulation-model-configuration-domain'
export async function listSimulationConfigurations(session: MedicalSession, query: SimulationListQuery, signal: AbortSignal): Promise<SimulationListing> {
  signal.throwIfAborted()
  if (!simulationAllowed(session, 'configuration.read')) fail('ACCESS_DENIED', '当前身份没有配置目录读取权限')
  validateSimulationQuery(query)
  const context = simulationContext(session)
  const response = envelope(await request.get('/api/simulation-model-config-revisions', { medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const value = validate<SimulationListing>('urn:medical:simulation-configuration-discovery:1.0.0#/$defs/SimulationModelList', response.data)
  verifySimulationListing(session, query, value, response.resource_revision)
  return value
}

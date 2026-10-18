import request from '@/utils/request'
import { envelope, fail, validate } from './protocol'
import type { MedicalSession } from './protocol'
import { modelAllowed, modelContext, modelSchema, verifyModelReceipt, verifyModelRequest, verifyModelView, verifyModelListing } from './model-configuration-domain'
import type { ModelReceipt, ModelSave, ModelView, PendingModelSave, ModelListing, ModelListQuery } from './model-configuration-domain'

export async function readModelConfiguration(session: MedicalSession, id: string, signal: AbortSignal): Promise<ModelView> {
  validate(modelSchema + 'Id', id)
  if (!modelAllowed(session, 'configuration.read')) fail('ACCESS_DENIED', '当前身份没有模型配置读取权限')
  const response = envelope(await request.get('/api/model-config-revisions/' + id, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': modelContext(session).context_revision } }))
  const value = validate<ModelView>(modelSchema + 'View', response.data)
  verifyModelView(session, id, value, response.resource_revision)
  if (!modelAllowed(session, 'configuration.read', value.project_id, value.config_id)) fail('ACCESS_DENIED', '当前身份没有此模型配置的读取权限')
  return value
}
export async function saveModelConfiguration(session: MedicalSession, pending: PendingModelSave, body: ModelSave, signal?: AbortSignal): Promise<ModelReceipt> {
  signal?.throwIfAborted()
  await verifyModelRequest(session, pending, body)
  // Do not dispatch a write after the page/session was invalidated during digest verification.
  signal?.throwIfAborted()
  const raw = await request.post('/api/model-config-revisions', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision, 'Idempotency-Key': pending.key,
    [session.csrf.header_name]: session.csrf.token
  } })
  const response = envelope(raw); const value = validate<ModelReceipt>(modelSchema + 'Receipt', response.data)
  if (raw.status !== 201) fail('CONTENT_MISMATCH', '模型草稿未返回预期保存状态')
  verifyModelReceipt(session, pending, value, response.resource_revision); return value
}

export type { ModelListing, ModelListQuery } from './model-configuration-domain'
export async function listModelConfigurations(session: MedicalSession, query: ModelListQuery, signal: AbortSignal): Promise<ModelListing> {
  if (!modelAllowed(session, 'configuration.read')) fail('ACCESS_DENIED', '当前身份没有配置目录读取权限')
  validate('urn:medical:configuration-discovery:1.0.0#/$defs/Query', query)
  const context = modelContext(session)
  const response = envelope(await request.get('/api/model-config-revisions', { medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const value = validate<ModelListing>('urn:medical:configuration-discovery:1.0.0#/$defs/ModelList', response.data)
  verifyModelListing(session, query, value, response.resource_revision)
  return value
}

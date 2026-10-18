import request from '@/utils/request'
import { envelope, fail } from './protocol'
import { lookupOperation, readOperation } from './operations'
import type { OperationLookup } from './operations'
import { capabilityRecordKind, capabilityOperation, capabilityGuard, capabilityValidate, capabilityCanonical, detached,
  prepareCapability, markCapabilityDispatched, rememberCapabilityReceipt, verifyPendingCapability,
  verifyCapabilityReceipt, verifyCapabilityRecord, verifyCapabilityAvailability, settleCapability } from './formal-capability-domain'
import type { FormalCapability, FormalCapabilityRecord, CapabilityAvailability, CapabilityPage, CapabilityReceipt,
  PendingCapability, CapabilitySessionReader } from './formal-capability-domain'

function headers(context: { context_revision: string }) { return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }
export async function readCapabilityAvailability(reader: CapabilitySessionReader, signal: AbortSignal): Promise<CapabilityAvailability> {
  const guard = capabilityGuard(reader, signal)
  const raw = await request.get('/api/agent-authoring-feature-availability', { medical: 'envelope', signal, headers: headers(guard.context) })
  guard.assert(); const response = envelope(raw)
  if (raw.status !== 200) fail('CONTENT_MISMATCH', '可用性读取没有返回成功状态')
  return verifyCapabilityAvailability(response.data, guard.context, response.resource_revision)
}
export async function queryCapabilities(reader: CapabilitySessionReader, cursor: string | null, signal: AbortSignal): Promise<CapabilityPage> {
  const guard = capabilityGuard(reader, signal), body = { schema_version: '1.0.0', kind: capabilityRecordKind, cursor }
  capabilityValidate('CapabilityQuery', body)
  const raw = await request.post('/api/agent-authoring/records/query', body, { medical: 'envelope', signal,
    headers: { ...headers(guard.context), [guard.session.csrf.header_name]: guard.session.csrf.token } })
  guard.assert(); const response = envelope(raw), page = capabilityValidate<CapabilityPage>('List', response.data)
  if (raw.status !== 200 || response.resource_revision !== '1' || new Set(page.items.map(item => item.record_id)).size !== page.items.length
      || cursor !== null && page.next_cursor === cursor) fail('CONTENT_MISMATCH', '能力目录的分页或版本信息不一致')
  const items: FormalCapabilityRecord[] = []
  for (const item of page.items) { items.push(await verifyCapabilityRecord(guard.session, item, response.resource_revision)); guard.assert() }
  return { schema_version: '1.0.0', items, next_cursor: page.next_cursor }
}
export async function readCapability(reader: CapabilitySessionReader, recordId: string, signal: AbortSignal): Promise<FormalCapabilityRecord> {
  capabilityValidate('Id', recordId); const guard = capabilityGuard(reader, signal)
  const raw = await request.get('/api/agent-authoring/records/' + recordId, { medical: 'envelope', signal, headers: headers(guard.context) })
  guard.assert(); const response = envelope(raw)
  if (raw.status !== 200) fail('CONTENT_MISMATCH', '能力详情读取没有返回成功状态')
  const value = await verifyCapabilityRecord(guard.session, response.data, response.resource_revision, recordId); guard.assert(); return value
}
async function typedReceipt(reader: CapabilitySessionReader, pending: PendingCapability, recordId: string, operationId: string, signal: AbortSignal): Promise<CapabilityReceipt> {
  capabilityValidate('Id', recordId); capabilityValidate('Id', operationId); const guard = capabilityGuard(reader, signal)
  const raw = await request.get('/api/agent-authoring/records/' + recordId + '/operation-receipts/' + operationId,
    { medical: 'envelope', signal, headers: headers(guard.context) })
  guard.assert(); const response = envelope(raw), receipt = verifyCapabilityReceipt(pending, response.data, response.resource_revision)
  if (raw.status !== 200 || receipt.operation_id !== operationId || receipt.resource_id !== recordId) fail('CONTENT_MISMATCH', '类型回执不是原操作记录')
  return receipt
}
export async function lookupCapability(reader: CapabilitySessionReader, pending: PendingCapability, signal: AbortSignal): Promise<OperationLookup> {
  const guard = capabilityGuard(reader, signal); await verifyPendingCapability(guard.session, pending); guard.assert()
  const result = await lookupOperation(guard.session, capabilityOperation, pending.key, signal); guard.assert()
  if (pending.receipt && result.operation && (result.operation.operation_id !== pending.receipt.operation_id || result.operation.resource_id !== pending.receipt.resource_id)) fail('CONTENT_MISMATCH', '原键查询与已收到的类型回执不一致')
  return result
}
/** Recovery is read-only. IDs are resolved from the original key, never typed or inferred from a list page. */
export async function recoverCapability(reader: CapabilitySessionReader, pending: PendingCapability, signal: AbortSignal): Promise<{ lookup: OperationLookup; record: FormalCapabilityRecord | null }> {
  const guard = capabilityGuard(reader, signal), lookup = await lookupCapability(reader, pending, signal); guard.assert()
  const found = lookup.operation
  if (lookup.lookup_status !== 'FOUND' || found?.status !== 'COMPLETED') return { lookup, record: null }
  if (!found.resource_id) fail('CONTENT_MISMATCH', '已完成操作没有原记录身份')
  const operation = await readOperation(guard.session, found.operation_id, signal); guard.assert()
  if (operation.operation_id !== found.operation_id || operation.resource_id !== found.resource_id || operation.status !== 'COMPLETED') fail('ACTION_STALE', '原操作状态已变化，请再次查询')
  const receipt = await typedReceipt(reader, pending, found.resource_id, found.operation_id, signal); guard.assert()
  if (pending.receipt && capabilityCanonical(receipt) !== capabilityCanonical(pending.receipt)) fail('CONTENT_MISMATCH', '类型回读与原保存回执不一致')
  const record = await readCapability(reader, found.resource_id, signal); guard.assert()
  await settleCapability(reader, pending, receipt, record, signal); guard.assert()
  return { lookup, record }
}
/** The only write entry creates a new request. An existing pending record always blocks a second write. */
export async function createCapability(reader: CapabilitySessionReader, body: FormalCapability, signal: AbortSignal,
  onPending: (value: PendingCapability) => void): Promise<{ pending: PendingCapability; receipt: CapabilityReceipt }> {
  const guard = capabilityGuard(reader, signal)
  const prepared = await prepareCapability(reader, body, signal); guard.assert(); onPending(detached(prepared))
  const pending = await markCapabilityDispatched(reader, prepared, signal); guard.assert(); onPending(detached(pending))
  const raw = await request.post('/api/agent-authoring/capabilities', detached(pending.body), { medical: 'envelope', signal,
    headers: { ...headers(pending.context), 'Idempotency-Key': pending.key, [guard.session.csrf.header_name]: guard.session.csrf.token } })
  guard.assert(); const response = envelope(raw), receipt = verifyCapabilityReceipt(pending, response.data, response.resource_revision)
  if (raw.status !== 201) fail('CONTENT_MISMATCH', '新能力保存没有返回创建成功状态')
  const saved = await rememberCapabilityReceipt(reader, pending, receipt, signal); guard.assert(); onPending(detached(saved))
  return { pending: saved, receipt }
}

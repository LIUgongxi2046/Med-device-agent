import request from '@/utils/request'
import { envelope, fail, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'

export interface Operation {
  operation_id: string; resource_id: string | null; status: 'PROCESSING' | 'COMPLETED' | 'FAILED'; status_url: string
}
export interface OperationLookup { lookup_status: 'FOUND' | 'NOT_FOUND' | 'EXPIRED'; operation: Operation | null }

function current(session: MedicalSession): string {
  if (session.current_context === null) fail('ACCESS_DENIED', '原请求查询需要当前企业身份')
  validContext(session.current_context)
  return session.current_context.context_revision
}

function checkOperation(operation: Operation): Operation {
  validate('urn:medical:shared#/$defs/Operation', operation)
  if (operation.status_url !== '/api/operations/' + operation.operation_id) fail('CONTENT_MISMATCH', '原操作状态地址与操作身份不一致')
  return operation
}

export async function lookupOperation(session: MedicalSession, operation: string, key: string, signal: AbortSignal): Promise<OperationLookup> {
  const context = current(session)
  const body = { operation_id: operation, idempotency_key: key }
  validate('urn:medical:shared#/$defs/OperationLookup', body)
  if (!/^[\x21-\x7e]{1,64}$/.test(operation)) fail('SCHEMA_INVALID', '原操作定义编号无效')
  const response = envelope(await request.post('/api/operation-lookups', body, { medical: 'envelope', signal, headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': context, [session.csrf.header_name]: session.csrf.token
  } }))
  const result = validate<OperationLookup>('urn:medical:shared#/$defs/OperationLookupResult', response.data)
  if (response.resource_revision !== null || (result.lookup_status === 'FOUND') !== (result.operation !== null)) {
    fail('CONTENT_MISMATCH', '原请求查询结果与状态不一致')
  }
  if (result.operation !== null) checkOperation(result.operation)
  return result
}

export async function readOperation(session: MedicalSession, operationId: string, signal: AbortSignal): Promise<Operation> {
  const context = current(session)
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(operationId)) fail('SCHEMA_INVALID', '原操作身份无效')
  const response = envelope(await request.get('/api/operations/' + operationId, { medical: 'envelope', signal, headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': context
  } }))
  const result = checkOperation(validate<Operation>('urn:medical:shared#/$defs/Operation', response.data))
  if (response.resource_revision !== null || result.operation_id !== operationId) fail('CONTENT_MISMATCH', '原操作响应身份不一致')
  return result
}

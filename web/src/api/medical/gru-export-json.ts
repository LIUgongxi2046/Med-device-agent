import { fail, MedicalFailure } from './protocol'
import { readBoundedJson } from './project-json'
export const gruExportContentLimit = 32 * 1024 * 1024
export const gruExportEnvelopeLimit = 1024 * 1024
export function readGruExportJson(text: unknown): unknown {
  if (typeof text !== 'string') fail('SCHEMA_INVALID', '导出元数据需要原始 JSON 文本')
  if (text.length > gruExportEnvelopeLimit || new TextEncoder().encode(text).byteLength > gruExportEnvelopeLimit) throw new MedicalFailure('CONTEXT_LIMIT', '导出元数据超过客户端读取容量')
  return readBoundedJson(text)
}

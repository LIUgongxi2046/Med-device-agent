import { fail, MedicalFailure } from '@/api/medical/protocol'
import { readBoundedJson } from '@/api/medical/project-json'
export const retentionEnvelopeLimit = 2 * 1024 * 1024
export function readRetentionJson(text: unknown): unknown {
  if (typeof text !== 'string') fail('SCHEMA_INVALID', '保存策略响应需要原始 JSON 文本')
  if (text.length > retentionEnvelopeLimit || new TextEncoder().encode(text).byteLength > retentionEnvelopeLimit) throw new MedicalFailure('CONTEXT_LIMIT', '保存策略响应超过客户端解析容量')
  return readBoundedJson(text)
}

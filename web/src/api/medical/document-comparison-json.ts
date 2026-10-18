import { fail, MedicalFailure } from './protocol'
import { readBoundedJson } from './project-json'

/** Client safety ceiling, not a claimed deployment limit or browser network-buffer bound. */
export const comparisonResponseBytes = 8 * 1024 * 1024
export function readComparisonJson(text: unknown): unknown {
  if (typeof text !== 'string') fail('SCHEMA_INVALID', '比较响应需要原始 JSON 文本')
  if (text.length > comparisonResponseBytes || new TextEncoder().encode(text).byteLength > comparisonResponseBytes) throw new MedicalFailure('CONTEXT_LIMIT', '比较响应超过客户端读取容量，请联系部署管理员核对容量')
  return readBoundedJson(text)
}

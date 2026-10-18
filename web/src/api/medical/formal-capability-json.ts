import { readBoundedJson } from './project-json'
import { MedicalFailure } from './protocol'
/** Closed capability responses need original text to reject duplicate fields before JSON decoding. */
export function readCapabilityJson(value: unknown): unknown {
  if (typeof value !== 'string') throw new MedicalFailure('SCHEMA_INVALID', '正式能力响应需要原始 JSON 文本')
  if (new TextEncoder().encode(value).byteLength > 8388608) throw new MedicalFailure('CONTEXT_LIMIT', '正式能力响应超过本页核验容量，请缩小声明规模或联系管理员')
  return readBoundedJson(value)
}

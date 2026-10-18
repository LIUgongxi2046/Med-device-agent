import { readBoundedJson } from './project-json'
import { MedicalFailure } from './protocol'
export function readExecutionPolicyJson(value: unknown): unknown {
 if (typeof value !== 'string') throw new MedicalFailure('SCHEMA_INVALID','执行策略响应需要原始 JSON 文本')
 if (new TextEncoder().encode(value).byteLength > 8388608) throw new MedicalFailure('CONTEXT_LIMIT','执行策略响应超过客户端核验容量')
 return readBoundedJson(value)
}

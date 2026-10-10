import type { JsonValue, SchemaCatalogue } from './catalogue.js'
import { utcTime, uuidValue } from './wire.js'

// 字段来自唯一 model-stream.schema.json，数值及条件约束由发布集合执行。
export type Id = string
export type Text = string
export type NullableText = string | null
export type Hash = string
export type TokenCount = number | null
export type ExecutionStatus = 'RESERVED' | 'DISPATCHING' | 'IN_FLIGHT' | 'SUCCEEDED' | 'FAILED' | 'CANCEL_REQUESTED' | 'CANCELLED' | 'UNKNOWN'
export type SettlementStatus = 'PENDING' | 'SETTLED' | 'DISPUTED'

export interface RequestParameters {
  max_output_tokens: number
  temperature: number | null
  reasoning_effort: NullableText
  stop: Text[]
  purpose: 'TASK' | 'COMPACTION' | 'VERIFICATION' | 'EVALUATION'
}

export interface StreamRequest {
  schema_version: '1.0.0'
  run_id: Id
  step_id: Id
  action_id: Id
  context_manifest_id: Id
  model_config_revision: Id
  request_parameters: RequestParameters
  lease_generation: number
}

export interface CancelRequest {
  schema_version: '1.0.0'
  run_id: Id
  step_id: Id
  action_id: Id
  invocation_id: Id
  reason_code: 'RUN_CANCEL_REQUESTED' | 'DEADLINE_EXCEEDED' | 'AUTH_REVOKED' | 'PROTOCOL_ERROR'
  lease_generation: number
}

export interface InvocationStatus {
  invocation_id: Id
  origin_action_id: Id
  context_manifest_id: Id
  model_config_revision: Id
  execution_status: ExecutionStatus
  settlement_status: SettlementStatus
  provider_request_id: NullableText
  result_artifact_id: Id | null
  result_sha256: Hash | null
  failure_code: NullableText
}

export interface Failure {
  code: Text
  message: Text
  provider_status: number | null
  provider_request_id: NullableText
}

export interface TextBlock { type: 'text'; text: string }
export interface ToolBlock { type: 'tool-call'; id: Text; name: Text; arguments: Text }
export type Block = TextBlock | ToolBlock

export interface Usage {
  uncached_input_tokens: TokenCount
  cache_read_tokens: TokenCount
  cache_write_tokens: TokenCount
  output_tokens: TokenCount
  total_tokens: TokenCount
  reasoning_tokens: TokenCount
  source_receipt_id: Id
}

export type SuccessfulFinishReason = { kind: 'stop' | 'tool-calls' | 'max-tokens'; failure: null }
export type AbortedFinishReason = { kind: 'aborted'; failure: Failure }
export type ErrorFinishReason = { kind: 'error'; failure: Failure }
export type FinishReason = SuccessfulFinishReason | AbortedFinishReason | ErrorFinishReason

export interface Accepted { type: 'accepted'; execution_status: 'RESERVED' | 'DISPATCHING' | 'IN_FLIGHT' }
export interface BlockStart { type: 'block_start'; index: number; block_type: Block['type'] }
export interface TextDelta { type: 'text_delta'; index: number; text: Text }
export interface ToolDelta { type: 'tool_call_delta'; index: number; call_id: Text; name: NullableText; arguments_delta: string }
export interface BlockEnd { type: 'block_end'; index: number; block: Block }
export interface UsageEvent { type: 'usage'; usage: Usage }

interface FinishIdentity {
  type: 'finish'
  settlement_status: SettlementStatus
  result_artifact_id: Id
  result_sha256: Hash
  replay_state_id: Id | null
}

export type FinishEvent = FinishIdentity & (
  { reason: SuccessfulFinishReason; execution_status: 'SUCCEEDED' }
  | { reason: AbortedFinishReason; execution_status: 'CANCELLED' }
  | { reason: ErrorFinishReason; execution_status: 'FAILED' }
)

export interface ErrorEvent {
  type: 'error'
  failure: Failure
  execution_status: 'FAILED' | 'UNKNOWN' | 'CANCEL_REQUESTED'
  settlement_status: SettlementStatus
}

export type StreamPayload = Accepted | BlockStart | TextDelta | ToolDelta | BlockEnd | UsageEvent | FinishEvent | ErrorEvent

export interface StreamEvent {
  schema_version: '1.0.0'
  invocation_id: Id
  run_id: Id
  step_id: Id
  action_id: Id
  context_manifest_id: Id
  model_config_revision: Id
  sequence: number
  emitted_at: string
  payload: StreamPayload
}

export interface ModelResult {
  schema_version: '1.0.0'
  invocation_id: Id
  context_manifest_id: Id
  model_config_revision: Id
  provider_request_id: NullableText
  blocks: Block[]
  usage: Usage | null
  reason: FinishReason
  replay_state_id: Id | null
}

export type EnterpriseModelProtocol = StreamRequest | StreamEvent | ModelResult | InvocationStatus | CancelRequest

export interface ModelStreamDefinitions {
  Id: Id; Text: Text; NullableText: NullableText; Hash: Hash; TokenCount: TokenCount
  StreamRequest: StreamRequest; RequestParameters: RequestParameters; CancelRequest: CancelRequest; InvocationStatus: InvocationStatus
  Failure: Failure; TextBlock: TextBlock; ToolBlock: ToolBlock; Block: Block; Usage: Usage; FinishReason: FinishReason
  Accepted: Accepted; BlockStart: BlockStart; TextDelta: TextDelta; ToolDelta: ToolDelta; BlockEnd: BlockEnd
  UsageEvent: UsageEvent; FinishEvent: FinishEvent; ErrorEvent: ErrorEvent; StreamEvent: StreamEvent; ModelResult: ModelResult
}

export const MODEL_STREAM_DEFINITIONS = [
  'Id', 'Text', 'NullableText', 'Hash', 'TokenCount', 'StreamRequest', 'RequestParameters', 'CancelRequest', 'InvocationStatus',
  'Failure', 'TextBlock', 'ToolBlock', 'Block', 'Usage', 'FinishReason', 'Accepted', 'BlockStart', 'TextDelta', 'ToolDelta',
  'BlockEnd', 'UsageEvent', 'FinishEvent', 'ErrorEvent', 'StreamEvent', 'ModelResult',
] as const satisfies readonly (keyof ModelStreamDefinitions)[]

export interface ModelStreamBinding<K extends keyof ModelStreamDefinitions> {
  readonly definition: K
  readonly schemaRef: string
  readonly schemaSha256: string
  readonly canonicalJson: string
  read(): ModelStreamDefinitions[K]
}

const uuidFields = new Set([
  'run_id', 'step_id', 'action_id', 'context_manifest_id', 'model_config_revision',
  'invocation_id', 'origin_action_id', 'result_artifact_id', 'replay_state_id', 'source_receipt_id',
])

function validateWireFields(value: JsonValue): void {
  if (Array.isArray(value)) {
    for (const child of value) validateWireFields(child)
    return
  }
  if (value === null || typeof value !== 'object') return
  for (const [field, child] of Object.entries(value)) {
    if (uuidFields.has(field) && child !== null) uuidValue(child)
    if (field === 'emitted_at') utcTime(child)
    validateWireFields(child)
  }
}

export function bindModelStream<K extends keyof ModelStreamDefinitions>(
  catalogue: SchemaCatalogue, definition: K, value: unknown,
): ModelStreamBinding<K> {
  const saved = catalogue.bind('model-stream', value, definition)
  const parsed: JsonValue = JSON.parse(saved.canonicalJson) as JsonValue
  if (definition === 'Id') uuidValue(parsed)
  validateWireFields(parsed)
  // 每次读取重新解析已验证的规范 JSON，调用者修改不能改变保存内容。
  return Object.freeze({
    ...saved, definition,
    read: (): ModelStreamDefinitions[K] => JSON.parse(saved.canonicalJson) as ModelStreamDefinitions[K],
  })
}

export function decodeModelStream<K extends keyof ModelStreamDefinitions>(
  catalogue: SchemaCatalogue, definition: K, value: unknown,
): ModelStreamDefinitions[K] {
  return bindModelStream(catalogue, definition, value).read()
}

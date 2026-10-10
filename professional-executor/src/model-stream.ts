import { createHash } from 'node:crypto'
import { canonicalize } from 'json-canonicalize'
import { LlmError, ToolCallId, ProviderRequestId, type StreamChunk, type TokenUsage, type FinishReason } from '@deepseek-ai/dsh-llm'
import { type SchemaCatalogue, type JsonValue } from '@medical-agent/contracts'
import { decodeModelStream, type StreamRequest, type StreamEvent, type ModelResult, type Block, type Usage, type FinishEvent } from '@medical-agent/contracts/model-stream'
import { readJson } from './json.js'

export interface ModelResultReader {
  readResult(artifactId: string, signal: AbortSignal): Promise<Uint8Array>
}

interface OpenBlock {
  kind: Block['type']
  text: string
  callId: string | null
  name: string | null
  closed: Block | null
}

export function protocolError(message: string): never {
  throw new LlmError(message, 'MODEL_PROTOCOL_ERROR')
}

export function usageChunk(usage: Usage): StreamChunk | null {
  const { uncached_input_tokens: input, cache_read_tokens: read, cache_write_tokens: write, output_tokens: output, total_tokens: total, reasoning_tokens: reasoning } = usage
  if (reasoning !== null && output !== null && reasoning > output) protocolError('推理计数超过输出计数')
  if (total !== null && input !== null && read !== null && write !== null && output !== null && total !== input + read + write + output) protocolError('总计数与分项不一致')
  if (input === null || output === null) return null
  const mapped: TokenUsage = { inputTokens: input, outputTokens: output }
  if (read !== null) mapped.cacheReadTokens = read
  if (write !== null) mapped.cacheWriteTokens = write
  if (total !== null) mapped.totalTokens = total
  if (reasoning !== null) mapped.reasoningTokens = reasoning
  return { type: 'usage', usage: mapped }
}

function blockChunk(index: number, block: Block): StreamChunk {
  return { type: 'block-end', index, block: block.type === 'text' ? block : { ...block, id: ToolCallId(block.id) } }
}

export function finishReason(result: ModelResult['reason']): FinishReason {
  if (result.kind !== 'error' && result.kind !== 'aborted') return { kind: result.kind }
  const failure: Extract<FinishReason, { kind: 'error' }>['failure'] = { code: result.failure.code, message: result.failure.message }
  if (result.failure.provider_status !== null) Object.assign(failure, { status: result.failure.provider_status })
  if (result.failure.provider_request_id !== null) Object.assign(failure, { requestId: ProviderRequestId(result.failure.provider_request_id) })
  return { kind: result.kind, failure }
}

export class ModelStreamVerifier {
  private sequence = 0
  private invocation: string | null = null
  private readonly blocks: OpenBlock[] = []
  private readonly callIds = new Set<string>()
  private usage: Usage | null = null
  private ended = false
  private permitsTools = false

  constructor(private readonly catalogue: SchemaCatalogue, private readonly request: StreamRequest,
    private readonly validateTool: (name: string, args: JsonValue) => void,
    private readonly reader: ModelResultReader) {
    decodeModelStream(catalogue, 'StreamRequest', request)
  }

  get invocationId(): string | null { return this.invocation }
  get terminal(): boolean { return this.ended }
  get authorizedToolCalls(): readonly Extract<Block, { type: 'tool-call' }>[] {
    return this.permitsTools ? this.blocks.flatMap(block => block.closed?.type === 'tool-call' ? [block.closed] : []) : []
  }

  private open(index: number, kind?: Block['type']): OpenBlock {
    const block = this.blocks[index]
    if (!block || block.closed !== null || (kind !== undefined && block.kind !== kind)) protocolError('模型内容块状态不一致')
    return block
  }

  async consume(raw: unknown, signal: AbortSignal): Promise<StreamChunk[]> {
    signal.throwIfAborted()
    const event = decodeModelStream(this.catalogue, 'StreamEvent', raw)
    if (this.ended || event.sequence !== this.sequence) protocolError('模型事件顺序不一致')
    for (const field of ['run_id', 'step_id', 'action_id', 'context_manifest_id', 'model_config_revision'] as const) {
      if (event[field] !== this.request[field]) protocolError('模型事件身份不一致')
    }
    if (this.sequence === 0) {
      if (event.payload.type !== 'accepted') protocolError('模型流缺少接收回执')
      this.invocation = event.invocation_id
    } else if (event.invocation_id !== this.invocation || event.payload.type === 'accepted') protocolError('模型调用身份发生变化')
    this.sequence += 1
    const payload = event.payload
    if (payload.type === 'accepted') return []
    if (payload.type === 'error') {
      this.ended = true
      throw new LlmError(payload.failure.message, payload.failure.code)
    }
    if (payload.type === 'finish') return this.finish(payload, signal)
    if (this.usage !== null) protocolError('用量回执之后存在内容事件')
    if (payload.type === 'usage') {
      if (this.blocks.some(block => block.closed === null)) protocolError('用量回执存在未完成内容块')
      this.usage = payload.usage
      const mapped = usageChunk(payload.usage)
      return mapped === null ? [] : [mapped]
    }
    if (payload.type === 'block_start') {
      if (payload.index !== this.blocks.length) protocolError('模型内容块序号不连续')
      this.blocks.push({ kind: payload.block_type, text: '', callId: null, name: null, closed: null })
      return [{ type: 'block-start', index: payload.index, blockType: payload.block_type }]
    }
    if (payload.type === 'text_delta') {
      this.open(payload.index, 'text').text += payload.text
      return [{ type: 'text-delta', index: payload.index, text: payload.text }]
    }
    if (payload.type === 'tool_call_delta') {
      const block = this.open(payload.index, 'tool-call')
      if (block.callId === null) {
        if (payload.name === null || this.callIds.has(payload.call_id)) protocolError('工具调用缺少名称或重复身份')
        block.callId = payload.call_id
        block.name = payload.name
        this.callIds.add(payload.call_id)
      } else if (block.callId !== payload.call_id || (payload.name !== null && payload.name !== block.name)) protocolError('工具调用身份发生变化')
      block.text += payload.arguments_delta
      const chunk: Extract<StreamChunk, { type: 'tool-call-delta' }> = { type: 'tool-call-delta', index: payload.index, id: ToolCallId(payload.call_id), argumentsDelta: payload.arguments_delta }
      if (payload.name !== null) chunk.name = payload.name
      return [chunk]
    }
    const block = this.open(payload.index, payload.block.type)
    if (payload.block.type === 'text') {
      if (payload.block.text !== block.text) protocolError('完整文本与增量不一致')
    } else {
      if (payload.block.id !== block.callId || payload.block.name !== block.name || payload.block.arguments !== block.text) protocolError('完整工具调用与增量不一致')
      this.validateTool(payload.block.name, readJson(payload.block.arguments))
    }
    block.closed = payload.block
    return [blockChunk(payload.index, payload.block)]
  }

  private async finish(event: FinishEvent, signal: AbortSignal): Promise<StreamChunk[]> {
    const successful = event.reason.kind !== 'error' && event.reason.kind !== 'aborted'
    if (successful && this.blocks.some(block => block.closed === null)) protocolError('成功响应存在未完成内容块')
    const complete = this.blocks.flatMap(block => block.closed === null ? [] : [block.closed])
    const tools = complete.filter(block => block.type === 'tool-call')
    if (event.reason.kind === 'tool-calls' && tools.length === 0) protocolError('工具终态缺少完整工具调用')
    if (event.reason.kind === 'stop' && tools.length !== 0) protocolError('文本终态包含待执行工具调用')
    const bytes = await this.reader.readResult(event.result_artifact_id, signal)
    if (createHash('sha256').update(bytes).digest('hex') !== event.result_sha256) protocolError('保存结果摘要不一致')
    const saved = decodeModelStream(this.catalogue, 'ModelResult', readJson(new TextDecoder('utf-8', { fatal: true }).decode(bytes)))
    if (saved.invocation_id !== this.invocation || saved.context_manifest_id !== this.request.context_manifest_id || saved.model_config_revision !== this.request.model_config_revision) protocolError('保存结果身份不一致')
    if (canonicalize(saved.blocks) !== canonicalize(complete) || canonicalize(saved.usage) !== canonicalize(this.usage) || canonicalize(saved.reason) !== canonicalize(event.reason) || saved.replay_state_id !== event.replay_state_id) protocolError('保存结果与本次响应不一致')
    this.ended = true
    this.permitsTools = saved.reason.kind === 'tool-calls'
    const chunk: Extract<StreamChunk, { type: 'finish' }> = { type: 'finish', reason: finishReason(saved.reason) }
    if ((saved.reason.kind === 'stop' || saved.reason.kind === 'tool-calls') && saved.replay_state_id !== null) chunk.replayState = { response: { invocation_id: saved.invocation_id, replay_state_id: saved.replay_state_id } }
    return [chunk]
  }

  assertComplete(): void {
    if (!this.ended) protocolError('模型流缺少持久保存的终态回执')
  }
}

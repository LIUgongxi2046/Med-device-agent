import { createHash } from 'node:crypto'
import { canonicalize } from 'json-canonicalize'
import { LlmAdapter, LlmError, ToolCallId, ReasoningEffortId, type GenerateOptions, type StreamChunk, type LlmResolvedModelInfo, type ResolvedRetryPolicy } from '@deepseek-ai/dsh-llm'
import { type SchemaCatalogue, type JsonValue } from '@medical-agent/contracts'
import { decodeModelStream, type StreamRequest, type ModelResult, type CancelRequest, type ToolBlock } from '@medical-agent/contracts/model-stream'
import { PrivateBridge } from './bridge.js'
import { ModelStreamVerifier, finishReason, usageChunk, protocolError } from './model-stream.js'
import { readJson } from './json.js'

export interface EnterpriseModelConfig {
  model: string
  contextWindow: number
  maxOutputTokens: number
  streamTimeoutMs: number
  cancellationReason: CancelRequest['reason_code']
  reasoningEfforts: readonly string[]
  defaultReasoningEffort: string | null
}

export class EnterpriseModelAdapter extends LlmAdapter {
  constructor(private readonly config: EnterpriseModelConfig, private readonly catalogue: SchemaCatalogue,
    private readonly connect: () => Promise<PrivateBridge>, private readonly validateTool: (name: string, args: JsonValue) => void,
    private readonly authorizeTools: (calls: readonly ToolBlock[]) => void) { super() }

  override providerInfo(provider: string) {
    if (provider !== 'enterprise-model') throw new LlmError('模型路由未发布', 'MODEL_CONFIG_INVALID')
    return { id: provider, name: '企业模型接口' }
  }

  override providerRetryPolicy(): ResolvedRetryPolicy {
    return Object.freeze({ mode: 'normal', maxRetries: 0, retryableCodes: Object.freeze(['TRANSPORT']), initialDelayMs: 500, maxDelayMs: 10_000, jitterRatio: 0 })
  }

  override async listModels(provider: string) { return [await this.resolveModel(provider, this.config.model)] }

  override async resolveModel(provider: string, model: string, signal?: AbortSignal): Promise<LlmResolvedModelInfo> {
    signal?.throwIfAborted()
    this.providerInfo(provider)
    if (model !== this.config.model) throw new LlmError('模型版本未发布', 'MODEL_VERSION_UNAVAILABLE')
    const info: LlmResolvedModelInfo = { provider, id: model, name: model, inputModalities: ['text'], context: { contextWindow: this.config.contextWindow }, defaultMaxTokens: this.config.maxOutputTokens }
    if (this.config.reasoningEfforts.length !== 0) {
      info.reasoning = { efforts: this.config.reasoningEfforts.map(id => ({ id: ReasoningEffortId(id), name: id })) }
      if (this.config.defaultReasoningEffort !== null) {
        if (!this.config.reasoningEfforts.includes(this.config.defaultReasoningEffort)) throw new LlmError('默认推理参数未发布', 'MODEL_CONFIG_INVALID')
        info.reasoning.defaultEffort = ReasoningEffortId(this.config.defaultReasoningEffort)
      }
    } else if (this.config.defaultReasoningEffort !== null) throw new LlmError('默认推理参数未发布', 'MODEL_CONFIG_INVALID')
    return info
  }

  override async *stream(options: GenerateOptions): AsyncIterable<StreamChunk> {
    this.authorizeTools([])
    await this.resolveModel(options.provider, options.model, options.signal)
    if (options.purpose === 'session-title') throw new LlmError('当前运行配置不允许会话标题调用', 'MODEL_CONFIG_INVALID')
    const timeout = AbortSignal.timeout(this.config.streamTimeoutMs)
    const signal = options.signal === undefined ? timeout : AbortSignal.any([options.signal, timeout])
    signal.throwIfAborted()
    const projection = readJson(canonicalize({ provider: options.provider, model: options.model, messages: options.messages,
      system: options.system ?? null, tools: options.tools ?? [], tool_history: options.toolHistory ?? null,
      parameters: { max_output_tokens: options.maxTokens ?? this.config.maxOutputTokens, temperature: options.temperature ?? null,
        reasoning_effort: options.reasoningEffort ?? null, stop: options.stop ?? [], purpose: options.purpose === 'compaction' ? 'COMPACTION' : 'TASK' },
      session_id: options.sessionId ?? null }))
    const bridge = await this.connect()
    let request: StreamRequest | null = null
    let verifier: ModelStreamVerifier | null = null
    let originalInvocation: string | null = null
    let originalTerminal = false
    try {
      request = await bridge.prepare(projection, signal)
      const parameters = request.request_parameters
      if (parameters.max_output_tokens !== (options.maxTokens ?? this.config.maxOutputTokens) || parameters.temperature !== (options.temperature ?? null) || parameters.reasoning_effort !== (options.reasoningEffort ?? null) || canonicalize(parameters.stop) !== canonicalize(options.stop ?? [])) protocolError('已保存请求参数与模型请求不一致')
      if (parameters.max_output_tokens > this.config.maxOutputTokens || (options.purpose === 'compaction' && parameters.purpose !== 'COMPACTION') || (options.purpose === undefined && parameters.purpose === 'COMPACTION')) protocolError('模型请求超出发布配置')
      verifier = new ModelStreamVerifier(this.catalogue, request, this.validateTool, bridge)
      const opening = await bridge.open(request, signal)
      if (opening.kind === 'original') {
        const status = opening.status
        originalInvocation = status.invocation_id
        if (status.result_artifact_id === null || status.result_sha256 === null || !['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status.execution_status)) throw new LlmError('原模型调用需要核对', 'INVOCATION_RECONCILIATION_REQUIRED')
        const bytes = await bridge.readResult(status.result_artifact_id, signal)
        if (createHash('sha256').update(bytes).digest('hex') !== status.result_sha256) protocolError('原模型结果摘要不一致')
        const result = decodeModelStream(this.catalogue, 'ModelResult', readJson(new TextDecoder('utf-8', { fatal: true }).decode(bytes)))
        if (result.invocation_id !== status.invocation_id || result.context_manifest_id !== request.context_manifest_id || result.model_config_revision !== request.model_config_revision) protocolError('原模型结果身份不一致')
        const expected = result.reason.kind === 'error' ? 'FAILED' : result.reason.kind === 'aborted' ? 'CANCELLED' : 'SUCCEEDED'
        if (expected !== status.execution_status || result.provider_request_id !== status.provider_request_id) protocolError('原模型结果终态不一致')
        const chunks = this.replay(result)
        originalTerminal = true
        this.authorizeTools(result.reason.kind === 'tool-calls' ? result.blocks.filter((block): block is ToolBlock => block.type === 'tool-call') : [])
        for (const chunk of chunks) { signal.throwIfAborted(); yield chunk }
        return
      }
      let completed: Extract<StreamChunk, { type: 'finish' }> | null = null
      for await (const event of opening.events) {
        const chunks = await verifier.consume(event, signal)
        for (const chunk of chunks) {
          if (chunk.type === 'finish') completed = chunk
          else yield chunk
        }
      }
      verifier.assertComplete()
      if (completed === null) protocolError('模型流缺少完成回执')
      this.authorizeTools(verifier.authorizedToolCalls)
      yield completed
    } finally {
      try {
        const invocation = verifier?.invocationId ?? originalInvocation
        if (signal.aborted && request !== null && invocation !== null && !verifier?.terminal && !originalTerminal) {
          const status = await bridge.cancel({ schema_version: '1.0.0', run_id: request.run_id, step_id: request.step_id, action_id: request.action_id,
            invocation_id: invocation, reason_code: timeout.aborted ? 'DEADLINE_EXCEEDED' : this.config.cancellationReason, lease_generation: request.lease_generation })
          if (status.invocation_id !== invocation || status.origin_action_id !== request.action_id || status.context_manifest_id !== request.context_manifest_id || status.model_config_revision !== request.model_config_revision) protocolError('模型取消回执身份不一致')
        }
      } finally { await bridge.close() }
    }
  }

  private replay(result: ModelResult): StreamChunk[] {
    const ids = new Set<string>()
    const chunks: StreamChunk[] = []
    for (const [index, block] of result.blocks.entries()) {
      chunks.push({ type: 'block-start', index, blockType: block.type })
      if (block.type === 'text') {
        chunks.push({ type: 'text-delta', index, text: block.text }, { type: 'block-end', index, block })
      } else {
        if (ids.has(block.id)) protocolError('原结果包含重复工具调用')
        ids.add(block.id)
        this.validateTool(block.name, readJson(block.arguments))
        chunks.push({ type: 'tool-call-delta', index, id: ToolCallId(block.id), name: block.name, argumentsDelta: block.arguments },
          { type: 'block-end', index, block: { ...block, id: ToolCallId(block.id) } })
      }
    }
    if ((result.reason.kind === 'tool-calls' && ids.size === 0) || (result.reason.kind === 'stop' && ids.size !== 0)) protocolError('原结果工具终态不一致')
    if (result.usage !== null) { const usage = usageChunk(result.usage); if (usage !== null) chunks.push(usage) }
    const finish: Extract<StreamChunk, { type: 'finish' }> = { type: 'finish', reason: finishReason(result.reason) }
    if ((result.reason.kind === 'stop' || result.reason.kind === 'tool-calls') && result.replay_state_id !== null) finish.replayState = { response: { invocation_id: result.invocation_id, replay_state_id: result.replay_state_id } }
    chunks.push(finish)
    return chunks
  }
}

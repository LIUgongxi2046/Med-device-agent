/** Independent EVAL SDK lane. Backend owns exact-input and history authorization. */
import { canonicalize } from 'json-canonicalize'
import { LlmError, type GenerateOptions, type StreamChunk } from '@deepseek-ai/dsh-llm'
import { decodeModelStream, type ToolBlock } from '@medical-agent/contracts/model-stream'
import { type JsonValue, type SchemaCatalogue } from '@medical-agent/contracts'
import { EnterpriseModelAdapter, type EnterpriseModelConfig } from './enterprise-model.js'
import { PrivateBridge } from './bridge.js'
import { readJson } from './json.js'

export class EvaluationModelAdapter extends EnterpriseModelAdapter {
  constructor(private readonly settings: EnterpriseModelConfig, private readonly schemas: SchemaCatalogue,
    private readonly connection: () => Promise<PrivateBridge>, validate: (name: string, args: JsonValue) => void,
    private readonly authorize: (calls: readonly ToolBlock[]) => void) { super(settings, schemas, connection, validate, authorize) }

  override async *stream(options: GenerateOptions): AsyncIterable<StreamChunk> {
    this.authorize([])
    await this.resolveModel(options.provider, options.model, options.signal)
    if (options.purpose !== undefined) throw new LlmError('EVAL 不允许辅助模型调用', 'MODEL_CONFIG_INVALID')
    // The runner supplies the physically approved exact-input object as the sole
    // SDK user seed. Never send this serialization as model user content.
    const initial = options.messages.findIndex(message => message.role === 'user')
    if (initial < 0 || options.messages.slice(0, initial).some(message => message.role !== 'system')) throw new Error('EVAL_EXACT_INPUT_REQUIRED')
    const seed = options.messages[initial]!
    if (seed.content.some(block => block.type !== 'text')) throw new Error('EVAL_TEXT_INPUT_REQUIRED')
    const raw = seed.content.map(block => block.type === 'text' ? block.text : '').join('')
    const exact = readJson(raw) as { messages: { role: string; content: string }[]; tools: { type: string; function: JsonValue }[] }
    if (Object.keys(exact).sort().join(',') !== 'messages,tools' || !Array.isArray(exact.messages) || !Array.isArray(exact.tools)) throw new Error('EVAL_EXACT_INPUT_REQUIRED')
    let user = false
    for (const message of exact.messages) {
      if (Object.keys(message).sort().join(',') !== 'content,role' || typeof message.content !== 'string' || user || !['system', 'user'].includes(message.role)) throw new Error('EVAL_MESSAGE_ORDER_INVALID')
      if (message.role === 'user') user = true
    }
    if (!user || exact.tools.some(tool => tool.type !== 'function') || canonicalize(exact.tools.map(tool => tool.function)) !== canonicalize(options.tools ?? [])) throw new Error('EVAL_APPROVED_TOOLS_MISMATCH')
    const history = options.messages.slice(initial + 1)
    if (history.some(message => !['assistant', 'tool'].includes(message.role))) throw new Error('EVAL_HISTORY_INVALID')
    const messages = [...exact.messages.map(message => ({ role: message.role, content: [{ type: 'text', text: message.content }], source: { kind: message.role === 'system' ? 'system-prompt' : 'user' } })), ...history]
    const projection = readJson(canonicalize({ provider: options.provider, model: options.model, messages, system: null,
      tools: options.tools ?? [], tool_history: options.toolHistory ?? null, session_id: options.sessionId ?? null,
      parameters: { max_output_tokens: options.maxTokens ?? this.settings.maxOutputTokens, temperature: options.temperature ?? null,
        reasoning_effort: options.reasoningEffort ?? null, stop: options.stop ?? [], purpose: 'EVALUATION' } }))
    const signal = options.signal === undefined ? AbortSignal.timeout(this.settings.streamTimeoutMs) : AbortSignal.any([options.signal, AbortSignal.timeout(this.settings.streamTimeoutMs)])
    const bridge = await this.connection()
    try {
      // Exactly one observed request -> one backend dispatch. Never retry a lost response.
      const result = decodeModelStream(this.schemas, 'ModelResult', await bridge.evaluation(projection, signal))
      const chunks = this.replay(result)
      this.authorize(result.reason.kind === 'tool-calls' ? result.blocks.filter((block): block is ToolBlock => block.type === 'tool-call') : [])
      for (const chunk of chunks) { signal.throwIfAborted(); yield chunk }
    } finally { await bridge.close() }
  }
}

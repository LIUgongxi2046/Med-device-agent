import { createHash } from 'node:crypto'
import { canonicalize } from 'json-canonicalize'
import { type Context } from '@deepseek-ai/cordis'
import { type ToolDefinition } from '@deepseek-ai/dsh-tools'
import { assertJson, type SchemaCatalogue, type JsonValue } from '@medical-agent/contracts'
import { type PrivateBridge } from './bridge.js'
import { readJson } from './json.js'

export const toolDefinitions = [
  ['AT-01', 'search_sources', 'SearchArgs', '检索已批准的资料', 'SEARCH', null],
  ['AT-02', 'read_source', 'ReadArgs', '读取指定原文及引用', 'SOURCE', null],
  ['AT-03', 'read_artifact', 'ArtifactArgs', '读取指定版本的成果', 'ARTIFACT', null],
  ['AT-04', 'load_skill', 'SkillArgs', '读取当前角色允许的能力', 'SKILL', null],
  ['AT-05', 'validate_fields', 'FieldArgs', '核验指定字段与证据', 'RECEIPT', 'FIELD_CHECK'],
  ['AT-06', 'submit_candidate', 'CandidateArgs', '保存指定章节候选', 'RECEIPT', 'CANDIDATE'],
  ['AT-07', 'submit_review_findings', 'ReviewArgs', '保存独立审查结果', 'RECEIPT', 'REVIEW_FINDINGS'],
  ['AT-08', 'ask_user_question', 'QuestionArgs', '保存人工待办并结束当前执行', 'RECEIPT', 'WAITING_ITEM'],
  ['AT-09', 'propose_web_acquisition', 'WebArgs', '提出已批准范围的采集任务', 'RECEIPT', 'WEB_ACQUISITION'],
  ['AT-10', 'submit_plan_candidate', 'PlanArgs', '保存完整计划候选', 'RECEIPT', 'PLAN_CANDIDATE'],
] as const

export const roleTools = {
  Planner: ['AT-03', 'AT-04', 'AT-08', 'AT-10'],
  Researcher: ['AT-01', 'AT-02', 'AT-03', 'AT-04', 'AT-08'],
  NetworkResearcher: ['AT-03', 'AT-04', 'AT-08', 'AT-09'],
  FieldVerifier: ['AT-02', 'AT-03', 'AT-04', 'AT-05', 'AT-08'],
  SectionWriter: ['AT-02', 'AT-03', 'AT-04', 'AT-06', 'AT-08'],
  SourceVerifier: ['AT-01', 'AT-02', 'AT-03', 'AT-04', 'AT-05', 'AT-07', 'AT-08'],
  Assembler: ['AT-03', 'AT-04', 'AT-05', 'AT-07', 'AT-08'],
} as const

type ToolReceipt = {
  action_id: string; tool_id: string; status: string; coverage: { complete: boolean }; next_cursor: string | null
  payload: { kind: string; resource_kind?: string; match_status?: string; evidence_refs?: unknown[]; content?: string; content_sha256?: string; media_type?: string; content_schema_ref?: string | null }
}

export function validateToolResult(catalogue: SchemaCatalogue, toolId: string, value: unknown, evaluation = false, evidenceV2 = false): asserts value is JsonValue {
  const resultSchema = !evaluation && (toolId === 'AT-01' || toolId === 'AT-02') ? 'task-tool-read-success' : 'tool-success'
  catalogue.validate(evidenceV2 ? `${resultSchema}-v2` : resultSchema, value)
  const saved = value as unknown as ToolReceipt
  const tool = toolDefinitions.find(entry => entry[0] === toolId)
  if (!tool || saved.tool_id !== toolId || saved.payload.kind !== tool[4] || (tool[5] !== null && saved.payload.resource_kind !== tool[5])) throw new Error('工具结果类型不一致')
  if (saved.status !== (toolId === 'AT-08' ? 'WAITING_INPUT' : 'SUCCEEDED') || saved.coverage.complete !== (saved.next_cursor === null)) throw new Error('工具结果状态或覆盖范围不一致')
  if (saved.payload.kind === 'SEARCH' && saved.payload.match_status === 'EMPTY' && (saved.payload.evidence_refs?.length !== 0 || !saved.coverage.complete)) throw new Error('空检索结果缺少完整覆盖')
  if (saved.payload.kind === 'ARTIFACT' || saved.payload.kind === 'SKILL') {
    const payload = saved.payload
    if (typeof payload.content !== 'string' || createHash('sha256').update(payload.content, 'utf-8').digest('hex') !== payload.content_sha256) throw new Error('工具内容摘要不一致')
    if (payload.media_type === 'application/json') {
      if (typeof payload.content_schema_ref !== 'string') throw new Error('结构化内容缺少发布 Schema')
      const reference = new URL(payload.content_schema_ref)
      const definition = reference.hash.length === 0 ? undefined : decodeURIComponent(reference.hash.replace(/^#\/\$defs\//, ''))
      // 发布引用必须由登记集合确认，内容使用完整 Schema 再次校验。
      let matched = false
      for (const name of ['artifacts', 'dataset', 'model-stream', 'interfaces', 'resource-details', 'agent-release', 'task-plan-view', 'templates', 'template-operations', 'tasks', 'collections', 'rule-expressions', 'template-applicability', 'tool-inputs', 'tool-success', 'task-tool-read-success', 'artifacts-v2', 'source-evidence-v2', 'tool-inputs-v2', 'tool-success-v2', 'task-tool-read-success-v2', 'document-body-v2', 'task-section-writer-package-v2', 'task-source-verifier-package-v2', 'document-review-package-v2', 'strict-field-typed-v2', 'strict-field-comparison-v2', 'task-strict-field-input-v2', 'evaluation-stage-input-v2']) {
        const declared = new URL(catalogue.schemaRef(name))
        const baseReference = new URL(reference.href)
        baseReference.hash = ''
        declared.hash = ''
        if (baseReference.href === declared.href && reference.search === '') {
          if (payload.content_schema_ref !== catalogue.schemaRef(name, definition)) throw new Error('结构化内容 Schema 引用不正确')
          catalogue.validate(name, readJson(payload.content), definition)
          matched = true
          break
        }
      }
      if (!matched) throw new Error('结构化内容 Schema 未发布')
    }
  }
}

export function registerTools(ctx: Context, catalogue: SchemaCatalogue, inputs: Record<string, Record<string, unknown>>,
  role: keyof typeof roleTools, timeouts: Record<string, number>, connect: () => Promise<PrivateBridge>,
  authorizedCalls: ReadonlyMap<string, { name: string; argumentsSha256: string }>, evaluationToolIds?: readonly string[], evidenceToolContracts: Partial<Record<'AT-01' | 'AT-02' | 'AT-08', '2.0.0'>> = {}, inputsV2: Record<string, Record<string, unknown>> = {}) {
  if (evaluationToolIds !== undefined && (new Set(evaluationToolIds).size !== evaluationToolIds.length || evaluationToolIds.some(id => !['AT-01','AT-02','AT-03','AT-04','AT-08'].includes(id) || !(roleTools[role] as readonly string[]).includes(id)))) throw new Error('EVAL 工具必须属于当前角色批准的独立工具范围')
  const selected = toolDefinitions.filter(tool => (roleTools[role] as readonly string[]).includes(tool[0]) && (evaluationToolIds === undefined || evaluationToolIds.includes(tool[0])))
  for (const id of Object.keys(evidenceToolContracts)) if (!selected.some(tool => tool[0] === id)) throw new Error('证据版本配置包含未批准工具')
  const v2 = (id: string) => evidenceToolContracts[id as keyof typeof evidenceToolContracts] === '2.0.0'
  const validate = (name: string, args: JsonValue): void => {
    const tool = selected.find(entry => entry[1] === name)
    if (!tool) throw new Error('当前角色不允许该工具')
    catalogue.validate(v2(tool[0]) ? 'tool-inputs-v2' : 'tool-inputs', args, tool[2])
  }
  for (const [toolId, name, definition, description] of selected) {
    const parameters = (v2(toolId) ? inputsV2 : inputs)[definition]
    const timeoutMs = timeouts[toolId]
    if (!parameters || !Number.isSafeInteger(timeoutMs) || timeoutMs === undefined || timeoutMs <= 0) throw new Error('工具缺少发布 Schema 或时限')
    const tool: ToolDefinition = {
      name, description, parameters, timeoutMs,
      // 官方输出接口支持 JSON Schema 子集；完整发布 Schema 在返回及渲染时执行。
      output: { schema: {}, render: (_args, value) => { validateToolResult(catalogue, toolId, value, evaluationToolIds !== undefined, v2(toolId)); return [{ type: 'text', text: canonicalize(value) }] } },
      execute: async (args, exec) => {
        assertJson(args)
        validate(name, args)
        const authority = authorizedCalls.get(exec.callId)
        const argumentsSha256 = createHash('sha256').update(canonicalize(args)).digest('hex')
        if (!authority || authority.name !== name || authority.argumentsSha256 !== argumentsSha256) throw new Error('工具调用缺少有效模型终态授权')
        const bridge = await connect()
        try {
          const result = await bridge.tool(toolId, { call_id: exec.callId, name, arguments: args, arguments_sha256: argumentsSha256 }, AbortSignal.any([exec.signal, AbortSignal.timeout(timeoutMs)]))
          validateToolResult(catalogue, toolId, result, evaluationToolIds !== undefined, v2(toolId))
          if (toolId === 'AT-08') exec.concludeTurn()
          return result
        } finally { await bridge.close() }
      },
    }
    ctx.tools.register(tool)
  }
  ctx.tools.guard(exec => {
    const call = authorizedCalls.get(exec.callId)
    if (!call || call.name !== exec.name) return '工具调用缺少有效模型终态授权'
    assertJson(exec.arguments)
    if (createHash('sha256').update(canonicalize(exec.arguments)).digest('hex') !== call.argumentsSha256) return '工具参数摘要发生变化'
    return undefined
  })
  return { validate, names: selected.map(tool => tool[1]) }
}

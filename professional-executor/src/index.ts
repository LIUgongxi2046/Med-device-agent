import { createHash } from 'node:crypto'
import { constants, openSync, fstatSync, ftruncateSync, writeFileSync, closeSync } from 'node:fs'
import { isAbsolute } from 'node:path'
import { canonicalize } from 'json-canonicalize'
import { z } from 'zod'
import { type Context } from '@deepseek-ai/cordis'
import '@deepseek-ai/cordis-plugin-loader'
import '@deepseek-ai/dsh-tools'
import '@deepseek-ai/dsh-agent'
import { EnterpriseModelAdapter } from './enterprise-model.js'
import { PrivateBridge } from './bridge.js'
import { loadCatalogue } from './catalogue.js'
import { registerTools, roleTools } from './tools.js'
import { readJson } from './json.js'

export const name = 'medical-professional-executor'
export const inject = ['llm', 'tools', 'loader']

const positive = z.number().int().positive().max(Number.MAX_SAFE_INTEGER)
const absolutePath = z.string().min(1).refine(isAbsolute, '配置路径必须使用绝对路径')
const configuration = z.strictObject({
  schemaRoot: absolutePath, socketPath: absolutePath, tokenPath: absolutePath, inventoryPath: absolutePath,
  role: z.enum(['Planner', 'Researcher', 'NetworkResearcher', 'FieldVerifier', 'SectionWriter', 'SourceVerifier', 'Assembler']),
  model: z.string().min(1), contextWindow: positive, maxOutputTokens: positive,
  reasoningEfforts: z.array(z.string().min(1)), defaultReasoningEffort: z.string().min(1).nullable(),
  receiveTimeoutMs: positive.max(10_000), streamTimeoutMs: positive.max(300_000),
  maxResponseBytes: positive, maxEventCharacters: positive,
  cancellationReason: z.enum(['RUN_CANCEL_REQUESTED', 'DEADLINE_EXCEEDED', 'AUTH_REVOKED', 'PROTOCOL_ERROR']),
  toolTimeouts: z.record(z.enum(['AT-01', 'AT-02', 'AT-03', 'AT-04', 'AT-05', 'AT-06', 'AT-07', 'AT-08', 'AT-09', 'AT-10']), positive),
  allowedPluginNames: z.array(z.string().min(1)).nonempty(),
})

export async function apply(ctx: Context, input: unknown): Promise<void> {
  const config = configuration.parse(input)
  if (config.maxOutputTokens > config.contextWindow || new Set(config.allowedPluginNames).size !== config.allowedPluginNames.length) throw new Error('专业发布配置不正确')
  const { catalogue, inputDefinitions } = await loadCatalogue(config.schemaRoot)
  const connect = () => { verify(); return PrivateBridge.connect(config.socketPath, config.tokenPath, catalogue, config) }
  const authorizedCalls = new Map<string, { name: string; argumentsSha256: string }>()
  const tools = registerTools(ctx, catalogue, inputDefinitions, config.role, config.toolTimeouts, connect, authorizedCalls)
  const adapter = new EnterpriseModelAdapter(config, catalogue, connect, tools.validate, calls => {
    authorizedCalls.clear()
    for (const call of calls) authorizedCalls.set(call.id, { name: call.name, argumentsSha256: createHash('sha256').update(canonicalize(readJson(call.arguments))).digest('hex') })
  })
  ctx.llm.registerAdapter(['enterprise-model'], adapter)
  const verify = () => {
    const plugins = [...ctx.loader.entries()].filter(entry => !entry.disabled).map(entry => entry.options.name)
    const unknown = plugins.find(plugin => !config.allowedPluginNames.includes(plugin))
    if (unknown !== undefined) throw new Error(`专业运行环境包含未发布插件 ${unknown}`)
    if (canonicalize([...plugins].sort()) !== canonicalize([...config.allowedPluginNames].sort())) throw new Error('专业运行环境缺少发布插件或存在重复插件')
    const names = ctx.tools.schemas().map(tool => tool.name).sort()
    if (canonicalize(names) !== canonicalize([...tools.names].sort()) || ctx.llm.listProviders().some(provider => provider.id !== 'enterprise-model')) throw new Error('专业工具或模型清单不一致')
  }
  verify()
  ctx.on('agent/created', () => { verify(); return undefined })
  ctx.on('tools/change', verify)
  ctx.on('llm/adapters-updated', verify)
  ctx.on('loader/config-update', verify)
  ctx.tools.guard(() => { verify(); return undefined })
  const inventory = { schema_version: '1.0.0', process_id: process.pid, role: config.role, created_at: new Date().toISOString(),
    plugins: [...ctx.loader.entries()].filter(entry => !entry.disabled).map(entry => ({ id: entry.options.id, name: entry.options.name })),
    tools: ctx.tools.schemas().map(tool => ({ name: tool.name, parameters_sha256: createHash('sha256').update(canonicalize(tool.parameters)).digest('hex') })),
    providers: ctx.llm.listProviders(), models: await ctx.llm.listModels('enterprise-model'),
    model_stream_schema_ref: catalogue.schemaRef('model-stream'), configuration_sha256: createHash('sha256').update(canonicalize(config)).digest('hex') }
  const descriptor = openSync(config.inventoryPath, constants.O_WRONLY | constants.O_CREAT | constants.O_NOFOLLOW, 0o600)
  try {
    const owned = fstatSync(descriptor)
    if (!owned.isFile() || owned.uid !== process.getuid?.() || (owned.mode & 0o077) !== 0) throw new Error('专业运行清单权限不正确')
    ftruncateSync(descriptor, 0)
    writeFileSync(descriptor, canonicalize(inventory), 'utf-8')
  } finally { closeSync(descriptor) }
}

export { EnterpriseModelAdapter } from './enterprise-model.js'
export { ModelStreamVerifier } from './model-stream.js'
export { PrivateBridge } from './bridge.js'
export { loadCatalogue } from './catalogue.js'
export { roleTools } from './tools.js'

import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { createHash, randomUUID } from 'node:crypto'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadCatalogue, ModelStreamVerifier, EnterpriseModelAdapter } from '../lib/index.js'
import { readJson } from '../lib/json.js'
import { usageChunk } from '../lib/model-stream.js'

const root = fileURLToPath(new URL('../../', import.meta.url))
const { catalogue, inputDefinitions } = await loadCatalogue(root)
const request = { schema_version: '1.0.0', run_id: randomUUID(), step_id: randomUUID(), action_id: randomUUID(),
  context_manifest_id: randomUUID(), model_config_revision: randomUUID(), lease_generation: 1,
  request_parameters: { max_output_tokens: 8192, temperature: null, reasoning_effort: null, stop: [], purpose: 'TASK' } }
const invocation = randomUUID()
const event = (sequence, payload) => ({ schema_version: '1.0.0', invocation_id: invocation, run_id: request.run_id,
  step_id: request.step_id, action_id: request.action_id, context_manifest_id: request.context_manifest_id,
  model_config_revision: request.model_config_revision, sequence, emitted_at: '2026-10-09T02:00:00Z', payload })
const signal = new AbortController().signal

test('模拟输入经完整发布 Schema 校验，工具引用经成熟库展开', () => {
  assert.equal(inputDefinitions.SearchArgs.properties.question_id.format, 'uuid')
  assert.equal(inputDefinitions.ReadArgs.properties.evidence_ref.type, 'object')
  catalogue.validate('tool-inputs', { artifact_id: randomUUID(), node_ids: [], cursor: null }, 'ArtifactArgs')
  assert.throws(() => catalogue.validate('tool-inputs', { artifact_id: randomUUID(), node_ids: [], cursor: null, path: '/unauthorized' }, 'ArtifactArgs'))
  assert.throws(() => readJson('{"value":1,"value":2}'), /重复字段/)
  assert.throws(() => readJson('{"value":9007199254740993}'), /safely|JSON/)
})

test('模拟模型输出必须完成实际文件保存及摘要核验，方可返回完成回执', async () => {
  const directory = resolve(root, '.implementation-work/professional-model-check')
  await mkdir(directory, { recursive: true, mode: 0o700 })
  const result = { schema_version: '1.0.0', invocation_id: invocation, context_manifest_id: request.context_manifest_id,
    model_config_revision: request.model_config_revision, provider_request_id: 'SIMULATION-001',
    blocks: [{ type: 'text', text: '模拟资料的章节计划' }], usage: null, reason: { kind: 'stop', failure: null }, replay_state_id: null }
  const bytes = Buffer.from(JSON.stringify(result))
  const artifact = randomUUID()
  const path = resolve(directory, `${artifact}.json`)
  await writeFile(path, bytes, { mode: 0o600 })
  const reader = { readResult: async (id, abort) => { abort.throwIfAborted(); assert.equal(id, artifact); return readFile(path) } }
  const verifier = new ModelStreamVerifier(catalogue, request, () => assert.fail('当前响应未调用工具'), reader)
  assert.deepEqual(await verifier.consume(event(0, { type: 'accepted', execution_status: 'RESERVED' }), signal), [])
  await verifier.consume(event(1, { type: 'block_start', index: 0, block_type: 'text' }), signal)
  await verifier.consume(event(2, { type: 'text_delta', index: 0, text: result.blocks[0].text }), signal)
  await verifier.consume(event(3, { type: 'block_end', index: 0, block: result.blocks[0] }), signal)
  const chunks = await verifier.consume(event(4, { type: 'finish', reason: result.reason, execution_status: 'SUCCEEDED', settlement_status: 'PENDING',
    result_artifact_id: artifact, result_sha256: createHash('sha256').update(bytes).digest('hex'), replay_state_id: null }), signal)
  assert.deepEqual(chunks, [{ type: 'finish', reason: { kind: 'stop' } }])
  verifier.assertComplete()
  assert.equal(verifier.terminal, true)
  await assert.rejects(() => verifier.consume(event(5, { type: 'accepted', execution_status: 'RESERVED' }), signal), /顺序/)
})

test('序号缺口及跨任务事件立即拒绝，缺少完成回执不能登记成功', async () => {
  const verifier = new ModelStreamVerifier(catalogue, request, () => {}, { readResult: async () => { assert.fail('当前场景不允许读取结果') } })
  await verifier.consume(event(0, { type: 'accepted', execution_status: 'IN_FLIGHT' }), signal)
  await assert.rejects(() => verifier.consume(event(2, { type: 'block_start', index: 0, block_type: 'text' }), signal), /顺序/)
  await assert.rejects(() => verifier.consume({ ...event(1, { type: 'block_start', index: 0, block_type: 'text' }), run_id: randomUUID() }, signal), /身份/)
  assert.throws(() => verifier.assertComplete(), /终态/)
})

test('未知用量保留未知状态，矛盾用量立即拒绝', () => {
  const usage = { uncached_input_tokens: null, cache_read_tokens: null, cache_write_tokens: null, output_tokens: null,
    total_tokens: null, reasoning_tokens: null, source_receipt_id: randomUUID() }
  assert.equal(usageChunk(usage), null)
  assert.throws(() => usageChunk({ ...usage, uncached_input_tokens: 1, cache_read_tokens: 0, cache_write_tokens: 0, output_tokens: 2, total_tokens: 4 }), /总计数/)
  assert.throws(() => usageChunk({ ...usage, output_tokens: 2, reasoning_tokens: 3 }), /推理计数/)
})

test('企业模型固定路由及零次附加重试生效', async () => {
  const adapter = new EnterpriseModelAdapter({ model: 'SIMULATION', contextWindow: 32768, maxOutputTokens: 8192, streamTimeoutMs: 300000,
    cancellationReason: 'RUN_CANCEL_REQUESTED', reasoningEfforts: ['none'], defaultReasoningEffort: 'none' }, catalogue, async () => assert.fail('路由检查不允许发送'), () => {}, () => {})
  assert.equal((await adapter.resolveModel('enterprise-model', 'SIMULATION')).defaultMaxTokens, 8192)
  assert.equal(adapter.providerRetryPolicy().maxRetries, 0)
  await assert.rejects(() => adapter.resolveModel('enterprise-model', 'unpublished'), /未发布/)
})

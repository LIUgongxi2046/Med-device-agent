import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createServer } from 'node:http'
import { once } from 'node:events'
import { chmod, mkdir, readFile, writeFile, rm } from 'node:fs/promises'
import { createHash, randomBytes, randomUUID } from 'node:crypto'
import { relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadCatalogue, PrivateBridge, EnterpriseModelAdapter } from '../lib/index.js'
import { readJson } from '../lib/json.js'

const root = fileURLToPath(new URL('../../', import.meta.url))
const { catalogue } = await loadCatalogue(root)

test('工程模拟接口通过实际Unix Socket及SSE完成模型消费，重复动作使用原结果', { timeout: 45_000 }, async context => {
  await mkdir(resolve(root, '.build-work'), { recursive: true, mode: 0o700 })
  await mkdir(resolve(root, '.implementation-work'), { recursive: true, mode: 0o700 })
  const directory = resolve(root, '.build-work', `mp-${randomUUID().slice(0, 8)}`)
  await mkdir(directory, { mode: 0o700 })
  const socketPath = resolve(directory, 'b.sock')
  const tokenPath = resolve(directory, 'b.token')
  const token = randomBytes(32).toString('base64url')
  await writeFile(tokenPath, token, { mode: 0o600 })
  const request = { schema_version: '1.0.0', run_id: randomUUID(), step_id: randomUUID(), action_id: randomUUID(),
    context_manifest_id: randomUUID(), model_config_revision: randomUUID(), lease_generation: 1,
    request_parameters: { max_output_tokens: 8192, temperature: null, reasoning_effort: 'none', stop: [], purpose: 'TASK' } }
  const invocation = randomUUID()
  const artifact = randomUUID()
  const result = { schema_version: '1.0.0', invocation_id: invocation, context_manifest_id: request.context_manifest_id,
    model_config_revision: request.model_config_revision, provider_request_id: 'SIMULATION:UNIX-001',
    blocks: [{ type: 'text', text: '模拟资料用于开发验收' }], usage: null, reason: { kind: 'stop', failure: null }, replay_state_id: null }
  const bytes = Buffer.from(JSON.stringify(result))
  const artifactPath = resolve(directory, `${artifact}.json`)
  await writeFile(artifactPath, bytes, { mode: 0o600 })
  const digest = createHash('sha256').update(bytes).digest('hex')
  let dispatches = 0
  let contexts = 0
  let firstProjection = null
  const server = createServer(async (incoming, outgoing) => {
    assert.equal(incoming.headers.authorization, `Bearer ${token}`)
    const chunks = []
    for await (const chunk of incoming) chunks.push(chunk)
    const body = chunks.length === 0 ? null : readJson(Buffer.concat(chunks).toString('utf-8'))
    if (incoming.url === '/v1/model-contexts') {
      assert.equal(body.provider, 'enterprise-model')
      assert.equal(body.model, 'SIMULATION')
      assert.deepEqual(body.parameters, request.request_parameters)
      if (firstProjection === null) firstProjection = body
      else assert.deepEqual(body, firstProjection)
      contexts += 1
      outgoing.setHeader('content-type', 'application/json')
      outgoing.end(JSON.stringify(request))
      return
    }
    if (incoming.url === `/v1/model-results/${artifact}`) {
      outgoing.setHeader('content-type', 'application/json')
      outgoing.end(await readFile(artifactPath))
      return
    }
    assert.equal(incoming.url, '/v1/model-streams')
    assert.equal(incoming.headers['idempotency-key'], request.action_id)
    assert.deepEqual(body, request)
    if (dispatches !== 0) {
      outgoing.setHeader('content-type', 'application/json')
      outgoing.end(JSON.stringify({ invocation_id: invocation, origin_action_id: request.action_id,
        context_manifest_id: request.context_manifest_id, model_config_revision: request.model_config_revision,
        execution_status: 'SUCCEEDED', settlement_status: 'PENDING', provider_request_id: result.provider_request_id,
        result_artifact_id: artifact, result_sha256: digest, failure_code: null }))
      return
    }
    dispatches += 1
    outgoing.setHeader('content-type', 'text/event-stream; charset=utf-8')
    outgoing.setHeader('cache-control', 'no-store')
    const payloads = [{ type: 'accepted', execution_status: 'IN_FLIGHT' },
      { type: 'block_start', index: 0, block_type: 'text' }, { type: 'text_delta', index: 0, text: result.blocks[0].text },
      { type: 'block_end', index: 0, block: result.blocks[0] },
      { type: 'finish', reason: result.reason, execution_status: 'SUCCEEDED', settlement_status: 'PENDING',
        result_artifact_id: artifact, result_sha256: digest, replay_state_id: null }]
    for (const [sequence, payload] of payloads.entries()) {
      const event = { schema_version: '1.0.0', invocation_id: invocation, run_id: request.run_id, step_id: request.step_id,
        action_id: request.action_id, context_manifest_id: request.context_manifest_id, model_config_revision: request.model_config_revision,
        sequence, emitted_at: new Date().toISOString(), payload }
      const wire = Buffer.from(`id: ${invocation}:${sequence}\nevent: ${payload.type}\ndata: ${JSON.stringify(event)}\n\n`)
      // 实际传输包含UTF-8字符跨片段，完整分帧由成熟组件处理。
      for (let index = 0; index < wire.length; index += 7) outgoing.write(wire.subarray(index, index + 7))
    }
    outgoing.end()
  })
  server.listen(relative(process.cwd(), socketPath))
  await once(server, 'listening')
  await chmod(socketPath, 0o600)
  const limits = { receiveTimeoutMs: 10000, streamTimeoutMs: 300000, maxResponseBytes: 16777216, maxEventCharacters: 1048576 }
  const connect = () => PrivateBridge.connect(socketPath, tokenPath, catalogue, limits)
  const authorized = []
  const adapter = new EnterpriseModelAdapter({ model: 'SIMULATION', contextWindow: 32768, maxOutputTokens: 8192,
    streamTimeoutMs: 300000, cancellationReason: 'RUN_CANCEL_REQUESTED', reasoningEfforts: ['none'], defaultReasoningEffort: 'none' },
    catalogue, connect, () => assert.fail('当前模型响应未调用工具'), calls => authorized.push(calls))
  try {
    const options = { provider: 'enterprise-model', model: 'SIMULATION', reasoningEffort: 'none', signal: context.signal,
      messages: [{ role: 'user', content: [{ type: 'text', text: '模拟资料：编写计划' }] }] }
    const streamed = []
    for await (const chunk of adapter.stream(options)) streamed.push(chunk)
    const restored = []
    for await (const chunk of adapter.stream(options)) restored.push(chunk)
    assert.deepEqual(restored, streamed)
    assert.equal(streamed.at(-1).reason.kind, 'stop')
    assert.equal(contexts, 2)
    assert.equal(dispatches, 1)
    assert.equal(authorized.every(calls => calls.length === 0), true)
    await writeFile(resolve(root, '.implementation-work/professional-bridge-result.json'), JSON.stringify({ input_data_kind: 'SIMULATION',
      unix_socket_verified: true, utf8_sse_verified: true, saved_result_verified: true, original_result_restored: true,
      dispatches, customer_acceptance_verified: false }), { mode: 0o600 })
  } finally {
    server.closeAllConnections()
    await new Promise((resolveClose, rejectClose) => server.close(error => error ? rejectClose(error) : resolveClose()))
    await rm(directory, { recursive: true })
  }
})

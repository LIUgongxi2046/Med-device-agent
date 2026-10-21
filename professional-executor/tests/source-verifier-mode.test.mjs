import assert from 'node:assert/strict'
import { test } from 'node:test'
import { EnterpriseModelAdapter } from '../lib/enterprise-model.js'

const config = { modelToolMode: 'SOURCE_VERIFIER_N05_V1', model: 'fixture-approved', contextWindow: 10000,
  maxOutputTokens: 1000, streamTimeoutMs: 1000, cancellationReason: 'PROTOCOL_ERROR', reasoningEfforts: [], defaultReasoningEffort: null }
const options = { provider: 'enterprise-model', model: 'fixture-approved', messages: [], sessionId: 'fixture-session' }

for (const [name, override] of [
  ['model tool', { tools: [{ name: 'AT-07', parameters: {} }] }],
  ['compaction', { purpose: 'compaction' }],
  ['tool history', { toolHistory: { tools: [{ name: 'AT-07' }], updates: [] } }],
]) {
  test(`N05 rejects ${name} before contacting bridge`, async () => {
    let calls = 0
    const adapter = new EnterpriseModelAdapter(config, {}, async () => { calls++; assert.fail('bridge must not be contacted') }, () => assert.fail('no model tools'), () => {})
    await assert.rejects(async () => { for await (const _ of adapter.stream({ ...options, ...override })) {} }, /N05/)
    assert.equal(calls, 0)
  })
}

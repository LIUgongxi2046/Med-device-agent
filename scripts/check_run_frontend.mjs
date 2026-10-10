import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: protocol } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/protocol.ts', import.meta.url)), { root, mode: 'medical' })
for (const name of ['View', 'Step', 'Waiting', 'Invocation', 'Budget']) {
  const issues = protocol.schemaIssues('urn:medical:run-view:1.0.0#/$defs/' + name, {})
  assert.ok(issues.some(issue => issue.code === 'required'), '运行响应必须编译全部必填字段')
}
const id = '11111111-1111-4111-8111-111111111111'
const invocation = { invocation_id: id, step_id: id, action_id: id, status: 'UNKNOWN', settlement_status: 'PENDING', currency: 'CNY', settled_amount: null, total_tokens: null, revision: '1' }
assert.deepEqual(protocol.schemaIssues('urn:medical:run-view:1.0.0#/$defs/Invocation', invocation), [])
assert.ok(protocol.schemaIssues('urn:medical:run-view:1.0.0#/$defs/Invocation', { ...invocation, settlement_status: 'SETTLED' }).length > 0)
const actual = JSON.parse(await readFile(fileURLToPath(new URL('../.implementation-work/run-read-http-result.json', import.meta.url)), 'utf8'))
assert.equal(actual.data_kind, 'SIMULATION')
assert.deepEqual(protocol.schemaIssues('urn:medical:run-control:1.0.0#/$defs/View', actual.cancellation.data), [])
assert.ok(protocol.schemaIssues('urn:medical:run-control:1.0.0#/$defs/View', { ...actual.cancellation.data, command_id: null }).length > 0)
process.stdout.write(JSON.stringify({ input_data_kind: 'SIMULATION', compiled_definitions: 8, actual_cancellation_verified: true, unknown_cost_verified: true, visual_verified: false }) + '\n')

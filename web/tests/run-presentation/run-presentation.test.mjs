import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
const read = path => fs.readFileSync(new URL(path, import.meta.url), 'utf8')
const transpile = source => ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const helper = { exports: {} }; vm.runInNewContext(transpile(read('../../src/views/medical/run-presentation.ts')), helper)
const { runRecordStatus } = helper.exports
const schema = JSON.parse(read('../../../business/medical-persistence/src/main/resources/run-view.schema.json'))
for (const [kind, definition, field] of [['step', 'Step', 'status'], ['waiting', 'Waiting', 'status'], ['invocation', 'Invocation', 'status'], ['settlement', 'Invocation', 'settlement_status']]) {
  test(`${kind} labels cover exact schema enums and retain raw codes`, () => {
    for (const status of schema.$defs[definition].properties[field].enum) {
      assert.ok(runRecordStatus(kind, status).endsWith(`（${status}）`))
      assert.doesNotMatch(runRecordStatus(kind, status), /^未知状态/)
    }
    for (const status of ['FUTURE', 'toString', '__proto__']) assert.equal(runRecordStatus(kind, status), `未知状态（${status}）`)
  })
}
const content = read('../../src/views/medical/run-detail.vue')
const { descriptor, errors } = parse(content)
const script = compileScript(descriptor, { id: 'run-presentation-test' })
test('production component compiles and does not invent result routes or node identities', () => {
  assert.deepEqual(errors, [])
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: 'run-detail.vue', id: 'run-presentation-test', compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
  assert.match(content, /当前响应未提供专业节点名称/)
  assert.doesNotMatch(content, /router-link|router\.push|document_id|artifact_id|node_kind/)
  for (const field of ['role_version', 'section_id', 'target_revision', 'resolution_id', 'requirement_refs', 'allowed_resolution_actions']) assert.ok(content.includes(`scope.row.${field}`))
  assert.match(content, /settled_amount === null/)
  assert.match(content, /total_tokens \?\?/)
})
function setup({ readRun = async () => snapshot(), consumeRunEvents = () => new Promise(() => {}), allowed = true, hidden = false, cancellation = {} } = {}) {
  const listeners = new Map(), watches = [], hooks = []
  const document = { hidden, addEventListener: (key, fn) => listeners.set(key, fn), removeEventListener() {} }
  const window = { addEventListener: (key, fn) => listeners.set(key, fn), removeEventListener() {} }
  const vue = { defineComponent: x => x, ref: value => ({ value }), computed: fn => ({ get value() { return fn() } }), watch: (source, callback, options) => { watches.push(callback); if (options.immediate) callback() }, onBeforeUnmount: fn => hooks.push(fn) }
  const sandbox = { exports: {}, AbortController, document, window, require(name) {
    if (name === 'vue') return vue
    if (name.endsWith('run-presentation')) return helper.exports
    if (name.endsWith('task-detail')) return { taskActionAllowed: () => allowed }
    if (name.endsWith('/runs')) return { readRun, runStatusLabels: { RUNNING: '正在执行' } }
    if (name.endsWith('run-events')) return { consumeRunEvents }
    if (name.endsWith('run-cancellation')) return { pendingCancellation: () => null, ...cancellation }
    if (name.endsWith('.vue')) return {}
    throw Error(name)
  } }
  vm.runInNewContext(transpile(script.content), sandbox)
  const props = { session: {}, projectId: 'project', taskId: 'task', runId: 'run', active: true }
  const state = sandbox.exports.default.setup(props, { expose() {} })
  return { state, props, listeners, watches, hooks, document }
}
const snapshot = () => ({ run_id: 'run', task_id: 'task', status: 'RUNNING', revision: '9007199254740995', event_cursor: null })
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }
test('read failure shows actionable error without retaining snapshot', async () => {
  const { state } = setup({ readRun: async () => { throw Error('private server details') } }); await flush()
  assert.equal(state.current.value, null); assert.equal(state.loading.value, false)
  assert.match(state.notice.value, /读取失败/); assert.doesNotMatch(state.notice.value, /private/)
})
test('stream failure clears stale snapshot, stops streaming and does not infer failed run', async () => {
  const { state } = setup({ consumeRunEvents: async () => { throw Error('offline') } }); await flush()
  assert.equal(state.current.value, null); assert.equal(state.streaming.value, false); assert.equal(state.loading.value, false)
  assert.match(state.notice.value, /不将断开视为运行失败/)
})
test('late read failure cannot overwrite a new context', async () => {
  let reject; const { state, props, watches } = setup({ readRun: () => new Promise((_, no) => { reject = no }) })
  props.active = false; watches[0](); reject(Error('old request')); await flush()
  assert.equal(state.current.value, null); assert.equal(state.notice.value, '')
})
test('hidden and unauthorized contexts never initiate reads', async () => {
  let reads = 0
  for (const flags of [{ hidden: true }, { allowed: false }]) {
    const { state } = setup({ ...flags, readRun: async () => { reads++; return snapshot() } }); await flush(); assert.equal(state.current.value, null)
  }
  assert.equal(reads, 0)
})
test('global failure still clears records and aborts old request, with generic feedback only', async () => {
  let signal; const { state, listeners } = setup({ readRun: async (...args) => { signal = args.at(-1); return new Promise(() => {}) } })
  listeners.get('medical-failure')(); assert.equal(signal.aborted, true); assert.equal(state.current.value, null)
  assert.match(state.notice.value, /已清除旧记录/); assert.equal(state.confirming.value, false)
})
test('successful read preserves exact revision; hiding clears all feedback and data', async () => {
  const { state, document, listeners } = setup(); await flush()
  assert.equal(state.current.value.revision, '9007199254740995')
  document.hidden = true; listeners.get('visibilitychange')()
  assert.equal(state.current.value, null); assert.equal(state.notice.value, ''); assert.equal(state.streaming.value, false)
})

test('original cancellation lookup failure retains pending identity without submitting again', async () => {
  const original = { key: 'original-key', expected_revision: '9007199254740995' }; let lookups = 0, submits = 0
  const { state } = setup({ cancellation: { pendingCancellation: () => original, recoverCancellation: async (_, pending) => { lookups++; assert.equal(pending, original); throw Error('offline') }, submitCancellation: () => { submits++ } } }); await flush()
  await state.recover()
  assert.equal(state.pending.value, original); assert.equal(state.writing.value, false); assert.equal(lookups, 1); assert.equal(submits, 0)
  assert.match(state.notice.value, /保留原请求身份/)
})
test('uncertain stop result keeps original request identity and never automatically retries', async () => {
  const original = { key: 'original-key', expected_revision: '9007199254740995' }; let submits = 0
  const { state } = setup({ cancellation: { pendingCancellation: () => original, prepareCancellation: async () => original, submitCancellation: async (_, pending) => { submits++; assert.equal(pending, original); throw Error('offline') } } }); await flush()
  state.openCancellation(); state.acknowledged.value = true; state.reason.value = '停止理由'
  await state.cancelRun()
  assert.equal(state.pending.value, original); assert.equal(state.writing.value, false); assert.equal(submits, 1)
  assert.match(state.notice.value, /不要新建重复请求/)
})

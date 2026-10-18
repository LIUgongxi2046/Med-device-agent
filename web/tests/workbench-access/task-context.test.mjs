import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const vue = require('vue'), ts = require('typescript')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const source = readFileSync(new URL('../../src/views/medical/tasks.vue', import.meta.url), 'utf8')
const helperSource = readFileSync(new URL('../../src/utils/medical-task-context.ts', import.meta.url), 'utf8')
const transpile = s => ts.transpileModule(s, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const helpers = await import('data:text/javascript;base64,' + Buffer.from(transpile(helperSource)).toString('base64'))
const parsed = parse(source)
const script = compileScript(parsed.descriptor, { id: 'tasks-test' })
const compiled = transpile(script.content).replace(/^import .*;\n/gm, '').replace('export default', 'return')
const ids = Array.from({ length: 8 }, (_, i) => `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`)
const tick = async () => { await vue.nextTick(); await Promise.resolve(); await Promise.resolve(); await vue.nextTick() }
const item = (task, waiting = [], runs = []) => ({ detail: { task_id: task, project_id: ids[0], waiting_item_ids: waiting, active_run_ids: runs } })
const page = (items, n = 1) => ({ items, page_info: { page: n, page_size: 20, total: '40', watermark: '7', filter_sha256: 'fixed' } })
function harness() {
  const calls = [], replacements = [], active = vue.ref(true)
  const user = vue.reactive({ medicalSession: { current_context: {}, user: { user_id: ids[7] } }, medicalWriting: false })
  const route = vue.reactive({ name: 'MedicalProjectTasks', params: { projectId: ids[0] }, path: `/projects/${ids[0]}/tasks`, fullPath: '/tasks?first', query: { waiting_item_id: ids[1], run_id: ids[2] } })
  const document = { hidden: false, addEventListener() {}, removeEventListener() {} }, window = { addEventListener() {}, removeEventListener() {} }
  let cleanup
  const env = { ...vue, ...helpers, _defineComponent: vue.defineComponent, document, window,
    useRoute: () => route, useRouter: () => ({ async replace(value) { replacements.push(value) } }), useUserStore: () => user,
    onBeforeUnmount(fn) { cleanup = fn }, useMedicalRouteRead: () => active,
    projectActionAllowed: s => !!s?.current_context, taskReadAllowed: () => false,
    schemaIssues: () => [], taskIdReference: 'id', taskFilterReference: 'filter', taskStatusLabels: {}, taskDocumentLabels: {}, Pagination: {}, SessionPanel: {},
    listTasks(...args) { let resolve, reject; const promise = new Promise((ok, fail) => { resolve = ok; reject = fail }); calls.push({ args, resolve, reject }); return promise } }
  const component = new Function(...Object.keys(env), compiled)(...Object.values(env)), scope = vue.effectScope()
  const state = scope.run(() => component.setup({}, { expose() {} }))
  return { state, calls, user, route, active, document, replacements, stop() { cleanup(); scope.stop() } }
}
test('tasks template compiles with context associations', () => {
  assert.deepEqual(parsed.errors, [])
  assert.deepEqual(compileTemplate({ source: parsed.descriptor.template.content, filename: 'tasks.vue', id: 'tasks-test', compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
})
test('query context validates scalar IDs; arrays/malformed IDs cannot create a match', () => {
  for (const query of [{ waiting_item_id: [ids[1]] }, { run_id: 'not-an-id' }, { waiting_item_id: ids[1], run_id: null }]) {
    const context = helpers.readWaitingTaskContext(query)
    assert.equal(context.invalid, true)
    assert.equal(helpers.waitingTaskMatch(item(ids[3], [ids[1]], [ids[2]]), context), null)
  }
  assert.equal(helpers.readWaitingTaskContext({}).requested, false)
})
test('home handoff carries IDs as query context, never assumes a task ID', () => {
  assert.deepEqual(helpers.waitingTaskLocation(ids[0], ids[1], ids[2]), { path: `/projects/${ids[0]}/tasks`, query: { waiting_item_id: ids[1], run_id: ids[2] } })
})
test('matching uses returned waiting IDs first and separately labels active-run association', async () => {
  const h = harness()
  h.calls[0].resolve(page([item(ids[3], [ids[1]]), item(ids[4], [], [ids[2]]), item(ids[5])]))
  await tick()
  assert.equal(h.state.contextMatches.value.length, 2)
  assert.equal(helpers.waitingTaskMatch(h.state.listing.value.items[0], h.state.waitingContext.value), 'WAITING_ITEM')
  assert.equal(helpers.waitingTaskMatch(h.state.listing.value.items[1], h.state.waitingContext.value), 'ACTIVE_RUN')
  assert.equal(h.calls.length, 1, 'no additional scans')
  assert.equal(h.replacements.length, 0, 'no automatic task navigation')
  h.stop()
})
test('no match only describes current page; explicit pagination uses original watermark and replaces matches', async () => {
  const h = harness(); h.calls[0].resolve(page([item(ids[3])])); await tick()
  assert.equal(h.state.contextMatches.value.length, 0)
  assert.match(source, /当前页未找到关联任务。这不表示待办或任务不存在/)
  const pending = h.state.paginate({ page: 2, limit: 20 })
  assert.equal(h.calls.length, 2)
  assert.equal(h.calls[1].args[2].dataset_watermark, '7')
  assert.equal(h.calls[1].args[2].filter_sha256, 'fixed')
  assert.equal(h.calls[1].args[2].waiting_item_id, undefined, 'query IDs are not invented API filters')
  h.calls[1].resolve(page([item(ids[4], [ids[1]])], 2)); await pending
  assert.equal(h.state.contextMatches.value[0].detail.task_id, ids[4])
  assert.equal(h.state.page.value, 2)
  h.stop()
})
test('revocation immediately clears context results and ignores late page', async () => {
  const h = harness(); h.calls[0].resolve(page([item(ids[3], [ids[1]])])); await tick()
  const pending = h.state.paginate({ page: 2, limit: 20 })
  h.user.medicalSession = null
  assert.equal(h.calls[1].args[4].aborted, true)
  assert.equal(h.state.contextMatches.value.length, 0)
  h.calls[1].resolve(page([item(ids[4], [ids[1]])], 2)); await pending
  assert.equal(h.state.listing.value, null)
  h.stop()
})
test('new query context cannot be overwritten by old response', async () => {
  const h = harness()
  h.route.query = { waiting_item_id: ids[6] }; h.route.fullPath = '/tasks?second'
  assert.equal(h.calls.length, 2)
  assert.equal(h.calls[0].args[4].aborted, true)
  h.calls[1].resolve(page([item(ids[4], [ids[6]])])); await tick()
  h.calls[0].resolve(page([item(ids[3], [ids[1]])])); await tick()
  assert.equal(h.state.contextMatches.value[0].detail.task_id, ids[4])
  h.stop()
})
test('pagination failure shows an error, never no-match; retry starts fresh and clearing context preserves other query', async () => {
  const h = harness(); h.calls[0].resolve(page([item(ids[3], [ids[1]])])); await tick()
  const pending = h.state.paginate({ page: 2, limit: 20 })
  h.calls[1].reject(new Error('stale watermark')); await pending
  assert.equal(h.state.listing.value, null)
  assert.ok(h.state.readError.value)
  const retry = h.state.search(); h.calls[2].resolve(page([])); await retry
  assert.equal(h.calls[2].args[2].page, 1)
  assert.equal(h.calls[2].args[2].dataset_watermark, undefined)
  h.route.query.keep = 'unchanged'; await h.state.clearWaitingContext()
  assert.deepEqual(h.replacements[0], { path: h.route.path, query: { keep: 'unchanged' } })
  h.stop()
})

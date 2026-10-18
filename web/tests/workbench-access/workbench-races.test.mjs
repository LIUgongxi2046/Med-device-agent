import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const vue = require('vue')
const ts = require('typescript')
const { parse, compileScript } = require('@vue/compiler-sfc')
const source = readFileSync(new URL('../../src/views/index.vue', import.meta.url), 'utf8')
const compiled = ts.transpileModule(compileScript(parse(source).descriptor, { id: 'workbench-test' }).content,
  { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
  .replace(/^import .*;\n/gm, '').replace('export default', 'return')
const tick = async () => { await vue.nextTick(); await Promise.resolve(); await Promise.resolve(); await vue.nextTick() }
function deferred() { let resolve, reject; const promise = new Promise((ok, fail) => { resolve = ok; reject = fail }); return { promise, resolve, reject } }
function session(actor) { return { user: { user_id: actor }, current_context: { tenant_id: actor }, permissions: ['project.read', 'work_item.read', 'project.task.read'] } }
const project = id => ({ project_id: id, allowed_actions: ['project.task.read'], updated_at: '2026-10-18T10:00:00Z', children: { tasks: '1', active_runs: '0' } })
const listing = (...ids) => ({ projects: ids.map(project), total: String(ids.length), next_after: null })
const queue = (next = null) => ({ items: [], total: '0', next_after: next })
function harness() {
  const calls = { projects: [], items: [], scenarios: [], tasks: [] }
  const document = { hidden: false, addEventListener() {}, removeEventListener() {} }
  const user = vue.reactive({ medicalSession: session('first') })
  const active = vue.ref(true)
  const scope = vue.effectScope()
  let unmount
  const api = kind => (...args) => { const d = deferred(); calls[kind].push({ ...d, args }); return d.promise }
  const env = { ...vue, _defineComponent: vue.defineComponent, document, onMounted() {}, onBeforeUnmount(fn) { unmount = fn },
    useUserStore: () => user, listProjects: api('projects'), listWorkItems: api('items'), listScenarios: api('scenarios'), listTasks: api('tasks'),
    projectActionAllowed: (s, action) => !!s?.current_context && s.permissions.includes(action),
    taskReadAllowed: () => false, useMedicalRouteRead: () => active, taskStatusLabels: {},
    waitingTaskLocation: () => ({}), workbenchCount: () => '0', recentWorkbenchProjects: value => value.slice(0, 6), SessionPanel: {}, DocumentRevisionAssignmentQueue: {} }
  const component = new Function(...Object.keys(env), compiled)(...Object.values(env))
  const state = scope.run(() => component.setup({}, { expose() {} }))
  return { state, user, active, document, calls, stop() { unmount(); scope.stop() } }
}
async function finish(h, index = 0, ids = ['one', 'two'], next = null) {
  h.calls.projects[index].resolve(listing(...ids)); h.calls.items[index].resolve(queue(next)); h.calls.scenarios[index].resolve({ planned_requirements: [] }); await tick()
}
test('revoked session aborts and clears immediately, late promises cannot restore data', async () => {
  const h = harness()
  h.user.medicalSession = null
  assert.equal(h.calls.projects[0].args[2].aborted, true)
  await finish(h)
  assert.equal(h.state.projects.value, null)
  assert.equal(h.state.workItems.value, null)
  assert.equal(h.state.loading.value, false)
  h.stop()
})
test('new actor gets fresh scope; old completion and old cursor are ignored', async () => {
  const h = harness(); await finish(h, 0, ['old'], 'cursor-old')
  h.user.medicalSession = session('second')
  await h.state.load('cursor-old')
  assert.equal(h.calls.items.length, 2)
  assert.equal(h.calls.items[1].args[1].assignee_id, 'second')
  assert.equal(h.calls.items[1].args[1].after, undefined)
  await finish(h, 1, ['new'])
  assert.equal(h.state.projects.value.projects[0].project_id, 'new')
  h.stop()
})
test('partial failure preserves successful sections and pagination failure requires a fresh page', async () => {
  const h = harness(); await finish(h, 0, ['one'], 'next-page')
  const next = h.state.load('next-page')
  await h.state.load('next-page')
  assert.equal(h.calls.items.length, 2, 'double-click does not reuse the cursor')
  assert.equal(h.calls.items[1].args[1].after, 'next-page')
  h.calls.items[1].reject(new Error('expired cursor'))
  h.calls.projects[1].resolve(listing('one')); h.calls.scenarios[1].resolve({ planned_requirements: [] }); await next
  assert.ok(h.state.errors.value.items)
  assert.equal(h.state.projects.value.total, '1')
  assert.equal(h.state.workItems.value, null)
  await h.state.load('next-page')
  assert.equal(h.calls.items.length, 2, 'failed cursor is not replayable')
  const retry = h.state.load(); await finish(h, 2); await retry
  assert.equal(h.calls.items[2].args[1].after, undefined)
  assert.equal(h.state.errors.value.items, '')
  h.stop()
})
test('rapid project selection retires previous task result and error', async () => {
  const h = harness(); await finish(h)
  h.state.selectedProject.value = 'one'; await tick()
  h.state.selectedProject.value = 'two'; await tick()
  assert.equal(h.calls.tasks[0].args[4].aborted, true)
  h.calls.tasks[1].resolve({ items: [{ detail: { task_id: 'second-task' } }] }); await tick()
  h.calls.tasks[0].reject(new Error('stale failure')); await tick()
  assert.equal(h.state.tasks.value.items[0].detail.task_id, 'second-task')
  assert.equal(h.state.taskError.value, '')
  h.stop()
})
test('cached off-route workbench clears and does not fetch on later session changes', async () => {
  const h = harness(); await finish(h)
  h.active.value = false
  assert.equal(h.state.projects.value, null)
  h.user.medicalSession = session('second')
  assert.equal(h.calls.projects.length, 1)
  h.active.value = true
  assert.equal(h.calls.projects.length, 2)
  await finish(h, 1)
  h.stop()
})
test('hidden page drops data and rejects late task results', async () => {
  const h = harness(); await finish(h)
  h.state.selectedProject.value = 'one'; await tick()
  h.document.hidden = true; await h.state.visibilityChanged()
  h.calls.tasks[0].resolve({ items: [{ detail: { task_id: 'hidden' } }] }); await tick()
  assert.equal(h.state.tasks.value, null)
  assert.equal(h.state.workItems.value, null)
  h.stop()
})
test('detail navigation checks task.read separately; waiting items only navigate to project task lists', () => {
  assert.match(source, /taskReadAllowed\(session, selectedProject, scope.row.detail.task_id\)/)
  assert.match(source, /scope.row.project_id && canReadTasks\(scope.row.project_id\)/)
  assert.match(source, /waitingTaskLocation\(scope.row.project_id, scope.row.waiting_item_id, scope.row.run_id\)/)
  assert.doesNotMatch(source, /scope.row.run_id \+ '\/config'|scope.row.waiting_item_id \+ '\/config'/)
})

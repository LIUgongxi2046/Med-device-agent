import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import { webRoot, id, session, tick } from '../n01-plan-preparation/helpers.mjs'
const require = createRequire(import.meta.url)
const vue = require('vue'), ts = require('typescript'), { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const filename = webRoot + '/src/views/medical/task-create.vue'
const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename })
const script = compileScript(descriptor, { id: 'task-create-navigation' })
const template = compileTemplate({ source: descriptor.template.content, filename, id: 'task-create-navigation', compilerOptions: { bindingMetadata: script.bindings } })
assert.deepEqual(template.errors, [])
const compile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
const code = compile(script.content), lifecycleCode = compile(readFileSync(webRoot + '/src/api/medical/route-read.ts', 'utf8'))
function setup() {
  const scope = vue.effectScope(), cleanups = [], leave = [], update = [], after = [], listeners = new Map(), prompts = [], writes = []
  const route = vue.reactive({ name: 'MedicalTaskCreate', fullPath: '/projects/' + id(10) + '/tasks/create', params: { projectId: id(10) } })
  const user = vue.reactive({ medicalSession: session(), medicalWriting: false })
  const doc = { hidden: false, addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name) }
  let discard = false
  const window = { ...doc, confirm: message => { prompts.push(message); return discard } }
  const router = { useRoute: () => route, onBeforeRouteLeave: fn => leave.push(fn), onBeforeRouteUpdate: fn => update.push(fn), useRouter: () => ({ afterEach: fn => { after.push(fn); return () => after.splice(after.indexOf(fn), 1) } }) }
  const vueApi = { ...vue, onBeforeUnmount: fn => cleanups.push(fn) }
  const lifecycle = { exports: {} }
  vm.runInNewContext(lifecycleCode, { exports: lifecycle.exports, require: name => name === 'vue' ? vueApi : router })
  const pending = { key: 'original-operation', project_id: id(10) }
  const saved = { detail: { task_id: id(15), task_revision: '1', snapshot_id: id(16), owner_id: id(1) } }
  let storedPending = null
  const modules = {
    'vue': vueApi, 'vue-router': router, '@/store/modules/user': { default: () => user },
    '@/api/medical/route-read': lifecycle.exports,
    '@/api/medical/protocol': { schemaIssues: () => [], fail: (_code, message) => { throw Error(message) } },
    '@/api/medical/project-write': { projectActionAllowed: s => !!s?.current_context, projectTextIssue: () => '' },
    '@/api/medical/projects': { readProject: async () => ({ status: 'ACTIVE', profile: { profile_revision: id(11) } }) },
    '@/api/medical/task-creation-selection': { listTaskCreationOwners: async () => ({ items: [{ user_id: id(1) }] }) },
    '@/api/medical/task-detail': { readCurrentTask: async () => saved },
    '@/api/medical/task-create': { originalTaskCreate: async () => saved, pendingTaskCreate: () => storedPending, clearTaskCreate: () => { writes.push('clear'); storedPending = null }, prepareTaskCreate: () => { writes.push('prepare'); return pending }, submitTaskCreate: () => writes.push('submit') }
  }
  const exports = {}
  vm.runInNewContext(code, { exports, require: name => modules[name] ?? {}, document: doc, window, AbortController, console })
  const state = scope.run(() => exports.default.setup({}, { expose() {} }))
  return { state, route, user, doc, prompts, listeners, writes, pending, saved,
    setPending() { storedPending = pending; state.pending.value = pending },
    discard(value) { discard = value },
    navigate(kind, target = '/elsewhere') {
      for (const guard of kind === 'update' ? update : leave) {
        if (guard() === false) { for (const done of after) done(); return false }
      }
      if (kind !== 'update') route.name = 'Other'
      route.fullPath = target
      for (const done of after) done()
      return true
    },
    stop() { for (const cleanup of cleanups) cleanup(); scope.stop() }
  }
}
test('clean mount and automatic owner default do not prompt or write', async () => {
  const x = setup(); await tick()
  assert.equal(x.state.ownerId.value, id(1)); assert.equal(x.state.unsaved.value, false)
  assert.equal(x.navigate('leave'), true); assert.equal(x.prompts.length, 0); assert.deepEqual(x.writes, []); x.stop()
})
for (const kind of ['leave', 'update', 'back']) test(`${kind}: keep retains input before route-read invalidates; discard clears`, async () => {
  const x = setup(); await tick(); x.state.goal.value = 'Private unsaved goal'; x.state.releaseId.value = id(20)
  const path = x.route.fullPath
  assert.equal(x.navigate(kind), false)
  assert.equal(x.route.fullPath, path); assert.equal(x.state.goal.value, 'Private unsaved goal'); assert.equal(x.state.releaseId.value, id(20)); assert.equal(x.state.active.value, true)
  x.discard(true); assert.equal(x.navigate(kind), true); assert.equal(x.state.goal.value, ''); assert.equal(x.state.releaseId.value, '')
  assert.deepEqual(x.writes, []); x.stop()
})
test('all editable values including reconstruction text are tracked, reset returns clean', async () => {
  const x = setup(); await tick()
  const edits = { documentType: 'OVERVIEW', selected: { template: { template_version: id(12) } }, ownerId: id(3), goal: 'goal', basedOn: id(4), mode: 'COMBINED', sourceVersions: [id(5)], collectionVersions: [id(6)], networkEnabled: true, networkPolicy: id(7), approvedQuery: id(8), reconstructionText: '{draft}' }
  for (const [key, value] of Object.entries(edits)) { const original = x.state[key].value; x.state[key].value = value; assert.equal(x.state.unsaved.value, true, key); x.state[key].value = original; assert.equal(x.state.unsaved.value, false, key) }
  x.stop()
})
test('beforeunload warns without mutating draft; listeners removed on unmount', async () => {
  const x = setup(); await tick(); let prevented = 0
  const event = { preventDefault() { prevented++ }, returnValue: null }
  x.listeners.get('beforeunload')(event); assert.equal(prevented, 0)
  x.state.goal.value = 'private'; x.listeners.get('beforeunload')(event)
  assert.equal(prevented, 1); assert.equal(event.returnValue, ''); assert.equal(x.state.goal.value, 'private')
  x.stop(); assert.equal(x.listeners.has('beforeunload'), false)
})
test('hidden, pagehide, offline, and context revocation clear drafts without asking', async () => {
  for (const reason of ['visibilitychange', 'pagehide', 'offline', 'context']) {
    const x = setup(); await tick(); x.state.goal.value = 'private'; x.state.reconstructionText.value = 'private JSON'
    if (reason === 'context') x.user.medicalSession = null
    else { if (reason === 'visibilitychange') x.doc.hidden = true; x.listeners.get(reason)() }
    assert.equal(x.state.goal.value, '', reason); assert.equal(x.state.reconstructionText.value, '', reason); assert.equal(x.state.unsaved.value, false, reason); assert.equal(x.prompts.length, 0); x.stop()
  }
})
test('pending write navigation warns but never clears original operation or creates/retries writes', async () => {
  const x = setup(); await tick(); x.setPending(); x.user.medicalWriting = true
  assert.equal(x.navigate('leave'), false); assert.equal(x.state.pending.value.key, x.pending.key)
  assert.match(x.prompts[0], /不会取消已发出的请求/)
  x.discard(true); assert.equal(x.navigate('update', '/projects/' + id(10) + '/tasks/create?return=1'), true)
  assert.deepEqual(x.writes, []); assert.equal(x.user.medicalWriting, true)
  x.user.medicalWriting = false; await x.state.loadProject(); assert.equal(x.state.pending.value.key, x.pending.key)
  x.stop()
})
test('successful confirmed save establishes clean baseline but further edits warn', async () => {
  const x = setup(); await tick(); x.state.goal.value = 'submitted goal'
  x.setPending()
  await x.state.finish(x.user.medicalSession, x.state.generation, x.pending, x.saved)
  assert.deepEqual(x.writes, ['clear']); assert.equal(x.state.pending.value, null)
  assert.equal(x.state.unsaved.value, false)
  x.state.goal.value = 'new change'; assert.equal(x.state.unsaved.value, true); x.stop()
})

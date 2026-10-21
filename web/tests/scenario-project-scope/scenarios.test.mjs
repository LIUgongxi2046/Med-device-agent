import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
const require = createRequire(import.meta.url), vue = require('vue'), ts = require('typescript')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const source = readFileSync(new URL('../../src/views/medical/scenarios.vue', import.meta.url), 'utf8')
function fixture(imageSimulation = undefined) {
  const user = vue.reactive({ medicalSession: { current_context: { tenant_id: 'tenant', auth_epoch: '1' }, action_summary: [{ action: 'configuration.read' }] }, medicalWriting: false })
  const calls = [], active = vue.ref(true), scope = vue.effectScope(), document = { hidden: false, addEventListener() {}, removeEventListener() {} }
  const { descriptor } = parse(source, { filename: 'scenarios.vue' })
  const script = compileScript(descriptor, { id: 'scenarios' })
  const code = ts.transpileModule(script.content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const exports = {}, dependencies = {
    vue: { ...vue, onMounted() {}, onBeforeUnmount() {} }, '@/store/modules/user': { default: () => user },
    '@/api/medical/protocol': { schemaIssues: () => [], fail: (_, message) => { throw new Error(message) } },
    '@/api/medical/scenarios': { listScenarios: (session, query, previous, signal) => new Promise((resolve, reject) => calls.push({ session, query, previous, signal, resolve, reject })) },
    '@/api/medical/tasks': { taskDocumentLabels: {}, taskIdReference: 'id' },
    '@/api/medical/route-read': { useMedicalRouteRead: () => active }
  }
  vm.runInNewContext(code.replaceAll('import.meta.env.VITE_MEDICAL_IMAGE_SIMULATION', imageSimulation === undefined ? 'undefined' : JSON.stringify(imageSimulation)), { exports, require: id => dependencies[id] ?? {}, AbortController, document, Error })
  const state = scope.run(() => exports.default.setup({}, { expose() {} }))
  return { state, calls, user, document, active, stop: () => scope.stop() }
}
const settle = async () => { await Promise.resolve(); await vue.nextTick(); await Promise.resolve() }
const page = (project = null, next = null) => ({ project_id: project, document_type: null, total: '2', items: [], planned_requirements: [], next_after: next, dataset_watermark: 'watermark', limit: 20 })
test('scenario page and shared project picker compile', () => {
  for (const [name, text] of [['scenarios', source], ['project', readFileSync(new URL('../../src/components/Medical/ProjectScopeSelect.vue', import.meta.url), 'utf8')]]) {
    const { descriptor, errors } = parse(text, { filename: name + '.vue' }); assert.deepEqual(errors, [])
    const script = compileScript(descriptor, { id: name })
    assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: name + '.vue', id: name, compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
  }
  assert.match(source, /<project-scope-select/); assert.match(source, /版本总数未知/)
  assert.doesNotMatch(source, /router\.push|task_entry\s*=/)
})
test('project changes abort old request and reject its late response', async () => {
  const f = fixture()
  try {
    const first = f.calls[0]
    f.state.projectId.value = 'project-b'
    assert.equal(first.signal.aborted, true); assert.equal(f.state.page.value, null)
    const searching = f.state.search(), second = f.calls[1]
    assert.equal(second.query.project_id, 'project-b')
    first.resolve(page('project-a')); await settle()
    assert.equal(f.state.page.value, null); assert.equal(f.state.busy.value, true)
    second.resolve(page('project-b')); await searching
    assert.equal(f.state.page.value.project_id, 'project-b')
  } finally { f.stop() }
})
test('document changes clear confirmed count until a new query succeeds', async () => {
  const f = fixture()
  try {
    f.calls[0].resolve(page()); await settle(); assert.equal(f.state.page.value.total, '2')
    f.state.documentType.value = 'TARGET'
    assert.equal(f.state.page.value, null)
    const search = f.state.search(), call = f.calls.at(-1)
    assert.equal(call.query.document_type, 'TARGET')
    call.reject(new Error('access revoked')); await search
    assert.equal(f.state.page.value, null); assert.equal(f.state.error.value, 'access revoked')
  } finally { f.stop() }
})
test('pagination carries original project, document, watermark and cursor', async () => {
  const f = fixture()
  try {
    f.state.projectId.value = 'project-b'; f.state.documentType.value = 'TARGET'
    const search = f.state.search(), initial = { ...page('project-b', 'cursor-1'), document_type: 'TARGET' }
    f.calls.at(-1).resolve(initial); await search
    const next = f.state.next(), call = f.calls.at(-1)
    assert.deepEqual(JSON.parse(JSON.stringify(call.query)), { limit: 20, project_id: 'project-b', document_type: 'TARGET', after: 'cursor-1', dataset_watermark: 'watermark' })
    assert.equal(call.previous.dataset_watermark, 'watermark')
    call.resolve({ ...initial, next_after: null }); await next
    assert.equal(f.state.page.value.next_after, null)
  } finally { f.stop() }
})
test('in-place authorization revocation synchronously clears data and ignores older response', async () => {
  const f = fixture()
  try {
    f.calls[0].resolve(page()); await settle(); assert.ok(f.state.page.value)
    const loading = f.state.search(), old = f.calls.at(-1), pickerKey = f.state.pickerGeneration.value
    f.user.medicalSession.action_summary.splice(0)
    assert.equal(f.state.page.value, null); assert.equal(old.signal.aborted, true)
    assert.ok(f.state.pickerGeneration.value > pickerKey)
    old.resolve(page()); await loading
    assert.equal(f.state.page.value, null)
    f.calls.at(-1).resolve(page()); await settle()
  } finally { f.stop() }
})
test('hidden and inactive page immediately discard confirmed catalogue', async () => {
  const f = fixture()
  try {
    f.calls[0].resolve(page()); await settle(); f.document.hidden = true
    await f.state.visibilityChanged(); assert.equal(f.state.page.value, null)
    f.document.hidden = false; f.active.value = false
    assert.equal(f.state.page.value, null); assert.equal(f.state.disabled.value, true)
  } finally { f.stop() }
})
test('ordinary catalogue filters and searches preserve planning workspace identity', async () => {
  const f = fixture()
  try {
    const planningGeneration = f.state.pickerGeneration.value
    assert.match(source, /<scenario-planning-workspace :key="pickerGeneration"/)
    f.state.projectId.value = 'project-b'; f.state.documentType.value = 'TARGET'
    assert.equal(f.state.pickerGeneration.value, planningGeneration)
    const searching = f.state.search()
    assert.equal(f.state.pickerGeneration.value, planningGeneration)
    f.calls.at(-1).resolve({ ...page('project-b'), document_type: 'TARGET' }); await searching
    assert.equal(f.state.pickerGeneration.value, planningGeneration)
    f.document.hidden = true; await f.state.visibilityChanged()
    assert.ok(f.state.pickerGeneration.value > planningGeneration)
  } finally { f.stop() }
})

test('native image simulation remains opt-in and is enabled only by exact true build flag', () => {
  for (const [flag, expected] of [[undefined, false], ['false', false], ['TRUE', false], ['true', true]]) {
    const f = fixture(flag); try { assert.equal(f.state.showImageSimulation, expected) } finally { f.stop() }
  }
})

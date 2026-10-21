import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import * as vue from 'vue'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { loadModule } from '../formal-capability/helpers.mjs'

// Mounted Vue lifecycle with explicit focus-capable host references, not browser visual acceptance.
const api = (await loadModule('document-field-verification')).api
const fixture = (await loadModule('../../../tests/field-verification-preview/fixture')).api
const filename = new URL('../../src/views/medical/document-field-verification.vue', import.meta.url).pathname
const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename })
const script = compileScript(descriptor, { id: 'navigation-focus' })
const code = ts.transpileModule(script.content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
function mount() {
  const exported = {}, events = [], focus = []
  vm.runInNewContext(code, { exports: exported, require: key => key === 'vue' ? vue : api })
  const props = vue.reactive({ body: structuredClone(fixture.body), canOpenEvidence: true,
    navigationContext: { projectId: 'project', taskId: 'task', canReadMapping: true, canReadSourceExact: false } })
  let state
  const renderer = vue.createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode() {}, nextSibling() {} })
  const app = renderer.createApp({ setup() { state = exported.default.setup(props, { expose() {}, emit: (...args) => events.push(args) }); return () => null } })
  app.mount({})
  const trigger = { isConnected: true, disabled: false, hasAttribute(name) { return name === 'disabled' && this.disabled }, focus() { focus.push('trigger') } }
  state.navigationConfirmation.value = { focus() { focus.push('confirmation') } }
  return { state, props, trigger, events, focus, close: () => app.unmount() }
}
async function open(h) {
  h.state.beginDraft()
  await vue.nextTick()
  h.state.draft.value.reason = '保留这份本地意见'
  h.state.requestNavigation({ kind: 'mapping' }, { currentTarget: h.trigger })
  await vue.nextTick()
}
test('actual confirmation binds keyboard cancellation and moves focus before explicit discard', async () => {
  const template = compileTemplate({ source: descriptor.template.content, filename, id: 'navigation-focus', compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
  assert.match(descriptor.template.content, /ref="navigationConfirmation" tabindex="-1" @keydown\.esc\.prevent\.stop="cancelNavigation"/)
  assert.match(descriptor.template.content, /@click="cancelNavigation">保留意见/)
  assert.doesNotMatch(descriptor.template.content, /aria-modal/)
  const h = mount()
  try {
    await open(h)
    assert.deepEqual(h.focus, ['confirmation'])
    assert.equal(h.events.length, 0)
    h.state.cancelNavigation(); await vue.nextTick()
    assert.deepEqual(h.focus, ['confirmation', 'trigger'])
    assert.equal(h.state.pendingNavigation.value, null)
    assert.equal(h.state.draft.value.reason, '保留这份本地意见')
    assert.equal(h.state.draftOpen.value, true)
    assert.equal(h.events.length, 0)
  } finally { h.close() }
})
test('explicit discard clears draft and emits only the rechecked destination', async () => {
  const h = mount()
  try {
    await open(h); h.state.confirmNavigation(); await vue.nextTick()
    assert.equal(h.events.length, 1)
    assert.equal(h.events[0][0], 'open-field-targets')
    assert.equal(h.state.draft.value.reason, '')
    assert.equal(h.state.pendingNavigation.value, null)
    assert.deepEqual(h.focus, ['confirmation'])
  } finally { h.close() }
})
test('scope changes cancel queued focus and stale confirmations without navigating', async () => {
  for (const change of [h => h.props.navigationContext.canReadMapping = false, h => h.props.navigationContext.taskId = 'other', h => h.props.body = structuredClone(fixture.body), h => h.close()]) {
    const h = mount()
    try {
      h.state.beginDraft(); await vue.nextTick(); h.state.draft.value.reason = '保留'
      h.state.requestNavigation({ kind: 'mapping' }, { currentTarget: h.trigger })
      change(h); await vue.nextTick(); h.state.confirmNavigation()
      assert.deepEqual(h.focus, [])
      assert.equal(h.events.length, 0)
      assert.equal(h.state.pendingNavigation.value, null)
    } finally { h.close() }
  }
})
test('cancel never focuses a disconnected, disabled, or now-invalidated origin', async () => {
  for (const change of [h => h.trigger.isConnected = false, h => h.trigger.disabled = true, h => h.props.navigationContext.taskId = 'other', h => h.close()]) {
    const h = mount()
    try {
      await open(h); h.state.cancelNavigation(); change(h); await vue.nextTick()
      assert.deepEqual(h.focus, ['confirmation'])
      assert.equal(h.events.length, 0)
    } finally { h.close() }
  }
})

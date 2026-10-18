import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
const require = createRequire(import.meta.url)
const vue = require('vue'), ts = require('typescript')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
function setup(reader) {
  const filename = new URL('../../src/views/medical/source-original-reader.vue', import.meta.url)
  const source = readFileSync(filename, 'utf8')
  const { descriptor } = parse(source, { filename: filename.pathname })
  const compiled = compileScript(descriptor, { id: 'reader' })
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: filename.pathname, id: 'reader', compilerOptions: { bindingMetadata: compiled.bindings } }).errors, [])
  const code = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const exports = {}, scope = vue.effectScope(), doc = { hidden: false }
  const props = vue.reactive({ session: {}, view: { source_version: 'one', original_sha256: 'a' }, review: { parse_version: 'p' }, active: true })
  vm.runInNewContext(code, { exports, require: id => id === 'vue' ? { ...vue, onBeforeUnmount() {} } : id.endsWith('source-governance') ? { readCompleteSourceOriginal: reader } : {}, AbortController, document: doc, URL })
  const state = scope.run(() => exports.default.setup(props, { expose() {} }))
  return { state, props, doc, stop: () => scope.stop() }
}
test('original read failure is visible without leaking server details and can be retried', async () => {
  let calls = 0
  const result = { verified: true }
  const instance = setup(async () => { if (++calls === 1) throw Error('private server detail'); return result })
  try {
    await instance.state.read()
    assert.ok(instance.state.notice.value)
    assert.doesNotMatch(instance.state.notice.value, /private server/)
    assert.equal(instance.state.blob.value, null)
    assert.equal(instance.state.loading.value, false)
    await instance.state.read()
    assert.equal(instance.state.notice.value, '')
    assert.equal(instance.state.blob.value, result)
  } finally { instance.stop() }
})
test('cancel or changed source discards late original content and errors', async () => {
  let resolve, reject
  const instance = setup(() => new Promise((a,b) => { resolve = a; reject = b }))
  try {
    const first = instance.state.read(); instance.state.clear(); resolve({ stale: true }); await first
    assert.equal(instance.state.blob.value, null)
    const second = instance.state.read(); instance.props.view.source_version = 'two'; reject(Error('stale')); await second
    assert.equal(instance.state.notice.value, '')
    assert.equal(instance.state.blob.value, null)
    assert.equal(instance.state.loading.value, false)
  } finally { instance.stop() }
})
test('inactive or hidden readers do not issue an original read', async () => {
  let calls = 0; const instance = setup(async () => { calls++; return {} })
  try {
    instance.props.active = false; await instance.state.read()
    instance.props.active = true; instance.doc.hidden = true; await instance.state.read()
    assert.equal(calls, 0)
  } finally { instance.stop() }
})

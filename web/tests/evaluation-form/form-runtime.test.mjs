import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
const require = createRequire(import.meta.url)
const vue = require('vue'), ts = require('typescript')
const { parse, compileScript } = require('@vue/compiler-sfc')
function setup(name, props, extra = {}) {
  const source = readFileSync(new URL('../../src/views/medical/' + name + '.vue', import.meta.url), 'utf8')
  const { descriptor } = parse(source, { filename: name + '.vue' })
  const compiled = compileScript(descriptor, { id: name }).content
  const code = ts.transpileModule(compiled, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const exports = {}, events = [], scope = vue.effectScope()
  vm.runInNewContext(code, { exports, require: id => id === 'vue' ? { ...vue, onBeforeUnmount() {} } : extra[id] ?? {}, console, AbortController, document: { hidden: false, addEventListener() {}, removeEventListener() {} } })
  let exposed
  const state = scope.run(() => exports.default.setup(props, { emit: (...args) => events.push(args), expose: value => { exposed = value } }))
  return { state, events, exposed, stop: () => scope.stop() }
}
const plain = value => JSON.parse(JSON.stringify(value))
test('structured form emits an exact detached request without manufacturing fixed references', () => {
  const instance = setup('evaluation-definition-form', { disabled: false })
  try {
    const { state, events } = instance
    const reference = { version_id: '00000000-0000-4000-8000-000000000001', content_sha256: 'a'.repeat(64) }
    state.form.dataset = { ...reference }; state.form.sample_manifest_artifact = { ...reference }
    state.form.rule_revision = { ...reference }; state.form.authorized_use_evidence = { ...reference }
    state.form.candidates = [{ ...reference }]; state.form.required_dimensions = ['EVAL-11']
    state.project.value = '00000000-0000-4000-8000-000000000002'; state.confidence.value = 'existing-method'
    state.addSample(); const sample = state.form.samples[0]
    Object.assign(sample, { sample_id: reference.version_id, source_family: reference.version_id, document_type: 'existing-type', input_ref: { ...reference }, expected_ref: { ...reference }, expectations: [{ dimension: 'EVAL-11', applicable_item_ids: ['first', 'second'], control_item_ids: ['control'] }] })
    state.preview()
    const request = events.filter(item => item[0] === 'preview').at(-1)[1]
    assert.deepEqual(plain(request), { ...plain(state.form), project_id: state.project.value, variance_policy: { ...plain(state.form.variance_policy), confidence_interval_method: 'existing-method' } })
    sample.expectations[0].applicable_item_ids.push('third')
    assert.deepEqual(plain(request.samples[0].expectations[0].applicable_item_ids), ['first', 'second'])
    assert.equal(request.samples[0].expected_ref.content_sha256, 'a'.repeat(64))
    assert.equal(request.sample_manifest_artifact.content_sha256, 'a'.repeat(64))
  } finally { instance.stop() }
})
test('sample, candidate and dimension edits synchronously invalidate parent preview', () => {
  const instance = setup('evaluation-definition-form', { disabled: false })
  try {
    assert.equal(instance.exposed.dirty(), false)
    for (const update of [() => instance.state.addSample(), () => instance.state.form.candidates.push(instance.state.basis()), () => instance.state.form.required_dimensions.push('EVAL-01'), () => instance.state.form.samples.splice(0, 1), () => instance.state.form.candidates.splice(0, 1)]) {
      const before = instance.events.length; update()
      assert.ok(instance.events.length > before)
      assert.equal(instance.events.at(-1)[0], 'change')
      assert.equal(instance.exposed.dirty(), true)
    }
  } finally { instance.stop() }
})
test('disabled form cannot emit a new preview; blank optional fields remain null', () => {
  const props = { disabled: true }, instance = setup('evaluation-definition-form', props)
  try {
    instance.state.preview(); assert.equal(instance.events.length, 0)
    props.disabled = false; instance.state.preview()
    const request = instance.events.at(-1)[1]
    assert.equal(request.project_id, null)
    assert.equal(request.variance_policy.confidence_interval_method, null)
    assert.equal(request.samples.length, 0)
    assert.equal(request.candidates[0].version_id, '')
    assert.deepEqual(plain(instance.state.lines('one\ntwo')), ['one', 'two'])
    assert.deepEqual(plain(instance.state.lines('')), [])
  } finally { instance.stop() }
})
test('invalid advanced JSON cannot retain a previously attested preview', () => {
  const source = readFileSync(new URL('../../src/views/medical/evaluation-results.vue', import.meta.url), 'utf8')
  const functionBody = source.match(/async function previewCreate\(\): Promise<void> \{([\s\S]*?)\n\}/)[1]
  const code = ts.transpileModule('async function run() {' + functionBody + '\n}; run()', { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  const state = { preview: { existing: true }, attested: true }, error = { value: '' }
  return vm.runInNewContext(code, { busy: { value: false }, document: { hidden: false }, definitionText: { value: 'invalid' }, error,
    evaluationJsonInputIssue: () => '', invalidatePreview() { state.preview = null; state.attested = false }, parseEvaluationJson() { throw new Error('invalid JSON') },
    previewValue() { throw new Error('must not validate malformed JSON') }, evaluationFailureMessage: value => value.message
  }).then(() => { assert.equal(state.preview, null); assert.equal(state.attested, false); assert.equal(error.value, 'invalid JSON') })
})

test('reference picker drops in-flight catalogue data after a project scope change', async () => {
  let resolveRead
  const pending = new Promise(resolve => { resolveRead = resolve })
  const session = { current_context: { tenant_id: 'tenant' } }, user = vue.reactive({ medicalSession: session })
  const props = vue.reactive({ modelValue: { version_id: '', content_sha256: '' }, label: 'input', kind: 'artifact', project: 'first', disabled: false })
  const instance = setup('evaluation-basis-field', props, {
    '@/store/modules/user': { default: () => user },
    '@/api/medical/source-governance': { readArtifactCatalogue: () => pending }
  })
  try {
    const loading = instance.state.load()
    assert.equal(instance.state.loading.value, true)
    props.project = 'second'; await vue.nextTick()
    resolveRead({ items: [{ artifact_id: 'old', sha256: 'a'.repeat(64), artifact_kind: 'OLD' }], next_after: null })
    await loading
    assert.equal(instance.state.options.value.length, 0)
    assert.equal(instance.state.loading.value, false)
    assert.equal(instance.state.cursor.value, null)
    assert.equal(instance.events.length, 0)
  } finally { instance.stop() }
})

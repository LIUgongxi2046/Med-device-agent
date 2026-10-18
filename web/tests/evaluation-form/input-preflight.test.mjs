import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { createHash } from 'node:crypto'
import vm from 'node:vm'
const require = createRequire(import.meta.url), vue = require('vue'), ts = require('typescript')
const { parse, compileScript } = require('@vue/compiler-sfc')
const Ajv = require('ajv/dist/2020').default, addFormats = require('ajv-formats').default
const { canonicalizeEx } = require('json-canonicalize')
const schema = JSON.parse(readFileSync(new URL('../../../business/medical-persistence/src/main/resources/evaluation-results.schema.json', import.meta.url), 'utf8'))
const ajv = new Ajv({ strictSchema: true, strictTypes: false, allErrors: true }); addFormats(ajv); ajv.addSchema(schema)
const hash = value => createHash('sha256').update(canonicalizeEx(value, { strictUndefined: true })).digest('hex')
const formSource = readFileSync(new URL('../../src/views/medical/evaluation-results.vue', import.meta.url), 'utf8')
function fixture(componentName = 'evaluation-results', props = {}) {
  const user = vue.reactive({ medicalSession: { user: { user_id: 'actor' }, current_context: { tenant_id: 'tenant' }, action_summary: [] }, medicalWriting: false })
  const calls = { failures: 0, validate: 0, writes: 0 }, scope = vue.effectScope(), modules = new Map()
  const protocol = {
    schemaIssues(reference, value) { const check = ajv.getSchema(reference); assert.ok(check, reference); return check(value) ? [] : check.errors.map(item => ({ path: item.instancePath, code: item.keyword })) },
    validate(reference, value) { if (this.schemaIssues(reference, value).length) this.fail('SCHEMA_INVALID', 'invalid schema'); return value },
    fail(code, message) { calls.failures++; user.medicalSession = null; throw Object.assign(new Error(message), { code }) },
    revision: value => value, validContext() {}
  }
  protocol.validate = protocol.validate.bind(protocol)
  function execute(source, imports) {
    const exports = {}
    const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
    vm.runInNewContext(code, { exports, require: imports, Error, AbortController, window: { addEventListener() {}, removeEventListener() {} }, document: { hidden: false, addEventListener() {}, removeEventListener() {} } })
    return exports
  }
  function api(name) {
    if (modules.has(name)) return modules.get(name)
    const source = readFileSync(new URL('../../src/api/medical/' + name + '.ts', import.meta.url), 'utf8')
    const exports = execute(source, id => {
      if (id === './protocol') return protocol
      if (id === './content-digest') return { sourceDigest: async value => hash(value) }
      if (id.startsWith('./')) return api(id.slice(2))
      return require(id)
    }); modules.set(name, exports); return exports
  }
  const domain = api('evaluation-domain')
  const input = execute(readFileSync(new URL('../../src/views/medical/evaluation-input.ts', import.meta.url), 'utf8'), id => id === '@/api/medical/protocol' ? protocol : api(id.split('/').at(-1)))
  const imports = {
    vue: { ...vue, onBeforeUnmount() {} }, 'vue-router': { useRoute: () => ({ params: {}, meta: { evaluationCreate: true }, fullPath: '/evaluation/new' }), useRouter: () => ({ push() {} }), onBeforeRouteLeave() {}, onBeforeRouteUpdate() {} },
    '@/store/modules/user': { default: () => user }, '@/api/medical/protocol': protocol, './evaluation-input': input,
    '@/api/medical/evaluation-domain': { ...domain, evaluationAllowed: () => true, readPendingEvaluation: () => null, validateEvaluationCreate: async value => { calls.validate++; return domain.validateEvaluationCreate(value) }, validateEvaluationScoreTarget: (...args) => { calls.validate++; return domain.validateEvaluationScoreTarget(...args) } },
    '@/api/medical/evaluations': { submitEvaluation() { calls.writes++ } }
  }
  const source = componentName === 'evaluation-results' ? formSource : readFileSync(new URL('../../src/views/medical/' + componentName + '.vue', import.meta.url), 'utf8')
  const { descriptor } = parse(source, { filename: componentName + '.vue' })
  const component = execute(compileScript(descriptor, { id: 'evaluation-results' }).content, id => imports[id] ?? {})
  const emitted = []
  const state = scope.run(() => component.default.setup(props, { expose() {}, emit: (...args) => emitted.push(args) }))
  return { state, user, calls, emitted, stop: () => scope.stop() }
}
const valid = () => {
  const basis = { version_id: '00000000-0000-4000-8000-000000000001', content_sha256: 'a'.repeat(64) }
  return { schema_version: '1.0.0', project_id: null, dataset: { ...basis }, sample_manifest_artifact: { ...basis, content_sha256: hash([]) }, samples: [], candidates: [{ ...basis }], rule_revision: { ...basis }, required_dimensions: ['EVAL-01'], variance_policy: { repeat_count: 1, policy: 'ALL_REQUIRED_RUNS', confidence_interval_method: null }, authorized_use_evidence: { ...basis }, environment: 'SIMULATION', reason: '登记已有评测资料' }
}
test('missing structured fields show local error without medical-failure, logout or write', async () => {
  const f = fixture()
  try {
    const session = f.user.medicalSession
    await f.state.previewForm({ schema_version: '1.0.0' })
    assert.ok(f.state.error.value); assert.equal(f.state.createPreview.value, null)
    assert.equal(f.user.medicalSession, session); assert.deepEqual(f.calls, { failures: 0, validate: 0, writes: 0 })
  } finally { f.stop() }
})
test('malformed, duplicate-key and unsafe-number JSON remain local input errors', async () => {
  for (const text of ['{', '{"schema_version":"1.0.0","schema_version":"1.0.0"}', '{"count":9007199254740993}']) {
    const f = fixture()
    try {
      f.state.definitionText.value = text; await vue.nextTick(); await f.state.previewCreate()
      assert.ok(f.state.error.value); assert.ok(f.user.medicalSession)
      assert.deepEqual(f.calls, { failures: 0, validate: 0, writes: 0 })
    } finally { f.stop() }
  }
})
test('invalid UUID and duplicate candidate identities do not invoke fail-fast validator', async () => {
  for (const body of [{ ...valid(), project_id: 'invalid-id' }, (() => { const body = valid(); body.candidates.push({ ...body.candidates[0], content_sha256: 'b'.repeat(64) }); return body })()]) {
    const f = fixture()
    try { await f.state.previewForm(body); assert.ok(f.state.error.value); assert.ok(f.user.medicalSession); assert.deepEqual(f.calls, { failures: 0, validate: 0, writes: 0 }) }
    finally { f.stop() }
  }
})
test('valid structured and advanced JSON inputs still run original full validator', async () => {
  for (const advanced of [false, true]) {
    const f = fixture(), body = valid()
    try {
      if (advanced) { f.state.definitionText.value = JSON.stringify(body); await vue.nextTick(); await f.state.previewCreate() }
      else await f.state.previewForm(body)
      assert.equal(f.state.error.value, ''); assert.deepEqual(JSON.parse(JSON.stringify(f.state.createPreview.value)), body)
      assert.ok(f.user.medicalSession); assert.deepEqual(f.calls, { failures: 0, validate: 1, writes: 0 })
    } finally { f.stop() }
  }
})
test('fixed manifest hash mismatch still reaches original integrity validation', async () => {
  const f = fixture(), body = valid(); body.sample_manifest_artifact.content_sha256 = 'b'.repeat(64)
  try { await f.state.previewForm(body); assert.equal(f.calls.validate, 1); assert.equal(f.calls.failures, 1); assert.equal(f.calls.writes, 0); assert.equal(f.state.createPreview.value, null) }
  finally { f.stop() }
})

const scoreView = () => {
  const request = valid(), basis = request.dataset
  request.samples = [{ sample_id: basis.version_id, source_family: basis.version_id, split: 'DEVELOPMENT', document_type: 'existing', evidence_class: 'SIMULATION', input_ref: basis, expected_ref: basis, expectations: [{ dimension: 'EVAL-01', applicable_item_ids: ['item'], control_item_ids: [] }] }]
  return { content_sha256: 'c'.repeat(64), revision: '1', status: 'QUEUED', result: null, score_history: [], definition: { request, candidate_snapshots: [{ ...basis, created_by: 'another-actor' }] } }
}
async function selectCell(f, view) {
  f.state.sampleId.value = view.definition.request.samples[0].sample_id
  f.state.candidateId.value = view.definition.request.candidates[0].version_id
  f.state.dimension.value = 'EVAL-01'; await vue.nextTick()
  f.state.attested.value = true
}
test('incomplete score input remains local and never calls target validation or emits submit', async () => {
  const view = scoreView(), f = fixture('evaluation-score-editor', { view, actor: 'actor', disabled: false })
  try {
    await selectCell(f, view); f.state.reason.value = ''
    f.state.submit()
    assert.ok(f.state.error.value); assert.ok(f.user.medicalSession)
    assert.deepEqual(f.calls, { failures: 0, validate: 0, writes: 0 }); assert.equal(f.emitted.length, 0)
  } finally { f.stop() }
})
test('valid score still uses original target validation before emitting submit', async () => {
  const view = scoreView(), f = fixture('evaluation-score-editor', { view, actor: 'actor', disabled: false })
  try {
    await selectCell(f, view); f.state.reason.value = '记录未知观察'
    f.state.submit()
    assert.equal(f.state.error.value, ''); assert.ok(f.user.medicalSession)
    assert.deepEqual(f.calls, { failures: 0, validate: 1, writes: 0 }); assert.equal(f.emitted[0][0], 'submit')
    assert.equal(f.emitted[0][1].status, 'UNKNOWN'); assert.equal(f.emitted[0][1].numerator, null)
  } finally { f.stop() }
})
test('score numerator exceeding fixed denominator stays local', async () => {
  const view = scoreView(), f = fixture('evaluation-score-editor', { view, actor: 'actor', disabled: false })
  try {
    await selectCell(f, view); f.state.reason.value = '记录实际观察'; f.state.status.value = 'SCORED'; f.state.numerator.value = '2'
    f.state.outputBasis.value = { ...view.definition.request.dataset }
    f.state.evidence.value = [{ ...view.definition.request.dataset, localKey: 1 }]
    f.state.submit()
    assert.ok(f.state.error.value); assert.ok(f.user.medicalSession)
    assert.deepEqual(f.calls, { failures: 0, validate: 0, writes: 0 }); assert.equal(f.emitted.length, 0)
  } finally { f.stop() }
})

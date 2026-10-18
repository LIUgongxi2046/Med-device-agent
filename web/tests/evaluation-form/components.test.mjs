import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const read = name => readFileSync(new URL('../../src/views/medical/' + name + '.vue', import.meta.url), 'utf8')
for (const name of ['evaluation-results', 'evaluation-definition', 'evaluation-definition-form', 'evaluation-basis-field', 'evaluation-insights', 'evaluation-score-editor']) test(`S15 component compiles: ${name}`, () => {
  const filename = name + '.vue', parsed = parse(read(name), { filename })
  assert.deepEqual(parsed.errors, [])
  const script = compileScript(parsed.descriptor, { id: name })
  const template = compileTemplate({ source: parsed.descriptor.template.content, filename, id: name, compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
})
test('structured form remains primary; both paths use existing validator', () => {
  const source = read('evaluation-results')
  assert.ok(source.indexOf('<evaluation-definition-form') < source.indexOf('evaluation-create-json'))
  assert.match(source, /validateEvaluationCreate\(input\)/)
  assert.match(source, /definitionForm\.value\?\.dirty\(\)/)
  assert.match(source, /@change="invalidatePreview"/)
})
test('form never synthesizes artifact identities, hashes, or approvals', () => {
  const source = read('evaluation-definition-form')
  assert.doesNotMatch(source, /randomUUID|sourceDigest|evaluationDigest|approved_expectation_samples/)
  assert.match(source, /form\.sample_manifest_artifact/)
  assert.match(source, /sample\.expected_ref/)
  assert.match(source, /matrixSize > 10000/)
})
test('comparison uses verified fixed result cells and shared sample filters', () => {
  const source = read('evaluation-insights')
  assert.match(source, /props\.view\.result\?\.cells\.filter/)
  assert.match(source, /sampleIds\.value\.has\(cell\.sample_id\)/)
  assert.match(source, /effectiveEvaluationScores/)
  assert.match(source, /尚无固定结果/)
  assert.match(source, /当前接口未提供可核验数据/)
})
test('reference catalogues retain read boundaries and clear on context changes', () => {
  const source = read('evaluation-basis-field')
  assert.match(source, /listScenarios/)
  assert.match(source, /readArtifactCatalogue/)
  assert.match(source, /user\.medicalSession !== session/)
  assert.match(source, /props\.project, props\.kind/)
  assert.match(source, /document\.hidden/)
  assert.doesNotMatch(source, /request\.post|submitEvaluation|production_eligible:\s*true/)
})

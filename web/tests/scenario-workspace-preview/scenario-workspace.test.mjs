import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
const require = createRequire(import.meta.url), vue = require('vue'), ts = require('typescript')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const source = readFileSync(new URL('../../src/views/medical/ScenarioPlanningWorkspace.vue', import.meta.url), 'utf8')
const helper = readFileSync(new URL('../../src/views/medical/scenario-planning.ts', import.meta.url), 'utf8')
const transpile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
const helperExports = {}; vm.runInNewContext(transpile(helper), { exports: helperExports })
function fixture() {
  const props = vue.reactive({ disabled: false, preview: false }), scope = vue.effectScope()
  const { descriptor } = parse(source, { filename: 'ScenarioPlanningWorkspace.vue' })
  const script = compileScript(descriptor, { id: 'scenario-planning' }), exports = {}
  vm.runInNewContext(transpile(script.content), { exports, require: id => id === 'vue' ? vue : helperExports })
  const state = scope.run(() => exports.default.setup(props, { expose() {} }))
  return { props, state, stop: () => scope.stop() }
}
test('actual workspace SFC and template compile using installed Vue', () => {
  const { descriptor, errors } = parse(source); assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'scenario-planning' })
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: 'ScenarioPlanningWorkspace.vue', id: 'scenario-planning', compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
})
test('planning catalogue preserves exact five PRD scenario names without business versions', () => {
  assert.deepEqual(Array.from(helperExports.scenarioPlanning, item => item.name), ['法规版本比较与变化解读', '报告生成', '翻译', '市场分析', '多模态问答'])
  assert.equal(helperExports.planningFields.length, 8)
  assert.doesNotMatch(source + helper, /fetch\(|request\.|localStorage|sessionStorage|task_id:|task_entry\s*=/)
  assert.match(source, /disabled>创建正式任务（未开放）/)
})
test('all five flows open empty, validate local inputs, preview, edit and keep independent drafts', () => {
  const f = fixture()
  try {
    for (const item of helperExports.scenarioPlanning) {
      f.state.selectScenario(item.id)
      assert.equal(f.state.selected.value.name, item.name); assert.equal(f.state.step.value, 0)
      assert.equal(f.state.draft.value.title, '')
      f.state.step.value = 1; f.state.previewDraft()
      assert.equal(f.state.step.value, 1); assert.equal(f.state.errors.value.length, 2)
      f.state.draft.value.title = item.name + '需求'; f.state.draft.value.notes.inputs = '待确认输入范围'
      f.state.previewDraft(); assert.equal(f.state.step.value, 2); assert.equal(f.state.errors.value.length, 0)
      assert.equal(f.state.draft.value.notes.review, '')
    }
    f.state.selectScenario('REGULATION_COMPARISON')
    assert.equal(f.state.draft.value.title, '法规版本比较与变化解读需求')
    f.state.clearDraft(); assert.equal(f.state.draft.value.title, ''); assert.equal(f.state.step.value, 1)
  } finally { f.stop() }
})
test('access change synchronously clears every local draft and blocks selection and preview', () => {
  const f = fixture()
  try {
    f.state.selectScenario('TRANSLATION'); f.state.draft.value.title = 'private notes'
    f.props.disabled = true
    assert.equal(f.state.selected.value, null); assert.equal(Object.keys(f.state.drafts).length, 0)
    f.state.selectScenario('TRANSLATION'); f.state.previewDraft(); assert.equal(f.state.selected.value, null)
    f.props.disabled = false; f.state.selectScenario('TRANSLATION'); assert.equal(f.state.draft.value.title, '')
  } finally { f.stop() }
})
test('local validation rejects whitespace and excessive content, never marks domain approval', () => {
  const draft = helperExports.emptyPlanningDraft(); draft.title = ' '; draft.notes.inputs = ' '
  assert.equal(helperExports.validatePlanningDraft(draft).length, 2)
  draft.title = 'x'.repeat(81); draft.notes.inputs = 'x'.repeat(1001)
  assert.equal(helperExports.validatePlanningDraft(draft).length, 2)
  assert.equal('approved' in draft, false)
})

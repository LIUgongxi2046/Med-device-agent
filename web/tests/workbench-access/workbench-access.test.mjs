import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const ts = require('typescript')
const source = path => readFileSync(new URL('../../src/' + path, import.meta.url), 'utf8')
const helper = ts.transpileModule(source('utils/medical-workbench.ts'), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const { recentWorkbenchProjects, workbenchCount } = await import('data:text/javascript;base64,' + Buffer.from(helper).toString('base64'))
for (const component of ['views/index.vue', 'views/medical/access-denied.vue']) test('compiles ' + component, () => {
  const parsed = parse(source(component), { filename: component })
  assert.deepEqual(parsed.errors, [])
  const script = compileScript(parsed.descriptor, { id: component })
  const template = compileTemplate({ source: parsed.descriptor.template.content, filename: component, id: component, compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
})
test('project preview is bounded, newest first, and does not mutate the source', () => {
  const projects = Array.from({ length: 8 }, (_, i) => ({ project_id: String(i), updated_at: `2026-10-${10 + i}T10:00:00Z` }))
  assert.deepEqual(recentWorkbenchProjects(projects).map(p => p.project_id), ['7', '6', '5', '4', '3', '2'])
  assert.equal(projects[0].project_id, '0')
})
test('null child counts never become a fabricated zero or partial total', () => {
  assert.equal(workbenchCount([{ children: { tasks: '2' } }, { children: { tasks: null } }], 'tasks'), '部分不可读取')
  assert.equal(workbenchCount([{ children: { tasks: '9007199254740993' } }, { children: { tasks: '2' } }], 'tasks'), '9007199254740995')
  assert.equal(workbenchCount([], 'tasks'), '0')
})
test('queue is current-assignee scoped and task preview is one selected bounded project', () => {
  const view = source('views/index.vue')
  assert.match(view, /assignee_id: actual.user.user_id/)
  assert.match(view, /status: 'WAITING_INPUT'/)
  assert.match(view, /workItems.next_after/)
  assert.match(view, /page: 1, page_size: 5/)
  assert.match(view, /taskController === reading/)
  assert.match(view, /session.value === actual/)
  assert.doesNotMatch(view, /readProject\(|todayItems/)
  assert.match(view, /v-else-if="errors.projects"/)
  assert.match(view, /v-else-if="errors.items"/)
})
test('access denial has safe recovery only, no resource preload, auto redirect or persistence deletion', () => {
  const view = source('views/medical/access-denied.vue')
  assert.match(view, /user.readMedical\(\)/)
  assert.match(view, /user.selectMedical\(target.value\)/)
  assert.match(view, /to="\/index"/)
  assert.match(view, /to="\/access"/)
  assert.doesNotMatch(view, /useRoute|onMounted|localStorage|sessionStorage|removeItem|router\.(push|replace)|readProject|medicalError\.message/)
  assert.match(view, /user.medicalPendingSelection/)
})

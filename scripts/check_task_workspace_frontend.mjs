import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'
const require = createRequire(new URL('../web/package.json', import.meta.url))
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: model } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/task-workspace.ts', import.meta.url)), { root, mode: 'medical' })
assert.equal(model.taskWorkspaceStage('/projects/p/tasks/t/config'), 'config')
assert.equal(model.taskWorkspaceStage('/projects/p/tasks/t/documents/d/write'), 'write')
assert.equal(model.taskWorkspaceStage('/projects/p/tasks/t/documents/d/review/'), 'review')
assert.equal(model.authorizedWorkspaceDocument(['authorized'], undefined), 'authorized')
assert.equal(model.authorizedWorkspaceDocument(['authorized'], 'authorized'), 'authorized')
assert.equal(model.authorizedWorkspaceDocument(['authorized'], 'foreign'), null)
assert.equal(model.authorizedWorkspaceDocument(['authorized'], ['authorized']), null)
assert.equal(model.authorizedWorkspaceDocument([], undefined), null)
assert.equal(model.workspaceDocumentPath('p', 't', 'd', 'review'), '/projects/p/tasks/t/documents/d/review')
assert.equal(model.workspaceDocumentPath('p', 't', 'd/foreign', 'write'), '/projects/p/tasks/t/documents/d%2Fforeign/write')
for (const name of ['task-detail', 'document-editor']) {
  const source = await readFile(new URL(`../web/src/views/medical/${name}.vue`, import.meta.url), 'utf8')
  const { descriptor, errors } = parse(source, { filename: `${name}.vue` })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: name })
  const compiled = compileTemplate({ id: name, filename: `${name}.vue`, source: descriptor.template.content, compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(compiled.errors, [])
  if (name === 'task-detail') {
    assert.match(source, /useMedicalRouteRead\(\['MedicalTaskDetail', 'MedicalTaskWrite', 'MedicalTaskReview'\]/)
    assert.match(source, /authorizedWorkspaceDocument\(current.value\?\.detail.document_ids/)
    assert.match(source, /:review-mode="stage === 'review'"/)
    assert.match(source, /:key="selectedDocument \+ ':' \+ stage"/)
    assert.match(source, /签发操作尚未接入本工作区/)
  } else {
    assert.match(source, /const editable = computed\(\(\) => !props.reviewMode && props.active/)
    assert.match(source, /if \(props.reviewMode\) \{ await load\(\); return \}/)
    assert.ok(source.indexOf('if (props.reviewMode) { await load(); return }') < source.indexOf('const value = resumeDocumentDraft(key)'))
    assert.match(source, /if \(props.reviewMode \|\| ownedKey === null/)
    // The editor now shares one reader between write and review. Test its actual
    // editable guard rather than requiring the obsolete separate review element.
    assert.match(source, /<document-body-reader [^>]*:dirty="hasUnsaved"[^>]*:editable="editable"/)
    assert.match(source, /function replace\(value: DocumentBody\): void \{\s*if \(!editable.value \|\| draft.value === null\) fail\('ACTION_STALE'/)
    const editableExpression = source.match(/const editable = computed\(\(\) => (.+)\)/)?.[1]
    assert.ok(editableExpression)
    const context = { props: { reviewMode: true, active: true }, loading: { value: false }, annotationBusy: { value: false }, user: { medicalWriting: false }, currentSnapshot: { value: { allowed_actions: ['document.edit'] } } }
    assert.equal(vm.runInNewContext(editableExpression, context), false, 'Review must remain read-only even with edit permission')
    context.props.reviewMode = false
    assert.equal(vm.runInNewContext(editableExpression, context), true, 'Authorized write remains editable')
    context.props.active = false
    assert.equal(vm.runInNewContext(editableExpression, context), false, 'Inactive workspace cannot edit')
    assert.match(source, /:lifecycle="reviewMode"/)
  }
}
const guard = await readFile(new URL('../web/src/api/medical/route-read.ts', import.meta.url), 'utf8')
assert.match(guard, /name: string \| readonly string\[\]/)
assert.match(guard, /onBeforeRouteLeave\(begin\)/)
assert.match(guard, /onBeforeRouteUpdate\(begin\)/)
assert.match(guard, /!transitioning.value && matches\(\)/)
console.log('PASS task workspace: 3 stages, authorized document isolation, route cancellation, saved-only review and Vue SFC compilation. No live backend or browser behavior asserted.')

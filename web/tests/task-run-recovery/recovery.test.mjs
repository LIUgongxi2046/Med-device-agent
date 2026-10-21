import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
import vm from 'node:vm'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
const source = fs.readFileSync(new URL('../../src/api/medical/task-run-recovery.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const context = { exports: {} }; vm.runInNewContext(code, context)
const { recoveryView, recoveryFormIssues, emptyRecoveryForm } = context.exports
const base = (status = 'FAILED') => ({ run_id: 'run-1', task_id: 'task-1', task_revision: '9007199254740993', status, reason_code: null, steps: [], waiting_items: [], inflight_invocations: [], budget: { budget_id: 'budget-1', budget_revision: '2', spent: '0', reserved: '0', limit: '1', currency: 'CNY' }, revision: '9007199254740995' })
for (const status of ['FAILED', 'CANCELLED', 'WAITING_INPUT']) test(status + ' presents recovery review but never enables execution', () => { const v = recoveryView(base(status)); assert.equal(v.relevant, true); assert.equal(v.canSubmit, false); assert.ok(v.blockers.some(x => x.includes('接口尚未接入'))) })
for (const status of ['QUEUED', 'RUNNING', 'SUCCEEDED', 'CANCEL_REQUESTED', 'SUPERSEDED']) test(status + ' cannot be treated as recovery-ready', () => { assert.equal(recoveryView(base(status)).relevant, false); assert.equal(recoveryView(base(status)).canSubmit, false) })
test('unsupported status preserves raw value and fails closed', () => { const v = recoveryView(base('FUTURE_STATE')); assert.equal(v.known, false); assert.equal(v.rawStatus, 'FUTURE_STATE'); assert.equal(v.relevant, false); assert.match(v.summary, /不推断/) })
test('prototype-key status does not inherit a label', () => { const v = recoveryView(base('toString')); assert.equal(v.known, false); assert.equal(v.relevant, false); assert.equal(v.status, '未知运行状态') })
test('interrupted step is not invented run status or resumability', () => { const s = base('FAILED'); s.steps = [{ step_id: 's', status: 'INTERRUPTED', revision: '3' }]; const v = recoveryView(s); assert.equal(v.failures[0], s.steps[0]); assert.match(v.summary, /检查点/); assert.equal(v.canSubmit, false) })
test('inflight and waiting are independent prerequisites', () => { const s = base('WAITING_INPUT'); s.waiting_items = [{}]; s.inflight_invocations = [{}]; const v = recoveryView(s); assert.ok(v.blockers.some(x => x.includes('等待事项'))); assert.ok(v.blockers.some(x => x.includes('未结算'))) })
test('complete manual declarations never substitute backend qualification', () => { const f = { choice: 'retry', reason: '明确处理', versionsChecked: true, effectsChecked: true }; assert.ok(recoveryFormIssues(f, base(), true).some(x => x.includes('接口尚未接入'))); assert.ok(recoveryFormIssues(f, base(), false).some(x => x.includes('读取权限'))) })
test('form starts unselected and unchecked; instances independent', () => { const a = emptyRecoveryForm(); a.reason = 'private'; const b = emptyRecoveryForm(); assert.equal(b.reason, ''); assert.equal(b.choice, 'inspect'); assert.equal(b.versionsChecked, false); assert.equal(b.effectsChecked, false) })
test('model does not mutate exact original context or coerce large revisions', () => { const s = base(); const before = JSON.stringify(s); recoveryView(s); recoveryFormIssues(emptyRecoveryForm(), s, true); assert.equal(JSON.stringify(s), before) })
test('actual Vue component compiles and has no execution/network path', () => {
 const content = fs.readFileSync(new URL('../../src/views/medical/task-run-recovery.vue', import.meta.url), 'utf8'); const { descriptor, errors } = parse(content); assert.deepEqual(errors, []); const script = compileScript(descriptor, { id: 'recovery-check' }); const result = compileTemplate({ source: descriptor.template.content, filename: 'task-run-recovery.vue', id: 'recovery-check', compilerOptions: { bindingMetadata: script.bindings } }); assert.deepEqual(result.errors, []); assert.doesNotMatch(script.content, /\b(fetch|submitRunCreate|prepareCancellation|request|localStorage|sessionStorage)\s*[.(]/); assert.match(content, /type="submit" disabled/); assert.match(content, /active && canRead && visible/); assert.match(content, /visibilitychange/); assert.match(content, /medical-failure/); assert.match(content, /props\.snapshot\?\.revision/)
})

test('interrupted historical step does not enable choices during RUNNING', () => { const s = base('RUNNING'); s.steps = [{ status: 'INTERRUPTED' }]; assert.equal(recoveryView(s).relevant, false) })

test('actual Vue SSR keeps unauthorized content hidden and renders exact context safely', async () => {
 const vue = await import('vue'); const { renderToString } = await import('@vue/server-renderer')
 const content = fs.readFileSync(new URL('../../src/views/medical/task-run-recovery.vue', import.meta.url), 'utf8')
 const { descriptor } = parse(content)
 const script = compileScript(descriptor, { id: 'recovery-runtime', inlineTemplate: true })
 const js = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
 const sandbox = { exports: {}, document: { hidden: false, addEventListener() {}, removeEventListener() {} }, window: { addEventListener() {}, removeEventListener() {} }, require(name) { if (name === 'vue') return vue; if (name.includes('task-run-recovery')) return context.exports; throw Error(name) } }
 vm.runInNewContext(js, sandbox)
 const component = sandbox.exports.default, snapshot = base()
 snapshot.run_id = '<script>private-run</script>'
 const render = props => renderToString(vue.createSSRApp(component, { snapshot, active: true, canRead: true, canWrite: true, ...props }))
 const hidden = await render({ canRead: false }); assert.doesNotMatch(hidden, /private-run|9007199254740993/)
 const inactive = await render({ active: false }); assert.doesNotMatch(inactive, /private-run/)
 const shown = await render({}); assert.match(shown, /&lt;script&gt;private-run&lt;\/script&gt;/); assert.match(shown, /9007199254740993/); assert.match(shown, /9007199254740995/); assert.match(shown, /尚未提交任何恢复或重试请求/); assert.match(shown, /type="submit" disabled/)
 const readonly = await render({ canWrite: false }); assert.match(readonly, /fieldset disabled/); assert.match(readonly, /只读模式/)
})

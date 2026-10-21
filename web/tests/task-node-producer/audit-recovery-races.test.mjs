// Independent audit regressions. Unknown transport outcomes remain pending; fixed UI races assert intended behavior.
import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { loadModule, response, id, actor, author, prep, budget, webRoot } from './fixtures.mjs'
const require = createRequire(webRoot + '/package.json')

test('audit: unproven transport rejection remains pending after null recovery', async () => {
  const session = actor(); let pending; let posts = 0
  const transport = {
    get: async url => response(url.includes('preparation') ? prep() : budget()),
    post: async url => {
      if (url.endsWith('/recovery')) return response(null)
      posts++
      // A thrown error has no verifiable transaction-disposition evidence.
      const failure = new Error('REVISION_CONFLICT'); failure.code = 'REVISION_CONFLICT'; throw failure
    }
  }
  const { api, store } = await loadModule('task-node-producers', { request: transport })
  const submit = () => api.submitProducer(() => session, id(3), id(4), author(), null, id(16), new AbortController().signal, p => { pending = p })
  await assert.rejects(submit(), e => e.code === 'REVISION_CONFLICT')
  assert.equal(await api.recoverProducer(() => session, pending, new AbortController().signal), null)
  await assert.rejects(submit(), e => e.code === 'IDEMPOTENCY_CONFLICT')
  assert.equal(store.map.size, 1); assert.equal(posts, 1)
})

test('audit: visible return before aborted write settles restores pending presentation after settlement', async () => {
  const vue = require('vue'), ts = require('typescript')
  const source = readFileSync(webRoot + '/src/api/medical/use-task-field-target-page.ts', 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const user = vue.reactive({ medicalSession: actor(), medicalWriting: false })
  const route = vue.reactive({ fullPath: '/task', params: { projectId: id(3), taskId: id(4) } })
  const listeners = new Map(), doc = { hidden: false, addEventListener: (n, f) => listeners.set(n, f), removeEventListener() {} }
  const exports = {}, scope = vue.effectScope()
  vm.runInNewContext(code, { exports, require: target => target === 'vue' ? { ...vue, onBeforeUnmount() {} } : target === 'vue-router' ? { useRoute: () => route } : target === '@/store/modules/user' ? { default: () => user } : { useMedicalRouteRead: () => vue.ref(true) }, document: doc, window: { addEventListener() {}, removeEventListener() {} }, AbortController, Error })
  let bootstraps = 0, shownPending = true, finish
  const page = scope.run(() => exports.useFieldTargetPage('MedicalTaskNodeProducerRules', () => { shownPending = false }, async () => { bootstraps++; shownPending = true }))
  page.start(); assert.equal(bootstraps, 1)
  const write = page.run(async () => { await new Promise(resolve => { finish = resolve }) }, true)
  doc.hidden = true; listeners.get('visibilitychange')()
  doc.hidden = false; listeners.get('visibilitychange')()
  finish(); await write
  assert.equal(page.busy.value, false)
  assert.equal(bootstraps, 2)
  assert.equal(shownPending, true)
  scope.stop()
})

test('audit: synchronous medical-failure clears sensitive presentation but retains a safe visible error', async () => {
  const vue = require('vue'), ts = require('typescript')
  const source = readFileSync(webRoot + '/src/api/medical/use-task-field-target-page.ts', 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const user = vue.reactive({ medicalSession: actor(), medicalWriting: false })
  const route = vue.reactive({ fullPath: '/task', params: { projectId: id(3), taskId: id(4) } })
  const listeners = new Map(), doc = { hidden: false, addEventListener() {}, removeEventListener() {} }
  const exports = {}, scope = vue.effectScope()
  vm.runInNewContext(code, { exports, require: target => target === 'vue' ? { ...vue, onBeforeUnmount() {} } : target === 'vue-router' ? { useRoute: () => route } : target === '@/store/modules/user' ? { default: () => user } : { useMedicalRouteRead: () => vue.ref(true) }, document: doc, window: { addEventListener: (n, f) => listeners.set(n, f), removeEventListener() {} }, AbortController, Error })
  const page = scope.run(() => exports.useFieldTargetPage('MedicalTaskNodeProducerRules', () => {}, async () => {}))
  page.start()
  await page.run(async () => {
    const error = new Error('当前资源版本或管理状态不允许此操作')
    // protocol.fail() dispatches this synchronously immediately before throwing.
    listeners.get('medical-failure')({ detail: error })
    throw error
  })
  assert.match(page.error.value, /请求结果未能核验/)
  assert.equal(page.busy.value, false)
  scope.stop()
})

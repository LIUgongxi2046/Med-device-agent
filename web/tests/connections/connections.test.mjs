import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
const file = new URL('../../src/views/medical/connections.vue', import.meta.url)
const source = readFileSync(file, 'utf8')
const helper = readFileSync(new URL('../../src/views/medical/connections-presentation.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(helper, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const { blankConnectionDraft, validateConnectionDraft, connectionStateCopy, connectionCategories } = await import('data:text/javascript;base64,' + Buffer.from(js).toString('base64'))
test('actual connections SFC compiles', () => { const parsed = parse(source, { filename: file.pathname }); assert.deepEqual(parsed.errors, []); const script = compileScript(parsed.descriptor, { id: 'connections' }); const template = compileTemplate({ source: parsed.descriptor.template.content, filename: file.pathname, id: 'connections', compilerOptions: { bindingMetadata: script.bindings } }); assert.deepEqual(template.errors, []) })
test('blank local draft has eight actionable required errors', () => { assert.equal(Object.keys(validateConnectionDraft(blankConnectionDraft())).length, 8) })
const filled = () => Object.fromEntries(Object.keys(blankConnectionDraft()).map(key => [key, key === 'endpoint' ? 'https://example.invalid/api' : '模拟输入']))
test('format pass never claims connection or approval', () => { assert.deepEqual(validateConnectionDraft(filled()), {}); assert.match(source, /本地必填项与地址格式检查通过。尚未取得批准或真实验证/) })
test('endpoint rejects credentials, parameters, fragments and non HTTPS', () => { for (const endpoint of ['http://example.invalid', 'file:///tmp/file', 'https://user:pass@example.invalid', 'https://example.invalid?token=secret', 'https://example.invalid#token', 'not a url']) assert.ok(validateConnectionDraft({ ...filled(), endpoint }).endpoint, endpoint) })
test('whitespace and oversized fields are rejected', () => { assert.ok(validateConnectionDraft({ ...filled(), name: '   ' }).name); assert.ok(validateConnectionDraft({ ...filled(), scope: 'a'.repeat(501) }).scope) })
test('all non-success view states are explicit', () => { assert.deepEqual(Object.keys(connectionStateCopy), ['blocked', 'empty', 'loading', 'error', 'disconnected', 'verifying', 'sync-error']); assert.equal(connectionCategories.length, 3); assert.ok(connectionCategories.every(c => !('connected' in c))) })
test('no network, credentials or browser persistence exists in page or helper', () => { assert.doesNotMatch(source + helper, /\b(fetch|axios|localStorage|sessionStorage|indexedDB)\b/); assert.doesNotMatch(source, /type="password"|@\/api\//); assert.match(source, /未接入 CT-01—CT-03/); assert.match(source, /access === 'denied'/); assert.match(source, /document.removeEventListener\('visibilitychange'/) })
test('save, verify and sync controls are unconditionally disabled', () => { for (const text of ['验证连接', '开始同步', '保存并连接']) assert.match(source, new RegExp('<button[^>]* disabled[^>]*>' + text + '</button>')) })

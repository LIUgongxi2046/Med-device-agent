import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
const require = createRequire(new URL('../../package.json', import.meta.url))
const ts = require('typescript')
function transport(protocol = {}) {
  let interceptor, rejectResponse
  const actual = readFileSync(new URL('../../src/utils/request.js', import.meta.url), 'utf8')
    .replace(/^import .*\r?\n/gm, '')
    .replace(/import\.meta\.env/g, "({VITE_APP_ENV:'medical',VITE_APP_BASE_API:''})")
    .replace(/export default service/g, '').replace(/export /g, '')
  const readers = { comparison: value => value, bounded: value => value, project: value => value, write: value => value }
  const fail = (code, message) => { throw Object.assign(new Error(message), { code }) }
  const env = { URL, location: { origin: 'https://medical.test' }, axios: { defaults: { headers: {} }, create: () => ({ interceptors: { request: { use: callback => { interceptor = callback } }, response: { use(_success, failure) { rejectResponse = failure } } } }) }, fail,
    readComparisonJson: readers.comparison, readBoundedJson: readers.bounded, readProjectJson: readers.project, writeProjectJson: readers.write,
    prepareFrameworkRequest() {}, verifyFrameworkResponse() {}, console, ...protocol }
  vm.runInNewContext(actual, env)
  function configure(url, medical = 'envelope') {
    const deleted = [], types = []
    const config = { url, medical, method: 'get', headers: { delete: key => deleted.push(key), setContentType: value => types.push(value) } }
    return { config: interceptor(config), deleted, types }
  }
  return { configure, readers, rejectResponse }
}
const id = '00000000-0000-0000-0000-000000000005'
test('actual request interceptor routes exact comparison endpoints to raw-text reader and preserves medical identity handling', () => {
  const { configure, readers } = transport()
  for (const path of ['/api/document-comparisons', '/api/document-comparisons/' + id, '/api/document-comparison-operations', '/api/document-comparison-feature-availability']) {
    const { config, deleted, types } = configure(path)
    assert.equal(config.responseType, 'text'); assert.equal(config.transformResponse[0], readers.comparison)
    assert.equal(config.transformRequest[0], readers.write); assert.equal(config.withCredentials, true)
    assert.deepEqual(deleted, ['Authorization']); assert.deepEqual(types, ['application/json;charset=utf-8'])
    assert.equal(config.baseURL, ''); assert.equal(config.validateStatus(500), true)
  }
})
test('non-comparison and lookalike paths retain previous transform branches; external URL still denied', () => {
  const { configure, readers } = transport()
  assert.equal(configure('/api/documents/' + id).config.transformResponse[0], readers.bounded)
  assert.equal(configure('/api/projects/' + id + '/tasks').config.transformResponse[0], readers.bounded)
  for (const path of ['/api/elsewhere', '/api/document-comparisons/' + id + '/extra', '/api/document-comparisons/not-a-uuid', '/api/document-comparison-operations/not-a-uuid', '/api/document-comparison-feature-availability-extra']) {
    const result = configure(path).config
    assert.equal(result.responseType, 'json'); assert.equal(result.transformResponse, undefined)
  }
  assert.throws(() => configure('https://elsewhere.test/api/document-comparisons'), e => e.code === 'ACCESS_DENIED')
})
test('actual comparison raw reader checks UTF-8 ceiling before existing JSON parsing', () => {
  const source = readFileSync(new URL('../../src/api/medical/document-comparison-json.ts', import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText.replace(/^import .*;\n/gm, '').replace(/export /g, '')
  let parses = 0
  const fail = code => { throw Object.assign(new Error(code), { code }) }
  const read = new Function('fail', 'readBoundedJson', 'MedicalFailure', compiled + '\nreturn readComparisonJson')(fail, raw => { parses++; return JSON.parse(raw) }, class MedicalFailure extends Error { constructor(code, message) { super(message); this.code = code } })
  assert.deepEqual(read('{"status":"actual"}'), { status: 'actual' })
  assert.throws(() => read('汉'.repeat(3000000)), e => e.code === 'CONTEXT_LIMIT'); assert.equal(parses, 1)
})

test('actual MedicalFailure and request rejection keep local capacity errors off the global failure event', async () => {
  const protocolSource = readFileSync(new URL('../../src/api/medical/protocol.ts', import.meta.url), 'utf8')
  const section = protocolSource.slice(protocolSource.indexOf('export class MedicalFailure'), protocolSource.indexOf('export function revision('))
  const compile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText.replace(/^import .*;\n/gm, '').replace(/export /g, '')
  const events = []
  const protocol = new Function('window', 'CustomEvent', compile(section) + '\nreturn {MedicalFailure,fail}')({ dispatchEvent: event => events.push(event) }, class { constructor(type, details) { this.type = type; this.detail = details.detail } })
  let parsed = 0
  const text = readFileSync(new URL('../../src/api/medical/document-comparison-json.ts', import.meta.url), 'utf8')
  const read = new Function('MedicalFailure', 'fail', 'readBoundedJson', compile(text) + '\nreturn readComparisonJson')(protocol.MedicalFailure, protocol.fail, raw => { parsed++; if (raw === '{') protocol.fail('SCHEMA_INVALID', 'invalid JSON'); return JSON.parse(raw) })
  const t = transport(protocol)
  let failure
  try { read('汉'.repeat(3000000)) } catch (error) { failure = error }
  assert.ok(failure instanceof protocol.MedicalFailure); assert.equal(failure.code, 'CONTEXT_LIMIT')
  await assert.rejects(t.rejectResponse(failure), error => error === failure)
  assert.equal(parsed, 0); assert.deepEqual(events, [])
  assert.throws(() => read({}), error => error.code === 'SCHEMA_INVALID'); assert.equal(events.length, 1)
  assert.throws(() => read('{'), error => error.code === 'SCHEMA_INVALID'); assert.equal(events.length, 2)
})

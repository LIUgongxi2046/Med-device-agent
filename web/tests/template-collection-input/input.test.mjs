import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { randomUUID } from 'node:crypto'
import vm from 'node:vm'
const require = createRequire(import.meta.url), vue = require('vue'), ts = require('typescript')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const Ajv = require('ajv/dist/2020').default, addFormats = require('ajv-formats').default
const read = (name, area = 'views/medical') => readFileSync(new URL('../../src/' + area + '/' + name, import.meta.url), 'utf8')
const templates = JSON.parse(readFileSync(new URL('../../../business/medical-persistence/src/main/resources/templates.schema.json', import.meta.url), 'utf8'))
const collections = JSON.parse(readFileSync(new URL('../../../business/medical-persistence/src/main/resources/collections.schema.json', import.meta.url), 'utf8'))
const ajv = new Ajv({ strictSchema: true, strictTypes: false, allErrors: true }); addFormats(ajv); ajv.addSchema(templates); ajv.addSchema(collections)
const id = '00000000-0000-4000-8000-000000000001', id2 = '00000000-0000-4000-8000-000000000002', basis = { version_id: id, content_sha256: 'a'.repeat(64) }
const condition = { mode: 'ALWAYS', rule_revision: null, engine_revision: null, input_keys: [] }
const node = () => ({ node_id: id, parent_id: null, official_number: '1', title: '目录', ordinal: 1, condition: { ...condition }, required: true, content_mode: 'NARRATIVE', evidence_rule: { mode: 'NARRATIVE', authority_source_types: [], verification_rule_revisions: [], calculation_rule_revision: null, input_field_ids: [], require_original_locator: true, require_human_verification: true }, basis_refs: [{ ...basis }], nature: 'OFFICIAL', content_requirements: [] })
const templateBody = () => ({ schema_version: '1.0.0', base_version: null, schema_ref: templates.$id + '#/$defs/Content', reason: '创建完整模板', content: { schema_version: '1.0.0', document_type: 'OVERVIEW', application_path: 'FIRST_REGISTRATION', ivd: false, directory_profile: null, nodes: [node()], field_definitions: [], repeat_definitions: [], attachment_requirements: [], cross_document_rules: [], condition_schema: { attribute_keys: [], applicability: { ...condition }, unknown_result: 'UNKNOWN' }, basis_manifest: [{ ...basis }], format_profile: { format_revision: { ...basis }, output_types: ['DOCX'], numbering_rule_revision: { ...basis }, font_artifacts: [{ ...basis }], node_displays: [], attachment_order: 'DECLARED_ORDINAL' } } })
const collectionBody = () => ({ schema_version: '1.0.0', owner_project_id: null, content: { schema_version: '1.0.0', title: '资料集', purpose: 'SOURCE_REVIEW', members: [{ source_version: id, parse_version: id2 }] }, reason: '创建资料集' })
const file = value => ({ raw: { text: async () => typeof value === 'string' ? value : JSON.stringify(value) } })
function fixture(name, props = {}) {
  const user = vue.reactive({ medicalSession: { user: { user_id: 'actor' }, current_context: { tenant_id: 'tenant' }, action_summary: [] }, medicalWriting: false })
  const calls = { failures: 0, strict: 0, writes: 0 }, scope = vue.effectScope(), modules = new Map(), emitted = []
  const protocol = {
    schemaIssues(reference, value) { const check = ajv.getSchema(reference); assert.ok(check, reference); return check(value) ? [] : check.errors.map(error => ({ path: error.instancePath, code: error.keyword })) },
    fail(code, message) { calls.failures++; user.medicalSession = null; throw Object.assign(new Error(message), { code }) },
    validate(reference, value) { if (protocol.schemaIssues(reference, value).length) protocol.fail('SCHEMA_INVALID', 'schema rejected'); return value },
    revision(value) { if (!/^(0|[1-9][0-9]{0,19})$/.test(value) || BigInt(value) > 18446744073709551615n) protocol.fail('SCHEMA_INVALID', 'revision rejected'); return value }, validContext() {}
  }
  const approval = { canonicalTemplateValue: JSON.stringify, templateApprovalAllowed: () => true }
  function execute(source, imports) {
    const exports = {}, code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
    vm.runInNewContext(code, { exports, require: imports, Error, AbortController, crypto: { randomUUID }, document: { hidden: false, addEventListener() {}, removeEventListener() {} }, window: { addEventListener() {}, removeEventListener() {} } }); return exports
  }
  function api(name) {
    if (modules.has(name)) return modules.get(name)
    if (name === 'project-write') return api('project-text')
    if (name === 'template-approval') return approval
    if (name === 'operations') return {}
    const exports = execute(read(name + '.ts', 'api/medical'), dependency => {
      if (dependency === './protocol') return protocol
      if (dependency.startsWith('./')) return api(dependency.slice(2))
      if (dependency.includes('?raw')) return { default: '' }
      if (dependency === '@/utils/request') return { default: { post() { calls.writes++ } } }
      return require(dependency)
    }); modules.set(name, exports); return exports
  }
  const local = execute(read('template-collection-input.ts'), dependency => dependency === '@/api/medical/protocol' ? protocol : api(dependency.split('/').at(-1)))
  const template = api('template-create'), collection = api('collections')
  const imports = {
    vue: { ...vue, onBeforeUnmount() {} }, 'vue-router': { useRoute: () => ({ params: {}, query: {}, meta: { collectionCreate: true }, fullPath: '/new' }), useRouter: () => ({ push() {} }), onBeforeRouteLeave() {} },
    '@/store/modules/user': { default: () => user }, '@/api/medical/protocol': protocol, './template-collection-input': local,
    '@/api/medical/template-create': { ...template, templateCreateAllowed: () => true, readPendingTemplateCreate: () => null, validateTemplateCreate: value => { calls.strict++; return template.validateTemplateCreate(value) } },
    '@/api/medical/collections': { ...collection, collectionAllowed: () => true, readPendingCollection: () => null, validateCollectionBody: (...args) => { calls.strict++; return collection.validateCollectionBody(...args) } }
  }
  const source = read(name + '.vue'), parsed = parse(source, { filename: name + '.vue' })
  assert.deepEqual(parsed.errors, [])
  const script = compileScript(parsed.descriptor, { id: name })
  assert.deepEqual(compileTemplate({ source: parsed.descriptor.template.content, filename: name + '.vue', id: name, compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
  const component = execute(script.content, dependency => {
    if (imports[dependency]) return imports[dependency]
    if (dependency.startsWith('@/api/medical/')) return api(dependency.split('/').at(-1))
    if (dependency.endsWith('templates.schema.json')) return { default: templates }
    return {}
  })
  const state = scope.run(() => component.default.setup(props, { expose() {}, emit: (...args) => emitted.push(args) }))
  return { state, user, calls, emitted, stop: () => scope.stop() }
}
const plain = value => JSON.parse(JSON.stringify(value))
for (const [name, method, valid] of [['collections', 'importBody', collectionBody], ['template-create', 'importDefinition', templateBody]]) {
  test(`${name}: invalid files keep draft and session without failure events or writes`, async () => {
    for (const input of ['{', '{"x":1,"x":2}', '{"x":9007199254740993}', {}, { ...valid(), reason: ' bad reason ' }]) {
      const f = fixture(name)
      try {
        f.state.form.reason = '保留原草稿'; const before = plain(f.state.form), session = f.user.medicalSession
        await f.state[method](file(input))
        assert.ok(f.state.errors.import); assert.deepEqual(plain(f.state.form), before); assert.equal(f.user.medicalSession, session)
        assert.deepEqual(f.calls, { failures: 0, strict: 0, writes: 0 })
      } finally { f.stop() }
    }
  })
  test(`${name}: file read rejection is local and missing raw file is handled`, async () => {
    const f = fixture(name)
    try {
      f.state.form.reason = '保留原草稿'; const before = plain(f.state.form)
      await f.state[method]({ raw: { text: async () => { throw new Error('read failed') } } }); assert.ok(f.state.errors.import)
      await f.state[method]({}); assert.ok(f.state.errors.import)
      assert.deepEqual(plain(f.state.form), before); assert.ok(f.user.medicalSession); assert.equal(f.calls.failures, 0)
    } finally { f.stop() }
  })
  test(`${name}: valid import still runs original strict validator`, async () => {
    const f = fixture(name), body = valid()
    try {
      await f.state[method](file(body)); assert.equal(f.state.form.reason, body.reason)
      assert.ok(f.user.medicalSession); assert.deepEqual(f.calls, { failures: 0, strict: 1, writes: 0 })
    } finally { f.stop() }
  })
}
test('collection duplicate member source versions remain local', async () => {
  const f = fixture('collections'), body = collectionBody(); body.content.members.push({ source_version: id, parse_version: id })
  try { await f.state.importBody(file(body)); assert.ok(f.state.errors.import); assert.ok(f.user.medicalSession); assert.equal(f.calls.strict, 0); assert.equal(f.calls.failures, 0) }
  finally { f.stop() }
})
test('template conflicting fixed-reference draft hashes remain local', async () => {
  const f = fixture('template-create'), body = templateBody(); body.content.basis_manifest[0].content_sha256 = 'b'.repeat(64)
  try { await f.state.importDefinition(file(body)); assert.ok(f.state.errors.import); assert.ok(f.user.medicalSession); assert.equal(f.calls.strict, 0); assert.equal(f.calls.failures, 0) }
  finally { f.stop() }
})
test('collection pending owner mismatch still fails closed after valid input', async () => {
  const f = fixture('collections')
  try {
    f.state.pending.value = { owner_project_id: id, key: 'existing-key' }
    await assert.rejects(f.state.importBody(file(collectionBody())), /归属不一致/)
    assert.equal(f.calls.strict, 1); assert.equal(f.calls.failures, 1); assert.equal(f.calls.writes, 0)
  } finally { f.stop() }
})
test('structured definition open rejects bad JSON, wrong shape, bad member and duplicate IDs locally', () => {
  for (const value of ['{', '{}', '[{}]', '[9007199254740993]', JSON.stringify([node(), { ...node(), title: '重复标识' }])]) {
    const props = { kind: 'Node', value, disabled: false }, f = fixture('template-definition-editor', props)
    try { f.state.open(); assert.ok(f.state.errors.open); assert.equal(props.value, value); assert.equal(f.calls.failures, 0); assert.equal(f.emitted.length, 0); assert.ok(f.user.medicalSession) }
    finally { f.stop() }
  }
})
test('structured editor keeps invalid numeric and duplicate-ID edits for correction', () => {
  const second = { ...node(), node_id: id2 }, f = fixture('template-definition-editor', { kind: 'Node', value: JSON.stringify([node(), second]), disabled: false })
  try {
    f.state.open(); f.state.edit(1); f.state.texts.ordinal = '9007199254740993'; f.state.confirmItem()
    assert.ok(f.state.errors.ordinal); assert.equal(f.state.texts.ordinal, '9007199254740993'); assert.equal(f.state.editing.value, true)
    f.state.texts.ordinal = '1'; f.state.texts.node_id = id; f.state.confirmItem()
    assert.ok(f.state.errors.schema); assert.equal(f.state.texts.node_id, id); assert.equal(f.state.items.value[1].node_id, id2)
    assert.equal(f.calls.failures, 0); assert.equal(f.emitted.length, 0); assert.ok(f.user.medicalSession)
  } finally { f.stop() }
})
test('valid structured edit still checks original definitions before emitting changed', () => {
  const f = fixture('template-definition-editor', { kind: 'Node', value: JSON.stringify([node()]), disabled: false })
  try { f.state.open(); f.state.edit(0); f.state.texts.title = '修改标题'; f.state.confirmItem(); f.state.apply(); assert.equal(JSON.parse(f.emitted[0][1])[0].title, '修改标题'); assert.equal(f.calls.failures, 0) }
  finally { f.stop() }
})
test('structured editor keeps baseline/context stale guard fail-closed', () => {
  const props = { kind: 'Node', value: JSON.stringify([node()]), disabled: false }, f = fixture('template-definition-editor', props)
  try { f.state.open(); props.value = '[]'; assert.throws(() => f.state.apply(), /上下文已经变化/); assert.equal(f.calls.failures, 1); assert.equal(f.emitted.length, 0) }
  finally { f.stop() }
})
test('template form sections reject unsafe numbers locally and valid full body keeps strict validation', async () => {
  const f = fixture('template-create'), original = templateBody()
  try {
    await f.state.importDefinition(file(original)); assert.equal(f.calls.strict, 1)
    const valid = await f.state.body(); assert.equal(valid.reason, original.reason); assert.equal(f.calls.strict, 2)
    f.state.form.sections.nodes = '[9007199254740993]'
    assert.equal(await f.state.body(), null); assert.ok(f.state.errors.nodes); assert.equal(f.state.form.sections.nodes, '[9007199254740993]')
    assert.equal(f.calls.strict, 2); assert.equal(f.calls.failures, 0); assert.ok(f.user.medicalSession)
    f.state.form.sections.nodes = JSON.stringify(original.content.nodes)
    f.state.form.sections.basis_manifest = JSON.stringify([{ ...basis, content_sha256: 'b'.repeat(64) }])
    assert.equal(await f.state.body(), null); assert.ok(f.state.errors.schema); assert.equal(f.calls.strict, 2); assert.equal(f.calls.failures, 0)
  } finally { f.stop() }
})
test('template count beyond unsigned 64-bit bound is a local draft error', async () => {
  const f = fixture('template-create'), body = templateBody()
  body.content.repeat_definitions = [{ repeat_id: id2, node_id: id, name: '重复表格', field_ids: [id], min_count: '18446744073709551616', max_count: null, count_basis: [{ ...basis }], row_id_required: true }]
  try { await f.state.importDefinition(file(body)); assert.ok(f.state.errors.import); assert.equal(f.calls.strict, 0); assert.equal(f.calls.failures, 0); assert.ok(f.user.medicalSession) }
  finally { f.stop() }
})
for (const [name, method] of [['collections', 'importBody'], ['template-create', 'importDefinition']]) test(`${name}: late import after session change cannot overwrite new draft or error`, async () => {
  const f = fixture(name); let resolve
  try {
    const importing = f.state[method]({ raw: { text: () => new Promise(done => { resolve = done }) } })
    f.user.medicalSession = { user: { user_id: 'actor-new' }, current_context: { tenant_id: 'other' }, action_summary: [] }
    await vue.nextTick(); f.state.form.reason = '新上下文草稿'
    resolve('{'); await importing
    assert.equal(f.state.form.reason, '新上下文草稿'); assert.ok(!f.state.errors.import); assert.equal(f.calls.failures, 0); assert.equal(f.calls.strict, 0)
  } finally { f.stop() }
})

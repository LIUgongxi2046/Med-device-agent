import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { loadMedicalProtocol } from '../../scripts/medical-schema-module.mjs'
const instrumentation = `
let measuredCalls = 0
const measuredGet = ajv.getSchema.bind(ajv)
ajv.getSchema = (reference) => { measuredCalls++; return measuredGet(reference) }
export const probe = { size: () => validators.size, calls: () => measuredCalls, validator: (reference) => validators.get(reference) }
`
async function load(transform = value => value) {
  const events=[]
  const module=await loadMedicalProtocol({ transform: value => transform(value)+instrumentation,
    globals:{window:{dispatchEvent(event){events.push(event)}},CustomEvent:class {constructor(type,options){this.type=type;this.detail=options.detail}}} })
  return {module,events}
}
test('actual protocol is lazy, common validators reuse identical cached functions, entire allowlist compiles',async()=>{
  const {module:p,events}=await load()
  assert.equal(p.probe.size(),0)
  assert.equal(p.probe.calls(),0)
  assert.ok(p.schemaIssues('urn:medical:identity',{}).length)
  const identity=p.probe.validator('urn:medical:identity');assert.equal(typeof identity,'function')
  assert.equal(p.probe.calls(),1)
  p.schemaIssues('urn:medical:identity',{})
  assert.equal(p.probe.validator('urn:medical:identity'),identity);assert.equal(p.probe.calls(),1)
  const id='urn:medical:source-uploads:1.0.0#/$defs/Id',value='00000000-0000-4000-8000-000000000001'
  assert.equal(p.validate(id,value),value)
  const idValidator=p.probe.validator(id), calls=p.probe.calls()
  assert.equal(p.schemaIssues(id,value).length,0);assert.equal(p.probe.validator(id),idValidator);assert.equal(p.probe.calls(),calls)
  assert.throws(()=>p.validate(id,'invalid'),/Schema/);assert.equal(events.length,1);assert.equal(events[0].type,'medical-failure')
  const count=p.compileAllMedicalSchemas();assert.ok(count>=432);assert.equal(p.probe.size(),count)
  const compiled=p.probe.calls();assert.equal(p.compileAllMedicalSchemas(),count);assert.equal(p.probe.calls(),compiled)
})
test('unknown, malicious and registered-but-unapproved fragments never reach AJV',async()=>{
 const {module:p,events}=await load()
 for(const reference of ['https://attacker.invalid/schema','__proto__','urn:medical:identity#/$defs/NotRegistered','urn:medical:shared#/$defs/NullableText']) {
  assert.throws(()=>p.schemaIssues(reference,{}),/Schema/)
  assert.throws(()=>p.validate(reference,{}),/Schema/)
 }
 assert.equal(p.probe.calls(),0);assert.equal(p.probe.size(),0);assert.equal(events.length,4)
})
test('dangling references fail exhaustive gate and runtime validation, without caching a fallback',async()=>{
 const {module:p,events}=await load(value=>value.replace('ajv.addSchema(gruFeatureAvailability)',"ajv.addSchema({ ...gruFeatureAvailability, allOf: [{ $ref: 'urn:medical:missing-dependency' }] })"))
 assert.throws(()=>p.compileAllMedicalSchemas(),/resolve reference/)
 const ref='urn:medical:gru-feature-availability:1.0.0'
 assert.equal(p.probe.validator(ref),undefined)
 assert.throws(()=>p.validate(ref,{}),/Schema/);assert.equal(events.length,1)
 assert.throws(()=>p.schemaIssues(ref,{}),/resolve reference/)
 assert.equal(p.probe.validator(ref),undefined)
})
test('medical build and typecheck both run exhaustive schema gate first',async()=>{
 const pkg=JSON.parse(await readFile(new URL('../../package.json',import.meta.url)))
 for(const name of ['build:medical','typecheck:medical'])assert.ok(pkg.scripts[name].startsWith('npm run check:medical-schemas && '))
 assert.equal(pkg.scripts['check:medical-schemas'],'node scripts/check-medical-schemas.mjs')
})

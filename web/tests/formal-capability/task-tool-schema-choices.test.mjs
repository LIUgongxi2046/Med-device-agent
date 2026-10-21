import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createHash} from 'node:crypto'
import {loadModule,webRoot} from './helpers.mjs'
const root=webRoot+'/..',domain=(await loadModule('formal-capability-domain')).api
const extension=JSON.parse(readFileSync(root+'/contracts/extensions/task-tool-read.catalogue.json','utf8'))
test('normal authoring exposes exact SearchArgs, ReadArgs and receipt schema without fictional tool versions',()=>{
 const input=extension.schemas.find(v=>v.name==='tool-inputs'),output=extension.schemas.find(v=>v.name==='task-tool-read-success')
 for(const definition of ['SearchArgs','ReadArgs']){const choice=domain.capabilitySchemas.find(v=>v.schema_ref==='https://schemas.medical-agent.invalid/1.0.0/tool-inputs#/$defs/'+definition);assert.ok(choice);assert.equal(choice.schema_sha256,input.sha256);assert.equal('tool_id' in choice,false);assert.equal('approved' in choice,false)}
 const result=domain.capabilitySchemas.find(v=>v.schema_ref==='https://schemas.medical-agent.invalid/1.0.0/task-tool-read-success');assert.ok(result);assert.equal(result.schema_sha256,output.sha256)
})
test('additional input/output choices are byte-pinned and preserve all legacy choices in order',()=>{
 const base=JSON.parse(readFileSync(root+'/contracts/catalogue.json','utf8'))
 assert.deepEqual(JSON.parse(JSON.stringify(domain.capabilitySchemas.slice(0,base.schemas.length))),base.schemas.map(item=>({label:item.name,schema_ref:base.base_uri+item.path,schema_sha256:item.sha256})))
 assert.equal(domain.capabilitySchemas.length,base.schemas.length+6)
 for(const entry of extension.schemas)assert.equal(createHash('sha256').update(readFileSync(root+'/'+entry.path)).digest('hex'),entry.sha256)
 assert.equal(new Set(domain.capabilitySchemas.map(v=>v.schema_ref)).size,domain.capabilitySchemas.length)
})

test('normal authoring exposes only the three actual published professional package definitions',()=>{
 const manifest=JSON.parse(readFileSync(root+'/contracts/extensions/task-professional-package.catalogue.json','utf8'))
 const pin=manifest.schemas[0]
 assert.equal(createHash('sha256').update(readFileSync(root+'/'+pin.path)).digest('hex'),pin.sha256)
 for(const definition of ['RolePackage','PromptPackage','ProfilePackage']){
   const choice=domain.capabilitySchemas.find(v=>v.schema_ref==='urn:medical:task-professional-package:1.0.0#/$defs/'+definition)
   assert.ok(choice);assert.equal(choice.schema_sha256,pin.sha256);assert.equal('approved' in choice,false)
 }
 assert.equal(domain.capabilitySchemas.some(v=>v.schema_ref.includes('task-node-input')),false)
})

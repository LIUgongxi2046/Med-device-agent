import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from '../../node_modules/typescript/lib/typescript.js'
import Ajv from '../../node_modules/ajv/dist/2020.js'
import formats from '../../node_modules/ajv-formats/dist/index.js'
import {parse} from '../../node_modules/yaml/dist/index.js'
import * as Vue from '../../node_modules/vue/dist/vue.runtime.esm-bundler.js'
const read=p=>fs.readFileSync(new URL(p,import.meta.url),'utf8')
const transpile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
const fixtures={};vm.runInNewContext(transpile(read('./fixtures.ts')),{exports:fixtures})
const ajv=new Ajv({strict:false});formats(ajv)
for(const f of ['interfaces','resource-details']){const schema=parse(read('../../../backend-design/'+f+'.schema.yaml'));schema.$id='https://schemas.medical-agent.invalid/1.0.0/backend-design/'+f+'.schema.yaml';ajv.addSchema(schema)}
ajv.addSchema({$id:'urn:medical:templates:1.0.0',$defs:{Id:{type:'string',format:'uuid'}}})
ajv.addSchema(JSON.parse(read('../../../business/medical-persistence/src/main/resources/gru-records.schema.json')))
const dataset=JSON.parse(read('../../../data-design/dataset.schema.json'));dataset.$id='https://schemas.medical-agent.invalid/1.0.0/data-design/dataset.schema.json';ajv.addSchema(dataset)
const check=ajv.getSchema('urn:medical:gru-records:1.0.0#/$defs/Read');assert.equal(check(fixtures.conversation),true,JSON.stringify(check.errors))
const bodyCheck=ajv.getSchema('https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/DocumentBody');assert.equal(bodyCheck(fixtures.body),true,JSON.stringify(bodyCheck.errors))
const transport={};vm.runInNewContext(transpile(read('./transport.ts')),{exports:transport,require:()=>fixtures})
const client=transport.default
for(const path of ['/api/gru-feature-availability','/api/gru-record-export-feature-availability','/api/gru-conversations','/api/gru-conversations/'+fixtures.conversation.conversation_id]){const response=await client.get(path);assert.equal(response.headers['cache-control'],'no-store');assert.equal(response.headers['x-schema-version'],'1.0.0');assert.equal(response.headers['x-request-id'],response.data.request_id)}
const list=await client.get('/api/gru-conversations');const listCheck=ajv.getSchema('urn:medical:gru-records:1.0.0#/$defs/List');assert.equal(listCheck(list.data.data),true,JSON.stringify(listCheck.errors))
assert.throws(()=>client.post('/anything',{}),/禁止写入/)
await assert.rejects(client.get('https://example.com'),/禁止写入/)
assert.equal(fixtures.session.action_summary.some(a=>a.action!=='conversation.read'),false)
console.log('Gru preview: read fixtures and body schema valid; headers verified; unknown reads/writes blocked; read-only permissions verified.')

const userModule={};vm.runInNewContext(transpile(read('./user.ts')),{exports:userModule,require:path=>path==='vue'?Vue:fixtures});assert.equal(Vue.toRaw(userModule.default().medicalSession),fixtures.session);console.log('Preview authenticated store and component session share the same raw identity.')

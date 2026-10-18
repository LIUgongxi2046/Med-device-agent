import assert from 'node:assert/strict'
import fs from 'node:fs'
import crypto from 'node:crypto'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { parse, compileScript, compileTemplate } from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js'
import ts from '../../web/node_modules/typescript/lib/typescript.js'
const root = fileURLToPath(new URL('../../', import.meta.url))
const source = name => fs.readFileSync(`${root}web/src/views/medical/${name}.vue`, 'utf8')
const upload = source('source-uploads')
for (const name of ['source-uploads', 'source-upload-result', 'source-declaration']) test(`${name} script/template compiles`, () => {
 const filename = `${name}.vue`; const {descriptor, errors} = parse(source(name), {filename}); assert.deepEqual(errors, [])
 const script = compileScript(descriptor, {id:name})
 const result = compileTemplate({id:name, filename, source:descriptor.template.content, compilerOptions:{bindingMetadata:script.bindings}})
 assert.deepEqual(result.errors, [])
})
function functionText(text, name) {
 const start = text.search(new RegExp('(?:async )?function ' + name + '\\('))
 assert.ok(start >= 0)
 return text.slice(start, text.indexOf('\n}', start) + 2)
}
const preservedHashes = {"active": "8f5440803586f5334d6d59846d0fada1153e49149d3d3cc3f49f1c8bc0dd5be7", "recover": "7323b92e462da7034c1fcb4bc41e4b11f9be3814f96c9b3b4f24303be5e16973", "queryResult": "8f0b6596751b684cc381c9e4cf646692707acd62aed53a3f0536febd7f3bfbf1", "operationLabel": "de061e066db32d83cd4a959014786a867545d84cc276eaebea60f9fd39acecb6", "sendConfirmed": "3b3644e429fa6eb5d0a252112418c13453a23b543a84ffaa64e266334381844c", "send": "4a3c73e68f090f87e7e49a191050d97b2d5cf1b8a2d3ecb035f64e867d89be8a", "stop": "ef139246b490513d4ac1b9ded77f4f6c10cf355284ab27a776c35843e8b658b9"}
for (const [name, expected] of Object.entries(preservedHashes)) test(`${name} preserves original upload/recovery behavior`, () => {
 assert.equal(crypto.createHash('sha256').update(functionText(upload, name)).digest('hex'), expected)
})
const progressCode = ts.transpileModule(functionText(upload, 'receivedPercent'), {compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText
const progress = new Function(progressCode + '; return receivedPercent')()
const item = (received, total) => ({state:{registration:{received_size:received,request:{size_bytes:total}}},sent:999999})
test('server-confirmed progress excludes locally sent bytes and unknown state', () => {
 assert.equal(progress({state:null,sent:100}), null)
 assert.equal(progress(item('0','100')), 0)
 assert.equal(progress(item('25','100')), 25)
 assert.equal(progress(item('100','100')), 100)
 assert.equal(progress(item('1','0')), null)
 assert.equal(progress(item('101','100')), null)
 assert.equal(progress(item('-1','100')), null)
 assert.equal(progress(item('9007199254740993','18014398509481986')), 50)
})
test('independent statuses and fixed source identity stay explicit', () => {
 const result = source('source-upload-result')
 for (const field of ['review.parse_status','review.index_status','review.approval_status','review.expected_count','review.verified_count','value.source.source_version','value.source.current_rights.version_id','value.completion.operation_state','value.attempt.failure_manifest']) assert.ok(result.includes(field),field)
 assert.ok(result.includes("value.completion.result_status === 'DUPLICATE'"))
 assert.ok(upload.includes('处理结果尚未读取'))
 assert.ok(upload.includes('尚未核查接收状态'))
 assert.ok(upload.includes('inspected.confirmed'))
})
test('selection stays keyboard accessible and cleared with session state', () => {
 assert.ok(upload.includes('type="button" class="upload-file-item"'))
 assert.ok(upload.includes(':aria-pressed="inspected?.id === item.id"'))
 assert.ok(functionText(upload, 'reset').includes("inspectedId.value = ''"))
 assert.ok(upload.includes("window.addEventListener('medical-failure', reset)"))
})

// Run the compiled component setup with Vue's real reactivity. External I/O is stubbed;
// no DOM mount or real request is made by this harness.
import * as Vue from '../../web/node_modules/vue/dist/vue.runtime.esm-bundler.js'
import vm from 'node:vm'
function runtime(overrides = {}) {
 const user = Vue.reactive({medicalSession:{user:{user_id:'actor'},current_context:{tenant_id:'tenant'}},medicalWriting:false})
 const route = Vue.reactive({params:{projectId:'project'},query:{},fullPath:'/projects/project/uploads'})
 const api = {sourceMediaTypes:{txt:'text/plain'}, sourceUploadAllowed:()=>true, sourceMediaType:()=> 'text/plain',byteDigest:async()=> 'hash',
  readUploadJournals:()=>[],prepareSourceUpload:async()=> {throw new Error('Unexpected write preparation')},...overrides}
 const cleanup=[];const scope=Vue.effectScope()
 const {descriptor}=parse(upload,{filename:'source-uploads.vue'})
 const compiled=compileScript(descriptor,{id:'runtime-upload'}).content
 const js=ts.transpileModule(compiled,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
 const exports={};const document={hidden:false,addEventListener(){},removeEventListener(){}}
 const window={addEventListener(){},removeEventListener(){}}
 const context=vm.createContext({exports,require(name){
  if(name==='vue')return {...Vue,onBeforeUnmount:fn=>cleanup.push(fn)}
  if(name==='vue-router')return {useRoute:()=>route}
  if(name==='@/store/modules/user')return {default:()=>user}
  if(name==='@/api/medical/protocol')return {schemaIssues:()=>[],fail:(code,message)=>{throw new Error(code+': '+message)}}
  if(name==='@/api/medical/source-uploads')return api
  return {default:{}}
 },document,window,crypto:crypto.webcrypto,AbortController,console})
 vm.runInContext(js,context)
 const bindings=scope.run(()=>exports.default.setup({}, {expose(){}}))
 return {bindings,user,route,api,close(){cleanup.forEach(fn=>fn());scope.stop()}}
}
function queueItem(id='one') {return {id,file:{name:'file.txt',size:1,arrayBuffer:async()=>new ArrayBuffer(1)},body:{file_name:'file.txt',sha256:'hash',size_bytes:'1',provenance:{owner:'owner'},rights_declaration:{license:'original'}},journal:null,state:null,result:null,message:'',sent:0,errors:[],confirmed:false}}
test('compiled setup revokes only changed declaration confirmation, including closed dialogs', async()=>{
 const r=runtime();try{
  const b=r.bindings;b.items.value.push(queueItem('one'),queueItem('two'))
  const [one,two]=b.items.value;one.confirmed=true;two.confirmed=true
  b.selected.value=one;b.selected.value=null
  one.body.rights_declaration.license='changed'
  assert.equal(one.confirmed,false);assert.equal(two.confirmed,true)
  one.confirmed=true;one.body={...one.body,provenance:{owner:'replacement'}}
  assert.equal(one.confirmed,false)
  one.confirmed=true;one.message='read result';one.sent=1
  assert.equal(one.confirmed,true)
  one.body.rights_declaration.license='changed again'
  let called=false;r.api.prepareSourceUpload=async()=>{called=true;throw Error('must not send')}
  await b.send(one);assert.equal(called,false);assert.equal(b.selected.value,one)
 }finally{r.close()}
})
test('compiled setup clears selected removal and session switch details/dialogs',()=>{
 const r=runtime();try{
  const b=r.bindings;b.items.value.push(queueItem('one'),queueItem('two'));const one=b.items.value[0]
  b.inspectedId.value='one';b.selected.value=one;b.reselect.value=one;b.remove(one)
  assert.equal(b.inspectedId.value,'');assert.equal(b.selected.value,null);assert.equal(b.reselect.value,null);assert.equal(b.inspected.value.id,'two')
  b.selected.value=b.items.value[0];b.reselect.value=b.items.value[0];r.user.medicalSession={user:{user_id:'other'},current_context:{tenant_id:'other'}}
  assert.equal(b.inspected.value,null);assert.equal(b.selected.value,null);assert.equal(b.reselect.value,null);assert.equal(b.items.value.length,0)
 }finally{r.close()}
})
test('compiled setup ignores late file replacement for removed target',async()=>{
 let finish;const r=runtime({byteDigest:()=>new Promise(resolve=>{finish=resolve})});try{
  const b=r.bindings;b.items.value.push(queueItem());const row=b.items.value[0];b.reselect.value=row
  const pending=b.selectFile(row.file);await new Promise(resolve=>setImmediate(resolve));b.remove(row);finish('hash');await pending
  assert.equal(b.items.value.length,0);assert.equal(b.selected.value,null);assert.equal(b.reselect.value,null)
 }finally{r.close()}
})
test('compiled setup propagates original-declaration conflict without new write or replacing journal',async()=>{
 let prepared=0;let created=0
 const r=runtime({prepareSourceUpload:async()=>{prepared++;throw Error('IDEMPOTENCY_CONFLICT')},createSourceUpload:async()=>{created++}})
 try{const b=r.bindings;b.items.value.push(queueItem());const row=b.items.value[0];row.journal={declaration_sha256:'original',requests:[{key:'original-key'}]};row.confirmed=true
  await assert.rejects(b.send(row),/IDEMPOTENCY_CONFLICT/)
  assert.equal(prepared,1);assert.equal(created,0);assert.equal(row.journal.declaration_sha256,'original');assert.equal(row.journal.requests[0].key,'original-key');assert.equal(b.busy.value,false)
 }finally{r.close()}
})
test('compiled setup recovery replaces only server-confirmed declaration and revokes confirmation',async()=>{
 const journal={item_id:'one',upload_id:'upload',requests:[]}
 const state={registration:{request:{file_name:'server.txt',rights_declaration:{license:'server-original'}}}}
 const r=runtime({readUploadJournals:()=>[journal],uploadIdentity:()=>({key:'original-key'}),lookupUploadRequest:async()=>({lookup_status:'FOUND',operation:{resource_id:'upload'}}),saveUploadJournal(){},readSourceUpload:async()=>state,readSourceUploadResult:async()=>({upload_id:'upload'})})
 try{const b=r.bindings;b.items.value.push(queueItem());const row=b.items.value[0];row.confirmed=true;b.selected.value=null
  await b.recover();assert.equal(row.body.file_name,'server.txt');assert.equal(row.confirmed,false);assert.equal(row.journal.requests.length,0)
 }finally{r.close()}
})
test('compiled setup unknown registration recovery keeps original declaration and request',async()=>{
 const journal={item_id:'one',upload_id:null,requests:[{key:'original-key'}]};let reads=0
 const r=runtime({readUploadJournals:()=>[journal],uploadIdentity:()=>({key:'original-key'}),lookupUploadRequest:async()=>({lookup_status:'NOT_FOUND'}),readSourceUpload:async()=>{reads++;throw Error('must not read')}})
 try{const b=r.bindings;b.items.value.push(queueItem());const row=b.items.value[0];row.journal=journal
  await b.recover();assert.equal(row.body.rights_declaration.license,'original');assert.equal(row.journal.requests[0].key,'original-key');assert.equal(reads,0);assert.match(row.message,/原文件及完整原声明/)
 }finally{r.close()}
})
test('compiled setup locally rejects unsupported/empty files without protocol fail, hashing, or upload',async()=>{
 let hashCalls=0;let mediaCalls=0;let protocolFailures=0;let writeCalls=0
 const r=runtime({sourceMediaType:file=>{mediaCalls++;if(file.name.endsWith('.exe')||file.size===0){protocolFailures++;throw Error('medical-failure')}return 'text/plain'},byteDigest:async()=>{hashCalls++;return 'hash'},prepareSourceUpload:async()=>{writeCalls++;throw Error('must not write')}})
 try{const b=r.bindings;const session=r.user.medicalSession;b.items.value.push(queueItem());const original=b.items.value[0];original.confirmed=true;b.selected.value=original;b.reselect.value=original
  for(const file of [{name:'bad.exe',size:2},{name:'empty.txt',size:0},{name:'invalid.txt',size:NaN},{name:'negative.txt',size:-1},{name:'large.txt',size:Number.MAX_SAFE_INTEGER+1}]){
   await b.selectFile({...file,arrayBuffer:async()=>{throw Error('must not read')}})
   assert.ok(b.notice.value);assert.equal(r.user.medicalSession,session);assert.equal(b.items.value.length,1);assert.equal(b.items.value[0],original);assert.equal(original.confirmed,true);assert.equal(b.selected.value,original);assert.equal(b.reselect.value,original)
  }
  assert.equal(hashCalls,0);assert.equal(mediaCalls,0);assert.equal(protocolFailures,0);assert.equal(writeCalls,0)
 }finally{r.close()}
})
test('compiled setup accepts supported case-insensitive nonempty selection without submitting',async()=>{
 let hashCalls=0;let mediaCalls=0;let writes=0
 const r=runtime({sourceMediaTypes:{txt:'text/plain'},sourceMediaType:()=>{mediaCalls++;return 'text/plain'},byteDigest:async()=>{hashCalls++;return 'hash'},prepareSourceUpload:async()=>{writes++;throw Error('no implicit upload')}})
 try{r.bindings.notice.value='old local error';await r.bindings.selectFile({name:'Valid.TXT',size:2,arrayBuffer:async()=>new ArrayBuffer(2)})
  assert.equal(r.bindings.items.value.length,1);assert.equal(r.bindings.items.value[0].confirmed,false);assert.equal(r.bindings.selected.value,r.bindings.items.value[0]);assert.equal(r.bindings.notice.value,'');assert.equal(hashCalls,1);assert.equal(mediaCalls,2);assert.equal(writes,0)
 }finally{r.close()}
})
test('compiled setup still fails closed for supported replacement with wrong original hash',async()=>{
 const r=runtime({byteDigest:async()=> 'wrong-hash'});try{
  const b=r.bindings;b.items.value.push(queueItem());const row=b.items.value[0];row.journal={file_sha256:'original-hash',size_bytes:'1'};b.reselect.value=row
  await assert.rejects(b.selectFile({name:'file.txt',size:1,arrayBuffer:async()=>new ArrayBuffer(1)}),/CONTENT_MISMATCH/)
  assert.equal(row.journal.file_sha256,'original-hash');assert.equal(row.body.sha256,'hash')
 }finally{r.close()}
})

test('validateItem preserves project confirmation and permission checks, enforces enterprise new declaration only',()=>{
 const r=runtime();try{
  const row=queueItem();row.body.ownership='PROJECT'
  assert.equal(r.bindings.validateItem(row),false)
  assert.ok(row.errors.includes('请确认本文件的完整来源及许可声明'))
  row.confirmed=true;assert.equal(r.bindings.validateItem(row),true)
  r.api.sourceUploadAllowed=()=>false
  assert.equal(r.bindings.validateItem(row),false)
  assert.ok(row.errors.includes('当前资料归属或版本没有来源维护权限'))
  r.api.sourceUploadAllowed=()=>true;r.route.query.ownership='ENTERPRISE'
  assert.equal(r.bindings.validateItem(row),false)
  assert.ok(row.errors.includes('此入口仅登记企业资料，请保留企业归属'))
  row.body.ownership='ENTERPRISE';assert.equal(r.bindings.validateItem(row),true)
  row.body.ownership='PROJECT';row.journal={declaration_sha256:'original'}
  assert.equal(r.bindings.validateItem(row),true,'recovered original declaration is never rewritten by the entry mode')
  assert.equal(row.body.ownership,'PROJECT')
 }finally{r.close()}
})

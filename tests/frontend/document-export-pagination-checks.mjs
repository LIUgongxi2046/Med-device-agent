import assert from 'node:assert/strict'
import fs from 'node:fs'
import {test} from 'node:test'
import vm from 'node:vm'
import {fileURLToPath} from 'node:url'
import {parse,compileScript,compileTemplate} from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js'
import ts from '../../web/node_modules/typescript/lib/typescript.js'
import * as Vue from '../../web/node_modules/vue/dist/vue.runtime.esm-bundler.js'
const root=fileURLToPath(new URL('../../',import.meta.url))
const source=fs.readFileSync(root+'web/src/views/medical/document-export.vue','utf8')
const apiSource=fs.readFileSync(root+'web/src/api/medical/exports.ts','utf8')
const {descriptor}=parse(source,{filename:'document-export.vue'})
const compiled=compileScript(descriptor,{id:'export-tests'})
const transpile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
const basis={document_revision:'revision',allowed_formats:['PDF'],review_status:'CONFIRMED'}
const record=(id='export-1')=>({export_id:id,document_id:'document',document_revision:'revision',created_at:'2026-10-18',items:[],revision:'1'})
const page=(number=1,items=[record()],total='21')=>({document_id:'document',document_revision:'revision',page:String(number),size:'20',total,has_more:number*20<Number(total),items})
function setup(overrides={}){
 const props=Vue.reactive({active:true,session:{user:{user_id:'actor'},current_context:{tenant_id:'tenant'}},documentId:'document',documentRevision:'revision'})
 const api={readExportBasis:async()=>basis,readExportHistory:async()=>page(),...overrides}
 const scope=Vue.effectScope();const cleanup=[];const exports={};const revoked=[]
 const context=vm.createContext({exports,AbortController,console,document:{hidden:false},URL:{revokeObjectURL:value=>revoked.push(value)},require(name){
  if(name==='vue')return {...Vue,onBeforeUnmount:fn=>cleanup.push(fn)}
  if(name==='@/api/medical/protocol')return {fail:(code,message)=>{throw Error(code+': '+message)}}
  if(name==='@/api/medical/exports')return api
  if(name==='@/api/medical/request-metadata')return {listRequestMetadata:()=>[],confirmRequestMetadata(){},clearExpiredRequestMetadata(){}}
  throw Error(name)
 }})
 vm.runInContext(transpile(compiled.content),context)
 const b=scope.run(()=>exports.default.setup(props,{expose(){}}))
 return {b,props,api,revoked,close(){cleanup.forEach(fn=>fn());scope.stop()}}
}
test('export component script and template compile',()=>{const result=compileTemplate({id:'export-tests',filename:'document-export.vue',source:descriptor.template.content,compilerOptions:{bindingMetadata:compiled.bindings}});assert.deepEqual(result.errors,[])})
test('fixed validation fields shown without generic artifact link or fabricated coverage',()=>{
 for(const field of ['verified_node_ids','input_sha256','plan_sha256','sha256','size_bytes','converter_revision','font_manifest_revision','channel_check','visual_check'])assert.ok(source.includes('scope.row.validation.'+field),field)
 assert.ok(source.includes('不据此推算全文覆盖率'))
 assert.ok(!source.includes("'/artifacts/'"));assert.ok(source.includes("'未执行'"))
})
test('setup loads actual first page then second; clears prior selection and retry reason',async()=>{
 const calls=[];const r=setup({readExportHistory:async(...args)=>{calls.push(args);return page(args[4]??1,[record('page-'+(args[4]??1))])}})
 try{const b=r.b;await b.loadBasis();assert.equal(b.historyMeta.value.total,'21');assert.equal(b.current.value.export_id,'page-1');b.retryReason.value='old reason'
  await b.loadHistoryPage(2);assert.equal(calls.length,2);assert.equal(calls[1][4],2);assert.equal(calls[1][5],20);assert.equal(b.current.value,null);assert.equal(b.retryReason.value,'');assert.equal(b.history.value[0].export_id,'page-2');assert.equal(b.historyMeta.value.has_more,false)
  b.selectExport(b.history.value[0]);assert.equal(b.current.value.export_id,'page-2')
 }finally{r.close()}
})
test('late page response after document change cannot restore old history',async()=>{
 let finish;const r=setup();try{await r.b.loadBasis();r.api.readExportHistory=()=>new Promise(resolve=>{finish=resolve})
  const pending=r.b.loadHistoryPage(2);r.props.documentRevision='new-revision';finish(page(2));await pending
  assert.equal(r.b.history.value.length,0);assert.equal(r.b.current.value,null);assert.equal(r.b.historyMeta.value,null);assert.equal(r.b.loading.value,false)
 }finally{r.close()}
})
test('failed page read leaves no old actionable detail and allows retry',async()=>{
 const r=setup();try{await r.b.loadBasis();r.api.readExportHistory=async()=>{throw Error('offline')};await assert.rejects(r.b.loadHistoryPage(2),/offline/)
  assert.equal(r.b.current.value,null);assert.equal(r.b.history.value.length,0);assert.equal(r.b.historyMeta.value,null);assert.equal(r.b.historyReadFailed.value,true)
  r.api.readExportHistory=async()=>page(2,[],'20');await r.b.loadHistoryPage(2);assert.equal(r.b.historyReadFailed.value,false);assert.equal(r.b.historyMeta.value.page,'2')
 }finally{r.close()}
})
test('new or recovered export never gets inserted into an unrelated server page',async()=>{
 const r=setup();try{await r.b.loadBasis();r.b.remember(record('new-export'));assert.equal(r.b.history.value.length,1);assert.equal(r.b.history.value[0].export_id,'export-1');assert.equal(r.b.historyMeta.value.total,'21');assert.equal(r.b.historyNeedsRefresh.value,true)
  r.b.remember({...record(),revision:'2'});assert.equal(r.b.history.value[0].revision,'2')
 }finally{r.close()}
})
test('page navigation is blocked during writes and for invalid page bounds',async()=>{
 let reads=0;const r=setup({readExportHistory:async()=>{reads++;return page()}});try{await r.b.loadBasis();r.b.writing.value=true;await r.b.loadHistoryPage(2);r.b.writing.value=false
  for(const n of [0,-1,1.5,1000001])await r.b.loadHistoryPage(n);assert.equal(reads,1)
 }finally{r.close()}
})
function apiHarness(result){const calls=[];const exports={};vm.runInNewContext(transpile(apiSource),{exports,AbortController,Blob,console,require(name){
 if(name==='@/utils/request')return {default:{get:async(url,options)=>{calls.push({url,options});return {resource_revision:null,data:result}}}}
 if(name==='./protocol')return {envelope:value=>value,validate:(_schema,value)=>value,validContext(){},revision:x=>x,fail:(code,msg)=>{throw Error(code+': '+msg)}}
 return {}
 }});return {api:exports,calls}}
const session={current_context:{context_revision:'1'}}
test('real API passes requested page/size and verifies echo',async()=>{
 const h=apiHarness(page(2));await h.api.readExportHistory(session,'document','revision',new AbortController().signal,2,20)
 assert.equal(h.calls[0].options.params.page,2);assert.equal(h.calls[0].options.params.size,20)
})
for(const [name,result] of [['wrong page',{...page(),page:'2'}],['wrong size',{...page(),size:'10'}],['wrong record revision',page(1,[{...record(),document_revision:'other'}])],['duplicate export',page(1,[record(),record()])],['wrong has_more',{...page(),has_more:false}]])test('real API rejects '+name,async()=>{const h=apiHarness(result);await assert.rejects(h.api.readExportHistory(session,'document','revision',new AbortController().signal),/CONTENT_MISMATCH/)})
test('real API rejects invalid pagination before network',async()=>{const h=apiHarness(page());for(const args of [[0,20],[1,0],[1000001,20],[1,51],[1.2,20]])await assert.rejects(h.api.readExportHistory(session,'document','revision',new AbortController().signal,...args),/SCHEMA_INVALID/);assert.equal(h.calls.length,0)})
test('runtime selected second-page item drives retry and execution',async()=>{
 const called=[];const failed={export_item_id:'failed-second',status:'FAILED'};const queued={export_item_id:'queued-second',status:'QUEUED'}
 const selected={...record('second-export'),items:[failed,queued]}
 const r=setup({readExportHistory:async()=>page(2,[selected]),retryExportItem:async(_session,parent,item,reason)=>{called.push(['retry',parent.export_id,item.export_item_id,reason]);return {...selected,items:[]}},executeExportItem:async(_session,item)=>{called.push(['run',item.export_item_id]);return {...selected,items:[]}}})
 try{r.b.basis.value=basis;await r.b.loadHistoryPage(2);r.b.selectExport(r.b.history.value[0]);r.b.retryReason.value='retry second';await r.b.retry(failed)
  r.b.selectExport(selected);await r.b.run(queued)
  assert.deepEqual(called,[['retry','second-export','failed-second','retry second'],['run','queued-second']])
 }finally{r.close()}
})
test('real download API still requires fixed validation and matching bytes/hash',async()=>{
 let reads=0;let digest='expected';const bytes=new Uint8Array([1,2]).buffer;const exports={}
 vm.runInNewContext(transpile(apiSource),{exports,AbortController,Blob,ArrayBuffer,console,require(name){
  if(name==='@/utils/request')return {default:{get:async()=>{reads++;return {status:200,data:bytes}}}}
  if(name==='./protocol')return {validContext(){},fail:(code,msg)=>{throw Error(code+': '+msg)}}
  if(name==='./document-body')return {documentBytesDigest:async()=>digest}
  return {}
 }})
 const item={downloadable:true,status:'SUCCEEDED',format:'PDF',artifact_id:'artifact',validation_artifact_id:'validation',export_item_id:'item',validation:{format:'PDF',size_bytes:'2',sha256:'expected'}}
 await assert.rejects(exports.downloadExportItem(session,{...item,validation:null},new AbortController().signal),/ACTION_STALE/);assert.equal(reads,0)
 const blob=await exports.downloadExportItem(session,item,new AbortController().signal);assert.equal(blob.size,2)
 digest='wrong';await assert.rejects(exports.downloadExportItem(session,item,new AbortController().signal),/CONTENT_MISMATCH/)
})

import { loadModule as loadSourceReturnModule } from '../formal-capability/helpers.mjs'
const sourceReturnApi = (await loadSourceReturnModule('source-return-navigation')).api
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../../package.json',import.meta.url))
const Vue=require('vue'),ts=require('typescript'),{parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const read=name=>fs.readFileSync(new URL('../../src/views/medical/'+name,import.meta.url),'utf8')
const editorSource=read('document-editor.vue'),childSource=read('saved-document-comparison.vue'),directorySource=read('document-revision-directory.vue')
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0')
const transpile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
const tick=async()=>{for(let i=0;i<8;i++){await Vue.nextTick();await Promise.resolve()}}
const defer=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
const snapshot={document_id:id(2),project_id:id(3),document_revision:id(4),revision:'1',allowed_actions:[],body:{sha256:'a'.repeat(64)}}
const saved={snapshot,body:{nodes:[],fields:[],evidence_links:[],fact_usages:[]}}
function compile(source,imports){const result={};vm.runInNewContext(transpile(compileScript(parse(source).descriptor,{id:'parent-child'}).content),{exports:result,require:imports,AbortController,TextEncoder,Error,window:{addEventListener(){},removeEventListener(){}},document:{hidden:false,addEventListener(){},removeEventListener(){}}});return result.default}
function mount(readFailure){
 const props=Vue.reactive({session:{user:{user_id:id(1)},current_context:{tenant_id:id(6),context_revision:'1',auth_epoch:'1',governance_epoch:'1'},action_summary:[]},projectId:id(3),documentId:id(2),active:true,reviewMode:false})
 const user=Vue.reactive({medicalWriting:false}),history=[],pending=defer(),retained=[]
 const imports=path=>{if(path.endsWith('/source-return-navigation'))return sourceReturnApi;if(path==='vue-router')return {useRoute:()=>({path:'',query:{}}),useRouter:()=>({push(){}})};if(path==='vue')return Vue;if(path==='@/store/modules/user')return {default:()=>user};if(path.endsWith('/protocol'))return {fail:code=>{throw Error(code)}}
  if(path.endsWith('/task-workspace'))return {workspaceHistoryRevision:value=>({revision:typeof value==='string'?value:null,issue:''})}
  if(path.endsWith('/documents'))return {readSavedDocument:async()=>saved,readSavedDocumentRevision:async(...args)=>{history.push(args);return pending.promise}}
  if(path.endsWith('/document-body'))return {canonicalDocumentValue:JSON.stringify,DocumentEditBuffer:class{constructor(body){this.body=body;this.dirty=false}invalidate(){}}}
  if(path.endsWith('/document-session-drafts'))return {documentDraftKey:()=>'',resumeDocumentDraft:()=>null,retainDocumentDraft:()=>true}
  if(path.endsWith('/saved-document-comparison'))return {comparisonAvailability:async()=>({features:{document_comparison:true},maximum_request_bytes:4096,maximum_result_bytes:100000,maximum_records:100}),restoreComparison:()=>null,verifyComparisonPending:async()=>{},prepareComparison:async(_s,body)=>({actor:id(1),context:props.session.current_context,key:id(12),body,request_body_sha256:'a'.repeat(64),digest:'b'.repeat(64)}),retainComparison:(_s,p)=>retained.push(p),createComparison:async()=>({comparison_id:id(13)}),readComparison:async()=>{throw readFailure}}
  if(path.endsWith('/document-comparison-display'))return {differencePage:()=>({total:0,filtered:0,page:1,pages:1,items:[]})}
  if(path.endsWith('/document-revision-directory'))return {documentHistoryAvailability:async()=>({features:{document_history:true},maximum_page_size:10}),readDocumentRevisionDirectory:async()=>({kind:'PAGE',page:{document_id:id(2),document_head_revision:'1',current_document_revision:id(4),items:[{document_revision:id(5)}],next_cursor:null,complete:true}})}
  return {}
 }
 const Parent=compile(editorSource,imports),Comparison=compile(childSource,imports),Directory=compile(directorySource,imports);Directory.render=()=>null
 let state,comparison,child
 const renderer=Vue.createRenderer({createElement:tag=>({tag,children:[]}),createText:text=>({text}),createComment:text=>({text}),setText(){},setElementText(){},parentNode(node){return node.parent??null},nextSibling(){return null},patchProp(){},insert(el,parent){el.parent=parent;parent.children.push(el)},remove(el){if(el.parent){el.parent.children=el.parent.children.filter(value=>value!==el);el.parent=null}}})
 const MountedComparison=Vue.defineComponent({props:Comparison.props,setup(p,context){comparison=Comparison.setup(p,context);return()=>comparison.availability.value?.features.document_comparison?Vue.h(Directory,{ref:instance=>{child=instance},session:p.session,snapshot:p.snapshot,active:p.active,disabled:comparison.busy.value,selectionOnly:true,onSelect:comparison.select}):null}})
 const Host=Vue.defineComponent({setup(){state=Parent.setup(props,{expose(){},emit(){}});return()=>state.saved.value?Vue.h(MountedComparison,{session:props.session,snapshot:state.saved.value,active:props.active&&!state.historyVisible.value,disabled:state.writing.value||state.loading.value||state.comparing.value||state.annotationBusy.value||user.medicalWriting||state.historyLoading.value}):null}})
 const app=renderer.createApp(Host);app.mount({children:[]})
 return {props,user,history,pending,retained,get state(){return state},get child(){return child?.$?.setupState},get comparison(){return comparison},close(){app.unmount()}}
}
test('actual parent/comparison/directory mount selects fixed saved versions without any body read or draft overwrite',async()=>{
 const h=mount();try{await tick();const body=h.state.body.value,draft=h.state.draft.value;draft.dirty=true
  h.child.select(id(5));await tick();assert.equal(h.comparison.left.value,id(5));assert.equal(h.comparison.side.value,'right')
  h.child.select(id(5));await tick();assert.equal(h.comparison.right.value,id(5))
  assert.equal(h.history.length,0);assert.equal(h.state.body.value,body);assert.equal(h.state.draft.value,draft);assert.equal(draft.dirty,true);assert.equal(h.state.historyVisible.value,false)
 }finally{h.close()}
})
test('all parent busy states disable nested picker and scope change clears fixed selection',async()=>{
 const h=mount();try{await tick()
  for(const state of [h.state.writing,h.state.loading,h.state.comparing,h.state.annotationMainBusy,h.state.annotationHistoryBusy,h.state.reanchorMainBusy,h.state.reanchorHistoryBusy,h.state.historyLoading]){state.value=true;await tick();h.child.select(id(5));assert.equal(h.comparison.left.value,'');state.value=false;await tick()}
  h.user.medicalWriting=true;await tick();h.child.select(id(5));assert.equal(h.comparison.left.value,'');h.user.medicalWriting=false;await tick()
  h.child.select(id(5));assert.equal(h.comparison.left.value,id(5));h.props.active=false;await tick();assert.equal(h.comparison.left.value,'');assert.equal(h.history.length,0)
 }finally{h.close()}
})
test('real parent/child templates mount explicit comparison selection without body-loading handler',()=>{
 assert.match(editorSource,/<saved-document-comparison[^>]+:snapshot="saved"[^>]+:active="active && !historyVisible"[^>]+:disabled="writing \|\| loading \|\| comparing \|\| annotationBusy \|\| user.medicalWriting \|\| historyLoading"/s)
 assert.match(childSource,/<document-revision-directory[^>]+selection-only[^>]+@select="select"/s)
 for(const [name,source] of [['parent',editorSource],['child',childSource],['picker',directorySource]])assert.deepEqual(compileTemplate({source:parse(source).descriptor.template.content,filename:name+'.vue',id:name}).errors,[])
})

test('local oversized response leaves actual parent dirty draft, session and original comparison request intact',async()=>{
 const protocolSource=fs.readFileSync(new URL('../../src/api/medical/protocol.ts',import.meta.url),'utf8')
 const section=protocolSource.slice(protocolSource.indexOf('export class MedicalFailure'),protocolSource.indexOf('export function revision('))
 const exports={};const events=[]
 vm.runInNewContext(transpile(section),{exports,Error,window:{dispatchEvent:event=>events.push(event)},CustomEvent:class{constructor(type){this.type=type}}})
 const readerSource=fs.readFileSync(new URL('../../src/api/medical/document-comparison-json.ts',import.meta.url),'utf8'),readerExports={}
 let parsed=0
 vm.runInNewContext(transpile(readerSource),{exports:readerExports,require:path=>path==='./protocol'?exports:{readBoundedJson:()=>{parsed++;return {}}},TextEncoder})
 let failure;try{readerExports.readComparisonJson('汉'.repeat(3000000))}catch(error){failure=error}
 assert.equal(failure.code,'CONTEXT_LIMIT');assert.equal(parsed,0);assert.equal(events.length,0)
 const h=mount(failure);try{await tick();const session=h.props.session,body=h.state.body.value,draft=h.state.draft.value;draft.dirty=true
  h.child.select(id(5));h.child.select(id(5));await h.comparison.create();await tick()
  assert.equal(h.retained.length,1);assert.equal(h.comparison.pending.value,h.retained[0]);assert.equal(h.comparison.result.value,null)
  assert.equal(h.props.session,session);assert.equal(h.state.body.value,body);assert.equal(h.state.draft.value,draft);assert.equal(draft.dirty,true)
  assert.equal(h.user.medicalWriting,false);assert.equal(h.history.length,0);assert.equal(events.length,0);assert.match(h.comparison.error.value,/客户端读取容量/)
 }finally{h.close()}
})

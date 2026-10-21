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
const editorSource=read('document-editor.vue'),childSource=read('document-revision-directory.vue')
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0')
const transpile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
const tick=async()=>{for(let i=0;i<8;i++){await Vue.nextTick();await Promise.resolve()}}
const defer=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
const snapshot={document_id:id(2),project_id:id(3),document_revision:id(4),revision:'1',allowed_actions:[],body:{sha256:'a'.repeat(64)}}
const saved={snapshot,body:{nodes:[],fields:[],evidence_links:[],fact_usages:[]}}
function compile(source,imports){const result={};vm.runInNewContext(transpile(compileScript(parse(source).descriptor,{id:'parent-child'}).content),{exports:result,require:imports,AbortController,window:{addEventListener(){},removeEventListener(){}},document:{hidden:false,addEventListener(){},removeEventListener(){}}});return result.default}
function mount(){
 const props=Vue.reactive({session:{user:{user_id:id(1)},current_context:{tenant_id:id(6),context_revision:'1',auth_epoch:'1',governance_epoch:'1'},action_summary:[]},projectId:id(3),documentId:id(2),active:true,reviewMode:false})
 const user=Vue.reactive({medicalWriting:false}),history=[],pending=defer()
 const imports=path=>{if(path==='vue')return Vue;if(path.endsWith('/source-return-navigation'))return sourceReturnApi;if(path==='vue-router')return {useRoute:()=>({path:'',query:{}}),useRouter:()=>({push(){}})};if(path==='@/store/modules/user')return {default:()=>user};if(path.endsWith('/protocol'))return {fail:code=>{throw Error(code)}}
  if(path.endsWith('/task-workspace'))return {workspaceHistoryRevision:value=>({revision:typeof value==='string'?value:null,issue:''})}
  if(path.endsWith('/documents'))return {readSavedDocument:async()=>saved,readSavedDocumentRevision:async(...args)=>{history.push(args);return pending.promise}}
  if(path.endsWith('/document-body'))return {canonicalDocumentValue:JSON.stringify,DocumentEditBuffer:class{constructor(body){this.body=body;this.dirty=false}invalidate(){}}}
  if(path.endsWith('/document-session-drafts'))return {documentDraftKey:()=>'',resumeDocumentDraft:()=>null,retainDocumentDraft:()=>true}
  if(path.endsWith('/document-revision-directory'))return {documentHistoryAvailability:async()=>({features:{document_history:true},maximum_page_size:10}),readDocumentRevisionDirectory:async()=>({kind:'PAGE',page:{document_id:id(2),document_head_revision:'1',current_document_revision:id(4),items:[{document_revision:id(5)}],next_cursor:null,complete:true}})}
  return {}
 }
 const Parent=compile(editorSource,imports),Child=compile(childSource,imports);Child.render=()=>null
 let state,child
 const renderer=Vue.createRenderer({createElement:tag=>({tag,children:[]}),createText:text=>({text}),createComment:text=>({text}),setText(){},setElementText(){},parentNode(node){return node.parent??null},nextSibling(){return null},patchProp(){},insert(el,parent){el.parent=parent;parent.children.push(el)},remove(el){if(el.parent){el.parent.children=el.parent.children.filter(value=>value!==el);el.parent=null}}})
 const Host=Vue.defineComponent({setup(){state=Parent.setup(props,{expose(){},emit(){}});return()=>state.saved.value?Vue.h(Child,{ref:instance=>{child=instance},session:props.session,snapshot:state.saved.value,active:props.active&&!state.historyVisible.value,disabled:state.writing.value||state.loading.value||state.comparing.value||state.annotationBusy.value||user.medicalWriting||state.historyLoading.value,onSelect:state.openAnnotationOriginal}):null}})
 const app=renderer.createApp(Host);app.mount({children:[]})
 return {props,user,history,pending,get state(){return state},get child(){return child?.$?.setupState},close(){app.unmount()}}
}
test('actual parent and mounted directory child read selected fixed version without overwriting dirty draft',async()=>{
 const h=mount();try{await tick();const body=h.state.body.value,draft=h.state.draft.value;draft.dirty=true
  assert.equal(h.history.length,0);h.child.select(id(5));await tick();assert.equal(h.history.length,1);assert.equal(h.history[0][3],id(5));assert.equal(h.state.historyLoading.value,true)
  h.pending.resolve({snapshot:{...snapshot,document_revision:id(5)},body:{nodes:[],fields:[],evidence_links:[],fact_usages:[]}});await tick()
  assert.equal(h.state.historyVisible.value,true);assert.equal(h.state.body.value,body);assert.equal(h.state.draft.value,draft);assert.equal(draft.dirty,true);assert.equal(h.state.saved.value.document_revision,id(4))
 }finally{h.close()}
})
test('mounted child honors parent/global busy states and parent rejects late result after deactivation',async()=>{
 const h=mount();try{await tick();h.user.medicalWriting=true;await tick();h.child.select(id(5));assert.equal(h.history.length,0)
  h.user.medicalWriting=false;h.state.comparing.value=true;await tick();h.child.select(id(5));assert.equal(h.history.length,0)
  h.state.comparing.value=false;await tick();h.child.select(id(5));await tick();assert.equal(h.history.length,1)
  h.props.active=false;await tick();assert.equal(h.history[0][5].aborted,true)
  h.pending.resolve({snapshot:{...snapshot,document_revision:id(5)},body:{}});await tick();assert.equal(h.state.historyDocument.value,null);assert.equal(h.state.historyVisible.value,false)
 }finally{h.close()}
})
test('real parent template connects the mounted event and busy expressions and compiles',()=>{
 assert.match(editorSource,/<document-revision-directory[^>]+@select="openAnnotationOriginal"/s)
 assert.match(editorSource,/:disabled="writing \|\| loading \|\| comparing \|\| annotationBusy \|\| user.medicalWriting \|\| historyLoading"/)
 assert.deepEqual(compileTemplate({source:parse(editorSource).descriptor.template.content,filename:'document-editor.vue',id:'parent'}).errors,[])
})

import { loadModule as loadSourceReturnModule } from '../formal-capability/helpers.mjs'
const sourceReturnApi = (await loadSourceReturnModule('source-return-navigation')).api
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
const require=createRequire(new URL('../../package.json',import.meta.url)),Vue=require('vue'),ts=require('typescript'),{parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const read=name=>fs.readFileSync(new URL('../../src/views/medical/'+name,import.meta.url),'utf8')
const editor=read('document-editor.vue'),gru=read('document-gru-records.vue'),exporter=read('gru-record-export.vue')
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tick=async()=>{for(let i=0;i<10;i++){await Vue.nextTick();await Promise.resolve()}}
const defer=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject}}
const transpile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
function compile(source,imports){const result={};vm.runInNewContext(transpile(compileScript(parse(source).descriptor,{id:'gru-parent-child'}).content),{exports:result,require:imports,AbortController,TextEncoder,Error,URL,Blob,setTimeout,clearTimeout,window:{addEventListener(){},removeEventListener(){}},document:{hidden:false,addEventListener(){},removeEventListener(){}}});return result.default}
function mount(){
 const snapshot={document_id:id(2),project_id:id(3),document_revision:id(4),revision:'1',allowed_actions:[],body:{sha256:'a'.repeat(64)}}
 const saved={snapshot,body:{nodes:[],fields:[],evidence_links:[],fact_usages:[]}}
 const props=Vue.reactive({session:{user:{user_id:id(1)},current_context:{tenant_id:id(6),context_revision:'1',auth_epoch:'1',governance_epoch:'1'},action_summary:[]},projectId:id(3),documentId:id(2),active:true,reviewMode:false})
 const user=Vue.reactive({medicalWriting:false,medicalSession:props.session}),records=new Map(),reads=[],creates=[],basisReads=[],write=defer();let nextKey=20
 const selected=conversation=>({conversation_id:conversation,project_id:id(3),document_id:id(2),document_revision:id(4),scope_snapshot_id:id(7),scope:{},owner_id:id(1),revision:'2',messages:[{message_id:id(8),content:'current page only'}],message_records:[],page_info:{page:1}})
 const imports=path=>{if(path.endsWith('/source-return-navigation'))return sourceReturnApi;if(path==='vue-router')return {useRoute:()=>({path:'',query:{}}),useRouter:()=>({push(){}})};if(path==='vue')return Vue;if(path.endsWith('/gru-keyboard')){const out={};vm.runInNewContext(transpile(fs.readFileSync(new URL('../../src/api/medical/gru-keyboard.ts',import.meta.url),'utf8')),{exports:out});return out;}if(path==='@/store/modules/user')return {default:()=>user};if(path.endsWith('/protocol'))return {fail:code=>{throw Error(code)}}
  if(path.endsWith('/task-workspace'))return {workspaceHistoryRevision:()=>({revision:null,issue:''})}
  if(path.endsWith('/documents'))return {readSavedDocument:async()=>saved,readSavedDocumentRevision:()=>{throw Error('must not read body')}}
  if(path.endsWith('/document-body'))return {canonicalDocumentValue:JSON.stringify,DocumentEditBuffer:class{constructor(body){this.body=body;this.dirty=true}invalidate(){}}}
  if(path.endsWith('/document-session-drafts'))return {documentDraftKey:()=>'',resumeDocumentDraft:()=>null,retainDocumentDraft:()=>true}
  if(path.endsWith('/gru-records'))return {gruAvailability:async()=>true,gruAllowed:()=>true,restoreGruPending:()=>null,restoreGruStale:async()=>null,readGru:async(_s,conversation)=>{reads.push(conversation);return selected(conversation)}}
  if(path.endsWith('/gru-record-export'))return {gruExportAvailability:async()=>true,gruExportAllowed:()=>true,gruExportDownloadAllowed:()=>false,restoreGruExport:(_s,c)=>records.get(c)??null,verifyGruExportPending:async()=>{},readGruExportBasis:async(_s,c)=>{basisReads.push(c);return {conversation_id:c.conversation_id,conversation_revision:c.revision,message_count:3,message_manifest:[{message_id:id(8)},{message_id:id(9)},{message_id:id(10)}]}},gruExportReasonIssue:r=>r?'':'理由',prepareGruExport:async(_s,basis,reason)=>({key:id(nextKey++),body:{basis,reason},body_sha256:'a'.repeat(64)}),retainGruExport:(_s,p)=>records.set(p.body.basis.conversation_id,p),createGruExport:(...args)=>{creates.push(args);return write.promise}}
  return {}
 }
 const Parent=compile(editor,imports),Gru=compile(gru,imports),Export=compile(exporter,imports);let parentState,gruState,exportState
 const renderer=Vue.createRenderer({createElement:tag=>({tag,children:[]}),createText:text=>({text}),createComment:text=>({text}),setText(){},setElementText(){},parentNode:n=>n.parent??null,nextSibling:()=>null,patchProp(){},insert(n,p){n.parent=p;p.children.push(n)},remove(n){if(n.parent){n.parent.children=n.parent.children.filter(x=>x!==n);n.parent=null}}})
 const MountedExport=Vue.defineComponent({props:Export.props,setup(p,c){exportState=Export.setup(p,c);return()=>null}})
 const MountedGru=Vue.defineComponent({props:Gru.props,setup(p,c){gruState=Gru.setup(p,c);return()=>gruState.view.value?Vue.h(MountedExport,{session:p.session,selected:gruState.view.value,active:p.active,disabled:gruState.busy.value||!!gruState.pending.value}):null}})
 const Host=Vue.defineComponent({setup(){parentState=Parent.setup(props,{expose(){},emit(){}});return()=>parentState.saved.value?Vue.h(MountedGru,{session:props.session,snapshot:parentState.saved.value,active:props.active&&!parentState.historyVisible.value,dirty:parentState.hasUnsaved.value,selection:parentState.annotationSelection.value}):null}})
 const app=renderer.createApp(Host);app.mount({children:[]})
 return {props,user,records,reads,creates,basisReads,write,get parent(){return parentState},get gru(){return gruState},get child(){return exportState},close(){app.unmount()}}
}
async function open(h,conversation){h.gru.selected.value=conversation;await h.gru.open();await tick()}
test('actual editor/Gru/export mount consumes selected conversation and complete basis without altering dirty body',async()=>{
 const h=mount();try{await tick();const body=h.parent.body.value,draft=h.parent.draft.value;assert.equal(h.parent.hasUnsaved.value,true)
  await open(h,id(11));await h.child.loadBasis();assert.equal(h.basisReads[0].conversation_id,id(11));assert.equal(h.child.basis.value.message_count,3);assert.equal(h.gru.view.value.messages.length,1)
  assert.equal(h.parent.body.value,body);assert.equal(h.parent.draft.value,draft);assert.equal(h.parent.hasUnsaved.value,true)
 }finally{h.close()}
})
test('changing actual selected conversation clears private export UI and restores old unresolved original when returning',async()=>{
 const h=mount();try{await tick();await open(h,id(11));await h.child.loadBasis();h.child.reason.value='导出';h.child.confirmed.value=true;const creating=h.child.create();await tick();assert.equal(h.creates.length,1)
  h.write.reject(new Error('unknown'));await creating;const original=h.records.get(id(11));assert.ok(original)
  await open(h,id(12));assert.equal(h.child.pending.value,null);assert.equal(h.child.basis.value,null);assert.equal(h.records.get(id(11)),original)
  await open(h,id(11));assert.equal(h.child.pending.value,original);assert.equal(h.creates.length,1);assert.equal(h.parent.hasUnsaved.value,true)
 }finally{h.close()}
})
test('actual nested component respects Gru/global lock and does not clear original on local capacity rejection',async()=>{
 const h=mount();try{await tick();await open(h,id(11));h.user.medicalWriting=true;await tick();await h.child.loadBasis();assert.equal(h.basisReads.length,0)
  h.user.medicalWriting=false;await tick();await h.child.loadBasis();h.child.reason.value='导出';h.child.confirmed.value=true;const creating=h.child.create();await tick()
  const original=h.records.get(id(11));const session=h.props.session;const failure=Object.assign(new Error('客户端容量不足'),{name:'MedicalFailure',code:'CONTEXT_LIMIT'});h.write.reject(failure);await creating
  assert.equal(h.child.pending.value,original);assert.equal(h.records.get(id(11)),original);assert.equal(h.props.session,session);assert.equal(h.parent.hasUnsaved.value,true);assert.equal(h.user.medicalWriting,false)
 }finally{h.close()}
})
test('actual templates mount real selected view and keep export out of draft processing',()=>{
 assert.ok(gru.includes('<gru-record-export v-if="view" :session="session" :selected="view" :active="active" :disabled="busy || !!pending"'))
 for(const [name,source]of [['editor',editor],['gru',gru],['export',exporter]])assert.deepEqual(compileTemplate({source:parse(source).descriptor.template.content,filename:name+'.vue',id:name}).errors,[])
})

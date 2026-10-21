import { loadModule as loadNavigationModule } from '../../web/tests/formal-capability/helpers.mjs'
const sourceReturnApi = (await loadNavigationModule('source-return-navigation')).api
const evidenceNavigationApi = (await loadNavigationModule('document-evidence-navigation')).api
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import {test} from 'node:test'
import * as Vue from '../../web/node_modules/vue/dist/vue.runtime.esm-bundler.js'
import ts from '../../web/node_modules/typescript/lib/typescript.js'
import {parse,compileScript} from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js'
const read=p=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8'),id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0')
function mount(){
 const calls=[],drafts=[],snapshot={document_id:id(3),project_id:id(2),document_revision:id(4),revision:'1',allowed_actions:[],body:{sha256:'a'.repeat(64)}},body={fields:[],evidence_links:[],nodes:[]}
 const props=Vue.reactive({session:{user:{user_id:id(1)},current_context:{tenant_id:id(7),context_revision:'1'},action_summary:[]},projectId:id(2),documentId:id(3),active:true,reviewMode:false})
 const user=Vue.reactive({medicalWriting:false}),cache={}
 const stub={render:()=>null}
 function require(path){
  if(path.endsWith('/source-return-navigation'))return sourceReturnApi;if(path.endsWith('/document-evidence-navigation'))return evidenceNavigationApi;if(path==='vue-router')return {useRoute:()=>({path:'',query:{}}),useRouter:()=>({push(){}})};if(path==='vue')return Vue
  if(path==='@/store/modules/user')return {default:()=>user}
  if(path.endsWith('/protocol'))return {fail:code=>{throw Error(code)}}
  if(path.endsWith('/document-body'))return {canonicalDocumentValue:JSON.stringify,DocumentEditBuffer:class {constructor(body){this.body=body;this.dirty=false;drafts.push(this)}invalidate(){}}}
  if(path.endsWith('/document-session-drafts'))return {documentDraftKey:()=>'',resumeDocumentDraft:()=>null,retainDocumentDraft:()=>true}
  if(path.endsWith('/documents'))return {readSavedDocument:async()=>{calls.push('readDocument');return {snapshot,body}}}
  if(path.endsWith('/task-workspace'))return {workspaceHistoryRevision:()=>({revision:null,issue:''})}
  if(path.endsWith('/document-technical-check'))return {technicalAllowed:()=>false,technicalAvailability:async()=>{calls.push('availability');return false}}
  if(path==='./document-technical-check.vue')return {default:component('document-technical-check')}
  if(path.endsWith('.vue'))return {default:stub}
  return {}
 }
 function component(name){if(cache[name])return cache[name];const {descriptor}=parse(read('web/src/views/medical/'+name+'.vue')),out={}
  const compiled=compileScript(descriptor,{id:name,inlineTemplate:true}).content
  vm.runInNewContext(ts.transpileModule(compiled,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,{exports:out,require,AbortController,document:{hidden:false,addEventListener(){},removeEventListener(){}},window:{addEventListener(){},removeEventListener(){}}})
  return cache[name]=out.default
 }
 const renderer=Vue.createRenderer({patchProp(el,k,p,n){el[k]=n},insert(el,parent,anchor){if(el.parent){const old=el.parent.children.indexOf(el);if(old>=0)el.parent.children.splice(old,1)}parent.children??=[];const at=anchor?parent.children.indexOf(anchor):-1;if(at>=0)parent.children.splice(at,0,el);else parent.children.push(el);el.parent=parent},remove(el){if(el?.parent){const at=el.parent.children.indexOf(el);if(at>=0)el.parent.children.splice(at,1)}},createElement:type=>({type,children:[]}),createText:text=>({text}),createComment:text=>({text}),setText(el,text){el.text=text},setElementText(el,text){el.text=text},parentNode:el=>el.parent,nextSibling:el=>{if(!el?.parent)return null;return el.parent.children[el.parent.children.indexOf(el)+1]??null}})
 const app=renderer.createApp({render:()=>Vue.h(component('document-editor'),props)});app.config.warnHandler=()=>{}
 const root=app.mount({children:[]})
 function find(v){if(!v)return null;if(v.component?.type===cache['document-technical-check'])return v.component;if(v.component){const value=find(v.component.subTree);if(value)return value}if(Array.isArray(v.children))for(const child of v.children){const value=find(child);if(value)return value}return null}
 return {props,calls,drafts,snapshot,body,app,child:()=>find(root.$.subTree),async ready(){for(let i=0;i<8;i++){await Vue.nextTick();await Promise.resolve()}}}
}
test('actual editor mounts real technical panel with fixed saved data and default-disabled server gate',async()=>{const r=mount();try{await r.ready();const child=r.child();assert.ok(child);assert.equal(child.props.snapshot,r.snapshot);assert.equal(child.props.active,true);assert.equal(child.props.dirty,false);assert.equal(child.props.isCurrent,true);assert.deepEqual(r.calls,['readDocument','availability'])}finally{r.app.unmount()}})
test('technical completed event cannot reload or replace editor body or advance review',async()=>{const r=mount();try{await r.ready();const child=r.child(),draft=r.drafts[0];child.emit('completed',{technical_result:'PASS',professional_coverage:'UNKNOWN',overall_review_ready:false});await r.ready();assert.equal(r.drafts.length,1);assert.equal(r.drafts[0],draft);assert.equal(draft.body,r.body);assert.deepEqual(r.calls,['readDocument','availability'])}finally{r.app.unmount()}})
test('inactive parent clears and unmounts technical panel without starting another check',async()=>{const r=mount();try{await r.ready();r.props.active=false;await r.ready();assert.equal(r.child(),null);assert.deepEqual(r.calls,['readDocument','availability'])}finally{r.app.unmount()}})

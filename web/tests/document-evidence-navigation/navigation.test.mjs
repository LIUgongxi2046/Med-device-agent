import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import * as Vue from 'vue'
import ts from 'typescript'
import {parse,compileScript} from '@vue/compiler-sfc'
import {loadModule} from '../formal-capability/helpers.mjs'
const navigation=(await loadModule('document-evidence-navigation')).api
const selection={orderedDocumentNodes:body=>body.nodes.map(node=>({node,depth:node.parent_id?1:0}))}
const node=(id,parent,kind,title,links=[])=>({node_id:id,parent_id:parent,ordinal:0,field_ids:[],evidence_link_ids:links,content:kind==='TEXT'?{kind,text:title,marks:[]}:{kind,title}})
const fixture=()=>({nodes:[node('doc',null,'DOCUMENT','Document'),node('one','doc','SECTION','First'),node('text','one','TEXT','Real text',['a','missing']),node('two','doc','SECTION','Second'),node('empty','doc','SECTION','Empty')],evidence_links:[{evidence_link_id:'a',node_ids:['text','two','missing-node'],relation:'SUPPORT',evidence:{source_version:'source-a'}},{evidence_link_id:'b',node_ids:[],relation:'BACKGROUND',evidence:{source_version:'source-b'}}],fields:[],fact_usages:[]})
test('uses real reciprocal or one-sided relationships, deduplicates and never infers from content',()=>{
 const body=fixture();assert.deepEqual(Array.from(navigation.chapterEvidence(body,'one'),x=>x.evidence_link_id),['a'])
 assert.deepEqual(Array.from(navigation.evidenceChapters(body,body.evidence_links[0]),x=>x.node_id),['one','two'])
 assert.equal(navigation.chapterEvidence(body,'empty').length,0);assert.equal(navigation.chapterEvidence(body,'doc').length,0)
 body.evidence_links[0].node_ids=[];assert.equal(navigation.chapterEvidence(body,'one').length,1);assert.equal(navigation.chapterEvidence(body,'two').length,0)
 body.nodes[2].evidence_link_ids=[];assert.equal(navigation.chapterEvidence(body,'one').length,0)
})
function mount(options={}){
 const components={};const events=[];const props=Vue.reactive({body:fixture(),documentRevision:'saved-revision',active:true,dirty:true,editable:true,...options,onOpenEvidence:(...args)=>events.push(args)})
 function compile(name){const filename=new URL('../../src/views/medical/'+name+'.vue',import.meta.url).pathname;const {descriptor}=parse(readFileSync(filename,'utf8'),{filename});const content=compileScript(descriptor,{id:name,inlineTemplate:true}).content;const exports={}
 vm.runInNewContext(ts.transpileModule(content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:key=>key==='vue'?Vue:key.endsWith('document-node-editor.vue')?{default:components.node}:key.endsWith('/document-evidence-navigation')?navigation:key.endsWith('/document-selection')?selection:key.endsWith('/annotation-domain')?{selectionSummary:()=>''}:{},document:{hidden:false,addEventListener(){},removeEventListener(){}},window:{addEventListener(){},removeEventListener(){}},AbortController,console});return exports.default}
 components.node=compile('document-node-editor');const reader=compile('document-body-reader')
 const make=(tag,text='')=>({tag,text,children:[],props:{},style:{},setAttribute(){},removeAttribute(){},scrollIntoView(){},focus(){},querySelectorAll(){return []}})
 const renderer=Vue.createRenderer({createElement:tag=>make(tag),createText:text=>make('#text',text),createComment:text=>make('#comment',text),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,parentNode:n=>n.parent,nextSibling:()=>null,patchProp:(n,k,p,v)=>n.props[k]=v,insert(n,p,anchor){n.parent=p;const i=p.children.indexOf(anchor);if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove(n){if(n.parent)n.parent.children=n.parent.children.filter(x=>x!==n)}})
 const app=renderer.createApp({render:()=>Vue.h(reader,props)});app.config.warnHandler=()=>{}
 for(const tag of ['el-button','el-input','el-form','el-form-item','el-select','el-option','el-input-number'])app.component(tag,{inheritAttrs:false,setup(_,{attrs,slots}){return()=>Vue.h(tag==='el-button'?'button':'div',attrs,slots.default?.())}})
 app.component('DocumentNodeEditor',components.node)
 const root=make('root');app.mount(root)
 const all=()=>{const out=[];function walk(n){out.push(n);n.children.forEach(walk)}walk(root);return out}
 return {root,all,props,events,close:()=>app.unmount()}
}
const text=n=>n.text+n.children.map(text).join('')
test('mounted real recursive reader renders collapsed chapter-end links, zero state and emits exact link/chapter without editing draft',async()=>{
 const h=mount();try{
 const citations=h.all().filter(n=>n.props['data-chapter-evidence']);assert.equal(citations.length,4)
 const first=citations.find(n=>n.props['data-chapter-evidence']==='one');assert.match(text(first),/引用 1 项/);assert.equal(first.props.open,undefined)
 assert.match(text(citations.find(n=>n.props['data-chapter-evidence']==='empty')),/本章暂无关联引用/)
 assert.match(text(first),/不代表已完成专业核验/)
 const link=h.all().find(n=>n.tag==='button'&&text(n).includes('source-a'));link.props.onClick();assert.equal(h.events[0][0].evidence_link_id,'a');assert.equal(h.events[0][1],'one')
 const original=h.props.body;h.all().find(n=>n.tag==='button'&&text(n)==='编辑正文').props.onClick();await Vue.nextTick();h.all().find(n=>n.tag==='button'&&text(n)==='连续阅读').props.onClick();await Vue.nextTick();assert.equal(h.props.body,original);assert.equal(h.props.dirty,true);assert.equal(h.props.documentRevision,'saved-revision')
 h.props.body={...fixture(),evidence_links:[]};await Vue.nextTick();assert.equal(h.all().filter(n=>n.tag==='button'&&text(n).includes('source-a')).length,0)
 }finally{h.close()}
})

test('mounted read-only reader keeps citation controls without exposing edit mode',()=>{
 const h=mount({editable:false,dirty:false});try{
 assert.equal(h.all().some(n=>n.tag==='button'&&text(n)==='编辑正文'),false)
 assert.equal(h.all().some(n=>n.tag==='button'&&text(n).includes('source-a')),true)
 }finally{h.close()}
})
test('nearest nested section owns a citation; document fallback and cyclic orphan paths are safe',()=>{
 const body=fixture();body.nodes.push(node('nested','one','SECTION','Nested'),node('nested-text','nested','TEXT','Text',['b']),node('document-text','doc','TEXT','Document text',['b']),node('cycle-a','cycle-b','TEXT','A',['b']),node('cycle-b','cycle-a','TEXT','B'))
 assert.deepEqual(Array.from(navigation.evidenceChapters(body,body.evidence_links[1]),x=>x.node_id),['nested','doc'])
 assert.equal(navigation.chapterEvidence(body,'one').length,1)
 assert.equal(navigation.evidenceChapters(body,{evidence_link_id:'missing',node_ids:['one']}).length,0)
})

import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
const require=createRequire(new URL('../../package.json',import.meta.url)),ts=require('typescript'),Vue=require('vue'),{parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const read=name=>fs.readFileSync(new URL('../../src/views/medical/'+name,import.meta.url),'utf8')
const transpile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
const helper={};vm.runInNewContext(transpile(fs.readFileSync(new URL('../../src/api/medical/gru-keyboard.ts',import.meta.url),'utf8')),{exports:helper})
const event=(values={})=>({key:'Enter',shiftKey:false,ctrlKey:false,metaKey:false,altKey:false,isComposing:false,keyCode:13,repeat:false,...values})
test('IME composition, Safari 229, trailing composition Enter, repeats and Shift+Enter do not submit',()=>{
 const keys=helper.createGruComposerKeys();assert.equal(keys.shouldSubmit(event()),true)
 keys.start();assert.equal(keys.shouldSubmit(event()),false);keys.end();assert.equal(keys.shouldSubmit(event()),false)
 keys.keyup(event());assert.equal(keys.shouldSubmit(event()),true)
 for(const flags of [{shiftKey:true},{isComposing:true},{keyCode:229},{repeat:true},{ctrlKey:true},{metaKey:true}])assert.equal(keys.shouldSubmit(event(flags)),false)
})
test('registry stores only current entry and unregisters old owner without removing new owner',()=>{
 let opened=0,closed=0;assert.equal(helper.openRegisteredGru(),false)
 const first=helper.registerGruEntry({open:()=>opened++,close:()=>closed++});assert.equal(helper.openRegisteredGru(),true)
 const second=helper.registerGruEntry({open:()=>opened+=2,close:()=>closed++});first();helper.openRegisteredGru();assert.equal(opened,3);assert.equal(closed,1);second();assert.equal(helper.openRegisteredGru(),false)
})
test('all changed Vue templates compile and panel escapes closed ancestors through Teleport',()=>{
 for(const name of ['document-gru-records.vue','gru-panel.vue','gru-global-entry.vue']){const descriptor=parse(read(name)).descriptor;compileScript(descriptor,{id:name});assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:name,id:name}).errors,[])}
 assert.match(read('gru-panel.vue'),/<Teleport to="body">/);assert.doesNotMatch(read('document-gru-records.vue'),/<details class="gru-records"/)
 assert.match(read('gru-panel.vue'),/trigger\?\.isConnected/);assert.match(read('gru-global-entry.vue'),/event\.preventDefault\(\);open\(\)/)
})
function mount(){
 const source=read('document-gru-records.vue'),out={},session={user:{user_id:'actor'},current_context:{tenant_id:'t'},action_summary:[]}
 const props=Vue.reactive({session,snapshot:{project_id:'p',document_id:'d',document_revision:'r'},active:true,dirty:false})
 const user=Vue.reactive({medicalSession:props.session,medicalWriting:false});let resolveWrite,writes=0
 const write=new Promise(resolve=>resolveWrite=resolve),store=new Map()
 const imports=path=>{if(path==='vue')return Vue;if(path==='@/store/modules/user')return {default:()=>user};if(path.endsWith('/gru-keyboard'))return helper
 if(path.endsWith('/content-digest'))return {sourceDigest:async()=> 'digest'}
 if(path.endsWith('/document-body'))return {canonicalDocumentValue:JSON.stringify}
 if(path.endsWith('/gru-records'))return {gruAvailability:async()=>true,gruAllowed:()=>true,gruInputIssues:()=>[],restoreGruPending:()=>null,restoreGruStale:async()=>null,saveGruPending:(_s,_d,p)=>store.set(p.key,p),clearGruPending:()=>store.clear(),writeGru:()=>{writes++;return write}}
 return {}}
 vm.runInNewContext(transpile(compileScript(parse(source).descriptor,{id:'actual-gru'}).content),{exports:out,require:imports,crypto:{randomUUID:()=> 'key'},AbortController,TextEncoder,window:{addEventListener(){},removeEventListener(){}},document:{hidden:false,addEventListener(){},removeEventListener(){}}})
 let state;const renderer=Vue.createRenderer({createComment:()=>({}),insert(){},remove(){},parentNode(){},nextSibling(){}})
 const app=renderer.createApp(Vue.defineComponent({setup(){state=out.default.setup(props,{expose(){}});return()=>null}}));app.mount({})
 return {get state(){return state},get writes(){return writes},resolveWrite,close:()=>app.unmount()}
}
const tick=async()=>{for(let i=0;i<8;i++){await Vue.nextTick();await Promise.resolve()}}
for(const edited of [false,true])test(`actual composer shares validation/idempotency and ${edited?'preserves newer':'clears submitted'} draft`,async()=>{
 const h=mount();try{await tick();const s=h.state;s.view.value={conversation_id:'c',document_revision:'r',revision:'1',scope_snapshot_id:'scope',scope:{}};s.text.value='original';s.messageReason.value='reason'
 const pending=s.append();await tick();assert.equal(h.writes,1);await s.append();assert.equal(h.writes,1)
 if(edited){s.text.value='next draft';s.messageReason.value='next reason'}
 h.resolveWrite({conversation_id:'c',message_id:'m'});await pending;assert.equal(s.text.value,edited?'next draft':'');assert.equal(s.messageReason.value,edited?'next reason':'')
 }finally{h.close()}
})
test('panel opening focuses panel; closing restores connected trigger; detached trigger is ignored',async()=>{
 const out={},props=Vue.reactive({open:false,label:'Gru'});let focused=0,panelFocused=0
 class Element {isConnected=true;focus(){focused++}closest(){return null}getClientRects(){return [{}]}}
 const trigger=new Element(),document={activeElement:trigger},route=Vue.reactive({fullPath:'/medical/document'})
 vm.runInNewContext(transpile(compileScript(parse(read('gru-panel.vue')).descriptor,{id:'focus'}).content),{exports:out,require:path=>path==='vue'?Vue:{useRoute:()=>route},document,HTMLElement:Element})
 let state;const renderer=Vue.createRenderer({createComment:()=>({}),insert(){},remove(){},parentNode(){},nextSibling(){}})
 const app=renderer.createApp(Vue.defineComponent({setup(){state=out.default.setup(props,{expose(){},emit(){}});return()=>null}}));app.mount({})
 try{state.panel.value={focus(){panelFocused++}};props.open=true;await tick();assert.equal(panelFocused,1);props.open=false;assert.equal(focused,1)
 props.open=true;props.open=false;props.open=true;await tick();assert.equal(panelFocused,2);assert.equal(focused,2);route.fullPath='/medical/other';props.open=false;assert.equal(focused,2);props.open=true;trigger.isConnected=false;props.open=false;await tick();assert.equal(focused,2);assert.equal(panelFocused,2)}finally{app.unmount()}
})
test('keyboard submission uses same empty-reason and busy guards as button',async()=>{
 const h=mount();try{await tick();const s=h.state;s.view.value={conversation_id:'c',document_revision:'r',revision:'1',scope_snapshot_id:'scope',scope:{}};s.text.value='message';let prevented=0
 s.composerKeydown({...event(),preventDefault(){prevented++}});await tick();assert.equal(h.writes,0);assert.equal(prevented,1)
 s.messageReason.value='reason';s.composerKeys.start();s.composerKeydown({...event(),preventDefault(){prevented++}});await tick();assert.equal(h.writes,0)
 s.composerKeys.end();s.composerKeys.keyup(event());s.composerKeydown({...event(),preventDefault(){prevented++}});await tick();assert.equal(h.writes,1)
 s.composerKeydown({...event(),preventDefault(){}});assert.equal(h.writes,1);h.resolveWrite({conversation_id:'c'});await tick()
 }finally{h.close()}
})

import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import * as Vue from 'vue'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { loadModule, id, session } from '../formal-capability/helpers.mjs'
const nav = (await loadModule('source-return-navigation')).api
const chapterNavigation = (await loadModule('document-evidence-navigation')).api
const path = `/projects/${id(3)}/tasks/${id(4)}/documents/${id(5)}/write`
const link = { evidence_link_id:id(8), node_ids:[id(9)], relation:'SUPPORT', evidence:{tenant_id:id(2),source_id:id(10),source_version:id(11),parse_version:id(12),chunk_id:id(13),quote_hash:'q',locator:{page:1}} }
const body = { nodes:[{node_id:id(14),parent_id:null,evidence_link_ids:[],content:{kind:'SECTION',title:'Chapter'}},{node_id:id(9),parent_id:id(14),evidence_link_ids:[id(8)],content:{kind:'TEXT',text:'Quote'}}], fields:[], evidence_links:[link] }
const snapshot = {document_id:id(5),project_id:id(3),document_revision:id(6),allowed_actions:[]}
const tick = async()=>{for(let i=0;i<14;i++)await Promise.resolve();await Vue.nextTick()}
function mount(name, props, route, modules={}, emit=()=>{}) {
 const filename = new URL(`../../src/views/medical/${name}.vue`,import.meta.url).pathname
 const {descriptor}=parse(readFileSync(filename,'utf8'),{filename}), compiled=compileScript(descriptor,{id:name})
 assert.deepEqual(compileTemplate({source:descriptor.template.content,filename,id:name,compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
 const imports = {'vue-router':{useRoute:()=>route,useRouter:()=>({push(){}})},'@/store/modules/user':{default:()=>({medicalSession:props.session,medicalWriting:false})},
 '@/api/medical/source-return-navigation':nav,'@/api/medical/document-evidence-navigation':chapterNavigation,'@/api/medical/protocol':{fail:c=>{throw Error(c)}},
 '@/api/medical/task-workspace':{workspaceHistoryRevision:()=>({revision:null,issue:''})},
 '@/api/medical/document-body':{canonicalDocumentValue:JSON.stringify,documentBytesDigest:async()=> 'hash',DocumentEditBuffer:class {constructor(value){this.body=value;this.dirty=false}invalidate(){}}},
 '@/api/medical/document-session-drafts':{documentDraftKey:()=>'',resumeDocumentDraft:()=>null,retainDocumentDraft:()=>true},
 '@/api/medical/documents':{readSavedDocument:async()=>({snapshot,body})},...modules}
 const exports={};vm.runInNewContext(ts.transpileModule(compiled.content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,{exports,require:key=>key==='vue'?Vue:imports[key]??{},AbortController,TextEncoder,document:{hidden:false,addEventListener(){},removeEventListener(){}},window:{addEventListener(){},removeEventListener(){}}})
 const events=[];let state;const renderer=Vue.createRenderer({createComment:()=>({}),insert(){},remove(){},parentNode(){},nextSibling(){}})
 const app=renderer.createApp({setup(){state=exports.default.setup(props,{expose(){},emit:(...args)=>{events.push(args);emit(...args)}});return()=>null}});app.mount({})
 return {state,events,close:()=>app.unmount()}
}
function editor(s, query={}) {const route=Vue.reactive({path,query}), props=Vue.reactive({session:s,projectId:id(3),taskId:id(4),documentId:id(5),active:true,reviewMode:false});const h=mount('document-editor',props,route);const focus=[];h.state.documentReader.value={locate:value=>focus.push(value)};return {...h,route,props,focus}}
test('mounted editor → evidence → source reader → fresh authorized editor restores exact revision/chapter/focus',async()=>{
 const s=session(), start=editor(s);await tick()
 const evidence=mount('document-evidence-panel',{session:s,links:[link],documentRevision:id(6),active:true,returnOrigin:start.state.sourceReturnOrigin},{}, {'@/api/medical/source-governance':{readSourceGovernance:async()=>({source_id:id(10),reviews:[{parse_version:id(12)}],rights_purpose:'VERIFICATION'}),readSourceContent:async()=>({representation:'PARSED',source_id:id(10),items:[{quote_hash:'q',locator:{page:1},raw_text:'checked'}]})}})
 let reader,returned
 try {
 await evidence.state.read(link);const destination=evidence.state.locationRoute.value;assert.ok(destination.query.source_return)
 reader=mount('source-governance',{session:s},{params:{sourceVersion:id(11)},query:destination.query},{'@/api/medical/source-governance':{readSourceGovernance:()=>new Promise(()=>{})}})
 reader.state.view.value={source_version:id(11)}
 const back=reader.state.documentReturnRoute.value;assert.equal(back.path,path)
 returned=editor(s,back.query);await tick();assert.deepEqual(returned.focus,[id(9)]);assert.match(returned.state.sourceReturnNotice.value,/同一固定文稿版本/)
 assert.equal(returned.state.body.value,body);assert.equal(returned.state.saved.value.document_revision,id(6))
 } finally {returned?.close();reader?.close();evidence.close();start.close()}
})
test('mounted destination refuses changed versions and preserves dirty content',async()=>{
 for(const dirty of [false,true]){const s=session(),h=editor(s);await tick();try{
 const token=nav.createSourceReturn(s,id(11),{path,documentRevision:dirty?id(6):id(7),chapterId:id(14),focusNodeId:id(9)})
 if(dirty){h.state.fieldInputs.value={local:{original_value:'keep me'}}}
 h.route.query={source_return:token};await tick()
 assert.deepEqual(h.focus,[]);assert.equal(h.state.body.value,body)
 assert.match(h.state.sourceReturnNotice.value,dirty?/未保存/:/版本或引用位置已经变化/)
 if(dirty)assert.equal(h.state.fieldInputs.value.local.original_value,'keep me')
 }finally{h.close()}}
})
test('untrusted URLs/tokens, project/document/mode changes, scope changes, source mismatch and replay fail closed',()=>{
 const s=session(),origin={path,documentRevision:id(6),chapterId:id(14),focusNodeId:id(9)}
 for(const bad of ['https://evil.example','//evil.example',path+'?redirect=evil',path+'#x','/projects/../library',path.replace('/write','/other')])assert.equal(nav.createSourceReturn(s,id(11),{...origin,path:bad}),null)
 const token=nav.createSourceReturn(s,id(11),origin)
 assert.equal(nav.sourceReturnRoute(s,id(11),[token]),null);assert.equal(nav.sourceReturnRoute(s,id(99),token),null)
 const current={path,documentRevision:id(6),currentRevision:id(6),active:true,dirty:false,nodeIds:[id(14),id(9)]}
 for(const wrong of [path.replace(id(3),id(99)),path.replace(id(5),id(99)),path.replace('/write','/review')])assert.equal(nav.consumeSourceReturn(s,token,{...current,path:wrong}).status,'invalid')
 assert.equal(nav.consumeSourceReturn(s,token,current).status,'ready');assert.equal(nav.consumeSourceReturn(s,token,current).status,'invalid')
 for(const mutate of [x=>x.user.user_id=id(90),x=>x.current_context.tenant_id=id(90),x=>x.current_context.auth_epoch='2',x=>x.current_context.governance_epoch='2',x=>x.action_summary=[]]){const other=session(),t=nav.createSourceReturn(other,id(11),origin);mutate(other);assert.equal(nav.sourceReturnRoute(other,id(11),t),null)}
})
test('ephemeral context expiry, explicit invalidation and missing nodes never revive navigation',async()=>{
 let now=0;const listeners=new Map()
 const isolated=(await loadModule('source-return-navigation',{globals:{Date:class extends Date {static now(){return now}},window:{addEventListener:(name,fn)=>listeners.set(name,fn)}}})).api
 const s=session(),origin={path,documentRevision:id(6),chapterId:id(14),focusNodeId:id(9)},current={path,documentRevision:id(6),currentRevision:id(6),active:true,dirty:false,nodeIds:[id(14),id(9)]}
 const expired=isolated.createSourceReturn(s,id(11),origin);now=30*60*1000
 assert.equal(isolated.sourceReturnRoute(s,id(11),expired),null)
 const revoked=isolated.createSourceReturn(s,id(11),origin);listeners.get('medical-context-invalidated')()
 assert.equal(isolated.sourceReturnRoute(s,id(11),revoked),null)
 const blocked=isolated.createSourceReturn(s,id(11),origin)
 assert.equal(isolated.consumeSourceReturn(s,blocked,{...current,active:false}).status,'blocked')
 assert.equal(isolated.consumeSourceReturn(s,blocked,{...current,nodeIds:[id(14)]}).status,'stale')
 assert.equal(isolated.consumeSourceReturn(s,blocked,current).status,'invalid')
})

test('mounted evidence reverse navigation reaches actual chapter, rejects unrelated links and preserves dirty draft',async()=>{
 const s=session(),h=editor(s);await tick();const reverse=[]
 const evidence=mount('document-evidence-panel',{session:s,body,links:[link],documentRevision:id(6),active:true},{},{},(event,...args)=>{reverse.push(event);if(event==='locateChapter')h.state.locateEvidenceChapter(...args)})
 try {
 h.state.fieldInputs.value={local:{original_value:'keep me'}}
 evidence.state.locateChapter(link,id(14));assert.deepEqual(h.focus,[id(14)]);assert.equal(h.state.fieldInputs.value.local.original_value,'keep me');assert.match(h.state.locationNotice.value,/未保存修改保持不变/)
 evidence.state.locateChapter(link,id(9));evidence.state.locateChapter({...link,evidence_link_id:'forged'},id(14));assert.equal(reverse.length,1)
 h.state.historyVisible.value=true;evidence.state.locateChapter(link,id(14));assert.equal(h.focus.length,1)
 h.state.historyVisible.value=false;h.state.currentSnapshot.value={...snapshot,document_revision:id(99)};evidence.state.locateChapter(link,id(14));assert.equal(h.focus.length,1)
 }finally{evidence.close();h.close()}
})
test('mounted chapter open validates chapter membership and never reads source for dirty or unrelated targets',async()=>{
 const s=session(),h=editor(s);await tick();const reads=[];h.state.fieldEvidenceReader.value={read:async link=>reads.push(link)}
 try {
 await h.state.openChapterEvidence(link,id(9));assert.equal(reads.length,0)
 await h.state.openChapterEvidence(link,id(14));assert.equal(reads.length,1);assert.equal(h.state.sourceReturnOrigin(link).chapterId,id(14))
 h.state.fieldInputs.value={local:{original_value:'keep me'}};await h.state.openChapterEvidence(link,id(14));assert.equal(reads.length,1);assert.equal(h.state.fieldInputs.value.local.original_value,'keep me')
 }finally{h.close()}
})
function annotations(options={}) {
 const calls=[], props=Vue.reactive({session:session(),snapshot:{...snapshot,revision:'1',signature_ids:[]},baseline:body,selection:null,active:true,dirty:false,busy:false,history:false,effective:true,isCurrent:true})
 let allowed=true
 const item={annotation_id:id(31),document_revision:id(6),content:'private text not in return token'}
 const listing=()=>({document_id:id(5),document_revision:id(6),revision:'1',items:[item]})
 const h=mount('document-annotations',props,{}, {
  '@/api/medical/annotation-domain':{annotationAllowed:actual=>allowed && actual.current_context.auth_epoch === '1',annotationDraftKey:()=>'',resumeAnnotationDraft:()=>null,readPendingAnnotation:()=>null,retainAnnotationDraft(){},annotationStatusText:{},decisionTransition:()=>false},
  '@/api/medical/annotations':{readAnnotationThreads:async(...args)=>{calls.push('threads');return options.read && calls.filter(value=>value==='threads').length > 1 ? options.read(...args):listing()},readAnnotationReplies:async()=>{calls.push('replies');return {annotation_id:id(31),document_revision:id(6),items:[]}},readAnnotationDecisions:async()=>{calls.push('decisions');return {annotation_id:id(31),items:[]}}}
 })
 return {...h,props,calls,item,revoke:()=>{allowed=false;props.session.current_context.auth_epoch='2'}}
}
test('mounted selected annotation survives ID-only token and freshly authorized restoration without writes',async()=>{
 const original=annotations(),returned=annotations(),s=session(),start=editor(s);await tick()
 try {
 assert.equal(await original.state.restoreSelected(id(31),id(6)),true)
 const selection=original.events.filter(event=>event[0]==='selected-thread').at(-1)[1]
 assert.deepEqual(JSON.parse(JSON.stringify(selection)),{annotationId:id(31),documentRevision:id(6)})
 start.state.selectedAnnotationThread.value=selection
 const origin=start.state.sourceReturnOrigin(link),token=nav.createSourceReturn(s,id(11),origin)
 assert.equal(origin.annotationId,id(31));assert.ok(!JSON.stringify(origin).includes('private text'))
 const back=nav.sourceReturnRoute(s,id(11),token),destination=editor(s)
 destination.state.mainAnnotations.value={restoreSelected:returned.state.restoreSelected}
 destination.route.query=back.query;await tick();await tick()
 assert.equal(returned.state.selectedId.value,id(31));assert.deepEqual(returned.calls,['threads','replies','decisions','threads','replies','decisions'])
 assert.match(destination.state.sourceReturnNotice.value,/恢复原选中标注/);destination.close()
 }finally{original.close();returned.close();start.close()}
})
test('annotation restore rejects missing/stale membership, revoked scope, busy state and unsent drafts',async()=>{
 for(const kind of ['missing','stale','revoked','busy','dirty','reply','reason','content','pending']){
 const h=annotations();await tick();h.calls.length=0;h.state.selectedId.value='';try{
 if(kind==='missing')h.item.annotation_id=id(32)
 if(kind==='stale')h.item.document_revision=id(7)
 if(kind==='revoked')h.revoke()
 if(kind==='busy')h.props.busy=true
 if(kind==='dirty')h.props.dirty=true
 if(kind==='reply')h.state.replyDrafts.value={[id(31)]:'keep reply'}
 if(kind==='reason')h.state.reasonDrafts.value={[id(31)]:'keep reason'}
 if(kind==='content')h.state.content.value='keep content'
 if(kind==='pending')h.state.pending.value={operation_id:id(32)}
 const before=JSON.stringify([h.state.replyDrafts.value,h.state.reasonDrafts.value,h.state.content.value,h.state.pending.value])
 assert.equal(await h.state.restoreSelected(id(31),id(6)),false,kind)
 assert.equal(h.state.selectedId.value,'');assert.ok(!h.calls.includes('replies'))
 assert.equal(JSON.stringify([h.state.replyDrafts.value,h.state.reasonDrafts.value,h.state.content.value,h.state.pending.value]),before)
 }finally{h.close()}}
})
test('in-flight annotation restoration cannot select after authorization change or new local draft',async()=>{
 for(const kind of ['scope','draft']){let resolve;const h=annotations({read:()=>new Promise(done=>resolve=done)})
 await tick();h.state.selectedId.value='';
 try{
 const pending=h.state.restoreSelected(id(31),id(6));await tick()
 if(kind==='scope')h.revoke();else h.state.replyDrafts.value={[id(31)]:'typed while waiting'}
 resolve({document_id:id(5),document_revision:id(6),revision:'1',items:[h.item]})
 assert.equal(await pending,false);assert.equal(h.state.selectedId.value,'');assert.deepEqual(h.calls,['threads','replies','decisions','threads'])
 if(kind==='draft')assert.equal(h.state.replyDrafts.value[id(31)],'typed while waiting')
 }finally{h.close()}}
})
test('parent ignores late annotation success after draft/version changes and contains restore rejection',async()=>{
 for(const change of ['dirty','revision','reject']){const s=session(),h=editor(s);await tick();let resolve,reject
 try{
 h.state.mainAnnotations.value={restoreSelected:()=>new Promise((yes,no)=>{resolve=yes;reject=no})}
 h.route.query={source_return:nav.createSourceReturn(s,id(11),{path,documentRevision:id(6),chapterId:id(14),focusNodeId:id(9),annotationId:id(31)})}
 await tick();assert.ok(resolve)
 if(change==='dirty')h.state.fieldInputs.value={local:{original_value:'keep'}}
 if(change==='revision')h.state.currentSnapshot.value={...snapshot,document_revision:id(7)}
 if(change==='reject')reject(Error('read failure'));else resolve(true)
 await tick()
 assert.ok(!h.state.sourceReturnNotice.value.includes('已重新核验并恢复原选中标注'))
 if(change==='reject')assert.match(h.state.sourceReturnNotice.value,/原选中标注未恢复/)
 if(change==='dirty')assert.equal(h.state.fieldInputs.value.local.original_value,'keep')
 }finally{h.close()}}
})
test('restoration accepts only an authorized effective anchor for the exact returned version',async()=>{
 const h=annotations();await tick()
 try{
 h.item.document_revision=id(7);h.item.effective_anchor={document_revision:id(6)}
 assert.equal(await h.state.restoreSelected(id(31),id(6)),true)
 h.item.effective_anchor.document_revision=id(8)
 assert.equal(await h.state.restoreSelected(id(31),id(6)),false);assert.equal(h.state.selectedId.value,'')
 assert.equal(nav.createSourceReturn(session(),id(11),{path,documentRevision:id(6),chapterId:id(14),focusNodeId:id(9),annotationId:'untrusted text'}),null)
 }finally{h.close()}
})

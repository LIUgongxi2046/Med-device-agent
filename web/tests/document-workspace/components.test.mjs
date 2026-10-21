import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import vm from 'node:vm'
import * as vue from 'vue'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { loadModule, id } from '../formal-capability/helpers.mjs'
const sourceReturnApi = (await loadModule('source-return-navigation')).api
const selection = (await loadModule('document-selection')).api
const body = () => ({schema_version:'1.0.0',template_version:id(9),fields:[],evidence_links:[],fact_usages:[],nodes:[
 {node_id:id(1),parent_id:null,ordinal:0,field_ids:[],evidence_link_ids:[],content:{kind:'SECTION',title:'First section'}},
 {node_id:id(2),parent_id:id(1),ordinal:0,field_ids:[],evidence_link_ids:[],content:{kind:'TEXT',text:'Unmodified document',marks:[]}}
]})
function setup(name, initial = {}, modules = {}) {
 const filename = new URL(`../../src/views/medical/${name}.vue`,import.meta.url).pathname
 const {descriptor,errors}=parse(readFileSync(filename,'utf8'),{filename}); assert.deepEqual(errors,[])
 const compiled=compileScript(descriptor,{id:name,fs:{fileExists:existsSync,readFile:p=>readFileSync(p,'utf8')}})
 const template=compileTemplate({source:descriptor.template.content,filename,id:name,compilerOptions:{bindingMetadata:compiled.bindings}})
 assert.deepEqual(template.errors,[])
 const code=ts.transpileModule(compiled.content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
 const exported={},scope=vue.effectScope(),cleanup=[],events=[],listeners=new Map(),props=vue.reactive(initial)
 const dependencies={'@/api/medical/source-return-navigation':sourceReturnApi,'vue-router':{useRouter:()=>({push(){}})},'@/api/medical/task-detail':{taskReadAllowed:()=>false},'@/api/medical/document-selection':selection,'@/api/medical/protocol':{fail:(code,message)=>{throw Error(code+': '+message)}},'@/api/medical/annotation-domain':{selectionSummary:()=>''},...modules}
 dependencies['vue-router']={useRoute:()=>({path:'',query:{}}),...dependencies['vue-router']}
 vm.runInNewContext(code,{exports:exported,require:key=>key==='vue'?{...vue,onBeforeUnmount:fn=>cleanup.push(fn)}:dependencies[key]??{},document:{hidden:false,addEventListener:(k,v)=>listeners.set(k,v),removeEventListener:k=>listeners.delete(k)},window:{addEventListener:(k,v)=>listeners.set(k,v),removeEventListener:k=>listeners.delete(k)},AbortController,TextEncoder,Array,Number,console})
 const state=scope.run(()=>exported.default.setup(props,{expose(){},emit:(...args)=>events.push(args)}))
 return {state,props,events,listeners,template,stop(){cleanup.forEach(fn=>fn());scope.stop()}}
}
const reader = (options={}) => setup('document-body-reader',{body:body(),documentRevision:id(10),active:true,dirty:false,editable:true,...options})
test('actual reader defaults to continuous reading without constructing editable nodes',()=>{
 const x=reader();assert.equal(x.state.editing.value,false);assert.equal(x.state.editStarted.value,false);assert.equal(x.state.roots.value.length,1);assert.equal(x.state.outline.value.length,1);x.stop()
})
test('switching modes retains exact current draft and lazily keeps editor mounted',()=>{
 const x=reader({dirty:true});const original=x.props.body
 x.state.editing.value=true;assert.equal(x.state.editStarted.value,true)
 x.state.editing.value=false;assert.equal(x.state.editStarted.value,true);assert.equal(x.props.body,original);assert.equal(x.props.dirty,true)
 assert.equal(x.events.filter(e=>e[0]==='changed').length,0);x.stop()
})
test('revoking editable permission returns reader to read-only mode',async()=>{
 const x=reader();x.state.editing.value=true;x.props.editable=false;await vue.nextTick();assert.equal(x.state.editing.value,false);assert.equal(x.props.body.nodes[1].content.text,'Unmodified document');x.stop()
})
test('outline targets visible read or edit tree and focuses node without editing',()=>{
 const x=reader();const hits=[];const node=kind=>({dataset:{nodeId:id(1)},scrollIntoView:()=>hits.push(kind+' scroll'),focus:()=>hits.push(kind+' focus')})
 x.state.contents.value={querySelectorAll:()=>[node('read')]};x.state.editContents.value={querySelectorAll:()=>[node('edit')]}
 x.state.locate(id(1));x.state.editing.value=true;x.state.locate(id(1));assert.deepEqual(hits,['read scroll','read focus','edit scroll','edit focus']);assert.equal(x.events.filter(e=>e[0]==='changed').length,0);x.stop()
})
test('switching display invalidates saved selection and cancels pending selection ownership',()=>{
 const x=reader();x.state.selection.value={segments:[{node_id:id(2)}]};x.state.editing.value=true;assert.equal(x.state.selection.value,null);assert.equal(x.events.at(-1)[0],'selected');assert.equal(x.events.at(-1)[1],null);x.stop()
})
test('context invalidation clears reader selection and unmount removes listeners',()=>{
 const x=reader();x.state.selection.value={segments:[]};x.listeners.get('medical-context-invalidated')();assert.equal(x.state.selection.value,null);x.stop();assert.equal(x.listeners.size,0)
})
test('actual document editor jump opens only requested panel and leaves drafts untouched',()=>{
 const x=setup('document-editor',{active:false,reviewMode:false,session:{user:{user_id:id(1)},current_context:null}}, {'@/store/modules/user':{default:()=>vue.reactive({medicalWriting:false})},'@/api/medical/task-workspace':{workspaceHistoryRevision:()=>({revision:null,issue:''})}})
 const hits=[];x.state.fieldsPanel.value={tagName:'DETAILS',open:false,scrollIntoView:()=>hits.push('scroll'),focus:()=>hits.push('focus')}
 x.state.locatePanel('fields');assert.equal(x.state.fieldsPanel.value.open,true);assert.deepEqual(hits,['scroll','focus']);assert.equal(x.state.body.value,null);assert.equal(x.state.saved.value,null);x.state.locatePanel('save');x.stop()
})

test('professional issue navigation only focuses an existing node in the current clean saved body',()=>{
 const x=setup('document-editor',{active:false,reviewMode:true,session:{user:{user_id:id(1)},current_context:null}}, {'@/store/modules/user':{default:()=>vue.reactive({medicalWriting:false})},'@/api/medical/task-workspace':{workspaceHistoryRevision:()=>({revision:null,issue:''})}})
 const hits=[];x.state.documentReader.value={locate:value=>hits.push(value)};x.state.locateProfessionalIssue();assert.equal(x.events.length,0)
 x.props.active=true;x.state.saved.value={document_revision:id(10)};x.state.currentSnapshot.value={document_revision:id(10)};x.state.body.value=body()
 x.state.locateProfessionalIssue(id(2));assert.deepEqual(hits,[id(2)]);assert.equal(x.state.locationNotice.value,'')
 x.state.locateProfessionalIssue(id(99));assert.equal(hits.length,1);assert.match(x.state.locationNotice.value,/没有对应节点/)
 x.state.currentSnapshot.value={document_revision:id(11)};x.state.locateProfessionalIssue(id(2));assert.equal(hits.length,1);assert.match(x.state.locationNotice.value,/版本已变化/)
 x.state.currentSnapshot.value={document_revision:id(10)};x.state.historyVisible.value=true;x.state.locateProfessionalIssue(id(2));assert.equal(hits.length,1)
 x.state.historyVisible.value=false;x.state.draft.value={dirty:true,invalidate(){}};x.state.locateProfessionalIssue(id(2));assert.equal(hits.length,1)
 x.stop()
})
test('history dialog pauses transport panels but does not deactivate pure local planning drafts',()=>{
 const filename=new URL('../../src/views/medical/document-editor.vue',import.meta.url).pathname
 const {descriptor}=parse(readFileSync(filename,'utf8'),{filename});const tree=descriptor.template.ast;const found=new Map()
 function walk(n){if(n&&typeof n==='object'){if(n.type===1&&n.tag){const active=n.props?.find(p=>p.type===7&&p.name==='bind'&&p.arg?.content==='active');if(active)found.set(n.tag,active.exp.content)}if(Array.isArray(n.children))n.children.forEach(walk);if(Array.isArray(n.branches))n.branches.forEach(walk)}}walk(tree)
 for(const tag of ['document-chapter-candidates','document-professional-checks'])assert.equal(found.get(tag),'active',tag)
 for(const tag of ['document-gru-records','document-review-return','document-review-workspace'])assert.equal(found.get(tag),'active && !historyVisible',tag)
})

const fieldRoutes = (await loadModule('document-field-verification')).api
const canonical = value => JSON.stringify(value)
async function fieldEditor(readPreparation=async()=>null) {
 const routes=[], reads=[]
 const x=setup('document-editor',{active:false,reviewMode:true,projectId:'project',documentId:'document',taskId:'task',session:{user:{user_id:'user'},current_context:{tenant_id:'tenant',context_revision:'context'},action_summary:[{action:'task.read',resource_kind:'TASK',resource_id:'task',project_id:'project'},{action:'source.read',resource_kind:'PROJECT',resource_id:'project',project_id:'project'}]}}, {
  'vue-router':{useRouter:()=>({push:r=>routes.push(r)})},
  '@/store/modules/user':{default:()=>vue.reactive({medicalWriting:true})},
  '@/api/medical/task-workspace':{workspaceHistoryRevision:()=>({revision:null,issue:''})},
  '@/api/medical/task-detail':{taskReadAllowed:()=>true},
  '@/api/medical/document-field-verification':fieldRoutes,
  '@/api/medical/document-body':{canonicalDocumentValue:canonical},
  '@/api/medical/task-field-targets':{readFieldTargetPreparation:async(...args)=>{reads.push(args);return readPreparation(...args)}}
 })
 x.props.active=true;await vue.nextTick()
 x.state.saved.value={project_id:'project',document_revision:'document-revision',plan_revision:'plan'}
 x.state.body.value=body()
 return {...x,routes,reads}
}
const preparation = () => ({tenant_id:'tenant',project_id:'project',task_id:'task',template_version:id(9),plan_revision:'plan',task_revision:'tr',allowed_actions:['READ'],inventory:[],source_choices:[],current_mapping:null})
test('parent mounts real field preparation, existing routes and evidence events, with no automatic request', async()=>{
 const x=await fieldEditor(async()=>preparation());assert.equal(x.reads.length,0)
 assert.equal(x.state.fieldNavigationContext.value.canReadMapping,true)
 await x.state.loadFieldPreparation();assert.equal(x.reads.length,1);assert.equal(x.state.fieldPreparation.value.project_id,'project')
 const route=fieldRoutes.fieldTargetsDestination(x.state.fieldNavigationContext.value)
 x.state.openFieldTargets(route);assert.equal(x.routes.length,1);assert.equal(x.routes[0].name,'MedicalTaskFieldTargets')
 x.state.openFieldTargets({...route,params:{projectId:'other',taskId:'task'}});assert.equal(x.routes.length,1)
 x.state.fieldVerification.value={hasLocalDraft:()=>true};await x.state.loadFieldPreparation();x.state.openFieldTargets(route)
 assert.equal(x.reads.length,1);assert.equal(x.routes.length,1)
 x.state.historyVisible.value=true;assert.equal(x.state.fieldPreparation.value.project_id,'project')
 x.state.fieldVerification.value={hasLocalDraft:()=>false}
 const link={evidence_link_id:'link',evidence:{tenant_id:'tenant',source_version:'source'}};x.state.body.value={...body(),evidence_links:[link]}
 const opened=[];x.state.fieldEvidenceReader.value={read:async item=>opened.push(item)}
 await x.state.openFieldEvidence(link);assert.equal(opened.length,1)
 await x.state.openFieldEvidence({...link,evidence_link_id:'forged'});assert.equal(opened.length,1)
 x.stop()
})
test('parent rejects mismatched preparation and discards aborted late scope results', async()=>{
 let finish
 const x=await fieldEditor(()=>new Promise(resolve=>{finish=resolve}))
 const pending=x.state.loadFieldPreparation();assert.equal(x.state.fieldPreparationLoading.value,true)
 x.props.taskId='other';assert.equal(x.reads[0][3].aborted,true);finish(preparation());await pending
 assert.equal(x.state.fieldPreparation.value,null);assert.equal(x.state.fieldPreparationLoading.value,false)
 x.stop()
 const y=await fieldEditor(async()=>({...preparation(),plan_revision:'old-plan'}));await y.state.loadFieldPreparation();assert.equal(y.state.fieldPreparation.value,null);assert.match(y.state.fieldPreparationNotice.value,/不匹配/);y.stop()
})
test('parent does not infer source identity from a source-version or unrelated SOURCE grant', async()=>{
 const x=await fieldEditor(async()=>preparation());await x.state.loadFieldPreparation()
 assert.equal(x.state.fieldNavigationContext.value.canReadSourceExact,true)
 x.props.session.action_summary=[{action:'source.read',resource_kind:'SOURCE',resource_id:'source-id-unknown',project_id:'project'}]
 assert.equal(x.state.fieldNavigationContext.value.canReadSourceExact,false)
 x.stop()
})
test('existing evidence reader exposes guarded read and rejects forged membership before transport', async()=>{
 let calls=0
 const link={evidence_link_id:'link',evidence:{tenant_id:'tenant',source_version:'source'}}
 const x=setup('document-evidence-panel',{active:true,session:{current_context:{tenant_id:'tenant'}},links:[link],documentRevision:'revision'},{
 '@/api/medical/document-body':{canonicalDocumentValue:canonical},
 '@/api/medical/source-governance':{readSourceGovernance:async()=>{calls++;throw Error('should not read')}}})
 await x.state.read({...link,evidence_link_id:'forged'});assert.equal(calls,0)
 x.props.active=false;await x.state.read(link);assert.equal(calls,0)
 x.stop()
})
test('parent opens source workspace only for matching current pinned proposal and grant', async()=>{
 const exact={source_version:'source-version',field_revision:'field-revision',manifest_sha256:'hash',ordinal:1,field_kind:'MODEL'}
 const entry={entry_key:'entry',field_definition_id:'definition',node_id:id(1),source_exact:exact}
 const prep={...preparation(),inventory:[{field_definition_id:'definition',node_id:id(1),name:'Model',state:'MAPPED'}],source_choices:[{source_exact:exact,raw_value:' 0001.00 '}],current_mapping:{is_current:true,manifest:{template_version:id(9),task_revision:'tr',plan_revision:'plan',status:'PROPOSED',mappings:[entry]}}}
 const x=await fieldEditor(async()=>prep);await x.state.loadFieldPreparation()
 const route={name:'MedicalSourceExactFields',params:{sourceVersion:'source-version'}}
 x.state.openFieldSource(route,exact);assert.equal(x.routes.length,1)
 x.state.openFieldSource(route,{...exact,field_revision:'forged'});assert.equal(x.routes.length,1)
 x.props.session.action_summary=[];x.state.openFieldSource(route,exact);assert.equal(x.routes.length,1)
 x.stop()
})
test('existing evidence reader reads a current canonical member using its existing verified transport', async()=>{
 const evidence={tenant_id:'tenant',source_id:'source-id',source_version:'version',parse_version:'parse',chunk_id:'chunk',quote_hash:'quote',locator:{page:1}}
 const link={evidence_link_id:'link',evidence};let governanceCalls=0, contentCalls=0
 const x=setup('document-evidence-panel',{active:true,session:{current_context:{tenant_id:'tenant'}},links:[link],documentRevision:'revision'},{
 '@/api/medical/document-body':{canonicalDocumentValue:canonical,documentBytesDigest:async()=> 'locator-hash'},
 '@/api/medical/source-governance':{readSourceGovernance:async()=>{governanceCalls++;return {source_id:'source-id',reviews:[{parse_version:'parse'}],rights_purpose:'VERIFICATION'}},readSourceContent:async()=>{contentCalls++;return {representation:'PARSED',source_id:'source-id',items:[{quote_hash:'quote',locator:{page:1},raw_text:'fixed original'}]}}}})
 await x.state.read(structuredClone(link));assert.equal(governanceCalls,1);assert.equal(contentCalls,1);assert.equal(x.state.content.value.raw_text,'fixed original')
 x.props.links=[];await x.state.read(link);assert.equal(contentCalls,1);assert.equal(x.state.content.value,null)
 x.stop()
})
test('mounted actual field child forwards navigation to actual parent only after explicit draft discard', async()=>{
 const x=await fieldEditor(async()=>preparation());const {body:fieldBody}=(await loadModule('../../../tests/field-verification-preview/fixture')).api
 x.state.body.value=fieldBody
 const {descriptor}=parse(readFileSync(new URL('../../src/views/medical/document-field-verification.vue',import.meta.url),'utf8'))
 const compiled=compileScript(descriptor,{id:'mounted-field-navigation'})
 const code=ts.transpileModule(compiled.content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
 const exported={};vm.runInNewContext(code,{exports:exported,require:key=>key==='vue'?vue:fieldRoutes})
 let child
 const Host=vue.defineComponent({setup(){child=exported.default.setup({body:x.state.body.value,navigationContext:x.state.fieldNavigationContext.value,canOpenEvidence:true},{expose:exposed=>{x.state.fieldVerification.value=exposed},emit:(event,...args)=>{if(event==='open-field-targets')x.state.openFieldTargets(...args)}});return()=>null}})
 const renderer=vue.createRenderer({createComment:()=>({}),insert(){},remove(){},parentNode(){},nextSibling(){}})
 const app=renderer.createApp(Host);app.mount({})
 child.beginDraft();child.draft.value.reason='需要保留的本地意见';child.requestNavigation({kind:'mapping'})
 assert.equal(x.routes.length,0);assert.equal(child.draft.value.reason,'需要保留的本地意见')
 child.confirmNavigation();assert.equal(x.routes.length,1);assert.equal(x.routes[0].name,'MedicalTaskFieldTargets');assert.equal(child.draft.value.reason,'')
 app.unmount();x.stop()
})


test('mounted editor forwards explicit return handoff with actor/context and guards modal, dirty and stale version',()=>{
 const user=vue.reactive({medicalWriting:true}),calls=[]
 const x=setup('document-editor',{active:false,reviewMode:true,session:{user:{user_id:'user'},current_context:{tenant_id:'tenant'}}},{
  '@/store/modules/user':{default:()=>user},'@/api/medical/task-workspace':{workspaceHistoryRevision:()=>({revision:null,issue:''})},'@/api/medical/document-body':{canonicalDocumentValue:canonical}
 })
 try{
  x.props.active=true;user.medicalWriting=false;x.state.saved.value={document_revision:'version'};x.state.currentSnapshot.value={document_revision:'version'}
  x.state.reviewReturnPanel.value={openFromReview:(draft,key)=>{calls.push({draft,key});return true}}
  const handoff={reason:'意见',contextKey:canonical(['user',{tenant_id:'tenant'}])}
  assert.equal(x.state.openReviewReturn(handoff),true);assert.equal(calls[0].draft,handoff);assert.equal(calls[0].key,handoff.contextKey)
  assert.equal(x.state.openReviewReturn(),true);assert.equal(calls[1].draft,undefined)
  for(const flag of ['historyVisible','loading','writing']){x.state[flag].value=true;assert.equal(x.state.openReviewReturn(handoff),false);x.state[flag].value=false}
  user.medicalWriting=true;assert.equal(x.state.openReviewReturn(handoff),false);user.medicalWriting=false
  x.state.currentSnapshot.value={document_revision:'other'};assert.equal(x.state.openReviewReturn(handoff),false);x.state.currentSnapshot.value={document_revision:'version'}
  x.state.draft.value={dirty:true,invalidate(){}};assert.equal(x.state.openReviewReturn(handoff),false);assert.equal(calls.length,2);assert.equal(x.events.length,0)
  const filename=new URL('../../src/views/medical/document-editor.vue',import.meta.url).pathname,source=readFileSync(filename,'utf8')
  assert.match(source,/<document-review-workspace[^>]+:return-context-key="reviewReturnContextKey"[^>]+@navigate-return="openReviewReturn"/)
  assert.match(source,/<document-review-return[^>]+ref="reviewReturnPanel"/)
 }finally{x.stop()}
})

test('actual review workspace explicit event reaches mounted editor return ref without a formal decision',async()=>{
 const workspaceApi=(await loadModule('document-review-workspace')).api,user=vue.reactive({medicalWriting:true}),calls=[]
 const parent=setup('document-editor',{active:false,reviewMode:true,session:{user:{user_id:'user'},current_context:{tenant_id:'tenant'}}},{'@/store/modules/user':{default:()=>user},'@/api/medical/task-workspace':{workspaceHistoryRevision:()=>({revision:null,issue:''})},'@/api/medical/document-body':{canonicalDocumentValue:canonical}})
 const snapshot={project_id:'project',document_id:'document',document_revision:'version',revision:'1',body:{sha256:'hash'},check_ids:[],review_ids:[],signature_ids:[],invalidation_reasons:[],evidence_watermark:'1',issue_watermark:'1'}
 const child=setup('document-review-workspace',{snapshot,active:true,isCurrent:true,dirty:false,returnContextKey:parent.state.reviewReturnContextKey.value},{'../../api/medical/document-review-workspace':workspaceApi})
 try{
  parent.props.active=true;user.medicalWriting=false;parent.state.saved.value=snapshot;parent.state.currentSnapshot.value=snapshot
  parent.state.reviewReturnPanel.value={openFromReview:(draft,key)=>{calls.push({draft,key});return true}}
  child.events.push=(event)=>{assert.equal(event[0],'navigateReturn');parent.state.openReviewReturn(event[1])}
  child.state.decision.value='RETURN';child.state.opinion.value='补充原件';child.state.reason.value='来源不足'
  assert.equal(calls.length,0);child.state.navigateReturn(true);assert.equal(calls.length,1);assert.match(calls[0].draft.reason,/补充原件/);assert.equal(calls[0].key,parent.state.reviewReturnContextKey.value)
  parent.state.historyVisible.value=true;child.state.navigateReturn(true);assert.equal(calls.length,1)
  assert.equal(parent.events.length,0);assert.equal(child.state.model.value.canConfirm,false)
 }finally{child.stop();parent.stop()}
})

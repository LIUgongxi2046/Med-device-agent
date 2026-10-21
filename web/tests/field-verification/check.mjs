import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
const root = new URL('../../', import.meta.url)
async function load(path) { const code = ts.transpileModule(readFileSync(new URL(path, root), 'utf8'), { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText; return import('data:text/javascript;base64,'+Buffer.from(code).toString('base64')) }
const api = await load('src/api/medical/document-field-verification.ts')
const { body } = await load('tests/field-verification-preview/fixture.ts')
test('conflict, missing and unknown are document observations, never confirmed outcomes', () => {
 const rows = api.verificationRows(body)
 assert.deepEqual(rows.map(r=>r.state), ['CONFLICT','MISSING','UNKNOWN'])
 assert.equal(rows[2].field.original_value,'0001.2300')
 assert.equal(rows[2].field.decimal_value,'0001.2300')
 assert.equal(api.filterVerificationRows(rows,'AX-200','ALL').length,1)
 assert.equal(api.filterVerificationRows(rows,'','CONFLICT').length,1)
 assert.equal(api.filterVerificationRows(rows,'missing','ALL').length,0)
})
test('dangling evidence is missing, not a successful verification', () => {
 const input=structuredClone(body); input.fields[2].evidence_link_ids=['absent']
 const row=api.verificationRows(input)[2]
 assert.equal(row.state,'MISSING'); assert.deepEqual(row.missingEvidenceIds,['absent'])
})
test('fixed inventory missing from body stays visible; unrelated templates never merge', () => {
 const prep={template_version:body.template_version, inventory:[{field_definition_id:'absent',node_id:'node',name:'缺少字段',state:'MISSING_MAPPING'}],current_mapping:null}
 assert.equal(api.verificationRows(body,prep).length,4)
 assert.equal(api.verificationRows(body,prep)[3].field,null)
 prep.template_version='different'; assert.equal(api.verificationRows(body,prep).length,3)
})
test('SourceExact lookup requires every pinned identity and never converts raw values', () => {
 const ref={source_version:'s',field_revision:'f',manifest_sha256:'h',ordinal:1,field_kind:'MODEL'}
 const choices=[{source_exact:ref,raw_value:' 0001.00 '}]
 assert.equal(api.sourceForMapping({source_exact:{...ref}},choices).raw_value,' 0001.00 ')
 for (const key of Object.keys(ref)) assert.equal(api.sourceForMapping({source_exact:{...ref,[key]:key==='ordinal'?2:'other'}},choices),undefined)
})
test('SFC compiles, uses real props, and cannot emit consequential decisions', () => {
 const file='src/views/medical/document-field-verification.vue'; const text=readFileSync(new URL(file,root),'utf8'); const {descriptor,errors}=parse(text)
 assert.deepEqual(errors,[])
 const script=compileScript(descriptor,{id:'verification-check'})
 const template=compileTemplate({source:descriptor.template.content,filename:file,id:'verification-check',compilerOptions:{bindingMetadata:script.bindings}})
 assert.deepEqual(template.errors,[])
 assert.equal((text.match(/<el-button disabled>/g)||[]).length,3)
 assert.ok(!text.includes('fetch(')); assert.ok(!text.includes('submitFieldTarget'))
 assert.ok(!script.content.includes('confirmDocumentField'))
})
test('actual SFC setup keeps selection valid across filtering and incoming body changes', async () => {
 const vue=await import('vue'); const {createRequire}=await import('node:module'); const require=createRequire(import.meta.url)
 const {descriptor}=parse(readFileSync(new URL('src/views/medical/document-field-verification.vue',root),'utf8'))
 const script=compileScript(descriptor,{id:'interactive-check'})
 const code=ts.transpileModule(script.content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
 const exports={}; new Function('require','exports',code)(name=>name==='vue'?require('vue'):api,exports)
 const props=vue.reactive({body:structuredClone(body)}); const emitted=[]; const scope=vue.effectScope()
 const state=scope.run(()=>exports.default.setup(props,{expose(){},emit:(...args)=>emitted.push(args)}))
 assert.equal(state.selected.value.state,'CONFLICT')
 state.filter.value='MISSING'; assert.equal(state.selected.value.state,'MISSING')
 state.select(state.selected.value); assert.equal(emitted[0][0],'select-field')
 state.query.value='unfindable'; assert.equal(state.selected.value,null)
 props.body={...structuredClone(body),template_version:'new-template'}; await vue.nextTick()
 assert.equal(state.query.value,''); assert.equal(state.filter.value,'ALL')
 assert.equal(state.selected.value.state,'CONFLICT')
 scope.stop()
})
test('stale proposal lineage never appears as matching current proposal', () => {
 const field=body.fields[0]; const entry={field_definition_id:field.field_definition_id,node_id:field.node_id}
 const prep={template_version:body.template_version,task_revision:'task',plan_revision:'plan',inventory:[],current_mapping:{is_current:true,manifest:{template_version:body.template_version,task_revision:'task',plan_revision:'plan',mappings:[entry]}}}
 assert.equal(api.verificationRows(body,prep)[0].mappings.length,1)
 prep.current_mapping.is_current=false; assert.equal(api.verificationRows(body,prep)[0].mappings.length,0)
 prep.current_mapping.is_current=true; prep.current_mapping.manifest.plan_revision='old'; assert.equal(api.verificationRows(body,prep)[0].mappings.length,0)
})

test('handling draft validates real targets, reasons, limits and not-applicable basis without changing source data', () => {
 const rows=api.verificationRows(body), original=JSON.stringify(body)
 const draft=api.emptyFieldHandlingDraft()
 assert.ok(api.handlingDraftIssues(draft,rows[0]).length>=3)
 Object.assign(draft,{target:rows[0].field.field_id,intent:'NEEDS_EVIDENCE',reason:'待补固定原文'})
 assert.deepEqual(api.handlingDraftIssues(draft,rows[0]),[])
 draft.intent='NOT_APPLICABLE'; assert.equal(api.handlingDraftIssues(draft,rows[0]).length,1)
 draft.applicabilityBasis='仅记录待核查条件，未获批准'; assert.deepEqual(api.handlingDraftIssues(draft,rows[0]),[])
 draft.notes='x'.repeat(4001); assert.equal(api.handlingDraftIssues(draft,rows[0]).length,1)
 draft.notes=''; draft.reason=' '; assert.equal(api.handlingDraftIssues(draft,rows[0]).length,1)
 draft.reason='valid'; draft.target='other-field'; assert.equal(api.handlingDraftIssues(draft,rows[0]).length,1)
 assert.equal(JSON.stringify(body),original)
})
test('compiled local form previews valid input, invalidates edits, cancels and clears on context change', async () => {
 const vue=await import('vue'); const {createRequire}=await import('node:module'); const require=createRequire(import.meta.url)
 const text=readFileSync(new URL('src/views/medical/document-field-verification.vue',root),'utf8')
 const {descriptor}=parse(text); const script=compileScript(descriptor,{id:'draft-check'})
 const code=ts.transpileModule(script.content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
 const exports={}; new Function('require','exports',code)(name=>name==='vue'?require('vue'):api,exports)
 const props=vue.reactive({body:structuredClone(body)}); const emitted=[]; const scope=vue.effectScope()
 const state=scope.run(()=>exports.default.setup(props,{expose(){},emit:(...args)=>emitted.push(args)}))
 const original=JSON.stringify(props.body)
 state.beginDraft(); assert.equal(state.draftOpen.value,true)
 state.previewDraft(); assert.equal(state.draftPreview.value,false)
 Object.assign(state.draft.value,{intent:'NEEDS_EVIDENCE',reason:'需要固定来源原文'})
 state.previewDraft(); assert.equal(state.draftPreview.value,true)
 state.draft.value.notes='补充'; assert.equal(state.draftPreview.value,false)
 state.previewDraft(); assert.equal(state.draftPreview.value,true)
 state.filter.value='MISSING'; assert.equal(state.draftOpen.value,false); assert.equal(state.draft.value.reason,'')
 state.beginDraft(); state.draft.value.reason='未发送'; state.cancelDraft()
 assert.equal(state.draftOpen.value,false); assert.equal(state.draft.value.reason,'')
 state.beginDraft(); state.draft.value.reason='旧上下文'; props.body.fields[1].unit='cm'
 assert.equal(state.draftOpen.value,false); assert.equal(state.draft.value.reason,'')
 props.body.fields[1].unit='mm'; assert.equal(JSON.stringify(props.body),original)
 assert.deepEqual(emitted,[])
 assert.ok(text.includes('@keydown.esc.prevent.stop="cancelDraft"'))
 assert.ok(text.includes('type="button" disabled class="fv-draft-button">正式提交不可用'))
 scope.stop(); await vue.nextTick()
})

test('existing route destinations fail closed on identity, authority and stale exact lineage', () => {
 const context={projectId:'project',taskId:'task',canReadMapping:true,canReadSourceExact:true}
 assert.equal(api.fieldTargetsDestination(),null)
 assert.equal(api.fieldTargetsDestination({...context,taskId:''}),null)
 assert.equal(api.fieldTargetsDestination({...context,canReadMapping:false}),null)
 assert.deepEqual(api.fieldTargetsDestination(context),{name:'MedicalTaskFieldTargets',params:{projectId:'project',taskId:'task'}})
 const ref={source_version:'source',field_revision:'revision',manifest_sha256:'hash',ordinal:1,field_kind:'MODEL'}
 const entry={entry_key:'entry',field_definition_id:body.fields[0].field_definition_id,node_id:body.fields[0].node_id,source_exact:ref}
 const prep={project_id:'project',task_id:'task',template_version:body.template_version,task_revision:'tr',plan_revision:'pr',inventory:[],allowed_actions:['READ'],source_choices:[{source_exact:ref,raw_value:'0001.00'}],current_mapping:{is_current:true,manifest:{template_version:body.template_version,task_revision:'tr',plan_revision:'pr',mappings:[entry]}}}
 assert.deepEqual(api.sourceExactDestination(entry,body,prep,context),{name:'MedicalSourceExactFields',params:{sourceVersion:'source'}})
 for(const changed of [{...context,canReadSourceExact:false},{...context,taskId:'other'}]) assert.equal(api.sourceExactDestination(entry,body,prep,changed),null)
 assert.equal(api.sourceExactDestination({...entry,source_exact:{...ref,ordinal:2}},body,prep,context),null)
 prep.current_mapping.is_current=false; assert.equal(api.sourceExactDestination(entry,body,prep,context),null)
 prep.current_mapping.is_current=true; prep.allowed_actions=[]; assert.equal(api.sourceExactDestination(entry,body,prep,context),null)
})
test('navigation holds local edits until explicit discard and rechecks authority before emitting', async () => {
 const vue=await import('vue'); const {createRequire}=await import('node:module'); const require=createRequire(import.meta.url)
 const {descriptor}=parse(readFileSync(new URL('src/views/medical/document-field-verification.vue',root),'utf8'))
 const script=compileScript(descriptor,{id:'navigation-check'})
 const code=ts.transpileModule(script.content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
 const exports={}; new Function('require','exports',code)(name=>name==='vue'?require('vue'):api,exports)
 const props=vue.reactive({body:structuredClone(body),canOpenEvidence:true,navigationContext:{projectId:'project',taskId:'task',canReadMapping:true,canReadSourceExact:false}})
 const emitted=[]; const scope=vue.effectScope()
 const state=scope.run(()=>exports.default.setup(props,{expose(){},emit:(...args)=>emitted.push(args)}))
 state.beginDraft(); state.draft.value.reason='尚未保存'
 state.requestNavigation({kind:'mapping'}); assert.equal(emitted.length,0); assert.equal(state.draft.value.reason,'尚未保存')
 state.pendingNavigation.value=null; assert.equal(state.draft.value.reason,'尚未保存')
 state.requestNavigation({kind:'mapping'}); state.confirmNavigation()
 assert.equal(emitted[0][0],'open-field-targets'); assert.equal(state.draft.value.reason,'')
 state.beginDraft(); state.draft.value.reason='保留'
 state.requestNavigation({kind:'evidence',link:state.selected.value.evidence[0]}); assert.equal(emitted.length,1)
 props.canOpenEvidence=false; state.confirmNavigation(); assert.equal(emitted.length,1); assert.equal(state.draft.value.reason,'保留')
 props.canOpenEvidence=true; state.requestNavigation({kind:'evidence',link:state.selected.value.evidence[0]}); state.confirmNavigation()
 assert.equal(emitted[1][0],'open-evidence')
 state.requestNavigation({kind:'evidence',link:{evidence_link_id:'unrelated'}}); assert.equal(emitted.length,2)
 scope.stop(); await vue.nextTick()
})

<template>
  <section class="candidate-workspace" aria-label="候选比较与采用" data-candidate-adoption>
    <header class="workspace-heading"><div><p class="eyebrow">文稿工作区 / 候选审阅</p><h3>比较候选与当前正文</h3><p>核对完整内容、引用变化及准确节点，通过服务端预览确认采用范围。</p></div><el-tag :type="preview ? 'warning' : 'info'">{{ preview ? '待确认采用' : '候选审阅' }}</el-tag></header>
    <el-steps :active="result ? 3 : preview ? 2 : comparison ? 1 : 0" simple><el-step title="选择候选" /><el-step title="比较内容" /><el-step title="确认采用" /></el-steps>
    <p v-if="!active" role="status">工作区未激活，候选内容已隐藏。</p>
    <template v-else>
      <el-alert v-if="availability && !availability.candidate_adoption_enabled" title="当前服务器尚未启用候选采用服务。" type="info" :closable="false" />
      <div class="workspace-tools"><el-button :disabled="busy || pending !== null" :loading="loading" @click="loadDirectory()">读取候选目录</el-button><span v-if="directory">已经读取 {{ items.length }} 项 · {{ directory.complete ? '目录读取完整' : '仍有后续目录' }}</span></div>
      <el-empty v-if="directory && !items.length" description="当前文稿尚无已登记的文稿候选。" :image-size="64" />
      <el-table v-if="items.length" :data="items" row-key="candidate_id" border aria-label="实际文稿候选">
        <el-table-column label="候选内容" min-width="220"><template #default="{ row, $index }"><div data-candidate-row><strong>文稿候选 {{ $index + 1 }}</strong><p class="muted">{{ row.target_node_count }} 个目标节点 · {{ formatTime(row.created_at) }}</p></div></template></el-table-column>
        <el-table-column label="正文基准" min-width="180"><template #default="{ row }"><el-tag :type="row.base_document_revision === snapshot.document_revision ? 'success' : 'warning'">{{ row.base_document_revision === snapshot.document_revision ? '与当前正文一致' : '需要核查历史基准' }}</el-tag></template></el-table-column>
        <el-table-column label="候选状态" width="110"><template #default="{ row }">{{ statuses[row.status as CandidateItem['status']] }}</template></el-table-column>
        <el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" :disabled="busy || pending !== null" @click="choose(row.candidate_id)">比较完整内容</el-button></template></el-table-column>
      </el-table>
      <el-button v-if="directory && !directory.complete" :disabled="busy || pending !== null" @click="loadDirectory(directory.next_after)">读取更多候选</el-button>
      <section v-if="pending" class="pending-request" aria-label="原候选请求">
        <el-alert title="原请求结果待核查" type="warning" :closable="false" description="继续操作会使用原请求标识与完整参数；页面不会自动提交或创建替代请求。" />
        <details><summary>查看原请求及准确范围</summary><p>操作：{{ pending.operation === 'PREVIEW' ? '采用预览' : '采用确认' }}</p><p>请求标识：{{ pending.key }}</p><pre>{{ JSON.stringify(pending.input, null, 2) }}</pre><pre v-if="pending.preview">{{ JSON.stringify(pending.preview.selected_node_ids, null, 2) }}</pre></details>
        <el-button :disabled="busy || dirty || !isCurrent" @click="continueOriginal">继续原候选请求</el-button>
      </section>
      <template v-if="comparison">
        <div class="comparison-heading"><h4>正文与引用比较</h4><el-tag>{{ metadata?.status === 'READY' ? '等待采用' : metadata?.status }}</el-tag></div>
        <el-alert v-if="!matchesCurrent" title="候选基准与当前正文不一致，请重新生成并比较当前候选。" type="warning" :closable="false" />
        <el-alert v-if="dirty" title="当前存在未保存修改，请处理草稿以后继续采用。" type="warning" :closable="false" />
        <el-tabs v-model="tab">
          <el-tab-pane label="准确节点范围" name="nodes">
            <p>只采用勾选的目标节点。父节点的选择范围与关联内容由服务端验证。</p>
            <el-checkbox-group :model-value="selectedIds" :disabled="busy || pending !== null || preview !== null" @update:model-value="selectNodes">
              <div v-for="entry in targetNodes" :key="entry.node_id" class="node-choice"><el-checkbox :value="entry.node_id">{{ nodeLabel(entry) }}</el-checkbox><details><summary>节点身份</summary>{{ entry.node_id }}</details></div>
            </el-checkbox-group>
            <p v-if="!targetNodes.length">当前候选没有可单独采用的目标节点。</p>
            <div v-for="id in selectedIds" :key="id" class="node-comparison"><h5>{{ nodeLabel(comparison.body.nodes.find(node => node.node_id === id)) }}</h5><div class="comparison-columns"><section><h5>当前已保存内容</h5><pre>{{ nodeText(comparison.base_body.nodes.find(node => node.node_id === id)) }}</pre></section><section><h5>拟采用内容</h5><pre>{{ nodeText(comparison.body.nodes.find(node => node.node_id === id)) }}</pre></section></div></div>
          </el-tab-pane>
          <el-tab-pane label="完整正文" name="body"><div class="comparison-columns full-body"><section><h5>候选基准正文</h5><document-node-editor v-for="entry in baseRoots" :key="entry.node_id" :node="entry" :body="comparison.base_body" readonly /></section><section><h5>完整候选正文</h5><document-node-editor v-for="entry in candidateRoots" :key="entry.node_id" :node="entry" :body="comparison.body" readonly /></section></div></el-tab-pane>
          <el-tab-pane label="引用变化" name="references"><template v-if="references"><div class="reference-summary"><el-tag>新增 {{ references.added.length }}</el-tag><el-tag type="warning">移除 {{ references.removed.length }}</el-tag><el-tag type="info">内容变化 {{ references.changed.length }}</el-tag></div><p>引用比较仅包括所选节点，专业核验需要独立执行。</p><div class="comparison-columns"><section><h5>基准引用</h5><pre>{{ JSON.stringify(references.before, null, 2) }}</pre></section><section><h5>拟采用引用</h5><pre>{{ JSON.stringify(references.after, null, 2) }}</pre></section></div></template></el-tab-pane>
          <el-tab-pane label="固定版本依据" name="versions"><el-descriptions :column="1" border><el-descriptions-item label="候选版本">{{ comparison.candidate_id }}</el-descriptions-item><el-descriptions-item label="候选摘要">{{ comparison.candidate_sha256 }}</el-descriptions-item><el-descriptions-item label="基准正文">{{ comparison.base_document_revision }}</el-descriptions-item><el-descriptions-item label="文稿记录修订">{{ comparison.expected_document_revision }}</el-descriptions-item></el-descriptions></el-tab-pane>
        </el-tabs>
        <div v-if="!preview && !pending" class="preview-action"><el-radio-group v-model="mode" :disabled="busy"><el-radio value="SELECTED_NODES">采用明确选择的节点</el-radio><el-radio value="INITIAL">将候选用于空白初稿</el-radio></el-radio-group><p v-if="mode === 'INITIAL'">服务端会核验正文确为空白，并返回完整的准确采用范围。</p><el-button type="primary" :disabled="!canPreview" @click="createPreview">取得服务端采用预览</el-button></div>
      </template>
      <section v-if="preview && !pending" class="adoption-confirmation" aria-label="服务端采用预览">
        <h4>核对采用范围</h4><p>有效期限：{{ formatTime(preview.expires_at) }}。确认后生成新正文版本，原正文保留。</p>
        <div class="impact-summary"><div><strong>{{ preview.selected_node_ids.length }}</strong><span>采用节点</span></div><div><strong>{{ preview.replaced_node_ids.length }}</strong><span>修改节点</span></div><div><strong>{{ preview.inserted_node_ids.length }}</strong><span>新增节点</span></div></div>
        <details><summary>查看准确节点与结果摘要</summary><pre>{{ JSON.stringify({selected:preview.selected_node_ids,replaced:preview.replaced_node_ids,inserted:preview.inserted_node_ids,result_body_sha256:preview.result_body_sha256},null,2) }}</pre></details>
        <p>采用完成后需要对新正文执行独立检查和人工审查。</p>
        <el-checkbox v-model="acknowledged" :disabled="busy">已经核查完整候选、准确采用范围及引用变化，确认生成新正文</el-checkbox>
        <div class="confirmation-actions"><el-button type="primary" :disabled="busy || !acknowledged || dirty || !isCurrent || !matchesCurrent" @click="confirm">明确确认采用</el-button><el-button :disabled="busy" @click="clearPreview">重新选择范围</el-button></div>
      </section>
      <el-result v-if="result" icon="success" title="候选已采用并保存新正文" sub-title="专业检查与人工审查需要分别完成。"><template #extra><p>正文版本：{{ result.document_revision }}</p><p>正文检查意图：{{ result.check_document_intent_id }}</p></template></el-result>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import type { CandidateView, DocumentSnapshot, Node } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue } from '@/api/medical/document-body'
import { orderedDocumentNodes } from '@/api/medical/document-selection'
import { nodeText, referenceChanges } from '@/api/medical/document-chapter-candidates'
import { readCandidateMetadata } from '@/api/medical/candidate-read'
import { prepareAdoption, readAdoptionPending, readCandidateComparison, readDocumentCandidates, readWorkspaceAvailability, submitAdoption } from '@/api/medical/document-candidate-workspace'
import type { AdoptionPending, AdoptionPreview, AdoptionResult, CandidateComparison, CandidateDirectory, CandidateItem, WorkspaceAvailability } from '@/api/medical/document-candidate-workspace'
import DocumentNodeEditor from './document-node-editor.vue'

const props=defineProps<{session:MedicalSession;snapshot:DocumentSnapshot;active:boolean;dirty:boolean;isCurrent:boolean}>()
const emit=defineEmits<{adopted:[result:AdoptionResult]}>(),user=useUserStore()
const availability=shallowRef<WorkspaceAvailability|null>(null),directory=shallowRef<CandidateDirectory|null>(null),items=shallowRef<CandidateItem[]>([])
const comparison=shallowRef<CandidateComparison|null>(null),metadata=shallowRef<CandidateView|null>(null),preview=shallowRef<AdoptionPreview|null>(null),result=shallowRef<AdoptionResult|null>(null)
const pending=shallowRef<AdoptionPending|null>(null),selectedIds=ref<string[]>([]),acknowledged=ref(false),loading=ref(false),tab=ref('nodes'),mode=ref<'INITIAL'|'SELECTED_NODES'>('SELECTED_NODES')
const statuses:Record<CandidateItem['status'],string>={READY:'等待采用',ADOPTED:'已经采用',REJECTED:'已经拒绝',STALE:'已经失效'}
let alive=true,generation=0,controller=new AbortController(),ownsWrite=false
const busy=computed(()=>loading.value||user.medicalWriting)
const matchesCurrent=computed(()=>!!comparison.value&&props.isCurrent&&comparison.value.base_document_revision===props.snapshot.document_revision&&comparison.value.expected_document_revision===props.snapshot.revision)
const canPreview=computed(()=>props.active&&!busy.value&&!props.dirty&&matchesCurrent.value&&metadata.value?.allowed_actions.includes('candidate.adopt')===true&&(mode.value==='INITIAL'||selectedIds.value.length>0))
const targetNodes=computed(()=>comparison.value?.body.nodes.filter(node=>comparison.value!.target_node_ids.includes(node.node_id))??[])
const baseRoots=computed(()=>comparison.value?orderedDocumentNodes(comparison.value.base_body).filter(entry=>entry.depth===0).map(entry=>entry.node):[])
const candidateRoots=computed(()=>comparison.value?orderedDocumentNodes(comparison.value.body).filter(entry=>entry.depth===0).map(entry=>entry.node):[])
const references=computed(()=>comparison.value?referenceChanges(comparison.value.base_body,comparison.value.body,selectedIds.value):null)
function formatTime(value:string){return new Intl.DateTimeFormat('zh-CN',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value))}
function nodeLabel(node:Node|undefined){if(!node)return '未提供节点';const content=node.content;return content.kind==='TEXT'?content.text.slice(0,80)||'空文字节点':content.kind==='IMAGE'?content.alt_text:content.kind==='FORMULA'?content.description:content.kind==='TABLE'?content.caption:content.title}
function live(current:number,session:MedicalSession){return alive&&generation===current&&props.active&&!document.hidden&&props.session===session}
function clearPreview(){preview.value=null;acknowledged.value=false}
function selectNodes(values:unknown[]){if(busy.value||pending.value||preview.value)return;const ids=values as string[];if(new Set(ids).size!==ids.length||ids.some(id=>!targetNodes.value.some(node=>node.node_id===id)))fail('CONTENT_MISMATCH','选择范围包含未提供的候选节点');selectedIds.value=[...ids];acknowledged.value=false}
async function loadDirectory(after:string|null=null){
 if(!props.active||busy.value||pending.value||document.hidden)return
 const session=props.session,current=generation;loading.value=true
 try{
  const available=await readWorkspaceAvailability(session,controller.signal);if(!live(current,session))return;availability.value=available
  if(!available.candidate_adoption_enabled)return
  const value=await readDocumentCandidates(session,props.snapshot.project_id,props.snapshot.document_id,after,controller.signal)
  if(!live(current,session))return
  const merged=after?[...items.value,...value.items]:value.items
  if(new Set(merged.map(item=>item.candidate_id)).size!==merged.length)fail('CONTENT_MISMATCH','候选分页包含重复身份，请重新读取目录')
  items.value=merged;directory.value=value
 }finally{if(live(current,session))loading.value=false}
}
async function choose(id:string){
 if(!props.active||busy.value||pending.value||document.hidden)return
 const session=props.session,current=generation;loading.value=true;comparison.value=null;metadata.value=null;selectedIds.value=[];clearPreview();result.value=null
 try{
  const candidate=await readCandidateMetadata(()=>props.session,props.snapshot.project_id,id,controller.signal)
  if(!live(current,session))return
  const value=await readCandidateComparison(session,id,props.snapshot.document_id,controller.signal)
  if(!live(current,session))return
  if(candidate.base_document_revision!==value.base_document_revision||canonicalDocumentValue(candidate.target_node_ids)!==canonicalDocumentValue(value.target_node_ids))fail('CONTENT_MISMATCH','候选元数据与完整内容的版本范围不一致')
  metadata.value=candidate;comparison.value=value;tab.value='nodes'
 }finally{if(live(current,session))loading.value=false}
}
async function createPreview(){
 if(!canPreview.value||!comparison.value||pending.value)return
 const value=comparison.value
 pending.value=prepareAdoption(props.session,value.document_id,value.candidate_id,'PREVIEW',{schema_version:'1.0.0',document_id:value.document_id,base_document_revision:value.base_document_revision,expected_document_revision:value.expected_document_revision,candidate_sha256:value.candidate_sha256,mode:mode.value,selected_node_ids:mode.value==='INITIAL'?[]:[...selectedIds.value]})
 await continueOriginal()
}
async function confirm(){
 if(busy.value||!acknowledged.value||props.dirty||!matchesCurrent.value||!preview.value||pending.value)return
 const value=preview.value
 pending.value=prepareAdoption(props.session,value.document_id,value.candidate_id,'CONFIRM',{schema_version:'1.0.0',pending_action_id:value.pending_action_id,arguments_sha256:value.arguments_sha256,expected_action_revision:value.action_revision,confirm:true},value)
 await continueOriginal()
}
async function continueOriginal(){
 const original=pending.value,session=props.session,current=generation
 if(!original||!props.active||busy.value||props.dirty||!props.isCurrent||document.hidden)return
 loading.value=true;ownsWrite=true;user.medicalWriting=true;let adopted:AdoptionResult|null=null
 try{
  const value=await submitAdoption(session,original,controller.signal)
  if(!live(current,session))return
  pending.value=null;acknowledged.value=false
  if(original.operation==='PREVIEW'){preview.value=value as AdoptionPreview;selectedIds.value=[...preview.value.selected_node_ids]}
  else{result.value=value as AdoptionResult;preview.value=null;adopted=result.value}
 }finally{if(ownsWrite){ownsWrite=false;user.medicalWriting=false}if(live(current,session))loading.value=false}
 if(original.operation==='PREVIEW'&&preview.value&&!comparison.value&&live(current,session)){
  const fixed=preview.value
  await choose(original.candidate_id)
  if(live(current,session)){preview.value=fixed;selectedIds.value=[...fixed.selected_node_ids]}
 }
 if(adopted&&live(current,session))emit('adopted',adopted)
}
function reset(){generation++;controller.abort();controller=new AbortController();loading.value=false;availability.value=null;directory.value=null;items.value=[];comparison.value=null;metadata.value=null;selectedIds.value=[];clearPreview();result.value=null;pending.value=null;if(props.active&&props.session.current_context)pending.value=readAdoptionPending(props.session,props.snapshot.document_id)}
watch(()=>[props.session,canonicalDocumentValue(props.session.current_context),canonicalDocumentValue(props.session.action_summary),props.snapshot.document_id,props.snapshot.document_revision,props.snapshot.revision,props.active],reset,{immediate:true,flush:'sync'})
watch(()=>[props.dirty,props.isCurrent],clearPreview,{flush:'sync'})
function visibility(){reset()}
document.addEventListener('visibilitychange',visibility)
onBeforeUnmount(()=>{alive=false;reset();document.removeEventListener('visibilitychange',visibility)})
</script>

<style scoped>
.candidate-workspace{padding:24px;background:#fff;border:1px solid #DCE3EC;border-radius:8px;color:#243247;font-size:14px;line-height:1.7}.workspace-heading,.comparison-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.eyebrow,.muted{color:#526279;font-size:12px;margin:0}.workspace-heading h3{font-size:24px;margin:4px 0 8px}.candidate-workspace h4{font-size:17px;margin:0}.candidate-workspace h5{font-size:14px;margin:0 0 12px}.workspace-heading p{color:#526279}.workspace-tools{display:flex;align-items:center;flex-wrap:wrap;gap:16px;margin:20px 0}.workspace-tools span{color:#526279;font-size:12px}.comparison-heading{margin:24px 0 16px}.comparison-columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.comparison-columns>section{background:#F4F6FA;padding:16px;border-radius:8px;min-width:0}.comparison-columns>section+section{background:#EDF4FD}.node-choice{border-bottom:1px solid #DCE3EC;padding:12px 0}.node-choice .el-checkbox{height:auto;min-height:38px;white-space:normal}.node-choice :deep(.el-checkbox__label){white-space:normal}.node-choice details{font-size:12px;color:#526279}.node-comparison{margin:20px 0}.preview-action{padding-top:20px;border-top:1px solid #DCE3EC}.preview-action .el-radio-group{display:flex;margin-bottom:16px}.adoption-confirmation{margin-top:24px;padding:20px;border:1px solid #E7CB91;border-radius:8px;background:#FFFAF0}.impact-summary{display:flex;gap:32px;margin:20px 0}.impact-summary strong,.impact-summary span{display:block}.impact-summary strong{font-size:24px}.impact-summary span{color:#526279;font-size:12px}.confirmation-actions{display:flex;flex-wrap:wrap;gap:12px;margin-top:16px}.confirmation-actions .el-button{margin:0}.candidate-workspace details{margin:12px 0;overflow-wrap:anywhere}.candidate-workspace summary{cursor:pointer}.candidate-workspace pre{font:13px/1.7 system-ui,sans-serif;white-space:pre-wrap;overflow-wrap:anywhere;max-height:400px;overflow:auto}.pending-request{margin:20px 0}.reference-summary{display:flex;gap:12px}.full-body{max-height:640px;overflow:auto}.full-body :deep(.el-input__wrapper){background:transparent;box-shadow:none}.candidate-workspace :deep(.el-checkbox__label){white-space:normal}.candidate-workspace :deep(.el-steps--simple){padding:16px}.candidate-workspace :deep(.el-step__title){font-size:14px}@media(max-width:760px){.candidate-workspace{padding:16px}.workspace-heading,.comparison-heading{flex-wrap:wrap}.comparison-columns{grid-template-columns:minmax(0,1fr)}.workspace-heading h3{font-size:22px}.candidate-workspace :deep(.el-steps--simple){padding:12px 8px}.candidate-workspace :deep(.el-step__title){font-size:12px}}
</style>

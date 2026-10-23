<template>
  <main class="medical-page feedback-page">
    <header class="feedback-heading"><h1>评测与运营</h1><el-button type="primary" :disabled="!mayCreate || busy || !!recovery" @click="openCreate">登记反馈</el-button></header>
    <operations-navigation />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
    <el-card v-if="recovery" shadow="never" class="feedback-recovery"><template #header><h2>原反馈操作待核查</h2></template><p>原操作的身份及版本仍然保留。重新读取记录后确认结果；尚未查到记录时，继续使用完整原参数及相同请求身份。</p><el-button :loading="recoveryLoading" :disabled="writing" @click="recoverWrite">查询原反馈操作</el-button><p v-if="recoveryNotice" role="status">{{ recoveryNotice }}</p><el-collapse><el-collapse-item title="重新填写原问题或决定依据"><el-form label-position="top" @submit.prevent="resumeWrite"><el-form-item label="完整原问题或决定依据"><el-input v-model="recoveryReason" type="textarea" :rows="4" :maxlength="4000" /></el-form-item><el-button native-type="submit" :loading="writing" :disabled="!recoveryReason.trim() || recoveryLoading">使用完整原参数继续提交</el-button></el-form></el-collapse-item></el-collapse></el-card>
    <el-card shadow="never"><el-form inline label-position="top" @submit.prevent="load()">
      <el-form-item label="所属项目"><el-select v-model="projectId" filterable :loading="bootLoading" :disabled="writing || !!recovery" placeholder="选择项目"><el-option v-for="item in projects" :key="item.project_id" :value="item.project_id" :label="item.name" /><template #footer><el-button v-if="nextProject" text :loading="bootLoading" @click="loadProjects(nextProject)">加载更多项目</el-button></template></el-select></el-form-item>
      <el-form-item label="处理对象"><el-select v-model="kind" :disabled="writing || !!recovery"><el-option v-for="(label, value) in feedbackKindLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="处理进度"><el-select v-model="stateFilter" clearable placeholder="全部进度"><el-option v-for="(label, value) in availableStates" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item><el-button native-type="submit" :loading="loading" :disabled="!mayRead || busy">查询反馈</el-button></el-form-item>
    </el-form></el-card>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card shadow="never" class="feedback-list"><template #header><div class="feedback-panel-heading"><h2>反馈与版本改进</h2><span>{{ visibleRows.length }} 项反馈</span></div></template>
      <el-table :data="visibleRows" v-loading="loading" empty-text="当前项目与条件下没有反馈记录" row-key="feedback_id">
        <el-table-column prop="reason" label="问题与依据" min-width="300" show-overflow-tooltip /><el-table-column label="来源" min-width="200"><template #default="{ row }">{{ targetNames[row.target_version] ?? feedbackKindLabels[kind] + '固定版本' }}<small>{{ localTime(row.recorded_at) }}</small></template></el-table-column><el-table-column label="处理对象" width="110"><template #default>{{ feedbackKindLabels[kind] }}</template></el-table-column><el-table-column label="进度" width="140"><template #default="{ row }"><el-tag :type="row.state === 'CLOSED' ? 'success' : row.state === 'REJECTED' ? 'info' : 'warning'">{{ feedbackStateLabels[row.state] }}</el-tag></template></el-table-column><el-table-column label="操作" fixed="right" width="120"><template #default="{ row }"><el-button link type="primary" :disabled="busy" @click="openHistory(row)">处理记录</el-button></template></el-table-column>
      </el-table><div v-if="nextAfter" class="feedback-pagination"><el-button :loading="loading" @click="load(nextAfter)">加载更多反馈</el-button></div>
    </el-card>
    <el-dialog v-model="createVisible" title="登记改进反馈" width="680px" :close-on-click-modal="false" :close-on-press-escape="!writing" :show-close="!writing">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-form label-position="top" :disabled="preparing || writing" @submit.prevent="create">
        <el-form-item label="所属项目"><el-input :model-value="projectName" readonly /></el-form-item>
        <el-form-item label="关联对象"><el-input :model-value="feedbackKindLabels[kind]" readonly /></el-form-item>
        <el-form-item label="关联版本"><el-select v-model="target" filterable :loading="targetsLoading" placeholder="选择已授权版本" :disabled="writing || !!pending"><el-option v-for="item in targets" :key="item.id" :value="item.id" :label="item.name" /><template #footer><el-button v-if="nextTarget" text :loading="targetsLoading" @click="loadTargets(nextTarget)">加载更多版本</el-button></template></el-select></el-form-item>
        <el-form-item label="问题依据"><el-input v-model="reason" type="textarea" :rows="5" :maxlength="4000" show-word-limit :disabled="writing || !!pending" placeholder="描述问题、来源位置和需要改进的内容" /></el-form-item>
        <el-alert v-if="targetsError" :title="targetsError" type="error" :closable="false" />
        <el-alert v-if="pending" title="原反馈请求尚未核查完成。继续提交使用相同请求身份及内容。" type="warning" :closable="false" />
        <el-button native-type="submit" type="primary" :loading="writing" :disabled="!target || !reason.trim() || !mayCreate">{{ pending ? '继续原反馈请求' : '保存反馈' }}</el-button>
      </el-form>
    </el-dialog>
    <el-drawer v-model="historyVisible" title="反馈处理记录" size="min(760px, 95vw)" :close-on-click-modal="!writing" :close-on-press-escape="!writing" :show-close="!writing">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-skeleton v-if="historyLoading" :rows="8" animated />
      <template v-if="history && selected">
        <div class="feedback-detail-heading"><el-tag>{{ feedbackStateLabels[latest!.state] }}</el-tag><h2>{{ history.events[0]?.reason }}</h2><p>{{ projectName }} · {{ feedbackKindLabels[kind] }} · {{ targetNames[selected.target_version] ?? '固定版本' }}</p></div>
        <el-timeline><el-timeline-item v-for="event in history.events" :key="event.revision" :timestamp="localTime(event.recorded_at)"><strong>{{ feedbackStateLabels[event.state] }}</strong><p>{{ event.reason }}</p><small>记录版本 {{ event.revision }}</small></el-timeline-item></el-timeline>
        <el-form v-if="mayManage && transitions.length" label-position="top" :disabled="preparing || writing" @submit.prevent="transition">
          <h3>登记处理决定</h3><el-form-item label="处理阶段"><el-select v-model="operation" :disabled="writing || !!pending"><el-option v-for="item in transitions" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
          <template v-if="operation === 'REVISION_LINKED'"><el-form-item label="修订后的Agent版本"><el-select v-model="revisionTarget" :loading="targetsLoading" filterable><el-option v-for="item in targets.filter(item => item.id !== selected!.target_version)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item></template>
          <el-form-item v-if="operation === 'proposals'" label="关联修订提案"><el-input v-model="proposalId" placeholder="填写已建立的修订提案编号" /></el-form-item>
          <el-form-item v-if="operation === 'reviews'" label="修订采用决定"><el-input v-model="decisionId" placeholder="填写已确认的修订采用决定编号" /></el-form-item>
          <el-form-item v-if="['EVALUATED', 'PUBLISHED', 'CLOSED'].includes(operation)" label="独立验收记录"><el-input v-model="acceptanceId" placeholder="填写实际验收记录编号" /></el-form-item>
          <el-form-item v-if="['PUBLISHED', 'CLOSED'].includes(operation)" label="发布记录"><el-input v-model="releaseId" placeholder="填写实际发布记录编号" /></el-form-item>
          <el-form-item v-if="operation === 'closures'" label="修订核准记录"><el-input v-model="approvalId" placeholder="填写修订版本的核准记录编号" /></el-form-item>
          <el-form-item v-if="operation === 'closures' && kind === 'SOURCE'" label="核准后的解析、索引及绑定版本"><el-input v-model="parseVersion" placeholder="解析版本" /><el-input v-model="indexVersion" placeholder="索引版本" /><el-input v-model="bindingRevision" placeholder="绑定版本" /></el-form-item>
          <el-form-item label="决定依据"><el-input v-model="decisionReason" type="textarea" :rows="4" :maxlength="4000" :disabled="writing || !!pending" /></el-form-item>
          <el-button native-type="submit" type="primary" :loading="writing" :disabled="!operation || !decisionReason.trim()">{{ pending ? '继续原处理请求' : '保存处理决定' }}</el-button>
        </el-form>
        <el-collapse><el-collapse-item title="版本与操作信息"><el-descriptions :column="1" border><el-descriptions-item label="反馈编号">{{ selected.feedback_id }}</el-descriptions-item><el-descriptions-item label="关联版本">{{ selected.target_version }}</el-descriptions-item><el-descriptions-item label="当前记录版本">{{ history.revision }}</el-descriptions-item></el-descriptions></el-collapse-item></el-collapse>
      </template>
    </el-drawer>
  </main>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import useUserStore from '@/store/modules/user'
import OperationsNavigation from '@/components/Medical/OperationsNavigation.vue'
import { listProjects } from '@/api/medical/projects'
import type { Project } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { readRuleList } from '@/api/medical/rule-versions'
import { readSourceCatalogue } from '@/api/medical/source-governance'
import { queryDrafts } from '@/api/medical/formal-agent-drafts'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { clearFeedbackWrite, feedbackInputIssue, feedbackKindLabels, feedbackStateLabels, lookupFeedbackWrite, operationsAllowed, prepareFeedbackWrite, readFeedbackHistory, readFeedbackPage, readPendingFeedback, writeFeedback } from '@/api/medical/business-operations'
import type { FeedbackHistory, FeedbackKind, FeedbackMetadata, FeedbackReceipt, FeedbackWrite } from '@/api/medical/business-operations'
const user = useUserStore(), projectId = ref(''), kind = ref<FeedbackKind>('RULE'), stateFilter = ref('')
const projects = ref<Project[]>([]), nextProject = ref<string | null>(null), bootLoading = ref(false), rows = ref<FeedbackReceipt[]>([]), nextAfter = ref<string | null>(null)
const loading = ref(false), writing = ref(false), preparing=ref(false),error = ref(''), createVisible = ref(false), historyVisible = ref(false), historyLoading = ref(false)
const targets = ref<{ id: string; name: string }[]>([]), nextTarget = ref<string | null>(null), targetsLoading = ref(false), targetsError = ref(''), target = ref(''), reason = ref('')
const selected = ref<FeedbackReceipt | null>(null), history = ref<FeedbackHistory | null>(null), operation = ref(''), decisionReason = ref(''), revisionTarget = ref(''), proposalId = ref(''), decisionId = ref(''), acceptanceId = ref(''), releaseId = ref(''), approvalId = ref(''), parseVersion = ref(''), indexVersion = ref(''), bindingRevision = ref('')
const pending = ref<FeedbackWrite | null>(null)
const recovery=shallowRef<FeedbackMetadata|null>(null),recoveryLoading=ref(false),recoveryReason=ref(''),recoveryNotice=ref(''),notice=ref('')
let generation = 0, controller: AbortController | null = null, targetController: AbortController | null = null
function clear() { generation++; controller?.abort(); targetController?.abort(); controller = null; targetController = null; rows.value = []; nextAfter.value = null; history.value = null; selected.value = null; createVisible.value = false; historyVisible.value = false; bootLoading.value = false; loading.value = false; historyLoading.value = false; targetsLoading.value = false; writing.value = false; error.value = ''; targets.value = []; nextTarget.value = null; pending.value = null;recoveryLoading.value=false;recovery.value=user.medicalSession?readPendingFeedback(user.medicalSession):null }
onBeforeRouteLeave(()=>{if(writing.value){error.value='正在提交反馈，请等待读取结果后离开。';return false}return true})
const active = useMedicalRouteRead('MedicalBusinessFeedback', clear)
const mayRead = computed(() => operationsAllowed(user.medicalSession, 'feedback.read', projectId.value))
const mayCreate = computed(() => !!projectId.value && operationsAllowed(user.medicalSession, 'feedback.create', projectId.value))
const mayManage = computed(() => operationsAllowed(user.medicalSession, 'feedback.manage', projectId.value))
const busy = computed(() => writing.value || preparing.value || historyLoading.value)
const projectName = computed(() => projects.value.find(item => item.project_id === projectId.value)?.name ?? '')
const targetNames = computed(() => Object.fromEntries(targets.value.map(item => [item.id, item.name])))
const visibleRows = computed(() => rows.value.filter(item => !stateFilter.value || item.state === stateFilter.value))
const availableStates = computed(() => Object.fromEntries(Object.entries(feedbackStateLabels).filter(([value]) => kind.value === 'AGENT' ? ['OPEN', 'REVIEWED', 'REVISION_LINKED', 'EVALUATED', 'PUBLISHED', 'CLOSED', 'REJECTED'].includes(value) : ['PENDING_REVIEW', 'PROPOSAL_LINKED', 'REVIEWED', 'CLOSED', 'REJECTED'].includes(value))))
const latest = computed(() => history.value?.events.at(-1))
const transitions = computed(() => {
  const state = latest.value?.state
  const values = kind.value === 'AGENT' ? ({ OPEN: ['REVIEWED', 'REJECTED'], REVIEWED: ['REVISION_LINKED', 'REJECTED'], REVISION_LINKED: ['EVALUATED'], EVALUATED: ['PUBLISHED'], PUBLISHED: ['CLOSED'] } as Record<string, string[]>)[state ?? ''] ?? [] : ({ PENDING_REVIEW: ['proposals', 'rejections'], PROPOSAL_LINKED: ['reviews', 'rejections'], REVIEWED: ['closures'] } as Record<string, string[]>)[state ?? ''] ?? []
  const labels: Record<string, string> = { proposals: '关联修订提案', reviews: '确认修订采用', closures: '完成修订核准', rejections: '不采纳反馈' }
  return values.map(value => ({ value, label: labels[value] ?? feedbackStateLabels[value] }))
})
function localTime(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
async function loadProjects(after?: string | null) {
  const session = user.medicalSession; if (!session?.current_context || !active.value) return
  const current = generation, reading = new AbortController(); controller = reading; bootLoading.value = true
  try { const result = await listProjects(session.current_context, after ? { after } : {}, reading.signal); if (generation !== current || user.medicalSession !== session) return; projects.value = after ? [...projects.value, ...result.projects] : result.projects; nextProject.value = result.next_after; if (!projectId.value && result.projects.length) projectId.value = result.projects[0]!.project_id }
  catch (failure) { if (generation === current && !reading.signal.aborted) error.value = failure instanceof Error ? failure.message : '项目读取失败' }
  finally { if (generation === current) bootLoading.value = false }
}
async function load(after?: string | null) {
  const session = user.medicalSession; if (!session || !mayRead.value || !projectId.value || !active.value || loading.value) return
  const current = generation, reading = new AbortController(); controller = reading; loading.value = true; error.value = ''
  try { const page = await readFeedbackPage(session, kind.value, projectId.value, after ?? null, reading.signal); if (generation !== current || user.medicalSession !== session) return; rows.value = after ? [...rows.value, ...page.items] : page.items; nextAfter.value = page.next_after_id }
  catch (failure) { if (generation === current && !reading.signal.aborted) error.value = failure instanceof Error ? failure.message : '反馈读取失败' }
  finally { if (generation === current) loading.value = false }
}
async function loadTargets(after?: string | null) {
  const session = user.medicalSession; if (!session || !projectId.value || !active.value) return
  const current = generation, reading = new AbortController(); targetController?.abort(); targetController = reading; targetsLoading.value = true; targetsError.value = ''
  try {
    let values: { id: string; name: string }[], next: string | null
    if (kind.value === 'RULE') { const page = await readRuleList(session, { limit: 50, ...(after ? { after } : {}) }, reading.signal); values = page.items.map(item => ({ id: item.rule.rule_version, name: item.rule.reason })); next = page.next_after }
    else if (kind.value === 'SOURCE') { const page = await readSourceCatalogue(session, { project_id: projectId.value, limit: 50, ...(after ? { after } : {}) }, reading.signal); values = page.items.map(item => ({ id: item.source_version, name: item.title })); next = page.next_after }
    else { const page = await queryDrafts(() => session, 'AGENT', projectId.value, after ?? null, reading.signal); values = page.items.map(item => ({ id: item.version_id, name: item.name ?? 'Agent版本 · ' + localTime(item.created_at) })); next = page.next_cursor }
    if (generation !== current || user.medicalSession !== session || targetController !== reading) return
    targets.value = after ? [...targets.value, ...values] : values; nextTarget.value = next
  } catch (failure) { if (generation === current && !reading.signal.aborted) targetsError.value = failure instanceof Error ? failure.message : '关联版本读取失败' }
  finally { if (generation === current && targetController === reading) targetsLoading.value = false }
}
function openCreate() { if (!mayCreate.value || busy.value || recovery.value) return; target.value = ''; reason.value = ''; pending.value = null; createVisible.value = true; void loadTargets() }
async function create() {
  const session = user.medicalSession; if (!session || !mayCreate.value || writing.value || preparing.value || !target.value || !reason.value.trim()) return
  const current=generation
  preparing.value=true
  try{if (!pending.value) {const prepared = await prepareFeedbackWrite(session, kind.value, 'create', { project_id: projectId.value, target_version: target.value,
    ...(kind.value === 'AGENT' ? {} : { kind: kind.value, document_revision: null, revised_document_revision: null, review_id: null, return_id: null }), reason: reason.value.trim() });if(current!==generation||user.medicalSession!==session)return;pending.value=prepared}}
  catch(failure){error.value=failure instanceof Error?failure.message:'反馈参数未通过核查';return}finally{preparing.value=false}
  recovery.value=readPendingFeedback(session)
  await submitPending(true)
}
async function openHistory(row: FeedbackReceipt) {
  if (busy.value || recovery.value) return
  selected.value = row; history.value = null; historyVisible.value = true; decisionReason.value = ''; operation.value = ''; pending.value = null
  await refreshHistory(); void loadTargets()
}
async function refreshHistory() {
  const session = user.medicalSession; if (!session || !selected.value) return
  const current = generation, reading = new AbortController(); controller = reading; historyLoading.value = true
  try { const value = await readFeedbackHistory(session, kind.value, selected.value.feedback_id, reading.signal); if (generation === current && user.medicalSession === session) { history.value = value; operation.value = transitions.value[0]?.value ?? '' } }
  catch (failure) { if (generation === current && !reading.signal.aborted) error.value = failure instanceof Error ? failure.message : '处理记录读取失败' }
  finally { if (generation === current) historyLoading.value = false }
}
async function transition() {
  const session = user.medicalSession; if (!session || !latest.value || !selected.value || writing.value || preparing.value || !mayManage.value || !decisionReason.value.trim()) return
  const current=generation
  if (!pending.value) {
    let body: Record<string, unknown> = { expected_revision: history.value!.revision, reason: decisionReason.value.trim() }
    if (kind.value === 'AGENT') body = { ...body, state: operation.value, revision_version: operation.value === 'REVISION_LINKED' ? revisionTarget.value : latest.value.revision_version, acceptance_id: acceptanceId.value || latest.value.acceptance_id || null, release_id: releaseId.value || latest.value.release_id || null }
    else if (operation.value === 'proposals') body.proposal_id = proposalId.value
    else if (operation.value === 'reviews') body.decision_id = decisionId.value
    else if (operation.value === 'closures') { body.approval_id = approvalId.value; body.source_selection = kind.value === 'SOURCE' ? { parse_version: parseVersion.value, index_version: indexVersion.value, binding_revision: bindingRevision.value } : null }
    const action=kind.value==='AGENT'?'transitions':operation.value,issue=feedbackInputIssue(kind.value,action,body)
    if(issue){error.value=issue;return}
    preparing.value=true
    try{const prepared = await prepareFeedbackWrite(session, kind.value, action, body, selected.value.feedback_id,projectId.value);if(current!==generation||user.medicalSession!==session)return;pending.value=prepared}
    catch(failure){error.value=failure instanceof Error?failure.message:'处理决定未通过核查';return}finally{preparing.value=false}
    recovery.value=readPendingFeedback(session)
  }
  await submitPending(false)
}
async function submitPending(creating: boolean) {
  const session = user.medicalSession; if (!session || !pending.value) fail('CONTEXT_CHANGED', '反馈提交身份无效')
  const current = generation, reading = new AbortController(); controller = reading; writing.value = true; error.value = ''
  try { const result = await writeFeedback(session, pending.value, reading.signal); if (generation !== current || user.medicalSession !== session) return;clearFeedbackWrite(session,pending.value);pending.value = null;recovery.value=null;createVisible.value = false; selected.value = result; await load(); if (!creating) await refreshHistory() }
  catch (failure) { if (generation === current && !reading.signal.aborted) error.value = failure instanceof Error ? failure.message : '反馈提交未确认，请继续核查原请求' }
  finally { if (generation === current) writing.value = false }
}
async function recoverWrite(){
 const session=user.medicalSession,saved=recovery.value;if(!session||!saved||writing.value||recoveryLoading.value)return
 const current=generation,reading=new AbortController();controller=reading;recoveryLoading.value=true;error.value=''
 try{const result=await lookupFeedbackWrite(session,saved,reading.signal);if(current!==generation||user.medicalSession!==session)return;if(!result){recoveryNotice.value='当前尚未查到原操作记录，请继续核查。此结果不表示请求没有发送。';return}clearFeedbackWrite(session,saved);pending.value=null;recovery.value=null;createVisible.value=false;historyVisible.value=false;kind.value=saved.kind;projectId.value=saved.project_id;await nextTick();notice.value='原反馈操作已经完成，当前结果：'+feedbackStateLabels[result.state];await load()}
 catch(failure){if(current===generation&&!reading.signal.aborted)error.value=failure instanceof Error?failure.message:'原反馈核查未完成'}
 finally{recoveryLoading.value=false}
}
async function resumeWrite(){
 const session=user.medicalSession,saved=recovery.value;if(!session||!saved||writing.value||preparing.value||!recoveryReason.value.trim())return
 const originalReason=recoveryReason.value.trim();preparing.value=true
 try{kind.value=saved.kind;projectId.value=saved.project_id;await nextTick();pending.value=await prepareFeedbackWrite(session,saved.kind,saved.operation,{...saved.parameters,reason:originalReason},saved.feedback_id??undefined,saved.project_id);preparing.value=false;await submitPending(saved.operation==='create')}
 catch(failure){error.value=failure instanceof Error?failure.message:'原参数尚未核对完成'}
 finally{preparing.value=false}
}
watch([projectId, kind], () => { clear(); stateFilter.value = ''; if (projectId.value && active.value) { void load(); void loadTargets() } })
watch(() => user.medicalSession, () => { clear(); projects.value = []; projectId.value = ''; if (active.value) void loadProjects() }, { immediate: true })
onBeforeUnmount(clear)
</script>
<style scoped>
.feedback-page{max-width:1480px;margin:auto;padding:28px}.feedback-heading,.feedback-panel-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.feedback-heading{margin-bottom:12px}.feedback-heading h1{font-size:24px;margin:0}.feedback-page h2{font-size:17px;margin:0}.feedback-page>.el-card,.feedback-page>.el-alert{margin-bottom:20px}.feedback-page .el-table small{display:block;color:var(--el-text-color-secondary);font-size:12px;margin-top:6px}.feedback-page .el-form-item{min-width:200px}.feedback-panel-heading span{font-size:13px;color:var(--el-text-color-secondary)}.feedback-pagination{padding-top:16px;text-align:right}.feedback-detail-heading{margin-bottom:28px}.feedback-detail-heading h2{margin:16px 0;line-height:1.6}.feedback-detail-heading p,.feedback-page .el-timeline p{color:var(--el-text-color-regular);line-height:1.8}.feedback-page :deep(.el-drawer .el-form){margin:28px 0;padding:22px;background:var(--el-fill-color-light);border-radius:8px}.feedback-page :deep(.el-dialog .el-select){width:100%}@media(max-width:650px){.feedback-page{padding:16px}.feedback-heading{flex-wrap:wrap}}
</style>

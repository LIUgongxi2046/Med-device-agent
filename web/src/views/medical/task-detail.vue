<template>
  <main class="medical-page task-workspace" data-task-workspace>
    <div class="workspace-breadcrumb"><router-link to="/projects">项目中心</router-link><span>/</span><router-link v-if="canList" :to="'/projects/' + projectId + '/tasks'">项目任务</router-link><span>/ 注册文档工作区</span></div>
    <header class="workspace-hero">
      <div><span class="workspace-eyebrow">REGULATORY DOCUMENT AGENT</span><h1>注册文档工作区</h1><p>确认章节计划，撰写并核验来源，再进入人工审查。</p></div>
      <div class="workspace-hero-status"><el-tag effect="plain">{{ stage === 'review' ? '只读审查' : stage === 'write' ? '文稿编辑' : '任务配置' }}</el-tag><span>以当前任务与已保存文稿为准</span></div>
    </header>
    <nav class="workspace-steps" aria-label="注册文档 Agent 处理步骤">
      <router-link :to="configPath" :aria-current="stage === 'config' ? 'step' : undefined" class="workspace-step"><span class="step-number">1</span><span><strong>任务与计划</strong><small>资料范围与章节安排</small></span></router-link>
      <router-link v-if="selectedDocument" :to="documentPath('write')" :aria-current="stage === 'write' ? 'step' : undefined" class="workspace-step"><span class="step-number">2</span><span><strong>撰写与核验</strong><small>正文编辑与来源核查</small></span></router-link>
      <span v-else class="workspace-step unavailable" aria-disabled="true"><span class="step-number">2</span><span><strong>撰写与核验</strong><small>等待获授权文稿</small></span></span>
      <router-link v-if="selectedDocument" :to="documentPath('review')" :aria-current="stage === 'review' ? 'step' : undefined" class="workspace-step"><span class="step-number">3</span><span><strong>审查与签发</strong><small>只读全文与人工决定</small></span></router-link>
      <span v-else class="workspace-step unavailable" aria-disabled="true"><span class="step-number">3</span><span><strong>审查与签发</strong><small>等待获授权文稿</small></span></span>
    </nav>
    <details class="workspace-session"><summary>当前身份与企业上下文</summary><session-panel /></details>
    <p v-if="!validRoute" role="alert">项目或任务标识未通过接口校验</p>
    <p v-else-if="!permitted" role="status">读取任务需要当前企业身份及任务读取权限。请在上方核查会话。</p>
    <div class="workspace-status"><span v-if="current">{{ taskStatusLabels[current.detail.status] }} · {{ current.detail.document_ids.length }} 份文稿 · {{ current.detail.missing_requirements.length }} 项待补</span><span v-else>任务尚未读取</span><el-button :disabled="!permitted || loading || user.medicalWriting" @click="reload">重新读取任务</el-button></div>
    <p v-if="loading" role="status">正在核查当前任务修订及获授权关联对象</p>
    <section v-if="current" class="workspace-grid" data-task-detail>
      <section class="workspace-main" :aria-label="stage === 'config' ? '任务与章节计划' : stage === 'review' ? '只读审查全文' : '文稿撰写与核验'">
        <template v-if="stage === 'config'">
          <div class="panel-heading"><div><span class="workspace-eyebrow">STEP 01</span><h2>任务与章节计划</h2><p>确认固定版本与待补条件后，维护章节目标和研究问题。</p></div></div>
          <task-plan-editor v-if="session" :session="session" :current="current" :active="routeActive" @saved="reload" />
          <p><router-link :to="'/projects/' + projectId + '/tasks/' + taskId + '/field-targets'">准备严格字段目标与来源映射</router-link> · 提议值不代表确认事实，也不会启动任务。</p>
          <task-run-create v-if="session" :session="session" :current="current" :active="routeActive" @accepted="reload" />
          <details id="task-input-requirements" ref="inputRequirementsPanel" tabindex="-1" class="workspace-details" :open="current.detail.missing_requirements.length > 0"><summary>输入缺项 · {{ current.detail.missing_requirements.length }} 项</summary>
            <el-table :data="current.detail.missing_requirements" row-key="requirement_id" empty-text="当前任务没有输入缺项">
              <el-table-column prop="requirement_id" label="缺项标识" min-width="180" />
              <el-table-column label="关联目录节点" min-width="180"><template #default="scope">{{ scope.row.node_id ?? '未关联目录节点' }}</template></el-table-column>
              <el-table-column label="关联字段" min-width="180"><template #default="scope">{{ scope.row.field_id ?? '未关联字段' }}</template></el-table-column>
              <el-table-column prop="reason_code" label="原因代码" min-width="160" />
              <el-table-column label="当前获准处理动作" min-width="200"><template #default="scope">{{ scope.row.allowed_actions.join('、') || '当前没有获准处理动作' }}</template></el-table-column>
            </el-table>
          </details>
          <div class="workspace-next" v-if="selectedDocument"><span>已有获授权文稿，可进入撰写工作区。</span><router-link :to="documentPath('write')">进入撰写与核验 →</router-link></div>
        </template>
        <template v-else>
          <div class="panel-heading"><div><span class="workspace-eyebrow">{{ stage === 'review' ? 'STEP 03 · READ ONLY' : 'STEP 02' }}</span><h2>{{ stage === 'review' ? '审查全文' : '撰写与核验' }}</h2><p>{{ stage === 'review' ? '仅审查服务端已保存正文。未保存的编辑保留在撰写阶段。' : '修改正文并核验引用；保存后再进入审查。' }}</p></div><router-link v-if="stage === 'review' && selectedDocument" :to="documentPath('write')">返回修改正文</router-link></div>
          <nav v-if="current.detail.document_ids.length > 1" class="workspace-documents" aria-label="获授权文稿"><router-link v-for="(id, index) in current.detail.document_ids" :key="id" :to="documentPath(stage === 'review' ? 'review' : 'write', id)" :aria-current="selectedDocument === id ? 'page' : undefined">文稿 {{ index + 1 }}</router-link></nav>
          <p v-if="!selectedDocument" role="alert">该文稿不在本次任务获授权清单中，无法读取。请返回任务计划核查。</p>
          <document-editor v-else-if="session" :key="selectedDocument + ':' + stage" :session="session" :project-id="current.detail.project_id" :document-id="selectedDocument" :active="routeActive" :review-mode="stage === 'review'" :task-id="current.detail.task_id" :history-revision-request="route.query.history_revision" @returned="reload" @navigate-write="navigateWrite" />
          <details v-if="stage === 'review'" class="workspace-details"><summary>审查与签发条件</summary><p>选区标注、回复与处理使用当前正文权限。签发操作尚未接入本工作区，不会产生签发结果。</p></details>
        </template>
      </section>
      <aside class="workspace-aside" aria-label="来源与任务运行">
        <section class="workspace-card workspace-next-actions" aria-label="当前任务处理入口">
          <span class="workspace-eyebrow">TASK CONTEXT</span><h2>当前任务待办</h2>
          <p v-if="current.detail.missing_requirements.length" class="workspace-attention">{{ current.detail.missing_requirements.length }} 项输入待补，先核对缺项再继续。</p>
          <p v-else>当前任务没有输入缺项；这不表示专业核验或审查已经通过。</p>
          <ul v-if="current.detail.missing_requirements.length" class="workspace-requirements"><li v-for="item in current.detail.missing_requirements.slice(0, 5)" :key="item.requirement_id"><strong>{{ item.reason_code }}</strong><span>{{ item.field_id ? '关联字段：' + item.field_id : item.node_id ? '关联章节：' + item.node_id : '任务级输入要求' }}</span></li></ul>
          <router-link :to="configPath + '#task-input-requirements'" @click="focusInputRequirements">查看全部输入缺项与处理权限</router-link>
          <router-link :to="`/projects/${projectId}/tasks/${taskId}/field-targets`">字段目标与来源映射</router-link>
          <details class="workspace-advanced"><summary>研究运行设置</summary><router-link :to="`/projects/${projectId}/tasks/${taskId}/node-producer-rules`">研究节点生产授权</router-link></details>
        </section>
        <section class="workspace-card"><h2>固定版本与来源</h2><dl><dt>任务版本</dt><dd>{{ current.detail.task_revision }}</dd><dt>已确认计划</dt><dd>{{ current.detail.plan_revision ?? '尚未确认计划' }}</dd><dt>来源快照</dt><dd>{{ current.detail.snapshot_id ?? '尚未形成来源快照' }}</dd></dl><p>文稿引用及原文定位在正文的来源核验区域查看；来源权限按当前会话核查。</p></section>
        <section class="workspace-card"><h2>任务与运行</h2><p>{{ current.detail.active_run_ids.length }} 项获授权活动运行</p><p v-if="!current.detail.active_run_ids.length">当前没有获授权活动运行。运行状态以服务端记录为准。</p><details v-for="(id, index) in current.detail.active_run_ids" :key="id" class="workspace-details" @toggle="toggleRun(id, $event)"><summary>运行 {{ index + 1 }} · 状态与恢复</summary><run-detail v-if="session && expandedRuns.has(id)" :session="session" :project-id="current.detail.project_id" :task-id="current.detail.task_id" :run-id="id" :active="routeActive" /></details></section>
        <section class="workspace-card"><h2>待处理事项</h2><p v-if="!current.detail.waiting_item_ids.length">当前没有获授权待办</p><ul v-else><li v-for="id in current.detail.waiting_item_ids" :key="id">{{ id }}</li></ul></section>
        <details class="workspace-card"><summary>候选资料读取</summary><p>当前任务记录尚未提供已授权的候选引用，暂不能读取候选资料。</p><candidate-read-panel v-if="session" :session="session" :project-id="projectId" :candidate-id="null" :active="routeActive" /></details>
        <details class="workspace-card"><summary>任务身份与记录</summary><dl><dt>项目标识</dt><dd>{{ current.detail.project_id }}</dd><dt>任务标识</dt><dd>{{ current.detail.task_id }}</dd><dt>当前记录修订</dt><dd>{{ current.revision }}</dd><dt>责任人员</dt><dd>{{ current.detail.owner_id }}</dd></dl></details>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { projectActionAllowed } from '@/api/medical/project-write'
import { taskIdReference, taskStatusLabels } from '@/api/medical/tasks'
import { readCurrentTask, taskReadAllowed } from '@/api/medical/task-detail'
import type { CurrentTask } from '@/api/medical/task-detail'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { taskWorkspaceStage, authorizedWorkspaceDocument, workspaceDocumentPath } from '@/api/medical/task-workspace'
import SessionPanel from './session-panel.vue'
import TaskRunCreate from './task-run-create.vue'
import TaskPlanEditor from './task-plan-editor.vue'
import DocumentEditor from './document-editor.vue'
import RunDetail from './run-detail.vue'
import CandidateReadPanel from './candidate-read-panel.vue'

const route = useRoute(); const router = useRouter(); const user = useUserStore()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const taskId = computed(() => typeof route.params.taskId === 'string' ? route.params.taskId : '')
const validRoute = computed(() => schemaIssues(taskIdReference, projectId.value).length === 0 && schemaIssues(taskIdReference, taskId.value).length === 0)
const permitted = computed(() => routeActive.value && validRoute.value && taskReadAllowed(session.value, projectId.value, taskId.value))
const canList = computed(() => validRoute.value && projectActionAllowed(session.value, 'project.task.read', projectId.value))
const current = ref<CurrentTask | null>(null); const loading = ref(false)
const expandedRuns = ref<Set<string>>(new Set())
function toggleRun(id: string, event: Event): void {
  const next = new Set(expandedRuns.value)
  if ((event.currentTarget as HTMLDetailsElement).open) next.add(id)
  else next.delete(id)
  expandedRuns.value = next
}
const stage = computed(() => taskWorkspaceStage(route.path))
const configPath = computed(() => '/projects/' + projectId.value + '/tasks/' + taskId.value + '/config')
const selectedDocument = computed(() => authorizedWorkspaceDocument(current.value?.detail.document_ids ?? [], route.params.documentId))
function documentPath(mode: 'write' | 'review', id = selectedDocument.value): string {
  return id ? workspaceDocumentPath(projectId.value, taskId.value, id, mode) : configPath.value
}
function navigateWrite(): void { if (permitted.value && selectedDocument.value) void router.push(documentPath('write')) }
const inputRequirementsPanel = ref<HTMLDetailsElement>()
function focusInputRequirements(): void {
  if (route.hash !== '#task-input-requirements' || stage.value !== 'config' || !current.value || !permitted.value || document.hidden) return
  const target = inputRequirementsPanel.value
  if (!target) return
  target.open = true; target.scrollIntoView({ block: 'start' }); target.focus({ preventScroll: true })
}
let generation = 0; let controller: AbortController | null = null
function clear(): void { generation += 1; controller?.abort(); controller = null; current.value = null; loading.value = false; expandedRuns.value = new Set() }
const routeActive = useMedicalRouteRead(['MedicalTaskDetail', 'MedicalTaskWrite', 'MedicalTaskReview'], clear)
async function reload(): Promise<void> {
  const actual = session.value; const project = projectId.value; const task = taskId.value; const path = route.fullPath
  if (!actual || !permitted.value || loading.value || user.medicalWriting || document.hidden) return
  clear()
  const reading = new AbortController(); const requested = generation; controller = reading; loading.value = true
  try {
    const result = await readCurrentTask(actual, project, task, reading.signal)
    if (generation === requested && session.value === actual && route.fullPath === path && !document.hidden) { current.value = result; await nextTick(); if (generation === requested && session.value === actual && route.fullPath === path) focusInputRequirements() }
  } finally { if (generation === requested) { loading.value = false; controller = null } }
}
watch(() => [session.value, JSON.stringify(session.value?.current_context), JSON.stringify(session.value?.action_summary), route.fullPath, routeActive.value], () => {
  clear()
  if (permitted.value && !document.hidden) void reload()
}, { immediate: true, flush: 'sync' })
function visibility(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', visibility)
window.addEventListener('pagehide', clear); window.addEventListener('offline', clear); window.addEventListener('medical-failure', clear)
onBeforeUnmount(() => {
  clear(); document.removeEventListener('visibilitychange', visibility)
  window.removeEventListener('pagehide', clear); window.removeEventListener('offline', clear); window.removeEventListener('medical-failure', clear)
})
</script>

<style scoped>
.task-workspace { color: #243247; font-size: 14px; line-height: 1.6; }
.workspace-breadcrumb, .workspace-status { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; color: #526279; }
.workspace-status { justify-content: space-between; }
.workspace-hero { display: flex; justify-content: space-between; gap: 24px; padding: 24px; background: linear-gradient(115deg, #edf4ff, #f8fafc); border: 1px solid #dce3ec; border-radius: 12px; }
.workspace-hero h1 { font-size: 24px; margin: 6px 0; }.workspace-hero p, .panel-heading p { margin: 4px 0; color: #526279; }
.workspace-eyebrow { color: #175fae; font-size: 12px; letter-spacing: .08em; font-weight: 600; }
.workspace-hero-status { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; font-size: 12px; color: #5c6d82; }
.workspace-steps { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 20px 0; }
.workspace-step { display: flex; align-items: center; gap: 12px; padding: 16px; background: #fff; border: 1px solid #dce3ec; border-radius: 8px; color: #526279; text-decoration: none; }
.workspace-step[aria-current="step"] { color: #175fae; border-color: #1768c4; background: #edf4ff; }.workspace-step small { display: block; font-size: 12px; margin-top: 2px; }.step-number { display: grid; place-items: center; flex: 0 0 32px; height: 32px; border-radius: 50%; background: #f4f6fa; font-weight: 600; }.workspace-step[aria-current="step"] .step-number { background: #1768c4; color: #fff; }.unavailable { color: #738096; }
.workspace-next-actions a { display: block; margin-top: 12px; color: #175fae; }.workspace-requirements { padding-left: 18px; font-size: 12px; }.workspace-requirements li { margin: 10px 0; }.workspace-requirements span { display: block; color: #66758b; }.workspace-card .workspace-attention { color: #975a16; }.workspace-advanced { margin-top: 16px; border-top: 1px solid #e9edf3; padding-top: 8px; }.workspace-advanced summary { cursor: pointer; padding: 6px 0; }
.workspace-session { margin: 16px 0; }.workspace-grid { display: grid; grid-template-columns: minmax(0, 1fr) 290px; gap: 24px; align-items: start; }.workspace-main, .workspace-card { padding: 22px; background: #fff; border: 1px solid #dce3ec; border-radius: 8px; min-width: 0; }.workspace-main { box-shadow: 0 3px 18px #24324708; }.workspace-aside { display: grid; gap: 16px; min-width: 0; }.workspace-card h2, .panel-heading h2 { font-size: 17px; margin: 0 0 8px; }.workspace-card p { color: #526279; font-size: 13px; }.workspace-card dt { color: #5c6d82; font-size: 12px; margin-top: 12px; }.workspace-card dd { margin: 4px 0 0; overflow-wrap: anywhere; }.workspace-card li { overflow-wrap: anywhere; }.panel-heading { display: flex; justify-content: space-between; gap: 16px; padding-bottom: 20px; margin-bottom: 20px; border-bottom: 1px solid #e9edf3; }.workspace-details { margin-top: 20px; }.workspace-details > summary, .workspace-card > summary, .workspace-session > summary { cursor: pointer; min-height: 38px; padding: 8px 0; font-weight: 600; }.workspace-next { display: flex; justify-content: space-between; gap: 16px; margin-top: 24px; padding-top: 20px; border-top: 1px solid #e9edf3; }.workspace-documents { display: flex; gap: 8px; overflow-x: auto; margin-bottom: 20px; }.workspace-documents a { padding: 8px 16px; border: 1px solid #dce3ec; border-radius: 6px; white-space: nowrap; }.workspace-documents [aria-current] { background: #edf4ff; color: #175fae; }.task-workspace a:focus-visible, summary:focus-visible { outline: 2px solid #1768c4; outline-offset: 3px; }
@media (max-width: 1060px) { .workspace-grid { grid-template-columns: minmax(0, 1fr); }.workspace-aside { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 650px) { .workspace-hero, .panel-heading, .workspace-next { flex-direction: column; }.workspace-hero-status { align-items: flex-start; }.workspace-steps { gap: 6px; }.workspace-step { flex-direction: column; align-items: flex-start; padding: 12px 8px; gap: 8px; }.workspace-step small { display: none; }.workspace-main, .workspace-card, .workspace-hero { padding: 16px; }.workspace-aside { grid-template-columns: minmax(0, 1fr); }.workspace-details > summary, .workspace-card > summary, .workspace-session > summary { min-height: 44px; } }
</style>

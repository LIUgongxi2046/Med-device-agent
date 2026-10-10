<template>
  <main class="medical-page">
    <h1>创建项目任务</h1>
    <session-panel />
    <router-link v-if="validProject" :to="'/projects/' + projectId + '/tasks'">返回项目任务</router-link>
    <p v-if="!permitted" role="alert">创建任务需要当前企业身份、项目读取及任务创建许可</p>
    <p v-if="busy" role="status">正在处理任务输入及当前许可</p>
    <section v-if="project">
      <h2>{{ project.name }}</h2>
      <p>项目属性版本：{{ project.profile.profile_revision }}</p>
      <p v-if="project.status !== 'ACTIVE'" role="alert">当前项目状态不允许创建任务</p>
      <section v-if="pending" aria-label="原任务创建操作">
        <p>创建操作标识：{{ pending.key }}</p>
        <el-button :disabled="disabled" @click="recover">读取原创建结果与输入草稿</el-button>
        <el-button v-if="original" :disabled="disabled" @click="retry">继续原创建请求</el-button>
        <p v-if="unregistered" role="status">服务器尚未登记此操作。再次提交需要保留全部原参数及当前操作标识。</p>
        <p v-if="draftRestored" role="status">完整输入草稿已经恢复。继续提交时重新核验当前许可、模板、资料和发布版本。</p>
      </section>
      <el-form label-position="top" :disabled="disabled || Boolean(original)" @submit.prevent="submit">
        <el-form-item label="目标文档" :error="errors.document_type">
          <el-select v-model="documentType" placeholder="请选择目标文档" @change="clearTemplates">
            <el-option v-for="(label, value) in taskDocumentLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-button :disabled="disabled || Boolean(original) || !documentType" @click="loadTemplates(1, null)">查询兼容的核准模板</el-button>
        <section v-if="templates">
          <el-table :data="templates.items" :row-key="(item: ApplicableTemplate) => item.template.template_version" empty-text="当前项目没有兼容的核准模板">
            <el-table-column prop="template.template_version" label="模板版本" min-width="260" />
            <el-table-column label="适用条件" min-width="140"><template #default="cell">{{ applicabilityLabels[cell.row.applicability as Applicability] }}</template></el-table-column>
            <el-table-column label="待确认要求" min-width="120"><template #default="cell">{{ cell.row.missing_requirements.length }}</template></el-table-column>
            <el-table-column label="模板选择" min-width="140"><template #default="cell">
              <el-button :disabled="disabled || Boolean(original)" @click="selected = cell.row">选择此模板</el-button>
            </template></el-table-column>
          </el-table>
          <pagination :total="Number(templates.page_info.total)" :page="templates.page_info.page" :limit="templates.page_info.page_size"
            :page-sizes="[20]" :auto-scroll="false" @pagination="paginateTemplates" />
        </section>
        <el-form-item label="已选择核准模板" :error="errors.template_version"><p>{{ selected?.template.template_version ?? original?.template_version ?? '尚未选择模板' }}</p></el-form-item>
        <el-collapse v-if="selected"><el-collapse-item title="完整目录、字段、附件与适用条件" name="template">
          <pre class="task-definition">{{ JSON.stringify(selected, null, 2) }}</pre>
        </el-collapse-item></el-collapse>
        <el-form-item label="Agent 发布版本" :error="errors.agent_release_id"><el-input v-model="releaseId" placeholder="已发布版本的 UUID" /></el-form-item>
        <el-form-item label="责任人员" :error="errors.owner_id">
          <el-select v-model="ownerId" filterable placeholder="请选择当前获授权责任人员">
            <el-option v-for="member in ownerOptions" :key="member.user_id" :value="member.user_id" :label="member.user_id" />
          </el-select>
          <p v-if="ownerOptions.length === 0">当前查询没有具备项目任务读取许可的责任人员。</p>
          <el-button v-if="ownerPage?.next_after" :disabled="disabled || Boolean(original)" @click="loadMoreOwners">读取下一页责任人员</el-button>
        </el-form-item>
        <el-form-item label="任务目标" :error="errors.goal"><el-input v-model="goal" type="textarea" :autosize="{ minRows: 3, maxRows: 12 }" /></el-form-item>
        <el-form-item label="继续修订的签发文稿版本（可选）" :error="errors.based_on_document_revision">
          <el-input v-model="basedOn" clearable placeholder="原签发文稿版本的 UUID" />
        </el-form-item>
        <h2>来源范围</h2>
        <el-form-item label="来源选择模式" :error="errors.scope_selection">
          <el-select v-model="mode" @change="changeMode">
            <el-option label="当前项目核准资料" value="PROJECT_APPROVED" />
            <el-option label="固定资料集版本" value="COLLECTION_VERSIONS" />
            <el-option label="固定资料版本" value="SOURCE_VERSIONS" />
            <el-option label="固定资料与资料集组合" value="COMBINED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="['COLLECTION_VERSIONS', 'COMBINED'].includes(mode)" label="资料集版本">
          <el-button :disabled="disabled || Boolean(original)" @click="loadScope('COLLECTION', null)">查询当前获授权资料集</el-button>
          <el-select v-model="collectionVersions" multiple filterable placeholder="请选择固定资料集版本">
            <el-option v-for="item in collectionOptions" :key="item.version_id" :value="item.version_id" :label="scopeLabel(item)" />
          </el-select>
          <p v-if="collectionPage && collectionOptions.length === 0">当前查询没有可用于本项目任务的资料集。</p>
          <el-button v-if="collectionPage?.next_after" :disabled="disabled || Boolean(original)" @click="loadScope('COLLECTION', collectionPage)">读取下一页资料集</el-button>
        </el-form-item>
        <el-form-item v-if="['SOURCE_VERSIONS', 'COMBINED'].includes(mode)" label="资料版本">
          <el-button :disabled="disabled || Boolean(original)" @click="loadScope('SOURCE', null)">查询当前项目核准资料</el-button>
          <el-select v-model="sourceVersions" multiple filterable placeholder="请选择固定资料版本">
            <el-option v-for="item in sourceOptions" :key="item.version_id" :value="item.version_id" :label="scopeLabel(item)" />
          </el-select>
          <p v-if="sourcePage && sourceOptions.length === 0">当前查询没有可用于本项目任务的核准资料。</p>
          <el-button v-if="sourcePage?.next_after" :disabled="disabled || Boolean(original)" @click="loadScope('SOURCE', sourcePage)">读取下一页资料</el-button>
        </el-form-item>
        <el-collapse v-if="sourceOptions.length || collectionOptions.length"><el-collapse-item title="查询资料的固定版本与完整范围" name="scope">
          <pre class="task-definition">{{ JSON.stringify([...sourceOptions, ...collectionOptions], null, 2) }}</pre>
        </el-collapse-item></el-collapse>
        <el-button :disabled="disabled || Boolean(original)" @click="clearScope">清空来源范围</el-button>
        <el-button disabled>恢复已发布配置的默认范围</el-button>
        <p>尚未取得已发布配置的默认资料范围；当前范围按照本次明确选择保存。</p>
        <p>清空范围将明确保存为空范围；资料缺少时任务保留补充要求。网络采集需要独立的批准版本。</p>
        <el-form-item label="允许网络采集"><el-switch v-model="networkEnabled" @change="changeNetwork" /></el-form-item>
        <template v-if="networkEnabled">
          <el-form-item label="网络策略批准版本"><el-input v-model="networkPolicy" /></el-form-item>
          <el-form-item label="实际检索词批准版本"><el-input v-model="approvedQuery" /></el-form-item>
        </template>
        <el-button :disabled="disabled || Boolean(original)" @click="disableNetwork">关闭网络采集</el-button>
        <el-button native-type="submit" type="primary" :disabled="disabled || Boolean(original) || project.status !== 'ACTIVE'">创建任务</el-button>
      </el-form>
      <section v-if="created" aria-label="任务创建结果">
        <p role="status">任务已经保存。当前状态：{{ taskStatusLabels[created.detail.status] }}</p>
        <p>任务标识：{{ created.detail.task_id }}；来源快照：{{ created.detail.snapshot_id }}</p>
        <el-table :data="created.detail.missing_requirements" row-key="requirement_id" empty-text="当前任务没有输入缺项">
          <el-table-column prop="requirement_id" label="要求标识" min-width="220" />
          <el-table-column prop="node_id" label="目录节点" min-width="220" />
          <el-table-column prop="field_id" label="字段标识" min-width="220" />
          <el-table-column prop="reason_code" label="原因代码" min-width="160" />
        </el-table>
        <router-link :to="'/projects/' + projectId + '/tasks/' + created.detail.task_id + '/config'">打开任务并编辑计划</router-link>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import useUserStore from '@/store/modules/user'
import Pagination from '@/components/Pagination/index.vue'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession, Project } from '@/api/medical/protocol'
import { readProject } from '@/api/medical/projects'
import { projectActionAllowed, projectTextIssue } from '@/api/medical/project-write'
import { taskDocumentLabels, taskIdReference, taskStatusLabels } from '@/api/medical/tasks'
import { readCurrentTask } from '@/api/medical/task-detail'
import type { CurrentTask } from '@/api/medical/task-detail'
import { applicabilityLabels, listApplicableTemplates } from '@/api/medical/template-applicability'
import type { Applicability, ApplicableTemplate, ApplicableTemplatePage, DocumentType, TemplateQuery } from '@/api/medical/template-applicability'
import { clearTaskCreate, originalTaskCreate, originalTaskCreateDraft, pendingTaskCreate, prepareTaskCreate, submitTaskCreate, taskCreateReference } from '@/api/medical/task-create'
import type { PendingTaskCreate, SourceScope, TaskCreate } from '@/api/medical/task-create'
import { listTaskCreationOwners } from '@/api/medical/task-creation-selection'
import type { TaskCreationOwner, TaskCreationOwners } from '@/api/medical/task-creation-selection'
import { listTaskScopeChoices } from '@/api/medical/task-scope-selection'
import type { TaskScopeChoice, TaskScopeChoices, TaskScopeKind } from '@/api/medical/task-scope-selection'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import SessionPanel from './session-panel.vue'

const route = useRoute(); const user = useUserStore(); const session = computed<MedicalSession | null>(() => user.medicalSession)
const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const validProject = computed(() => schemaIssues(taskIdReference, projectId.value).length === 0)
const permitted = computed(() => active.value && validProject.value && projectActionAllowed(session.value, 'task.create', projectId.value)
  && projectActionAllowed(session.value, 'project.read', projectId.value))
const project = ref<Project | null>(null); const busy = ref(false)
const disabled = computed(() => busy.value || user.medicalWriting || !permitted.value || document.hidden)
const documentType = ref<DocumentType | ''>(''); const templates = ref<ApplicableTemplatePage | null>(null)
const selected = ref<ApplicableTemplate | null>(null); const releaseId = ref(''); const ownerId = ref(''); const goal = ref(''); const basedOn = ref('')
const ownerPage = ref<TaskCreationOwners | null>(null); const ownerOptions = ref<TaskCreationOwner[]>([])
const mode = ref<SourceScope['mode']>('SOURCE_VERSIONS'); const sourceVersions = ref<string[]>([]); const collectionVersions = ref<string[]>([])
const sourcePage = ref<TaskScopeChoices | null>(null); const collectionPage = ref<TaskScopeChoices | null>(null)
const sourceOptions = ref<TaskScopeChoice[]>([]); const collectionOptions = ref<TaskScopeChoice[]>([])
const networkEnabled = ref(false); const networkPolicy = ref(''); const approvedQuery = ref('')
const pending = ref<PendingTaskCreate | null>(null); const original = ref<TaskCreate | null>(null); const created = ref<CurrentTask | null>(null)
const unregistered = ref(false); const errors = reactive<Record<string, string>>({})
const draftRestored = ref(false)
let generation = 0; let controller: AbortController | null = null
function clearTemplates(): void { templates.value = null; selected.value = null }
function disableNetwork(): void { networkEnabled.value = false; networkPolicy.value = ''; approvedQuery.value = '' }
function changeNetwork(): void { if (!networkEnabled.value) disableNetwork() }
function changeMode(): void { sourceVersions.value = []; collectionVersions.value = [] }
function clearScope(): void { mode.value = 'SOURCE_VERSIONS'; changeMode() }
function clear(): void {
  generation++; controller?.abort(); controller = null; busy.value = false; project.value = null; clearTemplates()
  documentType.value = ''; releaseId.value = ''; ownerId.value = ''; goal.value = ''; basedOn.value = ''; clearScope(); disableNetwork()
  ownerPage.value = null; ownerOptions.value = []
  sourcePage.value = null; collectionPage.value = null; sourceOptions.value = []; collectionOptions.value = []
  pending.value = null; original.value = null; created.value = null; unregistered.value = false; draftRestored.value = false
  for (const key of Object.keys(errors)) delete errors[key]
}
const active = useMedicalRouteRead('MedicalTaskCreate', clear)
function current(actual: MedicalSession, version: number): boolean {
  return version === generation && session.value === actual && active.value && !document.hidden
}
async function loadProject(): Promise<void> {
  const actual = session.value; const target = projectId.value; const version = generation
  if (!actual?.current_context || !permitted.value || document.hidden || user.medicalWriting) return
  controller = new AbortController(); const reading = controller; busy.value = true
  try {
    const value = await readProject(actual.current_context, target, reading.signal)
    if (!current(actual, version)) return
    project.value = value; pending.value = pendingTaskCreate(actual, target)
    if (value.status === 'ACTIVE') {
      const owners = await listTaskCreationOwners(actual, target, { limit: 20 }, null, reading.signal)
      if (!current(actual, version)) return
      ownerPage.value = owners; ownerOptions.value = owners.items
      if (owners.items.some(item => item.user_id === actual.user.user_id)) ownerId.value = actual.user.user_id
    }
  } finally { if (current(actual, version)) { busy.value = false; controller = null } }
}
async function loadMoreOwners(): Promise<void> {
  const actual = session.value; const previous = ownerPage.value; const version = generation; const target = projectId.value
  if (!actual || !previous?.next_after || disabled.value || original.value) return
  busy.value = true; controller = new AbortController(); const reading = controller
  try {
    const next = await listTaskCreationOwners(actual, target, { limit: previous.limit, after: previous.next_after,
      dataset_watermark: previous.dataset_watermark }, previous, reading.signal)
    if (!current(actual, version)) return
    if (next.items.some(item => ownerOptions.value.some(existing => existing.user_id === item.user_id)))
      fail('CONTENT_MISMATCH', '责任人员分页不能重复原成员')
    ownerPage.value = next; ownerOptions.value = [...ownerOptions.value, ...next.items]
  } finally { if (current(actual, version)) { busy.value = false; controller = null } }
}
function scopeLabel(item: TaskScopeChoice): string {
  return (item.kind === 'SOURCE' ? item.source_id : item.title) + ' / ' + item.version_id
}
async function loadScope(kind: TaskScopeKind, previous: TaskScopeChoices | null): Promise<void> {
  const actual = session.value; const version = generation; const target = projectId.value
  if (!actual || disabled.value || original.value || project.value?.status !== 'ACTIVE') return
  if (previous && !previous.next_after) return
  const query = previous ? { limit: previous.limit, after: previous.next_after!, dataset_watermark: previous.dataset_watermark } : { limit: 20 }
  busy.value = true; controller = new AbortController(); const reading = controller
  try {
    const result = await listTaskScopeChoices(actual, target, kind, query, previous, reading.signal)
    if (!current(actual, version)) return
    const options = kind === 'SOURCE' ? sourceOptions : collectionOptions
    if (previous && result.items.some(item => options.value.some(existing => existing.version_id === item.version_id)))
      fail('CONTENT_MISMATCH', '资料分页不能重复原固定版本')
    options.value = previous ? [...options.value, ...result.items] : result.items
    if (kind === 'SOURCE') sourcePage.value = result; else collectionPage.value = result
  } finally { if (current(actual, version)) { busy.value = false; controller = null } }
}
async function loadTemplates(page: number, previous: ApplicableTemplatePage | null): Promise<void> {
  const actual = session.value; const value = project.value; const type = documentType.value; const version = generation
  if (!actual || !value || !type || disabled.value || original.value) return
  const query: TemplateQuery = { document_type: type, profile_revision: value.profile.profile_revision, page, page_size: 20 }
  if (previous) { query.dataset_watermark = previous.page_info.watermark; query.filter_sha256 = previous.page_info.filter_sha256 }
  busy.value = true; controller = new AbortController(); const reading = controller
  try {
    const result = await listApplicableTemplates(actual, value, query, previous, reading.signal)
    if (current(actual, version) && documentType.value === type) templates.value = result
  } finally { if (current(actual, version)) { busy.value = false; controller = null } }
}
async function paginateTemplates(value: { page: number; limit: number }): Promise<void> {
  if (value.limit !== 20) fail('SCHEMA_INVALID', '模板分页需要当前批准的页面容量')
  await loadTemplates(value.page, value.page === 1 ? null : templates.value)
}
function body(): TaskCreate | null {
  for (const key of Object.keys(errors)) delete errors[key]
  if (!project.value || !documentType.value || !selected.value) {
    errors.document_type = documentType.value ? '' : '请选择目标文档'; errors.template_version = selected.value ? '' : '请选择兼容的核准模板'; return null
  }
  const scope = { mode: mode.value, collection_versions: collectionVersions.value, source_versions: sourceVersions.value,
    network_enabled: networkEnabled.value, network_policy_revision: networkEnabled.value ? networkPolicy.value : null,
    approved_query_revision: networkEnabled.value ? approvedQuery.value : null }
  const value = { schema_version: '1.0.0', project_id: projectId.value, profile_revision: project.value.profile.profile_revision,
    document_type: documentType.value, template_version: selected.value.template.template_version, agent_release_id: releaseId.value,
    scope_selection: scope, owner_id: ownerId.value, goal: goal.value, based_on_document_revision: basedOn.value || null }
  for (const issue of schemaIssues(taskCreateReference, value)) errors[issue.path.split('/')[1] || 'goal'] = '此项内容未通过完整接口校验'
  if (projectTextIssue(goal.value, Number.MAX_SAFE_INTEGER)) errors.goal = '请填写有效任务目标'
  if (Object.keys(errors).length) return null
  return JSON.parse(JSON.stringify(value)) as TaskCreate
}
async function finish(actual: MedicalSession, version: number, operation: PendingTaskCreate, result: CurrentTask, recovered = false): Promise<void> {
  if (!current(actual, version)) return
  const saved = recovered ? result : await originalTaskCreate(actual, operation, new AbortController().signal)
  if (!saved || saved.detail.task_id !== result.detail.task_id || saved.detail.task_revision !== result.detail.task_revision
      || saved.detail.snapshot_id !== result.detail.snapshot_id || saved.detail.owner_id !== result.detail.owner_id)
    fail('CONTENT_MISMATCH', '任务创建需要保存后重新读取一致')
  const latest = await readCurrentTask(actual, operation.project_id, saved.detail.task_id, new AbortController().signal)
  if (!current(actual, version)) return
  clearTaskCreate(actual, operation); pending.value = null; original.value = null; created.value = latest; unregistered.value = false; draftRestored.value = false
}
async function submit(): Promise<void> {
  const actual = session.value; const version = generation
  if (!actual || disabled.value || original.value || project.value?.status !== 'ACTIVE') return
  const input = body(); if (!input) return
  busy.value = true; user.medicalWriting = true
  try {
    const operation = await prepareTaskCreate(actual, input)
    if (!current(actual, version)) return
    pending.value = operation; original.value = input
    await finish(actual, version, operation, await submitTaskCreate(actual, operation, input))
  } finally { if (current(actual, version)) busy.value = false; user.medicalWriting = false }
}
async function retry(): Promise<void> {
  const actual = session.value; const operation = pending.value; const input = original.value; const version = generation
  if (!actual || !operation || !input || disabled.value) return
  busy.value = true; user.medicalWriting = true
  try { await finish(actual, version, operation, await submitTaskCreate(actual, operation, input)) }
  finally { if (current(actual, version)) busy.value = false; user.medicalWriting = false }
}
async function recover(): Promise<void> {
  const actual = session.value; const operation = pending.value; const version = generation
  if (!actual || !operation || disabled.value) return
  busy.value = true
  try {
    const result = await originalTaskCreate(actual, operation, new AbortController().signal)
    if (result) await finish(actual, version, operation, result, true)
    else {
      const draft = await originalTaskCreateDraft(actual, operation, new AbortController().signal)
      if (!current(actual, version)) return
      unregistered.value = draft === null
      if (draft) {
        const input = draft.content; original.value = input; clearTemplates(); documentType.value = input.document_type
        releaseId.value = input.agent_release_id; ownerId.value = input.owner_id; goal.value = input.goal; basedOn.value = input.based_on_document_revision ?? ''
        mode.value = input.scope_selection.mode; sourceVersions.value = [...input.scope_selection.source_versions]
        collectionVersions.value = [...input.scope_selection.collection_versions]; networkEnabled.value = input.scope_selection.network_enabled
        networkPolicy.value = input.scope_selection.network_policy_revision ?? ''; approvedQuery.value = input.scope_selection.approved_query_revision ?? ''
        draftRestored.value = true
      }
    }
  } finally { if (current(actual, version)) busy.value = false }
}
watch(() => [session.value, route.fullPath, active.value], () => { clear(); if (permitted.value) void loadProject() }, { immediate: true, flush: 'sync' })
function visibility(): void { if (document.hidden) clear(); else if (permitted.value) void loadProject() }
document.addEventListener('visibilitychange', visibility); window.addEventListener('pagehide', clear); window.addEventListener('offline', clear)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('pagehide', clear); window.removeEventListener('offline', clear) })
</script>

<style scoped>
.task-definition { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

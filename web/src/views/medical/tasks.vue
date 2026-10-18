<template>
  <main class="medical-page">
    <h1>项目任务</h1>
    <session-panel />
    <router-link to="/projects">返回项目中心</router-link>
    <router-link v-if="validProject && projectActionAllowed(session, 'task.create', projectId)" :to="'/projects/' + projectId + '/tasks/new'">创建项目任务</router-link>
    <p v-if="!validProject" role="alert">项目标识未通过接口校验</p>
    <p v-else-if="!permitted">任务列表需要当前企业身份及项目任务读取许可</p>
    <template v-else>
      <p>当前项目：{{ projectId }}</p>
      <section v-if="waitingContext.requested" class="waiting-context" aria-labelledby="waiting-context-title">
        <h2 id="waiting-context-title">定位待办关联任务</h2>
        <p>只根据当前任务页返回的待办或活动运行关联核对。不会自动扫描其他页，也不会自动打开任务。</p>
        <el-alert v-if="waitingContext.invalid" title="待办定位参数无效，未用于关联匹配。请返回工作台重新选择待办。" type="warning" :closable="false" />
        <p v-else-if="loading" role="status">正在核查当前页的任务关联</p>
        <p v-else-if="listing && contextMatches.length" role="status">当前页找到 {{ contextMatches.length }} 项关联任务。请核对下方关联类型；“活动运行关联”不代表该待办仍在此任务中等待。</p>
        <p v-else-if="listing" role="status">当前页未找到关联任务。这不表示待办或任务不存在；可继续翻页或调整筛选条件。</p>
        <p v-else>查询当前任务页后显示关联结果。</p>
        <el-button :disabled="loading" @click="clearWaitingContext">退出待办定位</el-button>
      </section>
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="任务状态" :error="errors.status">
          <el-select v-model="form.status" clearable placeholder="全部获授权状态" :disabled="loading" @change="invalidateResult">
            <el-option v-for="(label, value) in taskStatusLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人员 UUID" :error="errors.owner_id">
          <el-input v-model="form.owner" clearable :disabled="loading" @input="invalidateResult" />
        </el-form-item>
        <el-form-item label="更新时间范围" :error="errors.updated_at">
          <el-date-picker v-model="form.dates" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间"
            :disabled="loading" @change="invalidateResult" />
        </el-form-item>
        <el-button native-type="submit" :disabled="loading || user.medicalWriting">查询任务</el-button>
      </el-form>
      <p>时间选择器使用当前设备时区，提交查询时转换为 UTC。</p>
      <p v-if="loading" role="status">正在核查获授权任务及固定版本</p>
      <el-alert v-if="readError" :title="readError" type="error" :closable="false"><el-button :disabled="loading" @click="search">重新查询首页</el-button></el-alert>
      <section v-if="listing" data-task-table>
        <p>获授权任务数量：{{ listing.page_info.total }}；分页水位：{{ listing.page_info.watermark }}</p>
        <p>过滤摘要：{{ listing.page_info.filter_sha256 }}</p>
        <el-table :data="listing.items" :row-key="(item: TaskItem) => item.detail.task_id" empty-text="当前授权和过滤范围没有任务">
          <el-table-column v-if="waitingContext.requested && !waitingContext.invalid" label="待办关联" min-width="165"><template #default="scope">
            <el-tag v-if="waitingTaskMatch(scope.row, waitingContext) === 'WAITING_ITEM'" type="success">待办直接关联</el-tag>
            <el-tag v-else-if="waitingTaskMatch(scope.row, waitingContext) === 'ACTIVE_RUN'" type="warning">活动运行关联，待核对</el-tag>
            <span v-else>当前记录未匹配</span>
          </template></el-table-column>
          <el-table-column prop="goal" label="任务目标" min-width="260" />
          <el-table-column label="文档类型" min-width="150"><template #default="scope">{{ taskDocumentLabels[scope.row.document_type as keyof typeof taskDocumentLabels] }}</template></el-table-column>
          <el-table-column prop="detail.owner_id" label="责任人员" min-width="260" />
          <el-table-column label="任务状态" min-width="140"><template #default="scope">{{ taskStatusLabels[scope.row.detail.status as TaskStatus] }}</template></el-table-column>
          <el-table-column label="缺项数量" min-width="100"><template #default="scope">{{ scope.row.detail.missing_requirements.length }}</template></el-table-column>
          <el-table-column prop="detail.task_revision" label="固定任务版本" min-width="260" />
          <el-table-column prop="updated_at" label="更新时间（UTC）" min-width="240" />
          <el-table-column label="任务信息" min-width="120"><template #default="scope"><el-button link type="primary" @click="selected = scope.row">阅读完整信息</el-button></template></el-table-column>
          <el-table-column label="当前任务" min-width="140"><template #default="scope">
            <router-link v-if="taskReadAllowed(session, projectId, scope.row.detail.task_id)"
              :to="'/projects/' + projectId + '/tasks/' + scope.row.detail.task_id + '/config'">读取当前状态</router-link>
          </template></el-table-column>
        </el-table>
        <pagination :total="Number(listing.page_info.total)" :page="page" :limit="pageSize" :page-sizes="[10, 20, 50, 100]"
          :auto-scroll="false" @pagination="paginate" />
      </section>
      <el-dialog :model-value="selected !== null" title="任务固定版本及当前许可" destroy-on-close @close="selected = null">
        <template v-if="selected">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="任务目标">{{ selected.goal }}</el-descriptions-item>
            <el-descriptions-item label="文档类型">{{ taskDocumentLabels[selected.document_type] }}</el-descriptions-item>
            <el-descriptions-item label="任务标识">{{ selected.detail.task_id }}</el-descriptions-item>
            <el-descriptions-item label="任务版本">{{ selected.detail.task_revision }}</el-descriptions-item>
            <el-descriptions-item label="项目标识">{{ selected.detail.project_id }}</el-descriptions-item>
            <el-descriptions-item label="责任人员">{{ selected.detail.owner_id }}</el-descriptions-item>
            <el-descriptions-item label="任务状态">{{ taskStatusLabels[selected.detail.status] }}</el-descriptions-item>
            <el-descriptions-item label="计划版本">{{ selected.detail.plan_revision ?? '尚未确认计划' }}</el-descriptions-item>
            <el-descriptions-item label="来源快照">{{ selected.detail.snapshot_id ?? '尚未形成来源快照' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间（UTC）">{{ selected.updated_at }}</el-descriptions-item>
          </el-descriptions>
          <h2>完整缺项及允许处理动作</h2>
          <el-table :data="selected.detail.missing_requirements" empty-text="当前任务没有输入缺项">
            <el-table-column prop="requirement_id" label="缺项标识" min-width="200" />
            <el-table-column prop="node_id" label="目录节点" min-width="240" />
            <el-table-column prop="field_id" label="字段标识" min-width="240" />
            <el-table-column prop="reason_code" label="原因代码" min-width="160" />
            <el-table-column label="允许动作" min-width="180"><template #default="scope">{{ scope.row.allowed_actions.join('、') }}</template></el-table-column>
          </el-table>
          <h2>当前获授权关联对象</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="文稿标识">{{ selected.detail.document_ids.join('、') || '当前没有获授权文稿' }}</el-descriptions-item>
            <el-descriptions-item label="运行标识">{{ selected.detail.active_run_ids.join('、') || '当前没有获授权活动运行' }}</el-descriptions-item>
            <el-descriptions-item label="待办标识">{{ selected.detail.waiting_item_ids.join('、') || '当前没有获授权待办' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-dialog>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import Pagination from '@/components/Pagination/index.vue'
import { schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { projectActionAllowed } from '@/api/medical/project-write'
import { listTasks, taskDocumentLabels, taskFilterReference, taskIdReference, taskStatusLabels } from '@/api/medical/tasks'
import type { TaskFilter, TaskItem, TaskPage, TaskStatus } from '@/api/medical/tasks'
import { taskReadAllowed } from '@/api/medical/task-detail'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { readWaitingTaskContext, waitingTaskMatch } from '@/utils/medical-task-context'
import SessionPanel from './session-panel.vue'

const user = useUserStore()
const route = useRoute(), router = useRouter()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const validProject = computed(() => schemaIssues(taskIdReference, projectId.value).length === 0)
const permitted = computed(() => routeActive.value && validProject.value && projectActionAllowed(session.value, 'project.task.read', projectId.value))
const form = reactive<{ status: TaskStatus | ''; owner: string; dates: [Date, Date] | null }>({ status: '', owner: '', dates: null })
const errors = reactive<Record<string, string>>({})
const listing = ref<TaskPage | null>(null)
const selected = ref<TaskItem | null>(null)
const readError = ref('')
const waitingContext = computed(() => readWaitingTaskContext(route.query))
const contextMatches = computed(() => (listing.value?.items ?? []).filter(item => waitingTaskMatch(item, waitingContext.value)))
async function clearWaitingContext(): Promise<void> {
  const query = { ...route.query }; delete query.waiting_item_id; delete query.run_id
  await router.replace({ path: route.path, query })
}
const loading = ref(false); const page = ref(1); const pageSize = ref(20)
let generation = 0; let controller: AbortController | null = null
let boundFilter: TaskFilter | null = null

function invalidateResult(): void {
  generation += 1; controller?.abort(); controller = null
  readError.value = ''; listing.value = null; selected.value = null; loading.value = false; boundFilter = null; page.value = 1
  for (const key of Object.keys(errors)) delete errors[key]
}

function reset(): void {
  invalidateResult(); form.status = ''; form.owner = ''; form.dates = null; pageSize.value = 20
}
const routeActive = useMedicalRouteRead('MedicalProjectTasks', reset)

async function load(filter: TaskFilter, previous: TaskPage | null): Promise<void> {
  const actual = session.value; const target = projectId.value; const path = route.fullPath
  invalidateResult()
  if (!actual || !permitted.value || document.hidden) return
  const current = generation; const reading = new AbortController(); controller = reading; loading.value = true
  try {
    const result = await listTasks(actual, target, filter, previous, reading.signal)
    if (generation !== current || session.value !== actual || route.fullPath !== path || !permitted.value || reading.signal.aborted || document.hidden) return
    listing.value = result; boundFilter = { ...filter }; page.value = result.page_info.page; pageSize.value = result.page_info.page_size
  } catch {
    if (generation === current && session.value === actual && route.fullPath === path && permitted.value && !reading.signal.aborted && !document.hidden) {
      readError.value = '当前任务页读取失败，无法判断关联关系。请重新查询首页；若权限变化，请重新核查会话。'
    }
  } finally { if (generation === current) loading.value = false }
}

async function search(): Promise<void> {
  const filter: TaskFilter = { page: 1, page_size: pageSize.value }
  invalidateResult()
  if (form.status) filter.status = form.status
  if (form.owner) {
    if (schemaIssues(taskIdReference, form.owner).length) errors.owner_id = '请填写获授权责任人员的规范 UUID'
    filter.owner_id = form.owner
  }
  if (form.dates) {
    const [from, to] = form.dates
    if (!(from instanceof Date) || !(to instanceof Date) || !Number.isFinite(from.getTime()) || !Number.isFinite(to.getTime()) || from > to) {
      errors.updated_at = '请填写有效时间并保持结束时间不早于开始时间'
    } else { filter.updated_from = from.toISOString(); filter.updated_to = to.toISOString() }
  }
  for (const issue of schemaIssues(taskFilterReference, filter)) errors[issue.path.slice(1) || 'status'] = '过滤内容未通过接口校验'
  if (Object.keys(errors).length) return
  await load(filter, null)
}

async function paginate(value: { page: number; limit: number }): Promise<void> {
  const original = listing.value; const bound = boundFilter
  if (loading.value || !original || !bound) return
  const filter: TaskFilter = { ...bound, page: value.page, page_size: value.limit }
  if (value.limit !== original.page_info.page_size || value.page === 1) {
    filter.page = 1; delete filter.dataset_watermark; delete filter.filter_sha256
    await load(filter, null)
  } else {
    filter.dataset_watermark = original.page_info.watermark; filter.filter_sha256 = original.page_info.filter_sha256
    await load(filter, original)
  }
}

watch(() => [session.value, route.fullPath, routeActive.value], () => {
  reset()
  if (permitted.value && !document.hidden) void search()
}, { immediate: true, flush: 'sync' })
function visibilityChanged(): void { if (document.hidden) reset() }
document.addEventListener('visibilitychange', visibilityChanged)
window.addEventListener('pagehide', reset)
onBeforeUnmount(() => {
  reset(); document.removeEventListener('visibilitychange', visibilityChanged); window.removeEventListener('pagehide', reset)
})
</script>

<style scoped>
.waiting-context { margin: 20px 0; padding: 16px; background: #f5f7fa; border: 1px solid #dce3ec; border-radius: 8px; }
.waiting-context h2 { margin-top: 0; font-size: 18px; }
.waiting-context p { line-height: 1.7; }
</style>

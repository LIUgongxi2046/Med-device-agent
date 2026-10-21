<template>
  <section data-run-detail class="run-detail" aria-label="运行状态与处理记录">
    <header><h2>运行跟踪</h2><p>以服务端记录为准，分别核对步骤、等待事项与调用费用。</p></header>
    <el-button :disabled="!permitted || loading" @click="reload">读取运行状态</el-button>
    <el-button v-if="cancellationAllowed" :disabled="writing || loading || !canRequestCancellation" @click="openCancellation">申请停止运行</el-button>
    <el-button v-if="pending" :disabled="writing || loading" @click="recover">查询原停止请求</el-button>
    <p v-if="pending" role="status">原停止请求待核查：{{ pending.key }}。请求版本：{{ pending.expected_revision }}。</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="streaming" role="status">正在接收持久运行事件</p>
    <p v-if="loading" role="status">正在读取运行与费用记录</p>
    <p v-if="!permitted" role="status">当前未激活运行读取或没有读取许可，不展示运行记录。</p>
    <p v-else-if="!current && !loading && !notice" role="status">尚无可展示的运行快照，请读取当前运行状态。</p>
    <template v-if="current">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="运行标识">{{ current.run_id }}</el-descriptions-item>
        <el-descriptions-item label="运行记录修订">{{ current.revision }}</el-descriptions-item>
        <el-descriptions-item label="任务版本">{{ current.task_revision }}</el-descriptions-item>
        <el-descriptions-item label="运行状态">{{ runStatusLabels[current.status] }}</el-descriptions-item>
        <el-descriptions-item label="原因代码">{{ current.reason_code ?? '没有原因代码' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ current.started_at ?? '尚未开始' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ current.ended_at ?? '尚未结束' }}</el-descriptions-item>
        <el-descriptions-item label="预算账户已用">{{ current.budget.spent }} {{ current.budget.currency }}</el-descriptions-item>
        <el-descriptions-item label="预算账户预留">{{ current.budget.reserved }} {{ current.budget.currency }}</el-descriptions-item>
        <el-descriptions-item label="本次批准限额">{{ current.budget.limit }} {{ current.budget.currency }}</el-descriptions-item>
      </el-descriptions>
      <h3>全部步骤（{{ current.steps.length }}）</h3>
      <p class="run-help">当前响应未提供专业节点名称，以下按原步骤标识与角色版本核对；列表顺序不代表执行顺序。</p>
      <el-table :data="current.steps" row-key="step_id" empty-text="当前没有运行步骤">
        <el-table-column prop="step_id" label="步骤标识" min-width="240" />
        <el-table-column label="步骤状态" min-width="240"><template #default="scope">{{ runRecordStatus('step', scope.row.status) }}</template></el-table-column>
        <el-table-column type="expand"><template #default="scope"><dl class="run-record"><dt>角色版本</dt><dd>{{ scope.row.role_version }}</dd><dt>章节标识</dt><dd>{{ scope.row.section_id ?? '未指定章节' }}</dd><dt>步骤修订</dt><dd>{{ scope.row.revision }}</dd></dl></template></el-table-column>
        <el-table-column prop="lease_generation" label="租约代数" />
      </el-table>
      <h3>等待事项（{{ current.waiting_items.length }}）</h3>
      <p class="run-help">事项状态与运行状态分别记录。没有等待事项不代表恢复条件已满足；当前仅供核查，不提交处理。</p>
      <el-table :data="current.waiting_items" row-key="waiting_item_id" empty-text="当前没有获授权等待事项">
        <el-table-column prop="question" label="处理要求" min-width="240" />
        <el-table-column label="事项状态" min-width="240"><template #default="scope">{{ runRecordStatus('waiting', scope.row.status) }}</template></el-table-column>
        <el-table-column type="expand"><template #default="scope"><dl class="run-record"><dt>事项标识</dt><dd>{{ scope.row.waiting_item_id }}</dd><dt>原步骤</dt><dd>{{ scope.row.step_id }}</dd><dt>动作标识</dt><dd>{{ scope.row.action_id }}</dd><dt>目标版本</dt><dd>{{ scope.row.target_revision }}</dd><dt>需求引用</dt><dd>{{ scope.row.requirement_refs.join('、') || '未提供' }}</dd><dt>允许的处理动作</dt><dd>{{ scope.row.allowed_resolution_actions.join('、') || '未提供' }}</dd><dt>处理记录</dt><dd>{{ scope.row.resolution_id ?? '尚无处理记录' }}</dd><dt>事项修订</dt><dd>{{ scope.row.revision }}</dd></dl></template></el-table-column>
        <el-table-column prop="reason_code" label="原因代码" min-width="160" />
        <el-table-column prop="assignee_id" label="责任人员" min-width="240" />
      </el-table>
      <h3>活动及未结算调用（{{ current.inflight_invocations.length }}）</h3>
      <p class="run-help">调用成功不代表费用已结算。此处不是全部历史调用；运行完成也不代表成果已获人工审查或签发。</p>
      <el-table :data="current.inflight_invocations" row-key="invocation_id" empty-text="当前没有活动或未结算调用">
        <el-table-column prop="invocation_id" label="调用标识" min-width="240" />
        <el-table-column label="调用状态" min-width="240"><template #default="scope">{{ runRecordStatus('invocation', scope.row.status) }}</template></el-table-column>
        <el-table-column label="结算状态" min-width="200"><template #default="scope">{{ runRecordStatus('settlement', scope.row.settlement_status) }}</template></el-table-column>
        <el-table-column type="expand"><template #default="scope"><dl class="run-record"><dt>原步骤</dt><dd>{{ scope.row.step_id }}</dd><dt>动作标识</dt><dd>{{ scope.row.action_id }}</dd><dt>调用修订</dt><dd>{{ scope.row.revision }}</dd></dl></template></el-table-column>
        <el-table-column label="实际金额" min-width="180"><template #default="scope">{{ scope.row.settled_amount === null ? '金额尚未确定' : scope.row.settled_amount + ' ' + scope.row.currency }}</template></el-table-column>
        <el-table-column label="Token 数量" min-width="140"><template #default="scope">{{ scope.row.total_tokens ?? '数量尚未确定' }}</template></el-table-column>
      </el-table>
    </template>
    <details v-if="permitted" class="run-recovery-panel"><summary>异常分析与继续／重试准备</summary><task-run-recovery :snapshot="current" :active="active" :can-read="permitted" :can-write="cancellationAllowed" :loading="loading" @refresh="reload" /></details>
    <el-dialog v-model="confirming" title="确认停止当前运行" :close-on-click-modal="false" :close-on-press-escape="!writing" :show-close="!writing">
      <p>运行标识：{{ runId }}；请求版本：{{ expectedRevision }}</p>
      <p>停止请求将关闭后续派发。在途执行的终止状态及费用继续由服务端核查。</p>
      <p v-if="pending">继续原请求需要输入完全相同的原理由。</p>
      <el-input v-model="reason" type="textarea" :rows="4" maxlength="2048" show-word-limit :disabled="writing" aria-label="停止运行的理由" />
      <el-checkbox v-model="acknowledged" :disabled="writing">确认停止这个运行</el-checkbox>
      <template #footer><el-button :disabled="writing" @click="confirming = false">关闭确认</el-button><el-button type="danger" :disabled="!acknowledged || !reason.trim() || writing" :loading="writing" @click="cancelRun">保存停止请求</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import TaskRunRecovery from './task-run-recovery.vue'
import { runRecordStatus } from './run-presentation'
import type { MedicalSession } from '@/api/medical/protocol'
import { taskActionAllowed } from '@/api/medical/task-detail'
import { readRun, runStatusLabels } from '@/api/medical/runs'
import type { RunView } from '@/api/medical/runs'
import { consumeRunEvents } from '@/api/medical/run-events'
import { pendingCancellation, prepareCancellation, submitCancellation, recoverCancellation } from '@/api/medical/run-cancellation'
import type { PendingCancellation } from '@/api/medical/run-cancellation'

const props = defineProps<{ session: MedicalSession; projectId: string; taskId: string; runId: string; active: boolean }>()
const permitted = computed(() => props.active && taskActionAllowed(props.session, props.projectId, props.taskId, 'run.read'))
const current = ref<RunView | null>(null); const loading = ref(false); const streaming = ref(false)
const cancellationAllowed = computed(() => props.active && taskActionAllowed(props.session, props.projectId, props.taskId, 'task.run'))
const canRequestCancellation = computed(() => pending.value !== null || current.value !== null && ['QUEUED', 'RUNNING', 'WAITING_INPUT'].includes(current.value.status))
const pending = ref<PendingCancellation | null>(null); const confirming = ref(false); const acknowledged = ref(false)
const reason = ref(''); const expectedRevision = ref(''); const writing = ref(false); const notice = ref('')
let generation = 0; let controller: AbortController | null = null; let eventsController: AbortController | null = null; let writeController: AbortController | null = null
function clear(): void {
  generation += 1; controller?.abort(); eventsController?.abort(); writeController?.abort(); controller = null; eventsController = null; writeController = null
  current.value = null; loading.value = false; streaming.value = false; writing.value = false; confirming.value = false; acknowledged.value = false
  reason.value = ''; expectedRevision.value = ''; pending.value = null; notice.value = ''
}
function restorePending(): void {
  pending.value = cancellationAllowed.value ? pendingCancellation(props.session, props.projectId, props.taskId, props.runId) : null
}
function openCancellation(): void {
  if (!cancellationAllowed.value || !canRequestCancellation.value || writing.value || document.hidden) return
  expectedRevision.value = pending.value?.expected_revision ?? current.value!.revision
  reason.value = ''; acknowledged.value = false; confirming.value = true
}
async function cancelRun(): Promise<void> {
  if (!cancellationAllowed.value || !confirming.value || !acknowledged.value || writing.value || document.hidden) return
  const requested = generation; const writingRequest = new AbortController(); writeController = writingRequest; writing.value = true
  const session = props.session; const projectId = props.projectId; const taskId = props.taskId; const runId = props.runId
  const body = { expected_revision: expectedRevision.value, reason: reason.value }
  try {
    const original = await prepareCancellation(session, projectId, taskId, runId, body)
    if (requested !== generation || document.hidden || !cancellationAllowed.value) return
    pending.value = original
    const result = await submitCancellation(session, original, body, writingRequest.signal)
    if (requested === generation && cancellationAllowed.value && !document.hidden) {
      confirming.value = false; reason.value = ''; acknowledged.value = false; pending.value = null
      const message = result.run.status === 'CANCEL_REQUESTED' ? '停止请求已保存，终止状态及费用继续核查。' : '停止请求已核查；运行状态：' + runStatusLabels[result.run.status]
      writing.value = false; writeController = null; await reload(); notice.value = message
    }
  } catch {
    if (requested === generation && cancellationAllowed.value && !document.hidden) notice.value = '停止请求结果尚未确认。请保留原请求身份，查询原停止请求，不要新建重复请求。'
  } finally { if (requested === generation) { writeController = null; writing.value = false; restorePending() } }
}
async function recover(): Promise<void> {
  if (!pending.value || !cancellationAllowed.value || writing.value || document.hidden) return
  const requested = generation; const reading = new AbortController(); writeController = reading; writing.value = true
  try {
    const result = await recoverCancellation(props.session, pending.value, reading.signal)
    if (requested !== generation || document.hidden || !cancellationAllowed.value) return
    const message = result.lookup_status === 'NOT_FOUND' ? '尚未查到原停止请求，请使用原版本和理由继续原请求。'
      : result.lookup_status === 'EXPIRED' ? '原请求记录已过期，需要核查运行状态；当前保留原请求身份。'
        : result.operation?.status === 'COMPLETED' ? '原停止请求已保存，当前运行状态由服务端提供。' : '原停止请求仍在处理，请保留原请求身份。'
    writing.value = false; writeController = null; await reload(); notice.value = message
  } catch {
    if (requested === generation && cancellationAllowed.value && !document.hidden) notice.value = '停止请求结果尚未确认。请保留原请求身份，查询原停止请求，不要新建重复请求。'
  } finally { if (requested === generation) { writeController = null; writing.value = false; restorePending() } }
}
async function subscribe(requested: number, snapshot: RunView): Promise<void> {
  const reading = new AbortController(); eventsController = reading; streaming.value = true
  try {
    await consumeRunEvents(props.session, props.projectId, props.taskId, props.runId, snapshot.event_cursor, reading.signal, async () => {
      if (requested !== generation || !permitted.value || document.hidden) { reading.abort(); return }
      const result = await readRun(props.session, props.projectId, props.taskId, props.runId, reading.signal)
      if (requested === generation && permitted.value && !document.hidden) current.value = result
    })
  } catch {
    if (requested === generation && permitted.value && !document.hidden) {
      clear(); restorePending(); notice.value = '运行事件读取中断，已清除旧快照。请重新读取运行状态，不将断开视为运行失败。'
    }
  } finally { if (requested === generation) { eventsController = null; streaming.value = false } }
}
async function reload(): Promise<void> {
  if (!permitted.value || loading.value || document.hidden) return
  clear(); restorePending(); const requested = generation; const reading = new AbortController(); controller = reading; loading.value = true
  try {
    const result = await readRun(props.session, props.projectId, props.taskId, props.runId, reading.signal)
    if (requested === generation && permitted.value && !document.hidden) { current.value = result; void subscribe(requested, result) }
  } catch {
    if (requested === generation && permitted.value && !document.hidden) notice.value = '运行记录读取失败，未保留旧快照。请重新读取运行状态。'
  } finally { if (requested === generation) { controller = null; loading.value = false } }
}
watch(() => [props.session, props.projectId, props.taskId, props.runId, props.active], () => {
  clear(); if (permitted.value && !document.hidden) void reload()
}, { immediate: true, flush: 'sync' })
function visibility(): void { if (document.hidden) clear(); else if (permitted.value) void reload() }
function failure(): void {
  const wasActive = loading.value || streaming.value || writing.value
  clear()
  if (wasActive && permitted.value && !document.hidden) notice.value = '运行读取或操作未能完成，已清除旧记录。请先核查当前权限与运行状态；停止请求结果不明时仅查询原请求。'
}
document.addEventListener('visibilitychange', visibility); window.addEventListener('medical-failure', failure)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('medical-failure', failure) })
</script>

<style scoped>
.run-detail{overflow-wrap:anywhere}.run-detail header p,.run-help{font-size:13px;color:#53697b;line-height:1.7}.run-detail h2{font-size:20px;margin:0 0 8px}.run-detail h3{font-size:16px;margin-top:24px}.run-record{display:grid;grid-template-columns:130px 1fr;gap:10px;padding:12px 20px;font-size:13px}.run-record dt{color:#53697b}.run-record dd{margin:0;white-space:normal;overflow-wrap:anywhere}.run-recovery-panel{margin-top:24px}.run-recovery-panel summary{cursor:pointer}@media(max-width:600px){.run-record{grid-template-columns:1fr;gap:5px;padding:12px}.run-record dd{margin-bottom:8px}}
</style>

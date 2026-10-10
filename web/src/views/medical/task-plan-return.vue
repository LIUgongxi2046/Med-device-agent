<template>
  <section v-if="allowed" aria-label="人工确认与退回计划">
    <el-radio-group v-if="eligible && !pending" v-model="decision" :disabled="busy || !active || user.medicalWriting" @change="clearBasis">
      <el-radio value="RETURN">退回计划</el-radio>
      <el-radio value="CONFIRM" :disabled="props.current.detail.status !== 'PLAN_PENDING'">确认计划</el-radio>
    </el-radio-group>
    <el-button v-if="eligible && !pending" :disabled="busy || !active || user.medicalWriting" @click="readBasis">读取人工决定依据</el-button>
    <section v-if="pending" aria-label="原人工计划操作">
      <p>人工计划操作标识：{{ pending.key }}</p>
      <el-button :disabled="busy || !active || user.medicalWriting" @click="recover">读取原决定结果</el-button>
      <el-button v-if="original" :disabled="busy || !active || user.medicalWriting" @click="retry">继续原决定请求</el-button>
      <el-button v-if="unregistered" :disabled="busy || !active || user.medicalWriting" @click="release">重新填写决定理由</el-button>
    </section>
    <el-form v-if="basis && !pending" label-position="top" :disabled="busy || !active || user.medicalWriting" @submit.prevent="submit">
      <p>目标计划：{{ basis.plan_revision }}；任务修订：{{ basis.expected_revision }}</p>
      <el-form-item label="决定理由"><el-input v-model="reason" type="textarea" maxlength="2000" show-word-limit /></el-form-item>
      <el-button native-type="submit" :disabled="Boolean(projectTextIssue(reason, 2000))">{{ decision === 'CONFIRM' ? '确认计划' : '退回计划' }}</el-button>
    </el-form>
    <p v-if="receipt" role="status">{{ receipt.decision === 'CONFIRM' ? '计划已经确认' : '计划已经退回' }}，决定标识：{{ receipt.decision_id }}；理由：{{ receipt.reason }}</p>
    <p v-if="receipt?.decision === 'CONFIRM'">确认计划版本：{{ receipt.resulting_plan_revision }}；任务版本：{{ receipt.resulting_task_revision }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { taskActionAllowed } from '@/api/medical/task-detail'
import type { CurrentTask } from '@/api/medical/task-detail'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { projectTextIssue } from '@/api/medical/project-write'
import { readTaskPlan } from '@/api/medical/task-plans'
import { clearPlanReturn, originalPlanReturn, pendingPlanReturn, planDecisionBasis, preparePlanReturn,
  releaseUnregisteredReturn, submitPlanReturn } from '@/api/medical/task-plan-return'
import type { PendingPlanReturn, PlanDecisionBaseline, PlanReturnReceipt, PlanReturnRequest } from '@/api/medical/task-plan-return'

const props = defineProps<{ session: MedicalSession; current: CurrentTask; active: boolean }>()
const emit = defineEmits<{ saved: [] }>(); const user = useUserStore(); const busy = ref(false)
const basis = ref<PlanDecisionBaseline | null>(null); const pending = ref<PendingPlanReturn | null>(null)
const original = ref<PlanReturnRequest | null>(null); const receipt = ref<PlanReturnReceipt | null>(null)
const reason = ref(''); const unregistered = ref(false); let request: AbortController | null = null; let generation = 0
const decision = ref<'RETURN' | 'CONFIRM'>('RETURN')
const allowed = computed(() => taskActionAllowed(props.session, props.current.detail.project_id, props.current.detail.task_id, 'plan.confirm'))
const eligible = computed(() => ['NEEDS_MATERIALS', 'PLAN_PENDING'].includes(props.current.detail.status))
function clear(): void {
  generation++; request?.abort(); request = null; basis.value = null; pending.value = null; original.value = null
  receipt.value = null; reason.value = ''; unregistered.value = false; decision.value = 'RETURN'
}
function clearBasis(): void { generation++; request?.abort(); request = null; basis.value = null; reason.value = ''; receipt.value = null }
function current(session: MedicalSession, version: number): boolean {
  return generation === version && props.session === session && props.active && !document.hidden
}
async function readBasis(): Promise<void> {
  if (busy.value || !allowed.value || !props.active || document.hidden || user.medicalWriting) return
  const session = props.session; const version = generation; request = new AbortController(); busy.value = true
  try {
    const actual = await planDecisionBasis(session, props.current, request.signal, decision.value)
    if (current(session, version)) basis.value = actual
  } finally { busy.value = false; request = null }
}
async function finish(session: MedicalSession, version: number, operation: PendingPlanReturn, result: PlanReturnReceipt): Promise<void> {
  if (!current(session, version)) return
  const actual = await originalPlanReturn(session, operation, new AbortController().signal)
  if (!actual || actual.operation_id !== result.operation_id || actual.decision_id !== result.decision_id
      || actual.decision !== result.decision || actual.resulting_task_revision !== result.resulting_task_revision
      || actual.resulting_plan_revision !== result.resulting_plan_revision || actual.resulting_revision !== result.resulting_revision
      || actual.reason !== result.reason) fail('CONTENT_MISMATCH', '人工计划决定需要保存后重新读取一致')
  if (actual.decision === 'CONFIRM') {
    const plan = await readTaskPlan(session, operation.project_id, operation.task_id, actual.resulting_plan_revision, new AbortController().signal)
    if (!plan.plan || !['CONFIRMED', 'SUPERSEDED'].includes(plan.plan.status) || plan.plan.task_revision !== actual.resulting_task_revision
        || plan.plan.content_sha256 !== actual.target_plan_sha256) fail('CONTENT_MISMATCH', '确认结果需要读取完整确认计划及新任务输入版本')
  }
  if (!current(session, version)) return
  clearPlanReturn(session, operation); pending.value = null; original.value = null; basis.value = null; reason.value = ''; receipt.value = actual
}
async function submit(): Promise<void> {
  if (busy.value || user.medicalWriting || !allowed.value || !props.active || document.hidden || !basis.value || pending.value) return
  const session = props.session; const version = generation
  const body: PlanReturnRequest = { schema_version: '1.0.0', target_revision: basis.value.plan_revision,
    expected_revision: basis.value.expected_revision, decision: decision.value, reason: reason.value, baseline_manifest: basis.value }
  busy.value = true; user.medicalWriting = true
  try {
    const operation = await preparePlanReturn(session, body)
    if (!current(session, version)) return
    pending.value = operation; original.value = body
    await finish(session, version, operation, await submitPlanReturn(session, operation, body))
  } finally { busy.value = false; user.medicalWriting = false }
  if (current(session, version) && !pending.value) emit('saved')
}
async function retry(): Promise<void> {
  if (busy.value || user.medicalWriting || !props.active || document.hidden || !pending.value || !original.value) return
  const session = props.session; const version = generation; const operation = pending.value; const body = original.value
  busy.value = true; user.medicalWriting = true
  try { await finish(session, version, operation, await submitPlanReturn(session, operation, body)) }
  finally { busy.value = false; user.medicalWriting = false }
  if (current(session, version) && !pending.value) emit('saved')
}
async function recover(): Promise<void> {
  if (busy.value || user.medicalWriting || !props.active || document.hidden || !pending.value) return
  const session = props.session; const version = generation; const operation = pending.value; let completed = false
  busy.value = true; user.medicalWriting = true
  try {
    const result = await originalPlanReturn(session, operation, new AbortController().signal)
    if (result) { await finish(session, version, operation, result); completed = current(session, version) && !pending.value }
    else if (current(session, version)) unregistered.value = true
  } finally { busy.value = false; user.medicalWriting = false }
  if (completed) emit('saved')
}
async function release(): Promise<void> {
  if (busy.value || user.medicalWriting || !props.active || document.hidden || !pending.value || !unregistered.value) return
  const session = props.session; const version = generation; const operation = pending.value
  busy.value = true; user.medicalWriting = true
  try {
    await releaseUnregisteredReturn(session, operation, new AbortController().signal)
    if (current(session, version)) { pending.value = null; original.value = null; basis.value = null; reason.value = ''; unregistered.value = false }
  } finally { busy.value = false; user.medicalWriting = false }
}
watch(() => [props.session, props.current, props.active], () => {
  clear()
  if (props.active && allowed.value && !document.hidden) pending.value = pendingPlanReturn(props.session, props.current.detail.project_id, props.current.detail.task_id)
}, { immediate: true, flush: 'sync' })
function visibility(): void {
  if (document.hidden) clear()
  else if (props.active && allowed.value) pending.value = pendingPlanReturn(props.session, props.current.detail.project_id, props.current.detail.task_id)
}
document.addEventListener('visibilitychange', visibility); window.addEventListener('pagehide', clear); window.addEventListener('offline', clear)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('pagehide', clear); window.removeEventListener('offline', clear) })
</script>

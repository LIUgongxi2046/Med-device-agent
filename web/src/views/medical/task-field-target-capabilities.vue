<template>
  <main class="medical-page field-target-page" data-task-field-target-capabilities>
    <nav><router-link :to="basePath + '/config'">返回任务与计划</router-link><router-link :to="basePath + '/field-targets'">返回字段目标准备</router-link></nav>
    <header><h1>字段目标输入能力治理</h1><button :disabled="busy" @click="bootstrap">重新读取实际候选与能力</button></header>
    <p class="field-target-notice">登记草稿、独立审批、停用与读取相互分离。仅明确绑定既有制品、生产回执、发布与策略；不会自动批准或授予 task.run 权限，不产生 N03 执行结果。</p>
    <details><summary>当前身份与企业</summary><session-panel /></details>
    <p v-if="loading" role="status">正在核查当前可读候选、固定制品与能力…</p>
    <p v-if="error" class="field-target-error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
    <task-field-target-recovery v-if="pending" :original-key="pending.key" :request-hash="pending.body_sha256" :operation="kindLabels[pending.kind]" :busy="busy" :state="recoveryState" :target-recovery="pending.kind === 'PRODUCE'" @recover="recover" />
    <section v-if="receipt" class="field-target-card" role="status"><h2>真实操作回执已核验</h2><p>能力状态：{{ statusLabels[receipt.status] }} · 修订 {{ receipt.revision }}。{{ receipt.status === 'DRAFT' ? '尚未批准，作者不可审批自己的草稿。' : '该状态不代表任务可运行或检查通过。' }}</p><p class="field-target-identifiers">能力 {{ receipt.capability_id }} · 操作 {{ receipt.operation_id }}</p></section>
    <template v-if="context">
      <form v-if="!pending" class="field-target-card" @submit.prevent="author">
        <h2>1. 登记单一既有制品的能力草稿</h2>
        <p v-if="!context.allowed_actions.includes('AUTHOR')" class="field-target-warning">当前没有独立输入能力登记权限。模板、计划或任务运行权限不会自动授权此动作。</p>
        <p v-if="!context.candidates.length" class="field-target-warning">当前没有满足条件的真实候选。请先核查已登记制品、生产回执及真实发布／角色／策略依据，不会生成替代记录或默认许可。</p>
        <fieldset :disabled="busy || !context.allowed_actions.includes('AUTHOR')">
          <label>实际固定候选<select v-model="candidateKey"><option value="">请选择一个实际候选，不默认选中</option><option v-for="item in context.candidates" :key="candidateIdentity(item)" :value="candidateIdentity(item)">{{ item.artifact_id }} · 发布 {{ item.release_id }} · 角色 {{ item.role_version.version_id }}</option></select></label>
          <task-field-target-input-definition v-if="candidate" :definition="candidate" />
          <label>登记理由<textarea v-model="authorReason" rows="3" maxlength="2000" /></label>
          <p v-if="authorProblem" class="field-target-warning">请填写非空、规范的登记理由。</p>
          <label class="attestation"><input v-model="authorConfirmed" type="checkbox" />我已核查精确既有制品、回执、读取边界和固定发布依据，只登记 DRAFT 草稿。</label>
        </fieldset>
        <button type="submit" :disabled="busy || !context.allowed_actions.includes('AUTHOR') || !candidate || !!authorProblem || !authorConfirmed">登记能力草稿（不批准）</button>
      </form>
      <section class="field-target-card" aria-label="当前可读既有能力">
        <h2>2. 读取与独立决定现有能力</h2>
        <p v-if="!context.capabilities.length">当前没有可读取的能力记录，或该身份没有这些记录的读取权限。</p>
        <label>实际能力记录<select v-model="capabilityId" :disabled="busy || !!pending"><option value="">请选择现有能力</option><option v-for="item in context.capabilities" :key="item.manifest.capability_id" :value="item.manifest.capability_id">{{ statusLabels[item.status] }} · 修订 {{ item.revision }} · {{ item.manifest.capability_id }}</option></select></label>
        <button type="button" :disabled="busy || !!pending || !capabilityId" @click="readSelected">重新读取所选固定声明</button>
        <template v-if="selected">
          <p>当前状态：{{ statusLabels[selected.status] }} · 修订 {{ selected.revision }}</p><p class="field-target-identifiers">作者：{{ selected.manifest.created_by }} · 固定声明摘要：{{ selected.capability_sha256 }}</p>
          <task-field-target-input-definition :definition="selected.manifest" />
          <p v-if="ownDraft" class="field-target-warning">这是当前本人创作的能力，不能批准自己的草稿。需要另一位具有独立审批权限的人明确核查。</p>
          <p v-if="!decisions.length" class="field-target-notice">本次读取没有可执行的独立决定。以服务端当前权限与状态为准。</p>
          <form v-if="!pending && decisions.length" @submit.prevent="decide">
            <fieldset :disabled="busy">
              <label>明确决定<select v-model="decision"><option value="">请选择动作，不默认批准</option><option v-for="item in decisions" :key="item" :value="item">{{ item === 'APPROVE' ? '批准这个固定能力' : '停用这个固定能力' }}</option></select></label>
              <label>决定理由<textarea v-model="decisionReason" rows="3" maxlength="2000" /></label>
              <p v-if="decisionProblem" class="field-target-warning">请填写非空、规范的决定理由。</p>
              <label class="attestation"><input v-model="decisionConfirmed" type="checkbox" />我已独立核查上述完整固定声明，明确执行所选决定。不会自动批准其他记录。</label>
            </fieldset>
            <button type="submit" :disabled="busy || !decision || !!decisionProblem || !decisionConfirmed">提交明确决定</button>
          </form>
        </template>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import SessionPanel from './session-panel.vue'
import { projectTextIssue } from '@/api/medical/project-text'
import TaskFieldTargetInputDefinition from './task-field-target-input-definition.vue'
import TaskFieldTargetRecovery from './task-field-target-recovery.vue'
import { useFieldTargetPage } from '@/api/medical/use-task-field-target-page'
import { canonical, detached, kindLabels, readPendingFieldTarget, readFieldTargetInputContext, readFieldTargetInput, submitFieldTarget, recoverFieldTarget } from '@/api/medical/task-field-targets'
import type { InputContext, InputView, InputAuthor, InputReceipt, InputDecide, PendingFieldTarget } from '@/api/medical/task-field-targets'
import './task-field-target.css'
const context = ref<InputContext | null>(null), pending = ref<PendingFieldTarget | null>(null), receipt = ref<InputReceipt | null>(null), selected = ref<InputView | null>(null)
const candidateKey = ref(''), authorReason = ref(''), authorConfirmed = ref(false), capabilityId = ref(''), decision = ref<'' | 'APPROVE' | 'DISABLE'>(''), decisionReason = ref(''), decisionConfirmed = ref(false), notice = ref(''), recoveryState = ref<string | null>(null)
const statusLabels = { DRAFT: '未批准草稿', APPROVED: '已独立批准', DISABLED: '已停用' } as const
function reset() { context.value = null; pending.value = null; receipt.value = null; selected.value = null; candidateKey.value = ''; authorReason.value = ''; authorConfirmed.value = false; capabilityId.value = ''; decision.value = ''; decisionReason.value = ''; decisionConfirmed.value = false; notice.value = ''; recoveryState.value = null }
const { user, projectId, taskId, basePath, busy, loading, error, reader, run, start } = useFieldTargetPage('MedicalTaskFieldTargetCapabilities', reset, bootstrap)
const candidateIdentity = (value: InputAuthor) => { const { reason: _reason, ...pins } = value; return canonical(pins) }
const candidate = computed(() => context.value?.candidates.find(item => candidateIdentity(item) === candidateKey.value) ?? null)
const authorProblem = computed(() => projectTextIssue(authorReason.value, 2000))
const decisionProblem = computed(() => projectTextIssue(decisionReason.value, 2000))
const ownDraft = computed(() => !!selected.value && selected.value.manifest.created_by === user.medicalSession?.user.user_id)
const decisions = computed(() => (selected.value?.allowed_actions ?? []).filter((action): action is 'APPROVE' | 'DISABLE' => action === 'DISABLE' || action === 'APPROVE' && !ownDraft.value))
async function bootstrap() {
  await run(async (signal, current) => {
    pending.value = readPendingFieldTarget(reader(), projectId.value, taskId.value)
    const value = await readFieldTargetInputContext(reader, projectId.value, taskId.value, signal)
    if (current()) { context.value = value; selected.value = null; capabilityId.value = ''; candidateKey.value = '' }
  })
}
async function author() {
  if (!candidate.value || pending.value || !context.value?.allowed_actions.includes('AUTHOR') || !authorConfirmed.value || !!authorProblem.value) return
  const body = { ...detached(candidate.value), reason: authorReason.value }
  await run(async (signal, current) => {
    const result = await submitFieldTarget(reader, projectId.value, taskId.value, 'INPUT_AUTHOR', body, null, signal, value => { if (current()) pending.value = value }) as InputReceipt
    if (!current()) return
    receipt.value = result; pending.value = null; authorConfirmed.value = false; notice.value = '草稿与原请求及真实回执一致，未执行审批。'
    const value = await readFieldTargetInputContext(reader, projectId.value, taskId.value, signal); if (current()) context.value = value
  }, true)
}
async function readSelected() {
  const id = capabilityId.value; if (!id || pending.value) return
  selected.value = null
  await run(async (signal, current) => { const value = await readFieldTargetInput(reader, projectId.value, taskId.value, id, signal); if (current() && capabilityId.value === id) selected.value = value })
}
async function decide() {
  const target = selected.value, action = decision.value
  if (!target || !action || pending.value || !decisions.value.includes(action) || !decisionConfirmed.value || !!decisionProblem.value) return
  const body: InputDecide = { schema_version: '1.0.0', project_id: projectId.value, expected_revision: target.revision, capability_sha256: target.capability_sha256, decision: action, reason: decisionReason.value }
  await run(async (signal, current) => {
    const result = await submitFieldTarget(reader, projectId.value, taskId.value, 'INPUT_DECIDE', body, target.manifest.capability_id, signal, value => { if (current()) pending.value = value }) as InputReceipt
    if (!current()) return
    receipt.value = result; pending.value = null; decisionConfirmed.value = false; decision.value = ''; notice.value = '这个固定能力的明确决定已核验，没有启动任何消费者。'
    const value = await readFieldTargetInput(reader, projectId.value, taskId.value, target.manifest.capability_id, signal); if (current()) selected.value = value
  }, true)
}
async function recover() {
  const original = pending.value; if (!original) return
  await run(async (signal, current) => {
    const result = await recoverFieldTarget(reader, original, signal); if (!current()) return
    if (!result.complete) { recoveryState.value = result.receipt && 'state' in result.receipt ? result.receipt.state : 'NOT_FOUND'; return }
    pending.value = null; notice.value = '已核验原请求真实回执，没有重发、创建替代记录或自动审批。'
    if (original.kind === 'INPUT_AUTHOR' || original.kind === 'INPUT_DECIDE') receipt.value = result.receipt as InputReceipt
    const value = await readFieldTargetInputContext(reader, projectId.value, taskId.value, signal); if (current()) context.value = value
  })
}
watch([candidateKey, authorReason], () => { authorConfirmed.value = false }, { flush: 'sync' })
watch([decision, decisionReason], () => { decisionConfirmed.value = false }, { flush: 'sync' })
watch(capabilityId, () => { selected.value = null; decision.value = ''; decisionReason.value = ''; decisionConfirmed.value = false }, { flush: 'sync' })
start()
</script>

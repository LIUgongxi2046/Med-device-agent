<template>
  <main class="medical-page field-target-page" data-task-field-target-preparation>
    <nav><router-link :to="basePath + '/config'">返回任务与计划</router-link><router-link :to="basePath + '/field-target-capabilities'">独立输入能力治理</router-link></nav>
    <header><h1>严格字段目标准备</h1><button :disabled="busy" @click="refresh">重新读取当前依据</button></header>
    <p class="field-target-notice">这里维护 PROPOSED 提议值与来源映射，不确认医疗事实，不批准比较规则，也不会启动 TASK 或 N03。当前生产器仅使用五类 SourceExact 的原始 TEXT，不使用别名或数值转换。</p>
    <details><summary>当前身份与企业</summary><session-panel /></details>
    <p v-if="loading" role="status">正在核查真实任务、完整模板清单与原请求…</p>
    <p v-if="error" class="field-target-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <task-field-target-recovery v-if="pending" :original-key="pending.key" :request-hash="pending.body_sha256" :operation="kindLabels[pending.kind]" :busy="busy" :state="recoveryState" :target-recovery="pending.kind === 'PRODUCE'" @recover="recover" />
    <section v-if="registration" class="field-target-card" aria-label="真实目标登记回执" data-target-registration>
      <h2>提议字段目标已登记</h2><p>以下身份来自真实服务端登记回执，不是计划中的目标 ID。登记不等于严格字段检查通过或任务可运行。</p>
      <dl class="field-target-identifiers"><dt>登记时任务／计划</dt><dd>{{ registration.task_revision }} / {{ registration.plan_revision }}</dd><dt>实际制品</dt><dd>{{ registration.artifact_id }}</dd><dt>登记回执</dt><dd>{{ registration.registration_id }}</dd><dt>对象版本</dt><dd>{{ registration.object_ref.object_version }}</dd><dt>实际内容摘要</dt><dd>{{ registration.object_ref.sha256 }}</dd><dt>来源读取回执</dt><dd>{{ registration.source_read_receipt_ids.length }} 项</dd></dl>
    </section>
    <template v-if="preparation">
      <p v-if="staleMapping" class="field-target-warning" role="status">{{ staleMapping }} 原回执仍是历史真实记录，不代表当前可生产或可运行。</p>
      <ul v-if="preparation.prerequisites.length" class="field-target-warning" aria-label="实际待补条件"><li v-for="item in preparation.prerequisites" :key="item">{{ prerequisiteLabels[item] }}</li></ul>
      <section class="field-target-card" aria-label="完整模板字段清单">
        <h2>完整固定模板清单</h2><p>{{ preparation.template_nodes.length }} 个节点 · {{ preparation.inventory.length }} 个字段定义 · {{ preparation.repeat_definitions.length }} 个重复组 · {{ preparation.attachment_requirements.length }} 项附件要求</p>
        <p v-if="!preparation.inventory.length" class="field-target-notice" data-empty-field-inventory>该固定模板的字段清单明确为空。仍保留全部目录、重复组和附件要求，不制造字段，也不据此宣称任务完成。</p>
        <ul class="field-target-inventory"><li v-for="item in preparation.inventory" :key="item.field_definition_id">
          <strong>{{ item.name }}</strong> · {{ item.value_type }} · {{ item.required ? '必填' : '可选，仍在清单内' }} · {{ inventoryLabels[item.state] }}
          <p>{{ item.field_definition.description }}<span v-if="item.repeat_id"> · 重复字段，{{ item.row_id_required ? '必须明确行身份' : '依实际重复行维护' }}</span></p>
          <p>条件：{{ item.condition.mode === 'ALWAYS' ? '固定无条件' : '固定规则条件，未知时不得省略' }}；{{ item.entry_keys.length }} 项提议发生位置。</p>
          <details class="field-target-identifiers"><summary>固定定义与实际归属</summary><dl><dt>字段定义</dt><dd>{{ item.field_definition_id }}</dd><dt>章节</dt><dd>{{ item.section_id ?? '尚未分配章节，请补齐实际计划归属；不是字段格式错误' }}</dd><dt>目录节点</dt><dd>{{ item.node_id }}</dd><dt>实际问题</dt><dd>{{ item.question_ids.join('、') || '没有关联研究问题' }}</dd><dt>重复组</dt><dd>{{ item.repeat_id ?? '非重复组字段' }}</dd><dt>条件规则</dt><dd>{{ item.condition.rule_revision?.version_id ?? '无规则引用' }}</dd><dt>条件输入</dt><dd>{{ item.condition.input_keys.join('、') || '无输入项' }}</dd></dl><details><summary>完整字段约束、证据要求与输出映射</summary><execution-policy-value :value="item.field_definition" /></details></details>
        </li></ul>
        <details><summary>全部目录节点（包括零字段节点）</summary><ul><li v-for="node in preparation.template_nodes" :key="node.node_id">{{ node.official_number }} {{ node.title }} · {{ node.required ? '必需' : '可选' }} · {{ preparation.inventory.filter(item => item.node_id === node.node_id).length }} 个字段<span class="field-target-identifiers"> · {{ node.node_id }}</span></li></ul></details>
        <details><summary>全部重复组与附件要求</summary><ul><li v-for="repeat in preparation.repeat_definitions" :key="repeat.repeat_id">{{ repeat.name }} · 数量 {{ repeat.min_count }} 至 {{ repeat.max_count ?? '未固定上限' }} · {{ repeat.field_ids.length }} 个字段</li><li v-for="attachment in preparation.attachment_requirements" :key="attachment.attachment_requirement_id">{{ attachment.name }} · {{ attachment.required ? '必需' : '可选' }} · {{ attachment.condition.mode === 'ALWAYS' ? '无条件' : '按固定条件核查' }}</li></ul></details>
        <details><summary>完整实际适用条件</summary><ul><li v-for="item in preparation.condition_results" :key="item.scope + ':' + item.target_id">{{ item.scope }} · {{ item.target_id }} · {{ item.status === 'APPLICABLE' ? '适用' : item.status === 'NOT_APPLICABLE' ? '不适用（不等于核验通过）' : '未知条件' }}<span v-if="item.unknown_input_keys.length"> · 待明确输入：{{ item.unknown_input_keys.join('、') }}</span></li></ul></details>
        <details class="field-target-identifiers"><summary>固定任务、计划与模板版本</summary><dl><dt>任务版本</dt><dd>{{ preparation.task_revision }}</dd><dt>任务输入摘要</dt><dd>{{ preparation.task_input_sha256 }}</dd><dt>确认计划</dt><dd>{{ preparation.plan_revision }} / {{ preparation.plan_sha256 }}</dd><dt>模板</dt><dd>{{ preparation.template_version }} / {{ preparation.template_sha256 }}</dd><dt>映射头修订</dt><dd>{{ preparation.head_revision }}</dd></dl></details>
      </section>
      <p v-if="!preparation.source_choices.length" class="field-target-warning">当前没有可读取的 SourceExact 原值。请核查实际来源修订、授权或发布情况；不会用别名、数字转换或候选侧复制值替代。</p>
      <form v-if="!pending" class="field-target-card" @submit.prevent="saveMapping">
        <h2>维护提议映射</h2><p v-if="!canMap" class="field-target-warning">当前没有独立字段映射写入权限。计划维护权限与 task.run 不会自动授权此操作。</p>
        <task-field-target-mapping-form v-model="mappings" :preparation="preparation" :disabled="busy || !canMap" />
        <label>保存理由<textarea v-model="mappingReason" rows="3" maxlength="2000" :disabled="busy || !canMap" /></label>
        <p v-if="mappingProblem" class="field-target-warning">{{ mappingProblem }}</p>
        <label class="attestation"><input v-model="mappingConfirmed" type="checkbox" :disabled="busy || !canMap" />我已核查完整清单与明确映射，仅保存 PROPOSED 提议，不确认事实。</label>
        <button type="submit" :disabled="busy || !canMap || !mappingConfirmed || !!mappingProblem">保存不可变提议映射版本</button>
      </form>
      <form v-if="!pending && preparation.current_mapping && !registration" class="field-target-card" @submit.prevent="produce">
        <h2>登记实际提议字段目标</h2><p>服务端重新读取原来源字节并核查当前权利，持久化物理对象意图、精确回读后才登记制品。只消费已保存映射，不消费上方未保存编辑。</p>
        <p v-if="!canProduce" class="field-target-warning">当前没有目标生产权限或真实生产条件未满足。请核查当前授权、配置、来源及清单缺项。</p>
        <p v-if="preparation.production.reason" class="field-target-warning">{{ preparation.production.reason }}</p>
        <label>目标登记理由<textarea v-model="produceReason" rows="3" maxlength="2000" :disabled="busy || !canProduce" /></label>
        <p v-if="produceProblem" class="field-target-warning">请填写非空、规范的目标登记理由。</p>
        <label class="attestation"><input v-model="produceConfirmed" type="checkbox" :disabled="busy || !canProduce" />仅登记当前已保存映射对应的真实 PROPOSED 目标，不启动运行。</label>
        <button type="submit" :disabled="busy || !canProduce || !produceConfirmed || !!produceProblem">登记提议字段目标</button>
      </form>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import SessionPanel from './session-panel.vue'
import ExecutionPolicyValue from './execution-policy-value.vue'
import { projectTextIssue } from '@/api/medical/project-text'
import TaskFieldTargetMappingForm from './task-field-target-mapping-form.vue'
import TaskFieldTargetRecovery from './task-field-target-recovery.vue'
import { useFieldTargetPage } from '@/api/medical/use-task-field-target-page'
import { readFieldTargetPreparation, readPendingFieldTarget, submitFieldTarget, recoverFieldTarget, detached, mappingBody, mappingIssue, mappingBasisIssue, produceBody, inventoryLabels, kindLabels } from '@/api/medical/task-field-targets'
import type { Preparation, PendingFieldTarget, MappingEntry, ProduceReceipt, Status } from '@/api/medical/task-field-targets'
import './task-field-target.css'
const preparation = ref<Preparation | null>(null), pending = ref<PendingFieldTarget | null>(null), registration = ref<ProduceReceipt | null>(null)
const mappings = ref<MappingEntry[]>([]), mappingReason = ref(''), produceReason = ref(''), mappingConfirmed = ref(false), produceConfirmed = ref(false), notice = ref(''), recoveryState = ref<string | null>(null)
function reset() { preparation.value = null; pending.value = null; registration.value = null; mappings.value = []; mappingReason.value = ''; produceReason.value = ''; mappingConfirmed.value = false; produceConfirmed.value = false; notice.value = ''; recoveryState.value = null }
const { projectId, taskId, basePath, busy, loading, error, reader, run, start } = useFieldTargetPage('MedicalTaskFieldTargets', reset, bootstrap)
const prerequisiteLabels = { CONFIGURATION_REQUIRED: '当前部署缺少实际生产配置，请由运维核查。', MAPPING_WRITE_PERMISSION_REQUIRED: '当前身份缺少独立字段映射写入权限。', PRODUCER_PERMISSION_REQUIRED: '当前身份缺少独立字段目标生产权限。', MAPPING_REQUIRED: '尚未保存实际提议映射。', UNRESOLVED_INVENTORY: '完整字段清单仍有未解决项，请逐项核查来源、条件、上下文或不支持的类型。' } as const
const canMap = computed(() => preparation.value?.allowed_actions.includes('WRITE_MAPPING') ?? false)
const produceProblem = computed(() => projectTextIssue(produceReason.value, 2000))
const staleMapping = computed(() => preparation.value?.current_mapping ? mappingBasisIssue(preparation.value) : '')
const canProduce = computed(() => !!preparation.value?.allowed_actions.includes('PRODUCE') && !staleMapping.value)
const mappingProblem = computed(() => preparation.value ? mappingIssue(mappingBody(preparation.value, mappings.value, mappingReason.value), preparation.value) : '请先读取准备依据')
function apply(value: Preparation) { preparation.value = value; mappings.value = detached(value.current_mapping?.manifest.mappings ?? []); registration.value = value.production.receipt; mappingConfirmed.value = false; produceConfirmed.value = false }
async function bootstrap() {
  await run(async (signal, current) => {
    pending.value = readPendingFieldTarget(reader(), projectId.value, taskId.value)
    const result = await readFieldTargetPreparation(reader, projectId.value, taskId.value, signal)
    if (current()) apply(result)
  })
}
async function refresh() { if (!busy.value) await bootstrap() }
async function saveMapping() {
  if (!preparation.value || pending.value || !canMap.value || !mappingConfirmed.value || mappingProblem.value) return
  const body = mappingBody(preparation.value, mappings.value, mappingReason.value)
  await run(async (signal, current) => {
    await submitFieldTarget(reader, projectId.value, taskId.value, 'MAPPING', body, null, signal, original => { if (current()) pending.value = original })
    if (!current()) return
    pending.value = null; notice.value = '真实映射回执与固定内容已核验，状态为 PROPOSED。'; mappingReason.value = ''
    const fresh = await readFieldTargetPreparation(reader, projectId.value, taskId.value, signal); if (current()) apply(fresh)
  }, true)
}
async function produce() {
  if (!preparation.value || pending.value || !canProduce.value || !produceConfirmed.value || !!produceProblem.value) return
  const body = produceBody(preparation.value, produceReason.value)
  await run(async (signal, current) => {
    const result = await submitFieldTarget(reader, projectId.value, taskId.value, 'PRODUCE', body, null, signal, original => { if (current()) pending.value = original }) as Status
    if (!current()) return
    recoveryState.value = result.state; registration.value = result.receipt
    if (result.receipt) { pending.value = null; notice.value = '实际提议目标已登记；没有启动 TASK 或 N03。' }
  }, true)
}
async function recover() {
  const original = pending.value; if (!original) return
  await run(async (signal, current) => {
    const result = await recoverFieldTarget(reader, original, signal); if (!current()) return
    recoveryState.value = result.receipt && 'state' in result.receipt ? result.receipt.state : result.complete ? null : 'NOT_FOUND'
    if (!result.complete) return
    pending.value = null; notice.value = '原请求真实回执已核验，没有重发或替代写入。'
    if (original.kind === 'PRODUCE') registration.value = (result.receipt as Status).receipt
    const fresh = await readFieldTargetPreparation(reader, projectId.value, taskId.value, signal); if (current()) apply(fresh)
  })
}
watch([mappings, mappingReason], () => { mappingConfirmed.value = false }, { deep: true, flush: 'sync' })
watch(produceReason, () => { produceConfirmed.value = false }, { flush: 'sync' })
start()
</script>

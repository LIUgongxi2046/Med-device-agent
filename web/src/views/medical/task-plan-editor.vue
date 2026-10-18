<template>
  <section aria-label="任务计划维护" data-task-plan-editor>
    <h2>章节计划</h2>
    <el-button :disabled="!active || loading || writing || user.medicalWriting" @click="load">重新读取计划</el-button>
    <p v-if="loading" role="status">正在读取固定计划和当前编辑依据</p>
    <section v-if="pending" aria-label="原计划保存请求">
      <p>原计划保存结果需要核查。请求标识：{{ pending.key }}</p>
      <el-button :disabled="busy || !canWrite" @click="recover">读取原保存结果</el-button>
      <el-button v-if="originalBody" :disabled="busy || !canWrite" @click="retry">继续原保存请求</el-button>
      <el-button v-if="notRegistered" :disabled="busy || !canWrite" @click="release">重新填写计划</el-button>
      <p v-if="notRegistered" role="status">本次读取没有发现已登记结果，重新填写前将再次核查原保存与任务修订。</p>
    </section>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="loaded && !stored">当前尚未保存章节计划</p>
    <el-descriptions v-if="stored" :column="1" border>
      <el-descriptions-item label="维护计划版本">{{ stored.plan_revision }}</el-descriptions-item>
      <el-descriptions-item label="计划状态">{{ planLabels[stored.status] }}</el-descriptions-item>
      <el-descriptions-item label="固定任务版本">{{ stored.task_revision }}</el-descriptions-item>
      <el-descriptions-item label="内容摘要">{{ stored.content_sha256 }}</el-descriptions-item>
    </el-descriptions>
    <p v-if="stored && stored.task_revision !== current.detail.task_revision" role="alert">维护计划属于历史任务版本，需要基于当前任务建立新的计划。</p>
    <task-plan-return :session="session" :current="current" :active="active && !loading && !writing && !pending" @saved="emit('saved')" />
    <el-button v-if="canWrite && mutable && !basis && !pending" :disabled="busy" @click="prepareEditing">读取当前编辑依据</el-button>
    <el-button v-if="basis && !pending" :disabled="busy || !canWrite" @click="createDraft">根据当前完整目录建立计划草稿</el-button>
    <p v-if="!canWrite">当前许可允许读取计划，保存需要计划维护权限。</p>
    <template v-if="content">
      <p>模板版本：{{ content.template_version }}；项目属性版本：{{ content.profile_revision }}</p>
      <p>全部目录节点：{{ content.requirement_node_ids.length }}；章节：{{ content.sections.length }}；研究问题：{{ content.questions.length }}</p>
      <el-form :disabled="!editable" label-position="top" @submit.prevent="submit">
        <el-form-item label="待确认条件">
          <el-select v-model="content.pending_condition_ids" multiple filterable aria-label="待确认条件">
            <el-option v-for="item in conditionOptions" :key="item.id" :value="item.id" :label="item.label" />
          </el-select>
        </el-form-item>
        <el-table v-if="basis" :data="basis.conditions.filter(item => item.status === 'UNKNOWN')" :row-key="(item: TemplateConditionResult) => item.scope + ':' + item.target_id" empty-text="当前没有待确认条件">
          <el-table-column label="条件对象"><template #default="scope">{{ conditionLabel(scope.row.target_id) }}</template></el-table-column>
          <el-table-column label="待确认属性"><template #default="scope">{{ scope.row.unknown_input_keys.join('、') }}</template></el-table-column>
        </el-table>
        <h3>完整章节与依赖</h3>
        <el-button @click="addSection">增加章节</el-button>
        <el-card v-for="(section, index) in content.sections" :key="section.section_id" shadow="never">
          <p>章节顺序 {{ index + 1 }}；章节标识：{{ section.section_id }}</p>
          <el-form-item label="章节目标" :error="inputIssue('/content/sections/' + index + '/goal')"><el-input v-model="section.goal" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" /></el-form-item>
          <el-form-item label="对应目录节点" :error="inputIssue('/content/sections/' + index + '/requirement_node_ids')">
            <el-select v-model="section.requirement_node_ids" multiple filterable :aria-label="'章节目录 ' + (index + 1)">
              <el-option v-for="id in content.requirement_node_ids" :key="id" :value="id" :label="nodeLabel(id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="责任人员标识（可以暂未分配）">
            <el-input :model-value="section.owner_id ?? ''" @update:model-value="section.owner_id = $event.trim() || null" />
          </el-form-item>
          <el-form-item label="处理方法">
            <el-select v-model="section.method"><el-option v-for="(label, value) in methodLabels" :key="value" :value="value" :label="label" /></el-select>
          </el-form-item>
          <el-form-item label="依赖章节" :error="inputIssue('/content/sections/' + index + '/depends_on_section_ids')">
            <el-select v-model="section.depends_on_section_ids" multiple filterable>
              <el-option v-for="item in content.sections.filter(item => item.section_id !== section.section_id)" :key="item.section_id" :value="item.section_id" :label="item.goal || item.section_id" />
            </el-select>
          </el-form-item>
          <el-form-item label="完成检查规则">
            <el-select v-model="section.completion_rule_ids" multiple filterable>
              <el-option v-for="id in ruleOptions" :key="id" :value="id" :label="id" />
            </el-select>
          </el-form-item>
          <el-button :disabled="index === 0" @click="move(content.sections, index, -1)">向上移动</el-button>
          <el-button :disabled="index === content.sections.length - 1" @click="move(content.sections, index, 1)">向下移动</el-button>
          <el-button @click="content.sections.splice(index, 1)">移除章节</el-button>
        </el-card>
        <h3>研究问题与依赖</h3>
        <el-button @click="addQuestion">增加研究问题</el-button>
        <el-card v-for="(question, index) in content.questions" :key="index" shadow="never">
          <el-form-item label="问题身份" :error="inputIssue('/content/questions/' + index + '/question_key')"><el-input v-model="question.question_key" /></el-form-item>
          <el-form-item label="研究问题" :error="inputIssue('/content/questions/' + index + '/question')"><el-input v-model="question.question" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" /></el-form-item>
          <el-form-item label="所属章节" :error="inputIssue('/content/questions/' + index + '/section_id')">
            <el-select v-model="question.section_id"><el-option v-for="section in content.sections" :key="section.section_id" :value="section.section_id" :label="section.goal || section.section_id" /></el-select>
          </el-form-item>
          <el-form-item label="对应目录要求" :error="inputIssue('/content/questions/' + index + '/requirement_ids')">
            <el-select v-model="question.requirement_ids" multiple filterable>
              <el-option v-for="id in questionNodes(question.section_id)" :key="id" :value="id" :label="nodeLabel(id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="完成检查规则">
            <el-select v-model="question.completion_rule_ids" multiple filterable><el-option v-for="id in ruleOptions" :key="id" :value="id" :label="id" /></el-select>
          </el-form-item>
          <el-form-item label="依赖研究问题" :error="inputIssue('/content/questions/' + index + '/depends_on_question_keys')">
            <el-select v-model="question.depends_on_question_keys" multiple filterable>
              <el-option v-for="item in content.questions.filter(item => item !== question && item.question_key.length > 0)" :key="item.question_key" :value="item.question_key" :label="item.question || item.question_key" />
            </el-select>
          </el-form-item>
          <el-button :disabled="index === 0" @click="move(content.questions, index, -1)">向上移动</el-button>
          <el-button :disabled="index === content.questions.length - 1" @click="move(content.questions, index, 1)">向下移动</el-button>
          <el-button @click="content.questions.splice(index, 1)">移除研究问题</el-button>
        </el-card>
        <el-form-item label="计划修订理由" :error="inputIssue('/reason')"><el-input v-model="reason" type="textarea" maxlength="2000" show-word-limit /></el-form-item>
        <p v-if="issues.length" role="alert">计划输入需要修改：{{ issues.map(item => (item.path || '完整计划') + '：' + item.message).join('；') }}</p>
        <p v-if="coverageIssue" role="alert">{{ coverageIssue }}</p>
        <el-button native-type="submit" type="primary" :disabled="!editable || issues.length > 0 || coverageIssue.length > 0">保存计划草稿</el-button>
      </el-form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import TaskPlanReturn from './task-plan-return.vue'
import { fail } from '@/api/medical/protocol'
import { planInputIssues } from '@/api/medical/plan-input-issues'
import type { MedicalSession } from '@/api/medical/protocol'
import { taskActionAllowed } from '@/api/medical/task-detail'
import type { CurrentTask } from '@/api/medical/task-detail'
import { clearVerifiedPlan, planContentReference, preparePlan, readPendingPlan, readPlanEditBasis, readTaskPlan,
  recoverSavedPlan, releaseUnregisteredPlan, savePlan } from '@/api/medical/task-plans'
import type { CurrentPlan, PendingPlan, PlanCandidate, PlanEditBasis, PlanSection, PlanVersionInput, TaskPlanView } from '@/api/medical/task-plans'
import type { TemplateConditionResult } from '@/api/medical/template-applicability'

const props = defineProps<{ session: MedicalSession; current: CurrentTask; active: boolean }>()
const emit = defineEmits<{ saved: [] }>(); const user = useUserStore()
const methodLabels: Record<PlanSection['method'], string> = { DETERMINISTIC: '规则与确定性处理', MODEL_ASSISTED: '模型辅助处理', HUMAN_PROVIDED: '人工提供内容' }
const planLabels = { DRAFT: '草稿', CONFIRMED: '已确认', SUPERSEDED: '已有后续版本' }
const loaded = ref(false); const loading = ref(false); const writing = ref(false); const notice = ref('')
const stored = ref<TaskPlanView | null>(null); const basis = ref<PlanEditBasis | null>(null); const content = ref<PlanCandidate | null>(null)
const pending = ref<PendingPlan | null>(null); const originalBody = ref<PlanVersionInput | null>(null); const notRegistered = ref(false); const reason = ref('')
let generation = 0; let controller: AbortController | null = null
const canWrite = computed(() => props.active && taskActionAllowed(props.session, props.current.detail.project_id, props.current.detail.task_id, 'plan.write'))
const mutable = computed(() => ['NEEDS_MATERIALS', 'PLAN_PENDING', 'READY', 'FAILED', 'CANCELLED'].includes(props.current.detail.status))
const busy = computed(() => loading.value || writing.value || user.medicalWriting)
const editable = computed(() => props.active && canWrite.value && mutable.value && basis.value !== null && content.value !== null
  && content.value.template_version === basis.value.template_version && content.value.profile_revision === basis.value.profile_revision && !pending.value && !busy.value)
const body = computed<PlanVersionInput | null>(() => content.value === null ? null : ({ schema_version: '1.0.0', expected_revision: props.current.revision,
  base_version: stored.value?.current_plan_revision ?? null, schema_ref: planContentReference, content: content.value, reason: reason.value }))
const issues = computed(() => body.value === null ? [] : planInputIssues(body.value))
function inputIssue(path: string): string { return issues.value.find(issue => issue.path === path)?.message ?? '' }
const coverageIssue = computed(() => {
  if (!content.value) return ''
  const ids = content.value.sections.flatMap(section => section.requirement_node_ids)
  return ids.length !== content.value.requirement_node_ids.length || new Set(ids).size !== ids.length
    || ids.some(id => !content.value?.requirement_node_ids.includes(id)) ? '全部目录节点需要由章节完整且唯一覆盖' : ''
})
const ruleOptions = computed(() => [...new Set([...(basis.value?.completion_rule_ids ?? []),
  ...(content.value?.sections.flatMap(item => item.completion_rule_ids) ?? []), ...(content.value?.questions.flatMap(item => item.completion_rule_ids) ?? [])])])
const conditionOptions = computed(() => {
  const values = new Set([...(content.value?.pending_condition_ids ?? []), ...(basis.value?.conditions.map(item => item.target_id) ?? [])])
  return [...values].map(id => ({ id, label: conditionLabel(id) }))
})
function nodeLabel(id: string): string {
  const node = basis.value?.template_content.nodes.find(item => item.node_id === id)
  return node ? [node.official_number, node.title].filter(Boolean).join(' ') : id
}
function conditionLabel(id: string): string {
  if (basis.value?.project_id === id) return '项目属性'
  if (basis.value?.template_version === id) return '模板适用条件'
  const field = basis.value?.template_content.field_definitions.find(item => item.field_id === id)
  const attachment = basis.value?.template_content.attachment_requirements.find(item => item.attachment_requirement_id === id)
  return field?.name ?? attachment?.name ?? nodeLabel(id)
}
function questionNodes(sectionId: string): string[] { return content.value?.sections.find(item => item.section_id === sectionId)?.requirement_node_ids ?? [] }
function move<T>(items: T[], index: number, direction: number): void {
  const target = index + direction; const item = items[index]
  if (target < 0 || target >= items.length || item === undefined) throw new Error('计划排列位置无效')
  items.splice(index, 1); items.splice(target, 0, item)
}
function clear(): void {
  generation += 1; controller?.abort(); controller = null; loaded.value = false; loading.value = false
  stored.value = null; basis.value = null; content.value = null; reason.value = ''; notice.value = ''; notRegistered.value = false
  pending.value = null; originalBody.value = null
}
async function load(): Promise<void> {
  if (!props.active || busy.value || document.hidden) return
  const session = props.session; const current = props.current; clear()
  const reading = new AbortController(); controller = reading; const requested = generation; loading.value = true
  try {
    const result = await readTaskPlan(session, current.detail.project_id, current.detail.task_id, null, reading.signal)
    if (result.revision !== current.revision) fail('REVISION_CONFLICT', '任务已经变化，需要重新读取任务和计划')
    if (generation !== requested || props.session !== session || props.current !== current || !props.active || document.hidden) return
    stored.value = result.plan; loaded.value = true
    if (result.plan) content.value = JSON.parse(JSON.stringify(result.plan.content)) as PlanCandidate
    pending.value = canWrite.value ? readPendingPlan(session, current.detail.project_id, current.detail.task_id) : null
  } finally { if (generation === requested) { loading.value = false; controller = null } }
}
async function prepareEditing(): Promise<void> {
  if (!canWrite.value || !mutable.value || busy.value || pending.value || document.hidden) return
  const session = props.session; const current = props.current; const requested = generation
  const reading = new AbortController(); controller = reading; loading.value = true
  try {
    const result = await readPlanEditBasis(session, current, reading.signal)
    if (generation === requested && props.session === session && props.current === current && props.active && !document.hidden) basis.value = result
  } finally { if (generation === requested) { loading.value = false; controller = null } }
}
function createDraft(): void {
  if (!basis.value || busy.value || pending.value || !canWrite.value || !mutable.value) return
  const actual = basis.value
  content.value = { template_version: actual.template_version, profile_revision: actual.profile_revision,
    requirement_node_ids: actual.template_content.nodes.map(node => node.node_id),
    sections: actual.template_content.nodes.map(node => ({ section_id: crypto.randomUUID(), requirement_node_ids: [node.node_id],
      goal: node.title, owner_id: props.current.detail.owner_id, method: 'HUMAN_PROVIDED', depends_on_section_ids: [], completion_rule_ids: [] })),
    questions: [], pending_condition_ids: [...new Set(actual.conditions.filter(item => item.status === 'UNKNOWN').map(item => item.target_id))] }
  reason.value = ''; notice.value = '草稿采用当前完整目录，目标、责任、方法和完成规则需要逐项核查。'
}
function addSection(): void {
  if (!editable.value || !content.value) return
  content.value.sections.push({ section_id: crypto.randomUUID(), requirement_node_ids: [], goal: '', owner_id: null,
    method: 'HUMAN_PROVIDED', depends_on_section_ids: [], completion_rule_ids: [] })
}
function addQuestion(): void {
  if (!editable.value || !content.value) return
  content.value.questions.push({ question_key: crypto.randomUUID(), section_id: '', question: '', requirement_ids: [], completion_rule_ids: [], depends_on_question_keys: [] })
}
async function finish(session: MedicalSession, actual: PendingPlan, result: CurrentPlan): Promise<void> {
  await clearVerifiedPlan(session, actual, result, new AbortController().signal)
}
async function submit(): Promise<void> {
  if (!editable.value || !body.value || issues.value.length || coverageIssue.value) return
  const session = props.session; const current = props.current; const requestBody = JSON.parse(JSON.stringify(body.value)) as PlanVersionInput
  writing.value = true; user.medicalWriting = true
  try {
    const actual = await preparePlan(session, current, requestBody); pending.value = actual; originalBody.value = requestBody
    const result = await savePlan(session, actual, requestBody); await finish(session, actual, result)
  } finally { writing.value = false; user.medicalWriting = false }
  if (props.session === session && props.active && !document.hidden) emit('saved')
}
async function retry(): Promise<void> {
  if (busy.value || !canWrite.value || !pending.value || !originalBody.value) return
  const session = props.session; const actual = pending.value; const input = originalBody.value
  writing.value = true; user.medicalWriting = true
  try { await finish(session, actual, await savePlan(session, actual, input)) }
  finally { writing.value = false; user.medicalWriting = false }
  if (props.session === session && props.active && !document.hidden) emit('saved')
}
async function recover(): Promise<void> {
  if (busy.value || !canWrite.value || !pending.value) return
  const session = props.session; const actual = pending.value
  let completed = false
  writing.value = true; user.medicalWriting = true
  try {
    const result = await recoverSavedPlan(session, actual, new AbortController().signal)
    if (result.plan) { await finish(session, actual, result); completed = true }
    else if (props.session === session && props.active && !document.hidden) notRegistered.value = true
  } finally { writing.value = false; user.medicalWriting = false }
  if (completed && props.session === session && props.active && !document.hidden) emit('saved')
}
async function release(): Promise<void> {
  if (busy.value || !canWrite.value || !pending.value || !notRegistered.value) return
  const session = props.session; const actual = pending.value
  writing.value = true; user.medicalWriting = true
  try {
    await releaseUnregisteredPlan(session, actual, new AbortController().signal)
    if (props.session === session && props.active && !document.hidden) { pending.value = null; originalBody.value = null; notRegistered.value = false }
  } finally { writing.value = false; user.medicalWriting = false }
}
watch(() => [props.session, props.current, props.active], () => { clear(); if (props.active && !document.hidden) void load() }, { immediate: true, flush: 'sync' })
function visibility(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', visibility); window.addEventListener('pagehide', clear); window.addEventListener('offline', clear)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('pagehide', clear); window.removeEventListener('offline', clear) })
</script>

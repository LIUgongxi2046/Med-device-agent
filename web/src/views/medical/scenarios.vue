<template>
  <main class="medical-page">
    <h1>场景目录</h1>
    <session-panel />
    <p v-if="!session?.current_context" role="alert">查看场景目录需要选择当前企业。</p>
    <scenario-planning-workspace :key="pickerGeneration" :disabled="filterDisabled" />
    <native-image-simulation-workspace v-if="showImageSimulation" />
    <h2>已授权配置目录</h2>
    <el-form :inline="true" @submit.prevent="search">
      <el-form-item label="项目范围" :error="projectError"><el-input v-if="filterDisabled" :model-value="projectId || '当前获授权范围'" disabled /><project-scope-select v-else :key="pickerGeneration" v-model="projectId" /></el-form-item>
      <el-form-item label="目标文档"><el-select v-model="documentType" clearable :disabled="filterDisabled">
        <el-option v-for="(label, value) in taskDocumentLabels" :key="value" :value="value" :label="label" />
      </el-select></el-form-item>
      <el-button native-type="submit" :disabled="disabled">查询场景目录</el-button>
    </el-form>
    <p>项目来自当前获授权项目目录，可显式加载更多；留空查询当前获授权范围。切换项目或文档条件后，请重新查询。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="!page && !busy" role="status">尚无当前条件的已确认目录结果，版本总数未知。</p>
    <p v-if="busy" role="status">正在读取当前获授权场景版本。</p>
    <template v-if="page">
      <h2>当前配置记录</h2>
      <p>当前查询范围共有{{ page.total }}个获授权场景版本，本页显示 {{ page.items.length }} 条。</p>
      <p>固定查询：{{ page.project_id ?? '当前获授权范围' }} · {{ page.document_type ? documentLabel(page.document_type) : '全部目标文档' }}；数据水位：{{ page.dataset_watermark }}</p>
      <el-alert title="配置记录不等于可执行场景。当前场景规格仍未核验，服务端未提供正式任务入口。" type="info" :closable="false" />
      <p>真实就绪条件：完整角色、输入、适用知识、任务规则、输出、人工审核、失败状态和验收样本经核验，完成发布准入，并由服务端提供获授权任务入口。发布状态文字不能替代这些条件。</p>
      <el-table :data="page.items" row-key="version_id" empty-text="当前范围没有获授权场景版本">
        <el-table-column label="配置名称" min-width="180"><template #default="cell">{{ cell.row.name ?? '配置名称未登记' }}</template></el-table-column>
        <el-table-column prop="version_id" label="固定场景版本" min-width="280" />
        <el-table-column label="配置状态" min-width="140"><template #default="cell">{{ statusLabels[cell.row.status] ?? cell.row.status }}</template></el-table-column>
        <el-table-column label="待完成条件" min-width="240"><template #default="cell">{{ cell.row.missing_requirements.map(requirementLabel).join('、') }}</template></el-table-column>
        <el-table-column label="规格核验" width="140"><template #default="cell">{{ cell.row.definition_status === 'UNVERIFIED' ? '尚未核验' : cell.row.definition_status }}</template></el-table-column>
        <el-table-column label="正式任务入口" min-width="140"><template #default>当前未提供</template></el-table-column>
      </el-table>
      <el-button :disabled="disabled || changedFilters || !page.next_after" @click="next">读取下一页场景版本</el-button>
      <p v-if="changedFilters">查询条件已经修改，请重新查询后继续分页。</p>
      <h2>待定义的规划要求</h2>
      <p>规划说明保留独立业务要求，不计入当前配置版本数量。</p>
      <el-table :data="page.planned_requirements" row-key="scenario_id">
        <el-table-column prop="name" label="场景名称" min-width="160" />
        <el-table-column prop="purpose" label="业务用途" min-width="260" />
        <el-table-column label="状态" min-width="110"><template #default>规划中</template></el-table-column>
        <el-table-column label="需要定义的内容" min-width="360"><template #default="cell">{{ cell.row.required_definition_fields.map(definitionLabel).join('、') }}</template></el-table-column>
      </el-table>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { listScenarios } from '@/api/medical/scenarios'
import type { ScenarioPage, ScenarioQuery } from '@/api/medical/scenarios'
import { taskDocumentLabels, taskIdReference } from '@/api/medical/tasks'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import SessionPanel from './session-panel.vue'
import ScenarioPlanningWorkspace from './ScenarioPlanningWorkspace.vue'
import NativeImageSimulationWorkspace from './NativeImageSimulationWorkspace.vue'
const showImageSimulation = import.meta.env.VITE_MEDICAL_IMAGE_SIMULATION === 'true'
import ProjectScopeSelect from '@/components/Medical/ProjectScopeSelect.vue'

const user = useUserStore(); const session = computed<MedicalSession | null>(() => user.medicalSession)
const error = ref(''), pickerGeneration = ref(0)
const projectId = ref(''); const documentType = ref(''); const page = ref<ScenarioPage | null>(null); const busy = ref(false)
const projectError = computed(() => projectId.value && schemaIssues(taskIdReference, projectId.value).length ? '请填写有效项目标识' : '')
const filterDisabled = computed(() => !active.value || user.medicalWriting || !session.value?.current_context || document.hidden)
const disabled = computed(() => filterDisabled.value || busy.value)
const changedFilters = computed(() => page.value !== null && (page.value.project_id !== (projectId.value || null) || page.value.document_type !== (documentType.value || null)))
const statusLabels: Record<string,string> = { DRAFT: '草稿', VALIDATING: '验证中', EVALUATING: '评测中', REVIEW_PENDING: '待审核', PUBLISHED: '发布记录', DISABLED: '已停用' }
const requirements: Record<string,string> = { SCENARIO_SPECIFICATION_UNVERIFIED: '完整场景规格尚未核验', RELEASE_ADMISSION_UNAVAILABLE: '完整发布准入尚未提供', SCENARIO_DISABLED: '场景版本已经停用' }
const definitions: Record<string,string> = { ROLES: '使用角色', INPUTS: '输入要求', APPLICABLE_KNOWLEDGE: '适用知识', TASK_RULES: '任务规则', OUTPUTS: '输出要求', HUMAN_REVIEW: '人工审核', FAILURE_STATES: '失败状态', ACCEPTANCE_SAMPLES: '验收样本' }
function documentLabel(value: string): string { return taskDocumentLabels[value as keyof typeof taskDocumentLabels] ?? value }
function requirementLabel(value: string): string {
  const label = requirements[value]
  if (label === undefined) fail('SCHEMA_INVALID', '场景缺项类型未经定义')
  return label
}
function definitionLabel(value: string): string {
  const label = definitions[value]
  if (label === undefined) fail('SCHEMA_INVALID', '场景规格字段未经定义')
  return label
}
let generation = 0; let controller: AbortController | null = null
function stopRead(): void { generation++; controller?.abort(); controller = null; busy.value = false; page.value = null }
function clear(): void { stopRead(); error.value = ''; pickerGeneration.value++; projectId.value = ''; documentType.value = '' }
const active = useMedicalRouteRead('MedicalScenarios', clear)
function current(actual: MedicalSession, version: number): boolean { return session.value === actual && generation === version && active.value && !document.hidden }
async function load(previous: ScenarioPage | null): Promise<void> {
  const actual = session.value; const version = generation
  if (!actual || disabled.value || projectError.value || previous && !previous.next_after) return
  const query: ScenarioQuery = { limit: 20 }
  if (projectId.value) query.project_id = projectId.value
  if (documentType.value) query.document_type = documentType.value
  if (previous) { query.after = previous.next_after!; query.dataset_watermark = previous.dataset_watermark }
  busy.value = true; error.value = ''; controller = new AbortController(); const reading = controller
  try { const value = await listScenarios(actual, query, previous, reading.signal); if (current(actual, version) && controller === reading && !reading.signal.aborted) page.value = value }
  catch (failure) { if (current(actual, version) && controller === reading && !reading.signal.aborted) { page.value = null; error.value = failure instanceof Error ? failure.message : '目录读取未确认，请重新查询当前范围。' } }
  finally { if (current(actual, version) && controller === reading) { busy.value = false; controller = null } }
}
async function search(): Promise<void> { if (disabled.value) return; stopRead(); await load(null) }
async function next(): Promise<void> { if (!changedFilters.value) await load(page.value) }
watch([projectId, documentType], () => { stopRead(); error.value = '' }, { flush: 'sync' })
watch([session, active], () => { clear(); if (active.value) void search() }, { immediate: true, deep: true, flush: 'sync' })
async function visibilityChanged(): Promise<void> {
  if (document.hidden) { stopRead(); pickerGeneration.value++ }
  else if (active.value) await search()
}
onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
onBeforeUnmount(() => { document.removeEventListener('visibilitychange', visibilityChanged); stopRead() })
</script>

<style scoped>p{overflow-wrap:anywhere;line-height:1.6;color:#526279}.el-alert{margin:16px 0}.el-table{margin:16px 0}h2{margin-top:28px}</style>

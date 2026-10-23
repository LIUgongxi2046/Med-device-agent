<template>
  <main class="medical-page operations-page">
    <header class="operations-heading"><h1>评测与运营</h1><el-button :disabled="!report || loading || exporting || changed" :loading="exporting" @click="download">导出当前统计</el-button></header>
    <operations-navigation />
    <el-card shadow="never" class="operations-filters">
      <el-form label-position="top" @submit.prevent="load">
        <div class="operations-filter-grid">
          <el-form-item label="统计范围"><el-radio-group v-model="scope"><el-radio-button value="enterprise">当前企业</el-radio-button><el-radio-button value="project">指定项目</el-radio-button></el-radio-group></el-form-item>
          <el-form-item v-if="scope === 'project'" label="项目"><project-scope-select v-model="projectId" /></el-form-item>
          <el-form-item label="统计周期"><el-select v-model="period" @change="setPeriod"><el-option label="本月" value="month" /><el-option label="最近七天" value="week" /><el-option label="自定义" value="custom" /></el-select></el-form-item>
          <el-form-item label="起止时间"><el-date-picker v-model="range" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" range-separator="至" :disabled-date="futureDate" @change="period = 'custom'" /></el-form-item>
        </div>
        <el-collapse><el-collapse-item title="更多筛选条件" name="filters"><div class="operations-filter-grid">
          <el-form-item label="器械类别"><el-input v-model="category" clearable /></el-form-item><el-form-item label="产品类型"><el-input v-model="productType" clearable /></el-form-item><el-form-item label="办理阶段"><el-input v-model="stage" clearable /></el-form-item>
          <el-form-item label="文档类型"><el-select v-model="documentType" clearable><el-option v-for="(label, key) in templateDocumentLabels" :key="key" :value="key" :label="label" /></el-select></el-form-item>
          <el-form-item><el-checkbox v-model="includeRecords">包含已授权的调用与业务明细</el-checkbox></el-form-item>
        </div></el-collapse-item></el-collapse>
        <div class="operations-query-actions"><span>统计范围最长93天，时间按照当前时区选择。</span><el-button native-type="submit" type="primary" :loading="loading" :disabled="!canRead">读取统计</el-button></div>
      </el-form>
    </el-card>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-alert v-if="changed" title="筛选条件已变化，请重新读取统计。" type="info" :closable="false" />
    <el-skeleton v-if="loading" :rows="7" animated />
    <template v-if="report">
      <section class="operations-summary" data-operations-report aria-label="用量与效果">
        <article><span>登记调用</span><strong>{{ report.registered_task_invocations }}</strong><small>任务模型调用记录</small></article>
        <article><span>结果采纳率</span><strong>{{ percentage(report.adoption) }}</strong><small>{{ report.adoption.numerator }} 项采纳 / {{ report.adoption.denominator }} 项候选</small></article>
        <article><span>审查退回率</span><strong>{{ percentage(report.return) }}</strong><small>{{ report.return.numerator }} 项退回 / {{ report.return.denominator }} 项审查决定</small></article>
        <article><span>已结算 Token</span><strong>{{ knownTokens }}</strong><small>仅统计具有完整结算依据的调用</small></article>
      </section>
      <el-card shadow="never"><template #header><div class="operations-panel-heading"><h2>调用与计费记录</h2><el-tag type="info">{{ scopeName }}</el-tag></div></template>
        <el-table :data="report.cost_by_currency" empty-text="当前周期没有正式调用与计费记录">
          <el-table-column prop="currency" label="币种" width="90" /><el-table-column prop="invocations" label="登记调用" /><el-table-column prop="settled_count" label="已结算调用" /><el-table-column prop="known_tokens" label="已结算 Token" min-width="130" /><el-table-column prop="known_amount" label="已确认费用" min-width="130" />
          <el-table-column label="周期总费用" min-width="150"><template #default="{ row }">{{ row.total_amount === null ? '等待结算依据' : row.total_amount }}</template></el-table-column><el-table-column prop="unknown_count" label="待核查调用" />
        </el-table>
      </el-card>
      <el-card shadow="never"><template #header><h2>项目统计</h2></template><el-table :data="report.project_groups" empty-text="当前周期没有符合条件的业务记录">
        <el-table-column label="项目" min-width="240"><template #default="{ row }"><router-link :to="'/projects/' + row.project_id">{{ projectNames[row.project_id] ?? row.project_id }}</router-link></template></el-table-column><el-table-column prop="invocations" label="模型调用" /><el-table-column prop="candidates" label="生成候选" /><el-table-column prop="formal_review_decisions" label="审查决定" /><el-table-column prop="independent_revision_returns" label="文稿退回" />
      </el-table></el-card>
      <el-card v-if="report.records_included" shadow="never"><template #header><h2>业务明细</h2></template><el-table :data="report.records" empty-text="当前周期没有符合条件的明细">
        <el-table-column label="业务类型" min-width="170"><template #default="{ row }">{{ recordKinds[String(row.kind)] }}</template></el-table-column><el-table-column label="项目" min-width="220"><template #default="{ row }">{{ projectNames[String(row.project_id)] ?? row.project_id }}</template></el-table-column><el-table-column prop="status" label="调用状态" /><el-table-column prop="known_tokens" label="Token" /><el-table-column prop="known_amount" label="费用" /><el-table-column prop="currency" label="币种" />
      </el-table></el-card>
      <el-collapse class="operations-method"><el-collapse-item title="统计依据与范围" name="method"><el-descriptions :column="2" border><el-descriptions-item label="统计开始">{{ localTime(report.from) }}</el-descriptions-item><el-descriptions-item label="统计结束">{{ localTime(report.to) }}</el-descriptions-item><el-descriptions-item label="读取时间">{{ localTime(report.observed_at) }}</el-descriptions-item><el-descriptions-item label="统计规则">{{ report.metric_revision }}</el-descriptions-item></el-descriptions><p>采纳率按照本周期创建的候选统计，退回率按照本周期正式审查决定统计。没有分母或采纳依据时显示暂无统计。独立文稿退回另行计数。</p><p>系统任务与SIMULATION运行不计入正式用量。不同币种分别统计，缺少结算依据时保留待核查状态。</p><p>质量改善、人工评分和节省时间需要相应评测与业务记录，当前统计不推算这些结果。</p></el-collapse-item></el-collapse>
    </template>
    <el-empty v-else-if="!loading && !error" description="选择统计范围与周期，查看实际用量及业务效果。" />
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'
import OperationsNavigation from '@/components/Medical/OperationsNavigation.vue'
import ProjectScopeSelect from '@/components/Medical/ProjectScopeSelect.vue'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { listProjects } from '@/api/medical/projects'
import { templateDocumentLabels } from '@/api/medical/template-directory'
import { exportOperationsMetrics, operationsAllowed, percentage, readOperationsMetrics } from '@/api/medical/business-operations'
import type { OperationsMetrics, OperationsQuery } from '@/api/medical/business-operations'
const user = useUserStore(), scope = ref('enterprise'), projectId = ref(''), period = ref('month'), range = ref<[Date, Date] | null>(null)
const category = ref(''), productType = ref(''), stage = ref(''), documentType = ref(''), includeRecords = ref(false)
const loading = ref(false), exporting = ref(false), error = ref(''), report = ref<OperationsMetrics | null>(null), projectNames = ref<Record<string, string>>({})
let generation = 0, controller: AbortController | null = null, selectedQuery: OperationsQuery | null = null
function clear() { generation++; controller?.abort(); controller = null; loading.value = false; exporting.value = false; error.value = ''; report.value = null; selectedQuery = null; projectNames.value = {} }
const active = useMedicalRouteRead('MedicalBusinessOperations', clear)
const canRead = computed(() => active.value && !!range.value && (scope.value === 'enterprise' || !!projectId.value) && operationsAllowed(user.medicalSession, 'report.read', scope.value === 'project' ? projectId.value : undefined))
function query(): OperationsQuery { if (!range.value) throw new Error('请选择统计周期'); return { ...(scope.value === 'project' ? { project_id: projectId.value } : {}), from: range.value[0].toISOString(), to: range.value[1].toISOString(), ...(category.value ? { device_category: category.value } : {}), ...(productType.value ? { product_type: productType.value } : {}), ...(stage.value ? { stage: stage.value } : {}), ...(documentType.value ? { document_type: documentType.value } : {}), include_records: includeRecords.value } }
const changed = computed(() => !!report.value && JSON.stringify(query()) !== JSON.stringify(selectedQuery))
const scopeName = computed(() => report.value?.project_id ? projectNames.value[report.value.project_id] ?? '指定项目' : '当前企业')
const knownTokens = computed(() => report.value?.cost_by_currency.reduce((sum, item) => sum + BigInt(item.known_tokens), 0n).toLocaleString('zh-CN') ?? '0')
const recordKinds: Record<string, string> = { TASK_INVOCATION: '模型调用', CANDIDATE: '章节或计划候选', REVIEW_DECISION: '审查决定', INDEPENDENT_REVISION_RETURN: '文稿退回' }
function localTime(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
function futureDate(value: Date) { return value.getTime() > Date.now() }
function setPeriod() { if (period.value === 'custom') return; const end = new Date(Date.now() - 1000), start = new Date(end); if (period.value === 'month') { start.setDate(1); start.setHours(0, 0, 0, 0) } else start.setDate(start.getDate() - 7); range.value = [start, end] }
async function load() {
  if (!canRead.value || loading.value) return
  const session = user.medicalSession!; clear(); const current = generation, reading = new AbortController(); controller = reading; loading.value = true
  try {
    const input = query(), value = await readOperationsMetrics(session, input, reading.signal)
    if (generation !== current || user.medicalSession !== session || !active.value) return
    report.value = value; selectedQuery = input
    const projects = await listProjects(session.current_context!, {}, reading.signal)
    if (generation === current && user.medicalSession === session) projectNames.value = Object.fromEntries(projects.projects.map(project => [project.project_id, project.name]))
  } catch (failure) { if (generation === current && !reading.signal.aborted) { report.value = null; error.value = failure instanceof Error ? failure.message : '统计读取失败' } }
  finally { if (generation === current) loading.value = false }
}
async function download() {
  if (!report.value || !selectedQuery || changed.value || exporting.value || !operationsAllowed(user.medicalSession, 'report.export', selectedQuery.project_id)) return
  const session = user.medicalSession!, current = generation, reading = new AbortController(); controller = reading; exporting.value = true; error.value = ''
  try { const file = await exportOperationsMetrics(session, selectedQuery, reading.signal); if (generation === current && user.medicalSession === session && active.value) saveAs(file, 'business-operations.csv') }
  catch (failure) { if (generation === current && !reading.signal.aborted) error.value = failure instanceof Error ? failure.message : '统计导出失败' }
  finally { if (generation === current) exporting.value = false }
}
watch(() => user.medicalSession, () => { clear(); projectId.value = ''; setPeriod() }, { immediate: true })
onBeforeUnmount(clear)
</script>
<style scoped>
.operations-page{max-width:1480px;margin:auto;padding:28px}.operations-heading,.operations-panel-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.operations-heading{margin-bottom:12px}.operations-heading h1{margin:0;font-size:24px}.operations-filter-grid{display:flex;flex-wrap:wrap;gap:0 24px}.operations-filter-grid .el-form-item{min-width:200px;flex:1}.operations-filter-grid :deep(.el-date-editor){width:100%;min-width:340px}.operations-query-actions{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-top:16px}.operations-query-actions span{color:var(--el-text-color-secondary);font-size:12px}.operations-summary{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin:24px 0}.operations-summary article{display:grid;gap:10px;background:white;border:1px solid var(--el-border-color-light);border-radius:8px;padding:22px}.operations-summary span{color:var(--el-text-color-regular);font-size:14px}.operations-summary strong{font-size:28px;font-weight:600;color:var(--el-text-color-primary)}.operations-summary small{color:var(--el-text-color-secondary);line-height:1.6;font-size:12px}.operations-page>.el-card{margin-bottom:20px}.operations-page h2{font-size:17px;margin:0}.operations-page>.el-alert{margin:16px 0}.operations-method p{color:var(--el-text-color-secondary);line-height:1.8}@media(max-width:1000px){.operations-summary{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:650px){.operations-page{padding:16px}.operations-summary{grid-template-columns:1fr}.operations-query-actions{align-items:flex-start;flex-direction:column}.operations-filter-grid :deep(.el-date-editor){min-width:0}.operations-heading{flex-wrap:wrap}}
</style>

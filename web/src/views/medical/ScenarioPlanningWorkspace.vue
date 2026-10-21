<template>
  <section class="scenario-planning" aria-labelledby="planning-heading">
    <header class="planning-header">
      <div><span class="eyebrow">扩展场景 · 需求准备</span><h2 id="planning-heading">先明确需求，再开启正式任务</h2><p>查看规划用途，整理输入与审核责任。准备记录仅保留在当前页面内存，不会提交或创建任务；目录查询条件不会改变准备记录。</p></div>
      <el-tag type="warning" effect="plain">规划中 · 待定义</el-tag>
    </header>
    <el-alert v-if="preview" title="交互预览：以下为真实 Vue 组件；无业务数据、无服务请求，不代表场景已发布。" type="info" :closable="false" />
    <el-alert v-if="disabled" title="当前不能准备需求：请选择企业并确认当前访问状态。已有本地记录已清除。" type="warning" :closable="false" />
    <div class="scenario-cards" aria-label="规划场景用途">
      <el-card v-for="item in scenarioPlanning" :key="item.id" shadow="never" :class="{ selected: selectedId === item.id }">
        <el-tag size="small" type="info">待定义</el-tag><h3>{{ item.short }}</h3><p>{{ item.purpose }}</p>
        <el-button :disabled="disabled" :type="selectedId === item.id ? 'primary' : 'default'" plain @click="selectScenario(item.id)">{{ selectedId === item.id ? '当前查看' : '查看需求与准备' }}</el-button>
      </el-card>
    </div>
    <el-empty v-if="!selected" description="选择一个规划场景，查看用途与准备内容。此操作不会选择正式任务版本。" :image-size="76" />
    <section v-else class="planning-detail" aria-labelledby="scenario-detail-title">
      <div class="detail-heading"><div><span class="eyebrow">规划详情 / {{ selected.short }}</span><h3 id="scenario-detail-title">{{ selected.name }}</h3><p>{{ selected.purpose }}</p></div><el-button @click="selectedId = null">返回规划目录</el-button></div>
      <el-steps :active="step" finish-status="process" simple><el-step title="查看边界" /><el-step title="整理需求" /><el-step title="本地预览" /></el-steps>
      <div v-if="step === 0" class="boundary-grid">
        <div><h4>本页可以做什么</h4><p>{{ selected.hint }}</p><p>填写的是待确认的需求说明，不是已批准规格。页面切换、企业切换或访问状态变化后不保留记录。</p><el-button type="primary" :disabled="disabled" @click="step = 1">开始整理需求</el-button></div>
        <div class="boundary-note"><h4>正式任务尚不可用</h4><p>完整场景规格未核验，发布准入未提供，服务端 task_entry 为空。配置的发布状态文字不能替代实际准入。</p><p>本页不生成任务编号、结果、审批记录或通过结论。</p></div>
      </div>
      <el-form v-else-if="step === 1" label-position="top" @submit.prevent="previewDraft">
        <p class="local-note">仅检查本地记录是否便于阅读，不进行业务验收。下列八项均为 PRD 要求的待确认规格维度。</p>
        <el-form-item label="本地准备记录名称" :error="attempted && !draft.title.trim() ? '请填写记录名称' : ''"><el-input v-model="draft.title" maxlength="80" show-word-limit placeholder="为这份待确认需求起一个名称" :disabled="disabled" /></el-form-item>
        <div class="field-grid"><el-form-item v-for="field in planningFields" :key="field.key" :label="field.label + ' · 待确认'" :error="attempted && field.key === 'inputs' && !draft.notes.inputs.trim() ? '请填写输入准备说明' : ''"><el-input v-model="draft.notes[field.key]" type="textarea" :rows="3" maxlength="1000" show-word-limit :placeholder="field.placeholder" :disabled="disabled" /></el-form-item></div>
        <el-alert v-if="errors.length" :title="errors.join(' ')" type="error" :closable="false" role="alert" />
        <div class="detail-actions"><el-button @click="step = 0">返回查看边界</el-button><el-button type="primary" native-type="submit" :disabled="disabled">预览本地准备记录</el-button><el-button :disabled="disabled" @click="clearDraft">清空当前记录</el-button></div>
      </el-form>
      <div v-else class="draft-preview">
        <el-alert title="本地准备记录 · 未提交 · 未核验，不是任务结果" type="warning" :closable="false" />
        <h4>{{ draft.title }}</h4><el-descriptions :column="1" border><el-descriptions-item v-for="field in planningFields" :key="field.key" :label="field.label + ' · 待确认'"><span class="draft-text">{{ draft.notes[field.key].trim() || '尚未填写，等待确认' }}</span></el-descriptions-item></el-descriptions>
        <div class="detail-actions"><el-button @click="step = 1">继续编辑</el-button><el-button @click="clearDraft">清空当前记录</el-button></div>
      </div>
      <footer class="formal-entry"><span>正式任务入口未提供。完成本地填写不代表获授权发布或可执行。</span><el-button type="primary" disabled>创建正式任务（未开放）</el-button></footer>
    </section>
  </section>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { emptyPlanningDraft, planningFields, scenarioPlanning, validatePlanningDraft } from './scenario-planning'
import type { PlanningDraft, PlanningId } from './scenario-planning'
const props = withDefaults(defineProps<{ disabled?: boolean; preview?: boolean }>(), { disabled: false, preview: false })
const selectedId = ref<PlanningId | null>(null), step = ref(0), attempted = ref(false), errors = ref<string[]>([])
const drafts = reactive<Partial<Record<PlanningId, PlanningDraft>>>({})
const selected = computed(() => scenarioPlanning.find(item => item.id === selectedId.value) ?? null)
const draft = computed(() => selectedId.value ? drafts[selectedId.value] ?? emptyPlanningDraft() : emptyPlanningDraft())
function selectScenario(id: PlanningId): void { if (props.disabled) return; if (!drafts[id]) drafts[id] = emptyPlanningDraft(); selectedId.value = id; step.value = 0; attempted.value = false; errors.value = [] }
function previewDraft(): void { if (props.disabled || !selectedId.value) return; attempted.value = true; errors.value = validatePlanningDraft(draft.value); if (!errors.value.length) step.value = 2 }
function clearDraft(): void { if (selectedId.value) drafts[selectedId.value] = emptyPlanningDraft(); attempted.value = false; errors.value = []; step.value = 1 }
watch(() => props.disabled, value => { if (value) { for (const id of Object.keys(drafts) as PlanningId[]) delete drafts[id]; selectedId.value = null; step.value = 0; attempted.value = false; errors.value = [] } }, { flush: 'sync' })
</script>
<style scoped>
.scenario-planning{margin:24px 0 32px;padding:24px;background:#f5f8fc;border:1px solid #dfe6ef;border-radius:16px;color:#24364c}.planning-header,.detail-heading,.formal-entry{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.eyebrow{font-size:12px;font-weight:650;letter-spacing:.06em;color:#4f6591}h2{font-size:23px;margin:10px 0}h3{font-size:17px;margin:12px 0}h4{font-size:16px;margin:18px 0 12px}p{font-size:14px;line-height:1.8;color:#526279;margin:8px 0 16px}.scenario-cards{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin:22px 0}.scenario-cards :deep(.el-card__body){display:flex;flex-direction:column;align-items:flex-start;height:100%}.scenario-cards p{flex:1;min-height:76px}.scenario-cards .el-card{border-radius:12px}.scenario-cards .selected{border-color:#4275d6;box-shadow:0 0 0 1px #4275d6}.scenario-cards .el-button{max-width:100%;margin-top:auto}.planning-detail{background:white;border:1px solid #dfe6ef;border-radius:12px;padding:24px}.boundary-grid,.field-grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}.boundary-note{background:#fff8ec;border-radius:10px;padding:0 20px;margin-top:20px}.el-steps{margin:12px 0 24px}.detail-actions{display:flex;gap:10px;flex-wrap:wrap;margin:22px 0}.detail-actions .el-button{margin:0}.formal-entry{border-top:1px solid #e4eaf2;padding-top:20px;margin-top:24px;align-items:center;font-size:13px;color:#64748b}.draft-text{white-space:pre-wrap;overflow-wrap:anywhere}.local-note{background:#f4f7fc;padding:12px;border-radius:8px}@media(max-width:1100px){.scenario-cards{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:700px){.scenario-planning{padding:14px}.planning-header,.detail-heading,.formal-entry{flex-direction:column}.scenario-cards{grid-template-columns:1fr}.scenario-cards p{min-height:0}.boundary-grid,.field-grid{grid-template-columns:1fr;gap:12px}.planning-detail{padding:16px}.el-steps{padding:12px 5px}.planning-header h2{font-size:20px}}
</style>

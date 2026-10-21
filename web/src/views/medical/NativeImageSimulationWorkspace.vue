<template>
  <section class="image-sim" aria-labelledby="image-sim-title">
    <h2 id="image-sim-title">原生图像 API 模拟联调</h2>
    <el-alert type="warning" :closable="false" title="SIM：仅验证图像传输和接口流程，不代表真实视觉理解或专业结论。" />
    <p>固定合成图片由服务器提供，不发送到外部模型。模拟账本只保存在服务进程中，服务重启后不能据此恢复生产结果。</p>
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="获授权项目"><project-scope-select v-model="projectId" :disabled="disabled || frozen !== null" /></el-form-item>
      <el-button :disabled="disabled || !projectId || frozen !== null" @click="loadProfile">读取模拟提供方与图片</el-button>
      <template v-if="profile">
        <p>提供方：{{ profile.provider }}；图片摘要：{{ profile.fixture.sha256 }}</p>
        <img :src="preview" alt="用于接口测试的四格合成色块，不是真实医学图像" width="96" height="96" class="image-sim-fixture" />
        <el-form-item label="模拟问题"><el-input v-model="question" type="textarea" :rows="2" maxlength="8000" :disabled="disabled || frozen !== null" /></el-form-item>
        <el-button :disabled="disabled || !question.trim() || frozen !== null" @click="freeze">固定问题与两个独立调用标识</el-button>
      </template>
    </el-form>
    <el-alert v-if="error" type="error" :title="error" :closable="false" role="alert" />
    <template v-if="frozen">
      <p>原问题：{{ frozen.answer.question }}。请求固定后不能修改问题、图片或标识。</p>
      <div v-for="side in sides" :key="side" class="image-sim-call">
        <h3>{{ side === 'answer' ? '回答调用' : '独立复核调用' }}</h3>
        <p>请求 {{ frozen[side].request_id }} · 状态 {{ states[side] }}</p>
        <el-button :disabled="disabled || !['NEW','PREPARE_UNKNOWN'].includes(states[side])" @click="operate(side, 'prepare')">准备原请求</el-button>
        <el-button :disabled="disabled || states[side] !== 'PREPARED'" @click="operate(side, 'dispatch')">发送固定模拟请求</el-button>
        <el-button :disabled="disabled || !['UNKNOWN','SETTLED'].includes(states[side])" @click="operate(side, 'recover')">只读恢复原结果</el-button>
        <p v-if="receipts[side]" class="image-sim-result">{{ receipts[side]?.data.result }}</p>
      </div>
      <p v-if="unresolved">尚有未确定结果，请保留原请求；不能将超时当作未发送，也不能换标识重发。</p>
      <el-button :disabled="disabled || states.answer !== 'SETTLED' || states.reviewer !== 'SETTLED'" @click="review">核对两个实际模拟回执</el-button>
      <p v-if="reviewed" role="status">两次合成图像传输回执已核对。这不是图像内容语义支持，不可用于正式文稿或审批。</p>
      <p v-if="last">模拟预占 {{ last.reserved_units }} 单位；已结算 {{ last.settled_units }} 单位。不是供应商实际费用。</p>
      <el-button :disabled="disabled || !reviewed" @click="resetCompleted">结束本次模拟</el-button>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import type { MedicalSession } from '@/api/medical/protocol'
import { readImageSimulationProfile, newImageSimulationContext, callImageSimulation } from '@/api/medical/native-image-simulation'
import type { ImageSimulationProfile, ImageSimulationContext, ImageSimulationResponse } from '@/api/medical/native-image-simulation'
import ProjectScopeSelect from '@/components/Medical/ProjectScopeSelect.vue'
const user = useUserStore()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const projectId = ref(''), question = ref('检查这张合成图片是否按原始字节传入模拟接口。')
const profile = ref<ImageSimulationProfile | null>(null)
const frozen = ref<{ answer: ImageSimulationContext; reviewer: ImageSimulationContext } | null>(null)
const sides = ['answer', 'reviewer'] as const
const states = reactive<Record<typeof sides[number], string>>({ answer: 'NEW', reviewer: 'NEW' })
const receipts = reactive<Record<typeof sides[number], ImageSimulationResponse | null>>({ answer: null, reviewer: null })
const last = ref<ImageSimulationResponse | null>(null), reviewed = ref(false), busy = ref(false), error = ref('')
let generation = 0, active = true, reading: AbortController | null = null
const disabled = computed(() => busy.value || user.medicalWriting || !session.value?.current_context || !active)
const preview = computed(() => profile.value ? `data:${profile.value.fixture.mime};base64,${profile.value.fixture.base64}` : '')
const unresolved = computed(() => Object.values(states).some(value => value === 'UNKNOWN' || value === 'PREPARE_UNKNOWN'))
const identity = computed(() => [session.value?.user.user_id, session.value?.current_context?.tenant_id,
  session.value?.current_context?.context_revision, session.value?.current_context?.auth_epoch, session.value?.current_context?.governance_epoch].join(':'))
function clear() {
  generation++; reading?.abort(); reading = null; profile.value = null; frozen.value = null
  states.answer = states.reviewer = 'NEW'; receipts.answer = receipts.reviewer = null
  last.value = null; reviewed.value = false; error.value = ''; busy.value = false
}
watch(identity, () => { clear(); projectId.value = '' })
watch(projectId, () => { if (!frozen.value) clear() })
function message(cause: unknown): string { return cause instanceof Error ? cause.message : '模拟请求未完成，请核查原请求状态' }
async function loadProfile() {
  const selected = session.value; if (disabled.value || !selected) return
  const current = generation; busy.value = true; error.value = ''; reading = new AbortController()
  try { const value = await readImageSimulationProfile(selected, projectId.value, reading.signal); if (active && current === generation) profile.value = value }
  catch (cause) { if (active && current === generation) error.value = message(cause) }
  finally { if (current === generation) busy.value = false }
}
function freeze() {
  if (disabled.value || !session.value || !profile.value || frozen.value) return
  try {
    frozen.value = { answer: newImageSimulationContext(session.value, projectId.value, question.value, profile.value),
      reviewer: newImageSimulationContext(session.value, projectId.value, question.value, profile.value) }
    error.value = ''
  } catch (cause) { error.value = message(cause) }
}
async function operate(side: typeof sides[number], operation: 'prepare' | 'dispatch' | 'recover') {
  const selected = session.value, fixed = frozen.value; if (disabled.value || !selected || !fixed) return
  const current = generation; busy.value = true; error.value = ''
  states[side] = operation === 'prepare' ? 'PREPARE_UNKNOWN' : 'UNKNOWN'
  try {
    const value = await callImageSimulation(selected, operation, fixed[side])
    if (!active || current !== generation) return
    last.value = value
    if (operation === 'prepare') states[side] = 'PREPARED'
    else if (value.data.request_id === fixed[side].request_id) { states[side] = 'SETTLED'; receipts[side] = value }
    else { states[side] = 'UNKNOWN'; error.value = '服务端尚无确定原结果，保持原请求，不重发。' }
  } catch (cause) { if (active && current === generation) error.value = message(cause) }
  finally { if (current === generation) busy.value = false }
}
async function review() {
  const selected = session.value, fixed = frozen.value
  if (disabled.value || !selected || !fixed || states.answer !== 'SETTLED' || states.reviewer !== 'SETTLED') return
  const current = generation; busy.value = true; error.value = ''
  try { const value = await callImageSimulation(selected, 'review', fixed); if (active && current === generation) { last.value = value; reviewed.value = true } }
  catch (cause) { if (active && current === generation) error.value = message(cause) }
  finally { if (current === generation) busy.value = false }
}
function resetCompleted() { if (!disabled.value && reviewed.value) clear() }
onBeforeUnmount(() => { active = false; clear() })
</script>
<style scoped>
.image-sim { margin-block: 2rem; padding: 1.25rem; border: 1px solid var(--el-border-color); border-radius: 8px; }
.image-sim-call { margin-block: 1rem; padding-block: .5rem; border-bottom: 1px solid var(--el-border-color); }
.image-sim p { overflow-wrap: anywhere; }
.image-sim-fixture { image-rendering: pixelated; margin-block: .75rem; }
.image-sim-result { white-space: pre-wrap; }
</style>

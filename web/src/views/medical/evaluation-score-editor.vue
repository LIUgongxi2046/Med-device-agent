<template>
  <section aria-label="追加评分或修订" data-evaluation-score-editor>
    <h2>追加专家观察</h2>
    <p>修订会保留原评分。分母来自固定预期项目集。</p>
    <el-form label-position="top" @submit.prevent="submit">
      <div class="evaluation-fields">
        <el-form-item label="固定样本"><el-select v-model="sampleId" :disabled="disabled" placeholder="选择固定样本"><el-option v-for="sample in view.definition.request.samples" :key="sample.sample_id" :label="sample.document_type + ' · ' + sample.sample_id" :value="sample.sample_id" /></el-select></el-form-item>
        <el-form-item label="固定候选"><el-select v-model="candidateId" :disabled="disabled" placeholder="选择候选版本"><el-option v-for="candidate in view.definition.candidate_snapshots" :key="candidate.version_id" :label="candidate.version_id + (candidate.created_by === actor ? '（本人创建，不可评分）' : '')" :value="candidate.version_id" :disabled="candidate.created_by === actor" /></el-select></el-form-item>
        <el-form-item label="必需维度"><el-select v-model="dimension" :disabled="disabled" placeholder="选择维度"><el-option v-for="value in view.definition.request.required_dimensions" :key="value" :label="value" :value="value" /></el-select></el-form-item>
        <el-form-item label="重复索引（从0开始）"><el-select v-model="repeatIndex" :disabled="disabled"><el-option v-for="value in view.definition.request.variance_policy.repeat_count" :key="value" :label="String(value - 1)" :value="value - 1" /></el-select></el-form-item>
      </div>
      <p v-if="latest">当前有效评分：{{ latest.score_id }}；提交将修订此记录，原记录保持可查。</p>
      <p v-else-if="cell">此单元尚无评分，提交将追加第一条观察。</p>
      <p v-if="cell && !expected" role="status">缺少预期项目集，请登记未知或阻断观察。</p>
      <el-form-item label="观察状态"><el-select v-model="status" :disabled="disabled"><el-option label="已评分：有实际输出与依据" value="SCORED" :disabled="!expected" /><el-option label="未知：观察不足" value="UNKNOWN" /><el-option label="阻断：无法完成观察" value="BLOCKED" /></el-select></el-form-item>
      <div v-if="status === 'SCORED'" class="evaluation-fields">
        <el-form-item label="符合项目数（分子）"><el-input id="evaluation-numerator" v-model="numerator" inputmode="numeric" :disabled="disabled" /></el-form-item>
        <el-form-item label="固定适用项目数（分母）"><el-input :model-value="String(expected?.applicable_item_ids.length ?? '')" disabled /></el-form-item>
        <el-form-item label="未知项目数（未填写保持未知）"><el-input id="evaluation-unknown" v-model="unknownCount" inputmode="numeric" :disabled="disabled" /></el-form-item>
        <el-form-item label="违反项目数（未填写保持未知）"><el-input id="evaluation-violations" v-model="violationCount" inputmode="numeric" :disabled="disabled" /></el-form-item>
        <template v-if="dimension === 'EVAL-11'"><el-form-item label="合法对照符合数"><el-input id="evaluation-control" v-model="controlNumerator" inputmode="numeric" :disabled="disabled" /></el-form-item><el-form-item label="固定合法对照分母"><el-input :model-value="String(expected?.control_item_ids.length ?? '')" disabled /></el-form-item></template>
      </div>
      <p v-if="sample">输入固定版本：{{ sample.input_ref.version_id }}；SHA-256：{{ sample.input_ref.content_sha256 }}</p>
      <evaluation-basis-field v-model="outputBasis" label="实际输出制品" kind="artifact" :project="view.definition.request.project_id" :disabled="disabled || fixedOutput !== null" />
      <p v-if="fixedOutput">此样本、候选及重复索引已有实际输出，所有维度沿用同一输出。</p>
      <h3>评分依据引用</h3>
      <div v-for="(basis, index) in evidence" :key="basis.localKey">
        <evaluation-basis-field :model-value="basis" :label="'评分依据 ' + (index + 1)" kind="artifact" :project="view.definition.request.project_id" :disabled="disabled" @update:model-value="Object.assign(basis, $event)" />
        <el-button :disabled="disabled" @click="evidence.splice(index, 1)">移除此依据</el-button>
      </div>
      <el-button :disabled="disabled || evidence.length >= 100" @click="addEvidence">添加已有依据引用</el-button>
      <el-form-item label="观察或修订理由"><el-input id="evaluation-score-reason" v-model="reason" type="textarea" :rows="3" maxlength="2000" :disabled="disabled" /></el-form-item>
      <el-form-item><el-checkbox v-model="attested" :disabled="disabled">已核查完整单元、实际输出和依据；此记录是本人观察。</el-checkbox></el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
      <el-button native-type="submit" type="primary" :disabled="disabled || !cell">{{ latest ? '确认追加评分修订' : '确认追加评分' }}</el-button>
    </el-form>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import EvaluationBasisField from './evaluation-basis-field.vue'
import { evaluationScoreInputIssue } from './evaluation-input'
import type { Basis } from '@/api/medical/protocol'
import { evaluationCellKey, effectiveEvaluationScores, evaluationExpectation, evaluationFailureMessage, validateEvaluationScoreTarget } from '@/api/medical/evaluation-domain'
import type { EvaluationRead, EvaluationDimension, EvaluationCell, EvaluationAppend } from '@/api/medical/evaluation-domain'
const props = defineProps<{ view: EvaluationRead; actor: string; disabled: boolean }>()
const emit = defineEmits<{ submit: [body: EvaluationAppend] }>()
const sampleId = ref(''), candidateId = ref(''), dimension = ref<EvaluationDimension | ''>(''), repeatIndex = ref(0)
const status = ref<EvaluationAppend['status']>('UNKNOWN'), numerator = ref(''), unknownCount = ref(''), violationCount = ref(''), controlNumerator = ref('')
const outputId = ref(''), outputHash = ref(''), reason = ref(''), attested = ref(false), error = ref('')
const outputBasis = computed<Basis>({ get: () => ({ version_id: outputId.value, content_sha256: outputHash.value }), set: value => { outputId.value = value.version_id; outputHash.value = value.content_sha256 } })
const evidence = ref<(Basis & { localKey: number })[]>([]); let key = 0
const cell = computed<EvaluationCell | null>(() => sampleId.value && candidateId.value && dimension.value ? { sample_id: sampleId.value, agent_version_id: candidateId.value, dimension: dimension.value, repeat_index: repeatIndex.value } : null)
const sample = computed(() => props.view.definition.request.samples.find(item => item.sample_id === sampleId.value))
const expected = computed(() => cell.value ? evaluationExpectation(props.view.definition, cell.value) : undefined)
const effective = computed(() => effectiveEvaluationScores(props.view.definition, props.view.content_sha256, props.view.score_history))
const latest = computed(() => cell.value ? effective.value.get(evaluationCellKey(cell.value)) : undefined)
const fixedOutput = computed(() => props.view.score_history.find(score => score.observation.cell.sample_id === sampleId.value && score.observation.cell.agent_version_id === candidateId.value && score.observation.cell.repeat_index === repeatIndex.value && score.observation.output_ref)?.observation.output_ref ?? null)
function resetObservation(): void { status.value = 'UNKNOWN'; numerator.value = ''; unknownCount.value = ''; violationCount.value = ''; controlNumerator.value = ''; reason.value = ''; attested.value = false; error.value = ''; evidence.value = []; outputId.value = fixedOutput.value?.version_id ?? ''; outputHash.value = fixedOutput.value?.content_sha256 ?? '' }
watch([sampleId, candidateId, dimension, repeatIndex], resetObservation)
function addEvidence(): void { evidence.value.push({ localKey: key++, version_id: '', content_sha256: '' }) }
function submit(): void {
  if (props.disabled || !cell.value || !sample.value) return
  error.value = ''
  if (!attested.value) { error.value = '请确认已核查本人观察及全部依据'; return }
  if (status.value === 'SCORED' && !expected.value) { error.value = '缺少固定预期项目集，请登记未知或阻断'; return }
  const body: EvaluationAppend = { schema_version: '1.0.0', expected_revision: props.view.revision, definition_sha256: props.view.content_sha256, cell: cell.value, input_ref: sample.value.input_ref,
    output_ref: outputId.value || outputHash.value ? { version_id: outputId.value, content_sha256: outputHash.value } : null, evidence_refs: evidence.value.map(({ version_id, content_sha256 }) => ({ version_id, content_sha256 })),
    numerator: status.value === 'SCORED' ? numerator.value : null, denominator: status.value === 'SCORED' ? String(expected.value!.applicable_item_ids.length) : null,
    unknown_count: status.value === 'SCORED' ? unknownCount.value || null : null, violation_count: status.value === 'SCORED' ? violationCount.value || null : null,
    control_numerator: status.value === 'SCORED' && dimension.value === 'EVAL-11' && controlNumerator.value ? controlNumerator.value : null,
    control_denominator: status.value === 'SCORED' && dimension.value === 'EVAL-11' && controlNumerator.value ? String(expected.value!.control_item_ids.length) : null,
    status: status.value, supersedes: latest.value?.score_id ?? null, reason: reason.value }
  const issue = evaluationScoreInputIssue(body)
  if (issue) { error.value = issue; return }
  try { validateEvaluationScoreTarget(props.view, body, props.actor); emit('submit', body) } catch (failure) { error.value = evaluationFailureMessage(failure) }
}
function dirty(): boolean { return Boolean(sampleId.value || candidateId.value || dimension.value || reason.value || evidence.value.length || attested.value) }
defineExpose({ dirty, reset: resetObservation })
</script>
<style scoped>.evaluation-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; } p { overflow-wrap: anywhere; } @media (max-width: 860px) { .evaluation-fields { grid-template-columns: minmax(0, 1fr); } }</style>

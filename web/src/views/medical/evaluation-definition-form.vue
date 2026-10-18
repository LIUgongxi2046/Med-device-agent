<template>
<section aria-label="填写评测定义" class="definition-form">
<p>按字段登记已有样本和固定引用。表单不会创建输入制品、核准预期集或运行候选。</p>
<el-alert type="warning" :closable="false" title="登记前须准备真实存在的数据集、完整样本清单、预期集及授权用途依据。可读取不等于获准评测；服务端输入制品与用途校验未通过时，登记仍会受阻。" />
<el-form label-position="top" @submit.prevent="preview">
<h3>1. 范围与评测策略</h3><div class="fields"><el-form-item label="项目范围（留空为企业级）"><el-input v-if="disabled" :model-value="project || '企业级'" disabled/><project-scope-select v-else v-model="project" /></el-form-item><el-form-item label="来源环境声明"><el-select v-model="form.environment" :disabled="disabled"><el-option label="仿真来源，不用于业务验收" value="SIMULATION"/><el-option label="生产来源，仍需核验" value="PRODUCTION"/></el-select></el-form-item>
<el-form-item label="重复次数"><el-input-number v-model="form.variance_policy.repeat_count" :min="1" :max="100" :disabled="disabled"/></el-form-item><el-form-item label="置信区间方法（可留空）"><el-input v-model="confidence" maxlength="128" :disabled="disabled"/></el-form-item></div>
<el-form-item label="必需评分维度"><el-checkbox-group v-model="form.required_dimensions" :disabled="disabled"><el-checkbox v-for="dimension in dimensions" :key="dimension" :value="dimension">{{ dimension }}</el-checkbox></el-checkbox-group></el-form-item>
<p>固定策略：所有要求的重复观察均须满足。当前矩阵 {{ matrixSize }} / 10000 个单元。</p>
<h3>2. 数据与授权依据</h3>
<evaluation-basis-field v-model="form.dataset" label="数据集" kind="artifact" :project="project" :disabled="disabled"/>
<evaluation-basis-field v-model="form.sample_manifest_artifact" label="完整样本清单制品" kind="artifact" :project="project" :disabled="disabled"/>
<evaluation-basis-field v-model="form.rule_revision" label="评分规则固定修订" :disabled="disabled"/>
<evaluation-basis-field v-model="form.authorized_use_evidence" label="授权用途证据" kind="artifact" :project="project" :disabled="disabled"/>
<h3>3. 候选固定版本（最多 10 个）</h3>
<div v-for="(_, index) in form.candidates" :key="index"><evaluation-basis-field v-model="form.candidates[index]!" :label="'候选 ' + (index + 1)" kind="candidate" :project="project" :disabled="disabled"/><el-button :disabled="disabled || form.candidates.length <= 1" @click="form.candidates.splice(index, 1)">移除此候选</el-button></div>
<el-button :disabled="disabled || form.candidates.length >= 10" @click="form.candidates.push(basis())">添加候选</el-button>
<h3>4. 完整样本与预期项目（最多 100 个）</h3><p>样本、来源家族及引用必须与已有清单一致。改动后须使用与完整内容匹配的原制品摘要，不能沿用其他清单或子集摘要。</p>
<el-collapse><el-collapse-item v-for="(sample, index) in form.samples" :key="index" :title="'样本 ' + (index + 1) + ' · ' + (sample.document_type || '待填写')" :name="index">
<div class="fields"><el-form-item label="样本标识"><el-input v-model="sample.sample_id" :disabled="disabled"/></el-form-item><el-form-item label="来源家族标识"><el-input v-model="sample.source_family" :disabled="disabled"/></el-form-item><el-form-item label="文档类型"><el-input v-model="sample.document_type" maxlength="64" :disabled="disabled"/></el-form-item><el-form-item label="来源分组"><el-select v-model="sample.split" :disabled="disabled"><el-option label="开发" value="DEVELOPMENT"/><el-option label="调优" value="TUNING"/><el-option label="验收" value="ACCEPTANCE"/></el-select></el-form-item><el-form-item label="证据来源声明"><el-select v-model="sample.evidence_class" :disabled="disabled"><el-option label="仿真" value="SIMULATION"/><el-option label="真实来源，仍需核验" value="REAL"/></el-select></el-form-item></div>
<formal-evaluation-input-field v-if="form.environment === 'PRODUCTION'" v-model="sample.input_ref" :disabled="disabled" @document-type="sample.document_type = $event"/>
<evaluation-basis-field v-else v-model="sample.input_ref" label="已有仿真输入固定引用" kind="artifact" :project="project" :disabled="disabled"/>
<evaluation-basis-field v-model="sample.expected_ref" label="已有预期项目集固定引用" kind="artifact" :project="project" :disabled="disabled"/>
<div v-for="(expectation, expectationIndex) in sample.expectations" :key="expectationIndex" class="expectation"><el-form-item label="预期维度"><el-select v-model="expectation.dimension" :disabled="disabled" @change="expectation.control_item_ids = []"><el-option v-for="dimension in dimensions" :key="dimension" :label="dimension" :value="dimension"/></el-select></el-form-item><el-form-item label="适用项目标识（每行一个）"><el-input :model-value="expectation.applicable_item_ids.join('\n')" type="textarea" :disabled="disabled" @update:model-value="expectation.applicable_item_ids = lines($event)"/></el-form-item><el-form-item v-if="expectation.dimension === 'EVAL-11'" label="合法对照项目标识（每行一个）"><el-input :model-value="expectation.control_item_ids.join('\n')" type="textarea" :disabled="disabled" @update:model-value="expectation.control_item_ids = lines($event)"/></el-form-item><el-button :disabled="disabled" @click="sample.expectations.splice(expectationIndex, 1)">移除此维度预期</el-button></div>
<el-button :disabled="disabled || sample.expectations.length >= 12" @click="sample.expectations.push({ dimension: 'EVAL-01', applicable_item_ids: [], control_item_ids: [] })">添加预期维度</el-button><el-button type="danger" plain :disabled="disabled" @click="form.samples.splice(index, 1)">移除此样本</el-button>
</el-collapse-item></el-collapse>
<el-button :disabled="disabled || form.samples.length >= 100" @click="addSample">添加已有样本</el-button>
<p v-if="!form.samples.length">尚无样本。空样本定义不能形成业务验收通过结论。</p>
<el-form-item label="登记理由"><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="2000" :disabled="disabled"/></el-form-item>
<el-button native-type="submit" type="primary" :disabled="disabled || matrixSize > 10000">校验并核查固定定义</el-button>
</el-form>
</section>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { EvaluationCreate, EvaluationDimension } from '@/api/medical/evaluation-domain'
import ProjectScopeSelect from '@/components/Medical/ProjectScopeSelect.vue'
import EvaluationBasisField from './evaluation-basis-field.vue'
import FormalEvaluationInputField from './formal-evaluation-input-field.vue'
const props = defineProps<{ disabled: boolean }>()
const emit = defineEmits<{ preview: [value: EvaluationCreate]; change: [] }>()
const basis = () => ({ version_id: '', content_sha256: '' })
const project = ref(''), confidence = ref(''), changed = ref(false)
const dimensions = Array.from({ length: 12 }, (_, index) => ('EVAL-' + String(index + 1).padStart(2, '0')) as EvaluationDimension)
const form = reactive<EvaluationCreate>({ schema_version: '1.0.0', project_id: null, dataset: basis(), sample_manifest_artifact: basis(), samples: [], candidates: [basis()], rule_revision: basis(), required_dimensions: [], variance_policy: { repeat_count: 1, policy: 'ALL_REQUIRED_RUNS', confidence_interval_method: null }, authorized_use_evidence: basis(), environment: 'SIMULATION', reason: '' })
watch(() => form.environment, () => { for (const sample of form.samples) sample.input_ref = basis() }, { flush: 'sync' })
const matrixSize = computed(() => form.samples.length * form.candidates.length * form.required_dimensions.length * form.variance_policy.repeat_count)
function lines(value: string) { return value === '' ? [] : value.split('\n') }
function addSample() { form.samples.push({ sample_id: '', source_family: '', split: 'DEVELOPMENT', document_type: '', evidence_class: 'SIMULATION', input_ref: basis(), expected_ref: basis(), expectations: [] }) }
watch([form, project, confidence], () => { changed.value = true; emit('change') }, { deep: true, flush: 'sync' })
function preview() { if (!props.disabled) emit('preview', { ...JSON.parse(JSON.stringify(form)), project_id: project.value || null, variance_policy: { ...form.variance_policy, confidence_interval_method: confidence.value || null } }) }
defineExpose({ dirty: () => changed.value })
</script>
<style scoped>.definition-form{padding:20px;background:#fff;border:1px solid #DCE3EC;border-radius:8px}.fields{display:grid;grid-template-columns:1fr 1fr;gap:0 20px}.expectation{padding:16px;background:#F5F7FA;margin:12px 0}h3{margin-top:28px}p{color:#526279;line-height:1.6}.el-button{margin:8px 8px 8px 0}.el-select{width:100%}@media(max-width:700px){.fields{grid-template-columns:1fr}.definition-form{padding:12px}}</style>

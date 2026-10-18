<template>
  <section aria-label="固定评测定义核查" data-evaluation-definition>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="企业/项目范围">{{ definition.project_id ?? '企业级' }}</el-descriptions-item>
      <el-descriptions-item label="来源环境声明">{{ definition.environment === 'SIMULATION' ? '仿真来源' : '生产来源（尚未独立核验）' }}</el-descriptions-item>
      <el-descriptions-item label="固定矩阵">{{ definition.samples.length }} 个样本 × {{ definition.candidates.length }} 个候选 × {{ definition.required_dimensions.length }} 个维度 × {{ definition.variance_policy.repeat_count }} 次重复 = {{ cells }} 个单元</el-descriptions-item>
      <el-descriptions-item label="必需维度">{{ definition.required_dimensions.join('、') }}</el-descriptions-item>
      <el-descriptions-item label="方差策略">{{ definition.variance_policy.policy }}；置信区间方法：{{ definition.variance_policy.confidence_interval_method ?? '未定义' }}</el-descriptions-item>
      <el-descriptions-item label="登记理由">{{ definition.reason }}</el-descriptions-item>
    </el-descriptions>
    <p v-if="!definition.samples.length" role="status">尚未登记样本，结果将标记为证据不足。</p>
    <h2>完整样本与来源分组</h2>
    <el-table :data="definition.samples" row-key="sample_id" border max-height="480">
      <el-table-column prop="sample_id" label="样本标识" min-width="290" />
      <el-table-column prop="source_family" label="来源家族" min-width="290" />
      <el-table-column label="分组" min-width="150"><template #default="scope">{{ ({ DEVELOPMENT: '开发', TUNING: '调优', ACCEPTANCE: '验收' } as Record<string, string>)[scope.row.split] }}</template></el-table-column>
      <el-table-column prop="document_type" label="文档类型" min-width="150" />
      <el-table-column label="来源性质" width="130"><template #default="scope">{{ scope.row.evidence_class === 'SIMULATION' ? '仿真' : '真实来源声明' }}</template></el-table-column>
      <el-table-column v-if="approvedSamples" label="规则绑定" width="150"><template #default="scope">{{ approvedSamples.includes(scope.row.sample_id) ? '已绑定核准预期集' : '未绑定：证据不足' }}</template></el-table-column>
      <el-table-column type="expand"><template #default="scope"><el-descriptions :column="1" border><el-descriptions-item label="输入固定版本">{{ scope.row.input_ref.version_id }}</el-descriptions-item><el-descriptions-item label="输入摘要">{{ scope.row.input_ref.content_sha256 }}</el-descriptions-item><el-descriptions-item label="预期集固定版本">{{ scope.row.expected_ref.version_id }}</el-descriptions-item><el-descriptions-item label="预期集摘要">{{ scope.row.expected_ref.content_sha256 }}</el-descriptions-item></el-descriptions><el-table :data="scope.row.expectations" border><el-table-column prop="dimension" label="维度" width="110"/><el-table-column label="适用项目"><template #default="item">{{ item.row.applicable_item_ids.join('、') || '未提供适用项目' }}</template></el-table-column><el-table-column label="合法对照项目"><template #default="item">{{ item.row.control_item_ids.join('、') || '未提供对照项目' }}</template></el-table-column></el-table></template></el-table-column>
    </el-table>
    <h2>固定候选版本</h2>
    <el-table :data="definition.candidates" row-key="version_id" border><el-table-column prop="version_id" label="候选版本" min-width="290" /><el-table-column prop="content_sha256" label="内容SHA-256" min-width="400" /></el-table>
    <h2>数据集、完整清单与授权依据</h2>
    <el-table :data="references" border><el-table-column prop="label" label="引用用途" width="150" /><el-table-column prop="version_id" label="固定版本" min-width="290" /><el-table-column prop="content_sha256" label="SHA-256" min-width="400" /></el-table>
    <el-collapse><el-collapse-item title="核查完整固定定义"><pre>{{ full(definition) }}</pre></el-collapse-item></el-collapse>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { EvaluationCreate } from '@/api/medical/evaluation-domain'
const props = defineProps<{ definition: EvaluationCreate; approvedSamples?: string[] }>()
const cells = computed(() => props.definition.samples.length * props.definition.candidates.length * props.definition.required_dimensions.length * props.definition.variance_policy.repeat_count)
const references = computed(() => [
  { label: '数据集', ...props.definition.dataset }, { label: '完整样本清单', ...props.definition.sample_manifest_artifact },
  { label: '评分规则', ...props.definition.rule_revision }, { label: '授权用途证据', ...props.definition.authorized_use_evidence }
])
const full = (value: unknown): string => JSON.stringify(value, null, 2)
</script>
<style scoped>pre { white-space: pre-wrap; overflow-wrap: anywhere; padding: 12px; } .el-table { margin-bottom: 20px; }</style>

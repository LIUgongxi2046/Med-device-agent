<template>
  <section aria-label="章节与生成规则">
    <header class="chapter-summary-heading"><h2>章节与生成规则</h2><el-tag>{{ template.content.nodes.length }} 个章节</el-tag></header>
    <el-table :data="template.content.nodes" row-key="node_id" default-expand-all>
      <el-table-column label="章节" min-width="220"><template #default="{ row }"><strong>{{ row.official_number }} {{ row.title }}</strong><small>{{ row.required ? '必需章节' : '根据条件确认' }}</small></template></el-table-column>
      <el-table-column label="处理方式" min-width="160"><template #default="{ row }"><el-tag type="info">{{ modeLabels[row.content_mode] }}</el-tag></template></el-table-column>
      <el-table-column label="指定依据" min-width="160"><template #default="{ row }">{{ row.basis_refs.length ? row.basis_refs.length + ' 项固定版本依据' : '未指定章节依据' }}</template></el-table-column>
      <el-table-column label="检查要求" min-width="240"><template #default="{ row }">{{ row.content_requirements.join('；') || '查看字段与证据核验要求' }}</template></el-table-column>
      <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="selected = row">查看章节</el-button></template></el-table-column>
    </el-table>
    <el-drawer :model-value="selected !== null" title="章节配置与依据" size="min(760px,95vw)" @close="selected = null">
      <template v-if="selected"><h2>{{ selected.official_number }} {{ selected.title }}</h2><el-descriptions :column="1" border><el-descriptions-item label="处理方式">{{ modeLabels[selected.content_mode] }}</el-descriptions-item><el-descriptions-item label="适用范围">{{ selected.condition.mode === 'ALWAYS' ? '始终保留此章节' : '根据已核准规则及项目属性判断' }}</el-descriptions-item><el-descriptions-item label="章节要求">{{ selected.required ? '必需' : '根据适用条件确认' }}</el-descriptions-item><el-descriptions-item label="检查要求"><ul><li v-for="(item, index) in selected.content_requirements" :key="index">{{ item }}</li></ul></el-descriptions-item><el-descriptions-item label="原文定位">{{ selected.evidence_rule.require_original_locator ? '必须定位原始资料' : '查看模板完整定义' }}</el-descriptions-item><el-descriptions-item label="人工核查">{{ selected.evidence_rule.require_human_verification ? '需要人工核查' : '查看模板完整定义' }}</el-descriptions-item></el-descriptions>
      <h3>本章字段</h3><el-table :data="template.content.field_definitions.filter(item => item.node_id === selected!.node_id)" empty-text="当前章节没有字段定义"><el-table-column prop="name" label="字段" /><el-table-column prop="description" label="核验说明" /><el-table-column label="必需"><template #default="{ row }">{{ row.required ? '必需' : '根据条件确认' }}</template></el-table-column></el-table>
      <h3>指定依据</h3><el-table :data="selected.basis_refs" empty-text="当前章节未指定固定依据"><el-table-column prop="version_id" label="固定版本" min-width="230" /><el-table-column prop="content_sha256" label="内容摘要" min-width="250" /></el-table>
      <h3>检查规则</h3><el-table :data="ruleReferences" empty-text="当前章节未指定独立检查规则"><el-table-column label="规则版本" min-width="230"><template #default="{ row }"><router-link :to="'/rules/' + row.version_id">{{ row.version_id }}</router-link></template></el-table-column><el-table-column prop="content_sha256" label="内容摘要" min-width="250" /></el-table></template>
    </el-drawer>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ReviewTemplate, TemplateNode } from '@/api/medical/template-approval'
defineProps<{template:ReviewTemplate}>()
const selected=ref<TemplateNode|null>(null)
const modeLabels:Record<string,string>={DIRECT_REFERENCE:'指定资料取值',APPROVED_CALCULATION:'依据批准规则计算',NARRATIVE:'限定范围编写',AUTHORIZED_ORIGINAL_ONLY:'使用获授权原件',DIRECTORY:'组织章节目录'}
const ruleReferences=computed(()=>Array.isArray(selected.value?.evidence_rule.verification_rule_revisions)?selected.value!.evidence_rule.verification_rule_revisions:[])
</script>
<style scoped>
.chapter-summary-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin:0 0 20px}.chapter-summary-heading h2{font-size:17px;margin:0}.el-table small{display:block;margin-top:6px;font-size:12px;color:var(--el-text-color-secondary)}h3{margin-top:28px;font-size:15px}li{line-height:1.8}.el-drawer h2{font-size:20px;margin:0 0 20px}
</style>

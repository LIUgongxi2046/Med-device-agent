<template>
  <main class="medical-page">
    <h1>项目适用模板</h1>
    <session-panel />
    <router-link v-if="validProject" :to="'/projects/' + projectId">返回项目详情</router-link>
    <p v-if="!validProject" role="alert">项目标识未通过接口校验</p>
    <p v-else-if="!permitted">查询适用模板需要当前企业身份及项目读取权限</p>
    <el-form :inline="true" @submit.prevent="search">
      <el-form-item label="目标文档" :error="documentError">
        <el-select v-model="documentType" aria-label="目标文档" placeholder="请选择目标文档" :disabled="!permitted || loading || user.medicalWriting" @change="invalidateResults">
          <el-option v-for="(label, value) in taskDocumentLabels" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-button native-type="submit" :disabled="!permitted || loading || user.medicalWriting">查询适用模板</el-button>
    </el-form>
    <p v-if="loading" role="status">正在核查当前项目属性、核准模板及完整适用条件</p>
    <section v-if="project">
      <h2>{{ project.name }}</h2>
      <p>项目属性版本：{{ project.profile.profile_revision }}</p>
      <p>属性内容摘要：{{ project.profile.content_sha256 }}</p>
      <router-link v-if="project.allowed_actions.includes('profile.confirm') && ['PENDING', 'ACTIVE'].includes(project.status)" :to="'/projects/' + projectId + '/profile'">修订项目属性</router-link>
    </section>
    <section v-if="listing" data-applicability-results>
      <p>当前获授权模板数量：{{ listing.page_info.total }}</p>
      <p>待确认条件需要由注册人员核查；完整目录与附件要求保留。</p>
      <p v-if="listing.page_info.total === '0'" role="status">当前项目属性和授权范围没有兼容的核准模板</p>
      <el-table v-if="listing.missing_requirements.length" :data="listing.missing_requirements" row-key="requirement_id">
        <el-table-column prop="requirement_id" label="缺项标识" min-width="240" />
        <el-table-column label="缺项原因" min-width="180"><template #default="scope">{{ requirementReason(scope.row.reason_code) }}</template></el-table-column>
      </el-table>
      <el-table :data="listing.items" :row-key="(item: ApplicableTemplate) => item.template.template_version" empty-text="当前查询没有模板">
        <el-table-column prop="template.template_version" label="核准模板版本" min-width="280" />
        <el-table-column label="适用状态" min-width="120"><template #default="scope">{{ applicabilityLabels[scope.row.applicability as Applicability] }}</template></el-table-column>
        <el-table-column label="待确认要求" min-width="120"><template #default="scope">{{ scope.row.missing_requirements.length }}</template></el-table-column>
        <el-table-column prop="template.created_at" label="创建时间（UTC）" min-width="220" />
        <el-table-column label="完整定义" min-width="160"><template #default="scope"><el-button data-applicable-template link type="primary" @click="selected = scope.row">阅读模板与适用条件</el-button></template></el-table-column>
      </el-table>
      <pagination :total="Number(listing.page_info.total)" :page="listing.page_info.page" :limit="listing.page_info.page_size"
        :page-sizes="[10, 20, 50, 100]" :auto-scroll="false" @pagination="paginate" />
    </section>
    <el-dialog :model-value="selected !== null" title="模板完整定义与项目适用条件" width="90%" destroy-on-close @close="selected = null">
      <template v-if="selected">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="目标文档">{{ taskDocumentLabels[selected.template.content.document_type as DocumentType] }}</el-descriptions-item>
          <el-descriptions-item label="模板版本">{{ selected.template.template_version }}</el-descriptions-item>
          <el-descriptions-item label="核准状态">已核准</el-descriptions-item>
          <el-descriptions-item label="项目适用状态">{{ applicabilityLabels[selected.applicability] }}</el-descriptions-item>
          <el-descriptions-item label="内容摘要">{{ selected.template.content_sha256 }}</el-descriptions-item>
        </el-descriptions>
        <h2>完整适用条件</h2>
        <el-table :data="selected.conditions" :row-key="(item: TemplateConditionResult) => item.scope + ':' + item.target_id">
          <el-table-column label="条件对象" min-width="160"><template #default="scope">{{ conditionScopeLabels[scope.row.scope as TemplateConditionResult['scope']] }}</template></el-table-column>
          <el-table-column prop="target_id" label="对象标识" min-width="280" />
          <el-table-column label="判断结果" min-width="120"><template #default="scope">{{ applicabilityLabels[scope.row.status as Applicability] }}</template></el-table-column>
          <el-table-column label="待确认属性" min-width="220"><template #default="scope">{{ scope.row.unknown_input_keys.join('、') || '没有待确认属性' }}</template></el-table-column>
        </el-table>
        <h2>需要确认的要求</h2>
        <el-table :data="selected.missing_requirements" row-key="requirement_id" empty-text="当前没有待确认要求">
          <el-table-column prop="requirement_id" label="要求标识" min-width="240" />
          <el-table-column prop="node_id" label="目录节点" min-width="240" />
          <el-table-column prop="field_id" label="字段标识" min-width="240" />
          <el-table-column label="原因" min-width="180"><template #default="scope">{{ requirementReason(scope.row.reason_code) }}</template></el-table-column>
        </el-table>
        <h2>完整目录</h2>
        <el-table :data="selected.template.content.nodes" row-key="node_id">
          <el-table-column prop="official_number" label="官方编号" min-width="120" />
          <el-table-column prop="title" label="目录名称" min-width="220" />
          <el-table-column prop="parent_id" label="上级节点" min-width="240" />
          <el-table-column prop="ordinal" label="排列顺序" />
          <el-table-column label="必需节点"><template #default="scope">{{ scope.row.required ? '必需' : '按条件确认' }}</template></el-table-column>
          <el-table-column label="内容要求" min-width="280"><template #default="scope">{{ scope.row.content_requirements.join('；') }}</template></el-table-column>
        </el-table>
        <h2>结构化字段</h2>
        <el-table :data="selected.template.content.field_definitions" row-key="field_id">
          <el-table-column prop="name" label="字段名称" min-width="160" />
          <el-table-column prop="description" label="字段说明" min-width="240" />
          <el-table-column prop="value_type" label="字段类型" min-width="160" />
          <el-table-column label="约束与依据" min-width="280"><template #default="scope"><pre class="template-definition">{{ full({ constraints: scope.row.constraints, evidence_rule: scope.row.evidence_rule, basis_refs: scope.row.basis_refs }) }}</pre></template></el-table-column>
        </el-table>
        <h2>附件要求</h2>
        <el-table :data="selected.template.content.attachment_requirements" row-key="attachment_requirement_id" empty-text="当前模板没有附件要求">
          <el-table-column prop="name" label="附件名称" min-width="200" />
          <el-table-column prop="kind" label="附件种类" min-width="160" />
          <el-table-column prop="min_count" label="最少数量" />
          <el-table-column label="最多数量"><template #default="scope">{{ scope.row.max_count ?? '未设置上限' }}</template></el-table-column>
          <el-table-column label="文件格式" min-width="220"><template #default="scope">{{ scope.row.allowed_media_types.join('、') }}</template></el-table-column>
        </el-table>
        <p>附件仅接受获授权原件，需按实际资料完成核查。</p>
        <el-collapse>
          <el-collapse-item title="重复表格完整定义" name="repeats"><pre class="template-definition">{{ full(selected.template.content.repeat_definitions) }}</pre></el-collapse-item>
          <el-collapse-item title="跨文档规则与版式" name="rules"><pre class="template-definition">{{ full({ cross_document_rules: selected.template.content.cross_document_rules, format_profile: selected.template.content.format_profile }) }}</pre></el-collapse-item>
          <el-collapse-item title="固定依据和依赖" name="basis"><pre class="template-definition">{{ full({ basis_manifest: selected.template.content.basis_manifest, dependency_refs: selected.template.dependency_refs, base_version: selected.template.base_version }) }}</pre></el-collapse-item>
          <el-collapse-item title="完整模板及条件记录" name="complete"><pre class="template-definition">{{ full(selected) }}</pre></el-collapse-item>
        </el-collapse>
        <router-link v-if="templateApprovalAllowed(session, selected.template.template_version)" :to="'/templates/' + selected.template.template_version + '/approval'">打开模板人工审查</router-link>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import useUserStore from '@/store/modules/user'
import Pagination from '@/components/Pagination/index.vue'
import { schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession, Project } from '@/api/medical/protocol'
import { readProject } from '@/api/medical/projects'
import { projectActionAllowed } from '@/api/medical/project-write'
import { taskDocumentLabels, taskIdReference } from '@/api/medical/tasks'
import { templateApprovalAllowed } from '@/api/medical/template-approval'
import { applicabilityLabels, conditionScopeLabels, listApplicableTemplates } from '@/api/medical/template-applicability'
import type { Applicability, ApplicableTemplate, ApplicableTemplatePage, DocumentType, TemplateConditionResult, TemplateQuery } from '@/api/medical/template-applicability'
import SessionPanel from './session-panel.vue'

const route = useRoute(); const user = useUserStore()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const validProject = computed(() => schemaIssues(taskIdReference, projectId.value).length === 0)
const permitted = computed(() => validProject.value && projectActionAllowed(session.value, 'project.read', projectId.value))
const documentType = ref<DocumentType | ''>(''); const documentError = ref(''); const pageSize = ref(20)
const project = ref<Project | null>(null); const listing = ref<ApplicableTemplatePage | null>(null)
const selected = ref<ApplicableTemplate | null>(null); const loading = ref(false)
let generation = 0; let controller: AbortController | null = null
function full(value: unknown): string { return JSON.stringify(value, null, 2) }
function requirementReason(code: string): string {
  return code === 'APPLICABILITY_UNKNOWN' ? '适用条件需要确认' : code === 'SOURCE_MISSING' ? '缺少兼容的核准模板' : code
}
function invalidateResults(): void {
  generation += 1; controller?.abort(); controller = null; listing.value = null; selected.value = null
  loading.value = false; documentError.value = ''
}
function reset(): void { invalidateResults(); project.value = null; documentType.value = ''; pageSize.value = 20 }
async function load(page: number, previous: ApplicableTemplatePage | null): Promise<void> {
  const actual = session.value; const target = projectId.value; const type = documentType.value; const path = route.fullPath
  const boundProject = project.value
  invalidateResults()
  if (!actual?.current_context || !permitted.value || !type || document.hidden || user.medicalWriting) return
  const current = generation; const reading = new AbortController(); controller = reading; loading.value = true
  try {
    // 新查询重新读取项目；后续分页继续核查原属性版本与授权集合。
    const actualProject = previous && boundProject ? boundProject : await readProject(actual.current_context, target, reading.signal)
    if (current !== generation || session.value !== actual || route.fullPath !== path || document.hidden) return
    const query: TemplateQuery = { document_type: type, profile_revision: actualProject.profile.profile_revision, page, page_size: pageSize.value }
    if (previous) { query.dataset_watermark = previous.page_info.watermark; query.filter_sha256 = previous.page_info.filter_sha256 }
    const result = await listApplicableTemplates(actual, actualProject, query, previous, reading.signal)
    if (current !== generation || session.value !== actual || route.fullPath !== path || document.hidden) return
    project.value = actualProject; listing.value = result
  } finally { if (current === generation) loading.value = false }
}
async function search(): Promise<void> {
  if (loading.value || user.medicalWriting || !permitted.value) return
  if (!documentType.value) { invalidateResults(); documentError.value = '请选择需要查询的目标文档'; return }
  project.value = null; await load(1, null)
}
async function paginate(value: { page: number; limit: number }): Promise<void> {
  const previous = listing.value
  if (loading.value || user.medicalWriting || !previous) return
  if (value.limit !== pageSize.value || value.page === 1) { pageSize.value = value.limit; await load(1, null) }
  else await load(value.page, previous)
}
watch(() => [session.value, route.fullPath], reset, { immediate: true, flush: 'sync' })
function visibility(): void { if (document.hidden) reset() }
document.addEventListener('visibilitychange', visibility)
window.addEventListener('pagehide', reset); window.addEventListener('offline', reset); window.addEventListener('medical-failure', reset)
onBeforeUnmount(() => {
  reset(); document.removeEventListener('visibilitychange', visibility)
  window.removeEventListener('pagehide', reset); window.removeEventListener('offline', reset); window.removeEventListener('medical-failure', reset)
})
</script>

<style scoped>
.template-definition { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

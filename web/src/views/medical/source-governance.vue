<template>
  <main class="medical-page source-governance">
    <session-panel />
    <p v-if="loading" role="status">正在读取固定资料版本</p>
    <template v-if="view">
      <header class="reader-header">
        <div class="reader-heading">
          <div><p class="reader-eyebrow">固定来源 · 资料阅读</p><h1>{{ view.title }}</h1></div>
          <router-link to="/library" class="reader-back">返回资料中心</router-link>
        </div>
        <p class="fixed-version">来源版本 <span>{{ view.source_version }}</span></p>
        <div class="reader-statuses" aria-label="当前固定版本状态" aria-live="polite">
          <span>资料核准 <el-tag :type="selected?.approval_status === 'APPROVED' ? 'success' : 'warning'">{{ selected?.approval_status === 'PENDING' ? '待核准' : stateLabel(selected?.approval_status) }}</el-tag></span>
          <span>当前权利 <el-tag :type="view.rights_status === 'APPROVED' ? 'success' : 'warning'">{{ view.rights_status === 'PENDING' ? '待审查' : stateLabel(view.rights_status) }}</el-tag></span>
          <span>上传 <strong>{{ selected?.upload_status === 'PENDING' ? '待上传' : stateLabel(selected?.upload_status) }}</strong></span>
          <span>解析 <strong>{{ stateLabel(selected?.parse_status) }}</strong></span>
          <span>索引 <strong>{{ stateLabel(selected?.index_status) }}</strong></span>
          <span>生命周期 <strong>{{ lifecycleStatusLabels[view.lifecycle_state] }}</strong></span>
        </div>
      </header>
      <div class="source-reader-workspace">
        <div class="source-reader-main">
      <section class="content-panel">
        <div class="reading-panel-heading"><h2>原件与解析内容</h2><span>固定版本阅读</span></div>
        <label class="parse-selector">当前解析版本
          <el-select :model-value="selected?.parse_version" :disabled="writing || view.reviews.length === 0" placeholder="暂无处理版本" @change="chooseReview">
            <el-option v-for="review in view.reviews" :key="review.parse_version" :value="review.parse_version"
              :label="review.parse_version + ' · ' + stateLabel(review.approval_status)" />
          </el-select>
        </label>
        <p class="reader-hint">原件、解析和引用位置均绑定当前版本。读取原件后仍需人工判断其是否支持引用。</p>
        <el-tabs v-model="contentTab" @tab-change="clearContent">
          <el-tab-pane label="完整原件" name="PREVIEW">
            <source-original-reader v-if="user.medicalSession && contentTab === 'PREVIEW'" :session="user.medicalSession"
              :view="view" :review="selected" :active="!writing" />
          </el-tab-pane>
          <el-tab-pane label="解析内容" name="PARSED">
            <el-button :loading="contentLoading" :disabled="!selected || writing" @click="loadParsed()">读取固定解析版本</el-button>
            <el-collapse v-if="parsedContent" v-model="parsedExpanded" class="content-list">
              <el-collapse-item v-for="item in parsedContent.items" :key="item.chunk_id" :name="item.chunk_id">
                <template #title>序号 {{ item.ordinal }} · {{ item.node_kind }} · {{ item.chunk_id }}</template>
                <pre>{{ item.raw_text }}</pre>
                <el-descriptions :column="1" border>
                  <el-descriptions-item label="定位摘要">{{ item.locator_sha256 }}</el-descriptions-item>
                  <el-descriptions-item label="原文摘要">{{ item.quote_hash }}</el-descriptions-item>
                  <el-descriptions-item label="固定定位"><pre>{{ JSON.stringify(item.locator, null, 2) }}</pre></el-descriptions-item>
                </el-descriptions>
                <el-button :loading="contentLoading" :disabled="writing" @click="loadExact(item)">核验此定位</el-button>
              </el-collapse-item>
            </el-collapse>
            <el-empty v-if="parsedContent && parsedContent.items.length === 0" description="当前范围没有解析内容" />
            <div v-if="parsedContent" class="content-navigation">
              <el-button :disabled="parsedPages.length < 2 || contentLoading" @click="previousParsed">上一页</el-button>
              <el-button :disabled="parsedContent.next_after_ordinal === null || contentLoading" @click="nextParsed">下一页</el-button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="原件分段" name="ORIGINAL">
            <el-button :loading="contentLoading" :disabled="!selected || writing" @click="loadOriginal(originalStart)">读取原件分段</el-button>
            <template v-if="originalContent">
              <el-descriptions :column="2" border class="content-summary">
                <el-descriptions-item label="字节范围">{{ originalContent.start_byte }}—{{ originalContent.end_byte }}</el-descriptions-item>
                <el-descriptions-item label="总字节数">{{ originalContent.size_bytes }}</el-descriptions-item>
                <el-descriptions-item label="分段摘要">{{ originalContent.chunk_sha256 }}</el-descriptions-item>
                <el-descriptions-item label="原件摘要">{{ originalContent.full_sha256 }}</el-descriptions-item>
              </el-descriptions>
              <pre v-if="originalText !== null">{{ originalText }}</pre>
              <el-alert v-else title="当前媒体类型使用二进制分段" type="info" :closable="false" />
              <div class="content-navigation">
                <el-button :disabled="!hasPreviousOriginal || contentLoading" @click="previousOriginal">上一段</el-button>
                <el-button :disabled="originalContent.next_start_byte === null || contentLoading" @click="nextOriginal">下一段</el-button>
                <el-button :disabled="contentLoading" @click="downloadOriginal">保存当前分段</el-button>
              </div>
            </template>
          </el-tab-pane>
        </el-tabs>
      </section>

        </div>
        <aside class="source-inspector" aria-label="资料属性与引用关系">
          <el-tabs v-model="inspectorTab" stretch>
            <el-tab-pane label="资料属性" name="METADATA">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ view.title }}</el-descriptions-item>
        <el-descriptions-item label="归属">{{ view.ownership }}</el-descriptions-item>
        <el-descriptions-item label="来源版本">{{ view.source_version }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ view.owner_project_id ?? '未指定项目' }}</el-descriptions-item>
        <el-descriptions-item label="媒体类型">{{ view.media_type }}</el-descriptions-item>
        <el-descriptions-item label="字节数">{{ view.size_bytes }}</el-descriptions-item>
        <el-descriptions-item label="原件摘要">{{ view.original_sha256 }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ view.classification }}</el-descriptions-item>
        <el-descriptions-item label="权利版本">{{ view.rights_revision }}</el-descriptions-item>
        <el-descriptions-item label="权利状态">{{ view.rights_status }} / {{ view.rights_purpose }}</el-descriptions-item>
        <el-descriptions-item label="生命周期">{{ lifecycleStatusLabels[view.lifecycle_state] }}</el-descriptions-item>
        <el-descriptions-item label="生命周期修订">{{ view.lifecycle_revision }}</el-descriptions-item>
        <el-descriptions-item v-if="view.deletion_id" label="清理作业">{{ view.deletion_id }}</el-descriptions-item>
        <el-descriptions-item v-if="view.deletion_status" label="清理状态">{{ view.deletion_status }}</el-descriptions-item>
      </el-descriptions>

              <div class="inspector-links"><router-link :to="'/library/versions/' + view.source_version + '/rights'">查看权利审查</router-link></div>
              <dl v-if="selected" class="selected-review-facts">
                <dt>固定解析版本</dt><dd>{{ selected.parse_version }}</dd>
                <dt>固定索引版本</dt><dd>{{ selected.index_version ?? '未提供' }}</dd>
                <dt>已核验 / 预期数量</dt><dd>{{ selected.verified_count ?? '未提供' }} / {{ selected.expected_count ?? '未提供' }}</dd>
                <dt>资料核准修订</dt><dd>{{ selected.approval_revision ?? '未提供' }}</dd>
              </dl>
            </el-tab-pane>
            <el-tab-pane label="引用与影响" name="USAGES">
      <section v-if="mayReadUsages" class="usage-panel">
        <h2>资料引用与影响</h2>
        <div class="usage-filter">
          <el-select v-model="usageKind" clearable placeholder="全部关系" :disabled="usageLoading || writing" @change="clearUsages">
            <el-option v-for="item in usageKinds" :key="item" :value="item" :label="usageLabels[item]" />
          </el-select>
          <el-button :loading="usageLoading" :disabled="writing" @click="loadUsages(undefined, true)">查询引用</el-button>
        </div>
        <p v-if="!usagePage && !usageLoading" class="reader-hint">选择关系类型并查询，查看当前授权范围内的使用对象。</p>
        <div v-if="usagePage" class="usage-results">
          <el-empty v-if="usagePage.items.length === 0" :image-size="60" description="当前范围没有引用关系" />
          <article v-for="item in usagePage.items" :key="item.usage_key" class="usage-card">
            <span class="usage-kind">{{ usageLabel(item.usage_kind) }}</span>
            <h3>{{ item.title ?? '名称无权读取' }}</h3>
            <dl><dt>对象编号</dt><dd>{{ item.resource_id }}</dd><dt>项目编号</dt><dd>{{ item.project_id ?? '未提供' }}</dd>
              <dt>关系状态</dt><dd>{{ item.status }}</dd><dt>影响状态</dt><dd>{{ item.impact_status }}</dd></dl>
          </article>
        </div>
        <p v-if="usagePage">共 {{ usagePage.total }} 项；治理水位 {{ usagePage.dataset_watermark }}</p>
        <div v-if="usagePage" class="content-navigation">
          <el-button :disabled="usageCursors.length < 2 || usageLoading" @click="previousUsages">上一页</el-button>
          <el-button :disabled="usagePage.next_after === null || usageLoading" @click="nextUsages">下一页</el-button>
        </div>
      </section>
      <p v-else class="reader-hint">当前身份没有读取资料引用与影响的权限。</p>

            </el-tab-pane>
          </el-tabs>
        </aside>
      </div>
      <details class="governance-disclosure">
        <summary>资料版本记录 <span>查看历史固定版本</span></summary>
      <section class="version-panel">
        <h2>资料版本记录</h2>
        <el-table :data="view.versions" row-key="source_version">
          <el-table-column prop="version_no" label="版本编号" width="110" />
          <el-table-column prop="publisher_version" label="发布版本" min-width="150">
            <template #default="scope">{{ scope.row.publisher_version ?? '未提供' }}</template>
          </el-table-column>
          <el-table-column prop="created_at" label="建立时间" min-width="210" />
          <el-table-column prop="lifecycle" label="生命周期" width="130">
            <template #default="scope">{{ lifecycleStatusLabel(scope.row.lifecycle) }}</template>
          </el-table-column>
          <el-table-column prop="original_sha256" label="原件摘要" min-width="260" />
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-tag v-if="scope.row.selected">当前查看</el-tag>
              <router-link v-else :to="'/library/versions/' + scope.row.source_version">查看</router-link>
            </template>
          </el-table-column>
        </el-table>
      </section>

      </details>
      <details class="governance-disclosure">
        <summary>处理、核准与项目适用 <span>查看记录和人工决定</span></summary>
      <section class="lifecycle-panel">
        <h2>资料生命周期</h2>
        <el-form v-if="mayDecideLifecycle && lifecycleDecisions.length > 0" label-position="top" @submit.prevent="submitLifecycle">
          <el-form-item label="决定" :error="lifecycleErrors.decision">
            <el-select v-model="lifecycleDecision" :disabled="writing" placeholder="选择生命周期决定">
              <el-option v-for="item in lifecycleDecisions" :key="item" :value="item" :label="lifecycleDecisionLabels[item]" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="lifecycleDecision === 'PURGE'" label="保存策略版本" :error="lifecycleErrors.policy">
            <el-input v-model="retentionPolicyRevision" :disabled="writing" />
          </el-form-item>
          <el-form-item label="决定理由" :error="lifecycleErrors.reason">
            <el-input v-model="lifecycleReason" type="textarea" :rows="4" maxlength="2000" show-word-limit :disabled="writing" />
          </el-form-item>
          <el-checkbox v-model="lifecycleAttested" :disabled="writing">已核查当前状态、引用关系、原件和保存要求</el-checkbox>
          <p v-if="lifecycleErrors.attested" class="form-error">{{ lifecycleErrors.attested }}</p>
          <el-button type="primary" native-type="submit" :loading="writing">提交生命周期决定</el-button>
        </el-form>
        <p v-else-if="lifecycleDecisions.length === 0">当前状态没有可提交的生命周期决定。</p>
        <p v-else>当前身份没有资料生命周期维护权限。</p>
        <el-alert v-if="lifecycleReceipt" :title="lifecycleDecisionLabels[lifecycleReceipt.decision] + '已保存'"
          type="success" :closable="false"
          :description="lifecycleReceipt.deletion_id === null
            ? '生命周期修订：' + lifecycleReceipt.lifecycle_revision
            : '清理作业：' + lifecycleReceipt.deletion_id + '；待处理项目：' + lifecycleReceipt.deletion_item_count" />
        <template v-if="view.deletion_id && mayReadDeletion">
          <el-button :loading="deletionLoading" :disabled="writing" @click="loadDeletionJob">刷新清理作业</el-button>
          <el-descriptions v-if="deletionJob" :column="2" border class="deletion-summary">
            <el-descriptions-item label="作业状态">{{ deletionJob.status }}</el-descriptions-item>
            <el-descriptions-item label="作业修订">{{ deletionJob.revision }}</el-descriptions-item>
            <el-descriptions-item label="生命周期状态">{{ lifecycleStatusLabels[deletionJob.lifecycle_status] }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ deletionJob.updated_at }}</el-descriptions-item>
          </el-descriptions>
          <el-table v-if="deletionJob" :data="deletionJob.items" row-key="item_id" empty-text="清理作业没有存储项">
            <el-table-column prop="store_kind" label="存储类型" width="140" />
            <el-table-column prop="status" label="处理状态" width="130" />
            <el-table-column prop="backup_expiry" label="备份期限" min-width="210" />
            <el-table-column prop="revision" label="修订" width="100" />
            <el-table-column prop="target_sha256" label="目标摘要" min-width="260" />
          </el-table>
        </template>
      </section>

      <section class="processing-panel">
        <h2>重新处理资料</h2>
        <el-form v-if="mayProcess" label-position="top" @submit.prevent="submitProcessing">
          <el-form-item label="执行项目编号" :error="processingErrors.project">
            <el-input v-model="processingProjectId" :disabled="writing || view.owner_project_id !== null" />
          </el-form-item>
          <el-form-item label="处理配置版本" :error="processingErrors.config">
            <el-input v-model="processingConfigRevision" :disabled="writing" />
          </el-form-item>
          <el-form-item label="处理范围" :error="processingErrors.stage">
            <el-select v-model="processingStage" :disabled="writing">
              <el-option label="解析并建立索引" value="PARSE_AND_INDEX" />
              <el-option label="仅解析" value="PARSE_ONLY" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理原因" :error="processingErrors.reason">
            <el-input v-model="processingReason" type="textarea" :rows="4" maxlength="2000" show-word-limit :disabled="writing" />
          </el-form-item>
          <el-checkbox v-model="processingAttested" :disabled="writing">已核查原件、执行项目、处理配置和处理范围</el-checkbox>
          <p v-if="processingErrors.attested" class="form-error">{{ processingErrors.attested }}</p>
          <el-button type="primary" native-type="submit" :loading="writing">建立处理记录</el-button>
        </el-form>
        <p v-else>当前身份没有资料重新处理权限，或执行项目尚未填写。</p>
        <el-alert v-if="processingReceipt" title="处理记录已进入队列" type="success" :closable="false"
          :description="'处理记录：' + processingReceipt.attempt_id + '；解析版本：' + processingReceipt.parse_version" />
      </section>

      <router-link :to="'/library/versions/' + view.source_version + '/exact-fields'">读取与核准结构化字段</router-link>
      <source-index-builds v-if="user.medicalSession" :session="user.medicalSession" :source-id="view.source_id"
        :source-version="view.source_version" :active="!writing" />
      <h2>处理版本与审核记录</h2>
      <el-table :data="view.reviews" row-key="review_manifest_sha256" highlight-current-row
        @current-change="selectReview">
        <el-table-column prop="parse_version" label="解析版本" min-width="220" />
        <el-table-column prop="parse_status" label="解析" width="130" />
        <el-table-column prop="index_mode" label="索引模式" width="120" />
        <el-table-column prop="index_status" label="索引" width="120" />
        <el-table-column prop="approval_status" label="资料核准" width="130" />
        <el-table-column prop="gate_revision" label="治理修订" width="110" />
        <el-table-column label="完整数量" width="140">
          <template #default="scope">{{ scope.row.verified_count ?? '未提供' }}/{{ scope.row.expected_count ?? '未提供' }}</template>
        </el-table-column>
      </el-table>

      <section class="binding-panel">
        <h2>项目适用决定</h2>
        <el-table :data="view.bindings" row-key="project_id">
          <el-table-column prop="project_id" label="项目" min-width="240" />
          <el-table-column prop="applicability" label="适用状态" width="150" />
          <el-table-column prop="binding_revision" label="绑定版本" min-width="240" />
        </el-table>
        <el-form label-position="top" @submit.prevent="submitBinding">
          <el-form-item label="项目编号" :error="bindingErrors.project"><el-input v-model="projectId" :disabled="writing" /></el-form-item>
          <el-button :loading="projectLoading" :disabled="writing || !projectId" @click="loadProject">读取当前项目属性版本</el-button>
          <el-descriptions v-if="projectProfile" :column="1" border class="profile-summary">
            <el-descriptions-item label="项目名称">{{ projectProfile.name }}</el-descriptions-item>
            <el-descriptions-item label="属性版本">{{ projectProfile.profile.profile_revision }}</el-descriptions-item>
            <el-descriptions-item label="属性摘要">{{ projectProfile.profile.content_sha256 }}</el-descriptions-item>
            <el-descriptions-item label="当前绑定">{{ currentBinding?.binding_revision ?? '尚无当前绑定' }}</el-descriptions-item>
          </el-descriptions>
          <el-form-item label="适用状态" :error="bindingErrors.applicability">
            <el-select v-model="applicability" :disabled="writing"><el-option label="适用" value="APPLICABLE" /><el-option label="不适用" value="NOT_APPLICABLE" /><el-option label="条件未知" value="UNKNOWN" /></el-select>
          </el-form-item>
          <el-form-item label="决定理由" :error="bindingErrors.reason"><el-input v-model="bindingReason" type="textarea" :rows="4" maxlength="2000" show-word-limit :disabled="writing" /></el-form-item>
          <el-checkbox v-model="bindingAttested" :disabled="writing">已核查资料版本、项目属性版本和适用条件</el-checkbox>
          <p v-if="bindingErrors.attested" class="form-error">{{ bindingErrors.attested }}</p>
          <el-button type="primary" native-type="submit" :loading="writing" :disabled="!mayBind">保存项目适用决定</el-button>
        </el-form>
        <el-alert v-if="bindingReceipt" title="项目适用决定已保存" type="success" :closable="false"
          :description="'绑定版本：' + bindingReceipt.binding_revision" />
      </section>

      <section v-if="selected" class="review-panel">
        <h2>人工决定</h2>
        <p>审核清单摘要：{{ selected.review_manifest_sha256 }}</p>
        <pre>{{ JSON.stringify(selected.review_manifest, null, 2) }}</pre>
        <el-alert v-if="!ready" title="当前处理版本尚不满足核准条件" type="warning" :closable="false"
          description="核准需要原件已接收、解析完成、索引就绪、数量一致、权利有效且生命周期有效。" />
        <el-form v-if="mayApprove" label-position="top" @submit.prevent="submit">
          <el-form-item label="决定" :error="errors.decision">
            <el-select v-model="decision" placeholder="选择人工决定" :disabled="writing">
              <el-option v-for="item in decisions" :key="item" :label="labels[item]" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="理由" :error="errors.reason">
            <el-input v-model="reason" type="textarea" :rows="4" maxlength="2000" show-word-limit :disabled="writing" />
          </el-form-item>
          <el-checkbox v-model="attested" :disabled="writing">已核查原件、解析、索引、权利和审核清单</el-checkbox>
          <p v-if="errors.attested" class="form-error">{{ errors.attested }}</p>
          <el-button type="primary" native-type="submit" :loading="writing" :disabled="decisions.length === 0">提交决定</el-button>
        </el-form>
        <p v-else>当前身份可读取资料，没有资料核准权限。</p>
      </section>
      <el-alert v-if="receipt" :title="'已保存' + labels[receipt.decision]" type="success" :closable="false"
        :description="'核准版本：' + receipt.approval_revision + '；治理修订：' + receipt.gate_revision" />
      </details>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SessionPanel from './session-panel.vue'
import SourceIndexBuilds from './source-index-builds.vue'
import SourceOriginalReader from './source-original-reader.vue'
import useUserStore from '@/store/modules/user'
import { fail, validate } from '@/api/medical/protocol'
import { decideSource, decideSourceBinding, decideSourceLifecycle, readSourceContent, readSourceDeletionJob, readSourceGovernance, readSourceUsages, requestSourceProcessing, sourceApprovalAllowed, sourceBindingAllowed, sourceContentBytes, sourceLifecycleAllowed, sourceProcessingAllowed, sourcePurgeAllowed } from '@/api/medical/source-governance'
import type { OriginalSourceContent, ParsedContentItem, ParsedSourceContent, SourceApplicability, SourceBindingReceipt, SourceDecision, SourceDecisionReceipt, SourceDeletionJob, SourceGovernanceView, SourceLifecycleDecision, SourceLifecycleReceipt, SourceLifecycleStatus, SourceProcessingReceipt, SourceProcessingStage, SourceReview, SourceUsageKind, SourceUsagePage } from '@/api/medical/source-governance'
import { readProject } from '@/api/medical/projects'
import type { MedicalSession, Project } from '@/api/medical/protocol'

const user = useUserStore(); const route = useRoute(); const view = ref<SourceGovernanceView | null>(null)
const selected = ref<SourceReview | null>(null); const receipt = ref<SourceDecisionReceipt | null>(null)
const loading = ref(false); const decision = ref<SourceDecision | ''>(''); const reason = ref(''); const attested = ref(false)
const errors = reactive({ decision: '', reason: '', attested: '' }); let controller: AbortController | null = null; let generation = 0
const projectId = ref(''); const projectProfile = ref<Project | null>(null); const projectLoading = ref(false)
const applicability = ref<SourceApplicability | ''>(''); const bindingReason = ref(''); const bindingAttested = ref(false)
const bindingReceipt = ref<SourceBindingReceipt | null>(null)
const bindingErrors = reactive({ project: '', applicability: '', reason: '', attested: '' })
const processingProjectId = ref(''); const processingConfigRevision = ref('')
const processingStage = ref<SourceProcessingStage>('PARSE_AND_INDEX'); const processingReason = ref('')
const processingAttested = ref(false); const processingReceipt = ref<SourceProcessingReceipt | null>(null)
const processingErrors = reactive({ project: '', config: '', stage: '', reason: '', attested: '' })
const contentTab = ref<'PARSED' | 'ORIGINAL' | 'PREVIEW'>('PREVIEW'); const contentLoading = ref(false)
const parsedContent = ref<ParsedSourceContent | null>(null); const originalContent = ref<OriginalSourceContent | null>(null)
const parsedExpanded = ref<string[]>([])
const parsedPages = ref<(string | undefined)[]>([]); const originalStart = ref('0'); let contentController: AbortController | null = null
const usagePage = ref<SourceUsagePage | null>(null); const usageKind = ref<SourceUsageKind | ''>('')
const usageCursors = ref<(string | undefined)[]>([]); const usageLoading = ref(false); let usageController: AbortController | null = null
const lifecycleDecision = ref<SourceLifecycleDecision | ''>(''); const lifecycleReason = ref('')
const retentionPolicyRevision = ref(''); const lifecycleAttested = ref(false)
const lifecycleReceipt = ref<SourceLifecycleReceipt | null>(null)
const lifecycleErrors = reactive({ decision: '', policy: '', reason: '', attested: '' })
const deletionJob = ref<SourceDeletionJob | null>(null); const deletionLoading = ref(false)
let deletionController: AbortController | null = null
const inspectorTab = ref('METADATA')
const originalPageBytes = 262144n
const writing = computed(() => user.medicalWriting || loading.value)
const sourceVersion = computed(() => typeof route.params.sourceVersion === 'string' ? route.params.sourceVersion : '')
const mayApprove = computed(() => view.value !== null && sourceApprovalAllowed(user.medicalSession, view.value.source_id, view.value.owner_project_id))
const currentBinding = computed(() => view.value?.bindings.find(item => item.project_id === projectId.value) ?? null)
const mayBind = computed(() => projectProfile.value !== null && applicability.value !== ''
  && sourceBindingAllowed(user.medicalSession, projectProfile.value.project_id))
const mayProcess = computed(() => view.value !== null && processingProjectId.value !== ''
  && sourceProcessingAllowed(user.medicalSession, view.value.source_id, view.value.owner_project_id ?? processingProjectId.value))
const mayReadUsages = computed(() => view.value !== null
  && sourceProcessingAllowed(user.medicalSession, view.value.source_id, view.value.owner_project_id))
const mayDecideLifecycle = computed(() => view.value !== null
  && sourceLifecycleAllowed(user.medicalSession, view.value.source_id, view.value.owner_project_id))
const mayReadDeletion = computed(() => view.value !== null
  && sourcePurgeAllowed(user.medicalSession, view.value.source_id, view.value.owner_project_id))
const ready = computed(() => selected.value !== null && selected.value.upload_status === 'RECEIVED'
  && selected.value.parse_status === 'COMPLETE' && selected.value.index_status === 'READY'
  && selected.value.expected_count !== null && selected.value.verified_count === selected.value.expected_count
  && BigInt(selected.value.expected_count) > 0n && selected.value.lifecycle === 'ACTIVE' && view.value?.rights_status === 'APPROVED')
const decisions = computed<SourceDecision[]>(() => {
  if (!selected.value) return []
  if (selected.value.approval_status === 'DISABLED') return []
  const result: SourceDecision[] = ['RETURN', 'DISABLE']
  if (selected.value.approval_status === 'PENDING' && ready.value) result.unshift('APPROVE')
  return result
})
const labels: Record<SourceDecision, string> = { APPROVE: '核准', RETURN: '退回', DISABLE: '停用' }
const usageKinds: SourceUsageKind[] = ['PROJECT_BINDING', 'COLLECTION_MEMBER', 'SCOPE_SNAPSHOT', 'DOCUMENT_EVIDENCE', 'LINEAGE_DEPENDENCY', 'REFERENCE_HOLD']
const usageLabels: Record<SourceUsageKind, string> = {
  PROJECT_BINDING: '项目适用', COLLECTION_MEMBER: '资料集成员', SCOPE_SNAPSHOT: '任务范围',
  DOCUMENT_EVIDENCE: '文稿证据', LINEAGE_DEPENDENCY: '版本依赖', REFERENCE_HOLD: '保留限制'
}
const lifecycleDecisionLabels: Record<SourceLifecycleDecision, string> = {
  DISABLE: '停用', RECYCLE: '移入回收区', RESTORE: '恢复并重新核验', PURGE: '永久清理'
}
const lifecycleStatusLabels: Record<SourceLifecycleStatus, string> = {
  ACTIVE: '可用', DISABLED: '已停用', RECYCLED: '回收区', PURGE_PENDING: '清理中', PURGED: '已清理'
}
const lifecycleDecisions = computed<SourceLifecycleDecision[]>(() => {
  const status = view.value?.lifecycle_state
  if (status === 'ACTIVE') return ['DISABLE', 'RECYCLE']
  if (status === 'DISABLED') return ['RECYCLE']
  if (status === 'RECYCLED') return sourcePurgeAllowed(user.medicalSession, view.value?.source_id, view.value?.owner_project_id)
    ? ['RESTORE', 'PURGE'] : ['RESTORE']
  return []
})
const statusLabels: Record<string, string> = {
  PENDING: '待处理', APPROVED: '已核准', DISABLED: '已停用', REVOKED: '已撤销',
  UPLOADING: '上传中', RECEIVED: '已接收', QUEUED: '排队中', PROCESSING: '处理中',
  COMPLETE: '解析完成', PARTIAL_FAILED: '部分失败', FAILED: '失败', READY: '已就绪',
  BUILDING: '构建中', WITHDRAWN: '已撤回'
}
function stateLabel(value: string | null | undefined): string { return value == null ? '未提供' : statusLabels[value] ?? value }
function chooseReview(version: string): void { selectReview(view.value?.reviews.find(item => item.parse_version === version) ?? null) }
function usageLabel(value: SourceUsageKind): string { return usageLabels[value] }
function lifecycleStatusLabel(value: SourceLifecycleStatus): string { return lifecycleStatusLabels[value] }
const originalText = computed(() => {
  const value = originalContent.value
  if (!value || !(/^text\//.test(value.media_type) || /(?:json|xml|csv|markdown)/.test(value.media_type))) return null
  return new TextDecoder('utf-8', { fatal: true }).decode(sourceContentBytes(value))
})
const hasPreviousOriginal = computed(() => originalContent.value !== null && BigInt(originalContent.value.start_byte) > 0n)

function clear(): void {
  generation += 1; controller?.abort(); controller = null; usageController?.abort(); usageController = null
  deletionController?.abort(); deletionController = null; clearContent()
  contentTab.value = 'PREVIEW'; inspectorTab.value = 'METADATA'
  view.value = null; selected.value = null; receipt.value = null; usagePage.value = null; usageCursors.value = []; usageLoading.value = false
  loading.value = false; decision.value = ''; reason.value = ''; attested.value = false
  projectId.value = ''; projectProfile.value = null; projectLoading.value = false; applicability.value = ''
  bindingReason.value = ''; bindingAttested.value = false; bindingReceipt.value = null
  processingProjectId.value = ''; processingConfigRevision.value = ''; processingStage.value = 'PARSE_AND_INDEX'
  processingReason.value = ''; processingAttested.value = false; processingReceipt.value = null
  lifecycleDecision.value = ''; lifecycleReason.value = ''; retentionPolicyRevision.value = ''
  lifecycleAttested.value = false; lifecycleReceipt.value = null; deletionJob.value = null; deletionLoading.value = false
}
function clearContent(): void {
  contentController?.abort(); contentController = null; contentLoading.value = false
  parsedContent.value = null; parsedExpanded.value = []; originalContent.value = null; parsedPages.value = []; originalStart.value = '0'
}
function clearUsages(): void {
  usageController?.abort(); usageController = null; usageLoading.value = false; usagePage.value = null; usageCursors.value = []
}
async function read(): Promise<void> {
  if (user.medicalWriting) return
  clear(); const session: MedicalSession | null = user.medicalSession; const id = sourceVersion.value; const current = generation
  if (!session?.current_context || !id || document.hidden) return
  const reading = new AbortController(); controller = reading; loading.value = true
  let fixed: Pick<ParsedContentItem, 'chunk_id' | 'locator_sha256'> | null = null
  try {
    const result = await readSourceGovernance(session, id, reading.signal)
    if (current !== generation || user.medicalSession !== session || sourceVersion.value !== id || document.hidden) return
    view.value = result; selected.value = result.reviews[0] ?? null; processingProjectId.value = result.owner_project_id ?? ''
    const parseVersion = typeof route.query.parse_version === 'string' ? route.query.parse_version : null
    const chunkId = typeof route.query.chunk_id === 'string' ? route.query.chunk_id : null
    const locatorSha256 = typeof route.query.locator_sha256 === 'string' ? route.query.locator_sha256 : null
    if (parseVersion !== null || chunkId !== null || locatorSha256 !== null) {
      if (parseVersion === null || chunkId === null || locatorSha256 === null || !/^[a-f0-9]{64}$/.test(locatorSha256)) {
        fail('SCHEMA_INVALID', '检索命中位置参数不完整')
      }
      validate('urn:medical:templates:1.0.0#/$defs/Id', parseVersion)
      validate('urn:medical:templates:1.0.0#/$defs/Id', chunkId)
      const review = result.reviews.find(item => item.parse_version === parseVersion)
      if (!review) fail('SOURCE_MISSING', '检索命中的解析版本不可读取')
      contentTab.value = 'PARSED'; selected.value = review; fixed = { chunk_id: chunkId, locator_sha256: locatorSha256 }
    }
  } finally { if (current === generation && controller === reading) { loading.value = false; controller = null } }
  if (fixed !== null && current === generation && user.medicalSession === session && !document.hidden) await loadExact(fixed)
  if (view.value?.deletion_id && mayReadDeletion.value && current === generation) await loadDeletionJob()
}
async function loadDeletionJob(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const currentView = view.value
  if (!session?.current_context || !currentView?.deletion_id || !mayReadDeletion.value || writing.value || document.hidden) return
  deletionController?.abort(); const reading = new AbortController(); deletionController = reading; deletionLoading.value = true
  const current = generation
  try {
    const result = await readSourceDeletionJob(session, currentView.deletion_id, reading.signal)
    if (current !== generation || user.medicalSession !== session || view.value !== currentView || document.hidden) return
    if (result.source_version !== currentView.source_version || result.status !== currentView.deletion_status
        || result.lifecycle_status !== currentView.lifecycle_state || result.lifecycle_revision !== currentView.lifecycle_revision) {
      fail('CONTENT_MISMATCH', '清理作业与资料生命周期不一致')
    }
    deletionJob.value = result
  } finally { if (deletionController === reading) { deletionController = null; deletionLoading.value = false } }
}
async function loadUsages(after?: string, reset = false): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const currentView = view.value
  if (!session?.current_context || !currentView || !mayReadUsages.value || writing.value || document.hidden) return
  usageController?.abort(); const reading = new AbortController(); usageController = reading; usageLoading.value = true
  const current = generation; const watermark = reset ? undefined : usagePage.value?.dataset_watermark
  try {
    const result = await readSourceUsages(session, currentView.source_version, { limit: 20,
      ...(after === undefined ? {} : { after }), ...(usageKind.value === '' ? {} : { usage_kind: usageKind.value }),
      ...(watermark === undefined ? {} : { dataset_watermark: watermark }) }, reading.signal)
    if (current !== generation || user.medicalSession !== session || view.value !== currentView || document.hidden) return
    usagePage.value = result
    if (reset) usageCursors.value = [undefined]
  } finally { if (usageController === reading) { usageController = null; usageLoading.value = false } }
}
function nextUsages(): void {
  const next = usagePage.value?.next_after; if (next === null || next === undefined) return
  usageCursors.value.push(next); void loadUsages(next)
}
function previousUsages(): void {
  if (usageCursors.value.length < 2) return
  usageCursors.value.pop(); void loadUsages(usageCursors.value.at(-1))
}
function selectReview(value: SourceReview | null): void { selected.value = value; decision.value = ''; receipt.value = null; clearContent() }
async function loadParsed(after?: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const review = selected.value; const currentView = view.value
  if (!session?.current_context || !review || !currentView || writing.value || document.hidden) return
  contentController?.abort(); const reading = new AbortController(); contentController = reading; contentLoading.value = true
  const current = generation
  try {
    const result = await readSourceContent(session, currentView.source_version, { parse_version: review.parse_version,
      representation: 'PARSED', purpose: currentView.rights_purpose, ...(after === undefined ? {} : { after_ordinal: after }), limit: 20 }, reading.signal)
    if (result.representation !== 'PARSED') fail('CONTENT_MISMATCH', '解析内容表示方式不一致')
    if (current !== generation || user.medicalSession !== session || selected.value !== review || document.hidden) return
    parsedContent.value = result; parsedExpanded.value = []; originalContent.value = null
    if (parsedPages.value.length === 0 || parsedPages.value.at(-1) !== after) parsedPages.value.push(after)
  } finally { if (contentController === reading) { contentController = null; contentLoading.value = false } }
}
async function loadExact(item: Pick<ParsedContentItem, 'chunk_id' | 'locator_sha256'>): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const review = selected.value; const currentView = view.value
  if (!session?.current_context || !review || !currentView || writing.value || document.hidden) return
  contentController?.abort(); const reading = new AbortController(); contentController = reading; contentLoading.value = true
  const current = generation
  try {
    const result = await readSourceContent(session, currentView.source_version, { parse_version: review.parse_version,
      representation: 'PARSED', purpose: currentView.rights_purpose, chunk_id: item.chunk_id,
      locator_sha256: item.locator_sha256 }, reading.signal)
    if (result.representation !== 'PARSED' || result.items.length !== 1 || result.items[0]?.chunk_id !== item.chunk_id) {
      fail('CONTENT_MISMATCH', '固定定位核验结果不一致')
    }
    if (current !== generation || user.medicalSession !== session || selected.value !== review || document.hidden) return
    parsedContent.value = result; parsedExpanded.value = [item.chunk_id]; parsedPages.value = []
  } finally { if (contentController === reading) { contentController = null; contentLoading.value = false } }
}
function nextParsed(): void { const next = parsedContent.value?.next_after_ordinal; if (next !== null && next !== undefined) void loadParsed(next) }
function previousParsed(): void {
  if (parsedPages.value.length < 2) return
  parsedPages.value.pop(); const previous = parsedPages.value.pop(); void loadParsed(previous)
}
async function loadOriginal(start: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const review = selected.value; const currentView = view.value
  if (!session?.current_context || !review || !currentView || writing.value || document.hidden) return
  const first = BigInt(start); const end = first + originalPageBytes > BigInt(currentView.size_bytes)
    ? BigInt(currentView.size_bytes) : first + originalPageBytes
  contentController?.abort(); const reading = new AbortController(); contentController = reading; contentLoading.value = true
  const current = generation
  try {
    const result = await readSourceContent(session, currentView.source_version, { parse_version: review.parse_version,
      representation: 'ORIGINAL', purpose: currentView.rights_purpose, start_byte: first.toString(), end_byte: end.toString() }, reading.signal)
    if (result.representation !== 'ORIGINAL') fail('CONTENT_MISMATCH', '原件内容表示方式不一致')
    if (current !== generation || user.medicalSession !== session || selected.value !== review || document.hidden) return
    originalContent.value = result; originalStart.value = result.start_byte; parsedContent.value = null; parsedExpanded.value = []
  } finally { if (contentController === reading) { contentController = null; contentLoading.value = false } }
}
function nextOriginal(): void { const next = originalContent.value?.next_start_byte; if (next !== null && next !== undefined) void loadOriginal(next) }
function previousOriginal(): void {
  const current = originalContent.value; if (!current) return
  const previous = BigInt(current.start_byte) > originalPageBytes ? BigInt(current.start_byte) - originalPageBytes : 0n
  void loadOriginal(previous.toString())
}
function downloadOriginal(): void {
  const value = originalContent.value; if (!value) return
  const url = URL.createObjectURL(new Blob([sourceContentBytes(value).buffer], { type: value.media_type }))
  const link = document.createElement('a'); link.href = url
  link.download = 'source-' + value.source_version + '-' + value.start_byte + '-' + value.end_byte + '.part'
  link.click(); URL.revokeObjectURL(url)
}
async function submitProcessing(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const currentView = view.value
  const idPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
  processingErrors.project = idPattern.test(processingProjectId.value) ? '' : '请填写有效的执行项目编号'
  processingErrors.config = idPattern.test(processingConfigRevision.value) ? '' : '请填写有效的处理配置版本'
  processingErrors.stage = ['PARSE_ONLY', 'PARSE_AND_INDEX'].includes(processingStage.value) ? '' : '请选择处理范围'
  processingErrors.reason = processingReason.value.trim() !== processingReason.value
    || processingReason.value.normalize('NFC') !== processingReason.value
    || Array.from(processingReason.value).length < 1 || Array.from(processingReason.value).length > 2000
    ? '请填写1至2000字的规范原因' : ''
  processingErrors.attested = processingAttested.value ? '' : '请确认已经核查处理输入'
  if (!session?.current_context || !currentView || !mayProcess.value || writing.value || document.hidden
      || Object.values(processingErrors).some(Boolean)) return
  validate('urn:medical:templates:1.0.0#/$defs/Id', processingProjectId.value)
  validate('urn:medical:templates:1.0.0#/$defs/Id', processingConfigRevision.value)
  const current = generation; const id = currentView.source_version; user.medicalWriting = true
  try {
    const result = await requestSourceProcessing(session, currentView.source_id, id, { schema_version: '1.0.0',
      project_id: processingProjectId.value, config_revision: processingConfigRevision.value,
      stage: processingStage.value, reason: processingReason.value })
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== session.user.user_id
        || fresh.current_context.tenant_id !== session.current_context.tenant_id) return
    const actual = await readSourceGovernance(fresh, id, new AbortController().signal)
    const queued = actual.reviews.find(item => item.parse_version === result.parse_version)
    if (!queued || queued.parse_status !== 'QUEUED' || queued.approval_status !== 'PENDING'
        || queued.gate_revision !== '1') fail('CONTENT_MISMATCH', '处理记录读取结果不一致')
    if (current !== generation || sourceVersion.value !== id || user.medicalSession !== fresh || document.hidden) return
    view.value = actual; selected.value = queued; processingReceipt.value = result
    processingReason.value = ''; processingAttested.value = false; clearContent()
  } finally { user.medicalWriting = false }
}
async function submitLifecycle(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const currentView = view.value
  const idPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
  lifecycleErrors.decision = lifecycleDecision.value && lifecycleDecisions.value.includes(lifecycleDecision.value)
    ? '' : '请选择当前状态允许的决定'
  lifecycleErrors.policy = lifecycleDecision.value !== 'PURGE' || idPattern.test(retentionPolicyRevision.value)
    ? '' : '永久清理需要有效的保存策略版本'
  lifecycleErrors.reason = lifecycleReason.value.trim() !== lifecycleReason.value
    || lifecycleReason.value.normalize('NFC') !== lifecycleReason.value
    || Array.from(lifecycleReason.value).length < 1 || Array.from(lifecycleReason.value).length > 2000
    ? '请填写1至2000字的规范理由' : ''
  lifecycleErrors.attested = lifecycleAttested.value ? '' : '请确认已经核查生命周期条件'
  if (!session?.current_context || !currentView || !lifecycleDecision.value || !mayDecideLifecycle.value
      || writing.value || document.hidden || Object.values(lifecycleErrors).some(Boolean)) return
  const current = generation; const id = currentView.source_version; user.medicalWriting = true
  try {
    const result = await decideSourceLifecycle(session, currentView.source_id, id, {
      schema_version: '1.0.0', decision: lifecycleDecision.value, reason: lifecycleReason.value,
      policy_revision: lifecycleDecision.value === 'PURGE' ? retentionPolicyRevision.value : null,
      expected_revision: currentView.lifecycle_revision
    })
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== session.user.user_id
        || fresh.current_context.tenant_id !== session.current_context.tenant_id) return
    const actual = await readSourceGovernance(fresh, id, new AbortController().signal)
    if (actual.lifecycle_state !== result.current_status || actual.lifecycle_revision !== result.lifecycle_revision
        || actual.last_lifecycle_decision !== result.decision_id) fail('CONTENT_MISMATCH', '生命周期决定读取结果不一致')
    if (current !== generation || sourceVersion.value !== id || user.medicalSession !== fresh || document.hidden) return
    view.value = actual; selected.value = actual.reviews[0] ?? null; lifecycleReceipt.value = result
    lifecycleDecision.value = ''; lifecycleReason.value = ''; retentionPolicyRevision.value = ''; lifecycleAttested.value = false
    clearContent(); clearUsages()
    if (actual.deletion_id !== null) await loadDeletionJob()
  } finally { user.medicalWriting = false }
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const currentView = view.value; const review = selected.value
  if (!session?.current_context || !currentView || !review?.index_version || !mayApprove.value || writing.value || document.hidden) return
  errors.decision = decision.value && decisions.value.includes(decision.value) ? '' : '请选择当前状态允许的决定'
  errors.reason = reason.value.trim() !== reason.value || reason.value.normalize('NFC') !== reason.value
    || Array.from(reason.value).length < 1 || Array.from(reason.value).length > 2000 ? '请填写1至2000字的规范理由' : ''
  errors.attested = attested.value ? '' : '请确认已完成审核清单核查'
  if (errors.decision || errors.reason || errors.attested || !decision.value) return
  const id = sourceVersion.value; const current = generation; user.medicalWriting = true
  try {
    const result = await decideSource(session, id, { schema_version: '1.0.0', parse_version: review.parse_version,
      index_version: review.index_version, rights_revision: currentView.rights_revision, binding_revision: null,
      reviewed_manifest_sha256: review.review_manifest_sha256, decision: decision.value, reason: reason.value,
      expected_revision: review.gate_revision })
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== session.user.user_id || fresh.current_context.tenant_id !== session.current_context.tenant_id) return
    const actual = await readSourceGovernance(fresh, id, new AbortController().signal)
    const changed = actual.reviews.find(item => item.parse_version === result.parse_version && item.index_version === result.index_version)
    if (!changed || changed.approval_revision !== result.approval_revision || changed.gate_revision !== result.gate_revision
        || changed.approval_status !== result.approval_status) fail('CONTENT_MISMATCH', '资料核准结果读取不一致')
    if (current !== generation || sourceVersion.value !== id || user.medicalSession !== fresh || document.hidden) return
    view.value = actual; selected.value = changed; receipt.value = result; decision.value = ''; reason.value = ''; attested.value = false
  } finally { user.medicalWriting = false }
}
async function loadProject(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const id = projectId.value
  projectProfile.value = null; bindingReceipt.value = null; bindingErrors.project = ''
  if (!session?.current_context || writing.value || document.hidden) return
  projectLoading.value = true
  try {
    const result = await readProject(session.current_context, id, new AbortController().signal)
    if (user.medicalSession !== session || projectId.value !== id || document.hidden) return
    projectProfile.value = result; applicability.value = currentBinding.value?.applicability ?? ''
  } finally { projectLoading.value = false }
}
async function submitBinding(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const project = projectProfile.value; const currentView = view.value
  bindingErrors.project = project && project.project_id === projectId.value ? '' : '请读取当前项目属性版本'
  bindingErrors.applicability = applicability.value ? '' : '请选择适用状态'
  bindingErrors.reason = bindingReason.value.trim() !== bindingReason.value || bindingReason.value.normalize('NFC') !== bindingReason.value
    || Array.from(bindingReason.value).length < 1 || Array.from(bindingReason.value).length > 2000 ? '请填写1至2000字的规范理由' : ''
  bindingErrors.attested = bindingAttested.value ? '' : '请确认已经核查适用条件'
  if (!session?.current_context || !project || !currentView || !applicability.value || !mayBind.value || writing.value
      || bindingErrors.project || bindingErrors.applicability || bindingErrors.reason || bindingErrors.attested || document.hidden) return
  const current = generation; const targetProject = project.project_id; user.medicalWriting = true
  try {
    const result = await decideSourceBinding(session, targetProject, { schema_version: '1.0.0', source_version: currentView.source_version,
      profile_revision: project.profile.profile_revision, profile_sha256: project.profile.content_sha256,
      applicability: applicability.value, reason: bindingReason.value,
      expected_binding_revision: currentBinding.value?.binding_revision ?? null, approval_attested: true })
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== session.user.user_id || fresh.current_context.tenant_id !== session.current_context.tenant_id) return
    const actual = await readSourceGovernance(fresh, currentView.source_version, new AbortController().signal)
    const binding = actual.bindings.find(item => item.project_id === targetProject)
    if (!binding || binding.binding_revision !== result.binding_revision || binding.applicability !== result.applicability) {
      fail('CONTENT_MISMATCH', '项目适用决定读取结果不一致')
    }
    if (current !== generation || user.medicalSession !== fresh || document.hidden) return
    view.value = actual; bindingReceipt.value = result; bindingReason.value = ''; bindingAttested.value = false
  } finally { user.medicalWriting = false }
}
watch(() => [user.medicalSession, sourceVersion.value, route.query.parse_version, route.query.chunk_id, route.query.locator_sha256],
  read, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>

<style scoped>
.source-governance { container-type: inline-size; }
.reader-header { position: sticky; top: 0; z-index: 5; padding: 18px 20px; margin: 16px 0; background: var(--el-bg-color); border: 1px solid var(--el-border-color-light); border-radius: 12px; }
.reader-heading { display: flex; justify-content: space-between; align-items: center; gap: 20px; }
.reader-heading h1 { margin: 4px 0 12px; font-size: 24px; overflow-wrap: anywhere; }
.reader-eyebrow { margin: 0; color: var(--el-text-color-secondary); font-size: 12px; }
.reader-back { flex-shrink: 0; color: var(--el-color-primary); }
.fixed-version { margin: 0 0 14px; font-size: 12px; color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.fixed-version span { color: var(--el-text-color-regular); }
.reader-statuses { display: flex; flex-wrap: wrap; gap: 12px 20px; font-size: 12px; }
.reader-statuses > span { display: inline-flex; align-items: center; gap: 8px; }
.reader-statuses strong { font-weight: 500; }
.source-reader-workspace { display: grid; grid-template-columns: minmax(0, 1fr) 300px; align-items: start; gap: 20px; }
.source-reader-main, .source-inspector { min-width: 0; border: 1px solid var(--el-border-color-light); border-radius: 12px; background: var(--el-bg-color); }
.source-reader-main { padding: 20px; }
.source-inspector { padding: 12px 16px; max-height: calc(100vh - 200px); overflow: auto; }
.source-governance h2 { margin: 0 0 16px; font-size: 17px; }
.reading-panel-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
.reading-panel-heading > span, .reader-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; }
.parse-selector { display: grid; gap: 8px; font-size: 13px; }
.parse-selector .el-select { width: 100%; }
.source-inspector :deep(.el-descriptions__table) { table-layout: fixed; }
.source-inspector :deep(.el-descriptions__cell) { overflow-wrap: anywhere; }
.source-inspector :deep(.el-descriptions__label) { width: 85px; }
.usage-results { display: grid; gap: 12px; }
.usage-card { border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 12px; overflow-wrap: anywhere; }
.usage-kind { color: var(--el-text-color-secondary); font-size: 12px; }
.usage-card h3 { font-size: 14px; line-height: 1.6; margin: 6px 0; }
.usage-card dl { font-size: 12px; margin-bottom: 0; }
.usage-card dt { color: var(--el-text-color-secondary); margin-top: 8px; }
.usage-card dd { margin: 4px 0 0; }
.usage-panel > p { font-size: 12px; overflow-wrap: anywhere; }
.selected-review-facts { font-size: 12px; }
.selected-review-facts dt { color: var(--el-text-color-secondary); margin-top: 14px; }
.selected-review-facts dd { margin: 6px 0 0; overflow-wrap: anywhere; }
.inspector-links { margin-top: 16px; }
.inspector-links a { color: var(--el-color-primary); }
.governance-disclosure { border: 1px solid var(--el-border-color-light); border-radius: 10px; margin-top: 20px; padding: 18px 20px; }
.governance-disclosure summary { cursor: pointer; font-weight: 600; }
.governance-disclosure summary span { font-size: 12px; font-weight: 400; color: var(--el-text-color-secondary); margin-left: 12px; }
.governance-disclosure[open] summary { margin-bottom: 20px; }
@container (max-width: 900px) { .source-reader-workspace { grid-template-columns: minmax(0, 1fr); } .source-inspector { max-height: none; } }
@container (max-width: 620px) { .reader-header { position: static; padding: 14px; } .reader-heading { align-items: flex-start; } .reader-heading h1 { font-size: 20px; } .source-reader-main, .source-inspector, .governance-disclosure { padding: 12px; } .governance-disclosure summary span { display: block; margin: 8px 0 0; } }

.review-panel, .binding-panel, .processing-panel, .lifecycle-panel, .version-panel { margin-top: 24px; }
.content-panel, .usage-panel { min-width: 0; }
.review-panel .el-select { width: 100%; }
.binding-panel .el-select, .processing-panel .el-select, .lifecycle-panel .el-select { width: 100%; }
.binding-panel form, .processing-panel form, .lifecycle-panel form { margin-top: 16px; }
.profile-summary { margin: 16px 0; }
.deletion-summary { margin: 16px 0; }
pre { max-height: 320px; overflow: auto; padding: 12px; background: var(--el-fill-color-light); white-space: pre-wrap; overflow-wrap: anywhere; }
.content-list, .content-summary { margin-top: 16px; }
.content-navigation { display: flex; gap: 12px; margin-top: 16px; }
.usage-filter { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.usage-filter .el-select { width: 260px; }
.form-error { color: var(--el-color-danger); }
.review-panel .el-button, .processing-panel .el-button, .lifecycle-panel .el-button { margin-top: 16px; }
</style>

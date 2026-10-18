<template>
  <section class="upload-result" aria-label="固定上传处理结果">
    <h2>固定处理结果</h2>
    <div v-if="value.completion?.result_status" class="completion-outcome" role="status">
      <strong>{{ resultLabels[value.completion.result_status] }}</strong>
      <p v-if="value.completion.result_status === 'DUPLICATE'">服务端返回既有固定版本，请核对下方来源。不会把相同内容显示为新资料。</p>
      <p v-else-if="value.completion.result_status === 'REGISTERED'">来源已登记。解析、索引和人工核准请分别查看下方处理版本。</p>
      <p v-else>完成处理失败，请核查原操作结果与失败原因。</p>
    </div>
    <p>上传编号：{{ value.upload_id }}；修订：{{ value.upload_revision }}</p>
    <p>服务器接收：{{ value.received_size }} 字节；{{ uploadStatusLabels[value.upload_status] }}；{{ transferStatusLabels[value.transfer_state] }}</p>
    <section v-if="value.completion" aria-label="原上传完成操作">
      <h3>原上传完成操作</h3>
      <p>{{ value.completion.operation_id }}：{{ operationLabels[value.completion.operation_state] }}</p>
      <p v-if="value.completion.result_status">结果：{{ resultLabels[value.completion.result_status] }}</p>
      <p v-if="value.completion.error_code" role="alert">{{ value.completion.error_code }}：{{ reasonLabels[value.completion.reason!] }}</p>
      <p v-if="value.completion.registered_rights_revision">登记时权利版本：{{ value.completion.registered_rights_revision }}</p>
    </section>
    <p v-else>尚未提交上传完成命令</p>
    <section v-if="value.source" aria-label="上传关联固定来源">
      <h3>上传关联固定来源</h3>
      <p>来源编号：{{ value.source.source_id }}</p>
      <router-link :to="'/library/versions/' + value.source.source_version">读取固定来源 {{ value.source.source_version }}</router-link>
      <p>原件摘要：{{ value.source.original_sha256 }}</p>
      <p>当前权利版本：{{ value.source.current_rights.version_id }}；状态：{{ rightsLabels[value.source.rights_status] }}</p>
      <p>当前权利摘要：{{ value.source.current_rights.content_sha256 }}；生命周期：{{ lifecycleLabels[value.source.lifecycle] }}</p>
      <div class="review-results" aria-label="逐解析版本处理及核准结果">
        <p v-if="value.source.reviews.length === 0">当前来源尚无处理版本。</p>
        <article v-for="review in value.source.reviews" :key="reviewIdentity(review)" class="review-result">
          <h4>解析版本 {{ review.parse_version }}</h4>
          <div class="processing-statuses">
            <div><span>解析</span><strong>{{ parseLabels[review.parse_status] }}</strong></div>
            <div><span>索引</span><strong>{{ review.index_status ?? '尚未取得' }}</strong></div>
            <div><span>人工核准</span><strong>{{ approvalLabels[review.approval_status] }}</strong></div>
          </div>
          <p>固定索引版本：{{ review.index_version ?? '尚未建立' }}</p>
          <p>预期数量：{{ review.expected_count ?? '尚未取得' }}；已核验数量：{{ review.verified_count ?? '尚未取得' }}</p>
          <details><summary>固定版本与核准依据</summary>
            <p>索引模式：{{ review.index_mode ?? '尚未指定' }}</p>
            <p>核准版本：{{ review.approval_revision ?? '尚未建立核准版本' }}；门槛修订：{{ review.gate_revision }}</p>
            <p>当前决定：{{ review.current_decision ?? '尚未决定' }}；时间：{{ review.decided_at ?? '尚未决定' }}</p>
            <p>审查摘要：{{ review.review_manifest_sha256 }}</p>
            <details><summary>读取完整审查清单</summary><pre>{{ JSON.stringify(review.review_manifest, null, 2) }}</pre></details>
          </details>
        </article>
      </div>
    </section>
    <section v-if="value.attempt" aria-label="独立解析处理尝试">
      <h3>独立解析处理尝试</h3>
      <p>尝试编号：{{ value.attempt.attempt_id }}；运行编号：{{ value.attempt.run_id }}</p>
      <p>配置版本：{{ value.attempt.config_revision }}；{{ attemptLabels[value.attempt.status] }}；修订：{{ value.attempt.revision }}；更新：{{ value.attempt.updated_at }}</p>
      <details><summary>读取完整处理失败清单</summary><pre>{{ JSON.stringify(value.attempt.failure_manifest, null, 2) }}</pre></details>
    </section>
  </section>
</template>

<script setup lang="ts">
import { transferStatusLabels, uploadStatusLabels } from '@/api/medical/source-uploads'
import type { SourceUploadResult } from '@/api/medical/source-uploads'
import type { SourceReview } from '@/api/medical/source-governance'

defineProps<{ value: SourceUploadResult }>()
function reviewIdentity(value: SourceReview): string { return value.parse_version + ':' + value.index_version }
const operationLabels = { PROCESSING: '正在处理', COMPLETED: '操作完成', FAILED: '操作失败' }
const resultLabels = { REGISTERED: '固定来源已登记', DUPLICATE: '已核查既有固定来源', FAILED: '完成处理失败' }
const reasonLabels = { UPLOAD_EXPIRED: '原上传已过期', EXECUTION_DEADLINE: '执行期限已到', ATTEMPT_LIMIT: '已达到尝试次数限制', SOURCE_VALIDATION_FAILED: '来源核验未通过' }
const rightsLabels = { PENDING_REVIEW: '待审查', APPROVED: '已批准', REVOKED: '已撤销', EXPIRED: '已过期' }
const lifecycleLabels = { ACTIVE: '使用中', RECYCLED: '已回收', PURGE_PENDING: '等待永久清理', PURGED: '永久清理完成' }
const parseLabels = { QUEUED: '等待解析', PROCESSING: '正在解析', COMPLETE: '解析完成', PARTIAL_FAILED: '部分解析失败', FAILED: '解析失败' }
const approvalLabels = { PENDING: '待核准', APPROVED: '已核准', DISABLED: '已停用' }
const attemptLabels = { QUEUED: '等待执行', RUNNING: '正在执行', SUCCEEDED: '执行完成', FAILED: '执行失败', CANCELLED: '已取消' }
</script>

<style scoped>
.upload-result { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--el-border-color-light); overflow-wrap: anywhere; }
h2 { font-size: 18px; margin: 0 0 16px; }
h3 { font-size: 15px; margin-top: 24px; }
p { font-size: 13px; line-height: 1.7; }
a { color: var(--el-color-primary); }
.completion-outcome { padding: 16px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.completion-outcome p { margin-bottom: 0; }
.review-results { display: grid; gap: 12px; }
.review-result { padding: 16px; border: 1px solid var(--el-border-color-light); border-radius: 8px; }
h4 { font-size: 13px; margin: 0 0 14px; font-weight: 500; }
.processing-statuses { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.processing-statuses > div { display: grid; gap: 8px; }
.processing-statuses span { color: var(--el-text-color-secondary); font-size: 12px; }
.processing-statuses strong { font-weight: 500; font-size: 14px; }
summary { cursor: pointer; font-size: 13px; line-height: 1.7; }
details { margin-top: 12px; }
pre { max-height: 320px; overflow: auto; padding: 12px; background: var(--el-fill-color-light); white-space: pre-wrap; overflow-wrap: anywhere; }
@container (max-width: 480px) { .processing-statuses { grid-template-columns: minmax(0, 1fr); } }
</style>

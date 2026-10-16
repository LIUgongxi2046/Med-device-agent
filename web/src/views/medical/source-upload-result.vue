<template>
  <section aria-label="固定上传处理结果">
    <h2>固定上传处理结果</h2>
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
      <el-table :data="value.source.reviews" :row-key="reviewIdentity" aria-label="逐解析版本处理及核准结果">
        <el-table-column prop="parse_version" label="固定解析版本" />
        <el-table-column label="解析状态"><template #default="scope">{{ parseLabels[scope.row.parse_status as keyof typeof parseLabels] }}</template></el-table-column>
        <el-table-column label="固定索引版本"><template #default="scope">{{ scope.row.index_version ?? '尚未建立' }}</template></el-table-column>
        <el-table-column label="索引结果"><template #default="scope">{{ scope.row.index_mode ?? '尚未指定' }} · {{ scope.row.index_status ?? '尚未取得' }}；预期 {{ scope.row.expected_count ?? '尚未取得' }}；已核验 {{ scope.row.verified_count ?? '尚未取得' }}</template></el-table-column>
        <el-table-column label="人工核准"><template #default="scope">{{ approvalLabels[scope.row.approval_status as keyof typeof approvalLabels] }}；{{ scope.row.approval_revision ?? '尚未建立核准版本' }}；门槛修订 {{ scope.row.gate_revision }}</template></el-table-column>
        <el-table-column label="固定核准依据"><template #default="scope">
          <p>当前决定：{{ scope.row.current_decision ?? '尚未决定' }}；时间：{{ scope.row.decided_at ?? '尚未决定' }}</p>
          <p>审查摘要：{{ scope.row.review_manifest_sha256 }}</p>
          <details><summary>读取完整审查清单</summary><pre>{{ JSON.stringify(scope.row.review_manifest, null, 2) }}</pre></details>
        </template></el-table-column>
      </el-table>
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

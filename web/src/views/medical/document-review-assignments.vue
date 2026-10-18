<template>
  <section aria-label="文稿修改分派">
    <h4>待修改事项</h4>
    <p v-if="rows.length===0">本次真实回读没有文稿修改分派。</p>
    <article v-for="row in rows" :key="row.assignment.assignment_id" class="review-assignment">
      <strong>{{ row.effective_state==='OPEN'?'待责任人修改':row.effective_state==='REVISION_AVAILABLE'?'已有新正文版本':'已非当前处理分派' }}</strong>
      <p v-if="row.effective_state!=='OPEN'">当前正文或任务状态已经变化，并不表示已处理完成。</p><p>缺少当前检查有效性核验，暂不能重新提交正式审查。</p>
      <pre>{{ row.assignment.reason }}</pre>
      <p>责任人：{{ row.assignment.assignee_id }} · {{ row.assignment.created_at }}</p>
      <p>当前任务状态：{{ row.task_status }}</p>
      <details><summary>查看固定关联</summary><p>分派 {{ row.assignment.assignment_id }}</p><p>原正文 {{ row.assignment.document_revision }}；原任务版本 {{ row.assignment.task_revision }}</p><p>当前正文 {{ row.current_document_revision }}；当前任务版本 {{ row.current_task_revision }}</p></details>
    </article>
  </section>
</template>
<script setup lang="ts">
import type { ReviewAssignmentView } from '@/api/medical/document-review-return'
defineProps<{rows:ReviewAssignmentView[]}>()
</script>
<style scoped>.review-assignment{padding:12px 0;border-top:1px solid #E9EDF3}.review-assignment p{overflow-wrap:anywhere}.review-assignment pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;line-height:1.6}</style>

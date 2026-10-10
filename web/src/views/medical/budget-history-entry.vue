<template>
  <section aria-label="指定预算批准的完整定义" data-budget-history-entry>
    <h2>固定批准定义</h2>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="企业标识">{{ entry.tenant_id }}</el-descriptions-item>
      <el-descriptions-item label="预算范围">{{ entry.project_id ?? '本企业' }}</el-descriptions-item>
      <el-descriptions-item label="预算标识">{{ entry.budget_id }}</el-descriptions-item>
      <el-descriptions-item label="指定批准版本">{{ entry.budget_revision }}</el-descriptions-item>
      <el-descriptions-item label="当前批准版本">{{ entry.current_budget_revision }}</el-descriptions-item>
      <el-descriptions-item label="当前账户版本">{{ entry.account_revision }}</el-descriptions-item>
      <el-descriptions-item label="当前采用状态">{{ entry.is_current ? '当前批准' : '历史批准' }}</el-descriptions-item>
      <el-descriptions-item label="币种">{{ entry.currency }}</el-descriptions-item>
      <el-descriptions-item label="金额上限">{{ entry.approval.limits.amount }}</el-descriptions-item>
      <el-descriptions-item label="Token上限">{{ entry.approval.limits.tokens }}</el-descriptions-item>
      <el-descriptions-item label="调用次数上限">{{ entry.approval.limits.calls }}</el-descriptions-item>
      <el-descriptions-item label="研究轮次上限">{{ entry.approval.limits.research_rounds }}</el-descriptions-item>
      <el-descriptions-item label="修订轮次上限">{{ entry.approval.limits.revision_rounds }}</el-descriptions-item>
      <el-descriptions-item label="截止时间（UTC）">{{ entry.approval.limits.deadline_at }}</el-descriptions-item>
      <el-descriptions-item label="批准人">{{ entry.approver_id }}</el-descriptions-item>
      <el-descriptions-item label="批准时间（UTC）">{{ entry.approved_at }}</el-descriptions-item>
      <el-descriptions-item label="批准原因">{{ entry.approval.reason }}</el-descriptions-item>
      <el-descriptions-item label="批准依据版本">{{ entry.approval.approval_basis.version_id }}</el-descriptions-item>
      <el-descriptions-item label="批准依据内容摘要">{{ entry.approval.approval_basis.content_sha256 }}</el-descriptions-item>
      <el-descriptions-item label="授权记录">{{ entry.approval.authority_grant_id }}</el-descriptions-item>
      <el-descriptions-item label="授权版本">{{ entry.approval.authority_revision }}</el-descriptions-item>
      <el-descriptions-item label="原批准版本">
        <router-link v-if="entry.approval.based_on_budget_revision" :to="'/agents/budgets/' + entry.budget_id + '/revisions/' + entry.approval.based_on_budget_revision">{{ entry.approval.based_on_budget_revision }}</router-link>
        <span v-else>初始批准</span>
      </el-descriptions-item>
      <el-descriptions-item label="批准内容摘要">{{ entry.content_sha256 }}</el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script setup lang="ts">
import type { BudgetHistoryEntry } from '@/api/medical/budgets'
defineProps<{ entry: BudgetHistoryEntry }>()
</script>

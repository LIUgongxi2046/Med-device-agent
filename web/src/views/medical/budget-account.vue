<template>
  <section aria-label="当前预算账户与批准版本" data-budget-detail>
    <h2>当前预算账户</h2>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="预算标识">{{ account.budget_id }}</el-descriptions-item>
      <el-descriptions-item label="企业标识">{{ account.tenant_id }}</el-descriptions-item>
      <el-descriptions-item label="预算范围">{{ account.project_id === null ? '本企业' : '指定项目' }}</el-descriptions-item>
      <el-descriptions-item v-if="account.project_id !== null" label="项目标识">{{ account.project_id }}</el-descriptions-item>
      <el-descriptions-item label="账户版本">{{ account.revision }}</el-descriptions-item>
      <el-descriptions-item label="批准版本">{{ account.budget_revision }}</el-descriptions-item>
      <el-descriptions-item label="币种">{{ account.currency }}</el-descriptions-item>
      <el-descriptions-item label="已用金额">{{ account.spent }}</el-descriptions-item>
      <el-descriptions-item label="预留金额">{{ account.reserved }}</el-descriptions-item>
      <el-descriptions-item label="已用Token">{{ account.spent_tokens }}</el-descriptions-item>
      <el-descriptions-item label="预留Token">{{ account.reserved_tokens }}</el-descriptions-item>
      <el-descriptions-item label="已用调用次数">{{ account.spent_calls }}</el-descriptions-item>
      <el-descriptions-item label="预留调用次数">{{ account.reserved_calls }}</el-descriptions-item>
      <el-descriptions-item label="创建主体">{{ account.created_by }}</el-descriptions-item>
      <el-descriptions-item label="创建时间（UTC）">{{ account.created_at }}</el-descriptions-item>
      <el-descriptions-item label="更新时间（UTC）">{{ account.updated_at }}</el-descriptions-item>
    </el-descriptions>
    <h2>当前固定批准定义</h2>
    <p>当前批准版本保持完整原值。追加批准不会释放未知预留或自动重新启动运行。</p>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="金额上限">{{ account.approval.limits.amount }} {{ account.currency }}</el-descriptions-item>
      <el-descriptions-item label="Token上限">{{ account.approval.limits.tokens }}</el-descriptions-item>
      <el-descriptions-item label="调用次数上限">{{ account.approval.limits.calls }}</el-descriptions-item>
      <el-descriptions-item label="研究轮次上限">{{ account.approval.limits.research_rounds }}</el-descriptions-item>
      <el-descriptions-item label="修订轮次上限">{{ account.approval.limits.revision_rounds }}</el-descriptions-item>
      <el-descriptions-item label="截止时间（UTC）">{{ account.approval.limits.deadline_at }}</el-descriptions-item>
      <el-descriptions-item label="批准人">{{ account.approver_id }}</el-descriptions-item>
      <el-descriptions-item label="批准时间（UTC）">{{ account.approved_at }}</el-descriptions-item>
      <el-descriptions-item label="批准原因">{{ account.approval.reason }}</el-descriptions-item>
      <el-descriptions-item label="批准依据版本">{{ account.approval.approval_basis.version_id }}</el-descriptions-item>
      <el-descriptions-item label="批准依据内容摘要">{{ account.approval.approval_basis.content_sha256 }}</el-descriptions-item>
      <el-descriptions-item label="授权记录">{{ account.approval.authority_grant_id }}</el-descriptions-item>
      <el-descriptions-item label="授权版本">{{ account.approval.authority_revision }}</el-descriptions-item>
      <el-descriptions-item label="原批准版本">{{ account.approval.based_on_budget_revision ?? '初始批准' }}</el-descriptions-item>
      <el-descriptions-item label="当前批准内容摘要">{{ account.content_sha256 }}</el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script setup lang="ts">
import type { BudgetAccount } from '@/api/medical/budgets'
defineProps<{ account: BudgetAccount }>()
</script>

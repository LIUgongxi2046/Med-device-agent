<template>
  <main class="medical-page">
    <h1>预算详情</h1>
    <session-panel />
    <p v-if="!permitted">预算读取需要当前企业的明确授权</p>
    <p v-if="loading" role="status">正在核查当前预算读取许可与固定版本</p>
    <template v-if="account && user.medicalSession?.current_context">
      <router-link :to="'/agents/budgets/' + account.budget_id + '/revisions'">查看批准历史</router-link>
      <el-button v-if="account.allowed_actions.includes('budget.approve') && budgetActionAllowed(user.medicalSession, 'budget.approve', account)" :disabled="user.medicalWriting" @click="router.push('/agents/budgets/' + account.budget_id + '/decisions')">追加预算批准</el-button>
      <budget-account :account="account" />
    </template>
    <router-link to="/agents/budgets">返回模型预算</router-link>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { budgetActionAllowed, readBudget } from '@/api/medical/budgets'
import type { BudgetAccount as Account } from '@/api/medical/budgets'
import type { MedicalSession } from '@/api/medical/protocol'
import useUserStore from '@/store/modules/user'
import BudgetAccount from './budget-account.vue'
import SessionPanel from './session-panel.vue'

const user = useUserStore(); const route = useRoute(); const router = useRouter()
const account = ref<Account | null>(null); const loading = ref(false)
const id = computed(() => { if (typeof route.params.budgetId !== 'string') throw new Error('预算路由缺少实际目标'); return route.params.budgetId })
const permitted = computed(() => budgetActionAllowed(user.medicalSession, 'budget.read', { budget_id: id.value }))
let generation = 0; let controller: AbortController | null = null
function clear(): void { generation += 1; controller?.abort(); controller = null; account.value = null; loading.value = false }
async function read(): Promise<void> {
  clear(); const session: MedicalSession | null = user.medicalSession; const target = id.value
  if (!session?.current_context || !permitted.value || document.hidden || user.medicalWriting) return
  const current = generation; controller = new AbortController(); loading.value = true
  try {
    const value = await readBudget(session, target, controller.signal)
    if (current === generation && user.medicalSession === session && id.value === target) account.value = value
  } finally { if (current === generation) loading.value = false }
}
watch(() => [user.medicalSession, id.value], read, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>

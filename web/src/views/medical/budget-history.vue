<template>
  <main class="medical-page">
    <h1>{{ version === null ? '预算批准历史' : '预算批准版本' }}</h1>
    <session-panel />
    <p v-if="!permitted">预算历史读取需要当前企业的明确授权</p>
    <template v-else>
      <p>批准内容保持原版本和原依据。读取历史不改变当前预算，不释放预留，也不会启动运行。</p>
      <p v-if="loading" role="status">正在核查当前许可与固定批准版本</p>
      <template v-if="version === null">
        <el-form :inline="true" @submit.prevent="read()">
          <el-form-item label="每页数量" for="budget-history-limit">
            <el-select id="budget-history-limit" v-model="limit" :disabled="loading || user.medicalWriting">
              <el-option v-for="count in ['10', '20', '50', '100']" :key="count" :label="count" :value="count" />
            </el-select>
          </el-form-item>
          <el-button native-type="submit" :disabled="loading || user.medicalWriting">重新读取历史</el-button>
        </el-form>
        <template v-if="listing">
          <p>当前游标范围共有{{ listing.total }}个批准版本，按照版本标识排序。</p>
          <p>当前账户版本：{{ listing.account_revision }}；当前批准版本：{{ listing.current_budget_revision }}</p>
          <el-table :data="listing.approvals" row-key="budget_revision" data-budget-history-table>
            <el-table-column label="批准版本" min-width="300"><template #default="scope"><router-link :to="'/agents/budgets/' + id + '/revisions/' + scope.row.budget_revision">{{ scope.row.budget_revision }}</router-link></template></el-table-column>
            <el-table-column label="采用状态" min-width="120"><template #default="scope">{{ scope.row.is_current ? '当前批准' : '历史批准' }}</template></el-table-column>
            <el-table-column prop="approver_id" label="批准人" min-width="300" />
            <el-table-column prop="approved_at" label="批准时间（UTC）" min-width="250" />
            <el-table-column prop="currency" label="币种" min-width="90" />
            <el-table-column prop="approval.limits.amount" label="金额上限" min-width="180" />
            <el-table-column prop="content_sha256" label="批准内容摘要" min-width="500" />
          </el-table>
          <el-empty v-if="listing.approvals.length === 0" description="当前游标范围没有批准版本" />
          <el-button :disabled="loading || user.medicalWriting || !listing.next_after" @click="next">读取下一页</el-button>
        </template>
      </template>
      <budget-history-entry v-if="entry" :entry="entry" />
    </template>
    <p><router-link v-if="version !== null" :to="'/agents/budgets/' + id + '/revisions'">返回批准历史</router-link></p>
    <router-link :to="'/agents/budgets/' + id">返回当前预算账户</router-link>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { budgetActionAllowed, listBudgetHistory, readBudgetHistory } from '@/api/medical/budgets'
import type { BudgetHistoryEntry as Entry, BudgetHistoryListing } from '@/api/medical/budgets'
import type { MedicalSession } from '@/api/medical/protocol'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import BudgetHistoryEntry from './budget-history-entry.vue'

const route = useRoute(); const user = useUserStore()
const id = computed(() => { if (typeof route.params.budgetId !== 'string') throw new Error('预算历史路由缺少目标'); return route.params.budgetId })
const version = computed(() => {
  if (route.params.budgetRevision === undefined) return null
  if (typeof route.params.budgetRevision !== 'string') throw new Error('预算历史路由缺少指定版本')
  return route.params.budgetRevision
})
const permitted = computed(() => budgetActionAllowed(user.medicalSession, 'budget.read', { budget_id: id.value }))
const listing = ref<BudgetHistoryListing | null>(null); const entry = ref<Entry | null>(null)
const limit = ref('20'); const loading = ref(false)
let generation = 0; let controller: AbortController | null = null
function clear(): void { generation += 1; controller?.abort(); controller = null; listing.value = null; entry.value = null; loading.value = false }
async function read(after?: string): Promise<void> {
  clear(); const session: MedicalSession | null = user.medicalSession; const target = id.value; const selected = version.value
  if (!session?.current_context || !permitted.value || document.hidden || user.medicalWriting) return
  const current = generation; const signal = new AbortController(); controller = signal; loading.value = true
  try {
    if (selected === null) {
      const value = await listBudgetHistory(session, target, { limit: limit.value, ...(after ? { after } : {}) }, signal.signal)
      if (current === generation && user.medicalSession === session && id.value === target && version.value === selected && !document.hidden) listing.value = value
    } else {
      const value = await readBudgetHistory(session, target, selected, signal.signal)
      if (current === generation && user.medicalSession === session && id.value === target && version.value === selected && !document.hidden) entry.value = value
    }
  } finally { if (current === generation) loading.value = false }
}
function next(): Promise<void> | undefined { if (listing.value?.next_after) return read(listing.value.next_after) }
watch(() => [user.medicalSession, id.value, version.value, limit.value], () => read(), { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>

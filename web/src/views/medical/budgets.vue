<template>
  <main class="medical-page">
    <h1>模型预算</h1>
    <session-panel />
    <el-button :disabled="!budgetCreationAllowed(user.medicalSession) || user.medicalWriting" @click="router.push('/agents/budgets/new')">创建预算</el-button>
    <p v-if="!budgetActionAllowed(user.medicalSession, 'budget.read')">预算读取需要当前企业的明确授权</p>
    <template v-else>
      <el-form ref="formElement" :inline="true" @submit.prevent="search">
        <el-form-item label="项目标识" :error="projectError" for="budget-filter-project">
          <el-input id="budget-filter-project" v-model="projectId" clearable :disabled="loading || user.medicalWriting" />
        </el-form-item>
        <el-form-item label="每页数量" for="budget-filter-limit">
          <el-select id="budget-filter-limit" v-model="limit" :disabled="loading || user.medicalWriting">
            <el-option v-for="count in ['10', '20', '50', '100']" :key="count" :label="count" :value="count" />
          </el-select>
        </el-form-item>
        <el-button native-type="submit" :disabled="loading || user.medicalWriting">查询预算</el-button>
      </el-form>
      <p v-if="loading" role="status">正在读取当前获授权预算</p>
      <template v-if="listing">
        <p>当前筛选及游标范围内共有{{ listing.total }}个获授权预算账户</p>
        <el-table :data="listing.budgets" row-key="budget_id" data-budget-table>
          <el-table-column label="预算标识" min-width="300"><template #default="scope"><router-link :to="'/agents/budgets/' + scope.row.budget_id">{{ scope.row.budget_id }}</router-link></template></el-table-column>
          <el-table-column label="范围" min-width="300"><template #default="scope">{{ scope.row.project_id ?? '本企业' }}</template></el-table-column>
          <el-table-column prop="currency" label="币种" min-width="90" />
          <el-table-column prop="approval.limits.amount" label="批准金额上限" min-width="180" />
          <el-table-column prop="spent" label="已用金额" min-width="180" />
          <el-table-column prop="reserved" label="预留金额" min-width="180" />
          <el-table-column prop="revision" label="账户版本" min-width="120" />
          <el-table-column prop="approval.limits.deadline_at" label="截止时间（UTC）" min-width="250" />
        </el-table>
        <el-empty v-if="listing.budgets.length === 0" description="当前授权及筛选范围没有预算账户" />
        <el-button :disabled="loading || user.medicalWriting || !listing.next_after || changedFilters" @click="next">读取下一页</el-button>
        <p v-if="changedFilters">筛选条件已修改，请查询预算后继续翻页。</p>
      </template>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { budgetActionAllowed, budgetCreationAllowed, listBudgets } from '@/api/medical/budgets'
import type { BudgetListing } from '@/api/medical/budgets'
import { schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'

const router = useRouter(); const user = useUserStore()
const listing = ref<BudgetListing | null>(null); const loading = ref(false)
const projectId = ref(''); const projectError = ref(''); const limit = ref('20')
const applied = ref<{ project: string; limit: string } | null>(null)
const changedFilters = computed(() => applied.value !== null && (applied.value.project !== projectId.value || applied.value.limit !== limit.value))
const formElement = ref<{ $el: HTMLElement } | null>(null)
let generation = 0; let controller: AbortController | null = null
function clear(): void { generation += 1; controller?.abort(); controller = null; listing.value = null; loading.value = false; applied.value = null }
async function read(after?: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || document.hidden || user.medicalWriting || !budgetActionAllowed(session, 'budget.read')) return
  const project = projectId.value; const size = limit.value
  projectError.value = project && schemaIssues('urn:medical:budgets:1.0.0#/$defs/Id', project).length ? '请填写规范项目标识，或者清除项目筛选' : ''
  if (projectError.value) {
    await nextTick(); formElement.value?.$el.querySelector<HTMLElement>('.is-error input')?.focus(); return
  }
  clear(); const current = generation; controller = new AbortController(); loading.value = true
  try {
    const value = await listBudgets(session, { limit: size, ...(project ? { project_id: project } : {}), ...(after ? { after } : {}) }, controller.signal)
    if (generation !== current || user.medicalSession !== session) return
    listing.value = value; applied.value = { project, limit: size }
  } finally { if (generation === current) loading.value = false }
}
function search(): Promise<void> { return read() }
function next(): Promise<void> | undefined { if (!changedFilters.value && listing.value?.next_after) return read(listing.value.next_after) }
watch(() => user.medicalSession, async () => { clear(); projectId.value = ''; projectError.value = ''; limit.value = '20'; await read() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>

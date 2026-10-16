<template>
  <main class="medical-page source-processing">
    <h1>资料处理队列</h1>
    <p v-if="loading" role="status">正在读取当前处理记录</p>
    <session-panel />
    <nav class="actions" aria-label="资料处理导航">
      <router-link to="/library"><el-button>返回资料中心</el-button></router-link>
    </nav>
    <el-form :inline="true" aria-label="处理队列筛选" @submit.prevent="applyFilters">
      <el-form-item label="项目编号"><el-input v-model="filters.project_id" clearable /></el-form-item>
      <el-form-item label="处理状态">
        <el-select v-model="filters.status" clearable>
          <el-option label="排队中" value="QUEUED" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已完成" value="COMPLETE" />
          <el-option label="部分失败" value="PARTIAL_FAILED" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" native-type="submit" :loading="loading">查询</el-button></el-form-item>
    </el-form>
    <p v-if="page">共 {{ page.total }} 项当前可读取处理记录</p>
    <el-table v-if="page" :data="page.items" row-key="attempt_id">
      <el-table-column prop="title" label="资料" min-width="220">
        <template #default="scope">
          <router-link :to="'/library/versions/' + scope.row.source_version">{{ scope.row.title }}</router-link>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="任务状态" width="120" />
      <el-table-column prop="parse_status" label="解析状态" width="120" />
      <el-table-column prop="job_type" label="任务类型" width="110" />
      <el-table-column prop="project_id" label="项目编号" min-width="220" />
      <el-table-column prop="attempt_id" label="处理编号" min-width="220" />
      <el-table-column prop="run_id" label="运行编号" min-width="220" />
      <el-table-column prop="updated_at" label="更新时间" min-width="190" />
      <el-table-column label="请求范围" min-width="260">
        <template #default="scope"><code>{{ requestText(scope.row.request_manifest) }}</code></template>
      </el-table-column>
      <el-table-column label="失败信息" min-width="240">
        <template #default="scope"><code>{{ requestText(scope.row.failure_manifest) }}</code></template>
      </el-table-column>
    </el-table>
    <el-empty v-if="page && page.items.length === 0" description="当前条件没有处理记录" />
    <el-button v-if="page?.next_after" class="more" :loading="loading" @click="readMore">读取下一页</el-button>
  </main>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import SessionPanel from './session-panel.vue'
import useUserStore from '@/store/modules/user'
import { readSourceProcessingQueue } from '@/api/medical/source-governance'
import type { SourceProcessingQueuePage, SourceProcessingQueueQuery } from '@/api/medical/source-governance'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { useMedicalRouteRead } from '@/api/medical/route-read'

const user = useUserStore(); const page = ref<SourceProcessingQueuePage | null>(null); const loading = ref(false)
const filters = reactive({ project_id: '', status: '' }); let controller: AbortController | null = null; let generation = 0
const active = useMedicalRouteRead('MedicalSourceProcessing', clear)
function requestText(value: Record<string, unknown>): string { return JSON.stringify(value) }
function query(after?: string): SourceProcessingQueueQuery {
  const result: SourceProcessingQueueQuery = { limit: 50 }
  if (after) result.after = after
  if (filters.project_id) result.project_id = filters.project_id
  if (filters.status) result.status = filters.status as NonNullable<SourceProcessingQueueQuery['status']>
  return result
}
function clear(): void {
  generation += 1; controller?.abort(); controller = null; page.value = null; loading.value = false
  filters.project_id = ''; filters.status = ''
}
async function read(after?: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!active.value || !session?.current_context || document.hidden) { clear(); return }
  controller?.abort(); const reading = new AbortController(); controller = reading; const current = ++generation; loading.value = true
  try {
    const result = await readSourceProcessingQueue(session, query(after), reading.signal)
    if (current !== generation || !active.value || user.medicalSession !== session || document.hidden || reading.signal.aborted) return
    if (after && (result.next_after === after || result.items.some(item => page.value?.items.some(previous => previous.attempt_id === item.attempt_id)))) {
      fail('CONTENT_MISMATCH', '处理队列分页游标或条目没有前进')
    }
    if (after && page.value) page.value = { ...result, items: [...page.value.items, ...result.items] }; else page.value = result
  } finally {
    if (current === generation && controller === reading) { controller = null; loading.value = false }
  }
}
function applyFilters(): Promise<void> { return read() }
function readMore(): Promise<void> | void { if (page.value?.next_after && !loading.value) return read(page.value.next_after) }
watch(() => [user.medicalSession, active.value], () => { clear(); if (active.value) return read() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>

<style scoped>
.actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 20px 0; }
.source-processing .el-form { margin: 16px 0; }
.source-processing .el-input, .source-processing .el-select { width: 240px; }
.source-processing code { white-space: pre-wrap; overflow-wrap: anywhere; }
.more { margin-top: 16px; }
</style>

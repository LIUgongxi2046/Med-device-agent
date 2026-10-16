<template>
  <section aria-label="固定来源索引构建记录">
    <h2>索引构建记录</h2>
    <el-form label-position="top" @submit.prevent="read">
      <el-form-item label="执行项目编号"><el-input v-model="projectId" aria-label="索引执行项目编号" :disabled="loading || !active" /></el-form-item>
      <el-button native-type="submit" :loading="loading" :disabled="!mayRead">读取完整索引记录</el-button>
    </el-form>
    <p v-if="listing">项目：{{ listing.project_id }}；固定来源：{{ listing.source_version }}；共 {{ listing.total }} 项；清单摘要：{{ listing.listing_sha256 }}</p>
    <el-table v-if="listing" :data="listing.items" row-key="index_version" aria-label="索引构建完整清单">
      <el-table-column prop="parse_version" label="固定解析版本" /><el-table-column prop="index_version" label="固定索引版本" />
      <el-table-column prop="event_id" label="原事件编号" /><el-table-column prop="mode" label="索引模式" />
      <el-table-column label="构建状态"><template #default="scope">{{ buildLabels[scope.row.build_status as keyof typeof buildLabels] }}</template></el-table-column>
      <el-table-column label="当前索引状态"><template #default="scope">{{ indexLabels[scope.row.index_status as keyof typeof indexLabels] }}</template></el-table-column>
      <el-table-column label="投递状态"><template #default="scope">{{ deliveryLabels[scope.row.delivery_status as keyof typeof deliveryLabels] }}</template></el-table-column>
      <el-table-column label="完整数量"><template #default="scope">已核验 {{ scope.row.verified_count }}；预期 {{ scope.row.expected_count }}</template></el-table-column>
      <el-table-column prop="started_at" label="开始时间" /><el-table-column prop="execution_deadline" label="执行期限" />
      <el-table-column prop="updated_at" label="更新时间" /><el-table-column prop="revision" label="构建修订" />
      <el-table-column label="实际处理回执"><template #default="scope">
        <span v-if="scope.row.result === null">等待处理结果</span>
        <span v-else>{{ buildLabels[scope.row.result.status as 'READY' | 'FAILED'] }}；{{ scope.row.result.failure_code === null ? '核验通过' : 'INDEX_LIMIT：索引数量达到限制' }}</span>
      </template></el-table-column>
    </el-table>
    <el-empty v-if="listing?.items.length === 0" description="当前来源与项目没有索引构建记录" />
    <p>构建结果、当前索引状态和投递状态分别读取。已撤回索引保留原构建记录。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { MedicalSession } from '@/api/medical/protocol'
import { schemaIssues } from '@/api/medical/protocol'
import { readSourceIndexBuilds } from '@/api/medical/source-index'
import type { SourceIndexListing } from '@/api/medical/source-index-result'
import { useMedicalRouteRead } from '@/api/medical/route-read'

const props = defineProps<{ session: MedicalSession; sourceId: string; sourceVersion: string; active: boolean }>()
const projectId = ref(''); const listing = shallowRef<SourceIndexListing | null>(null); const loading = ref(false)
let generation = 0; let controller: AbortController | null = null
const routeActive = useMedicalRouteRead('MedicalSourceGovernance', () => { clear(); projectId.value = '' })
const mayRead = computed(() => routeActive.value && props.active && !loading.value && Boolean(props.session.current_context)
  && schemaIssues('urn:medical:templates:1.0.0#/$defs/Id', projectId.value).length === 0)
const buildLabels = { BUILDING: '正在构建', READY: '构建完成', FAILED: '构建失败' }
const indexLabels = { BUILDING: '正在构建', READY: '可读取', FAILED: '构建失败', WITHDRAWN: '已撤回' }
const deliveryLabels = { PENDING: '等待投递', DELIVERING: '正在投递', DELIVERED: '投递完成', FAILED: '投递失败' }
function clear(): void { generation += 1; controller?.abort(); controller = null; listing.value = null; loading.value = false }
async function read(): Promise<void> {
  if (!mayRead.value || document.hidden) return
  clear(); const current = generation; const reading = new AbortController(); controller = reading; loading.value = true
  const session = props.session, version = props.sourceVersion, project = projectId.value
  try {
    const result = await readSourceIndexBuilds(session, props.sourceId, version, project, reading.signal)
    if (current === generation && routeActive.value && props.active && props.session === session && props.sourceVersion === version
        && projectId.value === project && !reading.signal.aborted && !document.hidden) listing.value = result
  } finally { if (current === generation) { controller = null; loading.value = false } }
}
watch(projectId, clear, { flush: 'sync' })
watch(() => [props.session, props.sourceId, props.sourceVersion, props.active], () => { clear(); projectId.value = '' }, { flush: 'sync' })
function visibility(): void { if (document.hidden) { clear(); projectId.value = '' } }
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility) })
</script>

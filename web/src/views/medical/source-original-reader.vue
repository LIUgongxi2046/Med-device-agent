<template>
  <section aria-label="完整固定原件阅读">
    <div class="reader-actions">
      <el-button :disabled="!active || !review || loading" :loading="loading" @click="read">读取完整固定原件</el-button>
      <el-button v-if="loading" @click="clear">取消读取</el-button>
      <el-button v-if="blob" :disabled="!active || loading" @click="download">下载已核验完整原件</el-button>
    </div>
    <p v-if="loading" role="status">已核验 {{ received }} / {{ view.size_bytes }} 字节</p>
    <p v-if="blob">完整原件SHA-256：{{ view.original_sha256 }}</p>
    <source-file-preview v-if="blob" :blob="blob" :active="active" />
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { readCompleteSourceOriginal } from '@/api/medical/source-governance'
import type { SourceGovernanceView, SourceReview } from '@/api/medical/source-governance'
import type { MedicalSession } from '@/api/medical/protocol'
import SourceFilePreview from './source-file-preview.vue'

const props = defineProps<{ session: MedicalSession; view: SourceGovernanceView; review: SourceReview | null; active: boolean }>()
const blob = shallowRef<Blob | null>(null); const loading = ref(false); const received = ref('0')
let generation = 0; let controller: AbortController | null = null; let downloadUrl: string | null = null
function clear(): void {
  generation++; controller?.abort(); controller = null; blob.value = null; loading.value = false; received.value = '0'
  if (downloadUrl !== null) URL.revokeObjectURL(downloadUrl)
  downloadUrl = null
}
async function read(): Promise<void> {
  const review = props.review; if (!props.active || !review || loading.value || document.hidden) return
  clear(); const current = generation; const request = new AbortController(); controller = request; loading.value = true
  try {
    const result = await readCompleteSourceOriginal(props.session, props.view, review, request.signal,
      value => { if (current === generation) received.value = value })
    if (current === generation && props.active && !document.hidden) blob.value = result
  } finally { if (current === generation) { controller = null; loading.value = false } }
}
function download(): void {
  if (!props.active || blob.value === null || document.hidden) return
  if (downloadUrl !== null) URL.revokeObjectURL(downloadUrl)
  downloadUrl = URL.createObjectURL(blob.value)
  const link = document.createElement('a'); link.href = downloadUrl; link.download = props.view.title
  document.body.appendChild(link); link.click(); link.remove()
}
watch(() => [props.session, props.view.source_version, props.view.original_sha256, props.review?.parse_version, props.active], clear, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<style scoped>
.reader-actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
.reader-actions .el-button { margin-left: 0; }
</style>

<template>
  <section class="original-reader" aria-label="完整固定原件阅读" :aria-busy="loading">
    <div class="reader-actions">
      <el-button type="primary" :disabled="!active || !review || loading" :loading="loading" @click="read">{{ blob ? '重新读取固定原件' : '读取完整固定原件' }}</el-button>
      <el-button v-if="loading" @click="clear">取消读取</el-button>
      <el-button v-if="blob" :disabled="!active || loading" @click="download">下载已核验完整原件</el-button>
    </div>
    <p v-if="notice" role="alert">{{ notice }}</p>
    <div v-if="!blob" class="original-placeholder" role="status">
      <span class="original-mark" aria-hidden="true">原件</span>
      <template v-if="loading"><h3>正在核验完整原件</h3><p>已核验 {{ received }} / {{ view.size_bytes }} 字节</p></template>
      <template v-else-if="!review"><h3>暂无可选处理版本</h3><p>处理版本可用后，可读取其绑定的固定原件。</p></template>
      <template v-else><h3>原件尚未读取</h3><p>点击上方按钮读取 {{ view.title }}，逐段核验后展示完整文件。</p><p>{{ view.media_type }} · {{ view.size_bytes }} 字节</p></template>
    </div>
    <template v-if="blob">
      <div class="verified-banner" role="status"><el-tag type="success" size="small">原件摘要已核验</el-tag><span>固定来源版本 · {{ view.source_version }}</span></div>
      <source-file-preview :blob="blob" :active="active" />
      <details class="original-integrity"><summary>完整原件 SHA-256</summary><p>{{ view.original_sha256 }}</p></details>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { readCompleteSourceOriginal } from '@/api/medical/source-governance'
import type { SourceGovernanceView, SourceReview } from '@/api/medical/source-governance'
import type { MedicalSession } from '@/api/medical/protocol'
import SourceFilePreview from './source-file-preview.vue'

const props = defineProps<{ session: MedicalSession; view: SourceGovernanceView; review: SourceReview | null; active: boolean }>()
const blob = shallowRef<Blob | null>(null); const loading = ref(false); const received = ref('0'); const notice = ref('')
let generation = 0; let controller: AbortController | null = null; let downloadUrl: string | null = null
function clear(): void {
  generation++; controller?.abort(); controller = null; blob.value = null; loading.value = false; received.value = '0'; notice.value = ''
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
  } catch {
    if (current === generation && props.active && !document.hidden) notice.value = '原件读取未完成，请检查当前访问权限后重试。'
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
.original-reader { min-width: 0; }
.original-placeholder { min-height: 360px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 24px; background: var(--el-fill-color-extra-light); border: 1px dashed var(--el-border-color); border-radius: 10px; }
.original-mark { padding: 16px 12px; border: 1px solid var(--el-border-color); border-radius: 6px; font-size: 12px; color: var(--el-text-color-secondary); background: var(--el-bg-color); }
.original-placeholder h3 { margin: 20px 0 6px; font-size: 17px; }
.original-placeholder p { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; margin: 6px 0; overflow-wrap: anywhere; }
.verified-banner { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; padding: 12px 0; font-size: 12px; overflow-wrap: anywhere; }
.original-integrity { margin-top: 12px; color: var(--el-text-color-secondary); font-size: 12px; }
.original-integrity summary { cursor: pointer; }
.original-integrity p { overflow-wrap: anywhere; line-height: 1.6; }
.reader-actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
.reader-actions .el-button { margin-left: 0; }
</style>

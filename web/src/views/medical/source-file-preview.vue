<template>
  <section class="source-file-preview" aria-label="固定原件预览" :aria-busy="loading">
    <p v-if="loading" role="status">正在准备固定原件预览</p>
    <img v-if="imageUrl" :src="imageUrl" alt="已核验固定图片原件" class="original-image" />
    <p v-if="imageNotice" role="status">{{ imageNotice }}</p>
    <iframe v-if="pdfUrl" :src="pdfUrl" title="固定PDF原件" class="file-frame" />
    <iframe v-if="officeDocument" :srcdoc="officeDocument" sandbox="" title="Office原件转换预览" class="file-frame" />
    <div v-if="markdownHtml" class="markdown-content" v-html="markdownHtml" />
    <pre v-if="text !== null" class="text-content">{{ text }}</pre>
    <template v-if="sheets.length > 0">
      <el-select v-model="sheetIndex" aria-label="工作表" :disabled="loading">
        <el-option v-for="(sheet, index) in sheets" :key="index" :label="sheet.name" :value="index" />
      </el-select>
      <div v-if="selectedSheet" class="sheet-scroll">
        <table><caption>{{ selectedSheet.name }}</caption><tbody>
          <tr v-for="(row, rowIndex) in selectedSheet.rows" :key="rowIndex">
            <th scope="row">{{ rowIndex + 1 }}</th><td v-for="(cell, column) in row" :key="column">
              <span>{{ cell.text }}</span><code v-if="cell.formula !== null" class="original-formula">{{ cell.formula }}</code>
            </td>
          </tr>
        </tbody></table>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { fail } from '@/api/medical/protocol'
import { renderSourceMarkdown, sourcePreviewDocument } from '@/api/medical/content-rendering'

const props = defineProps<{ blob: Blob; active: boolean }>()
type Cell = { text: string; formula: string | null }
type Sheet = { name: string; rows: Cell[][] }
const imageUrl = ref(''); const imageNotice = ref('')
const loading = ref(false); const pdfUrl = ref(''); const officeDocument = ref('')
const markdownHtml = ref(''); const text = ref<string | null>(null); const sheets = ref<Sheet[]>([]); const sheetIndex = ref(0)
const selectedSheet = computed(() => sheets.value[sheetIndex.value])
let generation = 0
function clear(): void {
  generation++; if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''; imageNotice.value = ''
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
  pdfUrl.value = ''; officeDocument.value = ''; markdownHtml.value = ''; text.value = null
  sheets.value = []; sheetIndex.value = 0; loading.value = false
}
async function read(): Promise<void> {
  clear(); if (!props.active || document.hidden) return
  const current = generation; const blob = props.blob; loading.value = true
  const valid = () => current === generation && props.active && props.blob === blob && !document.hidden
  try {
    const type = blob.type.toLowerCase()
    if (['image/png', 'image/jpeg'].includes(type)) { imageUrl.value = URL.createObjectURL(blob); return }
    if (type === 'image/tiff') { imageNotice.value = 'TIFF 原件已核验，可下载查看；解析文字与原件坐标见解析内容。'; return }
    if (type === 'application/pdf') { pdfUrl.value = URL.createObjectURL(blob); return }
    if (['text/plain', 'text/markdown', 'text/x-markdown', 'text/csv'].includes(type)) {
      const original = new TextDecoder('utf-8', { fatal: true }).decode(await blob.arrayBuffer())
      if (!valid()) return
      if (type === 'text/csv') {
        const { default: papa } = await import('papaparse')
        const result = papa.parse<string[]>(original, { dynamicTyping: false, skipEmptyLines: false })
        if (result.errors.length > 0) fail('CONTENT_MISMATCH', 'CSV原件无法按登记格式完整读取')
        if (valid()) sheets.value = [{ name: 'CSV原件', rows: result.data.map(row => row.map(text => ({ text, formula: null }))) }]
      } else if (type.includes('markdown')) markdownHtml.value = renderSourceMarkdown(original)
      else text.value = original
      return
    }
    if (type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') {
      const { default: excel } = await import('exceljs'); const book = new excel.Workbook()
      await book.xlsx.load(await blob.arrayBuffer())
      if (!valid()) return
      sheets.value = book.worksheets.map(sheet => {
        const rows: Cell[][] = []
        for (let row = 1; row <= sheet.rowCount; row++) {
          rows.push(Array.from({ length: sheet.columnCount }, (_, column) => {
            const cell = sheet.getCell(row, column + 1)
            return { text: cell.text, formula: cell.type === excel.ValueType.Formula ? '=' + cell.formula : null }
          }))
        }
        return { name: sheet.name, rows }
      })
      return
    }
    const container = document.createElement('div')
    if (type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
      const { renderAsync } = await import('docx-preview')
      await renderAsync(await blob.arrayBuffer(), container, container, {
        useBase64URL: true, renderAltChunks: false, renderComments: false, debug: false })
    } else if (type === 'application/vnd.openxmlformats-officedocument.presentationml.presentation') {
      const { init } = await import('pptx-preview')
      const preview = init(container, { width: 960, height: 540, mode: 'list' })
      try { await preview.preview(await blob.arrayBuffer()); if (valid()) officeDocument.value = sourcePreviewDocument(container) }
      finally { for (const url of Object.values(preview.pptx.medias)) if (url.startsWith('blob:')) URL.revokeObjectURL(url); preview.destroy() }
      return
    } else fail('SCHEMA_INVALID', '当前原件媒体类型尚未提供阅读配置')
    if (valid()) officeDocument.value = sourcePreviewDocument(container)
    container.replaceChildren()
  } finally { if (current === generation) loading.value = false }
}
watch(() => [props.blob, props.active], read, { immediate: true })
onBeforeUnmount(clear)
</script>

<style scoped>
.original-image { max-width: 100%; max-height: 70vh; object-fit: contain; }
.source-file-preview { min-width: 0; }
.file-frame { display: block; background: var(--el-fill-color-light); border-radius: 6px; width: 100%; height: 65vh; min-height: 360px; border: 1px solid var(--el-border-color); }
.text-content { margin: 0; padding: 24px; max-height: 70vh; overflow: auto; border: 1px solid var(--el-border-color-light); border-radius: 6px; background: var(--el-bg-color); white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; line-height: 1.8; }
.sheet-scroll { overflow: auto; max-height: 65vh; max-width: 100%; margin-top: 12px; border: 1px solid var(--el-border-color-light); border-radius: 6px; }
.sheet-scroll th { position: sticky; left: 0; background: var(--el-fill-color-light); }
table { border-collapse: collapse; white-space: pre-wrap; }
td, th { padding: 8px 12px; border: 1px solid var(--el-border-color); min-width: 100px; }
.original-formula { display: block; white-space: pre-wrap; color: var(--el-text-color-secondary); }
.markdown-content { padding: 24px; max-height: 70vh; overflow: auto; border: 1px solid var(--el-border-color-light); border-radius: 6px; overflow-wrap: anywhere; line-height: 1.8; }
.markdown-content :deep(img) { max-width: 100%; height: auto; }
.markdown-content :deep(table) { display: block; overflow: auto; }
.markdown-content :deep(pre) { overflow: auto; }
</style>

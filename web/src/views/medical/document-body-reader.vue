<template>
  <section v-if="active" class="document-body-reader" aria-label="全文阅读与选区">
    <nav aria-label="正文目录">
      <h4>正文目录</h4>
      <ul>
        <li v-for="entry in outline" :key="entry.node.node_id" :style="{ marginInlineStart: entry.depth * 16 + 'px' }">
          <el-button link @click="locate(entry.node.node_id)">{{ title(entry.node) }}</el-button>
        </li>
      </ul>
    </nav>
    <section>
      <p>固定正文版本：{{ documentRevision }}</p>
      <p v-if="dirty" role="status">当前选区包含未保存内容，只能用于提问与修订建议；保存正文以后重新选择才能提交核验或审查。</p>
      <el-button :disabled="selecting" @mousedown.prevent @click="capture">保留当前文字选区</el-button>
      <el-button :disabled="selecting" @click="selectEntireText">选择全文文字</el-button>
      <el-button :disabled="selecting || selection === null" @click="clear">清除正文选区</el-button>
      <details>
        <summary>使用键盘选择文字范围</summary>
        <el-form label-position="top" @submit.prevent="selectByOffsets">
          <el-form-item label="文字节点"><el-select v-model="textNodeId" aria-label="文字节点">
            <el-option v-for="entry in texts" :key="entry.node.node_id" :value="entry.node.node_id" :label="title(entry.node)" />
          </el-select></el-form-item>
          <el-form-item label="起始字符位置（从零开始）"><el-input-number v-model="start" :min="0" :precision="0" aria-label="起始字符位置" /></el-form-item>
          <el-form-item label="结束字符位置（不包含该位置）"><el-input-number v-model="end" :min="1" :precision="0" aria-label="结束字符位置" /></el-form-item>
          <el-button native-type="submit" :disabled="!textNodeId || selecting">确认文字范围</el-button>
        </el-form>
      </details>
      <p v-if="selecting" role="status">正在核查选区与原文摘要</p>
      <section v-if="selection" aria-label="当前正文选区" data-document-selection>
        <p>{{ dirty ? '未保存正文选区' : '已保存正文选区' }} · {{ selection.segments.length }} 个节点</p>
        <pre>{{ JSON.stringify(selection, null, 2) }}</pre>
      </section>
      <div ref="contents" class="reader-content">
        <document-node-editor v-for="entry in roots" :key="selectionEpoch + ':' + entry.node.node_id" :node="entry.node" :body="body" readonly selection-mode @selected="selectRequests([$event])" />
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { DocumentBody, Node, Selection } from '../../../../contracts/typescript/src/interfaces'
import { browserTextSelection, createDocumentSelection, orderedDocumentNodes } from '@/api/medical/document-selection'
import type { SelectionRequest } from '@/api/medical/document-selection'
import { fail } from '@/api/medical/protocol'
import DocumentNodeEditor from './document-node-editor.vue'

const props = defineProps<{ body: DocumentBody; documentRevision: string; active: boolean; dirty: boolean }>()
const emit = defineEmits<{ selected: [selection: Selection | null, dirty: boolean] }>()
const contents = ref<HTMLElement>(); const selection = shallowRef<Selection | null>(null); const selecting = ref(false)
const selectionEpoch = ref(0)
const textNodeId = ref(''); const start = ref(0); const end = ref(1)
const nodes = computed(() => orderedDocumentNodes(props.body)); const roots = computed(() => nodes.value.filter(entry => entry.depth === 0))
const texts = computed(() => nodes.value.filter(entry => entry.node.content.kind === 'TEXT'))
const outline = computed(() => nodes.value.filter(entry => ['DOCUMENT', 'SECTION', 'PARAGRAPH', 'TABLE', 'IMAGE', 'FORMULA'].includes(entry.node.content.kind)))
let generation = 0; let controller: AbortController | null = null
function title(node: Node): string {
  const content = node.content
  if (content.kind === 'TEXT') return content.text.slice(0, 60) || '空文字节点'
  if (content.kind === 'IMAGE') return content.alt_text || '图片节点'
  if (content.kind === 'FORMULA') return content.description || '公式节点'
  if (content.kind === 'TABLE') return content.caption || '表格节点'
  return content.title || '未填写标题'
}
function clearSelection(): void {
  generation += 1; controller?.abort(); controller = null; selection.value = null; selecting.value = false; emit('selected', null, props.dirty)
}
function clear(): void { clearSelection(); selectionEpoch.value += 1; textNodeId.value = ''; start.value = 0; end.value = 1 }
function locate(id: string): void {
  const element = Array.from(contents.value?.querySelectorAll<HTMLElement>('[data-node-id]') ?? []).find(item => item.dataset.nodeId === id)
  if (!element) fail('SELECTION_STALE', '当前目录节点没有可读取的正文位置')
  element.scrollIntoView({ block: 'nearest' }); element.focus({ preventScroll: true })
}
async function selectRequests(requests: SelectionRequest[]): Promise<void> {
  if (!props.active || document.hidden || selecting.value) return
  clearSelection(); const requested = generation; const reading = new AbortController(); controller = reading; selecting.value = true
  const body = props.body; const revision = props.documentRevision; const dirty = props.dirty
  try {
    const result = await createDocumentSelection(body, revision, requests, reading.signal)
    if (requested !== generation || !props.active || document.hidden || body !== props.body || revision !== props.documentRevision || dirty !== props.dirty) return
    selection.value = result; emit('selected', result, dirty)
  } finally { if (requested === generation) { selecting.value = false; controller = null } }
}
async function capture(): Promise<void> {
  const current = window.getSelection()
  if (!contents.value || !current || current.rangeCount !== 1) fail('SELECTION_INVALID', '请选择当前全文阅读区域中的文字')
  await selectRequests(browserTextSelection(contents.value, current.getRangeAt(0).cloneRange(), props.body))
}
async function selectEntireText(): Promise<void> {
  await selectRequests(texts.value.flatMap(({ node }) => node.content.kind === 'TEXT' && node.content.text.length > 0
    ? [{ kind: 'TEXT' as const, node_id: node.node_id, start_utf16: 0, end_utf16: node.content.text.length }] : []))
}
async function selectByOffsets(): Promise<void> {
  const node = texts.value.find(entry => entry.node.node_id === textNodeId.value)?.node
  if (!node || node.content.kind !== 'TEXT') fail('SELECTION_INVALID', '请选择当前正文文字节点')
  const characters = Array.from(node.content.text)
  if (!Number.isSafeInteger(start.value) || !Number.isSafeInteger(end.value) || start.value < 0 || end.value > characters.length || start.value >= end.value) {
    fail('SELECTION_INVALID', '起止字符位置必须对应实际原文范围')
  }
  await selectRequests([{ kind: 'TEXT', node_id: node.node_id, start_utf16: characters.slice(0, start.value).join('').length,
    end_utf16: characters.slice(0, end.value).join('').length }])
}
watch(() => [props.body, props.documentRevision, props.active, props.dirty], clear, { flush: 'sync' })
function visibility(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', visibility); window.addEventListener('medical-context-invalidated', clear)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('medical-context-invalidated', clear) })
</script>

<style scoped>
.document-body-reader { display: grid; grid-template-columns: minmax(160px, 240px) minmax(0, 1fr); gap: 20px; }
nav ul { list-style: none; padding: 0; }
nav .el-button { white-space: normal; text-align: start; height: auto; }
.reader-content { margin-top: 20px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 760px) { .document-body-reader { grid-template-columns: minmax(0, 1fr); } }
</style>

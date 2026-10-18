<template>
  <section class="revision-directory" aria-label="已保存文稿历史">
    <header class="directory-heading">
      <div><h3>已保存版本</h3><p>{{ selectionOnly ? '选择固定版本用于比较，不读取正文或替换草稿。' : '选择后单独读取固定正文，不替换当前编辑草稿。' }}</p></div>
      <el-button :disabled="busy || !enabled" @click="load()">从首页刷新</el-button>
    </header>
    <p v-if="error" class="directory-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在读取获授权的版本目录…</p>
    <p v-if="!snapshot">读取当前文稿后可查看已保存版本。</p>
    <template v-else-if="checked && !enabled">
      <p v-if="!error">当前服务器尚未启用文稿历史目录。</p>
      <el-button :disabled="busy" @click="reset">重新核查服务状态</el-button>
    </template>
    <template v-if="enabled && page">
      <p class="directory-summary">本页 {{ page.items.length }} 个可读取版本<span v-if="page.complete"> · 本轮目录读取结束</span></p>
      <p class="directory-note">目录会随保存和授权变化更新。检查、审查与签名数量仅表示已有关联记录。</p>
      <p v-if="page.items.length === 0">本页没有可显示的版本<span v-if="page.next_cursor">，仍可继续读取下一页</span>。</p>
      <ol class="revision-list">
        <li v-for="entry in page.items" :key="entry.document_revision">
          <div class="revision-row">
            <div>
              <strong>{{ entry.is_current ? '当前保存版本' : '历史保存版本' }}</strong>
              <time :datetime="entry.created_at">{{ entry.created_at }}</time>
            </div>
            <el-button :disabled="busy" @click="select(entry.document_revision)">{{ selectionOnly ? '选择此保存版本' : '读取此固定版本' }}</el-button>
          </div>
          <p class="directory-note">检查记录 {{ entry.check_ids.length }} · 审查记录 {{ entry.review_ids.length }} · 签名记录 {{ entry.signature_ids.length }}</p>
          <details><summary>版本标识与摘要</summary><dl>
            <dt>文稿版本</dt><dd>{{ entry.document_revision }}</dd>
            <dt>正文 SHA-256</dt><dd>{{ entry.body_sha256 }}</dd>
            <dt>保存人员标识</dt><dd>{{ entry.created_by }}</dd>
          </dl></details>
        </li>
      </ol>
      <el-button v-if="page.next_cursor" :disabled="busy" @click="load(page.next_cursor)">读取下一页</el-button>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import type { MedicalSession } from '@/api/medical/protocol'
import type { DocumentSnapshot } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue } from '@/api/medical/document-body'
import { documentHistoryAvailability, readDocumentRevisionDirectory } from '@/api/medical/document-revision-directory'
import type { DocumentRevisionPage } from '@/api/medical/document-revision-directory'

const props = defineProps<{ session: MedicalSession; snapshot: DocumentSnapshot | null; active: boolean; disabled?: boolean; selectionOnly?: boolean }>()
const emit = defineEmits<{ (event: 'select', revision: string): void }>()
const user = useUserStore()
const page = ref<DocumentRevisionPage | null>(null)
const enabled = ref(false)
const checked = ref(false)
const loading = ref(false)
const error = ref('')
const pageSize = ref(0)
const busy = computed(() => loading.value || !!props.disabled || user.medicalWriting)
let generation = 0
let alive = true
let controller = new AbortController()

function live(g: number, session: MedicalSession, snapshot: DocumentSnapshot | null) {
  return alive && props.active && !document.hidden && g === generation && props.session === session && props.snapshot === snapshot
}
function clear() {
  generation++
  controller.abort()
  controller = new AbortController()
  page.value = null
  enabled.value = false
  checked.value = false
  loading.value = false
  error.value = ''
  pageSize.value = 0
}
async function load(cursor?: string) {
  if (!enabled.value || !props.active || !props.snapshot || busy.value || document.hidden) return
  if (cursor !== undefined && cursor !== page.value?.next_cursor) return
  const g = generation
  const session = props.session
  const snapshot = props.snapshot
  page.value = null
  error.value = ''
  loading.value = true
  try {
    const result = await readDocumentRevisionDirectory(session, snapshot, pageSize.value, controller.signal, cursor)
    if (!live(g, session, snapshot)) return
    if (result.kind === 'CURSOR_STALE') {
      error.value = '版本目录或分页已变化，请从首页重新读取。'
      return
    }
    page.value = result.page
  } catch (failure) {
    if (live(g, session, snapshot)) error.value = failure instanceof Error ? failure.message : '历史目录读取失败'
  } finally {
    if (live(g, session, snapshot)) loading.value = false
  }
}
async function reset() {
  clear()
  if (!alive || !props.active || !props.snapshot || !props.session.current_context || document.hidden) return
  const g = generation
  const session = props.session
  const snapshot = props.snapshot
  loading.value = true
  try {
    const result = await documentHistoryAvailability(session, controller.signal)
    if (!live(g, session, snapshot)) return
    enabled.value = result.features.document_history
    checked.value = true
    pageSize.value = result.maximum_page_size === null ? 0 : Math.min(20, result.maximum_page_size)
  } catch (failure) {
    if (live(g, session, snapshot)) {
      checked.value = true
      error.value = failure instanceof Error ? failure.message : '历史目录服务状态读取失败'
    }
  } finally {
    if (live(g, session, snapshot)) loading.value = false
  }
  if (live(g, session, snapshot) && enabled.value) await load()
}
function select(revision: string) {
  if (!props.active || !enabled.value || busy.value || document.hidden || !page.value) return
  if (!page.value.items.some(entry => entry.document_revision === revision)) return
  emit('select', revision)
}
watch(() => [props.session, canonicalDocumentValue(props.session.current_context), props.session.user.user_id,
  canonicalDocumentValue(props.session.action_summary), props.snapshot, props.snapshot?.document_id,
  props.snapshot?.document_revision, props.snapshot?.revision, canonicalDocumentValue(props.snapshot?.allowed_actions ?? []), props.active], reset, { immediate: true, flush: 'sync' })
function visibility() { void reset() }
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => {
  alive = false
  clear()
  document.removeEventListener('visibilitychange', visibility)
})
</script>

<style scoped>
.revision-directory { padding: 20px; border: 1px solid var(--medical-border, #dce3ec); border-radius: 8px; background: var(--medical-surface, #fff); color: var(--medical-text, #243247); }
.directory-heading, .revision-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.directory-heading h3 { margin: 0; font-size: 16px; }
.directory-heading p, .directory-note, .directory-summary { color: var(--medical-text-secondary, #526279); font-size: 13px; line-height: 1.6; }
.directory-error { color: var(--medical-danger, #b42318); }
.revision-list { list-style: none; margin: 0; padding: 0; }
.revision-list li { padding: 16px 0; border-top: 1px solid var(--medical-border, #dce3ec); }
.revision-row time { display: block; margin-top: 6px; color: var(--medical-text-secondary, #526279); font-size: 12px; }
.revision-list summary { cursor: pointer; font-size: 13px; }
.revision-list dt { margin-top: 10px; font-size: 12px; color: var(--medical-text-secondary, #526279); }
.revision-list dd { margin: 4px 0; overflow-wrap: anywhere; font-size: 12px; }
@media (max-width: 600px) { .directory-heading, .revision-row { align-items: flex-start; flex-direction: column; } }
</style>

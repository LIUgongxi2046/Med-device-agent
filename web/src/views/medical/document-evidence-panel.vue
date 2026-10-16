<template>
  <section class="document-evidence" aria-label="固定原文与证据关系">
    <h4>固定原文与证据关系</h4>
    <el-empty v-if="links.length === 0" description="当前文稿没有引用证据" />
    <el-table v-else :data="links" row-key="evidence_link_id">
      <el-table-column label="来源版本" min-width="240"><template #default="scope">{{ scope.row.evidence.source_version }}</template></el-table-column>
      <el-table-column label="关系" width="110"><template #default="scope">{{ relations[scope.row.relation as keyof typeof relations] }}</template></el-table-column>
      <el-table-column label="定位" width="110"><template #default="scope">{{ scope.row.evidence.locator_kind }}</template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="scope">
        <el-button :disabled="!active || loading" @click="read(scope.row)">阅读固定原文</el-button>
      </template></el-table-column>
    </el-table>
    <aside v-if="selected" aria-label="原文来源面板" class="source-panel" :aria-busy="loading">
      <div class="source-heading"><h4>{{ view?.title ?? '固定原文' }}</h4><el-button @click="clear">关闭原文</el-button></div>
      <p v-if="loading" role="status">正在核查当前读取权限与固定位置</p>
      <template v-if="content && view && locationRoute">
        <el-tag>已读取固定位置</el-tag>
        <p>文稿版本：{{ documentRevision }}；核准版本：{{ selected.evidence.approval_revision }}</p>
        <pre>{{ content.raw_text }}</pre>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="来源版本">{{ selected.evidence.source_version }}</el-descriptions-item>
          <el-descriptions-item label="解析版本">{{ selected.evidence.parse_version }}</el-descriptions-item>
          <el-descriptions-item label="原文摘要">{{ content.quote_hash }}</el-descriptions-item>
          <el-descriptions-item label="引用位置"><pre>{{ JSON.stringify(selected.evidence.locator, null, 2) }}</pre></el-descriptions-item>
        </el-descriptions>
        <router-link :to="locationRoute"><el-button>进入完整原件与固定位置</el-button></router-link>
      </template>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { EvidenceLink } from '../../../../contracts/typescript/src/interfaces'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { canonicalDocumentValue, documentBytesDigest } from '@/api/medical/document-body'
import { readSourceContent, readSourceGovernance } from '@/api/medical/source-governance'
import type { ParsedContentItem, SourceGovernanceView } from '@/api/medical/source-governance'
import type { RouteLocationRaw } from 'vue-router'

const props = defineProps<{ session: MedicalSession; links: EvidenceLink[]; documentRevision: string; active: boolean }>()
const selected = shallowRef<EvidenceLink | null>(null); const view = shallowRef<SourceGovernanceView | null>(null)
const content = shallowRef<ParsedContentItem | null>(null); const locationRoute = shallowRef<RouteLocationRaw | null>(null)
const loading = ref(false); const relations = { SUPPORT: '支持', CONFLICT: '冲突', BACKGROUND: '背景' }
let generation = 0; let controller: AbortController | null = null
function clear(): void {
  generation++; controller?.abort(); controller = null; selected.value = null; view.value = null
  content.value = null; locationRoute.value = null; loading.value = false
}
async function read(link: EvidenceLink): Promise<void> {
  if (!props.active || loading.value || document.hidden) return
  const context = props.session.current_context
  if (context === null || link.evidence.tenant_id !== context.tenant_id) fail('CONTEXT_CHANGED', '引用证据与当前企业不一致')
  clear(); selected.value = link; const current = generation; const request = new AbortController(); controller = request; loading.value = true
  try {
    const evidence = link.evidence
    const source = await readSourceGovernance(props.session, evidence.source_version, request.signal)
    if (source.source_id !== evidence.source_id || !source.reviews.some(review => review.parse_version === evidence.parse_version)) {
      fail('CONTENT_MISMATCH', '固定引用与来源及解析版本不一致')
    }
    const locatorHash = await documentBytesDigest(new TextEncoder().encode(canonicalDocumentValue(evidence.locator)))
    request.signal.throwIfAborted()
    const result = await readSourceContent(props.session, evidence.source_version, { representation: 'PARSED',
      parse_version: evidence.parse_version, purpose: source.rights_purpose, chunk_id: evidence.chunk_id,
      locator_sha256: locatorHash }, request.signal)
    if (result.representation !== 'PARSED' || result.source_id !== evidence.source_id || result.items.length !== 1
        || result.items[0]?.quote_hash !== evidence.quote_hash
        || canonicalDocumentValue(result.items[0].locator) !== canonicalDocumentValue(evidence.locator)) {
      fail('CONTENT_MISMATCH', '来源原文与固定引用摘要或位置不一致')
    }
    if (current !== generation || !props.active || document.hidden) return
    view.value = source; content.value = result.items[0]
    locationRoute.value = { path: '/library/versions/' + evidence.source_version,
      query: { parse_version: evidence.parse_version, chunk_id: evidence.chunk_id, locator_sha256: locatorHash } }
  } finally { if (current === generation) { controller = null; loading.value = false } }
}
watch(() => [props.session, props.documentRevision, props.active], clear, { flush: 'sync' })
watch(() => props.links, links => {
  const previous = selected.value
  if (previous && !links.some(link => canonicalDocumentValue(link) === canonicalDocumentValue(previous))) clear()
}, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<style scoped>
.source-panel { border: 1px solid var(--el-border-color); border-radius: 8px; margin-top: 16px; padding: 16px; }
.source-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; }
</style>

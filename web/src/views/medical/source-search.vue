<template>
  <main class="medical-page source-search">
    <h1>资料全文检索</h1>
    <router-link to="/library/search/exact">结构化字段等值检索</router-link>
    <p>查询范围只包含当前项目中已经解析、建立索引、人工核准且仍有读取权限的适用资料。</p>
    <session-panel />

    <el-form label-position="top" class="search-form" @submit.prevent="search">
      <el-form-item label="当前项目" :error="errors.project">
        <el-select v-model="form.project_id" filterable :disabled="busy" placeholder="选择获授权项目">
          <el-option v-for="project in projects" :key="project.project_id" :label="project.name" :value="project.project_id">
            <span>{{ project.name }}</span><small>{{ project.status }}</small>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="全文查询" :error="errors.query">
        <el-input v-model="form.query" maxlength="500" show-word-limit :disabled="busy"
          placeholder="输入器械型号、标准编号、法规名称或资料正文" />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="searching" :disabled="busy || !canSearch">检索当前项目资料</el-button>
      <span v-if="!canSearch">需要当前项目及来源读取许可</span>
    </el-form>

    <section v-if="pending" aria-label="原检索请求">
      <p>原检索请求需要核查：{{ pending.key }}</p>
      <p v-if="lookupStatus === 'NOT_FOUND'">尚未找到原操作。保留原查询参数后再次提交，将使用同一请求编号。</p>
      <p v-if="pending.state === 'EXPIRED'">原请求已经过期，请明确开始新的检索。</p>
      <p v-if="operationStatus">原操作状态：{{ operationStatus }}</p>
      <el-button :disabled="busy || pending.state === 'EXPIRED'" @click="recover">查询原检索请求</el-button>
      <el-button v-if="pending.state === 'EXPIRED'" :disabled="busy" @click="startNew">开始新的检索</el-button>
    </section>

    <p v-if="loadingProjects" role="status">正在读取当前获授权项目</p>
    <p v-if="searching" role="status">正在建立固定检索结果集合</p>

    <section v-if="result" aria-labelledby="result-title" class="search-results">
      <div class="result-heading">
        <div>
          <h2 id="result-title">检索结果</h2>
          <p>共{{ result.page_info.total }}项；模式：{{ result.mode }}；创建时间：{{ formatTime(result.created_at) }}</p>
        </div>
        <el-tag :type="result.status === 'FOUND' ? 'success' : 'info'">{{ result.status === 'FOUND' ? '找到结果' : '没有结果' }}</el-tag>
      </div>
      <el-alert v-if="result.status === 'EMPTY'" title="当前限定范围没有匹配内容" type="info" :closable="false"
        description="可以修改查询词或选择其他获授权项目，系统不会自动扩展到其他项目或网络。" />
      <el-table v-else :data="result.items" row-key="rank" data-retrieval-results>
        <el-table-column prop="rank" label="顺序" width="72" />
        <el-table-column label="来源与摘录" min-width="420">
          <template #default="scope">
            <strong>{{ scope.row.title }}</strong>
            <el-button link class="source-link" :disabled="busy" @click="openHit(scope.row)">查看固定版本与命中位置</el-button>
            <p class="excerpt">{{ scope.row.excerpt }}{{ scope.row.excerpt_truncated ? '…' : '' }}</p>
            <el-collapse>
              <el-collapse-item title="固定来源身份与原文位置" :name="scope.row.rank">
                <dl class="identity-list">
                  <div><dt>来源版本</dt><dd>{{ scope.row.source_version }}</dd></div>
                  <div><dt>解析版本</dt><dd>{{ scope.row.parse_version }}</dd></div>
                  <div><dt>索引版本</dt><dd>{{ scope.row.index_version }}</dd></div>
                  <div><dt>内容片段</dt><dd>{{ scope.row.chunk_id }}</dd></div>
                  <div><dt>核准版本</dt><dd>{{ scope.row.approval_revision }}</dd></div>
                  <div><dt>原文摘要</dt><dd>{{ scope.row.quote_hash }}</dd></div>
                </dl>
                <pre>{{ locatorText(scope.row) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </template>
        </el-table-column>
        <el-table-column prop="locator_kind" label="位置类型" width="110" />
        <el-table-column prop="relevance" label="相关度" width="120" />
      </el-table>
      <el-pagination v-if="total > pageSize" :current-page="result.page_info.page" :page-size="pageSize" :total="total"
        layout="total, prev, pager, next" :disabled="busy" @current-change="changePage" />
      <p class="result-identity">固定结果编号：{{ result.retrieval_id }}；集合摘要：{{ result.page_info.selection_sha256 }}</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { canonicalizeEx } from 'json-canonicalize'
import SessionPanel from './session-panel.vue'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues, validate } from '@/api/medical/protocol'
import type { MedicalSession, Project } from '@/api/medical/protocol'
import { listProjects } from '@/api/medical/projects'
import { createRetrieval, readRetrieval, retrievalAllowed } from '@/api/medical/retrievals'
import type { RetrievalHit, RetrievalPage } from '@/api/medical/retrievals'
import { byteDigest } from '@/api/medical/source-uploads'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { lookupOperation, readOperation } from '@/api/medical/operations'
import { clearExpiredRequestMetadata, confirmRequestMetadata, expireRequestMetadata, readRequestMetadata } from '@/api/medical/request-metadata'
import type { RequestMetadata } from '@/api/medical/request-metadata'

const user = useUserStore(); const route = useRoute(); const router = useRouter()
const projects = ref<Project[]>([]); const result = ref<RetrievalPage | null>(null)
const loadingProjects = ref(false); const searching = ref(false); const errors = reactive<Record<string, string>>({})
const form = reactive({ project_id: '', query: '' }); const pageSize = 20
const pending = ref<RequestMetadata | null>(null)
const lookupStatus = ref<'FOUND' | 'NOT_FOUND' | 'EXPIRED' | null>(null); const operationStatus = ref('')
let controller: AbortController | null = null; let generation = 0
const active = useMedicalRouteRead('MedicalSourceSearch', clear)
const busy = computed(() => loadingProjects.value || searching.value)
const canSearch = computed(() => retrievalAllowed(user.medicalSession, form.project_id))
const total = computed(() => {
  if (!result.value) return 0
  const value = BigInt(result.value.page_info.total)
  if (value > BigInt(Number.MAX_SAFE_INTEGER)) fail('CONTEXT_LIMIT', '检索结果数量超出页面可准确表示范围')
  return Number(value)
})
async function openHit(hit: RetrievalHit): Promise<void> {
  const session = user.medicalSession; const original = result.value; const version = generation
  if (!session?.current_context || !original?.items.includes(hit) || !active.value || document.hidden || busy.value) return
  const locator = canonicalizeEx(hit.locator, { strictUndefined: true })
  const locatorSha256 = await byteDigest(new TextEncoder().encode(locator))
  if (version !== generation || user.medicalSession !== session || result.value !== original || !active.value || document.hidden) return
  await router.push({ path: '/library/versions/' + hit.source_version, query: {
    parse_version: hit.parse_version, chunk_id: hit.chunk_id, locator_sha256: locatorSha256
  } })
}

function clear(): void {
  generation += 1; controller?.abort(); controller = null; projects.value = []; result.value = null
  loadingProjects.value = false; searching.value = false; form.project_id = ''; form.query = ''
  pending.value = null; lookupStatus.value = null; operationStatus.value = ''
  for (const key of Object.keys(errors)) delete errors[key]
}
async function loadProjects(): Promise<void> {
  clear(); const session: MedicalSession | null = user.medicalSession; const current = generation
  if (!session?.current_context || !active.value || document.hidden) return
  const reading = new AbortController(); controller = reading; loadingProjects.value = true
  try {
    const found: Project[] = []; const seen = new Set<string>(); let after: string | undefined
    do {
      const page = await listProjects(session.current_context, after ? { after } : {}, reading.signal)
      for (const project of page.projects) {
        if (seen.has(project.project_id)) fail('CONTENT_MISMATCH', '项目分页返回重复项目')
        seen.add(project.project_id); found.push(project)
      }
      const next = page.next_after ?? undefined
      if (next !== undefined && next === after) fail('CONTENT_MISMATCH', '项目分页游标没有前进')
      after = next
    } while (after !== undefined)
    if (!isCurrent(current, session, reading)) return
    projects.value = found
    const requestedRetrieval = route.query.retrieval_id
    if (requestedRetrieval !== undefined) {
      validate('urn:medical:request-metadata:1.0.0#/$defs/Id', requestedRetrieval)
      const pageValue = route.query.page ?? '1'
      if (typeof pageValue !== 'string' || !/^[1-9][0-9]*$/.test(pageValue) || !Number.isSafeInteger(Number(pageValue))) fail('SCHEMA_INVALID', '检索历史页码无效')
      const fixed = await readRetrieval(session, requestedRetrieval as string, Number(pageValue), pageSize, reading.signal)
      if (!found.some(project => project.project_id === fixed.project_id)) fail('ACCESS_DENIED', '检索历史所属项目没有当前许可')
      if (!isCurrent(current, session, reading)) return
      result.value = fixed; form.project_id = fixed.project_id; form.query = fixed.query
      return
    }
    const requested = typeof route.query.project_id === 'string' ? route.query.project_id : ''
    form.project_id = found.some(project => project.project_id === requested) ? requested : found[0]?.project_id ?? ''
  } finally {
    if (current === generation && controller === reading) { loadingProjects.value = false; controller = null }
  }
}
async function search(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const current = generation
  const input = { schema_version: '1.0.0' as const, project_id: form.project_id, query: form.query, purpose: 'VERIFICATION' as const }
  const issues = schemaIssues('urn:medical:retrieval-view:1.0.0#/$defs/SearchInput', input)
  errors.project = issues.some(issue => issue.path === '/project_id') ? '请选择有效项目' : ''
  errors.query = issues.some(issue => issue.path === '/query') || form.query.trim() !== form.query || form.query.normalize('NFC') !== form.query
    ? '请输入2至500字的规范查询内容' : ''
  if (!session?.current_context || !active.value || !canSearch.value || errors.project || errors.query || busy.value || document.hidden) return
  controller?.abort(); const reading = new AbortController(); controller = reading; searching.value = true; result.value = null
  try {
    const operation = await createRetrieval(session, input, reading.signal)
    const confirmed = await readOperation(session, operation.operation_id, reading.signal)
    if (confirmed.status !== 'COMPLETED' || confirmed.resource_id !== operation.resource_id) fail('CONTENT_MISMATCH', '检索原操作与固定结果不一致')
    const page = await readRetrieval(session, operation.resource_id!, 1, pageSize, reading.signal)
    if (page.project_id !== input.project_id || page.query !== input.query || page.purpose !== input.purpose) fail('CONTENT_MISMATCH', '固定检索集合与原查询不一致')
    if (!isCurrent(current, session, reading)) return
    const saved = readRequestMetadata(session, 'KN-14-S04', input.project_id)
    if (saved === null) fail('ACTION_STALE', '检索原请求元数据缺失')
    confirmRequestMetadata(session, saved, confirmed, page.retrieval_id)
    await router.replace({ name: 'MedicalSourceSearch', query: { retrieval_id: page.retrieval_id, page: '1' } })
  } finally {
    if (current === generation && controller === reading) { searching.value = false; controller = null; syncPending() }
  }
}
function isCurrent(version: number, session: MedicalSession, reading: AbortController): boolean {
  return version === generation && user.medicalSession === session && active.value && !document.hidden && controller === reading && !reading.signal.aborted
}
function syncPending(): void {
  pending.value = user.medicalSession?.current_context && form.project_id && active.value
    ? readRequestMetadata(user.medicalSession, 'KN-14-S04', form.project_id) : null
  lookupStatus.value = null; operationStatus.value = ''
}
async function recover(): Promise<void> {
  const session = user.medicalSession; const original = pending.value; const version = generation
  if (!session?.current_context || !original || !active.value || busy.value || document.hidden) return
  const reading = new AbortController(); controller = reading; searching.value = true
  try {
    const lookup = await lookupOperation(session, original.operation, original.key, reading.signal)
    if (!isCurrent(version, session, reading)) return
    lookupStatus.value = lookup.lookup_status; operationStatus.value = lookup.operation?.status ?? ''
    if (lookup.lookup_status === 'EXPIRED') {
      expireRequestMetadata(session, original); pending.value = readRequestMetadata(session, original.operation, original.target_id); return
    }
    if (lookup.operation?.status !== 'COMPLETED') return
    if (lookup.operation.resource_id === null) fail('CONTENT_MISMATCH', '检索原操作缺少固定结果编号')
    const page = await readRetrieval(session, lookup.operation.resource_id, 1, pageSize, reading.signal)
    if (page.project_id !== original.target_id) fail('CONTENT_MISMATCH', '原检索结果所属项目不一致')
    if (!isCurrent(version, session, reading)) return
    confirmRequestMetadata(session, original, lookup.operation, page.retrieval_id)
    await router.replace({ name: 'MedicalSourceSearch', query: { retrieval_id: page.retrieval_id, page: '1' } })
  } finally { if (version === generation && controller === reading) { searching.value = false; controller = null } }
}
function startNew(): void {
  const session = user.medicalSession; const original = pending.value
  if (!session?.current_context || !original || !active.value || busy.value || document.hidden) return
  clearExpiredRequestMetadata(session, original); form.query = ''; result.value = null; syncPending()
}
async function changePage(page: number): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = result.value; const current = generation
  if (!session?.current_context || !active.value || !original || busy.value || document.hidden) return
  const reading = new AbortController(); controller = reading; searching.value = true
  try {
    const next = await readRetrieval(session, original.retrieval_id, page, pageSize, reading.signal)
    if (next.project_id !== original.project_id || next.query !== original.query
        || next.created_at !== original.created_at || next.mode !== original.mode || next.purpose !== original.purpose
        || next.status !== original.status || next.page_info.total !== original.page_info.total
        || next.page_info.selection_sha256 !== original.page_info.selection_sha256) fail('CONTENT_MISMATCH', '检索分页改变了固定结果集合')
    if (isCurrent(current, session, reading)) await router.replace({ name: 'MedicalSourceSearch', query: { retrieval_id: original.retrieval_id, page: String(page) } })
  } finally {
    if (current === generation && controller === reading) { searching.value = false; controller = null }
  }
}
function locatorText(hit: RetrievalHit): string { return JSON.stringify(hit.locator, null, 2) }
function formatTime(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
watch(() => [user.medicalSession, active.value], loadProjects, { immediate: true, flush: 'sync' })
watch(() => form.project_id, syncPending, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<style scoped>
.search-form { max-width: 760px; margin-top: 24px; }
.search-form .el-select { width: 100%; }
.search-form small { float: right; margin-left: 24px; color: #909399; }
.search-results { margin-top: 32px; }
.result-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.result-heading h2 { margin-bottom: 0; }
.excerpt { white-space: pre-wrap; }
.source-link { display: inline-block; margin-left: 12px; }
.identity-list { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 6px 12px; }
.identity-list div { display: contents; }
.identity-list dt { color: #606266; }
.identity-list dd { margin: 0; overflow-wrap: anywhere; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; }
.result-identity { color: #606266; overflow-wrap: anywhere; }
@media (max-width: 767px) { .result-heading { align-items: flex-start; flex-direction: column; } }
</style>

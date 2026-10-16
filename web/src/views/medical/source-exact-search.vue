<template>
  <main class="app-container exact-search">
    <h1>结构化字段等值检索</h1>
    <session-panel />
    <router-link to="/library/search">资料全文检索</router-link>
    <p>按已核准字段原值或人工别名进行等值查询，保留查询的空白、大小写和Unicode表示。</p>
    <el-form label-position="top" :disabled="busy" @submit.prevent="search">
      <el-form-item label="获授权项目"><el-select v-model="projectId" aria-label="等值检索项目">
        <el-option v-for="item in projects" :key="item.project_id" :value="item.project_id" :label="item.name" />
      </el-select></el-form-item>
      <el-form-item label="字段类别"><el-select v-model="kind" aria-label="等值检索字段类别">
        <el-option v-for="(label, value) in exactKindLabels" :key="value" :label="label" :value="value" />
      </el-select></el-form-item>
      <el-form-item label="完整等值查询"><el-input v-model="query" type="textarea" :rows="2" aria-label="完整等值查询" /></el-form-item>
      <p>{{ Array.from(query).length }} / 512个Unicode字符</p>
      <el-button native-type="submit" :disabled="!maySearch || !exactHasText(query)">检索核准字段</el-button>
    </el-form>
    <section v-if="pending" aria-label="原等值查询请求">
      <p>原请求：{{ pending.key }}；{{ pending.state }}</p>
      <p v-if="lookupState" role="status">原操作查询：{{ lookupState }}</p>
      <el-button :disabled="busy || pending.state === 'EXPIRED'" @click="recover">查询原等值检索请求</el-button>
      <p v-if="lookupState === 'NOT_FOUND'">完整原查询参数一致时再次提交将继续使用原请求编号。</p>
      <el-button v-if="pending.state === 'EXPIRED'" :disabled="busy" @click="startNew">结束已过期等值查询</el-button>
    </section>
    <section v-if="result" aria-label="固定等值检索结果">
      <h2>固定等值检索结果</h2>
      <p>结果编号：{{ result.retrieval_id }}；项目：{{ result.project_id }}；字段：{{ exactKindLabels[result.field_kind] }}</p>
      <p>查询：<span class="literal">{{ result.query }}</span>；用途：{{ result.purpose }}；模式：{{ result.mode }}；状态：{{ result.status }}</p>
      <p>完整数量：{{ result.total }}；时间：{{ result.created_at }}；集合摘要：{{ result.selection_sha256 }}</p>
      <el-empty v-if="result.status === 'EMPTY'" description="当前固定范围没有核准字段匹配" />
      <el-table v-else :data="result.items" row-key="rank" aria-label="等值检索命中">
        <el-table-column prop="rank" label="顺序" width="80" />
        <el-table-column label="来源及完整匹配字段" min-width="420"><template #default="scope">
          <strong>{{ scope.row.title }}</strong>
          <el-button link :disabled="busy" @click="openHit(scope.row)">读取固定原文位置</el-button>
          <p class="literal">{{ scope.row.excerpt }}{{ scope.row.excerpt_truncated ? '…' : '' }}</p>
          <p v-for="match in scope.row.matches" :key="match.ordinal" class="literal">
            字段顺序 {{ match.ordinal }}；原值 {{ match.field.raw_value }}；人工别名 {{ match.field.alias_value ?? '未指定' }}；审核理由 {{ match.field.alias_reason ?? '未指定' }}
          </p>
          <details><summary>完整来源、字段版本和原文位置</summary><pre>{{ JSON.stringify(scope.row, null, 2) }}</pre></details>
        </template></el-table-column>
      </el-table>
      <el-pagination v-if="total > pageSize" :current-page="result.page" :page-size="pageSize" :total="total"
        layout="total, prev, pager, next" :disabled="busy" @current-change="changePage" />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, toRaw, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { fail, validate } from '@/api/medical/protocol'
import type { MedicalSession, Project } from '@/api/medical/protocol'
import { listProjects } from '@/api/medical/projects'
import { retrievalAllowed } from '@/api/medical/retrievals'
import { sourceDigest } from '@/api/medical/source-uploads'
import { createExactRetrieval, readExactRetrieval } from '@/api/medical/source-exact'
import { exactHasText, exactKindLabels, exactLiteral } from '@/api/medical/source-exact-domain'
import type { ExactHit, ExactKind, ExactPage, ExactSearchInput } from '@/api/medical/source-exact-domain'
import { lookupOperation, readOperation } from '@/api/medical/operations'
import { clearExpiredRequestMetadata, confirmRequestMetadata, expireRequestMetadata, readRequestMetadata, requestBodyDigest } from '@/api/medical/request-metadata'
import type { RequestMetadata } from '@/api/medical/request-metadata'

const user = useUserStore(), route = useRoute(), router = useRouter()
const projects = shallowRef<Project[]>([]), projectId = ref(''), kind = ref<ExactKind>('MODEL'), query = ref('')
const result = shallowRef<ExactPage | null>(null), pending = shallowRef<RequestMetadata | null>(null), lookupState = ref('')
const working = ref(false), pageSize = 20; let generation = 0; let controller: AbortController | null = null
const active = useMedicalRouteRead('MedicalSourceExactSearch', clear), busy = computed(() => working.value || user.medicalWriting)
const maySearch = computed(() => active.value && retrievalAllowed(user.medicalSession, projectId.value))
const total = computed(() => {
  if (!result.value) return 0
  const count = BigInt(result.value.total)
  if (count > BigInt(Number.MAX_SAFE_INTEGER)) fail('CONTEXT_LIMIT', '等值查询数量超过页面准确表示范围')
  return Number(count)
})
function clear(): void {
  generation++; controller?.abort(); controller = null; working.value = false
  projects.value = []; projectId.value = ''; kind.value = 'MODEL'; query.value = ''; result.value = null; pending.value = null; lookupState.value = ''
}
function begin() {
  const session: MedicalSession | null = user.medicalSession
  if (!active.value || document.hidden || !session?.current_context) fail('ACCESS_DENIED', '等值检索需要当前可见页面及企业身份')
  controller?.abort(); controller = new AbortController(); generation++
  return { session, version: generation, signal: controller.signal }
}
function valid(work: ReturnType<typeof begin>): boolean { return active.value && !document.hidden && !work.signal.aborted
  && work.version === generation && user.medicalSession === work.session }
function input(): ExactSearchInput { return { schema_version: '1.0.0', project_id: projectId.value, field_kind: kind.value, query: query.value, purpose: 'VERIFICATION' } }
function syncPending(): void {
  result.value = null; lookupState.value = ''
  pending.value = active.value && user.medicalSession?.current_context && projectId.value
    ? readRequestMetadata(user.medicalSession, 'KN-14-EXACT', projectId.value) : null
}
async function load(): Promise<void> {
  clear(); if (!active.value || document.hidden || !user.medicalSession?.current_context) return
  const work = begin(); working.value = true
  try {
    const found: Project[] = [], seen = new Set<string>(), cursors = new Set<string>(); let after: string | undefined
    do {
      const page = await listProjects(work.session.current_context!, after ? { after } : {}, work.signal)
      for (const item of page.projects) { if (seen.has(item.project_id)) fail('CONTENT_MISMATCH', '获授权项目分页重复'); seen.add(item.project_id); found.push(item) }
      after = page.next_after ?? undefined
      if (after !== undefined) { if (cursors.has(after)) fail('CONTENT_MISMATCH', '项目分页游标没有前进'); cursors.add(after) }
    } while (after !== undefined)
    if (!valid(work)) return; projects.value = found
    const id = route.query.retrieval_id
    if (id !== undefined) {
      validate('urn:medical:templates:1.0.0#/$defs/Id', id)
      const number = route.query.page ?? '1'
      if (typeof number !== 'string' || !/^[1-9][0-9]*$/.test(number) || !Number.isSafeInteger(Number(number))) fail('SCHEMA_INVALID', '等值查询历史页码无效')
      const page = await readExactRetrieval(work.session, id as string, Number(number), pageSize, work.signal)
      if (!found.some(item => item.project_id === page.project_id) || !retrievalAllowed(work.session, page.project_id)) fail('ACCESS_DENIED', '固定等值查询所属项目没有当前许可')
      if (!valid(work)) return
      projectId.value = page.project_id; kind.value = page.field_kind; query.value = page.query; result.value = page
    } else if (route.query.project_id !== undefined) {
      validate('urn:medical:templates:1.0.0#/$defs/Id', route.query.project_id)
      if (!found.some(item => item.project_id === route.query.project_id)) fail('ACCESS_DENIED', '指定检索项目没有当前许可')
      projectId.value = route.query.project_id as string
    }
  } finally { if (work.version === generation) working.value = false }
}
async function search(): Promise<void> {
  if (busy.value || !maySearch.value) return
  const body = input(); exactLiteral(body.query); const work = begin(); working.value = true; result.value = null
  try {
    const operation = await createExactRetrieval(work.session, body, work.signal)
    const actual = await readOperation(work.session, operation.operation_id, work.signal)
    if (actual.resource_id !== operation.resource_id || actual.status !== 'COMPLETED') fail('CONTENT_MISMATCH', '等值查询原操作重读不一致')
    const page = await readExactRetrieval(work.session, operation.resource_id!, 1, pageSize, work.signal)
    if (page.project_id !== body.project_id || page.field_kind !== body.field_kind || page.query !== body.query) fail('CONTENT_MISMATCH', '等值查询固定结果与完整原输入不同')
    if (!valid(work)) return
    const metadata = readRequestMetadata(work.session, 'KN-14-EXACT', body.project_id)
    if (!metadata) fail('ACTION_STALE', '等值查询原请求身份缺少')
    confirmRequestMetadata(work.session, metadata, actual, page.retrieval_id)
    await router.replace({ name: 'MedicalSourceExactSearch', query: { retrieval_id: page.retrieval_id, page: '1' } })
  } finally { if (work.version === generation) { working.value = false; syncPending() } }
}
async function recover(): Promise<void> {
  if (busy.value || !pending.value) return
  const metadata = pending.value, work = begin(); working.value = true
  try {
    const lookup = await lookupOperation(work.session, metadata.operation, metadata.key, work.signal)
    if (!valid(work)) return; lookupState.value = lookup.lookup_status
    if (lookup.lookup_status === 'EXPIRED') { expireRequestMetadata(work.session, metadata); pending.value = readRequestMetadata(work.session, metadata.operation, metadata.target_id); return }
    if (lookup.operation?.status !== 'COMPLETED' || !lookup.operation.resource_id) return
    const actual = await readOperation(work.session, lookup.operation.operation_id, work.signal)
    if (actual.resource_id !== lookup.operation.resource_id) fail('CONTENT_MISMATCH', '原等值查询操作目标变化')
    const page = await readExactRetrieval(work.session, actual.resource_id!, 1, pageSize, work.signal)
    const body: ExactSearchInput = { schema_version: page.schema_version, project_id: page.project_id, field_kind: page.field_kind, query: page.query, purpose: page.purpose }
    if (page.project_id !== metadata.target_id || await requestBodyDigest(body) !== metadata.body_sha256) fail('CONTENT_MISMATCH', '原等值查询完整参数摘要不同')
    if (!valid(work)) return
    confirmRequestMetadata(work.session, metadata, actual, page.retrieval_id)
    await router.replace({ name: 'MedicalSourceExactSearch', query: { retrieval_id: page.retrieval_id, page: '1' } })
  } finally { if (work.version === generation) working.value = false }
}
function startNew(): void {
  if (busy.value || !pending.value || !user.medicalSession) return
  clearExpiredRequestMetadata(user.medicalSession, pending.value); query.value = ''; syncPending()
}
async function changePage(page: number): Promise<void> {
  if (busy.value || !result.value) return
  const original = result.value, work = begin(); working.value = true
  try {
    const next = await readExactRetrieval(work.session, original.retrieval_id, page, pageSize, work.signal)
    if (next.project_id !== original.project_id || next.query !== original.query || next.field_kind !== original.field_kind
        || next.purpose !== original.purpose || next.created_at !== original.created_at || next.total !== original.total
        || next.status !== original.status || next.selection_sha256 !== original.selection_sha256) fail('CONTENT_MISMATCH', '等值查询分页改变了固定集合')
    if (valid(work)) await router.replace({ name: 'MedicalSourceExactSearch', query: { retrieval_id: original.retrieval_id, page: String(page) } })
  } finally { if (work.version === generation) working.value = false }
}
async function openHit(hit: ExactHit): Promise<void> {
  const selected = toRaw(hit)
  if (busy.value || !result.value?.items.includes(selected)) return
  const version = generation, session = user.medicalSession, original = result.value
  const hash = await sourceDigest(selected.locator)
  if (version !== generation || user.medicalSession !== session || result.value !== original || !active.value || document.hidden) return
  await router.push({ path: '/library/versions/' + selected.source_version, query: { parse_version: selected.parse_version, chunk_id: selected.chunk_id, locator_sha256: hash } })
}
watch(() => [projectId.value, kind.value, query.value], syncPending, { flush: 'sync' })
watch(() => [user.medicalSession, active.value], load, { immediate: true, flush: 'sync' })
function visibility(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', visibility); window.addEventListener('medical-context-invalidated', clear)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('medical-context-invalidated', clear) })
</script>

<style scoped>
form { max-width: 760px; margin-top: 24px; }
.el-select { width: 100%; }
section { margin-top: 24px; }
.literal, pre { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

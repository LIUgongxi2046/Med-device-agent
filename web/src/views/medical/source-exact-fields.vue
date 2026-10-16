<template>
  <main class="app-container exact-fields">
    <h1>结构化字段核准</h1>
    <session-panel />
    <router-link :to="'/library/versions/' + sourceVersion">读取固定来源及原件</router-link>
    <p>来源版本：{{ sourceVersion }}</p>
    <p>原值保留原文中的空白、大小写和Unicode表示。别名需要独立审核理由。</p>
    <section v-if="pending" aria-label="原字段核准请求">
      <p>原请求：{{ pending.key }}；{{ pending.state }}；项目：{{ pending.scope_id }}</p>
      <el-button :disabled="busy || pending.state === 'EXPIRED'" @click="recover">查询原字段核准请求</el-button>
      <p v-if="lookupState" role="status">原操作查询：{{ lookupState }}</p>
      <details v-if="lookupState === 'NOT_FOUND'">
        <summary>提供完整原输入恢复核准</summary>
        <el-input v-model="recoveryJson" type="textarea" :rows="8" aria-label="原字段核准完整JSON" :disabled="busy" />
        <el-button :disabled="busy || !recoveryJson" @click="resend">重新发送原字段核准请求</el-button>
      </details>
      <el-button v-if="pending.state === 'EXPIRED'" :disabled="busy" @click="discardExpired">结束已过期字段请求</el-button>
    </section>
    <template v-if="view">
      <el-form label-position="top" :disabled="busy" @submit.prevent>
        <el-form-item label="适用项目"><el-select v-model="projectId" aria-label="字段适用项目" @change="clearSelection">
          <el-option v-for="item in bindings" :key="item.project_id" :value="item.project_id" :label="item.project_id" />
        </el-select></el-form-item>
        <el-form-item label="已核准解析及索引"><el-select v-model="reviewId" aria-label="字段解析及索引" @change="clearSelection">
          <el-option v-for="item in reviews" :key="item.review_manifest_sha256" :value="item.review_manifest_sha256"
            :label="item.parse_version + ' / ' + item.index_version" />
        </el-select></el-form-item>
      </el-form>
      <p v-if="!scopeReady" role="status">需要当前批准权利、有效适用项目、已核准解析和READY索引。</p>
      <el-descriptions v-if="scopeReady" :column="1" border aria-label="字段固定核准依据">
        <el-descriptions-item label="原件摘要">{{ view.original_sha256 }}</el-descriptions-item>
        <el-descriptions-item label="权利版本">{{ view.rights_revision }}</el-descriptions-item>
        <el-descriptions-item label="解析版本">{{ review!.parse_version }}</el-descriptions-item>
        <el-descriptions-item label="索引版本">{{ review!.index_version }}</el-descriptions-item>
        <el-descriptions-item label="资料核准版本">{{ review!.approval_revision }}</el-descriptions-item>
        <el-descriptions-item label="项目适用版本">{{ binding!.binding_revision }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top" :disabled="busy || !scopeReady" @submit.prevent="readFields">
        <el-form-item label="固定字段版本，空值读取当前版本"><el-input v-model="fixedRevision" aria-label="固定字段版本" /></el-form-item>
        <el-button native-type="submit">读取完整字段清单</el-button>
      </el-form>
      <section v-if="manifest" aria-label="固定字段清单">
        <h2>固定字段清单</h2>
        <p>字段版本：{{ manifest.field_revision }}；修订：{{ manifest.revision }}；核准人员：{{ manifest.actor_id }}；时间：{{ manifest.created_at }}</p>
        <p>完整清单摘要：{{ manifestHash }}</p>
        <pre>{{ JSON.stringify(manifest, null, 2) }}</pre>
        <el-button :disabled="busy || Boolean(pending) || !mayPublish" @click="useManifest">使用当前读取清单修订</el-button>
      </section>
      <section v-if="!pending" aria-label="完整字段编辑">
        <h2>从固定原文标记字段</h2>
        <el-button :disabled="busy || !scopeReady" @click="readParsed()">读取固定解析片段</el-button>
        <template v-if="parsed">
          <el-select v-model="chunkId" aria-label="字段原文片段" :disabled="busy">
            <el-option v-for="item in parsed.items" :key="item.chunk_id" :value="item.chunk_id" :label="item.ordinal + ' / ' + item.chunk_id" />
          </el-select>
          <p v-if="chunk">片段：{{ chunk.chunk_id }}；原文摘要：{{ chunk.quote_hash }}；位置摘要：{{ chunk.locator_sha256 }}</p>
          <pre v-if="chunk" aria-label="字段完整原文">{{ chunk.raw_text }}</pre>
          <el-button :disabled="busy || cursors.length < 2" @click="previousParsed">上一页解析片段</el-button>
          <el-button :disabled="busy || parsed.next_after_ordinal === null" @click="readParsed(parsed.next_after_ordinal!)">下一页解析片段</el-button>
        </template>
        <el-form v-if="chunk && mayPublish" label-position="top" :disabled="busy" @submit.prevent="addField">
          <el-form-item label="字段类别"><el-select v-model="kind" aria-label="结构化字段类别">
            <el-option v-for="(label, value) in exactKindLabels" :key="value" :label="label" :value="value" />
          </el-select></el-form-item>
          <el-form-item label="开始Unicode位置"><el-input-number v-model="start" :min="0" :precision="0" aria-label="字段开始Unicode位置" /></el-form-item>
          <el-form-item label="结束Unicode位置"><el-input-number v-model="end" :min="1" :precision="0" aria-label="字段结束Unicode位置" /></el-form-item>
          <p>选定原值：<span class="literal" aria-label="选定字段原值">{{ selectedText }}</span></p>
          <el-button native-type="submit" :disabled="!exactHasText(selectedText) || !validRange">加入完整字段清单</el-button>
        </el-form>
        <el-table :data="fields" aria-label="待核准完整字段" empty-text="尚未标记字段">
          <el-table-column label="字段类别"><template #default="scope">{{ exactKindLabels[scope.row.field_kind as ExactKind] }}</template></el-table-column>
          <el-table-column prop="raw_value" label="完整原值" min-width="200" />
          <el-table-column label="人工别名" min-width="180"><template #default="scope">
            <el-input :model-value="scope.row.alias_value ?? ''" :aria-label="'字段别名 ' + (scope.$index + 1)" :disabled="busy"
              @update:model-value="scope.row.alias_value = $event === '' ? null : $event" />
          </template></el-table-column>
          <el-table-column label="别名审核理由" min-width="220"><template #default="scope">
            <el-input :model-value="scope.row.alias_reason ?? ''" :aria-label="'别名审核理由 ' + (scope.$index + 1)" :disabled="busy"
              @update:model-value="scope.row.alias_reason = $event === '' ? null : $event" />
          </template></el-table-column>
          <el-table-column prop="chunk_id" label="原文片段" min-width="220" />
          <el-table-column label="Unicode范围"><template #default="scope">{{ scope.row.start_codepoint }}—{{ scope.row.end_codepoint }}</template></el-table-column>
          <el-table-column label="操作"><template #default="scope"><el-button :disabled="busy" @click="fields.splice(scope.$index, 1)">移除字段</el-button></template></el-table-column>
        </el-table>
        <el-form :disabled="busy || !mayPublish" label-position="top" @submit.prevent="submit">
          <el-form-item label="原字段清单修订，首次核准填写0"><el-input v-model="expectedRevision" aria-label="原字段清单修订" /></el-form-item>
          <el-form-item label="核准理由"><el-input v-model="reason" type="textarea" maxlength="4000" aria-label="字段核准理由" /></el-form-item>
          <el-checkbox v-model="attested">已核查全部字段、人工别名和固定原文位置</el-checkbox>
          <el-button native-type="submit" :disabled="!attested || !fields.length || !expectedRevision || !reason.trim() || !mayPublish">核准完整字段清单</el-button>
        </el-form>
      </section>
      <p v-if="receipt" role="status">字段核准回执已经重新读取：{{ receipt.operation_id }}；{{ receipt.field_revision }}</p>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { canonicalizeEx } from 'json-canonicalize'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { fail } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { readSourceContent, readSourceGovernance, sourceApprovalAllowed } from '@/api/medical/source-governance'
import type { ParsedSourceContent, SourceGovernanceView } from '@/api/medical/source-governance'
import { exactHasText, exactKindLabels, verifyExactField, verifyExactPublication } from '@/api/medical/source-exact-domain'
import type { ExactField, ExactKind, ExactManifest, ExactPublication, ExactReceipt } from '@/api/medical/source-exact-domain'
import { publishExactFields, readExactFields } from '@/api/medical/source-exact'
import { sourceDigest } from '@/api/medical/source-uploads'
import { lookupOperation, readOperation } from '@/api/medical/operations'
import { clearExpiredRequestMetadata, confirmRequestMetadata, expireRequestMetadata, prepareRequestMetadata, readRequestMetadata, requestBodyDigest } from '@/api/medical/request-metadata'
import type { RequestMetadata } from '@/api/medical/request-metadata'

const user = useUserStore(), route = useRoute(), sourceVersion = computed(() => String(route.params.sourceVersion))
const view = shallowRef<SourceGovernanceView | null>(null), parsed = shallowRef<ParsedSourceContent | null>(null)
const manifest = shallowRef<ExactManifest | null>(null), receipt = shallowRef<ExactReceipt | null>(null), manifestHash = ref('')
const pending = shallowRef<RequestMetadata | null>(null), lookupState = ref(''), recoveryJson = ref('')
const projectId = ref(''), reviewId = ref(''), fixedRevision = ref(''), expectedRevision = ref(''), reason = ref(''), attested = ref(false)
const fields = ref<ExactField[]>([]), chunkId = ref(''), kind = ref<ExactKind>('MODEL'), start = ref(0), end = ref(1), cursors = ref<(string | undefined)[]>([])
const working = ref(false); let generation = 0; let controller: AbortController | null = null
const active = useMedicalRouteRead('MedicalSourceExactFields', clear), busy = computed(() => working.value || user.medicalWriting)
const bindings = computed(() => view.value?.bindings.filter(item => item.applicability === 'APPLICABLE') ?? [])
const binding = computed(() => bindings.value.find(item => item.project_id === projectId.value))
const reviews = computed(() => view.value?.reviews.filter(item => item.approval_status === 'APPROVED' && item.approval_revision !== null
  && item.index_status === 'READY' && item.index_version !== null) ?? [])
const review = computed(() => reviews.value.find(item => item.review_manifest_sha256 === reviewId.value))
const scopeReady = computed(() => active.value && view.value?.rights_status === 'APPROVED' && view.value.lifecycle_state === 'ACTIVE' && Boolean(binding.value && review.value))
const mayPublish = computed(() => scopeReady.value && view.value !== null && sourceApprovalAllowed(user.medicalSession, view.value.source_id, view.value.owner_project_id))
const chunk = computed(() => parsed.value?.items.find(item => item.chunk_id === chunkId.value))
const validRange = computed(() => Boolean(chunk.value && Number.isSafeInteger(start.value) && Number.isSafeInteger(end.value)
  && start.value >= 0 && end.value > start.value && end.value <= Array.from(chunk.value.raw_text).length))
const selectedText = computed(() => validRange.value && chunk.value ? Array.from(chunk.value.raw_text).slice(start.value, end.value).join('') : '')
function clearSelection(): void {
  generation++; controller?.abort(); controller = null; working.value = false
  parsed.value = null; manifest.value = null; receipt.value = null; manifestHash.value = ''; fields.value = []
  chunkId.value = ''; cursors.value = []; expectedRevision.value = ''; reason.value = ''; attested.value = false; fixedRevision.value = ''
}
function clear(): void { clearSelection(); view.value = null; projectId.value = ''; reviewId.value = ''; pending.value = null; lookupState.value = ''; recoveryJson.value = '' }
function begin() {
  const session: MedicalSession | null = user.medicalSession
  if (!active.value || document.hidden || !session?.current_context) fail('ACCESS_DENIED', '字段操作需要当前可见页面及企业身份')
  controller?.abort(); controller = new AbortController(); generation++
  return { session, target: sourceVersion.value, signal: controller.signal, version: generation }
}
function valid(work: ReturnType<typeof begin>): boolean { return active.value && !document.hidden && !work.signal.aborted
  && work.version === generation && user.medicalSession === work.session && sourceVersion.value === work.target }
async function load(): Promise<void> {
  clear(); if (!active.value || document.hidden || !user.medicalSession?.current_context) return
  const work = begin(); working.value = true
  try { const value = await readSourceGovernance(work.session, work.target, work.signal)
    if (valid(work)) { view.value = value; pending.value = readRequestMetadata(work.session, 'KN-EXACT-PUBLISH', work.target) }
  } finally { if (work.version === generation) working.value = false }
}
async function readFields(): Promise<void> {
  if (busy.value || !scopeReady.value || !review.value) return
  const work = begin(), project = projectId.value, selected = review.value; working.value = true
  try { const value = await readExactFields(work.session, work.target, project, fixedRevision.value
    ? { field_revision: fixedRevision.value } : { parse_version: selected.parse_version, approval_revision: selected.approval_revision! }, work.signal)
    const hash = await sourceDigest(value)
    if (valid(work)) { manifest.value = value; manifestHash.value = hash }
  } finally { if (work.version === generation) working.value = false }
}
function useManifest(): void {
  if (busy.value || !mayPublish.value || !manifest.value || !view.value || !review.value || !binding.value) return
  const input = manifest.value.request
  if (input.parse_version !== review.value.parse_version || input.index_version !== review.value.index_version
      || input.approval_revision !== review.value.approval_revision || input.binding_revision !== binding.value.binding_revision
      || input.rights_revision !== view.value.rights_revision) fail('SCOPE_STALE', '读取清单的全部固定依据与当前选择不同')
  fields.value = JSON.parse(canonicalizeEx(input.fields)); expectedRevision.value = manifest.value.revision; reason.value = ''; attested.value = false
}
async function readParsed(after?: string): Promise<void> {
  if (busy.value || !scopeReady.value || !review.value || !view.value) return
  const work = begin(), original = view.value; working.value = true
  try { const value = await readSourceContent(work.session, work.target, { representation: 'PARSED', parse_version: review.value.parse_version,
    purpose: original.rights_purpose, limit: 20, ...(after === undefined ? {} : { after_ordinal: after }) }, work.signal)
    if (value.representation !== 'PARSED' || value.source_id !== original.source_id || value.original_sha256 !== original.original_sha256) fail('CONTENT_MISMATCH', '字段原文与固定来源不一致')
    if (valid(work)) { parsed.value = value; chunkId.value = ''; if (cursors.value.at(-1) !== after || cursors.value.length === 0) cursors.value.push(after) }
  } finally { if (work.version === generation) working.value = false }
}
async function previousParsed(): Promise<void> { cursors.value.pop(); const previous = cursors.value.pop(); await readParsed(previous) }
function addField(): void {
  if (busy.value || !mayPublish.value || !chunk.value || !validRange.value) return
  const item = chunk.value, field: ExactField = { field_kind: kind.value, raw_value: selectedText.value, alias_value: null, alias_reason: null,
    chunk_id: item.chunk_id, quote_hash: item.quote_hash, locator_sha256: item.locator_sha256, start_codepoint: start.value, end_codepoint: end.value }
  verifyExactField(field); fields.value.push(field); attested.value = false
}
async function verifySaved(operationId: string, fieldRevision: string, metadata: RequestMetadata,
    original: MedicalSession, target: string, expected?: ExactReceipt): Promise<void> {
  if (!active.value || document.hidden || sourceVersion.value !== target || user.medicalSession !== original) return
  const fresh = await user.readMedical() as MedicalSession | null
  if (!active.value || document.hidden || sourceVersion.value !== target || !fresh?.current_context
      || fresh.user.user_id !== original.user.user_id || fresh.current_context.tenant_id !== metadata.tenant_id) return
  const work = begin(); working.value = true
  try {
    const operation = await readOperation(fresh, operationId, work.signal)
    const actualSource = await readSourceGovernance(fresh, target, work.signal)
    const value = await readExactFields(fresh, target, metadata.scope_id, { field_revision: fieldRevision }, work.signal)
    const hash = await sourceDigest(value)
    if (metadata.body_sha256 !== await requestBodyDigest(value.request) || expected && (expected.manifest_sha256 !== hash
        || canonicalizeEx(expected.manifest) !== canonicalizeEx(value))) fail('CONTENT_MISMATCH', '原字段操作与完整固定输入及清单不一致')
    if (!valid(work)) return
    confirmRequestMetadata(fresh, metadata, operation, fieldRevision)
    if (actualSource.original_sha256 !== value.original_sha256 || actualSource.source_id !== metadata.resource_id) fail('CONTENT_MISMATCH', '字段固定清单与当前来源归属不同')
    view.value = actualSource; projectId.value = metadata.scope_id
    reviewId.value = actualSource.reviews.find(item => item.parse_version === value.request.parse_version
      && item.index_version === value.request.index_version && item.approval_revision === value.request.approval_revision)?.review_manifest_sha256 ?? ''
    pending.value = null; recoveryJson.value = ''; manifest.value = value; manifestHash.value = hash; receipt.value = expected ?? null
  } finally { if (work.version === generation) working.value = false }
}
async function send(input: ExactPublication): Promise<void> {
  if (!view.value) fail('ACCESS_DENIED', '字段核准需要当前固定来源读取结果')
  verifyExactPublication(input); const work = begin(); working.value = true; user.medicalWriting = true
  try {
    const metadata = await prepareRequestMetadata(work.session, 'KN-EXACT-PUBLISH', work.target, view.value!.source_id, input.project_id, input, work.signal)
    pending.value = metadata
    const lookup = await lookupOperation(work.session, metadata.operation, metadata.key, work.signal)
    if (lookup.lookup_status === 'EXPIRED') { expireRequestMetadata(work.session, metadata); pending.value = readRequestMetadata(work.session, metadata.operation, work.target); return }
    if (lookup.operation !== null) {
      if (lookup.operation.status !== 'COMPLETED' || !lookup.operation.resource_id) fail('ACTION_STALE', '字段原操作尚未完成')
      await verifySaved(lookup.operation.operation_id, lookup.operation.resource_id, metadata, work.session, work.target); return
    }
    const value = await publishExactFields(work.session, work.target, input, metadata, work.signal)
    await verifySaved(value.operation_id, value.field_revision, metadata, work.session, work.target, value)
  } finally { user.medicalWriting = false; working.value = false }
}
async function submit(): Promise<void> {
  if (busy.value || !mayPublish.value || !attested.value || !review.value || !binding.value || !view.value) return
  await send(JSON.parse(canonicalizeEx({ schema_version: '1.0.0', project_id: projectId.value, parse_version: review.value.parse_version,
    index_version: review.value.index_version, approval_revision: review.value.approval_revision, binding_revision: binding.value.binding_revision,
    rights_revision: view.value.rights_revision, expected_revision: expectedRevision.value, fields: fields.value, reason: reason.value })))
}
async function recover(): Promise<void> {
  if (busy.value || !pending.value) return
  const metadata = pending.value, work = begin(); working.value = true
  try {
    const lookup = await lookupOperation(work.session, metadata.operation, metadata.key, work.signal)
    if (!valid(work)) return; lookupState.value = lookup.lookup_status
    if (lookup.lookup_status === 'EXPIRED') { expireRequestMetadata(work.session, metadata); pending.value = readRequestMetadata(work.session, metadata.operation, work.target) }
    if (lookup.operation?.status === 'COMPLETED' && lookup.operation.resource_id) await verifySaved(lookup.operation.operation_id, lookup.operation.resource_id, metadata, work.session, work.target)
  } finally { working.value = false }
}
async function resend(): Promise<void> {
  if (busy.value || !pending.value || !recoveryJson.value) return
  const input = JSON.parse(recoveryJson.value) as ExactPublication
  verifyExactPublication(input)
  if (await requestBodyDigest(input) !== pending.value.body_sha256) fail('IDEMPOTENCY_CONFLICT', '完整恢复输入与原字段请求摘要不同')
  await send(input)
}
async function discardExpired(): Promise<void> { if (!busy.value && pending.value && user.medicalSession) { clearExpiredRequestMetadata(user.medicalSession, pending.value); await load() } }
watch(() => [user.medicalSession, sourceVersion.value, active.value], load, { immediate: true, flush: 'sync' })
watch(fields, () => { attested.value = false }, { deep: true })
function visibility(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', visibility); window.addEventListener('medical-context-invalidated', clear)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('medical-context-invalidated', clear) })
</script>

<style scoped>
section, form { margin-top: 24px; }
.el-select { width: 100%; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 400px; overflow: auto; }
.literal { white-space: pre-wrap; border: 1px solid var(--el-border-color); padding: 4px; }
</style>

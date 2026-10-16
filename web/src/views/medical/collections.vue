<template>
  <main class="medical-page">
    <h1>资料集与固定版本</h1>
    <session-panel />
    <router-link v-if="canSearch" to="/library/search">进入资料全文检索</router-link>
    <router-link v-if="canUpload" to="/projects">从项目详情上传资料</router-link>
    <p v-if="!permitted">资料集操作需要当前企业的明确权限</p>
    <p v-else-if="creating && !canCreate">创建资料集需要当前归属的维护和读取权限</p>
    <el-form v-if="!collectionId && !creating" :inline="true" @submit.prevent="open">
      <el-form-item label="资料集编号" :error="errors.target"><el-input v-model="target" :disabled="busy || !canRead" /></el-form-item>
      <el-button native-type="submit" :disabled="busy || !canRead">打开资料集</el-button>
      <el-button :disabled="busy || !canCreate" @click="router.push('/library/collections/new')">创建资料集</el-button>
    </el-form>
    <el-alert v-if="pending" title="原资料集请求待核查" type="warning" :closable="false" data-collection-pending>
      <p>原请求标识：{{ pending.key }}；原上下文修订：{{ pending.context.context_revision }}</p>
      <p>成员、名称、用途和理由只保存在当前页面；刷新以后需要重新填写完整原参数。</p>
      <el-button :disabled="busy" @click="lookup">查询原资料集操作</el-button>
      <p v-if="lookupResult">{{ lookupLabels[lookupResult.lookup_status] }}</p>
      <p v-if="lookupResult?.operation">操作编号：{{ lookupResult.operation.operation_id }}；状态：{{ operationLabels[lookupResult.operation.status] }}</p>
      <p v-if="lookupResult?.operation?.resource_id">原资料集版本：{{ lookupResult.operation.resource_id }}</p>
      <el-button v-if="collectionId && !editing" :disabled="busy || !canWrite" @click="editOriginal">填写原请求完整参数</el-button>
    </el-alert>
    <section v-if="created && permitted" aria-label="资料集保存回执" data-collection-created>
      <el-alert title="资料集固定版本已保存并核查完成操作" type="success" :closable="false" />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="资料集编号">{{ created.collection.manifest.collection_id }}</el-descriptions-item>
        <el-descriptions-item label="固定版本">{{ created.collection.manifest.collection_version }}</el-descriptions-item>
        <el-descriptions-item label="内容摘要">{{ created.collection.content_sha256 }}</el-descriptions-item>
        <el-descriptions-item label="操作编号">{{ created.operation_id }}</el-descriptions-item>
      </el-descriptions>
      <router-link :to="'/library/collections/' + created.collection.manifest.collection_id">读取资料集固定历史</router-link>
      <el-collapse><el-collapse-item title="完整保存回执" name="created"><pre>{{ full(created) }}</pre></el-collapse-item></el-collapse>
    </section>
    <p v-if="loading" role="status">正在读取当前获授权的固定版本</p>
    <section v-if="history && canRead" aria-label="资料集历史" data-collection-history>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="名称">{{ history.title }}</el-descriptions-item>
        <el-descriptions-item label="归属">{{ history.owner_project_id ?? '企业公共资料集' }}</el-descriptions-item>
        <el-descriptions-item label="当前固定版本">{{ history.current_version }}</el-descriptions-item>
        <el-descriptions-item label="当前修订">{{ history.revision }}</el-descriptions-item>
      </el-descriptions>
      <p>任务继续引用创建时的固定资料集版本。修改成员会保存新版本，已有成员历史可以独立核查。</p>
      <el-button :disabled="busy || editing" @click="reloadHistory">重新读取当前历史</el-button>
      <el-button :disabled="busy || editing || !canWrite || pending !== null" @click="editCurrent">根据当前版本修订成员</el-button>
      <el-table :data="history.items" row-key="content_sha256">
        <el-table-column label="修订" min-width="90"><template #default="scope">{{ scope.row.manifest.revision }}</template></el-table-column>
        <el-table-column label="固定版本" min-width="240"><template #default="scope">{{ scope.row.manifest.collection_version }}</template></el-table-column>
        <el-table-column label="用途" min-width="150"><template #default="scope">{{ scope.row.manifest.purpose }}</template></el-table-column>
        <el-table-column label="成员数量" min-width="100"><template #default="scope">{{ scope.row.manifest.members.length }}</template></el-table-column>
        <el-table-column label="维护人员" min-width="220"><template #default="scope">{{ scope.row.manifest.created_by }}</template></el-table-column>
        <el-table-column label="保存时间（UTC）" min-width="220"><template #default="scope">{{ scope.row.manifest.created_at }}</template></el-table-column>
        <el-table-column label="查看" min-width="120"><template #default="scope"><el-button :disabled="busy" @click="selected = scope.row">完整版本</el-button></template></el-table-column>
      </el-table>
      <el-pagination :current-page="history.page_info.page" :page-size="history.page_info.page_size" :total="total"
        layout="total, prev, pager, next" :disabled="busy || editing" @current-change="changePage" />
    </section>
    <section v-if="selected && canRead" aria-label="完整资料集版本">
      <h2>固定成员与来源</h2>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="名称">{{ selected.manifest.title }}</el-descriptions-item>
        <el-descriptions-item label="用途">{{ selected.manifest.purpose }}</el-descriptions-item>
        <el-descriptions-item label="维护理由">{{ selected.manifest.reason }}</el-descriptions-item>
        <el-descriptions-item label="原版本">{{ selected.manifest.base_version?.version_id ?? '独立建立' }}</el-descriptions-item>
        <el-descriptions-item label="固定内容摘要">{{ selected.content_sha256 }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-if="selected.manifest.members.length === 0" description="当前固定版本没有成员，任务不能据此扩大资料范围" />
      <el-table :data="selected.manifest.members" row-key="source_version">
        <el-table-column prop="source_id" label="来源编号" min-width="240" />
        <el-table-column prop="source_version" label="来源固定版本" min-width="240" />
        <el-table-column prop="parse_version" label="解析固定版本" min-width="240" />
        <el-table-column prop="rights_revision" label="资料权利版本" min-width="240" />
        <el-table-column prop="original_sha256" label="原件摘要" min-width="300" />
        <el-table-column prop="parse_sha256" label="解析内容摘要" min-width="300" />
      </el-table>
      <el-collapse><el-collapse-item title="完整版本清单" name="version"><pre>{{ full(selected) }}</pre></el-collapse-item></el-collapse>
    </section>
    <el-form v-if="(creating && canCreate || editing && canWrite) && !created" ref="formElement" label-position="top" data-collection-form @submit.prevent="submit">
      <h2>{{ creating ? '创建资料集' : '保存新的完整成员版本' }}</h2>
      <p>来源与解析使用明确的固定版本。服务器重新核查当前权限、原件、解析结果及资料权利。</p>
      <template v-if="creating">
        <el-form-item label="资料集归属" :error="errors.owner">
          <el-radio-group v-model="form.owner_mode" :disabled="busy || pending !== null"><el-radio value="ENTERPRISE">企业公共资料</el-radio><el-radio value="PROJECT">指定项目资料</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.owner_mode === 'PROJECT'" label="项目编号" :error="errors.owner"><el-input v-model="form.project" :disabled="busy || pending !== null" /></el-form-item>
      </template>
      <template v-else>
        <p>原修订：{{ baseRevision }}；原固定版本：{{ base?.version_id }}；原内容摘要：{{ base?.content_sha256 }}</p>
      </template>
      <el-form-item label="资料集名称" :error="errors.title"><el-input v-model="form.title" maxlength="256" :disabled="busy" /></el-form-item>
      <el-form-item label="批准的使用用途" :error="errors.purpose"><el-input v-model="form.purpose" :disabled="busy" /><p>填写资料授权规定的用途，保存时核查对应使用条件。</p></el-form-item>
      <h3>完整成员集合</h3>
      <el-table :data="form.members" data-collection-members>
        <el-table-column label="来源固定版本" min-width="240"><template #default="scope"><el-input v-model="scope.row.source_version" :disabled="busy" :aria-label="'第' + (scope.$index + 1) + '项来源固定版本'" /></template></el-table-column>
        <el-table-column label="解析固定版本" min-width="240"><template #default="scope"><el-input v-model="scope.row.parse_version" :disabled="busy" :aria-label="'第' + (scope.$index + 1) + '项解析固定版本'" /></template></el-table-column>
        <el-table-column label="操作" min-width="100"><template #default="scope"><el-button :disabled="busy" @click="form.members.splice(scope.$index, 1)">移除成员</el-button></template></el-table-column>
      </el-table>
      <p v-if="errors.members" role="alert">{{ errors.members }}</p>
      <el-empty v-if="form.members.length === 0" description="明确选择没有成员，保存后继续保持空范围" />
      <el-button :disabled="busy" @click="form.members.push({ source_version: '', parse_version: '' })">添加固定成员</el-button>
      <el-upload :auto-upload="false" :show-file-list="false" accept=".json,application/json" :disabled="busy" :on-change="importBody"><el-button :disabled="busy">导入完整原请求参数</el-button></el-upload>
      <el-form-item label="维护理由" :error="errors.reason"><el-input v-model="form.reason" type="textarea" maxlength="2000" :disabled="busy" /></el-form-item>
      <el-form-item label="完整成员确认" :error="errors.attested"><el-checkbox v-model="attested" :disabled="busy">已经核查全部成员、固定版本、用途和资料归属</el-checkbox></el-form-item>
      <el-alert v-if="errors.schema" :title="errors.schema" type="error" :closable="false" />
      <el-button type="primary" native-type="submit" :disabled="busy || !maySubmit">{{ pending ? '使用原请求保存完整版本' : '保存完整资料集版本' }}</el-button>
      <p v-if="pending && !mayReplay">原请求的上下文或查询状态需要核查，禁止自动重发或创建新的请求。</p>
    </el-form>
    <el-dialog v-model="discardDialog" title="离开资料集编辑" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <p>未保存成员、用途和理由会清除。已经发送的请求身份继续保留，离开不会取消服务器操作。</p>
      <template #footer><el-button @click="discardDialog = false">继续编辑</el-button><el-button :disabled="user.medicalWriting" @click="discard">放弃未保存编辑并离开</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { FormInstance, UploadFile } from 'element-plus'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import SessionPanel from './session-panel.vue'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { Basis, MedicalSession } from '@/api/medical/protocol'
import { readBoundedJson } from '@/api/medical/project-json'
import { projectTextIssue } from '@/api/medical/project-write'
import { canonicalTemplateValue } from '@/api/medical/template-approval'
import { lookupOperation } from '@/api/medical/operations'
import type { OperationLookup } from '@/api/medical/operations'
import { collectionAllowed, confirmCollection, prepareCollection, readCollectionPage, readPendingCollection, submitCollection, validateCollectionBody } from '@/api/medical/collections'
import type { CollectionBody, CollectionCreated, CollectionPage, CollectionSelection, CollectionView, PendingCollection } from '@/api/medical/collections'

const user = useUserStore(); const route = useRoute(); const router = useRouter(); const formElement = ref<FormInstance>()
const creating = computed(() => route.meta.collectionCreate === true)
const canUpload = computed(() => {
  const session: MedicalSession | null = user.medicalSession
  return session?.action_summary.some(item => item.action === 'source.write') === true
})
const canSearch = computed(() => {
  const session: MedicalSession | null = user.medicalSession
  return session?.action_summary.some(item => item.action === 'source.read') === true
})
const collectionId = computed(() => typeof route.params.collectionId === 'string' ? route.params.collectionId : null)
const canRead = computed(() => collectionAllowed(user.medicalSession, 'collection.read'))
const canCreate = computed(() => collectionAllowed(user.medicalSession, 'collection.write') && canRead.value)
const permitted = computed(() => canRead.value || collectionAllowed(user.medicalSession, 'collection.write'))
const canWrite = computed(() => history.value !== null && collectionAllowed(user.medicalSession, 'collection.write', history.value.owner_project_id)
  && collectionAllowed(user.medicalSession, 'collection.read', history.value.owner_project_id))
const history = ref<CollectionPage | null>(null); const selected = ref<CollectionView | null>(null); const target = ref('')
const pending = ref<PendingCollection | null>(null); const created = ref<CollectionCreated | null>(null); const lookupResult = ref<OperationLookup | null>(null)
const loading = ref(false); const checking = ref(false); const editing = ref(false); const attested = ref(false); const discardDialog = ref(false)
const base = ref<Basis | null>(null); const baseRevision = ref<string | null>(null)
const form = reactive({ owner_mode: '', project: '', title: '', purpose: '', members: [] as CollectionSelection[], reason: '' })
const errors = reactive<Record<string, string>>({})
const busy = computed(() => user.medicalWriting || loading.value || checking.value)
const mayReplay = computed(() => pending.value === null || user.medicalSession?.current_context
  && canonicalTemplateValue(user.medicalSession.current_context) === canonicalTemplateValue(pending.value.context)
  && lookupResult.value?.lookup_status !== 'EXPIRED' && lookupResult.value?.operation?.status !== 'PROCESSING' && lookupResult.value?.operation?.status !== 'FAILED')
const ownerProject = computed(() => creating.value ? form.owner_mode === 'ENTERPRISE' ? null : form.project : history.value?.owner_project_id)
const maySubmit = computed(() => mayReplay.value && ownerProject.value !== undefined && (!creating.value || form.owner_mode !== '')
  && collectionAllowed(user.medicalSession, 'collection.write', ownerProject.value) && collectionAllowed(user.medicalSession, 'collection.read', ownerProject.value))
const total = computed(() => {
  if (!history.value) return 0
  const count = BigInt(history.value.page_info.total)
  if (count > BigInt(Number.MAX_SAFE_INTEGER)) fail('CONTEXT_LIMIT', '资料集历史数量超出当前组件可准确表示范围')
  return Number(count)
})
const lookupLabels = { FOUND: '已经找到原操作，需要完整原参数核对回执', NOT_FOUND: '当前没有查询到原操作，不能据此判定请求没有发送', EXPIRED: '原记录已经过期，需要人工核查，禁止自动重发' }
const operationLabels = { PROCESSING: '正在执行', COMPLETED: '已经完成', FAILED: '执行失败，原请求继续保留并需要核查' }
let controller: AbortController | null = null; let generation = 0; let nextTarget: string | null = null; let leaveApproved = false
function full(value: unknown): string { return JSON.stringify(value, null, 2) }
function clearForm(): void {
  form.owner_mode = ''; form.project = ''; form.title = ''; form.purpose = ''; form.members = []; form.reason = ''
  attested.value = false; editing.value = false; base.value = null; baseRevision.value = null
  for (const key of Object.keys(errors)) delete errors[key]
}
function clear(): void {
  generation += 1; controller?.abort(); controller = null; history.value = null; selected.value = null
  pending.value = null; created.value = null; lookupResult.value = null; loading.value = false; checking.value = false
  discardDialog.value = false; target.value = ''; clearForm()
}
async function read(page = 1, previous: CollectionPage | null = null): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const id = collectionId.value; const current = generation
  if (!session?.current_context || !canRead.value || !id || document.hidden) return
  controller?.abort(); controller = new AbortController(); const signal = controller.signal
  loading.value = true; selected.value = null
  try {
    const result = await readCollectionPage(session, id, page, 20, previous, signal)
    if (current !== generation || user.medicalSession !== session || document.hidden || signal.aborted || controller?.signal !== signal) return
    history.value = result
  } finally { if (current === generation && controller?.signal === signal) loading.value = false }
}
async function reset(): Promise<void> {
  clear(); const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || document.hidden || !permitted.value) return
  const id = collectionId.value
  if (id !== null && schemaIssues('urn:medical:templates:1.0.0#/$defs/Id', id).length) fail('SCHEMA_INVALID', '资料集编号无效')
  if (creating.value || id !== null) {
    pending.value = readPendingCollection(session, id)
    if (pending.value && creating.value) { form.owner_mode = pending.value.owner_project_id === null ? 'ENTERPRISE' : 'PROJECT'; form.project = pending.value.owner_project_id ?? '' }
  }
  if (!user.medicalWriting) await read()
}
async function open(): Promise<void> {
  if (busy.value || !canRead.value) return
  errors.target = schemaIssues('urn:medical:templates:1.0.0#/$defs/Id', target.value).length ? '请输入实际资料集编号' : ''
  if (!errors.target) await router.push('/library/collections/' + target.value)
}
async function reloadHistory(): Promise<void> { if (!busy.value && !editing.value) { history.value = null; await read() } }
async function changePage(page: number): Promise<void> { if (!busy.value && !editing.value && history.value) await read(page, history.value) }
async function editCurrent(): Promise<void> {
  if (busy.value || !canWrite.value || pending.value) return
  const current = generation; await read()
  if (current !== generation || !history.value) return
  const view = history.value.items.find(item => item.manifest.collection_version === history.value?.current_version)
  if (!view) fail('CONTENT_MISMATCH', '当前资料集版本需要完整读取才能修订')
  clearForm(); form.title = view.manifest.title; form.purpose = view.manifest.purpose
  form.members = view.manifest.members.map(member => ({ source_version: member.source_version, parse_version: member.parse_version }))
  base.value = { version_id: view.manifest.collection_version, content_sha256: view.content_sha256 }; baseRevision.value = history.value.revision; editing.value = true
}
function editOriginal(): void {
  if (busy.value || !pending.value || !canWrite.value) return
  clearForm(); base.value = pending.value.base_version; baseRevision.value = pending.value.expected_revision; editing.value = true
}
async function focusError(): Promise<void> {
  await nextTick(); const element = (formElement.value?.$el as HTMLElement | undefined)?.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea')
  element?.scrollIntoView({ block: 'nearest' }); element?.focus()
}
async function body(): Promise<CollectionBody | null> {
  for (const key of Object.keys(errors)) delete errors[key]
  errors.title = projectTextIssue(form.title, 256); errors.reason = projectTextIssue(form.reason, 2000)
  errors.purpose = schemaIssues('urn:medical:collections:1.0.0#/$defs/Content', { schema_version: '1.0.0', title: form.title, purpose: form.purpose, members: form.members })
    .some(issue => issue.path === '/purpose') ? '请输入授权规定的使用用途' : ''
  errors.members = form.members.some(member => schemaIssues('urn:medical:collections:1.0.0#/$defs/MemberSelection', member).length)
    || new Set(form.members.map(member => member.source_version)).size !== form.members.length ? '全部成员必须使用有效固定版本，同一来源版本不能重复' : ''
  errors.owner = creating.value && (!['ENTERPRISE', 'PROJECT'].includes(form.owner_mode)
    || form.owner_mode === 'PROJECT' && schemaIssues('urn:medical:templates:1.0.0#/$defs/Id', form.project).length) ? '请选择明确归属并填写有效项目编号' : ''
  if (Object.values(errors).some(Boolean)) { await focusError(); return null }
  const content = { schema_version: '1.0.0', title: form.title, purpose: form.purpose, members: form.members.map(member => ({ ...member })) }
  const value = creating.value ? { schema_version: '1.0.0', owner_project_id: ownerProject.value, content, reason: form.reason }
    : { schema_version: '1.0.0', expected_revision: baseRevision.value, base_version: base.value,
      schema_ref: 'urn:medical:collections:1.0.0#/$defs/Content', content, reason: form.reason }
  const issues = schemaIssues('urn:medical:collections:1.0.0#/$defs/' + (creating.value ? 'Create' : 'VersionInput'), value)
  if (issues.length) { errors.schema = '完整资料集参数未通过字段规范，请核查全部必需内容'; return null }
  return validateCollectionBody(value, collectionId.value)
}
async function importBody(file: UploadFile): Promise<void> {
  const session = user.medicalSession; const current = generation; if (busy.value || !session || document.hidden) return
  if (!file.raw) fail('SCHEMA_INVALID', '资料集导入缺少实际文件')
  const value = validateCollectionBody(readBoundedJson(await file.raw.text()), collectionId.value)
  if (current !== generation || user.medicalSession !== session || document.hidden) return
  if ('owner_project_id' in value) {
    if (pending.value && value.owner_project_id !== pending.value.owner_project_id) fail('CONTENT_MISMATCH', '导入参数与原资料集归属不一致')
    form.owner_mode = value.owner_project_id === null ? 'ENTERPRISE' : 'PROJECT'; form.project = value.owner_project_id ?? ''
  } else {
    if (pending.value && (value.expected_revision !== pending.value.expected_revision || canonicalTemplateValue(value.base_version) !== canonicalTemplateValue(pending.value.base_version))
        || !pending.value && (value.expected_revision !== baseRevision.value || canonicalTemplateValue(value.base_version) !== canonicalTemplateValue(base.value))) {
      fail('CONTENT_MISMATCH', '导入参数与当前修订依据不一致')
    }
  }
  form.title = value.content.title; form.purpose = value.content.purpose; form.members = value.content.members.map(member => ({ ...member })); form.reason = value.reason; attested.value = false
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value; const current = generation
  if (!session?.current_context || !original || busy.value || document.hidden) return
  checking.value = true
  try {
    const result = await lookupOperation(session, original.operation, original.key, new AbortController().signal)
    if (current === generation && user.medicalSession === session && !document.hidden) lookupResult.value = result
  } finally { if (current === generation) checking.value = false }
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const current = generation; const path = route.fullPath; const project = ownerProject.value
  if (!session?.current_context || busy.value || !maySubmit.value || project === undefined || document.hidden) return
  const value = await body(); errors.attested = attested.value ? '' : '请核查完整成员及归属以后确认'
  if (!value || errors.attested) { await focusError(); return }
  if (current !== generation || user.medicalSession !== session) return
  user.medicalWriting = true
  try {
    const original = await prepareCollection(session, collectionId.value, project, value); pending.value = original
    if (current !== generation || user.medicalSession !== session || document.hidden) fail('CONTEXT_CHANGED', '资料集请求尚未发送，当前身份需要核查')
    const result = await submitCollection(session, original, value)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id) fail('CONTEXT_CHANGED', '资料集已经返回，当前主体或企业需要核查')
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    const reloaded = await submitCollection(fresh, original, value)
    if (canonicalTemplateValue(result) !== canonicalTemplateValue(reloaded)) fail('CONTENT_MISMATCH', '资料集原响应在重新核查以后不一致')
    await confirmCollection(fresh, original, reloaded, new AbortController().signal)
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    clearForm(); pending.value = null; lookupResult.value = null; created.value = reloaded
    if (collectionId.value) await read()
  } finally { user.medicalWriting = false }
}
function dirty(): boolean { return Boolean(form.owner_mode || form.project || form.title || form.purpose || form.members.length || form.reason || attested.value) }
async function discard(): Promise<void> {
  const destination = nextTarget; clearForm(); discardDialog.value = false; nextTarget = null
  if (destination !== null) { leaveApproved = true; await router.push(destination); leaveApproved = false }
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty()) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(to => {
  if (leaveApproved || !dirty()) return true
  if (user.medicalWriting) return false
  nextTarget = to.fullPath; discardDialog.value = true; return false
})
watch(() => user.medicalSession, reset, { immediate: true, flush: 'sync' })
watch(() => route.fullPath, reset)
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); clear() })
</script>

<style scoped>
pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 16px; }
</style>

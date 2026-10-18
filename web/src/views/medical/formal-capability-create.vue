<template>
 <main ref="root" class="medical-page capability-page">
  <nav class="breadcrumb" aria-label="位置"><router-link to="/agents">Agent 管理</router-link><span>/</span><router-link to="/agents/capabilities">正式能力</router-link><span>/ 登记草稿</span></nav>
  <header class="page-heading"><div><h1>登记正式能力草稿</h1><p>绑定真实来源成果、固定契约与明确权限声明，保存可追溯的不可变版本。</p></div><el-tag type="warning">PRODUCTION · DRAFT</el-tag></header>
  <session-panel />
  <el-alert type="info" :closable="false" title="本页仅登记专业角色与提示词" description="登记不会发布 Agent、安装 Skill、运行工具或扩大授权。即使声明模式为 PRODUCTION，草稿仍不具备生产执行资格。" />
  <el-alert v-if="error" ref="errorPanel" class="spaced" type="error" :closable="false" :title="error" role="alert" tabindex="-1" />
  <p v-if="loading" role="status">正在核查当前身份与真实候选…</p>
  <section v-if="availability === false" class="card spaced" role="status"><h2>当前部署未启用正式能力登记</h2><p>请由管理员确认正式能力作者服务与真实制品配置。可用性只表示服务部署状态，不授予业务权限。</p></section>
  <div class="actions"><el-button :disabled="busy" @click="bootstrap">重新读取当前状态</el-button><router-link to="/agents/capabilities">返回能力目录</router-link></div>
  <section v-if="pending" class="card pending spaced" aria-label="原能力请求恢复">
   <div class="section-heading"><h2>原请求待核查</h2><el-tag type="warning">{{ pending.phase === 'UNKNOWN' ? '结果待确认' : '已保存原请求' }}</el-tag></div>
   <p>完整原声明和请求标识已保留。本页只读取原结果，不重发请求、不生成新键，也不会从列表猜测结果。</p>
   <p class="identifier">原请求：{{ pending.key }} · 原上下文：{{ pending.context.context_revision }}</p>
   <el-button type="primary" :disabled="busy || availability !== true || !!cancelPending" @click="recover">核查原操作与类型回执</el-button>
   <p v-if="lookup" role="status">{{ lookupMessage }}</p>
   <div class="spaced" v-if="!cancelReceipt">
    <p>如原声明不能继续登记，可明确取消该原请求。服务端将原子核查并阻断迟到写入；已登记的草稿不会被撤销。</p>
    <el-checkbox v-model="cancelConfirmed" :disabled="busy || !canCancel">我确认取消这份原请求；未知结果仍保留全部请求标识</el-checkbox>
    <div class="actions"><el-button type="warning" :disabled="busy || availability !== true || !canCancel || !cancelConfirmed" @click="cancelOriginal">{{ cancelPending ? '按原取消键继续核查并提交' : '取消尚未登记的原请求' }}</el-button><el-button v-if="cancelPending" :disabled="busy || availability !== true" @click="recoverCancel">只读核查取消结果</el-button></div>
    <p v-if="cancelPending">取消请求已保存。未查到不表示已取消，也不会自动重发或换键。</p><details v-if="cancelPending"><summary>查看取消请求标识</summary><p class="identifier">{{ cancelPending.key }}</p><p>此标识与完整原声明一并保留，继续提交将使用同一取消请求。</p></details>
   </div>
   <div v-if="cancelReceipt" class="spaced" role="status"><h3>{{ cancelReceipt.status === 'CANCELLED' ? '原请求已被安全阻断' : '原请求已经登记' }}</h3><p>{{ cancelReceipt.status === 'CANCELLED' ? '保存取消证明与原声明后，可关闭这份请求并重新填写。' : '现有草稿保持不变。此处仅返回原操作回执，不替代对草稿内容的读取授权。' }}</p><el-button :disabled="busy" @click="exportCancellation">保存证明与原声明</el-button><el-checkbox v-model="cancelAcknowledged" :disabled="!cancelExported || busy">我已保存证明与原声明，确认关闭此请求</el-checkbox><el-button :disabled="busy || !cancelExported || !cancelAcknowledged" @click="finishCancellation">关闭原请求</el-button></div>
   <details><summary>查看完整原声明</summary><formal-capability-definition :body="pending.body" /></details>
  </section>
  <section v-if="saved" class="card result spaced" aria-label="能力保存结果" role="status"><h2>固定草稿已核验</h2><p>原操作、类型回执、完整声明与固定内容摘要一致。该草稿未获得发布或执行资格。</p><p class="identifier">固定版本 {{ saved.resource_id }}</p><router-link :to="'/agents/capabilities/' + saved.record_id"><el-button type="primary">查看固定草稿详情</el-button></router-link></section>
  <el-form v-if="availability === true && !pending" class="card spaced" label-position="top" :disabled="busy" @submit.prevent="submit">
   <div class="form-grid">
    <el-form-item label="能力类型" for="formal-capability-kind"><el-select id="formal-capability-kind" v-model="kind"><el-option value="ROLE" label="专业角色 ROLE"/><el-option value="PROMPT" label="提示词 PROMPT"/></el-select></el-form-item>
    <el-form-item label="归属范围" for="formal-capability-project"><el-select id="formal-capability-project" v-model="project" filterable><el-option value="" label="当前企业"/><el-option v-for="item in projects" :key="item.project_id" :value="item.project_id" :label="item.name"/></el-select><div class="field-actions"><el-button link @click="loadProjects(false)">刷新项目</el-button><el-button v-if="projectCursor" link @click="loadProjects(true)">读取更多项目</el-button></div></el-form-item>
   </div>
   <el-form-item label="已登记来源成果候选" for="formal-capability-artifact"><el-select id="formal-capability-artifact" v-model="artifactId" filterable placeholder="从真实可读成果目录选择"><el-option v-for="item in artifacts" :key="item.artifact_id" :value="item.artifact_id" :label="item.artifact_kind + ' · ' + item.created_at + ' · ' + item.artifact_id"/></el-select><div class="field-actions"><el-button link @click="loadArtifacts(false)">读取来源成果候选</el-button><el-button v-if="artifactCursor" link @click="loadArtifacts(true)">读取更多候选</el-button></div><p>目录只确认当前可读成果元数据。当前没有正式包资格预检接口；提交时服务端重新核查真实制品配置、完整来源权利与 AGENT_PUBLICATION 用途。不能选择未登记文件或手填成果编号。</p><p v-if="artifactsLoaded && !artifacts.length" role="status">此页没有当前范围的来源成果候选。{{ artifactCursor ? '可继续读取下一页。' : '请先通过已支持的制品登记流程准备真实来源。' }}</p><small v-if="selectedArtifact" class="identifier">固定 SHA-256：{{ selectedArtifact.sha256 }}</small></el-form-item>
   <div class="form-grid">
    <el-form-item label="输入契约" for="formal-capability-input"><el-select id="formal-capability-input" v-model="inputSchema" filterable placeholder="选择已发布契约"><el-option v-for="item in capabilitySchemas" :key="item.schema_ref" :value="item.schema_ref" :label="item.label"/></el-select></el-form-item>
    <el-form-item label="输出契约" for="formal-capability-output"><el-select id="formal-capability-output" v-model="outputSchema" filterable placeholder="选择已发布契约"><el-option v-for="item in capabilitySchemas" :key="item.schema_ref" :value="item.schema_ref" :label="item.label"/></el-select></el-form-item>
   </div>
   <section v-if="kind === 'ROLE'" class="role-scope" aria-label="角色精确权限声明">
    <h2>角色权限声明</h2><p>选择当前身份已有的精确资源与动作，逐项填写明确用途。声明须包含 1 至 128 个唯一权限，不会授予或扩大任何权限；服务端会重新核验每个当前授权。</p>
    <div class="form-grid">
     <el-form-item label="已有资源与动作" for="formal-capability-scope"><el-select id="formal-capability-scope" v-model="scopeKey" filterable placeholder="选择当前精确权限范围"><el-option v-for="item in scopeCandidates" :key="scopeIdentity(item)" :value="scopeIdentity(item)" :label="item.action + ' · ' + item.scope.resource_kind + ' · ' + item.scope.resource_id"/></el-select></el-form-item>
     <el-form-item label="明确用途" for="formal-capability-purpose"><el-input id="formal-capability-purpose" v-model="purpose" autocomplete="off" maxlength="64" placeholder="填写已批准用途，例如 AGENT_PUBLICATION"/><small>用途没有公开授权目录；输入用途不代表已获得许可。</small></el-form-item>
    </div>
    <el-button :disabled="busy || !scopeKey || !purpose || permissions.length >= 128" @click="addPermission">加入固定权限声明</el-button>
    <p v-if="!scopeCandidates.length">当前身份没有可选的精确资源范围。通配授权摘要不会自动展开为资源或权限。</p>
    <ul class="permission-list"><li v-for="(item,index) in permissions" :key="capabilityCanonical(item)"><div><strong>{{ item.action }}</strong><span>{{ item.scope.resource_kind }} · {{ item.scope.resource_id }}</span><span>用途：{{ item.purpose }}</span></div><el-button :disabled="busy" type="danger" link :aria-label="'移除权限 ' + item.action" @click="permissions.splice(index,1)">移除</el-button></li></ul>
   </section>
   <p v-else class="notice">提示词的工具范围固定为空。</p>
   <el-form-item label="登记原因" for="formal-capability-reason"><el-input id="formal-capability-reason" v-model="reason" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="说明本次真实来源、适用目标与固定范围"/></el-form-item>
   <el-checkbox v-model="attested">我已核查当前声明、真实来源与用途，确认仅登记 DRAFT；不会自动发布、执行或取得权限</el-checkbox>
   <p v-if="!mayCreate" class="notice">当前所选范围没有 capability.configure 的登记权限。</p>
   <div class="actions"><el-button type="primary" native-type="submit" :disabled="busy || !attested || !mayCreate || !selectedArtifact">保存正式能力草稿</el-button><el-button :disabled="busy" @click="clearForm">清除未提交表单</el-button></div>
  </el-form>
  <el-dialog v-model="leaveDialog" title="离开未提交的声明？" width="min(94vw,480px)" :close-on-click-modal="false"><p>未提交表单将清除。已经保存的原请求会保留，离开页面不会取消服务器操作。</p><template #footer><el-button @click="leaveDialog = false">继续填写</el-button><el-button type="danger" @click="confirmLeave">清除表单并离开</el-button></template></el-dialog>
 </main>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import FormalCapabilityDefinition from './formal-capability-definition.vue'
import { capabilityAllowed, capabilityCanonical, capabilityInputIssue, capabilitySchemas, capabilityScopeCandidates, capabilityValidate, detached, readPendingCapability, readCapabilityCancellation } from '@/api/medical/formal-capability-domain'
import type { CapabilityPermission, FormalCapability, FormalCapabilityRecord, PendingCapability, PendingCapabilityCancellation } from '@/api/medical/formal-capability-domain'
import { mayCancelCapability, prepareCapabilityCancellation, sendCapabilityCancellation, recoverCapabilityCancellation, closeCapabilityCancellation } from '@/api/medical/formal-capability-cancellation'
import type { CapabilityCancelReceipt } from '@/api/medical/formal-capability-cancellation'
import { createCapability, readCapabilityAvailability, recoverCapability } from '@/api/medical/formal-capabilities'
import { capabilityArtifactCandidates, capabilityProjectCandidates } from '@/api/medical/formal-capability-candidates'
import type { ArtifactCatalogueItem } from '@/api/medical/source-governance'
import type { MedicalSession, Project } from '@/api/medical/protocol'
import type { OperationLookup } from '@/api/medical/operations'
const user = useUserStore(), router = useRouter(), root = ref<HTMLElement | null>(null)
const loading = ref(false), error = ref(''), availability = ref<boolean | null>(null), pending = ref<PendingCapability | null>(null), lookup = ref<OperationLookup | null>(null), saved = ref<FormalCapabilityRecord | null>(null)
const cancelPending = ref<PendingCapabilityCancellation | null>(null), cancelReceipt = ref<CapabilityCancelReceipt | null>(null), cancelConfirmed = ref(false), cancelExported = ref(false), cancelAcknowledged = ref(false)
const canCancel = computed(() => !!pending.value && !!user.medicalSession && mayCancelCapability(user.medicalSession, pending.value))
const kind = ref<'ROLE' | 'PROMPT'>('PROMPT'), project = ref(''), artifactId = ref(''), inputSchema = ref(''), outputSchema = ref(''), reason = ref(''), attested = ref(false)
const projects = ref<Project[]>([]), projectCursor = ref<string | null>(null), artifacts = ref<ArtifactCatalogueItem[]>([]), artifactCursor = ref<string | null>(null), artifactsLoaded = ref(false)
const scopeKey = ref(''), purpose = ref(''), permissions = ref<CapabilityPermission[]>([]), leaveDialog = ref(false)
const busy = computed(() => loading.value || user.medicalWriting)
const mayCreate = computed(() => capabilityAllowed(user.medicalSession, project.value || null))
const selectedArtifact = computed(() => artifacts.value.find(item => item.artifact_id === artifactId.value) ?? null)
const scopeCandidates = computed(() => user.medicalSession?.current_context ? capabilityScopeCandidates(user.medicalSession, project.value || null) : [])
const lookupMessage = computed(() => lookup.value?.lookup_status === 'NOT_FOUND' ? '尚未查到原操作。原请求继续保留，不能据此认定未执行或自动重发。'
 : lookup.value?.lookup_status === 'EXPIRED' ? '原键查询期限已过，请保留原请求并联系管理员核查；本页不会重新创建。'
 : lookup.value?.operation?.status === 'PROCESSING' ? '原操作仍在处理，请稍后再次读取。'
 : lookup.value?.operation?.status === 'FAILED' ? '原操作显示失败，保留原声明与请求身份，需核查后处理。' : '原操作已完成，正在核对类型回执与固定声明。')
let controller = new AbortController(), generation = 0, mounted = true, ownsWrite: object | null = null, leaveApproved = false, leavePath = ''
const reader = (): MedicalSession => { if (!user.medicalSession?.current_context) throw new Error('请先选择当前企业'); return user.medicalSession }
function live(g: number, session: MedicalSession) { return mounted && g === generation && user.medicalSession === session && !document.hidden }
function clearForm() { kind.value = 'PROMPT'; project.value = ''; artifactId.value = ''; inputSchema.value = ''; outputSchema.value = ''; reason.value = ''; attested.value = false; scopeKey.value = ''; purpose.value = ''; permissions.value = [] }
function clear() { cancelPending.value = null; cancelReceipt.value = null; cancelConfirmed.value = false; cancelExported.value = false; cancelAcknowledged.value = false; controller.abort(); controller = new AbortController(); generation++; root.value?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input,textarea').forEach(item => { item.value = ''; if (item instanceof HTMLInputElement) item.checked = false }); clearForm(); projects.value = []; projectCursor.value = null; artifacts.value = []; artifactCursor.value = null; artifactsLoaded.value = false; availability.value = null; pending.value = null; lookup.value = null; saved.value = null; error.value = ''; loading.value = false; leaveDialog.value = false }
async function focusError() { await nextTick(); root.value?.querySelector<HTMLElement>('[role="alert"]')?.focus() }
async function read(work: (session: MedicalSession, signal: AbortSignal, current: () => boolean) => Promise<void>) {
 const session = user.medicalSession as MedicalSession | null; if (!session?.current_context || busy.value || document.hidden) return
 const g = generation; loading.value = true; error.value = ''; try { await work(session, controller.signal, () => live(g, session)) }
 catch (failure) { if (live(g, session)) { error.value = failure instanceof Error ? failure.message : '读取失败，请核查当前身份'; await focusError() } }
 finally { if (g === generation) loading.value = false }
}
async function bootstrap() { if (ownsWrite || user.medicalWriting) return; clear(); const session = user.medicalSession as MedicalSession | null; if (!session?.current_context || document.hidden) return
 try { pending.value = readPendingCapability(session); cancelPending.value = readCapabilityCancellation(session) } catch (failure) { error.value = failure instanceof Error ? failure.message : '原请求无法恢复'; return }
 await read(async (_session, signal, current) => { const available = await readCapabilityAvailability(reader, signal); if (!current()) return; availability.value = available.features.agent_authoring
   if (availability.value && !pending.value) { const page = await capabilityProjectCandidates(reader, null, signal); if (current()) { projects.value = page.projects; projectCursor.value = page.next_after } } })
}
async function loadProjects(next: boolean) { const cursor = next ? projectCursor.value : null; if (next && !cursor) return
 await read(async (_session, signal, current) => { const page = await capabilityProjectCandidates(reader, cursor, signal); if (current()) { const combined = next ? [...projects.value, ...page.projects] : page.projects; if (new Set(combined.map(item => item.project_id)).size !== combined.length) throw new Error('项目目录发生变化，请重新读取首页'); projects.value = combined; projectCursor.value = page.next_after } }) }
async function loadArtifacts(next: boolean) { const cursor = next ? artifactCursor.value : null, selectedProject = project.value; if (next && !cursor) return
 if (!next) { artifacts.value = []; artifactId.value = ''; artifactCursor.value = null; artifactsLoaded.value = false }
 await read(async (_session, signal, current) => { const page = await capabilityArtifactCandidates(reader, selectedProject || null, cursor, signal); if (current() && project.value === selectedProject) { const combined = next ? [...artifacts.value, ...page.items] : page.items; if (new Set(combined.map(item => item.artifact_id)).size !== combined.length) throw new Error('成果候选已变化，请重新读取首页'); artifacts.value = combined; artifactCursor.value = page.next_after; artifactsLoaded.value = true } }) }
function scopeIdentity(item: Omit<CapabilityPermission, 'purpose'>) { return capabilityCanonical(item) }
function addPermission() { if (busy.value || pending.value) return; const candidate = scopeCandidates.value.find(item => scopeIdentity(item) === scopeKey.value)
 if (!candidate || !/^[A-Z][A-Z0-9_]{0,63}$/.test(purpose.value)) { error.value = '请选择当前精确范围，并填写 1 至 64 位大写用途代码'; return }
 const value = { ...detached(candidate), purpose: purpose.value }; capabilityValidate('RuntimePermission', value)
 if (permissions.value.length >= 128 || permissions.value.some(item => capabilityCanonical(item) === capabilityCanonical(value))) { error.value = '权限声明重复或超过 128 项'; return }
 permissions.value.push(value); scopeKey.value = ''; purpose.value = ''; error.value = ''
}
function body(): FormalCapability { const artifact = selectedArtifact.value, input = capabilitySchemas.find(item => item.schema_ref === inputSchema.value), output = capabilitySchemas.find(item => item.schema_ref === outputSchema.value)
 if (!artifact || !input || !output) throw new Error('请选择真实来源成果和已发布输入、输出契约')
 if (project.value && !projects.value.some(item => item.project_id === project.value)) throw new Error('请选择当前目录中实际可读取的项目')
 return { schema_version: '1.0.0', execution_mode: 'PRODUCTION', project_id: project.value || null, kind: kind.value,
  source_artifact: { version_id: artifact.artifact_id, content_sha256: artifact.sha256 }, input_schema_ref: { schema_ref: input.schema_ref, schema_sha256: input.schema_sha256 }, output_schema_ref: { schema_ref: output.schema_ref, schema_sha256: output.schema_sha256 },
  tool_scope: kind.value === 'ROLE' ? { schema_version: '1.0.0', permissions: detached(permissions.value) } : [], reason: reason.value }
}
async function submit() { if (busy.value || pending.value || !attested.value || !mayCreate.value || availability.value !== true || document.hidden) return
 let value: FormalCapability; try { value = body(); const issue = capabilityInputIssue(value); if (issue) throw new Error(issue) } catch (failure) { error.value = failure instanceof Error ? failure.message : '请核查声明'; await focusError(); return }
 const session = reader(), g = generation; if (user.medicalWriting) return; const token = {}; ownsWrite = token; user.medicalWriting = true; error.value = ''; saved.value = null; lookup.value = null
 try { const result = await createCapability(reader, value, controller.signal, original => { if (live(g, session)) pending.value = original }); if (!live(g, session)) return
   attested.value = false; const recovered = await recoverCapability(reader, result.pending, controller.signal)
   if (live(g, session)) { lookup.value = recovered.lookup; if (recovered.record) { pending.value = null; clearForm(); saved.value = recovered.record } }
 } catch (failure) { if (live(g, session)) { error.value = failure instanceof Error ? failure.message : '保存结果待确认，请保留原请求并读取恢复'; pending.value = readPendingCapability(session); await focusError() } }
 finally { if (ownsWrite === token) { ownsWrite = null; user.medicalWriting = false } }
}
async function recover() { const original = pending.value; if (!original || cancelPending.value || availability.value !== true) return
 await read(async (_session, signal, current) => { const result = await recoverCapability(reader, original, signal); if (current()) { lookup.value = result.lookup; if (result.record) { pending.value = null; clearForm(); saved.value = result.record } } }) }
async function cancelOriginal() {
 const original = pending.value; if (!original || !cancelConfirmed.value || !canCancel.value || busy.value || availability.value !== true || document.hidden) return
 const session = reader(), g = generation, token = {}; ownsWrite = token; user.medicalWriting = true; error.value = ''
 try { let cancel = cancelPending.value; if (!cancel) cancel = await prepareCapabilityCancellation(reader, original, controller.signal); if (!live(g, session)) return; cancelPending.value = cancel
  const result = await sendCapabilityCancellation(reader, cancel, controller.signal); if (live(g, session)) { cancelReceipt.value = result; cancelConfirmed.value = false; cancelExported.value = false; cancelAcknowledged.value = false }
 } catch (failure) { if (live(g, session)) { cancelPending.value = readCapabilityCancellation(session); error.value = failure instanceof Error ? failure.message : '取消结果待确认，原请求继续保留'; await focusError() } }
 finally { if (ownsWrite === token) { ownsWrite = null; user.medicalWriting = false } }
}
async function recoverCancel() { const original = cancelPending.value; if (!original || availability.value !== true) return
 await read(async (_session, signal, current) => { const result = await recoverCapabilityCancellation(reader, original, signal); if (current()) { if(cancelReceipt.value && (!result || capabilityCanonical(result)!==capabilityCanonical(cancelReceipt.value))) throw new Error('取消回执与已核验结果不一致，请保留记录人工核查'); cancelReceipt.value = result; cancelExported.value = false; cancelAcknowledged.value = false; if(!result) error.value = '尚未查到取消回执。两个原请求标识继续保留，不会自动重发。' } })
}
function exportCancellation() { if (!cancelPending.value || !cancelReceipt.value || busy.value || document.hidden) return
 try { const content = capabilityCanonical({ original: cancelPending.value.original, cancellation: cancelPending.value, receipt: cancelReceipt.value }); const url = URL.createObjectURL(new Blob([content], {type:'application/json'})); try { const link=document.createElement('a'); link.href=url; link.download='capability-original-request-proof.json'; link.click() } finally { URL.revokeObjectURL(url) }; cancelExported.value = true } catch { cancelExported.value = false; cancelAcknowledged.value = false; error.value = '证明导出失败，原请求保持不变' }
}
async function finishCancellation() { const original=cancelPending.value, receipt=cancelReceipt.value; if (!original || !receipt || !cancelExported.value || !cancelAcknowledged.value) return
 await read(async (_session,signal,current)=>{await closeCapabilityCancellation(reader,original,receipt,signal);if(current()){cancelPending.value=null;cancelReceipt.value=null;pending.value=null;lookup.value=null;cancelConfirmed.value=false;cancelExported.value=false;cancelAcknowledged.value=false;clearForm()}})
}
watch(() => user.medicalWriting, value => { if(!value) ownsWrite=null }, {flush:'sync'})
function dirty() { return !pending.value && (!!project.value || !!artifactId.value || !!inputSchema.value || !!outputSchema.value || !!reason.value || !!scopeKey.value || !!purpose.value || permissions.value.length > 0 || attested.value) }
function unload(event: BeforeUnloadEvent) { if (dirty() || ownsWrite) { event.preventDefault(); event.returnValue = '' } }
function visibility() { if (document.hidden) clear(); else void bootstrap() }
async function confirmLeave() { clearForm(); leaveDialog.value = false; leaveApproved = true; try { await router.push(leavePath) } finally { leaveApproved = false; leavePath = '' } }
onBeforeRouteLeave(to => { if (ownsWrite || user.medicalWriting) return false; if (leaveApproved || !dirty()) return true; leavePath = to.fullPath; leaveDialog.value = true; return false })
watch([kind, project, artifactId, inputSchema, outputSchema, reason, scopeKey, purpose, permissions], () => { attested.value = false; saved.value = null }, { deep: true, flush: 'sync' })
watch(project, () => { artifacts.value = []; artifactId.value = ''; artifactCursor.value = null; artifactsLoaded.value = false; scopeKey.value = ''; permissions.value = [] }, { flush: 'sync' })
watch(kind, () => { permissions.value = []; scopeKey.value = ''; purpose.value = '' }, { flush: 'sync' })
watch(() => user.medicalSession, () => { clear(); if (!ownsWrite) void bootstrap() }, { immediate: true, flush: 'sync' })
document.addEventListener('visibilitychange', visibility); window.addEventListener('beforeunload', unload)
onBeforeUnmount(() => { mounted = false; clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('beforeunload', unload) })
</script>
<style scoped>
.capability-page{padding:28px;min-height:100%;background:#f4f6fa;color:#243247}.breadcrumb,.actions,.field-actions,.page-heading,.section-heading{display:flex;align-items:center;gap:14px;flex-wrap:wrap}.breadcrumb{font-size:13px;color:#5c6d82}.page-heading,.section-heading{justify-content:space-between}.page-heading{margin:18px 0 24px}.page-heading h1{font-size:24px;line-height:1.4;margin:0}.page-heading p{margin:10px 0 0}.card{padding:24px;background:#fff;border:1px solid #dce3ec;border-radius:8px;min-width:0}.card h2{font-size:17px;margin:0 0 12px}.spaced{margin-top:20px}.actions{margin:18px 0}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 24px}.field-actions{margin-top:6px}.identifier,small{overflow-wrap:anywhere}.identifier{font-size:13px}.role-scope{padding:20px 0;border-top:1px solid #dce3ec;border-bottom:1px solid #dce3ec;margin:8px 0 24px}p{color:#526279;line-height:1.65}small{color:#5c6d82}.permission-list{list-style:none;padding:0;margin:16px 0;display:grid;gap:10px}.permission-list li{padding:14px;border:1px solid #dce3ec;border-radius:6px;display:flex;justify-content:space-between;gap:16px;background:#f8fafc}.permission-list span{display:block;font-size:13px;color:#526279;margin-top:6px;overflow-wrap:anywhere}.notice,.pending{background:#fff9ed}.result{border-color:#87bba0}details{margin-top:20px}summary{cursor:pointer;padding:10px 0;color:#175fae}a{color:#175fae}:deep(.el-select){width:100%}:deep(.el-form-item__content){display:block}:deep(.el-checkbox){height:auto;align-items:flex-start;white-space:normal}:deep(.el-checkbox__label){white-space:normal;line-height:1.5}a:focus-visible,summary:focus-visible{outline:2px solid #1768c4;outline-offset:3px}@media(max-width:650px){.capability-page{padding:16px}.card{padding:16px}.form-grid{grid-template-columns:1fr}.page-heading{align-items:flex-start;flex-direction:column}.permission-list li{flex-direction:column}.actions>.el-button{margin-left:0}}
</style>

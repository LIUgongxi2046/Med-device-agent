<template>
  <main class="medical-page retention-page">
    <header><div><p class="eyebrow">运行治理</p><h1>保存策略</h1><p>管理运行记录与事件流的保存期限、生效区间和替换关系。</p></div><el-button :disabled="busy" @click="refresh">刷新</el-button></header>
    <session-panel />
    <p v-if="error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="busy" role="status">正在处理…</p>
    <p v-if="enabled === false">保存策略服务尚未启用。</p><p v-if="pending && !enabled">本地仍保留待核查的原请求。服务恢复后请刷新核查，不要重新提交。</p>
    <template v-if="enabled">
      <section class="toolbar" aria-label="策略范围">
        <label>范围<select v-model="scopeKind" :disabled="busy"><option value="ENTERPRISE">企业</option><option value="PROJECT">项目</option></select></label>
        <template v-if="scopeKind === 'PROJECT'">
          <label>项目<select v-model="projectId" :disabled="busy"><option value="">请选择项目</option><option v-for="p in projects?.projects ?? []" :key="p.project_id" :value="p.project_id">{{ p.name }}</option></select></label>
          <el-button :disabled="busy || !projectReadable" @click="loadProjects()">项目首页</el-button>
          <el-button v-if="projects?.next_after" :disabled="busy" @click="loadProjects(projects.next_after)">下一页项目</el-button>
        </template>
        <el-button :disabled="busy || !canRead" @click="loadPolicies()">查询策略</el-button>
        <el-button type="primary" :disabled="busy || !canApprove || !!pending" @click="begin('CREATE')">新增策略</el-button>
      </section>
      <section v-if="page" aria-label="策略列表">
        <p v-if="!page.items.length">当前页没有策略。</p>
        <ul class="policies"><li v-for="item in page.items" :key="item.fixed.policy_revision">
          <div><strong>{{ classificationNames[item.fixed.request.classification] }}</strong><span class="status">{{ lifecycleNames[item.lifecycle] }}</span><p>{{ item.fixed.request.effective_from }} 至 {{ item.fixed.request.effective_until }}</p><small>{{ item.fixed.policy_revision }}</small></div>
          <el-button :disabled="busy" @click="openPolicy(item.fixed.policy_revision)">查看详情</el-button>
        </li></ul>
        <el-button v-if="page.next_after" :disabled="busy" @click="loadPolicies(page.next_after)">下一页策略</el-button><p v-else>本次列表已到末页。</p>
      </section>
      <section v-if="selected" class="detail" aria-label="策略详情">
        <h2>策略详情</h2><p>{{ selected.fixed.policy_revision }} · 修订 {{ selected.revision }}</p>
        <dl><dt>状态</dt><dd>{{ lifecycleNames[selected.lifecycle] }}<span v-if="selected.retirement_at">；停止边界 {{ selected.retirement_at }}</span></dd><dt>批准人</dt><dd>{{ selected.fixed.approved_by }}</dd><dt>操作理由</dt><dd>{{ selected.fixed.request.reason }}</dd><template v-for="term in selected.fixed.request.terms" :key="term.category"><dt>{{ term.category === 'RUN' ? '运行记录' : '事件流' }}</dt><dd>保留 {{ term.retention_duration_seconds }} 秒，备份到期 {{ term.backup_expiry_seconds }} 秒；{{ term.purge_method === 'VERIFIED_DELETE' ? '核验删除' : '密码擦除' }}</dd></template></dl>
        <p>“生效期内”仅表示时间状态；实际运行还会核查当前批准与执行权限。</p>
        <el-button :disabled="busy || !!pending || !canChangeSelected" @click="begin('REPLACE')">以新策略替换</el-button><el-button :disabled="busy || !!pending || !canChangeSelected" @click="begin('RETIRE')">停止使用</el-button>
      </section>
      <form v-if="mode" class="editor" @submit.prevent="submit">
        <h2>{{ mode === 'CREATE' ? '新增并批准策略' : mode === 'REPLACE' ? '替换所选策略' : '停止使用所选策略' }}</h2>
        <p v-if="mode !== 'CREATE'">目标：{{ selected?.fixed.policy_revision }}，修订 {{ selected?.revision }}</p>
        <fieldset :disabled="busy || !!pending"><legend>策略设置</legend>
          <template v-if="mode !== 'RETIRE'">
            <label>数据级别<select v-model="classification" :disabled="mode === 'REPLACE'"><option v-for="(label,key) in classificationNames" :key="key" :value="key">{{ label }}</option></select></label>
            <label>生效时间（UTC）<input v-model="starts" type="datetime-local" step="1" required /></label><label>结束时间（UTC）<input v-model="ends" type="datetime-local" step="1" required /></label>
            <div class="term-grid"><fieldset v-for="term in terms" :key="term.category"><legend>{{ term.category === 'RUN' ? '运行记录' : '事件流' }}</legend><label>保存期限（秒）<input v-model="term.retention_duration_seconds" inputmode="numeric" pattern="[1-9][0-9]*" maxlength="18" required /></label><label>备份到期（秒）<input v-model="term.backup_expiry_seconds" inputmode="numeric" pattern="[1-9][0-9]*" maxlength="18" required /></label><label>清除方式<select v-model="term.purge_method"><option value="VERIFIED_DELETE">核验删除</option><option value="CRYPTOGRAPHIC_ERASURE">密码擦除</option></select></label></fieldset></div>
          </template>
          <label v-else>停止使用时间（UTC）<input v-model="retireAt" type="datetime-local" step="1" required /></label>
          <label>理由<textarea v-model="reason" rows="3" maxlength="2000" required /></label><label class="confirmation"><input v-model="confirmed" type="checkbox" required />我已核对目标、范围与时间，确认提交本次决定。</label>
        </fieldset>
        <el-button native-type="submit" type="primary" :disabled="busy || !!pending || !confirmed || !canSubmit">提交决定</el-button><el-button :disabled="busy || !!pending" @click="mode = null">取消编辑</el-button>
      </form>
      <section v-if="pending" class="pending" aria-label="原请求核查">
        <h2>{{ receipt ? '已取得原请求回执' : '请求结果待确认' }}</h2><p>请求编号：{{ pending.key }}</p><p v-if="!receipt">请按原请求核查结果。未查询到回执时仍保留记录，不自动重发。</p><p v-else>策略 {{ receipt.policy_revision }} · 操作 {{ receipt.operation_id }}</p>
        <el-button :disabled="busy" @click="recover">只读核查原请求</el-button><el-button :disabled="busy" @click="downloadProof">保存请求记录</el-button><el-button v-if="canClose" :disabled="busy || !proofSaved || recoveryConflict" @click="acknowledge">已保存回执，继续操作</el-button>
        <template v-if="cancelPending"><p>已保留撤回请求及完整原请求。</p><p v-if="cancelReceipt">{{ cancelReceipt.status === 'CANCELLED' ? '原请求已撤回，未登记决定。' : '原请求已登记，保留原决定。' }}</p><el-button :disabled="busy" @click="recoverCancel">只读核查撤回请求</el-button><details><summary>撤回请求编号</summary><p>{{ cancelPending.key }}</p></details></template>
        <template v-if="canCancel"><p>如果不再等待原决定，可明确撤回尚未登记的请求。若原决定已经登记，服务器只返回原回执，不会撤销策略。</p><label class="confirmation"><input v-model="cancelConfirmed" type="checkbox" :disabled="busy" />我确认撤回尚未登记的原请求，并保留双方请求记录。</label><el-button :disabled="busy || !cancelConfirmed" @click="cancelOriginal">{{ cancelPending ? '按原撤回请求再次提交' : '撤回尚未登记的原请求' }}</el-button></template>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { projectActionAllowed } from '@/api/medical/project-write'
import { listProjects } from '@/api/medical/projects'
import type { Listing, MedicalSession } from '@/api/medical/protocol'
import { projectJsonIssue } from '@/api/medical/project-json'
import { canonicalDocumentValue as canonical } from '@/api/medical/document-body'
import { retentionAvailability, retentionList, retentionDetail, retentionAllowed, retentionIdentity, prepareRetention, verifyPending, sendRetention, recoverRetention, prepareCancellation, verifyCancelPending, sendCancellation, recoverCancellation, validateRetentionSubmissionTime, RetentionInputError } from '@/api/medical/runtime-retention'
import type { Ownership, Term, Approval, Retirement, PolicyList, PolicyView, Pending, Receipt, CancelPending, CancelReceipt } from '@/api/medical/runtime-retention'
const user = useUserStore(), session = computed(() => user.medicalSession)
const enabled = ref<boolean | null>(null), loading = ref(false), error = ref(''), notice = ref('')
const busy = computed(() => loading.value || user.medicalWriting)
const scopeKind = ref<'ENTERPRISE' | 'PROJECT'>('ENTERPRISE'), projectId = ref(''), projects = ref<Listing | null>(null), page = ref<PolicyList | null>(null), selected = ref<PolicyView | null>(null)
const mode = ref<'CREATE' | 'REPLACE' | 'RETIRE' | null>(null), classification = ref<Approval['classification']>('INTERNAL'), starts = ref(''), ends = ref(''), retireAt = ref(''), reason = ref(''), confirmed = ref(false)
const pending = ref<Pending | null>(null), receipt = ref<Receipt | null>(null), proofSaved = ref(false), recoveryConflict = ref(false)
const cancelPending = ref<CancelPending | null>(null), cancelReceipt = ref<CancelReceipt | null>(null), cancelConfirmed = ref(false)
const terms = ref<Term[]>(emptyTerms())
const classificationNames = { PUBLIC: '公开', INTERNAL: '内部', RESTRICTED: '受限', PERSONAL_HEALTH: '个人健康信息' }
const lifecycleNames = { SCHEDULED: '待生效', IN_EFFECT: '生效期内', EXPIRED: '已到期', RETIRED: '已停止' }
let ownWrite: object | null = null
let generation = 0, controller: AbortController | null = null, alive = true
const seen = new Set<string>()
function emptyTerms(): Term[] { return ['RUN', 'STREAM'].map(value => ({ category: value as Term['category'], start_event: (value + '_CREATED') as Term['start_event'], retention_duration_seconds: '', hold_override: false, purge_method: 'VERIFIED_DELETE', backup_expiry_seconds: '' })) }
function clearRead() { generation++; controller?.abort(); controller = null; loading.value = false; page.value = null; selected.value = null; mode.value = null; confirmed.value = false; error.value = ''; notice.value = ''; seen.clear() }
function clearAll() { clearRead(); enabled.value = null; projects.value = null; projectId.value = ''; pending.value = null; receipt.value = null; proofSaved.value = false; recoveryConflict.value = false; cancelPending.value = null; cancelReceipt.value = null; cancelConfirmed.value = false }
const routeActive = useMedicalRouteRead('MedicalRuntimeRetention', clearAll)
const identity = computed(() => session.value ? retentionIdentity(session.value) : '')
const owner = computed<Ownership>(() => scopeKind.value === 'ENTERPRISE' ? { scope: 'ENTERPRISE', project_id: null } : { scope: 'PROJECT', project_id: projectId.value })
const selectedOwner = computed<Ownership | null>(() => selected.value ? selected.value.fixed.request.project_id === null ? { scope: 'ENTERPRISE', project_id: null } : { scope: 'PROJECT', project_id: selected.value.fixed.request.project_id } : null)
const validOwner = computed(() => scopeKind.value === 'ENTERPRISE' || !!projectId.value && !!projects.value?.projects.some(p => p.project_id === projectId.value))
const projectReadable = computed(() => projectActionAllowed(session.value, 'project.read'))
const canRead = computed(() => !!session.value && enabled.value === true && validOwner.value && retentionAllowed(session.value, owner.value, 'retention.read'))
const canApprove = computed(() => !!session.value && enabled.value === true && validOwner.value && retentionAllowed(session.value, owner.value, 'retention.approve'))
const canChangeSelected = computed(() => !!session.value && !!selected.value && !!selectedOwner.value && selected.value.retirement_at === null && retentionAllowed(session.value, selectedOwner.value, 'retention.approve'))
const formIdentity = computed(() => canonical({ mode: mode.value, owner: owner.value, target: selected.value ? { id: selected.value.fixed.policy_revision, revision: selected.value.revision, hash: selected.value.content_sha256 } : null, classification: classification.value, starts: starts.value, ends: ends.value, retireAt: retireAt.value, terms: terms.value, reason: reason.value }))
const canCancel = computed(() => !!pending.value && !receipt.value && !cancelReceipt.value && !recoveryConflict.value && !!session.value && enabled.value === true && (pending.value.kind === 'APPROVE' ? retentionAllowed(session.value, (pending.value.body as Approval).project_id === null ? { scope: 'ENTERPRISE', project_id: null } : { scope: 'PROJECT', project_id: (pending.value.body as Approval).project_id! }, 'retention.read') : session.value.action_summary.some((a: MedicalSession['action_summary'][number]) => a.action === 'retention.read')))
const canClose = computed(() => !recoveryConflict.value && (cancelPending.value ? !!cancelReceipt.value : !!receipt.value))
const canSubmit = computed(() => mode.value === 'CREATE' ? canApprove.value : canChangeSelected.value)
function live(g: number, s: MedicalSession, id: string) { return alive && g === generation && session.value === s && identity.value === id && routeActive.value && !document.hidden }
async function work(task: (s: MedicalSession, signal: AbortSignal, current: () => boolean) => Promise<void>) {
  const s = session.value; if (busy.value || !s?.current_context || !routeActive.value || document.hidden) return
  const g = generation, id = identity.value, c = new AbortController(); controller = c; loading.value = true; error.value = ''; notice.value = ''
  try { await task(s, c.signal, () => live(g, s, id)) } catch (e) { if (live(g, s, id)) error.value = e instanceof RetentionInputError ? e.message : '操作未完成。请核查当前权限或按原请求读取结果。' } finally { if (g === generation) { loading.value = false; controller = null } }
}
const storageKey = (s: MedicalSession) => 'medical-runtime-retention:1:' + s.current_context?.tenant_id + ':' + s.user.user_id
async function restorePending(s: MedicalSession) { const text = sessionStorage.getItem(storageKey(s)); if (!text) return null; if (new TextEncoder().encode(text).length > 262144) throw new RetentionInputError('本地请求记录超过容量，未发起新操作'); if (projectJsonIssue(text)) throw new RetentionInputError('本地请求记录格式无效，未发起新操作'); let p: Pending | CancelPending; try { p = JSON.parse(text) as Pending | CancelPending } catch { throw new RetentionInputError('本地请求记录损坏，未发起新操作') } if ('type' in p && p.type === 'CANCEL') await verifyCancelPending(s, p); else await verifyPending(s, p as Pending); return p }

async function refresh() { clearAll(); await work(async (s, signal, current) => { const original = await restorePending(s); if (!current()) return; if (original && 'type' in original && original.type === 'CANCEL') { cancelPending.value = original; pending.value = original.original } else pending.value = original as Pending | null; const on = await retentionAvailability(s, signal); if (!current()) return; enabled.value = on; if (on && projectReadable.value) { const p = await listProjects(s.current_context!, {}, signal); if (current()) projects.value = p } }) }
async function loadProjects(after?: string) { if (!projectReadable.value || after !== undefined && after !== projects.value?.next_after) return; clearRead(); projectId.value = ''; await work(async (s, signal, current) => { projects.value = null; const value = await listProjects(s.current_context!, after ? { after } : {}, signal); if (current()) projects.value = value }) }
async function loadPolicies(after?: string) { if (!canRead.value || after !== undefined && (after !== page.value?.next_after || seen.has(after))) return; await work(async (s, signal, current) => { const target = { ...owner.value } as Ownership; page.value = null; selected.value = null; mode.value = null; if (!after) seen.clear(); const value = await retentionList(s, target, signal, after); if (current()) { page.value = value; if (after) seen.add(after) } }) }
async function openPolicy(id: string) { if (!page.value?.items.some(item => item.fixed.policy_revision === id)) return; await work(async (s, signal, current) => { selected.value = null; mode.value = null; const value = await retentionDetail(s, id, signal); if (current()) selected.value = value }) }
function begin(value: 'CREATE' | 'REPLACE' | 'RETIRE') { if (busy.value || pending.value || value === 'CREATE' && !canApprove.value || value !== 'CREATE' && !canChangeSelected.value) return; mode.value = value; starts.value = ''; ends.value = ''; retireAt.value = ''; reason.value = ''; confirmed.value = false; if (value === 'CREATE') { classification.value = 'INTERNAL'; terms.value = emptyTerms() } else if (selected.value) { classification.value = selected.value.fixed.request.classification; terms.value = JSON.parse(canonical(selected.value.fixed.request.terms)) as Term[] } }
function utc(value: string) { if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) throw new RetentionInputError('请填写完整 UTC 时间'); const exact = value.length === 16 ? value + ':00' : value; const date = new Date(exact + 'Z'); if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 19) !== exact) throw new RetentionInputError('UTC 时间无效'); return date.toISOString() }
async function submit() { if (!mode.value || !canSubmit.value || !confirmed.value || pending.value) return; await work(async (s, signal, current) => {
  const originalForm = formIdentity.value
  const kind = mode.value === 'RETIRE' ? 'RETIRE' : 'APPROVE'; let body: Approval | Retirement
  if (kind === 'RETIRE') { if (!selected.value) return; body = { schema_version: '1.0.0', policy_revision: selected.value.fixed.policy_revision, expected_revision: selected.value.revision, effective_at: utc(retireAt.value), reason: reason.value } }
  else body = { schema_version: '1.0.0', project_id: mode.value === 'REPLACE' ? selected.value!.fixed.request.project_id : owner.value.project_id, classification: classification.value, effective_from: utc(starts.value), effective_until: utc(ends.value), terms: terms.value, reason: reason.value, replaces: mode.value === 'REPLACE' ? { policy_revision: selected.value!.fixed.policy_revision, expected_revision: selected.value!.revision } : null }
  validateRetentionSubmissionTime(kind, body)
  const p = await prepareRetention(s, kind, body); if (!current() || user.medicalWriting || !confirmed.value || originalForm !== formIdentity.value || !canSubmit.value) return
  if (sessionStorage.getItem(storageKey(s)) !== null) throw new RetentionInputError('已有原请求记录，请先刷新并核查，不能覆盖')
  try { sessionStorage.setItem(storageKey(s), canonical(p)) } catch { throw new RetentionInputError('无法保存原请求记录，本次没有发送') }
  pending.value = p; receipt.value = null; proofSaved.value = false; recoveryConflict.value = false
  const token = {}; ownWrite = token; user.medicalWriting = true
  try { const result = await sendRetention(s, p, signal); if (current()) { receipt.value = result; notice.value = '决定已登记。请保存回执后继续。' } }
  finally { if (ownWrite === token) { ownWrite = null; user.medicalWriting = false } }
}) }
async function recover() { const p = pending.value; if (!p) return; await work(async (s, signal, current) => {
  const previous = receipt.value; proofSaved.value = false
  const result = await recoverRetention(s, p, signal); if (!current()) return
  if (cancelReceipt.value?.status === 'CANCELLED' && result.status === 'FOUND') { recoveryConflict.value = true; throw new RetentionInputError('已撤回证明与原请求完成回执冲突，已保留全部记录。') }
  if (previous && (result.status !== 'FOUND' || canonical(previous) !== canonical(result.receipt))) { recoveryConflict.value = true; throw new RetentionInputError('原回执与本次核查结果冲突。已保留请求与原回执，暂停后续操作。') }
  if (result.status === 'FOUND') { receipt.value = result.receipt; recoveryConflict.value = false; notice.value = '已找到原请求回执。' }
  else notice.value = '暂未找到回执，原请求记录继续保留；没有重发。'
}) }
function acceptCancellation(value: CancelReceipt) {
  if (cancelReceipt.value && canonical(cancelReceipt.value) !== canonical(value) || receipt.value && (value.status !== 'COMPLETED' || canonical(receipt.value) !== canonical(value.receipt))) { recoveryConflict.value = true; throw new RetentionInputError('原请求与撤回回执发生冲突，已保留全部记录，请核查。') }
  cancelReceipt.value = value; if (value.status === 'COMPLETED') receipt.value = value.receipt
  notice.value = value.status === 'CANCELLED' ? '已登记撤回证明，迟到的原请求不能再生效。请保存记录后继续。' : '原请求已完成，未撤回已登记决定。请保存真实回执。'
}
async function cancelOriginal() {
  if (!canCancel.value || !cancelConfirmed.value || !pending.value) return
  const original = pending.value
  await work(async (s, signal, current) => {
    const p = cancelPending.value ?? await prepareCancellation(s, original, signal)
    if (!current() || user.medicalWriting || !cancelConfirmed.value || !canCancel.value || pending.value !== original) return
    const stored = cancelPending.value ?? original
    if (sessionStorage.getItem(storageKey(s)) !== canonical(stored)) throw new RetentionInputError('本地原请求已变化，未发起撤回')
    if (!cancelPending.value) { try { sessionStorage.setItem(storageKey(s), canonical(p)) } catch { throw new RetentionInputError('无法保存撤回请求，本次没有发送') }; cancelPending.value = p }
    proofSaved.value = false; cancelConfirmed.value = false
    const token = {}; ownWrite = token; user.medicalWriting = true
    try { const result = await sendCancellation(s, p, signal); if (current()) acceptCancellation(result) }
    finally { if (ownWrite === token) { ownWrite = null; user.medicalWriting = false } }
  })
}
async function recoverCancel() { const p = cancelPending.value; if (!p) return; await work(async (s, signal, current) => {
  proofSaved.value = false; const previous = cancelReceipt.value
  const result = await recoverCancellation(s, p, signal); if (!current()) return
  if (previous && (result.status !== 'FOUND' || canonical(previous) !== canonical(result.receipt))) { recoveryConflict.value = true; throw new RetentionInputError('撤回回执与本次查询冲突，已保留全部记录。') }
  if (result.status === 'FOUND' && result.receipt) acceptCancellation(result.receipt)
  else notice.value = '尚未查到撤回回执，原请求和撤回请求均继续保留；没有重发。'
}) }
function downloadProof() {
  if (!pending.value || busy.value) return
  proofSaved.value = false; let url: string | undefined; let a: HTMLAnchorElement | undefined
  try { const blob = new Blob([canonical({ pending: pending.value, receipt: receipt.value, cancellation: cancelPending.value, cancellation_receipt: cancelReceipt.value, recovery_conflict: recoveryConflict.value })], { type: 'application/json' }); url = URL.createObjectURL(blob); a = document.createElement('a'); a.href = url; a.download = 'retention-request-' + pending.value.key + '.json'; document.body.appendChild(a); a.click(); proofSaved.value = canClose.value }
  catch { error.value = '保存请求记录失败，全部原请求继续保留，请重试保存。' }
  finally { a?.remove(); if (url) { const value = url; setTimeout(() => URL.revokeObjectURL(value), 0) } }
}
function acknowledge() { const s = session.value; if (!s || !pending.value || !canClose.value || !proofSaved.value || busy.value) return; try { if (sessionStorage.getItem(storageKey(s)) !== canonical(cancelPending.value ?? pending.value)) throw new Error(); sessionStorage.removeItem(storageKey(s)); pending.value = null; receipt.value = null; cancelPending.value = null; cancelReceipt.value = null; cancelConfirmed.value = false; proofSaved.value = false; mode.value = null; notice.value = '已结束本次操作，可重新查询策略。' } catch { error.value = '本地记录已变化或不可用，未清除；请刷新核查。' } }

watch(formIdentity, () => { confirmed.value = false }, { flush: 'sync' })
watch(() => user.medicalWriting, value => { if (!value) ownWrite = null }, { flush: 'sync' })
watch([scopeKind, projectId], clearRead, { flush: 'sync' })
watch(() => [session.value, identity.value, routeActive.value], () => { void refresh() }, { immediate: true, flush: 'sync' })
function visibility() { if (document.hidden) clearAll() }
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { alive = false; clearAll(); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.retention-page{max-width:1100px;margin:auto;padding:28px}.retention-page header{display:flex;justify-content:space-between;gap:20px;align-items:center}.eyebrow{font-size:12px;letter-spacing:.15em;color:#53728b}.retention-page h1{font-size:28px;margin:6px 0}.retention-page p{line-height:1.65;color:#526575}.toolbar{display:flex;align-items:end;gap:12px;flex-wrap:wrap;margin:24px 0}.retention-page label{display:grid;gap:7px}.retention-page input,.retention-page select,.retention-page textarea{padding:10px;border:1px solid #bdcbd7;border-radius:7px;max-width:100%;background:#fff}.policies{padding:0;list-style:none}.policies li{display:flex;gap:16px;align-items:center;justify-content:space-between;padding:18px 0;border-bottom:1px solid #e1e8ef}.policies small{overflow-wrap:anywhere;color:#687989}.status{margin-left:12px;border-radius:20px;background:#eef4fa;padding:4px 10px;font-size:12px}.detail,.editor,.pending{margin-top:24px;padding:24px;border:1px solid #dbe5ee;border-radius:12px;background:#fbfdff}.retention-page dl{display:grid;grid-template-columns:100px 1fr;gap:14px}.retention-page dd{margin:0;overflow-wrap:anywhere}.editor fieldset{border:0;display:grid;gap:16px;padding:16px 0}.term-grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}.confirmation{display:flex!important;align-items:center}.retention-page [role=alert]{color:#a33030}.retention-page .el-button{margin-top:8px}@media(max-width:640px){.retention-page{padding:16px}.retention-page header{align-items:start}.term-grid{grid-template-columns:1fr}.retention-page dl{grid-template-columns:1fr;gap:5px}.retention-page dt{margin-top:12px}.detail,.editor,.pending{padding:16px}.policies li{align-items:start;flex-direction:column}}
</style>

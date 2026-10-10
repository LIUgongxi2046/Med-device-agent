<template>
  <main class="medical-page">
    <h1>企业成员与授权</h1>
    <session-panel />
    <el-button :disabled="loading || user.medicalWriting || user.medicalLoading" @click="refresh">重新读取管理身份</el-button>
    <p v-if="!permitted">当前企业没有成员管理或项目授权许可，请核查当前身份与授权。</p>
    <member-governance-write :selected="selected" @verified="verified" />
    <template v-if="permitted">
      <nav aria-label="成员管理记录">
        <el-button v-if="governanceAllowed(user.medicalSession, 'Members')" :disabled="loading || user.medicalWriting" @click="changeKind('Members')">读取成员记录</el-button>
        <el-button :disabled="loading || user.medicalWriting" @click="changeKind('Grants')">读取授权记录</el-button>
        <el-button v-if="governanceAllowed(user.medicalSession, 'Invitations')" :disabled="loading || user.medicalWriting" @click="changeKind('Invitations')">读取邀请记录</el-button>
      </nav>
      <h2>{{ titles[kind] }}</h2>
      <el-form label-position="top" @submit.prevent="search">
        <el-form-item label="账户标识筛选" for="governance-subject" :error="subjectError">
          <el-input id="governance-subject" v-model="subject" :disabled="loading || user.medicalWriting" clearable />
        </el-form-item>
        <el-form-item label="每页数量" for="governance-limit" :error="limitError">
          <el-input id="governance-limit" v-model="limit" :disabled="loading || user.medicalWriting" inputmode="numeric" />
        </el-form-item>
        <el-button native-type="submit" :disabled="loading || user.medicalWriting">查询当前记录</el-button>
      </el-form>
      <p v-if="loading" role="status">正在读取当前可管理记录</p>
      <section v-if="listing" data-governance-list :aria-label="titles[kind]">
        <p>当前页面共有{{ listing.items.length }}条获授权记录。</p>
        <el-table :data="rows" row-key="id">
          <el-table-column prop="id" label="记录标识" min-width="300" />
          <el-table-column prop="user" label="账户标识" min-width="300" />
          <el-table-column prop="status" label="当前状态" min-width="120" />
          <el-table-column prop="revision" label="对象版本" min-width="100" />
          <el-table-column v-if="kind === 'Grants'" prop="action" label="独立动作" min-width="180" />
          <el-table-column label="操作" width="150"><template #default="scope">
            <el-button :disabled="loading || user.medicalWriting" @click="selected = scope.row.item">查看完整记录</el-button>
          </template></el-table-column>
        </el-table>
        <el-empty v-if="listing.items.length === 0" description="当前授权及筛选范围没有记录" />
        <el-button :disabled="loading || user.medicalWriting" @click="search">读取首页</el-button>
        <el-button :disabled="!listing.next_after || loading || user.medicalWriting" @click="next">读取下一页</el-button>
      </section>
      <section v-if="selected" data-governance-detail aria-label="完整管理记录">
        <h2>完整管理记录</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item v-for="(value, key) in selected" :key="key" :label="labels[key]">
            <span class="medical-governance-value">{{ formatProjectJson(value) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </section>
    </template>
    <router-link to="/projects">返回项目中心</router-link>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import MemberGovernanceWrite from './member-governance-write.vue'
import { schemaIssues, validate } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { formatProjectJson } from '@/api/medical/project-json'
import { governanceAllowed, governanceId, governanceReference, listGovernance } from '@/api/medical/member-governance'
import type { GovernanceItem, GovernanceKind, GovernancePage, GovernanceQuery } from '@/api/medical/member-governance'

const user = useUserStore(); const permitted = computed(() => governanceAllowed(user.medicalSession))
const kind = ref<GovernanceKind>('Members'); const listing = ref<GovernancePage | null>(null); const selected = ref<GovernanceItem | null>(null)
const subject = ref(''); const limit = ref('50'); const subjectError = ref(''); const limitError = ref(''); const loading = ref(false)
const titles = { Members: '当前成员记录', Grants: '当前授权记录', Invitations: '当前邀请记录' }
const labels: Record<string, string> = { member_id: '成员标识', user_id: '账户标识', grant_id: '授权标识', subject_id: '授权主体',
  invitation_id: '邀请标识', status: '当前状态', revision: '对象版本', valid_from: '有效起始时间（UTC）', valid_to: '有效截止时间（UTC）',
  scope: '完整授权范围', action: '独立动作', condition: '完整有效条件', basis: '决定依据', roles: '固定岗位版本',
  expires_at: '邀请截止时间（UTC）', created_by: '邀请人员', created_at: '邀请创建时间（UTC）' }
const statusLabels: Record<string, string> = { INVITED: '受邀成员', DISABLED: '已停用', PENDING: '待接受', ACCEPTED: '已接受', EXPIRED: '已过期', REVOKED: '已撤销' }
const rows = computed(() => listing.value?.items.map(item => ({ item, id: governanceId(item), user: 'subject_id' in item ? item.subject_id : item.user_id,
  status: item.status === 'ACTIVE' ? kind.value === 'Members' ? '有效成员' : '有效授权' : statusLabels[item.status],
  revision: item.revision, action: 'action' in item ? item.action : '' })) ?? [])
let controller: AbortController | null = null; let generation = 0; let submitted: GovernanceQuery | null = null
function verified(value: { kind: GovernanceKind; page: GovernancePage; target: GovernanceItem }): void {
  clearRecords(); kind.value = value.kind; listing.value = value.page; selected.value = value.target
  subject.value = 'subject_id' in value.target ? value.target.subject_id : value.target.user_id; limit.value = '100'
  submitted = { subject_id: subject.value, limit: 100 }
}
function clearRecords(): void {
  generation++; controller?.abort(); controller = null; listing.value = null; selected.value = null; submitted = null; loading.value = false
}
function clearSensitive(): void { clearRecords(); subject.value = ''; limit.value = '50'; subjectError.value = ''; limitError.value = '' }
function filters(): GovernanceQuery | null {
  subjectError.value = ''; limitError.value = ''
  if (subject.value && schemaIssues('urn:medical:permission-decisions:1.0.0#/$defs/Id', subject.value).length) subjectError.value = '账户标识需要规范 UUID'
  if (!/^(?:[1-9]|[1-9][0-9]|100)$/.test(limit.value)) limitError.value = '每页数量需要填写1至100的整数'
  if (subjectError.value || limitError.value) {
    document.getElementById(subjectError.value ? 'governance-subject' : 'governance-limit')?.focus(); return null
  }
  const query: GovernanceQuery = { limit: Number(limit.value) }; if (subject.value) query.subject_id = subject.value
  validate(governanceReference('Query'), query)
  return query
}
async function read(query: GovernanceQuery): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session || !governanceAllowed(session, kind.value) || document.hidden || user.medicalWriting) return
  clearRecords(); const current = generation; const selectedKind = kind.value; loading.value = true; controller = new AbortController()
  try {
    const page = await listGovernance(session, selectedKind, query, controller.signal)
    if (generation === current && user.medicalSession === session && kind.value === selectedKind) { listing.value = page; submitted = { ...query } }
  } finally { if (generation === current) loading.value = false }
}
async function search(): Promise<void> { const query = filters(); if (query && !loading.value) await read(query) }
async function next(): Promise<void> {
  if (!listing.value?.next_after || !submitted || loading.value) return
  await read({ ...submitted, after: listing.value.next_after, dataset_watermark: listing.value.dataset_watermark })
}
async function changeKind(value: GovernanceKind): Promise<void> { kind.value = value; await search() }
async function refresh(): Promise<void> { if (!loading.value && !user.medicalWriting) await user.readMedical() }
function visibility(): void { if (document.hidden) clearSensitive() }
watch(() => user.medicalSession, async () => {
  clearSensitive(); const session: MedicalSession | null = user.medicalSession
  if (!session || !permitted.value) return
  kind.value = governanceAllowed(session, 'Members') ? 'Members' : 'Grants'; await search()
}, { immediate: true })
watch(() => user.medicalGeneration, clearSensitive, { flush: 'sync' })
window.addEventListener('medical-failure', clearSensitive); window.addEventListener('offline', clearSensitive)
window.addEventListener('pagehide', clearSensitive); document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => {
  clearSensitive(); window.removeEventListener('medical-failure', clearSensitive); window.removeEventListener('offline', clearSensitive)
  window.removeEventListener('pagehide', clearSensitive); document.removeEventListener('visibilitychange', visibility)
})
</script>

<style scoped>
.medical-governance-value { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

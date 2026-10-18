<template>
 <main class="medical-page capability-page">
  <nav class="breadcrumb" aria-label="位置"><router-link to="/agents">Agent 管理</router-link><span>/ 正式能力</span></nav>
  <header class="page-heading"><div><h1>正式能力草稿</h1><p>读取当前身份可访问的固定角色与提示词声明，核对来源、契约和权限边界。</p></div><router-link to="/agents/capabilities/new"><el-button type="primary" :disabled="availability !== true || busy">登记正式能力草稿</el-button></router-link></header>
  <session-panel />
  <el-alert type="info" :closable="false" title="PRODUCTION 声明仍保存为 DRAFT" description="本目录不提供发布、执行、Skill 安装或权限授予。当前仅支持 ROLE 与 PROMPT。"/>
  <el-alert v-if="error" class="spaced" type="error" :title="error" :closable="false" role="alert"/>
  <section v-if="pending" class="card pending spaced"><h2>有原登记请求待核查</h2><p>原声明和请求键仍保留，请通过只读恢复核对原操作与类型回执。目录记录不能代替原键恢复证据。</p><router-link to="/agents/capabilities/new"><el-button>读取原请求恢复</el-button></router-link></section>
  <section class="card spaced" aria-label="能力版本目录">
   <div class="section-heading"><h2>固定版本目录</h2><el-button :disabled="busy" @click="restart">{{ cursorExpired ? '重新开始目录查询' : '刷新目录首页' }}</el-button></div>
   <p v-if="loading" role="status">正在读取当前可访问的正式能力草稿…</p>
   <p v-else-if="availability === false" role="status">当前部署未启用正式能力作者服务。可用性不代表业务权限。</p>
   <el-empty v-else-if="loaded && records.length === 0" :description="cursor ? '当前扫描页没有可访问记录，可继续读取下一页' : '当前身份没有可访问的正式能力草稿'" />
   <div v-if="records.length" class="record-grid">
    <article v-for="item in records" :key="item.record_id" class="record">
     <div class="section-heading"><h3>{{ item.content.request.kind === 'ROLE' ? '专业角色' : '提示词' }}</h3><el-tag type="warning">DRAFT</el-tag></div>
     <p class="reason">{{ item.content.request.reason }}</p>
     <dl><dt>归属</dt><dd>{{ item.content.request.project_id || '当前企业' }}</dd><dt>创建时间（UTC）</dt><dd>{{ item.content.created_at }}</dd><dt>固定版本</dt><dd>{{ item.resource_id }}</dd><dt>权限声明</dt><dd>{{ Array.isArray(item.content.request.tool_scope) ? '工具范围为空' : item.content.request.tool_scope.permissions.length + ' 项精确权限' }}</dd></dl>
     <router-link :to="'/agents/capabilities/' + item.record_id"><el-button>读取固定详情</el-button></router-link>
    </article>
   </div>
   <div class="actions"><el-button v-if="cursor" :disabled="busy || cursorExpired" @click="nextPage">读取下一页</el-button><span v-if="cursorExpired" role="status">分页身份已过期，请明确点击重新开始。</span><span v-else-if="loaded && !cursor">已读取当前可访问目录的末页。</span></div>
  </section>
 </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import { queryCapabilities, readCapabilityAvailability } from '@/api/medical/formal-capabilities'
import { readPendingCapability } from '@/api/medical/formal-capability-domain'
import type { FormalCapabilityRecord, PendingCapability } from '@/api/medical/formal-capability-domain'
import type { MedicalSession } from '@/api/medical/protocol'
const user = useUserStore(), loading = ref(false), error = ref(''), availability = ref<boolean | null>(null)
const records = ref<FormalCapabilityRecord[]>([]), cursor = ref<string | null>(null), cursorExpired = ref(false), loaded = ref(false), pending = ref<PendingCapability | null>(null)
const busy = computed(() => loading.value || user.medicalWriting)
let controller = new AbortController(), generation = 0, mounted = true
const reader = (): MedicalSession => { if (!user.medicalSession?.current_context) throw new Error('请先选择当前企业'); return user.medicalSession }
function clear() { controller.abort(); controller = new AbortController(); generation++; records.value = []; cursor.value = null; cursorExpired.value = false; availability.value = null; pending.value = null; error.value = ''; loaded.value = false; loading.value = false }
function live(g: number, session: MedicalSession) { return mounted && generation === g && user.medicalSession === session && !document.hidden }
async function load(next: boolean) { const session = user.medicalSession as MedicalSession | null; if (!session?.current_context || busy.value || document.hidden || next && (!cursor.value || cursorExpired.value)) return
 const g = generation, requested = next ? cursor.value : null; loading.value = true; error.value = ''
 try { pending.value = readPendingCapability(session)
   if (!next) { const available = await readCapabilityAvailability(reader, controller.signal); if (!live(g, session)) return; availability.value = available.features.agent_authoring }
   if (availability.value !== true) return
   const page = await queryCapabilities(reader, requested, controller.signal); if (!live(g, session)) return
   const combined = next ? [...records.value, ...page.items] : page.items
   if (new Set(combined.map(item => item.record_id)).size !== combined.length) throw new Error('目录记录发生变化，请重新开始查询')
   records.value = combined; cursor.value = page.next_cursor; loaded.value = true; cursorExpired.value = false
 } catch (failure) { if (live(g, session)) { error.value = failure instanceof Error ? failure.message : '能力目录读取失败'; if ((failure as { code?: string })?.code === 'CURSOR_EXPIRED') cursorExpired.value = true } }
 finally { if (g === generation) loading.value = false }
}
function restart() { if (busy.value) return; clear(); void load(false) }
function nextPage() { void load(true) }
function visibility() { if (document.hidden) clear(); else restart() }
watch(() => user.medicalSession, () => { clear(); void load(false) }, { immediate: true, flush: 'sync' })
document.addEventListener('visibilitychange', visibility); onBeforeUnmount(() => { mounted = false; clear(); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.capability-page{padding:28px;color:#243247;background:#f4f6fa;min-height:100%}.breadcrumb,.actions,.page-heading,.section-heading{display:flex;gap:14px;align-items:center;flex-wrap:wrap}.breadcrumb{font-size:13px;color:#5c6d82}.page-heading,.section-heading{justify-content:space-between}.page-heading{margin:18px 0 24px}.page-heading h1{font-size:24px;margin:0}.card{background:#fff;border:1px solid #dce3ec;border-radius:8px;padding:24px;min-width:0}.card h2{font-size:17px;margin:0}.spaced{margin-top:20px}.record-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;margin-top:20px}.record{background:#f8fafc;border:1px solid #dce3ec;border-radius:8px;padding:20px;min-width:0}.record h3{font-size:16px;margin:0}.reason{white-space:pre-wrap;overflow-wrap:anywhere;max-height:7em;overflow:auto}dl{display:grid;grid-template-columns:105px minmax(0,1fr);gap:10px;font-size:13px}dt{color:#5c6d82}dd{margin:0;overflow-wrap:anywhere}p{color:#526279;line-height:1.65}.actions{margin-top:20px;font-size:13px;color:#5c6d82}.pending{background:#fff9ed}a{color:#175fae}a:focus-visible{outline:2px solid #1768c4;outline-offset:3px}@media(max-width:800px){.record-grid{grid-template-columns:1fr}}@media(max-width:650px){.capability-page{padding:16px}.card,.record{padding:16px}.page-heading{align-items:flex-start;flex-direction:column}dl{grid-template-columns:1fr}}
</style>

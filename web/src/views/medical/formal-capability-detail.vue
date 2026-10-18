<template>
 <main class="medical-page capability-page">
  <nav class="breadcrumb" aria-label="位置"><router-link to="/agents">Agent 管理</router-link><span>/</span><router-link to="/agents/capabilities">正式能力</router-link><span>/ 固定详情</span></nav>
  <header class="page-heading"><div><h1>正式能力固定草稿</h1><p>固定定义不可直接覆盖。新声明需要在当前权限下重新登记与核验。</p></div><el-button :disabled="busy" @click="refresh">重新读取详情</el-button></header>
  <session-panel />
  <el-alert v-if="error" type="error" :closable="false" :title="error" role="alert" />
  <p v-if="loading" role="status">正在核验当前权限与完整固定定义…</p>
  <p v-else-if="availability === false" role="status">当前部署未启用正式能力作者服务，无法读取固定详情。</p>
  <section v-if="record" class="card spaced" aria-label="正式能力固定详情">
   <div class="section-heading"><h2>{{ record.content.request.kind === 'ROLE' ? '专业角色' : '提示词' }}</h2><el-tag type="warning">DRAFT · 不具备生产资格</el-tag></div>
   <el-alert type="info" :closable="false" title="登记声明不等于发布或执行许可" description="角色声明不会建立新授权；后续操作仍需要当前身份、真实依赖与相应用途的独立核验。" />
   <dl class="identity"><dt>登记记录</dt><dd>{{ record.record_id }}</dd><dt>实际固定版本</dt><dd>{{ record.resource_id }}</dd><dt>配置身份</dt><dd>{{ record.content.config_id }}</dd><dt>创建者</dt><dd>{{ record.content.created_by }}</dd><dt>创建时间（UTC）</dt><dd>{{ record.content.created_at }}</dd><dt>固定内容 SHA-256</dt><dd>{{ record.content_sha256 }}</dd></dl>
   <formal-capability-definition :body="record.content.request" />
  </section>
  <div class="actions"><router-link to="/agents/capabilities">返回正式能力目录</router-link><router-link to="/agents/capabilities/new">登记另一份能力声明</router-link></div>
 </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import FormalCapabilityDefinition from './formal-capability-definition.vue'
import { readCapability, readCapabilityAvailability } from '@/api/medical/formal-capabilities'
import type { FormalCapabilityRecord } from '@/api/medical/formal-capability-domain'
import type { MedicalSession } from '@/api/medical/protocol'
const user = useUserStore(), route = useRoute(), loading = ref(false), error = ref(''), availability = ref<boolean | null>(null), record = ref<FormalCapabilityRecord | null>(null)
const busy = computed(() => loading.value || user.medicalWriting)
let controller = new AbortController(), generation = 0, mounted = true
const reader = (): MedicalSession => { if (!user.medicalSession?.current_context) throw new Error('请先选择当前企业'); return user.medicalSession }
function clear() { controller.abort(); controller = new AbortController(); generation++; record.value = null; error.value = ''; loading.value = false; availability.value = null }
async function load() { const session = user.medicalSession as MedicalSession | null; if (!session?.current_context || busy.value || document.hidden) return
 const id = route.params.recordId; if (typeof id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) { error.value = '能力详情地址不完整，请从真实能力目录进入'; return }
 const g = generation, live = () => mounted && g === generation && user.medicalSession === session && route.params.recordId === id && !document.hidden
 loading.value = true; error.value = ''
 try { const available = await readCapabilityAvailability(reader, controller.signal); if (!live()) return; availability.value = available.features.agent_authoring; if (!availability.value) return
   const value = await readCapability(reader, id, controller.signal); if (live()) record.value = value
 } catch (failure) { if (live()) error.value = failure instanceof Error ? failure.message : '固定详情不可用或当前没有读取权限' }
 finally { if (g === generation) loading.value = false }
}
function refresh() { if (busy.value) return; clear(); void load() }
function visibility() { if (document.hidden) clear(); else refresh() }
watch([() => user.medicalSession, () => route.params.recordId], () => { clear(); void load() }, { immediate: true, flush: 'sync' })
document.addEventListener('visibilitychange', visibility); onBeforeUnmount(() => { mounted = false; clear(); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.capability-page{padding:28px;color:#243247;background:#f4f6fa;min-height:100%}.breadcrumb,.actions,.page-heading,.section-heading{display:flex;gap:14px;align-items:center;flex-wrap:wrap}.breadcrumb{font-size:13px;color:#5c6d82}.page-heading,.section-heading{justify-content:space-between}.page-heading{margin:18px 0 24px}.page-heading h1{font-size:24px;margin:0}.card{background:#fff;border:1px solid #dce3ec;border-radius:8px;padding:24px;min-width:0}.card h2{font-size:18px;margin:0}.section-heading{margin-bottom:20px}.spaced{margin-top:20px}.identity{display:grid;grid-template-columns:150px minmax(0,1fr);gap:12px;border-bottom:1px solid #dce3ec;padding-bottom:24px;margin:24px 0}dt{color:#5c6d82}dd{margin:0;overflow-wrap:anywhere}p{color:#526279;line-height:1.65}.actions{margin-top:24px}a{color:#175fae}a:focus-visible{outline:2px solid #1768c4;outline-offset:3px}@media(max-width:650px){.capability-page{padding:16px}.card{padding:16px}.identity{grid-template-columns:1fr}.page-heading{align-items:flex-start;flex-direction:column}}
</style>

<template>
  <section class="candidate-metadata" aria-label="候选元数据读取">
    <h3>已引用候选</h3>
    <p>只读取已获授权的候选元数据，不加载正文、不比较或采用。</p>
    <p v-if="state === 'UNAVAILABLE'" role="status">{{ available ? '可读取当前引用的候选。' : '尚无已核验的候选引用或读取权限。' }}</p>
    <p v-else-if="state === 'LOADING'" role="status">正在读取候选元数据…</p>
    <p v-else-if="state === 'STALE_AUTH'" role="alert">身份或权限已变化，已清除候选信息。请核验后重新读取。</p>
    <p v-else-if="state === 'CANCELLED'" role="status">读取已取消，未保留候选信息。</p>
    <p v-else-if="state === 'ERROR'" role="alert">{{ error }}</p>
    <el-button :disabled="!available || state === 'LOADING'" @click="load">{{ value ? '重新读取元数据' : '读取候选元数据' }}</el-button>
    <el-button v-if="state === 'LOADING'" @click="cancel">取消读取</el-button>
    <dl v-if="value && active">
      <dt>候选状态</dt><dd>{{ statusLabels[value.status] }}</dd>
      <dt>候选修订</dt><dd>{{ value.revision }}</dd>
      <dt>目标节点数</dt><dd>{{ value.target_node_ids.length }}</dd>
      <dt>关联检查数</dt><dd>{{ value.check_ids.length }}</dd>
      <dt>候选标识</dt><dd>{{ value.candidate_id }}</dd>
      <dt>原正文版本</dt><dd>{{ value.base_document_revision }}</dd>
    </dl>
    <p v-if="value">元数据中的产物引用不表示正文可读取，也不表示当前允许采用候选。</p>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { MedicalSession } from '@/api/medical/protocol'
import type { CandidateView } from '../../../../contracts/typescript/src/interfaces'
import { candidateReadAllowed, candidateReadScope, readCandidateMetadata } from '@/api/medical/candidate-read'
import type { CandidateReadState } from '@/api/medical/candidate-read'

// candidateId must come from an authorized caller, never raw user entry or an object-store reference.
const props = defineProps<{ session: MedicalSession; projectId: string; candidateId?: string | null; active: boolean }>()
const state = ref<CandidateReadState>('UNAVAILABLE')
const value = ref<CandidateView | null>(null)
const error = ref('')
const statusLabels = { READY: '候选已就绪（不代表可采用）', ADOPTED: '已采用', REJECTED: '已拒绝', STALE: '已过期' }
const available = computed(() => props.active && !!props.candidateId
  && candidateReadAllowed(props.session, props.projectId, props.candidateId))
let generation = 0
let controller = new AbortController()
let alive = true
function clear(next: CandidateReadState) {
  generation++; controller.abort(); controller = new AbortController()
  value.value = null; error.value = ''; state.value = next
}
function cancel() { clear('CANCELLED') }
async function load() {
  if (!alive || !available.value || state.value === 'LOADING' || document.hidden || !props.candidateId) return
  clear('LOADING')
  const token = generation
  try {
    const result = await readCandidateMetadata(() => props.session, props.projectId, props.candidateId, controller.signal)
    if (!alive || token !== generation || !available.value || document.hidden) return
    value.value = result; state.value = 'READY'
  } catch (failure) {
    if (!alive || token !== generation) return
    const code = (failure as { code?: string })?.code
    value.value = null
    state.value = ['CONTEXT_CHANGED', 'ACCESS_DENIED', 'AUTHENTICATION_REQUIRED', 'AUTHENTICATION_FAILED'].includes(code ?? '') ? 'STALE_AUTH' : 'ERROR'
    error.value = '候选元数据读取失败，请重新读取；未加载正文或执行采用。'
  }
}
watch(() => candidateReadScope(props.session), () => clear('STALE_AUTH'), { flush: 'sync' })
watch(() => [props.session, props.projectId, props.candidateId, props.active], () => clear('UNAVAILABLE'), { flush: 'sync' })
function visibility() { if (document.hidden) clear('CANCELLED') }
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { alive = false; clear('CANCELLED'); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.candidate-metadata { border: 1px solid #dcdfe6; border-radius: 8px; padding: 16px; margin: 16px 0; }
dd { margin: 4px 0 12px; overflow-wrap: anywhere; }
p { color: #606266; }
[role="alert"] { color: #b42318; }
</style>

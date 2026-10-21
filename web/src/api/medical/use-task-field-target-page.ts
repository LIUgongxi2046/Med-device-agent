import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import useUserStore from '@/store/modules/user'
import type { MedicalSession } from './protocol'
import { useMedicalRouteRead } from './route-read'

/** Route, identity and visibility invalidate presentation, never the persisted original request. */
export function useFieldTargetPage(name: string, reset: () => void, bootstrap: () => Promise<void>) {
  const route = useRoute(), user = useUserStore(), loading = ref(false), error = ref('')
  const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
  const taskId = computed(() => typeof route.params.taskId === 'string' ? route.params.taskId : '')
  const basePath = computed(() => '/projects/' + projectId.value + '/tasks/' + taskId.value)
  let controller = new AbortController(), generation = 0, mounted = true, ownsWrite = false, resumeAfterWrite = false, resumeAfterGlobalWrite = false
  function clear() { controller.abort(); controller = new AbortController(); generation++; loading.value = false; error.value = ''; reset() }
  const active = useMedicalRouteRead(name, clear)
  const busy = computed(() => loading.value || user.medicalWriting)
  function reader(): MedicalSession { if (!user.medicalSession?.current_context) throw new Error('请先选择当前企业'); return user.medicalSession }
  async function run(work: (signal: AbortSignal, current: () => boolean) => Promise<void>, write = false) {
    if (!active.value || !user.medicalSession?.current_context || busy.value || document.hidden) return
    const session = reader(), g = generation, path = route.fullPath
    const current = () => mounted && g === generation && reader() === session && active.value && route.fullPath === path && !document.hidden
    error.value = ''
    if (write) { ownsWrite = true; user.medicalWriting = true } else loading.value = true
    try { await work(controller.signal, current) }
    catch (failure) { if (mounted && g === generation && user.medicalSession === session && !document.hidden) error.value = failure instanceof Error ? failure.message : '操作未能核实。请保留原请求并重新读取。' }
    finally { if (g === generation) loading.value = false; if (write && ownsWrite) { ownsWrite = false; user.medicalWriting = false; const resume = resumeAfterWrite; resumeAfterWrite = false; if (resume && mounted && active.value && user.medicalSession?.current_context && !document.hidden) void bootstrap() } }
  }
  function verificationFailure() {
    // Global failures may originate from an older identity or route. Clear data,
    // but never copy their potentially sensitive message into the new context.
    clear(); if (mounted && active.value && !document.hidden) error.value = '请求结果未能核验，已清除当前展示。请重新读取；未确定的原请求仍保留，可继续核查。'
  }
  function resumeRead() { if (user.medicalWriting) { if (ownsWrite) resumeAfterWrite = true; else resumeAfterGlobalWrite = true } else void bootstrap() }
  function visibility() { if (document.hidden) { resumeAfterWrite = false; resumeAfterGlobalWrite = false; clear() } else resumeRead() }
  function start() {
    watch(() => [user.medicalSession, JSON.stringify(user.medicalSession?.current_context), JSON.stringify(user.medicalSession?.action_summary), route.fullPath, active.value], () => {
      clear(); if (active.value && user.medicalSession?.current_context && !document.hidden) resumeRead()
    }, { immediate: true, flush: 'sync' })
    watch(() => user.medicalWriting, (writing, previous) => {
      if (previous && !writing && resumeAfterGlobalWrite) { resumeAfterGlobalWrite = false; if (mounted && active.value && user.medicalSession?.current_context && !document.hidden) void bootstrap() }
    }, { flush: 'sync' })
    document.addEventListener('visibilitychange', visibility)
    window.addEventListener('pagehide', clear); window.addEventListener('offline', clear); window.addEventListener('medical-failure', verificationFailure)
  }
  onBeforeUnmount(() => { mounted = false; clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('pagehide', clear); window.removeEventListener('offline', clear); window.removeEventListener('medical-failure', verificationFailure) })
  return { user, projectId, taskId, basePath, active, busy, loading, error, reader, run, clear, start }
}

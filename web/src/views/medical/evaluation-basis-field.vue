<template>
  <fieldset class="basis-field" :disabled="disabled"><legend>{{ label }}</legend>
    <div class="fields"><el-form-item label="固定版本标识"><el-input :model-value="modelValue.version_id" :disabled="disabled" @update:model-value="change('version_id', $event)" /></el-form-item><el-form-item label="SHA-256 摘要"><el-input :model-value="modelValue.content_sha256" :disabled="disabled" @update:model-value="change('content_sha256', $event)" /></el-form-item></div>
    <template v-if="kind"><el-button :loading="loading" :disabled="disabled" @click="load()">{{ kind === 'candidate' ? '读取配置目录' : '读取已有成果' }}</el-button>
    <el-select v-if="options.length" :model-value="''" placeholder="选择已有固定引用" filterable :disabled="disabled" @update:model-value="select"><el-option v-for="item in options" :key="item.version_id" :label="item.label" :value="item.version_id" /></el-select>
    <el-button v-if="cursor" :disabled="disabled || loading" @click="load(true)">加载更多</el-button><p class="hint">目录仅证明可读取。用途、来源权利及评测资格仍由服务端核验；配置目录状态为未核验。</p></template>
    <p v-if="error" role="alert">{{ error }}</p>
  </fieldset>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import useUserStore from '@/store/modules/user'
import type { Basis } from '@/api/medical/protocol'
import { readArtifactCatalogue } from '@/api/medical/source-governance'
import { listScenarios } from '@/api/medical/scenarios'
import type { ScenarioPage } from '@/api/medical/scenarios'
const props = defineProps<{ modelValue: Basis; label: string; disabled?: boolean; kind?: 'artifact' | 'candidate'; project?: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: Basis] }>()
const user = useUserStore(), options = ref<(Basis & { label: string })[]>([]), loading = ref(false), error = ref(''), cursor = ref<string | null>(null)
let controller = new AbortController(), generation = 0, page: ScenarioPage | null = null
function change(key: keyof Basis, value: string) { emit('update:modelValue', { ...props.modelValue, [key]: value }) }
function select(id: string) { const item = options.value.find(value => value.version_id === id); if (item) emit('update:modelValue', { version_id: item.version_id, content_sha256: item.content_sha256 }) }
function clear() { controller.abort(); controller = new AbortController(); generation++; options.value = []; page = null; cursor.value = null; loading.value = false; error.value = '' }
async function load(more = false) {
  const session = user.medicalSession, token = generation
  if (!session?.current_context || props.disabled || loading.value || document.hidden || !props.kind) return
  loading.value = true; error.value = ''
  try {
    let items: (Basis & { label: string })[], next: string | null
    if (props.kind === 'candidate') {
      const result = await listScenarios(session, { limit: 20, ...(props.project ? { project_id: props.project } : {}), ...(more && cursor.value ? { after: cursor.value, dataset_watermark: page!.dataset_watermark } : {}) }, more ? page : null, controller.signal)
      items = result.items.map(item => ({ version_id: item.version_id, content_sha256: item.content_sha256, label: (item.name || '未命名配置') + ' · 未核验 · ' + item.version_id })); next = result.next_after
      if (generation === token) page = result
    } else {
      const result = await readArtifactCatalogue(session, { limit: 20, ...(props.project ? { project_id: props.project } : {}), ...(more && cursor.value ? { after: cursor.value } : {}) }, controller.signal)
      items = result.items.map(item => ({ version_id: item.artifact_id, content_sha256: item.sha256, label: item.artifact_kind + ' · ' + item.artifact_id })); next = result.next_after
    }
    if (generation !== token || user.medicalSession !== session || document.hidden) return
    if (more && (next === cursor.value || items.some(item => options.value.some(old => old.version_id === item.version_id)))) throw new Error('目录分页未前进，请重新读取')
    options.value = more ? [...options.value, ...items] : items; cursor.value = next
  } catch (failure) { if (generation === token && !controller.signal.aborted) error.value = failure instanceof Error ? failure.message : '目录暂不可读取，可填写已有固定引用' }
  finally { if (generation === token) loading.value = false }
}
function visibility() { if (document.hidden) clear() }
watch(() => [user.medicalSession, props.project, props.kind], clear); document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>.basis-field{min-width:0;border:1px solid #DCE3EC;border-radius:6px;padding:16px;margin:12px 0}.fields{display:grid;grid-template-columns:1fr 1fr;gap:16px}.hint{font-size:12px;color:#526279}.el-select{margin:8px;max-width:100%;width:360px}p{overflow-wrap:anywhere}@media(max-width:700px){.fields{grid-template-columns:1fr}}</style>

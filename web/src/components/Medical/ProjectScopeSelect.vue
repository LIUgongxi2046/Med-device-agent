<template>
  <div class="project-scope-select">
    <el-select :model-value="modelValue" filterable allow-create clearable default-first-option
      :loading="loading" placeholder="选择项目或输入项目标识" @update:model-value="value => emit('update:modelValue', value || '')">
      <el-option v-for="project in projects" :key="project.project_id" :label="project.name" :value="project.project_id" />
      <template #footer><el-button v-if="nextAfter" text :loading="loading" @click="load(nextAfter)">加载更多获授权项目</el-button><span v-else class="scope-hint">留空查询当前获授权范围</span></template>
    </el-select>
    <span v-if="error" role="status" class="scope-hint">项目名称暂时无法读取；可保留或输入项目标识。</span>
  </div>
</template>
<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { listProjects } from '@/api/medical/projects'
import { projectActionAllowed } from '@/api/medical/project-write'
defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])
const user = useUserStore(); const projects = ref([]); const nextAfter = ref(null); const loading = ref(false); const error = ref(false)
let generation = 0; let controller = null
function clear() { generation += 1; controller?.abort(); controller = null; projects.value = []; nextAfter.value = null; loading.value = false; error.value = false }
async function load(after) {
  const session = user.medicalSession; const context = session?.current_context
  if (!context || document.hidden || !projectActionAllowed(session, 'project.read')) return
  controller?.abort(); const reading = new AbortController(); controller = reading; const current = generation; loading.value = true; error.value = false
  try {
    const result = await listProjects(context, after ? { after } : {}, reading.signal)
    if (generation !== current || controller !== reading || user.medicalSession !== session || document.hidden) return
    if (after && (result.next_after === after || result.projects.some(item => projects.value.some(existing => existing.project_id === item.project_id)))) throw new Error('项目分页没有前进')
    projects.value = after ? [...projects.value, ...result.projects] : result.projects; nextAfter.value = result.next_after
  } catch { if (generation === current && !reading.signal.aborted) error.value = true }
  finally { if (controller === reading) loading.value = false }
}
watch(() => user.medicalSession, () => { clear(); load() }, { immediate: true })
onBeforeUnmount(clear)
</script>
<style scoped>
.project-scope-select { max-width: 100%; }
.project-scope-select .el-select { width: 240px; max-width: 100%; }
.scope-hint { display: block; color: #526279; font-size: 12px; line-height: 1.6; }
</style>

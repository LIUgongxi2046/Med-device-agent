<template>
  <form class="field-target-card" @submit.prevent="submit" data-professional-package-form>
    <h2>登记来源文件，不批准或运行</h2>
    <fieldset :disabled="busy || blocked || loadingFiles">
      <label>来源包类型<select v-model="kind"><option value="">请选择类型</option><option v-for="choice in preparation.kinds" :key="choice.kind" :value="choice.kind">{{ packageLabels[choice.kind] }}</option></select></label>
      <template v-if="kind === 'PROMPT'">
        <p>仅适用于 N02，输入编码固定为 TASK_NODE_INPUT_CANONICAL_JSON。</p>
        <label>提示词正文<textarea v-model="systemInstructions" rows="10" maxlength="262144" placeholder="填写明确的研究角色要求、边界及输出要求" /></label>
      </template>
      <template v-else-if="kind === 'ROLE'">
        <label>角色<select v-model="roleName"><option value="">请选择角色</option><option value="Researcher">Researcher</option><option value="NetworkResearcher">NetworkResearcher（需相应部署与发布支持）</option></select></label>
        <label>已有提示词能力版本<select v-model="promptId"><option value="">请选择真实版本，不会自动批准</option><option v-for="item in preparation.prompts" :key="item.version.version_id" :value="item.version.version_id">DRAFT · {{ item.version.version_id }} · {{ item.version.content_sha256.slice(0, 12) }}</option></select></label>
        <p v-if="!preparation.prompts.length">当前没有可读取的提示词能力版本。请先登记提示词来源包，再单独创建正式能力声明。</p>
        <label>已有配置来源包<select v-model="profileId"><option value="">请选择实际登记的配置包</option><option v-for="item in preparation.profiles" :key="item.artifact.artifact_id" :value="item.artifact.artifact_id">{{ item.artifact.artifact_id }} · {{ item.artifact.sha256.slice(0, 12) }}</option></select></label>
        <p v-if="!preparation.profiles.length">当前没有可用配置包，不能用任意编号或摘要替代。</p>
      </template>
      <template v-else-if="kind === 'PROFILE'">
        <p>配置名固定为 medical-agent，投影固定为 TEXT_SYSTEM_USER_V1。登记以下声明不会部署配置或软件。</p>
        <label>系统前缀（可以为空）<textarea v-model="systemPrefix" rows="5" maxlength="262144" /></label>
        <label>实际登记的 C07 适配器<select v-model="adapterId"><option value="">请选择实际适配器文件</option><option v-for="item in preparation.adapters" :key="item.artifact.artifact_id" :value="item.artifact.artifact_id">{{ item.artifact.artifact_id }} · {{ item.artifact.media_type }} · {{ item.artifact.sha256.slice(0, 12) }}</option></select></label>
        <p v-if="!preparation.adapters.length" role="status">缺少经过管理的适配器导入记录，当前不能登记完整配置包。不会生成替代文件或虚构依赖。</p>
        <label>选择实际配置文件，计算声明中的摘要<input type="file" multiple @change="chooseFiles" /></label>
        <label>或选择 profiles 文件夹<input type="file" multiple webkitdirectory @change="chooseFiles" /></label>
        <p>这里只读取本机选中文件以计算路径、字节数和摘要，不上传这些文件、不部署也不运行。后续部署仍须核对真实文件。</p>
        <ul><li v-for="file in profileFiles" :key="file.relative">{{ file.relative }} · {{ file.size_bytes }} 字节 · {{ file.sha256.slice(0, 16) }} <button type="button" @click="removeFile(file.relative)">移除</button></li></ul>
        <p v-if="fileError" role="alert">{{ fileError }}</p>
      </template>
      <template v-if="kind">
        <fieldset><legend>实际使用的内容来源（不默认勾选）</legend>
          <p>选择本次内容实际引用的固定来源。角色和配置依赖会另外核查，不会因选择来源而自动获得模型发送权限。</p>
          <label v-for="item in preparation.sources" :key="item.version.version_id"><input v-model="sourceIds" type="checkbox" :value="item.version.version_id" />{{ item.source_id }} · {{ item.classification }} · 版本 {{ item.version.version_id }}</label>
          <p v-if="!preparation.sources.length">当前没有可选的外部内容来源。请勿把未核实来源的材料当作已授权内容。</p>
        </fieldset>
        <p>准备依据：{{ preparation.preparation_sha256 }}。大小上限 {{ preparation.maximum_package_bytes }} 字节。</p>
        <p v-if="problem" role="status">{{ problem }}</p>
        <label><input v-model="confirmed" type="checkbox" />我已核对原文、实际来源和固定依赖。此操作只登记来源文件，不会创建批准、发布或运行任务。</label>
      </template>
    </fieldset>
    <p v-if="loadingFiles" role="status">正在读取所选配置文件的实际字节并计算摘要…</p>
    <button :disabled="busy || blocked || loadingFiles || !allowed || !confirmed || !!problem || !body" type="submit">登记来源包</button>
  </form>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { byteDigest } from '@/api/medical/content-digest'
import { canonical, detached, packageIssue, packageLabels, profilePathIssue } from '@/api/medical/task-professional-package-domain'
import type { Kind, Preparation, ProfilePackage, Request } from '@/api/medical/task-professional-package-domain'
const props = defineProps<{ preparation: Preparation; busy: boolean; blocked: boolean; allowed: boolean }>()
const emit = defineEmits<{ submit: [body: Request] }>()
const kind = ref<Kind | ''>(''), systemInstructions = ref(''), systemPrefix = ref(''), roleName = ref<'' | 'Researcher' | 'NetworkResearcher'>('')
const promptId = ref(''), profileId = ref(''), adapterId = ref(''), sourceIds = ref<string[]>([]), profileFiles = ref<ProfilePackage['files']>([])
const confirmed = ref(false), loadingFiles = ref(false), fileError = ref(''); let fileGeneration = 0
function resetFields() { fileGeneration++; systemInstructions.value = ''; systemPrefix.value = ''; roleName.value = ''; promptId.value = ''; profileId.value = ''; adapterId.value = ''; sourceIds.value = []; profileFiles.value = []; confirmed.value = false; loadingFiles.value = false; fileError.value = '' }
watch(kind, resetFields, { flush: 'sync' })
watch(() => props.preparation.preparation_sha256, () => { kind.value = ''; resetFields() }, { flush: 'sync' })
const body = computed<Request | null>(() => {
  if (new Set(sourceIds.value).size !== sourceIds.value.length || sourceIds.value.some(id => !props.preparation.sources.some(v => v.version.version_id === id))) return null
  const common = { schema_version: '1.0.0' as const, preparation_sha256: props.preparation.preparation_sha256, content_sources: props.preparation.sources.filter(v => sourceIds.value.includes(v.version.version_id)).map(v => v.version) }
  if (kind.value === 'PROMPT') return { ...common, kind: 'PROMPT', package: { schema_version: '1.0.0', node_kind: 'N02', system_instructions: systemInstructions.value, input_encoding: 'TASK_NODE_INPUT_CANONICAL_JSON' } }
  if (kind.value === 'ROLE') { const prompt = props.preparation.prompts.find(v => v.version.version_id === promptId.value), profile = props.preparation.profiles.find(v => v.artifact.artifact_id === profileId.value); if (!prompt || !profile || !roleName.value) return null; return { ...common, kind: 'ROLE', package: { schema_version: '1.0.0', node_kind: 'N02', role_name: roleName.value, prompt_basis: prompt.version, profile_artifact: profile.artifact } } }
  if (kind.value === 'PROFILE') { const adapter = props.preparation.adapters.find(v => v.artifact.artifact_id === adapterId.value); if (!adapter) return null; return { ...common, kind: 'PROFILE', package: { schema_version: '1.0.0', node_kind: 'N02', profile_name: 'medical-agent', projection: 'TEXT_SYSTEM_USER_V1', system_prefix: systemPrefix.value, files: profileFiles.value, adapter_artifact: adapter.artifact } } }
  return null
})
const problem = computed(() => body.value ? packageIssue(body.value, props.preparation) : '请选择类型并补齐真实固定依赖')
watch(() => canonical([body.value, kind.value, sourceIds.value, promptId.value, profileId.value, adapterId.value]), () => { confirmed.value = false }, { flush: 'sync' })
function removeFile(path: string) { profileFiles.value = profileFiles.value.filter(v => v.relative !== path) }
async function chooseFiles(event: Event) {
  const input = event.target as HTMLInputElement, files = Array.from(input.files ?? []), generation = ++fileGeneration
  confirmed.value = false; loadingFiles.value = true; fileError.value = ''; profileFiles.value = []
  try {
    if (!files.length || files.length > 256 || files.reduce((sum, file) => sum + file.size, 0) > 67108864) throw Error('请选择1至256个配置文件，所选文件总量最多64 MiB')
    const values: ProfilePackage['files'] = [], paths = new Set<string>()
    for (const file of files) {
      const selectedPath = file.webkitRelativePath || file.name, relative = selectedPath.startsWith('profiles/') ? selectedPath : 'profiles/' + selectedPath
      const issue = profilePathIssue(relative); if (issue) throw Error(issue)
      if (paths.has(relative) || file.size === 0) throw Error('配置文件不能为空或具有重复声明路径'); paths.add(relative)
      const bytes = await file.arrayBuffer(); if (generation !== fileGeneration) return
      if (bytes.byteLength !== file.size) throw Error('所选文件的字节数发生变化，请重新选择')
      const sha256 = await byteDigest(bytes); if (generation !== fileGeneration) return
      values.push({ relative, sha256, size_bytes: String(bytes.byteLength) })
    }
    if (generation === fileGeneration) profileFiles.value = values.sort((a, b) => a.relative.localeCompare(b.relative))
  } catch (failure) { if (generation === fileGeneration) fileError.value = failure instanceof Error ? failure.message : '配置文件摘要计算失败' }
  finally { if (generation === fileGeneration) { loadingFiles.value = false; input.value = '' } }
}
function submit() { if (props.busy || props.blocked || loadingFiles.value || !props.allowed || !confirmed.value || problem.value || !body.value) return; emit('submit', detached(body.value)) }
onBeforeUnmount(() => { fileGeneration++ })
</script>

<template>
  <main class="medical-page field-target-page" data-task-professional-packages>
    <nav><router-link :to="projectPath">返回项目</router-link><router-link to="/agents/capabilities">正式能力草稿</router-link><router-link to="/library/artifacts">查看已登记成果</router-link></nav>
    <header><h1>研究配置来源包</h1><button :disabled="busy" @click="bootstrap">重新读取</button></header>
    <p class="field-target-notice">先登记实际的提示词、角色或配置文件，再另行创建正式能力并独立审核发布。这里不会部署或执行代码，也不会自动创建批准。</p>
    <p v-if="loading" role="status">正在读取当前项目的真实配置依据…</p><p v-if="error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
    <section v-if="pending" class="field-target-card" aria-label="原登记请求恢复">
      <h2>原登记请求尚待核查</h2><p>类型：{{ packageLabels[pending.body.kind] }} · 原请求 {{ pending.key }}</p><p>完整请求摘要：{{ pending.body_sha256 }}</p>
      <p>此操作可能核查实际对象并完成原登记，但不会重建上传、替换原请求或自动批准。未发现回执也不代表可以另建请求。</p>
      <button :disabled="busy" @click="recover">核查并完成原登记</button>
    </section>
    <section v-if="status" class="field-target-card" aria-label="实际登记状态">
      <h2>{{ statusLabels[status.state] }}</h2>
      <p v-if="status.state === 'NOT_FOUND'">尚未发现可核实的原登记结果。保留原请求，不自动重发。</p>
      <p v-else-if="status.state === 'PROCESSING'">原操作已存在，但实际文件登记尚未完成，不能作为可运行能力使用。</p>
      <template v-else><p>实际文件：{{ status.artifact?.artifact_id }} · {{ status.artifact?.size_bytes }} 字节</p><p>摘要：{{ status.artifact?.sha256 }}</p><p>对象版本：{{ status.object_version }}</p><p>来源已登记，但尚未批准、部署或发布，不代表任务已经可运行。</p><router-link to="/agents/capabilities/new">另行登记正式能力草稿</router-link></template>
      <p v-if="status.operation_id">原操作：{{ status.operation_id }}</p>
    </section>
    <template v-if="preparation && !pending">
      <section class="field-target-card"><h2>尚需完成的准备</h2><ul><li v-for="item in preparation.prerequisites" :key="item">{{ prerequisiteLabels[item] }}</li></ul><p v-if="!allowed">当前身份没有这个项目的 capability.configure 权限，不能登记来源包。</p></section>
      <task-professional-package-form :preparation="preparation" :busy="busy" :blocked="!!pending" :allowed="allowed" @submit="create" />
    </template>
    <p v-else-if="!pending && !loading && !preparation">请重新读取当前项目的准备信息。缺少来源、依赖或权限时不会生成替代记录。</p>
  </main>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import TaskProfessionalPackageForm from './task-professional-package-form.vue'
import { useFieldTargetPage } from '@/api/medical/use-task-field-target-page'
import { capabilityAllowed } from '@/api/medical/formal-capability-domain'
import { readPackagePreparation, readPendingPackage, createPackage, recoverPackage, packageLabels, prerequisiteLabels } from '@/api/medical/task-professional-packages'
import type { Preparation, PendingPackage, Request, Status } from '@/api/medical/task-professional-packages'
import './task-field-target.css'
const preparation = ref<Preparation | null>(null), pending = ref<PendingPackage | null>(null), status = ref<Status | null>(null), notice = ref('')
function reset() { preparation.value = null; pending.value = null; status.value = null; notice.value = '' }
const { user, projectId, busy, loading, error, reader, run, start } = useFieldTargetPage('MedicalTaskProfessionalPackages', reset, bootstrap)
const projectPath = computed(() => '/projects/' + projectId.value), allowed = computed(() => capabilityAllowed(user.medicalSession, projectId.value))
const statusLabels = { NOT_FOUND: '尚未发现登记结果', PROCESSING: '原登记仍在处理中', REGISTERED: '实际来源文件已登记' } as const
async function bootstrap() { await run(async (signal,current) => { preparation.value = null; status.value = null; notice.value = ''; const p = readPendingPackage(reader(), projectId.value); if (!current()) return; pending.value = p; if (p) return; const value = await readPackagePreparation(reader, projectId.value, signal); if (current()) preparation.value = value }) }
async function create(body: Request) {
  if (pending.value || !allowed.value) return
  await run(async (signal,current) => { const result = await createPackage(reader, projectId.value, body, signal, value => { if (current()) { pending.value = value; if (value === null) preparation.value = null } }); if (current()) { status.value = result; if (result.state === 'REGISTERED') { pending.value = null; preparation.value = null; notice.value = '实际文件与原登记回执已读回核验。下一步仍需单独创建能力并审核发布。' } else notice.value = '保留原请求，请核查并完成原登记。' } }, true)
}
async function recover() { const p = pending.value; if (!p) return; await run(async (signal,current) => { const result = await recoverPackage(reader, p, signal, value => { if (current()) { pending.value = value; if (value === null) preparation.value = null } }); if (current()) { status.value = result; if (result.state === 'REGISTERED') { pending.value = null; preparation.value = null } notice.value = result.state === 'REGISTERED' ? '原登记已完成并核验，没有另建上传或批准。' : '原请求仍保留，不会自动替换、重发或批准。' } }, true) }
start()
</script>

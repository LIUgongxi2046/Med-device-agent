<template>
  <el-dialog v-model="visible" :title="editing ? '修订已注册任务' : '创建已注册任务'" width="620px" :close-on-click-modal="false" :close-on-press-escape="!frozen" :show-close="!frozen">
    <el-alert title="仅使用已发布编译处理器，不接受调用表达式。创建后保持停用；修订前必须停用。Cron 使用 UTC。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-width="110px" :disabled="!!frozen">
      <el-form-item label="注册处理器">
        <el-select v-if="!editing" v-model="form.jobId" @change="selectHandler">
          <el-option v-for="entry in registrations" :key="entry.jobId" :value="entry.jobId" :label="entry.handlerKey + ' / ' + entry.jobGroup" />
        </el-select>
        <span v-else>{{ form.jobId }}</span>
      </el-form-item>
      <el-form-item label="任务名称"><el-input v-model="form.jobName" maxlength="64" /></el-form-item>
      <el-form-item label="Cron (UTC)"><el-input v-model="form.cronExpression" maxlength="255" /></el-form-item>
      <el-form-item label="错过触发策略">
        <el-select v-model="form.misfirePolicy">
          <el-option label="默认" value="0" /><el-option label="忽略错过触发" value="1" />
          <el-option label="补执行一次" value="2" /><el-option label="不补执行" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="并发规则"><span>{{ form.concurrent === '1' ? '编译处理器禁止并发' : '编译处理器允许并发' }}</span></el-form-item>
    </el-form>
    <template #footer><el-button v-if="frozen" @click="recover(false)">读取命令回执</el-button><el-button v-if="frozen" @click="recover(true)">核对调度状态</el-button><el-button @click="visible = false" :disabled="saving || !!frozen">取消</el-button><el-button type="primary" :loading="saving" @click="save">提交固定定义</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref } from 'vue'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
const emit = defineEmits(['saved'])
const visible = ref(false), editing = ref(false), saving = ref(false), error = ref(''), registrations = ref([])
const form = ref({ jobId: '', jobName: '', cronExpression: '', misfirePolicy: '3', concurrent: '1' })
const frozen = ref(null)
let revision = '', key = ''
const storageKey = 'framework-job-definition:' + (useUserStore().medicalSession?.user.user_id || 'unavailable')
try {
  const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null')
  if (saved) { frozen.value = saved.body; form.value = { ...saved.body }; editing.value = saved.editing; key = saved.key; revision = saved.body.expectedRevision || '' }
} catch { /* restoration never sends a command */ }
async function open(row) {
  if (frozen.value) { visible.value = true; return }
  error.value = ''; visible.value = true; editing.value = Boolean(row); key = crypto.randomUUID(); revision = ''
  form.value = { jobId: '', jobName: '', cronExpression: '', misfirePolicy: '3', concurrent: '1' }
  try {
    if (row) {
      const value = (await request.get('/monitor/job/' + row.jobId)).data
      if (value.status !== '1' || !value.frameworkRevision) throw new Error('请先停用已注册任务并重新读取版本')
      revision = value.frameworkRevision
      for (const field of Object.keys(form.value)) form.value[field] = String(value[field])
    } else registrations.value = (await request.get('/monitor/job/registrations')).data
  } catch (failure) { error.value = failure?.message || '无法读取当前注册定义' }
}
function selectHandler(id) { const entry = registrations.value.find(item => item.jobId === id); if (entry) form.value.concurrent = entry.concurrent }
async function recover(observe) {
  try {
    const response = observe ? await request.post('/monitor/job/commands/' + key + '/observations') : await request.get('/monitor/job/commands/' + key)
    emit('saved', response)
    if (['DISPATCHED', 'FAILED'].includes(response.data?.state)) { frozen.value = null; sessionStorage.removeItem(storageKey); visible.value = false }
    else error.value = '结果仍未确认，保留冻结定义和原命令身份'
  } catch (failure) { error.value = failure?.message || '无法确认命令结果，请勿创建替代命令' }
}
async function save() {
  if (saving.value || !form.value.jobId || !form.value.jobName.trim() || !form.value.cronExpression.trim() || editing.value && !revision) return
  saving.value = true; error.value = ''
  try {
    if (!frozen.value) frozen.value = { ...form.value, ...(editing.value ? { expectedRevision: revision } : {}) }
    sessionStorage.setItem(storageKey, JSON.stringify({ body: frozen.value, key, editing: editing.value }))
    const body = JSON.parse(JSON.stringify(frozen.value))
    const response = await request({ url: '/monitor/job', method: editing.value ? 'put' : 'post', data: body, headers: { 'Idempotency-Key': key } })
    emit('saved', response)
    if (['DISPATCHED', 'FAILED'].includes(response.data?.state)) { frozen.value = null; sessionStorage.removeItem(storageKey); visible.value = false }
    else error.value = '结果未确认，定义已冻结；请核对命令或重送同一请求' 
  } catch (failure) { error.value = failure?.message || '提交结果未确认，请保持原请求身份重试' }
  finally { saving.value = false }
}
defineExpose({ open })
</script>

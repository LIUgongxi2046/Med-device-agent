<template>
  <div v-if="medical" class="app-container">
    <el-alert title="仅展示连接池计数，不提供 SQL 控制台、连接地址或凭据。" type="info" :closable="false" />
    <el-button @click="load" :loading="loading">刷新</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-descriptions v-if="metrics" :column="2" border>
      <el-descriptions-item label="活动连接">{{ metrics.activeCount }}</el-descriptions-item>
      <el-descriptions-item label="空闲连接">{{ metrics.poolingCount }}</el-descriptions-item>
      <el-descriptions-item label="连接上限">{{ metrics.maxActive }}</el-descriptions-item>
      <el-descriptions-item label="等待线程">{{ metrics.waitThreadCount }}</el-descriptions-item>
    </el-descriptions>
  </div>
  <i-frame v-else v-model:src="url" />
</template>
<script setup>
import { ref, onMounted } from 'vue'
import iFrame from '@/components/iFrame'
import request from '@/utils/request'
const medical = import.meta.env.VITE_APP_ENV === 'medical'
const url = ref(import.meta.env.VITE_APP_BASE_API + '/druid/login.html')
const metrics = ref(null), loading = ref(false), error = ref('')
async function load() {
  loading.value = true; error.value = ''; metrics.value = null
  try { metrics.value = (await request.get('/monitor/druid')).data }
  catch (failure) { error.value = failure?.message || '连接池指标暂不可用' }
  finally { loading.value = false }
}
onMounted(() => { if (medical) load() })
</script>

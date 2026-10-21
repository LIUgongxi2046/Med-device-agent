<template>
  <div v-if="medical" class="app-container">
    <el-alert title="当前权限范围内的实际接口，仅供读取，不提供携带凭据的接口调试。" type="info" :closable="false" />
    <el-button :loading="loading" @click="load">刷新</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="operations">
      <el-table-column prop="method" label="方法" width="100" />
      <el-table-column prop="path" label="实际路径" />
      <el-table-column prop="permission" label="原权限" />
    </el-table>
  </div>
  <i-frame v-else v-model:src="url" />
</template>
<script setup>
import { ref, onMounted } from 'vue'
import iFrame from '@/components/iFrame'
import request from '@/utils/request'
const medical = import.meta.env.VITE_APP_ENV === 'medical'
const url = ref(import.meta.env.VITE_APP_BASE_API + '/swagger-ui/index.html')
const operations = ref([]), loading = ref(false), error = ref('')
async function load() {
  operations.value = []; loading.value = true; error.value = ''
  try { operations.value = (await request.get('/tool/swagger')).data.operations }
  catch (failure) { error.value = failure?.message || '接口清单不可用' }
  finally { loading.value = false }
}
onMounted(() => { if (medical) load() })
</script>

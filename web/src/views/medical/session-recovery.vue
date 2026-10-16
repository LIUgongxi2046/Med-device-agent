<template>
  <main class="app-container session-recovery">
    <h1>重新核查业务会话</h1>
    <el-alert v-if="user.medicalError" :title="user.medicalError.message" type="error" :closable="false" role="alert">
      <p>错误代码：{{ user.medicalError.code }}</p>
      <p v-if="user.medicalError.requestId">请求编号：{{ user.medicalError.requestId }}</p>
    </el-alert>
    <p>页面读取尚未完成。原业务请求的安全身份及摘要仍按原操作保存。</p>
    <el-button :loading="working || user.medicalLoading" :disabled="working || user.medicalLoading || user.medicalWriting"
      @click="resume">重新读取会话并继续页面</el-button>
    <p v-if="!user.medicalPendingNavigation">当前内存没有保留目标页面，可以重新进入项目或资料中心。</p>
    <router-link to="/projects">项目列表</router-link>
    <router-link to="/library">资料中心</router-link>
    <router-link to="/access">业务账户入口</router-link>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { fail } from '@/api/medical/protocol'

const user = useUserStore(), router = useRouter(), working = ref(false)
async function resume() {
  if (working.value || user.medicalLoading || user.medicalWriting || document.hidden) return
  const target = user.medicalPendingNavigation
  if (target !== null) {
    const resolved = router.resolve(target)
    if (typeof target !== 'string' || !target.startsWith('/') || target.startsWith('//')
        || !resolved.meta.medical || resolved.meta.public) fail('SCHEMA_INVALID', '原医疗页面身份无效')
  }
  working.value = true
  try {
    const current = await user.readMedical()
    if (!current || document.hidden || router.currentRoute.value.name !== 'MedicalSessionRecovery') return
    if (target !== null) await router.replace(target)
  } finally { working.value = false }
}
</script>

<style scoped>
.session-recovery { max-width: 900px; margin: 40px auto; }
a { display: inline-block; margin: 24px 24px 0 0; }
</style>

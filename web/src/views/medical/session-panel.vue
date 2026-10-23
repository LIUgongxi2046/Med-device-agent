<template>
  <section v-if="user.medicalError || !user.medicalSession || !user.medicalSession.current_context" class="session-notice" aria-label="组织会话状态">
    <el-alert v-if="user.medicalError" :title="user.medicalError.message" type="error" :closable="false" show-icon role="alert">
      <p v-if="user.medicalError.requestId">请求编号：{{ user.medicalError.requestId }}</p>
      <el-button :disabled="user.medicalLoading || user.medicalWriting" @click="user.readMedical()">重新读取会话</el-button>
    </el-alert>
    <el-alert v-else-if="user.medicalLoading" title="正在核查当前组织与访问权限" type="info" :closable="false" show-icon />
    <el-alert v-else-if="user.medicalSession" title="请在顶部选择工作组织" description="组织选择会应用于项目、资料、模型和全部业务菜单。" type="info" :closable="false" show-icon />
    <el-button v-else :disabled="user.medicalWriting" @click="user.readMedical()">重新读取会话</el-button>
  </section>
</template>

<script setup>
import useUserStore from '@/store/modules/user'

const user = useUserStore()
</script>

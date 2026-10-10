<template>
  <section aria-label="当前账户和企业空间">
    <router-link v-if="user.medicalSession" to="/member-invitations/acceptances">接受企业邀请</router-link>
    <el-alert v-if="user.medicalError" :title="user.medicalError.message" type="error" :closable="false" role="alert">
      <p v-if="user.medicalError.requestId">请求编号：{{ user.medicalError.requestId }}</p>
    </el-alert>
    <p v-if="user.medicalLoading" role="status">正在读取实际会话与企业身份</p>
    <template v-if="user.medicalSession">
      <p v-if="user.medicalSession.memberships.length === 0">当前账户没有企业成员身份</p>
      <p v-if="user.medicalSession.current_context === null">尚未选择企业空间</p>
      <p v-else>当前企业：{{ user.medicalSession.current_context.tenant_id }}；上下文版本：{{ user.medicalSession.current_context.context_revision }}</p>
      <el-form label-position="top" @submit.prevent="select">
        <el-form-item label="企业空间">
          <el-select v-model="target" placeholder="请选择获授权企业" :disabled="user.medicalWriting || activeMembers.length === 0">
            <el-option v-for="member in activeMembers" :key="member.tenant_id" :label="member.tenant_id" :value="member.tenant_id" />
          </el-select>
        </el-form-item>
        <el-button native-type="submit" :disabled="!target || user.medicalWriting || activeMembers.length === 0">确认选择企业</el-button>
      </el-form>
      <p v-if="user.medicalPendingSelection">原企业选择待核查；请求编号：{{ user.medicalPendingSelection.key }}</p>
    </template>
    <el-button :disabled="user.medicalLoading || user.medicalWriting" @click="user.readMedical()">重新读取会话</el-button>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'

const user = useUserStore()
const target = ref('')
const activeMembers = computed(() => user.medicalOptions === null ? [] : user.medicalOptions.memberships.filter(member => member.status === 'ACTIVE'))
watch(() => user.medicalOptions, () => { target.value = '' })
async function select() {
  if (target.value) await user.selectMedical(target.value)
}
</script>

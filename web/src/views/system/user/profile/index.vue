<template>
   <main class="app-container" data-framework-profile>
      <template v-if="medical">
         <h1>本人账户</h1>
         <session-panel />
         <p v-if="loading" role="status">正在核查本人账户及原个人资料</p>
         <section v-if="userStore.medicalPendingCredential" data-pending-credential aria-label="原密码请求">
            <p>原密码请求待核查。请求编号：{{ userStore.medicalPendingCredential.key }}；原账户版本：{{ userStore.medicalPendingCredential.revision }}</p>
            <p>重新登录和刷新以后使用原输入核查原请求。密码需要本人重新输入。清除请求以前，请确认服务端结果。</p>
            <el-button :disabled="userStore.medicalWriting || !userStore.medicalSession" @click="clearCredential">清除待提交请求</el-button>
         </section>
         <section v-if="userStore.medicalPendingProfile" data-pending-profile aria-label="原资料请求">
            <p>原资料请求待核查。请求编号：{{ userStore.medicalPendingProfile.key }}；原账户版本：{{ userStore.medicalPendingProfile.revision }}</p>
            <p>恢复连接后重新输入完整原资料并提交同一请求。清除请求以前，请确认服务端结果。</p>
            <el-button :disabled="userStore.medicalWriting || !userStore.medicalSession" @click="userStore.clearMedicalProfile()">清除待提交资料请求</el-button>
         </section>
      </template>
      <el-row v-if="state.user" :gutter="20" :data-own-account="medical ? '' : undefined">
         <el-col :span="6" :xs="24">
            <el-card class="box-card">
               <template v-slot:header>
                 <div class="clearfix">
                   <span>个人信息</span>
                 </div>
               </template>
               <div>
                  <div class="text-center">
                     <userAvatar :disabled="medical" />
                  </div>
                  <ul class="list-group list-group-striped">
                     <li class="list-group-item">
                        <svg-icon icon-class="user" />用户名称
                        <div class="pull-right" data-own-username>{{ state.user.userName }}</div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="phone" />手机号码
                        <div class="pull-right">{{ state.user.phonenumber }}</div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="email" />用户邮箱
                        <div class="pull-right">{{ state.user.email }}</div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="tree" />所属部门
                        <div class="pull-right" v-if="state.user.dept">{{ state.user.dept.deptName }} / {{ state.postGroup }}</div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="peoples" />所属角色
                        <div class="pull-right">{{ state.roleGroup }}</div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="date" />创建日期
                        <div class="pull-right">{{ state.user.createTime }}</div>
                     </li>
                     <template v-if="medical && account">
                        <li class="list-group-item">独立账户标识<div class="pull-right" data-own-id>{{ account.user_id }}</div></li>
                        <li class="list-group-item">平台角色<div class="pull-right">{{ account.platform_role === 'PLATFORM_ADMIN' ? '平台管理员' : '普通用户' }}</div></li>
                        <li class="list-group-item">账户版本<div class="pull-right">{{ account.revision }}</div></li>
                     </template>
                  </ul>
               </div>
            </el-card>
         </el-col>
         <el-col :span="18" :xs="24">
            <el-card>
               <template v-slot:header>
                 <div class="clearfix">
                   <span>基本资料</span>
                 </div>
               </template>
               <el-tabs v-model="selectedTab">
                  <el-tab-pane label="基本资料" name="userinfo">
                     <userInfo :user="state.user" :account="account" :disabled="loading" />
                  </el-tab-pane>
                  <el-tab-pane label="修改密码" name="resetPwd">
                     <template v-if="medical"><h2>变更本人密码</h2><p>密码至少15个字符，UTF-8编码最多72个字节。变更成功以后，全部旧会话撤销，需要使用新密码重新登录。</p></template>
                     <resetPwd ref="passwordForm" :account="account" :disabled="loading" />
                  </el-tab-pane>
               </el-tabs>
            </el-card>
         </el-col>
      </el-row>
   </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import userAvatar from './userAvatar.vue'
import userInfo from './userInfo.vue'
import resetPwd from './resetPwd.vue'
import { getUserProfile } from "@/api/system/user"
import useUserStore from '@/store/modules/user'
import SessionPanel from '@/views/medical/session-panel.vue'
import { readOwnAccount } from '@/api/medical/account'
import type { OwnAccount, FrameworkProfile } from '@/api/medical/account'
import defAva from '@/assets/images/profile.jpg'

const route = useRoute()
const userStore = useUserStore()
const medical = computed(() => userStore.medicalMode)
const selectedTab = ref(medical.value ? 'resetPwd' : 'userinfo')
const state = reactive<{ user: FrameworkProfile['data'] | null; roleGroup: string | null; postGroup: string | null }>({
  user: null, roleGroup: null, postGroup: null
})
const account = ref<OwnAccount | null>(null)
const passwordForm = ref<InstanceType<typeof resetPwd> | null>(null)
const loading = ref(false)
let generation = 0
let controller: AbortController | null = null
function clear(): void {
  generation += 1
  controller?.abort(); controller = null
  account.value = null; state.user = null; state.roleGroup = null; state.postGroup = null; loading.value = false
  passwordForm.value?.clearSecrets()
}
async function getUser(): Promise<void> {
  clear()
  const session = userStore.medicalSession
  if (medical.value && (!session || document.hidden)) return
  const current = generation
  const request = new AbortController(); controller = request; loading.value = true
  try {
    const own = medical.value ? await readOwnAccount(session, request.signal) : null
    const response = await getUserProfile(own, request.signal)
    if (current !== generation || medical.value && (userStore.medicalSession !== session || document.hidden)) return
    account.value = own; state.user = response.data; state.roleGroup = response.roleGroup; state.postGroup = response.postGroup
    if (medical.value) userStore.avatar = response.data.avatar ? '/api' + response.data.avatar : defAva
  } finally { if (current === generation) loading.value = false }
}
function clearCredential(): void { userStore.clearMedicalCredential(); passwordForm.value?.clearSecrets() }
function hidden(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', hidden)
window.addEventListener('pagehide', clear)
watch(() => userStore.medicalSession, () => { if (medical.value) void getUser() }, { immediate: true, flush: 'sync' })
onMounted(() => {
  const activeTab = route.params.activeTab
  if (typeof activeTab === 'string' && ['userinfo', 'resetPwd'].includes(activeTab)) selectedTab.value = activeTab
  if (!medical.value) void getUser()
})
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', hidden)
  window.removeEventListener('pagehide', clear)
  clear()
})
</script>

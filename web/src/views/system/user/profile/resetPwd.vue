<template>
  <section ref="passwordSection">
    <el-form ref="pwdRef" data-framework-reset-pwd :model="user" :rules="rules" label-width="80px"
      :label-position="medical ? 'top' : 'right'" :disabled="disabled || submitting || userStore.medicalWriting"
      @submit.prevent="submit">
      <el-form-item :label="medical ? '当前密码' : '旧密码'" prop="oldPassword">
        <el-input v-model="user.oldPassword" :aria-label="medical ? '当前密码' : '旧密码'" placeholder="请输入旧密码"
          type="password" autocomplete="current-password" show-password @input="inputChanged" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword" :rules="infoPwdValidator">
        <el-input v-model="user.newPassword" aria-label="新密码" placeholder="请输入新密码" type="password"
          autocomplete="new-password" show-password @input="inputChanged" />
      </el-form-item>
      <el-form-item :label="medical ? '确认新密码' : '确认密码'" prop="confirmPassword">
        <el-input v-model="user.confirmPassword" :aria-label="medical ? '确认新密码' : '确认密码'" placeholder="请确认新密码"
          type="password" autocomplete="new-password" show-password @input="inputChanged" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit" data-credential-submit :loading="submitting"
          :disabled="disabled || submitting || userStore.medicalWriting">{{ medical ? '确认变更本人密码' : '保存' }}</el-button>
        <el-button type="danger" :disabled="submitting || userStore.medicalWriting" @click="close">{{ medical ? '清除密码输入' : '关闭' }}</el-button>
      </el-form-item>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { usePasswordRule } from '@/utils/passwordRule'
import { updateUserPwd } from '@/api/system/user'
import type { OwnAccount } from '@/api/medical/account'
import useUserStore from '@/store/modules/user'
import modal from '@/plugins/modal'
import tab from '@/plugins/tab'

const props = defineProps<{ account?: OwnAccount | null; disabled?: boolean }>()
const userStore = useUserStore()
const medical = computed(() => userStore.medicalMode)
const { infoPwdValidator } = usePasswordRule()
const pwdRef = ref<FormInstance>()
const passwordSection = ref<HTMLElement | null>(null)
const submitting = ref(false)
const user = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const rules = computed<FormRules>(() => ({
  oldPassword: [{ required: true, message: '旧密码不能为空', trigger: 'blur' }, ...(medical.value ? infoPwdValidator.value.slice(1) : [])],
  confirmPassword: [
    { required: true, message: '确认密码不能为空', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (user.newPassword !== value) callback(new Error(medical.value ? '两次输入的新密码需要一致' : '两次输入的密码不一致'))
      else callback()
    }, trigger: 'blur' }
  ]
}))

function inputChanged(): void { pwdRef.value?.clearValidate() }
function clearSecrets(): void {
  user.oldPassword = ''; user.newPassword = ''; user.confirmPassword = ''
  // 页面隐藏及销毁时立即清除原生控件中的秘密。
  passwordSection.value?.querySelectorAll<HTMLInputElement>('input').forEach(input => { input.value = '' })
  pwdRef.value?.clearValidate()
}
async function submit(): Promise<void> {
  if (submitting.value || props.disabled || userStore.medicalWriting || document.hidden) return
  if (!pwdRef.value) throw new Error('原密码表单尚未初始化')
  submitting.value = true
  let attempted = false
  try {
    let formValid = false
    await pwdRef.value.validate(valid => { formValid = valid })
    if (!formValid) return
    attempted = true
    await updateUserPwd(user.oldPassword, user.newPassword, props.account)
    if (!medical.value) modal.msgSuccess('修改成功')
  } finally {
    submitting.value = false
    if (attempted) clearSecrets()
  }
}
function close(): void {
  clearSecrets()
  if (!medical.value) void tab.closePage()
}
function hidden(): void { if (document.hidden) clearSecrets() }
watch(() => props.account?.user_id, clearSecrets)
watch(() => userStore.medicalSession, clearSecrets)
document.addEventListener('visibilitychange', hidden)
window.addEventListener('pagehide', clearSecrets)
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', hidden)
  window.removeEventListener('pagehide', clearSecrets)
  clearSecrets()
})
defineExpose({ clearSecrets })
</script>

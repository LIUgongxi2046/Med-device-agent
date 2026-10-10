<template>
   <el-form ref="userRef" data-framework-user-info :model="form" :rules="rules" label-width="80px"
      :disabled="disabled || submitting || userStore.medicalWriting" @submit.prevent="submit">
      <el-form-item label="用户昵称" prop="nickName">
         <el-input v-model="form.nickName" aria-label="用户昵称" maxlength="128" />
      </el-form-item>
      <el-form-item label="手机号码" prop="phonenumber">
         <el-input v-model="form.phonenumber" aria-label="手机号码" maxlength="11" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
         <el-input v-model="form.email" aria-label="邮箱" maxlength="50" />
      </el-form-item>
      <el-form-item label="性别">
         <el-radio-group v-model="form.sex">
            <el-radio value="0">男</el-radio>
            <el-radio value="1">女</el-radio>
         </el-radio-group>
      </el-form-item>
      <el-form-item>
      <el-button type="primary" native-type="submit" :loading="submitting">保存</el-button>
      <el-button type="danger" :disabled="submitting || userStore.medicalWriting" @click="close">关闭</el-button>
      </el-form-item>
   </el-form>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { updateUserProfile } from "@/api/system/user"
import type { FrameworkProfile, OwnAccount, OwnProfileFields } from '@/api/medical/account'
import useUserStore from '@/store/modules/user'
import modal from '@/plugins/modal'
import tab from '@/plugins/tab'

const props = defineProps<{ user: FrameworkProfile['data']; account?: OwnAccount | null; disabled?: boolean }>()
const userStore = useUserStore()
const userRef = ref<FormInstance>()
const submitting = ref(false)
const form = ref<OwnProfileFields>({ nickName: '', email: null, phonenumber: null, sex: null })
const rules = computed<FormRules>(() => ({
  nickName: [{ required: true, message: "用户昵称不能为空", trigger: "blur" }],
  email: [{ required: !userStore.medicalMode, message: "邮箱地址不能为空", trigger: "blur" }, { type: "email", message: "请输入正确的邮箱地址", trigger: ["blur", "change"] }],
  phonenumber: [{ required: !userStore.medicalMode, message: "手机号码不能为空", trigger: "blur" }, { pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, message: "请输入正确的手机号码", trigger: "blur" }],
}))

/** 提交按钮 */
async function submit(): Promise<void> {
  if (submitting.value || props.disabled || userStore.medicalWriting || document.hidden) return
  if (!userRef.value) throw new Error('原个人资料表单尚未初始化')
  submitting.value = true
  try {
    let valid = false
    await userRef.value.validate(value => { valid = value })
    if (!valid) return
    const result = await updateUserProfile({ ...form.value }, props.account)
    if (!userStore.medicalMode) {
      props.user.nickName = form.value.nickName; props.user.phonenumber = form.value.phonenumber; props.user.email = form.value.email
    }
    if (!result) throw new Error('原个人资料保存结果缺少')
    modal.msgSuccess('修改成功')
  } finally { submitting.value = false }
}

/** 关闭按钮 */
function close() {
  void tab.closePage()
}

// 回显当前登录用户信息
watch(() => props.user, user => {
  if (user) {
    form.value = { nickName: user.nickName, phonenumber: user.phonenumber, email: user.email, sex: user.sex }
  }
},{ immediate: true })
onBeforeUnmount(() => { form.value = { nickName: '', email: null, phonenumber: null, sex: null } })
</script>

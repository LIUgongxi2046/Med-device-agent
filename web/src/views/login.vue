<template>
  <main class="login" :class="{ 'login--medical': medical }">
    <section v-if="medical" class="login-story" aria-label="平台介绍">
      <a class="login-brand" href="/access" :aria-label="brand.chineseName + '首页'">
        <img class="login-brand__icon" :src="brand.logo" :alt="brand.chineseName + ' Logo'" width="48" height="48">
        <span>{{ brand.chineseName }}<span class="login-brand__suffix">{{ brand.englishName }}</span></span>
      </a>
      <div class="login-story__content">
        <p class="login-eyebrow">{{ brand.englishName }}</p>
        <h1>每一份资料，<br>都有清晰的来路。</h1>
        <p class="login-story__intro">连接项目、知识与专业文稿，<br>让团队在同一个工作空间推进注册事务。</p>
        <div class="login-capabilities">
          <article><el-icon><FolderOpened /></el-icon><div><h2>项目与资料</h2><p>集中管理，保留每个固定版本。</p></div></article>
          <article><el-icon><DocumentChecked /></el-icon><div><h2>文稿与审查</h2><p>来源可追溯，审查过程有记录。</p></div></article>
          <article><el-icon><UserFilled /></el-icon><div><h2>团队与权限</h2><p>按企业、项目与岗位协同工作。</p></div></article>
        </div>
      </div>
      <div class="login-story__footer"><span>专注医疗器械注册工作</span><span>MEDICAL · AGENT</span></div>
    </section>

    <section class="login-workspace" aria-label="账户登录">
      <div class="login-workspace__top"><el-icon><CircleCheck /></el-icon><span>{{ medical ? '本地工作空间' : title }}</span></div>
      <div class="login-content">
        <header class="login-heading"><p class="login-eyebrow">欢迎使用</p><h2>{{ medical ? '登录工作台' : title }}</h2><p>使用您的账户，继续今天的工作。</p></header>
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" label-position="top" @submit.prevent="handleLogin">
      <el-alert v-if="medical && userStore.medicalError" :title="userStore.medicalError.message" type="error" :closable="false" role="alert" />
      <el-form-item prop="username" label="账号">
        <el-input
          v-model="loginForm.username"
          type="text"
          size="large"
          autocomplete="username"
          aria-label="用户名"
          :disabled="loading || userStore.medicalWriting"
          placeholder="请输入登录账号"
        >
          <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password" label="密码">
        <el-input
          v-model="loginForm.password"
          type="password"
          size="large"
          autocomplete="current-password"
          aria-label="密码"
          show-password
          :disabled="loading || userStore.medicalWriting"
          placeholder="请输入登录密码"
        >
          <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="code" v-if="captchaEnabled">
        <el-input
          v-model="loginForm.code"
          size="large"
          auto-complete="off"
          placeholder="验证码"
          style="width: 63%"
        >
          <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
        </el-input>
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img"/>
        </div>
      </el-form-item>
      <el-checkbox v-if="!medical" v-model="loginForm.rememberMe" style="margin:0px 0px 25px 0px;">记住密码</el-checkbox>
      <el-form-item style="width:100%;">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          native-type="submit"
          :disabled="userStore.medicalWriting"
          style="width:100%;"
        >
          <span v-if="!loading">{{ medical ? '登录工作台' : '登录' }}</span>
          <span v-else>正在登录</span>
          <el-icon v-if="!loading" class="login-submit-arrow"><ArrowRight /></el-icon>
        </el-button>
        <div style="float: right;" v-if="register">
          <router-link class="link-type" :to="'/register'">立即注册</router-link>
        </div>
      </el-form-item>
    </el-form>

      <aside v-if="medical && localAccountEnabled" class="local-account" aria-labelledby="local-account-title" :aria-busy="localAccountLoading">
        <div class="local-account__heading"><span class="local-account__icon"><el-icon><UserFilled /></el-icon></span><div><h3 id="local-account-title">本机超管账号</h3><p>平台管理员 · 本地使用</p></div></div>
        <p v-if="localAccountLoading" class="local-account__status" role="status">正在读取本机账号…</p>
        <template v-else-if="localAccount">
          <dl class="local-account__details">
            <div><dt>账号</dt><dd data-local-owner-username>{{ localAccount.username }}</dd></div>
            <div><dt>密码</dt><dd class="local-account__password"><span data-local-owner-password>{{ localPasswordVisible ? localAccount.password : '••••••••••••••••' }}</span><button type="button" class="password-visibility" :aria-label="localPasswordVisible ? '隐藏超管密码' : '显示超管密码'" :aria-pressed="localPasswordVisible" @click="localPasswordVisible = !localPasswordVisible"><el-icon><Hide v-if="localPasswordVisible" /><View v-else /></el-icon></button></dd></div>
          </dl>
          <button type="button" class="local-account__fill" :disabled="loading || userStore.medicalWriting" @click="fillLocalAccount">填入登录表单<el-icon><ArrowRight /></el-icon></button>
        </template>
        <div v-else-if="localAccountError" class="local-account__status" role="alert"><p>{{ localAccountError }}</p><button type="button" class="local-account__retry" @click="loadLocalAccount">重新读取</button></div>
      </aside>
      <p v-if="medical" class="login-note"><el-icon><Lock /></el-icon><span>登录后，按您的企业与项目权限访问工作空间。</span></p>
      </div>
      <footer class="el-login-footer">{{ medical ? brand.chineseName : footerContent }}</footer>
    </section>
  </main>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'
import { fail } from '@/api/medical/protocol'
import { ArrowRight, CircleCheck, DocumentChecked, FolderOpened, Hide, Lock, UserFilled, View } from '@element-plus/icons-vue'
import { brand } from '../../branding.mjs'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const medical = computed(() => route.meta.medical === true || userStore.medicalMode)
const localAccountEnabled = import.meta.env.DEV && import.meta.env.MODE === 'medical'
const localAccount = ref(null)
const localAccountLoading = ref(false)
const localAccountError = ref('')
const localPasswordVisible = ref(false)
let active = true

async function loadLocalAccount() {
  if (!localAccountEnabled || localAccountLoading.value) return
  localAccount.value = null
  localPasswordVisible.value = false
  localAccountLoading.value = true
  localAccountError.value = '本机超管账号读取失败，请重新读取。'
  try {
    const response = await fetch('/__local/owner-credentials', { cache: 'no-store', credentials: 'omit' })
    if (!response.ok) throw new Error('本机超管账号读取失败')
    const account = await response.json()
    if (!['username', 'password', 'display_name'].every(key => typeof account[key] === 'string' && account[key].length > 0)) {
      throw new Error('本机超管账号格式无效')
    }
    if (!active) return
    localAccount.value = account
    localAccountError.value = ''
  } finally {
    if (active) localAccountLoading.value = false
  }
}

function fillLocalAccount() {
  if (!localAccount.value || loading.value || userStore.medicalWriting) return
  loginForm.value.username = localAccount.value.username
  loginForm.value.password = localAccount.value.password
  proxy.$refs.loginRef.clearValidate()
}

const loginForm = ref({
  username: "",
  password: "",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(!medical.value)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

async function handleLogin() {
  if (loading.value || userStore.medicalWriting) return
  loading.value = true
  let attempted = false
  let authenticated = false
  try {
    if (!await proxy.$refs.loginRef.validate(valid => valid)) return
    let target
    if (medical.value) {
      target = redirect.value === undefined ? '/index' : redirect.value
      if (typeof target !== 'string' || !target.startsWith('/') || new URL(target, location.origin).origin !== location.origin
          || !router.resolve(target).meta.medical || router.resolve(target).meta.public) {
        fail('SCHEMA_INVALID', '登录目标需要使用当前业务页面')
      }
    } else {
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
    }
    attempted = true
    await userStore.login(loginForm.value)
    authenticated = true
    if (medical.value) {
      await router.replace(target)
    } else {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
      await router.push({ path: redirect.value || "/", query: otherQueryParams })
    }
  } finally {
    loading.value = false
    if (medical.value) loginForm.value.password = ''
    else if (attempted && !authenticated && captchaEnabled.value) await getCode()
  }
}

function getCode() {
  return getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

// 本地业务使用既有Cookie准入，密码输入不进入浏览器持久存储。
if (medical.value) {
  Cookies.remove('password')
  Cookies.remove('rememberMe')
} else {
  getCode()
  getCookie()
}
onMounted(async () => { if (medical.value && localAccountEnabled) await loadLocalAccount() })
onBeforeUnmount(() => {
  active = false
  loginForm.value.password = ''
  localAccount.value = null
  localPasswordVisible.value = false
})
</script>

<style lang='scss' scoped>
.login {
  --login-brand: #1768c4;
  --login-text: #243247;
  --login-muted: #65758b;
  --login-border: #dce3ec;
  --login-surface: #f5f8fc;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-height: 100vh;
  min-height: 100dvh;
  color: var(--login-text);
  background: #fff;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  -webkit-font-smoothing: antialiased;

  &--medical { grid-template-columns: minmax(0, 1.04fr) minmax(0, 1fr); }
  * { box-sizing: border-box; }
  button, a, :deep(.el-input__wrapper) { transition: background-color .18s, border-color .18s, box-shadow .18s; }
  button:focus-visible, a:focus-visible { outline: 3px solid #5da4f0; outline-offset: 4px; }
  button:disabled { opacity: .5; cursor: not-allowed; }
}
.login-story {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
  padding: 48px clamp(40px, 5.4vw, 88px) 32px;
  color: #fff;
  background: radial-gradient(ellipse at 0% 100%, #164873 0, transparent 65%), #132d4b;
}
.login-brand {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 14px;
  color: #fff;
  font-size: 19px;
  font-weight: 650;
  letter-spacing: .4px;
  text-decoration: none;
  &__icon { flex: 0 0 48px; width: 48px; height: 48px; object-fit: contain; border-radius: 13px; background: #fff; }
  &__suffix { display: block; margin-top: 4px; color: #b2c8df; font-size: 11px; font-weight: 400; letter-spacing: .2px; }
}
.login-story__content { padding: 76px 0 64px; }
.login-eyebrow { margin: 0 0 24px; font-size: 11px; font-weight: 600; letter-spacing: 2.2px; color: #8ab4de; }
.login-story h1 { margin: 0; font-size: clamp(34px, 3.1vw, 48px); line-height: 1.5; font-weight: 600; letter-spacing: -.7px; }
.login-story__intro { margin: 24px 0 40px; color: #b7cbe0; font-size: 15px; line-height: 1.95; }
.login-capabilities {
  display: grid;
  gap: 24px;
  article { display: flex; align-items: flex-start; gap: 16px; }
  article > .el-icon { display: grid; flex: 0 0 38px; place-items: center; height: 38px; border-radius: 10px; background: #ffffff0b; color: #a7d1f8; font-size: 19px; }
  h2 { margin: 1px 0 6px; font-size: 14px; font-weight: 550; color: #edf5ff; }
  p { margin: 0; font-size: 12px; line-height: 1.6; color: #a7bfd8; }
}
.login-story__footer { display: flex; justify-content: space-between; gap: 20px; padding-top: 24px; border-top: 1px solid #ffffff1a; color: #92abc6; font-size: 11px; line-height: 1.7; }
.login-story__footer span:last-child { font-size: 10px; letter-spacing: 1.3px; white-space: nowrap; }
.login-workspace { display: flex; flex-direction: column; min-width: 0; padding: 38px clamp(24px, 4.5vw, 72px) 24px; background: #fff; }
.login-workspace__top { display: flex; justify-content: flex-end; align-items: center; gap: 7px; color: var(--login-muted); font-size: 12px; }
.login-workspace__top .el-icon { color: #287044; font-size: 14px; }
.login-content { width: 100%; max-width: 400px; margin: auto; padding: 56px 0 40px; }
.login-heading {
  margin-bottom: 32px;
  .login-eyebrow { margin-bottom: 12px; color: var(--login-brand); font-size: 12px; letter-spacing: 1px; }
  h2 { margin: 0 0 12px; color: var(--login-text); font-size: 29px; line-height: 1.4; font-weight: 650; letter-spacing: -.5px; }
  > p:last-child { margin: 0; color: var(--login-muted); font-size: 13px; line-height: 1.7; }
}
.login-form {
  width: 100%;
  :deep(.el-form-item) { margin-bottom: 24px; }
  :deep(.el-form-item__label) { height: auto; margin-bottom: 9px; color: var(--login-text); font-size: 13px; line-height: 1.5; font-weight: 500; }
  :deep(.el-form-item__label::before) { display: none; }
  :deep(.el-input__wrapper) { min-height: 48px; padding: 2px 14px; border-radius: 8px; background: #fff; box-shadow: 0 0 0 1px var(--login-border) inset; }
  :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1.5px var(--login-brand) inset, 0 0 0 3px #1768c412; }
  :deep(.el-input__inner) { color: var(--login-text); font-size: 14px; }
  :deep(.el-input__inner::placeholder) { color: #98a4b4; }
  .input-icon { width: 16px; height: 20px; margin-right: 6px; color: #8b9aae; }
  :deep(.el-button--primary) { min-height: 48px; margin-top: 4px; border-color: var(--login-brand); border-radius: 8px; background: var(--login-brand); font-size: 14px; font-weight: 500; }
  :deep(.el-button--primary:hover) { background: #1259a9; }
  :deep(.el-alert) { margin-bottom: 24px; }
}
.login-submit-arrow { margin-left: 12px; }
.login-code { width: 33%; height: 40px; margin-left: auto; }
.login-code-img { height: 40px; max-width: 100%; cursor: pointer; }
.local-account {
  margin-top: 28px;
  padding: 20px;
  border: 1px solid #e4eaf2;
  border-radius: 12px;
  background: var(--login-surface);
  &__heading { display: flex; align-items: center; gap: 11px; }
  &__icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 9px; background: #e7eff9; color: var(--login-brand); }
  h3 { margin: 0 0 4px; color: var(--login-text); font-size: 13px; font-weight: 600; }
  &__heading p { margin: 0; color: var(--login-muted); font-size: 11px; }
  &__details { display: grid; gap: 12px; margin: 20px 0 16px; }
  &__details > div { display: grid; grid-template-columns: 32px minmax(0, 1fr); align-items: start; gap: 14px; }
  dt { color: var(--login-muted); font-size: 12px; line-height: 22px; }
  dd { min-width: 0; margin: 0; color: #34475f; font: 12px/22px "SFMono-Regular", Consolas, monospace; overflow-wrap: anywhere; }
  &__password { display: flex; align-items: flex-start; gap: 8px; }
  &__password > span { flex: 1; min-width: 0; }
  &__fill { display: flex; justify-content: center; align-items: center; gap: 10px; width: 100%; min-height: 38px; border: 1px solid #d5e0ee; border-radius: 7px; background: #fff; color: #175fae; font: inherit; font-size: 12px; cursor: pointer; }
  &__fill:hover { border-color: #a6c4e6; background: #fafdff; }
  &__status { margin: 20px 0 0; color: var(--login-muted); font-size: 12px; line-height: 1.8; }
  &__status p { margin: 0 0 8px; }
  &__retry { padding: 0; border: 0; background: transparent; color: var(--login-brand); font: inherit; cursor: pointer; }
}
.password-visibility { display: grid; flex: 0 0 28px; place-items: center; width: 28px; height: 28px; margin-top: -3px; border: 0; border-radius: 4px; background: transparent; color: #65758b; cursor: pointer; }
.password-visibility:hover { background: #e5edf7; color: var(--login-brand); }
.login-note { display: flex; align-items: flex-start; justify-content: center; gap: 6px; margin: 24px 0 0; color: #7a8799; font-size: 11px; line-height: 1.7; }
.login-note .el-icon { margin-top: 3px; flex-shrink: 0; }
.el-login-footer { margin-top: auto; color: #8b97a7; text-align: center; font-size: 11px; line-height: 1.8; }
@media (max-width: 980px) {
  .login--medical { grid-template-columns: minmax(0, .85fr) minmax(0, 1fr); }
  .login-story { padding: 36px 32px 24px; }
  .login-story__content { padding: 64px 0; }
  .login-story h1 { font-size: 32px; }
  .login-story__footer { flex-direction: column; gap: 8px; }
  .login-workspace { padding: 32px 28px 24px; }
}
@media (max-width: 700px) {
  .login--medical { grid-template-columns: minmax(0, 1fr); }
  .login-story { padding: 24px; }
  .login-brand { gap: 12px; font-size: 16px; }
  .login-brand__icon { width: 42px; height: 42px; }
  .login-story__content { padding: 28px 0 8px; }
  .login-story__content .login-eyebrow, .login-capabilities, .login-story__footer, .login-story__intro br { display: none; }
  .login-story h1 { font-size: 28px; line-height: 1.5; }
  .login-story__intro { margin: 14px 0 0; font-size: 12px; line-height: 1.8; }
  .login-workspace { padding: 24px; }
  .login-workspace__top { justify-content: flex-start; }
  .login-content { padding: 28px 0 32px; }
  .login-heading { margin-bottom: 28px; }
  .login-heading h2 { font-size: 26px; }
  .password-visibility { flex-basis: 36px; width: 36px; height: 36px; margin-top: -7px; }
  .local-account__fill { min-height: 44px; }
}
@media (prefers-reduced-motion: reduce) {
  .login *, .login :deep(*) { transition: none !important; animation: none !important; }
}
</style>

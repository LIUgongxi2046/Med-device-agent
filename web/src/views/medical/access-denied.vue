<template>
  <main class="access-denied medical-page">
    <p class="access-denied__eyebrow">医疗业务 · 访问范围</p>
    <h1>当前权限无法打开此页面</h1>
    <p class="access-denied__intro">登录状态、企业空间和项目授权会影响可访问的内容。此页面不会读取被拒绝的项目或文档。</p>
    <el-alert title="权限不足不代表已退出登录" description="可以重新读取会话，确认企业空间；如果仍无法访问，请联系企业管理员核对岗位和项目范围。重新读取不会自动授予权限。" type="warning" :closable="false" show-icon />
    <section aria-labelledby="session-title">
      <h2 id="session-title">核查账户与企业空间</h2>
      <p v-if="user.medicalSession">当前账户：{{ user.medicalSession.user.display_name || user.medicalSession.user.username || '已认证业务账户' }}</p>
      <p v-else>尚未取得可用会话。请重新读取，或前往账户入口登录。</p>
      <p v-if="user.medicalSession && !user.medicalSession.current_context">当前尚未选择企业空间。</p>
      <p v-else-if="user.medicalSession">已选择企业空间；项目授权仍需服务端核查。</p>
      <el-alert v-if="message" :title="message" type="error" :closable="false" role="alert" />
      <el-button :loading="working || user.medicalLoading" :disabled="busy" @click="refresh">重新读取会话</el-button>
      <el-form v-if="user.medicalSession" label-position="top" class="enterprise-form" @submit.prevent="select">
        <el-form-item label="切换至已获授权企业">
          <el-select v-model="target" placeholder="选择企业空间" :disabled="busy || !members.length" aria-label="切换企业空间">
            <el-option v-for="(member, index) in members" :key="member.tenant_id" :value="member.tenant_id"
              :label="'企业空间 ' + (index + 1) + (member.tenant_id === user.medicalSession.current_context?.tenant_id ? '（当前）' : '')" />
          </el-select>
        </el-form-item>
        <p v-if="!members.length">当前没有可选择的有效企业身份。</p>
        <details v-else class="identity-details"><summary>核对企业标识</summary><p>当前接口尚未提供企业名称，请与管理员确认以下标识。</p><p v-for="(member, index) in members" :key="member.tenant_id">企业空间 {{ index + 1 }}：{{ member.tenant_id }}</p></details>
        <el-button native-type="submit" :disabled="busy || !target || !!user.medicalPendingSelection">确认切换企业</el-button>
      </el-form>
      <p v-if="user.medicalPendingSelection">有企业选择请求待核查，请先重新读取会话。不要重复提交。</p>
    </section>
    <nav aria-label="访问受限后的可用入口">
      <router-link to="/index">返回工作台</router-link>
      <router-link to="/access">前往账户入口</router-link>
    </nav>
    <p class="access-denied__note">重新进入工作台时仍会核查实际会话与权限。原业务操作的待核查记录不会因本页操作而被清除。</p>
  </main>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'

const user = useUserStore()
const target = ref(''), message = ref(''), working = ref(false)
const busy = computed(() => working.value || user.medicalLoading || user.medicalWriting)
const members = computed(() => user.medicalOptions?.memberships.filter(member => member.status === 'ACTIVE') ?? [])
watch(() => user.medicalOptions, () => { target.value = '' })
async function refresh() {
  if (busy.value || document.hidden) return
  working.value = true; message.value = ''
  try { await user.readMedical() }
  catch { message.value = '未能取得可用会话，请稍后重试或前往账户入口。' }
  finally { working.value = false }
}
async function select() {
  if (busy.value || !target.value || document.hidden || user.medicalPendingSelection) return
  working.value = true; message.value = ''
  try { await user.selectMedical(target.value) }
  catch { message.value = '企业选择尚未确认，请重新读取会话核查。' }
  finally { working.value = false }
}
</script>

<style scoped>
.access-denied { max-width: 760px; margin: 40px auto; padding: 28px; }
.access-denied h1 { font-size: 28px; color: #263445; }
.access-denied__eyebrow { color: var(--el-color-primary); font-size: 13px; }
.access-denied__intro, .access-denied__note { color: #606266; line-height: 1.7; }
.access-denied section { margin: 24px 0; border: 1px solid #dce3ec; border-radius: 10px; padding: 20px; }
.access-denied h2 { font-size: 18px; margin-top: 0; }
.access-denied nav { display: flex; flex-wrap: wrap; gap: 24px; }
.enterprise-form { margin-top: 20px; }
.enterprise-form .el-select { width: 100%; }
.identity-details { margin: 12px 0; overflow-wrap: anywhere; color: #606266; }
.identity-details summary { cursor: pointer; }
@media (max-width: 600px) { .access-denied { margin: 12px auto; padding: 16px; } }
</style>

<template>
  <div class="organization-switcher" data-organization-switcher :aria-busy="user.medicalLoading || user.medicalWriting">
    <span class="organization-switcher__label"><svg-icon icon-class="peoples" />工作组织</span>
    <el-select v-model="target" aria-label="工作组织" filterable placeholder="请选择工作组织" :disabled="disabled || members.length === 0 || Boolean(user.medicalPendingSelection)" :loading="user.medicalLoading">
      <el-option v-for="member in members" :key="member.tenant_id" :value="member.tenant_id" :label="member.tenant_id">
        <span class="organization-switcher__option">{{ member.tenant_id }}</span>
        <el-tag v-if="member.tenant_id === current" size="small" type="success">当前</el-tag>
      </el-option>
    </el-select>
    <el-button v-if="target && target !== current || user.medicalPendingSelection" type="primary" :disabled="disabled || !target" @click="confirmation = true">{{ current ? '切换组织' : '确认选择组织' }}</el-button>
    <span v-else class="organization-switcher__state" data-current-organization :title="current"><span class="organization-switcher__indicator" />{{ current ? '已选择' : '未选择' }}<span class="organization-switcher__identifier">{{ current }}</span></span>
    <el-dialog v-model="confirmation" title="切换工作组织" width="min(92vw,460px)" append-to-body :close-on-click-modal="false">
      <p>切换后，全部业务菜单使用所选组织的数据与权限，当前页面会重新读取。</p>
      <p class="organization-switcher__notice">当前页面未保存的输入会清除，请确认已经完成保存。</p>
      <p class="organization-switcher__target">{{ target }}</p>
      <template #footer><el-button :disabled="user.medicalWriting" @click="cancel">保留当前组织</el-button><el-button type="primary" :loading="user.medicalWriting" :disabled="user.medicalLoading || user.medicalWriting" @click="select">确认切换</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
const user = useUserStore()
const target = ref('')
const current = ref('')
const confirmation = ref(false)
const disabled = computed(() => user.medicalLoading || user.medicalWriting || !user.medicalSession)
const members = computed(() => user.medicalOptions?.memberships.filter(member => member.status === 'ACTIVE') ?? [])
watch(() => user.medicalSession, session => {
  if (!session) return
  current.value = session.current_context?.tenant_id ?? ''
  target.value = user.medicalPendingSelection?.tenantId ?? current.value
}, { immediate: true })
function cancel() { target.value = current.value; confirmation.value = false }
async function select() {
  if (!target.value || disabled.value) throw new Error('组织切换需要当前有效会话与目标组织')
  await user.selectMedical(target.value)
  confirmation.value = false
}
</script>
<style scoped>
.organization-switcher { display: flex; align-items: center; gap: 10px; min-width: 200px; max-width: 430px; margin: 0 16px; flex: 1; }
.organization-switcher__label { display: inline-flex; align-items: center; gap: 6px; color: var(--el-text-color-secondary); white-space: nowrap; font-size: 12px; line-height: 20px; }
.organization-switcher .el-select { flex: 1; min-width: 130px; max-width: 270px; }
.organization-switcher :deep(.el-select__wrapper) { border-radius: 8px; min-height: 36px; background: var(--el-fill-color-light); box-shadow: none; border: 1px solid var(--el-border-color-lighter); }
.organization-switcher__state { display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; font-size: 12px; color: var(--el-text-color-secondary); }
.organization-switcher__indicator { width: 6px; height: 6px; border-radius: 50%; background: var(--el-color-success); }
.organization-switcher__identifier { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
.organization-switcher__option { margin-right: 12px; font-size: 12px; }
.organization-switcher__notice { color: var(--el-text-color-secondary); }
.organization-switcher__target { padding: 12px; border-radius: 8px; background: var(--el-fill-color-light); overflow-wrap: anywhere; }
@media (max-width: 991px) {
  .organization-switcher { position: absolute; top: 54px; left: 12px; right: 12px; max-width: none; margin: 0; }
  .organization-switcher .el-select { max-width: none; }
  .organization-switcher :deep(.el-select__wrapper), .organization-switcher .el-button { min-height: 44px; }
}
@media (max-width: 480px) { .organization-switcher__label .svg-icon, .organization-switcher__state { display: none; } }
</style>

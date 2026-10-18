<template>
  <section aria-label="已授权规则列表" data-rule-list>
    <h2>已授权规则</h2>
    <p v-if="!permitted">当前没有规则列表读取权限；已有审查权限的规则可从模板引用或固定版本入口进入。</p>
    <template v-else>
      <el-form label-position="top" @submit.prevent="search">
        <el-form-item label="按固定版本或引擎筛选" :error="queryError"><el-input v-model="queryInput" maxlength="256" :disabled="loading || user.medicalWriting" clearable /></el-form-item>
        <el-button native-type="submit" :disabled="loading || user.medicalWriting">读取规则列表</el-button>
      </el-form>
      <p>只显示当前企业和本人有权读取、依据仍可使用的规则。筛选按固定版本或引擎标识匹配。</p>
      <p v-if="loading" role="status">正在读取实际规则列表</p>
      <p v-if="loaded && !items.length && !loading && nextAfter === null">当前筛选下没有可读取的规则</p>
      <p v-if="loaded && !items.length && !loading && nextAfter !== null">本页没有返回可读取规则，可以继续读取后续结果。</p>
      <el-table v-if="items.length" :data="items" row-key="rule.rule_version" border>
        <el-table-column label="固定规则版本" min-width="280"><template #default="scope">
          <router-link :to="{ path: '/rules/' + scope.row.rule.rule_version, query: { content_sha256: scope.row.rule.content_sha256 } }">{{ scope.row.rule.rule_version }}</router-link>
        </template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope">{{ ruleStatusLabels[scope.row.rule.status as RuleStatus] }}</template></el-table-column>
        <el-table-column prop="rule.reason" label="创建理由" min-width="220" />
        <el-table-column prop="rule.content.engine_revision" label="固定引擎" min-width="250" />
        <el-table-column prop="rule.created_at" label="创建时间" min-width="190" />
      </el-table>
      <el-button v-if="nextAfter" :disabled="loading || user.medicalWriting || queryInput !== appliedQuery" @click="load(true)">继续读取规则</el-button>
      <p v-if="queryInput !== appliedQuery && loaded">筛选条件已修改，请重新读取列表。</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { readRuleList, ruleAllowed, ruleStatusLabels } from '@/api/medical/rule-versions'
import type { RuleListQuery, RuleRead, RuleStatus } from '@/api/medical/rule-versions'
const user = useUserStore(); const permitted = computed(() => ruleAllowed(user.medicalSession, 'rule.read'))
const items = shallowRef<RuleRead[]>([]); const nextAfter = ref<string | null>(null); const loading = ref(false); const loaded = ref(false)
const queryInput = ref(''); const appliedQuery = ref(''); const queryError = ref('')
let generation = 0; let controller: AbortController | null = null
function clear(): void { generation += 1; controller?.abort(); controller = null; items.value = []; nextAfter.value = null; loading.value = false; loaded.value = false }
async function load(more: boolean): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || !permitted.value || loading.value || user.medicalWriting || document.hidden || more && !nextAfter.value) return
  const query: RuleListQuery = { limit: 25, ...(appliedQuery.value ? { q: appliedQuery.value } : {}), ...(more && nextAfter.value ? { after: nextAfter.value } : {}) }
  const current = generation; controller?.abort(); controller = new AbortController(); loading.value = true
  try {
    const result = await readRuleList(session, query, controller.signal)
    if (current !== generation || user.medicalSession !== session || document.hidden) return
    const previous = more ? items.value : []; const last = previous.at(-1)?.rule.rule_version
    if (last && result.items.some(item => item.rule.rule_version <= last)) fail('CONTENT_MISMATCH', '规则分页与原列表不连续，需要重新读取')
    items.value = [...previous, ...result.items]; nextAfter.value = result.next_after; loaded.value = true
  } finally { if (current === generation) loading.value = false }
}
async function search(): Promise<void> {
  if (loading.value || user.medicalWriting) return
  queryError.value = queryInput.value && schemaIssues('urn:medical:configuration-discovery:1.0.0#/$defs/Query', { limit: 25, q: queryInput.value }).length ? '请填写最多256字符的版本或引擎筛选' : ''
  if (queryError.value) return
  clear(); appliedQuery.value = queryInput.value; await load(false)
}
watch(() => user.medicalSession, async () => { clear(); queryInput.value = ''; appliedQuery.value = ''; queryError.value = ''; await load(false) }, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>
<style scoped>section { margin: 24px 0; }</style>

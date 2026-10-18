<template>
 <section class="pending" aria-label="原执行策略请求恢复">
  <h2>原{{ pending.kind === 'EXECUTION_POLICY_AUTHOR' ? '登记' : '决定' }}请求待核查</h2><p>完整原正文与请求键已保留。只读取原操作、固定记录与类型回执；不会重发、生成新键或从目录猜测结果。</p><p>若写入后提示上下文已变化，请先在上方会话面板刷新当前身份，再核查原请求。</p><p class="identity">请求键：{{ pending.key }} · 原上下文：{{ pending.context.context_revision }}</p><p>原因：{{ pending.body.reason }}</p>
  <template v-if="'policy_version' in pending.body"><p>原决定：{{ pending.body.decision }} · 预期修订 {{ pending.body.expected_revision }}</p><p class="identity">固定版本：{{ pending.body.policy_version.version_id }}</p></template>
  <el-button type="primary" :disabled="disabled" @click="$emit('recover')">核查原操作与类型回执</el-button>
  <p v-if="lookup" role="status">{{ message }}</p>
  <details v-if="'definition' in pending.body"><summary>查看完整原声明</summary><execution-policy-definition :definition="pending.body.definition" /></details>
 </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { PendingPolicy } from '@/api/medical/execution-policy-domain'
import type { OperationLookup } from '@/api/medical/operations'
import ExecutionPolicyDefinition from './execution-policy-definition.vue'
const props=defineProps<{pending:PendingPolicy;lookup:OperationLookup|null;disabled:boolean}>();defineEmits<{recover:[]}>()
const message=computed(()=>props.lookup?.lookup_status==='NOT_FOUND'?'尚未查到原操作，不能据此认定未执行。原请求继续保留。':props.lookup?.lookup_status==='EXPIRED'?'原键查询已过期，请保留请求并联系管理员核查。':props.lookup?.operation?.status==='PROCESSING'?'原操作仍在处理，请稍后重新核查。':props.lookup?.operation?.status==='FAILED'?'原操作显示失败，请保留原正文与请求键核查。':'原操作已完成，正在核验原记录与类型回执。')
</script>
<style scoped>.pending{padding:22px;border:1px solid #e6c77f;border-radius:8px;background:#fff9ed;margin:20px 0}.pending h2{font-size:17px}.pending p{line-height:1.6;color:#526279;overflow-wrap:anywhere}.identity{font-size:13px}summary{cursor:pointer;margin:16px 0;color:#175fae}</style>

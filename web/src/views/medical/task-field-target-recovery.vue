<template>
  <section class="field-target-card" :aria-label="targetRecovery ? '原对象意图恢复' : '原请求只读核查'" data-field-target-recovery>
    <h2>原请求结果核查</h2>
    <p class="field-target-warning">{{ message }}</p>
    <p v-if="targetRecovery">仅按原键核验既有对象意图与原回执，必要时完成原登记；不会重新上传对象、生成替代身份或自动批准。离开页面不取消服务端操作。</p>
    <p v-else>查询只使用下方原键，不会重发原写入、生成替代键或自动批准。离开页面不取消服务端操作。</p>
    <details class="field-target-identifiers"><summary>原操作身份</summary>
      <dl><dt>操作</dt><dd>{{ operation }}</dd><dt>原键</dt><dd>{{ originalKey }}</dd><dt>完整请求摘要</dt><dd>{{ requestHash }}</dd></dl>
    </details>
    <button type="button" :disabled="busy" @click="$emit('recover')">{{ targetRecovery ? '按原键核查并恢复原登记' : '只读查询原请求' }}</button>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ originalKey: string; requestHash: string; operation: string; busy: boolean; targetRecovery?: boolean; state?: string | null }>()
defineEmits<{ recover: [] }>()
const message = computed(() => {
  switch (props.state) {
    case 'NOT_FOUND': return '尚未查到原记录。这不证明请求未执行，原请求继续保留。'
    case 'TRANSFER_UNCERTAIN': return '原对象传输结果尚不确定，等待核验原对象；尚无登记成功回执。'
    case 'INTENT_PERSISTED': return '服务端已保存原对象意图，尚未核实目标登记。'
    case 'WAITING_INPUT': return '原操作正在等待实际输入或授权。请核查具体缺项，不要改用替代请求。'
    case 'PROCESSING': return '原操作仍在处理，尚无完整成功回执。'
    default: return '请求结果尚未核实。保留完整原请求和原键，先查询真实回执。'
  }
})
</script>

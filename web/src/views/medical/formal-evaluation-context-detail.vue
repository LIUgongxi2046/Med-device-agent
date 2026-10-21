<template>
 <section class="binding-detail" aria-label="上下文绑定固定详情">
  <h2>固定绑定与当前审核状态</h2>
  <dl class="summary"><dt>当前状态</dt><dd>{{ statusLabel(view.status) }} · 第 {{ view.revision }} 次修订</dd><dt>作者</dt><dd>{{ view.fixed.created_by }}</dd><dt>创建时间（UTC）</dt><dd>{{ view.fixed.created_at }}</dd><dt>记录分类</dt><dd>{{ view.fixed.classification }}</dd><dt>登记理由</dt><dd>{{ view.author_reason }}</dd></dl>
  <ol class="nodes"><li v-for="node in view.fixed.definition.node_bindings" :key="node.node_kind"><h3>{{ node.node_kind }} · 模型节点</h3><p>角色、提示词和上下文工具均绑定固定版本。输入与输出契约随工具固定，不可在审核时修改。</p><dl><dt>角色</dt><dd>{{ node.role.version_id }}</dd><dt>提示词</dt><dd>{{ node.prompt.version_id }}</dd><dt>上下文工具</dt><dd>{{ node.context_assembler.version_id }}</dd><dt>输入契约</dt><dd>{{ node.input_schema.schema_ref }}</dd><dt>输出契约</dt><dd>{{ node.output_schema.schema_ref }}</dd></dl><details><summary>核查节点内容摘要</summary><dl><dt>角色摘要</dt><dd>{{ node.role.content_sha256 }}</dd><dt>提示词摘要</dt><dd>{{ node.prompt.content_sha256 }}</dd><dt>工具摘要</dt><dd>{{ node.context_assembler.content_sha256 }}</dd><dt>输入契约摘要</dt><dd>{{ node.input_schema.schema_sha256 }}</dd><dt>输出契约摘要</dt><dd>{{ node.output_schema.schema_sha256 }}</dd></dl></details></li></ol>
  <h3>独立审核记录</h3><p v-if="!view.decisions.length">尚未登记审核决定。</p><ol v-else><li v-for="decision in view.decisions" :key="decision.record_id"><strong>{{ decisionLabel(decision.decision) }}</strong> · {{ decision.decided_at }}<p>{{ decision.reason }}</p><dl><dt>审核人</dt><dd>{{ decision.actor_id }}</dd><dt>状态变化</dt><dd>{{ statusLabel(decision.previous_status) }} → {{ statusLabel(decision.status) }}</dd><dt>修订变化</dt><dd>{{ decision.previous_revision }} → {{ decision.revision }}</dd></dl></li></ol>
  <details><summary>核查项目、候选与固定版本标识</summary><dl><dt>项目</dt><dd>{{ view.fixed.definition.project_id }}</dd><dt>绑定版本</dt><dd>{{ view.binding.version_id }}</dd><dt>绑定摘要</dt><dd>{{ view.binding.content_sha256 }}</dd><dt>候选版本</dt><dd>{{ view.fixed.definition.candidate.version_id }}</dd><dt>候选摘要</dt><dd>{{ view.fixed.definition.candidate.content_sha256 }}</dd><dt>策略版本</dt><dd>{{ view.fixed.definition.execution_policy.version_id }}</dd><dt>策略摘要</dt><dd>{{ view.fixed.definition.execution_policy.content_sha256 }}</dd><dt>企业</dt><dd>{{ view.fixed.tenant_id }}</dd></dl></details>
 </section>
</template>
<script setup lang="ts">
import type { ContextView, ContextStatus, ContextDecisionKind } from '@/api/medical/formal-evaluation-context'
defineProps<{view:ContextView}>()
const statusLabel = (s: ContextStatus) => ({DRAFT:'待审核',APPROVED:'已批准',REJECTED:'已拒绝',DISABLED:'已停用'})[s]
const decisionLabel = (s: ContextDecisionKind) => ({APPROVE:'批准',REJECT:'拒绝',DISABLE:'停用'})[s]
</script>
<style scoped>
.binding-detail{display:grid;gap:1rem}h2,h3,p{margin:.2rem 0}.summary{display:grid;grid-template-columns:auto 1fr;gap:.5rem 1rem}.nodes{list-style:none;padding:0;display:grid;gap:1rem}.nodes>li{padding:1rem;border:1px solid var(--el-border-color);border-radius:8px}dl{margin:.5rem 0}dt{font-weight:600;margin-top:.5rem}dd{margin:.25rem 0;overflow-wrap:anywhere}details{margin-top:.6rem}summary{cursor:pointer;color:var(--el-color-primary)}li{margin:.8rem 0}p{color:var(--el-text-color-secondary);line-height:1.6}@media(max-width:640px){.summary{display:block}}
</style>

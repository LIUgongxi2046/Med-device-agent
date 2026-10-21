<template>
  <section aria-label="固定单一制品输入能力范围">
    <p>仅绑定下列既有 STRICT_FIELD_TARGET 和真实生产回执，消费节点限定为 N03；不是未来制品通配权限，也不会修改专业角色状态或启动消费者。</p>
    <dl><dt>任务版本</dt><dd>{{ definition.task_revision }}</dd><dt>既有实际制品</dt><dd>{{ definition.artifact_id }}</dd><dt>对象版本</dt><dd>{{ definition.object_version }}</dd><dt>内容摘要</dt><dd>{{ definition.artifact_sha256 }}</dd><dt>生产登记回执</dt><dd>{{ definition.registration_id }}</dd><dt>消费边界</dt><dd>{{ definition.consumer_node_kind }} · {{ definition.occurrence_ids.length }} 个明确发生位置 · 最多 {{ definition.maximum_read_bytes }} 字节</dd></dl>
    <details class="field-target-identifiers"><summary>完整固定依据与读取范围</summary><dl>
      <dt>项目／任务</dt><dd>{{ definition.project_id }} / {{ definition.task_id }}</dd><dt>任务输入摘要</dt><dd>{{ definition.task_input_sha256 }}</dd>
      <dt>确认计划</dt><dd>{{ definition.plan_revision }} / {{ definition.plan_sha256 }}</dd><dt>模板</dt><dd>{{ definition.template_version }} / {{ definition.template_sha256 }}</dd>
      <dt>提议映射</dt><dd>{{ definition.assignment_id }} / {{ definition.assignment_sha256 }}</dd><dt>生产回执摘要</dt><dd>{{ definition.producing_receipt_sha256 }}</dd>
      <dt>制品契约摘要</dt><dd>{{ definition.artifact_schema_sha256 }}</dd><dt>已发布版本</dt><dd>{{ definition.release_id }}</dd><dt>发布核准摘要</dt><dd>{{ definition.publication_approval_sha256 }}</dd>
      <dt>固定角色</dt><dd>{{ definition.role_version.version_id }} / {{ definition.role_version.content_sha256 }}</dd><dt>固定执行策略</dt><dd>{{ definition.execution_policy.version_id }} / {{ definition.execution_policy.content_sha256 }}</dd>
      <dt>对象读取摘要／大小</dt><dd>{{ definition.object_ref.sha256 }} / {{ definition.object_ref.size_bytes }}</dd><dt>实际发生位置</dt><dd>{{ definition.occurrence_ids.join('、') }}</dd><dt>分类</dt><dd>{{ definition.classification }}</dd>
    </dl></details>
  </section>
</template>
<script setup lang="ts">
import type { InputAuthor, InputManifest } from '@/api/medical/task-field-targets'
defineProps<{ definition: InputAuthor | InputManifest }>()
</script>

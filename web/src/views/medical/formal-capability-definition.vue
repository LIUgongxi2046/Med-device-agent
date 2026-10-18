<template>
  <section class="definition" aria-label="固定能力声明">
    <dl>
      <dt>能力类型</dt><dd>{{ body.kind === 'ROLE' ? '专业角色' : '提示词' }}</dd>
      <dt>声明模式</dt><dd>PRODUCTION · 仅登记 DRAFT，未授予执行资格</dd>
      <dt>归属范围</dt><dd>{{ body.project_id || '当前企业' }}</dd>
      <dt>来源成果</dt><dd>{{ body.source_artifact.version_id }}<small>SHA-256：{{ body.source_artifact.content_sha256 }}</small></dd>
      <dt>输入契约</dt><dd>{{ body.input_schema_ref.schema_ref }}<small>{{ body.input_schema_ref.schema_sha256 }}</small></dd>
      <dt>输出契约</dt><dd>{{ body.output_schema_ref.schema_ref }}<small>{{ body.output_schema_ref.schema_sha256 }}</small></dd>
      <dt>登记原因</dt><dd class="reason">{{ body.reason }}</dd>
    </dl>
    <template v-if="!Array.isArray(body.tool_scope)">
      <h3>固定角色权限声明（{{ body.tool_scope.permissions.length }} 项）</h3>
      <ul class="permissions"><li v-for="(permission,index) in body.tool_scope.permissions" :key="index">
        <strong>{{ permission.action }}</strong><span>用途：{{ permission.purpose }}</span>
        <span>{{ permission.scope.resource_kind }} · {{ permission.scope.resource_id }}</span>
        <small>企业 {{ permission.scope.tenant_id }} · 项目 {{ permission.scope.project_id || '无' }}</small>
      </li></ul>
    </template>
    <p v-else>提示词的工具范围为空。</p>
  </section>
</template>
<script setup lang="ts">
import type { FormalCapability } from '@/api/medical/formal-capability-domain'
defineProps<{ body: FormalCapability }>()
</script>
<style scoped>
dl{display:grid;grid-template-columns:100px minmax(0,1fr);gap:12px;margin:0}dt{color:#5c6d82}dd{margin:0;overflow-wrap:anywhere}small,.permissions span{display:block;color:#526279;margin-top:5px;overflow-wrap:anywhere}.reason{white-space:pre-wrap}.permissions{list-style:none;padding:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.permissions li{border:1px solid #dce3ec;border-radius:6px;padding:14px;background:#f8fafc}h3{font-size:15px;margin-top:24px}@media(max-width:650px){dl,.permissions{grid-template-columns:1fr}dt{margin-top:8px}}
</style>

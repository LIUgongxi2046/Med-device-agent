<template>
 <section class="definition" aria-label="完整执行策略声明">
  <dl><dt>归属项目</dt><dd>{{ definition.project_id || '当前企业' }}</dd><dt>业务用途</dt><dd>{{ definition.business_purpose }}</dd><dt>项目证据用途</dt><dd>{{ definition.profile_evidence_purpose }}</dd><dt>允许分类</dt><dd>{{ definition.allowed_classifications.join('、') }}</dd><dt>Worker 身份</dt><dd>{{ definition.worker.service_uri }}</dd><dt>Worker audience</dt><dd>{{ definition.worker.audience }}</dd><dt>部署环境 / 区域</dt><dd>{{ definition.environment }} / {{ definition.region }}</dd><dt>语言 / 时区</dt><dd>{{ definition.locale }} / {{ definition.timezone }}</dd><dt>有效期（UTC）</dt><dd>{{ definition.valid_from }} 至 {{ definition.valid_until || '未设结束时间' }}</dd><dt>训练 / 外部请求</dt><dd>均禁止</dd></dl>
  <h3>固定来源（{{ definition.source_selection.sources.length }}）</h3><p v-if="componentVersion(definition.source_selection)">来源范围组件：{{ componentVersion(definition.source_selection)?.version_id }}<small>组件 SHA-256：{{ componentVersion(definition.source_selection)?.content_sha256 }}</small></p><p v-if="!definition.source_selection.sources.length">本策略没有选定来源。</p>
  <ul><li v-for="s in definition.source_selection.sources" :key="s.version_id"><strong>{{ s.version_id }}</strong><small>SHA-256：{{ s.content_sha256 }}</small></li></ul>
  <h3>检索配置（{{ definition.retrieval_profiles.length }}）</h3><p v-if="!definition.retrieval_profiles.length">未声明检索能力；本策略不产生搜索授权。</p>
  <article v-for="r in definition.retrieval_profiles" :key="r.index_configuration.version_id"><p>全文检索 · 最多 {{ r.maximum_results }} 条</p><p v-if="componentVersion(r)">检索组件：{{ componentVersion(r)?.version_id }}<small>组件 SHA-256：{{ componentVersion(r)?.content_sha256 }}</small></p><p>固定索引配置：{{ r.index_configuration.version_id }}</p><small>SHA-256：{{ r.index_configuration.content_sha256 }}</small><p>允许字段：{{ r.permitted_fields.join('、') }}</p></article>
  <h3>网络限制</h3><p>外部请求禁止，HTTPS 目标列表为空。</p><p v-if="componentVersion(definition.network_policy)">网络组件：{{ componentVersion(definition.network_policy)?.version_id }}<small>组件 SHA-256：{{ componentVersion(definition.network_policy)?.content_sha256 }}</small></p><h3>节点绑定（{{ definition.bindings.length }}）</h3><article v-for="b in definition.bindings" :key="b.node_kind"><h4>{{ b.node_kind }} · {{ b.model_version ? '模型节点' : '非模型节点' }}</h4><p>角色固定版本：{{ b.role_version.version_id }}</p><small>角色 SHA-256：{{ b.role_version.content_sha256 }}</small>
   <template v-if="b.model_version"><p>模型固定版本：{{ b.model_version.version_id }}</p><small>模型 SHA-256：{{ b.model_version.content_sha256 }}</small><p>Tokenizer：{{ b.tokenizer_version?.version_id }}</p><small>Tokenizer SHA-256：{{ b.tokenizer_version?.content_sha256 }}</small><p>制品类型：{{ b.tokenizer_artifact?.artifact.media_type }} · {{ b.tokenizer_artifact?.artifact.size_bytes }} 字节</p><p>制品契约：{{ b.tokenizer_artifact?.schema.schema_ref }}</p><small>契约 SHA-256：{{ b.tokenizer_artifact?.schema.schema_sha256 }}</small><h4>生成参数</h4><policy-value :value="b.generation_parameters" /></template>
   <h4>声明的运行时限制</h4><ul><li v-for="(a,index) in b.actions" :key="index"><strong>{{ a.action }}</strong> · {{ a.resource_kind }} · {{ a.target_selector }}<p>用途：{{ a.purpose }}</p><template v-if="a.fixed_version"><p>固定目标：{{ a.fixed_version.version_id }}</p><small>SHA-256：{{ a.fixed_version.content_sha256 }}</small></template><p v-if="a.artifact_kinds.length">制品种类：{{ a.artifact_kinds.join('、') }}</p></li></ul>
  </article>
 </section>
</template>
<script setup lang="ts">
import type { Basis } from '@/api/medical/protocol'
import type { PolicyDefinitionInput } from '@/api/medical/execution-policy-domain'
import PolicyValue from './execution-policy-value.vue'
defineProps<{definition:PolicyDefinitionInput}>()
function componentVersion(value:object):Basis|null{return 'version' in value ? (value as {version:Basis}).version : null}
</script>
<style scoped>
.definition{min-width:0}dl{display:grid;grid-template-columns:145px minmax(0,1fr);gap:12px}dt,small{color:#596b82}dd{margin:0}dd,li,p,small{overflow-wrap:anywhere}h3{font-size:16px;margin:24px 0 12px}h4{font-size:14px}ul{list-style:none;padding:0}li,article{border:1px solid #dce3ec;border-radius:6px;padding:14px;margin:10px 0;background:#f8fafc}small{display:block;font-size:12px;margin-top:8px}p{line-height:1.6}@media(max-width:650px){dl{grid-template-columns:1fr;gap:8px}dd{margin-bottom:10px}}
</style>

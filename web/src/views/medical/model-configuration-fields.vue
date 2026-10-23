<template>
  <el-form ref="element" class="model-configuration-fields" label-position="top" autocomplete="off" :disabled="disabled" @submit.prevent>
    <section class="model-field-section" data-model-field-section>
    <header><span class="model-field-section__number">01</span><div><h3>供应商与模型</h3><p>定义模型身份、服务地址与部署区域。</p></div></header>
    <div class="model-field-grid">
      <el-form-item v-for="field in identityFields" :key="field.key" :label="field.label" :for="'model-' + field.key" :error="error('/definition/' + field.key)">
        <el-input :id="'model-' + field.key" v-model="form[field.key]" :maxlength="field.maximum" :placeholder="field.placeholder" autocomplete="off" />
      </el-form-item>
    </div>
    </section>
    <section class="model-field-section" data-model-field-section>
    <header><span class="model-field-section__number">02</span><div><h3>访问凭据</h3><p>关联秘密服务中已经登记的凭据。</p></div></header>
    <el-alert type="info" :closable="false" title="仅填写已登记的引用标识，不要粘贴API密钥、密码或访问令牌。此操作不写入或验证实际凭据。" />
    <div class="model-field-grid">
      <el-form-item label="凭据引用标识（UUID）" for="model-credential_reference_id" :error="error('/definition/credential_reference_id')">
        <el-input id="model-credential_reference_id" v-model="form.credential_reference_id" autocomplete="off" />
      </el-form-item>
      <el-form-item label="秘密服务引用路径" for="model-auth_ref" :error="error('/definition/auth_ref')">
        <el-input id="model-auth_ref" v-model="form.auth_ref" autocomplete="off" placeholder="仅引用路径，例如 vault:team/model" />
      </el-form-item>
      <el-form-item label="已有凭据的固定修订" for="model-credential_revision" :error="error('/definition/credential_revision')">
        <el-input id="model-credential_revision" v-model="form.credential_revision" autocomplete="off" />
      </el-form-item>
    </div>
    <p>公开回读不返回引用路径和凭据修订。修改草稿时需要重新填写这两项；它们不会保存到浏览器存储。</p>
    </section>
    <section class="model-field-section" data-model-field-section>
    <header><span class="model-field-section__number">03</span><div><h3>能力与调用限额</h3><p>设置上下文容量、吞吐限制与允许的请求参数。</p></div></header>
    <div class="model-field-grid">
      <el-form-item v-for="field in countFields" :key="field.key" :label="field.label" :for="'model-' + field.key" :error="error('/definition/limits/' + field.key)">
        <el-input :id="'model-' + field.key" v-model="form.limits[field.key]" inputmode="numeric" placeholder="填写正整数" />
      </el-form-item>
      <el-form-item label="请求时限（秒，1–300）" for="model-deadline_seconds" :error="error('/definition/limits/deadline_seconds')">
        <el-input-number id="model-deadline_seconds" v-model="form.limits.deadline_seconds" :min="1" :max="300" :precision="0" />
      </el-form-item>
      <el-form-item label="停止词条目上限（0–16）" for="model-stop_limit" :error="error('/definition/limits/stop_limit')">
        <el-input-number id="model-stop_limit" v-model="form.limits.stop_limit" :min="0" :max="16" :precision="0" />
      </el-form-item>
      <el-form-item label="允许的推理档位" :error="error('/definition/limits/reasoning_efforts')">
        <el-checkbox-group v-model="form.limits.reasoning_efforts" aria-label="允许的推理档位"><el-checkbox v-for="value in ['none', 'low', 'high', 'max']" :key="value" :value="value">{{ value }}</el-checkbox></el-checkbox-group>
      </el-form-item>
      <el-form-item label="temperature参数声明" :error="error('/definition/limits/temperature_enabled')">
        <el-checkbox v-model="form.limits.temperature_enabled">允许temperature（须包含none档位）</el-checkbox>
      </el-form-item>
    </div>
    </section>
    <section class="model-field-section" data-model-field-section>
    <header><span class="model-field-section__number">04</span><div><h3>价格与数据政策</h3><p>关联实际依据，明确数据使用、区域及保留期限。</p></div></header>
    <p>依据必须是同一企业可用的实际登记版本及其原始SHA-256摘要。登记引用核验不代表价格批准或数据外发授权。</p>
    <div class="model-field-grid">
      <el-form-item label="价格依据版本（UUID）" for="model-price-version" :error="error('/definition/price_basis/version_id')"><el-input id="model-price-version" v-model="form.price_basis.version_id" /></el-form-item>
      <el-form-item label="价格依据SHA-256" for="model-price-hash" :error="error('/definition/price_basis/content_sha256')"><el-input id="model-price-hash" v-model="form.price_basis.content_sha256" maxlength="64" /></el-form-item>
      <el-form-item label="币种（三位大写代码）" for="model-currency" :error="error('/definition/currency')"><el-input id="model-currency" v-model="form.currency" maxlength="3" placeholder="例如 CNY" /></el-form-item>
      <el-form-item label="政策依据版本（UUID）" for="model-policy-version" :error="error('/definition/data_policy/policy_basis/version_id')"><el-input id="model-policy-version" v-model="form.data_policy.policy_basis.version_id" /></el-form-item>
      <el-form-item label="政策依据SHA-256" for="model-policy-hash" :error="error('/definition/data_policy/policy_basis/content_sha256')"><el-input id="model-policy-hash" v-model="form.data_policy.policy_basis.content_sha256" maxlength="64" /></el-form-item>
      <el-form-item label="允许的数据分级（每行一项）" for="model-classifications" :error="error('/definition/data_policy/allowed_classifications')"><el-input id="model-classifications" v-model="classifications" type="textarea" :rows="3" placeholder="按实际政策填写" /></el-form-item>
      <el-form-item label="允许区域（每行一项，包含模型区域）" for="model-regions" :error="error('/definition/data_policy/allowed_regions')"><el-input id="model-regions" v-model="regions" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="保留天数（十进制整数字符串）" for="model-retention" :error="error('/definition/data_policy/retention_days')"><el-input id="model-retention" v-model="form.data_policy.retention_days" inputmode="numeric" /></el-form-item>
      <el-form-item label="训练使用政策"><el-checkbox v-model="form.data_policy.allow_training">政策允许用于训练</el-checkbox></el-form-item>
    </div>
    </section>
    <section class="model-field-section" data-model-field-section>
    <header><span class="model-field-section__number">05</span><div><h3>请求参数协议</h3><p>固定参数定义与模型协议版本，保持调用依据可追溯。</p></div></header>
    <div class="model-field-grid">
      <el-form-item label="参数Schema固定引用（URN）" for="model-parameter-ref" :error="error('/definition/request_parameter_schema_ref')"><el-input id="model-parameter-ref" v-model="form.request_parameter_schema_ref" placeholder="urn:..." /></el-form-item>
      <el-form-item label="参数Schema SHA-256" for="model-parameter-hash" :error="error('/definition/request_parameter_schema_sha256')"><el-input id="model-parameter-hash" v-model="form.request_parameter_schema_sha256" maxlength="64" /></el-form-item>
      <el-form-item label="模型协议修订" for="model-protocol-revision" :error="error('/definition/model_protocol_revision')"><el-input id="model-protocol-revision" v-model="form.model_protocol_revision" maxlength="64" /></el-form-item>
    </div>
    </section>
  </el-form>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { ModelDefinition, ModelIssue, PublicModelDefinition } from '@/api/medical/model-configuration-domain'
const props = defineProps<{ disabled: boolean; issues: ModelIssue[] }>()
const element = ref<{ $el: HTMLElement } | null>(null)
const classifications = ref(''); const regions = ref('')
function empty(): ModelDefinition {
  return { provider: '', base_url: '', region: '', model_id: '', model_version: '', auth_ref: '', credential_reference_id: '', credential_revision: '',
    limits: { context_window: '', max_output_tokens: '', max_concurrency: '', requests_per_minute: '', tokens_per_minute: '', deadline_seconds: 60, temperature_enabled: false, reasoning_efforts: [], stop_limit: 0 },
    price_basis: { version_id: '', content_sha256: '' }, currency: '', data_policy: { allowed_classifications: [], allowed_regions: [], allow_training: false, retention_days: '', policy_basis: { version_id: '', content_sha256: '' } },
    request_parameter_schema_ref: '', request_parameter_schema_sha256: '', model_protocol_revision: '' }
}
const form = reactive<ModelDefinition>(empty())
const identityFields = [
  { key: 'provider', label: '供应商名称', maximum: 128, placeholder: '按实际供应商填写' },
  { key: 'base_url', label: 'HTTPS服务地址', maximum: 2048, placeholder: 'https://...' },
  { key: 'region', label: '模型区域', maximum: 128, placeholder: '按实际区域填写' },
  { key: 'model_id', label: '模型标识', maximum: 256, placeholder: '供应商模型标识' },
  { key: 'model_version', label: '固定模型版本', maximum: 256, placeholder: '实际固定版本' }
] as const
const countFields = [
  { key: 'context_window', label: '上下文窗口（tokens）' }, { key: 'max_output_tokens', label: '最大输出（tokens）' },
  { key: 'max_concurrency', label: '最大并发数' }, { key: 'requests_per_minute', label: '每分钟请求上限' },
  { key: 'tokens_per_minute', label: '每分钟token上限' }
] as const
function error(path: string): string { return props.issues.find(issue => issue.path === path || issue.path.startsWith(path + '/'))?.message ?? '' }
function value(): ModelDefinition {
  const result = JSON.parse(JSON.stringify(form)) as ModelDefinition
  result.data_policy.allowed_classifications = classifications.value.split('\n')
  result.data_policy.allowed_regions = regions.value.split('\n')
  return result
}
function clear(): void {
  Object.assign(form, empty()); classifications.value = ''; regions.value = ''
  element.value?.$el.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input,textarea').forEach(input => { input.value = ''; if (input instanceof HTMLInputElement && input.type === 'checkbox') input.checked = false })
}
function load(definition: PublicModelDefinition): void {
  clear(); Object.assign(form, JSON.parse(JSON.stringify(definition)), { auth_ref: '', credential_revision: '' })
  classifications.value = definition.data_policy.allowed_classifications.join('\n'); regions.value = definition.data_policy.allowed_regions.join('\n')
}
function fingerprint(): string { return JSON.stringify(value()) }
const initial = fingerprint()
function dirty(): boolean { return fingerprint() !== initial }
function focusIssue(): void { element.value?.$el.querySelector<HTMLElement>('.is-error input,.is-error textarea')?.focus() }
defineExpose({ value, clear, load, dirty, focusIssue })
</script>
<style scoped>
.model-field-section { padding: 24px; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; background: var(--el-fill-color-blank); }
.model-field-section > header { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 20px; }
.model-field-section__number { display: grid; place-items: center; width: 32px; height: 32px; flex-shrink: 0; border-radius: 8px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 12px; font-weight: 600; }
.model-field-section h3 { margin: 0 0 4px; }
.model-field-section header p { margin: 0; font-size: 12px; }
.model-field-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 0 24px; margin-top: 16px; }
.model-field-grid > .el-form-item:has(.el-textarea,.el-checkbox-group) { grid-column: 1 / -1; }
p { color: var(--el-text-color-secondary); line-height: 1.6; }
@media (max-width: 700px) { .model-field-grid { grid-template-columns: minmax(0,1fr); } .model-field-section { padding: 18px; } }
</style>

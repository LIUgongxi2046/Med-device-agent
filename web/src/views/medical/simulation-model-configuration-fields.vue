<template>
  <el-form ref="element" label-position="top" autocomplete="off" :disabled="disabled" @submit.prevent>
    <h3>模拟模型与声明案例</h3>
    <p>仅填写模拟配置。案例标识和SHA-256必须来自拟使用的固定案例；保存时不读取或核验案例文件。</p>
    <div class="simulation-field-grid">
      <el-form-item v-for="field in identityFields" :key="field.key" :label="field.label" :for="'simulation-' + field.key" :error="error('/definition/' + field.key)">
        <el-input :id="'simulation-' + field.key" v-model="form[field.key]" maxlength="256" placeholder="必须以SIMULATION:开头" autocomplete="off" />
      </el-form-item>
      <el-form-item label="声明案例标识（小写UUID）" for="simulation-case-id" :error="error('/definition/case_binding/case_id')">
        <el-input id="simulation-case-id" v-model="form.case_binding.case_id" maxlength="36" autocomplete="off" />
      </el-form-item>
      <el-form-item label="声明案例SHA-256（64位小写十六进制）" for="simulation-case-hash" :error="error('/definition/case_binding/case_sha256')">
        <el-input id="simulation-case-hash" v-model="form.case_binding.case_sha256" maxlength="64" autocomplete="off" />
      </el-form-item>
    </div>
    <el-alert type="warning" :closable="false" title="案例绑定状态固定为：仅声明（DECLARED_ONLY）" description="仅检查输入格式。实际案例字节仍需由后续运行组件核验，草稿不能证明案例有效或运行成功。" />
    <h3>声明的能力与限额</h3>
    <div class="simulation-field-grid">
      <el-form-item v-for="field in countFields" :key="field.key" :label="field.label" :for="'simulation-' + field.key" :error="error('/definition/limits/' + field.key)">
        <el-input :id="'simulation-' + field.key" v-model="form.limits[field.key]" inputmode="numeric" maxlength="20" placeholder="正整数字符串" />
      </el-form-item>
      <el-form-item label="请求时限（秒，1–300）" for="simulation-deadline_seconds" :error="error('/definition/limits/deadline_seconds')">
        <el-input-number id="simulation-deadline_seconds" v-model="form.limits.deadline_seconds" :min="1" :max="300" :precision="0" />
      </el-form-item>
      <el-form-item label="停止词条目上限（0–16）" for="simulation-stop_limit" :error="error('/definition/limits/stop_limit')">
        <el-input-number id="simulation-stop_limit" v-model="form.limits.stop_limit" :min="0" :max="16" :precision="0" />
      </el-form-item>
      <el-form-item label="允许的推理档位" :error="error('/definition/limits/reasoning_efforts')">
        <el-checkbox-group v-model="form.limits.reasoning_efforts" aria-label="允许的推理档位"><el-checkbox v-for="value in ['none', 'low', 'high', 'max']" :key="value" :value="value">{{ value }}</el-checkbox></el-checkbox-group>
      </el-form-item>
      <el-form-item label="temperature参数声明" :error="error('/definition/limits/temperature_enabled')">
        <el-checkbox v-model="form.limits.temperature_enabled">允许temperature（须包含none档位）</el-checkbox>
      </el-form-item>
    </div>
    <h3>固定模拟协议（只读）</h3>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="执行方式 / 生产资格">SIMULATION（模拟） / 不具备生产资格</el-descriptions-item>
      <el-descriptions-item label="模拟器 / 版本">{{ simulationIdentity.simulator_id }} / {{ simulationIdentity.simulator_version }}</el-descriptions-item>
      <el-descriptions-item label="来源记录Schema">{{ simulationIdentity.provenance_schema_ref }}</el-descriptions-item>
      <el-descriptions-item label="来源记录Schema SHA-256"><span class="simulation-wrap">{{ simulationIdentity.provenance_schema_sha256 }}</span></el-descriptions-item>
      <el-descriptions-item label="供应商请求计费标记">{{ simulationIdentity.provider_charge_amount }}</el-descriptions-item>
      <el-descriptions-item label="计费依据"><span class="simulation-wrap">{{ simulationIdentity.charge_basis }}</span></el-descriptions-item>
    </el-descriptions>
    <p>计费标记仅表示本地模拟不发送供应商请求，不表示基础设施免费、预算结算或供应商价格批准。</p>
  </el-form>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { simulationIdentity } from '@/api/medical/simulation-model-configuration-domain'
import type { SimulationDefinition, SimulationIssue } from '@/api/medical/simulation-model-configuration-domain'
const props = defineProps<{ disabled: boolean; issues: SimulationIssue[] }>()
const element = ref<{ $el: HTMLElement } | null>(null)
function empty(): SimulationDefinition {
  return { ...simulationIdentity, model_id: '', model_version: '', case_binding: { case_id: '', case_sha256: '', validation: 'DECLARED_ONLY' },
    limits: { context_window: '', max_output_tokens: '', max_concurrency: '', requests_per_minute: '', tokens_per_minute: '', deadline_seconds: 60, temperature_enabled: false, reasoning_efforts: [], stop_limit: 0 } }
}
const form = reactive<SimulationDefinition>(empty())
const identityFields = [{ key: 'model_id', label: '模拟模型标识' }, { key: 'model_version', label: '固定模拟模型版本' }] as const
const countFields = [
  { key: 'context_window', label: '上下文窗口（tokens）' }, { key: 'max_output_tokens', label: '最大输出（tokens）' },
  { key: 'max_concurrency', label: '最大并发数' }, { key: 'requests_per_minute', label: '每分钟请求上限' },
  { key: 'tokens_per_minute', label: '每分钟token上限' }
] as const
function error(path: string): string { return props.issues.find(issue => issue.path === path || issue.path.startsWith(path + '/'))?.message ?? '' }
function value(): SimulationDefinition { return JSON.parse(JSON.stringify(form)) as SimulationDefinition }
function clear(): void {
  Object.assign(form, empty())
  element.value?.$el.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input,textarea').forEach(input => { input.value = ''; if (input instanceof HTMLInputElement && input.type === 'checkbox') input.checked = false })
}
function load(definition: SimulationDefinition): void { clear(); Object.assign(form, JSON.parse(JSON.stringify(definition))) }
const initial = JSON.stringify(value())
function dirty(): boolean { return JSON.stringify(value()) !== initial }
function focusIssue(): void { element.value?.$el.querySelector<HTMLElement>('.is-error input,.is-error textarea')?.focus() }
defineExpose({ value, clear, load, dirty, focusIssue })
</script>
<style scoped>
.simulation-field-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 0 24px; margin-top: 16px; }
.simulation-wrap { overflow-wrap: anywhere; }
p { color: var(--el-text-color-secondary); line-height: 1.6; }
@media (max-width: 700px) { .simulation-field-grid { grid-template-columns: 1fr; } :deep(.el-descriptions__content) { word-break: break-word; } }
</style>

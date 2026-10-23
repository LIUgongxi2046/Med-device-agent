<template>
  <section class="template-evidence-controls" aria-label="证据规则配置">
    <el-form-item label="内容来源方式"><el-select :model-value="state.mode" :disabled="disabled" @update:model-value="changeMode"><el-option v-for="(label,key) in modes" :key="key" :label="label" :value="key" /></el-select></el-form-item>
    <el-form-item label="允许的权威来源类型"><el-select :model-value="state.authority_source_types" multiple filterable allow-create default-first-option :disabled="disabled" placeholder="填写并确认实际来源类型 English 键名" @update:model-value="(value:string[]) => change('authority_source_types',value)" /></el-form-item>
    <rule-basis-editor :model-value="verificationRules" resource-kind="RULE" label="检查规则" :disabled="disabled" @update:model-value="value => change('verification_rule_revisions',value)" />
    <template v-if="modelValue.mode === 'APPROVED_CALCULATION'">
      <rule-basis-editor :model-value="calculationRules" resource-kind="RULE" :max-items="1" label="计算规则" :disabled="disabled" @update:model-value="setCalculation" />
      <el-form-item label="计算输入字段"><el-select :model-value="state.input_field_ids" multiple filterable :disabled="disabled" placeholder="选择当前模板的实际字段" @update:model-value="(value:string[]) => change('input_field_ids',value)"><el-option v-for="item in fields" :key="item.field_id" :label="item.name" :value="item.field_id" /></el-select></el-form-item>
    </template>
    <p class="evidence-required">保留原件定位，并经人工核验。</p>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { Basis } from '@/api/medical/protocol'
import type { JsonObject, JsonValue, TemplateField } from '@/api/medical/template-approval'
import RuleBasisEditor from './rule-basis-editor.vue'
const props=defineProps<{modelValue:JsonObject;fields:TemplateField[];disabled:boolean}>(),emit=defineEmits<{'update:modelValue':[value:JsonObject]}>()
const state=computed(()=>props.modelValue as unknown as {mode:string;authority_source_types:string[];input_field_ids:string[]})
const modes={DIRECT_REFERENCE:'指定资料取值',NARRATIVE:'限定范围编写',APPROVED_CALCULATION:'依据批准规则计算',AUTHORIZED_ORIGINAL_ONLY:'使用获授权原件'}
const verificationRules=computed(()=>props.modelValue.verification_rule_revisions as unknown as Basis[]),calculationRules=computed(()=>props.modelValue.calculation_rule_revision?[props.modelValue.calculation_rule_revision as unknown as Basis]:[])
function change(key:string,value:unknown){emit('update:modelValue',{...props.modelValue,[key]:JSON.parse(JSON.stringify(value)) as JsonValue})}
function changeMode(mode:string){emit('update:modelValue',{...props.modelValue,mode,...(mode==='APPROVED_CALCULATION'?{}:{calculation_rule_revision:null,input_field_ids:[]})})}
function setCalculation(values:Basis[]){if(values.length>1)return;change('calculation_rule_revision',values[0]??null)}
</script>
<style scoped>.template-evidence-controls .el-select{width:100%}.evidence-required{font-size:12px;color:var(--el-text-color-secondary);margin:12px 0 20px}</style>

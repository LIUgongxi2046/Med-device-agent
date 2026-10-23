<template>
  <section aria-label="模板适用条件业务配置" class="template-profile-controls">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="readable">
      <el-form-item label="模板输入属性"><el-select v-model="draft.attribute_keys" multiple filterable allow-create default-first-option aria-label="模板输入属性" :disabled="disabled" placeholder="选择或登记属性标识"><el-option v-for="key in attributeOptions" :key="key" :value="key" :label="key" /></el-select></el-form-item>
      <p>属性标识与已核准规则保持一致，不填写产品属性值。未取得属性或判断依据时，适用结果保留待确认。</p>
      <template-condition-editor v-model="draft.applicability" :disabled="disabled" />
      <el-descriptions :column="1" border><el-descriptions-item label="未知结果处理">待确认 · UNKNOWN</el-descriptions-item><el-descriptions-item label="规则所需输入">{{ draft.applicability.input_keys.join('、') || '当前适用方式不需要规则输入' }}</el-descriptions-item></el-descriptions>
      <el-button :disabled="disabled" @click="apply">确认模板适用条件</el-button>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import TemplateConditionEditor from './template-condition-editor.vue'
import { draftJsonIssue } from './template-collection-input'
import { readBoundedJson } from '@/api/medical/project-json'
import { schemaIssues } from '@/api/medical/protocol'
import type { TemplateCondition } from '@/api/medical/template-approval'
interface ConditionSchema {attribute_keys:string[];applicability:TemplateCondition;unknown_result:'UNKNOWN'}
const props=defineProps<{value:string;disabled:boolean}>(),emit=defineEmits<{changed:[string]}>()
const blank=():ConditionSchema=>({attribute_keys:[],applicability:{mode:'ALWAYS',rule_revision:null,engine_revision:null,input_keys:[]},unknown_result:'UNKNOWN'})
const draft=ref<ConditionSchema>(blank()),error=ref(''),readable=ref(true)
const attributeOptions=computed(()=>[...new Set([...draft.value.attribute_keys,...draft.value.applicability.input_keys])])
watch(()=>props.value,text=>{error.value='';if(!text.trim()){draft.value=blank();readable.value=true;return}const issue=draftJsonIssue(text);if(issue){error.value=issue;readable.value=false;return}const value=readBoundedJson(text);if(schemaIssues('urn:medical:templates:1.0.0#/$defs/ConditionSchema',value).length){error.value='完整适用条件未通过字段检查，请在高级区域修正。';readable.value=false;return}draft.value=value as unknown as ConditionSchema;readable.value=true},{immediate:true})
function apply(){if(props.disabled||!readable.value)return;const value=draft.value;if(schemaIssues('urn:medical:templates:1.0.0#/$defs/ConditionSchema',value).length){error.value='请核查属性标识、固定规则和完整适用条件。';return}if(value.applicability.input_keys.some(key=>!value.attribute_keys.includes(key))){error.value='模板输入属性需要包含适用规则要求的全部属性。';return}error.value='';emit('changed',JSON.stringify(value,null,2))}
</script>
<style scoped>.template-profile-controls{padding:16px 0}.template-profile-controls .el-select{width:100%}.template-profile-controls p{font-size:12px;color:#526279;line-height:1.8}.template-profile-controls .el-descriptions{margin:20px 0}.template-profile-controls>.el-alert{margin-bottom:16px}</style>

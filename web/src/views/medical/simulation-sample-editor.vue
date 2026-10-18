<template><fieldset :disabled="disabled"><legend>{{ label }}</legend><div class="grid"><el-form-item label="真实样本标识"><el-input v-model="sample.sample_id" :disabled="disabled"/></el-form-item><el-form-item label="真实来源家族标识"><el-input v-model="sample.source_family" :disabled="disabled"/></el-form-item><el-form-item label="文档类型"><el-input v-model="sample.document_type" maxlength="64" :disabled="disabled"/></el-form-item><el-form-item v-if="manifest" label="来源分组"><el-select v-model="sample.split" :disabled="disabled"><el-option v-for="s in ['DEVELOPMENT','TUNING','ACCEPTANCE']" :key="s" :value="s" :label="s"/></el-select></el-form-item></div>
<evaluation-basis-field v-model="sample.input_ref" label="真实已登记输入ARTIFACT" kind="artifact" :disabled="disabled"/>
<evaluation-basis-field v-if="manifest" v-model="sample.expected_ref" label="真实已登记ExpectedItems ARTIFACT" kind="artifact" :disabled="disabled"/>
<p>标识来自真实业务样本，不自动生成。目录只证明可读取，服务端继续核验企业范围、类型和用途。</p>
<div v-for="(e,index) in sample.expectations" :key="index"><el-form-item label="预期维度"><el-select v-model="e.dimension" :disabled="disabled" @change="e.control_item_ids=[]"><el-option v-for="d in dimensions" :key="d" :value="d" :label="d"/></el-select></el-form-item><el-form-item label="适用项目标识，每行一项"><el-input :model-value="e.applicable_item_ids.join('\n')" type="textarea" :disabled="disabled" @update:model-value="e.applicable_item_ids=lines($event)"/></el-form-item><el-form-item v-if="e.dimension==='EVAL-11'" label="合法对照项目标识，每行一项"><el-input :model-value="e.control_item_ids.join('\n')" type="textarea" :disabled="disabled" @update:model-value="e.control_item_ids=lines($event)"/></el-form-item><el-button :disabled="disabled" @click="sample.expectations.splice(index,1)">移除此维度</el-button></div><el-button :disabled="disabled||sample.expectations.length>=12" @click="sample.expectations.push({dimension:'EVAL-01',applicable_item_ids:[],control_item_ids:[]})">增加预期维度</el-button></fieldset></template>
<script setup lang="ts">
import {computed} from 'vue'
import type {EvaluationSample,EvaluationDimension} from '@/api/medical/evaluation-domain'
import EvaluationBasisField from './evaluation-basis-field.vue'
const props=defineProps<{modelValue:EvaluationSample;disabled:boolean;manifest:boolean;label:string}>()
const sample=computed(()=>props.modelValue)
const dimensions=Array.from({length:12},(_,i)=>('EVAL-'+String(i+1).padStart(2,'0')) as EvaluationDimension)
const lines=(v:string)=>v===''?[]:v.split('\n')
</script>
<style scoped>fieldset{min-width:0;margin:16px 0;padding:16px;border:1px solid #dce3ec}.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.el-select{width:100%}@media(max-width:700px){.grid{grid-template-columns:1fr}}</style>

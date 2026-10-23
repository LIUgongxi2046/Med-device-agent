<template>
  <section aria-label="字段输出位置">
    <el-form-item label="输出章节"><el-select :model-value="state.node_id" filterable :disabled="disabled" @update:model-value="(value:string)=>change('node_id',value)"><el-option v-for="item in catalogue.nodes" :key="item.node_id" :label="item.official_number+' '+item.title" :value="item.node_id" /></el-select></el-form-item>
    <el-form-item label="输出表格"><el-select :model-value="state.repeat_id" clearable filterable :disabled="disabled" placeholder="直接输出到章节" @update:model-value="(value:string)=>change('repeat_id',value||null)"><el-option v-for="item in catalogue.repeats" :key="item.repeat_id" :label="item.name" :value="item.repeat_id" /></el-select></el-form-item>
    <el-form-item label="输出列"><el-select :model-value="state.column_key" clearable filterable :disabled="disabled" placeholder="选择实际字段列" @update:model-value="(value:string)=>change('column_key',value||null)"><el-option v-for="item in columns" :key="item.field_id" :label="item.name" :value="item.key" /></el-select></el-form-item>
    <el-form-item label="输出顺序"><el-input-number :model-value="state.ordinal" :min="1" :max="2147483647" :precision="0" :disabled="disabled" aria-label="输出顺序" @update:model-value="(value:number|undefined)=>change('ordinal',value??null)" /></el-form-item>
    <el-checkbox :model-value="state.internal_only" :disabled="disabled" @update:model-value="(value:unknown)=>change('internal_only',value)">仅在内部工作区使用</el-checkbox>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { JsonObject, JsonValue } from '@/api/medical/template-approval'
import type { DefinitionCatalogue } from './template-business-controls'
const props=defineProps<{modelValue:JsonObject;catalogue:DefinitionCatalogue;disabled:boolean}>(),emit=defineEmits<{'update:modelValue':[value:JsonObject]}>()
const state=computed(()=>props.modelValue as unknown as {node_id:string;repeat_id:string|null;column_key:string|null;internal_only:boolean;ordinal:number})
const columns=computed(()=>{const repeat=props.catalogue.repeats.find(item=>item.repeat_id===state.value.repeat_id);return repeat?props.catalogue.fields.filter(item=>repeat.field_ids.includes(item.field_id)):props.catalogue.fields})
function change(key:string,value:unknown){emit('update:modelValue',{...props.modelValue,[key]:value as JsonValue})}
</script>

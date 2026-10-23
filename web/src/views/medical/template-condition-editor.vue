<template>
  <section class="template-condition-controls" aria-label="适用条件配置">
    <el-form-item label="适用条件"><el-select :model-value="modelValue.mode" :disabled="disabled" @update:model-value="changeMode"><el-option label="始终保留" value="ALWAYS" /><el-option label="根据已核准规则判断" value="RULE" /></el-select></el-form-item>
    <el-form-item v-if="modelValue.mode === 'RULE'" label="适用判断规则"><el-select :model-value="modelValue.rule_revision?.version_id" :disabled="disabled" :loading="loading" filterable placeholder="选择已核准规则" @visible-change="(opened:boolean) => opened && load()" @update:model-value="selectRule"><el-option v-for="item in rules" :key="item.rule_version" :label="item.reason" :value="item.rule_version" /><template #footer><el-button v-if="nextAfter" text :loading="loading" @click="load(nextAfter)">加载更多规则</el-button></template></el-select></el-form-item>
    <p v-if="modelValue.mode === 'RULE'" class="condition-detail">判断所需属性：{{ modelValue.input_keys.join('、') || '尚未选择规则' }}。未知结果保留待确认。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { readRuleList } from '@/api/medical/rule-versions'
import type { StoredRule } from '@/api/medical/rule-versions'
import type { TemplateCondition } from '@/api/medical/template-approval'
const props=defineProps<{modelValue:TemplateCondition;disabled:boolean}>(),emit=defineEmits<{'update:modelValue':[value:TemplateCondition]}>()
const user=useUserStore(),rules=shallowRef<StoredRule[]>([]),loading=ref(false),error=ref(''),nextAfter=ref<string|null>(null)
let controller:AbortController|null=null,generation=0
function changeMode(mode:'ALWAYS'|'RULE'){emit('update:modelValue',{mode,rule_revision:null,engine_revision:null,input_keys:[]});if(mode==='RULE')void load()}
function selectRule(id:string){const item=rules.value.find(item=>item.rule_version===id);if(!item)return;emit('update:modelValue',{mode:'RULE',rule_revision:{version_id:id,content_sha256:item.content_sha256},engine_revision:item.content.engine_revision,input_keys:[...item.content.expression.input_keys]})}
async function load(after?:string|null){
 const session=user.medicalSession;if(!session||loading.value||props.disabled)return
 const reading=new AbortController(),current=generation;controller?.abort();controller=reading;loading.value=true;error.value=''
 try{const page=await readRuleList(session,{limit:100,...(after?{after}:{})},reading.signal);if(current!==generation||user.medicalSession!==session)return;const values=page.items.map(item=>item.rule).filter(item=>item.status==='APPROVED'&&item.content.expression.input_keys.length>0);rules.value=after?[...rules.value,...values]:values;nextAfter.value=page.next_after}
 catch(failure){if(current===generation&&!reading.signal.aborted)error.value=failure instanceof Error?failure.message:'适用规则读取失败'}
 finally{if(current===generation)loading.value=false}
}
watch(()=>user.medicalSession,()=>{generation++;controller?.abort();rules.value=[];nextAfter.value=null;loading.value=false;error.value=''})
onBeforeUnmount(()=>{generation++;controller?.abort()})
</script>
<style scoped>.template-condition-controls .el-select{width:100%}.condition-detail{font-size:12px;color:var(--el-text-color-secondary);line-height:1.7}</style>

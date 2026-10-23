<template>
  <div class="member-selector">
    <el-select :id="inputId" :model-value="modelValue" :aria-label="label" filterable :disabled="disabled" :loading="loading" placeholder="选择企业成员" @update:model-value="emit('update:modelValue', $event)" @visible-change="visible">
      <el-option v-if="session" :value="session.user.user_id" :label="selfName" />
      <el-option v-for="member in members.filter(item => item.user_id !== session?.user.user_id && activeMember(item))" :key="member.user_id" :value="member.user_id" :label="memberLabelName(member)" />
      <el-option v-if="unresolved" :value="modelValue" label="已登记人员，当前目录未取得可选身份" disabled />
      <template #footer><el-button v-if="nextAfter" text :disabled="disabled" :loading="loading" @click="load(true)">加载更多企业成员</el-button></template>
    </el-select>
    <p v-if="session && !memberLabelsAllowed(session)">当前可选择本人，其他成员名称需要企业成员读取许可。</p>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { activeMember, memberLabelName, memberLabelsAllowed, readMemberLabels } from '@/api/medical/member-labels'
import type { MemberLabel } from '@/api/medical/member-labels'

const props=defineProps<{modelValue:string;disabled:boolean;label:string;inputId:string}>()
const emit=defineEmits<{'update:modelValue':[value:string]}>()
const user=useUserStore(),session=computed(()=>user.medicalSession),members=ref<MemberLabel[]>([]),nextAfter=ref<string|null>(null),loading=ref(false),loaded=ref(false)
const selfName=computed(()=>session.value?.user.display_name??session.value?.user.username??'当前人员')
const unresolved=computed(()=>!!props.modelValue&&props.modelValue!==session.value?.user.user_id&&!members.value.some(item=>item.user_id===props.modelValue&&activeMember(item)))
let generation=0,controller:AbortController|null=null
function clear(){generation++;controller?.abort();controller=null;members.value=[];nextAfter.value=null;loading.value=false;loaded.value=false}
function visible(value:boolean){if(value&&!loaded.value)void load(false)}
async function load(more:boolean){
 const captured=session.value;if(!captured?.current_context||!memberLabelsAllowed(captured)||props.disabled||loading.value||document.hidden||more&&!nextAfter.value)return
 const current=generation,reading=new AbortController();controller=reading;loading.value=true
 try{
  const page=await readMemberLabels(captured,more?nextAfter.value:null,reading.signal)
  if(current!==generation||session.value!==captured||reading.signal.aborted||document.hidden)return
  members.value=more?[...members.value,...page.items]:page.items;nextAfter.value=page.next_after;loaded.value=true
 }finally{if(current===generation&&controller===reading)loading.value=false}
}
watch(()=>user.medicalSession,clear)
onBeforeUnmount(clear)
</script>
<style scoped>
.member-selector,.member-selector .el-select{width:100%;min-width:0}.member-selector p{font-size:12px;line-height:1.7;color:var(--el-text-color-regular);margin:8px 0 0}
</style>

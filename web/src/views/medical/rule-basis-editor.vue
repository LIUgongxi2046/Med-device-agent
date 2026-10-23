<template>
  <fieldset :disabled="disabled" class="rule-bases">
    <legend>{{ label }}</legend>
    <div class="basis-picker"><el-select v-model="kind" aria-label="依据类型" :disabled="disabled || !!resourceKind" @change="load(false)"><el-option label="来源资料" value="SOURCE" /><el-option label="检查规则" value="RULE" /></el-select><el-select v-model="selected" filterable :loading="loading" :disabled="disabled" :placeholder="'选择' + label" aria-label="选择固定依据" @visible-change="(value:boolean) => value && !options.length && load(false)"><el-option v-for="item in options" :key="item.version_id" :value="item.version_id" :label="item.label" /><template #footer><el-button v-if="nextAfter" text :loading="loading" @click="load(true)">加载更多依据</el-button></template></el-select><el-button :disabled="disabled || !selected || loading || atLimit" @click="choose">采用所选依据</el-button><el-button text :loading="loading" :disabled="disabled" @click="load(false)">刷新目录</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="modelValue" empty-text="尚未选择依据" row-key="version_id"><el-table-column label="已选依据" min-width="200"><template #default="{ row }">{{ optionNames[row.version_id] ?? basisNames?.[row.version_id] ?? row.version_id }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{ $index }"><el-button link type="danger" :disabled="disabled" @click="emit('update:modelValue', modelValue.filter((_, i) => i !== $index))">移除依据</el-button></template></el-table-column></el-table>
    <el-collapse><el-collapse-item title="高级：查看或输入完整版本依据" name="advanced">
    <div v-for="(item, index) in modelValue" :key="index" class="basis-row">
      <el-form-item :label="label + ' ' + (index + 1) + ' 固定版本'">
        <el-input :model-value="item.version_id" :disabled="disabled" @update:model-value="change(index, 'version_id', $event)" />
      </el-form-item>
      <el-form-item :label="label + ' ' + (index + 1) + ' SHA-256摘要'">
        <el-input :model-value="item.content_sha256" :disabled="disabled" @update:model-value="change(index, 'content_sha256', $event)" />
      </el-form-item>
      <el-button :disabled="disabled" @click="emit('update:modelValue', modelValue.filter((_, i) => i !== index))">移除{{ label }} {{ index + 1 }}</el-button>
    </div>
    <el-button :disabled="disabled || atLimit" @click="emit('update:modelValue', [...modelValue, { version_id: '', content_sha256: '' }])">添加{{ label }}</el-button>
    </el-collapse-item></el-collapse>
  </fieldset>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import type { Basis } from '@/api/medical/protocol'
import { readSourceCatalogue, readSourceGovernance } from '@/api/medical/source-governance'
import { readRuleList } from '@/api/medical/rule-versions'
const props = defineProps<{ modelValue: Basis[]; label: string; disabled: boolean; resourceKind?:'SOURCE'|'RULE';maxItems?:number;basisNames?:Record<string,string>;sourceProjectId?:string | undefined }>()
const emit = defineEmits<{ 'update:modelValue': [value: Basis[]] }>()
const user = useUserStore(), kind = ref(props.resourceKind??'SOURCE'), selected = ref(''), options = ref<(Basis & {label:string})[]>([]), nextAfter = ref<string|null>(null), loading = ref(false), error = ref('')
const optionNames = computed(() => Object.fromEntries(options.value.map(item => [item.version_id,item.label])))
const atLimit=computed(()=>props.maxItems!==undefined&&props.modelValue.length>=props.maxItems)
let generation = 0, controller:AbortController|null = null
function clear(){generation++;controller?.abort();controller=null;options.value=[];selected.value='';nextAfter.value=null;loading.value=false;error.value=''}
async function load(more:boolean){
  const session=user.medicalSession;if(!session?.current_context||props.disabled||document.hidden||loading.value||more&&!nextAfter.value)return
  if(!more)clear();const current=generation,reading=new AbortController();controller=reading;loading.value=true;error.value=''
  try{
    let values:(Basis & {label:string})[],next:string|null
    if(kind.value==='RULE'){const page=await readRuleList(session,{limit:50,...(more?{after:nextAfter.value!}:{})},reading.signal);values=page.items.map(item=>({version_id:item.rule.rule_version,content_sha256:item.rule.content_sha256,label:item.rule.reason}));next=page.next_after}
    else{const page=await readSourceCatalogue(session,{limit:50,...(props.sourceProjectId?{project_id:props.sourceProjectId}:{ownership:'ENTERPRISE' as const}),...(more?{after:nextAfter.value!}:{})},reading.signal);values=page.items.filter(item=>item.ownership!=='SESSION'&&(item.lifecycle===null||item.lifecycle==='ACTIVE')).map(item=>({version_id:item.source_version,content_sha256:'',label:item.title}));next=page.next_after}
    if(current===generation&&session===user.medicalSession){options.value=more?[...options.value,...values]:values;nextAfter.value=next}
  }catch(failure){if(current===generation&&!reading.signal.aborted)error.value=failure instanceof Error?failure.message:'依据目录读取失败'}finally{if(current===generation)loading.value=false}
}
async function choose(){
  const session=user.medicalSession,item=options.value.find(value=>value.version_id===selected.value);if(!session||!item||props.disabled||atLimit.value)return
  const current=generation,reading=new AbortController();controller=reading;loading.value=true;error.value=''
  try{const basis=item.content_sha256?{version_id:item.version_id,content_sha256:item.content_sha256}:{version_id:item.version_id,content_sha256:(await readSourceGovernance(session,item.version_id,reading.signal)).original_sha256};if(current===generation&&session===user.medicalSession&&!props.modelValue.some(value=>value.version_id===basis.version_id))emit('update:modelValue',[...props.modelValue,basis])}
  catch(failure){if(current===generation&&!reading.signal.aborted)error.value=failure instanceof Error?failure.message:'依据版本读取失败'}finally{if(current===generation)loading.value=false}
}
watch(()=>user.medicalSession,clear)
watch(()=>props.sourceProjectId,clear)
onBeforeUnmount(clear)
function change(index: number, field: keyof Basis, value: string): void {
  emit('update:modelValue', props.modelValue.map((item, i) => i === index ? { ...item, [field]: value } : item))
}
</script>
<style scoped>
.rule-bases { border: 1px solid var(--el-border-color); padding: 16px; margin: 16px 0; min-width: 0; }
.basis-row { margin-bottom: 20px; }
.basis-picker{display:grid;grid-template-columns:150px minmax(200px,1fr) auto auto;gap:12px;margin:8px 0 16px}.rule-bases .el-collapse{margin-top:16px}@media(max-width:800px){.basis-picker{grid-template-columns:1fr}}
</style>

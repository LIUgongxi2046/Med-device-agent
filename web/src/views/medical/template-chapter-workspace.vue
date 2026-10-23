<template>
  <section data-template-chapter-workspace class="chapter-workspace">
    <header><h2>章节与生成规则</h2><el-button :disabled="disabled" @click="edit(null)">添加章节</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="nodes" row-key="node_id" empty-text="添加章节，配置处理方式、资料依据与检查要求">
      <el-table-column label="章节" min-width="200"><template #default="{ row }"><strong>{{ row.official_number }} {{ row.title }}</strong></template></el-table-column>
      <el-table-column label="处理方式" min-width="160"><template #default="{ row }"><el-tag type="info">{{ modes[row.content_mode] }}</el-tag></template></el-table-column>
      <el-table-column label="指定资料" width="130"><template #default="{ row }">{{ row.basis_refs.length }} 项依据</template></el-table-column>
      <el-table-column label="检查要求" min-width="230"><template #default="{ row }">{{ row.content_requirements.join('；') }}</template></el-table-column>
      <el-table-column label="操作" width="130"><template #default="{ $index }"><el-button link type="primary" :disabled="disabled" @click="edit($index)">配置章节</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" title="章节配置" width="min(900px,96vw)" :close-on-click-modal="false" destroy-on-close>
      <el-form v-if="draft" label-position="top" :disabled="disabled" @submit.stop.prevent="confirm">
        <el-row :gutter="20"><el-col :xs="24" :md="8"><el-form-item label="章节编号"><el-input v-model="draft.official_number" /></el-form-item></el-col><el-col :xs="24" :md="16"><el-form-item label="章节名称"><el-input v-model="draft.title" /></el-form-item></el-col></el-row>
        <el-row :gutter="20"><el-col :xs="24" :md="12"><el-form-item label="所属章节"><el-select v-model="draft.parent_id" clearable placeholder="顶层章节"><el-option v-for="item in nodes.filter(item => item.node_id !== draft!.node_id)" :key="item.node_id" :label="item.official_number + ' ' + item.title" :value="item.node_id" /></el-select></el-form-item></el-col><el-col :xs="24" :md="12"><el-form-item label="处理方式"><el-select v-model="draft.content_mode" @change="changeMode"><el-option v-for="(label, key) in modes" :key="key" :value="key" :label="label" /></el-select></el-form-item></el-col></el-row>
        <el-form-item label="章节要求"><el-checkbox v-model="draft.required">此章节为必需内容</el-checkbox></el-form-item>
        <el-form-item label="检查要求"><el-input v-model="requirements" type="textarea" :rows="4" placeholder="逐行填写章节内容、核对要求及适用限制" /></el-form-item>
        <rule-basis-editor v-model="draft.basis_refs" label="指定资料与依据" :disabled="disabled" />
        <rule-basis-editor :model-value="verificationRules" resource-kind="RULE" label="章节检查规则" :disabled="disabled" @update:model-value="setVerificationRules" />
        <el-row :gutter="20"><el-col :xs="24" :md="12"><el-form-item label="适用条件"><el-select :model-value="draft.condition.mode" @update:model-value="conditionMode"><el-option label="始终保留" value="ALWAYS" /><el-option label="根据规则判断" value="RULE" /></el-select></el-form-item></el-col><el-col v-if="draft.condition.mode === 'RULE'" :xs="24" :md="12"><el-form-item label="适用判断规则"><el-select :model-value="draft.condition.rule_revision?.version_id" :loading="rulesLoading" filterable placeholder="选择已核准规则" @visible-change="(value:boolean) => value && loadRules()" @update:model-value="selectCondition"><el-option v-for="item in rules" :key="item.rule_version" :value="item.rule_version" :label="item.reason" /></el-select></el-form-item></el-col></el-row>
        <el-alert v-if="rulesError" :title="rulesError" type="error" :closable="false" />
        <el-form-item v-if="draft.content_mode === 'APPROVED_CALCULATION'" label="计算依据"><rule-basis-editor :model-value="calculationRules" resource-kind="RULE" :max-items="1" label="计算规则" :disabled="disabled" @update:model-value="setCalculationRule" /></el-form-item>
        <el-collapse><el-collapse-item title="目录身份与完整证据配置" name="advanced"><el-descriptions :column="1" border><el-descriptions-item label="目录稳定标识">{{ draft.node_id }}</el-descriptions-item><el-descriptions-item label="顺序">{{ draft.ordinal }}</el-descriptions-item></el-descriptions><el-form-item label="证据规则完整定义"><el-input v-model="evidenceText" type="textarea" :rows="8" /></el-form-item><el-form-item label="目录性质"><el-select v-model="draft.nature"><el-option label="规定目录" value="OFFICIAL" /><el-option label="企业补充目录" value="ENTERPRISE_SUPPLEMENT" /></el-select></el-form-item></el-collapse-item></el-collapse>
        <el-alert v-if="editError" :title="editError" type="error" :closable="false" />
        <div class="chapter-editor-actions"><el-button @click="visible = false">取消修改</el-button><el-button native-type="submit" type="primary">确认章节配置</el-button></div>
      </el-form>
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowReactive, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { readBoundedJson } from '@/api/medical/project-json'
import { schemaIssues, validate } from '@/api/medical/protocol'
import type { Basis } from '@/api/medical/protocol'
import type { JsonObject, TemplateNode } from '@/api/medical/template-approval'
import { readRuleList } from '@/api/medical/rule-versions'
import type { StoredRule } from '@/api/medical/rule-versions'
import RuleBasisEditor from './rule-basis-editor.vue'
const props=defineProps<{value:string;disabled:boolean}>(),emit=defineEmits<{changed:[value:string]}>()
const user=useUserStore(),nodes=shallowRef<TemplateNode[]>([]),error=ref(''),visible=ref(false),draft=shallowRef<TemplateNode|null>(null),editing=ref<number|null>(null),requirements=ref(''),evidenceText=ref(''),editError=ref('')
const rules=shallowRef<StoredRule[]>([]),rulesLoading=ref(false),rulesError=ref('')
let controller:AbortController|null=null
const modes:Record<string,string>={DIRECT_REFERENCE:'指定资料取值',NARRATIVE:'限定范围编写',APPROVED_CALCULATION:'依据批准规则计算',AUTHORIZED_ORIGINAL_ONLY:'使用获授权原件',DIRECTORY:'组织章节目录'}
const verificationRules=computed(()=>Array.isArray(draft.value?.evidence_rule.verification_rule_revisions)?draft.value!.evidence_rule.verification_rule_revisions as unknown as Basis[]:[])
const calculationRules=computed(()=>draft.value?.evidence_rule.calculation_rule_revision?[draft.value.evidence_rule.calculation_rule_revision as unknown as Basis]:[])
function edit(index:number|null){
  if(props.disabled)return;editing.value=index;editError.value=''
  const chapter:TemplateNode=index===null?{node_id:crypto.randomUUID(),parent_id:null,official_number:'',title:'',ordinal:nodes.value.length+1,condition:{mode:'ALWAYS',rule_revision:null,engine_revision:null,input_keys:[]},required:true,content_mode:'NARRATIVE',evidence_rule:{mode:'NARRATIVE',authority_source_types:[],verification_rule_revisions:[],calculation_rule_revision:null,input_field_ids:[],require_original_locator:true,require_human_verification:true},basis_refs:[],nature:'ENTERPRISE_SUPPLEMENT',content_requirements:[]}:JSON.parse(JSON.stringify(nodes.value[index]!))
  draft.value=shallowReactive(chapter);requirements.value=chapter.content_requirements.join('\n');evidenceText.value=JSON.stringify(chapter.evidence_rule,null,2);visible.value=true
}
function updateEvidence(value:JsonObject){if(!draft.value)return;draft.value.evidence_rule=value;evidenceText.value=JSON.stringify(value,null,2)}
function setVerificationRules(value:Basis[]){if(draft.value)updateEvidence({...draft.value.evidence_rule,verification_rule_revisions:JSON.parse(JSON.stringify(value))})}
function setCalculationRule(value:Basis[]){if(draft.value)updateEvidence({...draft.value.evidence_rule,calculation_rule_revision:value[0]?JSON.parse(JSON.stringify(value[0])):null})}
function changeMode(value:string){if(!draft.value||value==='DIRECTORY')return;updateEvidence({...draft.value.evidence_rule,mode:value,...(value!=='APPROVED_CALCULATION'?{calculation_rule_revision:null,input_field_ids:[]}:{})})}
function conditionMode(value:'ALWAYS'|'RULE'){if(!draft.value)return;draft.value.condition=value==='ALWAYS'?{mode:'ALWAYS',rule_revision:null,engine_revision:null,input_keys:[]}:{mode:'RULE',rule_revision:null,engine_revision:null,input_keys:[]};if(value==='RULE')void loadRules()}
async function loadRules(){
  const session=user.medicalSession;if(!session||rulesLoading.value)return;controller?.abort();const reading=new AbortController();controller=reading;rulesLoading.value=true;rulesError.value=''
  try{const page=await readRuleList(session,{limit:100},reading.signal);if(controller===reading&&user.medicalSession===session)rules.value=page.items.map(item=>item.rule).filter(item=>item.status==='APPROVED'&&item.content.expression.input_keys.length>0)}
  catch(failure){if(!reading.signal.aborted)rulesError.value=failure instanceof Error?failure.message:'检查规则读取失败'}
  finally{if(controller===reading)rulesLoading.value=false}
}
function selectCondition(id:string){const rule=rules.value.find(item=>item.rule_version===id);if(!rule||!draft.value)return;draft.value.condition={mode:'RULE',rule_revision:{version_id:id,content_sha256:rule.content_sha256},engine_revision:rule.content.engine_revision,input_keys:[...rule.content.expression.input_keys]}}
function confirm(){
  if(!draft.value||props.disabled)return
  const evidence=readBoundedJson(evidenceText.value)
  if(schemaIssues('urn:medical:templates:1.0.0#/$defs/EvidenceRule',evidence).length){editError.value='请补充完整证据规则及实际计算输入';return}
  const value={...draft.value,parent_id:draft.value.parent_id||null,evidence_rule:evidence,content_requirements:requirements.value.split('\n').map(item=>item.trim()).filter(Boolean)}
  const issues=schemaIssues('urn:medical:templates:1.0.0#/$defs/Node',value)
  if(issues.length){editError.value='章节配置尚未完整：'+issues[0]!.path;return}
  const next=[...nodes.value];if(editing.value===null)next.push(value as TemplateNode);else next[editing.value]=value as TemplateNode
  emit('changed',JSON.stringify(next,null,2));visible.value=false
}
watch(()=>props.value,value=>{error.value='';if(!value){nodes.value=[];return}const result=readBoundedJson(value);if(!Array.isArray(result)){error.value='章节定义需要完整目录集合';nodes.value=[];return}nodes.value=result.map(item=>validate<TemplateNode>('urn:medical:templates:1.0.0#/$defs/Node',item))},{immediate:true})
watch(()=>user.medicalSession,()=>{controller?.abort();visible.value=false;rules.value=[]})
onBeforeUnmount(()=>controller?.abort())
</script>
<style scoped>
.chapter-workspace>header{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:20px}.chapter-workspace h2{font-size:17px;margin:0}.chapter-workspace :deep(.el-select){width:100%}.chapter-editor-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:24px}.chapter-workspace .el-alert{margin:16px 0}.chapter-workspace .el-collapse{margin-top:20px}
</style>

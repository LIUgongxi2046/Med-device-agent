<template>
  <section class="rule-directory" aria-label="检查规则目录" data-rule-list>
    <el-card shadow="never" class="rule-directory-card">
      <template #header><div class="directory-heading"><h2>规则目录</h2><span>检查对象、判断条件与处置方式</span></div></template>
      <el-empty v-if="!permitted" description="当前没有检查规则目录读取权限" />
      <template v-else>
        <el-form label-position="top" class="rule-filters" @submit.prevent="search">
          <el-form-item label="规则名称或判断条件" :error="queryError"><el-input v-model="queryInput" maxlength="256" placeholder="搜索名称、条件或固定版本" :disabled="busy" clearable /></el-form-item>
          <el-form-item label="检查维度"><el-select v-model="dimensionInput" :disabled="busy" clearable placeholder="全部检查维度"><el-option v-for="(label,value) in ruleDimensionLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item label="处置方式"><el-select v-model="handlingInput" :disabled="busy" clearable placeholder="全部处置方式"><el-option v-for="(label,value) in ruleHandlingLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item label="审核状态"><el-select v-model="statusInput" :disabled="busy" clearable placeholder="全部审核状态"><el-option v-for="(label,value) in ruleStatusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <div class="filter-actions"><el-button native-type="submit" type="primary" :loading="loading" :disabled="user.medicalWriting">查询规则</el-button><el-button :disabled="busy" @click="reset">重置筛选</el-button></div>
        </el-form>
        <div class="directory-meta"><span>仅显示当前组织授权可读取且依据可用的规则</span><span v-if="loaded">已读取 {{ items.length }} 项</span></div>
        <p v-if="loading" role="status">正在读取检查规则</p>
        <el-empty v-if="loaded && !items.length && !loading" :description="nextAfter ? '当前范围没有可读取规则，可继续查询后续记录' : '没有符合筛选条件的检查规则'" />
        <el-table v-if="items.length" :data="items" :row-key="rowKey" class="rule-table">
          <el-table-column label="规则" min-width="220"><template #default="{ row }"><router-link :to="link(row)">{{ row.business?.definition.name ?? '未登记业务名称的规则' }}</router-link></template></el-table-column>
          <el-table-column label="检查对象" min-width="150"><template #default="{ row }">{{ row.business ? ruleDimensionLabels[row.business.definition.dimension as RuleBusinessDefinition['dimension']] : '未登记' }}</template></el-table-column>
          <el-table-column label="条件与处置" min-width="280"><template #default="{ row }"><p class="rule-condition">{{ row.business?.definition.condition ?? '未登记业务判断条件' }}</p><el-tag v-if="row.business" :type="row.business.definition.handling === 'BLOCK_SIGNING' ? 'danger' : row.business.definition.handling === 'MANUAL_CONFIRMATION' ? 'warning' : 'info'" effect="plain">{{ ruleHandlingLabels[row.business.definition.handling as RuleBusinessDefinition['handling']] }}</el-tag><span v-else>未登记处置方式</span></template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.resource.rule.status === 'APPROVED' ? 'success' : row.resource.rule.status === 'DISABLED' ? 'info' : 'warning'">{{ ruleStatusLabels[row.resource.rule.status as RuleStatus] }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }"><router-link :to="link(row)">查看规则</router-link><el-popover trigger="click" width="380"><template #reference><el-button link type="primary">版本与依据</el-button></template><p class="rule-version">{{ row.resource.rule.rule_version }}</p><p class="rule-version">{{ row.resource.rule.content_sha256 }}</p><p>{{ row.resource.rule.content.engine_revision }}</p></el-popover></template></el-table-column>
        </el-table>
        <div v-if="loaded" class="directory-footer"><span v-if="changed">筛选条件已修改，请重新查询</span><span v-else-if="!nextAfter && items.length">当前筛选结果已全部读取</span><el-button v-if="nextAfter" :disabled="busy || changed" @click="load(true)">继续读取规则</el-button></div>
      </template>
    </el-card>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { ruleAllowed, ruleStatusLabels } from '@/api/medical/rule-versions'
import type { RuleStatus } from '@/api/medical/rule-versions'
import { ruleDimensionLabels, ruleHandlingLabels } from '@/api/medical/rule-business'
import type { RuleBusinessDefinition } from '@/api/medical/rule-business'
import { readRuleBusinessDirectory, ruleDirectorySchema } from '@/api/medical/rule-business-directory'
import type { RuleDirectoryEntry, RuleDirectoryQuery } from '@/api/medical/rule-business-directory'
import { canonicalTemplateValue } from '@/api/medical/template-approval'
import { projectTextIssue } from '@/api/medical/project-write'

const user=useUserStore(),permitted=computed(()=>ruleAllowed(user.medicalSession,'rule.read'))
const items=shallowRef<RuleDirectoryEntry[]>([]),nextAfter=ref<string|null>(null),loading=ref(false),loaded=ref(false)
const queryInput=ref(''),dimensionInput=ref<RuleBusinessDefinition['dimension']|''>(''),handlingInput=ref<RuleBusinessDefinition['handling']|''>(''),statusInput=ref<RuleStatus|''>(''),queryError=ref('')
const applied=shallowRef<RuleDirectoryQuery>({limit:25}),busy=computed(()=>loading.value||user.medicalWriting)
function filters():RuleDirectoryQuery{return {limit:25,...(queryInput.value?{q:queryInput.value}:{}),...(dimensionInput.value?{dimension:dimensionInput.value}:{}),...(handlingInput.value?{handling:handlingInput.value}:{}),...(statusInput.value?{status:statusInput.value}:{})}}
const changed=computed(()=>canonicalTemplateValue(filters())!==canonicalTemplateValue(applied.value))
function link(row:RuleDirectoryEntry){return {path:'/rules/'+row.resource.rule.rule_version,query:{content_sha256:row.resource.rule.content_sha256}}}
function rowKey(row:RuleDirectoryEntry){return row.resource.rule.rule_version}
let generation=0,controller:AbortController|null=null
function clear(){generation++;controller?.abort();controller=null;items.value=[];nextAfter.value=null;loading.value=false;loaded.value=false}
async function load(more:boolean):Promise<void>{
 const session:MedicalSession|null=user.medicalSession
 if(!session?.current_context||!permitted.value||busy.value||document.hidden||more&&(!nextAfter.value||changed.value))return
 const query:RuleDirectoryQuery={...applied.value,...(more&&nextAfter.value?{after:nextAfter.value}:{})},current=generation
 controller?.abort();controller=new AbortController();loading.value=true
 try{
  const result=await readRuleBusinessDirectory(session,query,controller.signal)
  if(current!==generation||user.medicalSession!==session||document.hidden)return
  const previous=more?items.value:[],last=previous.at(-1)?.resource.rule.rule_version
  if(last&&result.items.some(item=>item.resource.rule.rule_version<=last))fail('CONTENT_MISMATCH','检查规则分页需要连续且不能重复')
  items.value=[...previous,...result.items];nextAfter.value=result.next_after;loaded.value=true
 }finally{if(current===generation)loading.value=false}
}
async function search():Promise<void>{
 if(busy.value)return
 const query=filters();queryError.value=query.q?projectTextIssue(query.q,256):''
 if(schemaIssues(ruleDirectorySchema+'Query',query).length)queryError.value='请填写有效的规则筛选条件'
 if(queryError.value)return
 clear();applied.value=query;await load(false)
}
async function reset():Promise<void>{queryInput.value='';dimensionInput.value='';handlingInput.value='';statusInput.value='';await search()}
watch(()=>user.medicalSession,async()=>{clear();queryInput.value='';dimensionInput.value='';handlingInput.value='';statusInput.value='';queryError.value='';applied.value={limit:25};await load(false)},{immediate:true,flush:'sync'})
onBeforeUnmount(clear)
</script>
<style scoped>
.medical-page > section.rule-directory{padding:0;border:0;border-radius:8px;background:transparent}.rule-directory :deep(.el-card__header){padding:20px 22px;border-color:#e9edf3}.rule-directory :deep(.el-card__body){padding:22px}.rule-directory :deep(.el-table){border-radius:6px;--el-table-header-bg-color:#f8fafc}.rule-directory :deep(.el-form){margin-bottom:0}.rule-directory :deep(.el-input__wrapper),.rule-directory :deep(.el-select__wrapper){border-radius:6px;min-height:38px}
.rule-directory-card{border-radius:8px}.directory-heading,.directory-meta,.directory-footer{display:flex;align-items:center;justify-content:space-between;gap:16px}.directory-heading h2{font-size:17px;margin:0;color:#243247}.directory-heading span,.directory-meta,.directory-footer{font-size:13px;color:#526279}.rule-filters{display:grid;grid-template-columns:minmax(240px,2fr) repeat(3,minmax(140px,1fr));gap:0 16px}.filter-actions{grid-column:1/-1;display:flex;gap:8px;margin-bottom:20px}.filter-actions .el-button+.el-button{margin-left:0}.directory-meta{margin-bottom:16px}.rule-condition{color:#526279;line-height:1.7;font-size:13px;margin:6px 0}.rule-version{overflow-wrap:anywhere;font-size:12px}.directory-footer{margin-top:20px;min-height:38px}.rule-table a{color:#175fae}.rule-table .el-button{margin-left:12px}@media(max-width:1000px){.rule-filters{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.rule-filters{grid-template-columns:minmax(0,1fr)}.directory-heading{align-items:flex-start;flex-direction:column}}
</style>

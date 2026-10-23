<template>
  <el-form ref="form" :model="draft" :rules="rules" label-position="top" class="connector-form" @submit.prevent="submit">
    <div class="form-grid">
      <el-form-item label="名称" prop="name"><el-input v-model="draft.name" maxlength="200" /></el-form-item>
      <el-form-item label="负责人" prop="owner"><el-select v-model="draft.owner" filterable><el-option v-for="member in members" :key="member.user_id" :value="member.user_id" :label="memberLabelName(member)" /><template #footer><el-button v-if="moreMembers" text :disabled="busy" @click="emit('more-members')">加载更多成员</el-button></template></el-select></el-form-item>
      <el-form-item label="授权范围"><el-select v-model="draft.scope"><el-option label="当前企业" value="ENTERPRISE" /><el-option label="指定项目" value="PROJECT" /></el-select></el-form-item>
      <el-form-item label="同步目标项目"><span>{{ projectName }}</span></el-form-item>
    </div>
    <p class="form-note">授权范围用于登记连接业务资料。客户资料只同步至已明确授权的目标项目，每个项目分别核验权限。</p>
    <el-form-item label="连接与授权配置" prop="description"><el-input v-model="draft.description" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item>
    <div class="form-grid">
      <el-form-item label="配置有效期" prop="expires"><el-date-picker v-model="draft.expires" type="datetime" placeholder="选择配置到期时间" /></el-form-item>
      <el-form-item label="资料分级"><el-select v-model="draft.classification"><el-option v-for="(label,key) in classifications" :key="key" :value="key" :label="label" /></el-select></el-form-item>
    </div>
    <el-divider content-position="left">客户接入资料</el-divider>
    <p class="form-note">客户输入尚未提供时，可以保存准备草稿。缺少必要输入的版本无法批准、验证或同步。凭据仅填写受控引用。</p>
    <el-collapse v-model="sections">
      <el-collapse-item title="接口规格与版本" name="spec">
        <el-checkbox v-model="draft.specEnabled">已取得客户接口规格</el-checkbox>
        <template v-if="draft.specEnabled"><el-form-item label="接口规格版本"><el-input v-model="draft.specVersion" maxlength="200" /></el-form-item><el-form-item label="客户接口规格文档（JSON）"><el-input v-model="draft.specDocument" type="textarea" :rows="6" placeholder="按照客户协议提供完整规格对象" /></el-form-item></template>
      </el-collapse-item>
      <el-collapse-item title="账号映射" name="accounts">
        <el-checkbox v-model="draft.mappingEnabled">已明确客户账号与企业成员映射</el-checkbox>
        <template v-if="draft.mappingEnabled"><div v-for="(mapping,index) in draft.mappings" :key="index" class="mapping-row"><el-form-item label="客户账号标识"><el-input v-model="mapping.external_account_id" maxlength="200" /></el-form-item><el-form-item label="企业成员"><el-select v-model="mapping.user_id" filterable><el-option v-for="member in members" :key="member.user_id" :label="memberLabelName(member)" :value="member.user_id" /></el-select></el-form-item><el-button :disabled="busy" @click="draft.mappings.splice(index,1)">移除映射</el-button></div><el-button :disabled="busy" @click="draft.mappings.push({external_account_id:'',user_id:''})">添加账号映射</el-button></template>
      </el-collapse-item>
      <el-collapse-item title="资料归属与同步范围" name="ownership">
        <el-checkbox v-model="draft.ownershipEnabled">已明确客户资料归属及外部范围</el-checkbox>
        <template v-if="draft.ownershipEnabled"><el-form-item label="外部同步范围（JSON）"><el-input v-model="draft.externalScope" type="textarea" :rows="4" /></el-form-item><el-form-item label="文档归属规则（JSON）"><el-input v-model="draft.ownershipRules" type="textarea" :rows="4" /></el-form-item></template>
      </el-collapse-item>
      <el-collapse-item title="受控凭据引用" name="credentials">
        <el-checkbox v-model="draft.credentialEnabled">已登记客户系统受控凭据</el-checkbox>
        <div v-if="draft.credentialEnabled" class="form-grid"><el-form-item label="凭据引用标识"><el-input v-model="draft.credentialId" maxlength="36" /></el-form-item><el-form-item label="凭据固定版本"><el-input v-model="draft.credentialRevision" maxlength="200" /></el-form-item></div>
      </el-collapse-item>
    </el-collapse>
    <el-alert v-if="inputError" :title="inputError" type="error" :closable="false" show-icon />
    <div class="form-actions"><el-button :disabled="busy" @click="emit('cancel')">取消编辑</el-button><el-button type="primary" native-type="submit" :disabled="busy" :loading="busy">{{ pending ? '使用原请求保存连接' : '保存连接' }}</el-button></div>
  </el-form>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { connectorCreateIssues, connectorInput } from '@/api/medical/customer-connectors'
import { parse } from 'jsonc-parser'
import type { ParseError } from 'jsonc-parser'
import type { ConnectorConfiguration, ConnectorCreate, ConnectorDefinition, ConnectorPending } from '@/api/medical/customer-connectors'
import { memberLabelName } from '@/api/medical/member-labels'
import type { MemberLabel } from '@/api/medical/member-labels'
const props=defineProps<{projectId:string;projectName:string;ownerId:string;members:MemberLabel[];moreMembers:boolean;busy:boolean;source:ConnectorConfiguration|null;headRevision:string;pending:ConnectorPending|null}>()
const emit=defineEmits<{submit:[ConnectorCreate];cancel:[];'more-members':[]}>()
const form=ref<FormInstance>(),sections=ref<string[]>([]),inputError=ref(''),definition=props.source?.definition,profile=props.source?.business_profile?.profile
const identity=props.pending?.kind==='CREATE'?props.pending.create_identity:undefined
const connectorId=identity?.connector_id??definition?.connector_id??crypto.randomUUID()
const draft=reactive({name:profile?.name??'',owner:profile?.owner_user_id??props.ownerId,description:profile?.description??'',scope:profile?.scope_kind??'PROJECT',expires:new Date(identity?.expires_at??definition?.expires_at??Date.now()+86400000),classification:definition?.classification??'INTERNAL',specEnabled:!!definition?.interface_spec,specVersion:definition?.interface_spec?.version??'',specDocument:definition?.interface_spec?JSON.stringify(definition.interface_spec.document,null,2):'',mappingEnabled:!!definition?.account_mapping,mappings:definition?.account_mapping?definition.account_mapping.map(value=>({...value})):[],ownershipEnabled:!!definition?.ownership_scope,externalScope:definition?.ownership_scope?JSON.stringify(definition.ownership_scope.external_scope,null,2):'',ownershipRules:definition?.ownership_scope?JSON.stringify(definition.ownership_scope.document_ownership_rules,null,2):'',credentialEnabled:!!definition?.credential_reference_id,credentialId:definition?.credential_reference_id??'',credentialRevision:definition?.credential_revision??''})
const classifications={PUBLIC:'公开资料',INTERNAL:'企业内部资料',RESTRICTED:'受限资料',PERSONAL_HEALTH:'个人健康资料'}
const rules:FormRules={name:[{required:true,message:'请填写连接名称',trigger:'blur'}],owner:[{required:true,message:'请选择有效负责人',trigger:'change'}],description:[{required:true,message:'请填写连接与授权配置',trigger:'blur'}],expires:[{required:true,message:'请选择配置到期时间',trigger:'change'}]}
function object(text:string):Record<string,unknown>|null{const errors:ParseError[]=[],value:unknown=parse(text,errors,{disallowComments:true,allowTrailingComma:false});if(errors.length||value===null||typeof value!=='object'||Array.isArray(value)||Object.keys(value).length===0){inputError.value='客户规格、同步范围和归属规则需要完整、非空的 JSON 对象。';return null}return value as Record<string,unknown>}
async function submit(){
 if(props.busy||!form.value||!await form.value.validate(()=>{}))return
 inputError.value=''
 if(!props.members.some(member=>member.user_id===draft.owner)){inputError.value='负责人需要属于当前企业的有效成员。';return}
 const spec=draft.specEnabled?object(draft.specDocument):null,external=draft.ownershipEnabled?object(draft.externalScope):null,ownership=draft.ownershipEnabled?object(draft.ownershipRules):null;if(inputError.value)return
 const value:ConnectorCreate={schema_version:'1.0.0',profile:{name:draft.name.trim(),owner_user_id:draft.owner,description:draft.description.trim(),scope_kind:draft.scope,scope_project_id:draft.scope==='PROJECT'?props.projectId:null},definition:{schema_version:'1.0.0',connector_id:connectorId,project_id:props.projectId,expected_revision:identity?.expected_revision??props.headRevision,interface_spec:draft.specEnabled?{version:draft.specVersion,document:spec!}:null,account_mapping:draft.mappingEnabled?draft.mappings.map(value=>({...value})):null,ownership_scope:draft.ownershipEnabled?{project_id:props.projectId,external_scope:external!,document_ownership_rules:ownership!}:null,credential_reference_id:draft.credentialEnabled?draft.credentialId:null,credential_revision:draft.credentialEnabled?draft.credentialRevision:null,expires_at:draft.expires.toISOString(),classification:draft.classification as ConnectorDefinition['classification']}}
 inputError.value=connectorCreateIssues(value);if(inputError.value)return
 connectorInput('CREATE',value);emit('submit',value)
}
</script>
<style scoped>
.connector-form{padding-top:8px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 24px}.form-grid .el-select,.form-grid :deep(.el-date-editor){width:100%}.form-note{font-size:12px;color:#526279;line-height:1.8;margin:0 0 20px}.mapping-row{display:grid;grid-template-columns:1fr 1fr auto;align-items:center;gap:16px}.mapping-row>.el-button{margin-top:8px}.form-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:24px}.el-collapse .el-form-item{margin-top:16px}.el-checkbox{margin:8px 0}@media(max-width:650px){.form-grid,.mapping-row{grid-template-columns:1fr}}
</style>

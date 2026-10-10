<template>
  <el-form label-position="top" :disabled="disabled">
    <el-form-item label="资料归属">
      <el-select v-model="value.ownership" aria-label="资料归属" @change="value.session_id = null">
        <el-option label="项目资料" value="PROJECT" /><el-option label="企业资料" value="ENTERPRISE" /><el-option label="会话附件" value="SESSION" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="value.ownership === 'SESSION'" label="原会话标识"><el-input v-model="value.session_id" /></el-form-item>
    <el-form-item label="资料分类">
      <el-select v-model="value.classification" aria-label="资料分类">
        <el-option label="公开资料" value="PUBLIC" /><el-option label="企业内部" value="INTERNAL" />
        <el-option label="受限资料" value="RESTRICTED" /><el-option label="个人健康信息" value="PERSONAL_HEALTH" />
      </el-select>
    </el-form-item>
    <el-form-item label="本次版本意图">
      <el-radio-group v-model="value.version_intent" @change="value.source_id = null"><el-radio value="NEW_SOURCE">新增资料</el-radio><el-radio value="NEW_VERSION">新增版本</el-radio></el-radio-group>
    </el-form-item>
    <el-form-item v-if="value.version_intent === 'NEW_VERSION'" label="原资料标识"><el-input v-model="value.source_id" /></el-form-item>
    <p>名称相同且内容不同的文件，需要明确选择版本意图；新版本保留原资料内容。</p>
    <h3>来源记录</h3>
    <el-form-item v-for="field in provenanceFields" :key="field.key" :label="field.label" :required="field.required">
      <el-input :model-value="value.provenance[field.key] ?? ''" :placeholder="field.placeholder" :maxlength="4096"
        @update:model-value="value.provenance[field.key] = $event === '' && !field.required ? null : $event" />
    </el-form-item>
    <el-form-item label="来源状态证据标识">
      <el-select v-model="value.provenance.status_evidence" multiple filterable allow-create default-first-option placeholder="逐项输入现有证据标识" />
    </el-form-item>
    <el-form-item label="法律保存要求">
      <el-radio-group v-model="value.provenance.legal_hold"><el-radio :value="null">尚未确认</el-radio><el-radio :value="true">存在要求</el-radio><el-radio :value="false">确认没有要求</el-radio></el-radio-group>
    </el-form-item>
    <h3>使用授权声明</h3>
    <el-form-item label="使用目的编号" required><el-input v-model="value.rights_declaration.purpose" placeholder="填写实际授权目的编号，使用大写字母、数字和下划线" maxlength="128" /></el-form-item>
    <el-form-item label="许可说明" required><el-input v-model="value.rights_declaration.license" type="textarea" maxlength="4096" /></el-form-item>
    <el-form-item label="获准操作">
      <el-checkbox-group v-model="value.rights_declaration.allowed_actions">
        <el-checkbox value="READ">读取</el-checkbox><el-checkbox value="STORE">保存</el-checkbox><el-checkbox value="ENTERPRISE_USE">企业使用</el-checkbox>
        <el-checkbox value="DISTRIBUTE">分发</el-checkbox><el-checkbox value="EXTERNAL_PROCESS">外部处理</el-checkbox>
      </el-checkbox-group>
    </el-form-item>
    <el-form-item label="获准服务商"><el-select v-model="value.rights_declaration.providers" multiple filterable allow-create default-first-option placeholder="逐项填写实际许可中的服务商" /></el-form-item>
    <el-form-item label="获准地区"><el-select v-model="value.rights_declaration.regions" multiple filterable allow-create default-first-option placeholder="逐项填写实际许可中的地区" /></el-form-item>
    <el-form-item label="授权开始时间（UTC）"><el-input :model-value="value.rights_declaration.valid_from ?? ''" :placeholder="utcPlaceholder" @update:model-value="value.rights_declaration.valid_from = $event || null" /></el-form-item>
    <el-form-item label="授权结束时间（UTC）"><el-input :model-value="value.rights_declaration.valid_to ?? ''" :placeholder="utcPlaceholder" @update:model-value="value.rights_declaration.valid_to = $event || null" /></el-form-item>
    <el-form-item label="授权证据标识"><el-select v-model="value.rights_declaration.authorization_evidence" multiple filterable allow-create default-first-option placeholder="逐项输入现有证据标识" /></el-form-item>
    <p>时间使用 UTC 格式；未确认的可选字段保持空白。来源声明仍需服务器核查和人工核准。</p>
  </el-form>
</template>

<script setup lang="ts">
import type { SourceProvenance, SourceUploadCreate } from '@/api/medical/source-uploads'
defineProps<{ value: SourceUploadCreate; disabled: boolean }>()
type TextKey = Exclude<keyof SourceProvenance, 'status_evidence' | 'legal_hold'>
const utcPlaceholder = 'YYYY-MM-DDTHH:mm:ssZ'
const provenanceFields: { key: TextKey; label: string; required?: boolean; placeholder?: string }[] = [
  { key: 'owner', label: '资料所有者', required: true }, { key: 'publisher', label: '发布方' },
  { key: 'maintainer_id', label: '维护人员标识', required: true }, { key: 'original_url', label: '原始地址' },
  { key: 'final_url', label: '最终地址' }, { key: 'publisher_version', label: '发布方版本' },
  { key: 'published_at', label: '发布时间（UTC）', placeholder: utcPlaceholder },
  { key: 'issued_on', label: '颁布日期', placeholder: 'YYYY-MM-DD' }, { key: 'effective_from', label: '实施日期', placeholder: 'YYYY-MM-DD' },
  { key: 'effective_to', label: '失效日期', placeholder: 'YYYY-MM-DD' }, { key: 'observed_status', label: '已观察到的来源状态' },
  { key: 'verified_at', label: '来源核查时间（UTC）', placeholder: utcPlaceholder },
  { key: 'acquired_at', label: '取得时间（UTC）', placeholder: utcPlaceholder }, { key: 'acquired_by', label: '取得人员标识' },
  { key: 'acquisition_method', label: '取得方式' }, { key: 'update_frequency', label: '更新频率' },
  { key: 'next_check_at', label: '下次核查时间（UTC）', placeholder: utcPlaceholder }, { key: 'retention_policy_id', label: '保存策略标识' }
]
</script>

<template>
  <section aria-label="模板全部依据业务配置" class="template-profile-controls">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="readable"><p>选择实际资料或检查规则的固定版本。已有字体、版式、许可及其他制品依据继续保留，保存与人工核准时重新核验全部依赖。</p><rule-basis-editor v-model="draft" label="模板固定依据" :disabled="disabled" :basis-names="names" /><el-button :disabled="disabled" @click="apply">确认模板全部依据</el-button></template>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import RuleBasisEditor from './rule-basis-editor.vue'
import { draftJsonIssue } from './template-collection-input'
import { readBoundedJson } from '@/api/medical/project-json'
import { schemaIssues } from '@/api/medical/protocol'
import type { Basis } from '@/api/medical/protocol'
const props=defineProps<{value:string;disabled:boolean;names:Record<string,string>}>(),emit=defineEmits<{changed:[string]}>()
const draft=ref<Basis[]>([]),error=ref(''),readable=ref(true)
watch(()=>props.value,text=>{error.value='';if(!text.trim()){draft.value=[];readable.value=true;return}const issue=draftJsonIssue(text);if(issue){error.value=issue;readable.value=false;return}const value=readBoundedJson(text);if(schemaIssues('urn:medical:templates:1.0.0#/$defs/Bases',value).length){error.value='全部依据未通过固定版本检查，请在高级区域修正。';readable.value=false;return}draft.value=value as Basis[];readable.value=true},{immediate:true})
function apply(){if(props.disabled||!readable.value)return;if(!draft.value.length||schemaIssues('urn:medical:templates:1.0.0#/$defs/Bases',draft.value).length){error.value='请选择至少一项实际固定依据，版本和摘要需要完整。';return}error.value='';emit('changed',JSON.stringify(draft.value,null,2))}
</script>
<style scoped>.template-profile-controls{padding:16px 0}.template-profile-controls p{font-size:12px;color:#526279;line-height:1.8}</style>

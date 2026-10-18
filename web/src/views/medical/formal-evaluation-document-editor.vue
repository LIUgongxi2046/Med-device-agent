<template>
 <section aria-label="结构化正式输入文稿">
  <p>在本页按字段编写或校验文稿。下载的是本地规范JSON文件；它尚不是企业SOURCE、已批准输入或评测证据。请完成真实上传、审批与权利流程后再选择登记。</p>
  <el-form-item label="文档类型"><el-input v-model="draft.document_type" maxlength="64" :disabled="disabled"/></el-form-item>
  <el-form-item label="文稿语言"><el-input v-model="draft.language" maxlength="64" placeholder="例如 zh-CN（由你明确填写）" :disabled="disabled"/></el-form-item>
  <section v-for="(section,index) in draft.sections" :key="index" class="draft-section">
   <el-form-item :label="'章节 '+(index+1)+' 标识'"><el-input v-model="section.section_id" maxlength="128" :disabled="disabled"/></el-form-item>
   <el-form-item label="章节标题"><el-input v-model="section.title" maxlength="256" :disabled="disabled"/></el-form-item>
   <el-form-item label="完整章节正文"><el-input v-model="section.text" type="textarea" :rows="5" :disabled="disabled"/></el-form-item>
   <el-button :disabled="disabled || draft.sections.length<=1" @click="draft.sections.splice(index,1)">移除此章节</el-button>
  </section>
  <el-button :disabled="disabled || draft.sections.length>=256" @click="draft.sections.push({section_id:'',title:'',text:''})">添加章节</el-button>
  <el-button :disabled="disabled" @click="check">校验本地文稿</el-button><el-button :disabled="disabled || !checked" @click="download">下载规范JSON待上传</el-button>
  <p v-if="message" role="status">{{ message }}</p>
 </section>
</template>
<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { formalInputCanonical, formalInputDocumentIssue } from '@/api/medical/formal-evaluation-input'
import type { FormalInputDocument } from '@/api/medical/formal-evaluation-input'
const props=defineProps<{disabled?:boolean}>(),draft=reactive<FormalInputDocument>({schema_version:'1.0.0',document_type:'',language:'',sections:[{section_id:'',title:'',text:''}]}),checked=ref(false),message=ref('')
watch(draft,()=>{checked.value=false;message.value=''},{deep:true,flush:'sync'})
function check(){if(props.disabled)return;try{const issue=formalInputDocumentIssue(draft);if(issue)throw Error(issue);const value=draft;checked.value=true;message.value='本地结构校验通过，规范UTF-8大小 '+new TextEncoder().encode(formalInputCanonical(value)).length+' 字节。未登记来源或评测成果。'}catch(e){checked.value=false;message.value=e instanceof Error?e.message:'文稿未通过校验'}}
function download(){if(props.disabled||!checked.value)return;const issue=formalInputDocumentIssue(draft);if(issue){checked.value=false;message.value=issue;return}const value=draft,url=URL.createObjectURL(new Blob([formalInputCanonical(value)],{type:'application/json;charset=utf-8'})),link=document.createElement('a');try{link.href=url;link.download='formal-evaluation-input.json';link.click()}finally{URL.revokeObjectURL(url)}}
</script>
<style scoped>.draft-section{border-top:1px solid #DCE3EC;padding-top:12px}.el-button{margin:8px 8px 8px 0}p{line-height:1.6}</style>

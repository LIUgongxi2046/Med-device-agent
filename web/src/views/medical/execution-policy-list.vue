<template>
 <main class="policy-page"><nav aria-label="位置"><router-link to="/agents">Agent 管理</router-link> / 执行策略</nav>
  <header><div><h1>执行策略</h1><p>查看固定运行范围、独立审批状态及缺失依赖。</p></div><router-link to="/agents/execution-policies/new"><el-button type="primary" :disabled="busy || !availability?.features.policy_authoring">登记执行策略</el-button></router-link></header>
  <session-panel/><el-alert type="info" :closable="false" title="策略批准与 Agent 发布是不同步骤" description="APPROVED 仅代表固定执行策略完成审批，不产生 Agent 发布证明、模型许可或 TASK 执行资格。" />
  <el-alert v-if="error" class="spaced" type="error" :title="error" :closable="false" role="alert"/>
  <section v-if="pending" class="card spaced"><h2>有原策略请求待核查</h2><p>请先读取原键与类型回执。目录结果不能代替原请求恢复证据。</p><router-link to="/agents/execution-policies/new">打开原请求恢复</router-link></section>
  <section class="card spaced" aria-label="执行策略目录"><div class="section-heading"><h2>固定版本目录</h2><el-button :disabled="busy" @click="restart">重新读取首页</el-button></div>
   <el-form label-position="top"><el-form-item label="查看范围（包含企业策略）"><el-select v-model="project" :disabled="busy" filterable aria-label="查看范围"><el-option value="" label="当前企业"/><el-option v-for="p in projects" :key="p.project_id" :value="p.project_id" :label="p.name"/></el-select><el-button v-if="projectCursor" link :disabled="busy" @click="loadProjects">读取更多项目</el-button></el-form-item></el-form>
   <p v-if="loading" role="status">正在读取当前可访问的策略…</p><p v-else-if="availability?.features.policy_authoring === false" role="status">当前部署未启用执行策略服务。</p><el-empty v-else-if="loaded && !records.length" :description="cursor ? '当前扫描页没有可访问记录，可继续下一页' : '当前身份没有可访问的策略'" />
   <div class="record-grid"><article v-for="v in records" :key="v.fixed.version_id" class="record"><div class="section-heading"><h3>{{ v.fixed.definition.bindings.map(b=>b.node_kind).join('、') }} 执行策略</h3><el-tag :type="v.status==='APPROVED'?'success':v.status==='DRAFT'?'warning':'info'">{{ v.status }}</el-tag></div><dl><dt>归属</dt><dd>{{ v.fixed.definition.project_id || '当前企业' }}</dd><dt>策略标识</dt><dd>{{ v.fixed.policy_id }}</dd><dt>固定版本</dt><dd>{{ v.fixed.version_id }}</dd><dt>当前修订</dt><dd>{{ v.revision }}</dd><dt>创建时间（UTC）</dt><dd>{{ v.fixed.created_at }}</dd><dt>来源 / 节点</dt><dd>{{ v.fixed.definition.source_selection.sources.length }} / {{ v.fixed.definition.bindings.length }}</dd></dl><p v-if="v.missing_input_reasons.length">当前缺项：{{ v.missing_input_reasons.join('、') }}</p><router-link :to="'/agents/execution-policies/'+v.fixed.version_id">打开固定详情与审批</router-link></article></div>
   <div class="actions"><el-button v-if="cursor" :disabled="busy || cursorExpired" @click="load(true)">读取下一页</el-button><span v-if="cursorExpired" role="status">分页上下文已过期，请明确重新读取首页。</span><span v-else-if="loaded && !cursor">已读取当前范围的末页。</span></div>
  </section>
 </main>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import SessionPanel from './session-panel.vue'
import { usePolicyPage } from '@/api/medical/use-execution-policy-page'
import { readPendingPolicy } from '@/api/medical/execution-policy-domain'
import type { PendingPolicy, PolicyAvailability, PolicyView } from '@/api/medical/execution-policy-domain'
import { queryPolicies, readPolicyAvailability } from '@/api/medical/execution-policies'
import { policyProjectCandidates } from '@/api/medical/execution-policy-candidates'
import type { Project } from '@/api/medical/protocol'
const availability=ref<PolicyAvailability|null>(null),pending=ref<PendingPolicy|null>(null),records=ref<PolicyView[]>([]),cursor=ref<string|null>(null),cursorExpired=ref(false),loaded=ref(false),project=ref(''),projects=ref<Project[]>([]),projectCursor=ref<string|null>(null)
let resetting=false
const page=usePolicyPage(reset,bootstrap),{busy,loading,error,reader}=page
function reset(){resetting=true;availability.value=null;pending.value=null;records.value=[];cursor.value=null;cursorExpired.value=false;loaded.value=false;project.value='';projects.value=[];projectCursor.value=null;resetting=false}
async function bootstrap(){await page.run(async(signal,current)=>{pending.value=readPendingPolicy(reader());const a=await readPolicyAvailability(reader,signal);if(!current())return;availability.value=a;if(!a.features.policy_authoring)return;const p=await policyProjectCandidates(reader,null,signal);if(!current())return;projects.value=p.projects;projectCursor.value=p.next_after;const result=await queryPolicies(reader,null,null,signal);if(current()){records.value=result.items;cursor.value=result.next_cursor;loaded.value=true}})}
async function load(next:boolean){if(next&&(!cursor.value||cursorExpired.value))return;const scope=project.value,after=next?cursor.value:null;await page.run(async(signal,current)=>{try{const result=await queryPolicies(reader,scope||null,after,signal);if(!current()||scope!==project.value)return;const items=next?[...records.value,...result.items]:result.items;if(new Set(items.map(x=>x.fixed.version_id)).size!==items.length)throw new Error('策略目录已变化，请重新读取首页');records.value=items;cursor.value=result.next_cursor;loaded.value=true;cursorExpired.value=false}catch(failure){if(current()&&(failure as {code?:string}).code==='CURSOR_EXPIRED')cursorExpired.value=true;throw failure}})}
async function loadProjects(){const after=projectCursor.value;if(!after)return;await page.run(async(signal,current)=>{const p=await policyProjectCandidates(reader,after,signal);if(current()){const items=[...projects.value,...p.projects];if(new Set(items.map(x=>x.project_id)).size!==items.length)throw new Error('项目目录已变化，请重新读取首页');projects.value=items;projectCursor.value=p.next_after}})}
function restart(){if(busy.value)return;page.clear();void bootstrap()}
watch(project,()=>{if(resetting)return;records.value=[];cursor.value=null;cursorExpired.value=false;loaded.value=false;if(availability.value?.features.policy_authoring)void load(false)},{flush:'sync'})
page.start()
</script>
<style scoped src="./execution-policy.css"></style>

<template>
  <main class="medical-page">
    <h1>网络候选</h1>
    <el-alert title="网络候选工作区尚未接入公开接口" type="info" :closable="false" description="目前无法读取候选来源、权利状态或转存结果。网络候选需经来源核验、权利审查及人工核准，才能用于任务。" />
    <el-empty description="候选检索与转存功能待接入">
      <el-button disabled>检索网络候选</el-button><el-button disabled>转存待核验资料</el-button>
    </el-empty>
    <router-link :to="{ path: '/library', query: route.query.project_id ? { project_id: route.query.project_id } : {} }">返回资料库</router-link>
  </main>
</template>
<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

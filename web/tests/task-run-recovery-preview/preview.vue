<template><main><p class="banner">模拟数据 · 未连接后端 · 不会启动、恢复、重试或取消任何运行</p><h1>运行恢复核查</h1><p>使用实际 Vue 组件，展示服务端契约已有字段与尚未接入的恢复边界。</p><nav aria-label="模拟场景"><button v-for="item in scenarios" :key="item.value" @click="scenario=item.value">{{ item.label }}</button><button @click="canWrite=!canWrite">{{ canWrite ? '切换只读' : '允许本地选择' }}</button><button @click="canRead=!canRead">{{ canRead ? '模拟撤销读取许可' : '恢复读取许可' }}</button><button @click="inflight=!inflight">{{ inflight ? '移除模拟在途调用' : '模拟在途调用' }}</button></nav><TaskRunRecovery :snapshot="snapshot" :active="true" :can-read="canRead" :can-write="canWrite" @refresh="reads++"/><p role="status">模拟刷新点击次数：{{ reads }}。不发起网络请求。</p></main></template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import TaskRunRecovery from '../../src/views/medical/task-run-recovery.vue'
import type { RunView } from '../../src/api/medical/runs'
import { fixture } from './fixtures'
const scenarios = [{ value: 'FAILED', label: '失败' }, { value: 'CANCELLED', label: '停止 / 中断' }, { value: 'WAITING_INPUT', label: '等待输入' }, { value: 'FUTURE_STATUS', label: '未知状态' }, { value: 'RUNNING', label: '执行中' }, { value: 'EMPTY', label: '未读取' }]
const scenario = ref('FAILED'), canRead = ref(true), canWrite = ref(true), inflight = ref(false), reads = ref(0)
const snapshot = computed(() => {
 if (scenario.value === 'EMPTY') return null
 // Deliberately malformed status tests a future/unsupported response; never a production DTO extension.
 const run = fixture(scenario.value as RunView['status'])
 if (inflight.value) run.inflight_invocations = [{ invocation_id: '模拟调用-01', step_id: run.steps[0]!.step_id, action_id: '模拟动作', status: 'RUNNING', settlement_status: 'PENDING', currency: 'CNY', settled_amount: null, total_tokens: null, revision: '1' }]
 return run
})
</script>
<style>body{margin:0;background:#edf2f7;color:#243c50;font-family:system-ui,sans-serif}main{max-width:1050px;margin:auto;padding:28px}.banner{background:#fff1c7;border:1px solid #dbc17d;border-radius:8px;padding:14px;font-size:13px;font-weight:600}nav{display:flex;flex-wrap:wrap;gap:8px}nav button{background:white;border:1px solid #bfcfda;border-radius:7px;padding:10px;color:#385b77;cursor:pointer}@media(max-width:600px){main{padding:12px}h1{font-size:25px}}</style>

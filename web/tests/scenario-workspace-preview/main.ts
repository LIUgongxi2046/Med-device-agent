import { createApp, ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ScenarioPlanningWorkspace from '../../src/views/medical/ScenarioPlanningWorkspace.vue'
createApp({ components: { ScenarioPlanningWorkspace }, setup: () => ({ blocked: ref(false) }), template: `<main style="max-width:1440px;margin:auto;padding:24px;font-family:system-ui,sans-serif"><h1>场景需求工作台</h1><p>隔离交互预览 · 没有真实身份与业务数据 · 不连接服务</p><el-switch v-model="blocked" active-text="模拟访问不可用（清除本地输入）" /><scenario-planning-workspace :disabled="blocked" preview /></main>` }).use(ElementPlus).mount('#app')

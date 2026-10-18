import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ModelConfigurations from '../../src/views/medical/model-configurations.vue'
import useUserStore from '../../src/store/modules/user'
const router = createRouter({ history: createWebHashHistory(), routes: [
  { path: '/', name: 'ModelFixture', component: ModelConfigurations }, { path: '/away', component: { render: () => h('p', 'SIMULATION 下一页') } }
] })
const pinia = createPinia(); const app = createApp({ render: () => h(RouterView) })
app.use(pinia).use(router).use(ElementPlus)
const user = useUserStore(); let nextSession = null
user.readMedical = async () => { user.medicalSession = null; user.medicalSession = structuredClone(nextSession); return user.medicalSession }
window.modelFixture = { setSession: value => { nextSession = value; user.medicalSession = value }, setNextSession: value => { nextSession = value },
  session: () => JSON.parse(JSON.stringify(user.medicalSession)), navigate: path => router.push(path), errors: [], user }
app.config.errorHandler = error => { window.modelFixture.errors.push({ code: error.code ?? error.name, message: error.message }) }
app.mount('#app')

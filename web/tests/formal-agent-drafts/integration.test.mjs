import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createRequire} from 'node:module'
import vm from 'node:vm'
import {webRoot} from './helpers.mjs'
const require=createRequire(webRoot+'/package.json')
test('normal candidate route, management entrance and server page allowlist are integrated without replacing prior routes',()=>{
 const {createRouter,createMemoryHistory}=require('vue-router')
 const source=readFileSync(webRoot+'/src/router/index.js','utf8'),start=source.indexOf('export const medicalRoutes = '),end=source.indexOf('\n// 公共路由',start)
 const section=source.slice(start,end>start?end:source.indexOf('export const constantRoutes',start)).replace('export const medicalRoutes = ','const medicalRoutes = ').replace(/\(\) => import\([^)]*\)/g,'({render(){}})')
 const routes=vm.runInNewContext(section+'\nmedicalRoutes',{Layout:{render(){}}}),router=createRouter({history:createMemoryHistory(),routes})
 const entry=router.resolve('/agents/drafts');assert.equal(entry.name,'MedicalFormalAgentDrafts');assert.equal(entry.meta.medical,true);assert.equal(entry.meta.noCache,true);assert.equal(entry.meta.activeMenu,'/agents')
 assert.equal(router.resolve('/agents/capabilities').name,'MedicalFormalCapabilities');assert.equal(router.resolve('/agents/execution-policies').name,'MedicalExecutionPolicies');assert.equal(router.resolve('/agents/simulation-preparation').name,'MedicalSimulationPreparation')
 const server=readFileSync(webRoot+'/../business/medical-identity/src/main/java/com/ruoyi/medical/identity/FrameworkFrontendConfiguration.java','utf8');assert.ok(server.includes('"/agents/drafts"'))
 const parent=readFileSync(webRoot+'/src/views/medical/agents.vue','utf8');assert.ok(parent.includes('to="/agents/drafts"'));assert.ok(parent.includes('to="/agents/capabilities"'));assert.ok(parent.includes('登记不会发布或执行'))
})

import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require=createRequire(import.meta.url), Vue=require('vue'), {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const source=readFileSync(new URL('../../src/views/medical/project.vue',import.meta.url),'utf8')
const {descriptor}=parse(source), script=compileScript(descriptor,{id:'project-waiting'})
const code=script.content.replace(/^import .*\n/gm,'').replace('export default','return')
const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`
function harness(){
 const active=Vue.ref(true),calls=[],document={hidden:false}, user=Vue.reactive({medicalSession:{current_context:{}, grants:true},medicalWriting:false})
 const route=Vue.reactive({params:{projectId:id(1)}})
 const env={...Vue, watch(){}, onBeforeUnmount(){}, useRoute:()=>route,useRouter:()=>({push:r=>calls.push(r)}),useUserStore:()=>user,
  useMedicalRouteRead:()=>active,projectActionAllowed:s=>s?.grants===true,capabilityAllowed:()=>false,sourceUploadAllowed:()=>false,
  formatProjectJson:JSON.stringify,SessionPanel:{},readProject(){throw Error('unexpected read')},listWorkItems(){throw Error('unexpected read')},document,
  waitingTaskLocation:(p,w,r)=>({path:`/projects/${p}/tasks`,query:{waiting_item_id:w,run_id:r}}),
  readWaitingTaskContext:q=>({invalid:![q.waiting_item_id,q.run_id].every(x=>typeof x==='string'&&/^[0-9a-f-]{36}$/.test(x)),waitingItemId:q.waiting_item_id,runId:q.run_id})}
 const component=new Function(...Object.keys(env),code)(...Object.values(env));const state=component.setup({}, {expose(){}})
 state.project.value={project_id:id(1),allowed_actions:['project.task.read']};state.waiting.value={items:[{project_id:id(1),waiting_item_id:id(2),run_id:id(3)}]}
 return {state,calls,user,route,document,active,row:()=>state.waiting.value.items[0]}
}
test('project template compiles with per-item locator and preserved generic task entry',()=>{
 assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:'project.vue',id:'project-waiting',compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
 assert.match(source,/@click="locateWaiting\(scope.row\)"/)
})
test('explicit waiting action retains real project/waiting/run IDs without inventing task ID or reading more data',()=>{
 const h=harness();h.state.locateWaiting(h.row());assert.deepEqual(h.calls,[{path:`/projects/${id(1)}/tasks`,query:{waiting_item_id:id(2),run_id:id(3)}}])
})
test('stale rows, wrong project, invalid identity and current permission loss prevent navigation',()=>{
 for(const alter of [h=>h.user.medicalSession.grants=false,h=>h.user.medicalWriting=true,h=>h.document.hidden=true,h=>h.active.value=false,h=>h.state.waitingLoading.value=true,h=>h.route.params.projectId=id(9),h=>h.row().project_id=id(9),h=>h.row().run_id='bad',h=>h.state.project.value.allowed_actions=[]]){
  const h=harness();alter(h);h.state.locateWaiting(h.row());assert.equal(h.calls.length,0)
 }
 const h=harness();h.state.locateWaiting({...h.row()});assert.equal(h.calls.length,0)
 const row=h.row();h.state.waiting.value={items:[]};h.state.locateWaiting(row);assert.equal(h.calls.length,0)
})

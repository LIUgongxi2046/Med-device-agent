import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url), vue = require('vue'), ts = require('typescript')
const { parse, compileScript } = require('@vue/compiler-sfc')
function deferred() { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); return {promise,resolve,reject} }
function harness(name, enterprise = false) {
 const source=readFileSync(new URL('../../src/views/medical/'+name+'.vue',import.meta.url),'utf8')
 const code=ts.transpileModule(compileScript(parse(source).descriptor,{id:'test'}).content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText.replace(/^import .*;\n/gm,'').replace('export default','return')
 const user=vue.reactive({medicalSession:{user:{user_id:'actor'},current_context:{tenant_id:'tenant'}},medicalWriting:false})
 const route=vue.reactive({params:{projectId:'real-project'},query:enterprise?{ownership:'ENTERPRISE'}:{},fullPath:enterprise?'/p?ownership=ENTERPRISE':'/p'})
 const calls=[], scope=vue.effectScope(), document={hidden:false,addEventListener(){},removeEventListener(){}}
 let unmount
 const env={...vue,_defineComponent:vue.defineComponent,document,window:document,crypto:globalThis.crypto,onBeforeUnmount(fn){unmount=fn},useUserStore:()=>user,useRoute:()=>route,useMedicalRouteRead:()=>vue.ref(true),
 SessionPanel:{},FileUpload:{},SourceDeclaration:{},SourceUploadResult:{},schemaIssues:()=>[],sourceUploadAllowed:s=>!!s,projectActionAllowed:s=>!!s,
 sourceMediaTypes:{txt:'text/plain'},sourceMediaType:()=> 'text/plain',byteDigest:async()=> 'hash',fail:(code)=>{throw Error(code)},
 listProjects:(...args)=>{const d=deferred();calls.push({...d,args});return d.promise},uploadStatusLabels:{},transferStatusLabels:{},
 readUploadJournals:()=>[],prepareSourceUpload:async()=>({upload_id:null,requests:[]}),lookupUploadRequest:()=>Promise.reject(Error('unknown network result')),
 uploadIdentity:()=>({}),completeSourceUpload(){},createSourceUpload(){},prepareUploadPart(){},readSourceUpload(){},readSourceUploadResult(){},saveUploadJournal(){},sendUploadPart(){}
 }
 const component=new Function(...Object.keys(env),code)(...Object.values(env));const state=scope.run(()=>component.setup({}, {expose(){}}))
 return {state,user,route,calls,stop(){unmount?.();scope.stop()}}
}
const tick=async()=>{await vue.nextTick();await Promise.resolve()}
test('project payload unchanged; enterprise mode retains actual processing project and explicit ownership',()=>{
 for(const enterprise of [false,true]) {const h=harness('source-uploads',enterprise);const body=h.state.blank({name:'a.txt',size:1},'hash','actor','batch');assert.equal(body.project_id,'real-project');assert.equal(body.ownership,enterprise?'ENTERPRISE':'PROJECT');assert.equal(body.session_id,null);h.stop()}
})
test('unknown registration lookup preserves original pending journal and never fabricates success',async()=>{
 const h=harness('source-uploads',true);const file={name:'a.txt',size:1,arrayBuffer:async()=>new ArrayBuffer(1)}
 const item={id:'item',file,body:h.state.blank(file,'hash','actor','batch'),journal:null,confirmed:true,errors:[],sent:0}
 await assert.rejects(h.state.send(item),/unknown/);assert.ok(item.journal);assert.equal(item.journal.upload_id,null);assert.equal(h.state.busy.value,false);h.stop()
})
test('stale file digest cannot repopulate cleared context',async()=>{
 const h=harness('source-uploads',true), d=deferred();const pending=h.state.selectFile({name:'a.txt',size:1,arrayBuffer:()=>d.promise});h.user.medicalSession=null;d.resolve(new ArrayBuffer(1));await pending;assert.equal(h.state.items.value.length,0);h.stop()
})
test('enterprise chooser uses ACTIVE current page, rejects stale cursor and revoked late response',async()=>{
 const h=harness('enterprise-source-uploads');assert.deepEqual(h.calls[0].args[1],{status:'ACTIVE'});h.calls[0].resolve({projects:[],next_after:'cursor'});await tick();const next=h.state.read('cursor');assert.equal(h.calls[1].args[1].after,'cursor');h.calls[1].reject(Error('expired'));await next;await h.state.read('cursor');assert.equal(h.calls.length,2);assert.ok(h.state.error.value);const pending=h.state.read();h.user.medicalSession=null;h.calls[2].resolve({projects:[{project_id:'late'}],next_after:null});await pending;assert.equal(h.state.listing.value,null);assert.equal(h.calls[2].args[2].aborted,true);h.stop()
})

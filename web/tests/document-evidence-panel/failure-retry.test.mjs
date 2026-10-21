import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import * as Vue from 'vue'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
const filename=new URL('../../src/views/medical/document-evidence-panel.vue',import.meta.url).pathname
const source=readFileSync(filename,'utf8'),{descriptor}=parse(source,{filename})
const compiled=compileScript(descriptor,{id:'evidence-retry'})
const code=ts.transpileModule(compiled.content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText
const link=()=>({evidence_link_id:'link',evidence:{tenant_id:'tenant',source_id:'source',source_version:'version',parse_version:'parse',chunk_id:'chunk',quote_hash:'hash',locator:{page:1}}})
const governance=()=>({source_id:'source',reviews:[{parse_version:'parse'}],rights_purpose:'VERIFICATION'})
const content=()=>({representation:'PARSED',source_id:'source',items:[{quote_hash:'hash',locator:{page:1},raw_text:'verified'}]})
const tick=async()=>{for(let i=0;i<8;i++)await Promise.resolve()}
function mount(overrides={}) {
 const calls=[],props=Vue.reactive({session:{user:{user_id:'user'},current_context:{tenant_id:'tenant',auth_epoch:'1'}},links:[link()],active:true,documentRevision:'revision'})
 const modules={
  '@/api/medical/protocol':{fail:(code)=>{throw Error(code)}},
  '@/api/medical/document-body':{canonicalDocumentValue:JSON.stringify,documentBytesDigest:async()=> 'locator-hash'},
  '@/api/medical/source-governance':{readSourceGovernance:async(...args)=>{calls.push(['governance',...args]);return (overrides.governance??governance)(...args)},readSourceContent:async(...args)=>{calls.push(['content',...args]);return (overrides.content??content)(...args)}}}
 const exports={};vm.runInNewContext(code,{exports,require:name=>name==='vue'?Vue:modules[name],AbortController,TextEncoder,document:{hidden:false}})
 let state,exposed
 const Host=Vue.defineComponent({setup(){state=exports.default.setup(props,{expose:value=>{exposed=value}});return()=>null}})
 const renderer=Vue.createRenderer({createComment:()=>({}),insert(){},remove(){},parentNode(){},nextSibling(){}}),app=renderer.createApp(Host)
 app.mount({});return {props,calls,get state(){return state},get exposed(){return exposed},close:()=>app.unmount()}
}
test('failure and explicit accessible retry are in the compiled real template',()=>{
 assert.deepEqual(compileTemplate({source:descriptor.template.content,filename,id:'evidence-retry',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
 assert.match(source,/role="alert" aria-label="固定原文读取失败"/)
 assert.match(source,/<el-button[^>]+@click="retry">重新核查权限并读取原文/)
})
test('read error is safe and explicit retry reuses governance, digest and content flow without automatic transport',async()=>{
 let fail=true;const x=mount({governance:()=>{if(fail)throw Error('secret backend payload');return governance()}})
 assert.equal(x.calls.length,0);await x.exposed.read(x.props.links[0]);assert.equal(x.calls.length,1)
 assert.ok(x.state.readError.value);assert.ok(!x.state.readError.value.includes('secret'));assert.equal(x.state.loading.value,false);assert.equal(x.state.content.value,null)
 await tick();assert.equal(x.calls.length,1)
 fail=false;x.state.retry();await tick();assert.equal(x.calls.length,3);assert.equal(x.state.readError.value,'');assert.equal(x.state.content.value.raw_text,'verified')
 assert.equal(x.calls[2][3].locator_sha256,'locator-hash');x.close()
})
test('membership and tenant failures never invoke unauthorized transport',async()=>{
 const x=mount();await x.exposed.read({...link(),evidence_link_id:'forged'});assert.equal(x.calls.length,0)
 x.props.links[0].evidence.tenant_id='other';await x.exposed.read(x.props.links[0]);assert.equal(x.calls.length,0);assert.ok(x.state.readError.value)
 x.state.retry();await tick();assert.equal(x.calls.length,0);x.close()
})
test('source, quote and locator mismatches surface failure without content or navigation',async()=>{
 for(const override of [{governance:()=>({...governance(),source_id:'other'})},{content:()=>({...content(),items:[{...content().items[0],quote_hash:'other'}]})},{content:()=>({...content(),items:[{...content().items[0],locator:{page:2}}]})}]){
  const x=mount(override);await x.exposed.read(x.props.links[0]);assert.ok(x.state.readError.value);assert.equal(x.state.content.value,null);assert.equal(x.state.locationRoute.value,null);x.close()
 }
})
test('in-place authorization, link, revision and active changes clear failures and block stale retry',async()=>{
 for(const change of [x=>{x.props.session.current_context.auth_epoch='2'},x=>{x.props.links[0].evidence.quote_hash='changed'},x=>{x.props.documentRevision='new'},x=>{x.props.active=false}]){
  const x=mount({governance:()=>{throw Error('failure')}});await x.exposed.read(x.props.links[0]);assert.ok(x.state.readError.value)
  change(x);assert.equal(x.state.readError.value,'');assert.equal(x.state.selected.value,null);x.state.retry();await tick();assert.equal(x.calls.length,1);x.close()
 }
})
test('late failure and success after context change or close cannot restore panel',async()=>{
 for(const reject of [true,false]){
  let settle;const x=mount({content:()=>new Promise((resolve,fail)=>{settle=()=>reject?fail(Error('late')):resolve(content())})})
  const pending=x.exposed.read(x.props.links[0]);await tick();x.props.session.current_context.auth_epoch='2';settle();await pending
  assert.equal(x.state.readError.value,'');assert.equal(x.state.content.value,null);assert.equal(x.state.selected.value,null);assert.equal(x.state.loading.value,false);x.close()
 }
})
test('closing or unmounting pending read aborts it and never resurrects failure',async()=>{
 for(const unmount of [false,true]){
  let reject;const x=mount({governance:()=>new Promise((_,fail)=>{reject=fail})})
  const pending=x.exposed.read(x.props.links[0]);const signal=x.calls[0][3]
  if(unmount)x.close();else x.state.clear()
  assert.equal(signal.aborted,true);reject(Error('late'));await pending
  assert.equal(x.state.readError.value,'');assert.equal(x.state.selected.value,null);assert.equal(x.state.content.value,null)
  if(!unmount)x.close()
 }
})

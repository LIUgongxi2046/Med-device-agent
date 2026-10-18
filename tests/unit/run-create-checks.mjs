// Isolated schema/API checks with synthetic fixtures and an in-memory transport.
// These are not live-backend, browser, visual or end-to-end checks.
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { webcrypto } from 'node:crypto'
import { createRequire } from 'node:module'
import { build } from '../../web/node_modules/esbuild/lib/main.js'
import { parse, compileScript, compileTemplate } from '../../web/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js'

const root = path.resolve(import.meta.dirname, '../..')
const output = path.join(root, '.implementation-work/run-create-ui-unit')
const dependency = createRequire(path.join(root, 'web/package.json'))
await mkdir(output, { recursive: true })
await build({ entryPoints: [path.join(root, 'web/src/api/medical/run-create.ts')], bundle: true, platform: 'node', format: 'cjs',
  outfile: path.join(output, 'run-create.cjs'), plugins: [
    { name: 'isolated-transport', setup(bundler) {
      bundler.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'transport', namespace: 'synthetic' }))
      bundler.onLoad({ filter: /.*/, namespace: 'synthetic' }, () => ({ contents: `export default {
        get: (url, config) => globalThis.__runCreateTransport('GET', url, null, config),
        post: (url, body, config) => globalThis.__runCreateTransport('POST', url, body, config)
      }`, loader: 'js' }))
      bundler.onResolve({ filter: /^[a-z]/ }, args => ({ path: dependency.resolve(args.path), external: true }))
      bundler.onResolve({ filter: /\?raw$/ }, args => ({ path: path.resolve(args.resolveDir, args.path.replace(/\?raw$/, '')), namespace: 'raw' }))
      bundler.onLoad({ filter: /.*/, namespace: 'raw' }, async args => ({ contents: await readFile(args.path, 'utf8'), loader: 'text' }))
    } }
  ] })
Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
globalThis.window = new EventTarget()
const storage = new Map()
globalThis.sessionStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
const api = (await import(pathToFileURL(path.join(output, 'run-create.cjs')))).default
const id = () => crypto.randomUUID()
const tenant=id(),actor=id(),project=id(),task=id(),budgetId=id(),budgetRevision=id(),runId=id(),operationId=id()
const context={tenant_id:tenant,context_revision:'7',auth_epoch:'2',governance_epoch:'5'}
const session={user:{user_id:actor},current_context:context,csrf:{header_name:'X-CSRF-TOKEN',token:'ISOLATED_TEST_ONLY'},action_summary:['task.run','task.read','run.read'].map(action=>({action,project_id:project,resource_kind:'TASK',resource_id:task}))}
const current={revision:'9',detail:{task_id:task,project_id:project,task_revision:id(),plan_revision:id(),snapshot_id:id(),status:'READY',missing_requirements:[],active_run_ids:[]}}
assert.equal(api.runCreateReady(current),true)
for(const change of [{status:'DRAFT'},{plan_revision:null},{snapshot_id:null},{active_run_ids:[id()]},{missing_requirements:[{}]}]){const invalid={...current,detail:{...current.detail,...change}};assert.equal(api.runCreateReady(invalid),false);assert.throws(()=>api.runCreateInput(invalid,budgetRevision))}
assert.throws(()=>api.runCreateInput({...current,revision:'18446744073709551615'},budgetRevision))
const signal=new AbortController().signal
await assert.rejects(()=>api.prepareRunCreate({...session,action_summary:[]},current,budgetRevision,signal,budgetId),e=>e.code==='ACCESS_DENIED')
const p=await api.prepareRunCreate(session,current,budgetRevision,signal,budgetId)
assert.equal((await api.prepareRunCreate(session,current,budgetRevision,signal,budgetId)).key,p.key)
assert.equal(storage.size,1);assert.ok(![...storage.values()][0].includes('ISOLATED_TEST_ONLY'))
assert.equal(api.pendingRunCreate({...session,action_summary:[]},project,task).key,p.key)
await assert.rejects(()=>api.prepareRunCreate(session,{...current,revision:'10'},budgetRevision,signal,budgetId),e=>e.code==='IDEMPOTENCY_CONFLICT')
await assert.rejects(()=>api.prepareRunCreate(session,current,id(),signal,budgetId),e=>e.code==='IDEMPOTENCY_CONFLICT')
function response(data,rev=null,status=200){const requestId=id();return {status,headers:{'cache-control':'no-store','x-schema-version':'1.0.0','x-request-id':requestId},data:{schema_version:'1.0.0',request_id:requestId,resource_revision:rev,data}}}
const operation={operation_id:operationId,resource_id:runId,status:'PROCESSING',status_url:'/api/operations/'+operationId}
const receipt={operation,run_id:runId,task_id:task,task_revision:p.body.task_revision,plan_revision:p.body.plan_revision,snapshot_id:p.body.snapshot_id,budget_revision:budgetRevision,command_id:id(),status:'QUEUED',revision:'1'}
let calls=0;globalThis.__runCreateTransport=async(method,url,body,config)=>{calls++;assert.equal(method,'POST');assert.equal(url,'/api/tasks/'+task+'/runs');assert.deepEqual(body,p.body);assert.equal(config.headers['Idempotency-Key'],p.key);assert.equal(config.headers['X-Context-Revision'],'7');assert.equal(config.headers['X-CSRF-TOKEN'],'ISOLATED_TEST_ONLY');return response(receipt,'1',202)}
assert.equal((await api.submitRunCreate(session,p,signal)).status,'QUEUED');assert.equal(storage.size,1)
globalThis.__runCreateTransport=async()=>response({...receipt,plan_revision:id()},'1',202);await assert.rejects(()=>api.submitRunCreate(session,p,signal),e=>e.code==='CONTENT_MISMATCH');assert.equal(storage.size,1)
const run={run_id:runId,task_id:task,task_revision:p.body.task_revision,status:'QUEUED',reason_code:null,steps:[],waiting_items:[],inflight_invocations:[],budget:{budget_id:budgetId,budget_revision:budgetRevision,spent:'0',reserved:'0',limit:'100',currency:'USD'},started_at:null,ended_at:null,revision:'1',event_cursor:null}
let lookupStatus='FOUND',opStatus='PROCESSING',wrongBudget=false
globalThis.__runCreateTransport=async(method,url,body)=>{if(url==='/api/operation-lookups'){assert.equal(method,'POST');assert.deepEqual(body,{operation_id:'RN-01',idempotency_key:p.key});return response({lookup_status:lookupStatus,operation:lookupStatus==='FOUND'?{...operation,status:opStatus}:null})}assert.equal(method,'GET');assert.equal(url,'/api/runs/'+runId);return response(wrongBudget?{...run,budget:{...run.budget,budget_revision:id()}}:run,'1')}
assert.equal((await api.recoverRunCreate(session,p,signal,receipt)).run.status,'QUEUED');assert.equal(storage.size,1)
lookupStatus='NOT_FOUND';assert.equal((await api.recoverRunCreate(session,p,signal)).run,null);assert.equal(storage.size,1)
lookupStatus='EXPIRED';assert.equal((await api.recoverRunCreate(session,p,signal)).lookup.lookup_status,'EXPIRED');assert.equal(storage.size,1)
lookupStatus='FOUND';opStatus='COMPLETED';wrongBudget=true;await assert.rejects(()=>api.recoverRunCreate(session,p,signal),e=>e.code==='CONTENT_MISMATCH');assert.equal(storage.size,1)
wrongBudget=false;assert.equal((await api.recoverRunCreate(session,p,signal)).run.run_id,runId);assert.equal(storage.size,0)
for(const file of ['task-run-create.vue','task-detail.vue']){const source=await readFile(path.join(root,'web/src/views/medical',file),'utf8');const {descriptor,errors}=parse(source);assert.deepEqual(errors,[]);compileScript(descriptor,{id:file});const result=compileTemplate({source:descriptor.template.content,filename:file,id:file,compilerOptions:{expressionPlugins:['typescript']}});assert.deepEqual(result.errors,[])}
console.log('PASS: first-run readiness/CAS, authority, exact idempotency metadata, 202 headers/receipt, NOT_FOUND/EXPIRED/PROCESSING retention, revoked-authority metadata retention, operation/run/budget binding, terminal verified cleanup, two Vue component compilations. Synthetic offline tests only.')

// Execute the actual component setup with controlled async dependencies, not source matching.
await build({ entryPoints:[path.join(root,'web/src/views/medical/task-run-create.vue')],bundle:true,platform:'node',format:'cjs',outfile:path.join(output,'component.cjs'),plugins:[{name:'component-isolated-async',setup(b){
 b.onResolve({filter:/^@\/utils\/request$/},()=>({path:'request',namespace:'component-request'}))
 b.onLoad({filter:/.*/,namespace:'component-request'},()=>({contents:'export default {get:()=>{throw Error("Unexpected component GET")},post:()=>{throw Error("Unexpected component POST")}}',loader:'js'}))
 b.onResolve({filter:/^@\/store\/modules\/user$/},()=>({path:'user',namespace:'component-mock'}))
 b.onResolve({filter:/^@\/api\/medical\/(run-create|budgets|task-detail)$/},args=>({path:args.path.split('/').at(-1),namespace:'component-mock'}))
 b.onLoad({filter:/.*/,namespace:'component-mock'},args=>{if(args.path==='user')return {contents:'export default ()=>globalThis.__runUser',loader:'js'};const names=args.path==='run-create'?['pendingRunCreate','prepareRunCreate','recoverRunCreate','runCreateInput','runCreateReady','submitRunCreate']:args.path==='budgets'?['budgetActionAllowed','listBudgets','readBudget']:['taskActionAllowed','readCurrentTask'];return {contents:names.map(name=>`export const ${name}=(...args)=>globalThis.__runComponentDeps.${name}(...args)`).join('\n'),loader:'js'}})
 b.onResolve({filter:/^@\//},args=>({path:path.join(root,'web/src',args.path.slice(2))+(path.extname(args.path)?'':'.ts')}))
 b.onResolve({filter:/^[a-z]/},args=>({path:dependency.resolve(args.path),external:true}))
 b.onResolve({filter:/\?raw$/},args=>({path:path.resolve(args.resolveDir,args.path.replace(/\?raw$/,'')),namespace:'raw'}))
 b.onLoad({filter:/.*/,namespace:'raw'},async args=>({contents:await readFile(args.path,'utf8'),loader:'text'}))
 b.onLoad({filter:/\.vue$/},async args=>{if(path.basename(args.path)!=='task-run-create.vue')return {contents:'export default {}',loader:'js'};const {descriptor}=parse(await readFile(args.path,'utf8'),{filename:args.path});return {contents:compileScript(descriptor,{id:'run-create-async'}).content,loader:'ts',resolveDir:path.dirname(args.path)}})
}}]})
const component=(await import(pathToFileURL(path.join(output,'component.cjs')))).default.default
const vue=dependency('vue');globalThis.document=new EventTarget();document.hidden=false
const renderer=vue.createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),setText(){},setElementText(){},parentNode:()=>null,nextSibling:()=>null,insert(){},remove(){},patchProp(){}})
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
function mountLaunch(){globalThis.__runUser=vue.reactive({medicalWriting:false});const app=renderer.createApp({...component,render:()=>null},{session:structuredClone(session),current:structuredClone(current),active:true,onAccepted:()=>{assert.equal(__runUser.medicalWriting,false);acceptedCount++}});app.config.warnHandler=()=>{};const vm=app.mount({});return {app,state:vm.$.setupState,props:vm.$.props}}
const account={budget_id:budgetId,budget_revision:budgetRevision,project_id:project,currency:'USD',spent:'0',reserved:'0',approval:{limits:{amount:'100',deadline_at:'2026-12-31T00:00:00Z',tokens:'1000',calls:'20'}}}
let currentRead=()=>Promise.resolve(structuredClone(current)),budgetRead=()=>Promise.resolve(structuredClone(account)),budgetList=()=>Promise.resolve({budgets:[structuredClone(account)],next_after:null}),acceptedCount=0
const originalRunFunctions=Object.fromEntries(['pendingRunCreate','prepareRunCreate','recoverRunCreate','runCreateInput','runCreateReady','submitRunCreate'].map(name=>[name,(...args)=>api[name](...args)]))
globalThis.__runComponentDeps={...originalRunFunctions,budgetActionAllowed:()=>true,listBudgets:(...a)=>budgetList(...a),readBudget:(...a)=>budgetRead(...a),readCurrentTask:(...a)=>currentRead(...a),taskActionAllowed:(s,p,t,action)=>s.action_summary.some(item=>item.action===action&&item.resource_id===t)}
let panel=mountLaunch();const b2={...account,budget_id:id(),budget_revision:id()};let pageNo=0
budgetList=async(_s,q)=>{pageNo++;assert.equal(q.after,pageNo===1?undefined:budgetId);return {budgets:pageNo===1?[account]:[b2],next_after:pageNo===1?budgetId:null}}
await panel.state.loadBudgets();assert.equal(panel.state.budgets.length,1);await panel.state.loadBudgets(panel.state.nextBudget);assert.equal(panel.state.budgets.length,2);assert.equal(panel.state.nextBudget,null)
panel.state.selectedBudget=budgetId;currentRead=async()=>({...current,revision:'10'});await panel.state.openConfirm(false);assert.equal(panel.state.confirming,false);assert.match(panel.state.error,/任务记录已变化/)
currentRead=async()=>structuredClone(current);budgetRead=async()=>({...account,budget_revision:id()});await panel.state.openConfirm(false);assert.equal(panel.state.confirming,false);assert.match(panel.state.error,/预算批准已更新/)
budgetRead=async()=>structuredClone(account);await panel.state.openConfirm(false);assert.equal(panel.state.confirming,true);panel.state.acknowledged=true;panel.props.current={...structuredClone(current),revision:'10'};await vue.nextTick();assert.equal(panel.state.confirming,false);assert.equal(panel.state.confirmBody,null);await panel.state.submit();assert.equal(storage.size,0)
panel.app.unmount()
// An older budget request's finally must not clear the replacement generation's busy flag.
panel=mountLaunch();const oldRead=deferred(),newRead=deferred();let index=0;budgetList=()=>++index===1?oldRead.promise:newRead.promise
const oldLoading=panel.state.loadBudgets();assert.equal(panel.state.reading,true);panel.props.session={...structuredClone(session),current_context:{...context,context_revision:'8'}};await vue.nextTick();const newLoading=panel.state.loadBudgets();assert.equal(panel.state.reading,true);oldRead.resolve({budgets:[account],next_after:null});await oldLoading;assert.equal(panel.state.reading,true);assert.equal(panel.state.budgets.length,0);newRead.resolve({budgets:[b2],next_after:null});await newLoading;assert.equal(panel.state.reading,false);assert.equal(panel.state.budgets[0].budget_id,b2.budget_id);panel.app.unmount()
// Identity switch while confirmation reads are pending cannot open a stale dialog.
panel=mountLaunch();panel.state.budgets=[account];panel.state.selectedBudget=budgetId;const heldTask=deferred();currentRead=()=>heldTask.promise;const opening=panel.state.openConfirm(false);panel.props.session={...structuredClone(session),user:{user_id:id()}};await vue.nextTick();heldTask.resolve(structuredClone(current));await opening;assert.equal(panel.state.confirming,false);assert.equal(panel.state.confirmBody,null);panel.app.unmount()
// Unknown dispatch retains the original key. An external release/reacquire cannot be cleared by this write's finally.
currentRead=async()=>structuredClone(current);budgetRead=async()=>structuredClone(account);panel=mountLaunch();panel.state.budgets=[account];panel.state.selectedBudget=budgetId;await panel.state.openConfirm(false);panel.state.acknowledged=true
const heldPost=deferred();let dispatched=false;globalThis.__runCreateTransport=async(method,url,body,config)=>{assert.equal(method,'POST');assert.equal(url,'/api/tasks/'+task+'/runs');assert.equal(config.signal,undefined);dispatched=true;return heldPost.promise}
const sending=panel.state.submit();for(let i=0;i<200&&!dispatched;i++)await new Promise(r=>setTimeout(r,1));assert.equal(dispatched,true);const originalKey=panel.state.pending.key;__runUser.medicalWriting=false;__runUser.medicalWriting=true;heldPost.reject(new Error('Unknown transport outcome'));await sending;assert.equal(__runUser.medicalWriting,true);assert.equal(panel.state.pending.key,originalKey);assert.equal(panel.state.confirming,false);assert.equal(api.pendingRunCreate(session,project,task).key,originalKey);panel.app.unmount();__runUser.medicalWriting=false
// Navigate away after dispatch: no fake cancel, no replacement key, no stale UI result.
panel=mountLaunch();assert.equal(panel.state.pending.key,originalKey);assert.equal(panel.state.lookup,null);let retryPosts=0;globalThis.__runCreateTransport=async()=>{retryPosts++;throw Error('Must not submit without NOT_FOUND')};panel.state.confirmBody=p.body;panel.state.confirmBudget=account;panel.state.acknowledged=true;await panel.state.submit();assert.equal(retryPosts,0);panel.app.unmount()
console.log('PASS: actual Vue setup async regression: task/budget changes, identity switch, two budget pages, stale finally ownership, unknown POST retention and blocked unqueried retry. No live browser or backend.')
// Confirm a successful dispatch notifies its parent only after releasing its own lock.
storage.clear();panel=mountLaunch();panel.state.budgets=[account];panel.state.selectedBudget=budgetId;await panel.state.openConfirm(false);panel.state.acknowledged=true
const successReceipt={...receipt,operation:{...operation,status:'PROCESSING'}}
globalThis.__runCreateTransport=async(method,url)=>{if(url==='/api/tasks/'+task+'/runs')return response(successReceipt,'1',202);if(url==='/api/operation-lookups')return response({lookup_status:'FOUND',operation:{...operation,status:'PROCESSING'}});if(url==='/api/runs/'+runId)return response(run,'1');throw Error('Unexpected transport')}
await panel.state.submit();assert.equal(acceptedCount,1);assert.equal(__runUser.medicalWriting,false);assert.equal(panel.state.confirmedRun.run_id,runId);assert.equal(storage.size,1);panel.app.unmount()
// An identity change after dispatch clears private UI but keeps the original operation discoverable.
storage.clear();panel=mountLaunch();panel.state.budgets=[account];panel.state.selectedBudget=budgetId;await panel.state.openConfirm(false);panel.state.acknowledged=true
const latePost=deferred();let lateDispatched=false;globalThis.__runCreateTransport=async()=>{lateDispatched=true;return latePost.promise};const lateSending=panel.state.submit();for(let i=0;i<200&&!lateDispatched;i++)await new Promise(r=>setTimeout(r,1));assert.equal(lateDispatched,true);const retained=api.pendingRunCreate(session,project,task).key;panel.props.session={...structuredClone(session),user:{user_id:id()}};await vue.nextTick();latePost.resolve(response(successReceipt,'1',202));await lateSending;assert.equal(panel.state.pending,null);assert.equal(panel.state.confirmedRun,null);assert.equal(acceptedCount,1);assert.equal(api.pendingRunCreate(session,project,task).key,retained);panel.app.unmount()
console.log('PASS: actual setup accepted event follows lock release; post-dispatch identity change discards stale UI without losing the original key.')
// Write-lock ownership monitoring must survive component unmount until the POST settles.
storage.clear();panel=mountLaunch();panel.state.budgets=[account];panel.state.selectedBudget=budgetId;await panel.state.openConfirm(false);panel.state.acknowledged=true
const unmountedPost=deferred();let unmountedDispatch=false;globalThis.__runCreateTransport=async()=>{unmountedDispatch=true;return unmountedPost.promise};const unmountedSending=panel.state.submit();for(let i=0;i<200&&!unmountedDispatch;i++)await new Promise(r=>setTimeout(r,1));assert.equal(unmountedDispatch,true);panel.app.unmount();__runUser.medicalWriting=false;__runUser.medicalWriting=true;unmountedPost.reject(new Error('Unknown after unmount'));await unmountedSending;assert.equal(__runUser.medicalWriting,true);assert.equal(storage.size,1);__runUser.medicalWriting=false
console.log('PASS: detached ownership observer survives unmount; late write completion cannot release another operation lock.')
// A normal missing budget selection is a local form error, not a security/session failure.
storage.clear();panel=mountLaunch();let localFailures=0,localReads=0;const previousSession=panel.props.session;const countFailure=()=>localFailures++;window.addEventListener('medical-failure',countFailure);currentRead=async()=>{localReads++;return structuredClone(current)};await panel.state.openConfirm(false);assert.match(panel.state.error,/请选择实际预算账户/);assert.equal(localFailures,0);assert.equal(localReads,0);assert.equal(panel.props.session,previousSession);assert.equal(storage.size,0);panel.app.unmount();window.removeEventListener('medical-failure',countFailure)
console.log('PASS: actual RN-01 setup missing-budget preflight stays local, preserves session, and performs no read/write.')

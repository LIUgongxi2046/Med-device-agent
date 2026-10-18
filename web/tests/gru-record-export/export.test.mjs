import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
import { webcrypto } from 'node:crypto'
const root=process.env.MEDICAL_REPO||process.cwd(),base=resolve(root,'web/src')
const require=createRequire(resolve(root,'web/package.json')),ts=require('typescript'),Vue=require('vue')
const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc'),Ajv=require('ajv/dist/2020.js').default
const ajv=new Ajv({strict:false,allErrors:true});require('ajv-formats').default(ajv)
for(const p of ['contracts/resources/gru-record-export.schema.json','availability/gru-record-export-feature-availability.schema.json'])ajv.addSchema(JSON.parse(readFileSync(resolve(root,'business/medical-persistence/src/main/resources',p.split('/').at(-1)))))
const canonical=require('json-canonicalize').canonicalize,source=p=>readFileSync(resolve(base,p.replace('api/','api/medical/').replace('components/','views/medical/')),'utf8')
const compile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText.replace(/^import .*;\n/gm,'').replace(/export /g,'')
const byteDigest=async value=>Buffer.from(await webcrypto.subtle.digest('SHA-256',value)).toString('hex'),sourceDigest=value=>byteDigest(new TextEncoder().encode(canonical(value)))
const id=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`,hash='a'.repeat(64)
const session=()=>({user:{user_id:id(1)},current_context:{tenant_id:id(2),context_revision:'1',auth_epoch:'1',governance_epoch:'1'},csrf:{header_name:'X-CSRF-TOKEN',token:'test'},action_summary:[{action:'conversation.read',project_id:id(3),resource_kind:'DOCUMENT',resource_id:id(4)},{action:'conversation.export',project_id:id(3),resource_kind:'DOCUMENT',resource_id:id(4)}]})
const selected=()=>({conversation_id:id(5),document_id:id(4),project_id:id(3),document_revision:id(6),scope_snapshot_id:id(7),scope:{mode:'FIXED'},owner_id:id(1),revision:'2'})
async function basis(s=session()) {const messages=[{message_id:id(8),sequence:'2',content_sha256:hash}];return {schema_version:'1.0.0',tenant_id:id(2),project_id:id(3),conversation_id:id(5),owner_id:id(1),conversation_revision:'2',document_id:id(4),document_revision:id(6),body_sha256:hash,scope_snapshot_id:id(7),scope_sha256:await sourceDigest(selected().scope),mode:'RECORD_ONLY',execution_state:'NOT_EXECUTED',message_count:1,message_manifest:messages,message_manifest_sha256:await sourceDigest(messages),complete:true,next_cursor:null,current_context:s.current_context}}
class MedicalFailure extends Error {constructor(code,message){super(message);this.code=code}}
const response=data=>({status:200,headers:{},data:{resource_revision:'1',data}})
function adapter(raw){const calls=[],events=[],storage=new Map();let parses=0
 const fail=(code,message)=>{events.push(code);throw new MedicalFailure(code,message)}
 const validate=(ref,v)=>{if(!ajv.getSchema(ref)?.(v))fail('SCHEMA_INVALID',JSON.stringify(ajv.getSchema(ref)?.errors));return v}
 const env={request:Object.fromEntries(['get','post'].map(method=>[method,async(...args)=>{calls.push({method,args});return typeof raw==='function'?raw():raw}])),envelope:r=>{if(r.status>=400)fail(r.data.code,r.data.message);return r.data},fail,MedicalFailure,validate,validContext(){},canonical,sourceDigest,byteDigest,crypto:webcrypto,readBoundedJson:text=>{parses++;return JSON.parse(text)},readGruExportJson:JSON.parse,gruExportContentLimit:32*1024*1024,sessionStorage:{setItem:(k,v)=>storage.set(k,v),getItem:k=>storage.get(k),removeItem:k=>storage.delete(k)}}
 const api=new Function(...Object.keys(env),compile(source('api/gru-record-export.ts'))+'\nreturn {gruExportAllowed,gruExportDownloadAllowed,gruExportAvailability,readGruExportBasis,prepareGruExport,verifyGruExportPending,createGruExport,recoverGruExport,readGruExport,downloadGruExport,retainGruExport,restoreGruExport,gruExportReasonIssue,finishGruExport}')(...Object.values(env))
 return {...api,calls,events,storage,get parses(){return parses}}
}
async function receipt(p){return {schema_version:'1.0.0',export_id:id(10),operation_id:id(11),conversation_id:id(5),conversation_revision:'2',document_id:id(4),document_revision:id(6),message_manifest_sha256:p.body.basis.message_manifest_sha256,request_body_sha256:p.body_sha256,artifact_id:id(12),content_sha256:hash,size_bytes:'1',media_type:'application/json',format:'JSON',mode:'RECORD_ONLY',execution_state:'NOT_EXECUTED',created_at:'2026-10-18T10:00:00Z'}}
test('real complete basis verifies full manifest, scope and revision independently from current page',async()=>{
 const b=await basis(),a=adapter(response(b));assert.deepEqual(await a.readGruExportBasis(session(),selected(),new AbortController().signal),b)
 for(const change of [v=>v.message_manifest[0].sequence='3',v=>v.message_count=2,v=>v.scope_sha256='b'.repeat(64),v=>v.extra=true]){const v=structuredClone(b);change(v);await assert.rejects(adapter(response(v)).readGruExportBasis(session(),selected(),new AbortController().signal))}
 const stale=adapter(response(b));await assert.rejects(stale.readGruExportBasis(session(),{...selected(),revision:'1'},new AbortController().signal),e=>e.code==='ACTION_STALE');assert.deepEqual(stale.events,[])
})
test('export permission is independent of read/use and artifact permission is independent of receipt',async()=>{
 const a=adapter(),s=session();s.action_summary=s.action_summary.filter(v=>v.action!=='conversation.export');s.action_summary.push({...s.action_summary[0],action:'conversation.use'})
 assert.equal(a.gruExportAllowed(s,selected(),'conversation.export'),false);assert.equal(a.gruExportDownloadAllowed(s,id(3),{artifact_id:id(12)}),false)
 s.action_summary.push({action:'artifact.read',project_id:id(3),resource_kind:'ARTIFACT',resource_id:id(12)});assert.equal(a.gruExportDownloadAllowed(s,id(3),{artifact_id:id(12)}),true)
})
test('local reason validation has no security event; unknown write retains frozen body and key',async()=>{
 const a=adapter(()=>{throw new Error('unknown')}),s=session(),b=await basis(s)
 await assert.rejects(a.prepareGruExport(s,b,' '));assert.deepEqual(a.events,[])
 const p=await a.prepareGruExport(s,b,'保存用户记录');a.retainGruExport(s,p)
 await assert.rejects(a.createGruExport(s,p,new AbortController().signal));assert.deepEqual(a.restoreGruExport(s,id(5)),p);assert.equal(a.calls[0].args[2].signal,undefined)
 p.body.reason='changed';await assert.rejects(a.createGruExport(s,p,new AbortController().signal));assert.equal(a.calls.length,1)
})
test('new-session recovery uses full original ASCII header key and original body hash',async()=>{
 const s=session(),a=adapter(),p=await a.prepareGruExport(s,await basis(s),'导出记录');p.key='original/key?#12345';const {digest,...v}=p;p.digest=await sourceDigest(v)
 s.current_context={...s.current_context,context_revision:'2'}
 const r={schema_version:'1.0.0',conversation_id:id(5),idempotency_key:p.key,status:'PROCESSING',request_body_sha256:p.body_sha256,receipt:null,current_context:s.current_context}
 const b=adapter(response(r));assert.equal((await b.recoverGruExport(s,p,new AbortController().signal)).status,'PROCESSING');assert.equal(b.calls[0].args[0],'/api/gru-conversations/'+id(5)+'/export-operations');assert.equal(b.calls[0].args[1].headers['Idempotency-Key'],p.key)
 r.request_body_sha256=hash;await assert.rejects(b.recoverGruExport(s,p,new AbortController().signal))
})
async function downloadFixture(){const s=session(),a=adapter(),p=await a.prepareGruExport(s,await basis(s),'导出记录'),r=await receipt(p);const {current_context,...b}=p.body.basis;const doc={...b,purpose:'GRU_RECORD_EXPORT',format:'JSON',messages:[{message_id:id(8),sequence:'2',text:'用户实际记录',intent:'CHECK',execution_state:'NOT_EXECUTED',created_at:'2026-10-18T10:00:00Z',content_sha256:hash}]};const bytes=new TextEncoder().encode(canonical(doc));r.size_bytes=String(bytes.length);r.content_sha256=await byteDigest(bytes);const raw={status:200,data:bytes.buffer,headers:{'cache-control':'no-store','x-schema-version':'1.0.0','x-resource-revision':'1','x-content-type-options':'nosniff','content-type':'application/json','content-length':r.size_bytes,'x-content-sha256':r.content_sha256,'x-request-id':id(15)}};return {s,p,r,doc,raw}}
test('download returns only verified real bytes with complete canonical document and fixed manifest',async()=>{
 const {s,p,r,raw}=await downloadFixture(),a=adapter(raw);const blob=await a.downloadGruExport(s,p,r,new AbortController().signal);assert.equal(blob.size,Number(r.size_bytes));assert.equal(await byteDigest(await blob.arrayBuffer()),r.content_sha256)
 for(const mutate of [v=>v.headers['content-length']='1',v=>v.headers['x-content-sha256']=hash,v=>v.headers['x-content-type-options']='unsafe',v=>v.data=new TextEncoder().encode('bad').buffer]){const copy=structuredClone(raw);mutate(copy);await assert.rejects(adapter(copy).downloadGruExport(s,p,r,new AbortController().signal))}
})
test('private source payload and assistant output are rejected even with matching tampered file hash',async()=>{
 for(const mutate of [d=>d.private_scope={secret:'not allowed'},d=>d.messages[0].role='ASSISTANT',d=>d.messages[0].sequence='3']){const {s,p,r,doc,raw}=await downloadFixture();mutate(doc);const bytes=new TextEncoder().encode(canonical(doc));raw.data=bytes.buffer;r.size_bytes=String(bytes.length);r.content_sha256=await byteDigest(bytes);raw.headers['content-length']=r.size_bytes;raw.headers['x-content-sha256']=r.content_sha256;await assert.rejects(adapter(raw).downloadGruExport(s,p,r,new AbortController().signal))}
})
test('client capacity refuses before request or JSON parse without global failure',async()=>{
 const {s,p,r}=await downloadFixture(),a=adapter();r.size_bytes='33554433';await assert.rejects(a.downloadGruExport(s,p,r,new AbortController().signal),e=>e.code==='CONTEXT_LIMIT');assert.equal(a.calls.length,0);assert.deepEqual(a.events,[]);assert.equal(a.parses,0)
})
const tick=async()=>{for(let i=0;i<6;i++){await Promise.resolve();await Vue.nextTick()}}
const defer=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject}}
const componentText=source('components/gru-record-export.vue')
const componentCode=ts.transpileModule(compileScript(parse(componentText).descriptor,{id:'gru-export'}).content,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText.replace(/^import .*;\n/gm,'').replace('export default','return')
function mount(){const props=Vue.reactive({session:session(),selected:selected(),active:true,disabled:false}),user=Vue.reactive({medicalWriting:false}),calls={availability:[],basis:[],prepare:[],create:[],recover:[],view:[],download:[]},retained=[],revoked=[],created=[],clicks=[];let stop
 const api=kind=>(...args)=>{const d=defer();calls[kind].push({...d,args});return d.promise}
 const document={hidden:false,addEventListener(){},removeEventListener(){},body:{appendChild(){}},createElement:()=>({click(){clicks.push(true)},remove(){}})}
 const env={...Vue,_defineComponent:Vue.defineComponent,useUserStore:()=>user,canonical,document,URL:{createObjectURL:b=>{created.push(b);return 'blob:verified'},revokeObjectURL:u=>revoked.push(u)},setTimeout:()=>1,clearTimeout(){},gruExportAllowed:(_s,_v,action)=>props.session.action_summary.some(a=>a.action===action),gruExportDownloadAllowed:()=>props.session.action_summary.some(a=>a.action==='artifact.read'),gruExportReasonIssue:r=>r.trim()? '':'填写理由',gruExportAvailability:api('availability'),readGruExportBasis:api('basis'),prepareGruExport:api('prepare'),verifyGruExportPending:async()=>{},createGruExport:api('create'),recoverGruExport:api('recover'),readGruExport:api('view'),downloadGruExport:api('download'),retainGruExport:(_s,p)=>retained.push(p),restoreGruExport:()=>null,finishGruExport:async()=>{},onBeforeUnmount:fn=>{stop=fn}}
 const component=new Function(...Object.keys(env),componentCode)(...Object.values(env)),scope=Vue.effectScope(),vm=scope.run(()=>component.setup(props,{expose(){}}));return {props,user,calls,vm,retained,revoked,created,clicks,stop:()=>{stop();scope.stop()}}
}
async function ready(h){h.calls.availability[0].resolve(true);await tick()}
async function start(h){await ready(h);const load=h.vm.loadBasis();await tick();const b=await basis(h.props.session);h.calls.basis[0].resolve(b);await load;h.vm.reason.value='导出理由';h.vm.confirmed.value=true;const work=h.vm.create();await tick();const p=await adapter().prepareGruExport(h.props.session,b,'导出理由');h.calls.prepare[0].resolve(p);await tick();return {work,p}}
test('actual setup requires purpose confirmation and local reason before any write',async()=>{
 const h=mount();await ready(h);h.vm.basis.value=await basis(h.props.session);await h.vm.create();assert.equal(h.calls.prepare.length,0);h.vm.confirmed.value=true;await h.vm.create();assert.equal(h.calls.prepare.length,0);assert.match(h.vm.error.value,/理由/);h.stop()
})
test('actual setup persists original before POST and keeps it on unknown result',async()=>{
 const h=mount(),{work,p}=await start(h);assert.equal(h.retained[0],p);assert.equal(h.user.medicalWriting,true);h.calls.create[0].reject(new Error('unknown'));await work;assert.equal(h.vm.pending.value,p);assert.equal(h.user.medicalWriting,false);h.stop()
})
test('receipt does not enable download without independent artifact permission',async()=>{
 const h=mount(),{work,p}=await start(h);h.calls.create[0].resolve(await receipt(p));await work;assert.equal(h.vm.canDownload.value,false);await h.vm.download();assert.equal(h.calls.view.length,0);h.stop()
})
test('verified download creates URL only after bytes result and revokes it on scope change',async()=>{
 const h=mount();h.props.session.action_summary.push({action:'artifact.read'});await tick();h.calls.availability.at(-1).resolve(true);await tick()
 const load=h.vm.loadBasis();await tick();h.calls.basis[0].resolve(await basis(h.props.session));await load;h.vm.confirmed.value=true;h.vm.reason.value='导出';const work=h.vm.create();await tick();const p=await adapter().prepareGruExport(h.props.session,h.vm.basis.value,'导出');h.calls.prepare[0].resolve(p);await tick();h.calls.create[0].resolve(await receipt(p));await work
 const download=h.vm.download();await tick();h.calls.view[0].resolve({});await tick();assert.equal(h.created.length,0);h.calls.download[0].resolve(new Blob(['verified']));await download;assert.equal(h.created.length,1);assert.equal(h.clicks.length,1)
 h.props.active=false;await tick();assert.deepEqual(h.revoked,['blob:verified']);assert.equal(h.vm.receipt.value,null);h.stop()
})
test('identity transition retains stored original and late finally cannot clear newer busy or another lock',async()=>{
 const h=mount(),{work}=await start(h);h.props.session.current_context.context_revision='2';await tick();h.user.medicalWriting=false;h.user.medicalWriting=true;h.calls.create[0].reject(new Error('late'));await work;assert.equal(h.retained.length,1);assert.equal(h.vm.pending.value,null);assert.equal(h.vm.loading.value,true);assert.equal(h.user.medicalWriting,true);h.stop()
})
test('actual template compiles, no source export or draft mutation route is exposed',()=>{assert.deepEqual(compileTemplate({source:parse(componentText).descriptor.template.content,filename:'gru-record-export.vue',id:'gru-export'}).errors,[]);assert.doesNotMatch(componentText,/v-html|replaceBaseline|readSavedDocumentRevision|JSON\.stringify\(.*messages/);assert.match(componentText,/RECORD_ONLY \/ NOT_EXECUTED/)})
test('zero-message basis is true empty complete prefix, not an invented zero from missing permission',async()=>{
 const b=await basis();b.message_manifest=[];b.message_count=0;b.conversation_revision='1';b.message_manifest_sha256=await sourceDigest([])
 assert.equal((await adapter(response(b)).readGruExportBasis(session(),{...selected(),revision:'1'},new AbortController().signal)).message_count,0)
 await assert.rejects(adapter({status:403,data:{code:'ACCESS_DENIED',message:'denied'}}).readGruExportBasis(session(),selected(),new AbortController().signal))
})
test('NOT_FOUND recovery keeps exact original record and changed selected page never reconstructs body',async()=>{
 const h=mount(),{work,p}=await start(h);h.calls.create[0].reject(new Error('unknown'));await work
 const recover=h.vm.recover();await tick();h.calls.recover[0].resolve({status:'NOT_FOUND',receipt:null});await recover
 assert.equal(h.vm.pending.value,p);assert.equal(h.retained[0],p);assert.equal(h.calls.create.length,1);h.stop()
})
test('local download capacity failure retains receipt/pending and creates no ObjectURL',async()=>{
 const h=mount(),{work,p}=await start(h);h.calls.create[0].resolve(await receipt(p));await work
 // Change only the direct permission predicate fixture, without an identity event or fake backend success.
 h.props.session.action_summary.push({action:'artifact.read'});await tick();h.calls.availability.at(-1).resolve(true);await tick()
 h.vm.pending.value=p;h.vm.receipt.value=await receipt(p)
 const download=h.vm.download();await tick();h.calls.view[0].resolve({});await tick();h.calls.download[0].reject(new MedicalFailure('CONTEXT_LIMIT','客户端容量不足'));await download
 assert.equal(h.vm.pending.value,p);assert.ok(h.vm.receipt.value);assert.equal(h.created.length,0);assert.match(h.vm.error.value,/容量/);h.stop()
})
test('deactivation during download prevents late blob URL and unmount revokes existing URL',async()=>{
 const h=mount();await ready(h);const p=await adapter().prepareGruExport(h.props.session,await basis(h.props.session),'导出');h.props.session.action_summary.push({action:'artifact.read'});await tick();h.calls.availability.at(-1).resolve(true);await tick();h.vm.pending.value=p;h.vm.receipt.value=await receipt(p)
 const downloading=h.vm.download();await tick();h.calls.view[0].resolve({});await tick();h.props.active=false;await tick();h.calls.download[0].resolve(new Blob(['late']));await downloading
 assert.equal(h.created.length,0);h.stop()
})

test('only explicit acknowledged completed receipt archives original before releasing pending',async()=>{
 const a=adapter(),s=session(),p=await a.prepareGruExport(s,await basis(s),'导出');a.retainGruExport(s,p);const r=await receipt(p)
 await a.finishGruExport(s,p,r);assert.equal(a.restoreGruExport(s,id(5)),null);assert.equal([...a.storage.keys()].some(k=>k.endsWith(':completed:'+r.export_id)),true)
 const h=mount(),{work}=await start(h);h.calls.create[0].reject(new Error('unknown'));await work;await h.vm.finish();assert.ok(h.vm.pending.value);h.stop()
})

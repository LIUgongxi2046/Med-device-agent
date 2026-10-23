import assert from 'node:assert/strict'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083',privatePath=new URL('.implementation-work/runtime/project-profile-save-operation.json',root)
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const input=JSON.parse(readFileSync(new URL('.implementation-work/runtime/project-directory-workspace-input.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext(),page=await context.newPage()
page.setDefaultTimeout(45000)
let step='登录',captured=null
const save=value=>writeFileSync(privatePath,JSON.stringify(value,null,2),{mode:0o600})
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json();assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const session=async()=>{const response=await context.request.get(origin+'/api/session',{headers:{'X-Schema-Version':'1.0.0'}});assert.equal(response.status(),200);return (await response.json()).data}
 const get=async(path,current)=>{const response=await context.request.get(origin+path,{headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':current.current_context.context_revision}});assert.equal(response.status(),200);return (await response.json()).data}
 const initial=await session(),before=await get('/api/projects/'+input.project_id,initial)
 assert.equal(before.name,input.body.name);assert.ok(before.name.startsWith('SIMULATION '));assert.equal(before.status,'PENDING')
 if(existsSync(privatePath)){
  const previous=JSON.parse(readFileSync(privatePath));assert.equal(previous.state,'COMPLETED','原项目请求需要按照原登记继续核查');assert.equal(previous.project_id,input.project_id)
  assert.equal((await get('/api/operations/'+previous.operation_id,initial)).status,'COMPLETED');assert.equal(before.revision,previous.receipt.revision);assert.deepEqual(before.profile.attributes,previous.body.attributes)
  console.log('原项目属性保存操作、固定版本和完整来源已重新核查');
 }else{
  assert.deepEqual(before.profile.attributes,[],'实际已有属性需要保留，不能用验收输入覆盖')
  step='填写真实工作区属性';await page.goto(origin+'/projects/'+input.project_id+'/profile');const form=page.locator('[data-project-write-form]');await form.waitFor()
  const reason='当前本地工作区未提供有效产品资料，属性尚未确认。'
  for(const [key,label] of [['product_name','产品名称'],['device_class','器械类别'],['application_path','注册路径'],['ivd','产品类型']]){await form.getByRole('button',{name:'添加'+label,exact:true}).click();await form.locator('[data-attribute-key="'+key+'"]').getByRole('textbox',{name:'未知原因',exact:true}).fill(reason)}
  await form.locator('#project-write-reason').fill('登记本地工作区尚未确认的产品属性，保存完整未知状态及原因。');await form.getByText('本人已核查本次项目请求的实际依据并获准执行',{exact:true}).click();assert.equal(await form.locator('input[type="checkbox"]').isChecked(),true)
  step='提交原项目属性请求';page.on('request',request=>{if(request.method()==='POST'&&new URL(request.url()).pathname==='/api/projects/'+input.project_id+'/profile-revisions'){assert.equal(captured,null);captured={state:'PREPARED',project_id:input.project_id,body:request.postDataJSON(),key:request.headers()['idempotency-key'],context_revision:request.headers()['x-context-revision'],before_revision:before.revision,prepared_at:new Date().toISOString()};save(captured)}})
  const saving=page.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname==='/api/projects/'+input.project_id+'/profile-revisions');await form.getByRole('button',{name:'确认提交项目请求',exact:true}).click();const response=await saving;assert.equal(response.status(),200);const receipt=(await response.json()).data;assert.ok(captured);save({...captured,state:'COMPLETED',operation_id:receipt.operation_id,receipt})
  await page.locator('[data-project-write-receipt]').waitFor();const current=await session(),actual=await get('/api/projects/'+input.project_id,current),operation=await get('/api/operations/'+receipt.operation_id,current)
  assert.equal(operation.status,'COMPLETED');assert.equal(operation.resource_id,input.project_id);assert.equal(actual.revision,receipt.revision);assert.equal(actual.profile.profile_revision,receipt.profile_revision);assert.deepEqual(actual.profile.attributes,captured.body.attributes)
  assert.equal(actual.profile.attributes.length,4)
  for(const value of actual.profile.attributes){assert.equal(value.status,'UNKNOWN');assert.equal(value.value,null);assert.deepEqual(value.authority_refs,[]);assert.equal(value.reason,reason)}
  assert.deepEqual(actual.creation_basis,before.creation_basis);assert.equal(actual.name,before.name)
  save({...captured,state:'COMPLETED',operation_id:receipt.operation_id,receipt,verified_at:new Date().toISOString()})
  writeFileSync(new URL('.implementation-work/project-profile-save-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),project_id:input.project_id,operation_id:receipt.operation_id,revision:actual.revision,profile_revision:actual.profile.profile_revision,checks:['REAL_BROWSER_PROFILE_SAVE','ACTUAL_PROJECT_NAME_PRESERVED','UNKNOWN_PRODUCT_PROPERTIES_PRESERVED','ORIGINAL_CREATION_BASIS_PRESERVED','COMPLETED_ORIGINAL_OPERATION','FULL_RELOAD_EQUALS_ORIGINAL_REQUEST'],screenshots:false},null,2),{mode:0o600})
  console.log('项目属性已通过实际页面保存、原操作核查和完整属性重读，产品信息保持尚未确认')
 }
}finally{await context.close();await browser.close()}

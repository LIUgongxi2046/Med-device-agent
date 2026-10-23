import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const input=JSON.parse(readFileSync(new URL('.implementation-work/runtime/project-directory-workspace-input.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext(),page=await context.newPage()
page.setDefaultTimeout(45000)
let step='登录'
async function open(locator){await locator.focus();await locator.press('ArrowDown')}
async function choose(label){const id=await page.locator('[role="combobox"][aria-expanded="true"]').getAttribute('aria-controls');assert.ok(id);await page.locator('[id="'+id+'"]').getByRole('option',{name:label,exact:true}).click()}
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json()
 assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const sessionResponse=await context.request.get(origin+'/api/session',{headers:{'X-Schema-Version':'1.0.0'}});assert.equal(sessionResponse.status(),200);const session=(await sessionResponse.json()).data
 const read=async()=>{const response=await context.request.get(origin+'/api/projects/'+input.project_id,{headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}});assert.equal(response.status(),200);return (await response.json()).data}
 const before=await read()
 assert.ok(before.name.startsWith('SIMULATION '));assert.equal(before.status,'PENDING')
 step='打开实际项目属性';await page.goto(origin+'/projects/'+input.project_id+'/profile')
 const form=page.locator('[data-project-write-form]');await form.waitFor()
 const fields=[['product_name','产品名称'],['device_class','器械类别'],['application_path','注册路径'],['ivd','产品类型']]
 for(const [key,label] of fields){if(!before.profile.attributes.some(attribute=>attribute.key===key))await form.getByRole('button',{name:'添加'+label,exact:true}).click();await form.locator('[data-attribute-key="'+key+'"]').waitFor()}
 step='未知属性必填检查';await form.getByRole('button',{name:'确认提交项目请求',exact:true}).click();await form.getByText('请核查实际依据并确认本次授权声明',{exact:true}).waitFor()
 for(const [key] of fields){const row=form.locator('[data-attribute-key="'+key+'"]');await row.getByRole('textbox',{name:'未知原因',exact:true}).fill('当前本地工作区未提供有效产品资料，属性尚未确认。本次输入尚未提交。')}
 step='注册路径业务控件';const path=form.locator('[data-attribute-key="application_path"]')
 await open(path.getByRole('combobox',{name:'声明状态',exact:true}));await choose('已有声明依据')
 await open(path.getByRole('combobox',{name:'注册路径',exact:true}))
 for(const label of ['首次注册','变更注册','延续注册'])await page.getByRole('option',{name:label,exact:true}).waitFor()
 await choose('首次注册')
 step='实际资料目录与版本选择';const sources=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/source-versions'&&new URL(response.url()).searchParams.get('project_id')===input.project_id)
 await open(path.getByRole('combobox',{name:'选择固定依据',exact:true}));const listed=await sources;assert.equal(listed.status(),200)
 const catalogue=(await listed.json()).data,source=catalogue.items.find(value=>value.source_version==='20161267-4370-409d-b1b8-8cffd8d0d11f');assert.ok(source,'实际公共来源必须出现在获授权项目的依据目录')
 await choose(source.title)
 const governance=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/source-versions/'+source.source_version)
 await path.getByRole('button',{name:'采用所选依据',exact:true}).click();const chosen=await governance;assert.equal(chosen.status(),200)
 await path.getByRole('cell',{name:source.title,exact:true}).waitFor()
 step='布尔业务控件';const ivd=form.locator('[data-attribute-key="ivd"]')
 await open(ivd.getByRole('combobox',{name:'声明状态',exact:true}));await choose('已有声明依据')
 await open(ivd.getByRole('combobox',{name:'产品类型',exact:true}));await page.getByRole('option',{name:'体外诊断试剂',exact:true}).waitFor();await choose('医疗器械（非体外诊断试剂）')
 step='返回未知声明';for(const key of ['application_path','ivd']){const row=form.locator('[data-attribute-key="'+key+'"]');await open(row.getByRole('combobox',{name:'声明状态',exact:true}));await choose('尚未确认');await row.getByRole('textbox',{name:'未知原因',exact:true}).fill('当前本地工作区未提供有效产品资料，属性尚未确认。本次输入尚未提交。')}
 step='未保存输入处理';await form.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:'继续编辑',exact:true}).click();await form.waitFor()
 await form.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:'放弃输入并返回',exact:true}).click();await form.waitFor({state:'hidden'})
 const after=await read();assert.deepEqual(after,before)
 step='项目建档成员选择';await page.goto(origin+'/projects/new');await form.waitFor()
 const memberRead=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/enterprise-member-labels')
 await open(form.getByRole('combobox',{name:'项目负责人',exact:true}));assert.equal((await memberRead).status(),200)
 await choose(session.user.display_name??session.user.username??'当前人员')
 step='项目建档实际公共依据';const basis=form.getByRole('group',{name:'建档依据',exact:true})
 const creationSources=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/source-versions'&&new URL(response.url()).searchParams.get('ownership')==='ENTERPRISE')
 await open(basis.getByRole('combobox',{name:'选择固定依据',exact:true}));assert.equal((await creationSources).status(),200)
 await choose(source.title);const creationView=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/source-versions/'+source.source_version)
 await basis.getByRole('button',{name:'采用所选依据',exact:true}).click();assert.equal((await creationView).status(),200);await basis.getByRole('cell',{name:source.title,exact:true}).waitFor()
 await form.getByText('高级：负责人账户标识',{exact:true}).click();assert.equal(await form.getByRole('textbox',{name:'负责人账户标识',exact:true}).inputValue(),session.user.user_id)
 step='放弃未提交的建档输入';await form.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:'放弃输入并返回',exact:true}).click();await form.waitFor({state:'hidden'})
 writeFileSync(new URL('.implementation-work/project-profile-controls-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),project_id:input.project_id,revision:before.revision,checks:['ACTUAL_PROJECT_PROFILE','FOUR_BUSINESS_ATTRIBUTE_CONTROLS','UNKNOWN_REASON_AND_ATTESTATION_VALIDATION','REGISTRATION_PATH_OPTIONS','ACTUAL_ENTERPRISE_AND_PROJECT_SOURCE_DIRECTORY','ACTUAL_ORIGINAL_SOURCE_VERSION_SELECTION','BOOLEAN_CONTROL','UNSAVED_CONTINUE_AND_DISCARD','ORIGINAL_PROFILE_AND_REFERENCES_UNCHANGED','CREATE_OWNER_REAL_MEMBER_DIRECTORY','CREATE_ENTERPRISE_SOURCE_SELECTION','UNSUBMITTED_CREATE_DISCARD'],source_version:source.source_version,screenshots:false},null,2),{mode:0o600})
 console.log('项目属性业务控件、实际来源选择、未知声明与未保存输入处理核查通过')
}finally{await context.close();await browser.close()}

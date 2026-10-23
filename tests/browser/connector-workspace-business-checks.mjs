import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const seed=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext(),page=await context.newPage()
page.setDefaultTimeout(120000)
let phase='认证'
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json()
 assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 phase='读取企业与连接';await page.goto(origin+'/connections')
 await page.getByRole('heading',{name:'企业与连接',exact:true}).waitFor()
 for(const name of ['私有化部署','SaaS 企业空间','配套服务协作']){const tab=page.getByRole('tab',{name,exact:true});await tab.click();assert.equal(await tab.getAttribute('aria-selected'),'true')}
 phase='读取身份连接';await page.getByRole('button',{name:'管理连接：账号与单点登录',exact:true}).click()
 const identity=page.getByRole('dialog',{name:'账号与单点登录',exact:true});await identity.waitFor();assert.ok((await identity.innerText()).includes('企业本地账号'));phase='关闭身份连接弹窗';await identity.getByRole('button',{name:'关闭此对话框',exact:true}).click()
 phase='选择真实项目';const directory=page.locator('.connector-directory')
 const project=directory.getByRole('combobox').first();await project.focus();await project.press('ArrowDown')
 const session=(await(await context.request.get(origin+'/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
 const projects=await(await context.request.get(origin+'/api/projects',{headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}})).json()
 const actual=projects.data.projects.find(item=>item.project_id===seed.project_id);assert.ok(actual)
 await page.getByRole('option',{name:actual.name,exact:true}).click()
 await directory.getByRole('button',{name:'读取连接目录',exact:true}).click()
 phase='保存连接业务表单';await directory.getByRole('button',{name:'新增资料连接',exact:true}).click()
 const form=page.getByRole('dialog',{name:'资料系统连接',exact:true}),name='SIMULATION 资料连接准备 UI '+Date.now(),description='SIMULATION 仅核查业务表单保存、版本阅读与缺项阻止，客户规格和凭据尚未提供。'
 await form.getByLabel('名称',{exact:true}).fill(name)
 await form.getByLabel('连接与授权配置',{exact:true}).fill(description)
 const saved=page.waitForResponse(value=>new URL(value.url()).pathname==='/api/business-connector-configs'&&value.request().method()==='POST')
 await form.getByRole('button',{name:'保存连接',exact:true}).click()
 const response=await saved;assert.equal(response.status(),200);const config=await response.json()
 assert.equal(config.business_profile.profile.name,name);assert.equal(config.business_profile.profile.description,description);assert.equal(config.business_profile.profile.owner_user_id,session.user.user_id);assert.equal(config.connected,false)
 await form.waitFor({state:'hidden'})
 const row=directory.getByRole('row').filter({hasText:name});await row.waitFor();assert.ok((await row.innerText()).includes('接入条件未满足'))
 phase='核查配置详情与阻止条件';await row.getByRole('button',{name:'配置详情',exact:true}).click()
 const drawer=page.getByRole('dialog',{name:'资料连接详情',exact:true});await drawer.waitFor()
 assert.ok((await drawer.innerText()).includes('缺少客户接口规格'))
 assert.equal(await drawer.getByRole('button',{name:'批准当前配置',exact:true}).isEnabled(),false)
 assert.equal(await drawer.getByRole('button',{name:'验证连接',exact:true}).isEnabled(),false)
 assert.equal(await drawer.getByRole('button',{name:'开始同步',exact:true}).isEnabled(),false)
 for(const label of ['审核记录','验证记录','同步记录']){await drawer.getByRole('tab',{name:label,exact:true}).click();const history=page.waitForResponse(value=>new URL(value.url()).pathname.startsWith('/api/connector-configs/'+config.config_revision+'/history/'));await drawer.getByRole('button',{name:'读取处理记录',exact:true}).click();assert.equal((await history).status(),200)}
 phase='刷新与设计Token';await page.reload();await page.getByRole('heading',{name:'企业与连接',exact:true}).waitFor()
 const state=await page.locator('.connector-workspace').evaluate(element=>({padding:getComputedStyle(element).padding,font:getComputedStyle(element.querySelector('h1')).fontSize,color:getComputedStyle(element).color,radius:getComputedStyle(element.querySelector('.el-card')).borderRadius}))
 assert.deepEqual(state,{padding:'28px',font:'24px',color:'rgb(36, 50, 71)',radius:'8px'})
 assert.equal(await page.getByRole('heading',{name:'核查原连接请求',exact:true}).count(),0)
 writeFileSync(new URL('.implementation-work/connector-workspace-business-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),config_revision:config.config_revision,checks:['DEPLOYMENT_TABS','ACTUAL_IDENTITY','REAL_BUSINESS_FORM','SAME_REQUEST_CONFIRMED','DRAFT_DIRECTORY','MISSING_INPUTS_BLOCKED','ACTUAL_HISTORY','CONTEXT_RELOAD','DESIGN_TOKENS'],screenshots:false,customer_connection_verified:false},null,2),{mode:0o600})
 console.log('企业与连接业务表单、真实草稿、处理记录与设计Token核查通过')
}catch(failure){throw new Error(phase+'：'+failure.message.split('\n')[0])}finally{await context.close();await browser.close()}

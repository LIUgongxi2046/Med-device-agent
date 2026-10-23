import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext(),page=await context.newPage()
page.setDefaultTimeout(120000)
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json()
 assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 await page.goto(origin+'/connections')
 await page.getByRole('heading',{name:'企业与连接',exact:true}).waitFor({timeout:15000})
 for(const name of ['私有化部署','SaaS 企业空间','配套服务协作'])await page.getByRole('tab',{name,exact:true}).waitFor()
 for(const name of ['账号与单点登录','资料系统连接','专家服务协作'])await page.locator('.connection-categories').getByRole('heading',{name,exact:true}).waitFor()
 const state=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/connector-workspace')
 await page.getByRole('button',{name:'刷新企业与连接',exact:true}).click()
 const response=await state
 assert.equal(response.status(),200)
 const workspace=(await response.json()).data
 assert.equal(workspace.schema_version,'1.0.0')
 assert.ok(Array.isArray(workspace.allowed_actions))
 assert.ok(workspace.allowed_actions.includes('connector.read'),'当前超管需要已登记的连接读取许可')
 writeFileSync(new URL('.implementation-work/connector-workspace-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),checks:[{id:'PR06-S16-NAVIGATION',state:'PASS'},{id:'PR06-S16-ACTUAL-SERVICE',state:'PASS'}],screenshots:false,customer_connection_verified:false},null,2),{mode:0o600})
 console.log('企业与连接原型导航和真实服务核查通过')
}catch(failure){throw new Error(failure.message.split('\n')[0])}finally{await context.close();await browser.close()}

import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root))),member=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-member-credentials.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),proof={verified_at:new Date().toISOString(),checks:[],screenshots:false}
try{
 for(const [kind,credentials] of [['member',member],['owner',owner]]){
  const context=await browser.newContext(),page=await context.newPage(),requests=[],errors=[];page.setDefaultTimeout(60000)
  try{
   const csrf=await(await context.request.get(origin+'/auth/csrf')).json();assert.equal((await context.request.post(origin+'/auth/login',{form:{username:credentials.username,password:credentials.password},headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
   page.on('request',request=>{if(new URL(request.url()).pathname==='/api/system/notice/listTop')requests.push(request.url())});page.on('response',response=>{if(new URL(response.url()).pathname.startsWith('/api/')&&response.status()>=400)errors.push({path:new URL(response.url()).pathname,status:response.status()})})
   const notice=kind==='owner'?page.waitForResponse(response=>new URL(response.url()).pathname==='/api/system/notice/listTop'):null
   await page.goto(origin+'/projects');await page.getByRole('heading',{name:'项目中心',exact:true}).waitFor({timeout:15000})
   if(kind==='member'){
    assert.equal(await page.locator('#header-notice').count(),0,'没有公告权限的普通账户不应创建公告组件')
    await page.locator('[data-project-table]').waitFor()
    await page.goto(origin+'/templates');await page.locator('[data-template-list]').waitFor();assert.equal(await page.locator('#header-notice').count(),0)
    await page.goto(origin+'/account');await page.getByText('个人信息',{exact:true}).waitFor();assert.equal(await page.locator('#header-notice').count(),0)
    assert.equal(requests.length,0);assert.deepEqual(errors,[])
    const session=(await(await context.request.get(origin+'/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
    assert.equal((await context.request.get(origin+'/api/system/notice/listTop',{headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}})).status(),403)
    proof.checks.push({id:'NN-01',state:'PASS'})
   }else{assert.equal((await notice).status(),200);assert.equal(await page.locator('#header-notice').count(),1);proof.checks.push({id:'NN-02',state:'PASS'})}
  }finally{await context.close()}
 }
 writeFileSync(new URL('.implementation-work/notice-access-navigation-checks.json',root),JSON.stringify(proof,null,2),{mode:0o600});console.log('公告入口按实际权限读取，普通业务账户连续导航及服务端拒绝核查通过')
}catch(failure){throw new Error(failure.message.split('\n')[0])}finally{await browser.close()}

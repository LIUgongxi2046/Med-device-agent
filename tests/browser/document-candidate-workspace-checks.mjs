import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const seed=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-workspace-seed.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true})
const context=await browser.newContext(),page=await context.newPage()
page.setDefaultTimeout(120000)
page.on('response',response=>{const path=new URL(response.url()).pathname;if(path.startsWith('/api/'))console.log(response.status(),path)})
page.on('pageerror',failure=>console.error('页面错误',failure.message))
await page.addInitScript(()=>window.addEventListener('medical-failure',event=>console.error('文稿读取错误',event.detail?.code,event.detail?.message)))
page.on('console',message=>{if(message.text().startsWith('文稿读取错误'))console.error(message.text())})
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json()
 assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 await page.goto(origin+`/projects/${seed.project_id}/tasks/${seed.task_id}/documents/${seed.document_id}/write`)
 await page.getByRole('button',{name:'读取完整正文',exact:true}).waitFor()
 console.log('已打开文稿编辑工作区')
 await page.getByText('章节候选 · 预览与比较',{exact:true}).click()
 console.log('已打开候选比较分区')
 const workspace=page.getByRole('region',{name:'候选比较与采用',exact:true})
 await workspace.waitFor({timeout:15000})
 console.log('已读取候选业务控件')
 const availability=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/document-workspace-availability')
 await workspace.getByRole('button',{name:'读取候选目录',exact:true}).click()
 const available=await availability
 assert.equal(available.status(),200)
 const features=(await available.json()).data
 const checks=[{id:'PR05-CANDIDATE-SERVICE-STATE',state:'PASS',enabled:features.candidate_adoption_enabled}]
 if(features.candidate_adoption_enabled){
  const response=await page.waitForResponse(response=>new URL(response.url()).pathname===`/api/documents/${seed.document_id}/candidates`)
  assert.equal(response.status(),200)
  const value=(await response.json()).data
  assert.equal(value.document_id,seed.document_id)
  assert.equal(value.project_id,seed.project_id)
  if(!value.items.length)await workspace.getByText('当前文稿尚无已登记的文稿候选。',{exact:true}).waitFor()
  else assert.equal(await workspace.locator('[data-candidate-row]').count(),value.items.length)
  checks.push({id:'PR05-CANDIDATE-DIRECTORY',state:'PASS',count:value.items.length,complete:value.complete})
 }else{
  await workspace.getByText('当前服务器尚未启用候选采用服务。',{exact:true}).waitFor()
  checks.push({id:'PR05-CANDIDATE-ADOPTION',state:'NOT_RUN',reason:'实际候选运行服务尚未启用'})
 }
 assert.equal(await workspace.getByRole('button',{name:'明确确认采用',exact:true}).count(),0)
 writeFileSync(new URL('.implementation-work/document-candidate-workspace-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),checks,screenshots:false},null,2),{mode:0o600})
 console.log('文稿候选服务状态与确认门槛核查通过')
}catch(failure){console.error('页面地址',page.url());console.error('页面提示',await page.getByRole('alert').allTextContents());if(await page.locator('[data-candidate-adoption]').count())console.error('工作区状态',await page.locator('[data-candidate-adoption]').innerText());throw new Error(failure.message.split('\n')[0])}finally{await context.close();await browser.close()}

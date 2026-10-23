import assert from 'node:assert/strict'
import { readFileSync,writeFileSync } from 'node:fs'
import { chromium } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083',owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage(),checks=[]
page.setDefaultTimeout(120000)
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json();assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 for(const route of ['/projects','/rules','/templates','/document-save-audit']){
  await page.goto(origin+route);await page.locator('main.medical-page h1').first().waitFor()
  if(route==='/document-save-audit')await page.locator('.audit-query .el-select__wrapper').first().waitFor()
  const style=await page.locator('main.medical-page').first().evaluate(element=>{const title=element.querySelector('h1'),input=element.querySelector('.el-input__wrapper,.el-select__wrapper');return {fontSize:getComputedStyle(title).fontSize,fontWeight:getComputedStyle(title).fontWeight,color:getComputedStyle(title).color,body:getComputedStyle(element).fontSize,controlRadius:input?getComputedStyle(input).borderRadius:null,controlHeight:input?input.getBoundingClientRect().height:null}})
  assert.equal(style.fontSize,'24px',route);assert.equal(style.fontWeight,'600',route);assert.equal(style.color,'rgb(36, 50, 71)',route);assert.equal(style.body,'14px',route);if(style.controlRadius!==null){assert.equal(style.controlRadius,'6px',route);assert.ok(style.controlHeight>=38,route)}
  await page.evaluate(()=>document.documentElement.classList.add('dark'))
  const dark=await page.locator('main.medical-page').first().evaluate(element=>({color:getComputedStyle(element.querySelector('h1')).color,fontWeight:getComputedStyle(element.querySelector('h1')).fontWeight}))
  assert.notEqual(dark.color,style.color,route+' 需要继续支持现有深色主题');assert.equal(dark.fontWeight,'600')
  await page.evaluate(()=>document.documentElement.classList.remove('dark'));checks.push({route,style,dark,state:'PASS'})
 }
 writeFileSync(new URL('.implementation-work/medical-design-token-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),checks,screenshots:false},null,2),{mode:0o600});console.log('医疗工作站标题、正文、文字颜色及控件设计参数核查通过')
}finally{await context.close();await browser.close()}

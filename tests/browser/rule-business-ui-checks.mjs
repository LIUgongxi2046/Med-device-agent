import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const workflow=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-template-workflow.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext(),page=await context.newPage()
page.setDefaultTimeout(120000)
const checks=[]
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json()
 assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 await page.goto(origin+'/rules/new?from='+workflow.rule_version)
 const name='SIMULATION 公开编号规则页面验证 '+Date.now()
 await page.getByRole('textbox',{name:'规则名称',exact:true}).fill(name)
 await page.getByRole('textbox',{name:'判断条件',exact:true}).fill('核查公开编辑样例中的必需编号属性，并由人工确认资料的适用范围。')
 const dimension=page.getByRole('combobox',{name:'检查维度',exact:true})
 await dimension.focus();await dimension.press('ArrowDown')
 await page.getByRole('option',{name:'引用准确性',exact:true}).click()
 const handling=page.getByRole('combobox',{name:'处置方式',exact:true})
 await handling.focus();await handling.press('ArrowDown')
 await page.getByRole('option',{name:'提示',exact:true}).click()
 await page.getByRole('textbox',{name:'创建或修订理由',exact:true}).fill('SIMULATION 从已核准的公开资料规则创建独立修订，验证业务定义与人工审核的完整页面操作。')
 await page.getByText('已经核查表达式、输入属性、全部常量及固定依据，本次只保存草稿',{exact:true}).click()
 const save=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/business-rule-versions'&&response.request().method()==='POST')
 await page.getByRole('button',{name:'保存规则草稿',exact:true}).click()
 const saved=await save
 assert.equal(saved.status(),201,await saved.text())
 const rule=(await saved.json()).data.rule
 assert.equal(rule.base_version.version_id,workflow.rule_version)
 checks.push({id:'PR02-UI-CREATE-REVISION',state:'PASS',rule_version:rule.rule_version})
 await page.getByRole('link',{name:'打开固定规则版本',exact:true}).click()
 await page.getByRole('heading',{name,exact:true}).waitFor()
 assert.ok((await page.locator('[data-rule-preview]').innerText()).includes('引用准确性'))
 assert.ok((await page.locator('[data-rule-preview]').innerText()).includes('提示'))
 await page.getByRole('button',{name:'登记规则人工决定',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'确认规则人工决定',exact:true})
 await dialog.getByRole('combobox').focus();await dialog.getByRole('combobox').press('ArrowDown')
 await page.getByRole('option',{name:'核准',exact:true}).click()
 await dialog.getByRole('textbox',{name:'决定理由',exact:true}).fill('SIMULATION 已核查业务定义、固定表达式、常量及公开素材依据，仅核准本地编辑验证规则。')
 await dialog.getByText('已经核查规则表达式、输入属性、全部常量及获授权依据',{exact:true}).click()
 const approval=page.waitForResponse(response=>new URL(response.url()).pathname===`/api/rule-versions/${rule.rule_version}/approval-decisions`&&response.request().method()==='POST')
 await dialog.getByRole('button',{name:'确认登记规则决定',exact:true}).click()
 const approved=await approval
 assert.equal(approved.status(),200,await approved.text())
 assert.match(approved.request().headers()['x-rule-business-sha256'],/^[a-f0-9]{64}$/)
 await page.locator('[data-rule-completed]').waitFor()
 await page.reload()
 await page.getByRole('heading',{name,exact:true}).waitFor()
 assert.ok((await page.locator('[data-rule-preview]').innerText()).includes('已核准'))
 checks.push({id:'PR02-UI-APPROVAL-READ',state:'PASS',rule_version:rule.rule_version})
 writeFileSync(new URL('.implementation-work/rule-business-ui-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),checks,screenshots:false,professional_approval:false},null,2),{mode:0o600})
 console.log('规则业务表单、实际修订与人工核准页面验证通过')
}catch(failure){console.error('页面地址',page.url());console.error('页面提示',await page.getByRole('alert').allTextContents());throw new Error(failure.message.split('\n')[0])}finally{await context.close();await browser.close()}

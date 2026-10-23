import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const workflow=JSON.parse(readFileSync(new URL('.implementation-work/runtime/acceptance-template-workflow.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext(),page=await context.newPage()
page.setDefaultTimeout(60000);let phase='登录'
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json()
 assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const original=workflow.template_version??workflow.template_id
 const session=(await(await context.request.get(origin+'/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
 const baseline=await context.request.get(origin+'/api/template-versions/'+original+'/approval-baseline',{headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}});assert.equal(baseline.status(),200);const originalTemplate=(await baseline.json()).data.template
 phase='读取模板完整定义';await page.goto(origin+'/templates/new?from='+original)
 await page.getByRole('heading',{name:'创建模板草稿',exact:true}).waitFor()
 await page.getByRole('button',{name:'配置章节',exact:true}).first().waitFor()
 phase='模板适用条件业务配置';await page.getByRole('tab',{name:'适用条件',exact:true}).click()
 const condition=page.getByRole('region',{name:'模板适用条件业务配置',exact:true})
 await condition.waitFor({timeout:15000})
 await condition.getByRole('combobox',{name:'模板输入属性',exact:true}).waitFor()
 await condition.getByRole('button',{name:'确认模板适用条件',exact:true}).click()
 phase='全部固定依据业务配置';await page.getByRole('tab',{name:'全部依据',exact:true}).click()
 await page.getByRole('region',{name:'模板全部依据业务配置',exact:true}).waitFor({timeout:15000})
 phase='模板字体版式业务配置';await page.getByRole('tab',{name:'版式和输出',exact:true}).click()
 const format=page.getByRole('region',{name:'模板版式业务配置',exact:true});await format.waitFor({timeout:15000})
 const source=format.getByRole('combobox',{name:'版式来源模板',exact:true});await source.waitFor();await source.focus();await source.press('ArrowDown');await page.getByRole('option').filter({hasText:originalTemplate.reason}).click()
 await format.getByRole('button',{name:'采用来源模板的字体与版式',exact:true}).click()
 assert.ok((await format.innerText()).includes('获授权字体'))
 assert.equal(await format.getByRole('checkbox',{name:'DOCX',exact:true}).isChecked(),true)
 if(await format.getByRole('checkbox',{name:'PDF',exact:true}).isChecked())await format.getByText('PDF',{exact:true}).click()
 await format.getByRole('button',{name:'确认版式和输出配置',exact:true}).click()
 await page.getByRole('textbox',{name:'创建或修订理由',exact:true}).fill('SIMULATION 模板业务控件核查 '+Date.now())
 await page.getByText('已经核查全部目录、字段、附件、条件、依据及版式；本次只保存草稿',{exact:true}).click()
 phase='保存完整新模板';const save=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/template-versions'&&response.request().method()==='POST')
 await page.getByRole('button',{name:'保存模板草稿',exact:true}).click();const saved=await save;assert.equal(saved.status(),201)
 const value=(await saved.json()).data.template;assert.deepEqual(value.content.format_profile.output_types,['DOCX']);assert.deepEqual(value.content.format_profile.font_artifacts,originalTemplate.content.format_profile.font_artifacts);assert.deepEqual(value.content.format_profile.format_revision,originalTemplate.content.format_profile.format_revision);assert.ok(value.content.basis_manifest.length>0);assert.equal(value.content.condition_schema.unknown_result,'UNKNOWN');assert.equal(value.base_version.version_id,original)
 await page.getByRole('link',{name:'打开人工审查',exact:true}).click();await page.locator('[data-template-preview]').waitFor()
 phase='真实人工决定';await page.getByRole('button',{name:'登记人工决定',exact:true}).click()
 const decision=page.getByRole('dialog',{name:'确认模板人工决定',exact:true});await decision.getByRole('combobox').focus();await decision.getByRole('combobox').press('ArrowDown');await page.getByRole('option',{name:'核准',exact:true}).click()
 await decision.getByRole('textbox',{name:'决定理由',exact:true}).fill('SIMULATION 复核实际完整模板的适用属性、依据、字体、版式及输出类型，仅用于本地配置编辑核验。')
 await decision.getByText('已经核查完整目录、字段、附件、规则、版式和获授权依据',{exact:true}).click()
 const approve=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/template-versions/'+value.template_version+'/approval-decisions'&&response.request().method()==='POST');await decision.getByRole('button',{name:'确认登记决定',exact:true}).click();assert.equal((await approve).status(),200)
 await page.getByText('原模板决定已完成并核查',{exact:true}).waitFor();await page.reload();await page.locator('[data-template-preview]').waitFor();assert.ok((await page.locator('[data-template-preview]').innerText()).includes('已核准'))
 writeFileSync(new URL('.implementation-work/template-profile-controls-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),template_version:value.template_version,checks:['CONDITION_BUSINESS_CONTROLS','COMPLETE_BASIS_CONTROLS','FORMAT_BUSINESS_CONTROLS','ORIGINAL_FONT_REFS_RETAINED','ACTUAL_NEW_VERSION_SAVE','ACTUAL_APPROVAL','APPROVED_VERSION_RELOAD'],screenshots:false},null,2),{mode:0o600})
 console.log('模板适用条件、依据、字体与版式业务配置真实保存及人工核准通过')
}catch(failure){throw new Error(phase+'：'+failure.message.split('\n')[0])}finally{await context.close();await browser.close()}

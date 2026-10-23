import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083'
const owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root))),receipt=JSON.parse(readFileSync(new URL('.implementation-work/rule-business-ui-checks.json',root)))
const id=receipt.checks.find(item=>item.id==='PR02-UI-APPROVAL-READ').rule_version
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage(),errors=[],definitionRequests=[]
page.setDefaultTimeout(120000)
const dimensionNames={CONTENT_COMPLETENESS:'内容完整性',REFERENCE_ACCURACY:'引用准确性',STRICT_FIELD_CONSISTENCY:'严格字段一致性',RULE_COMPLIANCE:'规则符合性',HUMAN_MODIFICATION:'人工修改量'},handlingNames={BLOCK_SIGNING:'阻止签发',MANUAL_CONFIRMATION:'人工确认',NOTICE:'提示'},statusNames={DRAFT:'草稿',APPROVED:'已核准',RETURNED:'已退回',DISABLED:'已停用'}
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json();assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const session=(await(await context.request.get(origin+'/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data,headers={'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}
 const rule=(await(await context.request.get(origin+'/api/rule-versions/'+id,{headers})).json()).data.rule,business=(await(await context.request.get(origin+'/api/rule-versions/'+id+'/business-definition',{headers})).json()).data.definition
 page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(new URL(request.url()).pathname.endsWith('/business-definition'))definitionRequests.push(request.url())})
 const initial=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/business-rule-directory');await page.goto(origin+'/rules');assert.equal((await initial).status(),200)
 await page.getByRole('heading',{name:'检查规则',exact:true}).waitFor();await page.getByRole('button',{name:'查询规则',exact:true}).waitFor({state:'visible'})
 await page.getByRole('textbox',{name:'规则名称或判断条件',exact:true}).fill(business.name)
 for(const [label,name]of [['检查维度',dimensionNames[business.dimension]],['处置方式',handlingNames[business.handling]],['审核状态',statusNames[rule.status]]]){const select=page.getByRole('combobox',{name:label,exact:true});await select.focus();await select.press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}
 const query=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/business-rule-directory'&&new URL(response.url()).searchParams.get('q')===business.name);await page.getByRole('button',{name:'查询规则',exact:true}).click();const actual=await query;assert.equal(actual.status(),200)
 const parameters=new URL(actual.url()).searchParams;assert.equal(parameters.get('dimension'),business.dimension);assert.equal(parameters.get('handling'),business.handling);assert.equal(parameters.get('status'),rule.status)
 await page.getByRole('link',{name:business.name,exact:true}).waitFor();assert.equal(await page.getByRole('textbox',{name:'固定规则版本',exact:true}).isVisible(),false);assert.equal(definitionRequests.length,0)
 assert.deepEqual(await page.locator('.rule-table .el-table__header-wrapper th').allTextContents(),['规则','检查对象','条件与处置','状态','操作'])
 const styles=await page.evaluate(()=>{const heading=document.querySelector('.rule-page-heading h1'),button=document.querySelector('.rule-page-heading .el-button'),card=document.querySelector('.rule-directory-card'),input=document.querySelector('.rule-filters .el-input__wrapper');return {heading:getComputedStyle(heading).fontSize,headingColor:getComputedStyle(heading).color,primary:getComputedStyle(button).backgroundColor,cardRadius:getComputedStyle(card).borderRadius,controlRadius:getComputedStyle(input).borderRadius,controlHeight:input.getBoundingClientRect().height}})
 assert.equal(styles.heading,'24px');assert.equal(styles.headingColor,'rgb(36, 50, 71)');assert.equal(styles.primary,'rgb(23, 104, 196)');assert.equal(styles.cardRadius,'8px');assert.equal(styles.controlRadius,'6px');assert.ok(styles.controlHeight>=38)
 await page.getByRole('textbox',{name:'规则名称或判断条件',exact:true}).fill('未登记的中文组合筛选验证')
 const empty=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/business-rule-directory'&&new URL(response.url()).searchParams.get('q')==='未登记的中文组合筛选验证');await page.getByRole('button',{name:'查询规则',exact:true}).click();assert.equal((await empty).status(),200);await page.getByText('没有符合筛选条件的检查规则',{exact:true}).waitFor();assert.equal(await page.getByRole('link',{name:business.name,exact:true}).count(),0)
 const reset=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/business-rule-directory'&&!new URL(response.url()).searchParams.has('q'));await page.getByRole('button',{name:'重置筛选',exact:true}).click();assert.equal((await reset).status(),200)
 await page.getByRole('link',{name:business.name,exact:true}).waitFor();await page.getByRole('link',{name:business.name,exact:true}).click();await page.getByRole('heading',{name:business.name,exact:true}).waitFor()
 assert.deepEqual(errors,[])
 writeFileSync(new URL('.implementation-work/rule-business-directory-ui-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),rule_version:id,checks:['BUSINESS_FILTER_CONTROLS','SERVER_COMBINED_FILTER_REQUEST','NO_DIRECTORY_N_PLUS_ONE','ADVANCED_VERSION_ENTRY','EMPTY_RESULT_CLEARS_ROWS','FILTER_RESET','ACTUAL_RULE_DETAIL','DESIGN_COMPUTED_TOKENS'],styles,page_errors:errors,screenshots:false},null,2),{mode:0o600});console.log('检查规则业务筛选、空状态、详情与设计参数核查通过')
}catch(failure){console.error('页面地址',page.url());console.error('页面提示',await page.getByRole('alert').allTextContents());throw new Error(failure.message.split('\n')[0])}finally{await context.close();await browser.close()}

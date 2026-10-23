import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'
const root=new URL('../../',import.meta.url),origin='http://127.0.0.1:18083',owner=JSON.parse(readFileSync(new URL('.implementation-work/runtime/owner-credentials.json',root)))
const input=JSON.parse(readFileSync(new URL('.implementation-work/runtime/project-directory-workspace-input.json',root)))
const browser=await chromium.launch({channel:'chrome',headless:true}),context=await browser.newContext(),page=await context.newPage()
page.setDefaultTimeout(45000)
let step='登录'
try{
 const csrf=await(await context.request.get(origin+'/auth/csrf')).json();assert.equal((await context.request.post(origin+'/auth/login',{form:owner,headers:{Origin:origin,[csrf.header_name]:csrf.token},maxRedirects:0})).status(),302)
 const initial=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/project-workspace-directory');await page.goto(origin+'/projects');const loaded=await initial;assert.equal(loaded.status(),200);const directory=(await loaded.json()).data;await page.locator('[data-project-table]').waitFor()
 assert.deepEqual(await page.locator('[data-project-table] .el-table__header-wrapper th').allTextContents(),['项目名称','产品名称','器械类别','注册路径','项目阶段','资料状态','任务进度','最近更新','操作'])
 await page.getByRole('combobox',{name:'项目每页条数',exact:true}).waitFor()
 assert.equal(await page.getByRole('button',{name:'上一页',exact:true}).count(),1);assert.equal(await page.getByRole('button',{name:'下一页',exact:true}).count(),1)
 const search=page.getByRole('textbox',{name:'搜索项目',exact:true});await search.fill('未登记的项目目录搜索验证')
 const query=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/project-workspace-directory'&&new URL(response.url()).searchParams.get('q')==='未登记的项目目录搜索验证');await page.getByRole('button',{name:'查询',exact:true}).click();assert.equal((await query).status(),200);await page.getByText('当前授权及筛选范围没有项目',{exact:true}).waitFor()
 const reset=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/project-workspace-directory'&&!new URL(response.url()).searchParams.has('q'));await page.getByRole('button',{name:'重置',exact:true}).click();assert.equal((await reset).status(),200)
 step='阶段选择入口';const phase=page.getByRole('combobox',{name:'筛选项目阶段',exact:true});await phase.focus();await phase.press('ArrowDown');step='阶段选择选项';await page.getByRole('option',{name:'待配置',exact:true}).click()
 const filter=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/project-workspace-directory'&&new URL(response.url()).searchParams.get('status')==='PENDING');await page.getByRole('button',{name:'查询',exact:true}).click();const filtered=await filter;assert.equal(filtered.status(),200);const result=(await filtered.json()).data;assert.ok(result.items.some(value=>value.project.project_id===input.project_id));for(const item of result.items)assert.equal(item.project.status,'PENDING')
 await page.getByRole('link',{name:input.body.name,exact:true}).waitFor()
 const selected=result.items.find(value=>value.project.project_id===input.project_id),row=page.locator('[data-project-table] .el-table__row').filter({has:page.getByRole('link',{name:input.body.name,exact:true})})
 step='资料状态';const materialLabel=selected.materials.registered==='0'?'暂无可见资料':'审查核准 '+selected.materials.approved+' / '+selected.materials.registered;await row.getByRole('button',{name:materialLabel,exact:true}).click();await page.getByText('统计当前账户可读取的企业公共及本项目资料版本。',{exact:true}).waitFor();assert.equal(await page.getByRole('link',{name:'查看资料与核准状态',exact:true}).getAttribute('href'),'/library?project_id='+input.project_id);await page.getByRole('heading',{name:'项目中心',exact:true}).click()
 step='每页条数';const size=page.getByRole('combobox',{name:'项目每页条数',exact:true});await size.focus();await size.press('ArrowDown');const changing=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/project-workspace-directory'&&new URL(response.url()).searchParams.get('limit')==='20');await page.getByRole('option',{name:'20 条',exact:true}).click();const changed=await changing;assert.equal(changed.status(),200);assert.equal((await changed.json()).data.total,result.total)
 const visible=await page.locator('main.projects-workspace').innerText()
 writeFileSync(new URL('.implementation-work/project-directory-ui-checks.json',root),JSON.stringify({verified_at:new Date().toISOString(),projects:directory.total,checks:['PROTOTYPE_NINE_BUSINESS_COLUMNS','ACTUAL_SEARCH_AND_EMPTY_STATE','RESET_AUTHORIZED_DIRECTORY','EXPLICIT_PAGE_NAVIGATION','ACTUAL_STAGE_FILTER','MATERIAL_STATUS_POPOVER_AND_SCOPE_LINK','PAGE_SIZE_QUERY_PRESERVES_TOTAL'],visible_text:visible,screenshots:false},null,2),{mode:0o600});console.log('项目中心业务列、阶段筛选、资料状态、条数与分页入口核查通过')
}finally{await context.close();await browser.close()}

import assert from 'node:assert/strict';
import { readFile,writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { chromium } from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';

const root='/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent';
const browser=await chromium.launch({headless:true,executablePath:'/Users/liuhaoxian/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-x64/chrome-headless-shell'});
const page=await browser.newPage({viewport:{width:1440,height:1000},reducedMotion:'reduce'});page.setDefaultTimeout(10000);
const errors=[],requests=[],layouts=[];page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(/^https?:/.test(request.url()))requests.push(request.url())});
const active=()=>page.locator('.screen.active'),modal=()=>page.locator('#product-dialog');
const act=(name,value)=>active().locator(`[data-action="${name}"]${value===undefined?'':`[data-value="${value}"]`}`).first();
const dialogAct=(name,value)=>modal().locator(`[data-action="${name}"]${value===undefined?'':`[data-value="${value}"]`}`).first();
const field=name=>modal().locator(`[name="${name}"]`);
const submit=()=>modal().locator('button[type="submit"]').click();
const close=()=>modal().locator('[data-action="dialog-close"]').first().click();
const open=async id=>page.evaluate(id=>showScreen(id),id);
async function layout(label){const result=await page.evaluate(label=>({label,width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth,dialogOverflow:document.querySelector('dialog[open]')?document.querySelector('dialog[open]').scrollWidth>document.querySelector('dialog[open]').clientWidth:false}),label);layouts.push(result);assert.equal(result.overflow,false,label);assert.equal(result.dialogOverflow,false,label)}
await page.goto(pathToFileURL(`${root}/设计稿.html`).href+'#knowledge');
assert.equal(await active().locator('[data-change="library-scope"]').inputValue(),'project');
await act('kw-area','processing').click();await act('kw-upload').click();
await field('files').setInputFiles([`${root}/PRD.md`,`${root}/界面规范.md`,`${root}/ui-delivery/asset-manifest.csv`]);await field('source').fill('当前工作区需求与设计文档');await submit();await modal().waitFor({state:'hidden'});
assert.equal(await active().locator('tbody tr').count(),7);assert.match(await active().innerText(),/等待解析服务/);
const prdRow=active().locator('tbody tr').filter({hasText:'PRD.md'}),specRow=active().locator('tbody tr').filter({hasText:'界面规范.md'});
const prdId=await prdRow.locator('[data-action="kw-process"]').getAttribute('data-value'),specId=await specRow.locator('[data-action="kw-process"]').getAttribute('data-value');
await act('kw-upload').click();await field('files').setInputFiles(`${root}/PRD.md`);await field('source').fill('重复内容核查');await submit();await modal().waitFor({state:'hidden'});
assert.match(await active().innerText(),/重复内容/);assert.equal(await active().locator('tbody tr').count(),8);
await act('kw-process',prdId).click();assert.match(await modal().innerText(),/本地全文可查/);assert.match(await modal().innerText(),/处理 v1/);await close();
await act('kw-area','collections').click();await act('kw-collection-new').click();
await field('name').fill('PRD 与界面规范');await field('application').fill('需求与界面评审');await field('purpose').fill('核对 V1.2.0 页面要求与设计内容');
await modal().locator(`[name="members"][value="${prdId}"]`).check();await submit();assert.match(await active().innerText(),/v1/);
await act('kw-collection-edit').click();await modal().locator(`[name="members"][value="${specId}"]`).check();await submit();
assert.match(await active().locator('.collection-summary').innerText(),/2 项成员/);
await act('kw-collection-history','collection-1:1').click();assert.match(await modal().innerText(),/PRD.md/);assert.doesNotMatch(await modal().innerText(),/界面规范.md/);await close();
await act('kw-requirement').click();await field('name').fill('提供 V1.2.0 需求正文');await submit();
await act('kw-requirement-evidence','collection-1:0').click();await field('ids').first().check();await submit();assert.match(await active().innerText(),/0 \/ 1 项已具备资料/);
await act('kw-collection-history','collection-1:2').click();assert.match(await modal().innerText(),/尚未登记要求/);await close();
await act('kw-collection-history','collection-1:4').click();assert.match(await modal().innerText(),/提供 V1.2.0 需求正文/);await close();
await act('kw-area','search').click();await active().locator('[name="query"]').fill('BR-12');await active().locator('[data-form="kw-search"] button').click();assert.match(await active().innerText(),/范围为空/);
await active().locator('[name="collection"]').selectOption('collection-1:4');await active().locator('[data-form="kw-search"] button').click();assert.ok(await active().locator('.evidence-result').count()>0);
await active().locator('[name="mode"]').selectOption('semantic');await active().locator('[data-form="kw-search"] button').click();assert.match(await active().innerText(),/语义检索服务未接入/);assert.equal(await active().locator('.evidence-result').count(),0);
await act('kw-area','files').click();
await active().locator('[data-change="library-size"]').selectOption('50');await act('select-material',prdId).click();await act('library-tab','links').click();assert.match(await active().innerText(),/PRD 与界面规范 v1/);
await act('material-actions').click();await dialogAct('dialog-material-trash').click();assert.match(await modal().innerText(),/不能移入回收站/);await close();

await open('S13');await act('config-select','D-1.5').click();await act('config-tab','roles').click();assert.equal(await active().locator('.role-card').count(),6);
await act('cap-role','0').click();await field('input').fill('PRD 中的目标、模板、资料清单与预算');await submit();await act('cap-role','0').click();assert.match(await field('input').inputValue(),/PRD/);await close();
await act('config-tab','capabilities').click();await act('cap-new-skill').click();await field('name').fill('注册资料检索需求');await field('version').fill('V1.2.0');await field('purpose').fill('PRD 8.2.1 定义的注册资料检索能力');await field('source').fill(`${root}/PRD.md`);await field('scope').fill('注册文稿；需要授权检索工具');await submit();assert.match(await active().innerText(),/待审核/);
await act('cap-skill','skill-3').click();assert.match(await modal().innerText(),/不能作为已核准能力发布/);await close();
await act('config-tab','limits').click();await act('cap-limits').click();assert.equal(await field('questions').inputValue(),'');await submit();assert.equal(await field('questions').evaluate(node=>node.validity.valueMissing),true);await close();
await act('config-tab','release').click();assert.equal(await act('publish-version').isDisabled(),true);assert.match(await active().innerText(),/角色工具已验证/);

await open('S07');await act('research-scope').click();await dialogAct('research-clear-files').click();await field('mode').selectOption('approved');await field('network').check();await submit();assert.match(await page.locator('#product-dialog-error').innerText(),/不允许联网/);await dialogAct('research-offline').click();await submit();assert.match(await active().locator('.research-scope-summary').innerText(),/0 项固定资料/);
await open('S09');await active().locator('.reader-operations>summary').click();await act('research-plan').click();await dialogAct('research-question').click();await field('question').fill('产品名称是否与原始项目资料一致');await field('condition').fill('由负责人核对原文名称与版本');await submit();assert.match(await modal().innerText(),/待补证/);await close();
await act('research-run').click();await dialogAct('research-stop').click();await field('reason').fill('核查停止请求与远端状态区分');await field('confirm').check();await submit();assert.match(await modal().innerText(),/停止请求尚未提交服务/);assert.doesNotMatch(await active().locator('.reader-operations').innerText(),/已取消/);await close();

await open('S04');await act('kw-area','artifacts').click();await act('kw-artifact','task-1').click();
const firstDownload=page.waitForEvent('download');await dialogAct('kw-export-new','task-1').click();await (await firstDownload).saveAs(`${root}/ui-delivery/.checks/research-artifact.md`);
const artifact=await readFile(`${root}/ui-delivery/.checks/research-artifact.md`,'utf8');assert.match(artifact,/来源附录/);assert.match(artifact,/文稿版本：v1/);
const secondDownload=page.waitForEvent('download');await act('kw-export','export-1').click();await (await secondDownload).saveAs(`${root}/ui-delivery/.checks/research-artifact-again.md`);assert.equal(await readFile(`${root}/ui-delivery/.checks/research-artifact-again.md`,'utf8'),artifact);
await act('kw-artifact','task-1').click();await dialogAct('kw-artifact-save','task-1').click();await field('application').fill('本项目资料审查；核准责任人为注册审查人员');await field('confirm').check();await submit();assert.match(await active().innerText(),/待核验/);await act('library-tab','links').click();assert.match(await active().innerText(),/原始证据/);

await page.locator('#agent-launcher').click();await page.locator('#conversation-agent [data-action="research-assistant-scope"]').click();await dialogAct('research-clear-files').click();await submit();
await page.locator('#assistant-prompt').fill('BR-12');await page.locator('#assistant-composer select').selectOption('search');await page.locator('#assistant-composer button[type="submit"]').click();assert.match(await page.locator('.chat-message.assistant').last().innerText(),/没有匹配资料/);
await page.locator('#conversation-agent [data-action="research-preferences"]').click();await field('text').fill('保留文档标题和来源附录');await submit();await page.locator('#conversation-agent [data-action="research-preferences"]').click();assert.match(await modal().innerText(),/保留文档标题/);await dialogAct('research-preference-delete','0').click();assert.match(await modal().innerText(),/尚未保存写作习惯/);await close();await page.locator('[data-action="assistant-close"]').click();

await page.locator('#agent-launcher').click();await page.locator('#conversation-agent [data-action="research-attachment"]').click();await field('file').setInputFiles(`${root}/PRD.md`);await submit();await modal().waitFor({state:'hidden'});assert.match(await page.locator('.chat-message.assistant').last().innerText(),/会话附件：PRD.md/);
await page.locator('#conversation-agent [data-action="assistant-card"]').filter({hasText:'保存附件到项目'}).last().click();await field('source').fill('工作区 PRD V1.2.0；仅用于需求评审');await field('confirm').check();await submit();assert.match(await active().innerText(),/待核验/);await page.locator('[data-action="assistant-close"]').click();

for(const width of [1440,1024,768,390,320]){
  await page.setViewportSize({width,height:width<700?844:1000});
  await open('S04');for(const area of ['files','collections','processing','search','candidates','artifacts']){await act('kw-area',area).click();await layout(`资料中心 ${area}`)}
  await act('kw-area','collections').click();await act('kw-collection-new').click();await layout('资料集表单');await close();
  await open('S13');for(const tab of ['roles','capabilities','limits','release']){await act('config-tab',tab).click();await layout(`Agent ${tab}`)}
  await act('config-tab','roles').click();await act('cap-role','0').click();await layout('角色配置表单');await close();
}
assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);await writeFile(`${root}/ui-delivery/.checks/prd-v12.json`,JSON.stringify({status:'PASS',layouts,errors,externalRequests:requests},null,2));
await browser.close();console.log('PASS: 真实文件批量读取与重复识别、资料集版本、限定检索、引用保护、角色与能力发布条件、来源范围、停止条件、固定成果导出和五种宽度检查。');

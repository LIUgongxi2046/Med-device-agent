import assert from 'node:assert/strict';
import {writeFile,readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {chromium} from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';

const root='/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent';
const browser=await chromium.launch({headless:true,executablePath:'/Users/liuhaoxian/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-x64/chrome-headless-shell'});
const page=await browser.newPage({viewport:{width:1440,height:1000},reducedMotion:'reduce'});page.setDefaultTimeout(10000);
const errors=[],requests=[],layouts=[];page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(/^https?:/.test(request.url()))requests.push(request.url())});
const active=()=>page.locator('.screen.active'),modal=()=>page.locator('#product-dialog');
const act=(name,value)=>active().locator(`[data-action="${name}"]${value===undefined?'':`[data-value="${value}"]`}`).first();
const field=name=>modal().locator(`[name="${name}"]`),submit=()=>modal().locator('button[type="submit"]').click(),close=()=>modal().locator('[data-action="dialog-close"]').first().click();
const open=async id=>page.evaluate(id=>showScreen(id),id);
await page.goto(pathToFileURL(`${root}/设计稿.html`).href+'?scenario=complex#human-review');
assert.equal(await active().getAttribute('id'),'S11');
await act('navigate','S09').click();await active().locator('.reader-operations>summary').click();
assert.match(await active().locator('.document-sheet>h2').innerText(),/产品描述与型号配置/);
assert.equal(await active().locator('.research-question').count(),6);
assert.match(await active().locator('#review-readiness').innerText(),/5 项研究问题/);
await active().locator('.research-question').nth(1).locator('summary').first().click();assert.match(await active().locator('.research-question').nth(1).innerText(),/500 Hz/);
assert.equal(await modal().evaluate(node=>node.open),false);
await act('research-locate','task-5:1').click();assert.match(await active().locator('.document-outline button.active').innerText(),/性能指标/);
await act('research-evidence','task-5:1').click();await field('note').fill('演示流程核查：原始条件与前置问题尚未确认。');await field('status').selectOption('已核验');await submit();assert.match(await modal().innerText(),/前置问题尚未完成/);await field('status').selectOption('待补证');await submit();await close();
await act('research-evidence','task-5:0').click();await field('note').fill('演示流程核查：选定型号清单和附件矩阵均为已核准演示版本。');await field('status').selectOption('已核验');await submit();await close();assert.match(await active().locator('.research-question').first().innerText(),/已核验/);assert.match(await active().locator('#review-readiness').innerText(),/4 项研究问题/);
await act('research-tab','task-5:activity').click();assert.match(await active().locator('.research-panel-body').innerText(),/人工处理/);
await act('research-tab','task-5:roles').click();assert.equal(await active().locator('.role-handoff').count(),6);assert.match(await active().locator('.role-handoff').nth(3).innerText(),/技术字段核验/);
await act('research-tab','task-5:questions').click();await act('research-gru','task-5:1').click();assert.match(await page.locator('#conversation-agent .assistant-context').innerText(),/确认采样频率冲突/);assert.match(await page.locator('#assistant-prompt').inputValue(),/采样频率/);
await page.locator('#assistant-prompt').fill('补充两份验证记录的模式与适用条件');await page.locator('#assistant-composer button[type="submit"]').click();
await page.locator('#conversation-agent [data-action="assistant-card"]').filter({hasText:'登记为待处理要求'}).click();await field('confirm').check();await submit();
await page.locator('[data-action="assistant-close"]').click();await act('research-tab','task-5:activity').click();assert.match(await active().locator('.research-panel-body').innerText(),/补充两份验证记录/);
await act('research-artifacts').click();assert.equal(await active().getAttribute('id'),'S09');assert.match(await active().locator('.research-artifact').innerText(),/产品描述与型号配置/);
await act('kw-artifact','task-5').click();const downloaded=page.waitForEvent('download');await modal().locator('[data-action="kw-export-new"]').click();await (await downloaded).saveAs(`${root}/ui-delivery/.checks/complex-artifact.md`);
assert.match(await readFile(`${root}/ui-delivery/.checks/complex-artifact.md`,'utf8'),/设计演示/);assert.equal(await active().getAttribute('id'),'S09');assert.match(await readFile(`${root}/ui-delivery/.checks/complex-artifact.md`,'utf8'),/data:image\/png;base64/);
await open('S04');assert.equal(await active().locator('.library-progress button').count(),4);await act('kw-progress','files').click();assert.ok((await active().locator('[data-material-row]').allTextContents()).every(text=>text.includes('待核准')||text.includes('等待解析')));
await act('kw-area','collections').click();assert.equal(await active().locator('tbody tr').count(),3);await act('kw-collection-open','demo-set-1').click();assert.match(await active().innerText(),/6 项成员/);
await act('kw-source','sample-3:v1.0').click();assert.match(await active().locator('.library-next-step').innerText(),/等待解析/);assert.match(await active().locator('.processing-strip').innerText(),/OCR 待核查/);
for(const width of [1920,1440,1024,768,390,320]){
  await page.setViewportSize({width,height:width<700?844:1000});await open('S09');
  for(const tab of ['questions','roles','activity','artifacts']){await act('research-tab',`task-5:${tab}`).click();const value=await page.evaluate(()=>({width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth,body:document.querySelector('.research-panel-body').scrollHeight}));layouts.push({...value,tab});assert.equal(value.overflow,false,`${width} ${tab}`)}
  await open('S04');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
}
assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);await writeFile(`${root}/ui-delivery/.checks/continuous-workspace.json`,JSON.stringify({status:'PASS',layouts,errors,externalRequests:requests},null,2));
await browser.close();console.log('PASS: 复杂演示问题、依赖条件、人工补证、Gru 要求登记、当前页面成果、固定下载、资料处理入口与六种宽度。');

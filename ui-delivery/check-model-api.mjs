import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { chromium } from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';

const root='/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent';
const browser=await chromium.launch({headless:true,executablePath:'/Users/liuhaoxian/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-x64/chrome-headless-shell'});
const page=await browser.newPage({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
page.setDefaultTimeout(10000);
const errors=[],requests=[],layouts=[];
page.on('pageerror',error=>errors.push(error.message));
page.on('request',request=>{if(/^https?:/.test(request.url()))requests.push(request.url())});
const active=()=>page.locator('.screen.active');
const dialog=()=>page.locator('#product-dialog');
const action=(name,value)=>active().locator(`[data-action="${name}"]${value===undefined?'':`[data-value="${value}"]`}`).first();
const field=name=>dialog().locator(`[name="${name}"]`);
const submit=()=>dialog().locator('button[type="submit"]').click();
const close=()=>dialog().locator('[data-action="dialog-close"]').first().click();
const open=async()=>{await page.evaluate(()=>showScreen('S13'));await action('agent-area','models').click()};
async function confirm(reason){await field('reason').fill(reason);await field('confirm').check();await submit()}
async function layout(label){
  const result=await page.evaluate(label=>({label,width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth,dialogOverflow:document.querySelector('dialog[open]')?document.querySelector('dialog[open]').scrollWidth>document.querySelector('dialog[open]').clientWidth:false}),label);
  layouts.push(result);assert.equal(result.overflow,false,label);assert.equal(result.dialogOverflow,false,label+' 弹窗');
}
async function fillConnection(){
  await field('name').fill('OpenAI 官方接口');await field('provider').fill('OpenAI');
  await field('protocol').selectOption('openai-responses');await field('modelId').fill('gpt-4.1');
  await field('baseUrl').fill('https://api.openai.com/v1');
  await field('license').fill('接口资料登记；未获调用授权，不发送企业资料。');
}

await page.goto(pathToFileURL(`${root}/设计稿.html`).href);
await open();assert.match(await active().innerText(),/尚未登记模型 API/);
assert.equal(await active().locator('[data-api-row]').count(),0);
await action('api-new').click();assert.equal(await field('apiKey').getAttribute('type'),'password');
assert.equal(await field('apiKey').inputValue(),'');
await fillConnection();
await field('scope').selectOption('projects');await submit();assert.match(await page.locator('#product-dialog-error').innerText(),/至少选择一个/);
await field('projectIds').first().check();
await field('baseUrl').fill('https://api.openai.com/v1?view=models');await submit();assert.match(await page.locator('#product-dialog-error').innerText(),/查询参数/);
await field('baseUrl').fill('https://api.openai.com/v1');await field('timeout').fill('0');await submit();
assert.equal(await field('timeout').evaluate(node=>node.validity.rangeUnderflow),true);
await field('timeout').fill('60');await submit();assert.equal(await dialog().isVisible(),false);
assert.match(await active().locator('.api-detail-layout').innerText(),/待配置凭据/);
assert.match(await active().innerText(),/gpt-4.1/);assert.match(await active().innerText(),/配置 v1/);
await action('api-verify').click();assert.match(await dialog().innerText(),/请设置 API Key/);assert.match(await dialog().innerText(),/未发送/);await close();
await action('api-credential').click();assert.equal(await field('apiKey').inputValue(),'');await close();
await action('api-edit').click();await field('credentialRef').fill('OPENAI_API_KEY');await field('budget').fill('10');await field('currency').selectOption('USD');await submit();
assert.match(await active().innerText(),/配置 v2/);assert.match(await active().innerText(),/已登记引用 · 等待解析/);
await action('api-verify').click();assert.match(await dialog().innerText(),/验证服务尚未接入/);assert.match(await dialog().innerText(),/未产生/);await close();
await action('api-version','api-1:1').click();assert.match(await dialog().innerText(),/历史版本不保存密钥/);assert.doesNotMatch(await dialog().innerText(),/OPENAI_API_KEY/);await submit();
await action('api-list').click();assert.equal(await active().locator('[data-api-row]').count(),1);
await action('api-new').click();await fillConnection();await submit();assert.match(await page.locator('#product-dialog-error').innerText(),/名称已存在/);await close();
await active().locator('[data-form="api-search"] input').fill('OpenAI');await active().locator('[data-form="api-search"] button[type="submit"]').click();assert.equal(await active().locator('[data-api-row]').count(),1);
await active().locator('[data-form="api-search"] select').selectOption('已停用');await active().locator('[data-form="api-search"] button[type="submit"]').click();assert.match(await active().innerText(),/没有符合条件/);
await action('api-reset').click();await page.locator('[data-api-change="size"]').selectOption('20');assert.equal(await page.locator('[data-api-change="size"]').inputValue(),'20');
assert.equal(await action('api-page').isDisabled(),true);
await action('api-open','api-1').click();await action('api-disable').click();await confirm('暂不开放此接口。');
assert.match(await active().locator('.api-detail-layout').innerText(),/已停用/);assert.equal(await action('api-verify').isDisabled(),true);
await action('api-restore').click();await confirm('恢复配置登记，等待授权与验证。');assert.match(await active().locator('.api-detail-layout').innerText(),/未验证/);
await action('agent-area','agents').click();await action('config-select','D-1.5').click();await action('config-tab','model').click();
assert.equal(await active().locator('[name="model"] option[value="api-1"]').count(),0);
assert.match(await active().locator('.api-binding').innerText(),/已验证并启用 0 项/);
await action('api-home').click();await action('api-open','api-1').click();

for(const width of [1440,1024,768,390,320]){
  await page.setViewportSize({width,height:width<700?844:1000});
  await layout('连接详情');await action('api-edit').click();await layout('配置表单');
  await field('auth').selectOption('header');assert.equal(await field('header').isVisible(),true);
  await field('auth').selectOption('none');assert.equal(await field('apiKey').isVisible(),false);
  await field('auth').selectOption('bearer');assert.equal(await field('apiKey').isVisible(),true);
  await field('scope').selectOption('projects');assert.equal(await field('projectIds').first().isVisible(),true);
  await layout('鉴权与授权表单');await close();await action('api-list').click();await layout('连接列表');await action('api-open','api-1').click();
}
await page.setViewportSize({width:1440,height:1000});await action('api-clear').click();await confirm('清除尚未解析的凭据引用。');
assert.match(await active().locator('.api-detail-layout').innerText(),/待配置凭据/);assert.doesNotMatch(await active().locator('.api-facts').innerText(),/OPENAI_API_KEY/);
await action('api-delete').click();await field('reason').fill('完成配置界面核查。');await submit();
assert.equal(await field('confirm').evaluate(node=>node.validity.valueMissing),true);await field('confirm').check();await submit();
assert.match(await active().innerText(),/尚未登记模型 API/);
await action('agent-area','agents').click();await page.locator('#agent-launcher').click();
await page.locator('#assistant-prompt').fill('查看模型配置');await page.locator('#assistant-composer select').selectOption('question');await page.locator('#assistant-composer button[type="submit"]').click();
await page.locator('.chat-message.assistant').last().locator('[data-action="assistant-card"]').click();
assert.equal(await active().locator('[data-action="agent-area"][data-value="models"]').getAttribute('aria-pressed'),'true');
await page.locator('[data-action="assistant-close"]').click();
await action('api-new').click();await fillConnection();await submit();await page.reload();await open();assert.match(await active().innerText(),/尚未登记模型 API/);
await page.goto(pathToFileURL(`${root}/设计稿.html`).href+'?panel=model-api#agents');
assert.equal(await active().locator('[data-action="agent-area"][data-value="models"]').getAttribute('aria-pressed'),'true');
assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
await writeFile(`${root}/ui-delivery/.checks/model-api.json`,JSON.stringify({status:'PASS',errors,externalRequests:requests,layouts,credentialValidation:'NOT_RUN：未提供真实密钥，未执行网络验证。'},null,2));
await browser.close();
console.log('PASS: 模型 API 入口、新增编辑、字段校验、授权范围、版本、筛选、停用恢复、清除删除、Agent 选择限制、Gru 入口与五种宽度检查。未发送外部请求。');

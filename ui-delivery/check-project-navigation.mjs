import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {chromium} from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';

const root=process.cwd();
const browser=await chromium.launch({headless:true,executablePath:'/Users/liuhaoxian/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-x64/chrome-headless-shell'});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
page.setDefaultTimeout(8000);
const errors=[];page.on('pageerror',error=>errors.push(error.message));
const active=()=>page.locator('.screen.active');
const action=(name,value)=>active().locator(`[data-action="${name}"]${value===undefined?'':`[data-value="${value}"]`}`).first();
const screen=async id=>assert.equal(await active().getAttribute('id'),id);
await page.goto(pathToFileURL(`${root}/设计稿.html`).href+'#projects');
await active().locator('[data-demo-project]:visible').first().click();await screen('S18');
const projectName=await active().locator('.project-location [aria-current="page"]').innerText();
assert.equal(await page.locator('.topbar>strong').count(),0);
assert.equal(await active().locator(':scope>.heading').count(),0);
assert.equal(await active().locator('.project-summary h1').innerText(),projectName);
assert.equal(await active().locator('.project-toolbar>.project-location').count(),1);
const navigationBox=await active().locator('.project-toolbar>.project-location').boundingBox(),actionsBox=await active().locator('.project-toolbar>.actions').boundingBox();
assert.ok(Math.abs(navigationBox.y+navigationBox.height/2-actionsBox.y-actionsBox.height/2)<2);
await action('navigate','S06').click();
await active().locator('[data-change="task-filter"]').selectOption('待人工审查');
await action('task-open','task-1').click();await screen('S11');
assert.match(await active().locator('.project-location').innerText(),new RegExp(projectName));
await action('task-step','S07').click();await screen('S07');
assert.equal(await active().locator('h1').innerText(),'任务配置');
assert.equal(await active().locator('[data-action="create-task"]').count(),0);
await action('task-step','S09').click();await screen('S09');
const original=await active().locator('[data-document-module="0"]').innerText();
await active().locator('[data-document-module="0"]').fill(original.slice(0,-1));
await action('navigate','S06').click();await screen('S06');
assert.equal(await active().locator('[data-change="task-filter"]').inputValue(),'待人工审查');
await page.goBack();await screen('S09');
assert.equal(await active().locator('[data-document-module="0"]').innerText(),original.slice(0,-1));
assert.equal(await active().locator('#document-save-state').innerText(),'修改尚未保存');
await page.goBack();await screen('S07');
await page.goForward();await screen('S09');
await action('navigate','S18').count().then(count=>assert.equal(count,0));
await action('navigate','S06').click();await action('navigate','S18').click();
await action('project-tasks','writing').click();
assert.equal(await active().locator('[data-change="task-filter"]').inputValue(),'writing');
await action('navigate','S18').click();await action('project-tasks','review').click();
assert.equal(await active().locator('[data-change="task-filter"]').inputValue(),'review');
await action('navigate','S18').click();await action('navigate','S07').click();
await action('material-open','material-8').click();
await action('approve-material').click();
await page.locator('#product-dialog [name="read"]').check();
await page.locator('#product-dialog [name="scope"]').check();
await page.locator('#product-dialog [name="reason"]').fill('核对产品技术要求模板的名称、版本和适用范围。');
await page.locator('#product-dialog [type="submit"]').click();
await action('source-return').click();await screen('S07');
const types=await active().locator('#document-type option').evaluateAll(items=>items.map(item=>({value:item.value,label:item.textContent})));
assert.equal(types.length,13);
const checked=[];
for(const type of types){
  await active().locator('#document-type').selectOption(type.value);
  const expectedChapters=await active().locator('.plan-row').count();
  await action('create-task').click();await screen('S09');
  if(type.value==='requirements'){
    await page.goBack();await screen('S07');assert.equal(await active().locator('h1').innerText(),'任务配置');
    await page.goForward();await screen('S09');
  }
  assert.equal(await active().locator('.document-sheet>h2').innerText(),type.label);
  assert.equal(await active().locator('.document-module').count(),expectedChapters);
  assert.equal(await active().locator('.evidence-fold[open]').count(),0);
  assert.equal(await active().locator('.writing-aside>.document-outline').count(),1);
  assert.equal(await active().locator('.writing-aside .dossier-banner').count(),1);
  await action('task-step','S11').click();await screen('S11');
  assert.equal(await active().locator('.source-fold,.document-evidence,[data-action="module-comment"],[contenteditable="true"]').count(),0);
  assert.equal(await action('sign-document').isDisabled(),true);
  await action('task-step','S07').click();
  assert.match(await active().locator('.facts-grid').innerText(),new RegExp(type.label));
  if(type.value==='requirements'){
    await action('task-template').click();
    await page.locator('#product-dialog [name="template"]').selectOption('material-8');
    await page.locator('#product-dialog [name="reason"]').fill('产品技术要求采用已核准的产品技术要求模板。');
    await page.locator('#product-dialog [name="applicable"]').check();
    await page.locator('#product-dialog [type="submit"]').click();
    assert.match(await active().locator('.facts-grid').innerText(),/产品技术要求模板/);
  }
  await action('task-step','S09').click();
  if(type.value==='ifu'){
    await active().locator('.source-fold>summary').click();
    await active().locator('#source-inspector [data-action="dossier-insert"]').click();
    await page.locator('#product-dialog [name="file"]').selectOption('material-0');
    await page.locator('#product-dialog [type="submit"]').click();
    assert.match(await active().locator('#source-inspector').innerText(),/产品/);
    await action('source-original').click();await screen('S05');
    await action('source-return').click();await screen('S09');
    assert.equal(await active().locator('.document-sheet>h2').innerText(),type.label);
  }
  if(type.value==='review'){
    await page.locator('#agent-launcher').click();
    assert.equal(await page.locator('#conversation-agent').isVisible(),true);
    await page.keyboard.press('Escape');
    await active().locator('.reader-operations>summary').click();
    await action('research-artifacts').click();await action('kw-artifact').click();
    const downloadEvent=page.waitForEvent('download');
    await page.locator('#product-dialog [data-action="kw-export-new"]').click();
    const download=await downloadEvent,stream=await download.createReadStream(),chunks=[];
    for await(const chunk of stream)chunks.push(chunk);
    assert.match(Buffer.concat(chunks).toString('utf8'),/来源：尚未关联/);
  }
  checked.push({type:type.label,chapters:expectedChapters});
  await action('navigate','S18').click();await action('navigate','S07').click();
}
await action('navigate','S18').click();await action('navigate','S03').click();
await active().locator('[data-page-next]').click();
const pagination=await active().locator('[data-page-info]').innerText();
await active().locator('[data-demo-project]:visible').first().click();await screen('S18');
assert.notEqual(await active().locator('.project-location [aria-current="page"]').innerText(),projectName);
await action('navigate','S03').click();assert.equal(await active().locator('[data-page-info]').innerText(),pagination);
for(const width of [1440,768,390,320]){
  await page.setViewportSize({width,height:1000});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
}
assert.deepEqual(errors,[]);
await writeFile(`${root}/ui-delivery/.checks/project-navigation.json`,JSON.stringify({status:'PASS',types:checked,history:true,filters:true,errors},null,2));
await browser.close();
console.log('PASS: 项目层级返回、任务筛选、浏览器前进后退、正文编辑保留、13 种文档流程及引用返回。');

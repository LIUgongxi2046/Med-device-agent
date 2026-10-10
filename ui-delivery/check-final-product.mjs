import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {chromium} from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
const root=process.cwd(),browser=await chromium.launch({headless:true,executablePath:'/Users/liuhaoxian/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-x64/chrome-headless-shell'});
const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(6000);
const errors=[];page.on('pageerror',error=>errors.push(error.message));
const active=()=>page.locator('.screen.active'),dialog=()=>page.locator('#product-dialog'),action=(name,value)=>page.locator(`[data-action="${name}"]${value===undefined?'':`[data-value="${value}"]`}`).filter({visible:true}).first();
const open=id=>page.evaluate(id=>showScreen(id),id);
const close=()=>dialog().locator('[data-action="dialog-close"]').first().click();
await page.goto(pathToFileURL(`${root}/设计稿.html`).href);
const routes=['S01','S02','S03','S04','S05','S06','S07','S09','S11','S13','S15','S16','S17','S18'];
for(const id of routes){await open(id);assert.equal(await active().getAttribute('id'),id);assert.ok((await active().innerText()).length>30)}
await open('S01');assert.equal(await active().locator('.final-scene').count(),7);
assert.equal(await active().locator('[data-action="final-scene"]:disabled').count(),6);assert.equal(await action('final-scene','document').isEnabled(),true);
assert.equal(await active().locator('.final-scene .status').count(),0);
assert.equal(await active().getByRole('button',{name:'能力说明',exact:true}).count(),7);
assert.equal(await active().getByRole('button',{name:'查看成果',exact:true}).count(),0);
const sceneImages=await active().locator('.scene-visual img').evaluateAll(async images=>{await Promise.all(images.map(image=>image.decode()));return images.map(image=>({source:image.getAttribute('src'),width:image.naturalWidth,height:image.naturalHeight}))});assert.equal(new Set(sceneImages.map(image=>image.source)).size,7);assert.ok(sceneImages.every(image=>image.width>=1024&&image.height>=680));
for(const scene of ['document','quality','regulation','report','translation','market','question']){await action('final-scene-info',scene).click();assert.equal(await dialog().locator('.scene-capability li').count(),3);assert.equal(await dialog().locator('[data-action="final-scene"]').isEnabled(),scene==='document');assert.match(await dialog().innerText(),/需要提供/);assert.match(await dialog().innerText(),/交付内容/);await dialog().locator('.scene-capability img').evaluate(image=>image.decode());await close()}
await action('final-scene','document').click();assert.equal(await active().getAttribute('id'),'S07');
assert.equal(await active().locator('[name="final-scene"] option:disabled').count(),6);
const types=await active().locator('#document-type option').evaluateAll(nodes=>nodes.map(n=>n.value));assert.equal(types.length,13);
for(const type of types){await active().locator('#document-type').selectOption(type);assert.ok(await active().locator('[data-action="final-map"]').count())}
await active().locator('#document-type').selectOption('requirements');
await action('final-map','requirements:0').click();assert.equal(await dialog().locator('[name="method"] option').count(),4);await close();
await action('final-start').click();await dialog().locator('[name="confirm"]').check();await dialog().locator('[type="submit"]').click();assert.equal(await active().getAttribute('id'),'S09');
await close();
await action('final-check').click();assert.ok(await dialog().locator('tbody tr').count());await close();
await action('final-deliveries').count().then(async count=>{if(!count)await active().locator('.reader-operations>summary').click()});
await action('final-deliveries').click();assert.equal(await dialog().locator('tbody tr').count(),4);await action('final-preview','正文').click();assert.ok(await dialog().locator('h3').count());await close();
await open('S18');await action('final-members').click();assert.equal(await dialog().locator('tbody tr').count(),3);await action('final-member-edit','0').click();assert.equal(await dialog().locator('[name="sign"]').count(),1);await close();
await open('S04');for(const tab of ['templates','rules','regulations','files']){await action('final-resource',tab).click();assert.ok((await active().innerText()).length>50)}
await open('S13');for(const tab of ['directory','models','capability','publish']){await action('final-agent-area',tab).click();assert.ok((await active().innerText()).length>50)}
await action('final-agent-area','directory').click();await action('final-agent-config','document').click();await dialog().locator('[name="goal"]').fill('依据已核准模板准备产品技术要求');await dialog().locator('[type="submit"]').click();await action('final-agent-config','document').click();assert.equal(await dialog().locator('[name="goal"]').inputValue(),'依据已核准模板准备产品技术要求');await close();
await action('final-agent-area','capability').click();await action('final-role','0').click();await dialog().locator('[name="stop"]').fill('资料缺少时请求项目负责人确认');await dialog().locator('[name="tools"][value="限定检索"]').check();await dialog().locator('[type="submit"]').click();await action('final-role','0').click();assert.equal(await dialog().locator('[name="tools"][value="限定检索"]').isChecked(),true);assert.equal(await dialog().locator('[name="stop"]').inputValue(),'资料缺少时请求项目负责人确认');await close();
await open('S15');for(const tab of ['dimensions','feedback','usage','audit']){await action('final-eval-area',tab).click();assert.ok((await active().innerText()).length>10)}
await action('final-eval-area','dimensions').click();await active().locator('[name="final-baseline"]').selectOption('D-1.5');const deltas=await active().locator('tbody tr td:last-child').allTextContents();assert.ok(deltas.every(value=>value==='0'));
await action('final-score').click();for(let index=0;index<5;index++)await dialog().locator(`[name="score-${index}"]`).fill(String(90+index));await dialog().locator('[name="reason"]').fill('逐章节核对内容与引用');await dialog().locator('[type="submit"]').click();await action('final-score').click();assert.equal(await dialog().locator('[name="score-3"]').inputValue(),'93');await close();
await action('final-release').click();await dialog().locator('[name="reason"]').fill('全部维度完成复核，批准当前范围发布');await dialog().locator('[name="confirm"]').check();await dialog().locator('[type="submit"]').click();await open('S13');await action('final-agent-area','publish').click();await action('final-release-history').click();assert.match(await dialog().innerText(),/全部维度完成复核/);await close();
await open('S15');await action('final-eval-area','usage').click();const monthly=await active().innerText();await active().locator('[name="usage-period"]').selectOption('week');assert.notEqual(await active().innerText(),monthly);await action('final-budget').click();await dialog().locator('[name="cost"]').fill('80');await dialog().locator('[type="submit"]').click();await action('final-budget').click();assert.equal(await dialog().locator('[name="cost"]').inputValue(),'80');await close();
await open('S16');for(const mode of ['private','saas','service']){await action('final-deployment',mode).click();assert.equal(await action('final-deployment',mode).getAttribute('aria-pressed'),'true')}
const layouts=[];for(const width of [1440,1024,768,390,320]){await page.setViewportSize({width,height:1000});for(const id of routes){await open(id);const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);layouts.push({width,id,overflow});assert.equal(overflow,false,`${id}: ${width}`)}}
assert.deepEqual(errors,[]);await writeFile(`${root}/ui-delivery/.checks/final-product.json`,JSON.stringify({status:'PASS',routes:routes.length,types:types.length,layouts,errors},null,2));await browser.close();console.log('PASS: 最终状态设计的14个页面、13种文档、场景配置、资料治理、项目授权、Agent管理、评测运营与窗口适配。');

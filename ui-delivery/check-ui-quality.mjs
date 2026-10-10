import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { chromium } from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';

const root = '/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent';
const verify = process.argv.includes('--verify');
const browser = await chromium.launch({ headless: true, executablePath: '/Users/liuhaoxian/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-x64/chrome-headless-shell' });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
await page.goto(pathToFileURL(`${root}/设计稿.html`).href);
const routes = ['S01','S02','S03','S04','S05','S06','S07','S09','S11','S13','S15','S16','S17','S18'];
const report = { pages: [], layouts: [], controls: {}, errors };

async function measure() {
  const active=page.locator('.screen.active');
  assert.equal(await page.locator('.topbar>strong').count(),0);
  assert.equal(await active.locator('.public-entry,.complex-case-entry,#admin-description,#admin-scope,.config-policy,.library-side-note').count(),0);
  assert.doesNotMatch(await active.innerText(),/公开原件与需求字段|复杂项目审查案例|角色定义取自 PRD|资料读取、任务创建、人工审查、导出和签发须分别/);
  return page.evaluate(() => {
    const rgba = value => value.match(/[\d.]+/g).map(Number);
    const blend = (front, back) => front.slice(0,3).map((channel,index) => channel * (front[3] ?? 1) + back[index] * (1 - (front[3] ?? 1)));
    const luminance = rgb => rgb.slice(0,3).map(value => value / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4).reduce((sum,value,index) => sum + value * [.2126,.7152,.0722][index],0);
    const rows = [];
    const roots = [...document.querySelectorAll('.screen.active,.sidebar,.topbar,#conversation-agent:not([hidden]),dialog[open]')];
    for (const root of roots) {
      for (const element of [root,...root.querySelectorAll('*')]) {
        if (!element.checkVisibility({ checkOpacity:true, checkVisibilityCSS:true }) || element.matches(':disabled') || element.closest('fieldset:disabled,.sr-only') || element.tagName === 'OPTION') continue;
        const text = [...element.childNodes].filter(node => node.nodeType === Node.TEXT_NODE).map(node => node.textContent.trim()).join('');
        if (!text) continue;
        const style = getComputedStyle(element);
        const ancestors = [];
        let gradient = false;
        for (let current = element; current; current = current.parentElement) {
          const s = getComputedStyle(current);
          if (Number(s.opacity) !== 1) { gradient = true; break; }
          if (s.backgroundImage !== 'none') { gradient = true; break; }
          const background = rgba(s.backgroundColor);
          ancestors.push(background);
          if ((background[3] ?? 1) === 1) break;
        }
        const background = ancestors.reverse().reduce((back,front) => blend(front,back),[255,255,255]);
        const foreground = blend(rgba(style.color),background);
        const l1 = luminance(foreground), l2 = luminance(background);
        const ratio = (Math.max(l1,l2)+.05)/(Math.min(l1,l2)+.05);
        const size = parseFloat(style.fontSize);
        const required = size >= 24 || (size >= 18.667 && parseInt(style.fontWeight) >= 700) ? 3 : 4.5;
        rows.push({ selector: element.tagName.toLowerCase() + (element.className ? '.' + String(element.className).trim().replaceAll(' ','.') : ''), text: text.slice(0,35), size, color: style.color, background: background.map(Math.round), contrast: gradient ? null : Number(ratio.toFixed(2)), lowContrast: !gradient && ratio < required, small: size < 12 });
      }
    }
    return { screen: document.querySelector('.screen.active').id, width: innerWidth, overflow: document.documentElement.scrollWidth > innerWidth, title: getComputedStyle(document.querySelector('.screen.active h1')).fontSize, count: rows.length, issues: rows.filter(row => row.small || row.lowContrast), skipped: rows.filter(row => row.contrast === null).length };
  });
}

for (const id of routes) {
  await page.evaluate(id => showScreen(id),id);
  report.pages.push(await measure());
}
const systemMenus=await page.locator('[data-admin-module]').evaluateAll(nodes=>nodes.map(node=>node.dataset.adminModule));
for(const key of systemMenus){
  await page.locator(`[data-admin-module="${key}"]`).first().click();
  assert.equal(await page.locator('.screen.active .admin-detail p').count(),0);
  assert.ok(await page.locator('.screen.active [data-paged-list="admin"]').isVisible());
  report.pages.push({...(await measure()),context:`系统管理 ${key}`});
}
await page.evaluate(() => showScreen('S13'));
await page.locator('#S13 [data-action="agent-area"][data-value="models"]').click();
report.pages.push({ ...(await measure()), context:'模型 API 空列表' });
await page.locator('#S13 [data-action="api-new"]').first().click();
report.pages.push({ ...(await measure()), context:'模型 API 表单' });
await page.locator('#product-dialog [name="name"]').fill('OpenAI 官方接口');
await page.locator('#product-dialog [name="provider"]').fill('OpenAI');
await page.locator('#product-dialog [name="modelId"]').fill('gpt-4.1');
await page.locator('#product-dialog [name="baseUrl"]').fill('https://api.openai.com/v1');
await page.locator('#product-dialog [name="license"]').fill('接口资料登记；未获调用授权，不发送企业资料。');
await page.locator('#product-dialog button[type="submit"]').click();
report.pages.push({ ...(await measure()), context:'模型 API 详情' });
await page.locator('#S13 [data-action="api-verify"]').first().click();
report.pages.push({ ...(await measure()), context:'模型 API 验证条件' });
await page.locator('#product-dialog button[type="submit"]').click();
await page.locator('#S13 [data-action="api-list"]').click();
report.pages.push({ ...(await measure()), context:'模型 API 连接列表' });
await page.locator('#S13 [data-action="agent-area"][data-value="agents"]').click();
await page.locator('#S13 [data-action="config-select"][data-value="D-1.5"]').click();
for(const tab of ['roles','capabilities','limits','release']){
  await page.locator(`#S13 [data-action="config-tab"][data-value="${tab}"]`).click();
  report.pages.push({...(await measure()),context:`Agent ${tab}`});
}
await page.locator('#S13 [data-action="config-tab"][data-value="roles"]').click();
await page.locator('#S13 [data-action="cap-role"]').first().click();
report.pages.push({...(await measure()),context:'专业角色表单'});
await page.locator('#product-dialog [data-action="dialog-close"]').first().click();
await page.evaluate(() => showScreen('S04'));
for(const area of ['files','collections','processing','search','candidates','artifacts']){
  await page.locator(`#S04 [data-action="kw-area"][data-value="${area}"]`).click();
  report.pages.push({...(await measure()),context:`资料中心 ${area}`});
}
await page.locator('#S04 [data-action="kw-area"][data-value="collections"]').click();
await page.locator('#S04 [data-action="kw-collection-new"]').click();
report.pages.push({...(await measure()),context:'资料集表单'});
await page.locator('#product-dialog [data-action="dialog-close"]').first().click();
await page.locator('#S04 [data-action="kw-area"][data-value="files"]').click();
await page.locator('#agent-launcher').click();
report.pages.push({ ...(await measure()), context: 'Gru' });
await page.locator('[data-action="assistant-suggestion"][data-value="sources"]').click();
report.pages.push({ ...(await measure()), context: 'Gru 来源' });
await page.locator('[data-action="assistant-close"]').click();

await page.evaluate(()=>showScreen('S01'));
await page.locator('#S01 [data-action="queue-tab"][data-value="all"]').click();
await page.locator('#S01 [data-action="task-open"][data-value="task-5"]').click();
await page.locator('.screen.active [data-action="navigate"][data-value="S09"]').first().click();
await page.locator('.screen.active .reader-operations>summary').click();
for(const tab of ['questions','roles','activity','artifacts']){
  await page.locator(`.screen.active [data-action="research-tab"][data-value="task-5:${tab}"]`).first().click();
  report.pages.push({...(await measure()),context:`复杂案例 ${tab}`});
}

await page.locator('.screen.active [data-action="dossier-toggle"]').click();
report.pages.push({...(await measure()),context:'申请资料六类清单'});
await page.locator('.screen.active [data-action="dossier-item"]').first().click();
report.pages.push({...(await measure()),context:'申请项适用性与资料关联'});
await page.locator('#product-dialog [data-action="dialog-close"]').first().click();
await page.locator('.screen.active [data-action="dossier-toggle"]').click();
await page.locator('.screen.active .evidence-fold').nth(1).locator('summary').first().click();
await page.locator('.screen.active [data-action="dossier-citation"][data-value="task-5:1:2"]').first().click();
report.pages.push({...(await measure()),context:'正文图表与原页引用'});
await page.locator('.screen.active .evidence-fold[open] [data-action="dossier-insert"]').first().click();
report.pages.push({...(await measure()),context:'插入引用表单'});
await page.locator('#product-dialog [data-action="dialog-close"]').first().click();
await page.locator('.screen.active [data-action="dossier-original"]').click();
report.pages.push({...(await measure()),context:'资料中心原件页面'});
await page.locator('.screen.active [data-action="source-return"]').click();

await page.evaluate(()=>showScreen('S01'));
await page.goto(pathToFileURL(`${root}/设计稿.html`).href+'?scenario=public#document-workspace');
report.pages.push({...(await measure()),context:'真实公开资料与正文图文'});
await page.locator('.screen.active .evidence-fold').first().locator('summary').first().click();
report.pages.push({...(await measure()),context:'展开章节证据'});
await page.locator('.screen.active [data-action="navigate"][data-value="S11"]').first().click();
report.pages.push({...(await measure()),context:'全文阅读与标注'});
await page.locator('.screen.active .figure-select').first().click();
await page.locator('.selection-popover [data-action="comment-image"]').click();
report.pages.push({...(await measure()),context:'选区标注表单'});
await page.locator('#product-dialog [data-action="dialog-close"]').first().click();
await page.locator('.screen.active .review-decision>summary').click();
await page.locator('.screen.active [data-action="export-document"]').click();
report.pages.push({...(await measure()),context:'正文与独立审查材料'});
await page.locator('#product-dialog [data-action="dialog-close"]').first().click();

for (const width of [1920,1440,1024,768,390,320]) {
  await page.setViewportSize({ width, height: width < 700 ? 844 : 1000 });
  for (const id of routes) {
    await page.evaluate(id => showScreen(id),id);
    report.layouts.push({ screen:id, width, overflow: await page.evaluate(() => document.documentElement.scrollWidth > innerWidth) });
  }
  await page.locator('#agent-launcher').click();
  const bounds = await page.locator('#assistant-prompt').boundingBox();
  const close = await page.locator('[data-action="assistant-close"]').boundingBox();
  const sizes = await page.locator('.conversation-agent button').evaluateAll(nodes => nodes.filter(node => node.checkVisibility()).map(node => Math.round(node.getBoundingClientRect().height)));
  report.layouts.push({ screen:'Gru', width, overflow: await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), composerReachable: bounds.y >= 0 && bounds.y + bounds.height <= (width < 700 ? 844 : 1000), closeReachable: close.x >= 0 && close.x + close.width <= width, minButtonHeight: Math.min(...sizes) });
  await page.locator('[data-action="assistant-close"]').click();
}

await page.setViewportSize({ width:1440, height:1000 });
await page.evaluate(() => showScreen('S01'));
await page.locator('#agent-launcher').focus();
await page.keyboard.press('Shift+Tab');
await page.keyboard.press('Tab');
report.controls.focus = await page.locator('#agent-launcher').evaluate(node => ({ style: getComputedStyle(node).outlineStyle, width: getComputedStyle(node).outlineWidth }));
report.controls.images = await page.locator('.gru-avatar').evaluateAll(nodes => nodes.every(node => node.complete && node.naturalWidth > 0));
await page.locator('#agent-launcher').click();
await page.locator('[data-action="assistant-expand"]').click();
await page.waitForFunction(() => document.querySelector('#conversation-agent').getBoundingClientRect().width === 740);
report.controls.expanded = await page.evaluate(() => ({ class: document.querySelector('#conversation-agent').className, width: document.querySelector('#conversation-agent').getBoundingClientRect().width, margin: getComputedStyle(document.querySelector('.app')).marginRight, viewport: innerWidth, label: document.querySelector('[data-action="assistant-expand"]').textContent }));
await page.locator('[data-action="assistant-attach"]').click();
report.controls.dialog = await page.locator('#product-dialog').evaluate(node => ({ visible: node.open, height: node.getBoundingClientRect().height, viewport: innerHeight }));
await page.locator('#product-dialog [data-action="dialog-close"]').first().click();
await page.setViewportSize({ width:1600, height:1000 });
await page.waitForFunction(() => getComputedStyle(document.querySelector('.app')).marginRight === '740px');
for (const id of routes) {
  await page.evaluate(id => showScreen(id),id);
  report.layouts.push({ screen:id, width:1600, mode:'Gru 展开', overflow:await page.evaluate(() => document.documentElement.scrollWidth > innerWidth) });
}
report.controls.docked = await page.evaluate(() => ({ margin:getComputedStyle(document.querySelector('.app')).marginRight, panel:document.querySelector('#conversation-agent').getBoundingClientRect().width, mainRight:document.querySelector('.main').getBoundingClientRect().right, panelLeft:document.querySelector('#conversation-agent').getBoundingClientRect().left }));
await browser.close();
await writeFile(`${root}/ui-delivery/.checks/ui-quality.json`,JSON.stringify(report,null,2));
const unique = [...new Map(report.pages.flatMap(row => row.issues).map(row => [`${row.selector}|${row.color}|${row.size}`,row])).values()];
console.log(JSON.stringify({ pages:report.pages.length, issues:unique, layoutIssues:report.layouts.filter(row => row.overflow || row.composerReachable === false || row.closeReachable === false), controls:report.controls, errors },null,2));
if (verify) {
  assert.equal(unique.length,0,'可见文字字号及纯色背景对比度');
  assert.ok(report.layouts.every(row => !row.overflow && row.composerReachable !== false && row.closeReachable !== false),'页面宽度及对话控件可达');
  assert.ok(report.layouts.filter(row => row.screen === 'Gru' && row.width <= 650).every(row => row.minButtonHeight >= 44),'移动端对话按钮尺寸');
  assert.notEqual(report.controls.focus.style,'none','键盘焦点可见');
  assert.equal(report.controls.images,true,'Gru 图片正常加载');
  assert.equal(report.controls.expanded.width,740,'Gru 展开宽度');
  assert.ok(report.controls.docked.mainRight <= report.controls.docked.panelLeft,'Gru 展开时正文空间');
  assert.ok(report.controls.dialog.visible && report.controls.dialog.height <= report.controls.dialog.viewport,'弹窗可达');
  assert.deepEqual(errors,[]);
  console.log('PASS: 字号、纯色背景文字对比度、14 页面与六种宽度、Gru 操作和焦点检查。');
}

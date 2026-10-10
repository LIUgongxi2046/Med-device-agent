import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { marked } from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/marked/lib/marked.esm.js';
import { getDocument } from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/pdfjs-dist/legacy/build/pdf.mjs';
import { chromium } from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';

const root = '/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent';
const source = await readFile(`${root}/界面规范.md`,'utf8');
const browser = await chromium.launch({ headless:true, executablePath:'/Users/liuhaoxian/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-x64/chrome-headless-shell' });
const page = await browser.newPage({ viewport:{width:674,height:1000} });
await page.setContent(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>医疗器械企业 Agent 平台界面规范</title><style>
  * { box-sizing:border-box; }
  body { margin:0; color:#243247; font:10.5pt/1.8 "PingFang SC","Microsoft YaHei",sans-serif; }
  h1 { font-size:23pt; line-height:1.5; font-weight:600; border-bottom:3px solid #1768c4; padding-bottom:14px; margin:0 0 20px; }
  h2 { font-size:15pt; font-weight:600; margin:26px 0 12px; break-after:avoid; }
  h3 { font-size:12pt; break-after:avoid; }
  p { margin:10px 0; orphans:3; widows:3; }
  table { width:100%; border-collapse:collapse; table-layout:fixed; font-size:9pt; line-height:1.7; margin:16px 0; }
  th { background:#edf4ff; color:#175fae; font-weight:600; }
  th,td { border:1px solid #dce3ec; padding:8px 10px; vertical-align:top; overflow-wrap:anywhere; }
  th:first-child,td:first-child { width:20%; }
  tr { break-inside:avoid; }
  thead { display:table-header-group; }
  a { color:#175fae; text-decoration:none; overflow-wrap:anywhere; }
  code { font-family:"SFMono-Regular",Consolas,monospace; font-size:.92em; background:#f4f6fa; overflow-wrap:anywhere; }
  li { margin:5px 0; }
  @media print { body { -webkit-print-color-adjust:exact; print-color-adjust:exact; } }
</style></head><body>${marked.parse(source)}</body></html>`,{waitUntil:'load'});
await page.emulateMedia({media:'print'});
await page.evaluate(() => document.fonts.ready);
assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),true,'规范文档宽度');
await page.pdf({ path:`${root}/界面规范.pdf`, format:'A4', printBackground:true, displayHeaderFooter:true, margin:{top:'20mm',right:'16mm',bottom:'20mm',left:'16mm'}, headerTemplate:'<div></div>', footerTemplate:'<div style="font:9px sans-serif;color:#526279;width:100%;text-align:center">UI DESIGN SYSTEM · <span class="pageNumber"></span> / <span class="totalPages"></span></div>' });
await browser.close();
const pdf=await getDocument({data:new Uint8Array(await readFile(`${root}/界面规范.pdf`)),useSystemFonts:true}).promise;
const texts=[];
for(let index=1;index<=pdf.numPages;index++) {
  const page=await pdf.getPage(index);
  const content=await page.getTextContent();
  const text=content.items.map(item=>item.str).join('');
  assert.ok(text.length>30,`PDF 第 ${index} 页内容`);
  texts.push(text);
}
assert.ok(texts.join('').includes('Gru'),'PDF 助手规范');
assert.ok(texts.join('').includes('1768C4'),'PDF 颜色规范');
console.log(`PASS: 界面规范 PDF 共 ${pdf.numPages} 页，文字提取、颜色规范和 Gru 规范检查通过。`);
await pdf.destroy();

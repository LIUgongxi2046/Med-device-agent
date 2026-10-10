import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {chromium} from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {getDocument} from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/pdfjs-dist/legacy/build/pdf.mjs';
const root='/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/ui-delivery/assets/evidence';
const browser=await chromium.launch({headless:true,executablePath:'/Users/liuhaoxian/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-x64/chrome-headless-shell'});
const page=await browser.newPage({viewport:{width:800,height:1040},deviceScaleFactor:1});
await page.goto(pathToFileURL(`${root}/design-evidence-v1.html`).href);await page.evaluate(()=>document.fonts.ready);
const manifest=[],embedded={};
for(let index=1;index<=3;index++){
  const node=page.locator(`[data-page="${index}"]`);assert.equal(await node.evaluate(n=>n.scrollHeight<=n.clientHeight),true);
  const filename=`design-evidence-v1-page-${index}.png`;await node.screenshot({path:`${root}/${filename}`});
  embedded[`ui-delivery/assets/evidence/${filename}`]=`data:image/png;base64,${(await readFile(`${root}/${filename}`)).toString('base64')}`;
  manifest.push({file:filename,page:index,width:800,height:1040,sha256:createHash('sha256').update(await readFile(`${root}/${filename}`)).digest('hex'),source:'design-evidence-v1.html',version:'v1.0',nature:'用户授权的演示原页截图'});
}
await page.pdf({path:`${root}/design-evidence-v1.pdf`,width:'800px',height:'1040px',printBackground:true,margin:{top:0,bottom:0,left:0,right:0}});await browser.close();
const pdf=await getDocument({data:new Uint8Array(await readFile(`${root}/design-evidence-v1.pdf`)),useSystemFonts:true}).promise;
assert.equal(pdf.numPages,3);for(let index=1;index<=3;index++){const text=(await (await pdf.getPage(index)).getTextContent()).items.map(x=>x.str).join('');assert.match(text,/设计演示/);assert.ok(text.length>200)}await pdf.destroy();
await writeFile(`${root}/provenance.json`,JSON.stringify(manifest,null,2));console.log('PASS: 三页演示原件 PDF、三张原页截图及来源摘要；未执行视觉识别。');
await writeFile(`${root}/embedded-images.js`,`window.EvidenceImages=${JSON.stringify(embedded)};\n`);

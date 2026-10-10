import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {getDocument} from '/Users/liuhaoxian/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/pdfjs-dist/legacy/build/pdf.mjs';
const base='ui-delivery/assets/public-evidence',records=[];
for(const [name,ext,title] of [['safety-principles-2021','doc','基本原则'],['clinical-evaluation-2021','docx','临床评价']]){
  const original=await readFile(`${base}/${name}.${ext}`),converted=await readFile(`${base}/${name}.pdf`);
  const pdf=await getDocument({data:new Uint8Array(converted),useSystemFonts:true}).promise;const pages=pdf.numPages;assert.ok(pages>0);
  const texts=[];for(let page=1;page<=pages;page++){const text=(await(await pdf.getPage(page)).getTextContent()).items.map(x=>x.str).join(' ');assert.ok(text.trim().length>0);texts.push({page,text});console.log(`${name} 第${page}页：${text.slice(0,85)}`)}
  assert.ok(texts.some(p=>p.text.includes(title)));
  records.push({name,original:`${name}.${ext}`,originalSHA256:createHash('sha256').update(original).digest('hex'),previewSHA256:createHash('sha256').update(converted).digest('hex'),pages,nature:'官方原件转换预览；页码为转换后的阅读位置',text: texts});await pdf.destroy();
}
await writeFile(`${base}/provenance.json`,JSON.stringify(records,null,2));

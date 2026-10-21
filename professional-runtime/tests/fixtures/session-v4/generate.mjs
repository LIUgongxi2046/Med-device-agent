// Fixture generation only. No SDK, worker, provider, socket or database execution.
// Run from a separately installed official pinned RC.2 npm tree, never install at runtime.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const [modules,output]=process.argv.slice(2);
if(!modules||!output)throw new Error('Usage: node generate.mjs /absolute/node_modules /absolute/output-directory');
const require=createRequire(path.join(path.resolve(modules),'fixture-loader.cjs'));
const proof=JSON.parse(fs.readFileSync(path.resolve(here,'../../../../docs/development/registered-tool-runtime-format-provenance.json'),'utf8'));
for(const pkg of ['session-format','session-format-v1-to-v2','session-format-v2-to-v3','session-format-v3-to-v4','llm']){
 const root=path.dirname(require.resolve(`@deepseek-ai/dsh-${pkg}/package.json`));
 if(JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8')).version!=='0.2.1-rc.2')throw new Error('Pinned codec version required');
 const hash=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'lib/index.js'))).digest('hex');
 if(hash!==proof.embedded_sources.find(v=>v.package===pkg).source_sha256)throw new Error('Pinned embedded codec source required');
}
const { releasedV4SessionFormatCodec:codec }=await import(pathToFileURL(require.resolve('@deepseek-ai/dsh-session-format-v3-to-v4')).href);
const logical=JSON.parse(fs.readFileSync(path.join(here,'logical-fixture.json'),'utf8'));
const bytes=Buffer.from([codec.encodeHeader(logical.header,0),...logical.events.map(e=>codec.encodeEvent(e))].map(v=>JSON.stringify(v)+'\n').join(''),'utf8');
fs.mkdirSync(output,{recursive:true});
fs.writeFileSync(path.join(output,'session.v4.jsonl'),bytes);
fs.writeFileSync(path.join(output,'expectations.json'),JSON.stringify({fixture_notice:logical.fixture_notice,session:logical.session,workspace:logical.workspace,request:logical.request,response:logical.response,sha256:crypto.createHash('sha256').update(bytes).digest('hex')},null,2)+'\n');

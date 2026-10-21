import assert from 'node:assert/strict'
import { test } from 'node:test'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { loadCatalogue } from '../lib/catalogue.js'
import { validateToolResult } from '../lib/tools.js'
const { catalogue } = await loadCatalogue(fileURLToPath(new URL('../../', import.meta.url)))
const hash = 'a'.repeat(64)
function search() { return { schema_version:'1.0.0',kind:'TASK_READ_RESULT',action_id:randomUUID(),tool_id:'AT-01',status:'SUCCEEDED',summary:'No matches in the fixed approved selection',coverage:{requested_scope_sha256:hash,delivered_scope_sha256:hash,complete:true},next_cursor:null,read_receipt:{kind:'RETRIEVAL_OPERATION',receipt_id:randomUUID(),receipt_sha256:hash},payload:{kind:'SEARCH',retrieval_id:randomUUID(),match_status:'EMPTY',evidence_refs:[],missing_requirement_ids:[]} } }
test('TASK read receipt has no fictional artifact and is rejected by the historical success schema',()=>{const result=search();validateToolResult(catalogue,'AT-01',result);assert.equal('artifact_id' in result,false);assert.throws(()=>catalogue.validate('tool-success',result));})
test('read-result version cannot widen to writable tools or masquerade as a source receipt',()=>{const result=search();assert.throws(()=>validateToolResult(catalogue,'AT-06',result));result.tool_id='AT-02';assert.throws(()=>validateToolResult(catalogue,'AT-02',result));})
test('missing receipt pins, fake artifact field and continuation claims are rejected',()=>{for(const mutate of [r=>delete r.read_receipt,r=>r.read_receipt.receipt_sha256='unverified',r=>r.artifact_id=randomUUID(),r=>r.next_cursor='caller',r=>r.coverage.complete=false]){const r=search();mutate(r);assert.throws(()=>validateToolResult(catalogue,'AT-01',r));}})
test('legacy writable success shape remains unchanged and cannot enter read-only branch',()=>{const r={schema_version:'1.0.0',action_id:randomUUID(),tool_id:'AT-06',status:'SUCCEEDED',artifact_id:randomUUID(),resource_revision:'1',summary:'Saved candidate',coverage:{requested_scope_sha256:hash,delivered_scope_sha256:hash,complete:true},next_cursor:null,payload:{kind:'RECEIPT',resource_id:randomUUID(),resource_kind:'CANDIDATE',resource_state:'DRAFT',check_ids:[]}};validateToolResult(catalogue,'AT-06',r);assert.throws(()=>validateToolResult(catalogue,'AT-01',r));})
// Deferred B26 coverage: these synthetic receipts do not represent an OCR run.
test('explicit v2 selection is required for v2 results and cannot weaken v1',()=>{
  const result=search();result.schema_version='2.0.0';
  assert.throws(()=>validateToolResult(catalogue,'AT-01',result));
  validateToolResult(catalogue,'AT-01',result,false,true);
  result.schema_version='1.0.0';
  assert.throws(()=>validateToolResult(catalogue,'AT-01',result,false,true));
});
test('both dereferenced input publications preserve explicit evidence version selection',async()=>{
  const {inputDefinitions,inputDefinitionsV2}=await loadCatalogue(fileURLToPath(new URL('../../',import.meta.url)));
  assert.equal(inputDefinitions.SearchArgs.properties.evidence_contract,undefined);
  assert.equal(inputDefinitionsV2.SearchArgs.properties.evidence_contract.const,'2.0.0');
  assert.ok(inputDefinitionsV2.SearchArgs.required.includes('evidence_contract'));
});

// Written for the later authorized test stage; no test execution during development.
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { EvaluationModelAdapter } from '../lib/evaluation-model.js'
const config = { modelToolMode:'EVALUATION_VERIFICATION_V1', model:'fixture', contextWindow:10000, maxOutputTokens:1000, streamTimeoutMs:1000, cancellationReason:'PROTOCOL_ERROR', reasoningEfforts:[], defaultReasoningEffort:null }
const exact = { messages:[{ role:'system',content:'approved system' },{role:'user',content:'literal user'}], tools:[] }
const seed = { role:'user',content:[{type:'text',text:JSON.stringify(exact)}],source:{kind:'user'} }

test('EVAL sends exact approved prefix, not the serialized runner seed or TASK system', async () => {
  let captured; let count=0; let closed=false
  const bridge={ evaluation:async projection=>{ captured=projection;count++;throw new Error('fixture stop before provider') },close:async()=>{closed=true} }
  const adapter=new EvaluationModelAdapter(config,{},async()=>bridge,()=>{},()=>{})
  await assert.rejects(async()=>{for await (const _ of adapter.stream({provider:'enterprise-model',model:'fixture',system:'SDK default, never the approved system',messages:[seed],sessionId:'fixture-session'})){}},/fixture stop/)
  assert.equal(count,1);assert.equal(closed,true)
  assert.equal(captured.system,null);assert.equal(captured.parameters.purpose,'EVALUATION')
  assert.deepEqual(captured.messages.map(m=>({role:m.role,content:m.content[0].text})),exact.messages)
})

test('EVAL preserves genuine ordered SDK history without synthesizing assistants or tools', async () => {
  const history=[{role:'assistant',content:[{type:'text',text:'actual prior output'}],source:{kind:'model',provider:'enterprise-model',model:'fixture'}},{role:'tool',content:[{type:'text',text:'actual registered response'}],toolCallId:'call-1',source:{kind:'tool',callId:'call-1'}}]
  let captured
  const adapter=new EvaluationModelAdapter(config,{},async()=>({evaluation:async value=>{captured=value;throw Error('stop')},close:async()=>{}}),()=>{},()=>{})
  await assert.rejects(async()=>{for await (const _ of adapter.stream({provider:'enterprise-model',model:'fixture',messages:[seed,...history],sessionId:'fixture-session'})){}},/stop/)
  assert.deepEqual(captured.messages.slice(2),history)
})

for(const purpose of ['compaction','session-title']) test(`EVAL rejects ${purpose} without bridge`,async()=>{
  const adapter=new EvaluationModelAdapter(config,{},async()=>assert.fail('must not connect'),()=>{},()=>{})
  await assert.rejects(async()=>{for await (const _ of adapter.stream({provider:'enterprise-model',model:'fixture',messages:[seed],purpose})){}},/EVAL/)
})

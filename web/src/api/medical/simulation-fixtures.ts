import request from '@/utils/request'
import { envelope, fail, validate } from './protocol'
import type { MedicalSession } from './protocol'
import { fixtureSchema, inputSchema, inputRequest, verifyPending, verifyFixtureReceipt, verifyInputReceipt } from './simulation-fixture-domain'
import type { PendingPreparation, FixtureReceipt, InputReceipt } from './simulation-fixture-domain'
export async function registerFixture(session: MedicalSession, p: PendingPreparation, signal: AbortSignal): Promise<FixtureReceipt> {
 await verifyPending(session,p,true); signal.throwIfAborted()
 const raw=await request.post('/api/simulation-fixtures',p.request,{medical:'envelope',headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':p.context.context_revision,[session.csrf.header_name]:session.csrf.token}})
 const e=envelope(raw), r=validate<FixtureReceipt>(fixtureSchema+'SimulationFixtureRegistrationReceipt',e.data)
 if(raw.status!==201 || e.resource_revision!=='1')fail('CONTENT_MISMATCH','案例登记响应身份无效')
 verifyFixtureReceipt(p,r);return r
}
export async function registerInputArtifact(session: MedicalSession,p: PendingPreparation,signal: AbortSignal): Promise<InputReceipt> {
 await verifyPending(session,p,true);signal.throwIfAborted()
 const raw=await request.post('/api/simulation-fixtures/'+p.case_id+'/input-artifact',inputRequest(p.request),{medical:'envelope',headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':p.context.context_revision,'Idempotency-Key':p.key,[session.csrf.header_name]:session.csrf.token}})
 const e=envelope(raw),r=validate<InputReceipt>(inputSchema+'Receipt',e.data)
 if(raw.status!==200 || e.resource_revision!=='1')fail('CONTENT_MISMATCH','输入登记响应修订无效')
 verifyInputReceipt(p,r);return r
}
export async function lookupInputArtifact(session: MedicalSession,p: PendingPreparation,signal: AbortSignal): Promise<{schema_version:'1.0.0';status:'FOUND'|'NOT_FOUND';receipt:InputReceipt|null}> {
 await verifyPending(session,p,false);signal.throwIfAborted()
 const e=envelope(await request.post('/api/simulation-fixtures/'+p.case_id+'/input-artifact-lookups',{schema_version:'1.0.0',original_key:p.key,original_request:inputRequest(p.request)},{medical:'envelope',signal,headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context!.context_revision,[session.csrf.header_name]:session.csrf.token}}))
 if(e.resource_revision!=='1')fail('CONTENT_MISMATCH','输入查询响应修订无效')
 const r=validate<{schema_version:'1.0.0';status:'FOUND'|'NOT_FOUND';receipt:InputReceipt|null}>('urn:medical:simulation-input-artifact-lookup:1.0.0#/$defs/Result',e.data)
 if(r.receipt){validate(inputSchema+'Receipt',r.receipt);verifyInputReceipt(p,r.receipt)}
 return r
}

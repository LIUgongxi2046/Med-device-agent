import request from '@/utils/request'
import {envelope,fail,validate} from './protocol'
import type {Basis} from './protocol'
import {runtimeGuard,detached} from './model-runtime-domain'
import type {RuntimeReader} from './model-runtime-domain'
export interface TokenizerCandidates {schema_version:'1.0.0';project_id:string;model_config_revision:string;purpose:'MODEL_CONFIGURATION';tokenizer_basis:Basis|null;missing_input_reasons:('FORMAL_RUNTIME_UNAVAILABLE'|'TOKENIZER_EXECUTABLE_UNAVAILABLE'|'TOKENIZER_SOURCE_UNAVAILABLE')[]}
export async function readTokenizerCandidates(reader:RuntimeReader,project:string,model:string,signal:AbortSignal):Promise<TokenizerCandidates>{validate('urn:medical:model-verification-input-workflow:1.0.0#/$defs/Id',model);const g=runtimeGuard(reader,signal),raw=await request.get('/api/model-verification-tokenizer-candidates/'+model,{medical:'envelope',signal,headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':g.context.context_revision}});g.assert();const e=envelope(raw),v=validate<TokenizerCandidates>('urn:medical:model-verification-tokenizer-candidates:1.0.0#/$defs/Candidates',e.data);if(raw.status!==200||e.resource_revision!=='1'||v.project_id!==project||v.model_config_revision!==model)fail('CONTENT_MISMATCH','Tokenizer候选不属于所选模型和项目');return detached(v)}

import { fail } from './protocol'
import type { Basis } from './protocol'
import type { ModelView } from './model-configuration-domain'
import { canonical, detached, validateBody, runtimeGuard } from './model-runtime-domain'
import type { EnableRequest, PendingRuntime, RuntimeReader } from './model-runtime-domain'
import { recoverRuntime } from './model-runtime'
import { readModelConfiguration } from './model-configurations'

/** Only independently prepared MODEL_ENABLEMENT evidence may reach this boundary. */
export interface EnablementEvidence {
 business_purpose:'MODEL_ENABLEMENT'; project_id:string; model_config_revision:string; policy_basis:Basis;
 data_policy_evidence:EnableRequest['data_policy_evidence']; scope_snapshot_basis:Basis
}
export function buildEnablementSelection(model:ModelView,verification:PendingRuntime,evidence:EnablementEvidence,reason:string):EnableRequest {
 const result=verification.result,policy=model.definition.data_policy.policy_basis,tuple=evidence.data_policy_evidence
 if(evidence.business_purpose!=='MODEL_ENABLEMENT'||!model.project_id||model.definition.provider!=='DEEPSEEK'||verification.kind!=='VERIFY'||result?.status!=='PASSED'||verification.body.model_config_revision!==model.model_config_revision||evidence.model_config_revision!==model.model_config_revision||evidence.project_id!==model.project_id||tuple.project_id!==model.project_id||tuple.source_version!==policy.version_id||canonical(evidence.policy_basis)!==canonical(policy)||tuple.snapshot_id!==evidence.scope_snapshot_basis.version_id)fail('ACTION_STALE','启用必须绑定当前模型、原 PASSED 验证和独立 MODEL_ENABLEMENT 政策范围')
 const body:EnableRequest={schema_version:'1.0.0',model_config_revision:model.model_config_revision,connection_verification_id:result.verification_id,data_policy_evidence:detached(tuple),reason:reason.trim()}
 validateBody('ENABLE',body);return body
}
export async function recheckEnablementSelection(reader:RuntimeReader,model:ModelView,verification:PendingRuntime,evidence:EnablementEvidence,reason:string,signal:AbortSignal):Promise<EnableRequest>{
 const g=runtimeGuard(reader,signal),expected=buildEnablementSelection(model,verification,evidence,reason)
 const fresh=await readModelConfiguration(g.session,model.model_config_revision,signal);g.assert()
 if(canonical(fresh)!==canonical(model))fail('ACTION_STALE','模型、价格或政策固定配置已变化')
 const original=await recoverRuntime(reader,verification,signal);g.assert()
 const body=buildEnablementSelection(fresh,original,evidence,reason)
 if(canonical(body)!==canonical(expected))fail('ACTION_STALE','原连接验证或启用依据已变化')
 return body
}

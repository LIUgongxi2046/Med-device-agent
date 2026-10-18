package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;
/** Current deployment availability only; never a data-use grant. Disabled services are not instantiated. */
@Component public final class FormalEvaluationInputFeatureAvailability {
 private final EnterpriseAccess access;private final ListableBeanFactory beans;private final PermissionSchema json;private final FormalEvaluationInputWorkflowSchema schema;private final FormalEvaluationInputSchema input;
 public FormalEvaluationInputFeatureAvailability(EnterpriseAccess access,ListableBeanFactory beans,PermissionSchema json,FormalEvaluationInputWorkflowSchema schema,FormalEvaluationInputSchema input){this.access=access;this.beans=beans;this.json=json;this.schema=schema;this.input=input;}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context){return access.execute(subject,a->{if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);boolean enabled=beans.getBeanNamesForType(FormalEvaluationInputController.class,false,false).length>0;var view=json.object().put("schema_version","1.0.0").put("enabled",enabled).put("scope","ENTERPRISE").put("maximum_bytes",FormalEvaluationInputSchema.MAXIMUM_BYTES).put("input_schema_ref",FormalEvaluationInputSchema.REF).put("input_schema_sha256",input.schemaSha256());if(enabled)view.putNull("unavailable_reason");else view.put("unavailable_reason","FEATURE_DISABLED");return schema.validate("Availability",view);});}
}

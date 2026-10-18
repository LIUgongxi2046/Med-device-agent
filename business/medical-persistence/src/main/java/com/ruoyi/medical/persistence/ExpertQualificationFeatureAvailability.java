package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;
@Component public final class ExpertQualificationFeatureAvailability {
 private final EnterpriseAccess access;private final ListableBeanFactory beans;private final ExpertQualificationSchema schema;
 public ExpertQualificationFeatureAvailability(EnterpriseAccess access,ListableBeanFactory beans,ExpertQualificationSchema schema){this.access=access;this.beans=beans;this.schema=schema;}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context){return access.execute(subject,a->{if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);boolean enabled=beans.getBeanNamesForType(ExpertQualificationController.class,false,false).length>0;var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("enabled",enabled).put("scope","HUMAN_QUALIFICATION_GOVERNANCE").put("creates_professional_licence",false).put("creates_scores",false).put("proves_formal_execution",false);if(enabled)n.putNull("unavailable_reason");else n.put("unavailable_reason","FEATURE_DISABLED");return schema.validate("Availability",n);});}
}

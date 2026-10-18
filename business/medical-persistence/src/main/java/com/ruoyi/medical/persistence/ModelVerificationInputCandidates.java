package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;

/** Bounded metadata only. No snapshot/artifact writes, object GET, credentials, tokenization or provider request. */
@Component @ConditionalOnProperty(prefix="medical.model-verification-input",name="enabled",havingValue="true")
public final class ModelVerificationInputCandidates {
 private final EnterpriseAccess access;private final ModelConfigurationService models;private final ResourceReadAuthority resources;
 private final ProjectMapper projectRows;private final ProjectService projects;private final ScopeSelectionAuthority selections;
 private final ModelVerificationInputMapper mapper;private final DocumentUploadPolicy policy;private final PermissionSchema json;private final ModelVerificationInputWorkflowSchema schema;
 public ModelVerificationInputCandidates(EnterpriseAccess access,ModelConfigurationService models,ResourceReadAuthority resources,
  ProjectMapper projectRows,ProjectService projects,ScopeSelectionAuthority selections,ModelVerificationInputMapper mapper,
  DocumentUploadPolicy policy,PermissionSchema json,ModelVerificationInputWorkflowSchema schema){this.access=access;this.models=models;this.resources=resources;this.projectRows=projectRows;this.projects=projects;this.selections=selections;this.mapper=mapper;this.policy=policy;this.json=json;this.schema=schema;}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String modelRevision,String purpose){
  WireValues.uuid(modelRevision);PermissionSchema.revision(context);if(!Set.of("DOCUMENT_BODY","MODEL_VERIFICATION").contains(purpose))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);policy.requireConfigured();
   var model=models.read(a,modelRevision).required("data");String project=model.required("project_id").textValue();if(project==null||!modelRevision.equals(model.required("current_model_revision").textValue()))throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);
   resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"CONFIGURATION",model.required("config_id").textValue()),"model.verify","MODEL_VERIFICATION");
   resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),"project.read",purpose);
   var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",project);var rows=projectRows.project(values);if(rows.size()!=1)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
   var profile=projects.fixedProfile(a,project,rows.get(0).profileRevision(),purpose);if(!"ACTIVE".equals(profile.project().status()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   var scope=new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.PROJECT_APPROVED,List.of(),List.of(),false,null,null);
   var selection=selections.require(a,project,scope,purpose,policy.environment(),policy.region(),new DependencyLineage.Limits(Math.min(128,policy.maximumSources()),512));
   var result=json.object().put("schema_version","1.0.0").put("project_id",project).put("model_config_revision",modelRevision).put("business_purpose",purpose);
   result.set("profile_basis",json.tree(new PermissionProtocol.Basis(profile.project().profileRevision(),profile.sha256())));var items=result.putArray("sources");
   for(var verified:selection.sources()){
    var source=verified.source();if(!"PROJECT".equals(source.ownership())||!project.equals(source.ownerProjectId())||!purpose.equals(verified.rights().purpose()))continue;
    // Source read is already independently checked by ScopeSelectionAuthority; registration right remains separate.
    if(resources.find(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"SOURCE",source.sourceId()),"artifact.register",purpose).isEmpty())continue;
    values.put("sourceVersion",source.sourceVersion());var originals=mapper.original(values);if(originals.size()!=1)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);var original=originals.get(0);
    if(!a.context().tenant_id().equals(original.tenantId())||!source.originalSha256().equals(original.sha256()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    if(original.sizeBytes()==null||original.sizeBytes().signum()<=0||original.sizeBytes().compareTo(java.math.BigInteger.valueOf(ModelVerificationInputSchema.MAXIMUM_BYTES))>0||original.sizeBytes().compareTo(policy.maximumBytes())>0||!Set.of("application/json","text/plain").contains(original.mediaType()))continue;
    var item=items.addObject().put("source_id",source.sourceId()).put("rights_revision",verified.rights().rightsRevision()).put("size_bytes",original.sizeBytes().toString()).put("media_type",original.mediaType()).put("content_validation","NOT_READ");
    item.set("source",json.tree(new PermissionProtocol.Basis(source.sourceVersion(),source.originalSha256())));
   }
   var missing=result.putArray("missing_input_reasons");if(items.isEmpty())missing.add("NO_CURRENT_PROJECT_SOURCE_CANDIDATE");else missing.add("EXACT_INPUT_CONTENT_VALIDATION_REQUIRED");
   return schema.validate("Candidates",result);
  }).get());
 }
}

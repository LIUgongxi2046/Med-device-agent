package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;
/** Actual deployed tokenizer discovery. Reads metadata and the pinned local executable; never executes it or performs a provider call. */
@Component public final class ModelVerificationTokenizerCandidates {
 private final EnterpriseAccess access;private final ModelConfigurationService models;private final ResourceReadAuthority resources;
 private final ObjectProvider<FormalTokenizerRunner> runners;private final ObjectProvider<FormalInvocationSourceOriginals> originals;
 private final PermissionSchema json;private final ModelVerificationTokenizerSchema schema;
 public ModelVerificationTokenizerCandidates(EnterpriseAccess access,ModelConfigurationService models,ResourceReadAuthority resources,
  ObjectProvider<FormalTokenizerRunner> runners,ObjectProvider<FormalInvocationSourceOriginals> originals,PermissionSchema json,ModelVerificationTokenizerSchema schema){this.access=access;this.models=models;this.resources=resources;this.runners=runners;this.originals=originals;this.json=json;this.schema=schema;}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String modelRevision){
  PermissionSchema.revision(context);WireValues.uuid(modelRevision);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   if(!Objects.equals(context,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
   var model=models.read(a,modelRevision).required("data");String project=model.required("project_id").textValue();
   if(project==null||!modelRevision.equals(model.required("current_model_revision").textValue()))throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);
   var scope=new PermissionProtocol.Scope(a.context().tenant_id(),project,"CONFIGURATION",model.required("config_id").textValue());
   var grant=resources.require(a,scope,"model.verify","MODEL_VERIFICATION");
   var result=json.object().put("schema_version","1.0.0").put("project_id",project).put("model_config_revision",modelRevision).put("purpose","MODEL_CONFIGURATION").putNull("tokenizer_basis");var missing=result.putArray("missing_input_reasons");
   var runner=runners.getIfAvailable();var reader=originals.getIfAvailable();
   if(runner==null||reader==null){missing.add("FORMAL_RUNTIME_UNAVAILABLE");return schema.validate(result);}
   PermissionProtocol.Basis basis;
   try{basis=runner.configuredSourceBasis();}catch(java.io.IOException|TenantAccessException invalid){missing.add("TOKENIZER_EXECUTABLE_UNAVAILABLE");return schema.validate(result);}
   // This is the exact current SOURCE admission used by076 before reading/executing tokenizer bytes.
   try{String current=reader.requireCurrentBasis(a,basis,"MODEL_CONFIGURATION",project);if(!current.equals(reader.requireCurrentBasis(a,basis,"MODEL_CONFIGURATION",project)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
   catch(TenantAccessException unavailable){missing.add("TOKENIZER_SOURCE_UNAVAILABLE");return schema.validate(result);}
   if(!basis.equals(runner.configuredSourceBasis())||!model.equals(models.read(a,modelRevision).required("data"))||!grant.equals(resources.require(a,scope,"model.verify","MODEL_VERIFICATION")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
   result.set("tokenizer_basis",json.tree(basis));return schema.validate(result);
  }).get());
 }
}

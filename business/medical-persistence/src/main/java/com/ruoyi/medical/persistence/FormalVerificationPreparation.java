package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Actual bounded input and tokenizer execution. Returned proof cannot be constructed from request JSON. */
public final class FormalVerificationPreparation {
 public static final String PURPOSE="MODEL_VERIFICATION";
 private static final Set<String> ACTIONS=Set.of("model.verify","model.send","configuration.read","capability.read","source.read","source.external.process","artifact.read","rule.read","budget.read","project.read");
 private final EnterpriseAccess access;private final ModelConfigurationService models;private final ResourceReadAuthority resources;
 private final FormalCapabilityIdentityAuthority capabilities;private final FormalInvocationRules rules;private final FormalInvocationInputs inputs;
 private final FormalInvocationPriceAuthority prices;private final FormalInvocationSourceOriginals originals;private final FormalTokenizerRunner tokenizer;
 private final FormalInvocationSchema schema;private final RunTokenAuthoritySchema tokenSchema;private final PermissionSchema json;private final String serviceUri,audience;
 public FormalVerificationPreparation(EnterpriseAccess access,ModelConfigurationService models,ResourceReadAuthority resources,FormalCapabilityIdentityAuthority capabilities,FormalInvocationRules rules,FormalInvocationInputs inputs,FormalInvocationPriceAuthority prices,FormalInvocationSourceOriginals originals,FormalTokenizerRunner tokenizer,FormalInvocationSchema schema,RunTokenAuthoritySchema tokenSchema,PermissionSchema json,String serviceUri,String audience){this.access=access;this.models=models;this.resources=resources;this.capabilities=capabilities;this.rules=rules;this.inputs=inputs;this.prices=prices;this.originals=originals;this.tokenizer=tokenizer;this.schema=schema;this.tokenSchema=tokenSchema;this.json=json;this.serviceUri=Objects.requireNonNull(serviceUri);this.audience=Objects.requireNonNull(audience);if(!java.net.URI.create(serviceUri).isAbsolute()||audience.isBlank())throw new IllegalArgumentException();}
 public final class Prepared {
  private final ConnectionVerificationIntentProof original;private final JsonNode request,model,role,prompt,rulesSnapshot;private final String modelDigest;private final List<RunTokenAuthoritySchema.Permission> permissions;
  private final FormalInvocationInputs.Prepared input;private final FormalInvocationPriceAuthority.Price price;private final FormalInvocationSourceOriginals.Read executable;private final FormalTokenizerRunner.Result count;
  private Prepared(ConnectionVerificationIntentProof original,JsonNode request,JsonNode model,JsonNode role,JsonNode prompt,JsonNode rulesSnapshot,List<RunTokenAuthoritySchema.Permission> permissions,FormalInvocationInputs.Prepared input,FormalInvocationPriceAuthority.Price price,FormalInvocationSourceOriginals.Read executable,FormalTokenizerRunner.Result count)throws Exception{this.original=original;this.request=request.deepCopy();this.model=model.deepCopy();this.role=role.deepCopy();this.prompt=prompt.deepCopy();this.rulesSnapshot=rulesSnapshot.deepCopy();this.permissions=List.copyOf(permissions);this.modelDigest=schema.hash(model);this.input=input;this.price=price;this.executable=executable;this.count=count;}
  public String tenantId(){return original.tenantId();}public String projectId(){return original.projectId();}public String actorId(){return original.actorId();}public String verificationId(){return original.verificationId();}public String operationId(){return original.operationId();}public String key(){return original.idempotencyKey();}public String requestHash(){return original.requestBodySha256();}
  public JsonNode request(){return request.deepCopy();}public JsonNode model(){return model.deepCopy();}public JsonNode role(){return role.deepCopy();}public JsonNode prompt(){return prompt.deepCopy();}public JsonNode rules(){return rulesSnapshot.deepCopy();}public List<RunTokenAuthoritySchema.Permission> permissions(){return permissions;}
  public FormalInvocationInputs.Prepared input(){return input;}public FormalInvocationPriceAuthority.Price price(){return price;}public FormalTokenizerRunner.Result tokenization(){return count;}public String serviceUri(){return serviceUri;}public String audience(){return audience;}
  public void requireCurrent(EnterpriseAccess.Admission a)throws Exception{
   original.requireCurrent(a);var now=initial(a,original,request);
   if(!modelDigest.equals(schema.hash(now.model()))||!schema.canonical(role).equals(schema.canonical(now.role()))||!schema.canonical(prompt).equals(schema.canonical(now.prompt()))||!schema.canonical(rulesSnapshot).equals(schema.canonical(now.rules()))||!permissions.equals(now.permissions()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
   input.requireCurrent(a);price.requireCurrent(a);executable.requireCurrent(a);
  }
 }
 private record Initial(JsonNode model,JsonNode role,JsonNode prompt,JsonNode rules,List<RunTokenAuthoritySchema.Permission> permissions){}
 public Prepared prepare(ApplicationSessionIdentity.Verified actor,String context,ConnectionVerificationIntentProof original)throws IOException{
  try{
   var request=original.approvedRequest();schema.require("VerificationRequest",request);
   if(request.required("project_id").isNull())throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);
   var initial=tx(actor,a->{if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);return initial(a,original,request);});
   var definition=initial.model().required("definition");var input=inputs.prepare(actor,context,request,definition.required("provider").textValue(),definition.required("region").textValue());
   var price=prices.prepare(actor,context,original.modelRevision(),original.projectId());var tokenizerBasis=basis(request.required("tokenizer_basis"));
   var executable=originals.read(actor,context,tokenizerBasis,"MODEL_CONFIGURATION",original.projectId());
   var counted=tokenizer.count(input.bytes(),tokenizerBasis.version_id(),tokenizerBasis.content_sha256(),()->Thread.currentThread().isInterrupted());
   if(!counted.inputSha256().equals(input.inputSha256())||!counted.executableSha256().equals(WireValues.sha256(executable.bytes()))||counted.tokenCount().compareTo(new BigInteger(request.required("maximum_input_tokens").textValue()))>0)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
   FormalInvocationModelPolicy.require(initial.model(),request,input.classification(),counted.tokenCount());
   var prepared=new Prepared(original,request,initial.model(),initial.role(),initial.prompt(),initial.rules(),initial.permissions(),input,price,executable,counted);
   tx(actor,a->{if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);prepared.requireCurrent(a);return null;});return prepared;
  }catch(IOException e){throw e;}catch(RuntimeException e){throw e;}catch(Exception e){throw new IOException("Formal verification preparation unavailable",e);}
 }
 private Initial initial(EnterpriseAccess.Admission a,ConnectionVerificationIntentProof original,JsonNode request)throws Exception{
  original.requireCurrent(a);if(!schema.hash(request).equals(original.requestBodySha256())||!original.modelRevision().equals(request.required("model_config_revision").textValue())||!Objects.equals(original.projectId(),request.required("project_id").textValue()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  var model=models.read(a,original.modelRevision()).required("data");if(!original.projectId().equals(model.required("project_id").textValue()))throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);var definition=model.required("definition");if(!original.modelRevision().equals(model.required("current_model_revision").textValue())||!original.credentialReferenceId().equals(definition.required("credential_reference_id").textValue()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  var modelScope=new PermissionProtocol.Scope(a.context().tenant_id(),model.required("project_id").textValue(),"CONFIGURATION",model.required("config_id").textValue());resources.require(a,modelScope,"model.verify",PURPOSE);resources.require(a,modelScope,"model.send",PURPOSE);
  var role=capabilities.require(a,basis(request.required("role_basis")),"ROLE",original.projectId(),PURPOSE);var prompt=capabilities.require(a,basis(request.required("prompt_basis")),"PROMPT",original.projectId(),PURPOSE);
  var roleScope=tokenSchema.read("RoleScope",schema.canonical(role.canonicalDefinition().required("request").required("tool_scope")));var permissions=new ArrayList<RunTokenAuthoritySchema.Permission>();
  for(var p:roleScope.required("permissions")){var permission=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(p.required("scope").required("tenant_id").textValue(),p.required("scope").required("project_id").textValue(),p.required("scope").required("resource_kind").textValue(),p.required("scope").required("resource_id").textValue()),p.required("action").textValue(),p.required("purpose").textValue());
   if(!ACTIONS.contains(permission.action())||!Set.of(PURPOSE,"DOCUMENT_BODY","AGENT_PUBLICATION","TEMPLATE_MAINTENANCE","MODEL_CONFIGURATION","BUDGET_READING").contains(permission.purpose()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   resources.require(a,permission.scope(),permission.action(),permission.purpose());permissions.add(permission);
  }
  for(String action:List.of("model.verify","model.send"))if(!permissions.contains(new RunTokenAuthoritySchema.Permission(modelScope,action,PURPOSE)))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var ruleBases=new ArrayList<PermissionProtocol.Basis>();for(var r:request.required("rule_bases"))ruleBases.add(basis(r));var checkedRules=rules.require(a,original.projectId(),ruleBases);
  return new Initial(model,role.canonicalDefinition(),prompt.canonicalDefinition(),checkedRules.manifest(),List.copyOf(permissions));
 }
 private static PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(n.required("version_id").textValue(),n.required("content_sha256").textValue());}
 private <T>T tx(ApplicationSessionIdentity.Verified actor,FormalInvocationInputs.Work<T> work){return access.execute(actor,a->ThrowingSupplier.of(()->work.apply(a)).get());}
}

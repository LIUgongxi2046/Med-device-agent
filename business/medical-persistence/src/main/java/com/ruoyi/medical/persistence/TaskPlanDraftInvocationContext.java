package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** N01 exact model context. A compilation is detached data; Prepared and Registered are
 * private producer proofs. Bootstrap reads never confer permission to send to a provider. */
public final class TaskPlanDraftInvocationContext {
 private final JdbcTemplate db;private final ModelConfigurationService models;private final ModelEligibilityAuthority eligibility;
 private final ResourceReadAuthority resources;private final SourceReadAuthority sources;private final TaskPlanDraftPackages packages;
 private final FormalInvocationSourceOriginals originals;private final FormalInvocationSchema schema;private final TaskPlanDraftInvocationSchema exact;
 private final PermissionSchema json;private final TaskPlanDraftProducerAuthority producers;private final TaskPlanDraftContentAuthority contents;private final RuntimeObjectTransport transport;
 public TaskPlanDraftInvocationContext(DataSource data,ModelConfigurationService models,ModelEligibilityAuthority eligibility,ResourceReadAuthority resources,
   SourceReadAuthority sources,TaskPlanDraftPackages packages,FormalInvocationSourceOriginals originals,FormalInvocationSchema schema,
   TaskPlanDraftInvocationSchema exact,PermissionSchema json,TaskPlanDraftContentAuthority contents,RuntimeObjectTransport transport,TaskPlanDraftProducerAuthority producers){
  db=new JdbcTemplate(data);db.setQueryTimeout(5);this.models=models;this.eligibility=eligibility;this.resources=resources;this.sources=sources;this.packages=packages;this.originals=originals;this.schema=schema;this.exact=exact;this.json=json;this.contents=contents;this.transport=transport;this.producers=producers;
 }
 private record State(ModelConfigurationProtocol.Fixed model,ModelEligibilityAuthority.Verified enabled,JsonNode policy,JsonNode node,JsonNode admission,String digest){}
 private State state(TaskPlanDraftRuntimeAuthority.Verified a)throws Exception {
  a.requireUnchanged();var j=a.job();var admitted=schema.parse(j.admissionJson());var policy=json.tree(a.current().current().node());
  var binding=json.tree(a.current().current().node());var basis=json.basis(admitted.required("model_version"));
  var model=models.requireCurrentExternal(a.admission(),basis.version_id(),"RUN_CREATION",j.project());if(!basis.content_sha256().equals(schema.hash(model)))bad();
  var enabled=eligibility.require(a.admission(),basis.version_id(),"RUN_CREATION",j.project());if(!enabled.contentSha256().equals(basis.content_sha256()))bad();
  // The fixed policy is re-read by JobAuthority. Never trust a caller's provider, parameters or region.
  var definition=json.tree(a.current().current().policyDefinition());
  enabled.requireDataUse(j.classification(),text(definition,"region"),definition.required("training_allowed").booleanValue());enabled.requireParameters(binding.required("generation_parameters"));
  var witness=json.object();witness.set("model",json.tree(model));witness.set("eligibility",json.tree(enabled.safeReplayBinding()));witness.set("policy",definition);witness.set("node",binding);witness.set("sources",json.tree(a.current().current().sources()));
  enabled.requireUnchanged();return new State(model,enabled,definition,binding,admitted,schema.hash(witness));
 }
 public static final class Compilation {
  private final byte[] bytes;private final PermissionProtocol.Basis price,tokenizer;private final AgentReleaseProtocol.SchemaBoundArtifact executable;
  private Compilation(byte[] bytes,PermissionProtocol.Basis price,PermissionProtocol.Basis tokenizer,AgentReleaseProtocol.SchemaBoundArtifact executable){this.bytes=bytes.clone();this.price=price;this.tokenizer=tokenizer;this.executable=executable;}
  public byte[] bytes(){return bytes.clone();}public PermissionProtocol.Basis priceBasis(){return price;}public PermissionProtocol.Basis tokenizerBasis(){return tokenizer;}public AgentReleaseProtocol.SchemaBoundArtifact tokenizerArtifact(){return executable;}
 }
 public Compilation compile(TaskPlanDraftRuntimeAdapter.Session session,TaskPlanDraftSupervisor.Admitted supervisor,TaskPlanDraftSupervisor.ModelRequest observed,TaskPlanDraftPromptCompiler.Compiled prompt)throws Exception {
  require(session,supervisor,observed);var a=session.authority();prompt.requireFor(a);var current=state(a);var projection=exact.readProjection(observed.bytes());
  if(observed.ordinal()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  if(!prompt.launchText().equals(text(session.launch(),"prompt"))||!text(session.deployment().value().required("sdk"),"model").equals(text(projection,"model"))||!projection.required("parameters").equals(current.node.required("generation_parameters")))bad();
  // Closed first-round adapter. Extra SDK history is rejected, not silently discarded.
  var tools=approvedTools(session,projection.required("tools"));var document=TaskInvocationProjection.planDraftInitial(projection,session.sessionId(),prompt.launchText(),prompt.approvedSystemMessages(),tools);
  byte[] bytes=ContentReadAuthorization.canonicalBytes(document);exact.readExactInput(bytes);
  var parser=new com.fasterxml.jackson.databind.ObjectMapper().setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
  var executable=parser.treeToValue(current.node.required("tokenizer_artifact"),AgentReleaseProtocol.SchemaBoundArtifact.class);
  return new Compilation(bytes,((ModelConfigurationProtocol.Definition)current.model.definition()).price_basis(),json.basis(current.node.required("tokenizer_version")),executable);
 }
 private JsonNode approvedTools(TaskPlanDraftRuntimeAdapter.Session session,JsonNode actual)throws Exception {
  // Initial Planner is a bounded one-shot proposal. AT10 is an independently authorized
  // post-registration host tool, not an in-model call with a fabricated artifact/call ID.
  if(!actual.isArray()||!actual.isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  return actual.deepCopy();
 }
 public Prepared prepare(TaskPlanDraftRuntimeAdapter.Session session,TaskPlanDraftSupervisor.Admitted supervisor,TaskPlanDraftSupervisor.ModelRequest observed,
   TaskPlanDraftPromptCompiler.Compiled prompt,FormalInvocationSourceOriginals.Read price,TaskPlanDraftPackages.Read executable,FormalTokenizerRunner.Result counted)throws Exception {
  var c=compile(session,supervisor,observed,prompt);var a=session.authority();var s=state(a);price.requireCurrent(a.admission());executable.requireCurrent(a.admission());
  if(!c.price.version_id().equals(price.sourceVersion())||!c.price.content_sha256().equals(price.contentSha256())||!c.executable.artifact().artifactId().equals(executable.artifact().version_id())||!c.executable.artifact().sha256().equals(executable.artifact().content_sha256())||!c.executable.schema().schemaRef().equals(executable.schemaRef())||!c.executable.schema().schemaSha256().equals(executable.schemaSha256()))bad();
  if(!c.tokenizer.version_id().equals(counted.tokenizerVersion())||!c.executable.artifact().sha256().equals(counted.executableSha256())||!WireValues.sha256(c.bytes).equals(counted.inputSha256())||!WireValues.sha256(executable.bytes()).equals(counted.executableSha256()))bad();
  var priceDocument=schema.read("PriceProfile",price.bytes(),TaskPlanDraftInvocationSchema.MAXIMUM_BYTES);bindPrice(s.model,priceDocument,c.price);
  var maximum=new BigInteger(a.current().current().releaseManifest().limits().maxInputTokens());
  if(counted.tokenCount().compareTo(maximum)>0)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  var request=json.object().put("maximum_input_tokens",maximum.toString());request.set("request_parameters",s.node.required("generation_parameters"));FormalInvocationModelPolicy.require(json.tree(s.model),request,a.job().classification(),counted.tokenCount());
  var content=contents.require(a.current());var members=new TreeMap<String,ArtifactRegistryProtocol.MemberRow>();for(var values:List.of(content.members(),prompt.contentMembers(),executable.contentMembers(),price.contentMembers()))for(var member:values){var old=members.putIfAbsent(member.upstreamVersionId(),member);if(old!=null&&!old.equals(member))bad();}
  // Content lineage contains only actual source-backed members. Governance identities
  // (package/model/data-policy/artifact UUIDs) stay in producer provenance, not fake rights.
  var roots=new TreeSet<String>(members.keySet());var producer=producers.require(a.current());producer.requireBytes("EXACT_INPUT",c.bytes().length);
  return new Prepared(session,supervisor,observed,prompt,price,executable,counted,c,s,priceDocument,List.copyOf(members.values()),List.copyOf(roots),producer.canonicalPins());
 }
 public final class Prepared {
  private final TaskPlanDraftRuntimeAdapter.Session session;private final TaskPlanDraftSupervisor.Admitted supervisor;private final TaskPlanDraftSupervisor.ModelRequest observed;private final TaskPlanDraftPromptCompiler.Compiled prompt;
  private final FormalInvocationSourceOriginals.Read price;private final TaskPlanDraftPackages.Read executable;private final FormalTokenizerRunner.Result counted;private final Compilation compilation;private final State state;private final JsonNode priceDocument;private final List<ArtifactRegistryProtocol.MemberRow> members;private final List<String> roots;private final JsonNode producerPins;
  private Prepared(TaskPlanDraftRuntimeAdapter.Session session,TaskPlanDraftSupervisor.Admitted supervisor,TaskPlanDraftSupervisor.ModelRequest observed,TaskPlanDraftPromptCompiler.Compiled prompt,FormalInvocationSourceOriginals.Read price,TaskPlanDraftPackages.Read executable,FormalTokenizerRunner.Result counted,Compilation compilation,State state,JsonNode priceDocument,List<ArtifactRegistryProtocol.MemberRow> members,List<String> roots,JsonNode producerPins){this.session=session;this.supervisor=supervisor;this.observed=observed;this.prompt=prompt;this.price=price;this.executable=executable;this.counted=counted;this.compilation=compilation;this.state=state;this.priceDocument=priceDocument.deepCopy();this.members=members;this.roots=roots;this.producerPins=producerPins.deepCopy();}
  public void requireUnchanged()throws Exception {var fresh=prepare(session,supervisor,observed,prompt,price,executable,counted);if(!producerPins.equals(fresh.producerPins)||!state.digest.equals(fresh.state.digest)||!Arrays.equals(compilation.bytes,fresh.compilation.bytes)||!members.equals(fresh.members)||!roots.equals(fresh.roots))bad();}
  public byte[] bytes(){return compilation.bytes();}public TaskPlanDraftRuntimeAuthority.Verified authority(){return session.authority();}public String sessionId(){return session.sessionId();}public String requestId(){return observed.requestId();}public String requestSha256(){return observed.sha256();}
  public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return members;}public List<String> contentRoots(){return roots;}
  public JsonNode provenance()throws Exception {requireUnchanged();var a=authority();var j=a.job();var n=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","PLAN_DRAFT").put("node_kind","N01").put("phase","PREPARATION_READ_ONLY").put("tenant_id",j.tenant()).put("project_id",j.project()).put("task_id",j.task()).put("run_id",j.run()).put("step_id",j.step()).put("principal_id",j.principal()).put("host_action_id",session.actionId()).put("actor_id",j.actor()).put("classification",j.classification()).put("lease_generation",a.leaseGeneration().toString()).put("worker_service_uri",a.workerService()).put("session_id",sessionId()).put("input_sha256",j.inputHash()).put("input_artifact_id",prompt.input().artifactId()).put("input_receipt_sha256",prompt.input().receiptSha256()).put("payload_sha256",WireValues.sha256(bytes())).put("state_sha256",state.digest).put("supervision_receipt_id",supervisor.receiptId()).put("supervision_sha256",supervisor.sha256()).put("deployment_sha256",supervisor.deploymentSha256());
   n.set("host_binding",session.binding());n.set("model_request",observed.canonicalReceipt());n.set("model",json.tree(state.model));n.set("model_eligibility",json.tree(state.enabled.safeReplayBinding()));n.set("price",priceDocument);n.put("price_authorization_sha256",price.authorizationSha256());n.set("prompt_compilation",prompt.canonicalPins());n.set("tokenizer_artifact",executable.canonicalPins());n.set("tokenizer_version",json.tree(compilation.tokenizer));n.set("tokenization",json.tree(Map.of("tokenizer_version",counted.tokenizerVersion(),"executable_sha256",counted.executableSha256(),"input_sha256",counted.inputSha256(),"output_sha256",counted.outputSha256(),"token_count",counted.tokenCount().toString())));n.set("request_parameters",state.node.required("generation_parameters"));n.set("output_storage",json.tree(transport.storageBinding()));n.set("producer",producerPins);n.set("retention",a.current().current().retention());n.set("output_retention",TaskInvocationOutputPolicy.forRun(j.run(),j.created().toInstant(java.time.ZoneOffset.UTC),a.current().current().retention()));n.set("content_members",json.tree(members));n.set("content_roots",json.tree(roots));n.put("launch_prompt",prompt.launchText());n.set("approved_system_messages",json.tree(prompt.approvedSystemMessages()));
   var original=a.current().current().original().request();n.set("enterprise_budget_basis",json.tree(original.enterpriseBudget()));n.set("parent_budget_basis",json.tree(original.parentBudget()));n.put("maximum_input_tokens",a.current().current().releaseManifest().limits().maxInputTokens());n.put("business_purpose",text(state.policy,"business_purpose"));return n;
  }
 }
 JsonNode requireTransmission(EnterpriseAccess.Admission modelAdmission,TaskPlanDraftRuntimeAuthority.Verified a,JsonNode dependencies)throws Exception {
  a.requireUnchanged();requireOpenSession(a,dependencies);if(!producers.require(a.current()).canonicalPins().equals(dependencies.required("producer")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);var current=state(a);if(!current.digest.equals(text(dependencies,"state_sha256")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
  var model=current.model;var definition=(ModelConfigurationProtocol.Definition)model.definition();var j=a.job();String purpose=text(current.policy,"business_purpose");
  var grant=resources.require(modelAdmission,new PermissionProtocol.Scope(j.tenant(),model.project_id(),"CONFIGURATION",model.config_id()),"model.send",purpose);
  var sent=json.object().put("phase","MODEL_CALL").put("action_id",a.actionId()).put("run_id",j.run()).put("step_id",j.step()).put("lease_generation",a.leaseGeneration().toString());sent.set("model_send",json.tree(grant));var selected=sent.putArray("sources");
  for(var fixed:a.current().current().sources()){var r=TaskPlanDraftPackages.modelRequirement(fixed.source(),a,purpose,text(current.policy,"environment"),text(current.policy,"region"),definition.provider());var read=sources.require(modelAdmission,r);TaskPlanDraftPackages.requireModelSelection(a,read,purpose);selected.add(json.tree(read));}
  var prompt=dependencies.required("prompt_compilation");sent.set("prompt",packages.requireCurrentPins(modelAdmission,j.project(),prompt.required("prompt_package"),purpose,definition.provider(),a));sent.set("profile",packages.requireCurrentPins(modelAdmission,j.project(),prompt.required("profile_package"),purpose,definition.provider(),a));
  originals.requireCurrentBasis(a.admission(),definition.price_basis(),"MODEL_CONFIGURATION",j.project());current.enabled.requireUnchanged();return sent;
 }
 void requireRead(TaskPlanDraftRuntimeAuthority.Verified a,JsonNode dependencies)throws Exception {
  a.requireUnchanged();var s=state(a);if(!s.digest.equals(text(dependencies,"state_sha256")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);var prompt=dependencies.required("prompt_compilation");
  for(String key:List.of("role_package","prompt_package","profile_package"))packages.requireReviewedPins(a.admission(),a.job().project(),prompt.required(key));packages.requireReviewedPins(a.admission(),a.job().project(),dependencies.required("tokenizer_artifact"));
  var d=(ModelConfigurationProtocol.Definition)s.model.definition();if(!text(dependencies,"price_authorization_sha256").equals(originals.requireCurrentBasis(a.admission(),d.price_basis(),"MODEL_CONFIGURATION",a.job().project())))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
 }
 private void requireOpenSession(TaskPlanDraftRuntimeAuthority.Verified a,JsonNode dependencies){var j=a.job();var rows=db.queryForList("SELECT admission_state FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND session_id=? AND action_id=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),a.leaseGeneration(),text(dependencies,"session_id"),text(dependencies,"host_action_id"),j.actor());if(rows.size()!=1||!"OPEN".equals(rows.get(0).get("admission_state")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 private void bindPrice(ModelConfigurationProtocol.Fixed model,JsonNode price,PermissionProtocol.Basis basis)throws Exception {var d=(ModelConfigurationProtocol.Definition)model.definition();if(!d.price_basis().equals(basis))bad();schema.require("PriceProfile",price);var n=json.tree(d);for(String k:List.of("provider","model_id","model_version","currency"))if(!n.required(k).equals(price.required(k)))bad();}
 private void require(TaskPlanDraftRuntimeAdapter.Session session,TaskPlanDraftSupervisor.Admitted supervisor,TaskPlanDraftSupervisor.ModelRequest observed)throws Exception {session.requireUnchanged();supervisor.requireUnchanged();observed.requireUnchanged();if(!"OPEN".equals(session.state())||!session.sessionId().equals(supervisor.sessionId())||!session.sessionId().equals(observed.sessionId())||!session.binding().equals(supervisor.binding())||!session.binding().equals(observed.binding()))bad();}
 private static String text(JsonNode n,String field){if(!n.required(field).isTextual())bad();return n.required(field).textValue();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

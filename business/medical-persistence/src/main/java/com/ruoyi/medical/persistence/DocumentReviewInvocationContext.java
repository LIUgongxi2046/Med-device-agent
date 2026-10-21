package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** N07 context producer. Only actual independently observed SDK bytes, physically read
 * packages and an immutable registered DocumentReview input can produce the exact input. */
public final class DocumentReviewInvocationContext {
 private final JdbcTemplate db;private final ModelConfigurationService models;private final ModelEligibilityAuthority eligibility;
 private final ResourceReadAuthority resources;private final SourceReadAuthority sources;private final TaskInvocationPackages packages;
 private final FormalInvocationSourceOriginals originals;private final FormalInvocationSchema schema;private final DocumentReviewInvocationSchema exact;
 private final PermissionSchema json;private final DocumentReviewInvocationProduction producers;private final RuntimeObjectTransport transport;private final DocumentReviewContextReads contentReads;
 public DocumentReviewInvocationContext(DataSource data,ModelConfigurationService models,ModelEligibilityAuthority eligibility,ResourceReadAuthority resources,
   SourceReadAuthority sources,TaskInvocationPackages packages,FormalInvocationSourceOriginals originals,FormalInvocationSchema schema,
   DocumentReviewInvocationSchema exact,PermissionSchema json,RuntimeObjectTransport transport,DocumentReviewInvocationProduction producers,DocumentReviewContextReads contentReads){
  db=new JdbcTemplate(data);db.setQueryTimeout(5);this.models=models;this.eligibility=eligibility;this.resources=resources;this.sources=sources;this.packages=packages;this.originals=originals;this.schema=schema;this.exact=exact;this.json=json;this.transport=transport;this.producers=producers;this.contentReads=contentReads;
 }
 private record State(ModelConfigurationProtocol.Fixed model,ModelEligibilityAuthority.Verified enabled,JsonNode policy,JsonNode node,JsonNode replay,String digest){}
 private State state(DocumentReviewRuntimeAuthority.Verified authority)throws Exception {
  authority.requireUnchanged();var admitted=authority.current().current();var j=authority.job();var replay=admitted.evidence();var policy=replay.required("policy_definition");var node=replay.required("node_binding");
  if(!"N07".equals(text(node,"node_kind")))bad();
  var basis=json.basis(node.required("model_version"));var model=models.requireCurrentExternal(authority.admission(),basis.version_id(),"RUN_CREATION",j.project());if(!basis.content_sha256().equals(schema.hash(model)))bad();
  var enabled=eligibility.require(authority.admission(),basis.version_id(),"RUN_CREATION",j.project());if(!enabled.contentSha256().equals(basis.content_sha256()))bad();
  enabled.requireDataUse(j.classification(),text(policy,"region"),policy.required("training_allowed").booleanValue());enabled.requireParameters(node.required("generation_parameters"));
  var witness=json.object();witness.set("model",json.tree(model));witness.set("eligibility",json.tree(enabled.safeReplayBinding()));witness.set("admission",replay);witness.put("input_sha256",authority.inputSha256()).put("document_baseline_sha256",authority.document().baselineSha256());
  enabled.requireUnchanged();return new State(model,enabled,policy,node,replay,schema.hash(witness));
 }

 public static final class Compilation {
  private final byte[] bytes;private final PermissionProtocol.Basis price,tokenizer;private final AgentReleaseProtocol.SchemaBoundArtifact executable;
  private Compilation(byte[] bytes,PermissionProtocol.Basis price,PermissionProtocol.Basis tokenizer,AgentReleaseProtocol.SchemaBoundArtifact executable){this.bytes=bytes.clone();this.price=price;this.tokenizer=tokenizer;this.executable=executable;}
  public byte[] bytes(){return bytes.clone();}public PermissionProtocol.Basis priceBasis(){return price;}public PermissionProtocol.Basis tokenizerBasis(){return tokenizer;}public AgentReleaseProtocol.SchemaBoundArtifact tokenizerArtifact(){return executable;}
 }
 public Compilation compile(DocumentReviewRuntimeAdapter.Session session,DocumentReviewSupervisor.Admitted supervisor,DocumentReviewSupervisor.ModelRequest observed,DocumentReviewPromptCompiler.Compiled prompt)throws Exception {
  require(session,supervisor,observed);var a=session.authority();prompt.requireFor(a);var current=state(a);var projection=exact.readProjection(observed.bytes());
  if(observed.ordinal()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  if(!prompt.launchText().equals(text(session.launch(),"prompt"))||!text(session.deployment().value().required("sdk"),"model").equals(text(projection,"model"))||!projection.required("parameters").equals(current.node.required("generation_parameters")))bad();
  if(!projection.required("tools").isArray()||!projection.required("tools").isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  var document=DocumentReviewInvocationProjection.initial(projection,session.sessionId(),prompt.launchText(),prompt.approvedSystemMessages(),projection.required("tools"));
  byte[] bytes=ContentReadAuthorization.canonicalBytes(document);exact.readExactInput(bytes);
  var parser=new com.fasterxml.jackson.databind.ObjectMapper().setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
  var executable=parser.treeToValue(current.node.required("tokenizer_artifact"),AgentReleaseProtocol.SchemaBoundArtifact.class);
  return new Compilation(bytes,((ModelConfigurationProtocol.Definition)current.model.definition()).price_basis(),json.basis(current.node.required("tokenizer_version")),executable);
 }
 public Prepared prepare(DocumentReviewRuntimeAdapter.Session session,DocumentReviewSupervisor.Admitted supervisor,DocumentReviewSupervisor.ModelRequest observed,
   DocumentReviewPromptCompiler.Compiled prompt,FormalInvocationSourceOriginals.Read price,TaskInvocationPackages.Read executable,FormalTokenizerRunner.Result counted)throws Exception {
  var c=compile(session,supervisor,observed,prompt);var a=session.authority();var s=state(a);price.requireCurrent(a.admission());executable.requireCurrent(a.admission());
  if(!c.price.version_id().equals(price.sourceVersion())||!c.price.content_sha256().equals(price.contentSha256())||!c.executable.artifact().artifactId().equals(executable.artifact().version_id())||!c.executable.artifact().sha256().equals(executable.artifact().content_sha256())||!c.executable.schema().schemaRef().equals(executable.schemaRef())||!c.executable.schema().schemaSha256().equals(executable.schemaSha256()))bad();
  if(!c.tokenizer.version_id().equals(counted.tokenizerVersion())||!c.executable.artifact().sha256().equals(counted.executableSha256())||!WireValues.sha256(c.bytes).equals(counted.inputSha256())||!WireValues.sha256(executable.bytes()).equals(counted.executableSha256()))bad();
  var priceDocument=schema.read("PriceProfile",price.bytes(),DocumentReviewInvocationSchema.MAXIMUM_BYTES);bindPrice(s.model,priceDocument,c.price);
  var maximum=new BigInteger(text(s.replay.required("release_manifest").required("limits"),"max_input_tokens"));if(counted.tokenCount().compareTo(maximum)>0)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  var request=json.object().put("maximum_input_tokens",maximum.toString());request.set("request_parameters",s.node.required("generation_parameters"));FormalInvocationModelPolicy.require(json.tree(s.model),request,a.job().classification(),counted.tokenCount());
  var members=new TreeMap<String,ArtifactRegistryProtocol.MemberRow>();for(var group:List.of(prompt.contentMembers(),executable.contentMembers(),price.contentMembers()))for(var member:group){var old=members.putIfAbsent(member.upstreamVersionId(),member);if(old!=null&&!old.equals(member))bad();}var producer=producers.require(a,a.admission());producer.requireBytes("DOCUMENT_REVIEW_EXACT_INPUT",c.bytes.length);
  return new Prepared(session,supervisor,observed,prompt,price,executable,counted,c,s,priceDocument,List.copyOf(members.values()),List.copyOf(members.keySet()),producer.canonicalPins(),producer.enterpriseBudgetBasis(),parentBudget(a));
 }
 private PermissionProtocol.Basis parentBudget(DocumentReviewRuntimeAuthority.Verified a)throws Exception {a.requireUnchanged();return a.current().current().parentBudget();}

 public final class Prepared {
  private final DocumentReviewRuntimeAdapter.Session session;private final DocumentReviewSupervisor.Admitted supervisor;private final DocumentReviewSupervisor.ModelRequest observed;private final DocumentReviewPromptCompiler.Compiled prompt;
  private final FormalInvocationSourceOriginals.Read price;private final TaskInvocationPackages.Read executable;private final FormalTokenizerRunner.Result counted;private final Compilation compilation;private final State state;private final JsonNode priceDocument,producerPins;private final List<ArtifactRegistryProtocol.MemberRow> members;private final List<String> roots;private final PermissionProtocol.Basis enterprise,parent;
  private Prepared(DocumentReviewRuntimeAdapter.Session session,DocumentReviewSupervisor.Admitted supervisor,DocumentReviewSupervisor.ModelRequest observed,DocumentReviewPromptCompiler.Compiled prompt,FormalInvocationSourceOriginals.Read price,TaskInvocationPackages.Read executable,FormalTokenizerRunner.Result counted,Compilation compilation,State state,JsonNode priceDocument,List<ArtifactRegistryProtocol.MemberRow> members,List<String> roots,JsonNode producerPins,PermissionProtocol.Basis enterprise,PermissionProtocol.Basis parent){this.session=session;this.supervisor=supervisor;this.observed=observed;this.prompt=prompt;this.price=price;this.executable=executable;this.counted=counted;this.compilation=compilation;this.state=state;this.priceDocument=priceDocument.deepCopy();this.members=List.copyOf(members);this.roots=List.copyOf(roots);this.producerPins=producerPins.deepCopy();this.enterprise=enterprise;this.parent=parent;if(enterprise.version_id().equals(parent.version_id()))bad();}
  public void requireUnchanged()throws Exception {var fresh=prepare(session,supervisor,observed,prompt,price,executable,counted);if(!producerPins.equals(fresh.producerPins)||!state.digest.equals(fresh.state.digest)||!Arrays.equals(compilation.bytes,fresh.compilation.bytes)||!members.equals(fresh.members)||!roots.equals(fresh.roots)||!enterprise.equals(fresh.enterprise)||!parent.equals(fresh.parent))stale();}
  public byte[] bytes(){return compilation.bytes();}public DocumentReviewRuntimeAuthority.Verified authority(){return session.authority();}public String sessionId(){return session.sessionId();}public String requestId(){return observed.requestId();}public String requestSha256(){return observed.sha256();}
  public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return members;}public List<String> contentRoots(){return roots;}
  public JsonNode provenance()throws Exception {
   requireUnchanged();var a=authority();var j=a.job();var input=a.document();var n=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","CHECK_DOCUMENT").put("node_kind","N07").put("phase","PREPARATION_READ_ONLY").put("tenant_id",j.tenant()).put("project_id",j.project()).put("document_id",j.document()).put("run_id",j.run()).put("step_id",j.step()).put("principal_id",j.principal()).put("host_action_id",session.actionId()).put("actor_id",j.actor()).put("classification",j.classification()).put("lease_generation",a.leaseGeneration().toString()).put("worker_service_uri",a.workerService()).put("session_id",sessionId()).put("input_sha256",j.inputHash()).put("input_artifact_id",input.header().bodyArtifactId()).put("input_receipt_sha256",input.baselineSha256()).put("document_revision",input.header().documentRevision()).put("body_sha256",input.header().bodySha256()).put("payload_sha256",WireValues.sha256(bytes())).put("state_sha256",state.digest).put("supervision_receipt_id",supervisor.receiptId()).put("supervision_sha256",supervisor.sha256()).put("deployment_sha256",supervisor.deploymentSha256());
   n.set("host_binding",session.binding());n.set("model_request",observed.canonicalReceipt());n.set("model",json.tree(state.model));n.set("model_eligibility",json.tree(state.enabled.safeReplayBinding()));n.set("price",priceDocument);n.put("price_authorization_sha256",price.authorizationSha256());n.set("prompt_compilation",prompt.canonicalPins());n.set("tokenizer_artifact",executable.canonicalPins());n.set("tokenizer_version",json.tree(compilation.tokenizer));n.set("tokenization",json.tree(Map.of("tokenizer_version",counted.tokenizerVersion(),"executable_sha256",counted.executableSha256(),"input_sha256",counted.inputSha256(),"output_sha256",counted.outputSha256(),"token_count",counted.tokenCount().toString())));n.set("request_parameters",state.node.required("generation_parameters"));n.set("output_storage",json.tree(transport.storageBinding()));n.set("producer",producerPins);n.set("retention",state.replay.required("retention"));n.set("output_retention",TaskInvocationOutputPolicy.forRun(j.run(),j.created().toInstant(java.time.ZoneOffset.UTC),state.replay.required("retention")));n.set("content_members",json.tree(members));n.set("content_roots",json.tree(roots));n.put("launch_prompt",prompt.launchText());n.set("approved_system_messages",json.tree(prompt.approvedSystemMessages()));n.set("enterprise_budget_basis",json.tree(enterprise));n.set("parent_budget_basis",json.tree(parent));n.put("maximum_input_tokens",text(state.replay.required("release_manifest").required("limits"),"max_input_tokens"));n.put("business_purpose",text(state.policy,"business_purpose"));return n;
  }
 }
 JsonNode requireTransmission(EnterpriseAccess.Admission modelAdmission,DocumentReviewRuntimeAuthority.Verified a,JsonNode dependencies)throws Exception {
  a.requireUnchanged();requireOpenSession(a,dependencies);var producer=producers.require(a,modelAdmission);if(!producer.canonicalPins().equals(dependencies.required("producer")))stale();producer.requireModelInputConsume(Integer.parseInt(text(dependencies.required("exact_input_receipt").required("object_ref"),"size_bytes")));var current=state(a);if(!current.digest.equals(text(dependencies,"state_sha256")))stale();requireRead(a,dependencies);
  var model=current.model;var definition=(ModelConfigurationProtocol.Definition)model.definition();var j=a.job();String purpose=text(current.policy,"business_purpose");
  var grant=resources.require(modelAdmission,new PermissionProtocol.Scope(j.tenant(),model.project_id(),"CONFIGURATION",model.config_id()),"model.send",purpose);
  var sent=json.object().put("phase","MODEL_CALL").put("action_id",a.actionId()).put("run_id",j.run()).put("step_id",j.step()).put("lease_generation",a.leaseGeneration().toString());sent.set("model_send",json.tree(grant));var selected=sent.putArray("sources");
  var prompt=dependencies.required("prompt_compilation");sent.set("review_content",contentReads.requireCurrentPins(a,prompt.required("review_content"),true,definition.provider()));sent.set("prompt",packages.requireCurrentPins(modelAdmission,j.project(),prompt.required("prompt_package"),purpose,definition.provider(),a));sent.set("profile",packages.requireCurrentPins(modelAdmission,j.project(),prompt.required("profile_package"),purpose,definition.provider(),a));current.enabled.requireUnchanged();return sent;
 }
 void requireOriginalReadPins(EnterpriseAccess.Admission a,DocumentReviewExecutionService.Job job,JsonNode pins)throws Exception {contentReads.requireOriginalPins(a,job,pins);}
 void requireReservation(DocumentReviewRuntimeAuthority.Verified a)throws Exception {a.requireUnchanged();producers.require(a,a.admission()).requireInvocationCapacity();}
 void requireRead(DocumentReviewRuntimeAuthority.Verified a,JsonNode dependencies)throws Exception {
  a.requireUnchanged();var s=state(a);if(!s.digest.equals(text(dependencies,"state_sha256")))stale();var prompt=dependencies.required("prompt_compilation");
  contentReads.requireCurrentPins(a,prompt.required("review_content"),false,null);
  for(String key:List.of("role_package","prompt_package","profile_package"))packages.requireReviewedPins(a.admission(),a.job().project(),prompt.required(key));packages.requireReviewedPins(a.admission(),a.job().project(),dependencies.required("tokenizer_artifact"));
  var d=(ModelConfigurationProtocol.Definition)s.model.definition();if(!text(dependencies,"price_authorization_sha256").equals(originals.requireCurrentBasis(a.admission(),d.price_basis(),"MODEL_CONFIGURATION",a.job().project())))stale();
  a.document().requireCurrent(a.admission());if(!a.document().baselineSha256().equals(text(dependencies,"input_receipt_sha256")))stale();
 }
 private void requireOpenSession(DocumentReviewRuntimeAuthority.Verified a,JsonNode dependencies){var j=a.job();var rows=db.queryForList("SELECT canonical_start,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND session_id=? AND action_id=? AND created_by=? AND admission_state='OPEN' LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),a.leaseGeneration(),text(dependencies,"session_id"),text(dependencies,"host_action_id"),j.actor());if(rows.size()!=1)stale();}
 private void bindPrice(ModelConfigurationProtocol.Fixed model,JsonNode price,PermissionProtocol.Basis basis)throws Exception {var d=(ModelConfigurationProtocol.Definition)model.definition();if(!d.price_basis().equals(basis))bad();schema.require("PriceProfile",price);var n=json.tree(d);for(String k:List.of("provider","model_id","model_version","currency"))if(!n.required(k).equals(price.required(k)))bad();}
 private void require(DocumentReviewRuntimeAdapter.Session session,DocumentReviewSupervisor.Admitted supervisor,DocumentReviewSupervisor.ModelRequest observed)throws Exception {session.requireUnchanged();supervisor.requireUnchanged();observed.requireUnchanged();if(!"OPEN".equals(session.state())||!session.sessionId().equals(supervisor.sessionId())||!session.sessionId().equals(observed.sessionId())||!session.binding().equals(supervisor.binding())||!session.binding().equals(observed.binding()))bad();}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String text(JsonNode n,String field){if(!n.required(field).isTextual())bad();return n.required(field).textValue();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}

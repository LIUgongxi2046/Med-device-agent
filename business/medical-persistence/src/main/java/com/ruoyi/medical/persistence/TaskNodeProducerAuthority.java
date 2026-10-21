package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Private bounded pre-intent authority. Not an artifact read token, source grant or model permission. */
public final class TaskNodeProducerAuthority {
 private final TaskNodeProducerService rules;private final PermissionSchema json;private final JdbcTemplate db;
 private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public TaskNodeProducerAuthority(TaskNodeProducerService rules,PermissionSchema json,DataSource data){this.rules=Objects.requireNonNull(rules);this.json=Objects.requireNonNull(json);this.db=new JdbcTemplate(data);db.setQueryTimeout(5);}
 public Verified require(TaskNodeProductionBasis bootstrap,TaskRunReplayRegistration.Registered replay,
     TaskRunGraphService.Verified graph,AgentPublicationAuthority.VerifiedPublication publication,
     AgentExecutionPolicyAuthority.VerifiedPolicy policy,String ruleId,String ruleSha256,String questionId)throws Exception {
  Objects.requireNonNull(bootstrap);Objects.requireNonNull(replay);Objects.requireNonNull(graph);
  var nodes=policy.definition().bindings().stream().filter(n->"N02".equals(n.nodeKind())).toList();if(nodes.size()!=1)deny();
  var a=bootstrap.admission();graph.requireUnchanged();bootstrap.requireUnchanged();
  if(graph.admission()!=a||!graph.runId().equals(bootstrap.runId())||!graph.taskId().equals(bootstrap.taskId())
    ||!graph.principalId().equals(bootstrap.principalId())||!graph.replaySha256().equals(bootstrap.replay().rawSha256()))deny();
  graph.question(questionId);
  var checked=rules.requireCurrent(a,publication,policy,nodes.get(0).roleVersion(),ruleId,ruleSha256,bootstrap.taskId(),null,null);
  var proof=new Verified(bootstrap,replay,graph,publication,policy,nodes.get(0).roleVersion(),questionId,checked);
  proof.requireUnchanged();return proof;
 }
 public final class Verified {
  private final TaskNodeProductionBasis bootstrap;private final TaskRunReplayRegistration.Registered replay;
  private final TaskRunGraphService.Verified graph;private final AgentPublicationAuthority.VerifiedPublication publication;
  private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final PermissionProtocol.Basis role;
  private final String question;private final TaskNodeProducerService.Checked original;
  private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
  private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
  private Verified(TaskNodeProductionBasis bootstrap,TaskRunReplayRegistration.Registered replay,TaskRunGraphService.Verified graph,
    AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,
    PermissionProtocol.Basis role,String question,TaskNodeProducerService.Checked original){this.bootstrap=bootstrap;this.replay=replay;this.graph=graph;this.publication=publication;this.policy=policy;this.role=role;this.question=question;this.original=original;}
  private void active(){TaskFieldTargetInputAuthority.requireLifetime(bootstrap.admission(),bootstrap.admission(),thread,scope,transaction);}
  public void requireUnchanged()throws Exception {
   active();bootstrap.requireUnchanged();graph.requireUnchanged();graph.question(question);
   var fresh=rules.requireCurrent(bootstrap.admission(),publication,policy,role,original.capabilityId(),original.capabilityHash(),bootstrap.taskId(),original.decisionId(),original.decisionHash());
   if(!fresh.fingerprint().equals(original.fingerprint()))stale();
   var m=original.manifest();TaskNodeProducerService.requireClassification(text(m,"classification"),bootstrap.classification());rules.requireReplayPins(m,bootstrap.replay().content());
   if(!graph.planRevision().equals(text(m,"plan_revision"))||!graph.planSha256().equals(text(m,"plan_sha256"))
     ||!contains(m.required("question_ids"),question))deny();
   var r=replay.receipt();
   for(var pin:Map.of("tenant_id",bootstrap.tenantId(),"project_id",bootstrap.projectId(),"task_id",bootstrap.taskId(),"run_id",bootstrap.runId(),"principal_id",bootstrap.principalId()).entrySet())
    if(!pin.getValue().equals(text(r,pin.getKey())))deny();
   if(!bootstrap.replay().rawSha256().equals(replay.reference().sha256()))deny();
   var rows=db.queryForList("SELECT receipt_json,receipt_sha256 FROM task_run_replay_registration WHERE tenant_id=? AND project_id=? AND run_id=? AND artifact_id=? FOR UPDATE",bootstrap.tenantId(),bootstrap.projectId(),bootstrap.runId(),replay.artifactId());
   if(rows.size()!=1||!replay.receiptSha256().equals(rows.get(0).get("receipt_sha256"))
      ||!reader.readTree(rows.get(0).get("receipt_json").toString()).equals(r))stale();
  }
  public void requireProduction(EnterpriseAccess.Admission a,String task,String run,String principal,String requestedQuestion,String artifactKind,String bytes)throws Exception {
   requireUnchanged();if(a!=bootstrap.admission()||!bootstrap.taskId().equals(task)||!bootstrap.runId().equals(run)
    ||!bootstrap.principalId().equals(principal)||!question.equals(requestedQuestion))deny();
   // Non-input kinds need a separate active-lease host boundary, never this pre-RN01/queued proof.
   if(!"TASK_NODE_INPUT".equals(artifactKind))deny();
   requireBound(original.manifest(),artifactKind,bytes,"NODE_PRODUCE");
   var intents=db.queryForList("SELECT run_id,question_id FROM task_node_input_intent WHERE tenant_id=? AND producer_rule_id=? FOR UPDATE",bootstrap.tenantId(),ruleId());
   boolean existing=intents.stream().anyMatch(r->run.equals(str(r,"run_id"))&&question.equals(str(r,"question_id")));
   long outputs=db.queryForObject("SELECT COUNT(*) FROM runtime_object_intent o JOIN task_node_input_intent i ON i.tenant_id=o.tenant_id AND i.project_id=o.project_id AND i.run_id=o.run_id AND i.step_id=o.step_id WHERE i.tenant_id=? AND i.producer_rule_id=? AND o.schema_version='2.0.0'",Long.class,bootstrap.tenantId(),ruleId());
   if(java.math.BigInteger.valueOf(intents.size()+outputs+invocationSlots(bootstrap.tenantId(),ruleId(),null)+(existing?0:1)).compareTo(new java.math.BigInteger(text(original.manifest(),"maximum_artifacts")))>0)deny();
  }
  public String ruleId(){active();return original.capabilityId();}public String ruleSha256(){active();return original.capabilityHash();}
  public String decisionId(){active();return original.decisionId();}public String decisionSha256(){active();return original.decisionHash();}
  public JsonNode authorizations()throws Exception{requireUnchanged();return original.authorizations().deepCopy();}
 }
 /** Derives output authority only from genuine formal mTLS/action admission and a private registered-input proof. */
 public LeasedProduction requireLeased(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input)throws Exception {
  TaskNodeProducerService.requireAdmission(a);if(!a.formalWorker())deny();
  var identity=a.formalProof().identity();input.requireUnchanged();
  if(!"TASK".equals(identity.executionKind())||!identity.run().equals(input.runId())||!identity.step().equals(input.stepId())
    ||!identity.tenant().equals(input.tenantId())||!identity.project().equals(input.projectId()))deny();
  var pins=producerPins(a,input);var current=rules.requireRegisteredRule(a,str(pins,"producer_rule_id"),str(pins,"producer_sha256"),
      str(pins,"producer_decision_id"),str(pins,"producer_decision_sha256"),input.manifest());
  var proof=new LeasedProduction(a,input,identity,current,"HOST_CONTROL");proof.requireUnchanged();return proof;
 }
 private Map<String,Object> producerPins(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input){
  var rows=db.queryForList("SELECT i.producer_rule_id,i.producer_sha256,i.producer_decision_id,i.producer_decision_sha256 FROM task_node_input_intent i JOIN task_node_input_registered r ON r.tenant_id=i.tenant_id AND r.project_id=i.project_id AND r.write_id=i.write_id AND r.artifact_id=i.artifact_id AND r.payload_sha256=i.payload_sha256 WHERE i.tenant_id=? AND i.project_id=? AND i.task_id=? AND i.run_id=? AND i.step_id=? AND r.artifact_id=? AND r.receipt_sha256=? FOR UPDATE",
    input.tenantId(),input.projectId(),input.taskId(),input.runId(),input.stepId(),input.inputId(),input.receiptSha256());
  if(rows.size()!=1)deny();return rows.get(0);
 }
 public final class LeasedProduction {
  private final EnterpriseAccess.Admission admission;private final TaskNodeInputAuthority.Verified input;
  private final FormalWorkerAccess.Identity identity;private final TaskNodeProducerService.Checked original;private final String actionKind;
  private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
  private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
  private LeasedProduction(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,FormalWorkerAccess.Identity identity,TaskNodeProducerService.Checked original,String actionKind){this.admission=a;this.input=input;this.identity=identity;this.original=original;this.actionKind=actionKind;}
  private void active(){TaskFieldTargetInputAuthority.requireLifetime(admission,admission,thread,scope,transaction);if(!identity.equals(admission.formalProof().identity()))deny();}
  public void requireUnchanged()throws Exception {
   active();input.requireUnchanged();var pins=producerPins(admission,input);
   if(!original.capabilityId().equals(str(pins,"producer_rule_id"))||!original.capabilityHash().equals(str(pins,"producer_sha256"))
    ||!original.decisionId().equals(str(pins,"producer_decision_id"))||!original.decisionHash().equals(str(pins,"producer_decision_sha256")))stale();
   var current=rules.requireRegisteredRule(admission,original.capabilityId(),original.capabilityHash(),original.decisionId(),original.decisionHash(),input.manifest());
   if(!current.fingerprint().equals(original.fingerprint()))stale();
   var leases=db.queryForList("SELECT s.step_id FROM run_step s JOIN task_run r ON r.tenant_id=s.tenant_id AND r.project_id=s.project_id AND r.run_id=s.run_id JOIN run_principal p ON p.tenant_id=r.tenant_id AND p.project_id=r.project_id AND p.run_id=r.run_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? AND s.status='RUNNING' AND s.lease_generation=? AND s.lease_owner=? AND s.lease_expires_at>UTC_TIMESTAMP(6) AND s.input_sha256=? AND r.execution_kind='TASK' AND r.task_id=? AND r.created_by=? AND r.status IN ('QUEUED','RUNNING') AND r.ended_at IS NULL AND r.dispatch_closed_at IS NULL AND r.superseded_by_run_id IS NULL AND p.principal_id=? AND p.status='ACTIVE' FOR UPDATE",
    identity.tenant(),identity.project(),identity.run(),identity.step(),identity.generation(),identity.workerService(),input.inputSha256(),input.taskId(),identity.actor(),identity.principal());
   if(leases.size()!=1)deny();
   var actions=db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND lease_generation=? AND kind=? AND state IN ('BOUND','RESERVED','DISPATCHING','IN_FLIGHT') FOR UPDATE",
      identity.tenant(),identity.project(),identity.run(),identity.step(),identity.action(),input.inputSha256(),identity.generation(),actionKind);
   if(actions.size()!=1)deny();
   requireBudget(original.manifest(),identity);
  }
  /** Exact host-only read. Registration or a generic artifact permission never implies this operation. */
  public void requireHostInputConsume(TaskNodeInputAuthority.Verified exactInput)throws Exception {
   requireUnchanged();if(!"HOST_CONTROL".equals(actionKind)||exactInput!=input)deny();exactInput.requireUnchanged();
   requireBound(original.manifest(),"TASK_NODE_INPUT",exactInput.objectReference().sizeBytes(),"HOST_INPUT_CONSUME");
   exactInput.requireUnchanged();requireUnchanged();
  }
  /** Exact consuming lease and explicit 098 operation, never generic artifact permission.
   * The private reader separately checks graph, original completion, source closure and physical bytes. */
  public void requireHostPredecessorConsume(TaskNodeInputAuthority.Verified exactInput,String bytes)throws Exception {
   requireUnchanged();if(!"HOST_CONTROL".equals(actionKind)||exactInput!=input)deny();exactInput.requireUnchanged();
   requireBound(original.manifest(),"EVIDENCE_BUNDLE",bytes,"HOST_PREDECESSOR_CONSUME");exactInput.requireUnchanged();requireUnchanged();
  }
  public void requireProduction(TaskProfessionalRuntimeService.Envelope envelope)throws Exception {
   envelope.requireProductionReady();
   Objects.requireNonNull(envelope);requireUnchanged();envelope.requireUnchanged();
   requireProducedIdentity(envelope.runId(),envelope.stepId(),envelope.leaseGeneration(),envelope.workerService(),envelope.inputId(),envelope.inputSha256(),envelope.sessionId(),envelope.actionId());
   byte[] bytes=envelope.bytes();if(!envelope.payloadSha256().equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)))deny();
   requireBytes("STEP_ENVELOPE",bytes);envelope.requireUnchanged();requireUnchanged();
  }
  public void requireProduction(TaskResearchExecutionService.Produced produced)throws Exception {
   Objects.requireNonNull(produced);requireUnchanged();produced.requireUnchanged();
   requireProducedIdentity(produced.runId(),produced.stepId(),produced.leaseGeneration(),produced.workerService(),produced.inputId(),produced.inputSha256(),produced.sessionId(),produced.actionId());
   if(!Set.of("EVIDENCE_BUNDLE","SESSION_EXPORT","STEP_RESULT").contains(produced.kind()))deny();
   byte[] bytes=produced.bytes();if(!produced.payloadSha256().equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)))deny();
   requireBytes(produced.kind(),bytes);produced.requireUnchanged();requireUnchanged();
  }
  public void requireProduction(TaskInvocationContext.Prepared exact)throws Exception {
   Objects.requireNonNull(exact);requireUnchanged();exact.requireUnchanged();
   requireProducedIdentity(exact.runId(),exact.stepId(),exact.leaseGeneration(),exact.workerService(),exact.inputId(),exact.inputSha256(),exact.sessionId(),exact.actionId());
   if(!"TASK_EXACT_INPUT".equals(exact.kind())||!"OPEN".equals(exact.session().state())||!exact.sessionId().equals(exact.admitted().sessionId())||!FormalTaskNodeAuthority.sameEvidence(exact.session().binding(),exact.admitted().binding()))deny();
   byte[] bytes=exact.bytes();if(!exact.payloadSha256().equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)))deny();
   requireBytes("TASK_EXACT_INPUT",bytes,TaskProfessionalObjectLedger.exactKey(exact));exact.requireUnchanged();requireUnchanged();
  }
  /** Exact registered prepared bytes, not session admission. The explicit 098 read operation
   * remains mandatory even when this same producer was authorized to create the envelope. */
  public void requireHostEnvelopeConsume(TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalObjectRegistration.Registered registered)throws Exception {
   Objects.requireNonNull(envelope);Objects.requireNonNull(registered);requireUnchanged();envelope.requireUnchanged();registered.requireUnchanged();
   requireProducedIdentity(envelope.runId(),envelope.stepId(),envelope.leaseGeneration(),envelope.workerService(),envelope.inputId(),envelope.inputSha256(),envelope.sessionId(),envelope.actionId());
   var pins=Map.ofEntries(Map.entry("producer_rule_id",original.capabilityId()),Map.entry("producer_sha256",original.capabilityHash()),
     Map.entry("producer_decision_id",original.decisionId()),Map.entry("producer_decision_sha256",original.decisionHash()),
     Map.entry("tenant_id",identity.tenant()),Map.entry("project_id",identity.project()),Map.entry("run_id",identity.run()),Map.entry("step_id",identity.step()),
     Map.entry("principal_id",identity.principal()),Map.entry("action_id",identity.action()),Map.entry("lease_generation",identity.generation()),
     Map.entry("worker_service_uri",identity.workerService()),Map.entry("input_artifact_id",input.inputId()),Map.entry("input_sha256",input.inputSha256()),
     Map.entry("input_receipt_sha256",input.receiptSha256()),Map.entry("session_id",envelope.sessionId()),Map.entry("created_by",identity.actor()),
     Map.entry("schema_version","2.0.0"),Map.entry("schema_ref",TaskProfessionalRuntimeService.ENVELOPE_REF));
   for(var pin:pins.entrySet())if(!pin.getValue().equals(registered.pin(pin.getKey())))deny();
   byte[] bytes=envelope.bytes();var object=registered.reference();
   if(!"STEP_ENVELOPE".equals(registered.kind())||!envelope.payloadSha256().equals(object.sha256())
      ||!org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes).equals(object.sha256())||!Integer.toString(bytes.length).equals(object.sizeBytes())
      ||!"application/json".equals(object.mediaType())||object.objectVersion()==null||object.objectVersion().isBlank()
      ||!"PREPARED_BYTES".equals(registered.receipt().required("provenance").required("phase").asText())
      ||!envelope.sessionId().equals(registered.receipt().required("provenance").required("session_preparation_id").asText()))deny();
   requireBound(original.manifest(),"STEP_ENVELOPE",Integer.toString(bytes.length),"HOST_ENVELOPE_CONSUME");
   envelope.requireUnchanged();registered.requireUnchanged();requireUnchanged();
  }
  private void requireProducedIdentity(String run,String step,String generation,String service,String inputId,String inputHash,String session,String action){
   active();if(!"HOST_CONTROL".equals(actionKind))deny();if(!identity.run().equals(run)||!identity.step().equals(step)||!identity.generation().equals(generation)
     ||!identity.workerService().equals(service)||!identity.action().equals(action)||!input.inputId().equals(inputId)||!input.inputSha256().equals(inputHash))deny();
   com.ruoyi.medical.contracts.WireValues.uuid(session);
  }
  private void requireBytes(String kind,byte[] actualBytes)throws Exception {requireBytes(kind,actualBytes,TaskProfessionalObjectLedger.key(identity.run(),identity.step(),identity.generation(),kind));}
  private void requireBytes(String kind,byte[] actualBytes,String expectedKey)throws Exception {
   requireUnchanged();if(actualBytes==null)deny();
   requireBound(original.manifest(),kind,Integer.toString(actualBytes.length),"NODE_PRODUCE");
   String digest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(actualBytes);
   var outputs=db.queryForList("SELECT o.write_kind,o.object_sha256,o.step_id,o.write_key FROM runtime_object_intent o JOIN task_node_input_intent i ON i.tenant_id=o.tenant_id AND i.project_id=o.project_id AND i.run_id=o.run_id AND i.step_id=o.step_id WHERE i.tenant_id=? AND i.producer_rule_id=? AND o.schema_version='2.0.0' FOR UPDATE",identity.tenant(),original.capabilityId());
   long inputs=db.queryForObject("SELECT COUNT(*) FROM task_node_input_intent WHERE tenant_id=? AND producer_rule_id=?",Long.class,identity.tenant(),original.capabilityId());
   boolean same=outputs.stream().anyMatch(r->expectedKey.equals(str(r,"write_key"))&&kind.equals(str(r,"write_kind"))&&digest.equals(str(r,"object_sha256"))&&identity.step().equals(str(r,"step_id")));
   if(java.math.BigInteger.valueOf(inputs+outputs.size()+invocationSlots(identity.tenant(),original.capabilityId(),null)+(same?0:1)).compareTo(new java.math.BigInteger(text(original.manifest(),"maximum_artifacts")))>0)deny();
  }
  public String ruleId(){active();return original.capabilityId();}public String ruleSha256(){active();return original.capabilityHash();}
  public String decisionId(){active();return original.decisionId();}public String decisionSha256(){active();return original.decisionHash();}
  public PermissionProtocol.Basis enterpriseBudgetBasis()throws Exception{requireUnchanged();return json.basis(original.manifest().required("enterprise_budget_basis"));}
  public String runId(){active();return identity.run();}public String stepId(){active();return identity.step();}
  public String principalId(){active();return identity.principal();}public String leaseGeneration(){active();return identity.generation();}
  public String workerService(){active();return identity.workerService();}
  public JsonNode authorizations()throws Exception{requireUnchanged();return original.authorizations().deepCopy();}
 }

 /** A MODEL_CALL-bound proof, distinct from the HOST_CONTROL producer and never a dispatch token. */
 public InvocationProduction requireInvocation(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,
     TaskInvocationContext.Registered context)throws Exception {
  TaskNodeProducerService.requireAdmission(a);if(!a.formalWorker())deny();context.requireCurrent(a);input.requireUnchanged();
  var identity=a.formalProof().identity();var node=context.request().node();
  if(!"TASK".equals(identity.executionKind())||!identity.tenant().equals(input.tenantId())||!identity.project().equals(input.projectId())
    ||!identity.run().equals(input.runId())||!identity.step().equals(input.stepId())||!identity.run().equals(node.run_id())
    ||!identity.step().equals(node.step_id())||!identity.generation().equals(node.lease_generation())
    ||!identity.workerService().equals(node.worker_service_uri())||identity.action().equals(node.host_action_id())
    ||!input.inputId().equals(node.node_input_artifact_id())||!input.receiptSha256().equals(node.node_input_receipt_sha256()))deny();
  var pins=producerPins(a,input);var current=rules.requireRegisteredRule(a,str(pins,"producer_rule_id"),str(pins,"producer_sha256"),
    str(pins,"producer_decision_id"),str(pins,"producer_decision_sha256"),input.manifest());
  var lease=new LeasedProduction(a,input,identity,current,"MODEL_CALL");lease.requireUnchanged();
  return new InvocationProduction(lease,context);
 }
 public final class InvocationProduction {
  private final LeasedProduction lease;private final TaskInvocationContext.Registered context;
  private InvocationProduction(LeasedProduction lease,TaskInvocationContext.Registered context){this.lease=lease;this.context=context;}
  public void requireUnchanged()throws Exception{lease.requireUnchanged();context.requireCurrent(lease.admission);}
  /** Both slots are reserved by persistence of this exact object in the original 076 binding,
   * in the same transaction holding the current 098 rule lock. No separate guessed output IDs. */
  public void requireModelInputConsume()throws Exception {
   requireUnchanged();
   requireBound(lease.original.manifest(),"TASK_EXACT_INPUT",context.inputSizeBytes(),"MODEL_INPUT_CONSUME");
   requireUnchanged();
  }
  public JsonNode commitment(String invocation)throws Exception {
   com.ruoyi.medical.contracts.WireValues.uuid(invocation);requireUnchanged();var rule=lease.original.manifest();
   for(String kind:List.of("MODEL_RESULT","PROVIDER_CAPTURE"))requireBound(rule,kind,"1","NODE_PRODUCE");
   long inputCount=db.queryForObject("SELECT COUNT(*) FROM task_node_input_intent WHERE tenant_id=? AND producer_rule_id=?",Long.class,lease.identity.tenant(),lease.ruleId());
   long outputs=db.queryForObject("SELECT COUNT(*) FROM runtime_object_intent o JOIN task_node_input_intent i ON i.tenant_id=o.tenant_id AND i.project_id=o.project_id AND i.run_id=o.run_id AND i.step_id=o.step_id WHERE i.tenant_id=? AND i.producer_rule_id=? AND o.schema_version='2.0.0'",Long.class,lease.identity.tenant(),lease.ruleId());
   if(java.math.BigInteger.valueOf(inputCount+outputs+invocationSlots(lease.identity.tenant(),lease.ruleId(),invocation)+2)
      .compareTo(new java.math.BigInteger(text(rule,"maximum_artifacts")))>0)deny();
   var value=json.object().put("schema_version","1.0.0").put("kind","TASK_INVOCATION_OUTPUT_COMMITMENT")
     .put("tenant_id",lease.identity.tenant()).put("project_id",lease.identity.project()).put("actor_id",lease.identity.actor())
     .put("run_id",lease.identity.run()).put("step_id",lease.identity.step()).put("action_id",lease.identity.action())
     .put("invocation_id",invocation).put("context_id",context.contextId()).put("lease_generation",lease.identity.generation())
     .put("worker_service_uri",lease.identity.workerService()).put("session_id",context.request().node().session_id())
     .put("producer_rule_id",lease.ruleId()).put("producer_sha256",lease.ruleSha256())
     .put("producer_decision_id",lease.decisionId()).put("producer_decision_sha256",lease.decisionSha256())
     .put("maximum_bytes",text(rule,"maximum_bytes")).put("write_valid_until",text(rule,"valid_until")).put("reserved_artifacts","2")
     .put("model_config_revision",context.request().model_config_revision()).put("input_artifact_id",lease.input.inputId())
     .put("input_sha256",lease.input.inputSha256()).put("input_receipt_sha256",lease.input.receiptSha256())
     .put("exact_input_artifact_id",context.request().exact_input().artifactId()).put("exact_input_sha256",context.request().exact_input().sha256())
     .put("exact_input_receipt_sha256",context.exactInputReceiptSha256())
     .put("model_request_id",context.modelRequestId()).put("model_request_sha256",context.modelRequestSha256())
     .put("model_request_ordinal",Long.toString(context.modelRequestOrdinal()));
   value.set("enterprise_budget_basis",rule.required("enterprise_budget_basis"));
   value.set("output_storage",context.dependencies().required("output_storage"));
   value.set("output_retention",context.dependencies().required("output_retention"));
   value.set("artifact_kinds",json.tree(List.of("MODEL_RESULT","PROVIDER_CAPTURE")));value.set("authorizations",lease.original.authorizations());
   requireUnchanged();return value;
  }
 }
 private long invocationSlots(String tenant,String rule,String excluded) {
  // The immutable original commitment remains counted even after cancellation or settlement.
  Long count=db.queryForObject("SELECT COUNT(*) FROM formal_invocation_binding WHERE tenant_id=? AND JSON_UNQUOTE(JSON_EXTRACT(authorization_snapshot,'$.task_output_commitment.producer_rule_id'))=? AND (? IS NULL OR invocation_id<>?)",Long.class,tenant,rule,excluded,excluded);
  return Math.multiplyExact(Objects.requireNonNull(count),2L);
 }
 /** Factual recovery only. A current authenticated original-dispatch proof is mandatory;
  * expired execution grants are not revived and this method conveys no content-read permission. */
 public void requireOriginalOutput(FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,String kind,byte[] bytes)throws Exception {
  Objects.requireNonNull(outcome);outcome.requireActive();
  var commitment=verifyOriginalOutput(outcome.tenantId(),outcome.invocationId(),kind,bytes);
  var binding=outcome.binding();for(String key:List.of("project_id","run_id","step_id","action_id","input_sha256","lease_generation"))
   if(!text(commitment,key).equals(text(binding,key)))deny();
  if(!outcome.actorId().equals(text(commitment,"actor_id"))||!text(commitment,"worker_service_uri").equals(text(binding,"service_uri"))
    ||!text(commitment,"context_id").equals(text(binding,"context_manifest_id"))||!text(commitment,"model_config_revision").equals(text(binding,"config_revision")))deny();
  if("MODEL_RESULT".equals(kind)&&!Arrays.equals(bytes,outcome.bytes()))deny();outcome.requireActive();
 }
 /** Historical byte-bound verification only, not a minting or physical-write capability. */
 public JsonNode verifyOriginalOutput(String tenant,String invocation,String kind,byte[] bytes)throws Exception {
  if(!Set.of("MODEL_RESULT","PROVIDER_CAPTURE").contains(kind)||bytes==null||bytes.length==0)deny();
  var commitment=verifyOriginalCommitment(tenant,invocation);
  TaskNodeProducerService.requireReadBound(text(commitment,"maximum_bytes"),Integer.toString(bytes.length));return commitment;
 }
 /** Factual original commitment only, deliberately independent of current execution grants
  * and write cutoff. It does not authorize a new output or any physical transfer. */
 JsonNode verifyOriginalCommitment(String tenant,String invocation)throws Exception {
  if(!TransactionSynchronizationManager.isActualTransactionActive()||!TenantScope.requireCurrent().tenantId().equals(tenant))deny();
  com.ruoyi.medical.contracts.WireValues.uuid(invocation);
  var rows=db.queryForList("SELECT b.authorization_snapshot,b.authorization_sha256,b.canonical_request,b.request_sha256,b.created_by,b.project_id,i.run_id,i.step_id,i.action_id,i.context_manifest_id,i.config_revision,i.input_sha256,i.lease_generation,i.verification_id FROM formal_invocation_binding b JOIN invocation i ON i.tenant_id=b.tenant_id AND i.invocation_id=b.invocation_id JOIN execution_registry e ON e.tenant_id=i.tenant_id AND e.run_id=i.run_id AND e.execution_kind='TASK' WHERE b.tenant_id=? AND b.invocation_id=? FOR UPDATE",tenant,invocation);
  if(rows.size()!=1)deny();var row=rows.get(0);var snapshot=reader.readTree(str(row,"authorization_snapshot"));var request=reader.readTree(str(row,"canonical_request"));
  if(!hash(snapshot).equals(str(row,"authorization_sha256"))||!hash(request).equals(str(row,"request_sha256"))||!"TASK".equals(text(request,"execution_kind")))stale();
  var c=snapshot.required("task_output_commitment");
  if(!"TASK_INVOCATION_OUTPUT_COMMITMENT".equals(text(c,"kind"))||!"1.0.0".equals(text(c,"schema_version"))
    ||!"2".equals(text(c,"reserved_artifacts"))||!tenant.equals(text(c,"tenant_id"))||!invocation.equals(text(c,"invocation_id"))
    ||!json.tree(List.of("MODEL_RESULT","PROVIDER_CAPTURE")).equals(c.required("artifact_kinds")))deny();
  for(String key:List.of("project_id","run_id","step_id","action_id","input_sha256","lease_generation"))if(!text(c,key).equals(str(row,key)))deny();
  if(!text(c,"actor_id").equals(str(row,"created_by"))||!text(c,"context_id").equals(str(row,"context_manifest_id"))
    ||!text(c,"model_config_revision").equals(str(row,"config_revision")))deny();
  var originalRule=originalProducer(c);
  if(!text(c,"write_valid_until").equals(text(originalRule,"valid_until"))||!text(c,"maximum_bytes").equals(text(originalRule,"maximum_bytes")))stale();
  var audits=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND subject_id=? AND action='model.invocation_reserved' AND request_id=? AND trace_id=? AND result='SUCCESS' LIMIT 2",tenant,str(row,"created_by"),invocation,str(row,"verification_id"));
  var events=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='model.invocation_reserved' LIMIT 2",tenant,invocation);
  if(audits.size()!=1||events.size()!=1)deny();var audit=reader.readTree(str(audits.get(0),"resource_ref"));var event=reader.readTree(str(events.get(0),"payload"));
  if(!audit.equals(event)||!str(row,"authorization_snapshot").equals(text(audit.required("binding"),"authorization_snapshot"))
    ||!str(row,"canonical_request").equals(text(audit.required("binding"),"canonical_request")))stale();
  return c.deepCopy();
 }
 /** Original098 integrity only. Does not require current approval or revive execution. */
 private JsonNode originalProducer(JsonNode commitment)throws Exception {
  String tenant=text(commitment,"tenant_id"),project=text(commitment,"project_id"),rule=text(commitment,"producer_rule_id");
  var rules=db.queryForList("SELECT r.*,v.content_sha256 registry_sha256 FROM task_node_producer_rule r JOIN version_registry v ON v.tenant_id=r.tenant_id AND v.version_id=r.rule_id AND v.owner_project_id=r.project_id AND v.entity_kind='TASK_NODE_PRODUCER_RULE' WHERE r.tenant_id=? AND r.project_id=? AND r.rule_id=? LIMIT 2",tenant,project,rule);
  if(rules.size()!=1)deny();var row=rules.get(0);var fixed=reader.readTree(str(row,"fixed_json"));var manifest=fixed.required("manifest");
  if(!FormalTaskNodeAuthority.sameEvidence(fixed,commitment.required("authorizations").required("rule"))||!hash(manifest).equals(text(commitment,"producer_sha256"))||!str(row,"content_sha256").equals(text(commitment,"producer_sha256"))||!str(row,"registry_sha256").equals(text(commitment,"producer_sha256"))||!tenant.equals(text(manifest,"tenant_id"))||!project.equals(text(manifest,"project_id"))||!rule.equals(text(manifest,"rule_id")))stale();
  originalProducerJournal(row,fixed,"task.node_producer.write","AUTHOR");
  var decisions=db.queryForList("SELECT * FROM task_node_producer_decision WHERE tenant_id=? AND project_id=? AND rule_id=? AND decision_id=? AND decision='APPROVE' LIMIT 2",tenant,project,rule,text(commitment,"producer_decision_id"));
  if(decisions.size()!=1)deny();var decision=decisions.get(0);var body=reader.readTree(str(decision,"decision_json"));
  if(!FormalTaskNodeAuthority.sameEvidence(body,commitment.required("authorizations").required("approval"))||!hash(body).equals(text(commitment,"producer_decision_sha256"))||!str(decision,"content_sha256").equals(text(commitment,"producer_decision_sha256"))||!"APPROVED".equals(text(body,"status")))stale();
  originalProducerJournal(decision,body,"task.node_producer.approve","DECIDE");return manifest;
 }
 private void originalProducerJournal(Map<String,Object> row,JsonNode body,String action,String kind)throws Exception {
  String tenant=str(row,"tenant_id"),actor=str(row,"created_by");var receipt=reader.readTree(str(row,"receipt_json"));
  var operations=db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_id=? AND operation_name=? AND idempotency_key=? AND request_sha256=? AND state='COMPLETED' AND http_status=200 AND target_id=? AND error_code IS NULL AND created_by=? AND created_at=? AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2",tenant,actor,str(row,"operation_id"),"TASK-NODE-PRODUCER-"+kind,str(row,"idempotency_key"),str(row,"request_sha256"),str(row,"rule_id"),actor,row.get("created_at"),str(row,"schema_manifest_sha256"));
  var audits=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND created_by=? AND action=? AND result='SUCCESS' AND reason_code IS NULL AND request_id=? AND trace_id=? AND created_at=? AND classification=? AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2",tenant,str(row,"project_id"),str(row,"audit_id"),actor,actor,action,str(row,"operation_id"),str(row,"operation_id"),row.get("created_at"),str(row,"classification"),str(row,"schema_manifest_sha256"));
  var events=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=? AND event_type=? AND occurred_at=? LIMIT 2",tenant,str(row,"event_id"),str(row,"rule_id"),text(receipt,"revision"),action,row.get("created_at"));
  if(operations.size()!=1||audits.size()!=1||events.size()!=1||!FormalTaskNodeAuthority.sameEvidence(receipt,reader.readTree(str(operations.get(0),"response_ref")))||!FormalTaskNodeAuthority.sameEvidence(body,reader.readTree(str(audits.get(0),"resource_ref")))||!FormalTaskNodeAuthority.sameEvidence(body,reader.readTree(str(events.get(0),"payload"))))stale();
 }
 private static String hash(JsonNode value)throws Exception{return com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
 private void requireBudget(JsonNode rule,FormalWorkerAccess.Identity identity){
  var rows=db.queryForList("SELECT b.spent,b.reserved,b.spent_tokens,b.reserved_tokens,b.spent_calls,b.reserved_calls,b.current_budget_revision,b.currency FROM task_run r JOIN budget_revision v ON v.tenant_id=r.tenant_id AND v.budget_revision=r.budget_revision JOIN budget_account b ON b.tenant_id=v.tenant_id AND b.budget_id=v.budget_id WHERE r.tenant_id=? AND r.project_id=? AND r.run_id=? AND r.budget_revision=? FOR UPDATE",
   identity.tenant(),identity.project(),identity.run(),text(rule,"budget_revision"));
  if(rows.size()!=1)deny();var b=rows.get(0);
  if(!text(rule,"budget_revision").equals(str(b,"current_budget_revision"))||!text(rule,"currency").equals(str(b,"currency")))deny();
  if(new java.math.BigDecimal(str(b,"spent")).add(new java.math.BigDecimal(str(b,"reserved"))).compareTo(new java.math.BigDecimal(text(rule,"maximum_amount")))>0)deny();
  for(String unit:List.of("tokens","calls"))if(new java.math.BigInteger(str(b,"spent_"+unit)).add(new java.math.BigInteger(str(b,"reserved_"+unit))).compareTo(new java.math.BigInteger(text(rule,"maximum_"+unit)))>0)deny();
 }
 private static String str(Map<String,Object> r,String field){var v=r.get(field);if(v==null)deny();return v.toString();}
 /** Current 098 governance check, not itself physical-read authority. Caller must verify the original 097 receipt and active lease. */
 public void requireRegisteredInput(EnterpriseAccess.Admission a,String rule,String ruleHash,String decision,String decisionHash,JsonNode input)throws Exception {
  var current=rules.requireRegisteredRule(a,rule,ruleHash,decision,decisionHash,input);
  if(!contains(current.manifest().required("artifact_kinds"),"TASK_NODE_INPUT"))deny();
 }
 public void requireRegisteredInput(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
   AgentExecutionPolicyAuthority.VerifiedPolicy policy,String rule,String ruleHash,String decision,String decisionHash,JsonNode input)throws Exception {
  requireRegisteredInput(a,rule,ruleHash,decision,decisionHash,input);
  var roles=policy.definition().bindings().stream().filter(n->"N02".equals(n.nodeKind())).toList();if(roles.size()!=1)deny();
  rules.requireCurrent(a,publication,policy,roles.get(0).roleVersion(),rule,ruleHash,text(input,"task_id"),decision,decisionHash);
 }
 /** Only a governance check; parent host boundary must require a real lease, mTLS service and actual 097 receipt for each read. */
 public void requireHostInputConsume(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
   AgentExecutionPolicyAuthority.VerifiedPolicy policy,String rule,String ruleHash,String decision,String decisionHash,JsonNode input,String bytes)throws Exception {
  requireRegisteredInput(a,publication,policy,rule,ruleHash,decision,decisionHash,input);
  var current=rules.requireRegisteredRule(a,rule,ruleHash,decision,decisionHash,input);
  requireBound(current.manifest(),"TASK_NODE_INPUT",bytes,"HOST_INPUT_CONSUME");
 }
 /** Governance-only predecessor read check. A private caller must separately verify the actual 095 dependency,
  * genuine producing completion receipt, current source lineage and consuming lease for every physical read. */
 public void requirePredecessorConsume(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
   AgentExecutionPolicyAuthority.VerifiedPolicy policy,String rule,String ruleHash,String decision,String decisionHash,JsonNode consumingInput,String bytes)throws Exception {
  requireRegisteredInput(a,publication,policy,rule,ruleHash,decision,decisionHash,consumingInput);
  var current=rules.requireRegisteredRule(a,rule,ruleHash,decision,decisionHash,consumingInput);
  requireBound(current.manifest(),"EVIDENCE_BUNDLE",bytes,"HOST_PREDECESSOR_CONSUME");
 }
 /** Data-only bound validation, never a proof factory. */
 static void requireBound(JsonNode rule,String kind,String bytes,String operation){
  if(!TaskNodeProducerProtocol.KINDS.contains(kind)||!contains(rule.required("artifact_kinds"),kind)
    ||!contains(rule.required("operations"),operation)||"HOST_INPUT_CONSUME".equals(operation)&&!"TASK_NODE_INPUT".equals(kind)
    ||"HOST_PREDECESSOR_CONSUME".equals(operation)&&!"EVIDENCE_BUNDLE".equals(kind)
    ||"HOST_ENVELOPE_CONSUME".equals(operation)&&!"STEP_ENVELOPE".equals(kind)
    ||"SUPERVISOR_PREPARE_READ".equals(operation)&&!"STEP_ENVELOPE".equals(kind)
    ||"MODEL_INPUT_CONSUME".equals(operation)&&!"TASK_EXACT_INPUT".equals(kind))deny();
  TaskNodeProducerService.requireReadBound(text(rule,"maximum_bytes"),bytes);
 }
 static boolean contains(JsonNode array,String value){for(var item:array)if(value.equals(item.textValue()))return true;return false;}
 private static String text(JsonNode n,String f){return n.required(f).textValue();}
 private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
 private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}

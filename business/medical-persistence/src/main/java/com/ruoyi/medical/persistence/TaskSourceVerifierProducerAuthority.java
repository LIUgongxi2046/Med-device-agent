package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
/** Private N05 producer proof; JSON pins alone never grant execution or a physical write. */
public final class TaskSourceVerifierProducerAuthority {
 private static final Set<String> CONTEXT_READ_KINDS=Set.of("SECTION_CANDIDATE","SECTION_BODY");
 static boolean verifierProducedKind(String kind){return TaskNodeProducerProfile.VERIFIER.kinds().contains(kind)&&!CONTEXT_READ_KINDS.contains(kind);}
 private final TaskNodeProducerSchema schema;private final TaskNodeProducerService rules;private final PermissionSchema json;private final JdbcTemplate db;
 private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public TaskSourceVerifierProducerAuthority(TaskNodeProducerService rules,PermissionSchema json,DataSource data){try{schema=new TaskNodeProducerSchema(TaskNodeProducerProfile.VERIFIER);}catch(java.io.IOException e){throw new IllegalStateException(e);}this.rules=Objects.requireNonNull(rules);this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
 public Prepared require(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,
   AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSourceVerifierInput input,String ruleId,String ruleHash)throws Exception {
  production.requireUnchanged();if(!"N05".equals(input.nodeKind())||!production.runId().equals(input.runId())||!production.taskId().equals(input.taskId())||!production.tenantId().equals(input.tenantId())||!production.projectId().equals(input.projectId()))deny();
  var checked=rules.requireCurrent(production.admission(),publication,policy,input.roleVersion(),ruleId,ruleHash,input.taskId(),null,null);
  var result=new Prepared(production,publication,policy,input,checked);result.requireUnchanged();return result;
 }
 public Prepared requireInput(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSourceVerifierInput input,String ruleId,String ruleHash)throws Exception{return require(production,publication,policy,input,ruleId,ruleHash);}
 public final class Prepared {
  private final TaskNodeProductionAuthority.Verified production;private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final TaskSourceVerifierInput input;private final TaskNodeProducerService.Checked original;
  private Prepared(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication pub,AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSourceVerifierInput i,TaskNodeProducerService.Checked c){production=p;publication=pub;this.policy=policy;input=i;original=c;}
  public void requireUnchanged()throws Exception{production.requireUnchanged();var fresh=rules.requireCurrent(production.admission(),publication,policy,input.roleVersion(),original.capabilityId(),original.capabilityHash(),input.taskId(),original.decisionId(),original.decisionHash());if(!fresh.fingerprint().equals(original.fingerprint()))stale();scope(original,input);rules.requireReplayPins(original.manifest(),production.replay().content());}
  public void requireFor(TaskSourceVerifierInputAuthority.Prepared exact)throws Exception{requireUnchanged();exact.requireUnchanged();if(exact.production()!=production||!exact.input().equals(input))deny();}
  public void requireBytes(String kind,long size)throws Exception{requireUnchanged();if(!"TASK_SOURCE_VERIFIER_INPUT".equals(kind))deny();bound(original,kind,size);capacity(input.tenantId(),original,input.runId(),input.stepId(),kind);}
  public JsonNode canonicalPins()throws Exception{requireUnchanged();return pins(original);}
 }
 public Verified require(TaskSourceVerifierInputAuthority.Verified input)throws Exception {return require(input,input.admission());}
 public Verified require(TaskSourceVerifierInputAuthority.Verified input,EnterpriseAccess.Admission admission)throws Exception {
  input.requireUnchanged();var row=inputRow(input);var stored=reader.readTree(str(row,"producer_pins_json"));
  var checked=rules.requireRegisteredRule(admission,str(row,"producer_rule_id"),str(row,"producer_sha256"),str(row,"producer_decision_id"),str(row,"producer_decision_sha256"),manifest(input));
  if(!pins(checked).equals(stored))stale();var result=new Verified(input,admission,checked);result.requireUnchanged();return result;
 }
 public final class Verified {
  private final TaskSourceVerifierInputAuthority.Verified input;private final EnterpriseAccess.Admission admission;private final TaskNodeProducerService.Checked original;
  private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> tx=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
  private Verified(TaskSourceVerifierInputAuthority.Verified i,EnterpriseAccess.Admission a,TaskNodeProducerService.Checked c){input=i;admission=a;original=c;}
  public void requireUnchanged()throws Exception{
   TaskFieldTargetInputAuthority.requireLifetime(admission,admission,thread,scope,tx);input.requireUnchanged();var row=inputRow(input);
   var fresh=rules.requireRegisteredRule(admission,str(row,"producer_rule_id"),str(row,"producer_sha256"),str(row,"producer_decision_id"),str(row,"producer_decision_sha256"),manifest(input));
   if(!fresh.fingerprint().equals(original.fingerprint())||!pins(original).equals(reader.readTree(str(row,"producer_pins_json"))))stale();scope(original,input.input());
   var a=admission;if(!a.formalWorker()){if(!a.controlWorker())deny();a.controlProof().requireBootstrap(input.taskId(),input.runId());return;}var i=a.formalProof().identity();
   if(!i.run().equals(input.runId())||!i.step().equals(input.stepId())||!i.project().equals(input.projectId())||!i.tenant().equals(input.tenantId()))deny();
   var lease=db.queryForList("SELECT s.step_id FROM run_step s JOIN task_run r ON r.tenant_id=s.tenant_id AND r.project_id=s.project_id AND r.run_id=s.run_id JOIN run_principal p ON p.tenant_id=r.tenant_id AND p.project_id=r.project_id AND p.run_id=r.run_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? AND s.status='RUNNING' AND s.lease_generation=? AND s.lease_owner=? AND s.lease_expires_at>UTC_TIMESTAMP(6) AND s.input_sha256=? AND r.execution_kind='TASK' AND r.task_id=? AND r.created_by=? AND r.status IN ('QUEUED','RUNNING') AND r.ended_at IS NULL AND r.dispatch_closed_at IS NULL AND r.superseded_by_run_id IS NULL AND p.principal_id=? AND p.status='ACTIVE' FOR UPDATE",i.tenant(),i.project(),i.run(),i.step(),i.generation(),i.workerService(),input.inputSha256(),input.taskId(),i.actor(),i.principal());if(lease.size()!=1)deny();
   var action=db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND lease_generation=? AND kind IN ('HOST_CONTROL','MODEL_CALL') AND state IN ('BOUND','RESERVED','DISPATCHING','IN_FLIGHT') FOR UPDATE",i.tenant(),i.project(),i.run(),i.step(),i.action(),input.inputSha256(),i.generation());if(action.size()!=1)deny();requireBudget(original.manifest(),i);
  }
  public void requireFor(TaskSourceVerifierInputAuthority.Verified exact)throws Exception{requireUnchanged();if(exact!=input)deny();}
  public void requireBytes(String kind,long size)throws Exception{requireUnchanged();if(!admission.formalWorker()||!verifierProducedKind(kind)||"TASK_SOURCE_VERIFIER_INPUT".equals(kind))deny();bound(original,kind,size);capacity(input.tenantId(),original,input.runId(),input.stepId(),kind);}
  public JsonNode canonicalPins()throws Exception{requireUnchanged();return pins(original);}
  public void requireHostInputConsume()throws Exception{requireUnchanged();if(!admission.formalWorker())deny();operation("HOST_INPUT_CONSUME","TASK_SOURCE_VERIFIER_INPUT",Long.parseLong(input.objectReference().sizeBytes()));}
  public void requireHostContextConsume(String kind,long size)throws Exception{requireUnchanged();if(!admission.formalWorker()||!CONTEXT_READ_KINDS.contains(kind))deny();operation("HOST_PREDECESSOR_CONSUME",kind,size);}
  public void requireModelInputConsume(int size)throws Exception{requireUnchanged();if(!admission.formalWorker())deny();operation("MODEL_INPUT_CONSUME","TASK_SOURCE_VERIFIER_EXACT_INPUT",size);}
  private void operation(String operation,String kind,long size){if(!TaskNodeProducerAuthority.contains(original.manifest().required("operations"),operation)||!TaskNodeProducerAuthority.contains(original.manifest().required("artifact_kinds"),kind))deny();TaskNodeProducerService.requireReadBound(text(original.manifest(),"maximum_bytes"),Long.toString(size));}
  public void requireInvocationCapacity()throws Exception{requireUnchanged();if(!admission.formalWorker())deny();bound(original,"MODEL_RESULT",1);bound(original,"PROVIDER_CAPTURE",1);var existing=db.queryForList("SELECT invocation_id FROM invocation WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? FOR UPDATE",input.tenantId(),input.runId(),input.stepId(),admission.formalProof().identity().action());if(existing.size()>1)deny();capacity(input.tenantId(),original,input.runId(),input.stepId(),existing.isEmpty()?"INVOCATION_RESERVATION":"EXISTING_INVOCATION");}
  public PermissionProtocol.Basis enterpriseBudgetBasis()throws Exception{requireUnchanged();return json.basis(original.manifest().required("enterprise_budget_basis"));}
 }
 private JsonNode manifest(TaskSourceVerifierInputAuthority.Verified input){var n=(ObjectNode)input.manifest();var rows=db.queryForList("SELECT manifest_sha256 FROM scope_snapshot WHERE tenant_id=? AND project_id=? AND snapshot_id=? FOR UPDATE",input.tenantId(),input.projectId(),input.snapshotId());if(rows.size()!=1)deny();String sha=str(rows.get(0),"manifest_sha256");if(!sha.equals(text(input.production().replay().content(),"scope_snapshot_sha256")))stale();n.put("snapshot_sha256",sha);return n;}
 private Map<String,Object> inputRow(TaskSourceVerifierInputAuthority.Verified input)throws Exception{var rows=db.queryForList("SELECT * FROM task_source_verifier_input WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND step_id=? AND input_id=? AND receipt_sha256=? FOR UPDATE",input.tenantId(),input.projectId(),input.taskId(),input.runId(),input.stepId(),input.inputId(),input.receiptSha256());if(rows.size()!=1)deny();var r=rows.get(0);var receipt=reader.readTree(str(r,"receipt_json"));if(!hash(reader.readTree(str(r,"producer_pins_json"))).equals(text(receipt,"producer_pins_sha256")))stale();return r;}
 private JsonNode pins(TaskNodeProducerService.Checked c){var n=json.object().put("producer_rule_id",c.capabilityId()).put("producer_sha256",c.capabilityHash()).put("producer_decision_id",c.decisionId()).put("producer_decision_sha256",c.decisionHash());n.set("manifest",c.manifest().deepCopy());var a=json.object();a.set("rule",c.authorizations().required(c.authorizations().has("rule")?"rule":"capability").deepCopy());a.set("approval",c.authorizations().required("approval").deepCopy());n.set("authorizations",a);return n;}
 private void scope(TaskNodeProducerService.Checked c,TaskSourceVerifierInput input){var m=c.manifest();if(!"N05".equals(text(m,"producer_node_kind"))||!input.planRevision().equals(text(m,"plan_revision"))||!input.planSha256().equals(text(m,"plan_sha256"))||!input.snapshotId().equals(text(m,"scope_snapshot")))deny();for(String id:input.targetSections())if(!TaskNodeProducerAuthority.contains(m.required("section_ids"),id))deny();}
 private static void bound(TaskNodeProducerService.Checked c,String kind,long size){if(size<=0||!TaskNodeProducerProfile.VERIFIER.kinds().contains(kind)||!TaskNodeProducerAuthority.contains(c.manifest().required("artifact_kinds"),kind)||!TaskNodeProducerAuthority.contains(c.manifest().required("operations"),"NODE_PRODUCE"))deny();TaskNodeProducerService.requireReadBound(text(c.manifest(),"maximum_bytes"),Long.toString(size));}
 private void capacity(String tenant,TaskNodeProducerService.Checked c,String run,String step,String kind){
  // Locks the immutable rule row, serializing all writes sharing this bounded authority.
  db.queryForList("SELECT rule_id FROM task_source_verifier_producer_rule WHERE tenant_id=? AND rule_id=? FOR UPDATE",tenant,c.capabilityId());
  long count=db.queryForObject("SELECT COUNT(*) FROM task_source_verifier_object_intent WHERE tenant_id=? AND JSON_UNQUOTE(JSON_EXTRACT(producer_pins_json,'$.producer_rule_id'))=?",Long.class,tenant,c.capabilityId());
  long exact=db.queryForObject("SELECT COUNT(*) FROM task_source_verifier_exact_intent e JOIN task_source_verifier_input i ON i.tenant_id=e.tenant_id AND i.run_id=e.run_id AND i.step_id=e.step_id WHERE i.tenant_id=? AND i.producer_rule_id=?",Long.class,tenant,c.capabilityId());
  long sessions=db.queryForObject("SELECT COUNT(*) FROM task_source_verifier_runtime_prepare e JOIN task_source_verifier_input i ON i.tenant_id=e.tenant_id AND i.run_id=e.run_id AND i.step_id=e.step_id WHERE i.tenant_id=? AND i.producer_rule_id=?",Long.class,tenant,c.capabilityId());
  long invocations=db.queryForObject("SELECT COUNT(*) FROM invocation e JOIN task_source_verifier_input i ON i.tenant_id=e.tenant_id AND i.run_id=e.run_id AND i.step_id=e.step_id WHERE i.tenant_id=? AND i.producer_rule_id=?",Long.class,tenant,c.capabilityId());
  // Reserve both envelope/export and both factual invocation outputs. Conservative headroom
  // intentionally fails closed at the last slot rather than permitting an unbounded retry.
  long requested="EXISTING_INVOCATION".equals(kind)?0:"INVOCATION_RESERVATION".equals(kind)?2:Set.of("STEP_ENVELOPE","TASK_SOURCE_VERIFIER_EXACT_INPUT").contains(kind)?3:1;
  if(java.math.BigInteger.valueOf(count+exact+2*sessions+2*invocations+requested).compareTo(new java.math.BigInteger(text(c.manifest(),"maximum_artifacts")))>0)deny();
 }
 private JsonNode originalProducer(JsonNode commitment)throws Exception {
  String tenant=text(commitment,"tenant_id"),project=text(commitment,"project_id"),rule=text(commitment,"producer_rule_id");
  var rules=db.queryForList("SELECT r.*,v.content_sha256 registry_sha256 FROM task_source_verifier_producer_rule r JOIN version_registry v ON v.tenant_id=r.tenant_id AND v.version_id=r.rule_id AND v.owner_project_id=r.project_id AND v.entity_kind='TASK_NODE_PRODUCER_RULE' WHERE r.tenant_id=? AND r.project_id=? AND r.rule_id=? LIMIT 2",tenant,project,rule);
  if(rules.size()!=1)deny();var row=rules.get(0);var fixed=reader.readTree(str(row,"fixed_json"));var manifest=schema.validate("InputManifest",fixed.required("manifest"));schema.validate("InputAuthor",fixed.required("request"));if(!schema.manifest().equals(str(row,"schema_manifest_sha256")))stale();
  if(!FormalTaskNodeAuthority.sameEvidence(fixed,commitment.required("authorizations").required("rule"))||!hash(manifest).equals(text(commitment,"producer_sha256"))||!str(row,"content_sha256").equals(text(commitment,"producer_sha256"))||!str(row,"registry_sha256").equals(text(commitment,"producer_sha256"))||!tenant.equals(text(manifest,"tenant_id"))||!project.equals(text(manifest,"project_id"))||!rule.equals(text(manifest,"rule_id")))stale();
  originalProducerJournal(row,fixed,"task.node_producer.write","AUTHOR");
  var decisions=db.queryForList("SELECT * FROM task_source_verifier_producer_decision WHERE tenant_id=? AND project_id=? AND rule_id=? AND decision_id=? AND decision='APPROVE' LIMIT 2",tenant,project,rule,text(commitment,"producer_decision_id"));
  if(decisions.size()!=1)deny();var decision=decisions.get(0);var body=reader.readTree(str(decision,"decision_json"));if(str(decision,"created_by").equals(str(row,"created_by"))||!schema.manifest().equals(str(decision,"schema_manifest_sha256")))deny();
  if(!FormalTaskNodeAuthority.sameEvidence(body,commitment.required("authorizations").required("approval"))||!hash(body).equals(text(commitment,"producer_decision_sha256"))||!str(decision,"content_sha256").equals(text(commitment,"producer_decision_sha256"))||!"APPROVED".equals(text(body,"status")))stale();
  originalProducerJournal(decision,body,"task.node_producer.approve","DECIDE");return manifest;
 }
 private void originalProducerJournal(Map<String,Object> row,JsonNode body,String action,String kind)throws Exception {
  String tenant=str(row,"tenant_id"),actor=str(row,"created_by");var receipt=reader.readTree(str(row,"receipt_json"));
  var operations=db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_id=? AND operation_name=? AND idempotency_key=? AND request_sha256=? AND state='COMPLETED' AND http_status=200 AND target_id=? AND error_code IS NULL AND created_by=? AND created_at=? AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2",tenant,actor,str(row,"operation_id"),"TASK-SECTION-WRITER-PRODUCER-"+kind,str(row,"idempotency_key"),str(row,"request_sha256"),str(row,"rule_id"),actor,row.get("created_at"),str(row,"schema_manifest_sha256"));
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

 /** Historical envelope witness only. This returns no production capability and does
  * not reauthorize a disabled rule, but rechecks its original author/approval journals. */
 void requireOriginalEnvelope(JsonNode envelope,java.time.LocalDateTime preparedAt)throws Exception {
  if(!"N05".equals(text(envelope,"node_kind"))||!"TASK".equals(text(envelope,"execution_kind")))deny();
  var pins=envelope.required("producer");var commitment=(ObjectNode)pins.deepCopy();commitment.put("tenant_id",text(envelope,"tenant_id")).put("project_id",text(envelope,"project_id"));
  var manifest=originalProducer(commitment);if(!manifest.equals(pins.required("manifest"))||!"N05".equals(text(manifest,"producer_node_kind"))||!TaskNodeProducerAuthority.contains(manifest.required("artifact_kinds"),"STEP_ENVELOPE")||!TaskNodeProducerAuthority.contains(manifest.required("operations"),"NODE_PRODUCE"))deny();
  if(!text(envelope,"plan_revision").equals(text(manifest,"plan_revision"))||!text(envelope,"snapshot_id").equals(text(manifest,"scope_snapshot")))deny();
  TaskNodeProducerService.requireReadBound(text(manifest,"maximum_bytes"),Integer.toString(ContentReadAuthorization.canonicalBytes(envelope).length));
  var at=preparedAt.toInstant(java.time.ZoneOffset.UTC);if(at.isBefore(java.time.Instant.parse(text(manifest,"valid_from")))||!at.isBefore(java.time.Instant.parse(text(manifest,"valid_until"))))deny();
 }
 public void requireOriginalOutput(FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,JsonNode expectedPins,String kind,int size)throws Exception {
  outcome.requireActive();if(!Set.of("MODEL_RESULT","PROVIDER_CAPTURE").contains(kind)||size<=0)deny();
  var rows=db.queryForList("SELECT b.authorization_snapshot,b.authorization_sha256,b.canonical_request,b.request_sha256,b.created_by,i.run_id,i.step_id,i.action_id,i.input_sha256,i.lease_generation,i.context_manifest_id,i.verification_id FROM formal_invocation_binding b JOIN invocation i ON i.tenant_id=b.tenant_id AND i.invocation_id=b.invocation_id WHERE b.tenant_id=? AND b.invocation_id=? FOR UPDATE",outcome.tenantId(),outcome.invocationId());if(rows.size()!=1)deny();var row=rows.get(0);
  var snapshot=reader.readTree(str(row,"authorization_snapshot"));if(!hash(snapshot).equals(str(row,"authorization_sha256"))||!hash(reader.readTree(str(row,"canonical_request"))).equals(str(row,"request_sha256")))stale();
  var c=snapshot.required("source_verifier_output_commitment");if(!"N05".equals(text(c,"node_kind"))||!outcome.invocationId().equals(text(c,"invocation_id"))||!expectedPins.equals(c.required("producer"))||!outcome.actorId().equals(str(row,"created_by")))deny();
  var original=(ObjectNode)expectedPins.deepCopy();original.put("tenant_id",outcome.tenantId()).put("project_id",outcome.projectId());var m=originalProducer(original);
  if(!m.equals(expectedPins.required("manifest"))||!"N05".equals(text(m,"producer_node_kind"))||!TaskNodeProducerAuthority.contains(m.required("artifact_kinds"),kind)||!TaskNodeProducerAuthority.contains(m.required("operations"),"NODE_PRODUCE"))deny();TaskNodeProducerService.requireReadBound(text(m,"maximum_bytes"),Integer.toString(size));
  for(String key:List.of("run_id","step_id","action_id","input_sha256","lease_generation"))if(!str(row,key).equals(text(outcome.binding(),key)))deny();
  if(!str(row,"context_manifest_id").equals(text(c,"context_manifest_id"))||!str(row,"input_sha256").equals(text(c,"input_sha256")))deny();
  // The authenticated outcome already proves the actual076 dispatch and cutoff. Preserve
  // original reservation evidence, never reauthorize a disabled producer or expired lease.
  var audits=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND subject_id=? AND action='model.invocation_reserved' AND request_id=? AND trace_id=? AND result='SUCCESS' LIMIT 2",outcome.tenantId(),outcome.actorId(),outcome.invocationId(),str(row,"verification_id"));
  var events=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='model.invocation_reserved' LIMIT 2",outcome.tenantId(),outcome.invocationId());if(audits.size()!=1||events.size()!=1)deny();var audit=reader.readTree(str(audits.get(0),"resource_ref"));if(!audit.equals(reader.readTree(str(events.get(0),"payload")))||!str(row,"authorization_snapshot").equals(text(audit.required("binding"),"authorization_snapshot"))||!str(row,"canonical_request").equals(text(audit.required("binding"),"canonical_request")))stale();
  if("MODEL_RESULT".equals(kind)&&size!=outcome.bytes().length)deny();var admitted=db.query("SELECT admitted_at FROM formal_invocation_attempt WHERE tenant_id=? AND invocation_id=? AND attempt_id=? AND dispatch_generation=1 LIMIT 2 FOR UPDATE",(r,n)->r.getTimestamp(1).toInstant(),outcome.tenantId(),outcome.invocationId(),outcome.attemptId());
  if(admitted.size()!=1||admitted.get(0).isBefore(java.time.Instant.parse(text(m,"valid_from")))||!admitted.get(0).isBefore(java.time.Instant.parse(text(m,"valid_until"))))deny();outcome.requireActive();
 }
 private static String text(JsonNode n,String k){var v=n.required(k);if(!v.isTextual())deny();return v.textValue();}
 private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}

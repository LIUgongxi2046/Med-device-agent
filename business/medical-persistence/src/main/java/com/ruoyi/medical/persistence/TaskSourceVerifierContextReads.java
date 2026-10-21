package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Closed N05 physical context reader. Registry metadata, producer approvals and source
 * references are never substituted for object GET/readback or verified literal chunks. */
public final class TaskSourceVerifierContextReads {
 private record Contract(String manifest,com.networknt.schema.JsonSchema schema){}
 private static final Contract CONTRACT=contract();
 private static Contract contract(){try(var in=Objects.requireNonNull(TaskSourceVerifierContextReads.class.getResourceAsStream("/task-source-verifier-context-read.schema.json"))){byte[] bytes=in.readAllBytes();var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);return new Contract(WireValues.sha256(bytes),com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(node,com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()));}catch(Exception e){throw new IllegalStateException(e);}}
 private void validatePins(JsonNode pins)throws Exception{if(!CONTRACT.schema.validate(pins).isEmpty())bad();var evidence=new TaskSourceReadReceiptSchema(json);for(var source:pins.required("sources")){evidence.evidence(source.required("evidence_ref"));if(source.required("located").booleanValue()!=source.required("complete").booleanValue())bad();}}
 private static final int OBJECT_LIMIT=16*1024*1024,CONTEXT_LIMIT=TaskSourceVerifierInvocationSchema.MAXIMUM_BYTES;
 private final org.springframework.jdbc.core.JdbcTemplate db;
 private final SourceUploadTransport transport;private final SourceReadAuthority sources;private final TaskFieldSourceAuthority processing;
 private final TaskResearchSourceReader literals;private final PermissionSchema json;private final TaskSourceVerifierProducerAuthority producers;
 public TaskSourceVerifierContextReads(javax.sql.DataSource data,SourceUploadTransport transport,SourceReadAuthority sources,TaskFieldSourceAuthority processing,TaskResearchSourceReader literals,PermissionSchema json,TaskSourceVerifierProducerAuthority producers){this.db=new org.springframework.jdbc.core.JdbcTemplate(data);this.db.setQueryTimeout(5);this.transport=transport;this.sources=sources;this.processing=processing;this.literals=literals;this.json=json;this.producers=producers;}
 private record Source(DatasetProtocol.EvidenceRef evidence,TaskFieldSourceAuthority.Processing physical){}
 private record Snapshot(String tenant,String actor,String input,String inputHash,String receipt,String session,JsonNode rules,JsonNode ruleWitness,Map<String,InterfaceProtocol.ObjectRef> objects,Map<String,JsonNode> payloads,List<Source> sources,JsonNode documentScenario){}
 public Reads read(TaskSourceVerifierHostAccess hosts,TaskSourceVerifierHostAccess.Handle handle)throws Exception {
  if(TransactionSynchronizationManager.isActualTransactionActive())deny();var original=hosts.authorized(handle,this::snapshot);
  S3ObjectReader.Admission guard=()->{try{hosts.authorized(handle,current->{if(!original.equals(snapshot(current)))stale();return null;});}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}};
  var content=json.object().put("session_id",original.session);content.set("rules",original.rules.deepCopy());if(original.documentScenario!=null)content.set("document_scenario",original.documentScenario.deepCopy());var ruleReceipts=content.putArray("rule_read_receipts");for(var rule:original.rules.required("rules")){String id=text(rule,"rule_id");ruleReceipts.addObject().put("rule_id",id).put("receipt_id",receiptId(original.session,"RULE",id));}var artifacts=content.putArray("artifacts");long total=0;
  for(var entry:new TreeMap<>(original.objects).entrySet()){
   total+=Long.parseLong(entry.getValue().sizeBytes());if(total>CONTEXT_LIMIT)limit();
   byte[] bytes=consume(original,entry.getValue(),true,guard);var expected=original.payloads.get(entry.getKey());
   if(expected==null||!Arrays.equals(bytes,ContentReadAuthorization.canonicalBytes(expected)))bad();
   artifacts.addObject().put("artifact_id",entry.getKey()).put("sha256",entry.getValue().sha256()).set("payload",expected.deepCopy());
  }
  var evidence=content.putArray("source_chunks");var physicalCache=new HashMap<String,byte[]>();var consumedOriginals=new HashSet<String>();long sourceBytes=0;
  for(var source:original.sources){
   var physical=source.physical;String originalKey=WireValues.sha256(ContentReadAuthorization.canonicalBytes(physical.original()));
   if(consumedOriginals.add(originalKey)){sourceBytes+=Long.parseLong(physical.original().sizeBytes());if(sourceBytes>64L*1024*1024)limit();consume(original,physical.original(),false,guard);}
   String parsedKey=WireValues.sha256(ContentReadAuthorization.canonicalBytes(physical.parsed()));byte[] parsed=physicalCache.get(parsedKey);
   if(parsed==null){sourceBytes+=Long.parseLong(physical.parsed().sizeBytes());if(sourceBytes>64L*1024*1024)limit();parsed=consume(original,physical.parsed(),true,guard);physicalCache.put(parsedKey,parsed);}
   var located=literals.reviewLiteral(physical,source.evidence,parsed);String literal=located.orElse("");total+=literal.getBytes(StandardCharsets.UTF_8).length;if(total>CONTEXT_LIMIT)limit();
   var delivered=evidence.addObject().put("raw_text",literal).put("located",located.isPresent()).put("read_status",located.isPresent()?"COMPLETE":"LOCATOR_MISMATCH").put("receipt_id",receiptId(original.session,"SOURCE",WireValues.sha256(ContentReadAuthorization.canonicalBytes(source.evidence))));delivered.set("evidence_ref",json.tree(source.evidence));
  }
  if(ContentReadAuthorization.canonicalBytes(content).length>CONTEXT_LIMIT)limit();guard.requireCurrent();var result=new Reads(original,content,pins(original,content));hosts.authorized(handle,a->{if(!original.equals(snapshot(a)))stale();register(a,result.pins);result.requireFor(a);return null;});return result;
 }
 private Snapshot snapshot(TaskSourceVerifierRuntimeAuthority.Verified runtime)throws Exception {
  runtime.requireUnchanged();var input=runtime.input();var a=runtime.admission();if(!a.formalWorker())deny();
  var objects=input.readObjects();var payloads=input.expectedReadPayloads();if(objects.isEmpty()||objects.size()>514||!objects.keySet().equals(payloads.keySet()))bad();
  var producer=producers.require(input,a);for(var entry:objects.entrySet()){
   String kind=entry.getKey().equals(input.input().candidateArtifactId())?"SECTION_CANDIDATE":"SECTION_BODY";
   producer.requireHostContextConsume(kind,Long.parseLong(entry.getValue().sizeBytes()));
   if(!entry.getValue().sha256().equals(WireValues.sha256(ContentReadAuthorization.canonicalBytes(payloads.get(entry.getKey())))))bad();
  }
  var ordered=new TreeMap<String,DatasetProtocol.EvidenceRef>();for(var ref:input.evidenceRefs()){String key=WireValues.sha256(ContentReadAuthorization.canonicalBytes(ref));var prior=ordered.putIfAbsent(key,ref);if(prior!=null&&!prior.equals(ref))bad();}var sourceList=new ArrayList<Source>();for(var ref:ordered.values())sourceList.add(source(runtime,ref,SourceReadProtocol.Use.FORMAL,null));if(sourceList.size()>512)limit();
  input.requireUnchanged();return new Snapshot(input.tenantId(),runtime.job().actor(),input.inputId(),input.inputSha256(),input.receiptSha256(),TaskSourceVerifierRuntimeAdapter.sessionId(runtime),input.rules().manifest(input.input().ruleRevision()),input.rules().witness(),Map.copyOf(objects),Map.copyOf(payloads),List.copyOf(sourceList),input.documentScenario());
 }
 private Source source(TaskSourceVerifierRuntimeAuthority.Verified runtime,DatasetProtocol.EvidenceRef ref,SourceReadProtocol.Use use,String provider)throws Exception {
  var input=runtime.input();input.requireUnchanged();if(!input.evidenceRefs().contains(ref))deny();var fixed=input.fixedSources().stream().filter(p->p.sourceVersion().equals(ref.sourceVersion())).toList();if(fixed.size()!=1)deny();var selected=fixed.get(0);
  var policy=input.production().replay().content().required("execution_policy");var granted=sources.require(runtime.admission(),new SourceReadProtocol.ReadRequirement(selected.sourceVersion(),selected.rightsRevision(),input.projectId(),input.snapshotId(),selected.parseVersion(),null,use,selected.purpose(),text(policy,"environment"),text(policy,"region"),provider));
  TaskInvocationPackages.requireModelSelection(TaskInvocationSources.of(input),granted,selected.purpose());
  if(!input.tenantId().equals(ref.tenantId())||!input.projectId().equals(ref.projectId())||!selected.parseVersion().equals(ref.parseVersion())||!selected.approvalRevision().equals(ref.approvalRevision())||!granted.source().sourceId().equals(ref.sourceId()))deny();
  var physical=processing.processing(input.tenantId(),ref.sourceVersion(),ref.parseVersion(),granted.source().originalSha256());
  if(granted.source().ownerProjectId()!=null&&!granted.source().ownerProjectId().equals(physical.projectId()))deny();return new Source(ref,physical);
 }
 private byte[] consume(Snapshot snapshot,InterfaceProtocol.ObjectRef reference,boolean keep,S3ObjectReader.Admission guard)throws Exception {
  long length=Long.parseLong(reference.sizeBytes());if(length<1||length>OBJECT_LIMIT)limit();
  return transport.readTaskDocument(snapshot.tenant,snapshot.actor,OBJECT_LIMIT,guard,(reader,current)->{try(var object=reader.readVerified(reference,current)){
   if(!reference.equals(object.reference()))bad();var digest=java.security.MessageDigest.getInstance("SHA-256");byte[] bytes=keep?new byte[(int)length]:null;
   for(int offset=0;offset<length;){current.requireCurrent();int count=(int)Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,length-offset);byte[] piece=object.chunk(offset,count);if(piece.length!=count)bad();digest.update(piece);if(keep)System.arraycopy(piece,0,bytes,offset,count);offset+=count;current.requireCurrent();}
   String sha=java.util.HexFormat.of().formatHex(digest.digest());if(!reference.sha256().equals(sha)||!sha.equals(object.fullSha256()))bad();return bytes;
  }});
 }
 private JsonNode pins(Snapshot s,JsonNode content)throws Exception {
  var n=json.object().put("schema_version","1.0.0").put("kind","N05_PHYSICAL_CONTEXT").put("session_id",s.session).put("input_id",s.input).put("input_sha256",s.inputHash).put("input_receipt_sha256",s.receipt).put("content_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(content)));
  n.put("rules_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(s.rules))).put("rule_authorizations_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(s.ruleWitness)));
  var ruleIds=n.putArray("rule_ids");var ruleReads=n.putArray("rule_reads");for(var rule:s.rules.required("rules")){String id=text(rule,"rule_id");ruleIds.add(id);ruleReads.addObject().put("rule_id",id).put("sha256",text(rule,"sha256")).put("receipt_id",receiptId(s.session,"RULE",id)).put("complete",true);}
  var objects=n.putArray("objects");for(var entry:new TreeMap<>(s.objects).entrySet()){var v=objects.addObject().put("artifact_id",entry.getKey());v.set("object_ref",json.tree(entry.getValue()));}
  var src=n.putArray("sources");int ordinal=0;for(var source:s.sources){var delivered=content.required("source_chunks").get(ordinal++);boolean located=delivered.required("located").booleanValue();var v=src.addObject().put("receipt_id",receiptId(s.session,"SOURCE",WireValues.sha256(ContentReadAuthorization.canonicalBytes(source.evidence)))).put("access",true).put("located",located).put("complete",located).put("literal_sha256",WireValues.sha256(text(delivered,"raw_text").getBytes(StandardCharsets.UTF_8)));v.set("evidence_ref",json.tree(source.evidence));v.put("processing_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(source.physical)));v.set("original_object",json.tree(source.physical.original()));v.set("parsed_object",json.tree(source.physical.parsed()));}return n;
 }
 public final class Reads {
  private final Snapshot original;private final JsonNode content,pins;
  private Reads(Snapshot original,JsonNode content,JsonNode pins){this.original=original;this.content=content.deepCopy();this.pins=pins.deepCopy();}
  public void requireFor(TaskSourceVerifierRuntimeAuthority.Verified runtime)throws Exception {if(!original.equals(snapshot(runtime))||!pins.equals(pins(original,content)))stale();requireRegistered(runtime,pins);}
  public JsonNode content(){return content.deepCopy();}public JsonNode canonicalPins(){return pins.deepCopy();}
 }
 /** Factual immutable content pins are rechecked under current read or actual model.send
  * admission; a caller-supplied pin cannot create a physical Reads capability. */
 JsonNode requireCurrentPins(TaskSourceVerifierRuntimeAuthority.Verified runtime,JsonNode pins,boolean send,String provider)throws Exception {
  runtime.requireUnchanged();requireRegistered(runtime,pins);var input=runtime.input();if(!TaskSourceVerifierRuntimeAdapter.sessionId(runtime).equals(text(pins,"session_id"))||!"N05_PHYSICAL_CONTEXT".equals(text(pins,"kind"))||!input.inputId().equals(text(pins,"input_id"))||!input.inputSha256().equals(text(pins,"input_sha256"))||!input.receiptSha256().equals(text(pins,"input_receipt_sha256")))bad();
  input.rules().requireUnchanged();if(!WireValues.sha256(ContentReadAuthorization.canonicalBytes(input.rules().manifest(input.input().ruleRevision()))).equals(text(pins,"rules_sha256"))||!WireValues.sha256(ContentReadAuthorization.canonicalBytes(input.rules().witness())).equals(text(pins,"rule_authorizations_sha256")))stale();
  if(!json.tree(input.rules().bases().stream().map(PermissionProtocol.Basis::version_id).toList()).equals(pins.required("rule_ids")))stale();
  var actualObjects=json.object().arrayNode();for(var entry:new TreeMap<>(input.readObjects()).entrySet()){var row=actualObjects.addObject().put("artifact_id",entry.getKey());row.set("object_ref",json.tree(entry.getValue()));}if(!actualObjects.equals(pins.required("objects")))stale();
  var expected=new LinkedHashSet<>(input.evidenceRefs());if(pins.required("sources").size()!=expected.size())bad();var seen=new HashSet<DatasetProtocol.EvidenceRef>();var schema=new TaskSourceReadReceiptSchema(json);
  for(var row:pins.required("sources")){var ref=schema.evidence(row.required("evidence_ref"));if(!expected.contains(ref)||!seen.add(ref))bad();var current=source(runtime,ref,send?SourceReadProtocol.Use.MODEL_SEND:SourceReadProtocol.Use.FORMAL,provider);
   if(!WireValues.sha256(ContentReadAuthorization.canonicalBytes(current.physical)).equals(text(row,"processing_sha256"))||!json.tree(current.physical.original()).equals(row.required("original_object"))||!json.tree(current.physical.parsed()).equals(row.required("parsed_object")))stale();}
  runtime.requireUnchanged();return pins.deepCopy();
 }
 private static String receiptId(String session,String kind,String identity){return UUID.nameUUIDFromBytes(("N05_INDEPENDENT_READ|"+session+"|"+kind+"|"+identity).getBytes(StandardCharsets.UTF_8)).toString();}
 /** Only the private physical Reads path calls this after every original and parsed object
  * was consumed and hash checked. Deterministic IDs are locators, never read authority. */
 private void register(TaskSourceVerifierRuntimeAuthority.Verified runtime,JsonNode pins)throws Exception {
  runtime.requireUnchanged();validatePins(pins);var j=runtime.job();String session=text(pins,"session_id"),hash=WireValues.sha256(ContentReadAuthorization.canonicalBytes(pins));
  var rows=db.queryForList("SELECT receipt_sha256 FROM task_source_verifier_context_read WHERE tenant_id=? AND session_id=? LIMIT 2 FOR UPDATE",j.tenant(),session);
  if(rows.isEmpty()){
   String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class);String canonical=json.canonical(pins);
   changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.source_verifier.context.read',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",j.tenant(),j.project(),audit,j.actor(),canonical,session,j.run(),CONTRACT.manifest,j.actor(),now,j.classification()));
   changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,'task.source_verifier.context.read',?,?)",j.tenant(),event,session,canonical,now));
   changed(db.update("INSERT INTO task_source_verifier_context_read(tenant_id,project_id,run_id,step_id,session_id,input_sha256,input_receipt_sha256,canonical_receipt,receipt_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",j.tenant(),j.project(),j.run(),j.step(),session,j.inputHash(),runtime.input().receiptSha256(),canonical,hash,audit,event,CONTRACT.manifest,j.actor(),now,j.classification()));
   for(String kind:List.of("sources","rule_reads"))for(var read:pins.required(kind))changed(db.update("INSERT INTO task_source_verifier_independent_read(tenant_id,project_id,session_id,receipt_id,read_kind,canonical_receipt,receipt_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",j.tenant(),j.project(),session,text(read,"receipt_id"),kind.equals("sources")?"SOURCE":"RULE",json.canonical(read),WireValues.sha256(ContentReadAuthorization.canonicalBytes(read)),CONTRACT.manifest,j.actor(),now,j.classification()));
  }
  requireRegistered(runtime,pins);
 }
 private record ReadIdentity(String tenant,String project,String run,String step,String actor,String inputHash,String inputReceipt,String inputId,String session,String classification){}
 private void requireRegistered(TaskSourceVerifierRuntimeAuthority.Verified runtime,JsonNode pins)throws Exception {
  runtime.requireUnchanged();var job=runtime.job();String session=TaskSourceVerifierRuntimeAdapter.sessionId(runtime);if(runtime.input().target().writerSessionIds().contains(session))deny();
  requireStoredFacts(new ReadIdentity(job.tenant(),job.project(),job.run(),job.step(),job.actor(),job.inputHash(),runtime.input().receiptSha256(),runtime.input().inputId(),session,job.classification()),pins);
 }
 /** Original-only factual verification. It checks the durable, immutable read receipts;
  * it does not recreate a live read capability, current source grant or model permission. */
 void requireOriginalPins(TaskSourceVerifierDispatchJournal.Original original,JsonNode pins)throws Exception {
  original.requireUnchanged();var envelope=original.envelope();if(!envelope.required("prompt_provenance").required("review_content").equals(pins))bad();
  requireStoredFacts(originalIdentity(original.reference(),original.dispatch(),original.admission(),envelope),pins);original.requireUnchanged();
 }
 void requireOriginalPins(TaskSourceVerifierDispatchJournal.StageFacts original,JsonNode pins)throws Exception {
  original.requireUnchanged();var envelope=original.envelope();if(!envelope.required("prompt_provenance").required("review_content").equals(pins))bad();
  requireStoredFacts(originalIdentity(original.reference(),original.dispatch(),original.admission(),envelope),pins);original.requireUnchanged();
 }
 private ReadIdentity originalIdentity(com.ruoyi.medical.workflow.WorkflowProtocol.Reference reference,com.ruoyi.medical.workflow.WorkflowProtocol.Dispatch dispatch,EnterpriseAccess.Admission admission,JsonNode envelope){
  var rows=db.query("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",(r,n)->r.getString(1),reference.tenantId(),reference.projectId(),reference.runId(),admission.actorId());if(rows.size()!=1)bad();
  return new ReadIdentity(reference.tenantId(),reference.projectId(),reference.runId(),dispatch.stepId(),admission.actorId(),dispatch.inputSha256(),text(envelope,"input_receipt_sha256"),text(envelope,"input_artifact_id"),text(envelope,"session_id"),rows.get(0));
 }
 private void requireStoredFacts(ReadIdentity j,JsonNode pins)throws Exception {
  validatePins(pins);String session=j.session();if(!session.equals(text(pins,"session_id"))||!j.inputHash().equals(text(pins,"input_sha256"))||!j.inputReceipt().equals(text(pins,"input_receipt_sha256"))||!j.inputId().equals(text(pins,"input_id")))bad();
  var rows=db.queryForList("SELECT * FROM task_source_verifier_context_read WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND session_id=? AND input_sha256=? AND input_receipt_sha256=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),session,j.inputHash(),j.inputReceipt(),j.actor());if(rows.size()!=1)bad();var row=rows.get(0);String canonical=json.canonical(pins);
  if(!"1.0.0".equals(row.get("schema_version"))||!CONTRACT.manifest.equals(row.get("schema_manifest_sha256"))||!j.classification().equals(row.get("classification")))bad();
  if(!canonical.equals(row.get("canonical_receipt"))||!WireValues.sha256(ContentReadAuthorization.canonicalBytes(pins)).equals(row.get("receipt_sha256")))bad();
  var audits=db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND created_by=? AND action='task.source_verifier.context.read' AND result='SUCCESS' AND reason_code IS NULL AND request_id=? AND trace_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? AND created_at=? LIMIT 2",(r,n)->r.getString(1),j.tenant(),j.project(),row.get("audit_id"),j.actor(),j.actor(),session,j.run(),CONTRACT.manifest,j.classification(),row.get("created_at"));
  var events=db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='task.source_verifier.context.read' AND occurred_at=? LIMIT 2",(r,n)->r.getString(1),j.tenant(),row.get("event_id"),session,row.get("created_at"));if(!audits.equals(List.of(canonical))||!events.equals(List.of(canonical)))bad();
  int count=0;for(String kind:List.of("sources","rule_reads"))for(var read:pins.required(kind)){count++;var stored=db.queryForList("SELECT canonical_receipt,receipt_sha256 FROM task_source_verifier_independent_read WHERE tenant_id=? AND project_id=? AND session_id=? AND receipt_id=? AND read_kind=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND created_by=? AND created_at=? AND classification=? LIMIT 2",j.tenant(),j.project(),session,text(read,"receipt_id"),kind.equals("sources")?"SOURCE":"RULE",CONTRACT.manifest,j.actor(),row.get("created_at"),j.classification());if(stored.size()!=1||!json.canonical(read).equals(stored.get(0).get("canonical_receipt"))||!WireValues.sha256(ContentReadAuthorization.canonicalBytes(read)).equals(stored.get(0).get("receipt_sha256")))bad();}
  if(!Integer.valueOf(count).equals(db.queryForObject("SELECT COUNT(*) FROM task_source_verifier_independent_read WHERE tenant_id=? AND session_id=?",Integer.class,j.tenant(),session)))bad();
 }
 private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
 private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void limit(){throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
}

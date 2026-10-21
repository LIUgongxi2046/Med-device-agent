package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Closed N04 physical context reader. Registry metadata, producer approvals and source
 * references are never substituted for object GET/readback or verified literal chunks. */
public final class TaskSectionWriterContextReads {
 private static final int OBJECT_LIMIT=16*1024*1024,CONTEXT_LIMIT=TaskSectionWriterInvocationSchema.MAXIMUM_BYTES;
 private final SourceUploadTransport transport;private final SourceReadAuthority sources;private final TaskFieldSourceAuthority processing;
 private final TaskResearchSourceReader literals;private final PermissionSchema json;private final TaskSectionWriterProducerAuthority producers;
 public TaskSectionWriterContextReads(SourceUploadTransport transport,SourceReadAuthority sources,TaskFieldSourceAuthority processing,TaskResearchSourceReader literals,PermissionSchema json,TaskSectionWriterProducerAuthority producers){this.transport=transport;this.sources=sources;this.processing=processing;this.literals=literals;this.json=json;this.producers=producers;}
 private record Source(DatasetProtocol.EvidenceRef evidence,TaskFieldSourceAuthority.Processing physical){}
 private record Snapshot(String tenant,String actor,String input,String inputHash,String receipt,Map<String,InterfaceProtocol.ObjectRef> objects,Map<String,JsonNode> payloads,List<Source> sources,JsonNode documentScenario){}
 public Reads read(TaskSectionWriterHostAccess hosts,TaskSectionWriterHostAccess.Handle handle)throws Exception {
  if(TransactionSynchronizationManager.isActualTransactionActive())deny();var original=hosts.authorized(handle,this::snapshot);
  S3ObjectReader.Admission guard=()->{try{hosts.authorized(handle,current->{if(!original.equals(snapshot(current)))stale();return null;});}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}};
  var content=json.object();if(original.documentScenario!=null){content.set("document_scenario",original.documentScenario.deepCopy());if(RegistrationLifecycleSchema.supportsKind(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("registration_lifecycle_plan",RegistrationLifecyclePipeline.plan(original.documentScenario));if(InstructionsLabelSchema.KIND.equals(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("instructions_label_plan",InstructionsLabelPipeline.plan(original.documentScenario));if(MultimodalQaSchema.KIND.equals(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("multimodal_qa_plan",MultimodalQaPipeline.plan(original.documentScenario));if(NonclinicalPrinciplesRiskSchema.KIND.equals(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("nonclinical_principles_risk_plan",NonclinicalPrinciplesRiskPipeline.plan(original.documentScenario));if(ClinicalEvaluationSchema.KIND.equals(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("clinical_evaluation_plan",ClinicalEvaluationPipeline.plan(original.documentScenario));if(RegistrationDossierSchema.KIND.equals(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("registration_dossier_plan",RegistrationDossierPipeline.plan(original.documentScenario));if(ProductTechnicalRequirementSchema.KIND.equals(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("product_technical_requirement_plan",ProductTechnicalRequirementPipeline.plan(original.documentScenario));if(ReportScenarioSchema.KIND.equals(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("report_calculations",ReportScenarioPipeline.calculations(original.documentScenario));if(MarketAnalysisScenarioSchema.KIND.equals(original.documentScenario.required("selection").required("scenario_kind").asText()))content.set("market_calculations",MarketAnalysisScenarioPipeline.calculations(original.documentScenario));}var artifacts=content.putArray("artifacts");long total=0;
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
   String literal=literals.literal(physical,source.evidence,parsed);total+=literal.getBytes(StandardCharsets.UTF_8).length;if(total>CONTEXT_LIMIT)limit();
   var delivered=evidence.addObject().put("raw_text",literal);delivered.set("evidence_ref",json.tree(source.evidence));
  }
  if(ContentReadAuthorization.canonicalBytes(content).length>CONTEXT_LIMIT)limit();guard.requireCurrent();var result=new Reads(original,content,pins(original,content));hosts.authorized(handle,a->{result.requireFor(a);return null;});return result;
 }
 private Snapshot snapshot(TaskSectionWriterRuntimeAuthority.Verified runtime)throws Exception {
  runtime.requireUnchanged();var input=runtime.input();var a=runtime.admission();if(!a.formalWorker())deny();
  var objects=input.readObjects();var payloads=input.expectedReadPayloads();if(objects.isEmpty()||objects.size()>514||!objects.keySet().equals(payloads.keySet()))bad();
  var producer=producers.require(input,a);for(var entry:objects.entrySet()){
   String kind=entry.getKey().equals(input.input().fieldCheckArtifactId())?"FIELD_CHECK_RESULT":input.input().predecessors().stream().anyMatch(p->p.evidenceArtifactId().equals(entry.getKey()))?"EVIDENCE_BUNDLE":"DOCUMENT_BODY";
   producer.requireHostContextConsume(kind,Long.parseLong(entry.getValue().sizeBytes()));
   if(!entry.getValue().sha256().equals(WireValues.sha256(ContentReadAuthorization.canonicalBytes(payloads.get(entry.getKey())))))bad();
  }
  var sourceList=new ArrayList<Source>();var seen=new HashSet<DatasetProtocol.EvidenceRef>();for(var ref:input.evidenceRefs())if(seen.add(ref))sourceList.add(source(runtime,ref,SourceReadProtocol.Use.FORMAL,null));if(sourceList.size()>512)limit();
  input.requireUnchanged();return new Snapshot(input.tenantId(),runtime.job().actor(),input.inputId(),input.inputSha256(),input.receiptSha256(),Map.copyOf(objects),Map.copyOf(payloads),List.copyOf(sourceList),input.documentScenario());
 }
 private Source source(TaskSectionWriterRuntimeAuthority.Verified runtime,DatasetProtocol.EvidenceRef ref,SourceReadProtocol.Use use,String provider)throws Exception {
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
  var n=json.object().put("schema_version","1.0.0").put("kind","N04_PHYSICAL_CONTEXT").put("input_id",s.input).put("input_sha256",s.inputHash).put("input_receipt_sha256",s.receipt).put("content_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(content)));
  var objects=n.putArray("objects");for(var entry:new TreeMap<>(s.objects).entrySet()){var v=objects.addObject().put("artifact_id",entry.getKey());v.set("object_ref",json.tree(entry.getValue()));}
  var src=n.putArray("sources");for(var source:s.sources){var v=src.addObject();v.set("evidence_ref",json.tree(source.evidence));v.put("processing_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(source.physical)));v.set("original_object",json.tree(source.physical.original()));v.set("parsed_object",json.tree(source.physical.parsed()));}return n;
 }
 public final class Reads {
  private final Snapshot original;private final JsonNode content,pins;
  private Reads(Snapshot original,JsonNode content,JsonNode pins){this.original=original;this.content=content.deepCopy();this.pins=pins.deepCopy();}
  public void requireFor(TaskSectionWriterRuntimeAuthority.Verified runtime)throws Exception {if(!original.equals(snapshot(runtime))||!pins.equals(pins(original,content)))stale();}
  public JsonNode content(){return content.deepCopy();}public JsonNode canonicalPins(){return pins.deepCopy();}
 }
 /** Factual immutable content pins are rechecked under current read or actual model.send
  * admission; a caller-supplied pin cannot create a physical Reads capability. */
 JsonNode requireCurrentPins(TaskSectionWriterRuntimeAuthority.Verified runtime,JsonNode pins,boolean send,String provider)throws Exception {
  runtime.requireUnchanged();var input=runtime.input();if(!"N04_PHYSICAL_CONTEXT".equals(text(pins,"kind"))||!input.inputId().equals(text(pins,"input_id"))||!input.inputSha256().equals(text(pins,"input_sha256"))||!input.receiptSha256().equals(text(pins,"input_receipt_sha256")))bad();
  var actualObjects=json.object().arrayNode();for(var entry:new TreeMap<>(input.readObjects()).entrySet()){var row=actualObjects.addObject().put("artifact_id",entry.getKey());row.set("object_ref",json.tree(entry.getValue()));}if(!actualObjects.equals(pins.required("objects")))stale();
  var expected=new LinkedHashSet<>(input.evidenceRefs());if(pins.required("sources").size()!=expected.size())bad();var seen=new HashSet<DatasetProtocol.EvidenceRef>();var schema=new TaskSourceReadReceiptSchema(json);
  for(var row:pins.required("sources")){var ref=schema.evidence(row.required("evidence_ref"));if(!expected.contains(ref)||!seen.add(ref))bad();var current=source(runtime,ref,send?SourceReadProtocol.Use.MODEL_SEND:SourceReadProtocol.Use.FORMAL,provider);
   if(!WireValues.sha256(ContentReadAuthorization.canonicalBytes(current.physical)).equals(text(row,"processing_sha256"))||!json.tree(current.physical.original()).equals(row.required("original_object"))||!json.tree(current.physical.parsed()).equals(row.required("parsed_object")))stale();}
  runtime.requireUnchanged();return pins.deepCopy();
 }
 private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void limit(){throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
}

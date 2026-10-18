package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.mapper.DocumentTechnicalCheckMapper.Registration;
import com.ruoyi.medical.persistence.DocumentTechnicalCheckProtocol.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;

/** One bounded synchronous technical job. Never declares full professional coverage or changes review state. */
@Component
@EnableConfigurationProperties(DocumentTechnicalCheckPolicy.class)
@ConditionalOnProperty(prefix="medical.document-technical-check",name="enabled",havingValue="true")
public final class DocumentTechnicalCheckService {
 static final String PURPOSE="DOCUMENT_BODY", EVENT="document.technical_check.completed";
 private final EnterpriseAccess access; private final DocumentTechnicalCheckMapper mapper;
 private final DocumentReadService documents; private final PermissionMapper permissions;
 private final ResourceReadAuthority resources; private final ArtifactRegistryMapper artifacts;
 private final DependencyLineage lineage; private final SourceUploadTransport transport;
 private final DocumentTechnicalCheckPolicy policy; private final PermissionSchema json;
 private final com.fasterxml.jackson.databind.ObjectMapper codec=com.fasterxml.jackson.databind.json.JsonMapper.builder().propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
 private final DocumentTechnicalCheckSchema schema; private final DocumentTechnicalCheckEngine engine;
 public DocumentTechnicalCheckService(EnterpriseAccess access,DocumentTechnicalCheckMapper mapper,DocumentReadService documents,
        PermissionMapper permissions,ResourceReadAuthority resources,ArtifactRegistryMapper artifacts,DependencyLineage lineage,
        SourceUploadTransport transport,DocumentTechnicalCheckPolicy policy,PermissionSchema json)throws Exception {
  this.access=access;this.mapper=mapper;this.documents=documents;this.permissions=permissions;this.resources=resources;
  this.artifacts=artifacts;this.lineage=lineage;this.transport=transport;this.policy=policy;this.json=json;
  schema=new DocumentTechnicalCheckSchema();engine=new DocumentTechnicalCheckEngine(schema);
 }
 private record Prepared(DocumentReadService.FixedRead proof,JsonNode baseline) { }
 public JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String document,String key,byte[] bytes)throws Exception {
  policy.requireConfigured();WireValues.uuid(document);SimulationInputArtifactSchema.key(key);PermissionSchema.revision(context);
  JsonNode request=schema.parse("Request",bytes,policy.maximumBytes());String requestHash=schema.hash(Map.of("request",request,"document_id",document,"context",context,"session",subject.sessionId()));
  var existing=tx(subject,a->{current(a,context);var v=values(a);v.put("key",key);return operationForKey(a,key);});
  if(existing.size()>1)bad();
  if(!existing.isEmpty()) {
   same(requestHash,existing.get(0).requestSha256());
   if("COMPLETED".equals(existing.get(0).state()))return read(subject,context,existing.get(0).targetId()).required("receipt");
  }
  var proof=documents.readFixed(subject,context,document,text(request,"document_revision"));
  Command command=tx(subject,a->start(a,context,key,document,request,requestHash,subject.sessionId(),proof));
  // Refresh after insertion: DocumentReadService's complete proof includes the actual check-id list.
  // The immutable content baseline below intentionally excludes that list, so our own job does not invalidate its input.
  var currentProof=documents.readFixed(subject,context,document,text(request,"document_revision"));
  if(command.outputs().size()!=2)bad();
  for(int index=0;index<command.outputs().size();index++) {
   Output output=command.outputs().get(index);
   if(output.reference()!=null){
    Command fixed=command;
    var actual=transport.writeDocumentProjection(subject,currentProof.header().tenantId(),output.intent(),canonicalBytes(output.kind().equals("COVERAGE")?command.coverage():command.findings()),true,policy.maximumBytes(),
      ()->tx(subject,a->{guard(a,context,currentProof,fixed);return null;}));
    verifyReference(output.intent(),actual);same(output.reference(),actual);continue;
   }
   boolean recover=output.attempted();Command previous=command;final Command beforeAttempt=previous;
   var updated=new ArrayList<>(command.outputs());updated.set(index,new Output(output.artifactId(),output.kind(),output.schemaRef(),output.intent(),true,null));
   command=copy(command,updated);Command attempted=command;
   tx(subject,a->{guard(a,context,currentProof,beforeAttempt);if(!recover){insertAttempt(a,currentProof,beforeAttempt,output);update(a,key,beforeAttempt,attempted);}verifyLedger(a,currentProof,attempted);return null;});
   byte[] content=canonicalBytes(output.kind().equals("COVERAGE")?command.coverage():command.findings());
   // Once attempted is durable, retries ONLY recoverVerified. No second write after an unknown outcome.
   var reference=transport.writeDocumentProjection(subject,currentProof.header().tenantId(),output.intent(),content,recover,policy.maximumBytes(),
      ()->tx(subject,a->{guard(a,context,currentProof,attempted);return null;}));
   verifyReference(output.intent(),reference);updated=new ArrayList<>(command.outputs());
   updated.set(index,new Output(output.artifactId(),output.kind(),output.schemaRef(),output.intent(),true,reference));
   previous=command;command=copy(command,updated);Command stored=command,prior=previous;
   tx(subject,a->{guard(a,context,currentProof,stored);update(a,key,prior,stored);return null;});
  }
  Command completed=command;
  return tx(subject,a->finish(a,context,key,currentProof,completed));
 }
 /** Original-key inspection is read-only and remains available after the synchronous deadline. */
 public JsonNode recovery(ApplicationSessionIdentity.Verified subject,String context,String document,String key)throws Exception {
  policy.requireConfigured();WireValues.uuid(document);PermissionSchema.revision(context);SimulationInputArtifactSchema.key(key);
  var state=tx(subject,a->{current(a,context);var v=values(a);v.put("key",key);var rows=operationForKey(a,key);if(rows.size()>1)bad();
   if(rows.isEmpty())return (com.ruoyi.medical.persistence.SelectionOperationRow)null;
   var op=rows.get(0);same("1.0.0",op.schemaVersion());same(schema.manifest(),op.schemaManifestSha256());if("PROCESSING".equals(op.state())){var c=command(op.responseRef());same(document,text(c.input(),"document_id"));same(c.checkId(),op.targetId());same(c.operationId(),op.operationId());same(c.requestHash(),op.requestSha256());}
   else if(!"COMPLETED".equals(op.state()))bad();return op;});
  var view=json.object().put("schema_version","1.0.0").put("execution_mode","SYNC_REQUEST").put("overall_review_ready",false);
  if(state==null){view.put("state","NOT_FOUND").putNull("request_body_sha256").putNull("check_id").putNull("receipt");return schema.validate("Recovery",view);}
  if("COMPLETED".equals(state.state())){
   var receipt=read(subject,context,state.targetId()).required("receipt");same(document,text(receipt,"document_id"));same(state.operationId(),text(receipt,"operation_id"));
   view.put("state","COMPLETED").put("check_id",state.targetId()).put("request_body_sha256",text(receipt,"request_body_sha256"));view.set("receipt",receipt);
  }else{
   var c=command(state.responseRef());var proof=documents.readFixed(subject,context,document,text(c.input(),"document_revision"));
   tx(subject,a->{prepare(a,context,proof,false);same(c.bodyHash(),schema.hash(c.request()));same(c.input(),schema.input(c.input()));same(c.manifest(),schema.validate("ExecutionManifest",c.manifest()));verifyPending(a,proof,c);verifyStage(a,proof,c);return null;});
   view.put("state","PROCESSING").put("check_id",c.checkId()).put("request_body_sha256",c.bodyHash()).putNull("receipt");
  }return schema.validate("Recovery",view);
 }
 private Command start(EnterpriseAccess.Admission a,String context,String key,String document,JsonNode request,String requestHash,String requestSession,DocumentReadService.FixedRead proof)throws Exception {
  var prepared=prepare(a,context,proof,true);var h=proof.header();
  same(document,h.documentId());same(text(request,"document_revision"),h.documentRevision());same(text(request,"expected_revision"),h.revision().toString());
  var v=values(a);v.put("key",key);var prior=operationForKey(a,key);if(prior.size()>1)bad();
  if(!prior.isEmpty()) {same(requestHash,prior.get(0).requestSha256());same("PROCESSING",prior.get(0).state());var p=prior.get(0);same("1.0.0",p.schemaVersion());same(schema.manifest(),p.schemaManifestSha256());var c=command(p.responseRef());same(p.operationId(),c.operationId());same(p.targetId(),c.checkId());same(p.requestSha256(),c.requestHash());same(request,c.request());guard(a,context,proof,c);verifyStage(a,proof,c);return c;}
  JsonNode manifest=executionManifest();String manifestHash=schema.hash(manifest);v.put("projectId",h.projectId());v.put("executionManifestHash",manifestHash);
  var registered=mapper.manifest(v);if(registered.size()>1)bad();String manifestId;
  if(registered.isEmpty()) {
   manifestId=id();v.put("versionId",manifestId);v.put("versionKind","TECHNICAL_CHECK_MANIFEST");v.put("versionHash",manifestHash);count(mapper.insertVersion(v));
   v.put("executionManifestId",manifestId);v.put("manifestJson",schema.canonical(manifest));v.put("manifest",schema.manifest());v.put("classification",h.classification());count(mapper.insertManifest(v));
  } else {var m=registered.get(0);manifestId=m.executionManifestId();same(schema.canonical(manifest),m.canonicalManifest());same(manifestHash,m.manifestSha256());same(schema.manifest(),m.schemaManifest());}
  var input=json.object().put("schema_version","1.0.0").put("tenant_id",h.tenantId()).put("project_id",h.projectId()).put("document_id",document)
   .put("document_revision",h.documentRevision()).put("body_sha256",h.bodySha256()).put("baseline_sha256",schema.hash(prepared.baseline()))
   .put("body_schema_ref",schema.bodySchemaRef()).put("body_schema_sha256",schema.bodySchemaSha256()).put("execution_manifest_id",manifestId)
   .put("check_kind","STRUCTURE_FIXED_REFERENCES").put("checker_id","urn:medical:document-technical-check:structure-fixed-references:1.0.0");
  input.set("limits",limits());schema.input(input);
  var now=(LocalDateTime)v.get("now");var c=new Command(id(),id(),id(),manifestId,utc(now),utc(now.plusSeconds(policy.maximumSeconds())),requestHash,context,requestSession,schema.hash(request),
    request,manifest,prepared.baseline(),input,null,null,List.of());
  c=withOutputs(c,proof);
  jobValues(v,c,proof);if(!mapper.existingInput(v).isEmpty())throw new TenantAccessException(ErrorCode.ACTION_STALE);
  count(mapper.insertExecution(v));count(mapper.insertJob(v));count(mapper.insertCheck(v));
  v.put("commandJson",schema.canonical(c));v.put("requestHash",requestHash);v.put("operationManifest",schema.manifest());count(mapper.startOperation(v));insertIntent(a,proof,c,key);
  count(mapper.claimJob(v));count(mapper.claimCheck(v));return c;
 }
 private Prepared prepare(EnterpriseAccess.Admission a,String context,DocumentReadService.FixedRead proof,boolean writing)throws Exception {
  current(a,context);policy.requireConfigured();documents.requireCurrent(a,proof);var h=proof.header();
  same(a.context().tenant_id(),h.tenantId());resources.require(a,new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"DOCUMENT",h.documentId()),writing?"document.check":"document.read",PURPOSE);
  if(writing&&(!h.documentRevision().equals(h.currentDocumentRevision())||!"ACTIVE".equals(h.projectStatus())))throw new TenantAccessException(ErrorCode.ACTION_STALE);
  var baseline=json.object();baseline.set("header",schema.read(schema.canonical(h)));baseline.set("dependencies",schema.read(schema.canonical(proof.dependencies())));baseline.set("watermarks",schema.read(schema.canonical(proof.watermarks())));
  baseline.put("auth_epoch",a.context().auth_epoch()).put("governance_epoch",a.context().governance_epoch());return new Prepared(proof,baseline);
 }
 private void guard(EnterpriseAccess.Admission a,String context,DocumentReadService.FixedRead proof,Command c)throws Exception {
  var prepared=prepare(a,context,proof,true);same(prepared.baseline(),c.baseline());same(executionManifest(),c.manifest());
  if(!permissions.databaseTime().isBefore(LocalDateTime.ofInstant(Instant.parse(c.deadline()),ZoneOffset.UTC)))throw new TenantAccessException(ErrorCode.ACTION_STALE);
  same(schema.hash(c.request()),c.bodyHash());same(schema.hash(c.baseline()),text(c.input(),"baseline_sha256"));schema.input(c.input());verifyPending(a,proof,c);
 }
 /** Rebuild durable pending identity from the real execution rows before trusting a command or touching storage. */
 private void verifyPending(EnterpriseAccess.Admission a,DocumentReadService.FixedRead proof,Command c)throws Exception {
  var h=proof.header();same(c.requestHash(),schema.hash(Map.of("request",c.request(),"document_id",h.documentId(),"context",c.requestContext(),"session",c.requestSession())));var v=values(a);v.put("projectId",h.projectId());v.put("checkId",c.checkId());v.put("runId",c.runId());
  var check=one(mapper.check(v));var job=one(mapper.job(v));var execution=one(mapper.execution(v));
  same(h.tenantId(),check.tenantId());same(h.projectId(),check.projectId());same(c.checkId(),check.checkId());
  same("DOCUMENT",check.targetKind());same(h.documentId(),check.targetId());same(h.documentRevision(),check.targetRevision());
  same("RUNNING",check.status());same(BigInteger.TWO,check.revision());if(check.invalidationReason()!=null||check.runId()!=null||check.stepId()!=null||check.actionId()!=null||check.coverageArtifactId()!=null||check.findingsArtifactId()!=null||check.resultRef()!=null)bad();
  same(schema.hash(c.input()),check.inputSha256());same(c.manifestId(),check.ruleManifestId());same(h.snapshotId(),check.scopeManifestId());
  sameJson(schema.canonical(c.manifest()),check.ruleManifest());sameJson(schema.canonical(c.baseline()),check.inputWatermarks());
  same(h.tenantId(),job.tenantId());same(h.projectId(),job.projectId());same(c.runId(),job.runId());same("SYSTEM_JOB",job.executionKind());same("DOCUMENT_TECHNICAL_CHECK",job.jobType());
  same("DOCUMENT",job.targetKind());same(h.documentId(),job.targetId());same(h.documentRevision(),job.targetRevision());
  same("RUNNING",job.status());same(BigInteger.TWO,job.revision());if(job.workflowId()!=null||job.endedAt()!=null||job.coverageArtifactId()!=null)bad();
  same(schema.hash(c.input()),job.inputSha256());sameJson(schema.canonical(c.input()),job.inputManifest());same(c.manifestId(),job.configRevision());same(c.manifestId(),job.ruleManifestId());same(h.snapshotId(),job.scopeManifestId());
  same(h.tenantId(),execution.tenantId());same(h.projectId(),execution.projectId());same(c.runId(),execution.runId());same("SYSTEM_JOB",execution.executionKind());sameJson(schema.canonical(proof.dependencies()),execution.scopeManifest());
  var created=LocalDateTime.ofInstant(Instant.parse(c.createdAt()),ZoneOffset.UTC);
  same(created,check.createdAt());same(created,job.createdAt());same(created,execution.createdAt());same(created,job.startedAt());
  same(utc(created.plusSeconds(policy.maximumSeconds())),c.deadline());
  for(String actor:List.of(check.createdBy(),job.createdBy(),job.initiatorId(),execution.createdBy()))same(a.actorId(),actor);
  for(String version:List.of(check.schemaVersion(),job.schemaVersion(),execution.schemaVersion()))same("1.0.0",version);
  for(String digest:List.of(check.schemaManifest(),job.schemaManifest(),execution.schemaManifest()))same(schema.manifest(),digest);
  for(String classification:List.of(check.classification(),job.classification(),execution.classification()))same(h.classification(),classification);
  var input=schema.input(c.input());same(h.tenantId(),text(input,"tenant_id"));same(h.projectId(),text(input,"project_id"));same(h.documentId(),text(input,"document_id"));same(h.documentRevision(),text(input,"document_revision"));same(h.bodySha256(),text(input,"body_sha256"));same(c.manifestId(),text(input,"execution_manifest_id"));
  same(schema.hash(c.baseline()),text(input,"baseline_sha256"));same(schema.hash(schema.validate("Request",c.request())),c.bodyHash());same(h.documentRevision(),text(c.request(),"document_revision"));
  same(text(c.baseline().required("header"),"revision"),text(c.request(),"expected_revision"));
  var marks=c.baseline().required("watermarks");same(WireValues.revision(text(marks,"issue_watermark")),check.issueWatermark());same(WireValues.revision(text(marks,"evidence_watermark")),check.evidenceWatermark());
  v.put("executionManifestHash",schema.hash(c.manifest()));var manifest=one(mapper.manifest(v));same(c.manifestId(),manifest.executionManifestId());same(h.tenantId(),manifest.tenantId());same(h.projectId(),manifest.projectId());same(schema.canonical(c.manifest()),manifest.canonicalManifest());same(schema.hash(c.manifest()),manifest.manifestSha256());same(schema.manifest(),manifest.schemaManifest());same("1.0.0",manifest.schemaVersion());
  var version=one(artifacts.version(c.manifestId()));same(c.manifestId(),version.id());same(h.projectId(),version.projectId());same("TECHNICAL_CHECK_MANIFEST",version.entityKind());same(manifest.manifestSha256(),version.contentSha256());
  same(a.actorId(),one(mapper.recoveryIntent(ledgerValues(a,proof,c))).createdBy());verifyLedger(a,proof,c);
  if(c.outputs().isEmpty())bad();
  if(c.outputs().size()!=2)bad();same(c.findings(),engine.execute(json.tree(proof.body()),c.input(),policy.limits()));same(c.coverage(),c.findings().required("coverage"));
  if(c.outputs().get(0).artifactId().equals(c.outputs().get(1).artifactId()))bad();
  for(int index=0;index<2;index++){
   var output=c.outputs().get(index);String kind=index==0?"COVERAGE":"FINDINGS";same(kind,output.kind());WireValues.uuid(output.artifactId());WireValues.uuid(output.intent().writeId());
   same(schema.ref(index==0?"Coverage":"Result"),output.schemaRef());
   same("documents/"+h.tenantId()+"/"+h.documentId()+"/technical-checks/"+c.checkId()+"/"+output.artifactId(),output.intent().objectKey());
   byte[] bytes=canonicalBytes(index==0?c.coverage():c.findings());same(WireValues.sha256(bytes),output.intent().sha256());same(Integer.toString(bytes.length),output.intent().sizeBytes());same("application/json",output.intent().mediaType());
   if(output.reference()!=null){if(!output.attempted())bad();verifyReference(output.intent(),output.reference());}
  }
 }
 private List<SelectionOperationRow> operationForKey(EnterpriseAccess.Admission a,String key){
  var v=values(a);v.put("key",key);v.put("creatorId",a.actorId());var operations=mapper.operation(v);var intents=mapper.recoveryIntentByKey(v);
  if(operations.size()>1||intents.size()>1||operations.isEmpty()!=intents.isEmpty())bad();
  if(!operations.isEmpty()){var op=operations.get(0);var intent=intents.get(0);same(a.context().tenant_id(),intent.tenantId());same(a.actorId(),intent.createdBy());same(key,intent.idempotencyKey());same(op.operationId(),intent.operationId());same(op.targetId(),intent.checkId());}
  return operations;
 }
 private Command withOutputs(Command c,DocumentReadService.FixedRead proof)throws Exception {
  JsonNode findings=engine.execute(json.tree(proof.body()),c.input(),policy.limits()),coverage=findings.required("coverage");var outputs=new ArrayList<Output>();
  for(String kind:List.of("COVERAGE","FINDINGS")){
   byte[] content=canonicalBytes(kind.equals("COVERAGE")?coverage:findings);if(content.length>policy.maximumBytes())throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
   String artifact=id();var intent=new S3ObjectWriter.WriteIntent(id(),"documents/"+proof.header().tenantId()+"/"+proof.header().documentId()+"/technical-checks/"+c.checkId()+"/"+artifact,WireValues.sha256(content),Integer.toString(content.length),"application/json");
   outputs.add(new Output(artifact,kind,schema.ref(kind.equals("COVERAGE")?"Coverage":"Result"),intent,false,null));
  }
  return new Command(c.checkId(),c.runId(),c.operationId(),c.manifestId(),c.createdAt(),c.deadline(),c.requestHash(),c.requestContext(),c.requestSession(),c.bodyHash(),c.request(),c.manifest(),c.baseline(),c.input(),coverage,findings,outputs);
 }
 private Command initial(Command c){return copy(c,c.outputs().stream().map(o->new Output(o.artifactId(),o.kind(),o.schemaRef(),o.intent(),false,null)).toList());}
 private Map<String,Object> ledgerValues(EnterpriseAccess.Admission a,DocumentReadService.FixedRead proof,Command c){var v=values(a);v.put("projectId",proof.header().projectId());v.put("operationId",c.operationId());v.put("checkId",c.checkId());return v;}
 private JsonNode startEvent(DocumentTechnicalCheckMapper.RecoveryIntent row)throws Exception{return json.object().put("schema_version","1.0.0").put("operation_id",row.operationId()).put("check_id",row.checkId()).put("intent_sha256",row.intentSha256()).put("start_event_id",row.startEventId()).put("idempotency_key_sha256",WireValues.sha256(row.idempotencyKey().getBytes(StandardCharsets.US_ASCII)));}
 private void insertIntent(EnterpriseAccess.Admission a,DocumentReadService.FixedRead proof,Command c,String key)throws Exception {
  var h=proof.header();String canonical=schema.canonical(initial(c)),digest=WireValues.sha256(canonical.getBytes(StandardCharsets.UTF_8));
  var row=new DocumentTechnicalCheckMapper.RecoveryIntent(h.tenantId(),h.projectId(),c.operationId(),c.checkId(),h.documentRevision(),key,canonical,digest,id(),"1.0.0",schema.manifest(),a.actorId(),LocalDateTime.ofInstant(Instant.parse(c.createdAt()),ZoneOffset.UTC),h.classification());
  var v=ledgerValues(a,proof,c);v.put("row",row);v.put("versionId",c.operationId());v.put("versionKind","TECHNICAL_CHECK_INPUT");v.put("versionHash",digest);count(mapper.insertVersion(v));count(mapper.insertRecoveryIntent(v));
  var event=schema.canonical(startEvent(row));count(permissions.saveAudit(a.actorId(),h.projectId(),id(),"document.technical_check.started",event,c.operationId(),schema.manifest(),h.classification()));
  count(permissions.saveEvent(a.actorId(),row.startEventId(),c.checkId(),"1","document.technical_check.started",event));
 }
 private void verifyLedger(EnterpriseAccess.Admission a,DocumentReadService.FixedRead proof,Command c)throws Exception {
  var v=ledgerValues(a,proof,c);var row=one(mapper.recoveryIntent(v));var h=proof.header();
  same(h.tenantId(),row.tenantId());same(h.projectId(),row.projectId());same(c.operationId(),row.operationId());same(c.checkId(),row.checkId());same(h.documentRevision(),row.documentRevision());
  same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());same(h.classification(),row.classification());same(LocalDateTime.ofInstant(Instant.parse(c.createdAt()),ZoneOffset.UTC),row.createdAt());
  same(schema.canonical(initial(c)),row.canonicalCommand());same(schema.hash(initial(c)),row.intentSha256());
  // Both pending and completed callers already bind the real original actor in their execution/check rows.
  var version=one(artifacts.version(c.operationId()));same(c.operationId(),version.id());same(h.projectId(),version.projectId());same("TECHNICAL_CHECK_INPUT",version.entityKind());same(row.intentSha256(),version.contentSha256());
  SimulationInputArtifactSchema.key(row.idempotencyKey());v.put("creatorId",row.createdBy());v.put("key",row.idempotencyKey());
  same(row,one(mapper.recoveryIntentByKey(v)));var originalOperation=one(mapper.recoveryOriginalOperation(v));same(c.operationId(),originalOperation.operationId());same(c.checkId(),originalOperation.targetId());same(c.requestHash(),originalOperation.requestSha256());
  v.put("creatorId",row.createdBy());v.put("startEventId",row.startEventId());v.put("manifest",row.schemaManifest());v.put("classification",row.classification());
  same(startEvent(row),schema.read(one(mapper.recoveryStartAudit(v))));same(startEvent(row),schema.read(one(mapper.recoveryStartEvent(v))));
  var attempts=mapper.recoveryAttempts(v);var byKind=new HashMap<String,DocumentTechnicalCheckMapper.RecoveryAttempt>();for(var attempt:attempts)if(byKind.put(attempt.outputKind(),attempt)!=null)bad();
  if(c.outputs().size()!=2||!Set.of("COVERAGE","FINDINGS").containsAll(byKind.keySet()))bad();
  for(var o:c.outputs()){
   var attempt=byKind.get(o.kind());if(!o.attempted()){if(attempt!=null||o.reference()!=null)bad();continue;}if(attempt==null)bad();
   same(row.tenantId(),attempt.tenantId());same(row.projectId(),attempt.projectId());same(c.operationId(),attempt.operationId());same(c.checkId(),attempt.checkId());same(o.intent().writeId(),attempt.writeId());same(o.artifactId(),attempt.artifactId());same(row.intentSha256(),attempt.intentSha256());
   same("1.0.0",attempt.schemaVersion());same(row.schemaManifest(),attempt.schemaManifest());same(row.createdBy(),attempt.createdBy());same(row.classification(),attempt.classification());if(attempt.createdAt()==null||attempt.createdAt().isBefore(row.createdAt())||!attempt.createdAt().isBefore(LocalDateTime.ofInstant(Instant.parse(c.deadline()),ZoneOffset.UTC)))bad();
  }
 }
 private void insertAttempt(EnterpriseAccess.Admission a,DocumentReadService.FixedRead proof,Command c,Output o)throws Exception {
  var v=ledgerValues(a,proof,c);var row=one(mapper.recoveryIntent(v));same(a.actorId(),row.createdBy());
  var attempt=new DocumentTechnicalCheckMapper.RecoveryAttempt(row.tenantId(),row.projectId(),row.operationId(),row.checkId(),o.kind(),o.intent().writeId(),o.artifactId(),row.intentSha256(),"1.0.0",row.schemaManifest(),a.actorId(),(LocalDateTime)v.get("now"),row.classification());v.put("row",attempt);count(mapper.insertRecoveryAttempt(v));
 }
 private void verifyStage(EnterpriseAccess.Admission a,DocumentReadService.FixedRead proof,Command c){
  long stage=1;for(var o:c.outputs()){if(o.attempted())stage++;if(o.reference()!=null)stage++;}
  same(BigInteger.valueOf(stage),one(mapper.recoveryRevision(ledgerValues(a,proof,c))));
 }
 private void update(EnterpriseAccess.Admission a,String key,Command prior,Command next)throws Exception {
  var v=values(a);v.put("key",key);v.put("operationId",prior.operationId());v.put("checkId",prior.checkId());v.put("requestHash",prior.requestHash());
  var op=one(mapper.operation(v));sameJson(schema.canonical(prior),op.responseRef());same("PROCESSING",op.state());
  long stage=1;for(var o:prior.outputs()){if(o.attempted())stage++;if(o.reference()!=null)stage++;}same(BigInteger.valueOf(stage),one(mapper.recoveryRevision(v)));
  v.put("operationRevision",one(mapper.operationRevision(v)));v.put("commandJson",schema.canonical(next));count(mapper.updateCommand(v));sameJson(schema.canonical(next),one(mapper.operation(v)).responseRef());
 }
 private JsonNode finish(EnterpriseAccess.Admission a,String context,String key,DocumentReadService.FixedRead proof,Command c)throws Exception {
  guard(a,context,proof,c);verifyStage(a,proof,c);if(c.outputs().size()!=2||c.outputs().stream().anyMatch(o->o.reference()==null))bad();
  schema.result(c.findings());schema.coverage(c.coverage());same(c.input(),c.findings().required("input"));
  same(c.findings(),engine.execute(json.tree(proof.body()),c.input(),policy.limits()));
  var v=values(a);jobValues(v,c,proof);v.put("key",key);v.put("requestHash",c.requestHash());v.put("operationManifest",schema.manifest());
  var op=one(mapper.operation(v));same("PROCESSING",op.state());sameJson(schema.canonical(c),op.responseRef());v.put("operationRevision",one(mapper.operationRevision(v)));
  String dependencyHash=schema.hash(proof.dependencies());
  for(Output output:c.outputs()) {
   verifyReference(output.intent(),output.reference());v.put("versionId",output.artifactId());v.put("versionKind","ARTIFACT");v.put("versionHash",output.reference().sha256());count(mapper.insertVersion(v));
   v.put("artifactId",output.artifactId());v.put("artifactKind",output.kind().equals("COVERAGE")?"DOC_TECH_CHECK_COVERAGE":"DOC_TECH_CHECK_FINDINGS");
   v.put("reference",output.reference());v.put("storedObject",StoredObjectReferences.encode(output.reference()));v.put("accessSetId",proof.header().accessSetId());
   v.put("schemaRef",output.schemaRef());v.put("schemaHash",schema.schemaDocumentSha256());v.put("dependencyHash",dependencyHash);
   count(mapper.insertArtifact(v));count(mapper.insertBinding(v));
   for(var member:proof.dependencies().members()) {v.put("consumerId",output.artifactId());v.put("upstreamId",member.upstreamVersionId());count(mapper.insertLineage(v));}
  }
  var coverage=c.outputs().get(0);var findings=c.outputs().get(1);
  var receipt=json.object().put("schema_version","1.0.0").put("check_id",c.checkId()).put("job_id",c.runId()).put("operation_id",c.operationId())
    .put("document_id",proof.header().documentId()).put("document_revision",proof.header().documentRevision()).put("input_sha256",schema.hash(c.input()))
    .put("technical_execution","COMPLETE").put("technical_result",text(c.findings(),"technical_result"))
    .put("professional_coverage","UNKNOWN").put("overall_review_ready",false).put("request_body_sha256",c.bodyHash());
  receipt.set("coverage_artifact",json.tree(new PermissionProtocol.Basis(coverage.artifactId(),coverage.reference().sha256())));
  receipt.set("findings_artifact",json.tree(new PermissionProtocol.Basis(findings.artifactId(),findings.reference().sha256())));schema.validate("Receipt",receipt);
  var row=new Registration(a.context().tenant_id(),proof.header().projectId(),c.checkId(),c.runId(),proof.header().documentId(),proof.header().documentRevision(),c.manifestId(),
    schema.canonical(c.manifest()),schema.hash(c.manifest()),schema.canonical(c.input()),schema.hash(c.input()),schema.canonical(c.coverage()),schema.canonical(c.findings()),
    coverage.artifactId(),findings.artifactId(),schema.canonical(coverage.intent()),schema.canonical(findings.intent()),schema.canonical(coverage.reference()),schema.canonical(findings.reference()),
    proof.header().accessSetId(),dependencyHash,schema.canonical(c.baseline()),proof.watermarks().evidenceWatermark(),proof.watermarks().issueWatermark(),
    schema.canonical(c.request()),c.bodyHash(),c.requestHash(),c.operationId(),schema.canonical(receipt),"1.0.0",schema.manifest(),a.actorId(),
    LocalDateTime.ofInstant(Instant.parse(c.createdAt()),ZoneOffset.UTC),proof.header().classification(),"SYNC_REQUEST");
  v.put("coverageArtifactId",coverage.artifactId());v.put("findingsArtifactId",findings.artifactId());v.put("resultRef",schema.canonical(receipt));
  count(mapper.completeCheck(v));count(mapper.completeJob(v));v.put("row",row);count(mapper.insertRegistration(v));
  v.put("response",schema.canonical(receipt));count(mapper.completeOperation(v));
  var event=event(row);count(permissions.saveAudit(a.actorId(),row.projectId(),id(),EVENT,schema.canonical(event),c.operationId(),schema.manifest(),row.classification()));
  count(permissions.saveEvent(a.actorId(),c.operationId(),c.checkId(),"1",EVENT,schema.canonical(event)));
  same(row,one(mapper.registration(v)));verifyStored(a,row,proof,false);return receipt;
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String checkId)throws Exception {
  policy.requireConfigured();WireValues.uuid(checkId);PermissionSchema.revision(context);
  Registration row=tx(subject,a->{current(a,context);var v=values(a);v.put("checkId",checkId);var rows=mapper.registration(v);if(rows.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);var r=one(rows);
   resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),r.projectId(),"DOCUMENT",r.documentId()),"document.read",PURPOSE);return r;});
  var proof=documents.readFixed(subject,context,row.documentId(),row.documentRevision());
  tx(subject,a->{prepare(a,context,proof,false);verifyStored(a,row,proof,true);return null;});
  JsonNode coverage=readOutput(subject,context,row,proof,true),findings=readOutput(subject,context,row,proof,false);
  return tx(subject,a->{var prepared=prepare(a,context,proof,false);verifyStored(a,row,proof,true);
   var reasons=json.object().arrayNode();if(!schema.canonical(prepared.baseline()).equals(row.baselineJson()))reasons.add("DOCUMENT_OR_AUTHORITY_BASELINE_CHANGED");
   if(!schema.canonical(executionManifest()).equals(row.canonicalManifest()))reasons.add("CHECKER_CONFIGURATION_CHANGED");
   var cv=values(a);cv.put("projectId",row.projectId());cv.put("checkId",row.checkId());if(one(mapper.check(cv)).invalidationReason()!=null)reasons.add("CHECK_INVALIDATED");
   if(!row.postIssueWatermark().equals(proof.watermarks().issueWatermark())||!row.postEvidenceWatermark().equals(proof.watermarks().evidenceWatermark()))reasons.add("POST_EXECUTION_WATERMARK_CHANGED");
   var view=json.object().put("schema_version","1.0.0").put("currentness",reasons.isEmpty()?"CURRENT":"STALE");
   view.set("invalidation_reasons",reasons);view.set("receipt",schema.read(row.receiptJson()));view.set("result",findings);view.set("coverage",coverage);return schema.validate("Read",view);
  });
 }
 private JsonNode readOutput(ApplicationSessionIdentity.Verified subject,String context,Registration row,DocumentReadService.FixedRead proof,boolean coverage)throws Exception {
  var reference=codec.treeToValue(schema.read(coverage?row.coverageReference():row.findingsReference()),InterfaceProtocol.ObjectRef.class);
  return transport.readDocument(subject,row.tenantId(),policy.maximumBytes(),()->tx(subject,a->{prepare(a,context,proof,false);verifyStored(a,row,proof,true);return null;}),(reader,current)->{
   try(var actual=reader.readVerified(reference,current)) {
    same(reference,actual.reference());same(reference.sha256(),actual.fullSha256());int length=WireValues.revision(reference.sizeBytes()).intValueExact();
    if(length>policy.maximumBytes())throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=new byte[length];
    for(int start=0;start<length;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,length-start);System.arraycopy(actual.chunk(start,n),0,bytes,start,n);start+=n;}
    var content=schema.parse(coverage?"Coverage":"Result",bytes,policy.maximumBytes());same(new String(bytes,StandardCharsets.UTF_8),schema.canonical(content));
    same(schema.canonical(content),coverage?row.canonicalCoverage():row.canonicalFindings());current.requireCurrent();return content;
   }
  });
 }
 private void verifyStored(EnterpriseAccess.Admission a,Registration row,DocumentReadService.FixedRead proof,boolean artifactGrants)throws Exception {
  same(a.context().tenant_id(),row.tenantId());same(row.projectId(),proof.header().projectId());same(row.documentId(),proof.header().documentId());same(row.documentRevision(),proof.header().documentRevision());
  same("SYNC_REQUEST",row.executionMode());same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());
  var input=schema.input(schema.read(row.canonicalInput()));var result=schema.result(schema.read(row.canonicalFindings()));var coverage=schema.coverage(schema.read(row.canonicalCoverage()));
  same(input,result.required("input"));same(coverage,result.required("coverage"));same(row.inputSha256(),schema.hash(input));
  same(row.manifestSha256(),schema.hash(schema.validate("ExecutionManifest",schema.read(row.canonicalManifest()))));
  same(row.documentId(),text(input,"document_id"));same(row.documentRevision(),text(input,"document_revision"));same(row.tenantId(),text(input,"tenant_id"));same(row.projectId(),text(input,"project_id"));same(row.executionManifestId(),text(input,"execution_manifest_id"));same(proof.header().bodySha256(),text(input,"body_sha256"));
  var receipt=schema.validate("Receipt",schema.read(row.receiptJson()));same(row.checkId(),text(receipt,"check_id"));same(row.runId(),text(receipt,"job_id"));same(row.operationId(),text(receipt,"operation_id"));same(row.documentId(),text(receipt,"document_id"));same(row.documentRevision(),text(receipt,"document_revision"));same(row.inputSha256(),text(receipt,"input_sha256"));same(row.requestBodySha256(),text(receipt,"request_body_sha256"));same(text(result,"technical_result"),text(receipt,"technical_result"));
  same(json.tree(new PermissionProtocol.Basis(row.coverageArtifactId(),schema.hash(coverage))),receipt.required("coverage_artifact"));same(json.tree(new PermissionProtocol.Basis(row.findingsArtifactId(),schema.hash(result))),receipt.required("findings_artifact"));
  same(row.requestBodySha256(),schema.hash(schema.validate("Request",schema.read(row.canonicalRequest()))));
  var baselineMarks=schema.read(row.baselineJson()).required("watermarks");same(row.postIssueWatermark(),WireValues.revision(text(baselineMarks,"issue_watermark")));same(row.postEvidenceWatermark(),WireValues.revision(text(baselineMarks,"evidence_watermark")));
  same(row.baselineJson(),schema.canonical(schema.read(row.baselineJson())));same(text(input,"baseline_sha256"),schema.hash(schema.read(row.baselineJson())));
  same(row.accessSetId(),proof.header().accessSetId());same(row.dependencyManifestSha256(),schema.hash(proof.dependencies()));
  var v=values(a);v.put("checkId",row.checkId());v.put("runId",row.runId());v.put("operationId",row.operationId());v.put("creatorId",row.createdBy());
  v.put("projectId",row.projectId());v.put("classification",row.classification());v.put("operationManifest",schema.manifest());
  v.put("executionManifestHash",row.manifestSha256());var manifest=one(mapper.manifest(v));same(row.executionManifestId(),manifest.executionManifestId());same(row.canonicalManifest(),manifest.canonicalManifest());same(row.manifestSha256(),manifest.manifestSha256());same(row.schemaManifest(),manifest.schemaManifest());same("1.0.0",manifest.schemaVersion());same(row.tenantId(),manifest.tenantId());same(row.projectId(),manifest.projectId());
  var mv=one(artifacts.version(row.executionManifestId()));same("TECHNICAL_CHECK_MANIFEST",mv.entityKind());same(row.executionManifestId(),mv.id());same(row.projectId(),mv.projectId());same(row.manifestSha256(),mv.contentSha256());
  var check=one(mapper.check(v));var job=one(mapper.job(v));var execution=one(mapper.execution(v));
  same(row.tenantId(),check.tenantId());same(row.projectId(),check.projectId());same(row.checkId(),check.checkId());same("DOCUMENT",check.targetKind());same(row.documentId(),check.targetId());same(row.documentRevision(),check.targetRevision());
  same("SUCCEEDED",check.status());if(check.invalidationReason()==null)same(BigInteger.valueOf(3),check.revision());else if(check.revision().compareTo(BigInteger.valueOf(3))<=0)bad();same(row.inputSha256(),check.inputSha256());same(row.executionManifestId(),check.ruleManifestId());
  same(proof.header().snapshotId(),check.scopeManifestId());sameJson(row.canonicalManifest(),check.ruleManifest());sameJson(row.baselineJson(),check.inputWatermarks());
  same(row.postIssueWatermark(),check.issueWatermark());same(row.postEvidenceWatermark(),check.evidenceWatermark());same(row.coverageArtifactId(),check.coverageArtifactId());same(row.findingsArtifactId(),check.findingsArtifactId());sameJson(row.receiptJson(),check.resultRef());
  if(check.runId()!=null||check.stepId()!=null||check.actionId()!=null)bad();
  same(row.tenantId(),job.tenantId());same(row.projectId(),job.projectId());same(row.runId(),job.runId());same("SYSTEM_JOB",job.executionKind());same("DOCUMENT_TECHNICAL_CHECK",job.jobType());
  same("DOCUMENT",job.targetKind());same(row.documentId(),job.targetId());same(row.documentRevision(),job.targetRevision());same(row.inputSha256(),job.inputSha256());sameJson(row.canonicalInput(),job.inputManifest());
  same(row.executionManifestId(),job.configRevision());same(row.executionManifestId(),job.ruleManifestId());same(proof.header().snapshotId(),job.scopeManifestId());same(row.coverageArtifactId(),job.coverageArtifactId());
  same("SUCCEEDED",job.status());same(BigInteger.valueOf(3),job.revision());if(job.workflowId()!=null||job.startedAt()==null||job.endedAt()==null||job.endedAt().isBefore(job.startedAt()))bad();
  same(row.tenantId(),execution.tenantId());same(row.projectId(),execution.projectId());same(row.runId(),execution.runId());same("SYSTEM_JOB",execution.executionKind());sameJson(schema.canonical(proof.dependencies()),execution.scopeManifest());
  for(String actor:List.of(check.createdBy(),job.createdBy(),job.initiatorId(),execution.createdBy()))same(row.createdBy(),actor);
  for(String manifestValue:List.of(check.schemaManifest(),job.schemaManifest(),execution.schemaManifest()))same(row.schemaManifest(),manifestValue);
  for(String version:List.of(check.schemaVersion(),job.schemaVersion(),execution.schemaVersion()))same("1.0.0",version);
  for(String classification:List.of(check.classification(),job.classification(),execution.classification()))same(row.classification(),classification);
  for(LocalDateTime created:List.of(check.createdAt(),job.createdAt(),execution.createdAt()))same(row.createdAt(),created);
  var operation=one(mapper.operationById(v));same(row.operationId(),operation.operationId());same(row.checkId(),operation.targetId());same(row.operationRequestSha256(),operation.requestSha256());
  same("COMPLETED",operation.state());same(Integer.valueOf(201),operation.httpStatus());same("1.0.0",operation.schemaVersion());same(schema.manifest(),operation.schemaManifestSha256());sameJson(row.receiptJson(),operation.responseRef());
  var expected=event(row);same(expected,schema.read(one(mapper.audit(v))));same(expected,schema.read(one(mapper.event(v))));
  var original=command(one(mapper.recoveryIntent(v)).canonicalCommand());
  same(row.operationId(),original.operationId());same(row.checkId(),original.checkId());same(row.runId(),original.runId());
  same(row.createdBy(),one(mapper.recoveryIntent(v)).createdBy());
  sameJson(row.canonicalCoverage(),schema.canonical(original.coverage()));sameJson(row.canonicalFindings(),schema.canonical(original.findings()));
  same(row.operationRequestSha256(),original.requestHash());sameJson(row.canonicalRequest(),schema.canonical(original.request()));
  var verifiedOutputs=new ArrayList<Output>();
  for(int index=0;index<2;index++){var o=original.outputs().get(index);boolean cv=index==0;
   same(cv?row.coverageArtifactId():row.findingsArtifactId(),o.artifactId());sameJson(cv?row.coverageIntent():row.findingsIntent(),schema.canonical(o.intent()));
   verifiedOutputs.add(new Output(o.artifactId(),o.kind(),o.schemaRef(),o.intent(),true,codec.treeToValue(schema.read(cv?row.coverageReference():row.findingsReference()),InterfaceProtocol.ObjectRef.class)));
  }
  verifyLedger(a,proof,copy(original,verifiedOutputs));
  var started=LocalDateTime.ofInstant(Instant.parse(original.createdAt()),ZoneOffset.UTC);var deadline=LocalDateTime.ofInstant(Instant.parse(original.deadline()),ZoneOffset.UTC);
  same(started,job.startedAt());if(job.endedAt()==null||job.endedAt().isBefore(started)||!job.endedAt().isBefore(deadline))bad();
  for(var attempt:mapper.recoveryAttempts(v))if(attempt.createdAt().isAfter(job.endedAt()))bad();
  verifyArtifact(a,row,proof,true,artifactGrants);verifyArtifact(a,row,proof,false,artifactGrants);
 }
 private void verifyArtifact(EnterpriseAccess.Admission a,Registration row,DocumentReadService.FixedRead proof,boolean coverage,boolean grants)throws Exception {
  String id=coverage?row.coverageArtifactId():row.findingsArtifactId();var artifact=one(artifacts.artifact(id));var binding=one(artifacts.binding(id));
  var reference=codec.treeToValue(schema.read(coverage?row.coverageReference():row.findingsReference()),InterfaceProtocol.ObjectRef.class);
  var intent=codec.treeToValue(schema.read(coverage?row.coverageIntent():row.findingsIntent()),S3ObjectWriter.WriteIntent.class);verifyReference(intent,reference);
  same(reference.sha256(),schema.hash(schema.read(coverage?row.canonicalCoverage():row.canonicalFindings())));
  same(reference,artifact.reference());same(reference,StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));
  same(row.tenantId(),artifact.tenantId());same(row.projectId(),artifact.projectId());same(id,artifact.artifactId());same(reference.sha256(),artifact.contentSha256());
  same(coverage?"DOC_TECH_CHECK_COVERAGE":"DOC_TECH_CHECK_FINDINGS",artifact.artifactKind());same(row.accessSetId(),artifact.accessSetId());same(row.classification(),artifact.classification());same("1.0.0",artifact.schemaVersion());same(schema.manifest(),artifact.schemaManifest());
  same(row.tenantId(),binding.tenantId());same(row.projectId(),binding.projectId());same(id,binding.artifactId());same("DOCUMENT",binding.resourceKind());same(row.documentId(),binding.resourceId());
  same(schema.ref(coverage?"Coverage":"Result"),binding.schemaRef());same(schema.schemaDocumentSha256(),binding.schemaDocumentSha256());same(row.accessSetId(),binding.accessSetId());same(row.dependencyManifestSha256(),binding.dependencyManifestSha256());
  var v=values(a);v.put("artifactId",id);var owner=one(mapper.artifactOwner(v));same(row.documentRevision(),owner.documentRevision());same(row.runId(),owner.runId());
  var version=one(artifacts.version(id));same("ARTIFACT",version.entityKind());same(id,version.id());same(row.projectId(),version.projectId());same(reference.sha256(),version.contentSha256());
  lineage.require(id,proof.dependencies().members(),new DependencyLineage.Limits(policy.maximumNodes(),policy.maximumRelationships()));
  if(grants)resources.require(a,new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"ARTIFACT",id),"artifact.read",PURPOSE);
 }
 private JsonNode event(Registration row)throws Exception {
  var value=json.object().put("schema_version","1.0.0").put("check_id",row.checkId()).put("job_id",row.runId()).put("input_sha256",row.inputSha256())
    .put("request_body_sha256",row.requestBodySha256()).put("operation_request_sha256",row.operationRequestSha256()).put("execution_mode","SYNC_REQUEST").put("registration_sha256",schema.hash(row));
  value.set("receipt",schema.validate("Receipt",schema.read(row.receiptJson())));value.set("input",schema.input(schema.read(row.canonicalInput())));
  value.put("coverage_sha256",schema.hash(schema.read(row.canonicalCoverage()))).put("findings_sha256",schema.hash(schema.read(row.canonicalFindings())));return value;
 }
 private JsonNode executionManifest()throws Exception {
  var implementations=new TreeMap<String,String>();for(Class<?> type:List.of(DocumentTechnicalCheckEngine.class,DocumentBodySemantics.class)){try(var bytes=Objects.requireNonNull(type.getResourceAsStream(type.getSimpleName()+".class"))){implementations.put(type.getName(),WireValues.sha256(bytes.readAllBytes()));}}String implementation=schema.hash(implementations);
  var value=json.object().put("schema_version","1.0.0").put("manifest_kind","TECHNICAL_CHECK_MANIFEST").put("checker_id","urn:medical:document-technical-check:structure-fixed-references:1.0.0")
    .put("implementation_sha256",implementation).put("schema_manifest_sha256",schema.manifest());value.set("limits",limits());return schema.validate("ExecutionManifest",value);
 }
 private JsonNode limits(){return json.object().put("maximum_nodes",policy.maximumNodes()).put("maximum_table_cells",policy.maximumTableCells()).put("maximum_relationships",policy.maximumRelationships());}
 private void jobValues(Map<String,Object> v,Command c,DocumentReadService.FixedRead proof)throws Exception {
  v.put("checkId",c.checkId());v.put("runId",c.runId());v.put("operationId",c.operationId());v.put("executionManifestId",c.manifestId());
  v.put("projectId",proof.header().projectId());v.put("documentId",proof.header().documentId());v.put("documentRevision",proof.header().documentRevision());
  v.put("snapshotId",proof.header().snapshotId());v.put("manifest",schema.manifest());v.put("manifestJson",schema.canonical(c.manifest()));
  v.put("inputJson",schema.canonical(c.input()));v.put("inputHash",schema.hash(c.input()));v.put("scopeManifest",schema.canonical(proof.dependencies()));v.put("baselineJson",schema.canonical(c.baseline()));
  v.put("evidenceWatermark",proof.watermarks().evidenceWatermark());v.put("issueWatermark",proof.watermarks().issueWatermark());v.put("classification",proof.header().classification());
 }
 private Command command(String value)throws Exception {var c=codec.treeToValue(schema.read(value),Command.class);sameJson(value,schema.canonical(c));return c;}
 private static Command copy(Command c,List<Output> outputs){return new Command(c.checkId(),c.runId(),c.operationId(),c.manifestId(),c.createdAt(),c.deadline(),c.requestHash(),c.requestContext(),c.requestSession(),c.bodyHash(),c.request(),c.manifest(),c.baseline(),c.input(),c.coverage(),c.findings(),outputs);}
 private byte[] canonicalBytes(Object value)throws Exception{return schema.canonical(value).getBytes(StandardCharsets.UTF_8);}
 private Map<String,Object> values(EnterpriseAccess.Admission a){var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("now",permissions.databaseTime());return v;}
 private <T>T tx(ApplicationSessionIdentity.Verified s,Checked<T> work){return access.execute(s,a->ThrowingSupplier.of(()->work.run(a)).get());}
 @FunctionalInterface private interface Checked<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
 private static void current(EnterpriseAccess.Admission a,String context){if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static void verifyReference(S3ObjectWriter.WriteIntent i,InterfaceProtocol.ObjectRef r){same(i.objectKey(),r.objectKey());same(i.sha256(),r.sha256());same(i.sizeBytes(),r.sizeBytes());same(i.mediaType(),r.mediaType());}
 private static String text(JsonNode n,String key){return n.required(key).textValue();}
 private static String utc(LocalDateTime v){return v.toInstant(ZoneOffset.UTC).toString();}
 private static String id(){return UUID.randomUUID().toString();}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
 private static void count(int n){if(n!=1)bad();}
 private void sameJson(String a,String b)throws Exception{same(schema.read(a),schema.read(b));}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

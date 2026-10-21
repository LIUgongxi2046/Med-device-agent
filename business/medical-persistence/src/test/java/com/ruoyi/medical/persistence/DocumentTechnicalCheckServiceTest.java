package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Public service boundary tests. Explicit admission, database and object-transport doubles;
 * these do not establish real MySQL transactions, S3 recovery, or granted permissions. */
class DocumentTechnicalCheckServiceTest {
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 static final String KEY="document-check-key-001";
 final PermissionSchema json=new PermissionSchema();
 final DocumentTechnicalCheckSchema schema=new DocumentTechnicalCheckSchema();
 final EnterpriseAccess access=mock(EnterpriseAccess.class);
 final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
 final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
 final DocumentTechnicalCheckMapper mapper=mock(DocumentTechnicalCheckMapper.class);
 final DocumentReadService documents=mock(DocumentReadService.class);
 final PermissionMapper permissions=mock(PermissionMapper.class);
 final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
 final ArtifactRegistryMapper artifacts=mock(ArtifactRegistryMapper.class);
 final DependencyLineage lineage=mock(DependencyLineage.class);
 final SourceUploadTransport transport=mock(SourceUploadTransport.class);
 final DocumentReadService.FixedRead proof=mock(DocumentReadService.FixedRead.class);
 final DocumentTechnicalCheckPolicy enabled=new DocumentTechnicalCheckPolicy(true,100000,16,8,64,120);
 final LocalDateTime now=LocalDateTime.of(2026,10,18,12,0);
 final ObjectNode request=json.object().put("schema_version","1.0.0").put("document_revision",id(6)).put("expected_revision","1").put("reason","Check fixed document");
 final InterfaceProtocol.DocumentBody body=new InterfaceProtocol.DocumentBody("1.0.0",id(12),List.of(
  new InterfaceProtocol.Node(id(20),null,0,List.of(),List.of(),new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.DOCUMENT,"Fixed document"))),List.of(),List.of(),List.of());
 String operationKey=KEY;
 SelectionOperationRow operation;BigInteger operationRevision=BigInteger.ONE;
 final DocumentTechnicalCheckService service;
 DocumentTechnicalCheckServiceTest()throws Exception {
  when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(admission.actorId()).thenReturn(id(2));when(subject.sessionId()).thenReturn(id(3));
  when(access.execute(eq(subject),any())).thenAnswer(i->{Function<EnterpriseAccess.Admission,Object> f=i.getArgument(1);return f.apply(admission);});
  when(permissions.databaseTime()).thenReturn(now);
  String bodyHash=schema.hash(json.tree(body));
  var header=new DocumentReadProtocol.Header(id(1),id(4),id(5),id(6),id(6),BigInteger.ONE,"DRAFT","ACTIVE",id(10),id(11),id(7),id(13),id(14),bodyHash,"body/key","body-version",id(15),id(16),id(12),id(8),BigInteger.ZERO,bodyHash,id(2),now,"INTERNAL");
  when(proof.header()).thenReturn(header);when(proof.body()).thenReturn(body);
  // The fixed-read boundary supplies the registered body schema, not merely decoded body bytes.
  var bodyRequirement=new ArtifactRegistryProtocol.ReadRequirement(
   new PermissionProtocol.Scope(id(1),id(4),"DOCUMENT",id(5)),"document.read","DOCUMENT_BODY",
   schema.bodySchemaRef(),schema.bodySchemaSha256(),schema.manifest(),new DependencyLineage.Limits(16,64));
  when(proof.bodyIntent()).thenReturn(new ContentReadProtocol.ReadIntent(header.bodyArtifactId(),"1",bodyRequirement,
   List.of(),new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.FORMAL,null),Map.of()));
  when(proof.watermarks()).thenReturn(new DocumentReadProtocol.Watermarks(BigInteger.ZERO,BigInteger.ZERO));
  when(proof.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(4),id(8),List.of()));
  when(documents.readFixed(subject,"1",id(5),id(6))).thenReturn(proof);
  when(mapper.operation(anyMap())).thenAnswer(i->operation==null||!operationKey.equals(capture(i).get("key"))?List.of():List.of(operation));
  when(mapper.operationRevision(anyMap())).thenAnswer(i->List.of(operationRevision));
  when(mapper.startOperation(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);operation=new SelectionOperationRow((String)v.get("operationId"),(String)v.get("requestHash"),"PROCESSING",null,pretty((String)v.get("commandJson")),(String)v.get("checkId"),"1.0.0",(String)v.get("operationManifest"));return 1;});
  when(mapper.updateCommand(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);assertEquals(operationRevision,v.get("operationRevision"));operationRevision=operationRevision.add(BigInteger.ONE);operation=new SelectionOperationRow(operation.operationId(),operation.requestSha256(),"PROCESSING",null,pretty((String)v.get("commandJson")),operation.targetId(),"1.0.0",operation.schemaManifestSha256());return 1;});
  when(mapper.insertVersion(anyMap())).thenReturn(1);when(mapper.insertManifest(anyMap())).thenReturn(1);
  when(mapper.insertExecution(anyMap())).thenReturn(1);when(mapper.insertJob(anyMap())).thenReturn(1);when(mapper.insertCheck(anyMap())).thenReturn(1);
  when(mapper.claimJob(anyMap())).thenReturn(1);when(mapper.claimCheck(anyMap())).thenReturn(1);
  successfulBoundaryStore();
  ledgerStore();
  service=service(enabled);
 }
 DocumentTechnicalCheckService service(DocumentTechnicalCheckPolicy p)throws Exception{return new DocumentTechnicalCheckService(access,mapper,documents,permissions,resources,artifacts,lineage,transport,p,json);}
 byte[] bytes(JsonNode value)throws Exception{return schema.canonical(value).getBytes(StandardCharsets.UTF_8);}
 JsonNode create()throws Exception{return service.create(subject,"1",id(5),KEY,bytes(request));}
 void noWrites(){verify(mapper,never()).insertVersion(anyMap());verify(mapper,never()).insertExecution(anyMap());verify(mapper,never()).insertJob(anyMap());verify(mapper,never()).insertCheck(anyMap());verify(mapper,never()).startOperation(anyMap());verify(mapper,never()).updateCommand(anyMap());verify(mapper,never()).insertArtifact(anyMap());verify(mapper,never()).insertRegistration(anyMap());verifyNoInteractions(transport);}
 void ledgerStore(){
  when(mapper.recoveryIntentByKey(anyMap())).thenAnswer(i->durableIntent==null||!durableIntent.idempotencyKey().equals(capture(i).get("key"))||!durableIntent.createdBy().equals(capture(i).get("creatorId"))?List.of():List.of(durableIntent));
  when(mapper.recoveryOriginalOperation(anyMap())).thenAnswer(i->operation==null||!operationKey.equals(capture(i).get("key"))?List.of():List.of(operation));
  when(mapper.insertRecoveryIntent(anyMap())).thenAnswer(i->{assertNull(durableIntent);durableIntent=(DocumentTechnicalCheckMapper.RecoveryIntent)capture(i).get("row");return 1;});
  when(mapper.recoveryIntent(anyMap())).thenAnswer(i->durableIntent==null?List.of():List.of(durableIntent));
  when(mapper.insertRecoveryAttempt(anyMap())).thenAnswer(i->{var row=(DocumentTechnicalCheckMapper.RecoveryAttempt)capture(i).get("row");assertNull(durableAttempts.putIfAbsent(row.outputKind(),row));return 1;});
  when(mapper.recoveryAttempts(anyMap())).thenAnswer(i->List.copyOf(durableAttempts.values()));
  when(mapper.recoveryStartAudit(anyMap())).thenAnswer(i->startAuditJson==null?List.of():List.of(startAuditJson));
  when(mapper.recoveryStartEvent(anyMap())).thenAnswer(i->startEventJson==null?List.of():List.of(startEventJson));
  when(mapper.recoveryRevision(anyMap())).thenAnswer(i->List.of(operationRevision));
 }
 @Test void disabledCreateAndReadNeverEnterAdmissionOrStorage()throws Exception{
  var disabled=service(new DocumentTechnicalCheckPolicy(false,null,null,null,null,null));
  assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->disabled.create(subject,"1",id(5),KEY,bytes(request))).code());
  assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->disabled.read(subject,"1",id(30))).code());
  verifyNoInteractions(access,mapper,documents,resources,transport);
 }
 @Test void enabledPolicyRequiresEveryFiniteCapacity(){
  assertThrows(IllegalArgumentException.class,()->new DocumentTechnicalCheckPolicy(true,null,16,8,64,120));
  assertThrows(IllegalArgumentException.class,()->new DocumentTechnicalCheckPolicy(true,1000,0,8,64,120));
  assertThrows(IllegalArgumentException.class,()->new DocumentTechnicalCheckPolicy(true,1000,16,8,64,0));
 }
 @Test void malformedRequestsNeverEnterAdmissionOrWrite()throws Exception{
  for(byte[] malformed:List.of("{}".getBytes(StandardCharsets.UTF_8),bytes(request.deepCopy().put("tenant_id",id(99))),bytes(request.deepCopy().put("reason"," padded ")),(schema.canonical(request)+" {}").getBytes(StandardCharsets.UTF_8)))
   assertThrows(Exception.class,()->service.create(subject,"1",id(5),KEY,malformed));
  verifyNoInteractions(access,mapper,documents,resources,transport);
 }
 @Test void staleContextCreateAndReadRefuseBeforeMapper()throws Exception{
  assertEquals(ErrorCode.CONTEXT_CHANGED,assertThrows(TenantAccessException.class,()->service.create(subject,"2",id(5),KEY,bytes(request))).code());
  assertEquals(ErrorCode.CONTEXT_CHANGED,assertThrows(TenantAccessException.class,()->service.read(subject,"2",id(30))).code());
  verifyNoInteractions(mapper,documents,resources,transport);
 }
 @Test void fixedDocumentReadDenialCannotCreateJob()throws Exception{
  when(documents.readFixed(subject,"1",id(5),id(6))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::create).code());noWrites();
 }
 @Test void documentCheckPermissionRefusedBeforePersistentChanges()throws Exception{
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(resources).require(eq(admission),any(),eq("document.check"),eq("DOCUMENT_BODY"));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::create).code());noWrites();
 }
 @Test void currentProofRefusalPreventsAuthorizationAndWrites()throws Exception{
  doThrow(new TenantAccessException(ErrorCode.ACTION_STALE)).when(documents).requireCurrent(admission,proof);
  assertEquals(ErrorCode.ACTION_STALE,assertThrows(TenantAccessException.class,this::create).code());noWrites();verifyNoInteractions(resources);
 }
 @Test void wrongExpectedDocumentRevisionCannotCreateJob()throws Exception{
  request.put("expected_revision","2");assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());noWrites();
 }
 @Test void unknownCheckReadNeverReadsObjects()throws Exception{
  assertEquals(ErrorCode.OBJECT_UNAVAILABLE,assertThrows(TenantAccessException.class,()->service.read(subject,"1",id(30))).code());
  verifyNoInteractions(documents,transport);noWrites();
 }
 @Test void attemptedUnknownWriteRetriesOnlyRecoveryAndNeverRegistersResults()throws Exception{
  when(transport.writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),anyBoolean(),eq(100000),any())).thenThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
  assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());
  assertNotNull(operation);assertTrue(schema.read(operation.responseRef()).path("outputs").get(0).path("attempted").booleanValue());
  assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());
  verify(transport,times(1)).writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),eq(false),eq(100000),any());
  verify(transport,times(1)).writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),eq(true),eq(100000),any());
  verify(mapper,times(1)).insertJob(anyMap());verify(mapper,never()).insertArtifact(anyMap());verify(mapper,never()).completeOperation(anyMap());
 }
 @Test void expiredAttemptDoesNotRetryStorageOrMutateCommand()throws Exception{
  when(transport.writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),anyBoolean(),eq(100000),any())).thenThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
  assertThrows(TenantAccessException.class,this::create);assertNotNull(operation);
  clearInvocations(mapper,transport);when(permissions.databaseTime()).thenReturn(now.plusSeconds(121));
  assertEquals(ErrorCode.ACTION_STALE,assertThrows(TenantAccessException.class,this::create).code());
  verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());verify(mapper,never()).insertJob(anyMap());
 }
 DocumentTechnicalCheckMapper.Registration registration;
 final Map<String,Map<String,Object>> storedRows=new HashMap<>();
 final Map<String,ArtifactRegistryProtocol.VersionRow> versions=new HashMap<>();
 final Map<String,ArtifactRegistryProtocol.ArtifactRow> artifactRows=new HashMap<>();
 final Map<String,ArtifactRegistryProtocol.BindingRow> bindings=new HashMap<>();
 final Map<String,byte[]> objectBytes=new HashMap<>();
 String auditJson,eventJson,startAuditJson,startEventJson;
 DocumentTechnicalCheckMapper.RecoveryIntent durableIntent;
 final Map<String,DocumentTechnicalCheckMapper.RecoveryAttempt> durableAttempts=new HashMap<>();
 @SuppressWarnings("unchecked") Map<String,Object> capture(org.mockito.invocation.InvocationOnMock i){return new HashMap<>((Map<String,Object>)i.getArgument(0));}
 <T>T typed(Class<T> type,Map<String,Object> data)throws Exception{
  var c=type.getRecordComponents();Class<?>[] types=new Class<?>[c.length];Object[] args=new Object[c.length];
  for(int i=0;i<c.length;i++){types[i]=c[i].getType();args[i]=data.get(c[i].getName());}
  return type.getDeclaredConstructor(types).newInstance(args);
 }
 Map<String,Object> common(Map<String,Object> v){
  var out=new HashMap<>(v);out.put("tenantId",id(1));out.put("createdBy",id(2));out.put("initiatorId",id(2));out.put("createdAt",now);out.put("updatedAt",now);out.put("schemaVersion","1.0.0");out.put("schemaManifest",v.get("manifest"));out.put("revision",BigInteger.ONE);out.put("status","QUEUED");
  out.put("targetKind","DOCUMENT");out.put("targetId",id(5));out.put("targetRevision",id(6));out.put("inputSha256",v.get("inputHash"));out.put("scopeManifestId",id(7));out.put("ruleManifestId",v.get("executionManifestId"));out.put("executionKind","SYSTEM_JOB");return out;
 }
 void successfulBoundaryStore()throws Exception{
  when(mapper.insertVersion(anyMap())).thenAnswer(i->{var v=capture(i);String n=(String)v.get("versionId");versions.put(n,new ArtifactRegistryProtocol.VersionRow(n,(String)v.get("projectId"),(String)v.get("versionKind"),(String)v.get("versionHash")));return 1;});
  when(artifacts.version(anyString())).thenAnswer(i->{var r=versions.get(i.getArgument(0));return r==null?List.of():List.of(r);});
  when(mapper.insertManifest(anyMap())).thenAnswer(i->{var v=common(capture(i));v.put("canonicalManifest",v.get("manifestJson"));v.put("manifestSha256",v.get("executionManifestHash"));storedRows.put("manifest",v);return 1;});
  when(mapper.manifest(anyMap())).thenAnswer(i->storedRows.containsKey("manifest")?List.of(typed(DocumentTechnicalCheckMapper.Manifest.class,storedRows.get("manifest"))):List.of());
  when(mapper.insertExecution(anyMap())).thenAnswer(i->{storedRows.put("execution",common(capture(i)));return 1;});
  when(mapper.insertJob(anyMap())).thenAnswer(i->{var v=common(capture(i));v.put("jobType","DOCUMENT_TECHNICAL_CHECK");v.put("inputManifest",v.get("inputJson"));v.put("configRevision",v.get("executionManifestId"));storedRows.put("job",v);return 1;});
  when(mapper.insertCheck(anyMap())).thenAnswer(i->{var v=common(capture(i));v.put("runId",null);v.put("ruleManifest",v.get("manifestJson"));v.put("inputWatermarks",v.get("baselineJson"));storedRows.put("check",v);return 1;});
  when(mapper.claimJob(anyMap())).thenAnswer(i->{var r=storedRows.get("job");r.put("status","RUNNING");r.put("revision",BigInteger.TWO);r.put("startedAt",now);return 1;});
  when(mapper.claimCheck(anyMap())).thenAnswer(i->{var r=storedRows.get("check");r.put("status","RUNNING");r.put("revision",BigInteger.TWO);return 1;});
  when(mapper.completeJob(anyMap())).thenAnswer(i->{var r=storedRows.get("job");var v=capture(i);r.put("status","SUCCEEDED");r.put("revision",BigInteger.valueOf(3));r.put("coverageArtifactId",v.get("coverageArtifactId"));r.put("endedAt",now);return 1;});
  when(mapper.completeCheck(anyMap())).thenAnswer(i->{var r=storedRows.get("check");var v=capture(i);r.put("status","SUCCEEDED");r.put("revision",BigInteger.valueOf(3));for(String k:List.of("coverageArtifactId","findingsArtifactId","resultRef"))r.put(k,v.get(k));return 1;});
  when(mapper.execution(anyMap())).thenAnswer(i->List.of(typed(DocumentTechnicalCheckMapper.Execution.class,nativeJson(storedRows.get("execution")))));
  when(mapper.job(anyMap())).thenAnswer(i->List.of(typed(DocumentTechnicalCheckMapper.Job.class,nativeJson(storedRows.get("job")))));
  when(mapper.check(anyMap())).thenAnswer(i->{assertEquals(id(4),capture(i).get("projectId"));return List.of(typed(DocumentTechnicalCheckMapper.Check.class,nativeJson(storedRows.get("check"))));});
  when(mapper.insertRegistration(anyMap())).thenAnswer(i->{registration=(DocumentTechnicalCheckMapper.Registration)capture(i).get("row");return 1;});
  when(mapper.registration(anyMap())).thenAnswer(i->{assertNotNull(capture(i).get("checkId"));return registration==null?List.of():List.of(registration);});
  when(mapper.completeOperation(anyMap())).thenAnswer(i->{var v=capture(i);operation=new SelectionOperationRow(operation.operationId(),operation.requestSha256(),"COMPLETED",201,pretty((String)v.get("response")),operation.targetId(),"1.0.0",operation.schemaManifestSha256());return 1;});
  when(mapper.operationById(anyMap())).thenAnswer(i->List.of(operation));
  when(permissions.saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{if("document.technical_check.started".equals(i.getArgument(3)))startAuditJson=i.getArgument(4);else auditJson=i.getArgument(4);return 1;});
  when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{if("document.technical_check.started".equals(i.getArgument(4)))startEventJson=i.getArgument(5);else eventJson=i.getArgument(5);return 1;});
  when(mapper.audit(anyMap())).thenAnswer(i->List.of(auditJson));when(mapper.event(anyMap())).thenAnswer(i->List.of(eventJson));
  when(mapper.insertArtifact(anyMap())).thenAnswer(i->{var v=capture(i);var r=(InterfaceProtocol.ObjectRef)v.get("reference");String aid=(String)v.get("artifactId");artifactRows.put(aid,new ArtifactRegistryProtocol.ArtifactRow(id(1),id(4),aid,(String)v.get("artifactKind"),(String)v.get("storedObject"),r.objectKey(),r.objectVersion(),r.sha256(),new BigInteger(r.sizeBytes()),r.mediaType(),id(8),r.sha256(),"1.0.0",(String)v.get("manifest"),"INTERNAL"));return 1;});
  when(mapper.insertBinding(anyMap())).thenAnswer(i->{var v=capture(i);String aid=(String)v.get("artifactId");bindings.put(aid,new ArtifactRegistryProtocol.BindingRow(id(1),id(4),aid,"DOCUMENT",id(5),(String)v.get("schemaRef"),(String)v.get("schemaHash"),id(8),(String)v.get("dependencyHash")));return 1;});
  when(artifacts.artifact(anyString())).thenAnswer(i->List.of(artifactRows.get(i.getArgument(0))));when(artifacts.binding(anyString())).thenAnswer(i->List.of(bindings.get(i.getArgument(0))));
  when(mapper.artifactOwner(anyMap())).thenAnswer(i->List.of(new DocumentTechnicalCheckMapper.ArtifactOwner(id(6),registration.runId())));
  when(transport.writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),anyBoolean(),eq(100000),any())).thenAnswer(i->{S3ObjectWriter.WriteIntent intent=i.getArgument(2);byte[] b=i.getArgument(3);S3ObjectReader.Admission a=i.getArgument(6);a.requireCurrent();objectBytes.put(intent.objectKey(),b.clone());return new InterfaceProtocol.ObjectRef(intent.objectKey(),"version-1",intent.sha256(),intent.sizeBytes(),intent.mediaType());});
  when(transport.readDocument(eq(subject),eq(id(1)),eq(100000),any(),any())).thenAnswer(i->{
   S3ObjectReader.Admission admission=i.getArgument(3);admission.requireCurrent();
   S3ObjectReader reader=mock(S3ObjectReader.class);S3ObjectReader.TimedAdmission timed=mock(S3ObjectReader.TimedAdmission.class);
   doAnswer(x->{admission.requireCurrent();return null;}).when(timed).requireCurrent();
   when(reader.readVerified(any(),eq(timed))).thenAnswer(x->{InterfaceProtocol.ObjectRef ref=x.getArgument(0);byte[] b=objectBytes.get(ref.objectKey());var verified=mock(S3ObjectReader.VerifiedObject.class);when(verified.reference()).thenReturn(ref);when(verified.fullSha256()).thenReturn(WireValues.sha256(b));when(verified.chunk(anyLong(),anyInt())).thenAnswer(y->Arrays.copyOfRange(b,Math.toIntExact((long)y.getArgument(0)),Math.toIntExact((long)y.getArgument(0))+(int)y.getArgument(1)));return verified;});
   SourceUploadTransport.DocumentRead<?> callback=i.getArgument(4);return callback.apply(reader,timed);
  });
 }
 @Test void successfulFinishReadIsCurrentAndWatermarkChangeMakesActualReadStale()throws Exception{
  var receipt=create();assertEquals("COMPLETE",receipt.path("technical_execution").asText());assertEquals("UNKNOWN",receipt.path("professional_coverage").asText());assertFalse(receipt.path("overall_review_ready").asBoolean());
  var read=service.read(subject,"1",receipt.required("check_id").asText());assertEquals("CURRENT",read.path("currentness").asText());assertEquals(receipt,read.required("receipt"));assertTrue(read.path("invalidation_reasons").isEmpty());
  when(proof.watermarks()).thenReturn(new DocumentReadProtocol.Watermarks(BigInteger.ONE,BigInteger.ZERO));
  var stale=service.read(subject,"1",receipt.required("check_id").asText());assertEquals("STALE",stale.path("currentness").asText());assertFalse(stale.path("invalidation_reasons").isEmpty());assertEquals(receipt,stale.required("receipt"));
  verify(mapper,times(1)).completeJob(anyMap());verify(mapper,times(2)).insertArtifact(anyMap());
 }
 @Test void completedReadRejectsCorruptAuditBeforeReadingObjectBytes()throws Exception{
  var receipt=create();auditJson="{}";clearInvocations(transport);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.read(subject,"1",receipt.required("check_id").asText())).code());
  verify(transport,never()).readDocument(any(),anyString(),anyInt(),any(),any());
 }
 @Test void processingRecoveryAfterExpiryIsReadOnly()throws Exception{
  when(transport.writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),anyBoolean(),eq(100000),any())).thenThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
  assertThrows(TenantAccessException.class,this::create);when(permissions.databaseTime()).thenReturn(now.plusSeconds(121));clearInvocations(mapper,transport);
  var state=service.recovery(subject,"1",id(5),KEY);assertEquals("PROCESSING",state.path("state").asText());assertEquals(operation.targetId(),state.path("check_id").asText());
  verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());verify(mapper,never()).completeOperation(anyMap());verify(mapper,never()).insertJob(anyMap());
 }

 void pending()throws Exception {
  when(transport.writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),anyBoolean(),eq(100000),any())).thenThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
  assertThrows(TenantAccessException.class,this::create);clearInvocations(mapper,transport);
 }
 void commandMutation(java.util.function.Consumer<ObjectNode> change)throws Exception {
  var value=(ObjectNode)schema.read(operation.responseRef());change.accept(value);
  operation=new SelectionOperationRow(operation.operationId(),operation.requestSha256(),operation.state(),null,schema.canonical(value),operation.targetId(),operation.schemaVersion(),operation.schemaManifestSha256());
 }
 @Test void changedPendingDeadlineCannotExtendOriginalWindow()throws Exception {
  pending();commandMutation(c->c.put("deadline","2026-10-18T13:00:00Z"));when(permissions.databaseTime()).thenReturn(now.plusSeconds(121));
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());verifyNoInteractions(transport);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",id(5),KEY)).code());
 }
 @Test void rewrittenPendingRequestAndBodyHashCannotReplaceOriginalRequest()throws Exception {
  pending();var c=(ObjectNode)schema.read(operation.responseRef());((ObjectNode)c.path("request")).put("reason","Different reason");c.put("body_hash",schema.hash(c.path("request")));
  operation=new SelectionOperationRow(operation.operationId(),operation.requestSha256(),operation.state(),null,schema.canonical(c),operation.targetId(),operation.schemaVersion(),operation.schemaManifestSha256());
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",id(5),KEY)).code());verifyNoInteractions(transport);
 }
 @Test void changedPendingObjectKeyCannotRedirectRecovery()throws Exception {
  pending();commandMutation(c->((ObjectNode)c.path("outputs").get(0).path("intent")).put("object_key","other/object"));
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());verifyNoInteractions(transport);
 }
 @Test void missingPendingJobAndCorruptCheckRefuseReadonlyRecovery()throws Exception {
  pending();when(mapper.job(anyMap())).thenReturn(List.of());
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",id(5),KEY)).code());verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());
 }
 @Test void pendingJobInputAndExecutionScopeMustMatchOriginal()throws Exception {
  pending();storedRows.get("job").put("inputManifest","{}");
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());verifyNoInteractions(transport);
 }
 @Test void alteredCheckAndExecutionRowsRefusePendingRecovery()throws Exception {
  for(String table:List.of("check","execution")){
   var f=new DocumentTechnicalCheckServiceTest();f.pending();f.storedRows.get(table).put(table.equals("check")?"inputSha256":"scopeManifest",table.equals("check")?"f".repeat(64):"{}");
   assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->f.service.recovery(f.subject,"1",id(5),KEY)).code());verifyNoInteractions(f.transport);
  }
 }
 @Test void pendingRecoveryRechecksCurrentPermissionAndDoesNotWrite()throws Exception {
  pending();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(resources).require(eq(admission),any(),eq("document.read"),eq("DOCUMENT_BODY"));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",id(5),KEY)).code());verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());
 }
 @Test void pendingManifestVersionMustReallyExist()throws Exception {
  pending();versions.clear();
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",id(5),KEY)).code());verifyNoInteractions(transport);
 }
 String pretty(String value)throws Exception{return schema.read(value).toPrettyString();}
 Map<String,Object> nativeJson(Map<String,Object> original)throws Exception{
  var copy=new HashMap<>(original);for(String key:List.of("ruleManifest","inputWatermarks","resultRef","inputManifest","scopeManifest"))
   if(copy.get(key) instanceof String text)copy.put(key,pretty(text));return copy;
 }
 @Test void legitimateLaterInvalidationReturnsStaleWithOriginalReceipt()throws Exception{
  var receipt=create();var check=storedRows.get("check");check.put("invalidationReason","DOCUMENT_REVISION_CHANGED");check.put("revision",BigInteger.valueOf(4));
  var read=service.read(subject,"1",receipt.required("check_id").asText());assertEquals("STALE",read.path("currentness").asText());assertEquals(receipt,read.required("receipt"));
  assertTrue(read.path("invalidation_reasons").toString().contains("CHECK_INVALIDATED"));
 }
 @Test void completedReadRequiresIndependentArtifactReadGrantBeforeBytes()throws Exception{
  var receipt=create();clearInvocations(transport);
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(resources).require(eq(admission),any(),eq("artifact.read"),eq("DOCUMENT_BODY"));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.read(subject,"1",receipt.required("check_id").asText())).code());
  verify(transport,never()).readDocument(any(),anyString(),anyInt(),any(),any());
 }

}

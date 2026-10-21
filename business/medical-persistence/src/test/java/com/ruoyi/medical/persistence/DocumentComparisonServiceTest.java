package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.mapper.DocumentComparisonMapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real service/schema/comparison engine; explicit SQL, authorization and S3 boundary doubles.
 * Does not establish real database atomicity, durable S3 recovery or actual permission grants. */
class DocumentComparisonServiceTest {
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 static final String KEY="fixed-comparison-key-001";
 final PermissionSchema json=new PermissionSchema();final DocumentComparisonSchema schema=new DocumentComparisonSchema();
 final DependencyManifestSchema dependencies=new DependencyManifestSchema();
 final EnterpriseAccess access=mock(EnterpriseAccess.class);final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
 final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
 final DocumentComparisonMapper mapper=mock(DocumentComparisonMapper.class);final DocumentReadService documents=mock(DocumentReadService.class);
 final PermissionMapper permissions=mock(PermissionMapper.class);final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
 final DependencyAccessMapper accessSets=mock(DependencyAccessMapper.class);
 final Map<String,DependencyAccessProtocol.HeaderRow> accessHeaders=new HashMap<>();
 final ArtifactRegistryMapper registry=mock(ArtifactRegistryMapper.class);final DependencyLineage lineage=mock(DependencyLineage.class);
 final SourceUploadTransport transport=mock(SourceUploadTransport.class);
 final DocumentReadService.FixedRead left=mock(DocumentReadService.FixedRead.class),right=mock(DocumentReadService.FixedRead.class);
 final LocalDateTime now=LocalDateTime.of(2026,10,18,12,0);
 final DocumentComparisonPolicy enabled=new DocumentComparisonPolicy(true,128,32,512,1000000,2000000,256,100000,20000000L,60000L,120,256,1024);
 final InterfaceProtocol.CompareRequest request=new InterfaceProtocol.CompareRequest(id(5),id(6),id(7));
 final InterfaceProtocol.DocumentBody body=new InterfaceProtocol.DocumentBody("1.0.0",id(12),List.of(new InterfaceProtocol.Node(id(20),null,0,List.of(),List.of(),new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.DOCUMENT,"Fixed document"))),List.of(),List.of(),List.of());
 final Map<String,ArtifactRegistryProtocol.VersionRow> versions=new HashMap<>();
 final Map<String,ArtifactRegistryProtocol.ManifestRow> manifests=new HashMap<>();
 final Map<String,List<ArtifactRegistryProtocol.MemberRow>> members=new HashMap<>();
 final Map<String,ArtifactRegistryProtocol.ArtifactRow> artifactRows=new HashMap<>();
 final Map<String,ArtifactRegistryProtocol.BindingRow> bindings=new HashMap<>();
 final Map<String,byte[]> objects=new HashMap<>();
 final Map<String,String> audits=new HashMap<>(),events=new HashMap<>();
 Intent intent;Registration registration;Attempt attempt;SelectionOperationRow operation;BigInteger revision=BigInteger.ONE;
 final DocumentComparisonService service;
 DocumentComparisonServiceTest()throws Exception{
  when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(admission.actorId()).thenReturn(id(2));when(admission.subject()).thenReturn(subject);when(subject.sessionId()).thenReturn(id(3));
  when(access.execute(eq(subject),any())).thenAnswer(i->{Function<EnterpriseAccess.Admission,Object> fn=i.getArgument(1);return fn.apply(admission);});
  when(permissions.databaseTime()).thenReturn(now);fixed(left,id(6),id(8));fixed(right,id(7),id(9));
  when(documents.readFixed(subject,"1",id(5),id(6))).thenReturn(left);when(documents.readFixed(subject,"1",id(5),id(7))).thenReturn(right);
  when(mapper.attempt(anyMap())).thenAnswer(i->list(attempt));
  when(mapper.insertAttempt(anyMap())).thenAnswer(i->{assertNull(attempt);attempt=(Attempt)values(i).get("row");return 1;});
  when(mapper.intentByKey(anyMap())).thenAnswer(i->{var v=values(i);return intent!=null && intent.idempotencyKey().equals(v.get("key")) && intent.createdBy().equals(v.get("userId"))?List.of(intent):List.of();});
  when(mapper.operation(anyMap())).thenAnswer(i->operation==null?List.of():List.of(operation));when(mapper.operationById(anyMap())).thenAnswer(i->operation==null?List.of():List.of(operation));when(mapper.operationRevision(anyMap())).thenAnswer(i->List.of(revision));
  when(mapper.intent(anyMap())).thenAnswer(i->intent==null?List.of():List.of(intent));when(mapper.intentByOperation(anyMap())).thenAnswer(i->intent==null?List.of():List.of(intent));when(mapper.registration(anyMap())).thenAnswer(i->registration==null?List.of():List.of(registration));
  when(mapper.startOperation(anyMap())).thenAnswer(i->{var v=values(i);operation=new SelectionOperationRow((String)v.get("operationId"),(String)v.get("requestHash"),"PROCESSING",null,pretty((String)v.get("commandJson")),(String)v.get("comparisonId"),"1.0.0",(String)v.get("manifest"));return 1;});
  when(mapper.insertIntent(anyMap())).thenAnswer(i->{intent=(Intent)values(i).get("row");return 1;});
  when(mapper.updateCommand(anyMap())).thenAnswer(i->{var v=values(i);assertEquals(revision,v.get("operationRevision"));revision=revision.add(BigInteger.ONE);operation=new SelectionOperationRow(operation.operationId(),operation.requestSha256(),"PROCESSING",null,pretty((String)v.get("commandJson")),operation.targetId(),"1.0.0",operation.schemaManifestSha256());return 1;});
  when(mapper.completeOperation(anyMap())).thenAnswer(i->{var v=values(i);assertEquals(revision,v.get("operationRevision"));revision=revision.add(BigInteger.ONE);operation=new SelectionOperationRow(operation.operationId(),operation.requestSha256(),"COMPLETED",200,pretty((String)v.get("response")),operation.targetId(),"1.0.0",operation.schemaManifestSha256());return 1;});
  when(mapper.insertRegistration(anyMap())).thenAnswer(i->{registration=(Registration)values(i).get("row");return 1;});
  when(mapper.insertVersion(anyMap())).thenAnswer(i->{var v=values(i);var n=(String)v.get("versionId");versions.put(n,new ArtifactRegistryProtocol.VersionRow(n,(String)v.get("projectId"),(String)v.get("versionKind"),(String)v.get("versionHash")));return 1;});
  when(registry.version(anyString())).thenAnswer(i->{var r=versions.get(i.getArgument(0));return r==null?List.of():List.of(r);});
  when(mapper.insertAccessSet(anyMap())).thenAnswer(i->{var v=values(i);String set=(String)v.get("accessSetId");assertNotNull(set);accessHeaders.put(set,new DependencyAccessProtocol.HeaderRow(id(1),id(4),set,(String)v.get("dependencyHash"),(String)v.get("dependencyHash"),id(2),now,(String)v.get("classification")));return 1;});
  when(accessSets.header(anyString())).thenAnswer(i->list(accessHeaders.get(i.getArgument(0))));
  when(mapper.insertAccessManifest(anyMap())).thenAnswer(i->{var v=values(i);String set=(String)v.get("accessSetId");assertNotNull(set);manifests.put(set,new ArtifactRegistryProtocol.ManifestRow(id(1),id(4),set,pretty((String)v.get("dependencyJson")),(String)v.get("dependencyHash"),(String)v.get("dependencyHash"),BigInteger.valueOf(((Number)v.get("memberCount")).longValue()),"1.0.0",(String)v.get("dependencySchema")));members.put(set,new ArrayList<>());return 1;});
  when(mapper.insertAccessMember(anyMap())).thenAnswer(i->{var v=values(i);members.get((String)v.get("accessSetId")).add((ArtifactRegistryProtocol.MemberRow)v.get("member"));return 1;});
  when(mapper.insertLineage(anyMap())).thenReturn(1);
  when(registry.manifest(anyString())).thenAnswer(i->{var r=manifests.get(i.getArgument(0));return r==null?List.of():List.of(r);});when(registry.members(anyString())).thenAnswer(i->members.getOrDefault(i.getArgument(0),List.of()));
  when(permissions.saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{audits.put(i.getArgument(3),pretty(i.getArgument(4)));return 1;});
  when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{events.put(i.getArgument(4),pretty(i.getArgument(5)));return 1;});
  when(mapper.startAudit(anyMap())).thenAnswer(i->list(audits.get("document.comparison.started")));when(mapper.finalAudit(anyMap())).thenAnswer(i->list(audits.get("document.comparison.completed")));
  when(mapper.startEvent(anyMap())).thenAnswer(i->list(events.get("document.comparison.started")));when(mapper.finalEvent(anyMap())).thenAnswer(i->list(events.get("document.comparison.completed")));
  service=service(enabled);
 }
 void fixed(DocumentReadService.FixedRead p,String rev,String set)throws Exception{
  // Both complete fixed-read proofs declare the exact published schema consumed by the comparison engine.
  var catalogue=SchemaCatalogue.fromClasspath();
  when(p.bodySchemaRef()).thenReturn(catalogue.schemaRef("interfaces","DocumentBody"));
  when(p.bodySchemaSha256()).thenReturn(catalogue.schemaDigest("interfaces"));
  String hash=schema.hash(body);when(p.header()).thenReturn(new DocumentReadProtocol.Header(id(1),id(4),id(5),rev,id(7),BigInteger.TWO,"DRAFT","ACTIVE",id(10),id(11),id(15),id(13),id(14),hash,"body/key","v1",id(16),id(17),id(12),set,BigInteger.ZERO,hash,id(2),now,"INTERNAL"));
  when(p.body()).thenReturn(body);when(p.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(4),set,List.of()));when(p.watermarks()).thenReturn(new DocumentReadProtocol.Watermarks(BigInteger.ZERO,BigInteger.ZERO));
 }
 DocumentComparisonService service(DocumentComparisonPolicy p)throws Exception{return new DocumentComparisonService(access,mapper,documents,permissions,resources,registry,accessSets,dependencies,lineage,transport,p,json);}
 @SuppressWarnings("unchecked") Map<String,Object> values(org.mockito.invocation.InvocationOnMock i){return new HashMap<>((Map<String,Object>)i.getArgument(0));}
 static <T>List<T> list(T v){return v==null?List.of():List.of(v);}
 String pretty(String s)throws Exception{return schema.read(s).toPrettyString();}
 JsonNode create()throws Exception{return service.create(subject,"1",KEY,schema.bytes(request));}
 void noWrites(){verify(mapper,never()).insertVersion(anyMap());verify(mapper,never()).startOperation(anyMap());verify(mapper,never()).insertIntent(anyMap());verify(mapper,never()).insertArtifact(anyMap());verify(mapper,never()).insertRegistration(anyMap());verifyNoInteractions(transport);}
 void unavailableTransport()throws Exception{when(transport.writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),anyBoolean(),eq(2000000),any())).thenThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));}
 @Test void originalAsciiKeyCreatesAndRecoversWithoutPathEncoding()throws Exception{
  successfulStorage();String key="original/?#key-0123456789";
  var receipt=service.create(subject,"1",key,schema.bytes(request));
  var recovery=service.recovery(subject,"1",key);assertEquals("COMPLETED",recovery.path("state").asText());assertEquals(receipt,recovery.path("comparison"));assertEquals(schema.hash(request),recovery.path("request_body_sha256").asText());
 }
 @Test void disabledAllPublicApisNeverReachStorage()throws Exception{
  var disabled=service(new DocumentComparisonPolicy(false,null,null,null,null,null,null,null,null,null,null,null,null));
  assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->disabled.create(subject,"1",KEY,schema.bytes(request))).code());
  assertThrows(TenantAccessException.class,()->disabled.read(subject,"1",id(40)));assertThrows(TenantAccessException.class,()->disabled.recovery(subject,"1",KEY));verifyNoInteractions(access,mapper,documents,transport);
 }
 @Test void malformedRequestAndStaleContextCannotWrite()throws Exception{
  assertThrows(Exception.class,()->service.create(subject,"1",KEY,"{}".getBytes()));verifyNoInteractions(access,mapper,documents);
  assertEquals(ErrorCode.CONTEXT_CHANGED,assertThrows(TenantAccessException.class,()->service.create(subject,"2",KEY,schema.bytes(request))).code());noWrites();
 }
 @Test void sourceDenialOnEachSidePreventsWrites()throws Exception{
  when(documents.readFixed(subject,"1",id(5),id(6))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::create).code());noWrites();
  doReturn(left).when(documents).readFixed(subject,"1",id(5),id(6));when(documents.readFixed(subject,"1",id(5),id(7))).thenThrow(new TenantAccessException(ErrorCode.SOURCE_NOT_READY));assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,this::create).code());noWrites();
 }
 @Test void revokedCurrentProofPreventsIntentAndObjectStorage()throws Exception{
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(documents).requireCurrent(admission,right);assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::create).code());noWrites();
 }
 @Test void conflictingRightsForSameUnionMemberCannotCreateIntent()throws Exception{
  when(left.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(4),id(8),List.of(new ArtifactRegistryProtocol.MemberRow(id(50),id(51)))));
  when(right.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(4),id(9),List.of(new ArtifactRegistryProtocol.MemberRow(id(50),id(52)))));
  assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,this::create).code());noWrites();
 }
 @Test void unknownWriteRecoveryIsReadOnlyAndRetryIsRecoveryOnly()throws Exception{
  unavailableTransport();assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());assertNotNull(intent);
  assertTrue(schema.read(operation.responseRef()).path("attempted").asBoolean());clearInvocations(mapper,transport);
  var result=service.recovery(subject,"1",KEY);assertEquals("PROCESSING",result.path("state").asText());verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());
  assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());verify(transport,times(1)).writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),eq(true),eq(2000000),any());verify(mapper,never()).insertIntent(anyMap());
 }
 @Test void changedRequestCannotReuseOriginalKey()throws Exception{
  unavailableTransport();assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());clearInvocations(mapper,transport);
  var changed=new InterfaceProtocol.CompareRequest(id(5),id(7),id(6));assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.create(subject,"1",KEY,schema.bytes(changed))).code());verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());
 }
 @SuppressWarnings("unchecked") <T>T changed(T record,String field,Object value)throws Exception{
  var type=record.getClass();var parts=type.getRecordComponents();Class<?>[] types=new Class<?>[parts.length];Object[] args=new Object[parts.length];
  for(int n=0;n<parts.length;n++){types[n]=parts[n].getType();args[n]=parts[n].getName().equals(field)?value:parts[n].getAccessor().invoke(record);}
  return (T)type.getDeclaredConstructor(types).newInstance(args);
 }
 @Test void alteredDeadlineCannotBeRecoveredFromOperationJsonAlone()throws Exception{
  unavailableTransport();assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());intent=changed(intent,"deadline",intent.deadline().plusSeconds(3600));clearInvocations(transport,mapper);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());
 }
 @Test void corruptedStartAuditAndRegistryManifestBothBlockRecovery()throws Exception{
  unavailableTransport();assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());String original=audits.put("document.comparison.started","{}");clearInvocations(transport,mapper);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());audits.put("document.comparison.started",original);
  var version=versions.get(intent.manifestId());versions.put(intent.manifestId(),new ArtifactRegistryProtocol.VersionRow(version.id(),version.projectId(),version.entityKind(),"f".repeat(64)));
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());
 }
 @Test void elapsedDeadlineAllowsReadonlyInspectionButNoStorageRetry()throws Exception{
  unavailableTransport();assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());when(permissions.databaseTime()).thenReturn(now.plusSeconds(121));clearInvocations(transport,mapper);
  assertEquals("PROCESSING",service.recovery(subject,"1",KEY).path("state").asText());
  assertEquals(ErrorCode.ACTION_STALE,assertThrows(TenantAccessException.class,this::create).code());verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());
 }

 void successfulStorage()throws Exception{
  when(mapper.insertArtifact(anyMap())).thenAnswer(i->{var v=values(i);var r=(InterfaceProtocol.ObjectRef)v.get("reference");String aid=(String)v.get("artifactId");assertNotNull(v.get("rightRevision"));artifactRows.put(aid,new ArtifactRegistryProtocol.ArtifactRow(id(1),id(4),aid,"DOCUMENT_COMPARISON",(String)v.get("storedObject"),r.objectKey(),r.objectVersion(),r.sha256(),new BigInteger(r.sizeBytes()),r.mediaType(),(String)v.get("accessSetId"),r.sha256(),"1.0.0",(String)v.get("manifest"),(String)v.get("classification")));return 1;});
  when(mapper.insertBinding(anyMap())).thenAnswer(i->{var v=values(i);String aid=(String)v.get("artifactId");bindings.put(aid,new ArtifactRegistryProtocol.BindingRow(id(1),id(4),aid,"DOCUMENT",id(5),(String)v.get("schemaRef"),(String)v.get("schemaHash"),(String)v.get("accessSetId"),(String)v.get("dependencyHash")));return 1;});
  when(registry.artifact(anyString())).thenAnswer(i->list(artifactRows.get(i.getArgument(0))));when(registry.binding(anyString())).thenAnswer(i->list(bindings.get(i.getArgument(0))));
  when(mapper.artifactOwner(anyMap())).thenAnswer(i->List.of(new ArtifactOwner(intent.rightRevision(),null)));
  when(transport.writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),anyBoolean(),eq(2000000),any())).thenAnswer(i->{S3ObjectWriter.WriteIntent wi=i.getArgument(2);byte[] bytes=i.getArgument(3);S3ObjectReader.Admission a=i.getArgument(6);a.requireCurrent();assertEquals(wi.sha256(),WireValues.sha256(bytes));objects.put(wi.objectKey(),bytes.clone());return new InterfaceProtocol.ObjectRef(wi.objectKey(),"real-test-version",wi.sha256(),wi.sizeBytes(),wi.mediaType());});
  when(transport.readDocument(eq(subject),eq(id(1)),eq(2000000),any(),any())).thenAnswer(i->{
   S3ObjectReader.Admission a=i.getArgument(3);a.requireCurrent();var reader=mock(S3ObjectReader.class);var timed=mock(S3ObjectReader.TimedAdmission.class);
   doAnswer(z->{a.requireCurrent();return null;}).when(timed).requireCurrent();
   when(reader.readVerified(any(),eq(timed))).thenAnswer(z->{InterfaceProtocol.ObjectRef ref=z.getArgument(0);byte[] bytes=objects.get(ref.objectKey());var actual=mock(S3ObjectReader.VerifiedObject.class);when(actual.reference()).thenReturn(ref);when(actual.fullSha256()).thenReturn(WireValues.sha256(bytes));when(actual.chunk(anyLong(),anyInt())).thenAnswer(c->Arrays.copyOfRange(bytes,Math.toIntExact((long)c.getArgument(0)),Math.toIntExact((long)c.getArgument(0))+(int)c.getArgument(1)));return actual;});
   SourceUploadTransport.DocumentRead<?> callback=i.getArgument(4);return callback.apply(reader,timed);
  });
 }
 @Test void successfulLifecycleReadsActualCompleteOutputAndKeepsHeadSeparate()throws Exception{
  successfulStorage();var receipt=create();assertEquals("1",receipt.path("comparison_revision").asText());assertNotNull(intent);assertNotNull(registration);assertEquals("COMPLETED",operation.state());
  assertEquals(intent.coreSha256(),receipt.path("differences").path("sha256").asText());
  var view=service.read(subject,"1",intent.comparisonId());assertEquals(receipt,view.required("comparison"));assertEquals("NOT_PERFORMED",view.at("/result/semantic_assessment").asText());assertFalse(view.path("left_is_current").asBoolean());assertTrue(view.path("right_is_current").asBoolean());
  var newHead=changed(right.header(),"currentDocumentRevision",id(77));newHead=changed(newHead,"revision",BigInteger.valueOf(3));when(right.header()).thenReturn(newHead);
  var leftHead=changed(left.header(),"currentDocumentRevision",id(77));leftHead=changed(leftHead,"revision",BigInteger.valueOf(3));when(left.header()).thenReturn(leftHead);
  var later=service.read(subject,"1",intent.comparisonId());assertEquals(receipt,later.required("comparison"));assertFalse(later.path("right_is_current").asBoolean());assertEquals("3",later.path("document_head_revision").asText());
  verify(mapper,times(1)).insertIntent(anyMap());verify(mapper,times(1)).insertArtifact(anyMap());verify(mapper,times(1)).completeOperation(anyMap());
 }
 @Test void completedReadNeedsIndependentArtifactGrantBeforeByteRead()throws Exception{
  successfulStorage();create();clearInvocations(transport);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(resources).require(eq(admission),any(),eq("artifact.read"),eq("DOCUMENT_BODY"));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.read(subject,"1",intent.comparisonId())).code());verifyNoInteractions(transport);
 }

 @Test void deterministicCoreCanonicalBytesSurviveJsonNumberTypeRoundtrip()throws Exception{
  var engine=new DocumentStructuralComparisonEngine(schema.core);var a=engine.compare(request,body,body,enabled.limits());var b=schema.read(schema.canonical(a));
  assertEquals(schema.canonical(a),schema.canonical(b));assertEquals(schema.hash(a),schema.hash(b));
 }

 @Test void unionPreservesBothRealDependencyMembersAndDeduplicatesMatchingRights()throws Exception{
  var a=new ArtifactRegistryProtocol.MemberRow(id(50),id(51));var b=new ArtifactRegistryProtocol.MemberRow(id(60),id(61));
  when(left.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(4),id(8),List.of(a)));
  when(right.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(4),id(9),List.of(a,b)));
  unavailableTransport();assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());
  assertEquals(List.of(a,b),members.get(intent.accessSetId()));verify(mapper,times(2)).insertAccessMember(anyMap());
  var saved=dependencies.verify(manifests.get(intent.accessSetId()),members.get(intent.accessSetId()));assertEquals(List.of(a,b),saved.members());
 }
 @Test void missingRealAccessSetHeaderBlocksRecoveryDespiteIntactOperationJson()throws Exception{
  unavailableTransport();assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::create).code());accessHeaders.remove(intent.accessSetId());clearInvocations(mapper,transport);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());verifyNoInteractions(transport);verify(mapper,never()).updateCommand(anyMap());
 }

 @Test void immutableAttemptRejectsDowngradeEvenWhenMutableRevisionAlsoRollsBack()throws Exception{
  unavailableTransport();assertThrows(TenantAccessException.class,this::create);assertNotNull(attempt);
  operation=changed(operation,"responseRef",schema.canonical(new DocumentComparisonProtocol.Command(schema.hash(intent),false,null)));revision=BigInteger.ONE;clearInvocations(transport,mapper);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());verifyNoInteractions(transport);verify(mapper,never()).insertAttempt(anyMap());
 }
 @Test void attemptedCommandCannotReplaceMissingImmutableAttempt()throws Exception{
  unavailableTransport();assertThrows(TenantAccessException.class,this::create);assertNotNull(attempt);attempt=null;clearInvocations(transport,mapper);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());verifyNoInteractions(transport);
 }
 @Test void attemptAfterImmutableDeadlineFailsReadonlyRecovery()throws Exception{
  unavailableTransport();assertThrows(TenantAccessException.class,this::create);attempt=changed(attempt,"createdAt",intent.deadline().plusSeconds(1));clearInvocations(transport,mapper);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());verifyNoInteractions(transport);
 }
 @Test void fabricatedSavedObjectVersionRequiresRecoveryAndCannotRegister()throws Exception{
  unavailableTransport();assertThrows(TenantAccessException.class,this::create);
  var call=mockingDetails(transport).getInvocations().stream().filter(x->x.getMethod().getName().equals("writeDocumentProjection")).findFirst().orElseThrow();S3ObjectWriter.WriteIntent wi=call.getArgument(2);
  var fake=new InterfaceProtocol.ObjectRef(wi.objectKey(),"fabricated-version",wi.sha256(),wi.sizeBytes(),wi.mediaType());operation=changed(operation,"responseRef",schema.canonical(new DocumentComparisonProtocol.Command(schema.hash(intent),true,fake)));revision=BigInteger.valueOf(3);
  successfulStorage();clearInvocations(transport,mapper);
  assertEquals("PROCESSING",service.recovery(subject,"1",KEY).path("state").asText());verifyNoInteractions(transport);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());verify(transport).writeDocumentProjection(eq(subject),eq(id(1)),any(),any(),eq(true),eq(2000000),any());verify(mapper,never()).insertArtifact(anyMap());assertNull(registration);
 }
 @Test void referenceCorruptedAfterObjectVerificationCannotBeConsumedByFinish()throws Exception{
  successfulStorage();doAnswer(i->{var v=values(i);assertEquals(revision,v.get("operationRevision"));revision=revision.add(BigInteger.ONE);var command=schema.decode((String)v.get("commandJson"),DocumentComparisonProtocol.Command.class);if(command.reference()!=null){var r=command.reference();command=new DocumentComparisonProtocol.Command(command.intentSha256(),true,new InterfaceProtocol.ObjectRef(r.objectKey(),"swapped-version",r.sha256(),r.sizeBytes(),r.mediaType()));}operation=new SelectionOperationRow(operation.operationId(),operation.requestSha256(),"PROCESSING",null,schema.canonical(command),operation.targetId(),"1.0.0",operation.schemaManifestSha256());return 1;}).when(mapper).updateCommand(anyMap());
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());verify(mapper,never()).insertArtifact(anyMap());assertNull(registration);
 }

 @Test void immutableOriginalKeyPreventsNewCreateWhenMutableOperationDisappears()throws Exception{
  unavailableTransport();assertThrows(TenantAccessException.class,this::create);assertNotNull(intent);operation=null;clearInvocations(mapper,transport);
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());verifyNoInteractions(transport);verify(mapper,never()).startOperation(anyMap());verify(mapper,never()).insertIntent(anyMap());
 }
 @Test void changedMutableOperationKeyCannotHideImmutableOriginal()throws Exception{
  unavailableTransport();assertThrows(TenantAccessException.class,this::create);assertNotNull(intent);
  doReturn(List.of()).when(mapper).operation(argThat(v->KEY.equals(v.get("key"))));clearInvocations(mapper,transport);
  String moved="moved-comparison-key-002";assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",moved)).code());assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.create(subject,"1",moved,schema.bytes(request))).code());
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.recovery(subject,"1",KEY)).code());assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::create).code());verifyNoInteractions(transport);verify(mapper,never()).insertIntent(anyMap());
 }

}

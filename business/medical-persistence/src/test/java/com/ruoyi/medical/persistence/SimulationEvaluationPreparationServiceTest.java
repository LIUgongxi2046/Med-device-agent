package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.SimulationEvaluationPreparationProtocol.Registration;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit in-memory ledger/transport doubles; this is not a database or remote storage acceptance test. */
class SimulationEvaluationPreparationServiceTest {
 static String id(int n){return EvaluationFixture.id(n);} static final String KEY="prepare-register-key-001",H="a".repeat(64);
 final PermissionSchema json=new PermissionSchema();final EvaluationSchema evaluation=new EvaluationSchema(json);
 final EnterpriseAccess access=mock(EnterpriseAccess.class);final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);
 final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
 final SimulationEvaluationPreparationMapper mapper=mock(SimulationEvaluationPreparationMapper.class);
 final SimulationFixtureMapper fixtures=mock(SimulationFixtureMapper.class);final SourceReadMapper sourceRows=mock(SourceReadMapper.class);
 final SourceReadAuthority sourceAuthority=mock(SourceReadAuthority.class);final SourceUploadTransport transport=mock(SourceUploadTransport.class);
 final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);final PermissionMapper permissions=mock(PermissionMapper.class);
 final ArtifactRegistryMapper artifacts=mock(ArtifactRegistryMapper.class);final EvaluationReferences references=mock(EvaluationReferences.class);
 final DependencyLineage lineage=mock(DependencyLineage.class);final DependencyManifestSchema manifests=new DependencyManifestSchema();
 final SimulationAuthorizedUseEvidenceAuthority evidence=mock(SimulationAuthorizedUseEvidenceAuthority.class);
 final SimulationEvaluationPreparationService service;
 final JsonNode document=evaluation.expectedItems(EvaluationFixture.sample(100,"SIMULATION","DEVELOPMENT"));
 byte[] bytes=ContentReadAuthorization.canonicalBytes(document);String hash=WireValues.sha256(bytes);
 final com.fasterxml.jackson.databind.node.ObjectNode request=json.object().put("schema_version","1.0.0").putNull("project_id").put("kind","EXPECTED_ITEMS")
  .put("source_version",id(5)).put("rights_revision",id(6));
 final Map<String,ArtifactRegistryProtocol.VersionRow> versions=new HashMap<>();final List<ArtifactRegistryProtocol.MemberRow> members=new ArrayList<>();
 Registration registration;SelectionOperationRow operation;ArtifactRegistryProtocol.ArtifactRow artifact;ArtifactRegistryProtocol.BindingRow binding;ArtifactRegistryProtocol.ManifestRow manifest;
 String audit,event;SimulationInvocationSources.Original original;
 SimulationEvaluationPreparationServiceTest()throws Exception {
  request.put("document_sha256",hash);when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(a.actorId()).thenReturn(id(2));when(subject.sessionId()).thenReturn(id(3));
  when(access.execute(eq(subject),any())).thenAnswer(i->{Function<EnterpriseAccess.Admission,Object> f=i.getArgument(1);return f.apply(a);});
  original=new SimulationInvocationSources.Original(id(1),id(5),hash,"tenants/"+id(1)+"/preparation","v1",BigInteger.valueOf(bytes.length),"application/json");
  var source=source(id(5),id(6),hash);var right=new SourceReadProtocol.RightsRow(id(1),id(6),id(5),"SIMULATION_EVALUATION","[\"READ\"]","[]","[]",null,null,"APPROVED","{}",H,"1.0.0",H,"INTERNAL",H,null);
  when(sourceAuthority.require(eq(a),any())).thenReturn(new SourceReadProtocol.VerifiedSource(source,right,null,null,null,null,null));
  when(sourceRows.source(id(5))).thenReturn(List.of(source));when(fixtures.original(anyMap())).thenAnswer(i->List.of(original));
  when(sourceRows.gates(id(5))).thenReturn(List.of(new SourceReadProtocol.GateRow(null,"RECEIVED","APPROVED","ACTIVE",BigInteger.ONE)));
  graph(id(5),hash,null,"SOURCE",null);
  var input=EvaluationFixture.sample(100,"SIMULATION","DEVELOPMENT").input_ref();
  when(sourceRows.source(input.version_id())).thenReturn(List.of());when(sourceRows.source(id(8))).thenReturn(List.of(source(id(8),id(7),H)));when(sourceRows.gates(id(8))).thenReturn(List.of(new SourceReadProtocol.GateRow(null,"RECEIVED","APPROVED","ACTIVE",BigInteger.ONE)));graph(input.version_id(),H,null,"ARTIFACT",id(8));
  when(artifacts.artifact(input.version_id())).thenReturn(List.of(new ArtifactRegistryProtocol.ArtifactRow(id(1),null,input.version_id(),"SIMULATION_EXACT_INPUT","unused","key","v",H,BigInteger.ONE,"application/json",id(9),H,"1.0.0",H,"INTERNAL")));
  when(mapper.lockSource(anyMap())).thenReturn(List.of(id(5)));when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,0,0));
  when(transport.readDocument(eq(subject),eq(id(1)),anyInt(),any(),any())).thenAnswer(inv->{
   S3ObjectReader.Admission guard=inv.getArgument(3);guard.requireCurrent();var timed=mock(S3ObjectReader.TimedAdmission.class);doAnswer(i->{guard.requireCurrent();return null;}).when(timed).requireCurrent();
   var reader=mock(S3ObjectReader.class);var object=mock(S3ObjectReader.VerifiedObject.class);when(reader.readVerified(any(),any())).thenReturn(object);
   when(object.reference()).thenAnswer(i->original.reference());when(object.fullSha256()).thenAnswer(i->original.sha256());
   when(object.chunk(anyLong(),anyInt())).thenAnswer(i->Arrays.copyOfRange(bytes,((Long)i.getArgument(0)).intValue(),((Long)i.getArgument(0)).intValue()+(Integer)i.getArgument(1)));
   SourceUploadTransport.DocumentRead<Object> read=inv.getArgument(4);return read.apply(reader,timed);
  });
  when(fixtures.insertContextVersion(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);versions.put((String)v.get("versionId"),new ArtifactRegistryProtocol.VersionRow((String)v.get("versionId"),null,(String)v.get("kind"),(String)v.get("hash")));return 1;});
  when(fixtures.insertContextAccessSet(anyMap())).thenReturn(1);when(fixtures.insertContextDependencyManifest(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);manifest=new ArtifactRegistryProtocol.ManifestRow(id(1),null,(String)v.get("accessId"),(String)v.get("dependencyJson"),(String)v.get("hash"),(String)v.get("hash"),BigInteger.valueOf((Integer)v.get("memberCount")),"1.0.0",(String)v.get("dependencySchema"));return 1;});
  when(fixtures.insertContextDependencyMember(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);members.add((ArtifactRegistryProtocol.MemberRow)v.get("member"));return 1;});when(fixtures.insertContextLineage(anyMap())).thenReturn(1);
  when(mapper.insertArtifact(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);var ref=(InterfaceProtocol.ObjectRef)v.get("reference");artifact=new ArtifactRegistryProtocol.ArtifactRow(id(1),null,(String)v.get("artifactId"),(String)v.get("artifactKind"),(String)v.get("storedObject"),ref.objectKey(),ref.objectVersion(),ref.sha256(),new BigInteger(ref.sizeBytes()),ref.mediaType(),(String)v.get("accessId"),ref.sha256(),"1.0.0",(String)v.get("schemaHash"),(String)v.get("classification"));return 1;});
  when(mapper.insertBinding(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);binding=new ArtifactRegistryProtocol.BindingRow(id(1),null,(String)v.get("artifactId"),"SOURCE",(String)v.get("sourceId"),(String)v.get("schemaRef"),(String)v.get("schemaHash"),(String)v.get("accessId"),(String)v.get("dependencyHash"));return 1;});
  when(mapper.insertRegistration(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);registration=(Registration)v.get("row");return 1;});
  when(mapper.registration(anyMap())).thenAnswer(i->registration==null?List.of():List.of(registration));when(mapper.operation(anyMap())).thenAnswer(i->operation==null?List.of():List.of(operation));
  when(mapper.operationById(anyMap())).thenAnswer(i->operation==null?List.of():List.of(operation));
  when(mapper.saveOperation(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);operation=new SelectionOperationRow((String)v.get("operationId"),(String)v.get("requestHash"),"COMPLETED",201,(String)v.get("response"),(String)v.get("artifactId"),"1.0.0",(String)v.get("operationManifest"));return 1;});
  when(artifacts.artifact(argThat(v->v!=null&&!v.equals(input.version_id())))).thenAnswer(i->artifact==null?List.of():List.of(artifact));when(artifacts.binding(anyString())).thenAnswer(i->List.of(binding));when(artifacts.manifest(anyString())).thenAnswer(i->List.of(manifest));when(artifacts.members(anyString())).thenAnswer(i->List.copyOf(members));
  when(permissions.saveAudit(anyString(),isNull(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{audit=i.getArgument(4);return 1;});
  when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{event=i.getArgument(5);return 1;});
  when(mapper.audit(anyMap())).thenAnswer(i->List.of(audit));when(mapper.event(anyMap())).thenAnswer(i->List.of(event));
  service=new SimulationEvaluationPreparationService(access,mapper,fixtures,sourceRows,sourceAuthority,transport,resources,permissions,artifacts,references,lineage,manifests,json,evaluation,new RetrievalPolicy("TEST","local",10),evidence);
 }
 SourceReadProtocol.SourceRow source(String version,String rights,String digest){return new SourceReadProtocol.SourceRow(id(1),id(4),version,null,"ENTERPRISE","INTERNAL",rights,digest,digest,null);}
 void graph(String root,String digest,String project,String kind,String parent){var g=new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);g.addVertex(root);var versions=new HashMap<String,ArtifactRegistryProtocol.VersionRow>();versions.put(root,new ArtifactRegistryProtocol.VersionRow(root,project,kind,digest));if(parent!=null){g.addVertex(parent);g.addEdge(root,parent,new SourceReadProtocol.ParentRow(root,parent,"TEST"));versions.put(parent,new ArtifactRegistryProtocol.VersionRow(parent,project,"SOURCE",H));}when(lineage.inspect(eq(root),any())).thenReturn(new DependencyLineage.VerifiedLineage(root,g,versions));}
 JsonNode register()throws Exception{return service.register(subject,"1",KEY,request);}
 JsonNode preview(String kind,JsonNode spec)throws Exception{var r=json.object().put("schema_version","1.0.0").putNull("project_id").put("kind",kind);r.set("specification",spec);return service.preview(subject,"1",r);}
 @Test void canonicalPreviewIsNotAnApprovalOrRegistrationAndChecksRealInputReference()throws Exception {
  var result=preview("EXPECTED_ITEMS",document);assertEquals(hash,result.required("document_sha256").textValue());assertFalse(result.required("registered").booleanValue());assertFalse(result.required("approved").booleanValue());
  verify(references).artifact(isNull(),eq(EvaluationFixture.sample(100,"SIMULATION","DEVELOPMENT").input_ref()),eq(SimulationInputArtifactProfile.profile().schemaRef()));verifyNoInteractions(mapper,transport);
 }
 @Test void registerReplayAndTypedReadPreserveObjectAndUseCommonOperationLedger()throws Exception {
  var receipt=register();assertEquals(receipt,register());var read=service.read(subject,"1",registration.artifactId());assertEquals(receipt,read.required("receipt"));assertEquals(document,read.required("document"));
  assertEquals(original.reference(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));assertEquals("SOURCE",binding.resourceKind());assertEquals(id(4),binding.resourceId());
  assertEquals(receipt,new com.fasterxml.jackson.databind.ObjectMapper().readTree(operation.responseRef()));verify(mapper,times(1)).saveOperation(anyMap());verify(mapper,times(1)).insertArtifact(anyMap());
  verify(resources,atLeastOnce()).require(eq(a),eq(new PermissionProtocol.Scope(id(1),null,"SOURCE",id(4))),eq("artifact.register"),eq("SIMULATION_EVALUATION"));
 }
 @Test void readRequiresCurrentReadAuthorityWithoutProducerGrant()throws Exception {
  register();clearInvocations(resources);service.read(subject,"1",registration.artifactId());verify(resources,never()).require(any(),any(),eq("artifact.register"),anyString());
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(references).require(eq(a),isNull(),anyList(),eq("SIMULATION_EVALUATION"));assertThrows(Exception.class,()->service.read(subject,"1",registration.artifactId()));
 }
 @Test void nonCanonicalOriginalIsNotSilentlyRewritten()throws Exception {
  bytes=(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  // Keep a valid original SHA/size and current source binding; only canonical-byte requirement fails.
  String changed=WireValues.sha256(bytes);request.put("document_sha256",changed);original=new SimulationInvocationSources.Original(id(1),id(5),changed,original.objectKey(),original.objectVersion(),BigInteger.valueOf(bytes.length),"text/plain");
  var src=source(id(5),id(6),changed);var old=sourceAuthority.require(a,new SourceReadProtocol.ReadRequirement(id(5),id(6),null,null,null,null,SourceReadProtocol.Use.REVIEW,"SIMULATION_EVALUATION","TEST","local",null));
  when(sourceAuthority.require(eq(a),any())).thenReturn(new SourceReadProtocol.VerifiedSource(src,old.rights(),null,null,null,null,null));when(sourceRows.source(id(5))).thenReturn(List.of(src));graph(id(5),changed,null,"SOURCE",null);
  assertThrows(TenantAccessException.class,this::register);verify(mapper,never()).insertArtifact(anyMap());
 }
 @Test void projectAncestorAndMissingRegisterGrantFailBeforeStorageRead()throws Exception {
  graph(id(5),hash,id(99),"SOURCE",null);assertThrows(TenantAccessException.class,this::register);verifyNoInteractions(transport);
  graph(id(5),hash,null,"SOURCE",null);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(resources).require(eq(a),any(),eq("artifact.register"),anyString());assertThrows(TenantAccessException.class,this::register);verifyNoInteractions(transport);
 }
 @Test void alteredReceiptOrAuditCannotRecoverARegistration()throws Exception {
  register();event=event.replace("\"registered\":true","\"registered\":false");assertThrows(TenantAccessException.class,()->service.read(subject,"1",registration.artifactId()));
 }
 @Test void mixedOrEmptySamplesAreRejectedAndExpectedDigestCannotBeSubstituted()throws Exception {
  assertThrows(SchemaRejection.class,()->preview("SAMPLE_MANIFEST",json.tree(List.of())));
  assertThrows(SchemaRejection.class,()->preview("SAMPLE_MANIFEST",json.tree(List.of(EvaluationFixture.sample(100,"REAL","DEVELOPMENT")))));
  assertThrows(TenantAccessException.class,()->preview("SAMPLE_MANIFEST",json.tree(List.of(EvaluationFixture.sample(100,"SIMULATION","DEVELOPMENT")))));
 }
 @Test void authorizationPreviewAndRegistrationValidationDelegateToRealEvidenceAuthority()throws Exception {
  when(evidence.preview(eq(a),any())).thenReturn(document);when(evidence.verifySourceDocument(eq(a),eq(document))).thenReturn(List.of(EvaluationFixture.sample(100,"SIMULATION","DEVELOPMENT").input_ref()));
  preview("AUTHORIZED_USE_EVIDENCE",json.object());verify(evidence).preview(eq(a),any());verify(evidence).verifySourceDocument(a,document);
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(evidence).verifySourceDocument(eq(a),any());assertThrows(TenantAccessException.class,()->preview("AUTHORIZED_USE_EVIDENCE",json.object()));
 }
 @Test void operationRequestDigestTamperingCannotRecoverOrReplay()throws Exception {
  register();operation=new SelectionOperationRow(operation.operationId(),"f".repeat(64),operation.state(),operation.httpStatus(),operation.responseRef(),operation.targetId(),operation.schemaVersion(),operation.schemaManifestSha256());
  assertThrows(TenantAccessException.class,()->service.read(subject,"1",registration.artifactId()));
  assertThrows(TenantAccessException.class,this::register);verify(mapper,times(1)).insertArtifact(anyMap());
 }
 @Test void currentSourceAuthorityIsRepeatedInsideTheActualReadGuard()throws Exception {
  var checked=sourceAuthority.require(a,new SourceReadProtocol.ReadRequirement(id(5),id(6),null,null,null,null,SourceReadProtocol.Use.REVIEW,"SIMULATION_EVALUATION","TEST","local",null));
  when(sourceAuthority.require(eq(a),any())).thenReturn(checked).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
  assertThrows(TenantAccessException.class,this::register);verify(mapper,never()).insertArtifact(anyMap());verify(mapper,never()).saveOperation(anyMap());
 }

 @Test void approvedCanonicalUtf8TxtUsesExistingUploadMimeWithoutRewriting()throws Exception {
  original=new SimulationInvocationSources.Original(original.tenantId(),original.sourceVersion(),original.sha256(),original.objectKey(),original.objectVersion(),original.sizeBytes(),"text/plain");
  var receipt=register();assertEquals(hash,receipt.required("artifact").required("content_sha256").textValue());assertEquals("text/plain",artifact.mediaType());
  assertEquals(original.reference(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));assertArrayEquals(ContentReadAuthorization.canonicalBytes(document),bytes);
 }
 @Test void plainTextWithoutJsonIsRejectedEvenWithCorrectOriginalHashAndRights()throws Exception {
  bytes="This is not JSON".getBytes(java.nio.charset.StandardCharsets.UTF_8);String changed=WireValues.sha256(bytes);request.put("document_sha256",changed);
  original=new SimulationInvocationSources.Original(id(1),id(5),changed,original.objectKey(),original.objectVersion(),BigInteger.valueOf(bytes.length),"text/plain");
  var src=source(id(5),id(6),changed);var old=sourceAuthority.require(a,new SourceReadProtocol.ReadRequirement(id(5),id(6),null,null,null,null,SourceReadProtocol.Use.REVIEW,"SIMULATION_EVALUATION","TEST","local",null));
  when(sourceAuthority.require(eq(a),any())).thenReturn(new SourceReadProtocol.VerifiedSource(src,old.rights(),null,null,null,null,null));when(sourceRows.source(id(5))).thenReturn(List.of(src));graph(id(5),changed,null,"SOURCE",null);
  assertThrows(SchemaRejection.class,this::register);verify(mapper,never()).insertArtifact(anyMap());verify(mapper,never()).saveOperation(anyMap());
 }

}

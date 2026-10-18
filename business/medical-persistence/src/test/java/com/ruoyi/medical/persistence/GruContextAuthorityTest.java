package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Actual permission evaluator with an explicitly synthetic in-memory grant ledger. */
class GruContextAuthorityTest {
 static String id(int n){return GruServiceTest.id(n);}
 final PermissionSchema json=new PermissionSchema();final PermissionMapper mapper=mock(PermissionMapper.class);
 final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);
 final Map<String,List<PermissionProtocol.GrantRow>> grants=new HashMap<>();
 final ResourceReadAuthority resources=new ResourceReadAuthority(mapper,new PermissionAuthority(json),json);
 final GruContextAuthority authority;final com.fasterxml.jackson.databind.JsonNode request;
 final LocalDateTime now=LocalDateTime.of(2026,10,18,0,0);
 GruContextAuthorityTest()throws Exception{
  when(a.actorId()).thenReturn(id(2));when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(a.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",id(2),null,null));
  when(mapper.databaseTime()).thenReturn(now);when(mapper.resourceOwner("PROJECT",id(4))).thenReturn(List.of(new PermissionProtocol.OwnerRow(id(4),id(4))));when(mapper.resourceOwner("DOCUMENT",id(5))).thenReturn(List.of(new PermissionProtocol.OwnerRow(id(5),id(4))));
  when(mapper.resourceGrants(eq(id(2)),anyString())).thenAnswer(i->grants.getOrDefault(i.getArgument(1),List.of()));
  authority=new GruContextAuthority(mock(DocumentReadService.class),resources,mock(TaskMapper.class),mock(ScopeSnapshotSchema.class),mock(SourceReadAuthority.class),mock(CollectionAuthority.class),mock(DocumentUploadPolicy.class));
  request=json.object().put("project_id",id(4)).put("document_id",id(5));
  grant("project.read","PROJECT",id(4),"DOCUMENT_BODY");grant("document.read","DOCUMENT",id(5),"DOCUMENT_BODY");
 }
 void grant(String action,String kind,String resource,String purpose)throws Exception{
  String condition=json.canonicalObject(new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of(purpose),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),List.of(),List.of())));
  grants.put(action,List.of(new PermissionProtocol.GrantRow(id(20),id(2),id(4),kind,resource,action,condition,"ACTIVE",now.minusSeconds(1),null,id(21),"1.0.0",json.manifestSha256(),BigInteger.ONE)));
 }
 interface Checked {void run()throws Exception;}
 void scoped(Checked call)throws Exception{
  try(var scope=TenantScope.open(new TenantScope.Identity(id(1),id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
   TransactionSynchronizationManager.setActualTransactionActive(true);try{scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));call.run();}finally{scope.finishTransaction();TransactionSynchronizationManager.setActualTransactionActive(false);}
  }
 }
 @Test void documentReadCannotReplaceConversationReadOrUse()throws Exception{
  assertThrows(TenantAccessException.class,()->scoped(()->authority.authorize(a,"1",request,false)));
  grant("conversation.read","DOCUMENT",id(5),"DOCUMENT_BODY");scoped(()->authority.authorize(a,"1",request,false));
  assertThrows(TenantAccessException.class,()->scoped(()->authority.authorize(a,"1",request,true)));
  grant("conversation.use","DOCUMENT",id(5),"DOCUMENT_BODY");scoped(()->authority.authorize(a,"1",request,true));
 }
 @Test void wrongPurposeAndOtherDocumentDoNotAuthorizeConversation()throws Exception{
  grant("conversation.read","DOCUMENT",id(5),"TASK_CREATION");assertThrows(TenantAccessException.class,()->scoped(()->authority.authorize(a,"1",request,false)));
  grant("conversation.read","DOCUMENT",id(99),"DOCUMENT_BODY");assertThrows(TenantAccessException.class,()->scoped(()->authority.authorize(a,"1",request,false)));
 }
 @Test void projectPermissionAndCurrentContextRemainRequired()throws Exception{
  grant("conversation.read","DOCUMENT",id(5),"DOCUMENT_BODY");grants.remove("project.read");assertThrows(TenantAccessException.class,()->scoped(()->authority.authorize(a,"1",request,false)));
  assertEquals(ErrorCode.CONTEXT_CHANGED,assertThrows(TenantAccessException.class,()->scoped(()->authority.authorize(a,"2",request,false))).code());
 }
 @Test void simulationWorkerCannotConsumeDocumentBodyUnderItsRestrictedPurpose()throws Exception{
  when(a.simulationWorker()).thenReturn(true);grant("conversation.read","DOCUMENT",id(5),"DOCUMENT_BODY");assertThrows(TenantAccessException.class,()->scoped(()->authority.authorize(a,"1",request,false)));
 }

 // FixedRead/document body loading is an explicit proof boundary. Snapshot schemas, source rights,
 // source-read readiness, resource ownership and grant evaluation below are real production classes.
 @Test void historicalFixedRevisionReadsRecycledSelectedSource()throws Exception{
  var f=new FixedSourceFixture();f.selection("RECYCLED",id(45));f.verify(false);
  verify(f.sourceRows).selection(id(42),id(4),id(31),id(43));
  verify(f.documents).requireCurrent(a,f.proof);verifyNoInteractions(f.collections);
 }
 @Test void historicalFixedRevisionReadsApprovalDriftWithoutReopeningSelection()throws Exception{
  var f=new FixedSourceFixture();f.selection("ACTIVE",id(49));f.verify(false);
  verify(f.sourceRows).selection(id(42),id(4),id(31),id(43));
 }
 @Test void writingOldFixedRevisionStillRequiresFormalReadySelection()throws Exception{
  for(boolean recycled:List.of(true,false)){
   var f=new FixedSourceFixture();f.selection(recycled?"RECYCLED":"ACTIVE",recycled?id(45):id(49));
   assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,()->f.verify(true)).code());
  }
 }
 @Test void readingCurrentFixedRevisionStillRequiresFormalReadySelection()throws Exception{
  for(boolean recycled:List.of(true,false)){
   var f=new FixedSourceFixture();f.current();f.selection(recycled?"RECYCLED":"ACTIVE",recycled?id(45):id(49));
   assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,()->f.verify(false)).code());
  }
 }
 @Test void readySourcesAllowHistoricalReadsAndFormalCurrentReadsAndWrites()throws Exception{
  var f=new FixedSourceFixture();f.verify(false);f.verify(true);f.current();f.verify(false);f.verify(true);
 }
 @Test void historicalReadStillRejectsCurrentRightsRevocation()throws Exception{
  var f=new FixedSourceFixture();f.rights("REVOKED");
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.verify(false)).code());
  verify(f.sourceRows).rights(id(47));verify(f.sourceRows,never()).selection(anyString(),anyString(),anyString(),anyString());
 }
 @Test void historicalReadStillRejectsMissingSourceReadGrant()throws Exception{
  var f=new FixedSourceFixture();grants.remove("source.read");
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.verify(false)).code());
  verify(mapper).resourceGrants(id(2),"source.read");verify(f.sourceRows,never()).rights(anyString());
 }
 @Test void historicalReadDoesNotAllowPurgedSource()throws Exception{
  var f=new FixedSourceFixture();f.selection("PURGED",id(45));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.verify(false)).code());
 }
 final class FixedSourceFixture {
  final DocumentReadService documents=mock(DocumentReadService.class);
  final DocumentReadService.FixedRead proof=mock(DocumentReadService.FixedRead.class);
  final DocumentReadProtocol.Header header=mock(DocumentReadProtocol.Header.class);
  final TaskMapper tasks=mock(TaskMapper.class);final SourceReadMapper sourceRows=mock(SourceReadMapper.class);
  final CollectionAuthority collections=mock(CollectionAuthority.class);
  final SourceRightsSchema rightsSchema=new SourceRightsSchema();
  final SourceReadAuthority reads=new SourceReadAuthority(sourceRows,mapper,resources,rightsSchema);
  final ScopeSnapshotSchema snapshots=new ScopeSnapshotSchema(new TaskInputSchema(json));
  final com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope=new com.ruoyi.medical.contracts.CommonProtocol.SourceScope(
   com.ruoyi.medical.contracts.CommonProtocol.ScopeMode.SOURCE_VERSIONS,List.of(),List.of(id(42)),false,null,null);
  final TaskProtocol.ScopeSnapshotMember member=new TaskProtocol.ScopeSnapshotMember(id(41),id(42),id(43),id(44),id(45),id(46),id(47),"a".repeat(64));
  final TaskProtocol.ScopeSnapshotManifest manifest=new TaskProtocol.ScopeSnapshotManifest("1.0.0",id(1),id(4),id(31),id(32),scope,"1","1",List.of(member));
  final com.fasterxml.jackson.databind.node.ObjectNode fixedRequest;
  final GruContextAuthority fixedAuthority;
  final String rightsHash,evidence;
  FixedSourceFixture()throws Exception{
   grant("conversation.read","DOCUMENT",id(5),"DOCUMENT_BODY");grant("conversation.use","DOCUMENT",id(5),"DOCUMENT_BODY");
   grant("source.read","SOURCE",id(41),"DOCUMENT_BODY");
   when(mapper.resourceOwner("SOURCE",id(41))).thenReturn(List.of(new PermissionProtocol.OwnerRow(id(41),id(4))));
   when(proof.header()).thenReturn(header);when(header.tenantId()).thenReturn(id(1));when(header.projectId()).thenReturn(id(4));
   when(header.documentId()).thenReturn(id(5));when(header.documentRevision()).thenReturn(id(30));
   when(header.currentDocumentRevision()).thenReturn(id(39));when(header.snapshotId()).thenReturn(id(31));
   when(header.profileRevision()).thenReturn(id(32));when(header.accessSetId()).thenReturn(id(33));
   when(proof.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(4),id(33),
    List.of(new ArtifactRegistryProtocol.MemberRow(id(42),id(47)))));
   String manifestJson=json.canonical(json.tree(manifest)),manifestHash=hash(json.tree(manifest));
   when(tasks.snapshot(id(2),id(4),id(31))).thenReturn(List.of(new TaskProtocol.Snapshot(id(1),id(4),id(31),manifestJson,manifestHash,BigInteger.ONE,BigInteger.ONE,now,manifestHash)));
   when(tasks.members(id(2),id(4),id(31))).thenReturn(List.of(new DocumentUploadBasisProtocol.MemberRow(id(1),id(4),id(31),id(41),id(42),id(43),id(44),id(45),id(46))));
   when(sourceRows.source(id(42))).thenReturn(List.of(new SourceReadProtocol.SourceRow(id(1),id(41),id(42),id(4),"PROJECT","INTERNAL",id(47),"a".repeat(64),"a".repeat(64),id(4))));
   var payload=json.object().put("schema_version","1.0.0").put("tenant_id",id(1)).put("rights_revision",id(47))
    .put("source_version",id(42)).put("purpose","DOCUMENT_BODY").putNull("valid_from").putNull("valid_to").put("classification","INTERNAL");
   payload.putArray("allowed_actions").add("READ").add("ENTERPRISE_USE");payload.putArray("providers");payload.putArray("regions").add("cn-test");
   var ev=payload.putObject("evidence_manifest").put("owner","Test owner").put("license","Explicit test authorization");
   ev.putArray("environments").add("LOCAL");ev.putArray("classifications").add("INTERNAL");
   ev.putArray("authorization_evidence").addObject().put("object_key","test/license").put("object_version","v1")
    .put("sha256","b".repeat(64)).put("size_bytes","1").put("media_type","text/plain");
   evidence=json.canonical(ev);rightsHash=hash(payload);rights("APPROVED");selection("ACTIVE",id(45));
   fixedRequest=json.object().put("schema_version","1.0.0").put("project_id",id(4)).put("document_id",id(5))
    .put("document_revision",id(30)).put("scope_snapshot_id",id(31)).put("reason","Read fixed document");
   fixedRequest.set("scope",json.tree(scope));
   var policy=new DocumentUploadPolicy("LOCAL","cn-test",16,32,64,BigInteger.valueOf(65536),60,"c".repeat(64),List.of(),128,128,128);
   fixedAuthority=new GruContextAuthority(documents,resources,tasks,snapshots,reads,collections,policy);
  }
  void current(){when(header.currentDocumentRevision()).thenReturn(id(30));}
  void rights(String status){when(sourceRows.rights(id(47))).thenReturn(List.of(new SourceReadProtocol.RightsRow(id(1),id(47),id(42),"DOCUMENT_BODY",
   "[\"READ\",\"ENTERPRISE_USE\"]","[]","[\"cn-test\"]",null,null,status,evidence,rightsHash,"1.0.0",rightsSchema.manifestSha256(),"INTERNAL",rightsHash,id(4))));}
  void selection(String lifecycle,String currentApproval){
   when(sourceRows.gates(id(42))).thenReturn(List.of(new SourceReadProtocol.GateRow(id(43),"RECEIVED","APPROVED",lifecycle,BigInteger.ONE)));
   when(sourceRows.selection(id(42),id(4),id(31),id(43))).thenReturn(List.of(new SourceReadProtocol.SelectionRow(id(1),id(4),id(31),id(41),id(42),id(43),id(44),id(45),id(46),
    "COMPLETE","READY",BigInteger.ONE,BigInteger.ONE,currentApproval,"RECEIVED","APPROVED",lifecycle,BigInteger.ONE,"APPROVE","d".repeat(64),"APPLICABLE","ACTIVE")));
  }
  String hash(com.fasterxml.jackson.databind.JsonNode value)throws Exception{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(value));}
  void verify(boolean writing)throws Exception{scoped(()->assertEquals(manifest,fixedAuthority.verify(a,"1",fixedRequest,proof,writing)));}
 }
}

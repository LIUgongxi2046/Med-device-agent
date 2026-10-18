package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Explicit ledger/document/authority doubles. No claim of real database or granted rights acceptance. */
class GruServiceTest {
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 final PermissionSchema json=new PermissionSchema();final GruSchema schema=new GruSchema();
 final EnterpriseAccess access=mock(EnterpriseAccess.class);final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);
 final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
 final GruMapper mapper=mock(GruMapper.class);final PermissionMapper permissions=mock(PermissionMapper.class);
 final DocumentReadService documents=mock(DocumentReadService.class);final GruContextAuthority authority=mock(GruContextAuthority.class);
 final DocumentReadService.FixedRead proof=mock(DocumentReadService.FixedRead.class);final DocumentReadProtocol.Header header=mock(DocumentReadProtocol.Header.class);
 final Map<String,SelectionOperationRow> operations=new HashMap<>();final Map<String,String> keys=new HashMap<>(),audits=new HashMap<>(),events=new HashMap<>(),versions=new HashMap<>();
 final List<GruProtocol.Message> messages=new ArrayList<>();GruProtocol.Context stored;GruProtocol.Head head;final GruService service;
 final ObjectNode create=json.object().put("schema_version","1.0.0").put("project_id",id(4)).put("document_id",id(5)).put("document_revision",id(6)).put("scope_snapshot_id",id(7)).put("reason","保存固定文档会话");
 GruServiceTest()throws Exception{
  var scope=json.object().put("mode","SOURCE_VERSIONS").put("network_enabled",false).putNull("network_policy_revision").putNull("approved_query_revision");scope.putArray("collection_versions");scope.putArray("source_versions").add(id(9));create.set("scope",scope);
  when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(a.actorId()).thenReturn(id(2));when(subject.sessionId()).thenReturn(id(3));
  when(access.execute(eq(subject),any())).thenAnswer(i->{Function<EnterpriseAccess.Admission,Object> f=i.getArgument(1);return f.apply(a);});
  when(header.tenantId()).thenReturn(id(1));when(header.projectId()).thenReturn(id(4));when(header.documentId()).thenReturn(id(5));when(header.documentRevision()).thenReturn(id(6));when(header.snapshotId()).thenReturn(id(7));when(header.accessSetId()).thenReturn(id(8));when(header.classification()).thenReturn("INTERNAL");
  when(proof.header()).thenReturn(header);when(proof.dependencies()).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",id(1),id(4),id(8),List.of()));
  when(documents.readFixed(subject,"1",id(5),id(6))).thenReturn(proof);
  var fixed=new TaskProtocol.ScopeSnapshotManifest("1.0.0",id(1),id(4),id(7),id(10),schema.scope(scope),"1","1",List.of());when(authority.verify(eq(a),eq("1"),any(),eq(proof),anyBoolean())).thenReturn(fixed);
  when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,12,0));
  when(mapper.operation(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);String op=keys.get(v.get("operationName")+":"+v.get("key"));return op==null?List.of():List.of(operations.get(op));});
  when(mapper.operationById(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);return List.of(operations.get(v.get("operationId")));});
  when(mapper.lockHead(anyMap())).thenAnswer(i->head==null?List.of():List.of(head));
  when(mapper.recoveryOperation(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);String op=keys.get("GRU-RECORD-APPEND:"+v.get("key"));return op==null?List.of():List.of(operations.get(op));});
  when(mapper.context(anyMap())).thenAnswer(i->stored==null?List.of():List.of(stored));when(mapper.head(anyMap())).thenAnswer(i->head==null?List.of():List.of(head));when(mapper.messages(anyMap())).thenAnswer(i->List.copyOf(messages));
  when(mapper.insertConversation(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);head=new GruProtocol.Head(id(1),id(4),(String)v.get("conversationId"),id(2),id(5),"ACTIVE",BigInteger.ONE,id(2),(LocalDateTime)v.get("now"),"INTERNAL");return 1;});
  when(mapper.insertContext(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);stored=(GruProtocol.Context)v.get("row");return 1;});
  when(mapper.saveOperation(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);String op=(String)v.get("operationId");operations.put(op,new SelectionOperationRow(op,(String)v.get("requestHash"),"COMPLETED",201,(String)v.get("response"),(String)v.get("targetId"),"1.0.0",(String)v.get("manifest")));keys.put(v.get("operationName")+":"+v.get("key"),op);return 1;});
  when(mapper.advanceConversation(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);if(!head.revision().equals(v.get("expectedRevision")))return 0;head=new GruProtocol.Head(head.tenantId(),head.projectId(),head.conversationId(),head.ownerId(),head.documentId(),head.status(),(BigInteger)v.get("nextRevision"),head.createdBy(),head.createdAt(),head.classification());return 1;});
  when(mapper.insertMessage(anyMap())).thenReturn(1);when(mapper.insertMessageRecord(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);messages.add((GruProtocol.Message)v.get("message"));return 1;});
  when(mapper.insertVersion(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);versions.put((String)v.get("messageId"),(String)v.get("hash"));return 1;});when(mapper.version(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);return List.of(versions.get(v.get("messageId")));});when(mapper.parents(anyMap())).thenReturn(List.of(id(6)));when(mapper.insertLineage(anyMap())).thenReturn(1);
  when(permissions.saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{audits.put(i.getArgument(5),i.getArgument(4));return 1;});when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{events.put(i.getArgument(1),i.getArgument(5));return 1;});
  when(mapper.audit(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);return List.of(audits.get(v.get("operationId")));});when(mapper.event(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);return List.of(events.get(v.get("operationId")));});
  service=new GruService(access,mapper,permissions,documents,authority,json);
 }
 JsonNode create()throws Exception{return service.create(subject,"1","gru-create-key-001",create);}
 ObjectNode append(String revision){var n=json.object().put("schema_version","1.0.0").put("expected_revision",revision).put("document_revision",id(6)).put("scope_snapshot_id",id(7)).putNull("selection").put("intent","QUERY").put("execution_mode","RECORD_ONLY").put("text","  用户原文 e\u0301  ").put("reason","保存消息");n.set("scope",create.required("scope"));return n;}
 JsonNode append(String key,String rev)throws Exception{return service.append(subject,"1",stored.conversationId(),key,append(rev));}
 JsonNode read()throws Exception{return service.read(subject,"1",stored.conversationId(),1,50,null);}
 @Test void persistsUserOnlyAndReplaysHistoricalReceiptAfterLaterAppend()throws Exception{
  var first=create();assertEquals(first,create());var message=append("gru-append-key-001","1");append("gru-append-key-002","2");assertEquals(message,append("gru-append-key-001","1"));
  var view=read();assertEquals("3",view.path("revision").asText());assertEquals(2,view.path("messages").size());assertEquals("NOT_EXECUTED",view.path("execution_state").asText());
  for(var m:view.path("messages")){assertEquals("USER",m.path("sender_kind").asText());assertTrue(m.path("run_id").isNull());assertTrue(m.path("candidate_ids").isEmpty());assertEquals("  用户原文 e\u0301  ",m.path("content").asText());}
  verify(mapper,times(2)).insertMessage(anyMap());verify(mapper,times(3)).saveOperation(anyMap());
 }
 @Test void typedReceiptRecoveryUsesOriginalReceiptAndCurrentAuthority()throws Exception{
  var created=create();var saved=append("gru-append-key-001","1");append("gru-append-key-002","2");
  assertEquals(created,service.receipt(subject,"1",stored.conversationId(),created.path("operation_id").asText()));
  assertEquals(saved,service.receipt(subject,"1",stored.conversationId(),saved.path("operation_id").asText()));
  assertThrows(Exception.class,()->service.receipt(subject,"1",stored.conversationId(),id(99)));
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(authority).authorize(eq(a),eq("1"),any(),eq(false));
  assertThrows(Exception.class,()->service.receipt(subject,"1",stored.conversationId(),saved.path("operation_id").asText()));
 }
 @Test void receiptsBindOriginalCanonicalBodyAcrossRecoveryWithoutBindingANewSession()throws Exception{
  var created=create();assertEquals(schema.hash(create),created.required("request_body_sha256").textValue());
  assertNotEquals(schema.hash(create.deepCopy().put("reason","另一理由")),created.required("request_body_sha256").textValue());
  var body=append("1");var saved=append("gru-append-key-001","1");String original=schema.hash(body);
  assertEquals(original,saved.required("request_body_sha256").textValue());assertNotEquals(messages.get(0).operationRequestHash(),original);
  for(String field:List.of("text","intent","reason")){
   var changed=body.deepCopy();changed.put(field,field.equals("intent")?"CHECK":"另一个正文或理由");assertNotEquals(original,schema.hash(changed));
  }
  when(subject.sessionId()).thenReturn(id(99));
  assertEquals(saved,service.receipt(subject,"1",stored.conversationId(),saved.required("operation_id").textValue()));
 }
 @Test void changedRequestSameKeyConflictsAndStaleRevisionCannotAppend()throws Exception{
  create();append("gru-append-key-001","1");assertThrows(Exception.class,()->service.append(subject,"1",stored.conversationId(),"gru-append-key-001",append("1").put("text","changed")));assertThrows(Exception.class,()->append("gru-append-key-002","1"));assertEquals(1,messages.size());
 }
 @Test void scopeOrDocumentRetargetRejectedBeforeMessageWrite()throws Exception{
  create();assertThrows(Exception.class,()->service.append(subject,"1",stored.conversationId(),"gru-append-key-001",append("1").put("document_revision",id(99))));verify(mapper,never()).insertMessage(anyMap());
 }
 @Test void revokedCurrentContextBlocksReadAndReplay()throws Exception{
  create();append("gru-append-key-001","1");doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(authority).authorize(eq(a),eq("1"),any(),anyBoolean());assertThrows(Exception.class,this::read);assertThrows(Exception.class,()->append("gru-append-key-001","1"));
 }
 @Test void changedAuditOrOutboxFailsRead()throws Exception{
  create();var r=append("gru-append-key-001","1");String op=r.path("operation_id").asText();String before=audits.get(op);audits.put(op,"{}");assertThrows(Exception.class,this::read);audits.put(op,before);events.put(op,"{}");assertThrows(Exception.class,this::read);
 }
 @Test void changedReceiptOrRegistryOrLineageFailsRead()throws Exception{
  create();var r=append("gru-append-key-001","1");String op=r.path("operation_id").asText();var old=operations.get(op);operations.put(op,new SelectionOperationRow(op,old.requestSha256(),"COMPLETED",201,"{}",old.targetId(),"1.0.0",old.schemaManifestSha256()));assertThrows(Exception.class,this::read);operations.put(op,old);
  versions.put(messages.get(0).messageId(),"0".repeat(64));assertThrows(Exception.class,this::read);versions.put(messages.get(0).messageId(),messages.get(0).contentHash());when(mapper.parents(anyMap())).thenReturn(List.of(id(99)));assertThrows(Exception.class,this::read);
 }
 @Test void paginationWatermarkInvalidatesAfterAppend()throws Exception{
  create();append("gru-append-key-001","1");String mark=read().path("page_info").path("dataset_watermark").asText();append("gru-append-key-002","2");assertThrows(Exception.class,()->service.read(subject,"1",stored.conversationId(),1,50,mark));
 }
 @Test void directoryChecksCountsWithoutMessageBodiesAndCachesOnlyThisRequestsFixedProof()throws Exception{
  create();var firstContext=stored;var firstHead=head;
  service.create(subject,"1","gru-create-key-002",create);var secondContext=stored;var secondHead=head;
  var contexts=Map.of(firstContext.conversationId(),firstContext,secondContext.conversationId(),secondContext);
  var heads=Map.of(firstHead.conversationId(),firstHead,secondHead.conversationId(),secondHead);
  when(mapper.context(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);return List.of(contexts.get(v.get("conversationId")));});
  when(mapper.head(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);return List.of(heads.get(v.get("conversationId")));});
  when(mapper.listed(anyMap())).thenReturn(List.of(firstContext.conversationId(),secondContext.conversationId()));
  when(mapper.messageCounts(anyMap())).thenReturn(List.of(new GruProtocol.Counts(BigInteger.ZERO,BigInteger.ZERO)));
  clearInvocations(mapper,documents,authority);
  var result=service.list(subject,"1",id(4));assertEquals(2,result.path("items").size());
  verify(documents,times(1)).readFixed(subject,"1",id(5),id(6));verify(documents,times(2)).requireCurrent(a,proof);
  verify(authority,times(2)).verify(eq(a),eq("1"),any(),eq(proof),eq(false));
  verify(mapper,never()).messages(anyMap());verify(mapper,never()).version(anyMap());verify(mapper,never()).parents(anyMap());
  // Only each creation ledger is read. No historical message ledger or content is selected.
  verify(mapper,times(2)).operationById(anyMap());verify(mapper,times(2)).audit(anyMap());verify(mapper,times(2)).event(anyMap());
 }
 @Test void directoryRejectsMissingSidecarOrHeadCountMismatch()throws Exception{
  create();when(mapper.listed(anyMap())).thenReturn(List.of(stored.conversationId()));
  when(mapper.messageCounts(anyMap())).thenReturn(List.of(new GruProtocol.Counts(BigInteger.ONE,BigInteger.ZERO)));
  assertThrows(TenantAccessException.class,()->service.list(subject,"1",id(4)));
  when(mapper.messageCounts(anyMap())).thenReturn(List.of(new GruProtocol.Counts(BigInteger.ONE,BigInteger.ONE)));
  assertThrows(TenantAccessException.class,()->service.list(subject,"1",id(4)));verify(mapper,never()).messages(anyMap());
 }
 @Test void directoryRejectsHeadChangedDuringItsCountsRead()throws Exception{
  create();when(mapper.listed(anyMap())).thenReturn(List.of(stored.conversationId()));
  var old=head;when(mapper.messageCounts(anyMap())).thenAnswer(i->{head=new GruProtocol.Head(old.tenantId(),old.projectId(),old.conversationId(),old.ownerId(),old.documentId(),old.status(),BigInteger.TWO,old.createdBy(),old.createdAt(),old.classification());return List.of(new GruProtocol.Counts(BigInteger.ZERO,BigInteger.ZERO));});
  assertThrows(TenantAccessException.class,()->service.list(subject,"1",id(4)));verify(mapper,never()).messages(anyMap());
 }
 ObjectNode recovery(String key,String revision){var r=json.object().put("schema_version","1.0.0").put("idempotency_key",key);r.set("request",append(revision));return r;}
 JsonNode recover(String key,String revision)throws Exception{return service.recoverAppend(subject,"1",stored.conversationId(),recovery(key,revision));}
 @Test void recoveryFoundStrictlyBindsBodyAndDoesNotWriteAnyLedger()throws Exception{
  create();var saved=append("gru-append-key-001","1");clearInvocations(mapper,permissions);
  var found=recover("gru-append-key-001","1");assertEquals("FOUND",found.path("status").asText());assertEquals(saved,found.required("receipt"));assertEquals(schema.hash(append("1")),found.path("request_body_sha256").asText());
  var altered=recovery("gru-append-key-001","1");((ObjectNode)altered.required("request")).put("text","改变原消息");assertThrows(TenantAccessException.class,()->service.recoverAppend(subject,"1",stored.conversationId(),altered));
  verify(mapper,never()).saveOperation(anyMap());verify(mapper,never()).advanceConversation(anyMap());verify(permissions,never()).saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString());verify(permissions,never()).saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
 }
 @Test void recoveryAbsentOldCasIsDefinitivelyStaleButSameOrFutureRevisionRemainsUnknown()throws Exception{
  create();assertEquals("UNKNOWN",recover("gru-append-key-001","1").path("status").asText());assertEquals("UNKNOWN",recover("gru-append-key-001","9").path("status").asText());
  append("competing-append-key","1");var stale=recover("gru-append-key-001","1");assertEquals("NOT_APPLIED_STALE",stale.path("status").asText());assertTrue(stale.path("receipt").isNull());assertEquals("2",stale.path("current_revision").asText());assertEquals(id(2),stale.path("actor_id").asText());assertEquals(id(1),stale.path("current_context").path("tenant_id").asText());
  assertThrows(TenantAccessException.class,()->append("gru-append-key-001","1"));assertEquals(1,messages.size());
 }
 @Test void recoveryCurrentLockSeesDelayedOriginalCommitAndNeverMisreportsItStale()throws Exception{
  create();when(mapper.lockHead(anyMap())).thenAnswer(i->{append("gru-append-key-001","1");return List.of(head);});
  var found=recover("gru-append-key-001","1");assertEquals("FOUND",found.path("status").asText());assertEquals(messages.get(0).operationId(),found.path("receipt").path("operation_id").asText());
  var ordered=inOrder(mapper);ordered.verify(mapper).lockHead(anyMap());ordered.verify(mapper).recoveryOperation(anyMap());
 }
 @Test void recoveryRejectsRevokedReadAndDamagedHistoryBeforeAnyStaleProof()throws Exception{
  create();append("competing-append-key","1");doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(authority).verify(eq(a),eq("1"),any(),eq(proof),eq(false));assertThrows(TenantAccessException.class,()->recover("gru-append-key-001","1"));
  verify(mapper,never()).recoveryOperation(anyMap());
 }
 @Test void recoveryRejectsHeadHistoryCountCorruption()throws Exception{
  create();var old=head;head=new GruProtocol.Head(old.tenantId(),old.projectId(),old.conversationId(),old.ownerId(),old.documentId(),old.status(),BigInteger.TWO,old.createdBy(),old.createdAt(),old.classification());
  assertThrows(TenantAccessException.class,()->recover("gru-append-key-001","1"));verify(mapper,never()).recoveryOperation(anyMap());
 }
 @Test void emptyDirectoryStillRequiresProjectPermission()throws Exception{
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(authority).project(a,"1",id(4));assertThrows(Exception.class,()->service.list(subject,"1",id(4)));verify(mapper,never()).listed(anyMap());
 }
 @Test void lostCasCannotWriteMessage()throws Exception{create();when(mapper.advanceConversation(anyMap())).thenReturn(0);assertThrows(Exception.class,()->append("gru-append-key-001","1"));verify(mapper,never()).insertMessage(anyMap());}
}

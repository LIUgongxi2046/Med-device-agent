package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual orchestrator and RuntimeObjectTransport, privately committed real ledger attempt;
 * writer, current authority, registry and transaction boundary are explicit doubles, not live S3/DB. */
class TaskFieldTargetServiceTest {
 @Test void identicalOriginalProduceReturnsStoredReceiptWithoutNewPhysicalReadOrAttempt()throws Exception{
  try(var f=new Fixture()){when(f.ledger.findOriginal(f.base.admission,f.base.task,f.base.key)).thenReturn(Optional.of(f.row));when(f.registered.findRegistered(f.base.admission,f.attempt.artifactId(),TaskFieldTargetProtocol.TARGET_PRODUCE)).thenReturn(Optional.of(f.proof));
   assertEquals("REGISTERED",f.produce().required("state").asText());verifyNoInteractions(f.writer,f.base.reader,f.basis);verify(f.proof).requireUnchanged();verify(f.ledger,never()).prepare(any(),any(),anyList(),any(),anyString(),anyString());
  }
 }
 @Test void changedBodyCannotBorrowOriginalIdempotencyKey()throws Exception{try(var f=new Fixture()){
  when(f.ledger.findOriginal(f.base.admission,f.base.task,f.base.key)).thenReturn(Optional.of(f.row));f.base.request.put("reason","changed original reason");
  var e=assertThrows(TenantAccessException.class,f::produce);assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,e.code());verifyNoInteractions(f.writer,f.registered);
 }}
 @Test void unresolvedCurrentInventoryReturnsWaitingWithoutAnySourceReadOrIntent()throws Exception{try(var f=new Fixture()){
  ((ObjectNode)f.base.mapped.at("/inventory/0")).put("state","MISSING_SOURCE");assertEquals("WAITING_INPUT",f.produce().required("state").asText());
  verifyNoInteractions(f.writer,f.registered,f.base.reader);verify(f.ledger,never()).prepare(any(),any(),anyList(),any(),anyString(),anyString());
 }}
 @Test void initialTransferChecksOriginalReadAndFullAncestryOnEveryWriterCallback()throws Exception{try(var f=new Fixture()){
  assertEquals("REGISTERED",f.produce().required("state").asText());assertEquals(1,f.puts.get());assertEquals(0,f.gets.get());
  verify(f.registered).preflight(f.base.basis,f.base.mapping,f.base.reads);verify(f.registered,atLeast(5)).requireCurrentContent(f.base.admission,f.attempt,TaskFieldTargetProtocol.TARGET_PRODUCE);
  verify(f.base.reader,atLeast(5)).requireCurrent(f.base.admission,"1",f.base.reads.get(0));verify(f.registered).register(eq(f.base.basis),eq(f.base.mapping),eq(f.base.reads),eq(f.attempt),any(),eq("1"));
 }}
 @Test void revokedSourceBetweenPhysicalSegmentsStopsRegistration()throws Exception{try(var f=new Fixture()){
  f.revokeSource=true;assertThrows(TenantAccessException.class,f::produce);assertEquals(1,f.puts.get());verify(f.registered,never()).register(any(),any(),anyList(),any(),any(),anyString());
 }}
 @Test void changedFullAncestryBetweenPhysicalSegmentsStopsRegistration()throws Exception{try(var f=new Fixture()){
  f.revokeAncestry=true;assertThrows(TenantAccessException.class,f::produce);verify(f.registered,never()).register(any(),any(),anyList(),any(),any(),anyString());
 }}
 @Test void originalRecoveryUsesGetOnlyAndRechecksAncestry()throws Exception{try(var f=new Fixture()){
  f.attempt.claimTransferMode();when(f.ledger.findOriginal(f.base.admission,f.base.task,f.base.key)).thenReturn(Optional.of(f.row));
  assertEquals("REGISTERED",f.recover().required("state").asText());assertEquals(0,f.puts.get());assertEquals(1,f.gets.get());verify(f.ledger).recover(eq(f.base.basis),eq(f.base.mapping),eq(f.base.reads),any(),eq(f.base.key),eq("1"));
 }}
 @Test void missingTransportStopsBeforeAnySourceReceiptOrIntentWrite()throws Exception{try(var f=new Fixture()){
  var service=f.service(null);assertThrows(TenantAccessException.class,()->service.produce(f.subject,"1",f.base.task,f.base.key,f.body()));verifyNoInteractions(f.base.reader,f.registered);verify(f.ledger,never()).prepare(any(),any(),anyList(),any(),anyString(),anyString());
 }}
 @Test void absentOriginalRecoveryNeverCreatesAttemptOrReadsBytes()throws Exception{try(var f=new Fixture()){
  assertNull(f.recover());verifyNoInteractions(f.writer,f.base.reader,f.registered,f.basis);verify(f.ledger,never()).recover(any(),any(),anyList(),any(),anyString(),anyString());
 }}
 static final class Fixture implements AutoCloseable {
  final TaskFieldTargetLedgerTest.Fixture base=new TaskFieldTargetLedgerTest.Fixture(true);
  final TaskFieldTargetLedger.Attempt attempt=base.prepare();final TaskFieldTargetLedger.Row row=base.tx(()->base.ledger.findOriginal(base.admission,base.task,base.key).orElseThrow());
  final EnterpriseAccess access=mock(EnterpriseAccess.class);final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
  final TaskFieldTargetBasis basis=mock(TaskFieldTargetBasis.class);final TaskFieldMappingService mappings=mock(TaskFieldMappingService.class);
  final TaskFieldTargetLedger ledger=mock(TaskFieldTargetLedger.class);final TaskFieldTargetRegistration registered=mock(TaskFieldTargetRegistration.class);
  final TaskFieldTargetRegistration.Verified proof=mock(TaskFieldTargetRegistration.Verified.class);final S3ObjectWriter writer=mock(S3ObjectWriter.class);
  final Path directory=Files.createTempDirectory("field-transport-test-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
  final RuntimeObjectTransport transport=new RuntimeObjectTransport(()->writer,directory,1048576);
  final AtomicInteger puts=new AtomicInteger(),gets=new AtomicInteger();boolean revokeSource,revokeAncestry;
  Fixture()throws Exception{
   when(access.execute(eq(subject),any())).thenAnswer(i->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(base.admission));
   when(basis.require(base.admission,base.task,TaskFieldTargetProtocol.TARGET_PRODUCE)).thenReturn(base.basis);when(base.basis.pins()).thenReturn(base.mapped);
   when(mappings.requireCurrent(eq(base.basis),anyString(),anyString())).thenReturn(base.mapping);
   when(base.reader.read(eq(subject),eq("1"),any())).thenReturn(base.reads.get(0));
   when(ledger.findOriginal(base.admission,base.task,base.key)).thenReturn(Optional.empty());
   when(ledger.prepare(eq(base.basis),eq(base.mapping),eq(base.reads),any(),eq(base.key),eq("1"))).thenReturn(attempt);
   when(ledger.recover(eq(base.basis),eq(base.mapping),eq(base.reads),any(),eq(base.key),eq("1"))).thenReturn(attempt);
   when(registered.findRegistered(any(),anyString(),anyString())).thenReturn(Optional.empty());
   when(registered.register(eq(base.basis),eq(base.mapping),eq(base.reads),eq(attempt),any(),eq("1"))).thenReturn(proof);
   var producingReceipt=receipt();when(proof.taskId()).thenReturn(base.task);when(proof.artifactId()).thenReturn(attempt.artifactId());when(proof.receipt()).thenReturn(producingReceipt);
   when(writer.writeVerified(any(),any(),any())).thenAnswer(i->{puts.incrementAndGet();assertArrayEquals(attempt.bytes(),Files.readAllBytes(i.getArgument(1)));return physical(i.getArgument(2));});
   when(writer.recoverVerified(any(),any())).thenAnswer(i->{gets.incrementAndGet();return physical(i.getArgument(1));});
   clearInvocations(base.reader);
  }
  ObjectRef physical(S3ObjectReader.Admission guard)throws Exception{
   guard.requireCurrent();if(revokeSource)doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(base.reader).requireCurrent(base.admission,"1",base.reads.get(0));
   if(revokeAncestry)doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(registered).requireCurrentContent(base.admission,attempt,TaskFieldTargetProtocol.TARGET_PRODUCE);
   guard.requireCurrent();guard.requireCurrent();return new ObjectRef(attempt.expectedObjectKey(),"actual-readback-version",attempt.writeIntent().sha256(),attempt.writeIntent().sizeBytes(),"application/json");
  }
  ObjectNode receipt()throws Exception{
   var n=base.json.object().put("schema_version","1.0.0").put("tenant_id",base.tenant).put("project_id",base.project).put("task_id",base.task)
    .put("operation_id",attempt.operationId()).put("audit_id",id(91)).put("event_id",id(92)).put("request_sha256",attempt.requestSha256()).put("schema_manifest_sha256",base.schema.manifest())
    .put("created_by",base.actor).put("created_at","2026-10-20T00:00:00Z").put("classification","INTERNAL").put("registration_id",id(93)).put("write_id",attempt.writeId()).put("artifact_id",attempt.artifactId())
    .put("artifact_kind","STRICT_FIELD_TARGET").put("owner_kind","TASK").putNull("run_id").putNull("document_revision").put("task_revision",id(4)).put("plan_revision",id(5))
    .put("assignment_id",id(7)).put("assignment_sha256",base.mapping.mappingSha256()).put("intent_sha256",attempt.intentSha256()).put("status","REGISTERED");
   n.set("object_ref",base.json.tree(new ObjectRef(attempt.expectedObjectKey(),"actual-readback-version",attempt.writeIntent().sha256(),attempt.writeIntent().sizeBytes(),"application/json")));n.putArray("source_read_receipt_ids").add(base.reads.get(0).receiptId());base.schema.validate("ProduceReceipt",n);return n;
  }
  byte[] body()throws Exception{return base.schema.canonical(base.request).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
  TaskFieldTargetService service(RuntimeObjectTransport t){return new TaskFieldTargetService(access,basis,mappings,base.reader,ledger,t,registered,base.schema,new TaskFieldTargetPolicy(100,100,1048576,1048576,1048576,"LOCAL","LOCAL",100,100));}
  com.fasterxml.jackson.databind.JsonNode produce()throws Exception{return service(transport).produce(subject,"1",base.task,base.key,body());}
  com.fasterxml.jackson.databind.JsonNode recover()throws Exception{var n=base.json.object().put("schema_version","1.0.0").put("project_id",base.project).put("original_idempotency_key",base.key);return service(transport).recover(subject,"1",base.task,base.schema.canonical(n).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  static String id(int n){return TaskFieldTargetSchemaTest.id(n);}
  public void close()throws Exception{Files.deleteIfExists(directory);}
 }
}

package com.ruoyi.medical.persistence;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
class DocumentTechnicalCheckRecoveryLedgerTest {
 @Test void forgedBothReferencesCannotCompleteWithoutIndependentAttempts()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();f.pending();
  f.commandMutation(c->{for(var v:c.path("outputs")){var o=(ObjectNode)v;var i=o.path("intent");o.put("attempted",true);o.set("reference",f.json.tree(new InterfaceProtocol.ObjectRef(i.path("object_key").asText(),"fake",i.path("sha256").asText(),i.path("size_bytes").asText(),i.path("media_type").asText())));}});
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());verifyNoInteractions(f.transport);verify(f.mapper,never()).completeOperation(anyMap());
 }
 @Test void attemptedRollbackCannotReissueWrite()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();f.pending();f.commandMutation(c->((ObjectNode)c.path("outputs").get(0)).put("attempted",false));
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());verifyNoInteractions(f.transport);
 }
 @Test void attemptDeletionAloneAndTogetherWithMutableFlagCannotReissueWrite()throws Exception{
  for(boolean rollback:List.of(false,true)){var f=new DocumentTechnicalCheckServiceTest();f.pending();f.durableAttempts.clear();if(rollback)f.commandMutation(c->((ObjectNode)c.path("outputs").get(0)).put("attempted",false));
   assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());verifyNoInteractions(f.transport);}
 }
 @Test void legacyPendingWithoutIndependentIntentCannotBeUpgradedByRetryOrLookup()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();f.pending();f.durableIntent=null;f.durableAttempts.clear();
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->f.service.recovery(f.subject,"1",f.id(5),f.KEY)).code());verifyNoInteractions(f.transport);verify(f.mapper,never()).insertRecoveryIntent(anyMap());
 }
 @Test void mutableArtifactWriteIdentityCannotReplaceOriginalIntent()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();f.pending();f.commandMutation(c->{var o=(ObjectNode)c.path("outputs").get(0);var i=(ObjectNode)o.path("intent");o.put("artifact_id",f.id(991));i.put("write_id",f.id(992)).put("object_key","documents/"+f.id(1)+"/"+f.id(5)+"/technical-checks/"+c.path("check_id").asText()+"/"+f.id(991));});
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());verifyNoInteractions(f.transport);
 }
 @Test void startWitnessAndVersionMutationFailClosed()throws Exception{
  for(int mutation=0;mutation<3;mutation++){var f=new DocumentTechnicalCheckServiceTest();f.pending();if(mutation==0)f.startAuditJson="{}";if(mutation==1)f.startEventJson="{}";if(mutation==2)f.versions.remove(f.operation.operationId());assertThrows(TenantAccessException.class,f::create);verifyNoInteractions(f.transport);}
 }
 @Test void previouslyStoredReferenceRequiresRealRecoveryAndSameObjectVersion()throws Exception{
  var f=partiallyWritten();clearInvocations(f.transport,f.mapper);
  when(f.transport.writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),eq(true),eq(100000),any())).thenThrow(new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
  assertEquals(ErrorCode.OBJECT_UNAVAILABLE,assertThrows(TenantAccessException.class,f::create).code());verify(f.transport).writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),eq(true),eq(100000),any());verify(f.mapper,never()).completeOperation(anyMap());
 }
 @Test void changedRecoveredVersionRefusesFinish()throws Exception{
  var f=partiallyWritten();clearInvocations(f.transport,f.mapper);
  when(f.transport.writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),eq(true),eq(100000),any())).thenAnswer(i->{S3ObjectWriter.WriteIntent intent=i.getArgument(2);return new InterfaceProtocol.ObjectRef(intent.objectKey(),"changed-version",intent.sha256(),intent.sizeBytes(),intent.mediaType());});
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());verify(f.mapper,never()).completeOperation(anyMap());
 }
 @Test void eachOutputAttemptIsIndependentAndRetryOnlyRecoversBoth()throws Exception{
  var f=partiallyWritten();clearInvocations(f.transport,f.mapper);assertEquals(2,f.durableAttempts.size());
  when(f.transport.writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),eq(true),eq(100000),any())).thenAnswer(i->{S3ObjectWriter.WriteIntent intent=i.getArgument(2);S3ObjectReader.Admission a=i.getArgument(6);a.requireCurrent();assertArrayEquals((byte[])i.getArgument(3),f.objectBytes.get(intent.objectKey()));return new InterfaceProtocol.ObjectRef(intent.objectKey(),"version-1",intent.sha256(),intent.sizeBytes(),intent.mediaType());});
  assertEquals("COMPLETE",f.create().path("technical_execution").asText());verify(f.transport,times(2)).writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),eq(true),eq(100000),any());verify(f.transport,never()).writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),eq(false),eq(100000),any());verify(f.mapper,never()).insertRecoveryAttempt(anyMap());
 }
 @Test void revokedCurrentAuthorityPreventsRecoveryIo()throws Exception{
  var f=partiallyWritten();clearInvocations(f.transport);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.documents).requireCurrent(f.admission,f.proof);assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,f::create).code());verifyNoInteractions(f.transport);
 }
 @Test void completedReadRequiresOriginalAttemptEvidence()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();var receipt=f.create();f.durableAttempts.clear();clearInvocations(f.transport);assertThrows(TenantAccessException.class,()->f.service.read(f.subject,"1",receipt.path("check_id").asText()));verifyNoInteractions(f.transport);
 }
 @Test void failedAttemptInsertNeverTouchesStorage()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();doThrow(new IllegalStateException("simulated transaction failure")).when(f.mapper).insertRecoveryAttempt(anyMap());assertThrows(IllegalStateException.class,f::create);verifyNoInteractions(f.transport);
 }
 @Test void mutableStoredReferenceCannotForgeAnObjectVersion()throws Exception{
  var f=partiallyWritten();f.commandMutation(c->((ObjectNode)c.path("outputs").get(0).path("reference")).put("object_version","fabricated-version"));clearInvocations(f.mapper,f.transport);
  doAnswer(i->{S3ObjectWriter.WriteIntent intent=i.getArgument(2);return new InterfaceProtocol.ObjectRef(intent.objectKey(),"version-1",intent.sha256(),intent.sizeBytes(),intent.mediaType());}).when(f.transport).writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),eq(true),eq(100000),any());
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());verify(f.mapper,never()).completeOperation(anyMap());
 }
 @Test void operationCasFailureAfterAttemptCannotStartObjectIo()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();doReturn(0).when(f.mapper).updateCommand(anyMap());assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());verifyNoInteractions(f.transport);
 }
 @Test void missingAttemptAfterSuccessfulObjectRecoveryPreventsCompletion()throws Exception{
  var f=partiallyWritten();clearInvocations(f.mapper,f.transport);
  doAnswer(i->{S3ObjectWriter.WriteIntent intent=i.getArgument(2);f.durableAttempts.clear();return new InterfaceProtocol.ObjectRef(intent.objectKey(),"version-1",intent.sha256(),intent.sizeBytes(),intent.mediaType());}).when(f.transport).writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),eq(true),eq(100000),any());
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::create).code());verify(f.mapper,never()).completeOperation(anyMap());
 }
 @Test void deletedOperationCannotBecomeNotFoundWhenImmutableKeyExists()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();f.pending();f.operation=null;
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->f.service.recovery(f.subject,"1",f.id(5),f.KEY)).code());assertThrows(TenantAccessException.class,f::create);verifyNoInteractions(f.transport);
 }
 @Test void changedOperationKeyRefusesOriginalLookupAndNewKeyReplay()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();f.pending();f.operationKey="modified-original-key";
  assertThrows(TenantAccessException.class,()->f.service.recovery(f.subject,"1",f.id(5),f.KEY));
  assertThrows(TenantAccessException.class,()->f.service.create(f.subject,"1",f.id(5),f.operationKey,f.bytes(f.request)));verifyNoInteractions(f.transport);
 }
 @Test void completedReadRejectsChangedOriginalOperationKey()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();var receipt=f.create();f.operationKey="modified-original-key";clearInvocations(f.transport);
  assertThrows(TenantAccessException.class,()->f.service.read(f.subject,"1",receipt.path("check_id").asText()));verifyNoInteractions(f.transport);
 }
 @Test void historicalCompletionMustFollowBothActualAttemptsAndPrecedeOriginalDeadline()throws Exception{
  for(int mutation=0;mutation<3;mutation++){var f=new DocumentTechnicalCheckServiceTest();var receipt=f.create();clearInvocations(f.transport);
   if(mutation==0){var a=f.durableAttempts.get("FINDINGS");f.durableAttempts.put("FINDINGS",new com.ruoyi.medical.persistence.mapper.DocumentTechnicalCheckMapper.RecoveryAttempt(a.tenantId(),a.projectId(),a.operationId(),a.checkId(),a.outputKind(),a.writeId(),a.artifactId(),a.intentSha256(),a.schemaVersion(),a.schemaManifest(),a.createdBy(),f.now.plusSeconds(60),a.classification()));}
   if(mutation==1)f.storedRows.get("job").put("endedAt",f.now.plusSeconds(120));
   if(mutation==2)f.storedRows.get("job").put("startedAt",f.now.minusSeconds(1));
   assertThrows(TenantAccessException.class,()->f.service.read(f.subject,"1",receipt.path("check_id").asText()));verifyNoInteractions(f.transport);
  }
 }
 private DocumentTechnicalCheckServiceTest partiallyWritten()throws Exception{
  var f=new DocumentTechnicalCheckServiceTest();int[] calls={0};when(f.transport.writeDocumentProjection(eq(f.subject),eq(f.id(1)),any(),any(),anyBoolean(),eq(100000),any())).thenAnswer(i->{S3ObjectWriter.WriteIntent intent=i.getArgument(2);S3ObjectReader.Admission a=i.getArgument(6);a.requireCurrent();f.objectBytes.put(intent.objectKey(),((byte[])i.getArgument(3)).clone());if(++calls[0]==2)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return new InterfaceProtocol.ObjectRef(intent.objectKey(),"version-1",intent.sha256(),intent.sizeBytes(),intent.mediaType());});assertThrows(TenantAccessException.class,f::create);return f;
 }
}

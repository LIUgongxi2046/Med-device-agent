package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual outer parser, private historical preparation, ledger/transport, registration, lifecycle and journal.
 * Current professional/permission boundaries and JDBC storage remain explicit fixtures, not deployed acceptance. */
class PublicationDisableRecoveryTest {
 PublicationHistoricalAuthorityTest history;
 FormalAgentPublicationObjectLedgerTest f;
 PublicationRegistrationTest published;
 FormalAgentPublicationLifecycle lifecycle;
 FormalAgentPublicationLifecycle.State state;
 FormalAgentPublicationLifecycle.Disabled disabled;
 JdbcTemplate db;
 JsonNode registration;
 ObjectNode request;
 final String key="original-disable-key-001";
 @BeforeEach void setup()throws Exception{
  history=new PublicationHistoricalAuthorityTest();history.setup();f=history.f;published=history.fixture;
  registration=published.receipt();
  state=new FormalAgentPublicationLifecycle.State("PUBLISHED",BigInteger.TWO,f.proof.manifest().manifest().evaluationId(),LocalDateTime.parse(registration.required("created_at").asText()));
  lifecycle=new FormalAgentPublicationLifecycle(mock(DataSource.class),f.journal,f.guarded,f.resources,f.formal,f.json);
  db=mock(JdbcTemplate.class);ReflectionTestUtils.setField(lifecycle,"db",db);
  ReflectionTestUtils.setField(published.service,"lifecycle",lifecycle);ReflectionTestUtils.setField(history.service,"lifecycle",lifecycle);
  when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(i->{
   String sql=i.getArgument(0);Object[] v=PublicationRegistrationTest.args(i);
   if(sql.contains("FROM agent_version"))return List.of(state);
   if(sql.contains("FROM formal_agent_publication_disable"))return disabled==null?List.of():List.of(disabled);
   if(sql.contains("FROM release_scope"))return List.of((int)published.tables.get("release_scope").stream().filter(row->Objects.equals(row.get("scope_revision"),v[1])&&Objects.equals(row.get("release_id"),v[2])&&Objects.equals(row.get("status"),v[3])&&new BigInteger(row.get("revision").toString()).equals(v[4])&&Objects.equals(row.get("updated_at"),v[5])).count());
   throw new AssertionError(sql);
  });
  when(db.update(anyString(),any(Object[].class))).thenAnswer(i->{
   String sql=i.getArgument(0);Object[] v=PublicationRegistrationTest.args(i);
   if(sql.startsWith("INSERT INTO formal_agent_publication_disable")){disabled=new FormalAgentPublicationLifecycle.Disabled((String)v[1],(String)v[3],(String)v[4],(String)v[6],(LocalDateTime)v[7],"1.0.0",(String)v[5],(String)v[8]);return 1;}
   if(sql.startsWith("UPDATE agent_version")){state=new FormalAgentPublicationLifecycle.State("DISABLED",BigInteger.valueOf(3),state.evaluation(),(LocalDateTime)v[0]);return 1;}
   if(sql.startsWith("UPDATE release_scope")){var row=published.only("release_scope","scope_revision",v[2]);row.put("status","DISABLED");row.put("revision",2);row.put("updated_at",v[0]);return 1;}
   throw new AssertionError(sql);
  });
  request=f.json.object().put("schema_version","1.0.0").put("release_id",f.row.release()).put("expected_revision","2").put("reason","Disable this exact release");
  clearWrites();
 }
 @AfterEach void cleanup()throws Exception{history.close();}
 void disable()throws Exception{f.now=f.now.plusSeconds(1);f.begin();try{lifecycle.disable(f.a,key,request,f.row,registration);}finally{f.end();}clearWrites();}
 ObjectNode outer(){var value=f.json.object().put("schema_version","1.0.0").put("original_key",key);value.set("request",request.deepCopy());return value;}
 byte[] bytes(JsonNode value)throws Exception{return f.formal.canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 JsonNode recover()throws Exception{return history.service.recoverDisable(history.subject,"1",bytes(outer()));}
 void clearWrites(){clearInvocations(db,published.db,f.db,f.m,f.p,f.resources,published.writer);}
 void noWrites()throws Exception{verify(db,never()).update(anyString(),any(Object[].class));verify(published.db,never()).update(anyString(),any(Object[].class));verify(f.db,never()).update(anyString(),any(Object[].class));verify(f.m,never()).insertVersion(anyMap());verify(f.m,never()).insertOperation(anyMap());verify(f.m,never()).insertJournal(anyMap());verify(f.p,never()).saveAudit(any(),any(),any(),any(),any(),any(),any(),any());verify(f.p,never()).saveEvent(any(),any(),any(),any(),any(),any());verify(published.writer,never()).writeVerified(any(),any(),any());}
 void denied(ErrorCode code,org.junit.jupiter.api.function.Executable action)throws Exception{assertEquals(code,assertThrows(TenantAccessException.class,action).code());noWrites();}
 @Test void originalCompletedDisableRecoversWithExactIdentityAndNoWrites()throws Exception{
  disable();var result=recover();assertEquals("COMPLETED",result.path("state").asText());assertEquals(key,result.path("original_key").asText());assertEquals(f.actor,result.path("actor_id").asText());assertEquals(f.row.release(),result.path("release_id").asText());assertEquals("2",result.path("expected_revision").asText());assertEquals("3",result.path("resulting_revision").asText());assertEquals(f.formal.hash(request),result.path("request_body_sha256").asText());assertEquals(disabled.decision(),result.path("receipt").path("resource_id").asText());assertEquals("PUBLICATION",result.path("receipt").path("kind").asText());assertEquals(result,recover());assertEquals(1,f.records.size());assertEquals("DISABLED",state.status());noWrites();verify(published.writer,times(2)).recoverVerified(any(),any());verify(history.auth,never()).require(any(),any(),eq(true),anyBoolean(),any());verify(history.auth,never()).require(any(),any(),anyBoolean(),eq(false),any());
 }
 @Test void recoveryNeedsNoCurrentPublishWriteGrant()throws Exception{
  disable();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.resources).require(eq(f.a),any(),eq("agent.publish"),anyString());assertEquals("COMPLETED",recover().path("state").asText());verify(f.resources,never()).require(eq(f.a),any(),eq("artifact.register"),anyString());noWrites();
 }
 @Test void currentArtifactReadRevocationDeniesRecovery()throws Exception{
  disable();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.resources).require(eq(f.a),any(),eq("artifact.read"),eq("AGENT_PUBLICATION"));denied(ErrorCode.ACCESS_DENIED,this::recover);
 }
 @Test void currentHistoricalEvidenceStillRequired()throws Exception{
  disable();when(history.evidence.prepare(eq(history.subject),eq("1"),any(),eq(true))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));denied(ErrorCode.ACCESS_DENIED,this::recover);
 }
 @Test void changedOriginalReasonConflicts()throws Exception{disable();request.put("reason","Changed original reason");denied(ErrorCode.IDEMPOTENCY_CONFLICT,this::recover);}
 @Test void changedOriginalRevisionConflicts()throws Exception{disable();request.put("expected_revision","3");denied(ErrorCode.IDEMPOTENCY_CONFLICT,this::recover);}
 @Test void wrongOriginalKeyDoesNotReturnAnySuccessOrAbsence()throws Exception{disable();var other=outer().put("original_key","different-disable-key-002");denied(ErrorCode.ACCESS_DENIED,()->history.service.recoverDisable(history.subject,"1",bytes(other)));}
 @Test void anotherActorCannotRecoverOriginalDisable()throws Exception{disable();f.actor=f.id(998);when(history.subject.user().user_id()).thenReturn(f.actor);denied(ErrorCode.ACCESS_DENIED,this::recover);}
 @Test void wrongCurrentContextIsRejectedBeforeObjectRecovery()throws Exception{disable();denied(ErrorCode.CONTEXT_CHANGED,()->history.service.recoverDisable(history.subject,"2",bytes(outer())));verifyNoInteractions(published.writer);}
 @Test void contextChangingAfterPreparationCannotReturnSuccess()throws Exception{
  disable();var original=published.writer;doAnswer(i->{when(f.a.context()).thenReturn(new BusinessSessionProtocol.Context(f.id(1),"2","1","1"));return published.transfer.reference();}).when(original).recoverVerified(any(),any());denied(ErrorCode.CONTEXT_CHANGED,this::recover);
 }
 @Test void genuineAbsenceIsAnErrorNotACompletedOrNotFoundProjection()throws Exception{denied(ErrorCode.OBJECT_UNAVAILABLE,this::recover);assertNull(disabled);assertTrue(f.records.isEmpty());}
 @Test void originalOperationWithoutJournalIsCorruptionNotAbsence()throws Exception{disable();f.records.clear();denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void originalJournalWithoutOperationIsCorruptionNotAbsence()throws Exception{disable();f.operations.clear();denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void disabledSidecarWithoutBothOriginalRowsIsCorruptionNotAbsence()throws Exception{disable();f.records.clear();f.operations.clear();denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void missingDisabledSidecarCannotBecomeAbsence()throws Exception{disable();disabled=null;denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void alteredDisabledDecisionHashFails()throws Exception{disable();disabled=new FormalAgentPublicationLifecycle.Disabled(disabled.project(),disabled.decision(),"b".repeat(64),disabled.creator(),disabled.created(),disabled.schemaVersion(),disabled.schemaManifest(),disabled.classification());denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void missingOriginalAuditFails()throws Exception{disable();f.audits.clear();denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void missingOriginalEventFails()throws Exception{disable();f.events.clear();denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void changedCandidateRevisionFails()throws Exception{disable();state=new FormalAgentPublicationLifecycle.State("DISABLED",BigInteger.valueOf(4),state.evaluation(),state.changed());denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void reenabledScopeCannotBecomeValidHistoricalRecovery()throws Exception{disable();published.first("release_scope").put("status","ACTIVE");denied(ErrorCode.CONTENT_MISMATCH,this::recover);}
 @Test void missingPhysicalPublishedObjectCannotProduceRecoveredReceipt()throws Exception{disable();when(published.writer.recoverVerified(any(),any())).thenThrow(new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));denied(ErrorCode.OBJECT_UNAVAILABLE,this::recover);}
 void missingPublicationJoin(){var authorityDb=(JdbcTemplate)ReflectionTestUtils.getField(history.service,"db");when(authorityDb.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(List.of());}
 @Test void missingJoinedPublicationMetadataWithAuthorizedOriginalIsCorruption()throws Exception{disable();missingPublicationJoin();denied(ErrorCode.CONTENT_MISMATCH,this::recover);var owner=FormalAgentCandidateService.governance(f.a,f.row.project());verify(f.resources).require(eq(f.a),eq(owner),eq("configuration.read"),eq("AGENT_PUBLICATION"));verifyNoInteractions(published.writer);}
 @Test void missingPublicationJoinStillChecksExactOriginalBody()throws Exception{disable();missingPublicationJoin();request.put("reason","Not the original body");denied(ErrorCode.IDEMPOTENCY_CONFLICT,this::recover);verifyNoInteractions(published.writer);}
 @Test void missingPublicationJoinCannotDiscloseCorruptionAfterReadRevocation()throws Exception{disable();missingPublicationJoin();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.resources).require(eq(f.a),any(),eq("configuration.read"),eq("AGENT_PUBLICATION"));denied(ErrorCode.ACCESS_DENIED,this::recover);verify(f.m,never()).operation(anyMap());verifyNoInteractions(published.writer);}
 @Test void guessedForeignActorKeyWithDamagedJoinDoesNotDiscloseExistence()throws Exception{disable();missingPublicationJoin();f.actor=f.id(998);when(history.subject.user().user_id()).thenReturn(f.actor);denied(ErrorCode.ACCESS_DENIED,this::recover);verifyNoInteractions(f.resources,published.writer);verify(f.m,never()).operation(anyMap());}
 @Test void guessedUnknownKeyWithDamagedJoinMatchesForeignActorFailure()throws Exception{disable();missingPublicationJoin();var other=outer().put("original_key","unknown-original-key-001");denied(ErrorCode.ACCESS_DENIED,()->history.service.recoverDisable(history.subject,"1",bytes(other)));verifyNoInteractions(f.resources,published.writer);verify(f.m,never()).operation(anyMap());}
 @Test void guessedForeignProjectIsDeniedBeforeAnyCorruptionDisclosure()throws Exception{
  disable();missingPublicationJoin();var row=f.records.values().iterator().next();String foreign=f.id(997);
  f.records.put(row.recordId(),new com.ruoyi.medical.persistence.mapper.AgentPublicationMapper.Journal(row.tenantId(),foreign,row.recordId(),row.kind(),row.resourceId(),row.operationId(),row.idempotencyKey(),row.requestSha256(),row.requestJson(),row.contentJson(),row.contentSha256(),row.authorizationsJson(),row.authorizationsSha256(),row.receiptJson(),row.receiptSha256(),row.auditId(),row.eventId(),row.schemaVersion(),row.schemaManifest(),row.createdBy(),row.createdAt(),row.classification()));
  var owner=FormalAgentCandidateService.governance(f.a,foreign);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.resources).require(eq(f.a),eq(owner),eq("configuration.read"),eq("AGENT_PUBLICATION"));denied(ErrorCode.ACCESS_DENIED,this::recover);verify(f.m,never()).operation(anyMap());verifyNoInteractions(published.writer);
 }
 @Test void unexpectedOuterFieldRejectedBeforeCurrentProof()throws Exception{var value=outer().put("retry",true);assertThrows(SchemaRejection.class,()->history.service.recoverDisable(history.subject,"1",bytes(value)));verifyNoInteractions(published.writer);noWrites();}
 @Test void duplicateFieldsAndTrailingJsonRejectedBeforeCurrentProof()throws Exception{String value=f.formal.canonical(outer());assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->history.service.recoverDisable(history.subject,"1",(value+"{}").getBytes()));assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->history.service.recoverDisable(history.subject,"1",value.replace("\"original_key\":", "\"original_key\":\"duplicate-disable-key\",\"original_key\":").getBytes()));verifyNoInteractions(published.writer);noWrites();}
}

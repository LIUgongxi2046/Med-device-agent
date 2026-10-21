package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.TaskProfessionalPromptV2Test.*;

/** Production read orchestration with explicit local SQL/rights/storage doubles.
 * No fixture is an actual business approval, registered object or model-send permission. */
class TaskProfessionalPredecessorEvidenceTest {
    final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class,RETURNS_DEEP_STUBS);
    final TaskProfessionalHostAccess hosts=mock(TaskProfessionalHostAccess.class);final TaskProfessionalHostAccess.Handle handle=mock(TaskProfessionalHostAccess.Handle.class);
    final TaskNodeInputAuthority inputs=mock(TaskNodeInputAuthority.class);final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
    final TaskResearchCompletionAuthority completions=mock(TaskResearchCompletionAuthority.class);final TaskResearchCompletionAuthority.Verified complete=mock(TaskResearchCompletionAuthority.Verified.class);
    final TaskProfessionalObjectRegistration objects=mock(TaskProfessionalObjectRegistration.class);final TaskProfessionalObjectRegistration.OriginalProduced original=mock(TaskProfessionalObjectRegistration.OriginalProduced.class);
    final TaskProfessionalContentAuthority contents=mock(TaskProfessionalContentAuthority.class);final TaskProfessionalContentAuthority.Verified content=mock(TaskProfessionalContentAuthority.Verified.class);
    final TaskNodeProducerAuthority producers=mock(TaskNodeProducerAuthority.class);final TaskNodeProducerAuthority.LeasedProduction producer=mock(TaskNodeProducerAuthority.LeasedProduction.class);
    final TaskProfessionalSupervisor supervisor=mock(TaskProfessionalSupervisor.class);final TaskProfessionalRuntimeService.Deployment deployment=mock(TaskProfessionalRuntimeService.Deployment.class);
    final SourceUploadTransport transport=mock(SourceUploadTransport.class);final SourceReadAuthority sources=mock(SourceReadAuthority.class);final JdbcTemplate db=mock(JdbcTemplate.class);
    final AtomicInteger physicalReads=new AtomicInteger(),sourceSends=new AtomicInteger();ObjectNode manifest,p,body,receipt,replay;InterfaceProtocol.ObjectRef object;byte[] physical;
    TaskProfessionalPredecessorEvidence service;boolean badPhysical,denyAfterRead;
    @BeforeEach void setup()throws Exception {
        var json=new PermissionSchema();body=bundle();p=pin(body);manifest=input(p);manifest.put("snapshot_id",id(15));physical=ContentReadAuthorization.canonicalBytes(body);
        replay=json.object();replay.putObject("execution_policy").put("business_purpose","DOCUMENT_BODY").put("environment","LOCAL").put("region","LOCAL");manifest.put("replay_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(replay)));
        var fixed=new TaskNodeInputProtocol.SourceSelection(id(20),id(21),id(22),id(23),id(24),id(25),"DOCUMENT_BODY");manifest.set("fixed_sources",json.tree(List.of(fixed)));
        when(a.formalWorker()).thenReturn(true);when(a.actorId()).thenReturn(id(10));when(a.formalProof().identity().run()).thenReturn(id(8));when(a.formalProof().identity().step()).thenReturn(id(4));when(a.formalProof().identity().project()).thenReturn(id(6));
        when(input.manifest()).thenAnswer(c->manifest.deepCopy());when(input.tenantId()).thenReturn(id(5));when(input.projectId()).thenReturn(id(6));when(input.taskId()).thenReturn(id(7));when(input.runId()).thenReturn(id(8));when(input.stepId()).thenReturn(id(4));when(input.inputId()).thenReturn(id(11));when(input.inputSha256()).thenReturn("1".repeat(64));when(input.receiptSha256()).thenReturn("2".repeat(64));when(input.snapshotId()).thenReturn(id(15));when(input.fixedSources()).thenReturn(List.of(fixed));
        when(inputs.require(a,id(8),id(4))).thenReturn(input);when(completions.require(a,id(8),id(1))).thenReturn(complete);
        when(complete.resultArtifactId()).thenReturn(id(2));when(complete.resultSha256()).thenReturn("a".repeat(64));when(complete.resultObjectVersion()).thenReturn("result-original");when(complete.resultReceiptSha256()).thenReturn("b".repeat(64));when(complete.evidenceArtifactId()).thenReturn(id(3));when(complete.evidenceSha256()).thenReturn(p.required("evidence_sha256").textValue());when(complete.evidenceObjectVersion()).thenReturn("evidence-original");when(complete.evidenceReceiptSha256()).thenReturn("c".repeat(64));when(complete.completionReceiptSha256()).thenReturn("d".repeat(64));
        object=new InterfaceProtocol.ObjectRef("tenants/"+id(5)+"/evidence", "evidence-original",p.required("evidence_sha256").textValue(),Integer.toString(physical.length),"application/json");
        receipt=json.object();for(String f:List.of("tenant_id","project_id","task_id","run_id"))receipt.set(f,manifest.required(f));receipt.put("created_by",id(10)).put("kind","EVIDENCE_BUNDLE").put("step_id",id(1)).put("artifact_id",id(3));receipt.set("content_roots",json.tree(List.of(id(20),id(21),id(22))));receipt.putObject("content_manifest").set("members",json.tree(List.of(new ArtifactRegistryProtocol.MemberRow(id(20),id(25)),new ArtifactRegistryProtocol.MemberRow(id(21),id(25)),new ArtifactRegistryProtocol.MemberRow(id(22),id(25)))));
        // Explicit original evidence publication, independent of the V2 prompt/profile encoding.
        var catalogue=SchemaCatalogue.fromClasspath();
        receipt.put("schema_ref",catalogue.schemaRef("artifacts","EvidenceBundle")).put("payload_schema_sha256",catalogue.schemaDigest("artifacts"));
        when(objects.readOriginalProduced(a,id(3),"EVIDENCE_BUNDLE",supervisor,deployment)).thenReturn(original);when(original.reference()).thenReturn(object);when(original.bytes()).thenAnswer(c->ContentReadAuthorization.canonicalBytes(body));when(original.receiptSha256()).thenReturn("c".repeat(64));when(original.receipt()).thenAnswer(c->receipt.deepCopy());
        when(contents.require(input)).thenReturn(content);when(content.roots()).thenReturn(List.of(id(20),id(21),id(22)));when(content.members()).thenReturn(List.of(new ArtifactRegistryProtocol.MemberRow(id(20),id(25)),new ArtifactRegistryProtocol.MemberRow(id(21),id(25)),new ArtifactRegistryProtocol.MemberRow(id(22),id(25))));
        when(producers.requireLeased(a,input)).thenReturn(producer);when(deployment.maximumBytes()).thenReturn(1048576);
        when(hosts.execute(eq(handle),any())).thenAnswer(c->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());TransactionSynchronizationManager.setActualTransactionActive(true);try{return ((Function<EnterpriseAccess.Admission,?>)c.getArgument(1)).apply(a);}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}});
        when(transport.readTaskDocument(eq(id(5)),eq(id(10)),anyInt(),any(),any())).thenAnswer(c->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());physicalReads.incrementAndGet();S3ObjectReader.Admission guard=c.getArgument(3);guard.requireCurrent();var reader=mock(S3ObjectReader.class);var read=mock(S3ObjectReader.VerifiedObject.class);when(reader.readVerified(eq(object),any())).thenReturn(read);when(read.reference()).thenReturn(object);when(read.fullSha256()).thenReturn(object.sha256());when(read.chunk(anyLong(),anyInt())).thenAnswer(chunk->{byte[] bytes=physical.clone();if(badPhysical)bytes[0]='!';if(denyAfterRead)doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(input).requireUnchanged();return Arrays.copyOfRange(bytes,Math.toIntExact(chunk.getArgument(0,Long.class)),Math.toIntExact(chunk.getArgument(0,Long.class))+chunk.getArgument(1,Integer.class));});var timed=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){guard.requireCurrent();}public java.time.Duration remainingTime(){return java.time.Duration.ofSeconds(5);}};return ((SourceUploadTransport.DocumentRead<?>)c.getArgument(4)).apply(reader,timed);});
        when(db.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class))).thenAnswer(c->List.of(json.canonical(replay)));
        when(sources.require(eq(a),any())).thenAnswer(c->{SourceReadProtocol.ReadRequirement r=c.getArgument(1);assertEquals(SourceReadProtocol.Use.MODEL_SEND,r.use());assertEquals("provider-test",r.provider());assertEquals(id(25),r.rightsRevision());sourceSends.incrementAndGet();throw new TenantAccessException(ErrorCode.ACCESS_DENIED);});
        service=new TaskProfessionalPredecessorEvidence(db,inputs,completions,objects,contents,producers,supervisor,deployment,transport,sources,new TaskProfessionalSchema(),json);
    }
    @AfterEach void cleanup(){TransactionSynchronizationManager.setActualTransactionActive(false);}
    @Test void physicalExactReadAndEveryLaterProjectionRecheckCurrentProof()throws Exception {
        var read=service.readWorker(hosts,handle);assertEquals(1,physicalReads.get());var document=hosts.execute(handle,admission->org.springframework.util.function.ThrowingSupplier.of(()->read.document(admission)).get());assertEquals(body,document.get(0).required("evidence_bundle"));verify(producer,atLeastOnce()).requireHostPredecessorConsume(eq(input),eq(object.sizeBytes()));verifyNoInteractions(sources);
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(input).requireUnchanged();assertThrows(TenantAccessException.class,()->hosts.execute(handle,admission->org.springframework.util.function.ThrowingSupplier.of(()->read.document(admission)).get()));
    }
    @Test void copiedSqlPayloadCannotReplaceActualGet(){badPhysical=true;assertThrows(TenantAccessException.class,()->service.readWorker(hosts,handle));}
    @Test void sourceOrLeaseRevocationDuringReadPreventsCapability(){denyAfterRead=true;assertThrows(TenantAccessException.class,()->service.readWorker(hosts,handle));}
    @Test void missingCompletionOrChangedReceiptDeniesBeforeIo()throws Exception {when(complete.completionReceiptSha256()).thenReturn("f".repeat(64));assertThrows(TenantAccessException.class,()->service.readWorker(hosts,handle));verifyNoInteractions(transport);}
    @Test void absentExplicit098ReadOperationDeniesBeforeIo()throws Exception{doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(producer).requireHostPredecessorConsume(any(),anyString());assertThrows(TenantAccessException.class,()->service.readWorker(hosts,handle));verifyNoInteractions(transport);}
    @Test void registeredReadNeverSubstitutesForCurrentProviderSpecificModelSend()throws Exception{var read=service.readWorker(hosts,handle);TransactionSynchronizationManager.setActualTransactionActive(true);assertThrows(TenantAccessException.class,()->service.requireCurrentPins(a,input,read.canonicalPins(),"DOCUMENT_BODY","provider-test"));assertEquals(1,sourceSends.get());}
    @Test void alteredPersistedPinsCannotAuthorizeModelSend()throws Exception{var read=service.readWorker(hosts,handle);var pins=read.canonicalPins();((ObjectNode)pins.get(0).required("predecessor")).put("evidence_receipt_sha256","9".repeat(64));TransactionSynchronizationManager.setActualTransactionActive(true);assertThrows(TenantAccessException.class,()->service.requireCurrentPins(a,input,pins,"DOCUMENT_BODY","provider-test"));verifyNoInteractions(sources);}
    @Test void wholeEvidenceBudgetAndNotOnlyPerObjectLimitIsEnforced()throws Exception{when(deployment.maximumBytes()).thenReturn(1);assertThrows(TenantAccessException.class,()->service.readWorker(hosts,handle));verifyNoInteractions(transport);}
}

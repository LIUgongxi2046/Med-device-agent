package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual parser provenance and source receipt orchestration. Authority, SQL and transport are explicit local doubles. */
class TaskResearchSourceReaderTest {
    final TaskFieldSourceProvenanceTest fixture=new TaskFieldSourceProvenanceTest();
    final FormalWorkerAccess workers=mock(FormalWorkerAccess.class);final TaskNodeInputAuthority inputs=mock(TaskNodeInputAuthority.class);
    final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
    final SourceReadAuthority sources=mock(SourceReadAuthority.class);final TaskFieldSourceAuthority processing=mock(TaskFieldSourceAuthority.class);
    final SourceUploadTransport transport=mock(SourceUploadTransport.class);final ContentReadMapper receipts=mock(ContentReadMapper.class);final JdbcTemplate db=mock(JdbcTemplate.class);
    final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class,RETURNS_DEEP_STUBS);final HttpServletRequest http=mock(HttpServletRequest.class);
    final String tenant=id(1),project=id(202),actor=id(203),run=id(204),step=id(205),session=id(206),question=id(207),snapshot=id(208);
    final AtomicReference<ContentReadProtocol.AuditRow> stored=new AtomicReference<>();final AtomicInteger transfers=new AtomicInteger(),chunks=new AtomicInteger();
    final LocalDateTime now=LocalDateTime.of(2026,10,20,1,0);
    TaskResearchSourceReader reader;TaskResearchToolAccess tools;TaskResearchToolAccess.Handle handle;DatasetProtocol.EvidenceRef ref;ObjectNode manifest;
    boolean revoked;
    @BeforeEach void setup() throws Exception {
        fixture.fixture();var json=fixture.json;
        var pin=new TaskNodeInputProtocol.SourceSelection(id(4),id(5),id(210),id(211),id(212),id(213),"DOCUMENT_BODY");
        manifest=json.object().put("tenant_id",tenant).put("project_id",project).put("task_id",id(214)).put("run_id",run).put("step_id",step).put("snapshot_id",snapshot)
                .put("graph_id",id(215)).put("graph_sha256","a".repeat(64)).put("question_id",question).put("question_text","Exact question?");
        manifest.set("fixed_sources",json.tree(List.of(pin)));
        when(input.manifest()).thenAnswer(call->manifest.deepCopy());when(input.tenantId()).thenReturn(tenant);when(input.projectId()).thenReturn(project);when(input.runId()).thenReturn(run);when(input.stepId()).thenReturn(step);
        when(input.graphId()).thenReturn(id(215));when(input.graphSha256()).thenReturn("a".repeat(64));when(input.inputId()).thenReturn(id(216));when(input.inputSha256()).thenReturn("b".repeat(64));
        when(input.receiptSha256()).thenReturn("c".repeat(64));when(input.questionId()).thenReturn(question);when(input.questionText()).thenReturn("Exact question?");when(input.snapshotId()).thenReturn(snapshot);when(input.fixedSources()).thenReturn(List.of(pin));
        when(a.formalWorker()).thenReturn(true);when(a.actorId()).thenReturn(actor);when(a.formalProof().identity().executionKind()).thenReturn("TASK");
        when(a.formalProof().identity().workerService()).thenReturn("spiffe://medical/runtime");when(a.formalProof().identity().generation()).thenReturn("1");
        when(inputs.require(a,run,step)).thenAnswer(call->{if(revoked)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return input;});
        when(workers.executeAction(eq(http),eq("token-double"),any(),any())).thenAnswer(call->((BiFunction<EnterpriseAccess.Admission,RunTokenAuthority.Admission,?>)call.getArgument(3)).apply(a,null));
        var source=new SourceReadProtocol.SourceRow(tenant,id(217),id(4),null,"ENTERPRISE","INTERNAL",id(213),fixture.original.sha256(),fixture.original.sha256(),null);
        var selection=new SourceReadProtocol.SelectionRow(tenant,project,snapshot,id(217),id(4),id(5),id(210),id(211),id(212),"COMPLETE","READY",BigInteger.ONE,BigInteger.ONE,id(211),"RECEIVED","APPROVED","ACTIVE",BigInteger.ONE,"APPROVE","d".repeat(64),"APPLICABLE","ACTIVE");
        when(sources.require(eq(a),any())).thenAnswer(call->{if(revoked)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return new SourceReadProtocol.VerifiedSource(source,null,selection,null,null,null,null);});
        when(processing.processing(tenant,id(4),id(5),fixture.original.sha256())).thenAnswer(call->fixture.processing());
        when(db.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);if(sql.contains("professional_runtime_session"))return List.of(session);
            if(sql.contains("step_action_binding"))return List.of(stored.get().requestId());throw new AssertionError(sql);
        });
        when(receipts.databaseTime(anyMap())).thenReturn(now);
        when(receipts.saveAudit(anyMap())).thenAnswer(call->{Map<String,Object> v=call.getArgument(0);stored.set((ContentReadProtocol.AuditRow)v.get("audit"));return 1;});
        when(receipts.audit(anyMap())).thenAnswer(call->{Map<String,Object> v=call.getArgument(0);return stored.get()!=null&&stored.get().auditId().equals(v.get("auditId"))?List.of(stored.get()):List.of();});
        when(transport.readTaskDocument(eq(tenant),eq(actor),anyInt(),any(),any())).thenAnswer(call->{
            transfers.incrementAndGet();S3ObjectReader.Admission guard=call.getArgument(3);
            var timed=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){guard.requireCurrent();}public java.time.Duration remainingTime(){return java.time.Duration.ofSeconds(10);}};
            var physical=mock(S3ObjectReader.class);
            when(physical.readVerified(any(),any())).thenAnswer(read->{
                InterfaceProtocol.ObjectRef reference=read.getArgument(0);var object=mock(S3ObjectReader.VerifiedObject.class);
                byte[] bytes=reference.equals(fixture.original)?new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7', (byte)0xff, (byte)0xfe}:fixture.bytes;
                when(object.reference()).thenReturn(reference);when(object.fullSha256()).thenReturn(reference.sha256());
                when(object.chunk(anyLong(),anyInt())).thenAnswer(chunk->{chunks.incrementAndGet();int start=Math.toIntExact(chunk.getArgument(0,Long.class));int length=chunk.getArgument(1);return Arrays.copyOfRange(bytes,start,start+length);});return object;
            });
            return ((SourceUploadTransport.DocumentRead<?>)call.getArgument(4)).apply(physical,timed);
        });
        var policy=new RetrievalPolicy("LOCAL","LOCAL",100);
        reader=new TaskResearchSourceReader(processing,transport,receipts,json,sources,policy,db);
        tools=new TaskResearchToolAccess(workers,inputs,mock(RetrievalService.class),sources,mock(SimulationFixtureMapper.class),transport,policy,reader);
        var evidence=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("source_id",id(217)).put("source_version",id(4)).put("parse_version",id(5))
                .put("chunk_id",id(9)).put("locator_kind","PDF").put("quote_hash",TaskFieldSourceProvenanceTest.hash(fixture.raw)).put("approval_revision",id(211));evidence.set("locator",fixture.locator);
        ref=TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),DatasetBindings.EVIDENCE_REF,evidence).read();
        var action=new RunTokenAuthority.ActionRequest(run,step,id(218),BigInteger.ONE,"b".repeat(64),TaskResearchToolAccess.argumentsSha256("AT-02:read",TaskResearchSourceReader.requestSha256(ref,null)),"TOOL","AT-02-v1",new PermissionProtocol.Scope(tenant,null,"SOURCE",id(217)),"source.read","DOCUMENT_BODY");
        handle=tools.open(http,"token-double",action);
    }
    @Test void genuineOriginalAndParsedBytesProduceTypedDurableReceiptWithDistinctProcessingOwner() throws Exception {
        var result=tools.readSource(handle,ref,null);assertEquals(fixture.raw,result.content());assertEquals(2,transfers.get());assertTrue(chunks.get()>=2);
        assertNotNull(stored.get());assertEquals(TaskResearchSourceReader.ACTION,stored.get().action());assertEquals(result.readReceiptId(),stored.get().auditId());
        var receipt=new TaskSourceReadReceiptSchema(fixture.json).read(stored.get().resource());
        assertEquals(id(10),receipt.at("/body/processing_project_id").textValue());assertEquals(project,receipt.at("/body/identity/node_input/project_id").textValue());
        assertEquals(session,receipt.at("/body/identity/session_id").textValue());assertEquals(fixture.original.sha256(),receipt.at("/body/original_object/sha256").textValue());
        assertEquals(fixture.parsed.sha256(),receipt.at("/body/parsed_object/sha256").textValue());assertTrue(result.complete());
        reader.requireReceipts(a,input,List.of(result.readReceiptId()),List.of(ref)).requireUnchanged();
        verify(receipts,never()).saveHeader(anyMap());verify(receipts,never()).saveRange(anyMap());
        revoked=true;assertThrows(TenantAccessException.class,result::content);
    }
    @Test void foreignOrChangedEvidenceAndMissingReceiptCannotClaimReadCoverage() throws Exception {
        assertThrows(TenantAccessException.class,()->reader.requireReceipts(a,input,List.of(id(999)),List.of(ref)));
        assertThrows(TenantAccessException.class,()->reader.requireReceipts(a,input,List.of(),List.of(ref)));
        var changed=new DatasetProtocol.EvidenceRef(ref.schemaVersion(),ref.tenantId(),ref.projectId(),ref.sourceId(),ref.sourceVersion(),ref.parseVersion(),ref.chunkId(),ref.locatorKind(),ref.locator(),"f".repeat(64),ref.approvalRevision());
        assertThrows(TenantAccessException.class,()->reader.literal(fixture.processing(),changed,fixture.bytes));verify(receipts,never()).saveAudit(anyMap());
    }
    @Test void forgedGenericAuditReceiptAndHistoricalTokenMismatchAreRejected() throws Exception {
        var result=tools.readSource(handle,ref,null);String id=result.readReceiptId();var row=stored.get();
        stored.set(new ContentReadProtocol.AuditRow(row.tenantId(),row.projectId(),row.auditId(),row.subjectId(),"source.content.read",row.resource(),row.result(),row.reasonCode(),row.requestId(),row.traceId(),row.schemaVersion(),row.schemaManifest(),row.createdBy(),row.createdAt(),row.classification()));
        assertThrows(TenantAccessException.class,()->reader.requireReceipts(a,input,List.of(id),List.of(ref)));stored.set(row);
        when(db.query(contains("step_action_binding"),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class))).thenReturn(List.of());
        assertThrows(TenantAccessException.class,()->reader.requireReceipts(a,input,List.of(id),List.of(ref)));
    }
    @Test void locatorRoundTripIsNumericTypeIndependentButRejectsAdjacentLargeIndices() throws Exception {
        assertEquals(fixture.raw,reader.literal(fixture.processing(),ref,fixture.bytes));
        long exact=9007199254740992L;
        fixture.locator.put("page_index",exact);fixture.refresh();
        var exactRef=withPage(exact);var adjacent=withPage(exact+1);
        assertThrows(IllegalArgumentException.class,()->reader.literal(fixture.processing(),exactRef,fixture.bytes));
        assertThrows(IllegalArgumentException.class,()->reader.literal(fixture.processing(),adjacent,fixture.bytes));
        assertNotEquals(TaskResearchSourceReader.requestSha256(exactRef,null),TaskResearchSourceReader.requestSha256(adjacent,null));
        assertNotEquals(TaskSourceIdentity.sha256(exactRef),TaskSourceIdentity.sha256(adjacent));
        assertTrue(TaskSourceIdentity.same(fixture.json.tree(exactRef.locator()),fixture.locator));
        fixture.locator.put("page_index",exact+1);fixture.refresh();
        // The processor's existing physical JCS format rounds this value. Never accept that changed locator.
        assertThrows(IllegalArgumentException.class,()->reader.literal(fixture.processing(),adjacent,fixture.bytes));
    }
    @Test void numericIdentityNormalizesRoundTripsWithoutTagCollisions() throws Exception {
        var small=fixture.json.object().put("n",1);var roundTrip=fixture.json.object().put("n",1L);
        assertTrue(TaskSourceIdentity.same(small,roundTrip));assertEquals(TaskSourceIdentity.sha256(small),TaskSourceIdentity.sha256(roundTrip));
        var large=fixture.json.object().put("n",9007199254740992L);var adjacent=fixture.json.object().put("n",9007199254740993L);
        assertFalse(TaskSourceIdentity.same(large,adjacent));assertNotEquals(TaskSourceIdentity.sha256(large),TaskSourceIdentity.sha256(adjacent));
        assertNotEquals(TaskSourceIdentity.sha256(1),TaskSourceIdentity.sha256(List.of("number","1")));
        var decimal=fixture.json.object().put("n",new java.math.BigDecimal("0.1250"));
        var same=fixture.json.object().put("n",new java.math.BigDecimal("0.125"));
        assertTrue(TaskSourceIdentity.same(decimal,same));assertEquals(TaskSourceIdentity.sha256(decimal),TaskSourceIdentity.sha256(same));
    }
    @Test void receiptSealRejectsLossyPersistenceOfValidatedLargeLocator() throws Exception {
        tools.readSource(handle,ref,null);
        var schema=new TaskSourceReadReceiptSchema(fixture.json);
        var body=(ObjectNode)schema.read(stored.get().resource()).required("body").deepCopy();
        var adjacent=withPage(9007199254740993L);
        body.set("evidence_ref",fixture.json.tree(adjacent));
        ((ObjectNode)body.required("request")).set("evidence_ref",fixture.json.tree(adjacent));
        body.put("scope_sha256",TaskSourceIdentity.sha256(adjacent));
        assertThrows(IllegalArgumentException.class,()->schema.seal(body));
    }
    private DatasetProtocol.EvidenceRef withPage(long page) throws Exception {
        var old=(DatasetProtocol.PdfLocator)ref.locator();
        var locator=new DatasetProtocol.PdfLocator(old.kind(),page,old.pageBasis(),old.printedLabel(),old.region());
        return new DatasetProtocol.EvidenceRef(ref.schemaVersion(),ref.tenantId(),ref.projectId(),ref.sourceId(),ref.sourceVersion(),ref.parseVersion(),ref.chunkId(),ref.locatorKind(),locator,ref.quoteHash(),ref.approvalRevision());
    }
    static String id(int value){return TaskFieldSourceProvenanceTest.id(value);}
}

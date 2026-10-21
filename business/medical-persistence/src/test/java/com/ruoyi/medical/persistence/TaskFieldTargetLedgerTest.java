package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the real immutable ledger with explicit JDBC/authority boundary doubles. These tests
 * do not claim a real database, real permission grant, S3 transfer or approved business result. */
class TaskFieldTargetLedgerTest {
    @Test void explicitConfiguredByteBoundRejectsBeforeAnyIntentOrJournalWrite()throws Exception{
        var f=new Fixture(true);var limited=new TaskFieldTargetLedger(f.db,f.schema,f.json,f.reader,f.tasks,()->1);
        var failure=assertThrows(TenantAccessException.class,()->f.tx(()->limited.prepare(f.basis,f.mapping,f.reads,f.request,f.key,"1")));
        assertEquals(ErrorCode.FILE_TOO_LARGE,failure.code());assertTrue(f.writes.isEmpty());
    }
    @Test void emptyInventoryIsPersistedHonestlyWithNoFabricatedOccurrenceOrArtifact() throws Exception {
        var f=new Fixture();var first=f.prepare();
        assertTrue(first.targetManifest().required("inventory").isEmpty());
        assertTrue(first.targetManifest().at("/target/entries").isEmpty());
        assertTrue(first.targetManifest().required("entry_bindings").isEmpty());
        assertTrue(first.targetManifest().required("run_id").isNull());
        assertTrue(first.targetManifest().required("document_revision").isNull());
        assertEquals("PROPOSED",first.targetManifest().at("/target/assessment").textValue());
        assertEquals(1,f.rows("task_field_target_intent").size());assertEquals(1,f.rows("task_field_target_attempt").size());
        assertTrue(f.rows("task_field_target_entry").isEmpty());assertTrue(f.rows("artifact").isEmpty());
        assertEquals("PROCESSING",f.only("operation_record").get("state"));
        assertEquals(2,f.rows("audit_event").size());assertEquals(2,f.rows("outbox_event").size());
        assertEquals(7,first.targetManifest().required("governance_pins").size());
        assertTrue(first.targetManifest().required("source_lineage").isEmpty());
        verify(f.basis,atLeastOnce()).requireAction(TaskFieldTargetProtocol.TARGET_PRODUCE);
        verifyNoInteractions(f.reader);
    }
    @Test void genuineReadBoundaryProducesOnlyLiteralUnicodeTextAndStableOccurrenceIds() throws Exception {
        var f=new Fixture(true);var first=f.prepare();var target=first.targetManifest();var entry=target.at("/target/entries/0");
        assertEquals(" x😀 ",entry.at("/candidate_value/raw_text").textValue());
        assertEquals(6,entry.at("/provenance/start_codepoint").intValue());assertEquals(10,entry.at("/provenance/end_codepoint").intValue());
        assertNotEquals(entry.required("field_definition_id"),entry.required("field_id"));
        assertEquals(Fixture.id(29),entry.at("/provenance/read_receipt_ids/0").textValue());
        assertEquals(1,f.rows("task_field_target_entry").size());assertEquals(3,target.required("source_lineage").size());
        assertEquals(7,target.required("governance_pins").size());
        verify(f.reader,atLeastOnce()).requireCurrent(f.admission,"1",f.reads.get(0));
        var repeated=f.prepare();assertEquals(target,repeated.targetManifest());assertEquals(first.writeId(),repeated.writeId());
        assertEquals(entry.required("field_id"),repeated.targetManifest().at("/target/entries/0/field_id"));
        assertNotEquals(Fixture.id(89),first.actorId());
    }
    @Test void sameTemplateFieldInTwoSectionsPreservesMappedAndUnresolvedInventoriesSeparately() throws Exception {
        var f=new Fixture(true,true);var result=f.prepare().targetManifest();
        assertEquals(2,result.required("inventory").size());assertEquals(2,result.at("/target/sections").size());
        assertEquals("MAPPED",result.at("/inventory/0/state").textValue());assertEquals("MISSING_MAPPING",result.at("/inventory/1/state").textValue());
        assertEquals(result.at("/inventory/0/field_definition_id"),result.at("/inventory/1/field_definition_id"));
        assertEquals(1,result.at("/target/entries").size());assertEquals(Fixture.id(8),result.at("/target/entries/0/section_id").textValue());
        assertEquals(1,f.rows("task_field_target_entry").size());
    }
    @Test void sourceReadAndStoredLocatorChangesStopBeforeAnyIntentWrite() throws Exception {
        var f=new Fixture(true);doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(f.reader).requireCurrent(f.admission,"1",f.reads.get(0));
        assertThrows(TenantAccessException.class,f::prepare);assertTrue(f.writes.isEmpty());
        f=new Fixture(true);f.only("source_chunk").put("raw_text","altered literal");
        assertThrows(TenantAccessException.class,f::prepare);assertTrue(f.writes.isEmpty());
    }
    @Test void readReceiptMustMatchActorMappingAndEveryMappedEntryExactly() throws Exception {
        var f=new Fixture(true);f.reads=List.of();assertThrows(TenantAccessException.class,f::prepare);assertTrue(f.writes.isEmpty());
        f=new Fixture(true);f.reads=List.of(f.reads.get(0),f.reads.get(0));assertThrows(TenantAccessException.class,f::prepare);assertTrue(f.writes.isEmpty());
        f=new Fixture(true);var receipt=(ObjectNode)f.reads.get(0).manifest();receipt.put("actor_id",Fixture.id(89)).put("created_by",Fixture.id(89));
        when(f.reads.get(0).manifest()).thenReturn(receipt);when(f.reads.get(0).receiptSha256()).thenReturn(f.schema.hash(receipt));
        assertThrows(TenantAccessException.class,f::prepare);assertTrue(f.writes.isEmpty());
    }
    @Test void onlyFirstCommittedClaimCanPutEveryRetryRecoversSameObject() throws Exception {
        var f=new Fixture();var first=f.prepare();
        assertEquals(RuntimeObjectAttempt.TransferMode.INITIAL_WRITE,first.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,first.claimTransferMode());
        var again=f.prepare();var recovered=f.recover();
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,again.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,recovered.claimTransferMode());
        assertEquals(first.writeId(),again.writeId());assertEquals(first.plannedArtifactId(),recovered.plannedArtifactId());
        assertEquals(first.attemptId(),recovered.attemptId());assertArrayEquals(first.bytes(),recovered.bytes());
        assertEquals(1,f.rows("task_field_target_intent").size());assertEquals(1,f.rows("task_field_target_attempt").size());
    }
    @Test void missingIntentRecoveryIsReadOnlyAndCannotCreateAnything() throws Exception {
        var f=new Fixture();assertThrows(TenantAccessException.class,f::recover);
        assertTrue(f.writes.isEmpty());
    }
    @Test void existingIntentWithMissingAttemptCannotEverCreateReplacementAttempt() throws Exception {
        var f=new Fixture();f.prepare();f.rows("task_field_target_attempt").clear();f.only("operation_record").put("revision",1);
        f.writes.clear();assertThrows(TenantAccessException.class,f::prepare);assertTrue(f.writes.isEmpty());
        assertThrows(TenantAccessException.class,f::recover);assertTrue(f.writes.isEmpty());
    }
    @Test void secondKeyCannotReplaceAnUncertainObjectForSameActorAndMapping() throws Exception {
        var f=new Fixture();f.prepare();f.key="replacement-target-key-00002";
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,f::prepare).code());
        assertEquals(1,f.rows("task_field_target_intent").size());
    }
    @Test void alteredOriginalRequestNeverReusesOriginalKey() throws Exception {
        var f=new Fixture();f.prepare();f.request.put("reason","A changed original reason");
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,f::prepare).code());
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,f::recover).code());
    }
    @Test void beforeCommitAndRollbackCannotAuthorizePhysicalIo() throws Exception {
        var f=new Fixture();f.begin();TaskFieldTargetLedger.Attempt attempt;
        try {
            attempt=f.ledger.prepare(f.basis,f.mapping,List.of(),f.request,f.key,"1");
            assertThrows(TenantAccessException.class,attempt::claimTransferMode);
            f.complete(false);
        } finally {f.end();}
        assertThrows(TenantAccessException.class,attempt::claimTransferMode);
    }
    @Test void liveTransactionAndOtherThreadCannotConsumeCommittedInitialWrite() throws Exception {
        var f=new Fixture();var attempt=f.prepare();
        f.begin();try {assertThrows(TenantAccessException.class,attempt::claimTransferMode);}finally {f.end();}
        var failure=new AtomicReference<Throwable>();var thread=new Thread(()->{try{attempt.claimTransferMode();}catch(Throwable t){failure.set(t);}});
        thread.start();thread.join();assertInstanceOf(TenantAccessException.class,failure.get());
        assertEquals(RuntimeObjectAttempt.TransferMode.INITIAL_WRITE,attempt.claimTransferMode());
    }
    @Test void targetAndByteAccessorsCannotMutateIntentEvidence() throws Exception {
        var f=new Fixture();var attempt=f.prepare();var before=attempt.bytes();
        ((ObjectNode)attempt.targetManifest()).put("owner_kind","RUN");
        ((ObjectNode)attempt.intent()).put("planned_artifact_id",Fixture.id(999));attempt.bytes()[0]='X';
        assertArrayEquals(before,attempt.bytes());assertEquals("TASK",attempt.targetManifest().required("owner_kind").textValue());
        assertEquals(attempt.expectedObjectKey(),attempt.writeIntent().objectKey());
        assertNotEquals(Fixture.id(999),attempt.plannedArtifactId());
        assertTrue(java.lang.reflect.Modifier.isPrivate(TaskFieldTargetLedger.Attempt.class.getDeclaredConstructors()[0].getModifiers()));
    }
    @Test void missingOrCorruptedIntentJournalFailsBeforeTransportAuthority() throws Exception {
        var f=new Fixture();f.prepare();f.rows("audit_event").remove(0);
        assertThrows(TenantAccessException.class,f::recover);
        f=new Fixture();f.prepare();f.only("task_field_target_attempt").put("intent_sha256","b".repeat(64));
        assertThrows(TenantAccessException.class,f::recover);
    }
    @Test void alteredPayloadAndExtraneousEntryCannotHideBehindFilteredRows() throws Exception {
        var f=new Fixture();f.prepare();var payload=(ObjectNode)f.schema.read((String)f.only("task_field_target_intent").get("canonical_payload"));
        payload.put("owner_kind","RUN");f.only("task_field_target_intent").put("canonical_payload",f.schema.canonical(payload));
        assertThrows(TenantAccessException.class,f::recover);
        f=new Fixture();var attempt=f.prepare();var unexpected=new LinkedHashMap<String,Object>();
        unexpected.put("tenant_id",f.tenant);unexpected.put("write_id",attempt.writeId());unexpected.put("entry_id",Fixture.id(800));
        unexpected.put("project_id",Fixture.id(801));f.rows("task_field_target_entry").add(unexpected);
        assertThrows(TenantAccessException.class,f::recover);
    }
    @Test void changedAuthorityAndForeignLedgerCannotReuseProof() throws Exception {
        var f=new Fixture();var attempt=f.prepare();
        when(f.basis.authorizationsSha256()).thenReturn("b".repeat(64));
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,f::recover).code());
        var another=new TaskFieldTargetLedger(f.db,f.schema,f.json,f.reader);
        assertThrows(TenantAccessException.class,()->f.tx(()->another.require(f.basis,f.mapping,List.of(),attempt,"1")));
    }
    @Test void wrongProducerActionAndStaleMappingFailBeforeAnyPersistence() throws Exception {
        var f=new Fixture();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.basis).requireAction(TaskFieldTargetProtocol.TARGET_PRODUCE);
        assertThrows(TenantAccessException.class,f::prepare);assertTrue(f.writes.isEmpty());
        f=new Fixture();doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(f.mapping).requireUnchanged();
        assertThrows(TenantAccessException.class,f::prepare);assertTrue(f.writes.isEmpty());
    }
    @Test void originalAndMappingMetadataLookupsAreGatedAndNeverMintTransferAuthority() throws Exception {
        var f=new Fixture();assertTrue(f.tx(()->f.ledger.findOriginal(f.admission,f.task,f.key)).isEmpty());assertTrue(f.writes.isEmpty());
        var attempt=f.prepare();f.writes.clear();
        var original=f.tx(()->f.ledger.findOriginal(f.admission,f.task,f.key)).orElseThrow();
        var selected=f.tx(()->f.ledger.findForMapping(f.admission,f.task,f.mapping.mappingRevision(),f.mapping.mappingSha256())).orElseThrow();
        assertEquals(attempt.intent(),original.intent());assertEquals(original.manifest(),selected.manifest());assertTrue(f.writes.isEmpty());
        verify(f.tasks,atLeastOnce()).fixedTask(f.admission,f.task);
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.tasks).fixedTask(f.admission,f.task);
        assertThrows(TenantAccessException.class,()->f.tx(()->f.ledger.findOriginal(f.admission,f.task,f.key)));assertTrue(f.writes.isEmpty());
    }
    @Test void historicalIntegrityUsesOriginalDatabaseActorWithoutImpersonatingViewer() throws Exception {
        var f=new Fixture();var attempt=f.prepare();f.writes.clear();
        var originalContext=f.admission.context();var originalTask=f.basis.task();var viewer=mock(EnterpriseAccess.Admission.class);when(viewer.context()).thenReturn(originalContext);when(viewer.actorId()).thenReturn(Fixture.id(91));
        when(f.tasks.fixedTask(viewer,f.task)).thenReturn(originalTask);
        f.begin(viewer.actorId());try {
            var historic=f.ledger.verifyStored(viewer,f.task,attempt.writeId());assertEquals(attempt.actorId(),historic.columns().get("created_by"));
            assertEquals(Fixture.id(91),viewer.actorId());assertTrue(f.ledger.findOriginal(viewer,f.task,f.key).isEmpty());
            assertTrue(f.writes.isEmpty());f.complete(true);
        }finally{f.end();}
        verify(f.tasks,atLeastOnce()).fixedTask(viewer,f.task);
    }
    @Test void rawCallerRecoveryAndTargetJsonAreNotAcceptedByLedgerApis() throws Exception {
        for(var method:TaskFieldTargetLedger.class.getDeclaredMethods())if(Set.of("prepare","recover","require").contains(method.getName())) {
            assertFalse(Arrays.asList(method.getParameterTypes()).contains(boolean.class));
            assertEquals(TaskFieldTargetBasis.Verified.class,method.getParameterTypes()[0]);
        }
        var f=new Fixture();f.request.put("target_manifest", "caller-generated-target");
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,f::prepare);assertTrue(f.writes.isEmpty());
    }

    static final class Fixture {
        static String id(int n){return TaskFieldTargetSchemaTest.id(n);}
        final String tenant=id(1),project=id(2),task=id(3),actor=id(90),hash="a".repeat(64);
        String key="original-target-key-00001";
        final LocalDateTime now=LocalDateTime.of(2026,10,20,0,0);
        final JdbcTemplate db=mock(JdbcTemplate.class);
        final PermissionSchema json=new PermissionSchema();
        final TaskFieldTargetSchema schema=new TaskFieldTargetSchema();
        final TaskFieldSourceReader reader=mock(TaskFieldSourceReader.class);
        final TaskFieldTargetBasis.Verified basis=mock(TaskFieldTargetBasis.Verified.class);
        final TaskFieldMappingService.Verified mapping=mock(TaskFieldMappingService.Verified.class);
        final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
        final TaskService tasks=mock(TaskService.class);
        final TaskFieldTargetLedger ledger=new TaskFieldTargetLedger(db,schema,json,reader,tasks);
        final Map<String,List<Map<String,Object>>> tables=new HashMap<>();
        final List<String> writes=new ArrayList<>();
        final ObjectNode mapped=TaskFieldTargetSchemaTest.mappingManifest();
        final ObjectNode request;
        List<TaskFieldSourceReader.Read> reads=List.of();
        TenantScope scope;
        Fixture() throws Exception {this(false);}
        Fixture(boolean literal) throws Exception {this(literal,false);}
        Fixture(boolean literal,boolean secondSection) throws Exception {
            if(!literal){mapped.putArray("mappings");mapped.putArray("inventory");}
            if(secondSection){var other=((ObjectNode)mapped.at("/inventory/0")).deepCopy().put("section_id",id(18)).put("state","MISSING_MAPPING");other.putArray("entry_keys");mapped.withArray("inventory").add(other);}
            String mappingHash=schema.hash(mapped);
            request=json.object().put("schema_version","1.0.0").put("project_id",project).put("task_revision",id(4)).put("plan_revision",id(5))
                    .put("assignment_id",id(7)).put("assignment_sha256",mappingHash).put("expected_revision","1").put("reason","Prepare exact proposed field target");
            when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));
            when(admission.actorId()).thenReturn(actor);when(admission.watermarks()).thenReturn(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
            when(basis.admission()).thenReturn(admission);when(basis.classification()).thenReturn("INTERNAL");when(basis.authorizationsSha256()).thenReturn(hash);
            var head=new TaskProtocol.Head(tenant,project,task,"READY",BigInteger.ONE,actor,id(4),id(60),id(61),id(6),id(62),id(5),hash,hash,now);
            var fixed=new TaskService.VerifiedTask(json.object(),head,null);
            when(basis.task()).thenReturn(fixed);when(tasks.fixedTask(admission,task)).thenReturn(fixed);
            var sections=new ArrayList<ArtifactProtocol.PlanSection>();sections.add(new ArtifactProtocol.PlanSection(id(8),List.of(id(9)),"Proposed",actor,null,List.of(),List.of()));
            if(secondSection)sections.add(new ArtifactProtocol.PlanSection(id(18),List.of(id(9)),"Also proposed",actor,null,List.of(),List.of()));
            var plan=new ArtifactProtocol.PlanCandidate(id(6),id(60),List.of(id(9)),sections,List.of(),List.of());
            when(basis.plan()).thenReturn(new TaskPlanWriteProtocol.View("1.0.0",tenant,project,task,id(4),id(5),id(61),id(6),id(60),"CONFIRMED",plan,hash,id(5),"1"));
            var grant=mock(PermissionProtocol.GrantRow.class);when(grant.grantId()).thenReturn(id(63));when(grant.revision()).thenReturn(BigInteger.ONE);when(basis.grant()).thenReturn(grant);
            when(mapping.manifest()).thenAnswer(inv->mapped.deepCopy());when(mapping.mappingRevision()).thenReturn(id(7));when(mapping.mappingSha256()).thenReturn(mappingHash);
            when(mapping.revision()).thenReturn("1");when(mapping.actorId()).thenReturn(id(89));
            for(int n:new int[]{4,5,6,7,60,61,62})rows("version_registry").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"version_id",id(n),"content_sha256",n==7?mappingHash:hash)));
            if(literal) {
                var receipt=new TaskFieldTargetSchemaTest().sourceReceipt();
                receipt.put("tenant_id",tenant).put("project_id",project).put("task_id",task).put("task_revision",id(4)).put("plan_revision",id(5))
                        .put("snapshot_id",id(61)).put("mapping_revision",id(7)).put("mapping_sha256",mappingHash).put("entry_key",id(13)).put("receipt_id",id(29))
                        .put("parse_version",id(22)).put("index_version",id(23)).put("approval_revision",id(24)).put("rights_revision",id(25)).put("binding_revision",id(26));
                var locator=json.object().put("kind","TXT").put("encoding","UTF-8").put("newline_policy","PRESERVE")
                        .put("char_start",0).put("char_end",100).put("line_start",1).put("line_end",1);
                String raw="prefix x😀 suffix",quote=com.ruoyi.medical.contracts.WireValues.sha256(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var field=new SourceExactProtocol.Field("MODEL"," x😀 ","DO NOT COPY ALIAS","Display alias only",id(28),quote,schema.hash(locator),6,10);
                var read=mock(TaskFieldSourceReader.Read.class);when(read.manifest()).thenAnswer(inv->receipt.deepCopy());when(read.receiptId()).thenReturn(id(29));
                when(read.receiptSha256()).thenReturn(schema.hash(receipt));when(read.field()).thenReturn(field);
                when(read.request()).thenReturn(new TaskFieldSourceAuthority.Request(task,id(7),mappingHash,id(13)));reads=List.of(read);
                var source=new SourceReadProtocol.SourceRow(tenant,id(20),id(21),project,"PROJECT","INTERNAL",id(25),hash,hash,project);
                when(basis.execution()).thenReturn(new TaskPlanService.Confirmation(null,null,List.of(new SourceReadProtocol.VerifiedSource(source,null,null,null,null,null,null)),Map.of()));
                for(int n:new int[]{21,22,27})rows("version_registry").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"version_id",id(n),"content_sha256",hash)));
                rows("source_chunk").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"source_version",id(21),"parse_version",id(22),"chunk_id",id(28),"locator",schema.canonical(locator),"raw_text",raw,"quote_hash",quote)));
            }
            when(db.query(eq("SELECT UTC_TIMESTAMP(6)"),any(RowMapper.class))).thenAnswer(inv->{TaskFieldSqlCapture.positional("ledger-and-registration",inv.getArgument(0),new Object[0]);return List.of(now);});
            when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(inv->select(inv.getArgument(0),arguments(inv.getArguments())));
            when(db.update(anyString(),any(Object[].class))).thenAnswer(inv->update(inv.getArgument(0),arguments(inv.getArguments())));
        }
        List<Map<String,Object>> rows(String table){return tables.computeIfAbsent(table,k->new ArrayList<>());}
        Map<String,Object> only(String table){assertEquals(1,rows(table).size(),table);return rows(table).get(0);}
        TaskFieldTargetLedger.Attempt prepare()throws Exception{return tx(()->ledger.prepare(basis,mapping,reads,request,key,"1"));}
        TaskFieldTargetLedger.Attempt recover()throws Exception{return tx(()->ledger.recover(basis,mapping,reads,request,key,"1"));}
        interface Work<T>{T run()throws Exception;}
        <T>T tx(Work<T> work)throws Exception{begin();try{T result=work.run();complete(true);return result;}catch(Exception e){complete(false);throw e;}finally{end();}}
        void begin(){begin(actor);}
        void begin(String user){scope=TenantScope.open(new TenantScope.Identity(tenant,user,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();}
        void complete(boolean committed){for(var sync:TransactionSynchronizationManager.getSynchronizations()){if(committed)sync.afterCommit();sync.afterCompletion(committed?TransactionSynchronization.STATUS_COMMITTED:TransactionSynchronization.STATUS_ROLLED_BACK);}}
        void end(){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);if(scope!=null){scope.close();scope=null;}}
        private List<Map<String,Object>> select(String sql,Object[] args){
            TaskFieldSqlCapture.positional("ledger-and-registration",sql,args);
            if(sql.startsWith("SELECT user_id FROM tenant_member"))return List.of(Map.of("user_id",actor));
            var table=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!table.find())throw new AssertionError(sql);
            var result=new ArrayList<Map<String,Object>>();for(var row:rows(table.group(1))){var terms=Pattern.compile("([a-z_]+)=\\?").matcher(sql);int i=0;boolean matches=true;
                while(terms.find()){if(!Objects.equals(row.get(terms.group(1)),args[i++])){matches=false;break;}}if(matches)result.add(new LinkedHashMap<>(row));}return result;
        }
        private int update(String sql,Object[] args){
            TaskFieldSqlCapture.positional("ledger-and-registration",sql,args);
            writes.add(sql);
            if(sql.startsWith("INSERT INTO ")){
                var match=Pattern.compile("INSERT INTO ([a-z_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)").matcher(sql);if(!match.find())throw new AssertionError(sql);
                var columns=match.group(2).split(",");var values=match.group(3).split(",");assertEquals(columns.length,values.length,sql);int i=0;var row=new LinkedHashMap<String,Object>();
                for(int n=0;n<columns.length;n++){String value=values[n].trim();row.put(columns[n].trim(),value.equals("?")?args[i++]:value.equals("NULL")?null:value.startsWith("'")?value.substring(1,value.length()-1):Integer.valueOf(value));}
                assertEquals(args.length,i,sql);rows(match.group(1)).add(row);return 1;
            }
            if(sql.startsWith("UPDATE operation_record SET revision=2")){var op=only("operation_record");if(!Integer.valueOf(1).equals(op.get("revision")))return 0;op.put("revision",2);op.put("updated_at",args[0]);return 1;}
            throw new AssertionError(sql);
        }
        private static Object[] arguments(Object[] args){var actual=Arrays.copyOfRange(args,1,args.length);return actual.length==1&&actual[0] instanceof Object[] array?array:actual;}
    }
}

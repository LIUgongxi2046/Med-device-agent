package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.SourceExactMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Executes real reader, authority and receipt store against synthetic byte transport and SQL doubles.
 * No network, database, customer sources, live grants or migration execution. */
class TaskFieldSourceOrchestrationTest {
    final String tenant=id(1),project=id(202),task=id(203),actor=id(290),processingProject=id(10),fieldRevision=id(127);
    final LocalDateTime now=LocalDateTime.of(2026,10,20,0,0);
    TaskFieldSourceProvenanceTest physical;
    PermissionSchema json;TaskFieldTargetSchema schema;TaskFieldSourceReader reader;TaskFieldSourceAuthority authority;TaskFieldSourceReceiptStore store;
    JdbcTemplate db;NamedParameterJdbcTemplate named;EnterpriseAccess access;EnterpriseAccess.Admission admission;
    ApplicationSessionIdentity.Verified subject;TaskService tasks;TaskPlanService plans;ResourceReadAuthority resources;SourceReadAuthority sources;
    SourceExactMapper exacts;SourceUploadTransport transport;SourceReadProtocol.VerifiedSource source;
    TaskService.VerifiedTask fixed;TaskPlanWriteProtocol.View plan;TaskPlanService.Confirmation execution;
    ObjectNode mapping,exactManifest;Map<String,Object> mappingRow,processingRow,exactRow,fieldRow,receiptRow,auditRow;
    TaskFieldSourceAuthority.Request request;
    final AtomicInteger segments=new AtomicInteger(),transports=new AtomicInteger();final List<String> writes=new ArrayList<>();
    boolean revoked;int revokeAfterSegment=-1;
    static String id(int n){return TaskFieldSourceProvenanceTest.id(n);}
    static String hash(String value){return TaskFieldSourceProvenanceTest.hash(value);}

    @BeforeEach void setup() throws Exception {
        physical=new TaskFieldSourceProvenanceTest();physical.fixture();json=physical.json;schema=new TaskFieldTargetSchema();
        db=mock(JdbcTemplate.class);named=mock(NamedParameterJdbcTemplate.class);access=mock(EnterpriseAccess.class);
        admission=mock(EnterpriseAccess.Admission.class);subject=mock(ApplicationSessionIdentity.Verified.class);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(actor,"synthetic","Synthetic fixture","USER"));when(subject.sessionId()).thenReturn("synthetic-session");
        when(admission.subject()).thenReturn(subject);when(admission.actorId()).thenReturn(actor);
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));
        when(admission.watermarks()).thenReturn(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
        tasks=mock(TaskService.class);plans=mock(TaskPlanService.class);resources=mock(ResourceReadAuthority.class);sources=mock(SourceReadAuthority.class);
        exacts=mock(SourceExactMapper.class);transport=mock(SourceUploadTransport.class);
        var grant=new PermissionProtocol.GrantRow(id(300),actor,project,"TASK",task,"task.field_target.produce","{}","ACTIVE",now.minusDays(1),null,id(301),"1.0.0",hash("grant"),BigInteger.ONE);
        when(resources.require(eq(admission),any(),anyString(),anyString())).thenReturn(grant);
        var rights=new SourceReadProtocol.RightsRow(tenant,id(131),id(4),"DOCUMENT_BODY","[\"READ\",\"ENTERPRISE_USE\"]","[]","[\"LOCAL\"]",null,null,"APPROVED","{}",hash("rights"),"1.0.0",hash("rights-schema"),"INTERNAL",hash("rights"),null);
        var selected=new SourceReadProtocol.SelectionRow(tenant,project,id(261),id(132),id(4),id(5),id(128),id(129),id(130),"COMPLETE","READY",BigInteger.ONE,BigInteger.ONE,id(129),"RECEIVED","APPROVED","ACTIVE",BigInteger.ONE,"APPROVE",hash("review"),"APPLICABLE","ACTIVE");
        source=new SourceReadProtocol.VerifiedSource(new SourceReadProtocol.SourceRow(tenant,id(132),id(4),null,"ENTERPRISE","INTERNAL",id(131),physical.original.sha256(),physical.original.sha256(),null),rights,selected,null,grant,grant,null);
        when(sources.require(eq(admission),any())).thenAnswer(call -> {
            var r=call.getArgument(1,SourceReadProtocol.ReadRequirement.class);
            assertEquals(SourceReadProtocol.Use.FORMAL,r.use());assertEquals("DOCUMENT_BODY",r.purpose());assertEquals(project,r.projectId());
            assertEquals(id(261),r.snapshotId());assertEquals(id(5),r.parseVersion());assertEquals(id(131),r.rightsRevision());assertNull(r.provider());
            if(revoked)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return source;
        });
        mapping=(ObjectNode)schema.read(TaskFieldTargetSchemaTest.mappingManifest().toString().replace("00000000-0000-0000-0000-","00000000-0000-4000-8000-"));
        remap(mapping);mapping.put("tenant_id",tenant);
        var sourceRef=(ObjectNode)mapping.at("/mappings/0/source_exact");sourceRef.put("source_version",id(4)).put("field_revision",fieldRevision);
        var input=new SourceExactProtocol.PublicationInput("1.0.0",project,id(5),id(128),id(129),id(130),id(131),"0",List.of(physical.field),"Synthetic approved exact field");
        exactManifest=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("source_version",id(4))
            .put("field_revision",fieldRevision).put("revision","1").put("original_sha256",physical.original.sha256()).put("actor_id",actor)
            .put("created_at",now.toInstant(ZoneOffset.UTC).toString());exactManifest.set("request",json.tree(input));
        physical.exact.require("Manifest",exactManifest);sourceRef.put("manifest_sha256",schema.hash(exactManifest));
        schema.validate("MappingManifest",mapping);
        request=new TaskFieldSourceAuthority.Request(task,id(207),schema.hash(mapping),id(213));
        var head=new TaskProtocol.Head(tenant,project,task,"READY",BigInteger.ONE,actor,id(204),id(260),id(261),id(206),id(280),id(205),hash("task"),hash("task"),now);
        fixed=new TaskService.VerifiedTask(json.object(),head,new TaskProtocol.Input("SYNTHETIC",actor,"Synthetic fixture",null,"{}",TaskFieldTargetSchemaTest.H,"{}",hash("scope"),"[]",hash("[]"),"1.0.0",hash("input-schema")));
        plan=new TaskPlanWriteProtocol.View("1.0.0",tenant,project,task,id(204),id(205),id(261),id(206),id(260),"CONFIRMED",null,TaskFieldTargetSchemaTest.H,id(205),"1");
        var template=new TemplateProtocol.Stored(tenant,id(206),"APPROVED","1",null,TaskFieldTargetSchemaTest.H,null,"synthetic",id(300),"1",actor,now.toString(),List.of());
        var view=new TemplateApplicabilityProtocol.TemplateView(template,"APPLICABLE",List.of(),List.of(),hash("dependencies"));
        execution=new TaskPlanService.Confirmation(new TemplateApplicabilityService.Target(admission,"PLAN_MAINTENANCE",null,view,grant,"[]"),null,List.of(source),Map.of());
        when(tasks.fixedTask(eq(admission),anyString())).thenReturn(fixed);when(plans.readFixed(eq(admission),eq(fixed),anyString(),eq(true))).thenReturn(plan);
        when(plans.execution(admission,fixed,plan)).thenReturn(execution);
        when(plans.sourceProof(eq(admission),eq(project),eq(task),eq(id(204)),eq(id(261)),eq(id(260)),eq(true))).thenReturn(List.of(source));
        rows();jdbc();transport();
        authority=new TaskFieldSourceAuthority(tasks,plans,resources,sources,exacts,physical.exact,new SourceExactPolicy(100,100),schema,TaskFieldSourceProvenanceTest.policy(),json,db);
        store=new TaskFieldSourceReceiptStore(db,named,schema,json,authority);
        reader=new TaskFieldSourceReader(access,authority,store,transport,TaskFieldSourceProvenanceTest.policy(),physical.exact,json);
        when(access.execute(eq(subject),any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Every physical guard must return to a fresh admission transaction");
            try(var scope=TenantScope.open(new TenantScope.Identity(admission.context().tenant_id(),admission.actorId(),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))) {
                TransactionSynchronizationManager.setActualTransactionActive(true);
                return ((Function<EnterpriseAccess.Admission,?>)call.getArgument(1)).apply(admission);
            } finally {TransactionSynchronizationManager.clear();}
        });
    }

    /** Replace only synthetic template/task fixture IDs; physical source provenance stays independently assigned. */
    void remap(JsonNode node) {
        if(node.isObject()) {
            var names=new ArrayList<String>();node.fieldNames().forEachRemaining(names::add);
            for(String name:names) {
                var value=node.get(name);
                if(value.isTextual() && value.textValue().matches("00000000-0000-4000-8000-[0-9]{12}"))
                    ((ObjectNode)node).put(name,id(Integer.parseInt(value.textValue().substring(24))+200));
                else remap(value);
            }
        } else if(node.isArray())for(int i=0;i<node.size();i++) {
            var value=node.get(i);if(value.isTextual()&&value.textValue().matches("00000000-0000-4000-8000-[0-9]{12}"))
                ((com.fasterxml.jackson.databind.node.ArrayNode)node).set(i,json.tree(id(Integer.parseInt(value.textValue().substring(24))+200)));
            else remap(value);
        }
    }
    void rows() throws Exception {
        mappingRow=new HashMap<>();mapping.fields().forEachRemaining(e->{if(e.getValue().isTextual())mappingRow.put(e.getKey(),e.getValue().textValue());});
        mappingRow.put("mapping_revision",id(207));mappingRow.put("content_json",schema.canonical(mapping));mappingRow.put("content_sha256",schema.hash(mapping));
        mappingRow.put("registered_sha256",schema.hash(mapping));mappingRow.put("registry_project",project);mappingRow.put("entity_kind","TASK_FIELD_MAPPING_REVISION");
        mappingRow.put("schema_manifest_sha256",schema.manifest());mappingRow.put("classification","INTERNAL");mappingRow.put("created_by",actor);
        processingRow=new HashMap<>();processingRow.put("project_id",processingProject);processingRow.put("attempt_id",id(2));processingRow.put("run_id",id(3));processingRow.put("config_revision",id(6));
        processingRow.put("command_ref",schema.canonical(physical.command));processingRow.put("result_ref",schema.canonical(physical.result));processingRow.put("lease_generation",BigInteger.ONE);
        processingRow.put("write_intent",schema.canonical(json.tree(new S3ObjectWriter.WriteIntent(id(302),physical.parsed.objectKey(),physical.parsed.sha256(),physical.parsed.sizeBytes(),physical.parsed.mediaType()))));
        processingRow.put("object_key",physical.original.objectKey());processingRow.put("object_version",physical.original.objectVersion());processingRow.put("original_sha256",physical.original.sha256());
        processingRow.put("media_type",physical.original.mediaType());processingRow.put("size_bytes",new BigInteger(physical.original.sizeBytes()));
        processingRow.put("artifact_key",physical.parsed.objectKey());processingRow.put("artifact_version",physical.parsed.objectVersion());processingRow.put("artifact_size_bytes",new BigInteger(physical.parsed.sizeBytes()));
        processingRow.put("content_sha256",physical.parsed.sha256());processingRow.put("registered_sha256",physical.parsed.sha256());processingRow.put("processor_manifest",schema.canonical(physical.bundle.required("processor_manifest")));
        exactRow=new HashMap<>();exactRow.put("manifest",schema.canonical(exactManifest));exactRow.put("manifest_sha256",schema.hash(exactManifest));exactRow.put("registered_sha256",schema.hash(exactManifest));
        exactRow.put("entity_kind","SOURCE_EXACT_REVISION");exactRow.put("owner_project_id",null);exactRow.put("registered_project_id",null);exactRow.put("tenant_id",tenant);
        exactRow.put("source_version",id(4));exactRow.put("field_revision",fieldRevision);exactRow.put("parse_version",id(5));exactRow.put("index_version",id(128));
        exactRow.put("approval_revision",id(129));exactRow.put("rights_revision",id(131));exactRow.put("created_by",actor);exactRow.put("created_at",now);
        exactRow.put("publication_revision",BigInteger.ONE);exactRow.put("field_count",BigInteger.ONE);exactRow.put("schema_version","1.0.0");
        exactRow.put("schema_manifest_sha256",physical.exact.manifestSha256());exactRow.put("classification","INTERNAL");
        fieldRow=new HashMap<>(exactRow);fieldRow.put("ordinal",BigInteger.ZERO);fieldRow.put("field_manifest",json.canonicalObject(physical.field));
        fieldRow.put("field_kind",physical.field.field_kind());fieldRow.put("raw_value",physical.field.raw_value());fieldRow.put("raw_value_sha256",hash(physical.field.raw_value()));
        fieldRow.put("alias_value",physical.field.alias_value());fieldRow.put("alias_value_sha256",hash(physical.field.alias_value()));fieldRow.put("chunk_id",physical.field.chunk_id());
        when(exacts.revision(anyMap())).thenAnswer(call->{Map<String,Object> q=call.getArgument(0);return id(4).equals(q.get("sourceVersion"))&&fieldRevision.equals(q.get("fieldRevision"))?List.of(exactRow):List.of();});
        when(exacts.fields(anyMap())).thenReturn(List.of(fieldRow));
    }
    void jdbc() {
        when(db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class)).thenAnswer(call->{TaskFieldSqlCapture.positional("source","SELECT UTC_TIMESTAMP(6)",new Object[0]);return now;});
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);Object[] args=(Object[])call.getRawArguments()[1];TaskFieldSqlCapture.positional("source",sql,args);
            if(sql.contains("FROM task_field_mapping_revision"))return match(args,tenant,project,task,id(207),true)||match(args,tenant,project,task,id(207),false)?List.of(mappingRow):List.of();
            if(sql.contains("FROM field_definition"))return match(args,tenant,id(206),id(212))?List.of(Map.of("field_id",id(212),"node_id",id(209),"value_type","TEXT")):List.of();
            if(sql.contains("FROM processing_attempt"))return match(args,tenant,id(4),id(5))?List.of(processingRow):List.of();
            if(sql.contains("FROM task_field_source_read_receipt")) {
                if(receiptRow==null)return List.of();
                String[] keys=sql.contains("AND actor_id=?")?new String[]{"tenant_id","project_id","task_id","mapping_revision","actor_id","request_sha256","receipt_id"}:new String[]{"tenant_id","receipt_id","mapping_revision"};
                for(int i=0;i<args.length;i++)if(!Objects.equals(args[i],receiptRow.get(keys[i])))return List.of();return List.of(receiptRow);
            }
            if(sql.contains("FROM audit_event")) {
                if(auditRow==null)return List.of();String[] keys={"tenant_id","project_id","audit_id","subject_id","created_by","created_at","classification","request_id","trace_id","schema_manifest_sha256"};
                for(int i=0;i<args.length;i++)if(!Objects.equals(args[i],auditRow.get(keys[i])))return List.of();return List.of(Map.of("resource_ref",auditRow.get("resource_ref")));
            }
            throw new AssertionError(sql);
        });
        when(db.update(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);Object[] args=(Object[])call.getRawArguments()[1];TaskFieldSqlCapture.positional("source",sql,args);
            assertTrue(sql.contains("INSERT INTO audit_event"));writes.add(sql);auditRow=new HashMap<>();
            String[] keys={"tenant_id","project_id","audit_id","subject_id","resource_ref","request_id","trace_id","schema_manifest_sha256","created_by","created_at","classification"};
            for(int i=0;i<args.length;i++)auditRow.put(keys[i],args[i]);return 1;
        });
        when(named.update(anyString(),anyMap())).thenAnswer(call->{String sql=call.getArgument(0);TaskFieldSqlCapture.named("source",sql,call.getArgument(1));assertTrue(sql.startsWith("INSERT INTO task_field_source_read_receipt("));
            assertNull(receiptRow,"Receipt must never be replaced");writes.add(sql);receiptRow=new HashMap<>((Map<String,Object>)call.getArgument(1));return 1;});
    }
    static boolean match(Object[] args,Object... expected){return Arrays.equals(args,expected);}

    void transport() throws Exception {
        doAnswer(call->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());transports.incrementAndGet();
            assertSame(subject,call.getArgument(0));assertEquals(tenant,call.getArgument(1));
            S3ObjectReader.Admission current=call.getArgument(3);current.requireCurrent();
            var s3=mock(S3ObjectReader.class);var lease=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){}public Duration remainingTime(){return Duration.ofMinutes(1);}};
            when(s3.readVerified(any(),any())).thenAnswer(readCall->{
                ObjectRef ref=readCall.getArgument(0);S3ObjectReader.Admission guard=readCall.getArgument(1);
                byte[] actual=ref.objectKey().equals(physical.original.objectKey())?new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7', (byte)0xff, (byte)0xfe}:physical.bytes;
                assertEquals(ref.objectKey().equals(physical.original.objectKey())?physical.original:physical.parsed,ref);
                var delivered=new java.io.ByteArrayOutputStream();
                for(int start=0;start<actual.length;start+=1024){guard.requireCurrent();int end=Math.min(actual.length,start+1024);delivered.write(actual,start,end-start);
                    if(segments.incrementAndGet()==revokeAfterSegment)revoked=true;guard.requireCurrent();}
                byte[] transferred=delivered.toByteArray();if(!WireValues.sha256(transferred).equals(ref.sha256()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
                var object=mock(S3ObjectReader.VerifiedObject.class);when(object.reference()).thenReturn(ref);when(object.fullSha256()).thenReturn(WireValues.sha256(transferred));
                when(object.chunk(anyLong(),anyInt())).thenAnswer(chunk->{guard.requireCurrent();byte[] portion=Arrays.copyOfRange(transferred,Math.toIntExact(chunk.getArgument(0,Long.class)),Math.addExact(Math.toIntExact(chunk.getArgument(0,Long.class)),chunk.getArgument(1,Integer.class)));guard.requireCurrent();return portion;});
                return object;
            });
            return ((SourceUploadTransport.DocumentRead<?>)call.getArgument(4)).apply(s3,lease);
        }).when(transport).readDocument(eq(subject),anyString(),anyInt(),any(),any());
    }
    TaskFieldSourceReader.Read read() throws Exception{return reader.read(subject,"1",request);}
    @AfterEach void clean(){TransactionSynchronizationManager.clear();}

    @Test void realReadAuthorityAndStoreDeliverBinaryAndParsedBytesWithDistinctProcessingOwner() throws Exception {
        var result=read();assertEquals("AB 007",result.rawValue());assertEquals(2,transports.get());assertTrue(segments.get()>1);assertEquals(2,writes.size());
        assertNull(source.source().ownerProjectId());assertNotEquals(project,processingProject);
        assertEquals(processingProject,result.manifest().required("processing_project_id").textValue());assertEquals(project,result.manifest().required("project_id").textValue());
        assertEquals(physical.original.sha256(),result.manifest().at("/original_read/delivered_sha256").textValue());
        assertEquals(physical.parsed.sha256(),result.manifest().at("/parsed_read/delivered_sha256").textValue());
        assertEquals(project,exactManifest.at("/request/project_id").textValue());assertFalse(writes.stream().anyMatch(sql->sql.contains("content_read_receipt")||sql.contains("INSERT INTO artifact")));
        result.requireCurrent();assertEquals(2,transports.get());
    }
    @Test void recoveryUsesOriginalReceiptIdentityWithoutAnotherPhysicalReadOrReceipt() throws Exception {
        var first=read();var again=read();assertEquals(first.receiptId(),again.receiptId());assertEquals(first.receiptSha256(),again.receiptSha256());
        assertEquals(first.manifest(),again.manifest());assertEquals(2,transports.get());assertEquals(2,writes.size());
        ((ObjectNode)again.manifest()).put("actor_id",id(999));assertEquals(actor,first.manifest().required("actor_id").textValue());
    }
    @Test void wrongTaskTenantMappingDigestAndEntryNeverReachTransport() throws Exception {
        var original=request;
        for(var crossing:List.of(new TaskFieldSourceAuthority.Request(id(999),original.mappingRevision(),original.mappingSha256(),original.entryKey()),
                new TaskFieldSourceAuthority.Request(task,id(999),original.mappingSha256(),original.entryKey()),
                new TaskFieldSourceAuthority.Request(task,original.mappingRevision(),hash("other-mapping"),original.entryKey()),
                new TaskFieldSourceAuthority.Request(task,original.mappingRevision(),original.mappingSha256(),id(999)))) {
            request=crossing;assertThrows(TenantAccessException.class,this::read);assertEquals(0,transports.get());
        }
        request=original;when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(id(999),"1","1","1"));
        assertThrows(TenantAccessException.class,this::read);assertTrue(writes.isEmpty());assertEquals(0,transports.get());
    }
    @Test void currentRevocationDuringPhysicalSegmentsPreventsReceiptAndStopsFurtherTransfer() {
        revokeAfterSegment=1;assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::read).code());
        assertEquals(1,segments.get());assertEquals(1,transports.get());assertNull(receiptRow);assertTrue(writes.isEmpty());
    }
    @Test void revokedCurrentSourceCannotRecoverAnOtherwiseIntactReceipt() throws Exception {
        read();revoked=true;assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::read).code());assertEquals(2,transports.get());assertEquals(2,writes.size());
    }
    @Test void receiptCannotCrossStoredTaskMappingOrOriginalActorByReusingAnId() throws Exception {
        var original=read();var saved=new HashMap<>(receiptRow);
        for(String key:List.of("task_id","mapping_revision","actor_id")) {
            receiptRow=new HashMap<>(saved);receiptRow.put(key,id(999));
            assertThrows(TenantAccessException.class,()->access.execute(subject,a->org.springframework.util.function.ThrowingSupplier.of(
                ()->store.requireHistorical(a,original.receiptId(),request.mappingRevision(),"task.field_target.produce")).get()));
            assertEquals(2,transports.get());assertEquals(2,writes.size());
        }
    }
    @Test void independentAuditAndOriginalReceiptDigestAreRequiredForRecovery() throws Exception {
        read();auditRow.put("resource_ref","{}");assertThrows(TenantAccessException.class,this::read);assertEquals(2,transports.get());assertEquals(2,writes.size());
    }
    @Test void alteredReceiptPinsFailBeforeAnyRecoveryTransport() throws Exception {
        read();receiptRow.put("field_revision",id(999));assertThrows(TenantAccessException.class,this::read);assertEquals(2,transports.get());
    }
    @Test void changedDeliveredParsedBytesCannotCreateAReceiptFromDatabaseMetadata() {
        physical.bytes=physical.bytes.clone();physical.bytes[physical.bytes.length-1]^=1;
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::read).code());
        assertEquals(2,transports.get());assertNull(receiptRow);assertTrue(writes.isEmpty());
    }
    @Test void callerOrdinalCannotSelectAnotherFieldOrInferAReplacement() throws Exception {
        ((ObjectNode)mapping.at("/mappings/0/source_exact")).put("ordinal",1);
        mappingRow.put("content_json",schema.canonical(mapping));mappingRow.put("content_sha256",schema.hash(mapping));mappingRow.put("registered_sha256",schema.hash(mapping));
        request=new TaskFieldSourceAuthority.Request(task,id(207),schema.hash(mapping),id(213));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::read).code());assertEquals(0,transports.get());assertTrue(writes.isEmpty());
    }
    @Test void exactPublicationProjectCannotBeBorrowedFromOriginalProcessingProject() throws Exception {
        ((ObjectNode)exactManifest.required("request")).put("project_id",processingProject);
        exactRow.put("manifest",schema.canonical(exactManifest));exactRow.put("manifest_sha256",schema.hash(exactManifest));exactRow.put("registered_sha256",schema.hash(exactManifest));
        ((ObjectNode)mapping.at("/mappings/0/source_exact")).put("manifest_sha256",schema.hash(exactManifest));
        mappingRow.put("content_json",schema.canonical(mapping));mappingRow.put("content_sha256",schema.hash(mapping));mappingRow.put("registered_sha256",schema.hash(mapping));
        request=new TaskFieldSourceAuthority.Request(task,id(207),schema.hash(mapping),id(213));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::read).code());assertEquals(0,transports.get());assertTrue(writes.isEmpty());
    }
}

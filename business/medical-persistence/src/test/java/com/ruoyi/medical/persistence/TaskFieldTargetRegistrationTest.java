package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real registration/ledger/schema and lineage, with explicit JDBC/current-authority/physical-transfer doubles.
 * No real database, grant, S3 transfer or runtime activity is asserted. */
class TaskFieldTargetRegistrationTest {
    @Test void registersOnlyActualReadbackAndCompletesOriginalOperation() throws Exception {
        var f=new Fixture();f.tx(()->{
            var registered=f.register();assertEquals(f.attempt.artifactId(),registered.artifactId());
            assertTrue(registered.occurrenceIds().isEmpty());assertTrue(registered.manifest().at("/target/entries").isEmpty());
            assertEquals("REGISTERED",registered.receipt().required("status").textValue());
            assertNull(f.base.only("artifact").get("run_id"));assertNull(f.base.only("artifact").get("document_revision"));
            assertEquals("COMPLETED",f.base.only("operation_record").get("state"));
            assertEquals(200,f.base.only("operation_record").get("http_status"));
            assertEquals(1,f.base.rows("task_field_target_registration").size());
            assertEquals(3,f.base.rows("audit_event").size());assertEquals(3,f.base.rows("outbox_event").size());
            var replay=f.registration.requireRegistered(f.base.admission,f.attempt.artifactId(),"task.field_target_input.read");
            assertEquals(registered.receiptSha256(),replay.receiptSha256());replay.requireUnchanged();return null;
        });
    }
    @Test void literalMappedTargetRegistersRealOccurrencesAndCompleteSourceAncestry() throws Exception {
        var f=new Fixture(true);f.tx(()->{
            f.registration.preflight(f.base.basis,f.base.mapping,f.base.reads);
            var proof=f.register();assertEquals(1,proof.occurrenceIds().size());
            assertEquals(" x😀 ",proof.manifest().at("/target/entries/0/candidate_value/raw_text").textValue());
            assertEquals(3,f.base.rows("dependency_access_member").size());
            assertEquals(3,f.base.rows("lineage_edge").size());
            assertEquals(id(29),proof.receipt().at("/source_read_receipt_ids/0").textValue());
            assertTrue(f.base.rows("dependency_access_member").stream().allMatch(r->id(25).equals(r.get("rights_revision"))));
            f.registration.requireRegistered(f.base.admission,f.attempt.artifactId(),"task.field_target_input.read").requireUnchanged();return null;
        });
    }
    @Test void changedMappedReceiptAuthorityStopsBeforePhysicalRegistrationWrites() throws Exception {
        var f=new Fixture(true);when(f.receipts.requireHistorical(f.base.admission,id(29),id(7),"task.field_target.produce"))
            .thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
        int before=f.base.writes.size();assertThrows(TenantAccessException.class,()->f.tx(f::register));
        assertEquals(before,f.base.writes.size());assertTrue(f.base.rows("artifact").isEmpty());
    }
    @Test void mismatchedPhysicalReadbackCannotWriteAnyRegistration() throws Exception {
        var f=new Fixture();when(f.transfer.rawSha256()).thenReturn("f".repeat(64));
        int before=f.base.writes.size();assertThrows(TenantAccessException.class,()->f.tx(f::register));
        assertEquals(before,f.base.writes.size());assertTrue(f.base.rows("artifact").isEmpty());
    }
    @Test void siblingAttemptCannotBorrowPhysicalReadback() throws Exception {
        var f=new Fixture();when(f.transfer.originalAttempt()).thenReturn(mock(TaskFieldTargetLedger.Attempt.class));
        assertThrows(TenantAccessException.class,()->f.tx(f::register));assertTrue(f.base.rows("artifact").isEmpty());
    }
    @Test void privateRegisteredProofCannotEscapeTransaction() throws Exception {
        var f=new Fixture();var proof=f.tx(f::register);
        assertThrows(TenantAccessException.class,proof::manifest);assertThrows(TenantAccessException.class,proof::requireUnchanged);
    }
    @Test void hiddenExtraContentMemberCannotBeIgnored() throws Exception {
        var f=new Fixture();f.tx(f::register);var artifact=f.base.only("artifact");
        f.base.rows("dependency_access_member").add(new LinkedHashMap<>(Map.of("tenant_id",f.base.tenant,"access_set_id",artifact.get("dependency_access_set_id"),
                "upstream_version_id",id(88),"rights_revision",id(89),"created_by",id(91),"created_at",f.base.now,"classification","PUBLIC")));
        assertThrows(TenantAccessException.class,()->f.tx(()->f.registration.requireRegistered(f.base.admission,f.attempt.artifactId(),"task.field_target_input.read")));
    }
    @Test void changedGovernanceVersionCannotReuseReceipt() throws Exception {
        var f=new Fixture();f.tx(f::register);f.base.rows("version_registry").get(0).put("content_sha256","e".repeat(64));
        assertThrows(TenantAccessException.class,()->f.tx(()->f.registration.requireRegistered(f.base.admission,f.attempt.artifactId(),"task.field_target_input.read")));
    }
    @Test void registeredSnapshotIsDefensiveAndCurrentGrantRevocationIsChecked() throws Exception {
        var f=new Fixture();f.tx(()->{var proof=f.register();((com.fasterxml.jackson.databind.node.ObjectNode)proof.manifest()).put("artifact_kind","OTHER");
            assertEquals("STRICT_FIELD_TARGET",proof.manifest().required("artifact_kind").textValue());
            when(f.resources.require(eq(f.base.admission),any(),eq("task.field_target.produce"),eq("STRICT_FIELD_PREPARATION")))
                .thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
            assertThrows(TenantAccessException.class,proof::requireUnchanged);return null;});
    }
    @Test void registeredReaderRejectsOtherActionsAndWorkerAdmission() throws Exception {
        var f=new Fixture();f.tx(()->{assertThrows(TenantAccessException.class,()->f.registration.findRegistered(f.base.admission,f.attempt.artifactId(),"task.run"));
            when(f.base.admission.formalWorker()).thenReturn(true);assertThrows(TenantAccessException.class,()->f.registration.findRegistered(f.base.admission,f.attempt.artifactId(),"task.field_target_input.read"));return null;});
    }
    static String id(int n){return TaskFieldTargetSchemaTest.id(n);}
    static final class Fixture {
        final TaskFieldTargetLedgerTest.Fixture base;
        final TaskFieldTargetLedger.Attempt attempt;
        final ArtifactRegistryMapper registry=mock(ArtifactRegistryMapper.class);
        final SourceReadMapper parents=mock(SourceReadMapper.class);
        final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
        final TaskPlanService plans=mock(TaskPlanService.class);
        final RuntimeObjectTransport.VerifiedTransfer transfer=mock(RuntimeObjectTransport.VerifiedTransfer.class);
        final TaskFieldTargetRegistration registration;
        final VersionResourceMapper owners=mock(VersionResourceMapper.class);
        final TaskFieldSourceReceiptStore receipts=mock(TaskFieldSourceReceiptStore.class);
        Fixture() throws Exception {this(false);}
        Fixture(boolean mapped) throws Exception {
            base=new TaskFieldTargetLedgerTest.Fixture(mapped);attempt=base.prepare();
            var policy=mock(IdentityDirectoryPolicy.class);when(policy.classification()).thenReturn("INTERNAL");
            registration=new TaskFieldTargetRegistration(mock(DataSource.class),base.json,base.schema,base.ledger,registry,new DependencyManifestSchema(),
                    new DependencyLineage(parents,registry),new DependencyLineage.Limits(100,100),owners,receipts,base.tasks,plans,resources,policy);
            ReflectionTestUtils.setField(registration,"db",base.db);
            var input=mock(TaskProtocol.Input.class);when(input.requestHash()).thenReturn(attempt.targetManifest().required("task_input_sha256").textValue());
            var fixed=new TaskService.VerifiedTask(base.json.object(),base.basis.task().head(),input);when(base.tasks.fixedTask(base.admission,base.task)).thenReturn(fixed);
            var savedPlan=base.basis.plan();when(plans.readFixed(base.admission,fixed,id(5),true)).thenReturn(savedPlan);
            var grant=mock(PermissionProtocol.GrantRow.class);when(resources.require(eq(base.admission),any(),anyString(),anyString())).thenReturn(grant);
            base.rows("task_field_mapping_head").add(new LinkedHashMap<>(Map.of("tenant_id",base.tenant,"task_id",base.task,"mapping_revision",id(7))));
            var reference=new ObjectRef(attempt.expectedObjectKey(),"actual-object-version",attempt.writeIntent().sha256(),attempt.writeIntent().sizeBytes(),"application/json");
            when(transfer.originalAttempt()).thenReturn(attempt);when(transfer.reference()).thenReturn(reference);when(transfer.rawSha256()).thenReturn(reference.sha256());
            when(base.db.update(startsWith("UPDATE operation_record SET state='COMPLETED'"),any(Object[].class))).thenAnswer(inv->{
                Object[] args=Arrays.copyOfRange(inv.getArguments(),1,inv.getArguments().length);if(args.length==1&&args[0] instanceof Object[] a)args=a;
                TaskFieldSqlCapture.positional("registration-completion",inv.getArgument(0),args);
                var op=base.only("operation_record");assertEquals("PROCESSING",op.get("state"));assertEquals(2,op.get("revision"));
                op.put("state","COMPLETED");op.put("revision",3);op.put("http_status",200);op.put("response_ref",args[0]);op.put("updated_at",args[1]);return 1;
            });
            when(registry.version(anyString())).thenAnswer(inv->{String key=inv.getArgument(0);return rows("version_registry","version_id",key).stream().map(r->new ArtifactRegistryProtocol.VersionRow(key,(String)r.getOrDefault("owner_project_id",base.project),(String)r.getOrDefault("entity_kind","CONFIG"),s(r,"content_sha256"))).toList();});
            when(registry.artifact(anyString())).thenAnswer(inv->rows("artifact","artifact_id",inv.getArgument(0)).stream().map(r->new ArtifactRegistryProtocol.ArtifactRow(base.tenant,base.project,s(r,"artifact_id"),s(r,"artifact_kind"),s(r,"object_ref"),s(r,"object_key"),s(r,"object_version"),s(r,"sha256"),new BigInteger(s(r,"size_bytes")),s(r,"media_type"),s(r,"dependency_access_set_id"),s(r,"content_sha256"),s(r,"schema_version"),s(r,"schema_manifest_sha256"),s(r,"classification"))).toList());
            when(registry.binding(anyString())).thenAnswer(inv->rows("artifact_content_binding","artifact_id",inv.getArgument(0)).stream().map(r->new ArtifactRegistryProtocol.BindingRow(base.tenant,base.project,s(r,"artifact_id"),s(r,"resource_kind"),s(r,"resource_id"),s(r,"schema_ref"),s(r,"schema_document_sha256"),s(r,"dependency_access_set_id"),s(r,"dependency_manifest_sha256"))).toList());
            when(registry.manifest(anyString())).thenAnswer(inv->rows("dependency_access_manifest","access_set_id",inv.getArgument(0)).stream().map(r->new ArtifactRegistryProtocol.ManifestRow(base.tenant,base.project,s(r,"access_set_id"),s(r,"manifest_json"),s(r,"manifest_sha256"),s(r,"content_sha256"),new BigInteger(s(r,"member_count")),s(r,"schema_version"),s(r,"schema_manifest_sha256"))).toList());
            when(parents.parents(anyString())).thenAnswer(inv->{String key=inv.getArgument(0);
                if(mapped&&key.equals(id(22)))return List.of(new SourceReadProtocol.ParentRow(id(22),id(21),"PARSED_FROM"));
                if(mapped&&key.equals(id(27)))return List.of(new SourceReadProtocol.ParentRow(id(27),id(22),"EXACT_FROM"));
                return rows("lineage_edge","consumer_version_id",key).stream().map(r->new SourceReadProtocol.ParentRow(key,s(r,"upstream_version_id"),s(r,"relation_kind"))).toList();});
            if(mapped) {
                var stored=mock(TaskFieldSourceReceiptStore.Stored.class);var read=base.reads.get(0);
                String readHash=read.receiptSha256();when(stored.manifest()).thenAnswer(inv->read.manifest());when(stored.sha256()).thenReturn(readHash);
                when(receipts.requireHistorical(eq(base.admission),eq(id(29)),eq(id(7)),anyString())).thenReturn(stored);
                var ownerRows=new ArrayList<VersionResourceProtocol.DomainOwnerRow>();
                for(int number:new int[]{21,22,27})ownerRows.add(new VersionResourceProtocol.DomainOwnerRow("SOURCE_VERSION",
                    new VersionResourceProtocol.OwnerRow(base.tenant,id(number),base.project,base.project,"SOURCE",id(20),base.hash,id(21),null,null,null)));
                when(owners.ownersAcrossDomains(anyList(),anyList())).thenReturn(ownerRows);
            }
            when(registry.members(anyString())).thenAnswer(inv->rows("dependency_access_member","access_set_id",inv.getArgument(0)).stream().map(r->new ArtifactRegistryProtocol.MemberRow(s(r,"upstream_version_id"),s(r,"rights_revision"))).toList());
        }
        TaskFieldTargetRegistration.Verified register() throws Exception{return registration.register(base.basis,base.mapping,base.reads,attempt,transfer,"1");}
        <T>T tx(TaskFieldTargetLedgerTest.Fixture.Work<T> work)throws Exception{return base.tx(()->{base.scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));try{return work.run();}finally{base.scope.finishTransaction();}});}
        List<Map<String,Object>> rows(String table,String column,String key){return base.rows(table).stream().filter(r->Objects.equals(r.get(column),key)).toList();}
        static String s(Map<String,Object> r,String key){return Objects.requireNonNull(r.get(key),key).toString();}
    }
}

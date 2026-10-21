package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real capability service, schema, PermissionAuthority and private input proof over a stateful JDBC double.
 * Application admission, registered-target, publication/policy and role checks are explicitly doubled
 * external authority boundaries. No real grant, business approval, database, registration or I/O is asserted. */
class TaskFieldTargetInputLifecycleTest {
    @Test void authorIndependentApprovalConsumeDisableAndOriginalRecoveryUsePersistedState() throws Exception {
        var f=new Fixture(); var authored=f.author(); String capability=authored.path("capability_id").asText();
        assertEquals("DRAFT",authored.path("status").asText()); assertEquals(1,f.rows(Fixture.CAPABILITY).size());
        var approved=f.approve(authored); assertEquals("APPROVED",approved.path("status").asText());
        assertEquals(2,f.rows("operation_record").size()); assertEquals(2,f.rows("audit_event").size());
        var proof=f.consume(authored); assertThrows(TenantAccessException.class,proof::requireUnchanged);
        var disabled=f.disable(authored,"2"); assertEquals("DISABLED",disabled.path("status").asText());
        assertEquals("3",disabled.path("revision").asText());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.consume(authored)).code());
        int writes=f.dbWrites;
        assertEquals(authored,f.recover(f.author,"author-original-key-0001"));
        assertEquals(approved,f.recover(f.approver,"approval-original-key-0001"));
        assertEquals(disabled,f.recover(f.approver,"disable-original-key-0001"));
        assertEquals(authored,f.author()); assertEquals(approved,f.approve(authored));
        assertEquals(writes,f.dbWrites); assertEquals("DISABLED",f.only(Fixture.STATE).get("status"));
        assertEquals(capability,f.only(Fixture.STATE).get("capability_id"));
        assertEquals(1,f.rows(Fixture.CAPABILITY).size());assertEquals(2,f.rows(Fixture.DECISION).size());
    }
    @Test void missingOriginalRecoveryIsReadOnlyAndChangedOriginalBodyConflicts() throws Exception {
        var f=new Fixture(); assertNull(f.recover(f.author,"unknown-original-key-0001")); assertEquals(0,f.dbWrites);
        f.author(); var changed=f.request.deepCopy().put("reason","Different original author body"); int writes=f.dbWrites;
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,()->f.service.author(f.author,"1",f.task,"author-original-key-0001",f.bytes(changed))).code());
        assertEquals(writes,f.dbWrites); assertEquals(1,f.rows(Fixture.CAPABILITY).size());
    }
    @Test void endpointTaskBindingRejectsForeignTaskBeforeAnyDecisionWrite() throws Exception {
        var f=new Fixture();var authored=f.author();int writes=f.dbWrites;
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.service.author(f.author,"1",id(999),"wrong-task-author-key-0001",f.bytes(f.request))).code());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.service.read(f.author,"1",id(999),authored.path("capability_id").asText())).code());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.service.decide(f.approver,"1",id(999),authored.path("capability_id").asText(),"wrong-task-approval-key-0001",f.bytes(f.decision(authored,"1","APPROVE")))).code());
        assertEquals(writes,f.dbWrites);assertTrue(f.rows(Fixture.DECISION).isEmpty());
    }
    @Test void authorCannotApproveAndStaleRevisionCannotDisable() throws Exception {
        var f=new Fixture();var authored=f.author(); var request=f.decision(authored,"1","APPROVE");int writes=f.dbWrites;
        f.addGrant(f.authorId,TaskFieldTargetInputCapabilityService.APPROVE,150,"TASK",f.task);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.service.decide(f.author,"1",f.task,authored.path("capability_id").asText(),"self-approval-key-0001",f.bytes(request))).code());
        assertEquals(writes,f.dbWrites);assertTrue(f.rows(Fixture.DECISION).isEmpty());
        f.approve(authored);writes=f.dbWrites;
        assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->f.disable(authored,"1")).code());
        assertEquals(writes,f.dbWrites);assertEquals("APPROVED",f.only(Fixture.STATE).get("status"));
    }
    @Test void originalAuthorGrantRevocationStopsFreshAndAlreadyIssuedProofWithoutSubstitution() throws Exception {
        var f=new Fixture();var authored=f.author();f.approve(authored);
        f.tx(f.consumer,a->{var proof=f.issue(a,authored);proof.requireUnchanged();f.revoke(f.authorId,TaskFieldTargetInputCapabilityService.WRITE);
            assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,proof::requireUnchanged).code());return null;});
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->f.consume(authored)).code());
    }
    @Test void originalApproverGrantRevocationStopsFreshAndAlreadyIssuedProofWithoutSubstitution() throws Exception {
        var f=new Fixture();var authored=f.author();f.approve(authored);
        f.tx(f.consumer,a->{var proof=f.issue(a,authored);proof.requireUnchanged();f.revoke(f.approverId,TaskFieldTargetInputCapabilityService.APPROVE);
            assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,proof::requireUnchanged).code());return null;});
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->f.consume(authored)).code());
    }
    @Test void uint64GrantRevisionsSurvivePersistenceAndRevisionOnlyChangesRemainStale() throws Exception {
        for(boolean authorChange:List.of(true,false)) {
            var f=new Fixture();f.revision(f.authorId,TaskFieldTargetInputCapabilityService.WRITE,"9007199254740992");
            f.revision(f.approverId,TaskFieldTargetInputCapabilityService.APPROVE,"9007199254740992");
            var original=f.author();f.approve(original);assertNotNull(f.consume(original));
            var fixed=f.schema.read(f.only(Fixture.CAPABILITY).get("fixed_json").toString());
            assertEquals("9007199254740992",fixed.at("/grant/revision").textValue());
            String actor=authorChange?f.authorId:f.approverId;
            String action=authorChange?TaskFieldTargetInputCapabilityService.WRITE:TaskFieldTargetInputCapabilityService.APPROVE;
            f.revision(actor,action,"9007199254740993");
            assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->f.consume(original)).code());
        }
    }
    @Test void originalCapabilityNeverFallsBackToAnotherApprovedCapability() throws Exception {
        var f=new Fixture();var original=f.author();f.approve(original);
        var replacement=f.service.author(f.author,"1",f.task,"author-replacement-key-0001",f.bytes(f.request));
        f.service.decide(f.approver,"1",f.task,replacement.path("capability_id").asText(),"approval-replacement-key-0001",f.bytes(f.decision(replacement,"1","APPROVE")));
        f.disable(original,"2");
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->f.consume(original)).code());
        assertNotNull(f.consume(replacement));assertEquals(2,f.rows(Fixture.CAPABILITY).size());
    }
    @Test void issuedPrivateProofRejectsAnotherAdmissionObjectAndReboundTransaction() throws Exception {
        var f=new Fixture();var original=f.author();f.approve(original);
        f.tx(f.consumer,a->{var proof=f.issue(a,original);var other=new EnterpriseAccess.Admission(f.consumer,id(203),a.context(),a.watermarks());
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->proof.requireRead(other,f.task,"N03",f.reference,List.of(id(11)),"4","artifact.read",TaskFieldTargetInputCapabilityService.PURPOSE)).code());
            TransactionSynchronizationManager.unbindResource(f.transactionKey);TransactionSynchronizationManager.bindResource(f.transactionKey,new Object());
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,proof::requireUnchanged).code());return null;});
    }
    @Test void actualDisableCasFailureRollsBackDecisionJournalAndKeepsApproval() throws Exception {
        var f=new Fixture();var original=f.author();f.approve(original);int writes=f.dbWrites;f.failStateCas=true;
        assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->f.disable(original,"2")).code());
        assertEquals("APPROVED",f.only(Fixture.STATE).get("status"));assertEquals(1,f.rows(Fixture.DECISION).size());
        assertEquals(2,f.rows("operation_record").size());assertTrue(f.dbWrites>writes);
    }
    @Test void fullAuthorJournalMetadataMustRemainBoundToOriginalReceipt() throws Exception {
        for(String field:List.of("schema_version","schema_manifest_sha256","classification","created_by","created_at","reason_code","trace_id")){
            var f=new Fixture();var original=f.author();var audit=f.rows("audit_event").get(0);Object previous=audit.get(field);
            audit.put(field,"created_at".equals(field)?f.now.plusSeconds(1):"altered");
            assertThrows(RuntimeException.class,()->f.service.read(f.author,"1",f.task,original.path("capability_id").asText()),field);
            f.rows("audit_event").get(0).put(field,previous);assertNotNull(f.service.read(f.author,"1",f.task,original.path("capability_id").asText()));
        }
    }
    @Test void approvalJournalEventRevisionAndOccurrenceTimeCannotBeChanged() throws Exception {
        for(String field:List.of("aggregate_revision","occurred_at")){
            var f=new Fixture();var original=f.author();f.approve(original);var event=f.rows("outbox_event").get(1);
            event.put(field,"occurred_at".equals(field)?f.now.plusSeconds(1):"99");
            assertThrows(TenantAccessException.class,()->f.consume(original),field);
        }
    }
    @Test void staleTargetOrPublicationBoundaryBlocksApprovalAndNoDecisionIsPersisted() throws Exception {
        var f=new Fixture();var original=f.author();doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(f.registered).requireUnchanged();
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->f.approve(original)).code());
        assertTrue(f.rows(Fixture.DECISION).isEmpty());assertEquals("DRAFT",f.only(Fixture.STATE).get("status"));
        var other=new Fixture();var receipt=other.author();when(other.publication.approvalSha256()).thenReturn("b".repeat(64));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->other.approve(receipt)).code());
        assertTrue(other.rows(Fixture.DECISION).isEmpty());
    }
    static String id(int n){return TaskFieldTargetSchemaTest.id(n);}
    static final class Fixture {
        static final String CAPABILITY="task_field_target_input_capability",STATE="task_field_target_input_capability_state",DECISION="task_field_target_input_capability_decision";
        final String tenant=id(1),project=id(2),task=id(3),authorId=id(90),approverId=id(91),consumerId=id(92),hash="a".repeat(64);
        final LocalDateTime now=LocalDateTime.of(2026,10,20,0,0);
        final PermissionSchema json=new PermissionSchema();final TaskFieldTargetSchema schema=new TaskFieldTargetSchema();
        final ObjectNode request=new TaskFieldTargetSchemaTest().inputAuthor();
        final JdbcTemplate db=mock(JdbcTemplate.class);final EnterpriseAccess access=mock(EnterpriseAccess.class);
        final PermissionMapper permissions=mock(PermissionMapper.class);final PermissionAuthority permissionAuthority=new PermissionAuthority(json);
        final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);final PlatformMemberIdentityStatus identities=mock(PlatformMemberIdentityStatus.class);
        final AgentPublicationAuthority publications=mock(AgentPublicationAuthority.class);
        final AgentPublicationAuthority.VerifiedPublication publication=mock(AgentPublicationAuthority.VerifiedPublication.class);
        final AgentExecutionPolicyAuthority policies=mock(AgentExecutionPolicyAuthority.class);
        final AgentExecutionPolicyAuthority.VerifiedPolicy policy=mock(AgentExecutionPolicyAuthority.VerifiedPolicy.class);
        final FormalCapabilityIdentityAuthority roles=mock(FormalCapabilityIdentityAuthority.class);
        final TaskService tasks=mock(TaskService.class);final TaskPlanService plans=mock(TaskPlanService.class);
        final TaskFieldTargetRegistration registrations=mock(TaskFieldTargetRegistration.class);
        final TaskFieldTargetRegistration.Verified registered=mock(TaskFieldTargetRegistration.Verified.class);
        final TaskFieldTargetInputCapabilityService service=new TaskFieldTargetInputCapabilityService(access,mock(DataSource.class),publications,policies,roles,tasks,plans,permissions,permissionAuthority,identities,resources,registrations,schema,json);
        final TaskFieldTargetInputAuthority authority=new TaskFieldTargetInputAuthority(service,json);
        final PermissionProtocol.Basis role=new PermissionProtocol.Basis(id(53),hash);
        final ApplicationSessionIdentity.Verified author=subject(authorId),approver=subject(approverId),consumer=subject(consumerId);
        final Object transactionKey=new Object();
        final ObjectRef reference=new ObjectRef("target/key","v1",hash,"4","application/json");
        final Map<String,PermissionProtocol.GrantRow> currentGrants=new HashMap<>();
        final Map<String,String> grantSelection=new HashMap<>();
        final Map<String,List<Map<String,Object>>> tables=new HashMap<>();int dbWrites;boolean failStateCas;BigInteger epoch=BigInteger.ONE;
        Fixture() throws Exception {
            ReflectionTestUtils.setField(service,"db",db);request.set("object_ref",json.tree(reference));
            addGrant(authorId,TaskFieldTargetInputCapabilityService.WRITE,101,"TASK",task);addGrant(authorId,TaskFieldTargetInputCapabilityService.READ,102,"TASK",task);
            addGrant(approverId,TaskFieldTargetInputCapabilityService.APPROVE,111,"TASK",task);addGrant(approverId,TaskFieldTargetInputCapabilityService.READ,112,"TASK",task);addGrant(approverId,TaskFieldTargetInputCapabilityService.DISABLE,113,"TASK",task);
            addGrant(consumerId,TaskFieldTargetInputCapabilityService.READ,121,"TASK",task);addGrant(consumerId,"artifact.read",122,"ARTIFACT",id(50));
            when(permissions.databaseTime()).thenReturn(now);when(identities.active(any())).thenReturn(true);
            when(permissions.targetMember(anyString())).thenAnswer(i->List.of(new TenantMemberRow(tenant,id(201),i.getArgument(0),"ACTIVE",now.minusDays(1),null,BigInteger.ONE)));
            when(permissions.targetBinding(anyString())).thenAnswer(i->{String actor=i.getArgument(0);return List.of(new PermissionProtocol.BindingRow(actor,id(202),"local",null,null,actor,"ACTIVE"));});
            when(permissions.currentGrant(anyString())).thenAnswer(i->{var found=currentGrants.get(i.getArgument(0));return found==null?List.of():List.of(found);});
            when(permissions.basis(anyString())).thenAnswer(i->{String key=i.getArgument(0);return rows("version_registry").stream().filter(r->key.equals(r.get("version_id"))).map(r->new PermissionProtocol.VersionRow(key,project,r.get("content_sha256").toString())).toList();});
            when(resources.require(any(),any(),anyString(),anyString())).thenAnswer(i->{EnterpriseAccess.Admission a=i.getArgument(0);PermissionProtocol.Scope scope=i.getArgument(1);String action=i.getArgument(2),purpose=i.getArgument(3);
                var grant=currentGrants.get(grantSelection.get(a.actorId()+":"+action));if(grant==null)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                return permissionAuthority.requireResource(scope,a.actorId(),"local",action,purpose,List.of(grant),now);});
            when(resources.find(any(),any(),anyString(),anyString())).thenAnswer(i->{try{return Optional.of(resources.require(i.getArgument(0),i.getArgument(1),i.getArgument(2),i.getArgument(3)));}catch(TenantAccessException denied){return Optional.empty();}});
            when(publications.prepare(any(),eq("1"),eq(id(52)),eq("RUN_CREATION"),eq(false))).thenReturn(mock(AgentPublicationAuthority.PreparedPublication.class));
            when(publications.require(any(),eq(id(52)),eq("RUN_CREATION"),any(AgentPublicationAuthority.PreparedPublication.class))).thenReturn(publication);
            when(publication.approvalSha256()).thenReturn(hash);when(publication.executionPolicyId()).thenReturn(id(54));when(publication.executionPolicySha256()).thenReturn(hash);when(publication.authorizations()).thenReturn(json.object().put("boundary","publication"));
            when(policies.requireByVersion(any(),eq(id(54)),eq(hash),eq(project),eq("INTERNAL"),eq("RUN_CREATION"))).thenReturn(policy);
            when(policy.versionId()).thenReturn(id(54));when(policy.contentSha256()).thenReturn(hash);when(policy.authorizations()).thenReturn(json.object().put("boundary","policy"));
            var definition=mock(AgentExecutionPolicyProtocol.Definition.class);when(definition.allowedClassifications()).thenReturn(List.of("INTERNAL"));
            when(definition.bindings()).thenReturn(List.of(new AgentExecutionPolicyProtocol.NodeBinding("N03",role,null,null,null,json.object(),List.of())));when(policy.definition()).thenReturn(definition);
            when(roles.require(any(),eq(role),eq("ROLE"),eq(project),eq("RUN_CREATION"))).thenReturn(mock(FormalCapabilityIdentityAuthority.VerifiedCapability.class));
            var head=new TaskProtocol.Head(tenant,project,task,"READY",BigInteger.ONE,authorId,id(4),id(60),id(61),id(6),id(52),id(5),hash,hash,now);
            var input=mock(TaskProtocol.Input.class);when(input.requestHash()).thenReturn(hash);var fixed=new TaskService.VerifiedTask(json.object(),head,input);
            when(tasks.fixedTask(any(),eq(task))).thenReturn(fixed);var plan=mock(TaskPlanWriteProtocol.View.class);when(plan.status()).thenReturn("CONFIRMED");when(plan.contentSha256()).thenReturn(hash);
            when(plans.readFixed(any(),eq(fixed),eq(id(5)),eq(true))).thenReturn(plan);
            when(registrations.requireRegistered(any(),eq(id(50)),anyString())).thenAnswer(i->{EnterpriseAccess.Admission a=i.getArgument(0);String action=i.getArgument(2);
                resources.require(a,new PermissionProtocol.Scope(tenant,project,"TASK",task),action,TaskFieldTargetInputCapabilityService.PURPOSE);return registered;});
            when(registered.artifactId()).thenReturn(id(50));when(registered.projectId()).thenReturn(project);when(registered.taskId()).thenReturn(task);when(registered.mappingRevision()).thenReturn(id(7));
            when(registered.mappingSha256()).thenReturn(hash);when(registered.targetSha256()).thenReturn(hash);when(registered.targetSchemaSha256()).thenReturn(schema.manifest());
            when(registered.registrationId()).thenReturn(id(51));when(registered.receiptSha256()).thenReturn(hash);when(registered.reference()).thenReturn(reference);
            when(registered.receipt()).thenReturn(json.object().put("classification","INTERNAL"));when(registered.occurrenceIds()).thenReturn(List.of(id(11)));
            var pins=json.object();for(String field:List.of("task_revision","task_input_sha256","plan_revision","plan_sha256","template_version","template_sha256"))pins.set(field,request.required(field));when(registered.pins()).thenReturn(pins);
            when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(i->select(i.getArgument(0),args(i.getArguments())));
            when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(i->query(i.getArgument(0),i.getArgument(1),tail(i.getArguments(),2)));
            when(db.update(anyString(),any(Object[].class))).thenAnswer(i->update(i.getArgument(0),args(i.getArguments())));
            doAnswer(i->{ApplicationSessionIdentity.Verified subject=i.getArgument(0);Function<EnterpriseAccess.Admission,Object> work=i.getArgument(1);return tx(subject,work::apply);}).when(access).execute(any(),any());
        }
        ApplicationSessionIdentity.Verified subject(String actor){var result=mock(ApplicationSessionIdentity.Verified.class,RETURNS_DEEP_STUBS);when(result.user().user_id()).thenReturn(actor);when(result.binding().view()).thenReturn(new AuthenticatedIdentityBinding.View("local",actor,null,null));return result;}
        void addGrant(String actor,String action,int grantId,String kind,String resource) throws Exception {
            var grant=new PermissionProtocol.GrantRow(id(grantId),actor,project,kind,resource,action,
                    "{\"schema_version\":\"1.0.0\",\"identity_modes\":[\"local\"],\"purposes\":[\"TASK_FIELD_TARGET_INPUT\"],\"delegation\":{\"actions\":[],\"role_ids\":[],\"resource_kinds\":[],\"project_ids\":[],\"resource_ids\":[]}}",
                    "ACTIVE",now.minusDays(1),null,id(200),"1.0.0",json.manifestSha256(),BigInteger.ONE);
            currentGrants.put(grant.grantId(),grant);grantSelection.put(actor+":"+action,grant.grantId());
        }
        void revision(String actor,String action,String revision){String id=grantSelection.get(actor+":"+action);var g=currentGrants.get(id);currentGrants.put(id,new PermissionProtocol.GrantRow(g.grantId(),g.subjectId(),g.projectId(),g.resourceKind(),g.resourceId(),g.action(),g.condition(),g.status(),g.validFrom(),g.validTo(),g.basisVersion(),g.schemaVersion(),g.manifest(),new BigInteger(revision)));}
        void revoke(String actor,String action){String id=grantSelection.get(actor+":"+action);var g=currentGrants.get(id);currentGrants.put(id,new PermissionProtocol.GrantRow(g.grantId(),g.subjectId(),g.projectId(),g.resourceKind(),g.resourceId(),g.action(),g.condition(),"REVOKED",g.validFrom(),g.validTo(),g.basisVersion(),g.schemaVersion(),g.manifest(),g.revision().add(BigInteger.ONE)));}
        byte[] bytes(JsonNode node)throws Exception{return schema.canonical(node).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
        JsonNode author()throws Exception{return service.author(author,"1",task,"author-original-key-0001",bytes(request));}
        ObjectNode decision(JsonNode original,String revision,String decision){return json.object().put("schema_version","1.0.0").put("project_id",project).put("expected_revision",revision).put("capability_sha256",original.path("capability_sha256").asText()).put("decision",decision).put("reason","Independent exact input decision");}
        JsonNode approve(JsonNode original)throws Exception{return service.decide(approver,"1",task,original.path("capability_id").asText(),"approval-original-key-0001",bytes(decision(original,"1","APPROVE")));}
        JsonNode disable(JsonNode original,String revision)throws Exception{return service.decide(approver,"1",task,original.path("capability_id").asText(),"disable-original-key-0001",bytes(decision(original,revision,"DISABLE")));}
        JsonNode recover(ApplicationSessionIdentity.Verified actor,String key)throws Exception{return service.recover(actor,"1",task,bytes(json.object().put("schema_version","1.0.0").put("project_id",project).put("original_idempotency_key",key)));}
        TaskFieldTargetInputAuthority.Verified issue(EnterpriseAccess.Admission a,JsonNode original)throws Exception{return authority.require(a,publication,policy,role,task,original.path("capability_id").asText(),original.path("capability_sha256").asText());}
        TaskFieldTargetInputAuthority.Verified consume(JsonNode original)throws Exception{return tx(consumer,a->{var proof=issue(a,original);proof.requireRead(a,task,"N03",reference,List.of(id(11)),"4","artifact.read",TaskFieldTargetInputCapabilityService.PURPOSE);return proof;});}
        interface Work<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
        <T>T tx(ApplicationSessionIdentity.Verified actor,Work<T> work)throws Exception {
            var before=new HashMap<String,List<Map<String,Object>>>();tables.forEach((k,v)->{var copy=new ArrayList<Map<String,Object>>();v.forEach(r->copy.add(new LinkedHashMap<>(r)));before.put(k,copy);});var beforeEpoch=epoch;
            var watermarks=new TenantWatermarks(BigInteger.ONE,epoch);var context=new BusinessSessionProtocol.Context(tenant,"1","1",epoch.toString());
            try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor.user().user_id(),BigInteger.ONE,BigInteger.ONE,epoch))){
                TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(transactionKey,new Object());
                try{return work.run(new EnterpriseAccess.Admission(actor,id(203),context,watermarks));}
                catch(Exception|Error failure){tables.clear();tables.putAll(before);epoch=beforeEpoch;throw failure;}
                finally{TransactionSynchronizationManager.unbindResource(transactionKey);TransactionSynchronizationManager.setActualTransactionActive(false);}
            }
        }
        List<Map<String,Object>> rows(String table){return tables.computeIfAbsent(table,k->new ArrayList<>());}
        Map<String,Object> only(String table){assertEquals(1,rows(table).size(),table);return rows(table).get(0);}
        List<Map<String,Object>> select(String sql,Object[] args){
            TaskFieldSqlCapture.positional("capability",sql,args);
            var table=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!table.find())throw new AssertionError(sql);
            String where=sql.contains(" WHERE ")?sql.substring(sql.indexOf(" WHERE ")+7).replaceAll(" (ORDER BY|LIMIT|FOR UPDATE).*$",""):"";
            var result=new ArrayList<Map<String,Object>>();
            for(var row:rows(table.group(1))){int index=0;boolean match=true;
                for(String term:where.split(" AND ")){if(term.isEmpty())continue;
                    var in=Pattern.compile("([a-z_]+) IN \\(([^)]+)\\)").matcher(term);
                    if(in.matches()){boolean found=false;for(String value:in.group(2).split(",")){Object compare=value.equals("?")?args[index++]:literal(value);found|=equal(row.get(in.group(1)),compare);}match&=found;continue;}
                    var eq=Pattern.compile("([a-z_]+)\\s*(?:=|<=>)\\s*(\\?|'[^']*'|[0-9]+)").matcher(term);if(!eq.matches())throw new AssertionError("Unsupported where: "+term+" in "+sql);
                    Object compare=eq.group(2).equals("?")?args[index++]:literal(eq.group(2));match&=equal(row.get(eq.group(1)),compare);
                }
                assertEquals(args.length,index,sql);if(match)result.add(new LinkedHashMap<>(row));
            }
            if(sql.contains("ORDER BY upstream_version_id"))result.sort(Comparator.comparing(r->r.get("upstream_version_id").toString()));
            return result;
        }
        @SuppressWarnings({"unchecked","rawtypes"}) List<?> query(String sql,RowMapper mapper,Object[] args)throws Exception {
            var selected=select(sql,args);if(sql.startsWith("SELECT COUNT(*)"))return List.of(selected.size());
            String columns=sql.substring(7,sql.indexOf(" FROM "));var result=new ArrayList<>();
            for(var row:selected){var names=columns.split(",");var rs=mock(ResultSet.class);when(rs.getString(anyInt())).thenAnswer(i->{Object value=row.get(names[i.<Integer>getArgument(0)-1].trim());return value==null?null:value.toString();});result.add(mapper.mapRow(rs,result.size()));}
            return result;
        }
        int update(String sql,Object[] args){
            TaskFieldSqlCapture.positional("capability",sql,args);
            dbWrites++;
            if(sql.startsWith("INSERT INTO ")){
                var m=Pattern.compile("INSERT INTO ([a-z_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)").matcher(sql);if(!m.find())throw new AssertionError(sql);
                String[] columns=m.group(2).split(","),values=m.group(3).split(",");assertEquals(columns.length,values.length,sql);int index=0;var row=new LinkedHashMap<String,Object>();
                for(int n=0;n<columns.length;n++){String value=values[n].trim();row.put(columns[n].trim(),value.equals("?")?args[index++]:literal(value));}
                assertEquals(args.length,index,sql);rows(m.group(1)).add(row);return 1;
            }
            if(sql.startsWith("UPDATE tenant SET governance_epoch")){if(!equal(args[0],tenant)||!equal(args[1],BigInteger.ONE)||!equal(args[2],epoch))return 0;epoch=epoch.add(BigInteger.ONE);return 1;}
            if(sql.startsWith("UPDATE "+STATE+" SET ")){
                if(failStateCas)return 0;var selected=rows(STATE).stream().filter(r->equal(r.get("tenant_id"),args[4])&&equal(r.get("capability_id"),args[5])&&equal(r.get("status"),args[6])&&equal(r.get("revision"),args[7])&&equal(r.get("latest_decision_id"),args[8])).toList();
                if(selected.size()!=1)return selected.size();var row=selected.get(0);row.put("status",args[0]);row.put("revision",args[1]);row.put("latest_decision_id",args[2]);row.put("updated_at",args[3]);return 1;
            }
            throw new AssertionError(sql);
        }
        static boolean equal(Object a,Object b){return a==null?b==null:b!=null&&a.toString().equals(b.toString());}
        static Object literal(String value){value=value.trim();return value.equals("NULL")?null:value.startsWith("'")?value.substring(1,value.length()-1):Integer.valueOf(value);}
        static Object[] args(Object[] args){return tail(args,1);}
        static Object[] tail(Object[] args,int from){var result=Arrays.copyOfRange(args,from,args.length);return result.length==1&&result[0] instanceof Object[] actual?actual:result;}
    }
}

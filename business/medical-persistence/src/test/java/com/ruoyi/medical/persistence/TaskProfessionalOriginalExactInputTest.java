package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual075 ledger/transport/registration/historical reader with explicit authority, JDBC
 * and independent observation doubles. Not live storage, approval, mTLS or runtime acceptance. */
class TaskProfessionalOriginalExactInputTest {
    @TempDir Path directory;
    static final class Fixture extends TaskProfessionalExactInputRegistrationTest {
        String artifact;JsonNode observation;
        Fixture(Path directory)throws Exception{this.directory=directory;}
        void registered()throws Exception {
            var binding=open.binding();var unit=json.object().put("boot_id",id(200)).put("cgroup_path","/sys/fs/cgroup/medical/session").put("cgroup_inode",100);
            var processes=unit.putArray("processes");for(int pid:List.of(101,102)){var p=processes.addObject().put("pid",pid).put("start_ticks",pid*10).put("exe_sha256",hash(pid==101?'a':'b')).put("uid",1001).put("gid",1001).put("no_new_privs",1).put("seccomp",2).put("cap_eff","0000000000000000").put("lsm_profile","medical-runtime (enforce)");var ns=p.putObject("namespaces");for(String field:List.of("mnt","pid","net","user","ipc","uts","cgroup"))ns.put(field,1000);}
            for(String kind:List.of("runtime_files","profile_files"))unit.putArray(kind).addObject().put("path","/pinned/"+kind).put("sha256",hash('c'));
            var evidence=json.object().put("schema_version","1.0.0").put("phase","ADMITTED").put("gate","SDK_READY_LOCKED").put("receipt_id",id(95)).put("session_id",session).put("deployment_revision",id(88)).put("deployment_sha256",hash('a')).put("runtime_manifest_sha256",hash('c')).put("supervisor_instance_id",id(201)).put("inspected_at","2026-10-20T01:00:00Z");evidence.set("binding",binding);evidence.set("process_unit",unit);
            TaskProfessionalSupervisor.validateStoredAdmission(evidence);String admissionHash=TaskProfessionalObjectGovernance.hash(evidence);when(admitted.sha256()).thenReturn(admissionHash);
            var projection=json.object().put("provider","enterprise-model").put("model","approved").put("session_id",session);projection.putNull("system");projection.putNull("tool_history");projection.putArray("tools");projection.putArray("messages").addObject().put("role","user").putArray("content").addObject().put("type","text").put("text","literal approved prompt");projection.putObject("parameters").put("max_output_tokens",50).putNull("temperature").putNull("reasoning_effort").put("purpose","TASK").putArray("stop");
            byte[] observed=ContentReadAuthorization.canonicalBytes(projection);TaskProfessionalSupervisor.validateProjection(observed);
            var request=json.object().put("schema_version","1.0.0").put("receipt_id",id(202)).put("request_id",id(96)).put("ordinal",1).put("session_id",session).put("deployment_sha256",hash('a')).put("supervision_receipt_id",id(95)).put("supervision_sha256",admissionHash).put("method","POST").put("route","/v1/model-contexts").put("raw_body_sha256",WireValues.sha256(observed)).put("raw_size_bytes",observed.length).put("projection_schema_ref",TaskInvocationContextSchema.PROJECTION_REF).put("projection_schema_version","1.0.0").put("projection_schema_sha256",professionalSchema.manifest("TASK_EXACT_INPUT")).put("body_sha256",WireValues.sha256(observed)).put("body_base64",Base64.getEncoder().encodeToString(observed)).put("peer_pid",102).put("peer_start_ticks",1020).put("peer_exe_sha256",hash('b'));request.set("binding",binding);observation=request;
            when(exact.modelRequestSha256()).thenReturn(TaskProfessionalObjectGovernance.hash(request));
            var provenance=(ObjectNode)exact.provenance().deepCopy();provenance.put("supervision_sha256",admissionHash);provenance.set("model_request",request);provenance.putObject("tokenization").put("input_sha256",WireValues.sha256(body));when(exact.provenance()).thenReturn(provenance);
            var supervision=meta();supervision.putAll(Map.of("run_id",run,"step_id",step,"lease_generation",1L,"session_id",session,"receipt_id",id(95),"phase","ADMITTED","input_sha256",hash('a'),"envelope_sha256",hash('b'),"deployment_revision",id(88),"deployment_sha256",hash('a')));supervision.putAll(Map.of("canonical_evidence",json.canonical(evidence),"evidence_sha256",admissionHash,"audit_id",id(203),"event_id",id(204),"supervisor_service_uri","spiffe://test.invalid/supervisor","certificate_sha256",hash('c'),"trust_revision",id(205)));rows("professional_runtime_supervision").add(supervision);
            var audit=meta();audit.putAll(Map.of("audit_id",id(203),"subject_id",actor,"action","task.professional.admitted","result","SUCCESS","request_id",session,"trace_id",id(95),"resource_ref",json.canonical(evidence)));audit.put("reason_code",null);rows("audit_event").add(audit);
            rows("outbox_event").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"event_id",id(204),"aggregate_id",session,"aggregate_revision",1,"event_type","task.professional.admitted","payload",json.canonical(evidence),"occurred_at",now)));
            var start=json.object().put("session_id",session).put("purpose","TASK").put("node_kind","N02").put("state","START").put("deployment_sha256",hash('a')).put("runtime_manifest_sha256",hash('c'));start.set("binding",binding);
            var sessionRow=meta();sessionRow.putAll(Map.of("run_id",run,"step_id",step,"session_id",session,"action_id",action,"lease_generation",1L,"input_sha256",hash('a'),"envelope_sha256",hash('b'),"service_uri","spiffe://test.invalid/worker","admission_state","OPEN"));sessionRow.put("canonical_start",json.canonical(start));sessionRow.put("start_sha256",TaskProfessionalObjectGovernance.hash(start));rows("professional_runtime_session").add(sessionRow);
            var attempt=attempt();var transfer=transfer(attempt);artifact=tx(()->professionalRegistrations.register(leasedProducer,exact,attempt,transfer)).artifactId();
        }
        Map<String,Object> meta(){var row=new LinkedHashMap<String,Object>();row.putAll(Map.of("tenant_id",tenant,"project_id",project,"created_by",actor,"created_at",now,"classification","INTERNAL","schema_version","1.0.0","schema_manifest_sha256",json.manifestSha256()));return row;}
        <T>T historical(CheckedSupplier<T> work)throws Exception{return tx(()->{var key=new Object();TransactionSynchronizationManager.bindResource(key,new Object());try{return work.get();}finally{TransactionSynchronizationManager.unbindResource(key);}});}
        TaskProfessionalObjectRegistration.OriginalExactInput read()throws Exception{return professionalRegistrations.readOriginalExactInput(tenant,artifact);}
    }
    @Test void originalBytesRemainFactsAfterLeaseAndSourceRightsExpire()throws Exception {
        var f=new Fixture(directory);f.registered();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.exact).requireUnchanged();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.leasedProducer).requireUnchanged();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.professionalInput).requireUnchanged();f.only("professional_runtime_session").put("admission_state","CLOSED");
        f.historical(()->{var original=f.read();assertArrayEquals(f.body,original.bytes());assertEquals("TASK_EXACT_INPUT",original.kind());assertEquals(f.artifact,original.pin("artifact_id"));original.requireUnchanged();byte[] copy=original.bytes();copy[0]=0;assertArrayEquals(f.body,original.bytes());return null;});
    }
    @Test void partialOrRelabelledJournalCannotBecomeOriginalProof()throws Exception {
        var f=new Fixture(directory);f.registered();var intent=f.only("runtime_object_intent");Object original=intent.put("schema_version","1.0.0");assertThrows(TenantAccessException.class,()->f.historical(f::read));intent.put("schema_version",original);f.rows("runtime_object_attempt").clear();assertThrows(TenantAccessException.class,()->f.historical(f::read));
    }
    @Test void exactPhysicalVersionAndRequestKeyCannotBeSubstituted()throws Exception {
        var f=new Fixture(directory);f.registered();var row=f.only("artifact");var version=row.put("object_version","other-version");assertThrows(TenantAccessException.class,()->f.historical(f::read));row.put("object_version",version);f.only("runtime_object_intent").put("write_key",TaskNodeInputFixture.hash('9'));assertThrows(TenantAccessException.class,()->f.historical(f::read));
    }
    @Test void governanceAndAuditOriginalsAreMandatory()throws Exception {
        var f=new Fixture(directory);f.registered();var member=f.only("runtime_governance_member");var hash=member.put("content_sha256",TaskNodeInputFixture.hash('9'));assertThrows(TenantAccessException.class,()->f.historical(f::read));member.put("content_sha256",hash);f.rows("audit_event").removeIf(r->"runtime.professional.prepared".equals(r.get("action")));assertThrows(TenantAccessException.class,()->f.historical(f::read));
    }
    @Test void supervisionInputAndSessionOriginalCannotBeRebound()throws Exception {
        var f=new Fixture(directory);f.registered();var binding=f.only("runtime_professional_object_binding");var hash=binding.put("input_receipt_sha256",TaskNodeInputFixture.hash('9'));assertThrows(TenantAccessException.class,()->f.historical(f::read));binding.put("input_receipt_sha256",hash);f.only("professional_runtime_session").put("action_id",TaskNodeInputFixture.id(999));assertThrows(TenantAccessException.class,()->f.historical(f::read));
    }
    @Test void proofsCannotOutliveScopeTransactionOrMutatedJournal()throws Exception {
        var f=new Fixture(directory);f.registered();var original=f.historical(f::read);assertThrows(TenantAccessException.class,original::bytes);assertThrows(TenantAccessException.class,()->f.historical(()->{original.requireUnchanged();return null;}));
        f.historical(()->{var proof=f.read();f.only("runtime_object_state").put("revision",3);assertThrows(TenantAccessException.class,proof::requireUnchanged);return null;});
    }
    @Test void ambiguousOwnershipOrForeignTenantCannotReadHistoricalBytes()throws Exception {
        var f=new Fixture(directory);f.registered();assertThrows(TenantAccessException.class,()->f.historical(()->f.professionalRegistrations.readOriginalExactInput(TaskNodeInputFixture.id(999),f.artifact)));f.rows("runtime_object_intent").add(new LinkedHashMap<>(f.only("runtime_object_intent")));assertThrows(TenantAccessException.class,()->f.historical(f::read));
    }
    @Test void noDataFactoryOrExecutionCapabilityExists() {
        var type=TaskProfessionalObjectRegistration.OriginalExactInput.class;for(var c:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(c.getModifiers()));assertFalse(RuntimeObjectAttempt.class.isAssignableFrom(type));assertFalse(TaskProfessionalObjectRegistration.Registered.class.isAssignableFrom(type));
    }
}

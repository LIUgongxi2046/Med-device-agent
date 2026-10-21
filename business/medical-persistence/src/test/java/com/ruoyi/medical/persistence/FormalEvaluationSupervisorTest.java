package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Closed evidence-shape tests only. Deferred, not run. No live supervisor, isolation, mTLS or DB acceptance. */
class FormalEvaluationSupervisorTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private ObjectNode process(int pid,String hash){
        var p=JSON.createObjectNode().put("pid",pid).put("start_ticks",pid*10).put("exe_sha256",hash).put("uid",1001).put("gid",1001).put("no_new_privs",1).put("seccomp",2).put("cap_eff","0000000000000000").put("lsm_profile","medical-runtime (enforce)");
        var namespaces=p.putObject("namespaces");for(String field:List.of("mnt","pid","net","user","ipc","uts","cgroup"))namespaces.put(field,1000);return p;
    }
    private ObjectNode unit(){
        var unit=JSON.createObjectNode().put("boot_id","5a721522-02ae-4620-8548-c14b80d36a99").put("cgroup_path","/sys/fs/cgroup/medical/session").put("cgroup_inode",100);
        unit.putArray("processes").add(process(101,"a".repeat(64))).add(process(102,"b".repeat(64)));
        for(String kind:List.of("runtime_files","profile_files"))unit.putArray(kind).addObject().put("path","/pinned/"+kind).put("sha256","c".repeat(64));return unit;
    }
    @Test void actualRuntimeAndLauncherShapeAccepted(){assertDoesNotThrow(()->FormalEvaluationSupervisor.validateUnit(unit()));}
    @Test void launcherOnlyIsNotAdmission(){var unit=unit();unit.withArray("processes").remove(1);assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void duplicatePidIsNotIndependentChild(){var unit=unit();((ObjectNode)unit.withArray("processes").get(1)).put("pid",101);assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void sameExeLauncherOnlyIsNotRuntime(){var unit=unit();((ObjectNode)unit.withArray("processes").get(1)).put("exe_sha256","a".repeat(64));assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void absentStartIdentityFails(){var unit=unit();((ObjectNode)unit.withArray("processes").get(1)).remove("start_ticks");assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void rootWorkerFails(){var unit=unit();((ObjectNode)unit.withArray("processes").get(1)).put("uid",0);assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void missingNamespaceFails(){var unit=unit();((ObjectNode)unit.withArray("processes").get(1).required("namespaces")).remove("net");assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void missingNoNewPrivilegesFails(){var unit=unit();((ObjectNode)unit.withArray("processes").get(1)).put("no_new_privs",0);assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void unenforcedLsmFails(){for(String label:List.of("unconfined","medical-runtime (complain)","medical-runtime")){var unit=unit();((ObjectNode)unit.withArray("processes").get(1)).put("lsm_profile",label);assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}}
    @Test void inheritedCapabilityFails(){var unit=unit();((ObjectNode)unit.withArray("processes").get(1)).put("cap_eff","0000000000000001");assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void profileBytesRequired(){var unit=unit();unit.withArray("profile_files").removeAll();assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateUnit(unit));}
    @Test void callerCannotConstructPrivateProofs(){for(Class<?> type:List.of(FormalEvaluationSupervisor.Request.class,FormalEvaluationSupervisor.Inspection.class,FormalEvaluationSupervisor.Admitted.class,FormalEvaluationSupervisor.Closed.class,FormalEvaluationSupervisor.ModelSnapshot.class,FormalEvaluationSupervisor.ModelInspection.class,FormalEvaluationSupervisor.ModelRequest.class,FormalEvaluationContextReads.Verified.class))for(var ctor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()),type.getSimpleName());}
    @Test void closedBytesCannotBeRecreatedFromRowWithoutIndependentReread(){assertThrows(NoSuchMethodException.class,()->FormalEvaluationSupervisor.class.getMethod("requireClosed",FormalEvaluationNodeRuntime.Session.class));}
    @Test void historicalStoredAdmissionShapeDoesNotNeedInventedDeployment()throws Exception{
        var n=JSON.createObjectNode().put("schema_version","1.0.0").put("phase","ADMITTED").put("gate","SDK_READY_LOCKED").put("inspected_at","2026-01-01T00:00:00Z");
        for(String field:List.of("receipt_id","session_id","deployment_revision","supervisor_instance_id"))n.put(field,UUID.randomUUID().toString());
        for(String field:List.of("deployment_sha256","runtime_manifest_sha256"))n.put(field,"a".repeat(64));
        var b=n.putObject("binding");for(String field:List.of("tenant_id","run_id","step_id","action_id"))b.put(field,UUID.randomUUID().toString());b.put("lease_generation",1).put("input_sha256","b".repeat(64)).put("step_envelope_sha256","c".repeat(64));n.set("process_unit",unit());
        assertDoesNotThrow(()->FormalEvaluationSupervisor.validateStoredAdmission(n));
        n.put("caller_process_exited",true);assertThrows(Exception.class,()->FormalEvaluationSupervisor.validateStoredAdmission(n));
    }
}

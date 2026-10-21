package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** TASK adapter over the original 076 journal. No parallel job/dispatch/accounting table.
 * Historical reads verify original records but never confer current send/content authority. */
public final class TaskInvocationRecords {
    private final TaskInvocationOriginals contexts;private final FormalInvocationSchema schema;private final JdbcTemplate db;
    public TaskInvocationRecords(DataSource data,TaskInvocationOriginals contexts,FormalInvocationSchema schema){
        this(new JdbcTemplate(data),contexts,schema);
    }
    TaskInvocationRecords(JdbcTemplate db,TaskInvocationOriginals contexts,FormalInvocationSchema schema){
        this.db=db;db.setQueryTimeout(5);this.contexts=contexts;this.schema=schema;
    }
    static final class Binding implements FormalInvocationBinding {
        private final TaskInvocationOriginals.Stored original;private final String action,request,requestHash,dependencies,dependenciesHash;
        private Binding(TaskInvocationOriginals.Stored original,String action,FormalInvocationSchema schema)throws Exception {
            this.original=original;WireValues.uuid(action);this.action=action;
            request=schema.canonical(original.request());requestHash=schema.hash(original.request());
            dependencies=schema.canonical(original.dependencies());dependenciesHash=schema.hash(original.dependencies());
        }
        TaskInvocationProtocol.Node node(){return original.request().node();}
        public boolean task(){return true;}public String tenant(){return node().tenant_id();}public String project(){return node().project_id();}
        public String verification(){return original.connectionVerificationId();}public String actor(){return original.actorId();}
        public String run(){return node().run_id();}public String principal(){return node().principal_id();}
        public String step(){return node().step_id();}public String action(){return action;}public String context(){return original.contextId();}
        public String model(){return original.request().model_config_revision();}public String request(){return request;}
        public String requestHash(){return requestHash;}public String inputHash(){return node().node_input_sha256();}
        public String dependencies(){return dependencies;}public String dependenciesHash(){return dependenciesHash;}
        public String classification(){return node().classification();}
        JsonNode snapshot()throws Exception{return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("execution_kind","TASK","context_manifest_id",context(),"action_id",action));}
        TaskInvocationOriginals.Stored original(){return original;}
    }
    Binding bind(EnterpriseAccess.Admission admission,TaskInvocationContext.Registered registered,String action)throws Exception {
        registered.requireCurrent(admission);var original=contexts.requireStored(admission.context().tenant_id(),registered.contextId());
        if(!schema.canonical(original.request()).equals(schema.canonical(registered.request()))
                ||!original.actorId().equals(admission.actorId())||!schema.canonical(original.dependencies()).equals(schema.canonical(registered.dependencies())))bad();
        return new Binding(original,action,schema);
    }
    boolean supports(String tenant,String run){
        var rows=db.query("SELECT execution_kind FROM execution_registry WHERE tenant_id=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),tenant,run);
        if(rows.size()!=1)bad();return "TASK".equals(rows.get(0));
    }
    Binding byInvocation(String tenant,String invocation)throws Exception {
        requireTenant(tenant);WireValues.uuid(invocation);
        var stored=one(db.query("SELECT i.run_id,i.context_manifest_id,i.action_id,b.canonical_request,b.request_sha256,b.authorization_snapshot,b.authorization_sha256 FROM invocation i JOIN formal_invocation_binding b ON b.tenant_id=i.tenant_id AND b.invocation_id=i.invocation_id JOIN execution_registry e ON e.tenant_id=i.tenant_id AND e.run_id=i.run_id AND e.execution_kind='TASK' WHERE i.tenant_id=? AND i.invocation_id=? AND b.schema_version='1.0.0' AND b.schema_manifest_sha256=? LIMIT 2",
                (r,n)->List.of(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7)),tenant,invocation,schema.manifestSha256()));
        var authorization=schema.parse(stored.get(5));if(!schema.hash(authorization).equals(stored.get(6)))bad();
        var pin=authorization.required("task_binding");if(!"TASK".equals(text(pin,"execution_kind"))||!stored.get(1).equals(text(pin,"context_manifest_id"))||!stored.get(2).equals(text(pin,"action_id")))bad();
        var result=new Binding(contexts.requireStored(tenant,stored.get(1)),stored.get(2),schema);
        if(!result.run().equals(stored.get(0))||!result.request().equals(stored.get(3))||!result.requestHash().equals(stored.get(4))
                ||!result.dependenciesHash().equals(text(authorization,"job_dependency_sha256"))
                ||!result.verification().equals(text(authorization,"verification_id"))||!result.node().lease_generation().equals(text(authorization,"lease_generation"))
                ||!result.node().worker_service_uri().equals(text(authorization,"service_uri")))bad();
        var sent=authorization.required("task_send_authorizations");if(!"MODEL_CALL".equals(text(sent,"phase"))||!result.action().equals(text(sent,"action_id"))||!result.run().equals(text(sent,"run_id"))||!result.step().equals(text(sent,"step_id"))||!result.node().lease_generation().equals(text(sent,"lease_generation")))bad();
        var identity=one(db.query("SELECT COUNT(*) FROM invocation i JOIN step_action_binding a ON a.tenant_id=i.tenant_id AND a.run_id=i.run_id AND a.step_id=i.step_id AND a.action_id=i.action_id WHERE i.tenant_id=? AND i.project_id=? AND i.invocation_id=? AND i.run_id=? AND i.step_id=? AND i.action_id=? AND i.context_manifest_id=? AND i.config_revision=? AND i.verification_id=? AND i.input_sha256=? AND i.lease_generation=? AND i.created_by=? AND i.classification=? AND a.invocation_id=i.invocation_id AND a.kind='MODEL_CALL' AND a.arguments_sha256=? AND a.input_sha256=i.input_sha256 AND a.tool_version=i.config_revision LIMIT 2",
                (r,n)->r.getInt(1),tenant,result.project(),invocation,result.run(),result.step(),result.action(),result.context(),result.model(),result.verification(),result.inputHash(),result.node().lease_generation(),result.actor(),result.classification(),result.requestHash()));
        if(identity!=1)bad();return result;
    }
    void requireOutput(Binding binding,String kind,byte[] bytes)throws Exception {
        if(!List.of("MODEL_RESULT","PROVIDER_CAPTURE").contains(kind))bad();
        contexts.verifyOriginalOutput(binding.original(),kind,bytes);
    }
    void requireProduction(Binding binding,FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,String kind,byte[] bytes)throws Exception {
        outcome.requireActive();contexts.requireOriginalOutput(binding.original(),outcome,kind,bytes);
    }
    void requireRecoveryPolicy(Binding binding,FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,RuntimeObjectTransport transport)throws Exception {contexts.requireRecoveryPolicy(binding.original(),outcome,transport);}
    List<ArtifactRegistryProtocol.MemberRow> contentMembers(Binding binding)throws Exception {
        // Source leaf grants stay attached to the actual exact-input ancestry. Never assign
        // an arbitrary source's rights revision to the TASK-owned generated input artifact.
        return contexts.contentMembers(binding.original());
    }
    private static String text(JsonNode n,String key){var v=n.required(key);if(!v.isTextual())bad();return v.textValue();}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}
    private static void requireTenant(String tenant){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TenantScope.requireCurrent().tenantId().equals(tenant))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

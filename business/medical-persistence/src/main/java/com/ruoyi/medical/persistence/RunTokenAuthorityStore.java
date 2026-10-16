package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

final class RunTokenAuthorityStore {
    static final String EPOCHS="SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=?";
    static final String RUN="""
        SELECT e.tenant_id,e.project_id,e.run_id,e.execution_kind,e.scope_manifest,e.schema_version,e.schema_manifest_sha256,
          p.principal_id,p.initiator_id,p.capability_manifest,p.status principal_status,p.schema_version principal_schema,
          p.schema_manifest_sha256 principal_manifest,p.classification,p.revision principal_revision,
          CASE WHEN e.execution_kind='TASK' THEN r.created_by ELSE j.initiator_id END execution_actor,
          CASE WHEN e.execution_kind='TASK' THEN r.status ELSE j.status END execution_status,
          CASE WHEN e.execution_kind='TASK' THEN r.ended_at ELSE j.ended_at END ended_at,
          r.dispatch_closed_at,r.superseded_by_run_id,r.task_id
        FROM execution_registry e
        JOIN run_principal p ON p.tenant_id=e.tenant_id AND p.run_id=e.run_id AND p.project_key=e.project_key
        LEFT JOIN task_run r ON r.tenant_id=e.tenant_id AND r.run_id=e.run_id AND r.project_id=e.project_id AND r.execution_kind=e.execution_kind
        LEFT JOIN system_job_run j ON j.tenant_id=e.tenant_id AND j.run_id=e.run_id AND j.project_key=e.project_key AND j.execution_kind=e.execution_kind
        WHERE e.tenant_id=? AND e.run_id=? AND p.initiator_id=? FOR UPDATE
        """;
    static final String STEP="""
        SELECT s.step_id,s.input_sha256,s.status,s.lease_generation,s.lease_owner,s.lease_expires_at,s.revision,
          c.tool_scope,c.status role_status,c.content_sha256,v.content_sha256 registered_sha256,
          c.project_id role_project,h.status configuration_status,c.schema_manifest_sha256 role_manifest,
          c.schema_version role_schema,v.entity_kind
        FROM run_step s
        JOIN capability_version c ON c.tenant_id=s.tenant_id AND c.capability_version=s.role_version AND c.kind='ROLE'
        JOIN configuration_head h ON h.tenant_id=c.tenant_id AND h.config_id=c.config_id AND h.kind='CAPABILITY'
        JOIN version_registry v ON v.tenant_id=c.tenant_id AND v.version_id=c.capability_version AND v.owner_project_id <=> c.project_id
        WHERE s.tenant_id=? AND s.run_id=? AND s.step_id=? AND s.project_key=? FOR UPDATE
        """;
    static final String TOKEN="""
        SELECT jti,principal_id,project_id,run_id,step_id,audience,action_scope,auth_epoch,issued_at,expires_at,revoked_at,
          token_sha256,service_uri,policy_revision,policy_sha256,lease_generation,schema_version,schema_manifest_sha256,created_by
        FROM principal_token WHERE tenant_id=? AND jti=? FOR UPDATE
        """;
    static final String INSERT_TOKEN="""
        INSERT INTO principal_token (tenant_id,jti,principal_id,project_id,run_id,step_id,audience,action_scope,auth_epoch,
          issued_at,expires_at,revoked_at,token_sha256,service_uri,policy_revision,policy_sha256,lease_generation,
          schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,NULL,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
        """;
    static final String REVOKE_TOKEN="""
        UPDATE principal_token SET revoked_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6)
        WHERE tenant_id=? AND jti=? AND principal_id=? AND revoked_at IS NULL
        """;
    static final String ACTION="""
        SELECT action_id,input_sha256,kind,arguments_sha256,tool_version,state,lease_generation
        FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND project_key=? FOR UPDATE
        """;
    static final String CLAIM_STEP="""
        UPDATE run_step SET status='RUNNING',lease_owner=?,lease_expires_at=?,lease_generation=lease_generation+1,
          revision=revision+1,updated_at=UTC_TIMESTAMP(6)
        WHERE tenant_id=? AND run_id=? AND step_id=? AND project_key=? AND revision=? AND lease_generation=?
          AND ((status='READY' AND lease_owner IS NULL AND lease_expires_at IS NULL)
            OR (status='RUNNING' AND lease_expires_at<=UTC_TIMESTAMP(6)))
        """;
    static final String RENEW_STEP="""
        UPDATE run_step SET lease_expires_at=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
        WHERE tenant_id=? AND run_id=? AND step_id=? AND project_key=? AND revision=? AND lease_generation=?
          AND lease_owner=? AND status='RUNNING' AND lease_expires_at>UTC_TIMESTAMP(6)
        """;
    static final String AUDIT="""
        INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,
          schema_version,schema_manifest_sha256,created_by,created_at,classification)
        VALUES (?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
        """;
    static final String EVENT="""
        INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at,published_at)
        VALUES (?,?,?,1,?,?,UTC_TIMESTAMP(6),NULL)
        """;
    static final List<String> STATEMENTS=List.of(EPOCHS,RUN,STEP,TOKEN,INSERT_TOKEN,REVOKE_TOKEN,ACTION,CLAIM_STEP,RENEW_STEP,AUDIT,EVENT);
    private final JdbcTemplate jdbc;

    RunTokenAuthorityStore(DataSource data) { jdbc=new JdbcTemplate(Objects.requireNonNull(data)); }

    TenantWatermarks epochs(String tenant) {
        return single(jdbc.query(EPOCHS,(row,n)->new TenantWatermarks(new BigInteger(row.getString(1)),new BigInteger(row.getString(2))),tenant));
    }

    Run run(String run,String actor) {
        access(actor);
        return single(jdbc.query(RUN,(row,n)->new Run(row.getString("tenant_id"),row.getString("project_id"),row.getString("run_id"),
            row.getString("execution_kind"),row.getString("scope_manifest"),row.getString("schema_version"),row.getString("schema_manifest_sha256"),
            row.getString("principal_id"),row.getString("initiator_id"),row.getString("capability_manifest"),row.getString("principal_status"),
            row.getString("principal_schema"),row.getString("principal_manifest"),row.getString("classification"),
            new BigInteger(row.getString("principal_revision")),row.getString("execution_actor"),row.getString("execution_status"),
            row.getObject("ended_at",LocalDateTime.class),row.getObject("dispatch_closed_at",LocalDateTime.class),row.getString("superseded_by_run_id"),
            row.getString("task_id")),tenant(),run,actor));
    }

    Step step(Run run,String step) {
        access(run.actor());
        return single(jdbc.query(STEP,(row,n)->new Step(row.getString("step_id"),row.getString("input_sha256"),row.getString("status"),
            new BigInteger(row.getString("lease_generation")),row.getString("lease_owner"),row.getObject("lease_expires_at",LocalDateTime.class),
            row.getString("tool_scope"),row.getString("role_status"),row.getString("content_sha256"),row.getString("registered_sha256"),
            row.getString("role_project"),row.getString("configuration_status"),row.getString("role_manifest"),row.getString("role_schema"),
            row.getString("entity_kind"),new BigInteger(row.getString("revision"))),tenant(),run.id(),step,projectKey(run.project())));
    }

    Token token(String actor,String jti) {
        access(actor);
        return single(jdbc.query(TOKEN,(row,n)->new Token(row.getString("jti"),row.getString("principal_id"),row.getString("project_id"),
            row.getString("run_id"),row.getString("step_id"),row.getString("audience"),
            org.springframework.util.function.ThrowingSupplier.of(() -> new org.erdtman.jcs.JsonCanonicalizer(row.getString("action_scope")).getEncodedString()).get(),
            new BigInteger(row.getString("auth_epoch")),
            row.getObject("issued_at",LocalDateTime.class),row.getObject("expires_at",LocalDateTime.class),row.getObject("revoked_at",LocalDateTime.class),
            row.getString("token_sha256"),row.getString("service_uri"),row.getString("policy_revision"),row.getString("policy_sha256"),
            row.getString("lease_generation")==null?null:new BigInteger(row.getString("lease_generation")),row.getString("schema_version"),
            row.getString("schema_manifest_sha256"),row.getString("created_by")),tenant(),jti));
    }

    Action action(Run run,Step step,String action) {
        access(run.actor());
        return single(jdbc.query(ACTION,(row,n)->new Action(row.getString("action_id"),row.getString("input_sha256"),row.getString("kind"),
            row.getString("arguments_sha256"),row.getString("tool_version"),row.getString("state"),new BigInteger(row.getString("lease_generation"))),
            tenant(),run.id(),step.id(),action,projectKey(run.project())));
    }

    void insert(Run run,Token token,String manifest) {
        access(run.actor());
        one(jdbc.update(INSERT_TOKEN,tenant(),token.jti(),run.principal(),run.project(),run.id(),token.step(),token.audience(),token.actions(),token.auth(),
            token.issued(),token.expires(),token.sha256(),token.service(),token.policyRevision(),token.policySha256(),token.generation(),manifest,run.actor(),run.classification()));
    }

    void revoke(Run run,String jti) { access(run.actor()); one(jdbc.update(REVOKE_TOKEN,tenant(),jti,run.principal())); }

    void record(Run run,String identity,String action,String event,String detail,String manifest) {
        access(run.actor());
        var audit=java.util.UUID.randomUUID().toString();
        one(jdbc.update(AUDIT,tenant(),run.project(),audit,run.actor(),action,detail,identity,identity,manifest,run.actor(),run.classification()));
        one(jdbc.update(EVENT,tenant(),java.util.UUID.randomUUID().toString(),identity,event,detail));
    }

    void claim(Run run,Step step,String service,LocalDateTime expires) {
        access(run.actor());
        one(jdbc.update(CLAIM_STEP,service,expires,tenant(),run.id(),step.id(),projectKey(run.project()),step.revision(),step.generation()));
    }

    void renew(Run run,Step step,LocalDateTime expires) {
        access(run.actor());
        one(jdbc.update(RENEW_STEP,expires,tenant(),run.id(),step.id(),projectKey(run.project()),step.revision(),step.generation(),step.owner()));
    }

    LocalDateTime now() { TenantScope.requireSqlAccess("RunTokenAuthorityStore.now"); return Objects.requireNonNull(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class)); }
    private static String tenant() { return TenantScope.requireCurrent().tenantId(); }
    private static void access(String actor) {
        TenantScope.requireSqlAccess("RunTokenAuthorityStore");
        if(!TenantScope.requireCurrent().subjectId().equals(actor)) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    private static String projectKey(String project) { return project==null?"ENTERPRISE":project; }
    private static <T> T single(List<T> values) {
        if(values.size()!=1) throw new TenantAccessException(ErrorCode.ACCESS_DENIED); return values.get(0);
    }
    private static void one(int count) { if(count!=1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }

    record Run(String tenant,String project,String id,String kind,String scope,String schema,String manifest,String principal,String actor,
        String capabilities,String principalStatus,String principalSchema,String principalManifest,String classification,BigInteger revision,
        String executionActor,String status,LocalDateTime ended,LocalDateTime closed,String superseded,String task) { }
    record Step(String id,String input,String status,BigInteger generation,String owner,LocalDateTime expires,String roleScope,String roleStatus,
        String roleSha256,String registeredSha256,String roleProject,String configurationStatus,String roleManifest,String roleSchema,String entityKind,BigInteger revision) { }
    record Token(String jti,String principal,String project,String run,String step,String audience,String actions,BigInteger auth,
        LocalDateTime issued,LocalDateTime expires,LocalDateTime revoked,String sha256,String service,String policyRevision,String policySha256,
        BigInteger generation,String schema,String manifest,String creator) { }
    record Action(String id,String input,String kind,String arguments,String tool,String state,BigInteger generation) { }
}

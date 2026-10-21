package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.*;
import io.temporal.client.WorkflowClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Bounded service-only outbox relay. IDs select immutable existing events, never
 * create commands. No SQL acknowledgement is inferred from signal acceptance.
 * START_RUN retries delegate to the durable attempt's query-only recovery path. */
public final class TaskRunOutboxRelayV2 {
    private TaskRunPreRuntimeCancellation preRuntime;
    @org.springframework.beans.factory.annotation.Autowired
    void preRuntime(TaskRunPreRuntimeCancellation value){preRuntime=Objects.requireNonNull(value);}
    private final JdbcTemplate db;private final ServiceCertificateIdentity identities;
    private final TaskRunWorkflowConsumerV2 starts;private final FormalRunRecoveryAccess recovery;
    private final TaskRunLaunchRecoveryAuthority authority;private final TaskRunWorkflowLedger ledger;
    private final TaskRunWorkflowProtocolBinding protocols;private final TaskRunLifecycleService lifecycle;
    private final WorkflowClient client;private final int maximum;
    private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskRunOutboxRelayV2(DataSource data,ServiceCertificateIdentity identities,TaskRunWorkflowConsumerV2 starts,
            FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority authority,TaskRunWorkflowLedger ledger,
            TaskRunWorkflowProtocolBinding protocols,TaskRunLifecycleService lifecycle,WorkflowClient client,int maximum){
        this(new JdbcTemplate(Objects.requireNonNull(data)),identities,starts,recovery,authority,ledger,protocols,lifecycle,client,maximum);
    }
    TaskRunOutboxRelayV2(JdbcTemplate db,ServiceCertificateIdentity identities,TaskRunWorkflowConsumerV2 starts,
            FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority authority,TaskRunWorkflowLedger ledger,
            TaskRunWorkflowProtocolBinding protocols,TaskRunLifecycleService lifecycle,WorkflowClient client,int maximum){
        this.db=Objects.requireNonNull(db);db.setQueryTimeout(5);this.identities=Objects.requireNonNull(identities);
        this.starts=Objects.requireNonNull(starts);this.recovery=Objects.requireNonNull(recovery);this.authority=Objects.requireNonNull(authority);
        this.ledger=Objects.requireNonNull(ledger);this.protocols=Objects.requireNonNull(protocols);this.lifecycle=Objects.requireNonNull(lifecycle);this.client=Objects.requireNonNull(client);
        if(maximum<1||maximum>100)throw new IllegalArgumentException("BOUNDED_V2_RELAY_PAGE_REQUIRED");this.maximum=maximum;
    }
    /** Cursor is the original event ID; callers retain it and retry exact event IDs.
     * No status-only execution eligibility or tenant-global acknowledgement exists. */
    public List<String> candidates(HttpServletRequest http,String tenant,String afterEvent)throws Exception{
        var service=identities.require(http);WireValues.uuid(tenant);if(afterEvent!=null)WireValues.uuid(afterEvent);
        var rows=db.queryForList("""
            SELECT e.event_id,r.run_id FROM outbox_event e JOIN task_run r
              ON r.tenant_id=e.tenant_id AND r.run_id=e.aggregate_id
            WHERE e.tenant_id=? AND r.execution_kind='TASK'
              AND NOT EXISTS (SELECT 1 FROM task_run_preruntime_termination t WHERE t.tenant_id=r.tenant_id AND t.run_id=r.run_id)
              AND e.event_type IN ('run.start.requested','run.cancel.requested')
              AND r.replay_manifest->>'$.execution_policy.worker.service_uri'=?
              AND ((e.event_type='run.start.requested' AND
                    (NOT EXISTS (SELECT 1 FROM task_run_workflow_launch l WHERE l.tenant_id=r.tenant_id AND l.run_id=r.run_id)
                     OR EXISTS (SELECT 1 FROM operation_record p WHERE p.tenant_id=r.tenant_id AND p.target_id=r.run_id AND p.operation_name='task.run.protocol.v2')))
                   OR (e.event_type='run.cancel.requested' AND EXISTS
                       (SELECT 1 FROM operation_record p WHERE p.tenant_id=r.tenant_id AND p.target_id=r.run_id AND p.operation_name='task.run.protocol.accepted.v2')))
              AND e.event_id>? ORDER BY e.event_id LIMIT ?
            """,tenant,service.serviceUri(),afterEvent==null?"":afterEvent,maximum);
        var result=new ArrayList<String>();
        for(var row:rows){String run=(String)row.get("run_id");recovery.execute(http,tenant,run,a->null);result.add((String)row.get("event_id"));}
        identities.require(http);return List.copyOf(result);
    }
    public JsonNode deliver(HttpServletRequest http,String tenant,String eventId)throws Exception{
        identities.require(http);WireValues.uuid(tenant);WireValues.uuid(eventId);
        var rows=db.queryForList("SELECT aggregate_id,event_type,payload FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",tenant,eventId);
        if(rows.size()!=1)deny();var row=rows.get(0);String run=(String)row.get("aggregate_id");WireValues.uuid(run);
        // Authenticate current mTLS and the immutable run-owned original before exposing its content.
        recovery.execute(http,tenant,run,a->null);
        if(!Set.of("run.start.requested","run.cancel.requested").contains(row.get("event_type")))deny();
        if(preRuntime!=null){
            var terminal=recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->preRuntime.original(a,run)).get());
            if(terminal.isPresent()){
                var out=json.createObjectNode().put("schema_version","2.0.0").put("tenant_id",tenant).put("event_id",eventId).put("state","PRE_RUNTIME_CANCELLED");
                out.set("receipt",terminal.get());return out;
            }
        }
        var body=json.readTree((String)row.get("payload"));var c=body.required("command");
        same(tenant,c.required("tenant_id").asText());same(run,c.required("run_id").asText());
        String command=c.required("command_id").asText();WireValues.uuid(command);
        if("run.start.requested".equals(row.get("event_type"))){
            same("START_RUN",c.required("kind").asText());
            recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->{var p=authority.require(a,http,run,command);same(eventId,p.outboxEventId());p.requireUnchanged();return null;}).get());
            var receipt=starts.consume(http,tenant,run,command);
            var result=json.createObjectNode().put("schema_version","2.0.0").put("state","START_ACCEPTED").put("tenant_id",tenant).put("event_id",eventId);result.set("receipt",receipt.content());return result;
        }
        if(!"run.cancel.requested".equals(row.get("event_type")))deny();
        same("REQUEST_CANCEL",c.required("kind").asText());
        var original=new Command(command,c.required("command_sha256").asText(),run,CommandKind.REQUEST_CANCEL,c.required("decision_receipt_id").asText());
        var execution=recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->{
            var p=authority.require(a,http,run,a.recoveryProof().identity().commandId());
            var permit=protocols.authorizeRecovery(a,p);var receipt=protocols.read(a,p).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
            var accepted=protocols.requireWaitingV2(a,run);accepted.requireUnchanged();
            return new Original(permit.binding().intent().reference(),permit.binding().intent().commandId(),permit.binding().deployment(),accepted.temporalRunId());
        }).get());
        // Verify actual original started-history pins before query or signal. Never use latest-run routing.
        var transport=new WorkflowDeliveryV2(client,execution.deployment());var accepted=transport.recover(execution.reference(),execution.startCommand());
        same(execution.temporalRun(),accepted.temporalRunId());
        var consumed=lifecycle.verifyConsumedCancellationOriginal(http,execution.reference(),original);
        var commands=new WorkflowDelivery(client);
        if(consumed.isPresent()){
            var temporal=commands.consumed(execution.reference(),execution.temporalRun(),command);same(consumed.get(),temporal);
            return result(tenant,eventId,"CONSUMED",temporal);
        }
        var verified=lifecycle.verifyCancellationForDelivery(http,execution.reference(),original);
        same(original,verified.command());same(execution.reference(),verified.reference());
        commands.signal(verified.reference(),execution.temporalRun(),verified.command());
        // A signal is not a business receipt. The next exact-event retry obtains both
        // durable business consumption and Temporal's independently consumed receipt.
        return result(tenant,eventId,"SIGNALLED_NOT_CONSUMED",null);
    }
    private JsonNode result(String tenant,String event,String state,CommandReceipt receipt){var n=json.createObjectNode().put("schema_version","2.0.0").put("tenant_id",tenant).put("event_id",event).put("state",state);if(receipt!=null)n.set("receipt",json.valueToTree(receipt));else n.putNull("receipt");return n;}
    private record Original(Reference reference,String startCommand,WaitingWorkflowDeploymentV2 deployment,String temporalRun){}
    private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))deny();}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}

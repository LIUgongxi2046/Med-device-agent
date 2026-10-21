package com.ruoyi.medical.persistence;

import com.ruoyi.medical.workflow.RunActivities;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import javax.sql.DataSource;

/** No request is retained by a singleton or ThreadLocal. All dependencies are the
 * real fixed production authorities; this factory creates one short-lived adapter. */
public final class TaskRunActivityEndpointFactory {
    private final DataSource data;private final TaskRunActivityPreparation preparation;private final TaskNodeInputAuthority inputs;
    private final FormalTaskNodeAuthority nodes;private final FormalRunRecoveryAccess recovery;private final TaskRunLaunchRecoveryAuthority launches;
    private final TaskRunWorkflowLedger workflow;private final TaskRuntimeJournal journal;private final TaskResearchCommitService commits;
    private final TaskRunDispatchReservation reservations;private final RunTokenAuthority tokens;private final TaskHostControlBootstrap host;
    private final TaskProfessionalHostOrchestrator orchestrator;private final PermissionSchema json;private final Duration leaseLifetime;private final TaskRunLifecycleService lifecycle;
    public TaskRunActivityEndpointFactory(DataSource data,TaskRunActivityPreparation preparation,TaskNodeInputAuthority inputs,
            FormalTaskNodeAuthority nodes,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launches,TaskRunWorkflowLedger workflow,
            TaskRuntimeJournal journal,TaskResearchCommitService commits,TaskRunDispatchReservation reservations,RunTokenAuthority tokens,
            TaskHostControlBootstrap host,TaskProfessionalHostOrchestrator orchestrator,PermissionSchema json,Duration leaseLifetime,TaskRunLifecycleService lifecycle){
        this.lifecycle=lifecycle;this.data=data;this.preparation=preparation;this.inputs=inputs;this.nodes=nodes;this.recovery=recovery;this.launches=launches;this.workflow=workflow;
        this.journal=journal;this.commits=commits;this.reservations=reservations;this.tokens=tokens;this.host=host;this.orchestrator=orchestrator;this.json=json;this.leaseLifetime=leaseLifetime;
    }
    public RunActivities create(HttpServletRequest http){return new TaskRunActivitiesAdapter(http,data,preparation,inputs,nodes,recovery,launches,workflow,journal,commits,reservations,tokens,host,orchestrator,json,leaseLifetime,lifecycle);}
}

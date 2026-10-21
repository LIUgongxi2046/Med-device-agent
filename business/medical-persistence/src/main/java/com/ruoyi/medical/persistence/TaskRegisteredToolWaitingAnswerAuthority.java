package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import com.ruoyi.medical.workflow.TaskRunWaitingProtocol.WaitingCompletionV2;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.ruoyi.medical.persistence.TaskRegisteredToolAuthority.*;

/** Actual human ownership is separate from the immutable original worker identity.
 * No instance of either private proof grants run execution or content access. */
public final class TaskRegisteredToolWaitingAnswerAuthority {
    private final JdbcTemplate db;private final PermissionSchema json;private final ResourceReadAuthority resources;
    private final TaskRegisteredToolWaitingService waiting;private final TaskRegisteredToolWaitingCompletion completions;private final TaskRegisteredToolSchema schema;
    public TaskRegisteredToolWaitingAnswerAuthority(DataSource data,PermissionSchema json,ResourceReadAuthority resources,TaskRegisteredToolWaitingService waiting,TaskRegisteredToolWaitingCompletion completions)throws Exception{this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.resources=resources;this.waiting=waiting;this.completions=completions;schema=new TaskRegisteredToolSchema();}
    CurrentQuestion require(EnterpriseAccess.Admission a,String context,String waitingId)throws Exception {
        human(a,context);WireValues.uuid(waitingId);var creation=creation(a,waitingId);var grants=grants(a,creation);return new CurrentQuestion(a,context,creation,grants);
    }
    Original original(CurrentQuestion q)throws Exception{q.requireUnchanged();return new Original(q,completions.requireQuestionOriginal(q));}
    private JsonNode creation(EnterpriseAccess.Admission a,String id)throws Exception {
        var row=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND action=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),id,TaskRegisteredToolWaitingService.ACTION));var value=schema.parse(str(row,"resource_ref"));schema.validate("WaitingItem",value);
        var c=value.required("cause");same(id,text(value,"waiting_item_id"));waiting.requireCreationIdentity(a.context().tenant_id(),text(c,"created_by"),value);
        var input=c.required("node_input");new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(input));for(String f:List.of("tenant_id","project_id","run_id","step_id"))same(c.required(f),input.required(f));
        var run=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",a.context().tenant_id(),text(c,"project_id"),text(c,"run_id")));
        same(text(input,"task_id"),str(run,"task_id"));same(text(c,"created_by"),str(run,"created_by"));same(text(c,"classification"),str(run,"classification"));var replay=schema.parse(str(run,"replay_manifest"));same(hash(replay),text(input,"replay_sha256"));
        var sections=new ArrayList<JsonNode>();for(var section:replay.required("plan").required("content").required("sections"))if(text(input,"section_id").equals(text(section,"section_id")))sections.add(section);same(text(c,"assignee_id"),text(one(sections),"owner_id"));return value;
    }
    private JsonNode grants(EnterpriseAccess.Admission a,JsonNode creation)throws Exception {
        var c=creation.required("cause");same(a.actorId(),text(c,"assignee_id"));if(!Set.of("local","oidc").contains(a.bindingView().mode()))deny();
        one(db.queryForList("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) LIMIT 2 FOR UPDATE",a.context().tenant_id(),a.actorId()));
        var scope=new PermissionProtocol.Scope(a.context().tenant_id(),text(c,"project_id"),"TASK",text(c.required("node_input"),"task_id"));var pins=json.object();
        pins.set("read",TaskFieldAuthorityWitness.tree(resources.require(a,scope,"work_item.read","WORK_ITEM_READING")));pins.set("resolve",TaskFieldAuthorityWitness.tree(resources.require(a,scope,"work_item.resolve","WORK_ITEM_RESOLUTION")));pins.set("task",TaskFieldAuthorityWitness.tree(resources.require(a,scope,"task.read","TASK_READING")));return pins;
    }
    final class CurrentQuestion {
        private final EnterpriseAccess.Admission admission;private final String context;private final JsonNode creation,grants;
        private CurrentQuestion(EnterpriseAccess.Admission a,String context,JsonNode creation,JsonNode grants){this.admission=a;this.context=context;this.creation=creation.deepCopy();this.grants=grants.deepCopy();}
        void requireUnchanged()throws Exception{human(admission,context);same(creation,TaskRegisteredToolWaitingAnswerAuthority.this.creation(admission,waitingItemId()));same(grants,grants(admission,creation));}
        EnterpriseAccess.Admission admission(){return admission;}String tenantId(){return admission.context().tenant_id();}String projectId(){return text(creation.required("cause"),"project_id");}String runId(){return text(creation.required("cause"),"run_id");}String originalActorId(){return text(creation.required("cause"),"created_by");}String classification(){return text(creation.required("cause"),"classification");}String waitingItemId(){return text(creation,"waiting_item_id");}String taskId(){return text(creation.required("cause").required("node_input"),"task_id");}JsonNode creation(){return creation.deepCopy();}JsonNode grantPins(){return grants.deepCopy();}
    }
    final class Original {
        private final CurrentQuestion q;private final TaskRegisteredToolWaitingCompletion.QuestionOriginal producer;
        private Original(CurrentQuestion q,TaskRegisteredToolWaitingCompletion.QuestionOriginal producer){this.q=q;this.producer=producer;}
        void requireUnchanged()throws Exception{q.requireUnchanged();producer.requireUnchanged();}EnterpriseAccess.Admission admission(){return q.admission();}String tenantId(){return q.tenantId();}String projectId(){return q.projectId();}String runId(){return q.runId();}String originalActorId(){return q.originalActorId();}String classification(){return q.classification();}String waitingItemId(){return q.waitingItemId();}
        WorkflowProtocol.Dispatch originalDispatch(){return producer.dispatch();}JsonNode canonicalCommitRequest()throws Exception{return producer.canonicalCommitRequest();}String protocolSha256(){return producer.protocolSha256();}WaitingCompletionV2 locator(){return producer.locator();}CurrentQuestion question(){return q;}
    }
    static void human(EnterpriseAccess.Admission a,String context){if(!TransactionSynchronizationManager.isActualTransactionActive()||a.formalWorker()||a.controlWorker()||a.recoveryWorker()||a.simulationWorker()||a.planDraftWorker()||a.documentReviewWorker())deny();var scope=TenantScope.requireCurrent();same(scope.tenantId(),a.context().tenant_id());same(scope.subjectId(),a.actorId());PermissionSchema.revision(context);if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
}

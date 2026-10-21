package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** Bounded cross-run ORIGINAL read capability. It never impersonates the parent
 * control worker, reopens execution, or grants a lease/provider permission. */
@org.springframework.stereotype.Component
public final class TaskRunResumeOriginalAuthority {
    private final PermissionSchema json;private final JdbcTemplate db;private final JsonMapper reader=new JsonMapper();
    public TaskRunResumeOriginalAuthority(@org.springframework.beans.factory.annotation.Qualifier("masterDataSource") DataSource data,PermissionSchema json){this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
    public Source require(TaskNodeProductionAuthority.Verified successor,String stage,String successorStep,String originalStep)throws Exception {
        successor.requireUnchanged();if(!Set.of("N02","N03","N04","N05").contains(stage))bad();com.ruoyi.medical.contracts.WireValues.uuid(successorStep);com.ruoyi.medical.contracts.WireValues.uuid(originalStep);
        var basis=basis(successor.admission(),successor.runId(),successor);return new Source(basis,stage,successorStep,originalStep,inspect(basis,stage,successorStep,originalStep));
    }
    public Source requireSaved(TaskNodeProductionAuthority.Verified p,String step)throws Exception {
        p.requireUnchanged();TaskRunSuccessReuse.read(db,json,p.admission(),p.runId(),step).orElseThrow();var saved=one(db.queryForList("SELECT stage_kind,receipt_json FROM task_run_success_reuse WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.runId(),step));return require(p,text(saved,"stage_kind"),step,reader.readTree(text(saved,"receipt_json")).required("source").required("parent_step_id").asText());
    }
    /** Saved alias reader: exact current control run, never a caller-selected parent authority. */
    Source requireSaved(EnterpriseAccess.Admission admission,String run,String step)throws Exception {
        TaskRunSuccessReuse.read(db,json,admission,run,step).orElseThrow();var saved=one(db.queryForList("SELECT stage_kind,receipt_json FROM task_run_success_reuse WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",admission.context().tenant_id(),run,step));
        var p=basis(admission,run,null);String stage=text(saved,"stage_kind"),original=reader.readTree(text(saved,"receipt_json")).required("source").required("parent_step_id").asText();return new Source(p,stage,step,original,inspect(p,stage,step,original));
    }
    private Basis basis(EnterpriseAccess.Admission a,String run,TaskNodeProductionAuthority.Verified production)throws Exception {
        var row=one(db.queryForList("SELECT task_id,project_id,replay_manifest,classification,created_by FROM task_run WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run));same(a.actorId(),text(row,"created_by"));
        var basis=new Basis(a,run,text(row,"task_id"),text(row,"project_id"),text(row,"classification"),reader.readTree(text(row,"replay_manifest")),production);basis.requireUnchanged();return basis;
    }
    private final class Basis {
        private final EnterpriseAccess.Admission a;private final String run,task,project,classification;private final JsonNode replay;private final TaskNodeProductionAuthority.Verified production;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> resources=Map.copyOf(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap());
        private Basis(EnterpriseAccess.Admission a,String run,String task,String project,String classification,JsonNode replay,TaskNodeProductionAuthority.Verified production){this.a=a;this.run=run;this.task=task;this.project=project;this.classification=classification;this.replay=replay;this.production=production;}
        void requireUnchanged()throws Exception{if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())bad();var current=org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap();if(resources.isEmpty()||current.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))bad();if(production!=null)production.requireUnchanged();else{if(!a.controlWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);a.controlProof().requireBootstrap(task,run);}var row=one(db.queryForList("SELECT replay_manifest,created_by,classification FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? LIMIT 2 FOR UPDATE",tenantId(),project,task,run));same(a.actorId(),text(row,"created_by"));same(classification,text(row,"classification"));same(replay,reader.readTree(text(row,"replay_manifest")));}
        EnterpriseAccess.Admission admission(){return a;}String runId(){return run;}String taskId(){return task;}String projectId(){return project;}String tenantId(){return a.context().tenant_id();}String actorId(){return a.actorId();}String classification(){return classification;}JsonNode replay(){return replay;}TaskNodeProductionAuthority.Verified production(){return production;}
    }
    private JsonNode inspect(Basis p,String stage,String step,String original)throws Exception {
        p.requireUnchanged();RunContinuationService.requireSuccessor(db,p.admission(),p.runId());
        var link=one(db.queryForList("SELECT parent_run_id,mode,receipt_sha256 FROM task_run_continuation WHERE tenant_id=? AND project_id=? AND run_id=? AND task_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.runId(),p.taskId()));same("RESUME",text(link,"mode"));String parent=text(link,"parent_run_id");
        String directParent=parent,parentStep=original;var chain=reader.createArrayNode();var visited=new HashSet<String>();
        for(int depth=0;;depth++){
            if(depth>=32||!visited.add(parent+":"+original))bad();
            var aliases=db.queryForList("SELECT step_id FROM task_run_success_reuse WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",p.tenantId(),parent,original);if(aliases.isEmpty())break;
            var alias=TaskRunSuccessReuse.read(db,json,p.admission(),parent,original).orElseThrow();same(stage,alias.required("stage_kind").asText());chain.add(reader.createObjectNode().put("run_id",parent).put("step_id",original).put("reuse_sha256",hash(alias)));parent=alias.required("original_run_id").asText();original=alias.required("original_step_id").asText();
        }

        var run=one(db.queryForList("SELECT replay_manifest,created_by,classification FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND status IN ('FAILED','CANCELLED') AND ended_at IS NOT NULL AND dispatch_closed_at IS NOT NULL LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),parent));same(p.actorId(),text(run,"created_by"));same(p.classification(),text(run,"classification"));var replay=reader.readTree(text(run,"replay_manifest"));
        for(String field:List.of("task_revision","plan_revision","snapshot_id","budget_revision","agent_release_id","task_input","model_bindings","execution_policy_version","execution_policy","project_profile","template","budget_approval"))same(replay.required(field),p.replay().required(field));same(replay.required("scope_snapshot").required("members"),p.replay().required("scope_snapshot").required("members"));same(replay.required("plan").required("content"),p.replay().required("plan").required("content"));
        var old=one(db.queryForList("SELECT s.input_sha256,s.output_ref,s.lease_generation,d.node_kind,d.canonical_definition,d.definition_sha256 FROM run_step s JOIN runtime_node_definition d ON d.tenant_id=s.tenant_id AND d.run_id=s.run_id AND d.step_id=s.step_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? AND s.status='SUCCEEDED' LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),parent,original));same(stage,text(old,"node_kind"));
        var next=one(db.queryForList("SELECT s.input_sha256,s.lease_generation,d.node_kind,d.canonical_definition,d.definition_sha256 FROM run_step s JOIN runtime_node_definition d ON d.tenant_id=s.tenant_id AND d.run_id=s.run_id AND d.step_id=s.step_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? AND s.status IN ('READY','SUCCEEDED') LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.runId(),step));same(stage,text(next,"node_kind"));same("0",number(next,"lease_generation"));
        for(String table:List.of("step_runtime_attempt","professional_runtime_session","invocation"))if(!db.queryForList("SELECT step_id FROM "+table+" WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1 FOR UPDATE",p.tenantId(),p.runId(),step).isEmpty())bad();
        var originalDefinition=reader.readTree(text(old,"canonical_definition"));var successorDefinition=reader.readTree(text(next,"canonical_definition"));same(hash(originalDefinition),text(old,"definition_sha256"));same(hash(successorDefinition),text(next,"definition_sha256"));
        if("N02".equals(stage))same(originalDefinition.required("question_id"),successorDefinition.required("question_id"));
        var pin=reader.createObjectNode().put("parent_run_id",parent).put("direct_parent_run_id",directParent).put("parent_step_id",parentStep).put("successor_run_id",p.runId()).put("original_step_id",original).put("successor_step_id",step).put("stage_kind",stage).put("continuation_sha256",text(link,"receipt_sha256")).put("original_input_sha256",text(old,"input_sha256")).put("successor_input_sha256",text(next,"input_sha256")).put("original_generation",number(old,"lease_generation"));pin.set("reuse_chain",chain);pin.set("original_definition",originalDefinition);pin.set("successor_definition",successorDefinition);pin.set("original_outputs",reader.readTree(text(old,"output_ref")));return pin;
    }
    public final class Source {
        private final Basis successor;private final String stage,step,original;private final JsonNode pin;
        private Source(Basis p,String stage,String step,String original,JsonNode pin){successor=p;this.stage=stage;this.step=step;this.original=original;this.pin=pin.deepCopy();}
        public void requireUnchanged()throws Exception{same(pin,inspect(successor,stage,step,original));}
        public void requireOriginal(EnterpriseAccess.Admission a,String run,String step)throws Exception{if(a!=successor.admission())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);same(originalRunId(),run);same(originalStepId(),step);requireUnchanged();}
        public String originalRunId(){successor.admission();return pin.required("parent_run_id").asText();}public String originalStepId(){successor.admission();return pin.required("original_step_id").asText();}
        public String successorRunId(){return successor.runId();}public String successorStepId(){successor.admission();return step;}public EnterpriseAccess.Admission admission(){return successor.admission();}
        public String stageKind(){successor.admission();return stage;}public JsonNode pins(){successor.admission();return pin.deepCopy();}public TaskNodeProductionAuthority.Verified successor(){successor.admission();if(successor.production()==null)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return successor.production();}
    }
    private static String hash(JsonNode n)throws Exception{return com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(n));}
}

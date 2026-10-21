package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** N06 has no leased run_step. This private RN-05 capability grants only bounded
 * original assembly reads and can never be supplied to dispatch/COMMIT writers. */
@org.springframework.stereotype.Component
public final class TaskRunAssemblyResumeOriginalAuthority {
    private final JdbcTemplate db;private final PermissionSchema json;private final JsonMapper reader=new JsonMapper();
    public TaskRunAssemblyResumeOriginalAuthority(@org.springframework.beans.factory.annotation.Qualifier("masterDataSource")DataSource data,PermissionSchema json){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;}
    public Optional<Source> find(TaskNodeProductionAuthority.Verified p)throws Exception {
        p.requireUnchanged();var pin=inspect(p);return pin==null?Optional.empty():Optional.of(new Source(p,pin));
    }
    public Source require(TaskNodeProductionAuthority.Verified p)throws Exception{return find(p).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));}
    private JsonNode inspect(TaskNodeProductionAuthority.Verified p)throws Exception {
        p.requireUnchanged();RunContinuationService.requireSuccessor(db,p.admission(),p.runId());
        for(String action:List.of(TaskCandidateAssemblyStageJournal.PREPARE,TaskCandidateAssemblyStageJournal.DISPATCH,TaskCandidateAssemblyStageJournal.PRODUCE,TaskCandidateAssemblyStageJournal.COMMIT))if(!db.queryForList("SELECT operation_id FROM operation_record WHERE tenant_id=? AND target_id=? AND operation_name=? LIMIT 1 FOR UPDATE",p.tenantId(),p.runId(),action).isEmpty())bad();
        var links=db.queryForList("SELECT parent_run_id,receipt_sha256 FROM task_run_continuation WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND mode='RESUME' LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),p.runId());if(links.isEmpty())return null;var link=one(links);String parent=text(link,"parent_run_id"),direct=parent;var chain=reader.createArrayNode();var seen=new HashSet<String>();
        for(int depth=0;;depth++){
            if(depth>=32||!seen.add(parent))bad();
            var aliases=db.queryForList("SELECT operation_id FROM operation_record WHERE tenant_id=? AND target_id=? AND operation_name=? AND state='COMPLETED' LIMIT 2 FOR UPDATE",p.tenantId(),parent,TaskRunAssemblySuccessReuse.ACTION);if(aliases.isEmpty())break;
            var alias=TaskRunAssemblySuccessReuse.read(db,json,p.admission(),parent).orElseThrow();chain.add(reader.createObjectNode().put("run_id",parent).put("reuse_sha256",hash(alias)));parent=alias.required("original_run_id").asText();
        }
        var rows=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND target_id=? AND operation_name=? AND state='COMPLETED' LIMIT 2 FOR UPDATE",p.tenantId(),parent,TaskCandidateAssemblyStageJournal.COMMIT);if(rows.isEmpty())return null;var commit=one(rows);
        var run=one(db.queryForList("SELECT replay_manifest,created_by,classification FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND status IN ('FAILED','CANCELLED') AND ended_at IS NOT NULL AND dispatch_closed_at IS NOT NULL LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),parent));same(p.actorId(),text(run,"created_by"));same(p.classification(),text(run,"classification"));var replay=reader.readTree(text(run,"replay_manifest"));var next=p.replay().content();
        for(String field:List.of("task_revision","plan_revision","snapshot_id","budget_revision","agent_release_id","task_input","model_bindings","execution_policy_version","execution_policy","project_profile","template","budget_approval"))same(replay.required(field),next.required(field));same(replay.required("scope_snapshot").required("members"),next.required("scope_snapshot").required("members"));same(replay.required("plan").required("content"),next.required("plan").required("content"));
        var witness=new TaskRunActivityJournal(db,json).read(p.admission(),p.projectId(),parent,text(commit,"operation_id"),TaskCandidateAssemblyStageJournal.COMMIT,text(commit,"idempotency_key"),TaskCandidateAssemblyAuthority.derived("ACTION",parent),hash(replay),p.classification(),"1.0.0",TaskCandidateAssemblyStageJournal.MANIFEST).orElseThrow();var receipt=witness.required("receipt");same(hash(witness.required("body")),receipt.required("receipt_sha256").asText());if(!receipt.required("produced").required("ready").booleanValue())return null;
        var pin=reader.createObjectNode().put("schema_version","1.0.0").put("kind","N06_RESUME_ORIGINAL_SOURCE").put("successor_run_id",p.runId()).put("direct_parent_run_id",direct).put("original_run_id",parent).put("continuation_sha256",text(link,"receipt_sha256")).put("original_replay_sha256",hash(replay)).put("successor_replay_sha256",p.replay().rawSha256()).put("original_receipt_id",text(commit,"operation_id")).put("original_receipt_sha256",hash(receipt));pin.set("reuse_chain",chain);pin.set("original_receipt",receipt);p.requireUnchanged();return pin;
    }
    public final class Source {
        private final TaskNodeProductionAuthority.Verified successor;private final JsonNode pins;
        private Source(TaskNodeProductionAuthority.Verified successor,JsonNode pins){this.successor=successor;this.pins=pins.deepCopy();}
        public void requireUnchanged()throws Exception{successor.requireUnchanged();same(pins,inspect(successor));}
        public void requireOriginal(EnterpriseAccess.Admission a,String run)throws Exception{if(a!=successor.admission())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);same(originalRunId(),run);requireUnchanged();}
        public EnterpriseAccess.Admission admission(){return successor.admission();}public TaskNodeProductionAuthority.Verified successor(){successor.admission();return successor;}
        public String originalRunId(){successor.admission();return pins.required("original_run_id").asText();}public String successorRunId(){return successor.runId();}public JsonNode pins(){successor.admission();return pins.deepCopy();}
    }
    private static String hash(JsonNode n)throws Exception{return com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(n));}
}

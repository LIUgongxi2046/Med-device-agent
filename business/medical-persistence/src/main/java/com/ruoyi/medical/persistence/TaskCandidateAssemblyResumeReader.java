package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import com.ruoyi.medical.workflow.TaskCandidateAssemblyStageProtocolV1.CommitV1;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only N06 reuse consumer: a real four-object family and exact current deterministic
 * inputs, never a synthetic run_step, dispatch, current COMMIT, or document save. */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"medical.task-run.candidate-assembly-v1.enabled","medical.task-candidate-assembly.enabled","medical.task-run.waiting-v2.server-enabled"},havingValue="true",matchIfMissing=false)
public final class TaskCandidateAssemblyResumeReader {
    private final JdbcTemplate db;private final TaskSourceVerifierSchema json;
    private final TaskRunActivityPreparation runs;private final TaskRunAssemblyResumeOriginalAuthority sources;
    private final org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyOriginalReader> originals;
    private final org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyAuthority> inputs;
    private final org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyCrossFileAuthority> crossFiles;
    private final org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyStageJournal> stages;
    public TaskCandidateAssemblyResumeReader(@org.springframework.beans.factory.annotation.Qualifier("masterDataSource")DataSource data,TaskSourceVerifierSchema json,
            TaskRunActivityPreparation runs,TaskRunAssemblyResumeOriginalAuthority sources,
            org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyOriginalReader> originals,
            org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyAuthority> inputs,
            org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyCrossFileAuthority> crossFiles,
            org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyStageJournal> stages){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.runs=runs;this.sources=sources;this.originals=originals;this.inputs=inputs;this.crossFiles=crossFiles;this.stages=stages;
    }
    @FunctionalInterface public interface Completion<T>{T apply(Reusable original)throws Exception;}
    public <T>Optional<T> read(HttpServletRequest http,Reference successor,Completion<T> completion)throws Exception{
        if(!runs.withCurrent(http,successor,c->sources.find(c.production()).isPresent()))return Optional.empty();
        var cross=crossFiles.getObject().read(http,successor);
        return originals.getObject().readResume(http,successor,runs,sources,(current,original,family)->{
            var input=inputs.getObject().require(current,cross);var commit=stages.getObject().committed(original).orElseThrow(()->new TenantAccessException(ErrorCode.STEP_NOT_READY));
            same(commit.produced(),stages.getObject().recovered(original,family));var proof=new Reusable(input,original,family,commit);proof.requireUnchanged();return completion.apply(proof);
        });
    }
    public final class Reusable {
        private final TaskCandidateAssemblyAuthority.Verified current;private final TaskCandidateAssemblyStageJournal.Original original;
        private final TaskCandidateAssemblyOriginalReader.Verified family;private final CommitV1 commit;
        private Reusable(TaskCandidateAssemblyAuthority.Verified current,TaskCandidateAssemblyStageJournal.Original original,TaskCandidateAssemblyOriginalReader.Verified family,CommitV1 commit){this.current=current;this.original=original;this.family=family;this.commit=commit;}
        public void requireUnchanged()throws Exception {
            var source=source();source.requireOriginal(current.production().admission(),original.runId());current.requireUnchanged();original.requireUnchanged();family.requireUnchanged();
            if(!current.ready()||!family.ready()||!commit.produced().ready()||!current.blockers().isEmpty()||!family.blockers().isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            same(commit,stages.getObject().committed(original).orElseThrow());same(commit.produced(),stages.getObject().recovered(original,family));
            same(source.pins().required("original_receipt"),originalReceipt());
            same(semanticInput(original.input()),semanticInput(current.input()));
            var rows=db.queryForList("SELECT manifest_json,content_sha256 FROM task_candidate_assembly_rule_set WHERE tenant_id=? AND run_id=? AND revision_id=? LIMIT 2 FOR UPDATE",original.tenantId(),original.runId(),original.input().required("rule_revision").asText());
            if(rows.size()!=1)bad();var rules=json.read(Objects.requireNonNull(rows.get(0).get("manifest_json")).toString());same(json.hash(rules),rows.get(0).get("content_sha256"));same(json.hash(rules),original.input().required("rule_manifest_sha256").asText());
            var priorRules=(ObjectNode)rules.deepCopy();var nextRules=(ObjectNode)current.ruleManifest();priorRules.remove("revision_id");nextRules.remove("revision_id");same(priorRules,nextRules);
            same(family.body(),current.body());same(family.companion(),current.companion());same(family.report(),current.crossFileReport());
            var priorManifest=(ObjectNode)family.manifest();var nextManifest=(ObjectNode)json.tree(current.manifest());
            same(original.input().required("rule_revision"),priorManifest.required("rule_revision"));same(current.input().required("rule_revision"),nextManifest.required("rule_revision"));
            priorManifest.remove("rule_revision");nextManifest.remove("rule_revision");
            var checks=priorManifest.withArray("check_ids");int found=0;for(int x=checks.size()-1;x>=0;x--)if(family.checkId().equals(checks.get(x).asText())){checks.remove(x);found++;}if(found!=1)bad();same(priorManifest,nextManifest);
            current.requireUnchanged();source.requireUnchanged();
        }
        public TaskRunAssemblyResumeOriginalAuthority.Source source(){return original.resumeSource();}
        public TaskCandidateAssemblyAuthority.Verified current(){return current;}
        public String successorInputSha256()throws Exception{return current.inputSha256();}public String successorStepId(){return current.stepId();}
        public JsonNode originalReceipt(){return json.tree(commit);}public String originalReceiptId(){return commit.receiptId();}
        public String originalReceiptSha256()throws Exception{return json.hash(originalReceipt());}
        public List<TaskCandidateAssemblyOriginalReader.Artifact> artifacts(){return family.artifacts();}
        public String originalCheckId(){return family.checkId();}public JsonNode originalCheckReceipt(){return family.checkReceipt();}
    }
    static JsonNode semanticInput(JsonNode input){
        ObjectNode copy=input.deepCopy();copy.remove(List.of("run_id","step_id","action_id","graph_id","graph_sha256","replay_sha256","rule_revision","rule_manifest_sha256"));return copy;
    }
    private static void same(Object a,Object b){if(a instanceof JsonNode left&&b instanceof JsonNode right){if(!FormalTaskNodeAuthority.sameEvidence(left,right))bad();}else if(!Objects.equals(a,b))bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

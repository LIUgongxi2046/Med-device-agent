package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** C05 dispatch preparation from actual committed chapter candidates, not predicted artifacts.
 * Original intent identity is reused on every retry. No replacement upload after uncertain I/O. */
public final class TaskSourceVerifierPreparation {
    private final JdbcTemplate db;private final TaskRunActivityPreparation runs;private final TaskSourceVerifierTargetAuthority targets;
    private final TaskSourceVerifierRules rules;private final TaskSourceVerifierInputAuthority inputs;private final TaskSourceVerifierProducerAuthority producers;
    private final TaskSourceVerifierObjectLedger objects;private final TaskSourceVerifierMaterializer nodes;private final RuntimeObjectTransport transport;
    private final TaskSourceVerifierSchema json;private final FormalCapabilityIdentityAuthority roles;private final FormalRuntimeCapabilityIdentityAuthority tools;
    private final RunTokenAuthoritySchema tokens;private final PermissionSchema permissions;
    public TaskSourceVerifierPreparation(DataSource data,TaskRunActivityPreparation runs,TaskSourceVerifierTargetAuthority targets,TaskSourceVerifierRules rules,
        TaskSourceVerifierInputAuthority inputs,TaskSourceVerifierProducerAuthority producers,TaskSourceVerifierObjectLedger objects,TaskSourceVerifierMaterializer nodes,
        RuntimeObjectTransport transport,TaskSourceVerifierSchema json,FormalCapabilityIdentityAuthority roles,FormalRuntimeCapabilityIdentityAuthority tools,
        RunTokenAuthoritySchema tokens,PermissionSchema permissions){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.runs=runs;this.targets=targets;this.rules=rules;this.inputs=inputs;this.producers=producers;this.objects=objects;this.nodes=nodes;this.transport=transport;this.json=json;this.roles=roles;this.tools=tools;this.tokens=tokens;this.permissions=permissions;}
    private org.springframework.beans.factory.ObjectProvider<TaskRunSuccessReuse> reuse;
    @org.springframework.beans.factory.annotation.Autowired void reuse(org.springframework.beans.factory.ObjectProvider<TaskRunSuccessReuse> reuse){this.reuse=reuse;}
    public List<JsonNode> materialize(HttpServletRequest http,Reference reference)throws Exception {
        var candidates=runs.withCurrent(http,reference,c->{var ids=candidateIds(c);requireCapacity(c,reference,ids);return ids;});var result=new ArrayList<JsonNode>();
        for(String id:candidates){
            var attempt=runs.withCurrent(http,reference,c->{var fixed=input(c,id);return objects.prepareInput(fixed.input,fixed.producer);});
            var transfer=transport.transferVerified(attempt,()->org.springframework.util.function.ThrowingSupplier.of(()->runs.withCurrent(http,reference,c->{var fixed=input(c,id);objects.requireTransfer(fixed.input,fixed.producer,attempt);return null;})).get());
            result.add(runs.withCurrent(http,reference,c->{var fixed=input(c,id);var made=nodes.materialize(objects.registerInput(fixed.input,fixed.producer,attempt,transfer));reuse.getObject().materializeStage(c,"N05",stepId(c.production().runId(),id));return made;}));
        }return List.copyOf(result);
    }
    private List<String> candidateIds(TaskRunActivityPreparation.Current c)throws Exception {
        c.requireUnchanged();var p=c.production();var expected=new HashSet<String>();for(var s:p.replay().content().required("plan").required("content").required("sections"))if(!expected.add(text(s,"section_id")))bad();
        var rows=db.queryForList("SELECT c.candidate_id,c.target_sections FROM task_section_writer_submission s JOIN candidate c ON c.tenant_id=s.tenant_id AND c.project_id=s.project_id AND c.candidate_id=s.candidate_id WHERE c.tenant_id=? AND c.project_id=? AND c.task_id=? AND (c.run_id=? OR EXISTS (SELECT 1 FROM task_run_success_reuse r WHERE r.tenant_id=c.tenant_id AND r.project_id=c.project_id AND r.run_id=? AND r.stage_kind='N04' AND r.original_run_id=c.run_id AND r.original_step_id=c.step_id)) AND c.status='READY' ORDER BY c.candidate_id LIMIT 513 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),p.runId(),p.runId());
        if(rows.isEmpty()||rows.size()>512)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);var actual=new HashSet<String>();var ids=new ArrayList<String>();
        for(var row:rows){var sections=json.read(str(row,"target_sections"));if(sections.size()!=1||!actual.add(sections.get(0).asText()))bad();ids.add(str(row,"candidate_id"));}if(!expected.equals(actual))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);return List.copyOf(ids);
    }
    /** Count the complete run inventory before any input object can be uploaded. Existing
     * deterministic verifier steps do not consume a second slot on replay. */
    private void requireCapacity(TaskRunActivityPreparation.Current c,Reference reference,List<String> candidates)throws Exception {
        c.requireUnchanged();var p=c.production();
        var rows=db.queryForList("SELECT step_id FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? FOR UPDATE",p.tenantId(),p.projectId(),p.runId());
        var existing=new HashSet<String>();for(var row:rows)existing.add(str(row,"step_id"));
        long missing=candidates.stream().map(id->stepId(p.runId(),id)).filter(id->!existing.contains(id)).count();
        if(rows.size()+missing>reference.maximumSteps())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
    }
    private record Fixed(TaskSourceVerifierInputAuthority.Prepared input,TaskSourceVerifierProducerAuthority.Prepared producer){}
    private Fixed input(TaskRunActivityPreparation.Current c,String candidateId)throws Exception {
        c.requireUnchanged();var p=c.production();var replay=p.replay().content();var target=targets.require(p,candidateId,c.publication(),c.policy());var originalRules=rules.require(p);
        var binding=one(c.policy().definition().bindings().stream().filter(b->"N05".equals(b.nodeKind())).toList());
        var sources=new ArrayList<TaskNodeInputProtocol.SourceSelection>();for(var m:replay.required("scope_snapshot").required("members"))sources.add(new TaskNodeInputProtocol.SourceSelection(text(m,"source_version"),text(m,"parse_version"),text(m,"index_version"),text(m,"approval_revision"),text(m,"binding_revision"),text(m,"rights_revision"),c.policy().definition().businessPurpose()));
        var role=roles.require(p.admission(),binding.roleVersion(),"ROLE",p.projectId(),"RUN_CREATION");var declared=tokens.permissions(tokens.read("RoleScope",permissions.canonical(role.canonicalDefinition().required("request").required("tool_scope"))));var principal=tokens.permissions(replay.required("principal_manifest"));var nodePermissions=declared.stream().filter(principal::contains).toList();
        String revision=derived("RULES",p.runId(),candidateId);var input=new TaskSourceVerifierInput("1.0.0","N05",p.tenantId(),p.projectId(),p.taskId(),p.runId(),stepId(p.runId(),candidateId),inputId(p.runId(),candidateId),c.graph().graphId(),c.graph().graphSha256(),p.replay().rawSha256(),text(replay,"plan_revision"),text(replay.required("plan"),"content_sha256"),text(replay,"snapshot_id"),binding.roleVersion(),new PermissionProtocol.Basis(c.policy().versionId(),c.policy().contentSha256()),Objects.requireNonNull(binding.modelVersion()),submitTool(c),candidateId,target.candidateArtifactId(),target.readObjects().get(target.candidateArtifactId()),target.candidateReceiptSha256(),target.bodyArtifactId(),target.bodyObject(),target.writerStepId(),target.candidate().targetSections(),revision,json.hash(originalRules.manifest(revision)),List.copyOf(target.claims().keySet()),originalRules.bases(),sources,nodePermissions);
        var prepared=inputs.prepare(p,c.publication(),c.policy(),input);var selected=producerRule(c,input);return new Fixed(prepared,producers.requireInput(p,c.publication(),c.policy(),input,str(selected,"rule_id"),str(selected,"content_sha256")));
    }
    private Map<String,Object> producerRule(TaskRunActivityPreparation.Current c,TaskSourceVerifierInput input)throws Exception {
        var p=c.production();var original=db.queryForList("SELECT producer_pins_json FROM task_source_verifier_object_intent WHERE tenant_id=? AND run_id=? AND step_id=? AND kind='TASK_SOURCE_VERIFIER_INPUT' LIMIT 2 FOR UPDATE",p.tenantId(),p.runId(),input.stepId());
        if(!original.isEmpty()){var pins=json.read(str(one(original),"producer_pins_json"));return Map.of("rule_id",text(pins,"producer_rule_id"),"content_sha256",text(pins,"producer_sha256"));}
        var replay=p.replay().content();var role=input.roleVersion();var rows=db.queryForList("SELECT r.rule_id,r.content_sha256,r.section_ids FROM task_source_verifier_producer_rule r JOIN task_source_verifier_producer_state s ON s.tenant_id=r.tenant_id AND s.project_id=r.project_id AND s.task_id=r.task_id AND s.rule_id=r.rule_id WHERE r.tenant_id=? AND r.project_id=? AND r.task_id=? AND r.plan_revision=? AND r.plan_sha256=? AND r.scope_snapshot=? AND r.release_id=? AND r.publication_approval_sha256=? AND r.role_version=? AND r.role_sha256=? AND r.policy_version=? AND r.policy_sha256=? AND s.status='APPROVED' ORDER BY r.rule_id LIMIT 1025 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),input.planRevision(),input.planSha256(),input.snapshotId(),text(replay,"agent_release_id"),text(replay,"publication_approval_sha256"),role.version_id(),role.content_sha256(),c.policy().versionId(),c.policy().contentSha256());
        if(rows.size()>1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var selected=new ArrayList<Map<String,Object>>();for(var row:rows){var ids=new HashSet<String>();json.read(str(row,"section_ids")).forEach(n->ids.add(n.asText()));if(ids.containsAll(input.targetSections()))selected.add(row);}return one(selected);
    }
    private PermissionProtocol.Basis submitTool(TaskRunActivityPreparation.Current c)throws Exception {
        var result=new ArrayList<PermissionProtocol.Basis>();var catalogue=SchemaCatalogue.fromClasspath();for(var t:c.production().replay().content().required("agent_release").required("tool_versions")){var basis=permissions.basis(t);var proof=tools.require(c.production().admission(),basis,"TOOL",c.production().projectId(),"AGENT_PUBLICATION");var definition=proof.canonicalDefinition().required("request").required("input_schema_ref");if(catalogue.schemaRef("tool-inputs","ReviewArgs").equals(text(definition,"schema_ref"))&&catalogue.schemaDigest("tool-inputs").equals(text(definition,"schema_sha256")))result.add(basis);proof.requireUnchanged();}return one(result);
    }
    public static String stepId(String run,String candidate){return derived("STEP",run,candidate);}public static String inputId(String run,String candidate){return derived("INPUT",run,candidate);}
    private static String derived(String kind,String run,String candidate){WireValues.uuid(run);WireValues.uuid(candidate);return UUID.nameUUIDFromBytes(("N05:"+kind+":"+run+":"+candidate).getBytes(StandardCharsets.UTF_8)).toString();}
    private static <T>T one(List<T> values){if(values.size()!=1)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);return values.get(0);}private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}private static String text(JsonNode n,String key){return n.required(key).asText();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

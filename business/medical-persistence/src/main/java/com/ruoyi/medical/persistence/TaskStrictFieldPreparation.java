package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Discovery only selects an exact-one approved fixed scope. Private authorities still authorize every selection. */
public final class TaskStrictFieldPreparation {
    private final JdbcTemplate db;private final TaskStrictFieldInputAuthority inputs;private final TaskStrictFieldMaterializer materializer;
    private final TaskStrictFieldTargetAuthority targets;private final TaskStrictFieldSchema schema;private final StrictComparisonRunScope scopes;
    private final StrictComparisonPolicyService policies;private final FormalRuntimeCapabilityIdentityAuthority tools;private final PermissionSchema json;
    public TaskStrictFieldPreparation(DataSource data,TaskStrictFieldInputAuthority inputs,TaskStrictFieldMaterializer materializer,
            TaskStrictFieldTargetAuthority targets,TaskStrictFieldSchema schema,StrictComparisonRunScope scopes,StrictComparisonPolicyService policies,
            FormalRuntimeCapabilityIdentityAuthority tools,PermissionSchema json){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.inputs=inputs;this.materializer=materializer;this.targets=targets;this.schema=schema;this.scopes=scopes;this.policies=policies;this.tools=tools;this.json=json;}
    public JsonNode materialize(TaskRunActivityPreparation.Current current)throws Exception {
        current.requireUnchanged();var p=current.production();var replay=p.replay().content();var binding=one(current.policy().definition().bindings().stream().filter(n->"N03".equals(n.nodeKind())).toList());
        var rows=db.queryForList("""
            SELECT c.*,s.latest_decision_id,d.content_sha256 AS decision_sha256 FROM task_field_target_input_capability c
            JOIN task_field_target_input_capability_state s ON s.tenant_id=c.tenant_id AND s.project_id=c.project_id AND s.task_id=c.task_id AND s.capability_id=c.capability_id
            JOIN task_field_target_input_capability_decision d ON d.tenant_id=s.tenant_id AND d.project_id=s.project_id AND d.task_id=s.task_id AND d.capability_id=s.capability_id AND d.decision_id=s.latest_decision_id
            WHERE c.tenant_id=? AND c.project_id=? AND c.task_id=? AND c.task_revision=? AND c.plan_revision=? AND c.plan_sha256=?
              AND c.release_id=? AND c.publication_approval_sha256=? AND c.role_version=? AND c.role_sha256=? AND c.policy_version=? AND c.policy_sha256=? AND s.status='APPROVED'
            ORDER BY c.capability_id LIMIT 2 FOR UPDATE
            """,p.tenantId(),p.projectId(),p.taskId(),replay.required("task_revision").asText(),current.graph().planRevision(),current.graph().planSha256(),replay.required("agent_release_id").asText(),replay.required("publication_approval_sha256").asText(),binding.roleVersion().version_id(),binding.roleVersion().content_sha256(),current.policy().versionId(),current.policy().contentSha256());
        var typed=db.queryForList("""
            SELECT r.artifact_id,r.receipt_sha256,r.target_revision,r.target_digest FROM task_strict_field_typed_registration r
            JOIN task_strict_field_typed_revision t ON t.tenant_id=r.tenant_id AND t.project_id=r.project_id AND t.revision_id=r.target_revision
            JOIN task_strict_field_typed_state s ON s.tenant_id=t.tenant_id AND s.project_id=t.project_id AND s.revision_id=t.revision_id
            WHERE r.tenant_id=? AND r.project_id=? AND r.task_id=? AND s.status='APPROVED'
              AND JSON_UNQUOTE(JSON_EXTRACT(t.request_json,'$.scope.task_revision'))=? AND JSON_UNQUOTE(JSON_EXTRACT(t.request_json,'$.scope.plan_revision'))=?
              AND JSON_UNQUOTE(JSON_EXTRACT(t.request_json,'$.release_id'))=? AND JSON_UNQUOTE(JSON_EXTRACT(t.request_json,'$.publication_approval_sha256'))=?
              AND JSON_UNQUOTE(JSON_EXTRACT(t.request_json,'$.role_version.version_id'))=? AND JSON_UNQUOTE(JSON_EXTRACT(t.request_json,'$.execution_policy_version.version_id'))=?
            ORDER BY r.artifact_id LIMIT 2 FOR UPDATE
            """,p.tenantId(),p.projectId(),p.taskId(),replay.required("task_revision").asText(),current.graph().planRevision(),replay.required("agent_release_id").asText(),replay.required("publication_approval_sha256").asText(),binding.roleVersion().version_id(),current.policy().versionId());
        if(rows.size()+typed.size()!=1)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        if(!typed.isEmpty())return typed(current,one(typed));
        var cap=one(rows);var target=targets.require(p,str(cap,"artifact_id"),str(cap,"producing_receipt_sha256"));var run=scopes.require(p);
        var approved=db.queryForList("""
            SELECT r.revision_id,r.content_sha256 FROM strict_comparison_policy_revision r
            JOIN strict_comparison_policy_state s ON s.tenant_id=r.tenant_id AND s.project_id=r.project_id AND s.policy_revision=r.revision_id
            WHERE r.tenant_id=? AND r.project_id=? AND s.status='APPROVED' AND JSON_EXTRACT(r.content_json,'$.scope')=CAST(? AS JSON)
            ORDER BY r.revision_id LIMIT 1025 FOR UPDATE
            """,p.tenantId(),p.projectId(),json.canonical(json.tree(run.scope())));
        if(approved.size()>1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var verified=new ArrayList<StrictComparisonPolicyService.Verified>();for(var row:approved)verified.add(policies.requireApprovedFixedRun(run,str(row,"revision_id"),str(row,"content_sha256")));
        var selected=new ArrayList<TaskStrictFieldInput.PolicyEntry>();
        for(var entry:target.manifest().required("target").required("entries")){
            var candidates=new ArrayList<StrictComparisonPolicyService.Verified>();
            for(var policy:verified)for(var combination:policy.definition().coverage())if(combination.fieldDefinitionId().equals(entry.required("field_definition_id").asText())&&context(combination.models(),entry,"model")&&context(combination.conditions(),entry,"condition")){candidates.add(policy);break;}
            var policy=one(candidates);selected.add(new TaskStrictFieldInput.PolicyEntry(entry.required("entry_id").asText(),new PermissionProtocol.Basis(policy.revisionId(),policy.digest()),context(current,entry.required("provenance").required("source").required("evidence_ref"))));
        }
        var prepared=inputs.prepare(p,current.publication(),current.policy(),str(cap,"artifact_id"),str(cap,"producing_receipt_sha256"),new PermissionProtocol.Basis(str(cap,"capability_id"),str(cap,"content_sha256")),new PermissionProtocol.Basis(str(cap,"latest_decision_id"),str(cap,"decision_sha256")),tool(current),selected);
        var receipt=materializer.materialize(prepared);current.requireUnchanged();return receipt;
    }
    private JsonNode typed(TaskRunActivityPreparation.Current current,Map<String,Object> row)throws Exception{
        var p=current.production();var target=targets.require(p,str(row,"artifact_id"),str(row,"receipt_sha256"));var typed=target.typedTarget();var entries=new HashMap<String,TaskStrictFieldTypedProtocol.Entry>();for(var entry:typed.request().entries())entries.put(entry.fieldId(),entry);var policies=new ArrayList<TaskStrictFieldInput.PolicyEntry>();
        for(var entry:target.manifest().required("target").required("entries")){var selected=entries.remove(entry.required("field_id").asText());if(selected==null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);policies.add(new TaskStrictFieldInput.PolicyEntry(entry.required("entry_id").asText(),selected.comparisonPolicy(),selected.observedContext()));}if(!entries.isEmpty())throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        var prepared=inputs.prepare(p,current.publication(),current.policy(),target.artifactId(),target.receiptSha256(),new PermissionProtocol.Basis(typed.revisionId(),typed.digest()),typed.decision(),tool(current),policies);var receipt=materializer.materialize(prepared);current.requireUnchanged();return receipt;
    }
    private PermissionProtocol.Basis context(TaskRunActivityPreparation.Current current,JsonNode evidence)throws Exception{
        var p=current.production();var run=scopes.require(p);
        var rows=db.queryForList("SELECT r.revision_id,r.content_sha256 FROM task_strict_field_context_revision r JOIN task_strict_field_context_state s ON s.tenant_id=r.tenant_id AND s.project_id=r.project_id AND s.revision_id=r.revision_id WHERE r.tenant_id=? AND r.project_id=? AND s.status='APPROVED' AND JSON_EXTRACT(r.request_json,'$.scope')=CAST(? AS JSON) AND JSON_EXTRACT(r.request_json,'$.evidence')=CAST(? AS JSON) ORDER BY r.revision_id LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),json.canonical(json.tree(run.scope())),json.canonical(evidence));
        var row=one(rows);return new PermissionProtocol.Basis(str(row,"revision_id"),str(row,"content_sha256"));
    }
    private PermissionProtocol.Basis tool(TaskRunActivityPreparation.Current current)throws Exception{
        var p=current.production();var replay=p.replay().content();
        var candidates=new ArrayList<PermissionProtocol.Basis>();var catalogue=SchemaCatalogue.fromClasspath();
        for(var declared:replay.required("agent_release").required("tool_versions")){
            var basis=json.basis(declared);var proof=tools.require(p.admission(),basis,"TOOL",p.projectId(),"AGENT_PUBLICATION");var definition=proof.canonicalDefinition().required("request");
            if(catalogue.schemaRef("tool-inputs","FieldArgs").equals(definition.required("input_schema_ref").required("schema_ref").asText())&&catalogue.schemaDigest("tool-inputs").equals(definition.required("input_schema_ref").required("schema_sha256").asText()))candidates.add(basis);proof.requireUnchanged();
        }
        return one(candidates);
    }
    private static boolean context(StrictComparisonPolicyDefinition.Context c,JsonNode entry,String prefix){
        String state=entry.required(prefix+"_context").asText();if("UNRESOLVED".equals(state))return false;
        if(c.mode()==StrictComparisonPolicyDefinition.ContextMode.GLOBAL)return "KNOWN_GLOBAL".equals(state);
        var ids=new HashSet<String>();for(var id:entry.required(prefix+"_ids"))ids.add(id.asText());return "KNOWN_SCOPED".equals(state)&&ids.equals(new HashSet<>(c.ids()));
    }
    private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}
    private static <T>T one(List<T> values){if(values.size()!=1)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);return values.get(0);}
}

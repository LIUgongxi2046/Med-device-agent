package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.FormalEvaluationPlanPreparationLedger.*;

/** Concrete historical metadata reader. No executable admission, budget, registration or transfer proof is produced. */
public final class FormalEvaluationPlanPreparationReadService {
    private final EnterpriseAccess access;private final EvaluationService evaluations;
    private final FormalAgentCandidateAuthority candidates;private final AgentExecutionPolicyAuthority policies;
    private final FormalEvaluationContextStore contexts;private final FormalEvaluationContextReadAuthority contextReads;
    private final FormalEvaluationContextSchema contextSchema;private final ResourceReadAuthority resources;
    private final FormalEvaluationPlanPreparationDependencies provenance;private final FormalEvaluationPlanPreparationLedger ledger;
    private final FormalEvaluationPlanPreparationRegistration registrations;private final FormalEvaluationPlanPreparationSchema schema;
    private final PermissionSchema json;private final JdbcTemplate db;
    public FormalEvaluationPlanPreparationReadService(EnterpriseAccess a,EvaluationService e,FormalAgentCandidateAuthority candidates,AgentExecutionPolicyAuthority policies,FormalEvaluationContextStore c,
            FormalEvaluationContextReadAuthority r,FormalEvaluationContextSchema cs,ResourceReadAuthority resources,
            FormalEvaluationPlanPreparationDependencies p,FormalEvaluationPlanPreparationLedger l,FormalEvaluationPlanPreparationRegistration registrations,
            FormalEvaluationPlanPreparationSchema schema,PermissionSchema json,DataSource data){this(a,e,candidates,policies,c,r,cs,resources,p,l,registrations,schema,json,new JdbcTemplate(data));}
    FormalEvaluationPlanPreparationReadService(EnterpriseAccess a,EvaluationService e,FormalAgentCandidateAuthority candidates,AgentExecutionPolicyAuthority policies,FormalEvaluationContextStore c,
            FormalEvaluationContextReadAuthority r,FormalEvaluationContextSchema cs,ResourceReadAuthority resources,
            FormalEvaluationPlanPreparationDependencies p,FormalEvaluationPlanPreparationLedger l,FormalEvaluationPlanPreparationRegistration registrations,
            FormalEvaluationPlanPreparationSchema schema,PermissionSchema json,JdbcTemplate db){access=a;evaluations=e;this.candidates=candidates;this.policies=policies;contexts=c;contextReads=r;contextSchema=cs;
        this.resources=resources;provenance=p;ledger=l;this.registrations=registrations;this.schema=schema;this.json=json;this.db=db;db.setQueryTimeout(5);}
    public JsonNode recoverExecution(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes)throws Exception {
        var recovery=schema.boundRecovery(bytes);
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            current(a,context);var request=recovery.originalRequest();var root=execution(a,request.start(),null,recovery.originalKey()).row();
            requireContext(a,root,request.contextBinding());return registration(root);
        }).get());
    }
    public JsonNode status(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes)throws Exception {
        var recovery=schema.recovery(bytes);var original=schema.request(schema.canonical(recovery.originalRequest()).getBytes(StandardCharsets.UTF_8));
        return access.execute(subject,a->ThrowingSupplier.of(()->historical(a,context,recovery,original).status()).get());
    }
    /** Package-private, unforgeable snapshot. This is read evidence, never executable authority. */
    static final class Historical {
        private final JsonNode status, witness;
        private final Row row;
        private final FormalEvaluationPlanPreparationRegistration.Receipt receipt;
        private final String tenant;
        private Historical(JsonNode status,Row row,FormalEvaluationPlanPreparationRegistration.Receipt receipt,JsonNode witness,String tenant) {
            this.status=status.deepCopy();this.row=row;this.receipt=receipt;this.witness=witness==null?null:witness.deepCopy();this.tenant=tenant;
        }
        JsonNode status(){return status.deepCopy();}
        JsonNode witness(){return witness.deepCopy();}
        Row row(){return row;}
        String tenant(){return tenant;}
        FormalEvaluationPlanPreparationRegistration.Receipt receipt(){return receipt;}
    }
    Historical contentSnapshot(ApplicationSessionIdentity.Verified subject,String context,byte[] recoveryBytes,JsonNode expected)throws Exception {
        var recovery=schema.recovery(recoveryBytes);var original=schema.request(schema.canonical(recovery.originalRequest()).getBytes(StandardCharsets.UTF_8));
        schema.validate("PreparationIdentity",expected);
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            var snapshot=historical(a,context,recovery,original);
            if(snapshot.receipt()==null)throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
            if(!snapshot.status().required("receipt").equals(expected))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            return snapshot;
        }).get());
    }
    private Historical historical(EnterpriseAccess.Admission a,String context,FormalEvaluationPlanPreparationSchema.Recovery recovery,
            FormalEvaluationPlanPreparationSchema.Original original)throws Exception {
            current(a,context);var request=original.request();var execution=execution(a,request.start(),request.executionId(),null);var root=execution.row();
            var result=json.object().put("schema_version","1.0.0").put("content_available",false);
            var attempt=ledger.findOriginal(a,recovery.originalKey(),original);
            if(attempt.isEmpty()){requireContext(a,root,null);result.put("state","NOT_FOUND").putNull("receipt");return new Historical(schema.validate("Status",result),null,null,null,null);}
            var row=ledger.requireStored(a,attempt.get());var c=row.columns();var evidence=row.intent().required("original_evidence");
            if(!root.get("execution_id").equals(c.get("execution_id"))||!root.get("project_id").equals(c.get("project_id"))||!root.get("evaluation_id").equals(c.get("evaluation_id")))bad();
            var matching=new ArrayList<JsonNode>();
            for(var n:schema.read(text(root,"canonical_admission")).required("nodes"))
                if(request.sampleId().equals(n.required("cell").required("sample_id").asText())&&request.repeatIndex()==n.required("cell").required("repeat_index").intValue()&&request.nodeKind().equals(n.required("node_kind").asText()))matching.add(n);
            if(matching.size()!=1||!matching.get(0).equals(row.intent().required("node")))bad();
            var selected=evidence.required("preview").required("selected_template");var source=evidence.required("preview").required("profile_revision");
            // The immutable090 evidence cannot select a different candidate template or authorization purpose.
            if(request.template()!=null)same(json.tree(request.template()),selected);
            int templateMatches=0;
            for(var template:execution.candidateManifest().required("template_versions"))if(template.equals(selected))templateMatches++;
            if(templateMatches!=1)bad();
            same(execution.sourceBusinessPurpose(),text(evidence,"source_business_purpose"));
            var contextRow=requireContext(a,root,new PermissionProtocol.Basis(text(c,"context_version"),text(c,"context_sha256")));
            var uses=List.of(new AgentPublicationDependencies.DependencyUse(new PermissionProtocol.Basis(text(c,"source_input_version"),text(c,"source_input_sha256")),execution.sourceBusinessPurpose()),
                    new AgentPublicationDependencies.DependencyUse(new PermissionProtocol.Basis(text(selected,"version_id"),text(selected,"content_sha256")),"EVALUATION_RESULTS"),
                    new AgentPublicationDependencies.DependencyUse(new PermissionProtocol.Basis(source.asText(),text(evidence.required("preview"),"profile_sha256")),"EVALUATION_RESULTS"));
            var current=provenance.require(a,text(c,"project_id"),text(root,"classification"),uses);
            var stable=row.intent().required("stable_provenance");
            same(json.tree(current.roots()),stable.required("roots"));same(json.tree(current.members()),stable.required("members"));same(current.classification(),text(stable,"classification"));
            var receipt=registrations.readStored(a,attempt.get());
            JsonNode artifactRead=json.tree(null);
            if(receipt.isEmpty())result.put("state","PENDING").putNull("receipt");
            else {
                artifactRead=AgentPublicationWitness.tree(resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),text(c,"project_id"),"ARTIFACT",receipt.get().artifact().artifactId()),"artifact.read","EVALUATION_RESULTS"));
                result.put("state","PREPARED");result.set("receipt",identity(receipt.get()));
            }
            var rechecked=provenance.require(a,text(c,"project_id"),text(root,"classification"),uses);
            if(!current.equals(rechecked))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            return new Historical(schema.validate("Status",result),row,receipt.orElse(null),
                    AgentPublicationWitness.tree(Map.of("root",root,"context",contextRow,"intent_columns",row.columns(),"intent",row.intent(),
                        "current_provenance",json.tree(current),"actor",a.actorId(),"context_id",Objects.toString(a.contextId(),""),
                        "current_context",json.tree(a.context()),"artifact_read",artifactRead)),a.context().tenant_id());
    }
    /** Independently verified original083 bindings, retained only for historical read integrity. */
    private record HistoricalExecution(Map<String,Object> row,JsonNode candidateManifest,String sourceBusinessPurpose) {}
    private HistoricalExecution execution(EnterpriseAccess.Admission a,FormalEvaluationExecutionProtocol.Start request,String execution,String key)throws Exception {
        var evaluation=evaluations.requireFormalDefinitionMetadata(a,request.evaluationId());
        if(!evaluation.row().contentHash().equals(request.definitionSha256()))bad();
        var rows=execution==null?db.queryForList("SELECT * FROM formal_evaluation_execution WHERE tenant_id=? AND created_by=? AND idempotency_key=? LIMIT 2",a.context().tenant_id(),a.actorId(),key)
                :db.queryForList("SELECT * FROM formal_evaluation_execution WHERE tenant_id=? AND created_by=? AND execution_id=? LIMIT 2",a.context().tenant_id(),a.actorId(),execution);
        if(rows.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);var row=one(rows);
        if(!schema.canonical(request).equals(text(row,"canonical_request")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        same(a.context().tenant_id(),text(row,"tenant_id"));same(a.actorId(),text(row,"created_by"));same(evaluation.row().projectId(),text(row,"project_id"));same(request.evaluationId(),text(row,"evaluation_id"));
        same(schema.hash(schema.read(text(row,"canonical_request"))),text(row,"request_sha256"));same(schema.hash(schema.read(text(row,"canonical_admission"))),text(row,"admission_sha256"));
        same("1.0.0",text(row,"schema_version"));same(json.manifestSha256(),text(row,"schema_manifest_sha256"));
        var admission=schema.read(text(row,"canonical_admission"));same(json.tree(evaluation.definition()),admission.required("definition"));same(json.tree(request),admission.required("request"));
        same(schema.canonical(admission),text(row,"canonical_admission"));
        same(schema.canonical(request),text(row,"canonical_request"));
        same(request.definitionSha256(),text(row,"definition_sha256"));same(request.candidate().version_id(),text(row,"candidate_version"));
        same(request.candidate().content_sha256(),text(row,"candidate_sha256"));same(request.executionPolicy().version_id(),text(row,"policy_version"));
        same(request.executionPolicy().content_sha256(),text(row,"policy_sha256"));
        var candidate=candidates.require(a,request.candidate(),evaluation.row().projectId(),"EVALUATION_RESULTS");
        same(request.executionPolicy(),candidate.executionPolicy());same(candidate.runtimeManifestSha256(),text(row,"runtime_manifest_sha256"));same(candidate.classification(),text(row,"classification"));
        var fixed=evaluation.definition().candidate_snapshots().stream().filter(c->c.version_id().equals(request.candidate().version_id())).toList();
        if(fixed.size()!=1)bad();same(request.candidate().content_sha256(),fixed.get(0).content_sha256());same(candidate.manifestDefinition(),fixed.get(0).dependency_manifest());
        var policy=policies.requireHistoricalByVersion(a,request.executionPolicy().version_id(),request.executionPolicy().content_sha256(),evaluation.row().projectId(),candidate.classification(),"EVALUATION_RESULTS");
        if(policy.definition().bindings().size()!=1||!"N01".equals(policy.definition().bindings().get(0).nodeKind()))bad();
        var expectedNodes=FormalEvaluationExecutionAdmission.matrix(evaluation.definition(),request,candidate.runtimeManifestSha256(),policy.definition().bindings(),json);
        same(json.tree(expectedNodes),admission.required("nodes"));
        int expected=evaluation.definition().request().samples().size()*evaluation.definition().request().variance_policy().repeat_count();
        if(number(row,"expected_cells")!=expected||number(row,"expected_nodes")!=expectedNodes.size())bad();
        candidate.requireUnchanged();policy.requireUnchanged();
        var event=FormalEvaluationExecutionLedger.event(row,json);
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",a.context().tenant_id(),row.get("audit_id")));
        var emitted=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",a.context().tenant_id(),row.get("event_id")));
        journal(a,row,audit,emitted,"evaluation.execution_prepared",text(row,"execution_id"),event,json.manifestSha256());
        evaluation.requireUnchanged();return new HistoricalExecution(row,candidate.manifestDefinition(),policy.businessPurpose());
    }
    private Map<String,Object> requireContext(EnterpriseAccess.Admission a,Map<String,Object> root,PermissionProtocol.Basis expected)throws Exception {
        var row=one(db.queryForList("SELECT * FROM formal_evaluation_execution_context WHERE tenant_id=? AND execution_id=? LIMIT 2",a.context().tenant_id(),root.get("execution_id")));
        var basis=new PermissionProtocol.Basis(text(row,"context_version"),text(row,"context_sha256"));
        if(expected!=null&&!expected.equals(basis))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        var view=contexts.readView(a,basis.version_id(),contextReads);same(json.tree(basis),view.required("binding"));
        var binding=schema.read(text(row,"canonical_binding"));same(schema.hash(binding),text(row,"binding_sha256"));
        same(schema.canonical(binding),text(row,"canonical_binding"));if(binding.size()!=6)bad();same("1.0.0",binding.required("schema_version").asText());
        same(root.get("request_sha256"),binding.required("root_request_sha256").asText());same(root.get("admission_sha256"),binding.required("root_admission_sha256").asText());
        same(root.get("execution_id"),binding.required("execution_id").asText());same(json.tree(basis),binding.required("context_binding"));
        var definition=view.required("fixed").required("definition");same(definition,binding.required("context_definition"));
        same(text(root,"project_id"),definition.required("project_id").asText());
        same(text(root,"candidate_version"),definition.required("candidate").required("version_id").asText());same(text(root,"candidate_sha256"),definition.required("candidate").required("content_sha256").asText());
        same(text(root,"policy_version"),definition.required("execution_policy").required("version_id").asText());same(text(root,"policy_sha256"),definition.required("execution_policy").required("content_sha256").asText());
        var bindings=definition.required("node_bindings");if(bindings.size()!=1||!"N01".equals(bindings.get(0).required("node_kind").asText()))bad();
        for(var node:schema.read(text(root,"canonical_admission")).required("nodes"))same(bindings.get(0).required("role"),node.required("role"));
        if(time(row,"created_at").isBefore(time(root,"created_at")))bad();
        same(root.get("project_id"),row.get("project_id"));same(a.actorId(),row.get("created_by"));same(root.get("classification"),row.get("classification"));
        var event=json.object().put("schema_version","1.0.0").put("kind","EVALUATION_CONTEXT_BOUND").put("execution_id",text(root,"execution_id")).put("binding_sha256",text(row,"binding_sha256"));event.set("context_binding",json.tree(basis));
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",a.context().tenant_id(),row.get("audit_id")));
        var emitted=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",a.context().tenant_id(),row.get("event_id")));
        journal(a,row,audit,emitted,"evaluation.context_bound",text(root,"execution_id"),event,contextSchema.manifest());return row;
    }
    private void journal(EnterpriseAccess.Admission a,Map<String,Object> row,Map<String,Object> audit,Map<String,Object> event,String action,String execution,JsonNode payload,String manifest)throws Exception {
        for(var actual:List.of(row,audit)){same(a.context().tenant_id(),actual.get("tenant_id"));same(a.actorId(),actual.get("created_by"));same("1.0.0",actual.get("schema_version"));same(manifest,actual.get("schema_manifest_sha256"));same(row.get("classification"),actual.get("classification"));}
        same(row.get("project_id"),audit.get("project_id"));same(a.actorId(),audit.get("subject_id"));same(action,audit.get("action"));same("SUCCESS",audit.get("result"));
        same(execution,audit.get("request_id"));same(execution,audit.get("trace_id"));same(time(row,"created_at"),time(audit,"created_at"));same(payload,schema.read(text(audit,"resource_ref")));
        same(a.context().tenant_id(),event.get("tenant_id"));same(execution,event.get("aggregate_id"));if(number(event,"aggregate_revision")!=1)bad();same(action,event.get("event_type"));same(time(row,"created_at"),time(event,"occurred_at"));same(payload,schema.read(text(event,"payload")));
    }
    private JsonNode registration(Map<String,Object> r){return schema.validate("Registration",json.object().put("schema_version","1.0.0").put("execution_id",text(r,"execution_id")).put("request_sha256",text(r,"request_sha256")).put("expected_cells",number(r,"expected_cells")).put("expected_nodes",number(r,"expected_nodes")));}
    private JsonNode identity(FormalEvaluationPlanPreparationRegistration.Receipt r){return schema.validate("PreparationIdentity",json.object().put("schema_version","1.0.0").put("operation_id",r.operationId()).put("write_id",r.writeId()).put("attempt_id",r.attemptId()).put("execution_id",r.executionId()).put("artifact_id",r.artifact().artifactId()).put("artifact_sha256",r.artifact().sha256()).put("object_version",r.objectVersion()).put("receipt_sha256",r.receiptSha256()).put("state","PREPARED").put("runnable",false).put("professional_completion",false));}
    private static void current(EnterpriseAccess.Admission a,String context){FormalEvaluationExecutionAdmission.current(a);if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
}

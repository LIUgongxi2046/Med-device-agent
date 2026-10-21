package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Original079/083/088 and fixed candidate/template/profile authority. A caller's output is never accepted. */
public final class FormalEvaluationPlanPreparationAuthority {
    static final String PURPOSE="EVALUATION_RESULTS";
    private final EnterpriseAccess access;
    private final FormalEvaluationExecutionInputs inputs;
    private final FormalEvaluationBoundExecutions bounds;
    private final FormalAgentCandidateAuthority candidates;
    private final TemplateVersionStore templates;
    private final TemplateApplicabilityService applicability;
    private final ProjectService projects;
    private final ResourceReadAuthority resources;
    private final FormalEvaluationExactInputObjectAuthorization writes;
    private final FormalEvaluationPlanExpansion expansion;
    private final FormalEvaluationPlanPreparationDependencies dependencies;
    private final FormalEvaluationPlanPreparationSchema schema;
    private final PermissionSchema json;
    public FormalEvaluationPlanPreparationAuthority(EnterpriseAccess access,FormalEvaluationExecutionInputs inputs,
            FormalEvaluationBoundExecutions bounds,FormalAgentCandidateAuthority candidates,TemplateVersionStore templates,
            TemplateApplicabilityService applicability,ProjectService projects,ResourceReadAuthority resources,
            FormalEvaluationExactInputObjectAuthorization writes,FormalEvaluationPlanExpansion expansion,
            FormalEvaluationPlanPreparationDependencies dependencies,FormalEvaluationPlanPreparationSchema schema,PermissionSchema json) {
        this.access=access;this.inputs=inputs;this.bounds=bounds;this.candidates=candidates;this.templates=templates;
        this.applicability=applicability;this.projects=projects;this.resources=resources;this.writes=writes;
        this.expansion=expansion;this.dependencies=dependencies;this.schema=schema;this.json=json;
    }
    Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,FormalEvaluationPlanPreparationSchema.Original original,boolean write)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var input=inputs.prepare(subject,context,original.request().start());
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            var prepared=new Prepared(this,original,context,a.context().tenant_id(),a.actorId(),input,write);
            prepared.current(a); return prepared;
        }).get());
    }
    static final class Prepared {
        private final FormalEvaluationPlanPreparationAuthority owner;
        private final FormalEvaluationPlanPreparationSchema.Original original;
        private final String context,tenant,actor;
        private final FormalEvaluationExecutionInputs.Prepared inputs;
        private final boolean write;
        private Prepared(FormalEvaluationPlanPreparationAuthority owner,FormalEvaluationPlanPreparationSchema.Original original,
                String context,String tenant,String actor,FormalEvaluationExecutionInputs.Prepared inputs,boolean write) {
            this.owner=owner;this.original=original;this.context=context;this.tenant=tenant;this.actor=actor;this.inputs=inputs;this.write=write;
        }
        Checked current(EnterpriseAccess.Admission a)throws Exception {return owner.inspect(a,this);}
    }
    private Checked inspect(EnterpriseAccess.Admission a,Prepared p)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);
        if(!p.tenant.equals(a.context().tenant_id())||!p.actor.equals(a.actorId())||!p.context.equals(a.context().context_revision())) denied();
        var request=p.original.request();var root=p.inputs.requireCurrent(a);FormalEvaluationPlanExpansion.exactN01(root);
        var bound=bounds.require(a,request.executionId(),p.inputs);bound.requireConsumer(a,request.executionId());
        var matches=root.nodes().stream().filter(n->n.cell().sampleId().equals(request.sampleId())&&n.cell().repeatIndex()==request.repeatIndex()&&n.nodeKind().equals(request.nodeKind())).toList();
        if(matches.size()!=1)bad();var node=matches.get(0);
        var read=resources.require(a,new PermissionProtocol.Scope(p.tenant,root.projectId(),"EVALUATION",root.request().evaluationId()),"evaluation.read",PURPOSE);
        var candidate=candidates.require(a,node.cell().candidate(),root.projectId(),PURPOSE);
        if(!candidate.executionPolicy().equals(node.cell().policy())||"DISABLED".equals(candidate.status()))denied();
        var manifest=candidate.manifestDefinition();
        var fixed=root.definition().candidate_snapshots().stream().filter(c->c.version_id().equals(node.cell().candidate().version_id())).toList();
        if(fixed.size()!=1||!fixed.get(0).content_sha256().equals(node.cell().candidate().content_sha256())
                ||!fixed.get(0).dependency_manifest().equals(manifest))bad();
        // ProjectService requires the actual current profile to equal083's fixed snapshot. Never substitute it.
        var profile=projects.fixedProfile(a,root.projectId(),bound.scope().profileRevision(),PURPOSE);
        String documentType=p.inputs.document(a,node.cell().sampleId()).required("document_type").textValue();
        var choices=json.object().putArray("items");var eligible=new LinkedHashMap<PermissionProtocol.Basis,TemplateApplicabilityService.Target>();
        var ids=new HashSet<String>();var templateEvidence=json.object();
        for(var ref:manifest.required("template_versions")) {
            var basis=FormalAgentCandidateDependencies.basis(ref);if(!ids.add(basis.version_id()))bad();
            // This read still requires actual permission; unreadable templates abort without leaking metadata.
            var template=templates.applicationTarget(a,basis.version_id(),PURPOSE);
            if(!basis.content_sha256().equals(template.content_sha256()))bad();
            if(!documentType.equals(template.content().document_type()))continue;
            var target=applicability.inspectTarget(a,root.projectId(),bound.scope().profileRevision(),documentType,basis.version_id(),PURPOSE);
            if(!basis.content_sha256().equals(target.view().template().content_sha256())||!profile.sha256().equals(target.profile().sha256()))bad();
            boolean compatible=!"NOT_APPLICABLE".equals(target.view().applicability());
            var choice=json.object().put("document_type",documentType).put("applicability",target.view().applicability()).put("eligible",compatible);
            choice.set("template",json.tree(basis));choices.add(choice);
            if(compatible)eligible.put(basis,target);
            var evidence=json.object();evidence.set("template",json.tree(target.view().template()));evidence.set("conditions",json.tree(target.view().conditions()));
            evidence.set("read_grant",AgentPublicationWitness.tree(target.grant()));evidence.set("dependencies",schema.read(target.dependencyAuthorizations()));
            templateEvidence.set(basis.version_id(),evidence);
        }
        PermissionProtocol.Basis selected=request.template();
        if(selected!=null&&!eligible.containsKey(selected))denied();
        if(selected==null&&eligible.size()==1)selected=eligible.keySet().iterator().next();
        var preview=json.object().put("schema_version","1.0.0").put("preparation_kind","DETERMINISTIC_PLAN_DRAFT")
                .put("execution_id",request.executionId()).put("evaluation_id",root.request().evaluationId())
                .put("profile_revision",bound.scope().profileRevision()).put("profile_sha256",profile.sha256())
                .put("sample_id",request.sampleId()).put("repeat_index",request.repeatIndex()).put("document_type",documentType)
                .put("runnable",false).put("professional_completion",false);
        preview.set("candidate",json.tree(node.cell().candidate()));preview.set("execution_policy",json.tree(node.cell().policy()));
        preview.set("context_binding",json.tree(bound.context().binding()));preview.set("source_snapshot",json.tree(bound.scope().snapshot()));
        preview.set("templates",choices);preview.set("selected_template",json.tree(selected));
        preview.putObject("coverage").put("scope","SELECTED_CELL").put("expected_cells",root.nodes().size()).put("expected_nodes",root.nodes().size())
                .put("selected_cells",1).put("professional_completed_cells",0).put("complete_evaluation",false);
        var blockers=preview.putArray("blockers");
        byte[] bytes=null;FormalEvaluationPlanPreparationDependencies.Checked ancestry=null;
        if(selected==null) {
            preview.put("selection_state",eligible.isEmpty()?"NO_COMPATIBLE_TEMPLATE":"CHOICE_REQUIRED");preview.putNull("plan_candidate");preview.putArray("conditions");
            blockers.add(expansion.blocker(eligible.isEmpty()?"NO_COMPATIBLE_TEMPLATE":"TEMPLATE_CHOICE_REQUIRED",List.of()));
            if(p.write)throw new TenantAccessException(ErrorCode.PLAN_UNCONFIRMED);
        } else {
            var target=applicability.requireTarget(a,root.projectId(),bound.scope().profileRevision(),documentType,selected.version_id(),PURPOSE);
            if(!target.view().equals(eligible.get(selected).view()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            var expanded=expansion.expand(p.tenant,request.executionId(),node,bound.scope().profileRevision(),target.view());
            preview.put("selection_state","SELECTED");preview.set("plan_candidate",json.tree(expanded.validation().candidate()));
            preview.set("conditions",json.tree(target.view().conditions()));expanded.blockers().forEach(blockers::add);
            bytes=expanded.validation().canonicalContent().getBytes(StandardCharsets.UTF_8);
            ancestry=dependencies.require(a,root,node,selected,new PermissionProtocol.Basis(bound.scope().profileRevision(),profile.sha256()));
            applicability.requireUnchanged(a,target);
        }
        JsonNode write=null;boolean allowed=false;
        if(p.write){write=writes.require(a,root,node);allowed=true;}
        else try{write=writes.require(a,root,node);allowed=true;}catch(TenantAccessException denied){if(denied.code()!=ErrorCode.ACCESS_DENIED)throw denied;}
        var summary=preview.putArray("action_summary").addObject().put("action","artifact.register").put("resource_kind","EVALUATION")
                .put("resource_id",root.request().evaluationId()).put("project_id",root.projectId()).put("purpose",PURPOSE).put("allowed",allowed);
        schema.validate("Preview",preview);
        var evidence=json.object().put("schema_version","1.0.0").put("preparation_kind","DETERMINISTIC_PLAN_DRAFT");
        evidence.set("input_receipt",p.inputs.receipt(a,node.cell().sampleId()));
        evidence.put("source_business_purpose",root.policy().businessPurpose());
        evidence.put("plan_schema_sha256",SchemaCatalogue.fromClasspath().schemaDigest("artifacts"));
        evidence.set("evaluation_read",AgentPublicationWitness.tree(read));evidence.set("templates",templateEvidence);evidence.set("profile",AgentPublicationWitness.tree(profile));
        evidence.set("source_snapshot",json.tree(bound.scope().snapshot()));evidence.set("candidate",candidate.authorizations());evidence.set("original_admission",schema.read(root.canonical()));
        evidence.set("preview",preview);if(write!=null)evidence.set("artifact_registration",write);
        if(bytes!=null)evidence.put("plan_sha256",WireValues.sha256(bytes));
        var stable=(ObjectNode)evidence.deepCopy();stable.remove("artifact_registration");
        ((ObjectNode)stable.required("preview")).remove("action_summary");
        if(ancestry!=null){stable.set("roots",json.tree(ancestry.roots()));stable.set("members",json.tree(ancestry.members()));stable.set("dependencies",ancestry.authorizations());stable.put("classification",ancestry.classification());}
        candidate.requireUnchanged();bound.requireUnchanged();p.inputs.requireCurrent(a);
        if(!read.equals(resources.require(a,new PermissionProtocol.Scope(p.tenant,root.projectId(),"EVALUATION",root.request().evaluationId()),"evaluation.read",PURPOSE)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return new Checked(p.tenant,p.actor,p.context,root.projectId(),p.original,root.request().evaluationId(),node,bound.context().binding(),bytes,evidence,schema.canonical(stable),ancestry,preview,p.write);
    }
    record Checked(String tenant,String actor,String context,String project,FormalEvaluationPlanPreparationSchema.Original original,
            String evaluation,FormalEvaluationExecutionProtocol.Node node,PermissionProtocol.Basis contextBinding,byte[] bytes,
            JsonNode evidence,String stable,FormalEvaluationPlanPreparationDependencies.Checked dependencies,JsonNode preview,boolean writing) {
        Checked {bytes=bytes==null?null:bytes.clone();evidence=evidence.deepCopy();preview=preview.deepCopy();}
        @Override public byte[] bytes(){return bytes==null?null:bytes.clone();}
        @Override public JsonNode evidence(){return evidence.deepCopy();}
        @Override public JsonNode preview(){return preview.deepCopy();}
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}

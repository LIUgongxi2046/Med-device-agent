package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic directory scaffold, not an088 assembler, prompt compiler or professional result. */
public final class FormalEvaluationPlanExpansion {
    private final TaskPlanPolicy plans;
    private final PermissionSchema json;
    public FormalEvaluationPlanExpansion(TaskPlanPolicy plans,PermissionSchema json){this.plans=plans;this.json=json;}
    record Expanded(TaskPlanPolicy.Validation validation, List<JsonNode> blockers) { Expanded { blockers=List.copyOf(blockers); } }
    Expanded expand(String tenant, String execution, FormalEvaluationExecutionProtocol.Node node,
            String profile, TemplateApplicabilityProtocol.TemplateView view) throws IOException {
        var template=view.template(); var mapping=new TreeMap<String,String>();
        for(var fixed:template.content().nodes()) {
            String basis=String.join("/","evaluation-plan-preparation-v1",tenant,execution,node.cell().sampleId(),
                    Integer.toString(node.cell().repeatIndex()),template.template_version(),template.content_sha256(),profile,fixed.node_id());
            if(mapping.putIfAbsent(fixed.node_id(),UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)).toString())!=null) invalid();
        }
        // Fixed directory order is kept. Inapplicable and UNKNOWN nodes remain represented.
        var sections=template.content().nodes().stream().map(fixed->new ArtifactProtocol.PlanSection(mapping.get(fixed.node_id()),
                List.of(fixed.node_id()),fixed.title(),null,ArtifactProtocol.Method.HUMAN_PROVIDED,List.of(),List.of())).toList();
        var pending=view.conditions().stream().filter(c->"UNKNOWN".equals(c.status())).map(TemplateApplicabilityProtocol.ConditionResult::target_id).distinct().sorted().toList();
        var content=new ArtifactProtocol.PlanCandidate(template.template_version(),profile,
                template.content().nodes().stream().map(TemplateProtocol.Node::node_id).toList(),sections,List.of(),pending);
        var conditions=new HashSet<String>(); view.conditions().forEach(c->conditions.add(c.target_id()));
        // Verification rules attached to template evidence are not completion rules. Do not repurpose them.
        var validation=plans.inspect(json.tree(content),template.template_version(),profile,content.requirementNodeIds(),conditions,Set.of());
        var blockers=new ArrayList<JsonNode>();
        if(!pending.isEmpty()) blockers.add(blocker("APPLICABILITY_UNKNOWN",pending));
        if(!validation.unassignedSectionIds().isEmpty()) blockers.add(blocker("OWNER_UNASSIGNED",validation.unassignedSectionIds()));
        if(!validation.incompleteRuleTargets().isEmpty()) blockers.add(blocker("COMPLETION_RULE_MISSING",validation.incompleteRuleTargets()));
        blockers.add(blocker("METHOD_REVIEW_REQUIRED",sections.stream().map(ArtifactProtocol.PlanSection::sectionId).toList()));
        return new Expanded(validation,blockers);
    }
    JsonNode blocker(String code,List<String> targets) { var result=json.object().put("code",code);result.set("target_ids",json.tree(targets));return result; }
    static void exactN01(FormalEvaluationExecutionAdmission.Prepared root) {
        if(root.policy().bindings().size()!=1||!"N01".equals(root.policy().bindings().get(0).nodeKind())||root.nodes().isEmpty()
                ||root.nodes().stream().anyMatch(n->!"N01".equals(n.nodeKind()))) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        long cells=root.nodes().stream().map(FormalEvaluationExecutionProtocol.Node::cell).distinct().count();
        if(cells!=root.nodes().size()||cells!=(long)root.definition().request().samples().size()*root.definition().request().variance_policy().repeat_count()) invalid();
    }
    private static void invalid(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

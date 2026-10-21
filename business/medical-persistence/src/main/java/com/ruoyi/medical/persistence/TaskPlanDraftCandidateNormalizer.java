package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic template expansion. Model output supplies questions and proposed DAGs only. */
public final class TaskPlanDraftCandidateNormalizer {
    private final TaskPlanDraftCandidatePolicy policy;private final TaskPlanDraftSchema schema;private final PermissionSchema json;private final SchemaCatalogue shared;
    public TaskPlanDraftCandidateNormalizer(TaskPlanDraftCandidatePolicy policy,TaskPlanDraftSchema schema,PermissionSchema json)throws Exception{this.policy=policy;this.schema=schema;this.json=json;shared=SchemaCatalogue.fromClasspath();}
    Normalized normalize(TaskPlanDraftJobAuthority.Verified authority,JsonNode modelOutput)throws Exception {
        authority.requireUnchanged();var current=authority.current();
        var result=normalize(authority.job(),current.template(),current.fixedTask().head().profileRevision(),modelOutput);authority.requireUnchanged();return result;
    }
    Normalized normalize(TaskPlanDraftHistoricalAccess.Verified authority,JsonNode modelOutput)throws Exception {
        authority.requireUnchanged();var result=normalize(authority.job(),authority.template(),authority.fixedTask().head().profileRevision(),modelOutput);authority.requireUnchanged();return result;
    }
    private Normalized normalize(TaskPlanDraftJobs.Job j,TemplateApplicabilityProtocol.TemplateView fixed,String profile,JsonNode modelOutput)throws Exception {
        var proposal=TypedSchemaBindings.bind(shared,ArtifactBindings.PLAN_CANDIDATE,modelOutput).read();
        if(!fixed.template().template_version().equals(proposal.templateVersion())||!profile.equals(proposal.profileRevision()))bad();
        var nodes=new LinkedHashMap<String,TemplateProtocol.Node>();fixed.template().content().nodes().forEach(n->{if(nodes.putIfAbsent(n.node_id(),n)!=null)bad();});
        if(!new HashSet<>(proposal.requirementNodeIds()).equals(nodes.keySet())||proposal.requirementNodeIds().size()!=nodes.size())bad();
        var sections=new LinkedHashMap<String,ArtifactProtocol.PlanSection>();var owningSection=new HashMap<String,String>();var sectionDag=new LinkedHashMap<String,List<String>>();
        for(var section:proposal.sections()){
            if(sections.putIfAbsent(section.sectionId(),section)!=null||section.requirementNodeIds().isEmpty())bad();
            sectionDag.put(section.sectionId(),section.dependsOnSectionIds());
            for(String node:section.requirementNodeIds())if(!nodes.containsKey(node)||owningSection.putIfAbsent(node,section.sectionId())!=null)bad();
        }
        if(!owningSection.keySet().equals(nodes.keySet()))bad();TaskPlanPolicy.dependencyOrder(List.copyOf(sections.keySet()),sectionDag);
        var rulesByNode=new LinkedHashMap<String,List<String>>();var approvedRulePins=new TreeMap<String,String>();
        for(var node:nodes.values()){
            var rules=new TreeSet<String>();for(var ref:node.evidence_rule().verification_rule_revisions()){rules.add(ref.version_id());pin(approvedRulePins,ref);}
            var calculation=node.evidence_rule().calculation_rule_revision();if(calculation!=null){rules.add(calculation.version_id());pin(approvedRulePins,calculation);}
            rulesByNode.put(node.node_id(),List.copyOf(rules));
        }
        for(var e:approvedRulePins.entrySet())if(fixed.template().dependency_refs().stream().noneMatch(r->r.version_id().equals(e.getKey())&&r.content_sha256().equals(e.getValue())))bad();
        var expanded=new ArrayList<TaskPlanDraftCandidatePolicy.SectionProposal>();
        for(var node:nodes.values()){
            var section=sections.get(owningSection.get(node.node_id()));var dependencies=new LinkedHashSet<String>();
            for(String dependency:section.dependsOnSectionIds())dependencies.addAll(sections.get(dependency).requirementNodeIds());
            String goal=node.title();if(!node.content_requirements().isEmpty())goal+="\n"+String.join("\n",node.content_requirements());
            expanded.add(new TaskPlanDraftCandidatePolicy.SectionProposal(node.node_id(),goal,null,method(node.content_mode()),List.copyOf(dependencies),rulesByNode.get(node.node_id())));
        }
        var questions=new LinkedHashMap<String,ArtifactProtocol.ResearchQuestion>();var questionDag=new LinkedHashMap<String,List<String>>();var keys=new LinkedHashMap<String,Map<String,String>>();
        for(var q:proposal.questions()){
            if(questions.putIfAbsent(q.questionKey(),q)!=null||!sections.containsKey(q.sectionId())||q.requirementIds().isEmpty()||new HashSet<>(q.requirementIds()).size()!=q.requirementIds().size()
                    ||!sections.get(q.sectionId()).requirementNodeIds().containsAll(q.requirementIds()))bad();
            questionDag.put(q.questionKey(),q.dependsOnQuestionKeys());var mapping=new LinkedHashMap<String,String>();
            for(String node:q.requirementIds())mapping.put(node,derived(j.run(),"question/"+q.questionKey()+"/"+node));keys.put(q.questionKey(),mapping);
        }
        TaskPlanPolicy.dependencyOrder(List.copyOf(questions.keySet()),questionDag);var expandedQuestions=new ArrayList<TaskPlanDraftCandidatePolicy.QuestionProposal>();
        for(var q:questions.values())for(String node:q.requirementIds()){
            var dependencies=new LinkedHashSet<String>();for(String dependency:q.dependsOnQuestionKeys()){var map=keys.get(dependency);if(map.containsKey(node))dependencies.add(map.get(node));else dependencies.addAll(map.values());}
            expandedQuestions.add(new TaskPlanDraftCandidatePolicy.QuestionProposal(keys.get(q.questionKey()).get(node),node,q.question(),rulesByNode.get(node),List.copyOf(dependencies)));
        }
        var inspected=policy.expand(j.tenant(),j.run(),j.taskRevision(),proposal.profileRevision(),fixed,expanded,expandedQuestions,approvedRulePins.keySet());
        var content=json.tree(inspected.validation().candidate());var pins=json.object().put("implementation","N01_TEMPLATE_EXPANSION_V1").put("model_proposal_sha256",schema.hash(modelOutput)).put("candidate_sha256",schema.hash(content))
                .put("template_version",fixed.template().template_version()).put("template_sha256",fixed.template().content_sha256());
        return new Normalized(content,pins);
    }
    record Normalized(JsonNode candidate,JsonNode pins){Normalized{candidate=candidate.deepCopy();pins=pins.deepCopy();}@Override public JsonNode candidate(){return candidate.deepCopy();}@Override public JsonNode pins(){return pins.deepCopy();}}
    private static ArtifactProtocol.Method method(String mode){return switch(mode){case "DIRECT_REFERENCE","APPROVED_CALCULATION","DIRECTORY"->ArtifactProtocol.Method.DETERMINISTIC;case "NARRATIVE"->ArtifactProtocol.Method.MODEL_ASSISTED;case "AUTHORIZED_ORIGINAL_ONLY"->ArtifactProtocol.Method.HUMAN_PROVIDED;default->throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);};}
    private static void pin(Map<String,String> pins,PermissionProtocol.Basis ref){var old=pins.putIfAbsent(ref.version_id(),ref.content_sha256());if(old!=null&&!old.equals(ref.content_sha256()))bad();}
    private static String derived(String a,String b){return UUID.nameUUIDFromBytes((a+"/"+b).getBytes(StandardCharsets.UTF_8)).toString();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
